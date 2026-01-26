package com.flintr.tv.data.repository

import android.util.Log
import com.flintr.tv.data.model.CategoryType
import com.flintr.tv.data.model.Favorite
import com.flintr.tv.data.model.FavoriteType
import com.flintr.tv.data.model.WatchProgress
import com.flintr.tv.data.model.WatchedItem
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.flintr.tv.data.model.toFirestoreMap
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class UserRepository @Inject constructor() {
    
    private val firestore = FirebaseFirestore.getInstance()
    private val collectionName = "users"
    
    companion object {
        private const val TAG = "UserRepository"
        private const val ERROR_USERNAME_BLANK = "Username cannot be blank"
    }
    
    /**
     * Checks if a username already exists in Firestore.
     * 
     * @param username The username to check
     * @return Result<Boolean> - true if exists, false if not, Failure with exception on error
     */
    suspend fun usernameExists(username: String): Result<Boolean> {
        if (username.isBlank()) {
            return Result.failure(IllegalArgumentException(ERROR_USERNAME_BLANK))
        }
        
        return try {
            val documentSnapshot: DocumentSnapshot = firestore.collection(collectionName)
                .document(username)
                .get()
                .await()
            
            val exists = documentSnapshot.exists()
            Log.d(TAG, "Username existence check: $username = $exists")
            Result.success(exists)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check username existence: $username", e)
            Result.failure(e)
        }
    }
    
    /**
     * Saves username to Firestore with timestamp.
     * Uses username as document ID in "users" collection.
     * Only writes if the user doesn't already exist to preserve the original createdAt timestamp.
     * 
     * @param username The username to save
     * @return Result<Unit> - Success if saved or already exists, Failure with exception otherwise
     */
    suspend fun saveUsername(username: String): Result<Unit> {
        if (username.isBlank()) {
            return Result.failure(IllegalArgumentException(ERROR_USERNAME_BLANK))
        }
        
        // Check if username already exists
        val existsResult = usernameExists(username)
        val exists = existsResult.getOrElse { 
            // If read fails, log warning but continue with write (fallback)
            Log.w(TAG, "Failed to check username existence, attempting write anyway: $username", it)
            false
        }
        
        if (exists) {
            Log.d(TAG, "Username already exists, skipping write: $username")
            return Result.success(Unit)
        }
        
        // User doesn't exist, create new document
        return try {
            val userData = hashMapOf(
                "username" to username,
                "createdAt" to FieldValue.serverTimestamp()
            )
            
            firestore.collection(collectionName)
                .document(username)
                .set(userData)
                .await()
            
            Log.d(TAG, "Username saved successfully: $username")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save username: $username", e)
            Result.failure(e)
        }
    }
    
    /**
     * Saves hidden categories for a user to Firestore.
     * 
     * Note: The caller should synchronize with Firestore before calling this method
     * to ensure data consistency. This method saves the provided data directly,
     * with a union merge as a safety layer in case of concurrent modifications.
     * 
     * @param username The username to save categories for
     * @param hiddenCategories Map of CategoryType to Set of category IDs
     * @return Result<Unit> - Success if saved, Failure with exception otherwise
     */
    suspend fun saveHiddenCategories(
        username: String,
        hiddenCategories: Map<CategoryType, Set<String>>
    ): Result<Unit> {
        if (username.isBlank()) {
            return Result.failure(IllegalArgumentException(ERROR_USERNAME_BLANK))
        }
        
        return try {
            // Since the caller synchronizes before calling this method,
            // the provided hiddenCategories should already reflect the latest state from Firestore
            // plus any local changes. We save directly without additional merging to preserve
            // both additions and removals correctly.
            
            // Convert CategoryType enum to string keys for Firestore
            val firestoreData = hashMapOf(
                "hiddenCategories" to (hiddenCategories.mapKeys { it.key.name }
                    .mapValues { (_, value) -> value.toList() })
            )
            
            // Use set with merge to create document if it doesn't exist
            firestore.collection(collectionName)
                .document(username)
                .set(firestoreData, SetOptions.merge())
                .await()
            
            Log.d(TAG, "Hidden categories saved successfully for user: $username (${hiddenCategories.values.sumOf { it.size }} categories)")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save hidden categories for user: $username", e)
            Result.failure(e)
        }
    }
    
    /**
     * Loads hidden categories for a user from Firestore.
     * 
     * @param username The username to load categories for
     * @return Result<Map<CategoryType, Set<String>>> - Success with categories map, Failure with exception otherwise
     */
    suspend fun loadHiddenCategories(
        username: String
    ): Result<Map<CategoryType, Set<String>>> {
        if (username.isBlank()) {
            return Result.failure(IllegalArgumentException(ERROR_USERNAME_BLANK))
        }
        
        return try {
            val documentSnapshot: DocumentSnapshot = firestore.collection(collectionName)
                .document(username)
                .get()
                .await()
            
            if (!documentSnapshot.exists()) {
                Log.d(TAG, "User document does not exist: $username, returning empty map")
                return Result.success(emptyMap())
            }
            
            @Suppress("UNCHECKED_CAST")
            val hiddenCategoriesData = documentSnapshot["hiddenCategories"] as? Map<String, List<*>>
            
            if (hiddenCategoriesData == null) {
                Log.d(TAG, "No hiddenCategories field found for user: $username")
                return Result.success(emptyMap())
            }
            
            // Convert Firestore data back to Map<CategoryType, Set<String>>
            val result = hiddenCategoriesData.mapNotNull { (key, value) ->
                try {
                    val categoryType = CategoryType.valueOf(key)
                    val categoryIds = value.mapNotNull { it as? String }.toSet()
                    categoryType to categoryIds
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "Unknown category type in Firestore: $key", e)
                    null
                }
            }.toMap()
            
            Log.d(TAG, "Hidden categories loaded successfully for user: $username, count: ${result.values.sumOf { it.size }}")
            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load hidden categories for user: $username", e)
            Result.failure(e)
        }
    }
    
    /**
     * Migrates hidden categories from local storage to Firestore.
     * Merges local data with existing Firestore data (if any).
     * 
     * @param username The username to migrate categories for
     * @param localData Map of CategoryType to Set of category IDs from local storage
     * @return Result<Unit> - Success if migrated, Failure with exception otherwise
     */
    suspend fun migrateHiddenCategoriesFromLocal(
        username: String,
        localData: Map<CategoryType, Set<String>>
    ): Result<Unit> {
        if (username.isBlank()) {
            return Result.failure(IllegalArgumentException(ERROR_USERNAME_BLANK))
        }
        
        if (localData.isEmpty() || localData.values.all { it.isEmpty() }) {
            Log.d(TAG, "No local data to migrate for user: $username")
            return Result.success(Unit)
        }
        
        return try {
            // Load existing Firestore data
            val existingResult = loadHiddenCategories(username)
            val existingData = existingResult.getOrElse { emptyMap() }
            
            // Merge local data with existing Firestore data (local takes precedence for duplicates)
            val mergedData = existingData.toMutableMap()
            localData.forEach { (type, ids) ->
                val existingIds = mergedData[type] ?: emptySet()
                mergedData[type] = existingIds.union(ids)
            }
            
            // Save merged data
            val saveResult = saveHiddenCategories(username, mergedData)
            if (saveResult.isSuccess) {
                Log.d(TAG, "Successfully migrated hidden categories from local to Firestore for user: $username")
            }
            saveResult
        } catch (e: Exception) {
            Log.e(TAG, "Failed to migrate hidden categories from local to Firestore for user: $username", e)
            Result.failure(e)
        }
    }
    
    /**
     * Saves favorites for a user to Firestore.
     * 
     * WARNING: This method overwrites the entire favorites array and is susceptible to race conditions.
     * Use addFavoriteAtomically() or removeFavoriteAtomically() for individual operations instead.
     * This method is kept for backward compatibility and migration purposes only.
     * 
     * @param username The username to save favorites for
     * @param favorites List of Favorite objects
     * @return Result<Unit> - Success if saved, Failure with exception otherwise
     */
    suspend fun saveFavorites(
        username: String,
        favorites: List<Favorite>
    ): Result<Unit> {
        if (username.isBlank()) {
            return Result.failure(IllegalArgumentException(ERROR_USERNAME_BLANK))
        }
        
        return try {
            // Convert List<Favorite> to Firestore-compatible format (List of Maps)
            val firestoreData = hashMapOf(
                "favorites" to favorites.map { favorite ->
                    hashMapOf(
                        "id" to favorite.id,
                        "type" to favorite.type.name,
                        "streamId" to favorite.streamId,
                        "name" to favorite.name,
                        "metadata" to (favorite.metadata ?: emptyMap()),
                        "addedAt" to favorite.addedAt
                    )
                }
            )
            
            // Use set with merge to create document if it doesn't exist
            firestore.collection(collectionName)
                .document(username)
                .set(firestoreData, SetOptions.merge())
                .await()
            
            Log.d(TAG, "Favorites saved successfully for user: $username (${favorites.size} favorites)")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save favorites for user: $username", e)
            Result.failure(e)
        }
    }
    
    /**
     * Loads favorites for a user from Firestore.
     * 
     * @param username The username to load favorites for
     * @return Result<List<Favorite>> - Success with favorites list, Failure with exception otherwise
     */
    suspend fun loadFavorites(
        username: String
    ): Result<List<Favorite>> {
        if (username.isBlank()) {
            return Result.failure(IllegalArgumentException(ERROR_USERNAME_BLANK))
        }
        
        return try {
            val documentSnapshot: DocumentSnapshot = firestore.collection(collectionName)
                .document(username)
                .get()
                .await()
            
            if (!documentSnapshot.exists()) {
                Log.d(TAG, "User document does not exist: $username, returning empty list")
                return Result.success(emptyList())
            }
            
            @Suppress("UNCHECKED_CAST")
            val favoritesData = documentSnapshot["favorites"] as? List<Map<String, Any?>>
            
            if (favoritesData == null) {
                Log.d(TAG, "No favorites field found for user: $username")
                return Result.success(emptyList())
            }
            
            // Convert Firestore data back to List<Favorite>
            val result = favoritesData.mapNotNull { favoriteMap ->
                try {
                    val id = favoriteMap["id"] as? String ?: return@mapNotNull null
                    val typeStr = favoriteMap["type"] as? String ?: return@mapNotNull null
                    val type = FavoriteType.valueOf(typeStr)
                    val streamId = (favoriteMap["streamId"] as? Number)?.toInt() ?: return@mapNotNull null
                    val name = favoriteMap["name"] as? String ?: return@mapNotNull null
                    @Suppress("UNCHECKED_CAST")
                    val metadata = favoriteMap["metadata"] as? Map<String, String?>
                    val addedAt = favoriteMap["addedAt"] as? String ?: return@mapNotNull null
                    
                    Favorite(
                        id = id,
                        type = type,
                        streamId = streamId,
                        name = name,
                        metadata = metadata,
                        addedAt = addedAt
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing favorite from Firestore", e)
                    null
                }
            }
            
            Log.d(TAG, "Favorites loaded successfully for user: $username, count: ${result.size}")
            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load favorites for user: $username", e)
            Result.failure(e)
        }
    }
    
    /**
     * Adds a favorite atomically using Firestore transaction to prevent race conditions.
     * 
     * @param username The username to add favorite for
     * @param favorite The favorite to add
     * @return Result<Unit> - Success if added, Failure with exception otherwise
     */
    suspend fun addFavoriteAtomically(
        username: String,
        favorite: Favorite
    ): Result<Unit> {
        if (username.isBlank()) {
            return Result.failure(IllegalArgumentException(ERROR_USERNAME_BLANK))
        }
        
        val userDocRef = firestore.collection(collectionName).document(username)
        
        return try {
            firestore.runTransaction { transaction ->
                val snapshot = transaction[userDocRef]

                @Suppress("UNCHECKED_CAST")
                val favoritesData = snapshot["favorites"] as? List<Map<String, Any?>> ?: emptyList()

                // Check if favorite already exists
                if (favoritesData.any { (it["id"] as? String) == favorite.id }) {
                    // Favorite already exists, transaction succeeds without changes
                    return@runTransaction
                }
                
                // Add new favorite
                val newFavoritesList = favoritesData + favorite.toFirestoreMap()
                transaction.update(userDocRef, "favorites", newFavoritesList)
            }.await()
            
            Log.d(TAG, "Favorite added atomically for user: $username, id=${favorite.id}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add favorite atomically for user: $username", e)
            Result.failure(e)
        }
    }
    
    /**
     * Removes a favorite atomically using Firestore transaction to prevent race conditions.
     * 
     * @param username The username to remove favorite for
     * @param favoriteId The ID of the favorite to remove
     * @return Result<Unit> - Success if removed, Failure with exception otherwise
     */
    suspend fun removeFavoriteAtomically(
        username: String,
        favoriteId: String
    ): Result<Unit> {
        if (username.isBlank()) {
            return Result.failure(IllegalArgumentException(ERROR_USERNAME_BLANK))
        }
        
        val userDocRef = firestore.collection(collectionName).document(username)
        
        return try {
            firestore.runTransaction { transaction ->
                val snapshot = transaction[userDocRef]

                if (!snapshot.exists()) {
                    // Document doesn't exist, nothing to remove
                    return@runTransaction
                }
                
                @Suppress("UNCHECKED_CAST")
                val favoritesData = snapshot["favorites"] as? List<Map<String, Any?>> ?: emptyList()
                
                // Remove favorite by id
                val updatedFavoritesList = favoritesData.filterNot { (it["id"] as? String) == favoriteId }
                
                // Only update if something was actually removed
                if (updatedFavoritesList.size < favoritesData.size) {
                    transaction.update(userDocRef, "favorites", updatedFavoritesList)
                }
            }.await()
            
            Log.d(TAG, "Favorite removed atomically for user: $username, id=$favoriteId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove favorite atomically for user: $username", e)
            Result.failure(e)
        }
    }
    
    /**
     * Migrates favorites from local storage to Firestore.
     * Merges local data with existing Firestore data (if any), preventing duplicates based on favorite.id.
     * 
     * @param username The username to migrate favorites for
     * @param localFavorites List of Favorite objects from local storage
     * @return Result<Unit> - Success if migrated, Failure with exception otherwise
     */
    suspend fun migrateFavoritesFromLocal(
        username: String,
        localFavorites: List<Favorite>
    ): Result<Unit> {
        if (username.isBlank()) {
            return Result.failure(IllegalArgumentException(ERROR_USERNAME_BLANK))
        }
        
        if (localFavorites.isEmpty()) {
            Log.d(TAG, "No local favorites to migrate for user: $username")
            return Result.success(Unit)
        }
        
        val userDocRef = firestore.collection(collectionName).document(username)
        
        return try {
            firestore.runTransaction { transaction ->
                val snapshot = transaction[userDocRef]

                @Suppress("UNCHECKED_CAST")
                val existingFavoritesData = snapshot["favorites"] as? List<Map<String, Any?>> ?: emptyList()

                // Parse existing favorites
                val existingFavorites = existingFavoritesData.mapNotNull { favoriteMap ->
                    try {
                        val id = favoriteMap["id"] as? String ?: return@mapNotNull null
                        val typeStr = favoriteMap["type"] as? String ?: return@mapNotNull null
                        val type = FavoriteType.valueOf(typeStr)
                        val streamId = (favoriteMap["streamId"] as? Number)?.toInt() ?: return@mapNotNull null
                        val name = favoriteMap["name"] as? String ?: return@mapNotNull null
                        @Suppress("UNCHECKED_CAST")
                        val metadata = favoriteMap["metadata"] as? Map<String, String?>
                        val addedAt = favoriteMap["addedAt"] as? String ?: return@mapNotNull null
                        
                        Favorite(
                            id = id,
                            type = type,
                            streamId = streamId,
                            name = name,
                            metadata = metadata,
                            addedAt = addedAt
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Error parsing favorite from Firestore during migration", e)
                        null
                    }
                }
                
                // Merge logic
                val existingIds = existingFavorites.map { it.id }.toSet()
                val newFavorites = localFavorites.filter { it.id !in existingIds }
                
                if (newFavorites.isEmpty()) {
                    return@runTransaction // No changes needed
                }
                
                val mergedFavorites = existingFavorites + newFavorites
                val mergedFavoritesData = mergedFavorites.map { it.toFirestoreMap() }
                
                transaction.set(userDocRef, mapOf("favorites" to mergedFavoritesData), SetOptions.merge())
            }.await()
            
            Log.d(TAG, "Successfully migrated favorites from local to Firestore for user: $username")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to migrate favorites from local to Firestore for user: $username", e)
            Result.failure(e)
        }
    }
    
    /**
     * Generic helper to save a user data map to Firestore.
     */
    private suspend fun <T> saveUserDataMap(
        username: String,
        fieldName: String,
        data: Map<String, T>,
        toFirestoreMap: (T) -> Map<String, Any?>,
        successLogMessage: String
    ): Result<Unit> {
        if (username.isBlank()) {
            return Result.failure(IllegalArgumentException(ERROR_USERNAME_BLANK))
        }
        
        return try {
            val firestoreData = hashMapOf(
                fieldName to data.mapValues { (_, value) -> toFirestoreMap(value) }
            )
            
            firestore.collection(collectionName)
                .document(username)
                .set(firestoreData, SetOptions.merge())
                .await()
            
            Log.d(TAG, "$successLogMessage for user: $username (${data.size} entries)")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save '$fieldName' for user: $username", e)
            Result.failure(e)
        }
    }
    
    /**
     * Saves watch progress for a user to Firestore.
     * 
     * @param username The username to save progress for
     * @param progress Map of progress keys (e.g., "movie:123" or "series:456:789") to WatchProgress objects
     * @return Result<Unit> - Success if saved, Failure with exception otherwise
     */
    suspend fun saveWatchProgress(
        username: String,
        progress: Map<String, WatchProgress>
    ): Result<Unit> {
        return saveUserDataMap(
            username = username,
            fieldName = "watchProgress",
            data = progress,
            toFirestoreMap = { watchProgress ->
                hashMapOf(
                    "position" to watchProgress.position,
                    "duration" to watchProgress.duration,
                    "lastWatched" to watchProgress.lastWatched,
                    "title" to watchProgress.title
                )
            },
            successLogMessage = "Watch progress saved successfully"
        )
    }
    
    /**
     * Generic helper to load a user data map from Firestore.
     */
    private suspend fun <T> loadUserDataMap(
        username: String,
        fieldName: String,
        parser: (key: String, itemMap: Map<String, Any?>) -> Pair<String, T>?
    ): Result<Map<String, T>> {
        if (username.isBlank()) {
            return Result.failure(IllegalArgumentException(ERROR_USERNAME_BLANK))
        }
        
        return try {
            val documentSnapshot: DocumentSnapshot = firestore.collection(collectionName)
                .document(username)
                .get()
                .await()
            
            if (!documentSnapshot.exists()) {
                Log.d(TAG, "User document does not exist: $username, returning empty map for field $fieldName")
                return Result.success(emptyMap())
            }
            
            @Suppress("UNCHECKED_CAST")
            val data = documentSnapshot[fieldName] as? Map<String, Map<String, Any?>>
            
            if (data == null) {
                Log.d(TAG, "No '$fieldName' field found for user: $username")
                return Result.success(emptyMap())
            }
            
            val result = data.mapNotNull { (key, itemMap) ->
                try {
                    parser(key, itemMap)
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing item from Firestore for field '$fieldName' with key: $key", e)
                    null
                }
            }.toMap()
            
            Log.d(TAG, "'$fieldName' loaded successfully for user: $username, count: ${result.size}")
            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load '$fieldName' for user: $username", e)
            Result.failure(e)
        }
    }
    
    /**
     * Loads watch progress for a user from Firestore.
     * 
     * @param username The username to load progress for
     * @return Result<Map<String, WatchProgress>> - Success with progress map, Failure with exception otherwise
     */
    suspend fun loadWatchProgress(
        username: String
    ): Result<Map<String, WatchProgress>> {
        return loadUserDataMap(
            username = username,
            fieldName = "watchProgress",
            parser = { key, progressMap ->
                val position = (progressMap["position"] as? Number)?.toLong() ?: return@loadUserDataMap null
                val duration = (progressMap["duration"] as? Number)?.toLong() ?: return@loadUserDataMap null
                val lastWatched = progressMap["lastWatched"] as? String ?: return@loadUserDataMap null
                val title = progressMap["title"] as? String ?: return@loadUserDataMap null
                
                key to WatchProgress(
                    position = position,
                    duration = duration,
                    lastWatched = lastWatched,
                    title = title
                )
            }
        )
    }
    
    /**
     * Generic helper to migrate user data map from local storage to Firestore.
     */
    private suspend fun <T> migrateUserDataMapFromLocal(
        username: String,
        fieldName: String,
        localData: Map<String, T>,
        parser: (key: String, itemMap: Map<String, Any?>) -> Pair<String, T>?,
        toFirestoreMap: (T) -> Map<String, Any?>,
        emptyDataMessage: String,
        successLogMessage: String
    ): Result<Unit> {
        if (username.isBlank()) {
            return Result.failure(IllegalArgumentException(ERROR_USERNAME_BLANK))
        }
        
        if (localData.isEmpty()) {
            Log.d(TAG, "$emptyDataMessage for user: $username")
            return Result.success(Unit)
        }
        
        val userDocRef = firestore.collection(collectionName).document(username)
        
        return try {
            firestore.runTransaction { transaction ->
                val snapshot = transaction[userDocRef]

                @Suppress("UNCHECKED_CAST")
                val existingData = snapshot[fieldName] as? Map<String, Map<String, Any?>> ?: emptyMap()
                
                // Parse existing data from Firestore
                val existingParsed = existingData.mapNotNull { (key, itemMap) ->
                    try {
                        parser(key, itemMap)
                    } catch (e: Exception) {
                        Log.w(TAG, "Error parsing item from Firestore during migration for field '$fieldName' with key: $key", e)
                        null
                    }
                }.toMap()
                
                // Merge logic: local data takes precedence for duplicate keys
                val merged = existingParsed.toMutableMap()
                localData.forEach { (key, value) ->
                    merged[key] = value
                }
                
                // Convert merged data to Firestore format
                val mergedData = merged.mapValues { (_, value) -> toFirestoreMap(value) }
                
                transaction.set(userDocRef, mapOf(fieldName to mergedData), SetOptions.merge())
            }.await()
            
            Log.d(TAG, "$successLogMessage for user: $username")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to migrate '$fieldName' from local to Firestore for user: $username", e)
            Result.failure(e)
        }
    }
    
    /**
     * Migrates watch progress from local storage to Firestore.
     * Merges local data with existing Firestore data (if any), preventing duplicates based on progress keys.
     * Local data takes precedence over Firestore data for duplicate keys.
     * 
     * @param username The username to migrate progress for
     * @param localProgress Map of progress keys to WatchProgress objects from local storage
     * @return Result<Unit> - Success if migrated, Failure with exception otherwise
     */
    suspend fun migrateWatchProgressFromLocal(
        username: String,
        localProgress: Map<String, WatchProgress>
    ): Result<Unit> {
        return migrateUserDataMapFromLocal(
            username = username,
            fieldName = "watchProgress",
            localData = localProgress,
            parser = { key, progressMap ->
                val position = (progressMap["position"] as? Number)?.toLong() ?: return@migrateUserDataMapFromLocal null
                val duration = (progressMap["duration"] as? Number)?.toLong() ?: return@migrateUserDataMapFromLocal null
                val lastWatched = progressMap["lastWatched"] as? String ?: return@migrateUserDataMapFromLocal null
                val title = progressMap["title"] as? String ?: return@migrateUserDataMapFromLocal null
                
                key to WatchProgress(
                    position = position,
                    duration = duration,
                    lastWatched = lastWatched,
                    title = title
                )
            },
            toFirestoreMap = { watchProgress ->
                hashMapOf(
                    "position" to watchProgress.position,
                    "duration" to watchProgress.duration,
                    "lastWatched" to watchProgress.lastWatched,
                    "title" to watchProgress.title
                )
            },
            emptyDataMessage = "No local watch progress to migrate",
            successLogMessage = "Successfully migrated watch progress from local to Firestore"
        )
    }
    
    /**
     * Saves watched history for a user to Firestore.
     * 
     * @param username The username to save history for
     * @param watchedHistory Map of history keys (e.g., "movie:123" or "series:456:789") to WatchedItem objects
     * @return Result<Unit> - Success if saved, Failure with exception otherwise
     */
    suspend fun saveWatchedHistory(
        username: String,
        watchedHistory: Map<String, WatchedItem>
    ): Result<Unit> {
        return saveUserDataMap(
            username = username,
            fieldName = "watchedHistory",
            data = watchedHistory,
            toFirestoreMap = { watchedItem ->
                hashMapOf(
                    "contentType" to watchedItem.contentType,
                    "streamId" to watchedItem.streamId,
                    "seriesId" to watchedItem.seriesId,
                    "episodeId" to watchedItem.episodeId,
                    "watchedAt" to watchedItem.watchedAt
                )
            },
            successLogMessage = "Watched history saved successfully"
        )
    }
    
    /**
     * Loads watched history for a user from Firestore.
     * 
     * @param username The username to load history for
     * @return Result<Map<String, WatchedItem>> - Success with history map, Failure with exception otherwise
     */
    suspend fun loadWatchedHistory(
        username: String
    ): Result<Map<String, WatchedItem>> {
        return loadUserDataMap(
            username = username,
            fieldName = "watchedHistory",
            parser = { key, itemMap ->
                val contentType = itemMap["contentType"] as? String ?: return@loadUserDataMap null
                val streamId = (itemMap["streamId"] as? Number)?.toInt()
                val seriesId = (itemMap["seriesId"] as? Number)?.toInt()
                val episodeId = (itemMap["episodeId"] as? Number)?.toInt()
                val watchedAt = itemMap["watchedAt"] as? String ?: return@loadUserDataMap null
                
                key to WatchedItem(
                    contentType = contentType,
                    streamId = streamId,
                    seriesId = seriesId,
                    episodeId = episodeId,
                    watchedAt = watchedAt
                )
            }
        )
    }
    
    /**
     * Migrates watched history from local storage to Firestore.
     * Merges local data with existing Firestore data (if any), preventing duplicates based on history keys.
     * Local data takes precedence over Firestore data for duplicate keys.
     * 
     * @param username The username to migrate history for
     * @param localHistory Map of history keys to WatchedItem objects from local storage
     * @return Result<Unit> - Success if migrated, Failure with exception otherwise
     */
    suspend fun migrateWatchedHistoryFromLocal(
        username: String,
        localHistory: Map<String, WatchedItem>
    ): Result<Unit> {
        return migrateUserDataMapFromLocal(
            username = username,
            fieldName = "watchedHistory",
            localData = localHistory,
            parser = { key, itemMap ->
                val contentType = itemMap["contentType"] as? String ?: return@migrateUserDataMapFromLocal null
                val streamId = (itemMap["streamId"] as? Number)?.toInt()
                val seriesId = (itemMap["seriesId"] as? Number)?.toInt()
                val episodeId = (itemMap["episodeId"] as? Number)?.toInt()
                val watchedAt = itemMap["watchedAt"] as? String ?: return@migrateUserDataMapFromLocal null
                
                key to WatchedItem(
                    contentType = contentType,
                    streamId = streamId,
                    seriesId = seriesId,
                    episodeId = episodeId,
                    watchedAt = watchedAt
                )
            },
            toFirestoreMap = { watchedItem ->
                hashMapOf(
                    "contentType" to watchedItem.contentType,
                    "streamId" to watchedItem.streamId,
                    "seriesId" to watchedItem.seriesId,
                    "episodeId" to watchedItem.episodeId,
                    "watchedAt" to watchedItem.watchedAt
                )
            },
            emptyDataMessage = "No local watched history to migrate",
            successLogMessage = "Successfully migrated watched history from local to Firestore"
        )
    }
    
    /**
     * Extension function to convert Firebase Task to suspend function
     */
    private suspend fun <T> Task<T>.await(): T {
        return suspendCancellableCoroutine { cont ->
            addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    cont.resume(task.result)
                } else {
                    val exception = task.exception ?: Exception("Unknown error")
                    cont.resumeWithException(exception)
                }
            }
        }
    }
}
