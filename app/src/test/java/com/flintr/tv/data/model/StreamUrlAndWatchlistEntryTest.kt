package com.flintr.tv.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Stream-URL-Aufbau (Xtream-Codes-Schema) und Favoriten-Konvertierung.
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
    fun favoriteIdsEncodeNameAndCarryTypePrefix() {
        assertEquals(
            "live:7:Das+Erste+HD",
            testLiveStream(name = "Das Erste HD", streamId = 7).toFavoriteId()
        )
        assertEquals(
            "series:10:Breaking+Bad",
            testSeries(name = "Breaking Bad", seriesId = 10).toFavoriteId()
        )
    }

    @Test
    fun liveStreamToFavoriteCarriesIconMetadata() {
        val stream = testLiveStream(name = "Arte", streamId = 3, streamIcon = "http://icon.png")

        val favorite = stream.toWatchlistEntry()

        assertEquals(WatchlistEntryType.LIVE, favorite.type)
        assertEquals(3, favorite.streamId)
        assertEquals("Arte", favorite.name)
        assertEquals("http://icon.png", favorite.metadata?.get("streamIcon"))
    }

    @Test
    fun seriesToFavoriteMergesCoverIntoMetadata() {
        val series = testSeries(name = "Dark", seriesId = 12, cover = "http://cover.jpg")

        val favorite = series.toWatchlistEntry(metadata = mapOf("genre" to "Mystery"))

        assertEquals(WatchlistEntryType.SERIES, favorite.type)
        assertEquals("Mystery", favorite.metadata?.get("genre"))
        assertEquals("http://cover.jpg", favorite.metadata?.get("cover"))
    }

    @Test
    fun favoriteToFirestoreMapContainsAllFields() {
        val favorite = WatchlistEntry.create(
            id = "live:1:Test",
            type = WatchlistEntryType.LIVE,
            streamId = 1,
            name = "Test"
        )

        val map = favorite.toFirestoreMap()

        assertEquals("live:1:Test", map["id"])
        assertEquals("LIVE", map["type"])
        assertEquals(1, map["streamId"])
        assertEquals("Test", map["name"])
        assertEquals(emptyMap<String, String?>(), map["metadata"])
        assertEquals(favorite.addedAt, map["addedAt"])
    }

    @Test
    fun favoriteWithoutOptionalMetadataHasNullMetadata() {
        val favorite = WatchlistEntry.create(
            id = "movie:2:Film",
            type = WatchlistEntryType.MOVIE,
            streamId = 2,
            name = "Film"
        )

        assertNull(favorite.metadata)
    }
}
