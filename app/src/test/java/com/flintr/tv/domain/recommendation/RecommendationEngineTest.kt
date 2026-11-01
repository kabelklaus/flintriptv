package com.flintr.tv.domain.recommendation

import com.flintr.tv.data.local.CachedData
import com.flintr.tv.data.model.Category
import com.flintr.tv.data.model.Series
import com.flintr.tv.data.model.VodStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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

private fun profileWithSignals(weights: Map<String, Double>, signalCount: Int = 5) =
    GenreProfile(weights = weights, signalCount = signalCount)

class RecommendationEngineTest {

    @Test
    fun `candidate with matching genre wins over non matching candidate`() {
        val movies = listOf(
            testVod(streamId = 1, genre = "Action"),
            testVod(streamId = 2, genre = "Documentary")
        )
        val profile = profileWithSignals(mapOf("action" to 3.0))

        val result = RecommendationEngine.recommend(profile, cachedData(movies = movies))

        assertEquals(listOf(1), result.movies.map { it.streamId })
    }

    @Test
    fun `favorite and watched movies never appear`() {
        val movies = listOf(
            testVod(streamId = 1, genre = "Action"),
            testVod(streamId = 2, genre = "Action")
        )
        val profile = GenreProfile(
            weights = mapOf("action" to 3.0),
            signalCount = 5,
            excludedMovieIds = setOf(1, 2)
        )

        val result = RecommendationEngine.recommend(profile, cachedData(movies = movies))

        assertTrue(result.movies.isEmpty())
    }

    @Test
    fun `series with any episode activity never appears`() {
        val series = listOf(testSeries(seriesId = 10, genre = "Action"))
        val profile = GenreProfile(
            weights = mapOf("action" to 3.0),
            signalCount = 5,
            excludedSeriesIds = setOf(10)
        )

        val result = RecommendationEngine.recommend(profile, cachedData(series = series))

        assertTrue(result.series.isEmpty())
    }

    @Test
    fun `items with score at or below zero are excluded when genre matches exist`() {
        val movies = listOf(
            testVod(streamId = 1, genre = "Horror"), // not in profile -> score 0
            testVod(streamId = 2, genre = "Action")
        )
        val profile = profileWithSignals(mapOf("action" to 3.0))

        val result = RecommendationEngine.recommend(profile, cachedData(movies = movies))

        assertEquals(listOf(2), result.movies.map { it.streamId })
    }

    @Test
    fun `tie break sorts by rating then name`() {
        val movies = listOf(
            testVod(streamId = 1, genre = "Action", name = "Zeta", rating = "5"),
            testVod(streamId = 2, genre = "Action", name = "Alpha", rating = "9"),
            testVod(streamId = 3, genre = "Action", name = "Beta", rating = "9")
        )
        val profile = profileWithSignals(mapOf("action" to 3.0))

        val result = RecommendationEngine.recommend(profile, cachedData(movies = movies))

        assertEquals(listOf(2, 3, 1), result.movies.map { it.streamId })
    }

    @Test
    fun `cold start with fewer than five signals yields empty result`() {
        val movies = listOf(testVod(streamId = 1, genre = "Action"))
        val profile = profileWithSignals(weights = mapOf("action" to 3.0), signalCount = 4)

        val result = RecommendationEngine.recommend(profile, cachedData(movies = movies))

        assertTrue(result.movies.isEmpty())
        assertFalse(result.movies.isNotEmpty())
    }

    @Test
    fun `result is capped at top 20 candidates`() {
        val movies = (1..30).map { testVod(streamId = it, genre = "Action", name = "Movie$it") }
        val profile = profileWithSignals(mapOf("action" to 3.0))

        val result = RecommendationEngine.recommend(profile, cachedData(movies = movies))

        assertEquals(20, result.movies.size)
    }

    @Test
    fun `fallback ranks genre-less movies by rating desc then name asc`() {
        val movies = listOf(
            testVod(streamId = 1, genre = null, name = "Zeta", rating = "5"),
            testVod(streamId = 2, genre = null, name = "Alpha", rating = "9"),
            testVod(streamId = 3, genre = null, name = "Beta", rating = "7")
        )
        val profile = profileWithSignals(mapOf("action" to 3.0))

        val result = RecommendationEngine.recommend(profile, cachedData(movies = movies))

        assertEquals(listOf(2, 3, 1), result.movies.map { it.streamId })
    }

    @Test
    fun `fallback ranks genre-less series by rating desc then name asc`() {
        val series = listOf(
            testSeries(seriesId = 1, genre = null, name = "Zeta", rating = "5"),
            testSeries(seriesId = 2, genre = null, name = "Alpha", rating = "9"),
            testSeries(seriesId = 3, genre = null, name = "Beta", rating = "7")
        )
        val profile = profileWithSignals(mapOf("action" to 3.0))

        val result = RecommendationEngine.recommend(profile, cachedData(series = series))

        assertEquals(listOf(2, 3, 1), result.series.map { it.seriesId })
    }

    @Test
    fun `fallback does not mix in when genre based matches exist`() {
        val movies = listOf(
            testVod(streamId = 1, genre = "Action", name = "Match", rating = "1"),
            testVod(streamId = 2, genre = null, name = "NoGenre", rating = "10")
        )
        val profile = profileWithSignals(mapOf("action" to 3.0))

        val result = RecommendationEngine.recommend(profile, cachedData(movies = movies))

        assertEquals(listOf(1), result.movies.map { it.streamId })
    }

