package com.flintr.tv.viewmodel

import com.flintr.tv.data.local.CachedData
import com.flintr.tv.data.local.Credentials
import com.flintr.tv.data.local.CredentialsRepository
import com.flintr.tv.data.local.DataCacheService
import com.flintr.tv.data.model.testLiveStream
import com.flintr.tv.data.model.testSeries
import com.flintr.tv.data.model.testVodStream
import com.flintr.tv.data.repository.XtreamRepository
import com.flintr.tv.domain.usecase.DataRefreshProgress
import com.flintr.tv.domain.usecase.DataRefreshUseCase
import com.flintr.tv.domain.usecase.ItemCounts
import com.flintr.tv.domain.usecase.RefreshStep
import com.flintr.tv.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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

    private fun cachedData() = CachedData(
        channels = listOf(testLiveStream(streamId = 100)),
        movies = listOf(testVodStream(streamId = 1), testVodStream(streamId = 2)),
        series = listOf(testSeries(seriesId = 10)),
        liveCategories = emptyList(),
        vodCategories = emptyList(),
        seriesCategories = emptyList()
    )

    @Before
    fun setUp() {
        repository = mockk(relaxed = true)
        credentialsRepository = mockk(relaxed = true)
        cacheService = mockk(relaxed = true)
        dataRefreshUseCase = mockk()

        every { credentialsRepository.getCredentials() } returns credentials
        every { cacheService.isFallbackCooldownActive() } returns false
        every { cacheService.isCacheValid() } returns false
        every { cacheService.hasCacheData() } returns false
        every { dataRefreshUseCase.refreshData(any(), any()) } returns flowOf()
    }

    private fun viewModel() = DataLoadingViewModel(
        repository = repository,
        credentialsRepository = credentialsRepository,
        cacheService = cacheService,
        dataRefreshUseCase = dataRefreshUseCase
    )

    @Test
    fun `gueltiger Cache beendet Laden sofort mit Erfolgs-Schritten`() = runTest {
        every { cacheService.isCacheValid() } returns true
        every { cacheService.loadCachedData() } returns cachedData()

        val vm = viewModel()

        val state = vm.uiState.value
        assertTrue(state.loadingComplete)
        assertFalse(state.isLoading)
        assertFalse(state.isOfflineMode)
        assertEquals(1f, state.overallProgress)
        assertEquals(listOf(1, 2, 1), state.steps.map { it.itemCount })
        assertTrue(state.steps.all { it.status == LoadingStatus.Success })
    }

    @Test
    fun `aktiver Fallback-Cooldown nutzt Cache im Offline-Modus`() = runTest {
        every { cacheService.isFallbackCooldownActive() } returns true
        every { cacheService.hasCacheData() } returns true
        every { cacheService.loadCachedData() } returns cachedData()

        val vm = viewModel()

        assertTrue(vm.uiState.value.loadingComplete)
        assertTrue(vm.uiState.value.isOfflineMode)
        assertTrue(vm.uiState.value.offlineModeMessage!!.contains("gespeicherte Daten"))
    }

    @Test
    fun `ohne Credentials wird Fehler gemeldet`() = runTest {
        every { credentialsRepository.getCredentials() } returns null

        val vm = viewModel()

        assertEquals("Keine Anmeldedaten gefunden", vm.uiState.value.error)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `normale Fortschrittsfolge fuehrt zu abgeschlossenen Schritten`() = runTest {
        every { dataRefreshUseCase.refreshData("chris", "pw") } returns flowOf(
            DataRefreshProgress(RefreshStep.CHANNELS, "Kanaele laden"),
            DataRefreshProgress(RefreshStep.MOVIES, "Filme laden", ItemCounts(channels = 5)),
            DataRefreshProgress(RefreshStep.SERIES, "Serien laden", ItemCounts(movies = 7)),
            DataRefreshProgress(RefreshStep.SAVING, "Speichern", ItemCounts(series = 3)),
            DataRefreshProgress(RefreshStep.SAVING, "Fertig", isComplete = true)
        )

        val vm = viewModel()

        val state = vm.uiState.value
        assertTrue(state.loadingComplete)
        assertEquals(1f, state.overallProgress)
        assertEquals(listOf(5, 7, 3), state.steps.map { it.itemCount })
        assertTrue(state.steps.all { it.status == LoadingStatus.Success })
    }

    @Test
    fun `Fehler-Fortschritt markiert Schritt und setzt Fehler`() = runTest {
        every { dataRefreshUseCase.refreshData("chris", "pw") } returns flowOf(
            DataRefreshProgress(RefreshStep.CHANNELS, "Kanaele laden"),
            DataRefreshProgress(RefreshStep.MOVIES, "Fehler", error = "API kaputt")
        )

        val vm = viewModel()

        assertEquals("API kaputt", vm.uiState.value.error)
        assertEquals(LoadingStatus.Error, vm.uiState.value.steps[1].status)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `Retry-Fortschritt zeigt Wiederholungsversuch am Schritt`() = runTest {
        every { dataRefreshUseCase.refreshData("chris", "pw") } returns flowOf(
            DataRefreshProgress(RefreshStep.CHANNELS, "Erneut", retryAttempt = 2, maxRetries = 3)
        )

        val vm = viewModel()

        val step = vm.uiState.value.steps[0]
        assertEquals(LoadingStatus.Retrying, step.status)
        assertEquals(2, step.retryAttempt)
        assertEquals(3, step.maxRetries)
    }

    @Test
    fun `Cache-Fallback-Fortschritt aktiviert Offline-Modus`() = runTest {
        every { cacheService.loadCachedData() } returns cachedData()
        every { dataRefreshUseCase.refreshData("chris", "pw") } returns flowOf(
            DataRefreshProgress(RefreshStep.SAVING, "Server nicht erreichbar", usedCacheFallback = true)
        )

        val vm = viewModel()

        assertTrue(vm.uiState.value.isOfflineMode)
        assertTrue(vm.uiState.value.loadingComplete)
        assertEquals("Server nicht erreichbar", vm.uiState.value.offlineModeMessage)
    }

    @Test
    fun `Server-Rekonfiguration leert Credentials und sendet Navigationsereignis`() = runTest {
        every { dataRefreshUseCase.refreshData("chris", "pw") } returns flowOf(
            DataRefreshProgress(RefreshStep.CHANNELS, "410", requiresServerReconfiguration = true)
        )

        val vm = viewModel()
        val event = vm.navigationEvents.first()

        assertEquals(DataLoadingViewModel.NavigationEvent.ReconfigureServer, event)
        verify { credentialsRepository.clearActiveCredentials() }
        assertFalse(vm.uiState.value.loadingComplete)
    }

    @Test
    fun `retry setzt Schritte zurueck und laedt neu`() = runTest {
        every { dataRefreshUseCase.refreshData("chris", "pw") } returns flowOf(
            DataRefreshProgress(RefreshStep.MOVIES, "Fehler", error = "API kaputt")
        )
        val vm = viewModel()
        assertEquals("API kaputt", vm.uiState.value.error)

        every { dataRefreshUseCase.refreshData("chris", "pw") } returns flowOf(
            DataRefreshProgress(RefreshStep.SAVING, "Fertig", isComplete = true)
        )

        vm.retry()

        verify { cacheService.clearFallbackCooldown() }
        assertNull(vm.uiState.value.error)
        assertTrue(vm.uiState.value.loadingComplete)
    }

    @Test
    fun `retryStep laedt einzelnen Schritt nach`() = runTest {
        coEvery { repository.loadLiveStreamsFromApi("chris", "pw") } coAnswers {
            Result.success(listOf(testLiveStream(streamId = 100)))
        }
        val vm = viewModel()

        vm.retryStep(0)

        assertEquals(LoadingStatus.Success, vm.uiState.value.steps[0].status)
        assertEquals(1, vm.uiState.value.steps[0].itemCount)
    }

    @Test
    fun `retryStep meldet Fehler des Einzelschritts`() = runTest {
        coEvery { repository.loadVodStreamsFromApi("chris", "pw") } coAnswers { Result.failure(Exception("VOD kaputt")) }
        val vm = viewModel()

        vm.retryStep(1)

        assertEquals(LoadingStatus.Error, vm.uiState.value.steps[1].status)
        assertEquals("VOD kaputt", vm.uiState.value.error)
    }

    @Test
    fun `retryStep speichert Cache wenn alle Schritte erfolgreich`() = runTest {
        coEvery { repository.loadLiveStreamsFromApi(any(), any()) } coAnswers { Result.success(listOf(testLiveStream())) }
        coEvery { repository.loadVodStreamsFromApi(any(), any()) } coAnswers { Result.success(listOf(testVodStream())) }
        coEvery { repository.loadSeriesFromApi(any(), any()) } coAnswers { Result.success(listOf(testSeries())) }
        coEvery { repository.getLiveStreams(any(), any()) } coAnswers { Result.success(listOf(testLiveStream())) }
        coEvery { repository.getVodStreams(any(), any()) } coAnswers { Result.success(listOf(testVodStream())) }
        coEvery { repository.getSeries(any(), any()) } coAnswers { Result.success(listOf(testSeries())) }
        coEvery { repository.getLiveCategories(any(), any()) } coAnswers { Result.success(emptyList()) }
        coEvery { repository.getVodCategories(any(), any()) } coAnswers { Result.success(emptyList()) }
        coEvery { repository.getSeriesCategories(any(), any()) } coAnswers { Result.success(emptyList()) }
        every { cacheService.saveCachedData(any(), any(), any(), any(), any(), any()) } returns true

        val vm = viewModel()
        vm.retryStep(0)
        vm.retryStep(1)
        vm.retryStep(2)

        assertTrue(vm.uiState.value.loadingComplete)
        verify { cacheService.saveCachedData(any(), any(), any(), any(), any(), any()) }
    }
}
