package com.flintr.tv.data.local

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FirestoreSyncManagerTest {

    @Test
    fun `syncIfNeeded fuehrt ersten Sync aus`() = runBlocking {
        val manager = FirestoreSyncManager(syncCooldownMs = 30_000L)
        var syncCount = 0

        val performed = manager.syncIfNeeded { syncCount++ }

        assertTrue(performed)
        assertEquals(1, syncCount)
    }

    @Test
    fun `syncIfNeeded ueberspringt Sync innerhalb des Cooldowns`() = runBlocking {
        val manager = FirestoreSyncManager(syncCooldownMs = 60_000L)
        var syncCount = 0

        assertTrue(manager.syncIfNeeded { syncCount++ })
        val secondPerformed = manager.syncIfNeeded { syncCount++ }

        assertFalse(secondPerformed)
        assertEquals(1, syncCount)
    }

    @Test
    fun `syncIfNeeded synct erneut nach Ablauf des Cooldowns`() = runBlocking {
        val manager = FirestoreSyncManager(syncCooldownMs = 0L)
        var syncCount = 0

        assertTrue(manager.syncIfNeeded { syncCount++ })
        assertTrue(manager.syncIfNeeded { syncCount++ })
        assertEquals(2, syncCount)
    }

    @Test
    fun `resetCooldown erlaubt sofortigen erneuten Sync`() = runBlocking {
        val manager = FirestoreSyncManager(syncCooldownMs = 60_000L)
        var syncCount = 0

        assertTrue(manager.syncIfNeeded { syncCount++ })
        manager.resetCooldown()
        assertTrue(manager.syncIfNeeded { syncCount++ })
        assertEquals(2, syncCount)
    }

    @Test
    fun `updateLastSyncTimestamp startet den Cooldown ohne Sync`() = runBlocking {
        val manager = FirestoreSyncManager(syncCooldownMs = 60_000L)
        var syncCount = 0

        manager.updateLastSyncTimestamp()
        val performed = manager.syncIfNeeded { syncCount++ }

        assertFalse(performed)
        assertEquals(0, syncCount)
    }

    @Test
    fun `forceSync ignoriert den Cooldown`() = runBlocking {
        val manager = FirestoreSyncManager(syncCooldownMs = 60_000L)
        var syncCount = 0

        assertTrue(manager.syncIfNeeded { syncCount++ })
        manager.forceSync { syncCount++ }

        assertEquals(2, syncCount)
    }

    @Test
    fun `forceSync startet den Cooldown neu`() = runBlocking {
        val manager = FirestoreSyncManager(syncCooldownMs = 60_000L)
        var syncCount = 0

        manager.forceSync { syncCount++ }
        val performed = manager.syncIfNeeded { syncCount++ }

        assertFalse(performed)
        assertEquals(1, syncCount)
    }
}
