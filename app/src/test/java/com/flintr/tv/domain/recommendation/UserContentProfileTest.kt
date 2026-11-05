package com.flintr.tv.domain.recommendation

import com.flintr.tv.data.model.WatchlistEntry
import com.flintr.tv.data.model.FavoriteType
import com.flintr.tv.data.model.RatingEntry
import com.flintr.tv.data.model.Series
import com.flintr.tv.data.model.VodStream
import com.flintr.tv.data.model.WatchProgress
import com.flintr.tv.data.model.WatchedItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.math.abs

private fun testVod(streamId: Int, genre: String?, name: String = "Movie$streamId") = VodStream(
    num = null,
    name = name,
    streamType = null,
    streamId = streamId,
    streamIcon = null,
    rating = null,
    rating5Based = null,
    added = null,
    categoryId = null,
    categoryName = null,
    containerExtension = null,
    customSid = null,
    directSource = null,
    releaseDate = null,
    duration = null,
    plot = null,
    cast = null,
    director = null,
    genre = genre,
    backdropPath = null,
    youtubeTrailer = null,
    cover = null,
    tmdbId = null
)

private fun testSeries(seriesId: Int, genre: String?, name: String = "Series$seriesId") = Series(
    num = null,
    name = name,
    seriesId = seriesId,
    cover = null,
    plot = null,
    cast = null,
    director = null,
    genre = genre,
    releaseDate = null,
    rating = null,
    rating5Based = null,
    lastModified = null,
    categoryId = null,
    categoryName = null,
    youtubeTrailer = null,
    backdropPath = null,
    seasons = null
)

private fun agoIso(days: Long): String = Instant.now().minusSeconds(days * 86_400).toString()

class UserContentProfileTest {

    @Test
    fun `favorite series adds weighted genres`() {
        val series = listOf(testSeries(seriesId = 10, genre = "Action, Drama"))
        val favorites = listOf(
            WatchlistEntry.create(id = "series:10:x", type = FavoriteType.SERIES, streamId = 10, name = "Series10")
        )

        val profile = UserContentProfile.build(
            favorites = favorites,
            watchedHistory = emptyMap(),
            watchProgress = emptyMap(),
            ratings = emptyMap(),
            movies = emptyList(),
            series = series
        )

        assertEquals(3.0, profile.weights["action"]!!, 0.001)
        assertEquals(3.0, profile.weights["drama"]!!, 0.001)
        assertTrue(10 in profile.excludedSeriesIds)
    }

    @Test
    fun `five started episodes mark series active`() {
        val series = listOf(testSeries(seriesId = 10, genre = "Action"))
        val progress = (1..5).associate { ep ->
            "series:10:$ep" to WatchProgress(position = 500, duration = 1000, lastWatched = agoIso(0), title = "Ep$ep")
        }

        val profile = UserContentProfile.build(
            favorites = emptyList(),
            watchedHistory = emptyMap(),
            watchProgress = progress,
            ratings = emptyMap(),
            movies = emptyList(),
            series = series
        )

        assertEquals(2.5, profile.weights["action"]!!, 0.001)
        assertTrue(10 in profile.excludedSeriesIds)
    }

    @Test
    fun `two started episodes mark series as started only`() {
        val series = listOf(testSeries(seriesId = 10, genre = "Action"))
        val progress = (1..2).associate { ep ->
            "series:10:$ep" to WatchProgress(position = 500, duration = 1000, lastWatched = agoIso(0), title = "Ep$ep")
        }

        val profile = UserContentProfile.build(
            favorites = emptyList(),
            watchedHistory = emptyMap(),
            watchProgress = progress,
            ratings = emptyMap(),
            movies = emptyList(),
            series = series
        )

        assertEquals(1.0, profile.weights["action"]!!, 0.001)
    }

    @Test
    fun `completed movie adds weight and excludes it`() {
        val movies = listOf(testVod(streamId = 1, genre = "Horror"))
        val progress = mapOf(
            "movie:1" to WatchProgress(position = 950, duration = 1000, lastWatched = agoIso(0), title = "Movie1")
        )

        val profile = UserContentProfile.build(
            favorites = emptyList(),
            watchedHistory = emptyMap(),
            watchProgress = progress,
            ratings = emptyMap(),
            movies = movies,
            series = emptyList()
        )

        assertEquals(1.5, profile.weights["horror"]!!, 0.001)
        assertTrue(1 in profile.excludedMovieIds)
    }

