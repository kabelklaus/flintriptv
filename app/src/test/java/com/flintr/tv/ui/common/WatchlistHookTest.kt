package com.flintr.tv.ui.common

import com.flintr.tv.data.model.WatchlistEntry
import com.flintr.tv.data.model.WatchlistEntryType
import com.flintr.tv.data.model.FriendEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchlistHookTest {

    @Test
    fun `build friend badge prefers alias and falls back to username`() {
        assertEquals("Th", buildFriendBadge("Thomas", "thomas_8472"))
        assertEquals("Th", buildFriendBadge(null, "thomas_8472"))
        assertEquals("??", buildFriendBadge("___", "___"))
    }

    @Test
    fun `filter watchlist by type keeps only matching items`() {
        val entries = listOf(
            entry("live_1", WatchlistEntryType.LIVE, 1),
            entry("movie_2", WatchlistEntryType.MOVIE, 2),
            entry("series_3", WatchlistEntryType.SERIES, 3)
        )

        val filtered = filterWatchlistByType(entries,WatchlistEntryType.MOVIE)

        assertEquals(1, filtered.size)
        assertEquals("movie_2", filtered.first().id)
    }

    @Test
    fun `combine sourced items keeps own items first and friend groups in order`() {
        val ownItems = listOf("own-1", "own-2")
        val friendItems = listOf(
            FriendEntry("thomas_8472", "Thomas", "2026-04-12T10:30:00Z") to listOf("friend-a"),
            FriendEntry("anna_24", null, "2026-04-12T10:35:00Z") to listOf("friend-b", "friend-c")
        )

        val combined = combineSourcedItems(ownItems, friendItems)

        assertEquals(listOf("own-1", "own-2", "friend-a", "friend-b", "friend-c"), combined.map { it.item })
        assertTrue(combined[0].source.isOwn)
        assertFalse(combined[2].source.isOwn)
        assertEquals("Th", combined[2].source.badge)
        assertEquals("An", combined[3].source.badge)
        assertFalse(combined[2].allowWatchlistActions)
    }

    private fun entry(
        id: String,
        type: WatchlistEntryType,
        streamId: Int
    ): WatchlistEntry {
        return WatchlistEntry(
            id = id,
            type = type,
            streamId = streamId,
            name = id,
            addedAt = "2026-04-12T10:30:00Z"
        )
    }
}
