package com.flintr.tv.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.flintr.tv.data.model.Episode
import com.flintr.tv.data.model.Season
import com.flintr.tv.data.model.Series
import com.flintr.tv.data.model.WatchProgress
import com.flintr.tv.data.repository.UserRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.time.Instant
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import com.flintr.tv.utils.error.reportError
import com.flintr.tv.utils.error.reportWarning
import com.flintr.tv.utils.parseTimestampMillisOrZero

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
class WatchProgressService(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi,
    private val watchlistRepository: WatchlistRepository,
    private val watchedHistoryService: WatchedHistoryService,
    private val userRepository: UserRepository,
    private val credentialsRepository: CredentialsRepository,
    private val cacheService: DataCacheService,
    @Named("WatchProgress") private val syncManager: FirestoreSyncManager,
    private val ioDispatcher: CoroutineDispatcher,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val syncIntervalMs: Long = FIRESTORE_SYNC_MIN_INTERVAL_MS
) {
    /** DI constructor: Dagger cannot fill Kotlin defaults, so bind the production clock here. */
    @Inject
    constructor(
        @ApplicationContext context: Context,
        moshi: Moshi,
        watchlistRepository: WatchlistRepository,
        watchedHistoryService: WatchedHistoryService,
        userRepository: UserRepository,
        credentialsRepository: CredentialsRepository,
        cacheService: DataCacheService,
        @Named("WatchProgress") syncManager: FirestoreSyncManager,
        ioDispatcher: CoroutineDispatcher
    ) : this(
        context = context,
        moshi = moshi,
        watchlistRepository = watchlistRepository,
        watchedHistoryService = watchedHistoryService,
        userRepository = userRepository,
        credentialsRepository = credentialsRepository,
        cacheService = cacheService,
        syncManager = syncManager,
        ioDispatcher = ioDispatcher,
        nowMs = { System.currentTimeMillis() },
        syncIntervalMs = FIRESTORE_SYNC_MIN_INTERVAL_MS
    )

    // SharedPreferences only for migration flag and reading old data
    private val migrationPrefs: SharedPreferences = context.getSharedPreferences(
        "watch_progress_prefs",
        Context.MODE_PRIVATE
    )

    companion object {
        private const val KEY_WATCH_PROGRESS = "iptv_watch_progress_data"
        private const val KEY_MIGRATION_COMPLETE = "watch_progress_migrated"
        private const val TAG = "WatchProgressService"
        private const val MIN_POSITION_MS = 30_000L // 30 Sekunden
        private const val COMPLETION_THRESHOLD = 0.9 // 90%
        private const val KEY_PREFIX_MOVIE = "movie:"
        private const val KEY_PREFIX_SERIES = "series:"
        private const val FIRESTORE_SYNC_MIN_INTERVAL_MS = 60_000L
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // StateFlow for reactive state management
    private val _watchProgress = MutableStateFlow<Map<String, WatchProgress>>(emptyMap())
    val watchProgress: StateFlow<Map<String, WatchProgress>> = _watchProgress.asStateFlow()

    // Username whose progress map is currently in [_watchProgress]. The coalesced writer compares
    // this against the credentials read at write time so a user switch cannot full-replace the new
    // account's Firestore document with the previous account's map.
    @Volatile
    private var progressOwner: String? = null

    // Mutex to prevent concurrent loadFromFirestore() calls
    private val loadMutex = Mutex()
    
    // Mutex to prevent concurrent StateFlow updates
    private val updateMutex = Mutex()

    // Serializes Firestore writes/deletes so a delayed full-map save cannot resurrect a deleted key.
    private val firestoreWriteMutex = Mutex()

    // CONFLATED: while a write is cooling down, only the newest pending save request matters.
    private val syncRequests = Channel<Unit>(Channel.CONFLATED)

    // Timestamp of the last successful Firestore write. Shared by the coalesced writer and the
    // immediate exit flush so a flush also advances the coalescing clock (no redundant rewrite).
    @Volatile
    private var lastWriteAtMs = 0L

    private val mapType = Types.newParameterizedType(
        Map::class.java,
        String::class.java,
        WatchProgress::class.java
    )
    private val mapAdapter = moshi.adapter<Map<String, WatchProgress>>(mapType)
    private val progressAdapter = moshi.adapter(WatchProgress::class.java)

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
                    progressOwner = null
                    _watchProgress.value = emptyMap()
                    Log.d(TAG, "Credentials cleared, resetting watch progress")
                }
            }
        }

        // Coalesced writer: at most one full-map Firestore write per interval, always with the
        // latest local state. Continuous playback therefore syncs at most ~60s behind.
        serviceScope.launch {
            while (true) {
                syncRequests.receive()
                val waitMs = (lastWriteAtMs + syncIntervalMs - nowMs())
                    .coerceAtLeast(0L)
                if (waitMs > 0) {
                    delay(waitMs)
                }
                firestoreWriteMutex.withLock {
                    if (writeProgressIfOwnerMatches()) {
                        lastWriteAtMs = nowMs()
                    }
                }
            }
        }
    }

    /**
     * Writes the current progress map if the credentials still match the map's owner.
     * Caller must hold [firestoreWriteMutex]; shared by the coalesced writer and the exit flush.
     * @return true when Firestore accepted the write.
     */
    private suspend fun writeProgressIfOwnerMatches(): Boolean {
        val username = credentialsRepository.getCredentials()?.username
        if (username.isNullOrBlank()) {
            Log.w(TAG, "Cannot save to Firestore: no username available")
            return false
        }
        if (progressOwner != username) {
            Log.w(TAG, "Skipping Firestore save: progress owner changed")
            return false
        }

        return userRepository.saveWatchProgress(username, _watchProgress.value).fold(
            onSuccess = {
                Log.d(TAG, "Successfully saved watch progress to Firestore")
                true
            },
            onFailure = { exception ->
                reportError(TAG, "Failed to save watch progress to Firestore", exception)
                // StateFlow already updated, so UI will reflect the change
                // Firestore will sync when connection is restored
                false
            }
        )
    }

    /**
     * Loads watch progress from Firestore and performs migration if needed.
     * Uses mutex to prevent concurrent loads.
     */
    private suspend fun loadFromFirestore() {
        loadMutex.withLock {
            val username = credentialsRepository.getCredentials()?.username
            if (username.isNullOrBlank()) {
                Log.d(TAG, "No username available, skipping Firestore load due to missing credentials")
                progressOwner = null
                _watchProgress.value = emptyMap()
                return
            }

            // Check if migration is needed
            val migrationComplete = migrationPrefs.getBoolean(KEY_MIGRATION_COMPLETE, false)
            if (!migrationComplete) {
                performMigration(username)
            }

            // Load from Firestore
            val result = userRepository.loadWatchProgress(username)
            result.fold(
                onSuccess = { progress ->
                    progressOwner = username
                    _watchProgress.value = progress
                    syncManager.updateLastSyncTimestamp()
                    Log.d(TAG, "Loaded watch progress from Firestore: ${progress.size} total")
                },
                onFailure = { exception ->
                    reportError(TAG, "Failed to load watch progress from Firestore", exception)
                    // Keep current state or use empty map
                    if (_watchProgress.value.isEmpty()) {
                        Log.e(TAG, "State is empty after failed Firestore load - user data may be unavailable")
                        progressOwner = username
                        _watchProgress.value = emptyMap()
                    }
                }
            )
        }
    }

    /**
     * Synchronizes watch progress from Firestore if enough time has passed since last sync.
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
        Log.d(TAG, "Starting watch progress migration from local storage to Firestore")
        
        try {
            // Load old data from SharedPreferences
            val localData = loadLocalData()
            
            if (localData.isNotEmpty()) {
                // Migrate to Firestore
                val migrationResult = userRepository.migrateWatchProgressFromLocal(username, localData)
                
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
    private fun loadLocalData(): Map<String, WatchProgress> {
        return try {
            val json = migrationPrefs.getString(KEY_WATCH_PROGRESS, null)
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
                remove(KEY_WATCH_PROGRESS)
            }
            Log.d(TAG, "Cleared old local data after migration")
        } catch (e: Exception) {
            reportError(TAG, "Error clearing local data", e)
        }
    }

    /**
     * Requests an asynchronous, throttled Firestore sync of the current progress map.
     * This only triggers the request; credentials/owner validation happens in the writer
     * ([writeProgressIfOwnerMatches]) inside the IO context. The writer coalesces bursts and
     * always persists [_watchProgress] at write time.
     */
    private fun requestFirestoreSync() {
        syncRequests.trySend(Unit)
    }

    /**
     * Flushes the current progress map to Firestore immediately, bypassing and advancing the
     * coalescing clock. Uses the same [firestoreWriteMutex] and owner-skip guard as the regular
     * writer.
     * Call at most once per player exit; the write runs in [serviceScope], so it survives the
     * composition that requested it.
     */
    fun flushPendingSync() {
        serviceScope.launch {
            val username = credentialsRepository.getCredentials()?.username
            if (username.isNullOrBlank()) {
                Log.w(TAG, "Cannot flush watch progress: no username available")
                return@launch
            }
            firestoreWriteMutex.withLock {
                if (writeProgressIfOwnerMatches()) {
                    lastWriteAtMs = nowMs()
                }
            }
        }
    }

    /**
     * Generates a storage key for the given content type and IDs (same format as watched history)
     */
    private fun generateKey(
        contentType: String,
        streamId: Int?,
        seriesId: Int?,
        episodeId: Int?
    ): String = WatchedHistoryService.generateKey(contentType, streamId, seriesId, episodeId)

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
            when (val validation = validateProgressParams(contentType, position, duration)) {
                is ProgressValidationResult.Skip -> {
                    Log.d(TAG, "Skipping progress save: ${validation.reason}")
                    return@withContext false
                }
                is ProgressValidationResult.Completed -> {
                    Log.d(TAG, "Progress reached completion threshold; marking as watched and deleting progress")
                    // Mark as watched BEFORE deleting progress
                    val (name, genre, categoryName) = getNameGenreAndCategoryFromCache(contentType, streamId, seriesId)
                    watchedHistoryService.markAsWatched(contentType, streamId, seriesId, episodeId, name, genre, categoryName)
                    return@withContext deleteProgress(contentType, streamId, seriesId, episodeId)
                }
                is ProgressValidationResult.Valid -> {
                    val key = generateKey(contentType, streamId, seriesId, episodeId)
                    Log.d(TAG, "Saving watch progress: progress=${(validation.progressPercent * 100).toInt()}%")
                    
                    persistProgress(
                        key = key,
                        position = position,
                        duration = duration,
                        title = title,
                        owner = credentialsRepository.getCredentials()?.username
                    )
                    
                    Log.d(TAG, "Progress saved successfully")
                    true
                }
            }
        } catch (e: Exception) {
            reportError(TAG, "Error saving progress", e)
            false
        }
    }

    /**
     * Persists progress to StateFlow and Firestore (optimistic update).
     */
    private suspend fun persistProgress(
        key: String,
        position: Long,
        duration: Long,
        title: String,
        owner: String?
    ) {
        val progress = WatchProgress(
            position = position,
            duration = duration,
            lastWatched = Instant.now().toString(),
            title = title
        )
        
        // Optimistic update: Update StateFlow atomically
        updateMutex.withLock {
            progressOwner = owner
            _watchProgress.value = _watchProgress.value + (key to progress)
        }

        // Save to Firestore asynchronously (throttled)
        requestFirestoreSync()
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
            Log.d(TAG, "Getting saved progress")

            val progressMap = _watchProgress.value
            val progress = progressMap[key]

            if (progress == null) {
                Log.d(TAG, "No progress found")
                return@withContext null
            }

            Log.d(TAG, "Found saved progress")
            progress.position
        } catch (e: Exception) {
            reportError(TAG, "Error getting progress", e)
            null
        }
    }

    /**
     * Returns the full WatchProgress object for a movie or series episode,
     * or null if no progress exists.
     */
    suspend fun getWatchProgress(
        contentType: String,
        streamId: Int?,
        seriesId: Int?,
        episodeId: Int?
    ): WatchProgress? = withContext(ioDispatcher) {
        try {
            val key = generateKey(contentType, streamId, seriesId, episodeId)
            _watchProgress.value[key]
        } catch (e: Exception) {
            reportError(TAG, "Error getting watch progress", e)
            null
        }
    }

    /**
     * Deletes watch progress for a movie or series episode (optimistic update).
     */
    private suspend fun deleteProgress(
        contentType: String,
        streamId: Int?,
        seriesId: Int?,
        episodeId: Int?
    ): Boolean = withContext(ioDispatcher) {
        try {
            val key = generateKey(contentType, streamId, seriesId, episodeId)
            Log.d(TAG, "Deleting progress")

            val progress = _watchProgress.value[key]
            val username = credentialsRepository.getCredentials()?.username

            // Optimistic update: Update StateFlow atomically
            updateMutex.withLock {
                progressOwner = username
                val currentMap = _watchProgress.value
                val updated = currentMap - key
                _watchProgress.value = updated
            }
            
            // Delete from Firestore explicitly using FieldValue.delete()
            if (!username.isNullOrBlank()) {
                serviceScope.launch {
                    firestoreWriteMutex.withLock {
                        val currentUsername = credentialsRepository.getCredentials()?.username
                        if (currentUsername.isNullOrBlank()) {
                            Log.w(TAG, "Cannot delete from Firestore: no username available")
                            return@withLock
                        }
                        val result = userRepository.deleteWatchProgress(currentUsername, key)
                        result.fold(
                            onSuccess = {
                                Log.d(TAG, "Successfully deleted watch progress from Firestore")
                            },
                            onFailure = { exception ->
                                reportError(TAG, "Failed to delete watch progress from Firestore", exception)
                                // StateFlow already updated, so UI will reflect the change
                                // Firestore will sync when connection is restored
                            }
                        )
                    }
                }
            } else {
                Log.w(TAG, "Cannot delete from Firestore: no username available")
            }

            // Automatically remove movie from watchlist when progress is deleted
            if (contentType == "movie" && streamId != null && progress != null) {
                try {
                    val encodedName = URLEncoder.encode(progress.title, "UTF-8")
                    val entryId = "$KEY_PREFIX_MOVIE$streamId:$encodedName"
                    watchlistRepository.removeFromWatchlist(entryId)
                    Log.d(TAG, "Automatically removed completed movie from favorites")
                } catch (e: Exception) {
                    reportWarning(TAG, "Failed to automatically remove completed movie from favorites", e)
                    // Don't fail the progress deletion if watchlist removal fails
                }
            }

            Log.d(TAG, "Progress deleted successfully")
            true
        } catch (e: Exception) {
            reportError(TAG, "Error deleting progress", e)
            false
        }
    }

    /**
     * Result of parsing a progress key to extract content type and IDs.
     */
    private sealed class ParsedProgressKey {
        data class Movie(val streamId: Int) : ParsedProgressKey()
        data class Series(val seriesId: Int, val episodeId: Int) : ParsedProgressKey()
        object Unknown : ParsedProgressKey()
    }

    /**
     * Checks if a progress entry is completed (>= 90%).
     */
    private fun isCompletedProgress(progress: WatchProgress): Boolean {
        if (progress.duration <= 0) return false
        val progressPercent = progress.position.toDouble() / progress.duration.toDouble()
        return progressPercent >= COMPLETION_THRESHOLD
    }

    /**
     * Parses a progress key to extract content type and IDs.
     */
    private fun parseProgressKey(key: String): ParsedProgressKey {
        return when {
            key.startsWith(KEY_PREFIX_MOVIE) -> {
                val streamId = key.removePrefix(KEY_PREFIX_MOVIE).toIntOrNull()
                if (streamId != null) {
                    ParsedProgressKey.Movie(streamId)
                } else {
                    Log.w(TAG, "Failed to parse streamId from key: $key")
                    ParsedProgressKey.Unknown
                }
            }
            key.startsWith(KEY_PREFIX_SERIES) -> {
                val parts = key.removePrefix(KEY_PREFIX_SERIES).split(":")
                if (parts.size == 2) {
                    val seriesId = parts[0].toIntOrNull()
                    val episodeId = parts[1].toIntOrNull()
                    if (seriesId != null && episodeId != null) {
                        ParsedProgressKey.Series(seriesId, episodeId)
                    } else {
                        Log.w(TAG, "Failed to parse seriesId/episodeId from key: $key")
                        ParsedProgressKey.Unknown
                    }
                } else {
                    Log.w(TAG, "Invalid series key format: $key")
                    ParsedProgressKey.Unknown
                }
            }
            else -> {
                Log.w(TAG, "Unknown key prefix: $key")
                ParsedProgressKey.Unknown
            }
        }
    }

    /**
     * Looks up a movie's metadata from cache.
     */
    private fun lookupMovieFromCache(
        cachedData: CachedData,
        streamId: Int,
        vodCategoryMap: Map<String, com.flintr.tv.data.model.Category>
    ): Triple<String?, String?, String?> {
        val vodStream = cachedData.movies.find { it.streamId == streamId }
        if (vodStream == null) {
            Log.w(TAG, "Movie not found in cache: streamId=$streamId (total movies in cache: ${cachedData.movies.size})")
            return Triple(null, null, null)
        }

        val categoryName = vodStream.categoryName ?: lookupCategoryName(
            vodStream.categoryId,
            vodCategoryMap,
            "vodCategories"
        )
        Log.d(TAG, "Found movie in cache: streamId=$streamId, name=${vodStream.name}, genre=${vodStream.genre}, categoryId=${vodStream.categoryId}, categoryName=$categoryName")
        return Triple(vodStream.name, vodStream.genre, categoryName)
    }

    /**
     * Looks up a series' metadata from cache.
     */
    private fun lookupSeriesFromCache(
        cachedData: CachedData,
        seriesId: Int,
        seriesCategoryMap: Map<String, com.flintr.tv.data.model.Category>
    ): Triple<String?, String?, String?> {
        val series = cachedData.series.find { it.seriesId == seriesId }
        if (series == null) {
            Log.w(TAG, "Series not found in cache: seriesId=$seriesId (total series in cache: ${cachedData.series.size})")
            return Triple(null, null, null)
        }

        val categoryName = series.categoryName ?: lookupCategoryName(
            series.categoryId,
            seriesCategoryMap,
            "seriesCategories"
        )
        Log.d(TAG, "Found series in cache: seriesId=$seriesId, name=${series.name}, genre=${series.genre}, categoryId=${series.categoryId}, categoryName=$categoryName")
        return Triple(series.name, series.genre, categoryName)
    }

    /**
     * Looks up a category name from a pre-built map of categories by ID.
     * Using a Map provides O(1) lookup instead of O(n) with List.find().
     */
    private fun lookupCategoryName(
        categoryId: String?,
        categoryMap: Map<String, com.flintr.tv.data.model.Category>,
        categoryType: String
    ): String? {
        val catId = categoryId?.trim() ?: return null
        val foundCategory = categoryMap[catId]
        if (foundCategory == null) {
            Log.w(TAG, "Category not found in $categoryType: categoryId='$catId' (available categories: ${categoryMap.keys.take(5)})")
        }
        return foundCategory?.categoryName
    }

    /**
     * Gets name, genre and categoryName from cache for a content item.
     * Returns Triple(name, genre, categoryName) or (null, null, null) if not found.
     */
    private suspend fun getNameGenreAndCategoryFromCache(
        contentType: String,
        streamId: Int?,
        seriesId: Int?
    ): Triple<String?, String?, String?> {
        return try {
            val cachedData = cacheService.loadCachedDataAsync()
            if (cachedData == null) {
                Log.w(TAG, "Cache data is null for contentType: $contentType, streamId: $streamId, seriesId: $seriesId")
                return Triple(null, null, null)
            }

            // Create maps for O(1) category lookups instead of O(n) List.find()
            val vodCategoryMap = cachedData.vodCategories.associateBy { it.categoryId.trim() }
            val seriesCategoryMap = cachedData.seriesCategories.associateBy { it.categoryId.trim() }

            when (contentType) {
                "movie" -> if (streamId != null) lookupMovieFromCache(cachedData, streamId, vodCategoryMap) else Triple(null, null, null)
                "series" -> if (seriesId != null) lookupSeriesFromCache(cachedData, seriesId, seriesCategoryMap) else Triple(null, null, null)
                else -> Triple(null, null, null)
            }
        } catch (e: Exception) {
            reportWarning(TAG, "Failed to get name/genre/categoryName from cache for contentType: $contentType, streamId: $streamId, seriesId: $seriesId", e)
            Triple(null, null, null)
        }
    }

    /**
     * Cleans up a single completed progress entry.
     * Returns true if the entry was successfully cleaned up.
     */
    private suspend fun cleanupSingleEntry(parsedKey: ParsedProgressKey): Boolean {
        return when (parsedKey) {
            is ParsedProgressKey.Movie -> {
                Log.d(TAG, "cleanupCompletedProgress() - cleaning up movie: streamId=${parsedKey.streamId}")
                val (name, genre, categoryName) = getNameGenreAndCategoryFromCache("movie", parsedKey.streamId, null)
                watchedHistoryService.markAsWatched("movie", parsedKey.streamId, null, null, name, genre, categoryName)
                deleteProgress("movie", parsedKey.streamId, null, null)
                true
            }
            is ParsedProgressKey.Series -> {
                Log.d(TAG, "cleanupCompletedProgress() - cleaning up episode: seriesId=${parsedKey.seriesId}, episodeId=${parsedKey.episodeId}")
                val (name, genre, categoryName) = getNameGenreAndCategoryFromCache("series", null, parsedKey.seriesId)
                watchedHistoryService.markAsWatched("series", parsedKey.episodeId, parsedKey.seriesId, parsedKey.episodeId, name, genre, categoryName)
                deleteProgress("series", parsedKey.episodeId, parsedKey.seriesId, parsedKey.episodeId)
                true
            }
            is ParsedProgressKey.Unknown -> false
        }
    }

    /**
     * Cleans up completed progress entries (>= 90%)
     * Automatically marks them as watched and removes from progress list
     * Returns the number of entries cleaned up
     */
    suspend fun cleanupCompletedProgress(): Int = withContext(ioDispatcher) {
        Log.d(TAG, "cleanupCompletedProgress() called - starting cleanup")
        val progressMap = _watchProgress.value
        Log.d(TAG, "cleanupCompletedProgress() - total entries: ${progressMap.size}")
        
        if (progressMap.isEmpty()) {
            Log.d(TAG, "cleanupCompletedProgress() - no entries to check")
            return@withContext 0
        }
        
        var cleanedCount = 0
        
        progressMap.forEach { (key, progress) ->
            if (!isCompletedProgress(progress)) {
                return@forEach
            }
            
            val progressPercent = (progress.position.toDouble() / progress.duration.toDouble() * 100).toInt()
            Log.d(TAG, "cleanupCompletedProgress() - completed progress entry found: progress=$progressPercent%")
            
            val parsedKey = parseProgressKey(key)
            if (cleanupSingleEntry(parsedKey)) {
                cleanedCount++
            }
        }
        
        if (cleanedCount > 0) {
            Log.d(TAG, "cleanupCompletedProgress() - cleaned up $cleanedCount completed progress entries")
        } else {
            Log.d(TAG, "cleanupCompletedProgress() - no entries needed cleanup")
        }
        
        cleanedCount
    }

    /**
     * Gets all in-progress items (movies and episodes with progress > 0% and < 90%)
     */
    suspend fun getAllInProgress(): Map<String, WatchProgress> = withContext(ioDispatcher) {
        _watchProgress.value
    }

    /**
     * Gets all in-progress movies (keys starting with "movie:")
     */
    suspend fun getInProgressMovies(): Map<String, WatchProgress> = withContext(ioDispatcher) {
        _watchProgress.value.filterKeys { it.startsWith(KEY_PREFIX_MOVIE) }
    }

    /**
     * Gets all in-progress episodes (keys starting with "series:")
     */
    suspend fun getInProgressEpisodes(): Map<String, WatchProgress> = withContext(ioDispatcher) {
        _watchProgress.value.filterKeys { it.startsWith(KEY_PREFIX_SERIES) }
    }

    /**
     * Gets the total count of in-progress items (movies + episodes).
     * RAW count over the progress map - does not resolve against the catalog, so it may
     * include entries whose movie/series/episode no longer exists. Not used for UI labels/
     * counters (see [com.flintr.tv.ui.tabs.rememberInProgressItems] - Task 10b).
     */
    suspend fun getInProgressCount(): Int = withContext(ioDispatcher) {
        val progressMap = _watchProgress.value
        val movies = progressMap.count { it.key.startsWith(KEY_PREFIX_MOVIE) }
        val episodes = progressMap.count { it.key.startsWith(KEY_PREFIX_SERIES) }
        movies + episodes
    }

    /**
     * Gets the count of in-progress episodes only.
     * RAW count over the progress map - does not resolve against the catalog. Not used for UI
     * labels/counters (see [com.flintr.tv.ui.tabs.rememberInProgressItems] - Task 10b).
     */
    suspend fun getInProgressEpisodesCount(): Int = withContext(ioDispatcher) {
        val progressMap = _watchProgress.value
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
     * Returns null if the episode is not found or the duration is invalid
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
        val timestamp = parseTimestampMillisOrZero(progress.lastWatched) { error ->
            reportWarning(TAG, "Error parsing lastWatched timestamp: ${progress.lastWatched}", error)
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
            val progressMap = _watchProgress.value
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
            reportError(TAG, "Error getting last interaction for seriesId: $seriesId", e)
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
            reportError(TAG, "Error getting smart resume target for seriesId: $seriesId", e)
            null
        }
    }

    /**
     * Manually sets the watched state of a series episode and removes any
     * leftover in-progress state so the UI does not show a stale progress bar.
     */
    suspend fun setSeriesEpisodeWatched(
        seriesId: Int,
        episodeId: Int,
        watched: Boolean
    ): Boolean {
        val success = if (watched) {
            val (name, genre, categoryName) = getNameGenreAndCategoryFromCache("series", null, seriesId)
            watchedHistoryService.markAsWatched("series", null, seriesId, episodeId, name, genre, categoryName)
        } else {
            watchedHistoryService.markAsUnwatched("series", null, seriesId, episodeId)
        }
        deleteProgressIfPresent(seriesId, episodeId)
        return success
    }

    /**
     * Marks multiple episodes of a series as watched (single Firestore save)
     * and removes leftover in-progress states of those episodes.
     */
    suspend fun markSeriesEpisodesAsWatched(
        seriesId: Int,
        episodeIds: List<Int>
    ): Boolean = withContext(ioDispatcher) {
        if (episodeIds.isEmpty()) {
            return@withContext true
        }
        val (name, genre, categoryName) = getNameGenreAndCategoryFromCache("series", null, seriesId)
        val success = watchedHistoryService.markSeriesEpisodesAsWatched(
            seriesId = seriesId,
            episodeIds = episodeIds,
            name = name,
            genre = genre,
            categoryName = categoryName
        )
        episodeIds.forEach { episodeId ->
            deleteProgressIfPresent(seriesId, episodeId)
        }
        success
    }

    private suspend fun deleteProgressIfPresent(seriesId: Int, episodeId: Int) {
        val key = generateKey("series", null, seriesId, episodeId)
        if (_watchProgress.value.containsKey(key)) {
            deleteProgress("series", null, seriesId, episodeId)
        }
    }
}
