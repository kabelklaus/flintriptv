package com.flintr.tv.viewmodel

import com.flintr.tv.data.local.CredentialsRepository
import com.flintr.tv.data.local.Credentials
import com.flintr.tv.data.local.DataCacheService
import com.flintr.tv.data.model.Category
import com.flintr.tv.data.model.Series
import com.flintr.tv.data.model.testLiveStream
import com.flintr.tv.data.model.testSeries
import com.flintr.tv.data.model.testVodStream
import com.flintr.tv.data.repository.XtreamRepository
import com.flintr.tv.domain.usecase.DataRefreshUseCase
import com.flintr.tv.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        every {
            cacheService.saveCachedData(
                channels = any(),
                movies = any(),
                series = capture(seriesSlot),
                liveCategories = any(),
                vodCategories = any(),
                seriesCategories = any()
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
        coVerify(exactly = 0) { cacheService.saveCachedData(any(), any(), any(), any(), any(), any()) }
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
        coVerify(exactly = 0) { cacheService.saveCachedData(any(), any(), any(), any(), any(), any()) }
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
}