    @Test
    fun `abandoned unwatched movie applies negative weight`() {
        val movies = listOf(testVod(streamId = 1, genre = "Horror"))
        val progress = mapOf(
            "movie:1" to WatchProgress(position = 50, duration = 1000, lastWatched = agoIso(0), title = "Movie1")
        )

        val profile = UserContentProfile.build(
            favorites = emptyList(),
            watchedHistory = emptyMap(),
            watchProgress = progress,
            ratings = emptyMap(),
            movies = movies,
            series = emptyList()
        )

        assertEquals(-1.0, profile.weights["horror"]!!, 0.001)
    }

    @Test
    fun `abandoned movie already in watched history does not apply negative weight`() {
        val movies = listOf(testVod(streamId = 1, genre = "Horror"))
        val progress = mapOf(
            "movie:1" to WatchProgress(position = 50, duration = 1000, lastWatched = agoIso(0), title = "Movie1")
        )
        val watchedHistory = mapOf(
            "movie:1" to WatchedItem(
                contentType = "movie", streamId = 1, seriesId = null, episodeId = null,
                name = "Movie1", genre = null, categoryName = null, watchedAt = agoIso(0)
            )
        )

        val profile = UserContentProfile.build(
            favorites = emptyList(),
            watchedHistory = watchedHistory,
            watchProgress = progress,
            ratings = emptyMap(),
            movies = movies,
            series = emptyList()
        )

        assertTrue(profile.weights["horror"] == null || profile.weights["horror"] == 0.0)
    }

    @Test
    fun `thumbs down applies negative weight`() {
        val movies = listOf(testVod(streamId = 1, genre = "Comedy"))
        val ratings = mapOf(
            "movie_1" to RatingEntry(contentType = "movie", contentId = 1, title = "Movie1", rating = 0, ratedAt = agoIso(0))
        )

        val profile = UserContentProfile.build(
            favorites = emptyList(),
            watchedHistory = emptyMap(),
            watchProgress = emptyMap(),
            ratings = ratings,
            movies = movies,
            series = emptyList()
        )

        assertEquals(-2.0, profile.weights["comedy"]!!, 0.001)
    }

    @Test
    fun `old signal is damped to the capped minimum factor`() {
        val movies = listOf(testVod(streamId = 1, genre = "Horror"))
        val progress = mapOf(
            "movie:1" to WatchProgress(position = 950, duration = 1000, lastWatched = agoIso(200), title = "Movie1")
        )

        val profile = UserContentProfile.build(
            favorites = emptyList(),
            watchedHistory = emptyMap(),
            watchProgress = progress,
            ratings = emptyMap(),
            movies = movies,
            series = emptyList()
        )

        // weight = 1.5 * max(0.3, exp(-200/90)) = 1.5 * 0.3 (capped)
        assertTrue(abs(profile.weights["horror"]!! - 0.45) < 0.01)
    }

    @Test
    fun `mixed signals on the same genre accumulate`() {
        val movies = listOf(testVod(streamId = 1, genre = "Action"))
        val series = listOf(testSeries(seriesId = 10, genre = "Action"))
        val favorites = listOf(
            WatchlistEntry.create(id = "movie:1:x", type = FavoriteType.MOVIE, streamId = 1, name = "Movie1")
        )
        val progress = (1..5).associate { ep ->
            "series:10:$ep" to WatchProgress(position = 500, duration = 1000, lastWatched = agoIso(0), title = "Ep$ep")
        }

        val profile = UserContentProfile.build(
            favorites = favorites,
            watchedHistory = emptyMap(),
            watchProgress = progress,
            ratings = emptyMap(),
            movies = movies,
            series = series
        )

        // +3.0 (favorite movie) + 2.5 (active series) = 5.5
        assertEquals(5.5, profile.weights["action"]!!, 0.001)
    }

    @Test
    fun `cold start below five core signals yields low signal count`() {
        val movies = listOf(testVod(streamId = 1, genre = "Action"))
        val progress = mapOf(
            "movie:1" to WatchProgress(position = 50, duration = 1000, lastWatched = agoIso(0), title = "Movie1")
        )

        val profile = UserContentProfile.build(
            favorites = emptyList(),
            watchedHistory = emptyMap(),
            watchProgress = progress,
            ratings = emptyMap(),
            movies = movies,
            series = emptyList()
        )

        // Abandoned-movie signal doesn't count toward the cold-start core signal count.
        assertEquals(0, profile.signalCount)
    }
}
