package com.flintr.tv.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.flintr.tv.data.local.CachedData
import com.flintr.tv.data.local.CategoryVisibilityService
import com.flintr.tv.data.local.Credentials
import com.flintr.tv.data.local.CredentialsRepository
import com.flintr.tv.data.local.DataCacheService
import com.flintr.tv.data.model.Category
import com.flintr.tv.data.model.DestinationType
import com.flintr.tv.data.model.NavigationStateBeforePlayer
import com.flintr.tv.data.model.SearchScope
import com.flintr.tv.data.model.ServerInfo
import com.flintr.tv.data.model.testLiveStream
import com.flintr.tv.data.model.testSeries
import com.flintr.tv.data.model.testVodStream
import com.flintr.tv.data.repository.UserRepository
import com.flintr.tv.data.repository.XtreamRepository
import com.flintr.tv.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.UnknownHostException

@RunWith(RobolectricTestRunner::class)
class MainViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: XtreamRepository
    private lateinit var credentialsRepository: CredentialsRepository
    private lateinit var userRepository: UserRepository
    private lateinit var cacheService: DataCacheService
    private lateinit var categoryVisibilityService: CategoryVisibilityService
    private lateinit var savedStateHandle: SavedStateHandle

    private val credentials = Credentials(serverUrl = "http://srv", username = "chris", password = "pw")
    private val liveCategory = Category(categoryId = "1", categoryName = "Live DE")
    private val vodCategory = Category(categoryId = "2", categoryName = "Filme DE")
    private val seriesCategory = Category(categoryId = "3", categoryName = "Serien DE")

    private fun cachedData() = CachedData(
        channels = listOf(testLiveStream(name = "ARD HD", streamId = 100, categoryId = "1")),
        movies = listOf(testVodStream(name = "Der Film", streamId = 1, categoryId = "2")),
        series = listOf(testSeries(name = "Die Serie", seriesId = 10, categoryId = "3")),
        liveCategories = listOf(liveCategory),
        vodCategories = listOf(vodCategory),
        seriesCategories = listOf(seriesCategory)
    )

    @Before
    fun setUp() {
        repository = mockk(relaxed = true)
        credentialsRepository = mockk()
        userRepository = mockk(relaxed = true)
        cacheService = mockk(relaxed = true)
        categoryVisibilityService = mockk(relaxed = true)
        savedStateHandle = SavedStateHandle()

        every { credentialsRepository.getCredentials() } returns credentials
        coEvery { userRepository.saveUsername("chris") } coAnswers { Result.success(Unit) }
        every { categoryVisibilityService.getHiddenCategories(any()) } returns emptySet()

        // Standard: Cache verfuegbar, Cooldown aktiv -> keine Server-Info-Anfrage im init
        every { cacheService.shouldUseCacheForBrowsing() } returns true
        every { cacheService.isFallbackCooldownActive() } returns true
        coEvery { cacheService.loadCachedDataAsync() } returns cachedData()
    }

    private fun viewModel() = MainViewModel(
        savedStateHandle = savedStateHandle,
        repository = repository,
        credentialsRepository = credentialsRepository,
        userRepository = userRepository,
        cacheService = cacheService,
        categoryVisibilityService = categoryVisibilityService
    )

    @Test
    fun `init laedt Kategorien aus dem Cache`() = runTest {
        val vm = viewModel()

        assertEquals(listOf("Live DE"), vm.liveCategories.value.map { it.categoryName })
        assertEquals(listOf("Filme DE"), vm.vodCategories.value.map { it.categoryName })
        assertEquals(listOf("Serien DE"), vm.seriesCategories.value.map { it.categoryName })
        assertFalse(vm.uiState.value.isLoading)
        coVerify(exactly = 0) { repository.getLiveCategories(any(), any()) }
    }

    @Test
    fun `init ohne Credentials laedt nichts`() = runTest {
        every { credentialsRepository.getCredentials() } returns null

        val vm = viewModel()

        assertTrue(vm.liveCategories.value.isEmpty())
        coVerify(exactly = 0) { userRepository.saveUsername(any()) }
    }

    @Test
    fun `Benutzername wird nur einmal pro Session gespeichert`() = runTest {
        val vm = viewModel()
        vm.refreshData()

        coVerify(exactly = 1) { userRepository.saveUsername("chris") }
    }

    @Test
    fun `ohne Cache werden Kategorien vom Repository geladen`() = runTest {
        every { cacheService.shouldUseCacheForBrowsing() } returns false
        every { cacheService.isFallbackCooldownActive() } returns false
        coEvery { cacheService.loadCachedDataAsync() } returns null
        coEvery { repository.getServerInfo("chris", "pw") } coAnswers { Result.success(mockk<ServerInfo>()) }
        coEvery { repository.getLiveCategories("chris", "pw") } coAnswers { Result.success(listOf(liveCategory)) }
        coEvery { repository.getVodCategories("chris", "pw") } coAnswers { Result.success(listOf(vodCategory)) }
        coEvery { repository.getSeriesCategories("chris", "pw") } coAnswers { Result.success(listOf(seriesCategory)) }

        val vm = viewModel()

        assertEquals(listOf("1"), vm.liveCategories.value.map { it.categoryId })
        assertEquals(listOf("2"), vm.vodCategories.value.map { it.categoryId })
        assertEquals(listOf("3"), vm.seriesCategories.value.map { it.categoryId })
    }

    @Test
    fun `komplett fehlgeschlagene Kategorieladung setzt Fehler`() = runTest {
        every { cacheService.shouldUseCacheForBrowsing() } returns false
        every { cacheService.isFallbackCooldownActive() } returns false
        coEvery { cacheService.loadCachedDataAsync() } returns null
        coEvery { repository.getServerInfo(any(), any()) } coAnswers { Result.failure(Exception("srv kaputt")) }
        coEvery { repository.getLiveCategories(any(), any()) } coAnswers { Result.failure(Exception("api kaputt")) }
        coEvery { repository.getVodCategories(any(), any()) } coAnswers { Result.failure(Exception("api kaputt")) }
        coEvery { repository.getSeriesCategories(any(), any()) } coAnswers { Result.failure(Exception("api kaputt")) }

        val vm = viewModel()

        assertEquals("api kaputt", vm.uiState.value.error)
    }

    @Test
    fun `Verbindungsfehler bei ServerInfo wird mit Cache unterdrueckt`() = runTest {
        every { cacheService.shouldUseCacheForBrowsing() } returns true
        every { cacheService.isFallbackCooldownActive() } returns false
        coEvery { repository.getServerInfo(any(), any()) } coAnswers { Result.failure(UnknownHostException("offline")) }

        val vm = viewModel()

        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `loadLiveStreams laedt Sender fuer Kategorie`() = runTest {
        coEvery { repository.getLiveStreams("chris", "pw", "1") } coAnswers {
            Result.success(listOf(testLiveStream(name = "ARD HD", streamId = 100)))
        }
        val vm = viewModel()

        vm.loadLiveStreams("1")

        assertEquals(listOf("ARD HD"), vm.liveStreams.value.map { it.name })
    }

    @Test
    fun `loadVodStreams Fehler setzt uiState-Fehler`() = runTest {
        coEvery { repository.getVodStreams("chris", "pw", "2") } coAnswers { Result.failure(Exception("404")) }
        val vm = viewModel()

        vm.loadVodStreams("2")

        assertEquals("404", vm.uiState.value.error)
        assertTrue(vm.vodStreams.value.isEmpty())
    }

    @Test
    fun `loadLiveStreams setzt isLoading sofort auf true und nach Erfolg wieder auf false`() = runTest {
        val pendingResult = CompletableDeferred<Result<List<com.flintr.tv.data.model.LiveStream>>>()
        coEvery { repository.getLiveStreams("chris", "pw", "1") } coAnswers { pendingResult.await() }
        val vm = viewModel()

        vm.loadLiveStreams("1")

        assertTrue(vm.uiState.value.isLoading)

        pendingResult.complete(Result.success(listOf(testLiveStream(name = "ARD HD", streamId = 100))))
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
        assertEquals(listOf("ARD HD"), vm.liveStreams.value.map { it.name })
    }

    @Test
    fun `loadLiveStreams setzt isLoading nach Fehler wieder auf false`() = runTest {
        coEvery { repository.getLiveStreams("chris", "pw", "1") } coAnswers { Result.failure(Exception("500")) }
        val vm = viewModel()

        vm.loadLiveStreams("1")

        assertFalse(vm.uiState.value.isLoading)
        assertEquals("500", vm.uiState.value.error)
    }

    @Test
    fun `loadSeriesInfo setzt ausgewaehlte Serie`() = runTest {
        val serie = testSeries(name = "Die Serie", seriesId = 10)
        coEvery { repository.getSeriesInfo("chris", "pw", 10) } coAnswers { Result.success(serie) }
        val vm = viewModel()

        vm.loadSeriesInfo(10)

        assertEquals("Die Serie", vm.selectedSeries.value?.name)
    }

    @Test
    fun `clear-Methoden setzen Zustaende zurueck`() = runTest {
        coEvery { repository.getSeries("chris", "pw", "3") } coAnswers {
            Result.success(listOf(testSeries(seriesId = 10)))
        }
        val vm = viewModel()
        vm.loadSeries("3")

        vm.clearSeries()
        vm.clearLiveStreams()
        vm.clearVodStreams()
        vm.clearError()

        assertTrue(vm.series.value.isEmpty())
        assertNull(vm.selectedSeries.value)
        assertTrue(vm.liveStreams.value.isEmpty())
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `showSeriesFromFavorites liefert Kategorie-ID bei Treffer`() = runTest {
        coEvery { repository.getSeriesInfo(any(), any(), any()) } coAnswers { Result.success(testSeries(seriesId = 10)) }
        coEvery { repository.getSeries(any(), any(), any()) } coAnswers { Result.success(emptyList()) }
        val vm = viewModel()

        val categoryId = vm.showSeriesFromFavorites(testSeries(seriesId = 10, categoryId = "3"))

        assertEquals("3", categoryId)
    }

    @Test
    fun `showSeriesFromFavorites ohne Kategorie liefert null`() = runTest {
        coEvery { repository.getSeriesInfo(any(), any(), any()) } coAnswers { Result.success(testSeries(seriesId = 10)) }
        val vm = viewModel()

        assertNull(vm.showSeriesFromFavorites(testSeries(seriesId = 10, categoryId = "99")))
    }

    @Test
    fun `searchContent zeigt Hinweis bei zu kurzer Eingabe`() = runTest {
        val vm = viewModel()

        vm.searchContent("a")

        assertTrue(vm.searchState.value.showMinLengthHint)
        assertFalse(vm.searchState.value.isSearching)
        assertEquals("a", vm.searchState.value.query)
    }

    @Test
    fun `searchContentImmediate findet Inhalte im Cache`() = runTest {
        val vm = viewModel()

        vm.searchContent("Film")
        vm.searchContentImmediate()

        val results = vm.searchState.value.results
        assertEquals(listOf("Der Film"), results.vod.map { it.name })
        assertTrue(results.liveTV.isEmpty())
        assertTrue(vm.searchState.value.hasSearched)
        assertFalse(vm.searchState.value.isSearching)
    }

    @Test
    fun `searchContentImmediate filtert versteckte Kategorien`() = runTest {
        every { categoryVisibilityService.getHiddenCategories(com.flintr.tv.data.model.CategoryType.VOD) } returns setOf("2")
        val vm = viewModel()

        vm.searchContent("Film")
        vm.searchContentImmediate()

        assertTrue(vm.searchState.value.results.vod.isEmpty())
    }

    @Test
    fun `searchContentImmediate mit kurzer Eingabe setzt Ergebnisse zurueck`() = runTest {
        val vm = viewModel()

        vm.searchContent("a")
        vm.searchContentImmediate()

        assertFalse(vm.searchState.value.hasSearched)
        assertEquals(0, vm.searchState.value.results.totalCount)
    }

    @Test
    fun `toggleSearchScope wiederholt Suche mit neuem Umfang`() = runTest {
        val vm = viewModel()
        vm.searchContent("Serie")
        vm.searchContentImmediate()
        assertEquals(1, vm.searchState.value.results.series.size)

        vm.toggleSearchScope(SearchScope.SERIES)

        assertTrue(vm.searchState.value.results.series.isEmpty())
    }

    @Test
    fun `clearSearch setzt Suchzustand zurueck`() = runTest {
        val vm = viewModel()
        vm.searchContent("Film")
        vm.searchContentImmediate()

        vm.clearSearch()

        assertEquals("", vm.searchState.value.query)
        assertFalse(vm.searchState.value.hasSearched)
    }

    @Test
    fun `Navigationszustand wird gespeichert konsumiert und geleert`() = runTest {
        val vm = viewModel()
        val state = NavigationStateBeforePlayer(
            destinationType = DestinationType.VOD,
            categoryId = "2",
            contentId = "vod_1",
            scrollIndex = 4,
            scrollOffset = 80
        )

        vm.saveNavigationStateBeforePlayer(state)
        vm.consumeNavigationState()

        assertEquals(state, vm.pendingNavigationRestore.value)

        vm.clearPendingNavigationRestore()
        assertNull(vm.pendingNavigationRestore.value)

        // Erneutes Konsumieren ohne gespeicherten Zustand aendert nichts
        vm.consumeNavigationState()
        assertNull(vm.pendingNavigationRestore.value)
    }

    @Test
    fun `refreshData cancels previous initial load`() = runTest {
        every { cacheService.shouldUseCacheForBrowsing() } returns false
        every { cacheService.isFallbackCooldownActive() } returns false
        coEvery { cacheService.loadCachedDataAsync() } returns null
        coEvery { repository.getServerInfo(any(), any()) } coAnswers { Result.success(mockk<ServerInfo>(relaxed = true)) }

        val staleCategory = Category(categoryId = "old", categoryName = "Alt")
        val freshCategory = Category(categoryId = "new", categoryName = "Neu")

        // Blockiert den ersten loadInitialData-Aufruf mitten im Laden, bis der Test ihn gezielt freigibt -
        // simuliert eine langsame API-Antwort, die erst nach dem zweiten Aufruf zurueckkommt.
        val firstCallGate = kotlinx.coroutines.CompletableDeferred<Unit>()
        var liveCategoriesCallCount = 0
        coEvery { repository.getLiveCategories("chris", "pw") } coAnswers {
            liveCategoriesCallCount++
            if (liveCategoriesCallCount == 1) {
                firstCallGate.await()
                Result.success(listOf(staleCategory))
            } else {
                Result.success(listOf(freshCategory))
            }
        }
        coEvery { repository.getVodCategories("chris", "pw") } coAnswers { Result.success(listOf(vodCategory)) }
        coEvery { repository.getSeriesCategories("chris", "pw") } coAnswers { Result.success(listOf(seriesCategory)) }

        // init{} startet den ersten Load, der in getLiveCategories haengen bleibt
        val vm = viewModel()

        // refreshData() stoesst einen zweiten, parallelen Load derselben Funktion an
        vm.refreshData()

        // Der zweite (neuere) Load laeuft ohne Verzoegerung durch, danach wird der erste freigegeben
        firstCallGate.complete(Unit)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
        assertEquals(listOf("new"), vm.liveCategories.value.map { it.categoryId })
        coVerify(exactly = 2) { repository.getLiveCategories("chris", "pw") }
    }

    @Test
    fun `schneller Kategoriewechsel cancelt den alten Load ohne Fehler zu setzen`() = runTest {
        // Simuliert schnelles A-zu-B-Wechseln: der Load fuer Kategorie "1" haengt noch,
        // waehrend bereits Kategorie "2" ausgewaehlt wird und ihn per dataLoadingJob.cancel() ablöst.
        val staleCallGate = CompletableDeferred<Unit>()
        coEvery { repository.getVodStreams("chris", "pw", "1") } coAnswers {
            staleCallGate.await()
            Result.success(listOf(testVodStream(name = "Alt", streamId = 1, categoryId = "1")))
        }
        coEvery { repository.getVodStreams("chris", "pw", "2") } coAnswers {
            Result.success(listOf(testVodStream(name = "Neu", streamId = 2, categoryId = "2")))
        }
        val vm = viewModel()

        vm.loadVodStreams("1")
        vm.loadVodStreams("2")
        staleCallGate.complete(Unit)
        advanceUntilIdle()

        assertNull(vm.uiState.value.error)
        assertFalse(vm.uiState.value.isLoading)
        assertEquals(listOf("Neu"), vm.vodStreams.value.map { it.name })
    }

    @Test
    fun `veralteter Load ueberschreibt frische Daten nicht, wenn er trotz Cancellation noch durchlaeuft`() = runTest {
        // dataLoadingJob.cancel() stoppt den alten Load normalerweise, sobald er den naechsten
        // Suspension-Point erreicht. Bei echten Multithread-Workloads (z.B. eine bereits laufende
        // IO-Anfrage) kann der alte Callback aber trotzdem noch feuern, nachdem ein neuerer Load
        // begonnen hat - dafuer gibt es zusaetzlich den Generation-Vergleich in loadData().
        // NonCancellable simuliert genau diesen Fall: der alte Call ignoriert seine eigene
        // Cancellation und liefert am Ende trotzdem ein Ergebnis.
        val staleResultGate = CompletableDeferred<Unit>()
        coEvery { repository.getVodStreams("chris", "pw", "1") } coAnswers {
            withContext(NonCancellable) { staleResultGate.await() }
            Result.success(listOf(testVodStream(name = "Alt", streamId = 1, categoryId = "1")))
        }
        coEvery { repository.getVodStreams("chris", "pw", "2") } coAnswers {
            Result.success(listOf(testVodStream(name = "Neu", streamId = 2, categoryId = "2")))
        }
        val vm = viewModel()

        vm.loadVodStreams("1")
        vm.loadVodStreams("2")
        advanceUntilIdle()
        staleResultGate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("Neu"), vm.vodStreams.value.map { it.name })
        assertFalse(vm.uiState.value.isLoading)
    }
}
