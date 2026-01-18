package com.flintr.tv.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.flintr.tv.data.model.Episode
import com.flintr.tv.data.model.Favorite
import com.flintr.tv.data.model.FavoriteType
import com.flintr.tv.data.model.Season
import com.flintr.tv.data.model.Series
import com.flintr.tv.data.model.WatchProgress
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Represents the last interaction with an episode (either in-progress or completed)
 */
data class LastInteraction(
    val seasonNumber: Int,
    val episodeNumber: Int,
    val episodeId: Int,
    val progress: Float,  // 0.0 - 1.0
    val timestamp: Long   // Unix timestamp in milliseconds
)

/**
 * Represents the target episode for smart resume navigation
 */
data class SmartResumeTarget(
    val seasonNumber: Int,
    val episodeNumber: Int,
    val episodeId: Int
)

@Singleton
class WatchProgressService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi,
    private val favoritesRepository: FavoritesRepository,
    private val watchedHistoryService: WatchedHistoryService,
    private val ioDispatcher: CoroutineDispatcher
) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        "watch_progress_prefs",
        Context.MODE_PRIVATE
    )

    companion object {
        private const val KEY_WATCH_PROGRESS = "iptv_watch_progress_data"
        private const val TAG = "WatchProgressService"
        private const val MIN_POSITION_MS = 30_000L // 30 Sekunden
        private const val COMPLETION_THRESHOLD = 0.9 // 90%
        private const val KEY_PREFIX_MOVIE = "movie:"
        private const val KEY_PREFIX_SERIES = "series:"
    }

    private val mapType = Types.newParameterizedType(
        Map::class.java,
        String::class.java,
        WatchProgress::class.java
    )
    private val mapAdapter = moshi.adapter<Map<String, WatchProgress>>(mapType)
    private val progressAdapter = moshi.adapter(WatchProgress::class.java)
    
    // Cache for progress map to avoid repeated JSON parsing
    private var cachedProgressMap: Map<String, WatchProgress>? = null

    /**
     * Generates a storage key for the given content type and IDs
     */
    private fun generateKey(
        contentType: String,
        streamId: Int?,
        seriesId: Int?,
        episodeId: Int?
    ): String {
        return when (contentType) {
            "movie" -> {
                requireNotNull(streamId) { "streamId must not be null for movie" }
                "$KEY_PREFIX_MOVIE$streamId"
            }
            "series" -> {
                requireNotNull(seriesId) { "seriesId must not be null for series" }
                requireNotNull(episodeId) { "episodeId must not be null for series" }
                "$KEY_PREFIX_SERIES$seriesId:$episodeId"
            }
            else -> throw IllegalArgumentException("Unsupported contentType: $contentType")
        }
    }

    /**
     * Result of progress validation determining how to proceed with save operation.
     */
    private sealed class ProgressValidationResult {
        /** Progress should be skipped (invalid input or too short) */
        data class Skip(val reason: String) : ProgressValidationResult()
        /** Content is completed (>= 90%), should delete existing progress */
        object Completed : ProgressValidationResult()
        /** Valid progress that should be saved */
        data class Valid(val progressPercent: Double) : ProgressValidationResult()
    }

    /**
     * Validates progress parameters and determines the appropriate action.
     * Reduces cognitive complexity by centralizing validation logic.
     */
    private fun validateProgressParams(
        contentType: String,
        position: Long,
        duration: Long
    ): ProgressValidationResult {
        if (contentType == "live") {
            return ProgressValidationResult.Skip("live stream")
        }
        if (duration <= 0) {
            return ProgressValidationResult.Skip("invalid duration ($duration ms)")
        }
        if (position < 0) {
            return ProgressValidationResult.Skip("invalid position ($position ms)")
        }

        val progressPercent = position.toDouble() / duration.toDouble()
        
        if (progressPercent >= COMPLETION_THRESHOLD) {
            return ProgressValidationResult.Completed
        }
        if (position < MIN_POSITION_MS) {
            return ProgressValidationResult.Skip("position too short ($position ms < $MIN_POSITION_MS ms)")
        }

        return ProgressValidationResult.Valid(progressPercent)
    }

    /**
     * Automatically adds a movie to favorites when progress is saved.
     * Failures are logged but don't affect the progress save operation.
     */
    private suspend fun addMovieToFavoritesIfNeeded(
        contentType: String,
        streamId: Int?,
        title: String
    ) {
        if (contentType != "movie" || streamId == null) return
        
        try {
            val encodedName = URLEncoder.encode(title, "UTF-8")
            val favoriteId = "$KEY_PREFIX_MOVIE$streamId:$encodedName"
            val favorite = Favorite.create(
                id = favoriteId,
                type = FavoriteType.MOVIE,
                streamId = streamId,
                name = title
            )
            favoritesRepository.addFavorite(favorite)
            Log.d(TAG, "Automatically added movie to favorites: streamId=$streamId, title=$title")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to automatically add movie to favorites: streamId=$streamId, title=$title", e)
        }
    }

    /**
     * Saves watch progress for a movie or series episode
     * 
     * @param contentType "movie" or "series"
     * @param streamId Stream ID for movies
     * @param seriesId Series ID for series episodes
     * @param episodeId Episode ID for series episodes
     * @param position Current playback position in milliseconds
     * @param duration Total duration in milliseconds
     * @param title Title of the content
     * @return true if saved successfully, false otherwise
     */
    suspend fun saveProgress(
        contentType: String,
        streamId: Int?,
        seriesId: Int?,
        episodeId: Int?,
        position: Long,
        duration: Long,
        title: String
    ): Boolean = withContext(ioDispatcher) {
        try {
            val logContext = "contentType: $contentType, streamId: $streamId, seriesId: $seriesId, episodeId: $episodeId"
            
            when (val validation = validateProgressParams(contentType, position, duration)) {
                is ProgressValidationResult.Skip -> {
                    Log.d(TAG, "Skipping progress save: ${validation.reason} for $logContext")
                    return@withContext false
                }
                is ProgressValidationResult.Completed -> {
                    Log.d(TAG, "Progress >= 90%, marking as watched and deleting progress for $logContext")
                    // Mark as watched BEFORE deleting progress
                    watchedHistoryService.markAsWatched(contentType, streamId, seriesId, episodeId)
                    return@withContext deleteProgress(contentType, streamId, seriesId, episodeId)
                }
                is ProgressValidationResult.Valid -> {
                    val key = generateKey(contentType, streamId, seriesId, episodeId)
                    Log.d(TAG, "Saving progress: key=$key, position=${position}ms, duration=${duration}ms, progress=${(validation.progressPercent * 100).toInt()}%, title=$title")
                    
                    persistProgress(key, position, duration, title)
                    addMovieToFavoritesIfNeeded(contentType, streamId, title)
                    
                    Log.d(TAG, "Progress saved successfully: key=$key, position=${position}ms")
                    true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving progress for contentType: $contentType, streamId: $streamId, seriesId: $seriesId, episodeId: $episodeId", e)
            false
        }
    }

    /**
     * Persists progress to SharedPreferences and updates cache.
     */
    private fun persistProgress(key: String, position: Long, duration: Long, title: String) {
        val progressMap = loadProgressMap()
        val progress = WatchProgress(
            position = position,
            duration = duration,
            lastWatched = Instant.now().toString(),
            title = title
        )
        val updatedMap = progressMap.toMutableMap()
        updatedMap[key] = progress
        
        val json = mapAdapter.toJson(updatedMap)
        sharedPreferences.edit().putString(KEY_WATCH_PROGRESS, json).apply()
        cachedProgressMap = updatedMap
    }

    /**
     * Retrieves saved watch progress for a movie or series episode
     * 
     * @param contentType "movie" or "series"
     * @param streamId Stream ID for movies
     * @param seriesId Series ID for series episodes
     * @param episodeId Episode ID for series episodes
     * @return Saved position in milliseconds, or null if not found
     */
    suspend fun getProgress(
        contentType: String,
        streamId: Int?,
        seriesId: Int?,
        episodeId: Int?
    ): Long? = withContext(ioDispatcher) {
        try {
            val key = generateKey(contentType, streamId, seriesId, episodeId)
            Log.d(TAG, "Getting progress: key=$key, contentType: $contentType, streamId: $streamId, seriesId: $seriesId, episodeId: $episodeId")

            val progressMap = loadProgressMap()
            val progress = progressMap[key]

            if (progress == null) {
                Log.d(TAG, "No progress found for key: $key")
                return@withContext null
            }

            Log.d(TAG, "Found progress: key=$key, position=${progress.position}ms (${progress.position / 1000}s), duration=${progress.duration}ms (${progress.duration / 1000}s), lastWatched=${progress.lastWatched}, title=${progress.title}")
            progress.position
        } catch (e: Exception) {
            Log.e(TAG, "Error getting progress for contentType: $contentType, streamId: $streamId, seriesId: $seriesId, episodeId: $episodeId", e)
            null
        }
    }

    /**
     * Deletes watch progress for a movie or series episode
     */
    private suspend fun deleteProgress(
        contentType: String,
        streamId: Int?,
        seriesId: Int?,
        episodeId: Int?
    ): Boolean = withContext(ioDispatcher) {
        try {
            val key = generateKey(contentType, streamId, seriesId, episodeId)
            Log.d(TAG, "Deleting progress: key=$key")

            val progressMap = loadProgressMap()
            val progress = progressMap[key]
            val updatedMap = progressMap.toMutableMap()
            updatedMap.remove(key)

            val json = mapAdapter.toJson(updatedMap)
            sharedPreferences.edit().putString(KEY_WATCH_PROGRESS, json).apply()
            
            // Update cache
            cachedProgressMap = updatedMap

            // Automatically remove movie from favorites when progress is deleted
            // Reconstruct the favorite ID directly (same format as addMovieToFavoritesIfNeeded)
            if (contentType == "movie" && streamId != null && progress != null) {
                try {
                    val encodedName = URLEncoder.encode(progress.title, "UTF-8")
                    val favoriteId = "$KEY_PREFIX_MOVIE$streamId:$encodedName"
                    favoritesRepository.removeFavorite(favoriteId)
                    Log.d(TAG, "Automatically removed movie from favorites: streamId=$streamId, favoriteId=$favoriteId")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to automatically remove movie from favorites: streamId=$streamId", e)
                    // Don't fail the progress deletion if favorite removal fails
                }
            }

            Log.d(TAG, "Progress deleted successfully: key=$key")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting progress for contentType: $contentType, streamId: $streamId, seriesId: $seriesId, episodeId: $episodeId", e)
            false
        }
    }

    /**
     * Loads the progress map from SharedPreferences or cache.
     * Uses cached version if available to avoid repeated JSON parsing.
     */
    private fun loadProgressMap(): Map<String, WatchProgress> {
        // Return cached version if available
        if (cachedProgressMap != null) {
            return cachedProgressMap!!
        }
        
        // Load from SharedPreferences and cache it
        return try {
            val json = sharedPreferences.getString(KEY_WATCH_PROGRESS, null)
            val map = if (json.isNullOrBlank()) {
                emptyMap()
            } else {
                mapAdapter.fromJson(json) ?: emptyMap()
            }
            cachedProgressMap = map
            map
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing progress JSON, returning empty map", e)
            val emptyMap = emptyMap<String, WatchProgress>()
            cachedProgressMap = emptyMap
            emptyMap
        }
    }

    /**
     * Gets all in-progress items (movies and episodes with progress > 0% and < 90%)
     */
    suspend fun getAllInProgress(): Map<String, WatchProgress> = withContext(ioDispatcher) {
        loadProgressMap()
    }

    /**
     * Gets all in-progress movies (keys starting with "movie:")
     */
    suspend fun getInProgressMovies(): Map<String, WatchProgress> = withContext(ioDispatcher) {
        loadProgressMap().filterKeys { it.startsWith(KEY_PREFIX_MOVIE) }
    }

    /**
     * Gets all in-progress episodes (keys starting with "series:")
     */
    suspend fun getInProgressEpisodes(): Map<String, WatchProgress> = withContext(ioDispatcher) {
        loadProgressMap().filterKeys { it.startsWith(KEY_PREFIX_SERIES) }
    }

    /**
     * Gets the total count of in-progress items (movies + episodes)
     */
    suspend fun getInProgressCount(): Int = withContext(ioDispatcher) {
        val progressMap = loadProgressMap()
        val movies = progressMap.count { it.key.startsWith(KEY_PREFIX_MOVIE) }
        val episodes = progressMap.count { it.key.startsWith(KEY_PREFIX_SERIES) }
        movies + episodes
    }

    /**
     * Gets the count of in-progress movies only
     */
    suspend fun getInProgressMoviesCount(): Int = withContext(ioDispatcher) {
        val progressMap = loadProgressMap()
        progressMap.count { it.key.startsWith(KEY_PREFIX_MOVIE) }
    }

    /**
     * Gets the count of in-progress episodes only
     */
    suspend fun getInProgressEpisodesCount(): Int = withContext(ioDispatcher) {
        val progressMap = loadProgressMap()
        progressMap.count { it.key.startsWith(KEY_PREFIX_SERIES) }
    }

    /**
     * Checks if an item is watched (delegates to WatchedHistoryService)
     */
    suspend fun isWatched(
        contentType: String,
        streamId: Int?,
        seriesId: Int?,
        episodeId: Int?
    ): Boolean = watchedHistoryService.isWatched(contentType, streamId, seriesId, episodeId)

    /**
     * Gets all watched episode IDs for a specific series (batch loading, delegates to WatchedHistoryService)
     */
    suspend fun getWatchedEpisodeIds(seriesId: Int): Set<Int> = watchedHistoryService.getWatchedEpisodeIds(seriesId)

    /**
     * Gets all watched VOD IDs, optionally filtered by category (batch loading, delegates to WatchedHistoryService)
     */
    suspend fun getWatchedVodIds(categoryId: String? = null): Set<Int> = watchedHistoryService.getWatchedVodIds(categoryId)

    /**
     * Creates a map of episodeId -> (Season, Episode) for efficient lookup
     * Seasons are sorted once to avoid repeated sorting
     */
    private fun createEpisodeMap(series: Series): Map<Int, Pair<Season, Episode>> {
        val sortedSeasons = series.seasons?.sortedBy { it.seasonNumber } ?: return emptyMap()
        
        return sortedSeasons.flatMap { season ->
            season.episodes?.map { episode ->
                episode.id to (season to episode)
            } ?: emptyList()
        }.toMap()
    }

    /**
     * Creates a LastInteraction from a WatchProgress entry
     * Returns null if the episode is not found or timestamp parsing fails
     */
    private fun createInteractionFromProgress(
        episodeId: Int,
        progress: WatchProgress,
        episodeMap: Map<Int, Pair<Season, Episode>>
    ): LastInteraction? {
        val episodeInfo = episodeMap[episodeId] ?: return null
        val (season, episode) = episodeInfo
        
        if (progress.duration <= 0L) {
            Log.w(TAG, "Cannot calculate progress for episodeId=${episodeId} due to invalid duration: ${progress.duration}")
            return null
        }
        val progressPercent = progress.position.toFloat() / progress.duration.toFloat()
        val timestamp = try {
            Instant.parse(progress.lastWatched).toEpochMilli()
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing lastWatched timestamp: ${progress.lastWatched}", e)
            return null
        }
        
        val episodeNumber = episode.episodeNum ?: episode.id
        
        return LastInteraction(
            seasonNumber = season.seasonNumber,
            episodeNumber = episodeNumber,
            episodeId = episodeId,
            progress = progressPercent,
            timestamp = timestamp
        )
    }

    /**
     * Creates a LastInteraction from a WatchedHistory entry
     * Returns null if the episode is not found
     */
    private fun createInteractionFromWatched(
        episodeId: Int,
        timestamp: Long,
        episodeMap: Map<Int, Pair<Season, Episode>>
    ): LastInteraction? {
        val episodeInfo = episodeMap[episodeId] ?: return null
        val (season, episode) = episodeInfo
        
        val episodeNumber = episode.episodeNum ?: episode.id
        
        return LastInteraction(
            seasonNumber = season.seasonNumber,
            episodeNumber = episodeNumber,
            episodeId = episodeId,
            progress = 1.0f, // Completed episodes are 100%
            timestamp = timestamp
        )
    }

    /**
     * Finds the latest progress interaction from all in-progress episodes
     */
    private fun findLatestProgressInteraction(
        seriesId: Int,
        progressMap: Map<String, WatchProgress>,
        episodeMap: Map<Int, Pair<Season, Episode>>
    ): LastInteraction? {
        val prefix = "$KEY_PREFIX_SERIES$seriesId:"
        val progressEntries = progressMap.filterKeys { it.startsWith(prefix) }
        
        var latestProgress: LastInteraction? = null
        
        for ((key, progress) in progressEntries) {
            val episodeId = key.removePrefix(prefix).toIntOrNull() ?: continue
            val interaction = createInteractionFromProgress(episodeId, progress, episodeMap) ?: continue
            
            if (latestProgress == null || interaction.timestamp > latestProgress.timestamp) {
                latestProgress = interaction
            }
        }
        
        return latestProgress
    }

    /**
     * Gets the last interaction with any episode of the series
     * Checks both WatchProgress (in-progress) and WatchedHistory (completed) storage systems
     * Returns the most recent interaction based on timestamp
     */
    suspend fun getLastInteraction(seriesId: Int, series: Series): LastInteraction? = withContext(ioDispatcher) {
        try {
            if (series.seasons == null) return@withContext null
            
            // Sort seasons once and create episode map for efficient lookup
            val episodeMap = createEpisodeMap(series)
            
            // Check WatchProgress for in-progress episodes
            val progressMap = loadProgressMap()
            val latestProgress = findLatestProgressInteraction(seriesId, progressMap, episodeMap)
            
            // Check WatchedHistory for completed episodes
            val latestWatched = watchedHistoryService.getLatestWatchedEpisode(seriesId)
            val latestWatchedInteraction = if (latestWatched != null) {
                val (episodeId, timestamp) = latestWatched
                createInteractionFromWatched(episodeId, timestamp, episodeMap)
            } else {
                null
            }
            
            // Return the most recent interaction
            listOfNotNull(latestProgress, latestWatchedInteraction).maxByOrNull { it.timestamp }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting last interaction for seriesId: $seriesId", e)
            null
        }
    }

    /**
     * Finds the next episode after the given episode
     * Handles season transitions (e.g., S01E10 -> S02E01)
     * Returns null if no next episode exists
     */
    private fun findNextEpisode(
        series: Series,
        currentSeasonNum: Int,
        currentEpisodeNum: Int
    ): SmartResumeTarget? {
        val sortedSeasons = series.seasons?.sortedBy { it.seasonNumber } ?: return null
        
        // Find current season
        val currentSeasonIndex = sortedSeasons.indexOfFirst { it.seasonNumber == currentSeasonNum }
        if (currentSeasonIndex < 0) return null
        
        val currentSeason = sortedSeasons[currentSeasonIndex]
        val episodes = currentSeason.episodes ?: return null
        
        // Try to find next episode in same season
        val currentEpisodeIndex = episodes.indexOfFirst { 
            (it.episodeNum ?: it.id) == currentEpisodeNum 
        }
        
        if (currentEpisodeIndex >= 0 && currentEpisodeIndex < episodes.size - 1) {
            // Next episode in same season
            val nextEpisode = episodes[currentEpisodeIndex + 1]
            val nextEpisodeNum = nextEpisode.episodeNum ?: nextEpisode.id
            return SmartResumeTarget(
                seasonNumber = currentSeasonNum,
                episodeNumber = nextEpisodeNum,
                episodeId = nextEpisode.id
            )
        }
        
        // Check if there's a next season
        if (currentSeasonIndex < sortedSeasons.size - 1) {
            val nextSeason = sortedSeasons[currentSeasonIndex + 1]
            val nextSeasonEpisodes = nextSeason.episodes ?: return null
            
            if (nextSeasonEpisodes.isNotEmpty()) {
                // First episode of next season
                val firstEpisode = nextSeasonEpisodes[0]
                val firstEpisodeNum = firstEpisode.episodeNum ?: firstEpisode.id
                return SmartResumeTarget(
                    seasonNumber = nextSeason.seasonNumber,
                    episodeNumber = firstEpisodeNum,
                    episodeId = firstEpisode.id
                )
            }
        }
        
        // No next episode found
        return null
    }

    /**
     * Gets the smart resume target for a series
     * Implements the three cases:
     * - Case A: Last episode is in-progress (< 90%) -> resume this episode
     * - Case B: Last episode is completed (>= 90%) -> navigate to next episode
     * - Case C: No history or no next episode -> navigate to S01E01
     */
    suspend fun getSmartResumeTarget(seriesId: Int, series: Series): SmartResumeTarget? = withContext(ioDispatcher) {
        try {
            val sortedSeasons = series.seasons?.sortedBy { it.seasonNumber } ?: return@withContext null
            
            // Edge case: No episodes available
            val hasEpisodes = sortedSeasons.any { !it.episodes.isNullOrEmpty() }
            if (!hasEpisodes) {
                Log.d(TAG, "getSmartResumeTarget: seriesId=$seriesId, no episodes available")
                return@withContext null
            }
            
            // Get first episode for fallback (Case C)
            val firstSeason = sortedSeasons.firstOrNull() ?: return@withContext null
            val firstEpisodes = firstSeason.episodes ?: return@withContext null
            if (firstEpisodes.isEmpty()) return@withContext null
            
            val firstEpisode = firstEpisodes[0]
            val firstEpisodeNum = firstEpisode.episodeNum ?: firstEpisode.id
            val fallbackTarget = SmartResumeTarget(
                seasonNumber = firstSeason.seasonNumber,
                episodeNumber = firstEpisodeNum,
                episodeId = firstEpisode.id
            )
            
            // Get last interaction
            val lastInteraction = getLastInteraction(seriesId, series) ?: return@withContext fallbackTarget
            
            // Case A: Episode is in-progress (< 90%)
            if (lastInteraction.progress < COMPLETION_THRESHOLD) {
                Log.d(TAG, "getSmartResumeTarget: Case A - Resume episode S${lastInteraction.seasonNumber}E${lastInteraction.episodeNumber}")
                return@withContext SmartResumeTarget(
                    seasonNumber = lastInteraction.seasonNumber,
                    episodeNumber = lastInteraction.episodeNumber,
                    episodeId = lastInteraction.episodeId
                )
            }
            
            // Case B: Episode is completed (>= 90%) -> find next episode
            val nextEpisode = findNextEpisode(
                series = series,
                currentSeasonNum = lastInteraction.seasonNumber,
                currentEpisodeNum = lastInteraction.episodeNumber
            )
            
            if (nextEpisode != null) {
                Log.d(TAG, "getSmartResumeTarget: Case B - Next episode S${nextEpisode.seasonNumber}E${nextEpisode.episodeNumber}")
                return@withContext nextEpisode
            }
            
            // Case C: No next episode available -> fallback to first episode
            Log.d(TAG, "getSmartResumeTarget: Case C - Fallback to S${fallbackTarget.seasonNumber}E${fallbackTarget.episodeNumber}")
            return@withContext fallbackTarget
        } catch (e: Exception) {
            Log.e(TAG, "Error getting smart resume target for seriesId: $seriesId", e)
            null
        }
    }
}
