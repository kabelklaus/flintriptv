package com.flintr.tv.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.flintr.tv.data.local.CachedData
import com.flintr.tv.data.local.Credentials
import com.flintr.tv.data.local.CredentialsRepository
import com.flintr.tv.data.local.DataCacheService
import com.flintr.tv.data.local.WatchlistRepository
import com.flintr.tv.data.local.WatchProgressService
import com.flintr.tv.data.model.WatchlistEntry
import com.flintr.tv.data.model.WatchlistEntryType
import com.flintr.tv.data.model.VodInfo
import com.flintr.tv.data.model.VodStream
import com.flintr.tv.data.model.WatchProgress
import com.flintr.tv.data.repository.XtreamRepository
import com.flintr.tv.data.tmdb.TmdbCastClient
import com.flintr.tv.data.tmdb.TmdbCastMember
import com.flintr.tv.data.tmdb.TmdbGenre
import com.flintr.tv.data.tmdb.TmdbMeta
import com.flintr.tv.data.tmdb.TmdbMetaRepository
import com.flintr.tv.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
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
class MovieDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val vodId = 4711
    private val repository = mockk<XtreamRepository>()
    private val credentialsRepository = mockk<CredentialsRepository>()
    private val tmdbMetaRepository = mockk<TmdbMetaRepository>(relaxed = true)
    private val watchlistRepository = mockk<WatchlistRepository>(relaxed = true)
    private val watchProgressService = mockk<WatchProgressService>(relaxed = true)
    private val cacheService = mockk<DataCacheService>(relaxed = true)
    private val tmdbCastClient = mockk<TmdbCastClient>(relaxed = true)

    private val catalogMovie = VodStream(
        num = 1,
        name = "Katalogtitel",
        streamType = "movie",
        streamId = vodId,
        streamIcon = "https://img/poster.jpg",
        rating = "7.1",
        rating5Based = null,
        added = null,
        categoryId = "1",
        categoryName = "Filme",
        containerExtension = "mkv",
        customSid = null,
        directSource = null,
        releaseDate = "2020-01-01",
        duration = "02:00:00",
        plot = null,
        cast = null,
        director = null,
        genre = "Xtream-Action",
        backdropPath = null,
        youtubeTrailer = null,
        cover = "https://img/cover.jpg",
        tmdbId = null
    )

    private val vodInfo = VodInfo(
        name = "Detailtitel",
        plot = "Ein spannender Film.",
        cast = "Alice, Bob",
        director = "Regisseurin",
        genre = "Xtream-Genre",
        duration = "120 min",
        durationSecs = "7200",
        runtime = "2h 0min",
        releasedate = "2020-05-01",
        rating = "6.0",
        youtubeTrailer = "trailer123",
        backdropPath = listOf("https://img/backdrop.jpg"),
        movieImage = "https://img/movie.jpg",
        coverBig = "https://img/cover-big.jpg",
        tmdbId = "42"
    )

    private val watchProgress = WatchProgress(
        position = 1_800_000L,
        duration = 7_200_000L,
        lastWatched = "2026-10-30T20:00:00Z",
        title = "Detailtitel"
    )

    private fun cachedData(movies: List<VodStream> = listOf(catalogMovie)) = CachedData(
        channels = emptyList(),
        movies = movies,
        series = emptyList(),
        liveCategories = emptyList(),
        vodCategories = emptyList(),
        seriesCategories = emptyList()
    )

    private fun viewModel() = MovieDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf(MovieDetailViewModel.ARG_VOD_ID to vodId)),
        repository = repository,
        credentialsRepository = credentialsRepository,
        tmdbMetaRepository = tmdbMetaRepository,
        watchlistRepository = watchlistRepository,
        watchProgressService = watchProgressService,
        cacheService = cacheService,
        tmdbCastClient = tmdbCastClient,
        ioDispatcher = mainDispatcherRule.dispatcher
    )

    private fun stubHappyPath() {
        every { credentialsRepository.getCredentials() } returns
            Credentials(serverUrl = "http://srv", username = "chris", password = "pw")
        coEvery { cacheService.loadCachedDataAsync() } returns cachedData()
        coEvery { repository.loadVodInfo("chris", "pw", vodId) } returns Result.success(vodInfo)
        coEvery { tmdbMetaRepository.detailMeta("movie", any(), any(), any()) } returns
            TmdbMeta(
                tmdbId = 1,
                rating = 8.4,
                voteCount = 1234,
                genres = listOf(TmdbGenre(28, "Action"), TmdbGenre(18, "Drama"))
            )
        every { watchlistRepository.getAllWatchlistEntries() } returns emptyList()
        coEvery { watchProgressService.getWatchProgress("movie", vodId, null, null) } returns watchProgress
        coEvery { tmdbCastClient.movieCredits(any()) } returns emptyList()
        coEvery { tmdbMetaRepository.cachedResolvedIds(any(), any()) } returns emptyMap()
    }

    @Test
    fun `loadErfolg mappt Details mit TMDB Genres und Rating`() = runTest {
        stubHappyPath()

        val vm = viewModel()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertNotNull(state.detail)
        assertEquals("Katalogtitel", state.detail!!.title)
        assertEquals(listOf("Action", "Drama"), state.detail!!.genres)
        assertEquals(8.4, state.detail!!.rating!!, 0.0001)
        // Xtream-Genre darf nie als Anzeige-Fallback durchschlagen.
        assertFalse(state.detail!!.genres!!.contains("Xtream-Genre"))
    }

    @Test
    fun `loadErfolg ohne TMDB Match laesst Genres und Rating null`() = runTest {
        stubHappyPath()
        coEvery { tmdbMetaRepository.detailMeta("movie", any(), any(), any()) } returns null

        val vm = viewModel()

        assertNull(vm.uiState.value.detail!!.genres)
        assertNull(vm.uiState.value.detail!!.rating)
    }

    @Test
    fun `load laedt Watchlist-Status und WatchProgress`() = runTest {
        stubHappyPath()
        every { watchlistRepository.getAllWatchlistEntries() } returns listOf(
            WatchlistEntry.create(
                id = catalogMovie.toWatchlistEntryId(),
                type = WatchlistEntryType.MOVIE,
                streamId = vodId,
                name = catalogMovie.name
            )
        )

        val vm = viewModel()

        assertTrue(vm.uiState.value.isInWatchlist)
        assertEquals(watchProgress, vm.uiState.value.watchProgress)
        coVerify(exactly = 1) { watchProgressService.getWatchProgress("movie", vodId, null, null) }
    }

    @Test
    fun `load zeigt Katalog-Basisdaten sofort an bevor VodInfo fertig ist`() = runTest {
        every { credentialsRepository.getCredentials() } returns
            Credentials(serverUrl = "http://srv", username = "chris", password = "pw")
        coEvery { cacheService.loadCachedDataAsync() } returns cachedData()
        val pendingVodInfo = CompletableDeferred<Result<VodInfo>>()
        coEvery { repository.loadVodInfo("chris", "pw", vodId) } coAnswers { pendingVodInfo.await() }

        val vm = viewModel()

        val state = vm.uiState.value
        assertTrue(state.isLoading)
        assertNull(state.detail)
        assertEquals(catalogMovie, state.baseStream)
    }

    @Test
    fun `load Fehler setzt Error-State ohne Detail`() = runTest {
        stubHappyPath()
        coEvery { repository.loadVodInfo("chris", "pw", vodId) } returns
            Result.failure(Exception("kaputt"))

        val vm = viewModel()

        assertFalse(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.detail)
        assertNotNull(vm.uiState.value.error)
        assertTrue(vm.uiState.value.error!!.contains("Details"))
    }

    @Test
    fun `load ohne Credentials setzt Error-State`() = runTest {
        every { credentialsRepository.getCredentials() } returns null

        val vm = viewModel()

        assertFalse(vm.uiState.value.isLoading)
        assertNotNull(vm.uiState.value.error)
    }

    @Test
    fun `toggleWatchlist fuegt Watchlist-Eintrag hinzu und aktualisiert State`() = runTest {
        stubHappyPath()
        val added = slot<WatchlistEntry>()
        coEvery { watchlistRepository.addToWatchlist(capture(added)) } returns true

        val vm = viewModel()
        vm.toggleWatchlist()

        assertTrue(vm.uiState.value.isInWatchlist)
        assertEquals(WatchlistEntryType.MOVIE, added.captured.type)
        assertEquals(vodId, added.captured.streamId)
        coVerify(exactly = 1) { watchlistRepository.addToWatchlist(any()) }
    }

    @Test
    fun `toggleWatchlist entfernt bestehenden Watchlist-Eintrag`() = runTest {
        stubHappyPath()
        val entry = WatchlistEntry.create(
            id = catalogMovie.toWatchlistEntryId(),
            type = WatchlistEntryType.MOVIE,
            streamId = vodId,
            name = catalogMovie.name
        )
        every { watchlistRepository.getAllWatchlistEntries() } returns listOf(entry)
        coEvery { watchlistRepository.removeFromWatchlist(entry.id) } returns true

        val vm = viewModel()
        vm.toggleWatchlist()

        assertFalse(vm.uiState.value.isInWatchlist)
        coVerify(exactly = 1) { watchlistRepository.removeFromWatchlist(entry.id) }
    }

    @Test
    fun `retry laedt nach Fehler erneut`() = runTest {
        every { credentialsRepository.getCredentials() } returns
            Credentials(serverUrl = "http://srv", username = "chris", password = "pw")
        coEvery { cacheService.loadCachedDataAsync() } returns null
        coEvery { repository.loadVodInfo("chris", "pw", vodId) } returns
            Result.failure(Exception("kaputt"))
        every { watchlistRepository.getAllWatchlistEntries() } returns emptyList()
        coEvery { watchProgressService.getWatchProgress("movie", vodId, null, null) } returns null

        val vm = viewModel()
        assertNotNull(vm.uiState.value.error)

        coEvery { repository.loadVodInfo("chris", "pw", vodId) } returns Result.success(vodInfo)
        vm.retry()

        assertNull(vm.uiState.value.error)
        assertNotNull(vm.uiState.value.detail)
    }

    @Test
    fun `load mit tmdbId laedt Cast-Fotos von TMDB`() = runTest {
        stubHappyPath()
        coEvery { tmdbCastClient.movieCredits("42") } returns listOf(
            TmdbCastMember(id = 1, name = "Alice", profilePath = "/alice.jpg"),
            TmdbCastMember(id = 2, name = "Bob", profilePath = null)
        )

        val vm = viewModel()

        assertEquals(
            mapOf("Alice" to "https://image.tmdb.org/t/p/w185/alice.jpg"),
            vm.uiState.value.castPhotos
        )
        coVerify(exactly = 1) { tmdbCastClient.movieCredits("42") }
    }

    @Test
    fun `load ohne tmdbId ruft TMDB Cast-Client nicht auf`() = runTest {
        stubHappyPath()
        coEvery { repository.loadVodInfo("chris", "pw", vodId) } returns
            Result.success(vodInfo.copy(tmdbId = null))

        val vm = viewModel()

        assertTrue(vm.uiState.value.castPhotos.isEmpty())
        coVerify(exactly = 0) { tmdbCastClient.movieCredits(any()) }
    }

    @Test
    fun `nicht matchende TMDB Namen erzeugen keine Foto-Eintraege`() = runTest {
        stubHappyPath()
        coEvery { tmdbCastClient.movieCredits("42") } returns listOf(
            TmdbCastMember(id = 3, name = "Charlie", profilePath = "/charlie.jpg")
        )

        val vm = viewModel()

        assertTrue(vm.uiState.value.castPhotos.isEmpty())
    }

    @Test
    fun `Cast-Name mit Umlaut und doppeltem Leerzeichen matcht normalisierten TMDB Namen`() = runTest {
        stubHappyPath()
        coEvery { repository.loadVodInfo("chris", "pw", vodId) } returns
            Result.success(vodInfo.copy(cast = "Penélope  Cruz"))
        coEvery { tmdbCastClient.movieCredits("42") } returns listOf(
            TmdbCastMember(id = 1, name = "penelope cruz", profilePath = "/penelope.jpg")
        )

        val vm = viewModel()

        assertEquals(
            mapOf("Penélope  Cruz" to "https://image.tmdb.org/t/p/w185/penelope.jpg"),
            vm.uiState.value.castPhotos
        )
    }

    @Test
    fun `leerer Xtream Cast wird ab 2 TMDB Credits gefuellt`() = runTest {
        stubHappyPath()
        coEvery { repository.loadVodInfo("chris", "pw", vodId) } returns
            Result.success(vodInfo.copy(cast = null))
        coEvery { tmdbCastClient.movieCredits("42") } returns listOf(
            TmdbCastMember(id = 1, name = "Eve", profilePath = "/eve.jpg"),
            TmdbCastMember(id = 2, name = "Frank", profilePath = null)
        )

        val vm = viewModel()

        assertEquals(
            listOf("Eve", "Frank"),
            vm.uiState.value.detail!!.cast
        )
        assertEquals(
            mapOf("Eve" to "https://image.tmdb.org/t/p/w185/eve.jpg"),
            vm.uiState.value.castPhotos
        )
    }

    @Test
    fun `leerer Xtream Cast bleibt leer wenn TMDB weniger als 2 Mitglieder liefert`() = runTest {
        stubHappyPath()
        coEvery { repository.loadVodInfo("chris", "pw", vodId) } returns
            Result.success(vodInfo.copy(cast = null))
        coEvery { tmdbCastClient.movieCredits("42") } returns listOf(
            TmdbCastMember(id = 1, name = "Eve", profilePath = "/eve.jpg")
        )

        val vm = viewModel()

        assertTrue(vm.uiState.value.detail!!.cast.isEmpty())
        assertTrue(vm.uiState.value.castPhotos.isEmpty())
    }

    @Test
    fun `load mit leerer tmdbId ruft TMDB Cast-Client nicht auf`() = runTest {
        stubHappyPath()
        coEvery { repository.loadVodInfo("chris", "pw", vodId) } returns
            Result.success(vodInfo.copy(tmdbId = ""))

        val vm = viewModel()

        assertTrue(vm.uiState.value.castPhotos.isEmpty())
        coVerify(exactly = 0) { tmdbCastClient.movieCredits(any()) }
    }

    @Test
    fun `load befuellt Empfehlungen aus recommendationsFor und schliesst sich selbst aus`() = runTest {
        stubHappyPath()
        coEvery { tmdbMetaRepository.recommendationsFor("movie", "42") } returns listOf(777)
        val recommendation = catalogMovie.copy(streamId = 888, name = "Empfohlen", tmdbId = "777")
        coEvery { cacheService.loadCachedDataAsync() } returns
            cachedData(movies = listOf(catalogMovie, recommendation))

        val vm = viewModel()

        assertEquals(listOf(888), vm.uiState.value.recommendations.map { it.streamId })
    }

    @Test
    fun `load befuellt Empfehlungen fuer Katalog-Items ohne tmdbId aus dem Card-Resolve-Cache`() = runTest {
        stubHappyPath()
        coEvery { tmdbMetaRepository.recommendationsFor("movie", "42") } returns listOf(777, 778)
        val first = catalogMovie.copy(streamId = 888, name = "Empfohlen Eins", tmdbId = null)
        val second = catalogMovie.copy(streamId = 889, name = "Empfohlen Zwei", tmdbId = null)
        coEvery { cacheService.loadCachedDataAsync() } returns
            cachedData(movies = listOf(catalogMovie, first, second))
        coEvery { tmdbMetaRepository.cachedResolvedIds("movie", any()) } returns
            mapOf(888 to 777, 889 to 778)

        val vm = viewModel()

        assertEquals(listOf(888, 889), vm.uiState.value.recommendations.map { it.streamId })
    }

    @Test
    fun `load ohne Anchor-tmdbId liefert leere Empfehlungen`() = runTest {
        stubHappyPath()
        coEvery { repository.loadVodInfo("chris", "pw", vodId) } returns
            Result.success(vodInfo.copy(tmdbId = null))
        coEvery { tmdbMetaRepository.detailMeta("movie", any(), any(), any()) } returns null
        coEvery { cacheService.loadCachedDataAsync() } returns
            cachedData(movies = listOf(catalogMovie, catalogMovie.copy(streamId = 888, tmdbId = "777")))

        val vm = viewModel()

        assertTrue(vm.uiState.value.recommendations.isEmpty())
        coVerify(exactly = 0) { tmdbMetaRepository.recommendationsFor(any(), any()) }
    }

}
