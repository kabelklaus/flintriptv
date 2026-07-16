package com.flintr.tv.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchedHistoryEntriesTest {

    @Test
    fun `seriesEpisodeKey baut den Key im bestehenden Format`() {
        assertEquals("series:10:101", WatchedHistoryService.seriesEpisodeKey(seriesId = 10, episodeId = 101))
    }

    @Test
    fun `buildSeriesWatchedEntries erzeugt Eintraege fuer alle Episoden`() {
        val entries = WatchedHistoryService.buildSeriesWatchedEntries(
            seriesId = 10,
            episodeIds = listOf(101, 102),
            name = "Testserie",
            genre = "Drama",
            categoryName = "Serien DE",
            watchedAt = "2026-07-16T10:00:00Z"
        )

        assertEquals(setOf("series:10:101", "series:10:102"), entries.keys)

        val entry = entries.getValue("series:10:101")
        assertEquals("series", entry.contentType)
        assertEquals(10, entry.seriesId)
        assertEquals(101, entry.episodeId)
        assertEquals("Testserie", entry.name)
        assertEquals("Drama", entry.genre)
        assertEquals("Serien DE", entry.categoryName)
        assertEquals("2026-07-16T10:00:00Z", entry.watchedAt)
    }

    @Test
    fun `buildSeriesWatchedEntries liefert leere Map fuer leere Episodenliste`() {
        val entries = WatchedHistoryService.buildSeriesWatchedEntries(
            seriesId = 10,
            episodeIds = emptyList(),
            name = null,
            genre = null,
            categoryName = null,
            watchedAt = "2026-07-16T10:00:00Z"
        )

        assertTrue(entries.isEmpty())
    }
}
