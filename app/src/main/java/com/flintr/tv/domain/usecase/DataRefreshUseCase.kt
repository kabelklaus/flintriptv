package com.flintr.tv.domain.usecase

import android.util.Log
import com.flintr.tv.data.local.DataCacheService
import com.flintr.tv.data.repository.XtreamRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Progress information for data refresh operations.
 */
data class DataRefreshProgress(
    val step: RefreshStep,
    val message: String,
    val itemCounts: ItemCounts? = null,
    val isComplete: Boolean = false,
    val error: String? = null,
    val retryAttempt: Int = 0,
    val maxRetries: Int = 0,
    val usedCacheFallback: Boolean = false
)

/**
 * Item counts for each content type.
 */
data class ItemCounts(
    val channels: Int = 0,
    val movies: Int = 0,
    val series: Int = 0,
    val liveCategories: Int = 0,
    val vodCategories: Int = 0,
    val seriesCategories: Int = 0
)

/**
 * Steps in the data refresh process.
 */
enum class RefreshStep {
    CHANNELS,
    MOVIES,
    SERIES,
    SAVING
}

/**
 * Use case for refreshing all data from the API and saving it to cache.
 * Centralizes the data loading logic shared between SettingsConnectionViewModel and DataLoadingViewModel.
 *
 * Includes automatic retry with exponential backoff when the API returns empty responses (0 items).
 * Falls back to cached data after all retries are exhausted.
 */
