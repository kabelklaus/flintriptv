package com.flintr.tv.domain.usecase

import android.util.Log
import com.flintr.tv.data.local.DataCacheService
import com.flintr.tv.data.model.Category
import com.flintr.tv.data.model.LiveStream
import com.flintr.tv.data.model.Series
import com.flintr.tv.data.model.VodStream
import com.flintr.tv.data.repository.XtreamRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
        private const val UNKNOWN_ERROR_MESSAGE = "Unbekannter Fehler"
        private const val MAX_PARALLEL_LOADS = 3
        private const val MAX_RETRIES = 3
        private val RETRY_DELAYS_MS = longArrayOf(2000L, 4000L, 8000L)
    }

    /**
     * Loads data with automatic retry on empty responses or errors.
     * Returns loaded data on success, or a failure message after all retries are exhausted.
     */
    private suspend fun <T> loadWithRetry(
        step: RefreshStep,
        loadFn: suspend () -> Result<List<T>>
    ): RetryLoadResult<T> {
        val totalAttempts = MAX_RETRIES + 1

        for (attempt in 0 until totalAttempts) {
            if (attempt > 0) {
                delay(RETRY_DELAYS_MS[attempt - 1])
            }

            val result = try {
                loadFn()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Result.failure(e)
            }

            result.onSuccess { data ->
                if (data.isNotEmpty()) {
                    return RetryLoadResult.success(data)
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
        return RetryLoadResult.failure(errorMsg)
    }

    private suspend fun <T> loadBundle(
        step: RefreshStep,
        username: String,
        password: String,
        semaphore: Semaphore,
        contentLabel: String,
        loadStreams: suspend (String, String) -> Result<List<T>>,
        loadCategories: suspend (String, String) -> Result<List<Category>>
    ): ContentLoadResult<T> = semaphore.withPermit {
        val streamsResult = loadWithRetry(step) {
            loadStreams(username, password)
        }
        val streams = streamsResult.data ?: return@withPermit ContentLoadResult.failure(
            step = step,
            errorMessage = streamsResult.errorMessage ?: UNKNOWN_ERROR_MESSAGE
        )

        val categories = loadCategories(username, password).getOrNull() ?: emptyList()
        Log.d(TAG, "Loaded ${streams.size} $contentLabel, ${categories.size} categories")
        ContentLoadResult.success(step, streams, categories)
    }

    private suspend fun loadLiveBundle(
        username: String,
        password: String,
        semaphore: Semaphore
    ): ContentLoadResult<LiveStream> = loadBundle(
        step = RefreshStep.CHANNELS,
        username = username,
        password = password,
        semaphore = semaphore,
        contentLabel = "channels",
        loadStreams = repository::loadLiveStreamsFromApi,
        loadCategories = repository::getLiveCategories
    )

    private suspend fun loadVodBundle(
        username: String,
        password: String,
        semaphore: Semaphore
    ): ContentLoadResult<VodStream> = loadBundle(
        step = RefreshStep.MOVIES,
        username = username,
        password = password,
        semaphore = semaphore,
        contentLabel = "movies",
        loadStreams = repository::loadVodStreamsFromApi,
        loadCategories = repository::getVodCategories
    )

    private suspend fun loadSeriesBundle(
        username: String,
        password: String,
        semaphore: Semaphore
    ): ContentLoadResult<Series> = loadBundle(
        step = RefreshStep.SERIES,
        username = username,
        password = password,
        semaphore = semaphore,
        contentLabel = "series",
        loadStreams = repository::loadSeriesFromApi,
        loadCategories = repository::getSeriesCategories
    )

    private suspend fun cancelRemaining(vararg deferreds: Deferred<*>) {
        deferreds.forEach { deferred -> deferred.cancel() }
        deferreds.forEach { deferred -> runCatching { deferred.await() } }
    }

    private suspend fun FlowCollector<DataRefreshProgress>.emitStepFailure(
        step: RefreshStep,
        currentCounts: ItemCounts,
        errorMessage: String?
    ) {
        val message = errorMessage ?: "Keine Daten nach ${MAX_RETRIES + 1} Versuchen"
        emit(
            DataRefreshProgress(
                step = step,
                message = message,
                itemCounts = currentCounts,
                error = message,
                retryAttempt = MAX_RETRIES,
                maxRetries = MAX_RETRIES
            )
        )
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

            val loadResult = supervisorScope {
                Log.d(TAG, "Starting parallel refresh with max $MAX_PARALLEL_LOADS concurrent loads")
                val semaphore = Semaphore(MAX_PARALLEL_LOADS)

                val liveDeferred = async { loadLiveBundle(username, password, semaphore) }
                val vodDeferred = async { loadVodBundle(username, password, semaphore) }
                val seriesDeferred = async { loadSeriesBundle(username, password, semaphore) }

                val liveResult = liveDeferred.await()
                if (!liveResult.isSuccess) {
                    cancelRemaining(vodDeferred, seriesDeferred)
                    return@supervisorScope RefreshLoadResult.failure(
                        step = RefreshStep.CHANNELS,
                        counts = ItemCounts(),
                        errorMessage = liveResult.errorMessage
                    )
                }
                val liveStreams = liveResult.data ?: return@supervisorScope RefreshLoadResult.failure(
                    step = RefreshStep.CHANNELS,
                    counts = ItemCounts(),
                    errorMessage = liveResult.errorMessage
                )
                val liveCategories = liveResult.categories

                // Step 2: Load VOD Streams (already in progress in background)
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

                val vodResult = vodDeferred.await()
                if (!vodResult.isSuccess) {
                    cancelRemaining(seriesDeferred)
                    return@supervisorScope RefreshLoadResult.failure(
                        step = RefreshStep.MOVIES,
                        counts = countsAfterChannels,
                        errorMessage = vodResult.errorMessage
                    )
                }
                val vodStreams = vodResult.data ?: return@supervisorScope RefreshLoadResult.failure(
                    step = RefreshStep.MOVIES,
                    counts = countsAfterChannels,
                    errorMessage = vodResult.errorMessage
                )
                val vodCategories = vodResult.categories

                // Step 3: Load Series (already in progress in background)
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

                val seriesResult = seriesDeferred.await()
                if (!seriesResult.isSuccess) {
                    return@supervisorScope RefreshLoadResult.failure(
                        step = RefreshStep.SERIES,
                        counts = countsAfterMovies,
                        errorMessage = seriesResult.errorMessage
                    )
                }

                RefreshLoadResult.success(
                    liveStreams = liveStreams,
                    liveCategories = liveCategories,
                    vodStreams = vodStreams,
                    vodCategories = vodCategories,
                    series = seriesResult.data ?: return@supervisorScope RefreshLoadResult.failure(
                        step = RefreshStep.SERIES,
                        counts = countsAfterMovies,
                        errorMessage = seriesResult.errorMessage
                    ),
                    seriesCategories = seriesResult.categories
                )
            }

            if (!loadResult.isSuccess) {
                emitStepFailure(
                    step = loadResult.failedStep ?: RefreshStep.SAVING,
                    currentCounts = loadResult.failedCounts ?: ItemCounts(),
                    errorMessage = loadResult.errorMessage
                )
                handleCacheFallback()
                return@flow
            }

            val liveStreams = loadResult.liveStreams
            val liveCategories = loadResult.liveCategories
            val vodStreams = loadResult.vodStreams
            val vodCategories = loadResult.vodCategories
            val series = loadResult.series
            val seriesCategories = loadResult.seriesCategories

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

    private data class RetryLoadResult<T>(
        val data: List<T>? = null,
        val errorMessage: String? = null
    ) {
        companion object {
            fun <T> success(data: List<T>) = RetryLoadResult(data = data)
            fun <T> failure(errorMessage: String) = RetryLoadResult<T>(errorMessage = errorMessage)
        }
    }

    private data class ContentLoadResult<T>(
        val step: RefreshStep,
        val data: List<T>? = null,
        val categories: List<Category> = emptyList(),
        val errorMessage: String? = null
    ) {
        val isSuccess: Boolean get() = data != null

        companion object {
            fun <T> success(
                step: RefreshStep,
                data: List<T>,
                categories: List<Category>
            ) = ContentLoadResult(
                step = step,
                data = data,
                categories = categories
            )

            fun <T> failure(step: RefreshStep, errorMessage: String) = ContentLoadResult<T>(
                step = step,
                errorMessage = errorMessage
            )
        }
    }

    private data class RefreshLoadResult(
        val liveStreams: List<LiveStream> = emptyList(),
        val liveCategories: List<Category> = emptyList(),
        val vodStreams: List<VodStream> = emptyList(),
        val vodCategories: List<Category> = emptyList(),
        val series: List<Series> = emptyList(),
        val seriesCategories: List<Category> = emptyList(),
        val failedStep: RefreshStep? = null,
        val failedCounts: ItemCounts? = null,
        val errorMessage: String? = null
    ) {
        val isSuccess: Boolean get() = failedStep == null

        companion object {
            fun success(
                liveStreams: List<LiveStream>,
                liveCategories: List<Category>,
                vodStreams: List<VodStream>,
                vodCategories: List<Category>,
                series: List<Series>,
                seriesCategories: List<Category>
            ) = RefreshLoadResult(
                liveStreams = liveStreams,
                liveCategories = liveCategories,
                vodStreams = vodStreams,
                vodCategories = vodCategories,
                series = series,
                seriesCategories = seriesCategories
            )

            fun failure(
                step: RefreshStep,
                counts: ItemCounts,
                errorMessage: String?
            ) = RefreshLoadResult(
                failedStep = step,
                failedCounts = counts,
                errorMessage = errorMessage
            )
        }
    }
}
