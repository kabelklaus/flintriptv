package com.flintr.tv.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.flintr.tv.data.model.WatchedItem
import com.flintr.tv.data.repository.UserRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import com.flintr.tv.utils.error.reportError
import com.flintr.tv.utils.error.reportWarning

@Singleton
class WatchedHistoryService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi,
    private val userRepository: UserRepository,
    private val credentialsRepository: CredentialsRepository,
    @Named("WatchedHistory") private val syncManager: FirestoreSyncManager,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    // SharedPreferences only for migration flag and reading old data
    private val migrationPrefs: SharedPreferences = context.getSharedPreferences(
        "watched_history_prefs",
        Context.MODE_PRIVATE
    )

    companion object {
        private const val KEY_WATCHED_HISTORY = "iptv_watched_history"
        private const val KEY_MIGRATION_COMPLETE = "watched_history_migrated"
        private const val TAG = "WatchedHistoryService"
        private const val KEY_PREFIX_MOVIE = "movie:"
        private const val KEY_PREFIX_SERIES = "series:"

        internal fun seriesEpisodeKey(seriesId: Int, episodeId: Int): String {
            return "$KEY_PREFIX_SERIES$seriesId:$episodeId"
        }

        internal fun buildSeriesWatchedEntries(
            seriesId: Int,
            episodeIds: Collection<Int>,
            name: String?,
            genre: String?,
            categoryName: String?,
            watchedAt: String
        ): Map<String, WatchedItem> {
            return episodeIds.associate { episodeId ->
                seriesEpisodeKey(seriesId, episodeId) to WatchedItem(
                    contentType = "series",
                    streamId = null,
                    seriesId = seriesId,
                    episodeId = episodeId,
                    name = name,
                    genre = genre,
                    categoryName = categoryName,
                    watchedAt = watchedAt
                )
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // StateFlow for reactive state management
    private val _watchedHistory = MutableStateFlow<Map<String, WatchedItem>>(emptyMap())
    val watchedHistory: StateFlow<Map<String, WatchedItem>> = _watchedHistory.asStateFlow()

    // Mutex to prevent concurrent loadFromFirestore() calls
    private val loadMutex = Mutex()
    
    // Mutex to prevent concurrent StateFlow updates
    private val updateMutex = Mutex()

    private val mapType = Types.newParameterizedType(
        Map::class.java,
        String::class.java,
        WatchedItem::class.java
    )
    private val mapAdapter = moshi.adapter<Map<String, WatchedItem>>(mapType)

    init {
        // React to credential changes (e.g., user login/logout or user switch)
        // This also handles the initial load since credentialsFlow emits the current value on collection
        serviceScope.launch {
            credentialsRepository.credentialsFlow.collect { credentials ->
                val username = credentials?.username
                if (!username.isNullOrBlank()) {
                    loadFromFirestore()
                } else {
                    // Clear state when logged out
                    _watchedHistory.value = emptyMap()
                    Log.d(TAG, "Credentials cleared, resetting watched history")
                }
            }
        }
    }

    /**
     * Loads watched history from Firestore and performs migration if needed.
     * Uses mutex to prevent concurrent loads.
     */
    private suspend fun loadFromFirestore() {
        loadMutex.withLock {
            val username = credentialsRepository.getCredentials()?.username
            if (username.isNullOrBlank()) {
                Log.d(TAG, "No username available, skipping Firestore load due to missing credentials")
                _watchedHistory.value = emptyMap()
                return
            }

            // Check if migration is needed
            val migrationComplete = migrationPrefs.getBoolean(KEY_MIGRATION_COMPLETE, false)
            if (!migrationComplete) {
                performMigration(username)
            }

            // Load from Firestore
            val result = userRepository.loadWatchedHistory(username)
            result.fold(
                onSuccess = { history ->
                    _watchedHistory.value = history
                    syncManager.updateLastSyncTimestamp()
                    Log.d(TAG, "Loaded watched history from Firestore: ${history.size} total")
                    // Log entries with name/genre/categoryName for debugging
                    val entriesWithMetadata = history.filter { (_, item) -> item.name != null || item.genre != null || item.categoryName != null }
                    if (entriesWithMetadata.isNotEmpty()) {
                        Log.d(TAG, "Entries with name/genre/categoryName: ${entriesWithMetadata.size}")
                        entriesWithMetadata.forEach { (key, item) ->
                            Log.d(TAG, "  Entry $key: name=${item.name}, genre=${item.genre}, categoryName=${item.categoryName}")
                        }
                    } else {
                        Log.d(TAG, "No entries with name/genre/categoryName found (all entries have null values)")
                    }
                },
                onFailure = { exception ->
                    reportError(TAG, "Failed to load watched history from Firestore", exception)
                    // Keep current state or use empty map
                    if (_watchedHistory.value.isEmpty()) {
                        Log.e(TAG, "State is empty after failed Firestore load - user data may be unavailable")
                        _watchedHistory.value = emptyMap()
                    }
                }
            )
        }
    }

    /**
     * Synchronizes watched history from Firestore if enough time has passed since last sync.
     * This prevents excessive Firestore reads when users switch tabs quickly.
     * 
     * @return true if sync was performed, false if skipped due to cooldown
     */
    suspend fun syncIfNeeded(): Boolean {
        return syncManager.syncIfNeeded {
            loadFromFirestore()
        }
    }

    /**
     * Performs migration from local SharedPreferences to Firestore.
     */
    private suspend fun performMigration(username: String) {
        Log.d(TAG, "Starting watched history migration from local storage to Firestore")
        
        try {
            // Load old data from SharedPreferences
            val localData = loadLocalData()
            
            if (localData.isNotEmpty()) {
                // Migrate to Firestore
                val migrationResult = userRepository.migrateWatchedHistoryFromLocal(username, localData)
                
                migrationResult.fold(
                    onSuccess = {
                        // Mark migration as complete
                        migrationPrefs.edit {
                            putBoolean(KEY_MIGRATION_COMPLETE, true)
                        }
                        
                        // Clear old local data
                        clearLocalData()
                        
                        Log.d(TAG, "Migration completed successfully")
                    },
                    onFailure = { exception ->
                        reportError(TAG, "Migration failed, will retry on next app start", exception)
                        // Don't mark as complete, so it will retry
                    }
                )
            } else {
                // No local data to migrate, mark as complete
                migrationPrefs.edit {
                    putBoolean(KEY_MIGRATION_COMPLETE, true)
                }
                Log.d(TAG, "No local data to migrate")
            }
        } catch (e: Exception) {
            reportError(TAG, "Error during migration", e)
            // Don't mark as complete, so it will retry
        }
    }

    /**
     * Loads old data from SharedPreferences for migration.
     */
    private fun loadLocalData(): Map<String, WatchedItem> {
        return try {
            val json = migrationPrefs.getString(KEY_WATCHED_HISTORY, null)
            if (json.isNullOrBlank()) {
                emptyMap()
            } else {
                mapAdapter.fromJson(json) ?: emptyMap()
            }
        } catch (e: Exception) {
            reportError(TAG, "Error loading local data for migration", e)
            emptyMap()
        }
    }

    /**
     * Clears old local data after successful migration.
     */
    private fun clearLocalData() {
        try {
            migrationPrefs.edit {
                remove(KEY_WATCHED_HISTORY)
            }
            Log.d(TAG, "Cleared old local data after migration")
        } catch (e: Exception) {
            reportError(TAG, "Error clearing local data", e)
        }
    }

    /**
     * Saves watched history to Firestore asynchronously.
     */
    private fun saveToFirestore(history: Map<String, WatchedItem>) {
        val username = credentialsRepository.getCredentials()?.username
        if (username.isNullOrBlank()) {
            Log.w(TAG, "Cannot save to Firestore: no username available")
            return
        }

        serviceScope.launch {
            val result = userRepository.saveWatchedHistory(username, history)
            result.fold(
                onSuccess = {
                    Log.d(TAG, "Successfully saved watched history to Firestore (${history.size} entries)")
                    // Log details of entries with name/genre/categoryName for debugging
                    history.forEach { (key, item) ->
                        if (item.name != null || item.genre != null || item.categoryName != null) {
                            Log.d(TAG, "  Entry $key: name=${item.name}, genre=${item.genre}, categoryName=${item.categoryName}")
                        }
                    }
                },
                onFailure = { exception ->
                    reportError(TAG, "Failed to save watched history to Firestore", exception)
                    // StateFlow already updated, so UI will reflect the change
                    // Firestore will sync when connection is restored
                }
            )
        }
    }

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
     * Marks an item as watched (optimistic update).
     */
    suspend fun markAsWatched(
        contentType: String,
        streamId: Int?,
        seriesId: Int?,
        episodeId: Int?,
        name: String? = null,
        genre: String? = null,
        categoryName: String? = null
    ): Boolean = withContext(ioDispatcher) {
        try {
            val key = generateKey(contentType, streamId, seriesId, episodeId)
            Log.d(TAG, "Marking item as watched")

            val watchedItem = WatchedItem(
                contentType = contentType,
                streamId = streamId,
                seriesId = seriesId,
                episodeId = episodeId,
                name = name,
                genre = genre,
                categoryName = categoryName,
                watchedAt = Instant.now().toString()
            )
            
            // Optimistic update: Update StateFlow atomically
            val updatedMap = updateMutex.withLock {
                val currentMap = _watchedHistory.value
                val updated = currentMap + (key to watchedItem)
                _watchedHistory.value = updated
                updated
            }
            
            // Save to Firestore asynchronously
            saveToFirestore(updatedMap)
            
            Log.d(TAG, "Marked item as watched successfully")
            true
        } catch (e: Exception) {
            reportError(TAG, "Error marking item as watched", e)
            false
        }
    }

    /**
     * Removes an item from the watched history (optimistic update).
     */
    suspend fun markAsUnwatched(
        contentType: String,
        streamId: Int?,
        seriesId: Int?,
        episodeId: Int?
    ): Boolean = withContext(ioDispatcher) {
        try {
            val key = generateKey(contentType, streamId, seriesId, episodeId)
            Log.d(TAG, "Marking item as unwatched")

            val updatedMap = updateMutex.withLock {
                val currentMap = _watchedHistory.value
                if (!currentMap.containsKey(key)) {
                    return@withContext true
                }
                val updated = currentMap - key
                _watchedHistory.value = updated
                updated
            }

            saveToFirestore(updatedMap)

            Log.d(TAG, "Marked item as unwatched successfully")
            true
        } catch (e: Exception) {
            reportError(TAG, "Error marking item as unwatched", e)
            false
        }
    }

    /**
     * Marks multiple episodes of a series as watched with a single Firestore save
     * (optimistic update).
     */
    suspend fun markSeriesEpisodesAsWatched(
        seriesId: Int,
        episodeIds: Collection<Int>,
        name: String? = null,
        genre: String? = null,
        categoryName: String? = null
    ): Boolean = withContext(ioDispatcher) {
        if (episodeIds.isEmpty()) {
            return@withContext true
        }
        try {
            val entries = buildSeriesWatchedEntries(
                seriesId = seriesId,
                episodeIds = episodeIds,
                name = name,
                genre = genre,
                categoryName = categoryName,
                watchedAt = Instant.now().toString()
            )

            val updatedMap = updateMutex.withLock {
                val updated = _watchedHistory.value + entries
                _watchedHistory.value = updated
                updated
            }

            saveToFirestore(updatedMap)

            Log.d(TAG, "Marked ${episodeIds.size} episodes as watched")
            true
        } catch (e: Exception) {
            reportError(TAG, "Error marking episodes as watched", e)
            false
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
        try {
            val key = generateKey(contentType, streamId, seriesId, episodeId)
            val watchedMap = _watchedHistory.value
            val isWatched = watchedMap.containsKey(key)
            Log.d(TAG, "isWatched check completed: result=$isWatched")
            return@withContext isWatched
        } catch (e: Exception) {
            reportError(TAG, "Error checking if item is watched", e)
            false
        }
    }

    /**
     * Gets all watched episode IDs for a specific series (batch loading)
     */
    suspend fun getWatchedEpisodeIds(seriesId: Int): Set<Int> = withContext(ioDispatcher) {
        try {
            val watchedMap = _watchedHistory.value
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
            reportError(TAG, "Error getting watched episode IDs for seriesId: $seriesId", e)
            emptySet()
        }
    }

    /**
     * Gets all watched VOD IDs, optionally filtered by category
     * Note: categoryId filtering requires additional data from repository, so this returns all watched VODs
     */
    suspend fun getWatchedVodIds(categoryId: String? = null): Set<Int> = withContext(ioDispatcher) {
        try {
            val watchedMap = _watchedHistory.value
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
            reportError(TAG, "Error getting watched VOD IDs", e)
            emptySet()
        }
    }

    /**
     * Gets the latest watched episode for a series with its timestamp
     * Returns null if no episodes have been watched for this series
     */
    suspend fun getLatestWatchedEpisode(seriesId: Int): Pair<Int, Long>? = withContext(ioDispatcher) {
        try {
            val watchedMap = _watchedHistory.value
            val prefix = "$KEY_PREFIX_SERIES$seriesId:"
            
            val latest = watchedMap
                .filterKeys { it.startsWith(prefix) }
                .mapNotNull { (key, item) ->
                    val episodeId = key.removePrefix(prefix).toIntOrNull() ?: return@mapNotNull null
                    val timestamp = try {
                        Instant.parse(item.watchedAt).toEpochMilli()
                    } catch (e: Exception) {
                        reportWarning(TAG, "Error parsing watchedAt timestamp for episode $episodeId: ${item.watchedAt}", e)
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
            reportError(TAG, "Error getting latest watched episode for seriesId: $seriesId", e)
            null
        }
    }
}
