package com.flintr.tv.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flintr.tv.data.local.CredentialsRepository
import com.flintr.tv.data.local.DataCacheService
import com.flintr.tv.data.local.CachedData
import com.flintr.tv.data.model.*
import com.flintr.tv.data.repository.XtreamRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LoadingStatus {
    Pending,
    Loading,
    Success,
    Error
}

data class LoadingStep(
    val label: String,
    var status: LoadingStatus = LoadingStatus.Pending,
    var itemCount: Int? = null,
    var errorMessage: String? = null
)

data class DataLoadingUiState(
    val steps: List<LoadingStep> = listOf(
        LoadingStep("TV Kanäle"),
        LoadingStep("Filme"),
        LoadingStep("Serien")
    ),
    val overallProgress: Float = 0f,
    val error: String? = null,
    val isLoading: Boolean = false,
    val loadingComplete: Boolean = false,
    val cachedData: CachedData? = null
)

@HiltViewModel
class DataLoadingViewModel @Inject constructor(
    private val repository: XtreamRepository,
    private val credentialsRepository: CredentialsRepository,
    private val cacheService: DataCacheService
) : ViewModel() {

    private val _uiState = MutableStateFlow(DataLoadingUiState())
    val uiState: StateFlow<DataLoadingUiState> = _uiState.asStateFlow()

    init {
        checkCacheAndLoad()
    }

    fun checkCacheAndLoad() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val isCacheValid = cacheService.isCacheValid()

            if (isCacheValid) {
                Log.d("DataLoadingViewModel", "Using cached data")
                val cachedData = cacheService.loadCachedData()

                if (cachedData != null) {
                    // Update UI to show cached data counts
                    val updatedSteps = _uiState.value.steps.mapIndexed { index, step ->
                        when (index) {
                            0 -> step.copy(
                                status = LoadingStatus.Success,
                                itemCount = cachedData.channels.size
                            )
                            1 -> step.copy(
                                status = LoadingStatus.Success,
                                itemCount = cachedData.movies.size
                            )
                            2 -> step.copy(
                                status = LoadingStatus.Success,
                                itemCount = cachedData.series.size
                            )
                            else -> step
                        }
                    }

                    _uiState.value = _uiState.value.copy(
                        steps = updatedSteps,
                        overallProgress = 1f,
                        isLoading = false,
                        loadingComplete = true,
                        cachedData = cachedData
                    )
                    return@launch
                }
            }

            // Cache invalid or missing - load fresh data
            Log.d("DataLoadingViewModel", "Cache invalid or missing - loading fresh data")
            loadData()
        }
    }

    private suspend fun loadData() {
        val credentials = credentialsRepository.getCredentials()
        if (credentials == null) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = "Keine Anmeldedaten gefunden"
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            isLoading = true,
            error = null,
            overallProgress = 0f
        )

        try {
            // Step 1: Load Live Streams
            updateStepStatus(0, LoadingStatus.Loading)
            val liveStreamsResult = repository.loadLiveStreamsFromApi(credentials.username, credentials.password)
            val liveCategoriesResult = repository.getLiveCategories(credentials.username, credentials.password)

            val liveStreams = liveStreamsResult.getOrElse {
                handleStepError(0, it)
                return
            }

            val liveCategories = liveCategoriesResult.getOrNull() ?: emptyList()
            updateStepStatus(0, LoadingStatus.Success, liveStreams.size)
            updateProgress(1, 3)

            // Step 2: Load VOD Streams
            updateStepStatus(1, LoadingStatus.Loading)
            val vodStreamsResult = repository.loadVodStreamsFromApi(credentials.username, credentials.password)
            val vodCategoriesResult = repository.getVodCategories(credentials.username, credentials.password)

            val vodStreams = vodStreamsResult.getOrElse {
                handleStepError(1, it)
                return
            }

            val vodCategories = vodCategoriesResult.getOrNull() ?: emptyList()
            updateStepStatus(1, LoadingStatus.Success, vodStreams.size)
            updateProgress(2, 3)

            // Step 3: Load Series
            updateStepStatus(2, LoadingStatus.Loading)
            val seriesResult = repository.loadSeriesFromApi(credentials.username, credentials.password)
            val seriesCategoriesResult = repository.getSeriesCategories(credentials.username, credentials.password)

            val series = seriesResult.getOrElse {
                handleStepError(2, it)
                return
            }

            val seriesCategories = seriesCategoriesResult.getOrNull() ?: emptyList()
            updateStepStatus(2, LoadingStatus.Success, series.size)
            updateProgress(3, 3)

            // Save to cache
            cacheService.saveCachedData(
                channels = liveStreams,
                movies = vodStreams,
                series = series,
                liveCategories = liveCategories,
                vodCategories = vodCategories,
                seriesCategories = seriesCategories
            )

            val cachedData = CachedData(
                channels = liveStreams,
                movies = vodStreams,
                series = series,
                liveCategories = liveCategories,
                vodCategories = vodCategories,
                seriesCategories = seriesCategories
            )

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                loadingComplete = true,
                cachedData = cachedData
            )

            Log.d("DataLoadingViewModel", "All data loaded successfully")
        } catch (e: Exception) {
            Log.e("DataLoadingViewModel", "Error loading data", e)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = "Fehler beim Laden der Daten: ${e.message}"
            )
        }
    }

    private fun updateStepStatus(stepIndex: Int, status: LoadingStatus, itemCount: Int? = null, errorMessage: String? = null) {
        val updatedSteps = _uiState.value.steps.toMutableList()
        if (stepIndex < updatedSteps.size) {
            updatedSteps[stepIndex] = updatedSteps[stepIndex].copy(
                status = status,
                itemCount = itemCount ?: updatedSteps[stepIndex].itemCount,
                errorMessage = errorMessage
            )
        }
        _uiState.value = _uiState.value.copy(steps = updatedSteps)
    }

    private fun handleStepError(stepIndex: Int, error: Throwable) {
        updateStepStatus(stepIndex, LoadingStatus.Error, errorMessage = error.message)
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            error = error.message ?: "Unbekannter Fehler"
        )
        Log.e("DataLoadingViewModel", "Error in step $stepIndex", error)
    }

    private fun updateProgress(completed: Int, total: Int) {
        val progress = completed.toFloat() / total.toFloat()
        _uiState.value = _uiState.value.copy(overallProgress = progress)
    }

    fun retry() {
        // Reset all steps to pending
        val resetSteps = _uiState.value.steps.map { it.copy(status = LoadingStatus.Pending, itemCount = null, errorMessage = null) }
        _uiState.value = _uiState.value.copy(
            steps = resetSteps,
            overallProgress = 0f,
            error = null,
            isLoading = false,
            loadingComplete = false,
            cachedData = null
        )
        checkCacheAndLoad()
    }

    fun retryStep(stepIndex: Int) {
        val credentials = credentialsRepository.getCredentials()
        if (credentials == null) {
            _uiState.value = _uiState.value.copy(
                error = "Keine Anmeldedaten gefunden"
            )
            return
        }

        viewModelScope.launch {
            try {
                updateStepStatus(stepIndex, LoadingStatus.Loading, errorMessage = null)
                
                when (stepIndex) {
                    0 -> {
                        // Load Live Streams
                        val liveStreamsResult = repository.loadLiveStreamsFromApi(credentials.username, credentials.password)
                        val liveCategoriesResult = repository.getLiveCategories(credentials.username, credentials.password)
                        
                        val liveStreams = liveStreamsResult.getOrElse {
                            handleStepError(stepIndex, it)
                            return@launch
                        }
                        
                        val liveCategories = liveCategoriesResult.getOrNull() ?: emptyList()
                        updateStepStatus(stepIndex, LoadingStatus.Success, liveStreams.size)
                        
                        // Update cache if other steps are also loaded
                        updateCacheIfComplete(liveStreams, liveCategories)
                    }
                    1 -> {
                        // Load VOD Streams
                        val vodStreamsResult = repository.loadVodStreamsFromApi(credentials.username, credentials.password)
                        val vodCategoriesResult = repository.getVodCategories(credentials.username, credentials.password)
                        
                        val vodStreams = vodStreamsResult.getOrElse {
                            handleStepError(stepIndex, it)
                            return@launch
                        }
                        
                        val vodCategories = vodCategoriesResult.getOrNull() ?: emptyList()
                        updateStepStatus(stepIndex, LoadingStatus.Success, vodStreams.size)
                        
                        // Update cache if other steps are also loaded
                        updateCacheIfComplete(vodStreams = vodStreams, vodCategories = vodCategories)
                    }
                    2 -> {
                        // Load Series
                        val seriesResult = repository.loadSeriesFromApi(credentials.username, credentials.password)
                        val seriesCategoriesResult = repository.getSeriesCategories(credentials.username, credentials.password)
                        
                        val series = seriesResult.getOrElse {
                            handleStepError(stepIndex, it)
                            return@launch
                        }
                        
                        val seriesCategories = seriesCategoriesResult.getOrNull() ?: emptyList()
                        updateStepStatus(stepIndex, LoadingStatus.Success, series.size)
                        
                        // Update cache if other steps are also loaded
                        updateCacheIfComplete(series = series, seriesCategories = seriesCategories)
                    }
                }
                
                // Update overall progress
                val completedSteps = _uiState.value.steps.count { it.status == LoadingStatus.Success }
                updateProgress(completedSteps, _uiState.value.steps.size)
                
                // Check if all steps are complete
                if (_uiState.value.steps.all { it.status == LoadingStatus.Success }) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        loadingComplete = true
                    )
                }
            } catch (e: Exception) {
                Log.e("DataLoadingViewModel", "Error retrying step $stepIndex", e)
                handleStepError(stepIndex, e)
            }
        }
    }

    private suspend fun updateCacheIfComplete(
        liveStreams: List<LiveStream>? = null,
        liveCategories: List<Category>? = null,
        vodStreams: List<VodStream>? = null,
        vodCategories: List<Category>? = null,
        series: List<Series>? = null,
        seriesCategories: List<Category>? = null
    ) {
        // Get current cached data or use provided data
        val currentCachedData = _uiState.value.cachedData
        val finalLiveStreams = liveStreams ?: currentCachedData?.channels ?: emptyList()
        val finalLiveCategories = liveCategories ?: currentCachedData?.liveCategories ?: emptyList()
        val finalVodStreams = vodStreams ?: currentCachedData?.movies ?: emptyList()
        val finalVodCategories = vodCategories ?: currentCachedData?.vodCategories ?: emptyList()
        val finalSeries = series ?: currentCachedData?.series ?: emptyList()
        val finalSeriesCategories = seriesCategories ?: currentCachedData?.seriesCategories ?: emptyList()
        
        // Save to cache
        cacheService.saveCachedData(
            channels = finalLiveStreams,
            movies = finalVodStreams,
            series = finalSeries,
            liveCategories = finalLiveCategories,
            vodCategories = finalVodCategories,
            seriesCategories = finalSeriesCategories
        )
        
        // Update cached data in state
        val updatedCachedData = CachedData(
            channels = finalLiveStreams,
            movies = finalVodStreams,
            series = finalSeries,
            liveCategories = finalLiveCategories,
            vodCategories = finalVodCategories,
            seriesCategories = finalSeriesCategories
        )
        
        _uiState.value = _uiState.value.copy(cachedData = updatedCachedData)
    }
}

