package com.flintr.tv.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Stream-URL-Aufbau (Xtream-Codes-Schema) und Watchlist-Konvertierung.
 */
class StreamUrlAndWatchlistEntryTest {

    private val baseUrl = "http://server:8080"

    @Test
    fun vodStreamUrlUsesContainerExtension() {
        val stream = testVodStream(streamId = 42, containerExtension = "mp4")

        assertEquals(
            "http://server:8080/movie/user/pass/42.mp4",
            stream.getStreamUrl(baseUrl, "user", "pass")
        )
    }

    @Test
    fun vodStreamUrlFallsBackToMkv() {
        val stream = testVodStream(streamId = 42)

        assertEquals(
            "http://server:8080/movie/user/pass/42.mkv",
            stream.getStreamUrl(baseUrl, "user", "pass")
        )
    }

    @Test
    fun liveStreamUrlAlwaysUsesM3u8() {
        val stream = testLiveStream(streamId = 7)

        assertEquals(
            "http://server:8080/live/user/pass/7.m3u8",
            stream.getStreamUrl(baseUrl, "user", "pass")
        )
    }

    @Test
    fun episodeStreamUrlUsesEpisodeIdNotSeriesId() {
        val episode = testEpisode(id = 555, containerExtension = "avi")

        assertEquals(
            "http://server:8080/series/user/pass/555.avi",
            episode.getStreamUrl(baseUrl, "user", "pass", seriesId = 99)
        )
    }

    @Test
    fun watchlistEntryIdsEncodeNameAndCarryTypePrefix() {
        assertEquals(
            "live:7:Das+Erste+HD",
            testLiveStream(name = "Das Erste HD", streamId = 7).toWatchlistEntryId()
        )
        assertEquals(
            "series:10:Breaking+Bad",
            testSeries(name = "Breaking Bad", seriesId = 10).toWatchlistEntryId()
        )
    }

    @Test
    fun liveStreamToWatchlistEntryCarriesIconMetadata() {
        val stream = testLiveStream(name = "Arte", streamId = 3, streamIcon = "http://icon.png")

        val entry = stream.toWatchlistEntry()

        assertEquals(WatchlistEntryType.LIVE, entry.type)
        assertEquals(3, entry.streamId)
        assertEquals("Arte", entry.name)
        assertEquals("http://icon.png", entry.metadata?.get("streamIcon"))
    }

    @Test
    fun seriesToWatchlistEntryMergesCoverIntoMetadata() {
        val series = testSeries(name = "Dark", seriesId = 12, cover = "http://cover.jpg")

        val entry = series.toWatchlistEntry(metadata = mapOf("genre" to "Mystery"))

        assertEquals(WatchlistEntryType.SERIES, entry.type)
        assertEquals("Mystery", entry.metadata?.get("genre"))
        assertEquals("http://cover.jpg", entry.metadata?.get("cover"))
    }

    @Test
    fun watchlistEntryToFirestoreMapContainsAllFields() {
        val entry = WatchlistEntry.create(
            id = "live:1:Test",
            type = WatchlistEntryType.LIVE,
            streamId = 1,
            name = "Test"
        )

        val map = entry.toFirestoreMap()

        assertEquals("live:1:Test", map["id"])
        assertEquals("LIVE", map["type"])
        assertEquals(1, map["streamId"])
        assertEquals("Test", map["name"])
        assertEquals(emptyMap<String, String?>(), map["metadata"])
        assertEquals(entry.addedAt, map["addedAt"])
    }

    @Test
    fun watchlistEntryWithoutOptionalMetadataHasNullMetadata() {
        val entry = WatchlistEntry.create(
            id = "movie:2:Film",
            type = WatchlistEntryType.MOVIE,
            streamId = 2,
            name = "Film"
        )

        assertNull(entry.metadata)
    }
}
