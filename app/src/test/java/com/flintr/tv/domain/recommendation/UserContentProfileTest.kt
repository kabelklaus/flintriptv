package com.flintr.tv.domain.recommendation

import com.flintr.tv.data.model.WatchlistEntry
import com.flintr.tv.data.model.WatchlistEntryType
import com.flintr.tv.data.model.RatingEntry
import com.flintr.tv.data.model.Series
import com.flintr.tv.data.model.VodStream
import com.flintr.tv.data.model.WatchProgress
import com.flintr.tv.data.model.WatchedItem
import com.flintr.tv.data.tmdb.TmdbGenre
import com.flintr.tv.data.tmdb.TmdbMeta
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

/** Synchroner Fake: contentId -> TMDB-Genre-IDs; der kind-Parameter ist hier unerheblich. */
private fun lookup(vararg entries: Pair<Int, List<Int>>): (String, Int) -> TmdbMeta? {
    val byId = entries.toMap()
    return { _, contentId ->
        byId[contentId]?.let { ids ->
            TmdbMeta(
                tmdbId = contentId,
                rating = 0.0,
                voteCount = 0,
                genres = ids.map { TmdbGenre(it, "Genre$it") }
            )
        }
    }
}

class UserContentProfileTest {

    @Test
    fun `watchlist series adds weighted genres`() {
        val series = listOf(testSeries(seriesId = 10, genre = "Action, Drama"))
        val entries = listOf(
            WatchlistEntry.create(id = "series:10:x", type = WatchlistEntryType.SERIES, streamId = 10, name = "Series10")
        )

        val profile = UserContentProfile.build(
            watchlistEntries = entries,
            watchedHistory = emptyMap(),
            watchProgress = emptyMap(),
            ratings = emptyMap(),
            movies = emptyList(),
            series = series,
            metaLookup = lookup(10 to listOf(28, 18))
        )

        assertEquals(3.0, profile.weights[28]!!, 0.001)
        assertEquals(3.0, profile.weights[18]!!, 0.001)
        assertTrue(10 in profile.excludedSeriesIds)
    }

    @Test
    fun `five started episodes mark series active`() {
        val series = listOf(testSeries(seriesId = 10, genre = "Action"))
        val progress = (1..5).associate { ep ->
            "series:10:$ep" to WatchProgress(position = 500, duration = 1000, lastWatched = agoIso(0), title = "Ep$ep")
        }

        val profile = UserContentProfile.build(
            watchlistEntries = emptyList(),
            watchedHistory = emptyMap(),
            watchProgress = progress,
            ratings = emptyMap(),
            movies = emptyList(),
            series = series,
            metaLookup = lookup(10 to listOf(28))
        )

        assertEquals(2.5, profile.weights[28]!!, 0.001)
        assertTrue(10 in profile.excludedSeriesIds)
    }

    @Test
    fun `two started episodes mark series as started only`() {
        val series = listOf(testSeries(seriesId = 10, genre = "Action"))
        val progress = (1..2).associate { ep ->
            "series:10:$ep" to WatchProgress(position = 500, duration = 1000, lastWatched = agoIso(0), title = "Ep$ep")
        }

        val profile = UserContentProfile.build(
            watchlistEntries = emptyList(),
            watchedHistory = emptyMap(),
            watchProgress = progress,
            ratings = emptyMap(),
            movies = emptyList(),
            series = series,
            metaLookup = lookup(10 to listOf(28))
        )

        assertEquals(1.0, profile.weights[28]!!, 0.001)
    }

    @Test
    fun `completed movie adds weight and excludes it`() {
        val movies = listOf(testVod(streamId = 1, genre = "Horror"))
        val progress = mapOf(
            "movie:1" to WatchProgress(position = 950, duration = 1000, lastWatched = agoIso(0), title = "Movie1")
        )

        val profile = UserContentProfile.build(
            watchlistEntries = emptyList(),
            watchedHistory = emptyMap(),
            watchProgress = progress,
            ratings = emptyMap(),
            movies = movies,
            series = emptyList(),
            metaLookup = lookup(1 to listOf(27))
        )

        assertEquals(1.5, profile.weights[27]!!, 0.001)
        assertTrue(1 in profile.excludedMovieIds)
    }

