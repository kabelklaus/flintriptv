package com.flintr.tv.viewmodel

import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tv
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flintr.tv.data.local.CredentialsRepository
import com.flintr.tv.data.local.DataCacheService
import com.flintr.tv.data.local.CachedData
import com.flintr.tv.data.model.*
import com.flintr.tv.data.repository.XtreamRepository
import com.flintr.tv.domain.usecase.DataRefreshProgress
import com.flintr.tv.domain.usecase.DataRefreshUseCase
import com.flintr.tv.domain.usecase.RefreshStep
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.flintr.tv.utils.error.reportError

enum class LoadingStatus {
    Pending,
    Loading,
    Retrying,
    Success,
    Error
}

data class LoadingStep(
    val label: String,
    val icon: ImageVector? = null,
    var status: LoadingStatus = LoadingStatus.Pending,
    var itemCount: Int? = null,
    var errorMessage: String? = null,
    val retryAttempt: Int = 0,
    val maxRetries: Int = 0,
    // 0..1 while a Loading step can report how far it is; null = indeterminate
    val progress: Float? = null
)

// Steps 0..2 fetch one content type each; the save step persists all of them afterwards.
internal const val FETCH_STEP_COUNT = 3
internal const val SAVE_STEP_INDEX = 3

data class DataLoadingUiState(
    val steps: List<LoadingStep> = listOf(
        LoadingStep("TV Kanäle", icon = Icons.Default.Tv),
        LoadingStep("Filme", icon = Icons.Default.Movie),
        LoadingStep("Serien", icon = Icons.Default.PlayArrow),
        LoadingStep("Speichern", icon = Icons.Default.Save)
    ),
    val overallProgress: Float = 0f,
    val error: String? = null,
    val isLoading: Boolean = false,
    val loadingComplete: Boolean = false,
    val isOfflineMode: Boolean = false,
    val offlineModeMessage: String? = null
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
    sealed class NavigationEvent {
        object ReconfigureServer : NavigationEvent()
    }

    private val _uiState = MutableStateFlow(DataLoadingUiState())
    val uiState: StateFlow<DataLoadingUiState> = _uiState.asStateFlow()

    private val navigationEventsChannel = Channel<NavigationEvent>(Channel.BUFFERED)
    val navigationEvents = navigationEventsChannel.receiveAsFlow()

    init {
        checkCacheAndLoad()
    }

    private fun stepsFromCachedData(cachedData: CachedData): List<LoadingStep> {
        return _uiState.value.steps.mapIndexed { index, step ->
            when (index) {
                0 -> step.copy(status = LoadingStatus.Success, itemCount = cachedData.channels.size)
                1 -> step.copy(status = LoadingStatus.Success, itemCount = cachedData.movies.size)
                2 -> step.copy(status = LoadingStatus.Success, itemCount = cachedData.series.size)
                SAVE_STEP_INDEX -> step.copy(
                    status = LoadingStatus.Success,
                    itemCount = cachedData.channels.size + cachedData.movies.size + cachedData.series.size
                )
                else -> step
            }
        }
    }

    private fun finishWithCachedData(
        cachedData: CachedData,
        isOfflineMode: Boolean = false,
        offlineModeMessage: String? = null
    ) {
        _uiState.value = _uiState.value.copy(
            steps = stepsFromCachedData(cachedData),
            overallProgress = 1f,
            isLoading = false,
            loadingComplete = true,
            isOfflineMode = isOfflineMode,
            offlineModeMessage = offlineModeMessage
        )
    }

    fun checkCacheAndLoad() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            if (tryLoadFromFallbackCooldown()) return@launch
            if (tryLoadFromValidCache()) return@launch

            // Cache invalid or missing - load fresh data
            Log.d("DataLoadingViewModel", "Cache invalid or missing - loading fresh data")
            loadData()
        }
    }

    private suspend fun tryLoadFromFallbackCooldown(): Boolean {
        if (!cacheService.isFallbackCooldownActive() || !cacheService.hasCacheData()) return false

        Log.d("DataLoadingViewModel", "Fallback cooldown active, using cached data")
        val cachedData = cacheService.loadCachedDataAsync() ?: return false

        finishWithCachedData(
            cachedData,
            isOfflineMode = true,
            offlineModeMessage = "Server nicht erreichbar. Verwende gespeicherte Daten."
        )
        return true
    }

    private suspend fun tryLoadFromValidCache(): Boolean {
        if (!cacheService.isCacheValid()) return false

        Log.d("DataLoadingViewModel", "Using cached data")
        val cachedData = cacheService.loadCachedDataAsync() ?: return false

        finishWithCachedData(cachedData)
        // cachedData is not stored in StateFlow to prevent OutOfMemoryError
        return true
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
                    progress.usedCacheFallback -> handleCacheFallbackProgress(progress)
                    progress.requiresServerReconfiguration -> handleServerReconfigurationProgress()
                    progress.error != null -> handleErrorProgress(progress)
                    progress.isComplete -> handleCompleteProgress(progress)
                    progress.retryAttempt > 0 -> handleRetryProgress(progress)
                    else -> handleNormalProgress(progress)
                }
            }
            .launchIn(viewModelScope)
    }

    private suspend fun handleCacheFallbackProgress(progress: DataRefreshProgress) {
        val cachedData = cacheService.loadCachedDataAsync()
        val updatedSteps = if (cachedData != null) {
            stepsFromCachedData(cachedData)
        } else {
            _uiState.value.steps
        }

        _uiState.value = _uiState.value.copy(
            steps = updatedSteps,
            isLoading = false,
            loadingComplete = true,
            overallProgress = 1f,
            isOfflineMode = true,
            offlineModeMessage = progress.message
        )
    }

    private fun handleServerReconfigurationProgress() {
        credentialsRepository.clearActiveCredentials()
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            loadingComplete = false,
            error = null,
            overallProgress = 0f
        )
        viewModelScope.launch {
            navigationEventsChannel.send(NavigationEvent.ReconfigureServer)
        }
    }

    private fun handleErrorProgress(progress: DataRefreshProgress) {
        val stepIndex = stepIndexFromRefreshStep(progress.step)
        handleStepError(stepIndex, Exception(progress.error))
    }

    private fun handleCompleteProgress(progress: DataRefreshProgress) {
        val savedItems = progress.itemCounts?.let { it.channels + it.movies + it.series }
        updateStepStatus(SAVE_STEP_INDEX, LoadingStatus.Success, savedItems)
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            loadingComplete = true,
            overallProgress = 1f
        )
        Log.d("DataLoadingViewModel", "All data loaded successfully")
    }

    private fun handleRetryProgress(progress: DataRefreshProgress) {
        val stepIndex = stepIndexFromRefreshStep(progress.step)
        updateStepStatus(
            stepIndex,
            LoadingStatus.Retrying,
            retryAttempt = progress.retryAttempt,
            maxRetries = progress.maxRetries
        )
    }

    private fun handleNormalProgress(progress: DataRefreshProgress) {
        when (progress.step) {
            RefreshStep.CHANNELS -> {
                markPendingStepsAsLoading()
            }
            RefreshStep.MOVIES -> {
                updateStepStatus(0, LoadingStatus.Success, progress.itemCounts?.channels)
                ensureStepIsLoading(stepIndex = 1)
                ensureStepIsLoading(stepIndex = 2)
                updateProgress(1)
            }
            RefreshStep.SERIES -> {
                updateStepStatus(1, LoadingStatus.Success, progress.itemCounts?.movies)
                ensureStepIsLoading(stepIndex = 2)
                updateProgress(2)
            }
            RefreshStep.SAVING -> {
                if (_uiState.value.steps[2].status != LoadingStatus.Success) {
                    updateStepStatus(2, LoadingStatus.Success, progress.itemCounts?.series)
                }
                updateSaveProgress(progress.savedItems, progress.itemsToSave)
            }
        }
    }

    private fun updateSaveProgress(savedItems: Int, itemsToSave: Int) {
        val fraction = if (itemsToSave > 0) (savedItems.toFloat() / itemsToSave).coerceIn(0f, 1f) else 0f
        updateStepStatus(SAVE_STEP_INDEX, LoadingStatus.Loading, progress = fraction)
        _uiState.value = _uiState.value.copy(
            overallProgress = (FETCH_STEP_COUNT + fraction) / _uiState.value.steps.size
        )
    }

    private fun markPendingStepsAsLoading() {
        // The save step only starts once all fetches are done.
        val updatedSteps = _uiState.value.steps.mapIndexed { index, step ->
            if (step.status == LoadingStatus.Pending && index < FETCH_STEP_COUNT) {
                step.copy(status = LoadingStatus.Loading)
            } else {
                step
            }
        }
        _uiState.value = _uiState.value.copy(steps = updatedSteps)
    }

    private fun ensureStepIsLoading(stepIndex: Int) {
        val currentStep = _uiState.value.steps.getOrNull(stepIndex) ?: return
        if (currentStep.status == LoadingStatus.Pending) {
            updateStepStatus(stepIndex, LoadingStatus.Loading)
        }
    }

    private fun stepIndexFromRefreshStep(step: RefreshStep): Int {
        return when (step) {
            RefreshStep.CHANNELS -> 0
            RefreshStep.MOVIES -> 1
            RefreshStep.SERIES -> 2
            RefreshStep.SAVING -> SAVE_STEP_INDEX
        }
    }

    private fun updateStepStatus(
        stepIndex: Int,
        status: LoadingStatus,
        itemCount: Int? = null,
        errorMessage: String? = null,
        retryAttempt: Int = 0,
        maxRetries: Int = 0,
        progress: Float? = null
    ) {
        val updatedSteps = _uiState.value.steps.toMutableList()
        if (stepIndex < updatedSteps.size) {
            updatedSteps[stepIndex] = updatedSteps[stepIndex].copy(
                status = status,
                itemCount = itemCount ?: updatedSteps[stepIndex].itemCount,
                errorMessage = errorMessage,
                retryAttempt = retryAttempt,
                maxRetries = maxRetries,
                progress = progress
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
        reportError("DataLoadingViewModel", "Error in step $stepIndex", error)
    }

    private fun updateProgress(completed: Int) {
        val progress = completed.toFloat() / _uiState.value.steps.size
        _uiState.value = _uiState.value.copy(overallProgress = progress)
    }

    fun retry() {
        // Clear fallback cooldown on manual retry
        cacheService.clearFallbackCooldown()

        // Reset all steps to pending
        val resetSteps = _uiState.value.steps.map {
            it.copy(
                status = LoadingStatus.Pending,
                itemCount = null,
                errorMessage = null,
                icon = it.icon, // Preserve icon when resetting
                retryAttempt = 0,
                maxRetries = 0,
                progress = null
            )
        }
        _uiState.value = _uiState.value.copy(
            steps = resetSteps,
            overallProgress = 0f,
            error = null,
            isLoading = false,
            loadingComplete = false,
            isOfflineMode = false,
            offlineModeMessage = null
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

                if (stepIndex == SAVE_STEP_INDEX) {
                    saveAllDataToCacheAndFinalize()
                    return@launch
                }

                var freshLive: List<LiveStream>? = null
                var freshVod: List<VodStream>? = null
                var freshSeries: List<Series>? = null

                val result: Result<Int> = when (stepIndex) {
                    0 -> repository.loadLiveStreamsFromApi(credentials.username, credentials.password)
                        .onSuccess { freshLive = it }
                        .map { it.size }
                    1 -> repository.loadVodStreamsFromApi(credentials.username, credentials.password)
                        .onSuccess { freshVod = it }
                        .map { it.size }
                    2 -> repository.loadSeriesFromApi(credentials.username, credentials.password)
                        .onSuccess { freshSeries = it }
                        .map { it.size }
                    else -> Result.failure(IllegalArgumentException("Invalid step index: $stepIndex"))
                }

                result.onSuccess { size ->
                    updateStepStatus(stepIndex, LoadingStatus.Success, size)
                }.onFailure { error ->
                    handleStepError(stepIndex, error)
                    return@launch
                }

                // Update overall progress
                val completedSteps = _uiState.value.steps.count { it.status == LoadingStatus.Success }
                updateProgress(completedSteps)

                // Once every fetch step succeeded, run the save step
                if (_uiState.value.steps.take(FETCH_STEP_COUNT).all { it.status == LoadingStatus.Success }) {
                    saveAllDataToCacheAndFinalize(freshLive = freshLive, freshVod = freshVod, freshSeries = freshSeries)
                }
            } catch (e: Exception) {
                reportError("DataLoadingViewModel", "Error retrying step $stepIndex", e)
                handleStepError(stepIndex, e)
            }
        }
    }

    /**
     * Saves all data to cache and finalizes the loading process.
     * This should only be called when all fetch steps are confirmed to be successful.
     * It gathers all data from the repository's in-memory cache and writes the complete dataset to storage,
     * reporting chunk progress on the save step.
     */
    private suspend fun saveAllDataToCacheAndFinalize(
        freshLive: List<LiveStream>? = null,
        freshVod: List<VodStream>? = null,
        freshSeries: List<Series>? = null,
    ) {
        val credentials = credentialsRepository.getCredentials() ?: return

        // Since all steps are successful, their data is in the repository's in-memory cache
        // (or was just freshly loaded via retryStep and passed in directly)
        val liveStreams = freshLive
            ?: repository.getLiveStreams(credentials.username, credentials.password).getOrNull() ?: emptyList()
        val liveCategories = repository.getLiveCategories(credentials.username, credentials.password).getOrNull() ?: emptyList()
        val vodStreams = freshVod
            ?: repository.getVodStreams(credentials.username, credentials.password).getOrNull() ?: emptyList()
        val vodCategories = repository.getVodCategories(credentials.username, credentials.password).getOrNull() ?: emptyList()
        val series = freshSeries
            ?: repository.getSeries(credentials.username, credentials.password).getOrNull() ?: emptyList()
        val seriesCategories = repository.getSeriesCategories(credentials.username, credentials.password).getOrNull() ?: emptyList()

        val itemsToSave = liveStreams.size + vodStreams.size + series.size
        updateSaveProgress(savedItems = 0, itemsToSave = itemsToSave)

        Log.d("DataLoadingViewModel", "Saving all data to cache: Channels=${liveStreams.size}, Movies=${vodStreams.size}, Series=${series.size}")
        val saved = cacheService.saveCachedData(
            channels = liveStreams,
            movies = vodStreams,
            series = series,
            liveCategories = liveCategories,
            vodCategories = vodCategories,
            seriesCategories = seriesCategories,
            onProgress = { savedItems -> updateSaveProgress(savedItems, itemsToSave) }
        )
        if (!saved) {
            handleStepError(SAVE_STEP_INDEX, IllegalStateException("Daten konnten nicht gespeichert werden"))
            return
        }
        Log.d("DataLoadingViewModel", "Cache saved successfully after retry")

        // Data is not stored in StateFlow to prevent OutOfMemoryError;
        // it is in the persistent cache and can be loaded on demand
        updateStepStatus(SAVE_STEP_INDEX, LoadingStatus.Success, itemsToSave)
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            loadingComplete = true,
            overallProgress = 1f
        )
    }
}