    @Test
    fun `fallback respects movie and series exclusions`() {
        val movies = listOf(
            testVod(streamId = 1, genre = null, name = "Excluded", rating = "10"),
            testVod(streamId = 2, genre = null, name = "Kept", rating = "5")
        )
        val series = listOf(
            testSeries(seriesId = 10, genre = null, name = "ExcludedSeries", rating = "10"),
            testSeries(seriesId = 11, genre = null, name = "KeptSeries", rating = "5")
        )
        val profile = GenreProfile(
            weights = mapOf("action" to 3.0),
            signalCount = 5,
            excludedMovieIds = setOf(1),
            excludedSeriesIds = setOf(10)
        )

        val result = RecommendationEngine.recommend(profile, cachedData(movies = movies, series = series))

        assertEquals(listOf(2), result.movies.map { it.streamId })
        assertEquals(listOf(11), result.series.map { it.seriesId })
    }

    @Test
    fun `cold start yields empty fallback despite high ratings`() {
        val movies = listOf(testVod(streamId = 1, genre = null, rating = "10"))
        val series = listOf(testSeries(seriesId = 1, genre = null, rating = "10"))
        val profile = profileWithSignals(weights = mapOf("action" to 3.0), signalCount = 4)

        val result = RecommendationEngine.recommend(profile, cachedData(movies = movies, series = series))

        assertTrue(result.movies.isEmpty())
        assertTrue(result.series.isEmpty())
    }

    @Test
    fun `genre based list caps dominant genre at 5 and fills rest from other genre`() {
        // Descending rating keyed to streamId keeps sort order equal to ascending streamId,
        // independent of string-vs-numeric name comparison.
        val dominant = (1..12).map { testVod(streamId = it, genre = "Animation", rating = (200 - it).toString()) }
        val other = (101..105).map { testVod(streamId = it, genre = "Drama", rating = (300 - it).toString()) }
        val profile = profileWithSignals(mapOf("animation" to 30.0, "drama" to 6.0))

        // limitPerType = 10 keeps phase 2 from needing to raid the capped dominant genre again,
        // isolating the cap's effect.
        val result = RecommendationEngine.recommend(profile, cachedData(movies = dominant + other), limitPerType = 10)

        assertEquals(listOf(1, 2, 3, 4, 5, 101, 102, 103, 104, 105), result.movies.map { it.streamId })
    }

    @Test
    fun `phase two fills remaining slots after cap exhausts all genres`() {
        val dominant = (1..6).map { testVod(streamId = it, genre = "Animation", rating = (200 - it).toString()) }
        val other = (101..103).map { testVod(streamId = it, genre = "Drama", rating = (300 - it).toString()) }
        val profile = profileWithSignals(mapOf("animation" to 30.0, "drama" to 6.0))

        val result = RecommendationEngine.recommend(profile, cachedData(movies = dominant + other))

        // 9 total candidates, limit is 20 -> every candidate must appear despite the cap.
        // Phase 1 fills [1..5, 101..103] (id 6 skipped, cap reached); phase 2 appends id 6.
        assertEquals(listOf(1, 2, 3, 4, 5, 101, 102, 103, 6), result.movies.map { it.streamId })
    }

    @Test
    fun `single genre result is padded past the cap by phase two instead of being shortened`() {
        val movies = (1..20).map { testVod(streamId = it, genre = "Animation", rating = (300 - it).toString()) }
        val profile = profileWithSignals(mapOf("animation" to 30.0))

        val result = RecommendationEngine.recommend(profile, cachedData(movies = movies))

        assertEquals(20, result.movies.size)
        assertEquals((1..20).toList(), result.movies.map { it.streamId })
    }

    @Test
    fun `diversity cap preserves score desc rating desc name asc ordering`() {
        val movies = listOf(
            testVod(streamId = 1, genre = "Action", name = "Zeta", rating = "5"),
            testVod(streamId = 2, genre = "Action", name = "Alpha", rating = "9"),
            testVod(streamId = 3, genre = "Action", name = "Beta", rating = "9"),
            testVod(streamId = 4, genre = "Drama", name = "Delta", rating = "1")
        )
        val profile = profileWithSignals(mapOf("action" to 3.0, "drama" to 1.0))

        val result = RecommendationEngine.recommend(profile, cachedData(movies = movies))

        assertEquals(listOf(2, 3, 1, 4), result.movies.map { it.streamId })
    }

    @Test
    fun `fallback ordering is deterministic across repeated calls`() {
        val movies = listOf(
            testVod(streamId = 1, genre = null, name = "Beta", rating = "5"),
            testVod(streamId = 2, genre = null, name = "Alpha", rating = "5")
        )
        val profile = profileWithSignals(mapOf("action" to 3.0))

        val first = RecommendationEngine.recommend(profile, cachedData(movies = movies)).movies.map { it.streamId }
        val second = RecommendationEngine.recommend(profile, cachedData(movies = movies)).movies.map { it.streamId }

        assertEquals(listOf(2, 1), first)
        assertEquals(first, second)
    }
}
