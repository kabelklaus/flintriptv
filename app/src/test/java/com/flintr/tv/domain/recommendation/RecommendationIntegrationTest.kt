package com.flintr.tv.domain.recommendation

import com.flintr.tv.data.local.CachedData
import com.flintr.tv.data.model.Category
import com.flintr.tv.data.model.Series
import com.flintr.tv.data.model.VodStream
import com.flintr.tv.data.model.WatchlistEntry
import com.flintr.tv.data.model.WatchlistEntryType
import com.flintr.tv.data.tmdb.TmdbGenre
import com.flintr.tv.data.tmdb.TmdbMeta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun testVod(streamId: Int, tmdbId: String? = null, name: String = "Movie$streamId") = VodStream(
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

private fun cachedData(movies: List<VodStream> = emptyList(), series: List<Series> = emptyList()) = CachedData(
    channels = emptyList(),
    movies = movies,
    series = series,
    liveCategories = emptyList<Category>(),
    vodCategories = emptyList<Category>(),
    seriesCategories = emptyList<Category>()
)

private fun metaLookup(vararg entries: Pair<Int, List<Int>>): (String, Int) -> TmdbMeta? {
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

private fun watchlist(vararg streamIds: Int) = streamIds.map {
    WatchlistEntry.create(id = "movie:$it", type = WatchlistEntryType.MOVIE, streamId = it, name = "Signal$it")
}

/**
 * Kette Profil(metaLookup) -> Engine.select: Genre-Signale stammen aus TMDB-Metas, die Auswahl
 * ausschliesslich aus den vorab geladenen Discover-Listen in TMDB-Reihenfolge.
 */
class RecommendationIntegrationTest {

    @Test
    fun `Genre-28-Signale fuehren zu Discover-Treffern des Genres im Result`() {
        val signalMovies = (901..905).map { testVod(it, tmdbId = "60$it") }
        val profile = UserContentProfile.build(
            signals = UserContentProfile.UserSignalData(
                watchlistEntries = watchlist(901, 902, 903, 904, 905),
                watchedHistory = emptyMap(),
                watchProgress = emptyMap(),
                ratings = emptyMap(),
                movies = signalMovies,
                series = emptyList()
            ),
            // Genre-Lookup ueber die tmdbId (60xxx), nicht ueber die Xtream-streamId (90x).
            metaLookup = metaLookup(*(901..905).map { "60$it".toInt() to listOf(28) }.toTypedArray())
        )
        assertEquals(5, profile.signalCount)

        val catalog = listOf(
            testVod(1, tmdbId = "7001"),
            testVod(2, tmdbId = "7002"),
            testVod(3, tmdbId = "7003")
        )

        val result = RecommendationEngine.recommend(
            profile = profile,
            cached = cachedData(movies = catalog),
            discoveredMoviesByGenre = mapOf(28 to listOf(7002, 7001)),
            discoveredSeriesByGenre = emptyMap()
        )

        assertEquals(listOf(2, 1), result.movies.map { it.streamId })
    }

    @Test
    fun `ohne genug Signale bleibt das Result bei leeren Discover-Maps leer`() {
        val profile = UserContentProfile.build(
            signals = UserContentProfile.UserSignalData(
                watchlistEntries = watchlist(801, 802, 803),
                watchedHistory = emptyMap(),
                watchProgress = emptyMap(),
                ratings = emptyMap(),
                movies = (801..803).map { testVod(it, tmdbId = "60$it") },
                series = emptyList()
            ),
            metaLookup = metaLookup(60801 to listOf(28), 60802 to listOf(28), 60803 to listOf(28))
        )
        assertTrue(profile.signalCount < 5)

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
    fun `Diversitaets-Cap haelt auch durch die volle Kette`() {
        val profile = UserContentProfile.build(
            signals = UserContentProfile.UserSignalData(
                watchlistEntries = watchlist(901, 902, 903, 904, 905),
                watchedHistory = emptyMap(),
                watchProgress = emptyMap(),
                ratings = emptyMap(),
                movies = (901..905).map { testVod(it, tmdbId = "60$it") },
                series = emptyList()
            ),
            // 28 ist staerker gewichtet als 18, damit die Genre-Reihenfolge deterministisch ist.
            metaLookup = metaLookup(
                60901 to listOf(28), 60902 to listOf(28), 60903 to listOf(28),
                60904 to listOf(28, 18), 60905 to listOf(28, 18)
            )
        )

        val dominant = (1..8).map { testVod(it, tmdbId = "70${it.toString().padStart(2, '0')}") }
        val other = (11..13).map { testVod(it, tmdbId = "71${it.toString().padStart(2, '0')}") }

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

        assertEquals(listOf(1, 2, 3, 4, 5, 11, 12, 13, 6, 7), result.movies.map { it.streamId })
    }
}
