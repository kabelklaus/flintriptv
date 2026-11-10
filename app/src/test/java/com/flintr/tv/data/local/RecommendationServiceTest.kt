package com.flintr.tv.data.local

import com.flintr.tv.data.model.Category
import com.flintr.tv.data.model.WatchProgress
import com.flintr.tv.data.model.WatchlistEntry
import com.flintr.tv.data.model.WatchlistEntryType
import com.flintr.tv.data.model.Series
import com.flintr.tv.data.model.VodStream
import com.flintr.tv.data.repository.UserRepository
import com.flintr.tv.data.tmdb.TmdbGenre
import com.flintr.tv.data.tmdb.TmdbMeta
import com.flintr.tv.data.tmdb.TmdbMetaRepository
import com.flintr.tv.testutil.silentCredentialsFlow
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecommendationServiceTest {

    private companion object {
        // RecommendationService.RATINGS_CACHE_TTL_MS is private; pinned here on purpose.
        const val RATINGS_CACHE_TTL_MS = 5 * 60 * 1000L
    }

    private val credentials = Credentials(serverUrl = "http://server", username = "chris", password = "pw")
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var watchlistRepository: WatchlistRepository
    private lateinit var watchedHistoryService: WatchedHistoryService
    private lateinit var watchProgressService: WatchProgressService
    private lateinit var userRepository: UserRepository
    private lateinit var credentialsRepository: CredentialsRepository
    private lateinit var cacheService: DataCacheService
    private lateinit var tmdbMetaRepository: TmdbMetaRepository
    private lateinit var service: RecommendationService

    private lateinit var watchlistFlow: MutableStateFlow<List<WatchlistEntry>>
    private lateinit var watchedHistoryFlow: MutableStateFlow<Map<String, com.flintr.tv.data.model.WatchedItem>>
    private lateinit var watchProgressFlow: MutableStateFlow<Map<String, com.flintr.tv.data.model.WatchProgress>>
    private lateinit var metaFlow: MutableStateFlow<Map<String, TmdbMeta>>

    private fun testVod(streamId: Int, genre: String, tmdbId: String? = null) = VodStream(
        num = null, name = "Movie$streamId", streamType = null, streamId = streamId,
        streamIcon = null, rating = null, rating5Based = null, added = null,
        categoryId = null, categoryName = null, containerExtension = null, customSid = null,
        directSource = null, releaseDate = null, duration = null, plot = null, cast = null,
        director = null, genre = genre, backdropPath = null, youtubeTrailer = null,
        cover = null, tmdbId = tmdbId
    )

    private fun testSeries(seriesId: Int, tmdbId: String) = Series(
        num = null, name = "Series$seriesId", seriesId = seriesId, cover = null, plot = null,
        cast = null, director = null, genre = "Xtream-Genre", releaseDate = null,
        rating = null, rating5Based = null, lastModified = null, categoryId = null,
        categoryName = null, youtubeTrailer = null, backdropPath = null, seasons = null,
        tmdbId = tmdbId
    )

    private fun meta(genreId: Int) = TmdbMeta(
        tmdbId = 0, rating = 0.0, voteCount = 0,
        genres = listOf(TmdbGenre(genreId, "Genre$genreId"))
    )

    @Before
    fun setUp() {
        watchlistFlow = MutableStateFlow<List<WatchlistEntry>>(emptyList())
        watchedHistoryFlow = MutableStateFlow<Map<String, com.flintr.tv.data.model.WatchedItem>>(emptyMap())
        watchProgressFlow = MutableStateFlow<Map<String, com.flintr.tv.data.model.WatchProgress>>(emptyMap())
        metaFlow = MutableStateFlow(emptyMap())

        watchlistRepository = mockk(relaxed = true)
        watchedHistoryService = mockk(relaxed = true)
        watchProgressService = mockk(relaxed = true)
        userRepository = mockk(relaxed = true)
        credentialsRepository = mockk()
        cacheService = mockk(relaxed = true)
        tmdbMetaRepository = mockk(relaxed = true)

        every { tmdbMetaRepository.metaFlow } returns metaFlow
        coEvery { tmdbMetaRepository.discoverMovieIds(any()) } returns emptyList()
        coEvery { tmdbMetaRepository.discoverTvIds(any()) } returns emptyList()

        every { watchlistRepository.entries } returns watchlistFlow
        every { watchedHistoryService.watchedHistory } returns watchedHistoryFlow
        every { watchProgressService.watchProgress } returns watchProgressFlow
        every { credentialsRepository.credentialsFlow } returns silentCredentialsFlow
        every { credentialsRepository.getCredentials() } returns credentials
        coEvery { userRepository.loadAllRatings(any()) } returns Result.success(emptyMap())

        val catalogMovies = (1..5).map { testVod(it, "Action") } + listOf(
            testVod(101, "Action", tmdbId = "9101"),
            testVod(102, "Action", tmdbId = "9102")
        )
        coEvery { cacheService.loadCachedDataAsync() } returns CachedData(
            channels = emptyList(),
            movies = catalogMovies,
            series = emptyList<Series>(),
            liveCategories = emptyList<Category>(),
            vodCategories = emptyList<Category>(),
            seriesCategories = emptyList<Category>()
        )

        service = RecommendationService(
            watchlistRepository = watchlistRepository,
            watchedHistoryService = watchedHistoryService,
            watchProgressService = watchProgressService,
            userRepository = userRepository,
            credentialsRepository = credentialsRepository,
            cacheService = cacheService,
            tmdbMetaRepository = tmdbMetaRepository,
            ioDispatcher = testDispatcher,
            // TTL is driven by the virtual scheduler, so the test can advance past it.
            nowMs = { testDispatcher.scheduler.currentTime }
        )
    }

    private fun catalog(vararg movies: VodStream, series: List<Series> = emptyList()) = CachedData(
        channels = emptyList(),
        movies = movies.toList(),
        series = series,
        liveCategories = emptyList(),
        vodCategories = emptyList(),
        seriesCategories = emptyList()
    )

    @After
    fun tearDown() {
        val field = RecommendationService::class.java.getDeclaredField("serviceScope")
        field.isAccessible = true
        val scope = field.get(service) as CoroutineScope
        scope.cancel()
        testDispatcher.scheduler.advanceUntilIdle()
    }

    private fun entry(streamId: Int) = WatchlistEntry.create(
        id = "movie:$streamId:x",
        type = WatchlistEntryType.MOVIE,
        streamId = streamId,
        name = "Movie$streamId"
    )

    @Test
    fun `adding watchlist entries recomputes recommendations`() = runTest(testDispatcher) {
        advanceUntilIdle()
        assertTrue(service.recommendations.value.movies.isEmpty())

        metaFlow.value = (1..5).associate { "movie:$it" to meta(28) }
        coEvery { tmdbMetaRepository.discoverMovieIds(listOf(28)) } returns listOf(9101, 9102)

        watchlistFlow.value = (1..5).map { entry(it) }
        advanceUntilIdle()

        val recommendedIds = service.recommendations.value.movies.map { it.streamId }
        assertEquals(setOf(101, 102), recommendedIds.toSet())
        assertTrue((1..5).none { it in recommendedIds })
    }

    @Test
    fun `Meta-Nachlaeufe triggern Recompute ohne User-Daten-Aenderung`() = runTest(testDispatcher) {
        // User-Daten liegen bereits vor, aber ohne TMDB-Metas fehlen die Genre-Signale.
        watchlistFlow.value = (1..5).map { entry(it) }
        advanceUntilIdle()

        assertTrue("cold start without meta publishes empty", service.recommendations.value.movies.isEmpty())
        coVerify(exactly = 0) { tmdbMetaRepository.discoverMovieIds(any()) }

        // Nur metaFlow aendert sich: 5 Metas der gesehenen Items kommen nach.
        // Discover liefert tmdbIds 9101/9102 -> Katalog-Items 101/102.
        coEvery { tmdbMetaRepository.discoverMovieIds(listOf(28)) } returns listOf(9101, 9102)
        metaFlow.value = (1..5).associate { "movie:$it" to meta(28) }
        advanceUntilIdle()

        val recommendedIds = service.recommendations.value.movies.map { it.streamId }.toSet()
        assertEquals(setOf(101, 102), recommendedIds)
        coVerify(exactly = 1) { tmdbMetaRepository.discoverMovieIds(listOf(28)) }
    }

    @Test
    fun `Cold Start unter fuenf Signalen macht keine Discover-Calls`() = runTest(testDispatcher) {
        metaFlow.value = (1..3).associate { "movie:$it" to meta(28) }
        watchlistFlow.value = (1..3).map { entry(it) }
        advanceUntilIdle()

        assertTrue(service.recommendations.value.movies.isEmpty())
        coVerify(exactly = 0) { tmdbMetaRepository.discoverMovieIds(any()) }
        coVerify(exactly = 0) { tmdbMetaRepository.discoverTvIds(any()) }
    }

    @Test
    fun `volle Kette mappt Discover-IDs auf Katalog-Items`() = runTest(testDispatcher) {
        metaFlow.value = (1..5).associate { "movie:$it" to meta(28) }
        coEvery { tmdbMetaRepository.discoverMovieIds(listOf(28)) } returns listOf(101, 102)
        coEvery { tmdbMetaRepository.discoverTvIds(listOf(28)) } returns listOf(201)
        coEvery { cacheService.loadCachedDataAsync() } returns catalog(
            testVod(101, "Xtream-Action", tmdbId = "101"),
            testVod(102, "Xtream-Action", tmdbId = "102"),
            testVod(1, "Xtream-Action", tmdbId = "1"),
            testVod(2, "Xtream-Action", tmdbId = "2"),
            testVod(3, "Xtream-Action", tmdbId = "3"),
            testVod(4, "Xtream-Action", tmdbId = "4"),
            testVod(5, "Xtream-Action", tmdbId = "5"),
            series = listOf(testSeries(201, tmdbId = "201"))
        )

        watchlistFlow.value = (1..5).map { entry(it) }
        advanceUntilIdle()

        assertEquals(listOf(101, 102), service.recommendations.value.movies.map { it.streamId })
        assertEquals(listOf(201), service.recommendations.value.series.map { it.seriesId })
    }

    @Test
    fun `leere Discover-Antwort liefert leere Empfehlungen statt Crash`() = runTest(testDispatcher) {
        metaFlow.value = (1..5).associate { "movie:$it" to meta(28) }
        coEvery { tmdbMetaRepository.discoverMovieIds(any()) } returns emptyList()
        coEvery { tmdbMetaRepository.discoverTvIds(any()) } returns emptyList()

        watchlistFlow.value = (1..5).map { entry(it) }
        advanceUntilIdle()

        assertTrue(service.recommendations.value.movies.isEmpty())
        assertTrue(service.recommendations.value.series.isEmpty())
    }

    // --- toRecommendationSignal: pinnt die UserContentProfile-Schwellen 0.2 / 0.9 ---

    @Test
    fun `toRecommendationSignal bildet Ratio-Grenzen auf 0 10 50 100 ab`() {
        // 0.2 und 0.9 sind inclusive (>=) - exakt wie EPISODE_STARTED/COMPLETED_THRESHOLD.
        val cases = listOf(
            0L to 0L,
            190L to 10L,
            200L to 50L,
            890L to 50L,
            900L to 100L,
            1_000L to 100L
        )

        cases.forEach { (position, expectedBucket) ->
            val signal = with(service) {
                WatchProgress(position, 1_000L, "2026-07-16T10:00:00Z", "Titel")
                    .toRecommendationSignal()
            }
            assertEquals("position=$position", expectedBucket, signal.position)
            assertEquals("position=$position", 100L, signal.duration)
        }
    }

    @Test
    fun `toRecommendationSignal behandelt ungueltige Dauer als Bucket 0 ohne Division`() {
        listOf(0L, -5L).forEach { duration ->
            val signal = with(service) {
                WatchProgress(500L, duration, "2026-07-16T10:00:00Z", "Titel")
                    .toRecommendationSignal()
            }
            assertEquals(0L, signal.position)
            assertEquals(100L, signal.duration)
        }
    }

    @Test
    fun `toRecommendationSignal pinnt den lastWatched-Fallback exakt`() {
        fun signalFor(lastWatched: String) = with(service) {
            WatchProgress(500L, 1_000L, lastWatched, "Titel").toRecommendationSignal()
        }

        // Tag ohne 'T': wird als Datum erkannt und auf Mitternacht normalisiert.
        assertEquals("2026-07-16T00:00:00Z", signalFor("2026-07-16").lastWatched)
        // Kein 'T' und Laenge != 10: unveraendert durchgereicht (kein Crash).
        assertEquals("2026-7-6", signalFor("2026-7-6").lastWatched)
        // Voller Zeitstempel: auf den Tag normalisiert.
        assertEquals("2026-07-16T00:00:00Z", signalFor("2026-07-16T10:00:00Z").lastWatched)
    }

    // --- collectLatest-Supersede ---

    @Test
    fun `collectLatest unterdrückt den überholten Lauf und publiziert nur das finale Ergebnis`() = runTest(testDispatcher) {
        val published = mutableListOf<com.flintr.tv.domain.recommendation.RecommendationResult>()
        val collector = launch { service.recommendations.collect { published += it } }
        advanceUntilIdle()
        assertEquals("initial empty result", 1, published.size)
        assertTrue("initial result is empty", published.single().movies.isEmpty())
        // Only count the recomputes triggered by the two rapid inputs below.
        published.clear()

        fun progress(vararg streamIds: Int) = streamIds.associate { streamId ->
            "movie:$streamId" to WatchProgress(950L, 1_000L, "2026-07-16T10:00:00Z", "Movie$streamId")
        }
        metaFlow.value = (1..5).associate { "movie:$it" to meta(28) } +
            (200..204).associate { "movie:$it" to meta(18) }
        coEvery { tmdbMetaRepository.discoverMovieIds(listOf(28)) } returns listOf(100, 101)
        coEvery { tmdbMetaRepository.discoverMovieIds(listOf(18)) } returns listOf(300, 301)
        val cachedCatalog = catalog(
            testVod(1, "Xtream"),
            testVod(2, "Xtream"),
            testVod(3, "Xtream"),
            testVod(4, "Xtream"),
            testVod(5, "Xtream"),
            testVod(100, "Xtream", tmdbId = "100"),
            testVod(101, "Xtream", tmdbId = "101"),
            testVod(200, "Xtream"),
            testVod(201, "Xtream"),
            testVod(202, "Xtream"),
            testVod(203, "Xtream"),
            testVod(204, "Xtream"),
            testVod(300, "Xtream", tmdbId = "300"),
            testVod(301, "Xtream", tmdbId = "301")
        )
        coEvery { cacheService.loadCachedDataAsync() } coAnswers {
            delay(1_000)
            cachedCatalog
        }

        // Lauf A: startet und haengt im verzoegerten Cache-Load.
        watchProgressFlow.value = progress(1, 2, 3, 4, 5)
        advanceTimeBy(650)
        assertTrue("run A must still be blocked in cache load", published.isEmpty())

        // Lauf B kommt waehrend A haengt -> collectLatest cancelt A vor dem Publish.
        watchProgressFlow.value = progress(200, 201, 202, 203, 204)
        advanceTimeBy(650)
        advanceUntilIdle()

        collector.cancel()
        assertEquals("only the final run may publish", 1, published.size)
        assertEquals(service.recommendations.value, published.single())
        val publishedIds = published.single().movies.map { it.streamId }.toSet()
        assertEquals("published result must come from the second input", setOf(300, 301), publishedIds)
        assertFalse("cancelled run A must not publish", 100 in publishedIds || 101 in publishedIds)
    }

    @Test
    fun `abgeschlossener erster Lauf und zweiter Lauf publizieren beide Ergebnisse`() = runTest(testDispatcher) {
        val published = mutableListOf<com.flintr.tv.domain.recommendation.RecommendationResult>()
        val collector = launch { service.recommendations.collect { published += it } }
        advanceUntilIdle()
        assertEquals("initial empty result", 1, published.size)
        published.clear()

        fun progress(vararg streamIds: Int) = streamIds.associate { streamId ->
            "movie:$streamId" to WatchProgress(950L, 1_000L, "2026-07-16T10:00:00Z", "Movie$streamId")
        }
        metaFlow.value = (1..5).associate { "movie:$it" to meta(28) } +
            (200..204).associate { "movie:$it" to meta(18) }
        coEvery { tmdbMetaRepository.discoverMovieIds(listOf(28)) } returns listOf(100, 101)
        coEvery { tmdbMetaRepository.discoverMovieIds(listOf(18)) } returns listOf(300, 301)
        val cachedCatalog = catalog(
            testVod(1, "Xtream"),
            testVod(2, "Xtream"),
            testVod(3, "Xtream"),
            testVod(4, "Xtream"),
            testVod(5, "Xtream"),
            testVod(100, "Xtream", tmdbId = "100"),
            testVod(101, "Xtream", tmdbId = "101"),
            testVod(200, "Xtream"),
            testVod(201, "Xtream"),
            testVod(202, "Xtream"),
            testVod(203, "Xtream"),
            testVod(204, "Xtream"),
            testVod(300, "Xtream", tmdbId = "300"),
            testVod(301, "Xtream", tmdbId = "301")
        )
        coEvery { cacheService.loadCachedDataAsync() } returns cachedCatalog

        // Lauf A darf vollstaendig publizieren, bevor B ueberhaupt emittiert.
        watchProgressFlow.value = progress(1, 2, 3, 4, 5)
        advanceUntilIdle()
        assertEquals("first run publishes its own result", 1, published.size)
        assertEquals(
            "first result must come from the first input",
            setOf(100, 101),
            published.single().movies.map { it.streamId }.toSet()
        )

        watchProgressFlow.value = progress(200, 201, 202, 203, 204)
        advanceUntilIdle()

        collector.cancel()
        assertEquals("initial run plus both completed recomputes publish", 2, published.size)
        assertEquals(
            "second result must come from the second input",
            setOf(300, 301),
            published.last().movies.map { it.streamId }.toSet()
        )
    }

    // --- Ratings-TTL ---

    @Test
    fun `Ratings-Cache laedt innerhalb der TTL einmal und nach Ablauf erneut`() = runTest(testDispatcher) {
        var ratingsLoads = 0
        coEvery { userRepository.loadAllRatings("chris") } coAnswers {
            ratingsLoads++
            Result.success(emptyMap())
        }

        advanceUntilIdle()
        assertEquals(1, ratingsLoads)

        watchlistFlow.value = listOf(entry(1))
        advanceUntilIdle()
        watchlistFlow.value = listOf(entry(2))
        advanceUntilIdle()
        assertEquals("TTL hit must not refetch", 1, ratingsLoads)

        advanceTimeBy(RATINGS_CACHE_TTL_MS + 1_000)
        watchlistFlow.value = listOf(entry(3))
        advanceUntilIdle()
        assertEquals("TTL expired must refetch", 2, ratingsLoads)
    }

    @Test
    fun `fehlgeschlagene Ratings werden nicht gecacht`() = runTest(testDispatcher) {
        var ratingsLoads = 0
        coEvery { userRepository.loadAllRatings("chris") } coAnswers {
            ratingsLoads++
            if (ratingsLoads == 1) {
                Result.failure(Exception("offline"))
            } else {
                Result.success(emptyMap())
            }
        }

        advanceUntilIdle()
        assertEquals("first run failed", 1, ratingsLoads)

        watchlistFlow.value = listOf(entry(1))
        advanceUntilIdle()

        assertEquals("failure must not populate the cache", 2, ratingsLoads)
    }
}
