package com.flintr.tv.data.repository

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
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
    }
    
    /**
     * Checks if a username already exists in Firestore.
     * 
     * @param username The username to check
     * @return Result<Boolean> - true if exists, false if not, Failure with exception on error
     */
    suspend fun usernameExists(username: String): Result<Boolean> {
        if (username.isBlank()) {
            return Result.failure(IllegalArgumentException("Username cannot be blank"))
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
            return Result.failure(IllegalArgumentException("Username cannot be blank"))
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
