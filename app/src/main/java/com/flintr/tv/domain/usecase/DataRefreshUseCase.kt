package com.flintr.tv.domain.usecase

import android.util.Log
import com.flintr.tv.data.local.DataCacheService
import com.flintr.tv.data.repository.XtreamRepository
import kotlinx.coroutines.flow.Flow
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
    val error: String? = null
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
 */
@Singleton
class DataRefreshUseCase @Inject constructor(
    private val repository: XtreamRepository,
    private val cacheService: DataCacheService
) {
    companion object {
        private const val TAG = "DataRefreshUseCase"
    }

    /**
     * Refreshes all data from the API and saves it to cache.
     * Emits progress updates through a Flow.
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
            val liveStreamsResult = repository.loadLiveStreamsFromApi(username, password)
            val liveCategoriesResult = repository.getLiveCategories(username, password)

            val liveStreams = liveStreamsResult.getOrElse { error ->
                Log.e(TAG, "Failed to load channels", error)
                emit(DataRefreshProgress(
                    step = RefreshStep.CHANNELS,
                    message = "Fehler beim Laden der Kanäle: ${error.message}",
                    isComplete = false,
                    error = error.message
                ))
                return@flow
            }

            val liveCategories = liveCategoriesResult.getOrNull() ?: emptyList()
            Log.d(TAG, "Loaded ${liveStreams.size} channels, ${liveCategories.size} categories")

            // Step 2: Load VOD Streams
            emit(DataRefreshProgress(
                step = RefreshStep.MOVIES,
                message = "Lade Filme... (${liveStreams.size} Kanäle geladen)",
                itemCounts = ItemCounts(
                    channels = liveStreams.size,
                    liveCategories = liveCategories.size
                ),
                isComplete = false
            ))

            Log.d(TAG, "Loading movies from API...")
            val vodStreamsResult = repository.loadVodStreamsFromApi(username, password)
            val vodCategoriesResult = repository.getVodCategories(username, password)

            val vodStreams = vodStreamsResult.getOrElse { error ->
                Log.e(TAG, "Failed to load movies", error)
                emit(DataRefreshProgress(
                    step = RefreshStep.MOVIES,
                    message = "Fehler beim Laden der Filme: ${error.message}",
                    itemCounts = ItemCounts(
                        channels = liveStreams.size,
                        liveCategories = liveCategories.size
                    ),
                    isComplete = false,
                    error = error.message
                ))
                return@flow
            }

            val vodCategories = vodCategoriesResult.getOrNull() ?: emptyList()
            Log.d(TAG, "Loaded ${vodStreams.size} movies, ${vodCategories.size} categories")

            // Step 3: Load Series
            emit(DataRefreshProgress(
                step = RefreshStep.SERIES,
                message = "Lade Serien... (${liveStreams.size} Kanäle, ${vodStreams.size} Filme geladen)",
                itemCounts = ItemCounts(
                    channels = liveStreams.size,
                    movies = vodStreams.size,
                    liveCategories = liveCategories.size,
                    vodCategories = vodCategories.size
                ),
                isComplete = false
            ))

            Log.d(TAG, "Loading series from API...")
            val seriesResult = repository.loadSeriesFromApi(username, password)
            val seriesCategoriesResult = repository.getSeriesCategories(username, password)

            val series = seriesResult.getOrElse { error ->
                Log.e(TAG, "Failed to load series", error)
                emit(DataRefreshProgress(
                    step = RefreshStep.SERIES,
                    message = "Fehler beim Laden der Serien: ${error.message}",
                    itemCounts = ItemCounts(
                        channels = liveStreams.size,
                        movies = vodStreams.size,
                        liveCategories = liveCategories.size,
                        vodCategories = vodCategories.size
                    ),
                    isComplete = false,
                    error = error.message
                ))
                return@flow
            }

            val seriesCategories = seriesCategoriesResult.getOrNull() ?: emptyList()
            Log.d(TAG, "Loaded ${series.size} series, ${seriesCategories.size} categories")

            // Step 4: Save to cache
            emit(DataRefreshProgress(
                step = RefreshStep.SAVING,
                message = "Speichere Cache... (${liveStreams.size} Kanäle, ${vodStreams.size} Filme, ${series.size} Serien)",
                itemCounts = ItemCounts(
                    channels = liveStreams.size,
                    movies = vodStreams.size,
                    series = series.size,
                    liveCategories = liveCategories.size,
                    vodCategories = vodCategories.size,
                    seriesCategories = seriesCategories.size
                ),
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

            // Final success event
            emit(DataRefreshProgress(
                step = RefreshStep.SAVING,
                message = "Cache erfolgreich aktualisiert: ${liveStreams.size} Kanäle, ${vodStreams.size} Filme, ${series.size} Serien",
                itemCounts = ItemCounts(
                    channels = liveStreams.size,
                    movies = vodStreams.size,
                    series = series.size,
                    liveCategories = liveCategories.size,
                    vodCategories = vodCategories.size,
                    seriesCategories = seriesCategories.size
                ),
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
}
