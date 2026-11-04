package com.flintr.tv.viewmodel

import com.flintr.tv.data.local.CredentialsRepository
import com.flintr.tv.data.local.Credentials
import com.flintr.tv.data.local.CachedData
import com.flintr.tv.data.local.DataCacheService
import com.flintr.tv.data.model.Category
import com.flintr.tv.data.model.Series
import com.flintr.tv.data.model.testLiveStream
import com.flintr.tv.data.model.testSeries
import com.flintr.tv.data.model.testVodStream
import com.flintr.tv.data.repository.XtreamRepository
import com.flintr.tv.domain.usecase.DataRefreshProgress
import com.flintr.tv.domain.usecase.DataRefreshUseCase
import com.flintr.tv.domain.usecase.ItemCounts
import com.flintr.tv.domain.usecase.RefreshStep
import com.flintr.tv.testutil.MainDispatcherRule
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DataLoadingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var repository: XtreamRepository
    private lateinit var credentialsRepository: CredentialsRepository
    private lateinit var cacheService: DataCacheService
    private lateinit var dataRefreshUseCase: DataRefreshUseCase

    private val credentials = Credentials(serverUrl = "http://srv", username = "chris", password = "pw")
    private val liveCategory = Category(categoryId = "1", categoryName = "Live DE")
    private val vodCategory = Category(categoryId = "2", categoryName = "Filme DE")
    private val seriesCategory = Category(categoryId = "3", categoryName = "Serien DE")

    @Before
    fun setUp() {
        repository = mockk()
        credentialsRepository = mockk()
        cacheService = mockk(relaxed = true)
        dataRefreshUseCase = mockk()

        every { credentialsRepository.getCredentials() } returns credentials
        every { dataRefreshUseCase.refreshData(any(), any()) } returns emptyFlow()

        // No-cache path -> init loads via dataRefreshUseCase (empty flow, keine Steps veraendert)
        every { cacheService.isFallbackCooldownActive() } returns false
        every { cacheService.isCacheValid() } returns false

        coEvery { repository.getLiveCategories("chris", "pw") } coAnswers { Result.success(listOf(liveCategory)) }
        coEvery { repository.getVodCategories("chris", "pw") } coAnswers { Result.success(listOf(vodCategory)) }
        coEvery { repository.getSeriesCategories("chris", "pw") } coAnswers { Result.success(listOf(seriesCategory)) }
    }

    private fun viewModel() = DataLoadingViewModel(
        repository = repository,
        credentialsRepository = credentialsRepository,
        cacheService = cacheService,
        dataRefreshUseCase = dataRefreshUseCase
    )

    @Test
    fun `retryStep saves freshly loaded data instead of empty cache read`() = runTest {
        coEvery { repository.loadLiveStreamsFromApi("chris", "pw") } coAnswers {
            Result.success(listOf(testLiveStream(streamId = 1), testLiveStream(streamId = 2)))
        }
        coEvery { repository.loadVodStreamsFromApi("chris", "pw") } coAnswers {
            Result.success(listOf(testVodStream(streamId = 1), testVodStream(streamId = 2)))
        }
        coEvery { repository.loadSeriesFromApi("chris", "pw") } coAnswers {
            Result.success(listOf(testSeries(seriesId = 1), testSeries(seriesId = 2)))
        }
        coEvery { repository.getLiveStreams("chris", "pw") } coAnswers {
            Result.failure(Exception("ERROR_NO_CACHE_AVAILABLE"))
        }
        coEvery { repository.getVodStreams("chris", "pw") } coAnswers {
            Result.failure(Exception("ERROR_NO_CACHE_AVAILABLE"))
        }
        coEvery { repository.getSeries("chris", "pw") } coAnswers {
            Result.failure(Exception("ERROR_NO_CACHE_AVAILABLE"))
        }

        val seriesSlot = slot<List<Series>>()
        coEvery {
            cacheService.saveCachedData(
                channels = any(),
                movies = any(),
                series = capture(seriesSlot),
                liveCategories = any(),
                vodCategories = any(),
                seriesCategories = any(),
                onProgress = any()
            )
        } returns true

        val vm = viewModel()

        // Steps 0 und 1 sind bereits erfolgreich abgeschlossen
        vm.retryStep(0)
        advanceUntilIdle()
        vm.retryStep(1)
        advanceUntilIdle()

        // Step 2 (Serien) wird erneut geladen -> alle Steps erfolgreich -> Finalisierung
        vm.retryStep(2)
        advanceUntilIdle()

        assertTrue(seriesSlot.isCaptured)
        assertEquals(2, seriesSlot.captured.size)
        assertTrue(vm.uiState.value.loadingComplete)
        assertEquals(LoadingStatus.Success, vm.uiState.value.steps[SAVE_STEP_INDEX].status)
    }

    @Test
    fun `retryStep schliesst nicht ab wenn das Speichern scheitert`() = runTest {
        coEvery { repository.loadSeriesFromApi("chris", "pw") } coAnswers {
            Result.success(listOf(testSeries(seriesId = 1)))
        }
        coEvery { repository.getLiveStreams("chris", "pw") } coAnswers { Result.success(listOf(testLiveStream(streamId = 1))) }
        coEvery { repository.getVodStreams("chris", "pw") } coAnswers { Result.success(listOf(testVodStream(streamId = 1))) }
        coEvery { repository.loadLiveStreamsFromApi("chris", "pw") } coAnswers { Result.success(listOf(testLiveStream(streamId = 1))) }
        coEvery { repository.loadVodStreamsFromApi("chris", "pw") } coAnswers { Result.success(listOf(testVodStream(streamId = 1))) }
        coEvery { cacheService.saveCachedData(any(), any(), any(), any(), any(), any(), any()) } returns false

        val vm = viewModel()
        vm.retryStep(0)
        advanceUntilIdle()
        vm.retryStep(1)
        advanceUntilIdle()
        vm.retryStep(2)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(LoadingStatus.Error, state.steps[SAVE_STEP_INDEX].status)
        assertFalse(state.loadingComplete)
    }

    @Test
    fun `retryStep failure keeps step in error and does not finalize`() = runTest {
        coEvery { repository.loadSeriesFromApi("chris", "pw") } coAnswers {
            Result.failure(Exception("Netzwerkfehler"))
        }

        val vm = viewModel()

        vm.retryStep(2)
        advanceUntilIdle()

        assertEquals(LoadingStatus.Error, vm.uiState.value.steps[2].status)
        assertFalse(vm.uiState.value.loadingComplete)
        coVerify(exactly = 0) { cacheService.saveCachedData(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `retryStep ohne Zugangsdaten setzt Fehler und ruft Repository nicht auf`() = runTest {
        every { credentialsRepository.getCredentials() } returns null

        val vm = viewModel()

        vm.retryStep(0)
        advanceUntilIdle()

        assertEquals("Keine Anmeldedaten gefunden", vm.uiState.value.error)
        coVerify(exactly = 0) { repository.loadLiveStreamsFromApi(any(), any()) }
    }

    @Test
    fun `retryStep mit ungueltigem Stepindex faengt IllegalArgumentException ohne Crash ab`() = runTest {
        val vm = viewModel()

        vm.retryStep(5)
        advanceUntilIdle()

        assertEquals("Invalid step index: 5", vm.uiState.value.error)
        assertFalse(vm.uiState.value.loadingComplete)
        coVerify(exactly = 0) { cacheService.saveCachedData(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `retryStep faengt eine geworfene Exception ab und markiert den Step als Error`() = runTest {
        coEvery { repository.loadSeriesFromApi("chris", "pw") } throws RuntimeException("Boom")

        val vm = viewModel()

        vm.retryStep(2)
        advanceUntilIdle()

        assertEquals(LoadingStatus.Error, vm.uiState.value.steps[2].status)
        assertEquals("Boom", vm.uiState.value.error)
    }

    @Test
    fun `checkCacheAndLoad nutzt gecachte Daten wenn Fallback-Cooldown aktiv ist`() = runTest {
        val cachedData = CachedData(
            channels = listOf(testLiveStream(streamId = 1)),
            movies = listOf(testVodStream(streamId = 1), testVodStream(streamId = 2)),
            series = listOf(testSeries(seriesId = 1)),
            liveCategories = listOf(liveCategory),
            vodCategories = listOf(vodCategory),
            seriesCategories = listOf(seriesCategory)
        )
        every { cacheService.isFallbackCooldownActive() } returns true
        every { cacheService.hasCacheData() } returns true
        coEvery { cacheService.loadCachedDataAsync() } returns cachedData

        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.isOfflineMode)
        assertEquals("Server nicht erreichbar. Verwende gespeicherte Daten.", state.offlineModeMessage)
        assertTrue(state.loadingComplete)
        assertFalse(state.isLoading)
        assertEquals(1f, state.overallProgress)
        assertEquals(LoadingStatus.Success, state.steps[0].status)
        assertEquals(1, state.steps[0].itemCount)
        assertEquals(2, state.steps[1].itemCount)
        assertEquals(1, state.steps[2].itemCount)
    }

    @Test
    fun `checkCacheAndLoad ignoriert Fallback-Cooldown wenn kein Cache vorhanden`() = runTest {
        every { cacheService.isFallbackCooldownActive() } returns true
        every { cacheService.hasCacheData() } returns false

        val vm = viewModel()
        advanceUntilIdle()

        // Faellt durch zu loadData() -> isLoading bleibt true, kein Abschluss
        assertTrue(vm.uiState.value.isLoading)
        assertFalse(vm.uiState.value.loadingComplete)
        coVerify(exactly = 0) { cacheService.loadCachedDataAsync() }
    }

    @Test
    fun `checkCacheAndLoad faellt durch wenn Fallback-Cache leer zurueckgegeben wird`() = runTest {
        every { cacheService.isFallbackCooldownActive() } returns true
        every { cacheService.hasCacheData() } returns true
        every { cacheService.isCacheValid() } returns false
        coEvery { cacheService.loadCachedDataAsync() } returns null

        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isLoading)
        assertFalse(vm.uiState.value.loadingComplete)
        assertFalse(vm.uiState.value.isOfflineMode)
    }

    @Test
    fun `checkCacheAndLoad nutzt gueltigen Cache ohne Offline-Hinweis`() = runTest {
        val cachedData = CachedData(
            channels = listOf(testLiveStream(streamId = 5)),
            movies = emptyList(),
            series = listOf(testSeries(seriesId = 9)),
            liveCategories = emptyList(),
            vodCategories = emptyList(),
            seriesCategories = emptyList()
        )
        every { cacheService.isCacheValid() } returns true
        coEvery { cacheService.loadCachedDataAsync() } returns cachedData

        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.loadingComplete)
        assertFalse(state.isOfflineMode)
        assertNull(state.offlineModeMessage)
        assertEquals(1, state.steps[0].itemCount)
        assertEquals(0, state.steps[1].itemCount)
        assertEquals(1, state.steps[2].itemCount)
    }

    @Test
    fun `checkCacheAndLoad faellt durch wenn gueltiger Cache leer zurueckgegeben wird`() = runTest {
        every { cacheService.isCacheValid() } returns true
        coEvery { cacheService.loadCachedDataAsync() } returns null

        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isLoading)
        assertFalse(vm.uiState.value.loadingComplete)
    }

    @Test
    fun `loadData verarbeitet vollstaendigen Fortschritt ueber alle Schritte`() = runTest {
        val progressFlow = flowOf(
            DataRefreshProgress(step = RefreshStep.CHANNELS, message = "Lade Kanaele..."),
            DataRefreshProgress(
                step = RefreshStep.MOVIES,
                message = "Lade Filme...",
                itemCounts = ItemCounts(channels = 4)
            ),
            DataRefreshProgress(
                step = RefreshStep.SERIES,
                message = "Lade Serien...",
                itemCounts = ItemCounts(channels = 4, movies = 6)
            ),
            DataRefreshProgress(
                step = RefreshStep.SAVING,
                message = "Speichere Cache...",
                itemCounts = ItemCounts(channels = 4, movies = 6, series = 8)
            ),
            DataRefreshProgress(
                step = RefreshStep.SAVING,
                message = "Cache erfolgreich aktualisiert",
                itemCounts = ItemCounts(channels = 4, movies = 6, series = 8),
                isComplete = true
            )
        )
        every { dataRefreshUseCase.refreshData("chris", "pw") } returns progressFlow

        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(LoadingStatus.Success, state.steps[0].status)
        assertEquals(4, state.steps[0].itemCount)
        assertEquals(LoadingStatus.Success, state.steps[1].status)
        assertEquals(6, state.steps[1].itemCount)
        assertEquals(LoadingStatus.Success, state.steps[2].status)
        assertEquals(8, state.steps[2].itemCount)
        assertEquals(LoadingStatus.Success, state.steps[SAVE_STEP_INDEX].status)
        assertEquals(18, state.steps[SAVE_STEP_INDEX].itemCount)
        assertTrue(state.loadingComplete)
        assertFalse(state.isLoading)
        assertEquals(1f, state.overallProgress)
    }

    @Test
    fun `loadData zeigt Speicherfortschritt als eigenen Schritt`() = runTest {
        val progressFlow = flowOf(
            DataRefreshProgress(step = RefreshStep.CHANNELS, message = "Lade Kanaele..."),
            DataRefreshProgress(
                step = RefreshStep.SAVING,
                message = "Speichere Cache...",
                itemCounts = ItemCounts(channels = 4, movies = 6, series = 8),
                itemsToSave = 18
            ),
            DataRefreshProgress(
                step = RefreshStep.SAVING,
                message = "Speichere Cache... (9 / 18)",
                itemCounts = ItemCounts(channels = 4, movies = 6, series = 8),
                savedItems = 9,
                itemsToSave = 18
            )
        )
        every { dataRefreshUseCase.refreshData("chris", "pw") } returns progressFlow

        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        val saveStep = state.steps[SAVE_STEP_INDEX]
        assertEquals(4, state.steps.size)
        assertEquals(LoadingStatus.Loading, saveStep.status)
        assertEquals(0.5f, saveStep.progress)
        assertEquals(LoadingStatus.Success, state.steps[2].status)
        assertEquals(3.5f / 4f, state.overallProgress)
        assertFalse(state.loadingComplete)
    }

    @Test
    fun `loadData markiert Step als Retrying bei Retry-Fortschritt`() = runTest {
        val progressFlow = flowOf(
            DataRefreshProgress(
                step = RefreshStep.CHANNELS,
                message = "Erneuter Versuch...",
                retryAttempt = 1,
                maxRetries = 3
            )
        )
        every { dataRefreshUseCase.refreshData("chris", "pw") } returns progressFlow

        val vm = viewModel()
        advanceUntilIdle()

        val step = vm.uiState.value.steps[0]
        assertEquals(LoadingStatus.Retrying, step.status)
        assertEquals(1, step.retryAttempt)
        assertEquals(3, step.maxRetries)
    }

    @Test
    fun `loadData markiert betroffenen Step als Error bei Fehler-Fortschritt`() = runTest {
        val progressFlow = flowOf(
            DataRefreshProgress(
                step = RefreshStep.MOVIES,
                message = "Fehler",
                error = "Netzwerk nicht erreichbar"
            )
        )
        every { dataRefreshUseCase.refreshData("chris", "pw") } returns progressFlow

        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(LoadingStatus.Error, state.steps[1].status)
        assertEquals("Netzwerk nicht erreichbar", state.steps[1].errorMessage)
        assertEquals("Netzwerk nicht erreichbar", state.error)
        assertFalse(state.isLoading)
    }

    @Test
    fun `loadData nutzt Cache-Fallback und uebernimmt gecachte Daten`() = runTest {
        val cachedData = CachedData(
            channels = listOf(testLiveStream(streamId = 1), testLiveStream(streamId = 2)),
            movies = listOf(testVodStream(streamId = 1)),
            series = emptyList(),
            liveCategories = emptyList(),
            vodCategories = emptyList(),
            seriesCategories = emptyList()
        )
        coEvery { cacheService.loadCachedDataAsync() } returns cachedData
        val progressFlow = flowOf(
            DataRefreshProgress(
                step = RefreshStep.SAVING,
                message = "Server nicht erreichbar. Verwende gespeicherte Daten.",
                isComplete = true,
                usedCacheFallback = true
            )
        )
        every { dataRefreshUseCase.refreshData("chris", "pw") } returns progressFlow

        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.isOfflineMode)
        assertEquals("Server nicht erreichbar. Verwende gespeicherte Daten.", state.offlineModeMessage)
        assertTrue(state.loadingComplete)
        assertFalse(state.isLoading)
        assertEquals(2, state.steps[0].itemCount)
        assertEquals(1, state.steps[1].itemCount)
    }

    @Test
    fun `loadData Cache-Fallback ohne gecachte Daten behaelt bestehende Steps`() = runTest {
        coEvery { cacheService.loadCachedDataAsync() } returns null
        val progressFlow = flowOf(
            DataRefreshProgress(
                step = RefreshStep.SAVING,
                message = "Keine gespeicherten Daten verfuegbar.",
                isComplete = false,
                usedCacheFallback = true
            )
        )
        every { dataRefreshUseCase.refreshData("chris", "pw") } returns progressFlow

        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.isOfflineMode)
        assertEquals(LoadingStatus.Pending, state.steps[0].status)
    }

    @Test
    fun `loadData bei Server-Rekonfiguration loescht Zugangsdaten und sendet NavigationEvent`() = runTest {
        val progressFlow = flowOf(
            DataRefreshProgress(
                step = RefreshStep.SAVING,
                message = "Bitte Serveradresse neu eingeben.",
                requiresServerReconfiguration = true
            )
        )
        every { dataRefreshUseCase.refreshData("chris", "pw") } returns progressFlow
        every { credentialsRepository.clearActiveCredentials() } just Runs

        val vm = viewModel()
        advanceUntilIdle()

        verify { credentialsRepository.clearActiveCredentials() }
        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.loadingComplete)
        assertNull(state.error)
        assertEquals(0f, state.overallProgress)

        val event = vm.navigationEvents.first()
        assertTrue(event is DataLoadingViewModel.NavigationEvent.ReconfigureServer)
    }

    @Test
    fun `retry setzt alle Steps zurueck und laedt erneut`() = runTest {
        coEvery { repository.loadSeriesFromApi("chris", "pw") } coAnswers {
            Result.failure(Exception("Netzwerkfehler"))
        }
        val vm = viewModel()
        vm.retryStep(2)
        advanceUntilIdle()
        assertEquals(LoadingStatus.Error, vm.uiState.value.steps[2].status)

        vm.retry()
        advanceUntilIdle()

        verify { cacheService.clearFallbackCooldown() }
        val state = vm.uiState.value
        assertEquals(0f, state.overallProgress)
        assertNull(state.error)
        assertFalse(state.loadingComplete)
        assertFalse(state.isOfflineMode)
        assertNull(state.offlineModeMessage)
        state.steps.forEach { step ->
            assertEquals(LoadingStatus.Pending, step.status)
            assertNull(step.itemCount)
            assertNull(step.errorMessage)
            assertEquals(0, step.retryAttempt)
            assertEquals(0, step.maxRetries)
        }
    }
}
