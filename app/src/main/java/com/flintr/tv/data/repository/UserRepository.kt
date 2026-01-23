package com.flintr.tv.data.repository

import android.util.Log
import com.flintr.tv.data.model.CategoryType
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
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
