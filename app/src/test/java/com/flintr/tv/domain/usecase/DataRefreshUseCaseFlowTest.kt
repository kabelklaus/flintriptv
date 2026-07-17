package com.flintr.tv.domain.usecase

import com.flintr.tv.data.local.DataCacheService
import com.flintr.tv.data.model.Category
import com.flintr.tv.data.model.testLiveStream
import com.flintr.tv.data.model.testSeries
import com.flintr.tv.data.model.testVodStream
import com.flintr.tv.data.repository.XtreamRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.UnknownHostException

@RunWith(RobolectricTestRunner::class)
class DataRefreshUseCaseFlowTest {

    private lateinit var repository: XtreamRepository
    private lateinit var cacheService: DataCacheService
    private lateinit var useCase: DataRefreshUseCase

    private val channels = listOf(testLiveStream(streamId = 100))
    private val movies = listOf(testVodStream(streamId = 1), testVodStream(streamId = 2))
    private val series = listOf(testSeries(seriesId = 10))
    private val categories = listOf(Category(categoryId = "1", categoryName = "Kat"))

    @Before
    fun setUp() {
        repository = mockk()
        cacheService = mockk(relaxed = true)
        useCase = DataRefreshUseCase(repository, cacheService)

        coEvery { repository.loadLiveStreamsFromApi(any(), any()) } coAnswers { Result.success(channels) }
        coEvery { repository.loadVodStreamsFromApi(any(), any()) } coAnswers { Result.success(movies) }
        coEvery { repository.loadSeriesFromApi(any(), any()) } coAnswers { Result.success(series) }
        coEvery { repository.getLiveCategories(any(), any()) } coAnswers { Result.success(categories) }
        coEvery { repository.getVodCategories(any(), any()) } coAnswers { Result.success(categories) }
        coEvery { repository.getSeriesCategories(any(), any()) } coAnswers { Result.success(categories) }
    }

    @Test
    fun `erfolgreicher Refresh emittiert Fortschritt und speichert Cache`() = runTest {
        val events = useCase.refreshData("chris", "pw").toList()

        assertEquals(
            listOf(RefreshStep.CHANNELS, RefreshStep.MOVIES, RefreshStep.SERIES, RefreshStep.SAVING, RefreshStep.SAVING),
            events.map { it.step }
        )
        val final = events.last()
        assertTrue(final.isComplete)
        assertNull(final.error)
        assertEquals(1, final.itemCounts?.channels)
        assertEquals(2, final.itemCounts?.movies)
        assertEquals(1, final.itemCounts?.series)
        verify { cacheService.saveCachedData(channels, movies, series, categories, categories, categories) }
        verify { cacheService.clearFallbackCooldown() }
    }

    @Test
    fun `leere Antworten werden mit Wiederholungen behandelt`() = runTest {
        coEvery { repository.loadLiveStreamsFromApi(any(), any()) } coAnswers { Result.success(emptyList()) }

        val events = useCase.refreshData("chris", "pw").toList()

        val final = events.last()
        assertFalse(final.isComplete)
        assertTrue(final.error!!.contains("Versuchen"))
        assertEquals(3, final.retryAttempt)
        // 1 Versuch + 3 Wiederholungen
        coVerify(exactly = 4) { repository.loadLiveStreamsFromApi(any(), any()) }
    }

    @Test
    fun `Wiederholung nach leerer Antwort fuehrt zum Erfolg`() = runTest {
        var attempts = 0
        coEvery { repository.loadLiveStreamsFromApi(any(), any()) } coAnswers {
            attempts++
            if (attempts == 1) Result.success(emptyList()) else Result.success(channels)
        }

        val events = useCase.refreshData("chris", "pw").toList()

        assertTrue(events.last().isComplete)
        assertEquals(2, attempts)
    }

    @Test
    fun `Verbindungsfehler mit Cache faellt auf gespeicherte Daten zurueck`() = runTest {
        coEvery { repository.loadLiveStreamsFromApi(any(), any()) } coAnswers {
            Result.failure(UnknownHostException("offline"))
        }
        every { cacheService.hasUsableFallbackCache() } returns true

        val events = useCase.refreshData("chris", "pw").toList()

        val final = events.last()
        assertTrue(final.usedCacheFallback)
        assertTrue(final.isComplete)
        verify { cacheService.setFallbackCooldownTimestamp(any()) }
    }

    @Test
    fun `Verbindungsfehler ohne Cache verlangt Server-Rekonfiguration`() = runTest {
        coEvery { repository.loadLiveStreamsFromApi(any(), any()) } coAnswers {
            Result.failure(UnknownHostException("offline"))
        }
        every { cacheService.hasUsableFallbackCache() } returns false

        val events = useCase.refreshData("chris", "pw").toList()

        val final = events.last()
        assertTrue(final.requiresServerReconfiguration)
        assertFalse(final.isComplete)
        assertNull(final.error)
    }

    @Test
    fun `API-Fehler ohne Verbindungsbezug meldet fehlgeschlagenen Schritt`() = runTest {
        coEvery { repository.loadVodStreamsFromApi(any(), any()) } coAnswers {
            Result.failure(Exception("Failed to get VOD streams: 401"))
        }

        val events = useCase.refreshData("chris", "pw").toList()

        val final = events.last()
        assertEquals(RefreshStep.MOVIES, final.step)
        assertTrue(final.error!!.contains("401"))
        assertEquals(1, final.itemCounts?.channels)
    }

    @Test
    fun `Kategoriefehler blockieren den Refresh nicht`() = runTest {
        coEvery { repository.getLiveCategories(any(), any()) } coAnswers { Result.failure(Exception("kaputt")) }

        val events = useCase.refreshData("chris", "pw").toList()

        assertTrue(events.last().isComplete)
        verify { cacheService.saveCachedData(channels, movies, series, emptyList(), categories, categories) }
    }
}
