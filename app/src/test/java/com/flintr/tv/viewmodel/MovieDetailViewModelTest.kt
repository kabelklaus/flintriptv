package com.flintr.tv.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.flintr.tv.data.local.CachedData
import com.flintr.tv.data.local.Credentials
import com.flintr.tv.data.local.CredentialsRepository
import com.flintr.tv.data.local.DataCacheService
import com.flintr.tv.data.local.WatchlistRepository
import com.flintr.tv.data.local.WatchProgressService
import com.flintr.tv.data.local.imdb.ImdbEnrichment
import com.flintr.tv.data.local.imdb.ImdbEnrichmentService
import com.flintr.tv.data.model.WatchlistEntry
import com.flintr.tv.data.model.WatchlistEntryType
import com.flintr.tv.data.model.VodInfo
import com.flintr.tv.data.model.VodStream
import com.flintr.tv.data.model.WatchProgress
import com.flintr.tv.data.repository.XtreamRepository
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
    private val imdbEnrichmentService = mockk<ImdbEnrichmentService>(relaxed = true)
    private val favoritesRepository = mockk<WatchlistRepository>(relaxed = true)
    private val watchProgressService = mockk<WatchProgressService>(relaxed = true)
    private val cacheService = mockk<DataCacheService>(relaxed = true)

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
        imdbEnrichmentService = imdbEnrichmentService,
        favoritesRepository = favoritesRepository,
        watchProgressService = watchProgressService,
        cacheService = cacheService,
        ioDispatcher = mainDispatcherRule.dispatcher
    )

    private fun stubHappyPath() {
        every { credentialsRepository.getCredentials() } returns
            Credentials(serverUrl = "http://srv", username = "chris", password = "pw")
        coEvery { cacheService.loadCachedDataAsync() } returns cachedData()
        coEvery { repository.loadVodInfo("chris", "pw", vodId) } returns Result.success(vodInfo)
        every { imdbEnrichmentService.lookup(any()) } returns
            ImdbEnrichment(rating = 8.4, numVotes = 1234, genres = listOf("Action", "Drama"))
        every { favoritesRepository.getAllWatchlistEntries() } returns emptyList()
        coEvery { watchProgressService.getWatchProgress("movie", vodId, null, null) } returns watchProgress
    }

    @Test
    fun `loadErfolg mappt Details mit IMDb Genres und Rating`() = runTest {
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
    fun `loadErfolg ohne IMDb Match laesst Genres und Rating null`() = runTest {
        stubHappyPath()
        every { imdbEnrichmentService.lookup(any()) } returns null

        val vm = viewModel()

        assertNull(vm.uiState.value.detail!!.genres)
        assertNull(vm.uiState.value.detail!!.rating)
    }

    @Test
    fun `load laedt Favoritenstatus und WatchProgress`() = runTest {
        stubHappyPath()
        every { favoritesRepository.getAllWatchlistEntries() } returns listOf(
            WatchlistEntry.create(
                id = catalogMovie.toFavoriteId(),
                type = WatchlistEntryType.MOVIE,
                streamId = vodId,
                name = catalogMovie.name
            )
        )

        val vm = viewModel()

        assertTrue(vm.uiState.value.isFavorite)
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
    fun `toggleWatchlist fuegt Favorit hinzu und aktualisiert State`() = runTest {
        stubHappyPath()
        val added = slot<WatchlistEntry>()
        coEvery { favoritesRepository.addToWatchlist(capture(added)) } returns true

        val vm = viewModel()
        vm.toggleWatchlist()

        assertTrue(vm.uiState.value.isFavorite)
        assertEquals(WatchlistEntryType.MOVIE, added.captured.type)
        assertEquals(vodId, added.captured.streamId)
        coVerify(exactly = 1) { favoritesRepository.addToWatchlist(any()) }
    }

    @Test
    fun `toggleWatchlist entfernt bestehenden Favoriten`() = runTest {
        stubHappyPath()
        val favorite = WatchlistEntry.create(
            id = catalogMovie.toFavoriteId(),
            type = WatchlistEntryType.MOVIE,
            streamId = vodId,
            name = catalogMovie.name
        )
        every { favoritesRepository.getAllWatchlistEntries() } returns listOf(favorite)
        coEvery { favoritesRepository.removeFromWatchlist(favorite.id) } returns true

        val vm = viewModel()
        vm.toggleWatchlist()

        assertFalse(vm.uiState.value.isFavorite)
        coVerify(exactly = 1) { favoritesRepository.removeFromWatchlist(favorite.id) }
    }

    @Test
    fun `retry laedt nach Fehler erneut`() = runTest {
        every { credentialsRepository.getCredentials() } returns
            Credentials(serverUrl = "http://srv", username = "chris", password = "pw")
        coEvery { cacheService.loadCachedDataAsync() } returns null
        coEvery { repository.loadVodInfo("chris", "pw", vodId) } returns
            Result.failure(Exception("kaputt"))
        every { imdbEnrichmentService.lookup(any()) } returns null
        every { favoritesRepository.getAllWatchlistEntries() } returns emptyList()
        coEvery { watchProgressService.getWatchProgress("movie", vodId, null, null) } returns null

        val vm = viewModel()
        assertNotNull(vm.uiState.value.error)

        coEvery { repository.loadVodInfo("chris", "pw", vodId) } returns Result.success(vodInfo)
        vm.retry()

        assertNull(vm.uiState.value.error)
        assertNotNull(vm.uiState.value.detail)
    }
}
