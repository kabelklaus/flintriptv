package com.flintr.tv.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.flintr.tv.data.model.WatchedItem
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchedHistoryService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        "watched_history_prefs",
        Context.MODE_PRIVATE
    )

    companion object {
        private const val KEY_WATCHED_HISTORY = "iptv_watched_history"
        private const val TAG = "WatchedHistoryService"
        private const val KEY_PREFIX_MOVIE = "movie:"
        private const val KEY_PREFIX_SERIES = "series:"
    }

    private val mapType = Types.newParameterizedType(
        Map::class.java,
        String::class.java,
        WatchedItem::class.java
    )
    private val mapAdapter = moshi.adapter<Map<String, WatchedItem>>(mapType)
    
    // Cache for watched items map to avoid repeated JSON parsing
    private var cachedWatchedMap: Map<String, WatchedItem>? = null
    
    // Mutex for thread-safe access to cachedWatchedMap and SharedPreferences
    private val mutex = Mutex()

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
     * Loads the watched items map from SharedPreferences or cache.
     */
    private fun loadWatchedMap(): Map<String, WatchedItem> {
        // Return cached version if available
        val cached = cachedWatchedMap
        if (cached != null) {
            return cached
        }
        
        // Load from SharedPreferences and cache it
        return try {
            val json = sharedPreferences.getString(KEY_WATCHED_HISTORY, null)
            val map = if (json.isNullOrBlank()) {
                emptyMap()
            } else {
                mapAdapter.fromJson(json) ?: emptyMap()
            }
            cachedWatchedMap = map
            map
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing watched history JSON, returning empty map", e)
            val emptyMap = emptyMap<String, WatchedItem>()
            cachedWatchedMap = emptyMap
            emptyMap
        }
    }

    /**
     * Marks an item as watched
     */
    suspend fun markAsWatched(
        contentType: String,
        streamId: Int?,
        seriesId: Int?,
        episodeId: Int?
    ): Boolean = withContext(ioDispatcher) {
        mutex.withLock {
            try {
                val key = generateKey(contentType, streamId, seriesId, episodeId)
                Log.d(TAG, "Marking as watched: key=$key, contentType: $contentType, streamId: $streamId, seriesId: $seriesId, episodeId: $episodeId")

                val watchedMap = loadWatchedMap()
                val watchedItem = WatchedItem(
                    contentType = contentType,
                    streamId = streamId,
                    seriesId = seriesId,
                    episodeId = episodeId,
                    watchedAt = Instant.now().toString()
                )
                
                val updatedMap = watchedMap.toMutableMap()
                updatedMap[key] = watchedItem
                
                val json = mapAdapter.toJson(updatedMap)
                sharedPreferences.edit {
                    putString(KEY_WATCHED_HISTORY, json)
                }
                cachedWatchedMap = updatedMap
                
                Log.d(TAG, "Marked as watched successfully: key=$key")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error marking as watched for contentType: $contentType, streamId: $streamId, seriesId: $seriesId, episodeId: $episodeId", e)
                false
            }
        }
    }

    /**
     * Checks if an item is watched
     */
    suspend fun isWatched(
        contentType: String,
        streamId: Int?,
        seriesId: Int?,
        episodeId: Int?
    ): Boolean = withContext(ioDispatcher) {
        mutex.withLock {
            try {
                val key = generateKey(contentType, streamId, seriesId, episodeId)
                val watchedMap = loadWatchedMap()
                val isWatched = watchedMap.containsKey(key)
                Log.d(TAG, "isWatched check: key=$key, result=$isWatched")
                return@withContext isWatched
            } catch (e: Exception) {
                Log.e(TAG, "Error checking if watched for contentType: $contentType, streamId: $streamId, seriesId: $seriesId, episodeId: $episodeId", e)
                false
            }
        }
    }

    /**
     * Gets all watched episode IDs for a specific series (batch loading)
     */
    suspend fun getWatchedEpisodeIds(seriesId: Int): Set<Int> = withContext(ioDispatcher) {
        mutex.withLock {
            try {
                val watchedMap = loadWatchedMap()
                val prefix = "$KEY_PREFIX_SERIES$seriesId:"
                val episodeIds = watchedMap
                    .filterKeys { it.startsWith(prefix) }
                    .mapNotNull { (key, _) ->
                        key.removePrefix(prefix).toIntOrNull()
                    }
                    .toSet()
                
                Log.d(TAG, "getWatchedEpisodeIds: seriesId=$seriesId, found ${episodeIds.size} watched episodes")
                return@withContext episodeIds
            } catch (e: Exception) {
                Log.e(TAG, "Error getting watched episode IDs for seriesId: $seriesId", e)
                emptySet()
            }
        }
    }

    /**
     * Gets all watched VOD IDs, optionally filtered by category
     * Note: categoryId filtering requires additional data from repository, so this returns all watched VODs
     */
    suspend fun getWatchedVodIds(categoryId: String? = null): Set<Int> = withContext(ioDispatcher) {
        mutex.withLock {
            try {
                val watchedMap = loadWatchedMap()
                val vodIds = watchedMap
                    .filterKeys { it.startsWith(KEY_PREFIX_MOVIE) }
                    .mapNotNull { (key, _) ->
                        key.removePrefix(KEY_PREFIX_MOVIE).toIntOrNull()
                    }
                    .toSet()
                
                Log.d(TAG, "getWatchedVodIds: categoryId=$categoryId, found ${vodIds.size} watched VODs")
                // Note: categoryId filtering would require repository access, so we return all for now
                // The calling code can filter by category if needed
                return@withContext vodIds
            } catch (e: Exception) {
                Log.e(TAG, "Error getting watched VOD IDs", e)
                emptySet()
            }
        }
    }

    /**
     * Gets the latest watched episode for a series with its timestamp
     * Returns null if no episodes have been watched for this series
     */
    suspend fun getLatestWatchedEpisode(seriesId: Int): Pair<Int, Long>? = withContext(ioDispatcher) {
        mutex.withLock {
            try {
                val watchedMap = loadWatchedMap()
                val prefix = "$KEY_PREFIX_SERIES$seriesId:"
                
                val latest = watchedMap
                    .filterKeys { it.startsWith(prefix) }
                    .mapNotNull { (key, item) ->
                        val episodeId = key.removePrefix(prefix).toIntOrNull() ?: return@mapNotNull null
                        val timestamp = try {
                            Instant.parse(item.watchedAt).toEpochMilli()
                        } catch (e: Exception) {
                            Log.w(TAG, "Error parsing watchedAt timestamp for episode $episodeId: ${item.watchedAt}", e)
                            return@mapNotNull null
                        }
                        episodeId to timestamp
                    }
                    .maxByOrNull { it.second }
                
                if (latest != null) {
                    Log.d(TAG, "getLatestWatchedEpisode: seriesId=$seriesId, latest episodeId=${latest.first}, timestamp=${latest.second}")
                } else {
                    Log.d(TAG, "getLatestWatchedEpisode: seriesId=$seriesId, no watched episodes found")
                }
                return@withContext latest
            } catch (e: Exception) {
                Log.e(TAG, "Error getting latest watched episode for seriesId: $seriesId", e)
                null
            }
        }
    }
}