@Singleton
class DataRefreshUseCase @Inject constructor(
    private val repository: XtreamRepository,
    private val cacheService: DataCacheService
) {
    companion object {
        private const val TAG = "DataRefreshUseCase"
        private const val MAX_RETRIES = 3
        private val RETRY_DELAYS_MS = longArrayOf(2000L, 4000L, 8000L)
    }

    /**
     * Loads data with automatic retry on empty responses or errors.
     * Returns the loaded list, or null if all retries are exhausted.
     */
    private suspend fun <T> FlowCollector<DataRefreshProgress>.loadWithRetry(
        step: RefreshStep,
        stepLabel: String,
        currentCounts: ItemCounts,
        loadFn: suspend () -> Result<List<T>>
    ): List<T>? {
        val totalAttempts = MAX_RETRIES + 1

        for (attempt in 0 until totalAttempts) {
            if (attempt > 0) {
                emit(DataRefreshProgress(
                    step = step,
                    message = "$stepLabel (Versuch ${attempt + 1}/$totalAttempts...)",
                    itemCounts = currentCounts,
                    retryAttempt = attempt,
                    maxRetries = MAX_RETRIES
                ))
                delay(RETRY_DELAYS_MS[attempt - 1])
            }

            val result = loadFn()

            result.onSuccess { data ->
                if (data.isNotEmpty()) {
                    return data
                }
                Log.w(TAG, "Empty response for $step, attempt ${attempt + 1}/$totalAttempts")
            }

            result.onFailure { error ->
                Log.e(TAG, "API error for $step, attempt ${attempt + 1}/$totalAttempts: ${error.message}")
            }
        }

        // All retries exhausted
        val errorMsg = "Keine Daten nach $totalAttempts Versuchen"
        Log.w(TAG, "$step: $errorMsg")
        emit(DataRefreshProgress(
            step = step,
            message = errorMsg,
            itemCounts = currentCounts,
            error = errorMsg,
            retryAttempt = MAX_RETRIES,
            maxRetries = MAX_RETRIES
        ))
        return null
    }

    /**
     * Refreshes all data from the API and saves it to cache.
     * Emits progress updates through a Flow.
     *
     * If a step fails after all retries, falls back to existing cached data.
     * Empty API responses (0 items) are treated as errors and trigger retries.
     *
     * @param username Username for API authentication
     * @param password Password for API authentication
     * @return Flow of DataRefreshProgress events
     */
    fun refreshData(username: String, password: String): Flow<DataRefreshProgress> = flow {
        try {
            // Step 1: Load Live Streams
            emit(DataRefreshProgress(
                step = RefreshStep.CHANNELS,
                message = "Lade Kanäle...",
                isComplete = false
            ))

            Log.d(TAG, "Loading channels from API...")
            val liveStreams = loadWithRetry(
                step = RefreshStep.CHANNELS,
                stepLabel = "Lade Kanäle...",
                currentCounts = ItemCounts()
            ) {
                repository.loadLiveStreamsFromApi(username, password)
            }

            if (liveStreams == null) {
                handleCacheFallback()
                return@flow
            }

            val liveCategories = repository.getLiveCategories(username, password).getOrNull() ?: emptyList()
            Log.d(TAG, "Loaded ${liveStreams.size} channels, ${liveCategories.size} categories")

            // Step 2: Load VOD Streams
            val countsAfterChannels = ItemCounts(
                channels = liveStreams.size,
                liveCategories = liveCategories.size
            )
            emit(DataRefreshProgress(
                step = RefreshStep.MOVIES,
                message = "Lade Filme... (${liveStreams.size} Kanäle geladen)",
                itemCounts = countsAfterChannels,
                isComplete = false
            ))

            Log.d(TAG, "Loading movies from API...")
            val vodStreams = loadWithRetry(
                step = RefreshStep.MOVIES,
                stepLabel = "Lade Filme...",
                currentCounts = countsAfterChannels
            ) {
                repository.loadVodStreamsFromApi(username, password)
            }

            if (vodStreams == null) {
                handleCacheFallback()
                return@flow
            }

            val vodCategories = repository.getVodCategories(username, password).getOrNull() ?: emptyList()
            Log.d(TAG, "Loaded ${vodStreams.size} movies, ${vodCategories.size} categories")

            // Step 3: Load Series
            val countsAfterMovies = ItemCounts(
                channels = liveStreams.size,
                movies = vodStreams.size,
                liveCategories = liveCategories.size,
                vodCategories = vodCategories.size
            )
            emit(DataRefreshProgress(
                step = RefreshStep.SERIES,
                message = "Lade Serien... (${liveStreams.size} Kanäle, ${vodStreams.size} Filme geladen)",
                itemCounts = countsAfterMovies,
                isComplete = false
            ))

            Log.d(TAG, "Loading series from API...")
            val series = loadWithRetry(
                step = RefreshStep.SERIES,
                stepLabel = "Lade Serien...",
                currentCounts = countsAfterMovies
            ) {
                repository.loadSeriesFromApi(username, password)
            }

            if (series == null) {
                handleCacheFallback()
                return@flow
            }

            val seriesCategories = repository.getSeriesCategories(username, password).getOrNull() ?: emptyList()
            Log.d(TAG, "Loaded ${series.size} series, ${seriesCategories.size} categories")

            // Step 4: Save to cache
            val finalCounts = ItemCounts(
                channels = liveStreams.size,
                movies = vodStreams.size,
                series = series.size,
                liveCategories = liveCategories.size,
                vodCategories = vodCategories.size,
                seriesCategories = seriesCategories.size
            )
            emit(DataRefreshProgress(
                step = RefreshStep.SAVING,
                message = "Speichere Cache... (${liveStreams.size} Kanäle, ${vodStreams.size} Filme, ${series.size} Serien)",
                itemCounts = finalCounts,
                isComplete = false
            ))

            Log.d(TAG, "Saving data to cache: Channels=${liveStreams.size}, Movies=${vodStreams.size}, Series=${series.size}")
            cacheService.saveCachedData(
                channels = liveStreams,
                movies = vodStreams,
                series = series,
                liveCategories = liveCategories,
                vodCategories = vodCategories,
                seriesCategories = seriesCategories
            )
            Log.d(TAG, "Cache saved successfully")

            // Clear any previous fallback cooldown on successful refresh
            cacheService.clearFallbackCooldown()

            // Final success event
            emit(DataRefreshProgress(
                step = RefreshStep.SAVING,
                message = "Cache erfolgreich aktualisiert: ${liveStreams.size} Kanäle, ${vodStreams.size} Filme, ${series.size} Serien",
                itemCounts = finalCounts,
                isComplete = true
            ))

            Log.d(TAG, "Data refresh completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing data", e)
            emit(DataRefreshProgress(
                step = RefreshStep.SAVING,
                message = "Fehler beim Cache-Update: ${e.message}",
                isComplete = false,
                error = e.message
            ))
        }
    }

    /**
     * Attempts to fall back to cached data after all retries are exhausted.
     * Sets a 24h cooldown so the app doesn't immediately retry on next start.
     */
    private suspend fun FlowCollector<DataRefreshProgress>.handleCacheFallback() {
        if (cacheService.hasCacheData()) {
            Log.d(TAG, "Falling back to cached data")
            cacheService.setFallbackCooldownTimestamp(System.currentTimeMillis())
            emit(DataRefreshProgress(
                step = RefreshStep.SAVING,
                message = "Server nicht erreichbar. Verwende gespeicherte Daten.",
                isComplete = true,
                usedCacheFallback = true
            ))
        } else {
            Log.w(TAG, "No cached data available for fallback")
            emit(DataRefreshProgress(
                step = RefreshStep.SAVING,
                message = "Keine gespeicherten Daten verfügbar. Bitte später erneut versuchen.",
                isComplete = false,
                error = "Keine gespeicherten Daten verfügbar"
            ))
        }
    }
}
