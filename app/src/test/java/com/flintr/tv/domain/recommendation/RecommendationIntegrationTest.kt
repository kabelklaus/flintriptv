package com.flintr.tv.domain.recommendation

import com.flintr.tv.data.local.CachedData
import com.flintr.tv.data.model.Category
import com.flintr.tv.data.model.WatchlistEntry
import com.flintr.tv.data.model.WatchlistEntryType
import com.flintr.tv.data.model.Series
import com.flintr.tv.data.model.VodStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proves the full chain from an IMDb-enriched cache (Task 5/6) through [UserContentProfile]
 * and [RecommendationEngine]: genre scoring drives inclusion/ranking (not a rating sort), and
 * unmatched items (rating = null post-merge) no longer poison the rating fallback with 10.0s.
 */
private fun testVod(
    streamId: Int,
    genre: String?,
    name: String = "Movie$streamId",
    rating: String? = null
) = VodStream(
    num = null,
    name = name,
    streamType = null,
    streamId = streamId,
    streamIcon = null,
    rating = rating,
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

private fun testSeries(
    seriesId: Int,
    genre: String?,
    name: String = "Series$seriesId",
    rating: String? = null
) = Series(
    num = null,
    name = name,
    seriesId = seriesId,
    cover = null,
    plot = null,
    cast = null,
    director = null,
    genre = genre,
    releaseDate = null,
    rating = rating,
    rating5Based = null,
    lastModified = null,
    categoryId = null,
    categoryName = null,
    youtubeTrailer = null,
    backdropPath = null,
    seasons = null
)

private fun cachedData(movies: List<VodStream> = emptyList(), series: List<Series> = emptyList()) = CachedData(
    channels = emptyList(),
    movies = movies,
    series = series,
    liveCategories = emptyList<Category>(),
    vodCategories = emptyList<Category>(),
    seriesCategories = emptyList<Category>()
)

class RecommendationIntegrationTest {

    @Test
    fun `genre matched merged-cache movies outrank a higher-rated genre-foreign movie`() {
        // Signals come from real watchlist entries, resolved through UserContentProfile.build, not a
        // hand-rolled GenreProfile -- this is the signals-to-profile half of the chain.
        val signalMovies = (901..905).map { testVod(streamId = it, genre = "Drama", name = "Signal$it") }
        val entries = (901..905).map {
            WatchlistEntry.create(id = "movie:$it", type = WatchlistEntryType.MOVIE, streamId = it, name = "Signal$it")
        }
        val profile = UserContentProfile.build(
            watchlistEntries = entries,
            watchedHistory = emptyMap(),
            watchProgress = emptyMap(),
            ratings = emptyMap(),
            movies = signalMovies,
            series = emptyList()
        )
        assertEquals(5, profile.signalCount)

        // Cache rows built the way enrichment merge (Task 6) leaves them: start from an
        // unenriched row (genre/rating null) and .copy() in the IMDb-matched fields.
        val dramaFamilyMatch = testVod(streamId = 1, genre = null, name = "DramaFamilyMovie")
            .copy(genre = "Drama,Family", rating = "7.0")
        val dramaOnlyMatch = testVod(streamId = 2, genre = null, name = "DramaOnlyMovie")
            .copy(genre = "Drama", rating = "6.0")
        val genreForeignHighRating = testVod(streamId = 3, genre = null, name = "HorrorMovie")
            .copy(genre = "Horror", rating = "9.5")

        val result = RecommendationEngine.recommend(
            profile,
            cachedData(movies = listOf(dramaFamilyMatch, dramaOnlyMatch, genreForeignHighRating))
        )

        assertEquals(listOf(1, 2), result.movies.map { it.streamId })
        assertFalse(result.movies.map { it.streamId }.contains(3))
    }

    @Test
    fun `rating fallback sorts unmatched null-rating items last instead of as 10 0 junk`() {
        val profile = GenreProfile(weights = mapOf("action" to 3.0), signalCount = 5)

        val genreForeignButRated = testVod(streamId = 10, genre = "Horror", name = "HorrorRated", rating = "8.2")
        val unmatchedA = testVod(streamId = 11, genre = null, name = "UnenrichedA", rating = null)
        val unmatchedB = testVod(streamId = 12, genre = null, name = "UnenrichedB", rating = null)

        val result = RecommendationEngine.recommend(
            profile,
            cachedData(movies = listOf(unmatchedA, genreForeignButRated, unmatchedB))
        )

        assertEquals(listOf(10, 11, 12), result.movies.map { it.streamId })
    }

    @Test
    fun `diversity cap still holds when the engine is reached through the full chain`() {
        val profile = GenreProfile(weights = mapOf("animation" to 30.0, "drama" to 6.0), signalCount = 5)

        val dominant = (1..8).map { testVod(streamId = it, genre = "Animation", rating = (200 - it).toString()) }
        val other = (101..105).map { testVod(streamId = it, genre = "Drama", rating = (300 - it).toString()) }

        val result = RecommendationEngine.recommend(
            profile,
            cachedData(movies = dominant + other),
            limitPerType = 10
        )

        assertEquals(listOf(1, 2, 3, 4, 5, 101, 102, 103, 104, 105), result.movies.map { it.streamId })
    }

    @Test
    fun `cold start yields empty lists from the full chain regardless of rating quality`() {
        val signalMovies = (801..803).map { testVod(streamId = it, genre = "Drama", name = "Signal$it") }
        val entries = (801..803).map {
            WatchlistEntry.create(id = "movie:$it", type = WatchlistEntryType.MOVIE, streamId = it, name = "Signal$it")
        }
        val profile = UserContentProfile.build(
            watchlistEntries = entries,
            watchedHistory = emptyMap(),
            watchProgress = emptyMap(),
            ratings = emptyMap(),
            movies = signalMovies,
            series = emptyList()
        )
        assertTrue(profile.signalCount < 5)

        val movies = listOf(testVod(streamId = 1, genre = "Drama", rating = "10"))
        val series = listOf(testSeries(seriesId = 1, genre = "Drama", rating = "10"))

        val result = RecommendationEngine.recommend(profile, cachedData(movies = movies, series = series))

        assertTrue(result.movies.isEmpty())
        assertTrue(result.series.isEmpty())
    }
}
