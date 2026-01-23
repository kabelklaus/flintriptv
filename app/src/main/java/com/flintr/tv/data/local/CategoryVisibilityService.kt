package com.flintr.tv.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.flintr.tv.data.model.CategoryType
import com.flintr.tv.data.repository.UserRepository
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
class CategoryVisibilityService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userRepository: UserRepository,
    private val credentialsRepository: CredentialsRepository,
    @Named("CategoryVisibility") private val syncManager: FirestoreSyncManager
) {
    // SharedPreferences only for migration flag and reading old data
    private val migrationPrefs: SharedPreferences = context.getSharedPreferences(
        "category_visibility_prefs",
        Context.MODE_PRIVATE
    )

    companion object {
        private const val KEY_HIDDEN_LIVE_CATEGORIES = "iptv_hidden_live_categories"
        private const val KEY_HIDDEN_VOD_CATEGORIES = "iptv_hidden_vod_categories"
        private const val KEY_HIDDEN_SERIES_CATEGORIES = "iptv_hidden_series_categories"
        private const val KEY_MIGRATION_COMPLETE = "category_visibility_migrated"
        private const val TAG = "CategoryVisibilityService"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // StateFlow for reactive state management
    private val _hiddenCategories = MutableStateFlow<Map<CategoryType, Set<String>>>(emptyMap())
    val hiddenCategories: StateFlow<Map<CategoryType, Set<String>>> = _hiddenCategories.asStateFlow()

    // Mutex to prevent concurrent loadFromFirestore() calls
    private val loadMutex = Mutex()

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
                    _hiddenCategories.value = emptyMap()
                    Log.d(TAG, "Credentials cleared, resetting hidden categories")
                }
            }
        }
    }

    /**
     * Loads hidden categories from Firestore and performs migration if needed.
     * Uses mutex to prevent concurrent loads.
     */
    private suspend fun loadFromFirestore() {
        loadMutex.withLock {
            val username = credentialsRepository.getCredentials()?.username
            if (username.isNullOrBlank()) {
                Log.d(TAG, "No username available, skipping Firestore load due to missing credentials")
                _hiddenCategories.value = emptyMap()
                return
            }

            // Check if migration is needed
            val migrationComplete = migrationPrefs.getBoolean(KEY_MIGRATION_COMPLETE, false)
            if (!migrationComplete) {
                performMigration(username)
            }

            // Load from Firestore
            val result = userRepository.loadHiddenCategories(username)
            result.fold(
                onSuccess = { categories ->
                    _hiddenCategories.value = categories
                    syncManager.updateLastSyncTimestamp()
                    Log.d(TAG, "Loaded hidden categories from Firestore: ${categories.values.sumOf { it.size }} total")
                },
                onFailure = { exception ->
                    Log.e(TAG, "Failed to load hidden categories from Firestore", exception)
                    // Keep current state or use empty map
                    if (_hiddenCategories.value.isEmpty()) {
                        Log.e(TAG, "State is empty after failed Firestore load - user data may be unavailable")
                        _hiddenCategories.value = emptyMap()
                    }
                }
            )
        }
    }

    /**
     * Synchronizes hidden categories from Firestore if enough time has passed since last sync.
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
            
            if (localData.isNotEmpty() && localData.values.any { it.isNotEmpty() }) {
                // Migrate to Firestore
                val migrationResult = userRepository.migrateHiddenCategoriesFromLocal(username, localData)
                
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
    private fun loadLocalData(): Map<CategoryType, Set<String>> {
        return try {
            val liveCategories = loadLocalCategoriesForKey(KEY_HIDDEN_LIVE_CATEGORIES)
            val vodCategories = loadLocalCategoriesForKey(KEY_HIDDEN_VOD_CATEGORIES)
            val seriesCategories = loadLocalCategoriesForKey(KEY_HIDDEN_SERIES_CATEGORIES)
            
            mapOf(
                CategoryType.LIVE_TV to liveCategories,
                CategoryType.VOD to vodCategories,
                CategoryType.SERIES to seriesCategories
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error loading local data for migration", e)
            emptyMap()
        }
    }

    /**
     * Loads categories from SharedPreferences for a specific key.
     * Uses simple comma-separated string format as fallback if JSON parsing fails.
     */
    private fun loadLocalCategoriesForKey(key: String): Set<String> {
        return try {
            val json = migrationPrefs.getString(key, null)
            if (json.isNullOrBlank()) {
                emptySet()
            } else {
                // Try to parse as JSON array (old format)
                // Simple parsing: remove brackets and quotes, split by comma
                val cleaned = json.trim()
                    .removePrefix("[")
                    .removeSuffix("]")
                    .trim()
                
                if (cleaned.isEmpty()) {
                    emptySet()
                } else {
                    cleaned.split(",")
                        .map { it.trim().removeSurrounding("\"") }
                        .filter { it.isNotBlank() }
                        .toSet()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing local categories for key: $key", e)
            emptySet()
        }
    }

    /**
     * Clears old local data after successful migration.
     */
    private fun clearLocalData() {
        try {
            migrationPrefs.edit {
                remove(KEY_HIDDEN_LIVE_CATEGORIES)
                remove(KEY_HIDDEN_VOD_CATEGORIES)
                remove(KEY_HIDDEN_SERIES_CATEGORIES)
            }
            Log.d(TAG, "Cleared old local data after migration")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing local data", e)
        }
    }

    /**
     * Hide a category by adding its ID to the hidden set.
     * Synchronizes with Firestore before applying the change to ensure data consistency.
     * @return true if category was hidden, false if it was already hidden
     */
    suspend fun hideCategory(categoryId: String, categoryType: CategoryType): Boolean {
        // 1. First synchronize to get latest data from Firestore (ignore cooldown for critical operations)
        try {
            loadFromFirestore()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load from Firestore before hiding category", e)
            return false // Critical operation should not proceed with stale data
        }
        
        // 2. Apply change to synchronized data
        val currentState = _hiddenCategories.value.toMutableMap()
        val hiddenCategories = currentState.getOrDefault(categoryType, emptySet()).toMutableSet()
        val wasAdded = hiddenCategories.add(categoryId)
        if (wasAdded) {
            currentState[categoryType] = hiddenCategories
            _hiddenCategories.value = currentState
            saveToFirestore(currentState)
            Log.d(TAG, "Category hidden: categoryId=$categoryId, type=$categoryType")
        }
        return wasAdded
    }

    /**
     * Show a category by removing its ID from the hidden set.
     * Synchronizes with Firestore before applying the change to ensure data consistency.
     * @return true if category was shown, false if it was not hidden
     */
    suspend fun showCategory(categoryId: String, categoryType: CategoryType): Boolean {
        // 1. First synchronize to get latest data from Firestore (ignore cooldown for critical operations)
        try {
            loadFromFirestore()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load from Firestore before showing category", e)
            return false // Critical operation should not proceed with stale data
        }
        
        // 2. Apply change to synchronized data
        val currentState = _hiddenCategories.value.toMutableMap()
        val hiddenCategories = currentState.getOrDefault(categoryType, emptySet()).toMutableSet()
        val wasRemoved = hiddenCategories.remove(categoryId)
        if (wasRemoved) {
            currentState[categoryType] = hiddenCategories
            _hiddenCategories.value = currentState
            saveToFirestore(currentState)
            Log.d(TAG, "Category shown: categoryId=$categoryId, type=$categoryType")
        }
        return wasRemoved
    }

    /**
     * Saves hidden categories to Firestore asynchronously.
     */
    private fun saveToFirestore(categories: Map<CategoryType, Set<String>>) {
        val username = credentialsRepository.getCredentials()?.username
        if (username.isNullOrBlank()) {
            Log.w(TAG, "Cannot save to Firestore: no username available")
            return
        }

        serviceScope.launch {
            val result = userRepository.saveHiddenCategories(username, categories)
            result.fold(
                onSuccess = {
                    Log.d(TAG, "Successfully saved hidden categories to Firestore")
                },
                onFailure = { exception ->
                    Log.e(TAG, "Failed to save hidden categories to Firestore", exception)
                    // StateFlow already updated, so UI will reflect the change
                    // Firestore will sync when connection is restored
                }
            )
        }
    }

    /**
     * Check if a category is hidden
     */
    fun isCategoryHidden(categoryId: String, categoryType: CategoryType): Boolean {
        return _hiddenCategories.value[categoryType]?.contains(categoryId) ?: false
    }

    /**
     * Get all hidden category IDs for the given type
     */
    fun getHiddenCategories(categoryType: CategoryType): Set<String> {
        return _hiddenCategories.value[categoryType] ?: emptySet()
    }

    /**
     * Get all hidden categories for all types
     */
    fun getAllHiddenCategories(): Map<CategoryType, Set<String>> {
        return _hiddenCategories.value
    }
}
