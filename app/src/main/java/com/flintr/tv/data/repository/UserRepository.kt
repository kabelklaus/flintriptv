package com.flintr.tv.data.repository

import android.util.Log
import com.flintr.tv.data.model.CategoryType
import com.flintr.tv.data.model.WatchlistEntry
import com.flintr.tv.data.model.WatchlistEntryType
import com.flintr.tv.data.model.FriendEntry
import com.flintr.tv.data.model.RatingEntry
import com.flintr.tv.data.model.WatchProgress
import com.flintr.tv.data.model.WatchedItem
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.flintr.tv.data.model.toFirestoreMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.flintr.tv.utils.error.reportError
import com.flintr.tv.utils.error.reportWarning

@Singleton
class UserRepository @Inject constructor() {

    private val firestore = FirebaseFirestore.getInstance()
    private val collectionName = "users"

    companion object {
        private const val TAG = "UserRepository"
        private const val ERROR_USERNAME_BLANK = "Username cannot be blank"
        private const val ERROR_FRIEND_ALREADY_EXISTS = "Friend already exists"
        private const val ERROR_CANNOT_ADD_SELF = "Cannot add yourself as friend"

        // Firestore Tasks have no built-in timeout: if the underlying transport never
        // resolves the Task (e.g. Play Services rejects the calling app with
        // DEVELOPER_ERROR), .await() suspends forever with no error and no log line.
        private const val FIRESTORE_TIMEOUT_MS = 15_000L
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
            Log.d(TAG, "Username existence check completed: exists=$exists")
            Result.success(exists)
        } catch (e: Exception) {
            reportError(TAG, "Failed to check username existence", e)
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
            // Never write blindly: a set() on an existing document would replace it
            // and wipe watchedHistory/watchProgress/favorites of that user.
            reportError(TAG, "Failed to check username existence, aborting write to protect existing data", it)
            return Result.failure(it)
        }

        if (exists) {
            Log.d(TAG, "Username already exists, skipping write")
            return Result.success(Unit)
        }

