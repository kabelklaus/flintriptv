package com.flintr.tv.data.local

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Singleton

/**
 * Manages smart synchronization with cooldown to prevent excessive Firestore reads.
 * Can be used by any service that needs to sync data from Firestore periodically.
 * 
 * @param syncCooldownMs Cooldown period in milliseconds (default: 30 seconds)
 * @param tag Log tag for debugging (default: "FirestoreSyncManager")
 */
@Singleton
class FirestoreSyncManager constructor(
    private val syncCooldownMs: Long = 30_000L,
    private val tag: String = "FirestoreSyncManager"
) {
    private val syncMutex = Mutex()
    private var lastSyncTimestamp: Long = 0

    /**
     * Executes the provided sync function if enough time has passed since last sync.
     * This prevents excessive Firestore reads when sync is requested frequently.
     * 
     * @param syncFunction The suspend function to execute for synchronization
     * @return true if sync was performed, false if skipped due to cooldown
     */
    suspend fun syncIfNeeded(syncFunction: suspend () -> Unit): Boolean {
        val now = System.currentTimeMillis()
        val timeSinceLastSync = now - lastSyncTimestamp
        
        return if (timeSinceLastSync >= syncCooldownMs) {
            syncMutex.withLock {
                // Double-check after acquiring lock
                val timeSinceLastSyncLocked = System.currentTimeMillis() - lastSyncTimestamp
                if (timeSinceLastSyncLocked >= syncCooldownMs) {
                    syncFunction()
                    lastSyncTimestamp = System.currentTimeMillis()
                    Log.d(tag, "Sync performed successfully")
                    true
                } else {
                    Log.d(tag, "Sync skipped: another sync occurred while waiting for lock")
                    false
                }
            }
        } else {
            Log.d(tag, "Sync skipped: only ${timeSinceLastSync}ms since last sync (cooldown: ${syncCooldownMs}ms)")
            false
        }
    }

    /**
     * Manually updates the last sync timestamp.
     * Useful when sync is performed outside of syncIfNeeded().
     */
    fun updateLastSyncTimestamp() {
        lastSyncTimestamp = System.currentTimeMillis()
    }

    /**
     * Resets the sync cooldown, allowing immediate sync on next call.
     */
    fun resetCooldown() {
        lastSyncTimestamp = 0
    }

    /**
     * Forces synchronization, ignoring cooldown.
     * Use this for explicit user actions (e.g., navigating to favorites category).
     * 
     * @param syncFunction The suspend function to execute for synchronization
     */
    suspend fun forceSync(syncFunction: suspend () -> Unit) {
        syncMutex.withLock {
            syncFunction()
            lastSyncTimestamp = System.currentTimeMillis()
            Log.d(tag, "Force sync performed successfully")
        }
    }
}
