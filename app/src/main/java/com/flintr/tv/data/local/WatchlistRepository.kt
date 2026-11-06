package com.flintr.tv.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.flintr.tv.data.model.WatchlistEntry
import com.flintr.tv.data.model.WatchlistEntryType
import com.flintr.tv.data.repository.UserRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import com.flintr.tv.utils.error.reportError

@Singleton
class WatchlistRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi,
    private val userRepository: UserRepository,
    private val credentialsRepository: CredentialsRepository,
    @Named("Watchlist") private val syncManager: FirestoreSyncManager
) {
    // SharedPreferences only for migration flag and reading old data
    private val migrationPrefs: SharedPreferences = context.getSharedPreferences(
        "favorites_prefs",
        Context.MODE_PRIVATE
    )

    companion object {
        private const val KEY_WATCHLIST = "iptv_favorites"
        private const val KEY_MIGRATION_COMPLETE = "favorites_migrated"
        private const val TAG = "FavoritesRepository"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // StateFlow for reactive state management
    private val _entries = MutableStateFlow<List<WatchlistEntry>>(emptyList())
    val entries: StateFlow<List<WatchlistEntry>> = _entries.asStateFlow()

    // Non-null after a failed/timed-out Firestore load; cleared on the next successful load.
    // Consumed by WatchlistContent to show an error+retry state instead of a silent empty list.
    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    // Mutex to prevent concurrent loadFromFirestore() calls
    private val loadMutex = Mutex()

    // Mutex to prevent lost updates from concurrent addToWatchlist()/removeFromWatchlist() calls
    private val updateMutex = Mutex()

    // Moshi adapter for local data migration
    private val listType = Types.newParameterizedType(List::class.java, WatchlistEntry::class.java)
    private val listAdapter = moshi.adapter<List<WatchlistEntry>>(listType)

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
                    _entries.value = emptyList()
                    _loadError.value = null
                    Log.d(TAG, "Credentials cleared, resetting favorites")
                }
            }
        }
    }

    /**
     * Loads watchlist entries from Firestore and performs migration if needed.
     * Uses mutex to prevent concurrent loads.
     * @return true if entries were loaded successfully from Firestore, false otherwise
     */
    private suspend fun loadFromFirestore(): Boolean {
        return loadMutex.withLock {
            val username = credentialsRepository.getCredentials()?.username
            if (username.isNullOrBlank()) {
                Log.d(TAG, "No username available, skipping Firestore load due to missing credentials")
                _entries.value = emptyList()
                return@withLock false
            }

            // Check if migration is needed
            val migrationComplete = migrationPrefs.getBoolean(KEY_MIGRATION_COMPLETE, false)
            if (!migrationComplete) {
                performMigration(username)
            }

            // Load from Firestore
            val result = userRepository.loadWatchlist(username)
            result.fold(
                onSuccess = { loadedEntries ->
                    _entries.value = loadedEntries
                    _loadError.value = null
                    syncManager.updateLastSyncTimestamp()
                    Log.d(TAG, "Loaded favorites from Firestore: ${loadedEntries.size} total")
                    true
                },
                onFailure = { exception ->
                    reportError(TAG, "Failed to load favorites from Firestore", exception)
                    _loadError.value = "Fehler beim Laden der Watchlist"
                    // Keep current state or use empty list
                    if (_entries.value.isEmpty()) {
                        Log.e(TAG, "State is empty after failed Firestore load - user data may be unavailable")
                        _entries.value = emptyList()
                    }
                    false
                }
            )
        }
    }

    /**
     * Synchronizes the watchlist from Firestore if enough time has passed since last sync.
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
     * Retries loading the watchlist from Firestore, bypassing the sync cooldown.
     * Used by the UI's explicit retry action after a failed/timed-out load.
     */
    suspend fun retryLoadFromFirestore() {
        syncManager.forceSync {
            loadFromFirestore()
        }
    }

    /**
     * Performs migration from local SharedPreferences to Firestore.
     */
    private suspend fun performMigration(username: String) {
        Log.d(TAG, "Starting favorites migration from local storage to Firestore")

        try {
            // Load old data from SharedPreferences
            val localData = loadLocalData()

            if (localData.isNotEmpty()) {
                // Migrate to Firestore
                val migrationResult = userRepository.migrateWatchlistFromLocal(username, localData)

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
    private fun loadLocalData(): List<WatchlistEntry> {
        return try {
            val json = migrationPrefs.getString(KEY_WATCHLIST, null)
            if (json.isNullOrBlank()) {
                emptyList()
            } else {
                listAdapter.fromJson(json) ?: emptyList()
            }
        } catch (e: Exception) {
            reportError(TAG, "Error loading local data for migration", e)
            emptyList()
        }
    }

    /**
     * Clears old local data after successful migration.
     */
    private fun clearLocalData() {
        try {
            migrationPrefs.edit {
                remove(KEY_WATCHLIST)
            }
            Log.d(TAG, "Cleared old local data after migration")
        } catch (e: Exception) {
            reportError(TAG, "Error clearing local data", e)
        }
    }

    /**
     * Saves watchlist entries to Firestore asynchronously.
     */
    private fun saveToFirestore(entriesToSave: List<WatchlistEntry>) {
        val username = credentialsRepository.getCredentials()?.username
        if (username.isNullOrBlank()) {
            Log.w(TAG, "Cannot save to Firestore: no username available")
            return
        }

        serviceScope.launch {
            val result = userRepository.saveWatchlist(username, entriesToSave)
            result.fold(
                onSuccess = {
                    Log.d(TAG, "Successfully saved favorites to Firestore")
                },
                onFailure = { exception ->
                    reportError(TAG, "Failed to save favorites to Firestore", exception)
                    // StateFlow already updated, so UI will reflect the change
                    // Firestore will sync when connection is restored
                }
            )
        }
    }

    /**
     * Get all watchlist entries from StateFlow.
     */
    fun getAllWatchlistEntries(): List<WatchlistEntry> {
        return _entries.value
    }

    /**
     * Add a watchlist entry.
     * Uses optimistic updates: updates local state immediately and syncs atomically with Firestore.
     * Works offline thanks to Firestore offline persistence.
     * @return true if the entry was added, false if it already exists
     */
    suspend fun addToWatchlist(entry: WatchlistEntry): Boolean {
        // Optimistic update: Update local state immediately (locked to prevent lost updates)
        val added = updateMutex.withLock {
            val currentEntries = _entries.value.toMutableList()

            // Check if the entry already exists locally
            if (currentEntries.any { it.id == entry.id }) {
                Log.d(TAG, "Favorite with id ${entry.id} already exists locally")
                false
            } else {
                currentEntries.add(entry)
                _entries.value = currentEntries
                true
            }
        }

        if (added) {
            // Try to sync atomically with Firestore (works offline with Firestore persistence)
            val username = credentialsRepository.getCredentials()?.username
            if (!username.isNullOrBlank()) {
                val result = userRepository.addToWatchlistAtomically(username, entry)
                if (result.isFailure) {
                    Log.w(TAG, "Failed to sync favorite to Firestore, will retry on next sync: ${result.exceptionOrNull()}")
                    // Don't revert local change - Firestore offline persistence will handle it
                }
            }

            Log.d(TAG, "Favorite added: id=${entry.id}, type=${entry.type}")
        }
        return added
    }

    /**
     * Remove a watchlist entry.
     * Uses optimistic updates: updates local state immediately and syncs atomically with Firestore.
     * Works offline thanks to Firestore offline persistence.
     * @return true if the entry was removed, false if it was not found
     */
    suspend fun removeFromWatchlist(entryId: String): Boolean {
        // Optimistic update (locked to prevent lost updates)
        val removed = updateMutex.withLock {
            val currentEntries = _entries.value.toMutableList()
            val didRemove = currentEntries.removeAll { it.id == entryId }
            if (didRemove) {
                _entries.value = currentEntries
            }
            didRemove
        }

        if (removed) {
            // Try to sync atomically
            val username = credentialsRepository.getCredentials()?.username
            if (!username.isNullOrBlank()) {
                val result = userRepository.removeFromWatchlistAtomically(username, entryId)
                if (result.isFailure) {
                    Log.w(TAG, "Failed to sync favorite removal to Firestore, will retry on next sync")
                }
            }

            Log.d(TAG, "Favorite removed: id=$entryId")
        }
        return removed
    }

    /**
     * Check if an item is in the watchlist.
     */
    fun isInWatchlist(entryId: String): Boolean {
        return _entries.value.any { it.id == entryId }
    }

    /**
     * Clear the whole watchlist.
     * Aborts without touching local or remote state if the Firestore pre-load fails,
     * so a transient outage can't wipe entries that never made it into the pre-load.
     */
    suspend fun clearWatchlist() {
        // 1. First synchronize to get latest data from Firestore
        val preLoadSucceeded = loadFromFirestore()
        if (!preLoadSucceeded) {
            Log.e(TAG, "Aborting watchlist clear: Firestore pre-load failed, state left untouched")
            return
        }

        // 2. Clear state and save to Firestore
        _entries.value = emptyList()
        saveToFirestore(emptyList())
        Log.d(TAG, "All favorites cleared")
    }

    /**
     * Get watchlist entries by type.
     */
    fun getWatchlistEntriesByType(type: WatchlistEntryType): List<WatchlistEntry> {
        return _entries.value.filter { it.type == type }
    }

    /**
     * Get watchlist entry count by type.
     */
    fun getWatchlistCountByType(type: WatchlistEntryType): Int {
        return _entries.value.count { it.type == type }
    }
}
