package com.flintr.tv.ui.common

import com.flintr.tv.data.model.Favorite
import com.flintr.tv.data.model.FavoriteType
import com.flintr.tv.data.model.FriendEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritesHookTest {

    @Test
    fun `build friend badge prefers alias and falls back to username`() {
        assertEquals("Th", buildFriendBadge("Thomas", "thomas_8472"))
        assertEquals("Th", buildFriendBadge(null, "thomas_8472"))
        assertEquals("??", buildFriendBadge("___", "___"))
    }

    @Test
    fun `filter favorites by type keeps only matching items`() {
        val favorites = listOf(
            favorite("live_1", FavoriteType.LIVE, 1),
            favorite("movie_2", FavoriteType.MOVIE, 2),
            favorite("series_3", FavoriteType.SERIES, 3)
        )

        val filtered = filterFavoritesByType(favorites, FavoriteType.MOVIE)

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
        assertFalse(combined[2].allowFavoriteActions)
    }

    private fun favorite(
        id: String,
        type: FavoriteType,
        streamId: Int
    ): Favorite {
        return Favorite(
            id = id,
            type = type,
            streamId = streamId,
            name = id,
            addedAt = "2026-04-12T10:30:00Z"
        )
    }
}