    @Test
    fun `abandoned unwatched movie applies negative weight`() {
        val movies = listOf(testVod(streamId = 1, genre = "Horror"))
        val progress = mapOf(
            "movie:1" to WatchProgress(position = 50, duration = 1000, lastWatched = agoIso(0), title = "Movie1")
        )

        val profile = UserContentProfile.build(
            watchlistEntries = emptyList(),
            watchedHistory = emptyMap(),
            watchProgress = progress,
            ratings = emptyMap(),
            movies = movies,
            series = emptyList(),
            metaLookup = lookup(1 to listOf(27))
        )

        assertEquals(-1.0, profile.weights[27]!!, 0.001)
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
            watchlistEntries = emptyList(),
            watchedHistory = watchedHistory,
            watchProgress = progress,
            ratings = emptyMap(),
            movies = movies,
            series = emptyList(),
            metaLookup = lookup(1 to listOf(27))
        )

        assertTrue(profile.weights[27] == null || profile.weights[27] == 0.0)
    }

    @Test
    fun `thumbs down applies negative weight`() {
        val movies = listOf(testVod(streamId = 1, genre = "Comedy"))
        val ratings = mapOf(
            "movie_1" to RatingEntry(contentType = "movie", contentId = 1, title = "Movie1", rating = 0, ratedAt = agoIso(0))
        )

        val profile = UserContentProfile.build(
            watchlistEntries = emptyList(),
            watchedHistory = emptyMap(),
            watchProgress = emptyMap(),
            ratings = ratings,
            movies = movies,
            series = emptyList(),
            metaLookup = lookup(1 to listOf(35))
        )

        assertEquals(-2.0, profile.weights[35]!!, 0.001)
    }

    @Test
    fun `old signal is damped to the capped minimum factor`() {
        val movies = listOf(testVod(streamId = 1, genre = "Horror"))
        val progress = mapOf(
            "movie:1" to WatchProgress(position = 950, duration = 1000, lastWatched = agoIso(200), title = "Movie1")
        )

        val profile = UserContentProfile.build(
            watchlistEntries = emptyList(),
            watchedHistory = emptyMap(),
            watchProgress = progress,
            ratings = emptyMap(),
            movies = movies,
            series = emptyList(),
            metaLookup = lookup(1 to listOf(27))
        )

        // weight = 1.5 * max(0.3, exp(-200/90)) = 1.5 * 0.3 (capped)
        assertTrue(abs(profile.weights[27]!! - 0.45) < 0.01)
    }

    @Test
    fun `mixed signals on the same genre accumulate`() {
        val movies = listOf(testVod(streamId = 1, genre = "Action"))
        val series = listOf(testSeries(seriesId = 10, genre = "Action"))
        val entries = listOf(
            WatchlistEntry.create(id = "movie:1:x", type = WatchlistEntryType.MOVIE, streamId = 1, name = "Movie1")
        )
        val progress = (1..5).associate { ep ->
            "series:10:$ep" to WatchProgress(position = 500, duration = 1000, lastWatched = agoIso(0), title = "Ep$ep")
        }

        val profile = UserContentProfile.build(
            watchlistEntries = entries,
            watchedHistory = emptyMap(),
            watchProgress = progress,
            ratings = emptyMap(),
            movies = movies,
            series = series,
            metaLookup = lookup(1 to listOf(28), 10 to listOf(28))
        )

        // +3.0 (watchlist movie) + 2.5 (active series) = 5.5
        assertEquals(5.5, profile.weights[28]!!, 0.001)
    }

    @Test
    fun `cold start below five core signals yields low signal count`() {
        val movies = listOf(testVod(streamId = 1, genre = "Action"))
        val progress = mapOf(
            "movie:1" to WatchProgress(position = 50, duration = 1000, lastWatched = agoIso(0), title = "Movie1")
        )

        val profile = UserContentProfile.build(
            watchlistEntries = emptyList(),
            watchedHistory = emptyMap(),
            watchProgress = progress,
            ratings = emptyMap(),
            movies = movies,
            series = emptyList(),
            metaLookup = lookup(1 to listOf(28))
        )

        // Abandoned-movie signal doesn't count toward the cold-start core signal count.
        assertEquals(0, profile.signalCount)
    }
}
