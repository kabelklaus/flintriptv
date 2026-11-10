package com.flintr.tv.domain.recommendation

import com.flintr.tv.data.local.CachedData
import com.flintr.tv.data.model.Category
import com.flintr.tv.data.model.Series
import com.flintr.tv.data.model.VodStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun testVod(
    streamId: Int,
    name: String = "Movie$streamId",
    tmdbId: String? = null
) = VodStream(
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
    genre = null,
    backdropPath = null,
    youtubeTrailer = null,
    cover = null,
    tmdbId = tmdbId
)

private fun testSeries(
    seriesId: Int,
    name: String = "Series$seriesId",
    tmdbId: String? = null
) = Series(
    num = null,
    name = name,
    seriesId = seriesId,
    cover = null,
    plot = null,
    cast = null,
    director = null,
    genre = null,
    releaseDate = null,
    rating = null,
    rating5Based = null,
    lastModified = null,
    categoryId = null,
    categoryName = null,
    youtubeTrailer = null,
    backdropPath = null,
    seasons = null,
    tmdbId = tmdbId
)

private fun cachedData(movies: List<VodStream> = emptyList(), series: List<Series> = emptyList()) = CachedData(
    channels = emptyList(),
    movies = movies,
    series = series,
    liveCategories = emptyList<Category>(),
    vodCategories = emptyList<Category>(),
    seriesCategories = emptyList<Category>()
)

private fun profileWith(weights: Map<Int, Double>, signalCount: Int = 5) =
    GenreProfile(weights = weights, signalCount = signalCount)

class RecommendationEngineTest {

    @Test
    fun `mapping laeuft ausschliesslich ueber die numerische tmdbId`() {
        val movies = listOf(
            testVod(streamId = 1, tmdbId = "7001"),
            testVod(streamId = 2, tmdbId = null),
            testVod(streamId = 3, tmdbId = "abc"),
            testVod(streamId = 4, tmdbId = "7999")
        )
        val profile = profileWith(mapOf(28 to 3.0))

        val result = RecommendationEngine.recommend(
            profile = profile,
            cached = cachedData(movies = movies),
            discoveredMoviesByGenre = mapOf(28 to listOf(7001, 7002)),
            discoveredSeriesByGenre = emptyMap()
        )

        assertEquals(listOf(1), result.movies.map { it.streamId })
    }

    @Test
    fun `TMDB-Reihenfolge der Discover-IDs bleibt erhalten`() {
        val movies = listOf(
            testVod(streamId = 1, tmdbId = "7001"),
            testVod(streamId = 2, tmdbId = "7002"),
            testVod(streamId = 3, tmdbId = "7003")
        )
        val profile = profileWith(mapOf(28 to 3.0))

        val result = RecommendationEngine.recommend(
            profile = profile,
            cached = cachedData(movies = movies),
            discoveredMoviesByGenre = mapOf(28 to listOf(7003, 7001, 7002)),
            discoveredSeriesByGenre = emptyMap()
        )

        assertEquals(listOf(3, 1, 2), result.movies.map { it.streamId })
    }

    @Test
    fun `Diversitaet caps bei fuenf pro Genre und Phase zwei fuellt den Rest`() {
        val dominant = (1..8).map { testVod(streamId = it, tmdbId = "70${it.toString().padStart(2, '0')}") }
        val other = (11..13).map { testVod(streamId = it, tmdbId = "71${it.toString().padStart(2, '0')}") }
        val profile = profileWith(mapOf(28 to 30.0, 18 to 6.0))

        val result = RecommendationEngine.recommend(
            profile = profile,
            cached = cachedData(movies = dominant + other),
            discoveredMoviesByGenre = mapOf(
                28 to listOf(7001, 7002, 7003, 7004, 7005, 7006, 7007, 7008),
                18 to listOf(7111, 7112, 7113)
            ),
            discoveredSeriesByGenre = emptyMap(),
            limitPerType = 10
        )

        // Phase 1: 5x Genre 28, 3x Genre 18; Phase 2: die restlichen Genre-28-Treffer.
        assertEquals(listOf(1, 2, 3, 4, 5, 11, 12, 13, 6, 7), result.movies.map { it.streamId })
    }

    @Test
    fun `Exclusions greifen in beiden Phasen`() {
        val movies = (1..4).map { testVod(streamId = it, tmdbId = "700$it") }
        val profile = GenreProfile(
            weights = mapOf(28 to 3.0),
            signalCount = 5,
            excludedMovieIds = setOf(1, 3)
        )

        val result = RecommendationEngine.recommend(
            profile = profile,
            cached = cachedData(movies = movies),
            discoveredMoviesByGenre = mapOf(28 to listOf(7001, 7002, 7003, 7004)),
            discoveredSeriesByGenre = emptyMap()
        )

        assertEquals(listOf(2, 4), result.movies.map { it.streamId })
    }

    @Test
    fun `leere Discover-Maps liefern ein leeres Result`() {
        val profile = profileWith(mapOf(28 to 3.0))

        val result = RecommendationEngine.recommend(
            profile = profile,
            cached = cachedData(movies = listOf(testVod(1, tmdbId = "7001"))),
            discoveredMoviesByGenre = emptyMap(),
            discoveredSeriesByGenre = emptyMap()
        )

        assertTrue(result.movies.isEmpty())
        assertTrue(result.series.isEmpty())
    }

    @Test
    fun `Dedupe ueber Genres hinweg`() {
        val movies = listOf(
            testVod(streamId = 1, tmdbId = "7001"),
            testVod(streamId = 2, tmdbId = "7002")
        )
        val profile = profileWith(mapOf(28 to 30.0, 18 to 6.0))

        val result = RecommendationEngine.recommend(
            profile = profile,
            cached = cachedData(movies = movies),
            discoveredMoviesByGenre = mapOf(
                28 to listOf(7001, 7002),
                18 to listOf(7001, 7002)
            ),
            discoveredSeriesByGenre = emptyMap()
        )

        assertEquals(listOf(1, 2), result.movies.map { it.streamId })
    }

    @Test
    fun `selectSimilarMovies behaelt Anchor-Reihenfolge, excludiert und limitiert`() {
        val movies = listOf(
            testVod(streamId = 1, tmdbId = "7001"),
            testVod(streamId = 2, tmdbId = "7002"),
            testVod(streamId = 3, tmdbId = "7003")
        )

        val result = RecommendationEngine.selectSimilarMovies(
            anchorTmdbIds = listOf(7003, 7999, 7001, 7002, 7003),
            cached = cachedData(movies = movies),
            excludedMovieIds = setOf(2),
            limit = 2
        )

        assertEquals(listOf(3, 1), result.map { it.streamId })
    }

    @Test
    fun `Series werden getrennt ueber eigene tmdbIds gemappt`() {
        val series = listOf(
            testSeries(seriesId = 10, tmdbId = "8001"),
            testSeries(seriesId = 11, tmdbId = "8002")
        )
        val profile = profileWith(mapOf(18 to 2.0))

        val result = RecommendationEngine.recommend(
            profile = profile,
            cached = cachedData(series = series),
            discoveredMoviesByGenre = mapOf(18 to listOf(8001)),
            discoveredSeriesByGenre = mapOf(18 to listOf(8002, 8001))
        )

        assertTrue(result.movies.isEmpty())
        assertEquals(listOf(11, 10), result.series.map { it.seriesId })
    }
}
