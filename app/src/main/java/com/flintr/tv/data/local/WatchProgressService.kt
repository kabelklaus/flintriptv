package com.flintr.tv.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.flintr.tv.data.model.WatchProgress
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchProgressService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi
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
    }

    private val mapType = Types.newParameterizedType(
        Map::class.java,
        String::class.java,
        WatchProgress::class.java
    )
    private val mapAdapter = moshi.adapter<Map<String, WatchProgress>>(mapType)
    private val progressAdapter = moshi.adapter(WatchProgress::class.java)

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
                "movie:$streamId"
            }
            "series" -> {
                requireNotNull(seriesId) { "seriesId must not be null for series" }
                requireNotNull(episodeId) { "episodeId must not be null for series" }
                "series:$seriesId:$episodeId"
            }
            else -> throw IllegalArgumentException("Unsupported contentType: $contentType")
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
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Validation: No live streams
            if (contentType == "live") {
                Log.d(TAG, "Skipping progress save for live stream (contentType: $contentType)")
                return@withContext false
            }

            // Validation: Invalid duration
            if (duration <= 0) {
                Log.d(TAG, "Skipping progress save: invalid duration ($duration ms) for contentType: $contentType, streamId: $streamId, seriesId: $seriesId, episodeId: $episodeId")
                return@withContext false
            }

            // Validation: Position must be valid
            if (position < 0) {
                Log.d(TAG, "Skipping progress save: invalid position ($position ms) for contentType: $contentType, streamId: $streamId, seriesId: $seriesId, episodeId: $episodeId")
                return@withContext false
            }

            // Calculate progress percentage
            val progressPercent = position.toDouble() / duration.toDouble()

            // If progress >= 90%, delete the progress entry (marked as completed)
            if (progressPercent >= COMPLETION_THRESHOLD) {
                Log.d(TAG, "Progress >= 90% ($progressPercent), deleting progress for contentType: $contentType, streamId: $streamId, seriesId: $seriesId, episodeId: $episodeId")
                return@withContext deleteProgress(contentType, streamId, seriesId, episodeId)
            }

            // Validation: Position must be at least 30 seconds
            if (position < MIN_POSITION_MS) {
                Log.d(TAG, "Skipping progress save: position too short ($position ms < $MIN_POSITION_MS ms) for contentType: $contentType, streamId: $streamId, seriesId: $seriesId, episodeId: $episodeId")
                return@withContext false
            }

            val key = generateKey(contentType, streamId, seriesId, episodeId)
            Log.d(TAG, "Saving progress: key=$key, position=${position}ms (${position / 1000}s), duration=${duration}ms (${duration / 1000}s), progress=${(progressPercent * 100).toInt()}%, title=$title")

            // Load existing progress map
            val progressMap = loadProgressMap()

            // Create new progress entry
            val progress = WatchProgress(
                position = position,
                duration = duration,
                lastWatched = Instant.now().toString(),
                title = title
            )

            // Update map
            val updatedMap = progressMap.toMutableMap()
            updatedMap[key] = progress

            // Save to SharedPreferences
            val json = mapAdapter.toJson(updatedMap)
            sharedPreferences.edit().putString(KEY_WATCH_PROGRESS, json).apply()

            Log.d(TAG, "Progress saved successfully: key=$key, position=${position}ms")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving progress for contentType: $contentType, streamId: $streamId, seriesId: $seriesId, episodeId: $episodeId", e)
            false
        }
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
    ): Long? = withContext(Dispatchers.IO) {
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
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val key = generateKey(contentType, streamId, seriesId, episodeId)
            Log.d(TAG, "Deleting progress: key=$key")

            val progressMap = loadProgressMap()
            val updatedMap = progressMap.toMutableMap()
            updatedMap.remove(key)

            val json = mapAdapter.toJson(updatedMap)
            sharedPreferences.edit().putString(KEY_WATCH_PROGRESS, json).apply()

            Log.d(TAG, "Progress deleted successfully: key=$key")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting progress for contentType: $contentType, streamId: $streamId, seriesId: $seriesId, episodeId: $episodeId", e)
            false
        }
    }

    /**
     * Loads the progress map from SharedPreferences
     */
    private fun loadProgressMap(): Map<String, WatchProgress> {
        return try {
            val json = sharedPreferences.getString(KEY_WATCH_PROGRESS, null)
            if (json.isNullOrBlank()) {
                emptyMap()
            } else {
                mapAdapter.fromJson(json) ?: emptyMap()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing progress JSON, returning empty map", e)
            emptyMap()
        }
    }
}
