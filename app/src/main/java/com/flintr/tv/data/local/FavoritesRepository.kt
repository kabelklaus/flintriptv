package com.flintr.tv.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.flintr.tv.data.model.Favorite
import com.flintr.tv.data.model.FavoriteType
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

@Singleton
class FavoritesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi,
    private val userRepository: UserRepository,
    private val credentialsRepository: CredentialsRepository,
    @Named("Favorites") private val syncManager: FirestoreSyncManager
) {
    // SharedPreferences only for migration flag and reading old data
    private val migrationPrefs: SharedPreferences = context.getSharedPreferences(
        "favorites_prefs",
        Context.MODE_PRIVATE
    )

    companion object {
        private const val KEY_FAVORITES = "iptv_favorites"
        private const val KEY_MIGRATION_COMPLETE = "favorites_migrated"
        private const val TAG = "FavoritesRepository"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // StateFlow for reactive state management
    private val _favorites = MutableStateFlow<List<Favorite>>(emptyList())
    val favorites: StateFlow<List<Favorite>> = _favorites.asStateFlow()

    // Mutex to prevent concurrent loadFromFirestore() calls
    private val loadMutex = Mutex()

    // Moshi adapter for local data migration
    private val listType = Types.newParameterizedType(List::class.java, Favorite::class.java)
    private val listAdapter = moshi.adapter<List<Favorite>>(listType)

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
                    _favorites.value = emptyList()
                    Log.d(TAG, "Credentials cleared, resetting favorites")
                }
            }
        }
    }

    /**
     * Loads favorites from Firestore and performs migration if needed.
     * Uses mutex to prevent concurrent loads.
     */
    private suspend fun loadFromFirestore() {
        loadMutex.withLock {
            val username = credentialsRepository.getCredentials()?.username
            if (username.isNullOrBlank()) {
                Log.d(TAG, "No username available, skipping Firestore load due to missing credentials")
                _favorites.value = emptyList()
                return
            }

            // Check if migration is needed
            val migrationComplete = migrationPrefs.getBoolean(KEY_MIGRATION_COMPLETE, false)
            if (!migrationComplete) {
                performMigration(username)
            }

            // Load from Firestore
            val result = userRepository.loadFavorites(username)
            result.fold(
                onSuccess = { favorites ->
                    _favorites.value = favorites
                    syncManager.updateLastSyncTimestamp()
                    Log.d(TAG, "Loaded favorites from Firestore: ${favorites.size} total")
                },
                onFailure = { exception ->
                    Log.e(TAG, "Failed to load favorites from Firestore", exception)
                    // Keep current state or use empty list
                    if (_favorites.value.isEmpty()) {
                        Log.e(TAG, "State is empty after failed Firestore load - user data may be unavailable")
                        _favorites.value = emptyList()
                    }
                }
            )
        }
    }

    /**
     * Synchronizes favorites from Firestore if enough time has passed since last sync.
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
                val migrationResult = userRepository.migrateFavoritesFromLocal(username, localData)
                
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
    private fun loadLocalData(): List<Favorite> {
        return try {
            val json = migrationPrefs.getString(KEY_FAVORITES, null)
            if (json.isNullOrBlank()) {
                emptyList()
            } else {
                listAdapter.fromJson(json) ?: emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading local data for migration", e)
            emptyList()
        }
    }

    /**
     * Clears old local data after successful migration.
     */
    private fun clearLocalData() {
        try {
            migrationPrefs.edit {
                remove(KEY_FAVORITES)
            }
            Log.d(TAG, "Cleared old local data after migration")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing local data", e)
        }
    }

    /**
     * Saves favorites to Firestore asynchronously.
     */
    private fun saveToFirestore(favorites: List<Favorite>) {
        val username = credentialsRepository.getCredentials()?.username
        if (username.isNullOrBlank()) {
            Log.w(TAG, "Cannot save to Firestore: no username available")
            return
        }

        serviceScope.launch {
            val result = userRepository.saveFavorites(username, favorites)
            result.fold(
                onSuccess = {
                    Log.d(TAG, "Successfully saved favorites to Firestore")
                },
                onFailure = { exception ->
                    Log.e(TAG, "Failed to save favorites to Firestore", exception)
                    // StateFlow already updated, so UI will reflect the change
                    // Firestore will sync when connection is restored
                }
            )
        }
    }

    /**
     * Get all favorites from StateFlow.
     */
    fun getAllFavorites(): List<Favorite> {
        return _favorites.value
    }

    /**
     * Add a favorite.
     * Uses optimistic updates: updates local state immediately and syncs atomically with Firestore.
     * Works offline thanks to Firestore offline persistence.
     * @return true if favorite was added, false if it already exists
     */
    suspend fun addFavorite(favorite: Favorite): Boolean {
        // Optimistic update: Update local state immediately
        val currentFavorites = _favorites.value.toMutableList()
        
        // Check if favorite already exists locally
        if (currentFavorites.any { it.id == favorite.id }) {
            Log.d(TAG, "Favorite with id ${favorite.id} already exists locally")
            return false
        }
        
        // Update local state optimistically
        currentFavorites.add(favorite)
        _favorites.value = currentFavorites
        
        // Try to sync atomically with Firestore (works offline with Firestore persistence)
        val username = credentialsRepository.getCredentials()?.username
        if (!username.isNullOrBlank()) {
            val result = userRepository.addFavoriteAtomically(username, favorite)
            if (result.isFailure) {
                Log.w(TAG, "Failed to sync favorite to Firestore, will retry on next sync: ${result.exceptionOrNull()}")
                // Don't revert local change - Firestore offline persistence will handle it
            }
        }
        
        Log.d(TAG, "Favorite added: id=${favorite.id}, type=${favorite.type}")
        return true
    }

    /**
     * Remove a favorite.
     * Uses optimistic updates: updates local state immediately and syncs atomically with Firestore.
     * Works offline thanks to Firestore offline persistence.
     * @return true if favorite was removed, false if it was not found
     */
    suspend fun removeFavorite(favoriteId: String): Boolean {
        // Optimistic update
        val currentFavorites = _favorites.value.toMutableList()
        val removed = currentFavorites.removeAll { it.id == favoriteId }
        
        if (removed) {
            _favorites.value = currentFavorites
            
            // Try to sync atomically
            val username = credentialsRepository.getCredentials()?.username
            if (!username.isNullOrBlank()) {
                val result = userRepository.removeFavoriteAtomically(username, favoriteId)
                if (result.isFailure) {
                    Log.w(TAG, "Failed to sync favorite removal to Firestore, will retry on next sync")
                }
            }
            
            Log.d(TAG, "Favorite removed: id=$favoriteId")
        }
        return removed
    }

    /**
     * Check if an item is a favorite.
     */
    fun isFavorite(favoriteId: String): Boolean {
        return _favorites.value.any { it.id == favoriteId }
    }

    /**
     * Clear all favorites.
     */
    suspend fun clearAllFavorites() {
        // 1. First synchronize to get latest data from Firestore
        try {
            loadFromFirestore()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load from Firestore before clearing favorites", e)
            return
        }
        
        // 2. Clear state and save to Firestore
        _favorites.value = emptyList()
        saveToFirestore(emptyList())
        Log.d(TAG, "All favorites cleared")
    }

    /**
     * Get favorites by type.
     */
    fun getFavoritesByType(type: FavoriteType): List<Favorite> {
        return _favorites.value.filter { it.type == type }
    }

    /**
     * Get favorite count by type.
     */
    fun getFavoriteCountByType(type: FavoriteType): Int {
        return _favorites.value.count { it.type == type }
    }
}
