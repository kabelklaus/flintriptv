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
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // StateFlow for reactive state management
    private val _watchedHistory = MutableStateFlow<Map<String, WatchedItem>>(emptyMap())
    val watchedHistory: StateFlow<Map<String, WatchedItem>> = _watchedHistory.asStateFlow()

    // Mutex to prevent concurrent loadFromFirestore() calls
    private val loadMutex = Mutex()

    private val mapType = Types.newParameterizedType(
        Map::class.java,
        String::class.java,
        WatchedItem::class.java
    )
    private val mapAdapter = moshi.adapter<Map<String, WatchedItem>>(mapType)
    
    // Cache for watched items map to avoid repeated JSON parsing (used for migration)
    private var cachedWatchedMap: Map<String, WatchedItem>? = null

    init {
        // Initial load
        serviceScope.launch {
            loadFromFirestore()
        }

        // React to credential changes (e.g., user login/logout or user switch)
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
                },
                onFailure = { exception ->
                    Log.e(TAG, "Failed to load watched history from Firestore", exception)
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
        Log.d(TAG, "Starting migration from local storage to Firestore for user: $username")
        
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
                        Log.e(TAG, "Migration failed, will retry on next app start", exception)
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
            Log.e(TAG, "Error during migration", e)
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
            Log.e(TAG, "Error loading local data for migration", e)
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
            Log.e(TAG, "Error clearing local data", e)
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
                    Log.d(TAG, "Successfully saved watched history to Firestore")
                },
                onFailure = { exception ->
                    Log.e(TAG, "Failed to save watched history to Firestore", exception)
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
        episodeId: Int?
    ): Boolean = withContext(ioDispatcher) {
        try {
            val key = generateKey(contentType, streamId, seriesId, episodeId)
            Log.d(TAG, "Marking as watched: key=$key, contentType: $contentType, streamId: $streamId, seriesId: $seriesId, episodeId: $episodeId")

            val watchedItem = WatchedItem(
                contentType = contentType,
                streamId = streamId,
                seriesId = seriesId,
                episodeId = episodeId,
                watchedAt = Instant.now().toString()
            )
            
            // Optimistic update: Update StateFlow immediately
            val updatedMap = _watchedHistory.value.toMutableMap()
            updatedMap[key] = watchedItem
            _watchedHistory.value = updatedMap
            
            // Save to Firestore asynchronously
            saveToFirestore(updatedMap)
            
            // Also update local cache for migration fallback
            cachedWatchedMap = updatedMap
            
            Log.d(TAG, "Marked as watched successfully: key=$key")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error marking as watched for contentType: $contentType, streamId: $streamId, seriesId: $seriesId, episodeId: $episodeId", e)
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
            Log.d(TAG, "isWatched check: key=$key, result=$isWatched")
            return@withContext isWatched
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if watched for contentType: $contentType, streamId: $streamId, seriesId: $seriesId, episodeId: $episodeId", e)
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
            Log.e(TAG, "Error getting watched episode IDs for seriesId: $seriesId", e)
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
            Log.e(TAG, "Error getting watched VOD IDs", e)
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
