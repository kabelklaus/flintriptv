package com.flintr.tv.viewmodel

import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tv
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flintr.tv.data.local.CredentialsRepository
import com.flintr.tv.data.local.DataCacheService
import com.flintr.tv.data.local.CachedData
import com.flintr.tv.data.model.*
import com.flintr.tv.data.repository.XtreamRepository
import com.flintr.tv.domain.usecase.DataRefreshUseCase
import com.flintr.tv.domain.usecase.RefreshStep
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
    val icon: ImageVector? = null,
    var status: LoadingStatus = LoadingStatus.Pending,
    var itemCount: Int? = null,
    var errorMessage: String? = null
)

data class DataLoadingUiState(
    val steps: List<LoadingStep> = listOf(
        LoadingStep("TV Kanäle", icon = Icons.Default.Tv),
        LoadingStep("Filme", icon = Icons.Default.Movie),
        LoadingStep("Serien", icon = Icons.Default.PlayArrow)
    ),
    val overallProgress: Float = 0f,
    val error: String? = null,
    val isLoading: Boolean = false,
    val loadingComplete: Boolean = false
    // Note: cachedData removed from StateFlow to prevent OutOfMemoryError
    // Data is only held temporarily during loading and then saved to persistent cache
)

@HiltViewModel
class DataLoadingViewModel @Inject constructor(
    private val repository: XtreamRepository,
    private val credentialsRepository: CredentialsRepository,
    private val cacheService: DataCacheService,
    private val dataRefreshUseCase: DataRefreshUseCase
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
                        loadingComplete = true
                    )
                    // cachedData is not stored in StateFlow to prevent OutOfMemoryError
                    return@launch
                }
            }

            // Cache invalid or missing - load fresh data
            Log.d("DataLoadingViewModel", "Cache invalid or missing - loading fresh data")
            loadData()
        }
    }

    private fun loadData() {
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

        dataRefreshUseCase.refreshData(credentials.username, credentials.password)
            .onEach { progress ->
                when {
                    progress.error != null -> {
                        // Map error to appropriate step
                        val stepIndex = when (progress.step) {
                            RefreshStep.CHANNELS -> 0
                            RefreshStep.MOVIES -> 1
                            RefreshStep.SERIES -> 2
                            RefreshStep.SAVING -> 2 // Error during saving is shown on last step
                        }
                        handleStepError(stepIndex, Exception(progress.error))
                    }
                    progress.isComplete -> {
                        // All data loaded and saved successfully
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            loadingComplete = true,
                            overallProgress = 1f
                        )
                        Log.d("DataLoadingViewModel", "All data loaded successfully")
                    }
                    else -> {
                        // Update step status based on current step
                        when (progress.step) {
                            RefreshStep.CHANNELS -> {
                                updateStepStatus(0, LoadingStatus.Loading)
                            }
                            RefreshStep.MOVIES -> {
                                updateStepStatus(0, LoadingStatus.Success, progress.itemCounts?.channels)
                                updateStepStatus(1, LoadingStatus.Loading)
                                updateProgress(1, 3)
                            }
                            RefreshStep.SERIES -> {
                                updateStepStatus(1, LoadingStatus.Success, progress.itemCounts?.movies)
                                updateStepStatus(2, LoadingStatus.Loading)
                                updateProgress(2, 3)
                            }
                            RefreshStep.SAVING -> {
                                updateStepStatus(2, LoadingStatus.Success, progress.itemCounts?.series)
                                updateProgress(3, 3)
                            }
                        }
                    }
                }
            }
            .launchIn(viewModelScope)
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
        val resetSteps = _uiState.value.steps.map { 
            it.copy(
                status = LoadingStatus.Pending, 
                itemCount = null, 
                errorMessage = null,
                icon = it.icon // Preserve icon when resetting
            ) 
        }
        _uiState.value = _uiState.value.copy(
            steps = resetSteps,
            overallProgress = 0f,
            error = null,
            isLoading = false,
            loadingComplete = false
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

                val result = when (stepIndex) {
                    0 -> repository.loadLiveStreamsFromApi(credentials.username, credentials.password)
                    1 -> repository.loadVodStreamsFromApi(credentials.username, credentials.password)
                    2 -> repository.loadSeriesFromApi(credentials.username, credentials.password)
                    else -> Result.failure(IllegalArgumentException("Invalid step index: $stepIndex"))
                }

                result.onSuccess { data ->
                    updateStepStatus(stepIndex, LoadingStatus.Success, data.size)
                }.onFailure { error ->
                    handleStepError(stepIndex, error)
                    return@launch
                }

                // Update overall progress
                val completedSteps = _uiState.value.steps.count { it.status == LoadingStatus.Success }
                updateProgress(completedSteps, _uiState.value.steps.size)

                // If all steps are now complete, gather all data and save to cache
                if (_uiState.value.steps.all { it.status == LoadingStatus.Success }) {
                    saveAllDataToCacheAndFinalize()
                }
            } catch (e: Exception) {
                Log.e("DataLoadingViewModel", "Error retrying step $stepIndex", e)
                handleStepError(stepIndex, e)
            }
        }
    }

    /**
     * Saves all data to cache and finalizes the loading process.
     * This should only be called when all loading steps are confirmed to be successful.
     * It gathers all data from the repository's in-memory cache and writes the complete dataset to storage.
     */
    private suspend fun saveAllDataToCacheAndFinalize() {
        val credentials = credentialsRepository.getCredentials() ?: return

        // Since all steps are successful, their data is in the repository's in-memory cache
        val liveStreams = repository.getLiveStreams(credentials.username, credentials.password).getOrNull() ?: emptyList()
        val liveCategories = repository.getLiveCategories(credentials.username, credentials.password).getOrNull() ?: emptyList()
        val vodStreams = repository.getVodStreams(credentials.username, credentials.password).getOrNull() ?: emptyList()
        val vodCategories = repository.getVodCategories(credentials.username, credentials.password).getOrNull() ?: emptyList()
        val series = repository.getSeries(credentials.username, credentials.password).getOrNull() ?: emptyList()
        val seriesCategories = repository.getSeriesCategories(credentials.username, credentials.password).getOrNull() ?: emptyList()

        val cachedData = CachedData(
            channels = liveStreams,
            movies = vodStreams,
            series = series,
            liveCategories = liveCategories,
            vodCategories = vodCategories,
            seriesCategories = seriesCategories
        )

        Log.d("DataLoadingViewModel", "Saving all data to cache: Channels=${cachedData.channels.size}, Movies=${cachedData.movies.size}, Series=${cachedData.series.size}")
        cacheService.saveCachedData(
            channels = cachedData.channels,
            movies = cachedData.movies,
            series = cachedData.series,
            liveCategories = cachedData.liveCategories,
            vodCategories = cachedData.vodCategories,
            seriesCategories = cachedData.seriesCategories
        )
        Log.d("DataLoadingViewModel", "Cache saved successfully after retry")

        // cachedData is not stored in StateFlow to prevent OutOfMemoryError
        // Data is saved to persistent cache and can be loaded on demand

        _uiState.value = _uiState.value.copy(
            isLoading = false,
            loadingComplete = true
        )
    }
}