        // User doesn't exist, create new document.
        // merge() as safety net: even if the existence check was wrong,
        // existing fields can never be wiped by this write.
        return try {
            val userData = hashMapOf(
                "username" to username,
                "createdAt" to FieldValue.serverTimestamp()
            )

            firestore.collection(collectionName)
                .document(username)
                .set(userData, SetOptions.merge())
                .await()
            
            Log.d(TAG, "Username saved successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            reportError(TAG, "Failed to save username", e)
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
            
            Log.d(TAG, "Hidden categories saved successfully (${hiddenCategories.values.sumOf { it.size }} categories)")
            Result.success(Unit)
        } catch (e: Exception) {
            reportError(TAG, "Failed to save hidden categories", e)
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
                Log.d(TAG, "User document does not exist, returning empty map")
                return Result.success(emptyMap())
            }
            
            @Suppress("UNCHECKED_CAST")
            val hiddenCategoriesData = documentSnapshot["hiddenCategories"] as? Map<String, List<*>>
            
            if (hiddenCategoriesData == null) {
                Log.d(TAG, "No hiddenCategories field found")
                return Result.success(emptyMap())
            }
            
            // Convert Firestore data back to Map<CategoryType, Set<String>>
            val result = hiddenCategoriesData.mapNotNull { (key, value) ->
                try {
                    val categoryType = CategoryType.valueOf(key)
                    val categoryIds = value.mapNotNull { it as? String }.toSet()
                    categoryType to categoryIds
                } catch (e: IllegalArgumentException) {
                    reportWarning(TAG, "Unknown category type in Firestore: $key", e)
                    null
                }
            }.toMap()
            
            Log.d(TAG, "Hidden categories loaded successfully, count: ${result.values.sumOf { it.size }}")
            Result.success(result)
        } catch (e: Exception) {
            reportError(TAG, "Failed to load hidden categories", e)
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
            Log.d(TAG, "No local hidden categories to migrate")
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
                Log.d(TAG, "Successfully migrated hidden categories from local to Firestore")
            }
            saveResult
        } catch (e: Exception) {
            reportError(TAG, "Failed to migrate hidden categories from local to Firestore", e)
            Result.failure(e)
        }
    }
    
    /**
     * Saves the watchlist for a user to Firestore.
     *
     * WARNING: This method overwrites the entire "favorites" array and is susceptible to race conditions.
     * Use addToWatchlistAtomically() or removeFromWatchlistAtomically() for individual operations instead.
     * This method is kept for backward compatibility and migration purposes only.
     *
     * @param username The username to save the watchlist for
     * @param entries List of WatchlistEntry objects
     * @return Result<Unit> - Success if saved, Failure with exception otherwise
     */
    suspend fun saveWatchlist(
        username: String,
        entries: List<WatchlistEntry>
    ): Result<Unit> {
        if (username.isBlank()) {
            return Result.failure(IllegalArgumentException(ERROR_USERNAME_BLANK))
        }
        
        return try {
            // Convert List<WatchlistEntry> to Firestore-compatible format (List of Maps)
            val firestoreData = hashMapOf(
                "favorites" to entries.map { entry ->
                    hashMapOf(
                        "id" to entry.id,
                        "type" to entry.type.name,
                        "streamId" to entry.streamId,
                        "name" to entry.name,
                        "metadata" to (entry.metadata ?: emptyMap()),
                        "addedAt" to entry.addedAt
                    )
                }
            )
            
            // Use set with merge to create document if it doesn't exist
            firestore.collection(collectionName)
                .document(username)
                .set(firestoreData, SetOptions.merge())
                .await()
            
            Log.d(TAG, "Favorites saved successfully (${entries.size} favorites)")
            Result.success(Unit)
        } catch (e: Exception) {
            reportError(TAG, "Failed to save favorites", e)
            Result.failure(e)
        }
    }
    
    /**
     * Loads the watchlist for a user from Firestore.
     *
     * @param username The username to load the watchlist for
     * @return Result<List<WatchlistEntry>> - Success with watchlist entries, Failure with exception otherwise
     */
    suspend fun loadWatchlist(
        username: String
    ): Result<List<WatchlistEntry>> {
        if (username.isBlank()) {
            return Result.failure(IllegalArgumentException(ERROR_USERNAME_BLANK))
        }
        
        return try {
            val documentSnapshot: DocumentSnapshot = firestore.collection(collectionName)
                .document(username)
                .get()
                .await()
            
            if (!documentSnapshot.exists()) {
                Log.d(TAG, "User document does not exist, returning empty list")
                return Result.success(emptyList())
            }
            
            @Suppress("UNCHECKED_CAST")
            val watchlistData = documentSnapshot["favorites"] as? List<Map<String, Any?>>

            if (watchlistData == null) {
                Log.d(TAG, "No favorites field found")
                return Result.success(emptyList())
            }
            
            // Convert Firestore data back to List<WatchlistEntry>
            val result = watchlistData.mapNotNull { entryMap ->
                try {
                    val id = entryMap["id"] as? String ?: return@mapNotNull null
                    val typeStr = entryMap["type"] as? String ?: return@mapNotNull null
                    val type = WatchlistEntryType.valueOf(typeStr)
                    val streamId = (entryMap["streamId"] as? Number)?.toInt() ?: return@mapNotNull null
                    val name = entryMap["name"] as? String ?: return@mapNotNull null
                    @Suppress("UNCHECKED_CAST")
                    val metadata = entryMap["metadata"] as? Map<String, String?>
                    val addedAt = entryMap["addedAt"] as? String ?: return@mapNotNull null
                    
                    WatchlistEntry(
                        id = id,
                        type = type,
                        streamId = streamId,
                        name = name,
                        metadata = metadata,
                        addedAt = addedAt
                    )
                } catch (e: Exception) {
                    reportWarning(TAG, "Error parsing favorite from Firestore", e)
                    null
                }
            }
            
            Log.d(TAG, "Favorites loaded successfully, count: ${result.size}")
            Result.success(result)
        } catch (e: Exception) {
            reportError(TAG, "Failed to load favorites", e)
            Result.failure(e)
        }
    }

    /**
     * Loads friends for a user from Firestore.
     *
     * @param username The username to load friends for
     * @return Result<List<FriendEntry>> - Success with friends list, Failure with exception otherwise
     */
    suspend fun loadFriends(
        username: String
    ): Result<List<FriendEntry>> {
        if (username.isBlank()) {
            return Result.failure(IllegalArgumentException(ERROR_USERNAME_BLANK))
        }

        return try {
            val documentSnapshot: DocumentSnapshot = firestore.collection(collectionName)
                .document(username)
                .get()
                .await()

            if (!documentSnapshot.exists()) {
                Log.d(TAG, "User document does not exist, returning empty friends list")
                return Result.success(emptyList())
            }

            @Suppress("UNCHECKED_CAST")
            val friendsData = documentSnapshot["friends"] as? List<Map<String, Any?>>

            if (friendsData == null) {
                Log.d(TAG, "No friends field found")
                return Result.success(emptyList())
            }

            val result = friendsData.mapNotNull(::parseFriendEntry)
            Log.d(TAG, "Friends loaded successfully, count: ${result.size}")
            Result.success(result)
        } catch (e: Exception) {
            reportError(TAG, "Failed to load friends", e)
            Result.failure(e)
        }
    }

    /**
     * Adds a friend atomically using Firestore transaction to prevent duplicates.
     *
     * @param username The current username
     * @param friendEntry The friend entry to add
     * @return Result<Unit> - Success if added, Failure with exception otherwise
     */
    suspend fun addFriendAtomically(
        username: String,
        friendEntry: FriendEntry
    ): Result<Unit> {
        if (username.isBlank()) {
            return Result.failure(IllegalArgumentException(ERROR_USERNAME_BLANK))
        }

        if (friendEntry.username.isBlank()) {
            return Result.failure(IllegalArgumentException(ERROR_USERNAME_BLANK))
        }

        if (username == friendEntry.username) {
            return Result.failure(IllegalArgumentException(ERROR_CANNOT_ADD_SELF))
        }

        val userDocRef = firestore.collection(collectionName).document(username)

        return try {
            firestore.runTransaction { transaction ->
                val snapshot = transaction[userDocRef]

                @Suppress("UNCHECKED_CAST")
                val friendsData = snapshot["friends"] as? List<Map<String, Any?>> ?: emptyList()

                check(!(friendsData.any { (it["username"] as? String) == friendEntry.username })) {
                    ERROR_FRIEND_ALREADY_EXISTS
                }

                val updatedFriends = friendsData + friendEntry.toFirestoreMap()
                transaction.set(userDocRef, mapOf("friends" to updatedFriends), SetOptions.merge())
            }.await()

            Log.d(TAG, "Friend added atomically")
            Result.success(Unit)
        } catch (e: Exception) {
            reportError(TAG, "Failed to add friend atomically", e)
            Result.failure(e)
        }
    }
    
    /**
     * Saves (upserts) a thumbs up/down rating for a piece of content atomically using a
     * Firestore transaction. Storage: `users/{username}.ratings` = Map<contentKey, RatingEntry>,
     * where contentKey is "movie_{id}" or "series_{id}". Rating the same content again
     * overwrites the previous entry for that key.
     *
     * @param username The username to save the rating for
     * @param entry The rating entry to upsert
     * @return Result<Unit> - Success if saved, Failure with exception otherwise
     */
    suspend fun saveRating(
        username: String,
        entry: RatingEntry
    ): Result<Unit> {
        if (username.isBlank()) {
            return Result.failure(IllegalArgumentException(ERROR_USERNAME_BLANK))
        }

        val userDocRef = firestore.collection(collectionName).document(username)

        return try {
            firestore.runTransaction { transaction ->
                val snapshot = transaction[userDocRef]

                @Suppress("UNCHECKED_CAST")
                val ratingsData = snapshot["ratings"] as? Map<String, Map<String, Any?>> ?: emptyMap()

                val updatedRatings = upsertRating(ratingsData, entry)
                transaction.set(userDocRef, mapOf("ratings" to updatedRatings), SetOptions.merge())
            }.await()

            Log.d(TAG, "Rating saved atomically")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            reportError(TAG, "Failed to save rating", e)
            Result.failure(e)
        }
    }

    /**
     * Loads a single rating entry for a piece of content from Firestore.
     *
     * @param username The username to load the rating for
     * @param contentKey The content key, e.g. "movie_123" or "series_456"
     * @return Result<RatingEntry?> - Success with the entry (or null if not rated), Failure with exception otherwise
     */
    suspend fun getRating(
        username: String,
        contentKey: String
    ): Result<RatingEntry?> {
        if (username.isBlank()) {
            return Result.failure(IllegalArgumentException(ERROR_USERNAME_BLANK))
        }

        return try {
            val documentSnapshot: DocumentSnapshot = firestore.collection(collectionName)
                .document(username)
                .get()
                .await()

            if (!documentSnapshot.exists()) {
                Log.d(TAG, "User document does not exist, returning null rating")
                return Result.success(null)
            }

            @Suppress("UNCHECKED_CAST")
            val ratingsData = documentSnapshot["ratings"] as? Map<String, Map<String, Any?>>
            val ratingMap = ratingsData?.get(contentKey)
            val result = ratingMap?.let(::parseRatingEntry)

            Log.d(TAG, "Rating loaded, found: ${result != null}")
            Result.success(result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            reportError(TAG, "Failed to get rating", e)
            Result.failure(e)
        }
    }

    /**
     * Loads all rating entries for a user from Firestore, keyed by content key
     * (e.g. "movie_123" or "series_456"). Read-only bulk variant of [getRating].
     *
     * @param username The username to load ratings for
     * @return Result<Map<String, RatingEntry>> - Success with all ratings (empty map if
     * none), Failure with exception otherwise
     */
    suspend fun loadAllRatings(
        username: String
    ): Result<Map<String, RatingEntry>> {
        if (username.isBlank()) {
            return Result.failure(IllegalArgumentException(ERROR_USERNAME_BLANK))
        }

        return try {
            val documentSnapshot: DocumentSnapshot = firestore.collection(collectionName)
                .document(username)
                .get()
                .await()

            if (!documentSnapshot.exists()) {
                Log.d(TAG, "User document does not exist, returning empty ratings map")
                return Result.success(emptyMap())
            }

            @Suppress("UNCHECKED_CAST")
            val ratingsData = documentSnapshot["ratings"] as? Map<String, Map<String, Any?>> ?: emptyMap()
            val result = ratingsData.mapNotNull { (key, ratingMap) ->
                parseRatingEntry(ratingMap)?.let { key to it }
            }.toMap()

            Log.d(TAG, "All ratings loaded, count: ${result.size}")
            Result.success(result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            reportError(TAG, "Failed to load all ratings", e)
            Result.failure(e)
        }
    }

    /**
     * Adds a watchlist entry atomically using Firestore transaction to prevent race conditions.
     *
     * @param username The username to add the entry for
     * @param entry The watchlist entry to add
     * @return Result<Unit> - Success if added, Failure with exception otherwise
     */
    suspend fun addToWatchlistAtomically(
        username: String,
        entry: WatchlistEntry
    ): Result<Unit> {
        if (username.isBlank()) {
            return Result.failure(IllegalArgumentException(ERROR_USERNAME_BLANK))
        }
        
        val userDocRef = firestore.collection(collectionName).document(username)
        
        return try {
            firestore.runTransaction { transaction ->
                val snapshot = transaction[userDocRef]

                @Suppress("UNCHECKED_CAST")
                val watchlistData = snapshot["favorites"] as? List<Map<String, Any?>> ?: emptyList()

                // Check if the entry already exists
                if (watchlistData.any { (it["id"] as? String) == entry.id }) {
                    // Entry already exists, transaction succeeds without changes
                    return@runTransaction
                }

                // Add new entry
                val newEntriesList = watchlistData + entry.toFirestoreMap()
                transaction.update(userDocRef, "favorites", newEntriesList)
            }.await()
            
            Log.d(TAG, "Favorite added atomically")
            Result.success(Unit)
        } catch (e: Exception) {
            reportError(TAG, "Failed to add favorite atomically", e)
            Result.failure(e)
        }
    }
    
    /**
     * Removes a watchlist entry atomically using Firestore transaction to prevent race conditions.
     *
     * @param username The username to remove the entry for
     * @param entryId The ID of the watchlist entry to remove
     * @return Result<Unit> - Success if removed, Failure with exception otherwise
     */
    suspend fun removeFromWatchlistAtomically(
        username: String,
        entryId: String
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
                val watchlistData = snapshot["favorites"] as? List<Map<String, Any?>> ?: emptyList()

                // Remove entry by id
                val updatedEntriesList = watchlistData.filterNot { (it["id"] as? String) == entryId }

                // Only update if something was actually removed
                if (updatedEntriesList.size < watchlistData.size) {
                    transaction.update(userDocRef, "favorites", updatedEntriesList)
                }
            }.await()
            
            Log.d(TAG, "Favorite removed atomically")
            Result.success(Unit)
        } catch (e: Exception) {
            reportError(TAG, "Failed to remove favorite atomically", e)
            Result.failure(e)
        }
    }
    
    /**
     * Migrates the watchlist from local storage to Firestore.
     * Merges local data with existing Firestore data (if any), preventing duplicates based on entry.id.
     *
     * @param username The username to migrate the watchlist for
     * @param localEntries List of WatchlistEntry objects from local storage
     * @return Result<Unit> - Success if migrated, Failure with exception otherwise
     */
    suspend fun migrateWatchlistFromLocal(
        username: String,
        localEntries: List<WatchlistEntry>
    ): Result<Unit> {
        if (username.isBlank()) {
            return Result.failure(IllegalArgumentException(ERROR_USERNAME_BLANK))
        }

        if (localEntries.isEmpty()) {
            Log.d(TAG, "No local favorites to migrate")
            return Result.success(Unit)
        }
        
        val userDocRef = firestore.collection(collectionName).document(username)
        
        return try {
            firestore.runTransaction { transaction ->
                val snapshot = transaction[userDocRef]

                @Suppress("UNCHECKED_CAST")
                val existingEntriesData = snapshot["favorites"] as? List<Map<String, Any?>> ?: emptyList()

                // Parse existing entries
                val existingEntries = existingEntriesData.mapNotNull { entryMap ->
                    try {
                        val id = entryMap["id"] as? String ?: return@mapNotNull null
                        val typeStr = entryMap["type"] as? String ?: return@mapNotNull null
                        val type = WatchlistEntryType.valueOf(typeStr)
                        val streamId = (entryMap["streamId"] as? Number)?.toInt() ?: return@mapNotNull null
                        val name = entryMap["name"] as? String ?: return@mapNotNull null
                        @Suppress("UNCHECKED_CAST")
                        val metadata = entryMap["metadata"] as? Map<String, String?>
                        val addedAt = entryMap["addedAt"] as? String ?: return@mapNotNull null
                        
                        WatchlistEntry(
                            id = id,
                            type = type,
                            streamId = streamId,
                            name = name,
                            metadata = metadata,
                            addedAt = addedAt
                        )
                    } catch (e: Exception) {
                        reportWarning(TAG, "Error parsing favorite from Firestore during migration", e)
                        null
                    }
                }
                
                // Merge logic
                val existingIds = existingEntries.map { it.id }.toSet()
                val newEntries = localEntries.filter { it.id !in existingIds }

                if (newEntries.isEmpty()) {
                    return@runTransaction // No changes needed
                }

                val mergedEntries = existingEntries + newEntries
                val mergedEntriesData = mergedEntries.map { it.toFirestoreMap() }

                transaction.set(userDocRef, mapOf("favorites" to mergedEntriesData), SetOptions.merge())
            }.await()
            
            Log.d(TAG, "Successfully migrated favorites from local to Firestore")
            Result.success(Unit)
        } catch (e: Exception) {
            reportError(TAG, "Failed to migrate favorites from local to Firestore", e)
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
            
            Log.d(TAG, "$successLogMessage (${data.size} entries)")
            Result.success(Unit)
        } catch (e: Exception) {
            reportError(TAG, "Failed to save '$fieldName'", e)
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
     * Deletes a specific watch progress entry from Firestore.
     * 
     * @param username The username to delete progress for
     * @param key The progress key to delete (e.g., "movie:123" or "series:456:789")
     * @return Result<Unit> - Success if deleted, Failure with exception otherwise
     */
    suspend fun deleteWatchProgress(
        username: String,
        key: String
    ): Result<Unit> {
        if (username.isBlank()) {
            return Result.failure(IllegalArgumentException(ERROR_USERNAME_BLANK))
        }
        
        return try {
            val updateData = hashMapOf<String, Any>(
                "watchProgress.$key" to FieldValue.delete()
            )
            
            firestore.collection(collectionName)
                .document(username)
                .update(updateData)
                .await()
            
            Log.d(TAG, "Deleted watch progress entry")
            Result.success(Unit)
        } catch (e: Exception) {
            reportError(TAG, "Failed to delete watch progress entry", e)
            Result.failure(e)
        }
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
                Log.d(TAG, "User document does not exist, returning empty map for field $fieldName")
                return Result.success(emptyMap())
            }
            
            @Suppress("UNCHECKED_CAST")
            val data = documentSnapshot[fieldName] as? Map<String, Map<String, Any?>>
            
            if (data == null) {
                Log.d(TAG, "No '$fieldName' field found")
                return Result.success(emptyMap())
            }
            
            val result = data.mapNotNull { (key, itemMap) ->
                try {
                    parser(key, itemMap)
                } catch (e: Exception) {
                    reportWarning(TAG, "Error parsing item from Firestore for field '$fieldName' with key: $key", e)
                    null
                }
            }.toMap()
            
            Log.d(TAG, "'$fieldName' loaded successfully, count: ${result.size}")
            Result.success(result)
        } catch (e: Exception) {
            reportError(TAG, "Failed to load '$fieldName'", e)
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
            Log.d(TAG, emptyDataMessage)
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
                        reportWarning(TAG, "Error parsing item from Firestore during migration for field '$fieldName' with key: $key", e)
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
            
            Log.d(TAG, successLogMessage)
            Result.success(Unit)
        } catch (e: Exception) {
            reportError(TAG, "Failed to migrate '$fieldName' from local to Firestore", e)
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
                    "name" to watchedItem.name,
                    "genre" to watchedItem.genre,
                    "categoryName" to watchedItem.categoryName,
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
                val name = itemMap["name"] as? String
                val genre = itemMap["genre"] as? String
                val categoryName = itemMap["categoryName"] as? String
                val watchedAt = itemMap["watchedAt"] as? String ?: return@loadUserDataMap null
                
                key to WatchedItem(
                    contentType = contentType,
                    streamId = streamId,
                    seriesId = seriesId,
                    episodeId = episodeId,
                    name = name,
                    genre = genre,
                    categoryName = categoryName,
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
                val name = itemMap["name"] as? String
                val genre = itemMap["genre"] as? String
                val categoryName = itemMap["categoryName"] as? String
                val watchedAt = itemMap["watchedAt"] as? String ?: return@migrateUserDataMapFromLocal null
                
                key to WatchedItem(
                    contentType = contentType,
                    streamId = streamId,
                    seriesId = seriesId,
                    episodeId = episodeId,
                    name = name,
                    genre = genre,
                    categoryName = categoryName,
                    watchedAt = watchedAt
                )
            },
            toFirestoreMap = { watchedItem ->
                hashMapOf(
                    "contentType" to watchedItem.contentType,
                    "streamId" to watchedItem.streamId,
                    "seriesId" to watchedItem.seriesId,
                    "episodeId" to watchedItem.episodeId,
                    "name" to watchedItem.name,
                    "genre" to watchedItem.genre,
                    "categoryName" to watchedItem.categoryName,
                    "watchedAt" to watchedItem.watchedAt
                )
            },
            emptyDataMessage = "No local watched history to migrate",
            successLogMessage = "Successfully migrated watched history from local to Firestore"
        )
    }
    
    /**
     * Extension function to convert Firebase Task to suspend function.
     * Bounded by [FIRESTORE_TIMEOUT_MS] so every call site (all go through this single
     * extension) surfaces a Result.failure instead of hanging forever when Firestore's
     * underlying transport never resolves the Task.
     */
    private suspend fun <T> Task<T>.await(): T {
        return withTimeout(FIRESTORE_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
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

    private fun parseFriendEntry(friendMap: Map<String, Any?>): FriendEntry? {
        return try {
            val username = friendMap["username"] as? String ?: return null
            val alias = friendMap["alias"] as? String
            val addedAt = friendMap["addedAt"] as? String ?: return null

            FriendEntry(
                username = username,
                alias = alias,
                addedAt = addedAt
            )
        } catch (e: Exception) {
            reportWarning(TAG, "Error parsing friend from Firestore", e)
            null
        }
    }
}

/**
 * Builds the Firestore map key for a rating: "movie_{id}" or "series_{id}".
 * Top-level (not a class member) so it can be unit-tested without instantiating
 * UserRepository, whose constructor eagerly calls FirebaseFirestore.getInstance().
 */
internal fun buildRatingContentKey(contentType: String, contentId: Int): String =
    "${contentType}_$contentId"

/**
 * Computes the ratings map after upserting [entry]; the same content key overwrites
 * a previous entry. Pure function used inside saveRating()'s Firestore transaction.
 */
internal fun upsertRating(
    existingRatings: Map<String, Map<String, Any?>>,
    entry: RatingEntry
): Map<String, Map<String, Any?>> {
    val key = buildRatingContentKey(entry.contentType, entry.contentId)
    return existingRatings + (key to entry.toFirestoreMap())
}

/**
 * Parses a single Firestore rating map back into a [RatingEntry]; returns null if a
 * required field is missing or malformed.
 */
internal fun parseRatingEntry(ratingMap: Map<String, Any?>): RatingEntry? {
    return try {
        val contentType = ratingMap["contentType"] as? String ?: return null
        val contentId = (ratingMap["contentId"] as? Number)?.toInt() ?: return null
        val title = ratingMap["title"] as? String
        val rating = (ratingMap["rating"] as? Number)?.toInt() ?: return null
        val ratedAt = ratingMap["ratedAt"] as? String ?: return null

        RatingEntry(
            contentType = contentType,
            contentId = contentId,
            title = title,
            rating = rating,
            ratedAt = ratedAt
        )
    } catch (e: Exception) {
        null
    }
}
