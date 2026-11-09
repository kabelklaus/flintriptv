package com.flintr.tv.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.flintr.tv.data.local.CachedData
import com.flintr.tv.data.local.Credentials
import com.flintr.tv.data.local.CredentialsRepository
import com.flintr.tv.data.local.DataCacheService
import com.flintr.tv.data.local.WatchlistRepository
import com.flintr.tv.data.local.LastInteraction
import com.flintr.tv.data.local.SmartResumeTarget
import com.flintr.tv.data.local.WatchProgressService
import com.flintr.tv.data.local.imdb.ImdbEnrichment
import com.flintr.tv.data.local.imdb.ImdbEnrichmentService
import com.flintr.tv.data.model.Episode
import com.flintr.tv.data.model.EpisodeInfo
import com.flintr.tv.data.model.WatchlistEntry
import com.flintr.tv.data.model.WatchlistEntryType
import com.flintr.tv.data.model.Season
import com.flintr.tv.data.model.Series
import com.flintr.tv.data.model.WatchProgress
import com.flintr.tv.data.repository.XtreamRepository
import com.flintr.tv.data.tmdb.TmdbCastClient
import com.flintr.tv.data.tmdb.TmdbCastMember
import com.flintr.tv.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SeriesDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val seriesId = 2200
    private val repository = mockk<XtreamRepository>()
    private val credentialsRepository = mockk<CredentialsRepository>()
    private val imdbEnrichmentService = mockk<ImdbEnrichmentService>(relaxed = true)
    private val watchlistRepository = mockk<WatchlistRepository>(relaxed = true)
    private val watchProgressService = mockk<WatchProgressService>(relaxed = true)
    private val cacheService = mockk<DataCacheService>(relaxed = true)
    private val tmdbCastClient = mockk<TmdbCastClient>(relaxed = true)

    private fun episode(id: Int, episodeNum: Int) = Episode(
        id = id,
        episodeNum = episodeNum,
        title = "Episode $episodeNum",
        containerExtension = "mkv",
        info = EpisodeInfo(
            plot = null,
            duration = "00:42:00",
            movieImage = null,
            releaseDate = null,
            rating = null,
            tmdbId = null
        ),
        subtitles = null,
        added = null,
        season = null,
        directSource = null
    )

    private fun season(number: Int, episodes: List<Episode>) = Season(
        airDate = null,
        episodeCount = episodes.size,
        id = null,
        name = "Staffel $number",
        overview = null,
        seasonNumber = number,
        cover = "https://img/season-$number.jpg",
        episodes = episodes
    )

    private val seasonOne = season(1, listOf(episode(11, 1), episode(12, 2)))
    private val seasonTwo = season(2, listOf(episode(21, 1), episode(22, 2), episode(23, 3)))

    private val series = Series(
        num = null,
        name = "Serie (2020)",
        seriesId = seriesId,
        cover = "https://img/series-cover.jpg",
        plot = "Serienplot.",
        cast = "Alice, Bob",
        director = "Regie",
        genre = "Xtream-Genre",
        releaseDate = "2020-05-01",
        rating = "6.0",
        rating5Based = null,
        lastModified = null,
        categoryId = "7",
        categoryName = null,
        youtubeTrailer = "trailer123",
        backdropPath = listOf("https://img/backdrop.jpg"),
        seasons = listOf(seasonOne, seasonTwo)
    )

    private val recommendedSeries = Series(
        num = null,
        name = "Andere Serie",
        seriesId = 2201,
        cover = "https://img/other-cover.jpg",
        plot = null,
        cast = null,
        director = null,
        genre = "Drama",
        releaseDate = null,
        rating = null,
        rating5Based = null,
        lastModified = null,
        categoryId = "7",
        categoryName = null,
        youtubeTrailer = null,
        backdropPath = null,
        seasons = null
    )

    private fun cachedData(series: List<Series> = listOf(recommendedSeries)) = CachedData(
        channels = emptyList(),
        movies = emptyList(),
        series = series,
        liveCategories = emptyList(),
        vodCategories = emptyList(),
        seriesCategories = emptyList()
    )

    private fun viewModel() = SeriesDetailViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf(SeriesDetailViewModel.ARG_SERIES_ID to seriesId)
        ),
        repository = repository,
        credentialsRepository = credentialsRepository,
        imdbEnrichmentService = imdbEnrichmentService,
        watchlistRepository = watchlistRepository,
        watchProgressService = watchProgressService,
        cacheService = cacheService,
        tmdbCastClient = tmdbCastClient,
        ioDispatcher = mainDispatcherRule.dispatcher
    )

    private fun stubHappyPath(seriesResult: Series = series) {
        every { credentialsRepository.getCredentials() } returns
            Credentials(serverUrl = "http://srv", username = "chris", password = "pw")
        coEvery { repository.getSeriesInfo("chris", "pw", seriesId) } returns Result.success(seriesResult)
        every { imdbEnrichmentService.lookup(any()) } returns
            ImdbEnrichment(rating = 8.1, numVotes = 999, genres = listOf("Drama", "Mystery"))
        every { watchlistRepository.getAllWatchlistEntries() } returns emptyList()
        coEvery { watchProgressService.getWatchedEpisodeIds(seriesId) } returns emptySet()
        coEvery { watchProgressService.getLastInteraction(seriesId, series) } returns null
        coEvery { watchProgressService.getSmartResumeTarget(seriesId, series) } returns null
        coEvery { cacheService.loadCachedDataAsync() } returns cachedData()
        coEvery { tmdbCastClient.tvCredits(any()) } returns emptyList()
    }

    @Test
    fun `load aggregiert Staffeln und Episodenzahlen aus Series`() = runTest {
        stubHappyPath()

        val vm = viewModel()
        val state = vm.uiState.value

        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals(2, state.seasons.size)
        assertEquals(1, state.seasons[0].seasonNumber)
        assertEquals("Staffel 1", state.seasons[0].title)
        assertEquals(2, state.seasons[0].episodeCount)
        assertEquals("https://img/season-1.jpg", state.seasons[0].posterUrl)
        assertEquals(3, state.seasons[1].episodeCount)
    }

    @Test
    fun `ohne Staffel- und Serien-Cover faellt Poster auf erstes Episoden-Still zurueck`() = runTest {
        val stilllessEpisode = episode(31, 1)
        val stillEpisode = episode(32, 2).let {
            it.copy(info = it.info!!.copy(movieImage = "https://img/still-32.jpg"))
        }
        val blankSeason = season(1, listOf(stilllessEpisode, stillEpisode)).copy(cover = " ")
        stubHappyPath(series.copy(cover = null, seasons = listOf(blankSeason)))

        val vm = viewModel()

        assertEquals("https://img/still-32.jpg", vm.uiState.value.seasons[0].posterUrl)
    }

    @Test
    fun `ohne Cover und Episoden-Stills faellt Poster auf Serien-Backdrop zurueck`() = runTest {
        val blankSeason = season(1, listOf(episode(31, 1))).copy(cover = null)
        stubHappyPath(
            series.copy(
                cover = "",
                backdropPath = listOf("", "https://img/backdrop-2.jpg"),
                seasons = listOf(blankSeason)
            )
        )

        val vm = viewModel()

        assertEquals("https://img/backdrop-2.jpg", vm.uiState.value.seasons[0].posterUrl)
    }

    @Test
    fun `watched count und Ribbon werden aus WatchProgress berechnet`() = runTest {
        stubHappyPath()
        coEvery { watchProgressService.getWatchedEpisodeIds(seriesId) } returns
            setOf(11, 12, 21)

        val vm = viewModel()
        val state = vm.uiState.value

        assertEquals(2, state.seasons[0].watchedCount)
        assertTrue(state.seasons[0].isFullyWatched)
        assertEquals(1, state.seasons[1].watchedCount)
        assertFalse(state.seasons[1].isFullyWatched)
    }

    @Test
    fun `null episodes liefert leere Staffelliste mit Hinweis`() = runTest {
        stubHappyPath(series.copy(seasons = null))

        val vm = viewModel()
        val state = vm.uiState.value

        assertFalse(state.isLoading)
        assertTrue(state.seasons.isEmpty())
        assertNotNull(state.seasonsHint)
    }

    @Test
    fun `Genre und Rating kommen exklusiv aus IMDb`() = runTest {
        stubHappyPath()

        val vm = viewModel()
        val detail = vm.uiState.value.detail!!

        assertEquals(listOf("Drama", "Mystery"), detail.genres)
        assertEquals(8.1, detail.rating!!, 0.0001)
        assertFalse(detail.genres!!.contains("Xtream-Genre"))
        assertEquals(listOf("Alice", "Bob"), detail.cast)
    }

    @Test
    fun `ohne IMDb Match bleiben Genre und Rating null`() = runTest {
        stubHappyPath()
        every { imdbEnrichmentService.lookup(any()) } returns null

        val vm = viewModel()

        assertNull(vm.uiState.value.detail!!.genres)
        assertNull(vm.uiState.value.detail!!.rating)
    }

    @Test
    fun `load erkennt bestehenden Serien-Watchlist-Eintrag`() = runTest {
        stubHappyPath()
        every { watchlistRepository.getAllWatchlistEntries() } returns listOf(
            WatchlistEntry.create(
                id = series.toWatchlistEntryId(),
                type = WatchlistEntryType.SERIES,
                streamId = seriesId,
                name = series.name
            )
        )

        val vm = viewModel()

        assertTrue(vm.uiState.value.isInWatchlist)
    }

    @Test
    fun `toggleWatchlist fuegt Serien-Watchlist-Eintrag hinzu`() = runTest {
        stubHappyPath()
        val added = slot<WatchlistEntry>()
        coEvery { watchlistRepository.addToWatchlist(capture(added)) } returns true

        val vm = viewModel()
        vm.toggleWatchlist()

        assertTrue(vm.uiState.value.isInWatchlist)
        assertEquals(WatchlistEntryType.SERIES, added.captured.type)
        assertEquals(seriesId, added.captured.streamId)
        assertEquals(series.toWatchlistEntryId(), added.captured.id)
        coVerify(exactly = 1) { watchlistRepository.addToWatchlist(any()) }
    }

    @Test
    fun `load befuellt Empfehlungen per Genre-Match und schliesst sich selbst aus`() = runTest {
        stubHappyPath()
        coEvery { cacheService.loadCachedDataAsync() } returns
            cachedData(series = listOf(recommendedSeries, series))

        val vm = viewModel()

        assertEquals(listOf(2201), vm.uiState.value.recommendations.map { it.seriesId })
    }

    @Test
    fun `load ohne Cache liefert leere Empfehlungsliste`() = runTest {
        stubHappyPath()
        coEvery { cacheService.loadCachedDataAsync() } returns null

        val vm = viewModel()

        assertTrue(vm.uiState.value.recommendations.isEmpty())
    }

    @Test
    fun `ohne Fortschritt zeigt S1 E1 ansehen`() = runTest {
        stubHappyPath()

        val vm = viewModel()
        val state = vm.uiState.value

        assertEquals("S1 E1 ansehen", state.watchLabel)
        assertEquals(1, state.watchTarget?.seasonNumber)
        assertEquals(1, state.watchTarget?.episodeNumber)
        assertEquals(11, state.watchTarget?.episodeId)
        assertNull(state.watchProgress)
    }

    @Test
    fun `angebrochene Episode wird mit Sx Ey fortsetzen und Fortschritt angezeigt`() = runTest {
        stubHappyPath()
        coEvery { watchProgressService.getLastInteraction(seriesId, series) } returns
            LastInteraction(
                seasonNumber = 2,
                episodeNumber = 2,
                episodeId = 22,
                progress = 0.5f,
                timestamp = 1_000L
            )
        coEvery { watchProgressService.getSmartResumeTarget(seriesId, series) } returns
            SmartResumeTarget(seasonNumber = 2, episodeNumber = 2, episodeId = 22)
        coEvery {
            watchProgressService.getWatchProgress("series", null, seriesId, 22)
        } returns WatchProgress(
            position = 5_000L,
            duration = 10_000L,
            lastWatched = "2026-11-02T10:00:00Z",
            title = "Episode 2"
        )

        val vm = viewModel()
        val state = vm.uiState.value

        assertEquals("S2 E2 fortsetzen", state.watchLabel)
        assertEquals(22, state.watchTarget?.episodeId)
        assertEquals(50f, state.watchProgress!!, 0.001f)
    }

    @Test
    fun `kaputte LastInteraction widerspricht den Staffel-Ribbons nicht`() = runTest {
        stubHappyPath()
        coEvery { watchProgressService.getWatchedEpisodeIds(seriesId) } returns
            setOf(11, 12, 21)
        // Mayor-Fall: der Service verliert die Interaktion (z. B. kaputter Zeitstempel) und
        // liefert still null. Die Ribbons haben die Daten trotzdem.
        coEvery { watchProgressService.getLastInteraction(seriesId, series) } returns null
        coEvery { watchProgressService.getSmartResumeTarget(seriesId, series) } returns null

        val vm = viewModel()
        val state = vm.uiState.value

        assertEquals("S2 E2 fortsetzen", state.watchLabel)
        assertEquals(22, state.watchTarget?.episodeId)
    }

    @Test
    fun `angebrochene Episode mit kaputtem Zeitstempel bleibt Resume-Ziel`() = runTest {
        stubHappyPath()
        coEvery {
            watchProgressService.getWatchProgress("series", null, seriesId, 22)
        } returns WatchProgress(
            position = 5_000L,
            duration = 10_000L,
            lastWatched = "kein-datum",
            title = "Episode 2"
        )

        val vm = viewModel()
        val state = vm.uiState.value

        assertEquals("S2 E2 fortsetzen", state.watchLabel)
        assertEquals(22, state.watchTarget?.episodeId)
        assertEquals(50f, state.watchProgress!!, 0.001f)
    }

    @Test
    fun `nur gesehene Episoden fuehren zur naechsten ungesehenen`() = runTest {
        stubHappyPath()
        coEvery { watchProgressService.getWatchedEpisodeIds(seriesId) } returns
            setOf(11, 12, 21)
        coEvery { watchProgressService.getLastInteraction(seriesId, series) } returns
            LastInteraction(
                seasonNumber = 2,
                episodeNumber = 1,
                episodeId = 21,
                progress = 1f,
                timestamp = 1_000L
            )
        coEvery { watchProgressService.getSmartResumeTarget(seriesId, series) } returns
            SmartResumeTarget(seasonNumber = 2, episodeNumber = 2, episodeId = 22)

        val vm = viewModel()
        val state = vm.uiState.value

        assertEquals("S2 E2 fortsetzen", state.watchLabel)
        assertEquals(22, state.watchTarget?.episodeId)
        assertNull(state.watchProgress)
    }

    @Test
    fun `alles gesehen fuehrt zur letzten Episode`() = runTest {
        stubHappyPath()
        coEvery { watchProgressService.getWatchedEpisodeIds(seriesId) } returns
            setOf(11, 12, 21, 22, 23)
        coEvery { watchProgressService.getLastInteraction(seriesId, series) } returns
            LastInteraction(
                seasonNumber = 2,
                episodeNumber = 3,
                episodeId = 23,
                progress = 1f,
                timestamp = 1_000L
            )

        val vm = viewModel()
        val state = vm.uiState.value

        assertEquals(23, state.watchTarget?.episodeId)
        assertEquals("S2 E3 fortsetzen", state.watchLabel)
    }

    @Test
    fun `load Fehler setzt Error-State`() = runTest {
        every { credentialsRepository.getCredentials() } returns
            Credentials(serverUrl = "http://srv", username = "chris", password = "pw")
        coEvery { repository.getSeriesInfo("chris", "pw", seriesId) } returns
            Result.failure(Exception("kaputt"))
        every { watchlistRepository.getAllWatchlistEntries() } returns emptyList()

        val vm = viewModel()

        assertFalse(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.detail)
        assertNotNull(vm.uiState.value.error)
    }

    @Test
    fun `load ohne Credentials setzt Error-State`() = runTest {
        every { credentialsRepository.getCredentials() } returns null

        val vm = viewModel()

        assertFalse(vm.uiState.value.isLoading)
        assertNotNull(vm.uiState.value.error)
    }

    @Test
    fun `load mit tmdbId laedt Cast-Fotos von TMDB`() = runTest {
        stubHappyPath(series.copy(tmdbId = "99"))
        coEvery { tmdbCastClient.tvCredits("99") } returns listOf(
            TmdbCastMember(id = 1, name = "Alice", profilePath = "/alice.jpg"),
            TmdbCastMember(id = 2, name = "Bob", profilePath = null)
        )

        val vm = viewModel()

        assertEquals(
            mapOf("Alice" to "https://image.tmdb.org/t/p/w185/alice.jpg"),
            vm.uiState.value.castPhotos
        )
        coVerify(exactly = 1) { tmdbCastClient.tvCredits("99") }
    }

    @Test
    fun `load ohne tmdbId ruft TMDB Cast-Client nicht auf`() = runTest {
        stubHappyPath(series.copy(tmdbId = null))

        val vm = viewModel()

        assertTrue(vm.uiState.value.castPhotos.isEmpty())
        coVerify(exactly = 0) { tmdbCastClient.tvCredits(any()) }
    }

    @Test
    fun `nicht matchende TMDB Namen erzeugen keine Foto-Eintraege`() = runTest {
        stubHappyPath(series.copy(tmdbId = "99"))
        coEvery { tmdbCastClient.tvCredits("99") } returns listOf(
            TmdbCastMember(id = 3, name = "Charlie", profilePath = "/charlie.jpg")
        )

        val vm = viewModel()

        assertTrue(vm.uiState.value.castPhotos.isEmpty())
    }

    @Test
    fun `Cast-Name mit Umlaut und doppeltem Leerzeichen matcht normalisierten TMDB Namen im Fallback-Pfad`() = runTest {
        // Xtream liefert 2 Namen, TMDB nur 1 Treffer - TMDB unterliegt Xtream an Groesse, der
        // Namens-Matching-Fallback bleibt also aktiv und muss die Umlaut-Normalisierung testen.
        stubHappyPath(series.copy(tmdbId = "99", cast = "Penélope  Cruz, Other"))
        coEvery { tmdbCastClient.tvCredits("99") } returns listOf(
            TmdbCastMember(id = 1, name = "penelope cruz", profilePath = "/penelope.jpg")
        )

        val vm = viewModel()

        assertEquals(
            mapOf("Penélope  Cruz" to "https://image.tmdb.org/t/p/w185/penelope.jpg"),
            vm.uiState.value.castPhotos
        )
        assertEquals(listOf("Penélope  Cruz", "Other"), vm.uiState.value.detail?.cast)
    }

    @Test
    fun `TMDB-Cast grösser als Xtream-Cast wird zur Anzeige-Quelle`() = runTest {
        // Supernatural-Fall: Xtream liefert nur 3 Namen (letzte Staffel), TMDB (aggregate_credits)
        // liefert die gesamte Besetzung - TMDB gewinnt, Namen/Fotos kommen 1:1 aus derselben Liste.
        // "Jared Padalecki" taucht in beiden Listen auf und erfuellt damit den Plausibilitaets-Check (R1).
        stubHappyPath(series.copy(tmdbId = "99", cast = "Jared Padalecki, Bob, Charlie"))
        coEvery { tmdbCastClient.tvCredits("99") } returns listOf(
            TmdbCastMember(id = 1, name = "Jared Padalecki", profilePath = "/jared.jpg"),
            TmdbCastMember(id = 2, name = "Jensen Ackles", profilePath = "/jensen.jpg"),
            TmdbCastMember(id = 3, name = "Misha Collins", profilePath = null),
            TmdbCastMember(id = 4, name = "Mark Sheppard", profilePath = "/mark.jpg"),
            TmdbCastMember(id = 5, name = "Samantha Smith", profilePath = "/samantha.jpg")
        )

        val vm = viewModel()
        val state = vm.uiState.value

        assertEquals(
            listOf("Jared Padalecki", "Jensen Ackles", "Misha Collins", "Mark Sheppard", "Samantha Smith"),
            state.detail?.cast
        )
        assertEquals(
            mapOf(
                "Jared Padalecki" to "https://image.tmdb.org/t/p/w185/jared.jpg",
                "Jensen Ackles" to "https://image.tmdb.org/t/p/w185/jensen.jpg",
                "Mark Sheppard" to "https://image.tmdb.org/t/p/w185/mark.jpg",
                "Samantha Smith" to "https://image.tmdb.org/t/p/w185/samantha.jpg"
            ),
            state.castPhotos
        )
    }

    @Test
    fun `TMDB-Cast kleiner als Xtream-Cast behaelt Xtream-Namen mit Match-Fotos`() = runTest {
        stubHappyPath(series.copy(tmdbId = "99", cast = "Alice, Bob, Charlie"))
        coEvery { tmdbCastClient.tvCredits("99") } returns listOf(
            TmdbCastMember(id = 1, name = "Alice", profilePath = "/alice.jpg"),
            TmdbCastMember(id = 2, name = "Bob", profilePath = "/bob.jpg")
        )

        val vm = viewModel()
        val state = vm.uiState.value

        assertEquals(listOf("Alice", "Bob", "Charlie"), state.detail?.cast)
        assertEquals(
            mapOf(
                "Alice" to "https://image.tmdb.org/t/p/w185/alice.jpg",
                "Bob" to "https://image.tmdb.org/t/p/w185/bob.jpg"
            ),
            state.castPhotos
        )
    }

    @Test
    fun `TMDB- und Xtream-Cast gleicher Groesse mit passendem Namen lassen TMDB gewinnen`() = runTest {
        stubHappyPath(series.copy(tmdbId = "99", cast = "Alice, Bob, Charlie"))
        coEvery { tmdbCastClient.tvCredits("99") } returns listOf(
            TmdbCastMember(id = 1, name = "Alice", profilePath = "/alice.jpg"),
            TmdbCastMember(id = 2, name = "Dana", profilePath = "/dana.jpg"),
            TmdbCastMember(id = 3, name = "Eve", profilePath = null)
        )

        val vm = viewModel()

        assertEquals(listOf("Alice", "Dana", "Eve"), vm.uiState.value.detail?.cast)
    }

    @Test
    fun `TMDB-Cast ohne jeden passenden Namen bleibt trotz gleicher oder groesserer Groesse Fallback`() = runTest {
        // Plausibilitaets-Check (R1): eine falsche tmdbId darf die korrekten Anubis-Namen nicht
        // durch eine komplett fremde Besetzung ersetzen, auch wenn diese zahlenmaessig passt.
        stubHappyPath(series.copy(tmdbId = "99", cast = "Alice, Bob, Charlie"))
        coEvery { tmdbCastClient.tvCredits("99") } returns listOf(
            TmdbCastMember(id = 1, name = "Dana", profilePath = "/dana.jpg"),
            TmdbCastMember(id = 2, name = "Eve", profilePath = "/eve.jpg"),
            TmdbCastMember(id = 3, name = "Frank", profilePath = null)
        )

        val vm = viewModel()

        assertEquals(listOf("Alice", "Bob", "Charlie"), vm.uiState.value.detail?.cast)
        assertTrue(vm.uiState.value.castPhotos.isEmpty())
    }

    @Test
    fun `load mit leerer tmdbId ruft TMDB Cast-Client nicht auf`() = runTest {
        stubHappyPath(series.copy(tmdbId = ""))

        val vm = viewModel()

        assertTrue(vm.uiState.value.castPhotos.isEmpty())
        coVerify(exactly = 0) { tmdbCastClient.tvCredits(any()) }
    }
}
