package com.flintr.tv.ui.navigation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flintr.tv.data.local.Credentials
import com.flintr.tv.data.local.DataCacheService
import com.flintr.tv.data.local.WatchProgressService
import com.flintr.tv.data.model.WatchProgress
import com.flintr.tv.data.model.testEpisode
import com.flintr.tv.data.model.testSeason
import com.flintr.tv.data.model.testSeries
import com.flintr.tv.data.model.testVodStream
import com.flintr.tv.data.repository.XtreamRepository
import com.flintr.tv.ui.common.UiConstants.IN_PROGRESS_CATEGORY_ID
import com.flintr.tv.ui.screens.HomeDestination
import com.flintr.tv.ui.tabs.InProgressItemsState
import com.flintr.tv.ui.tabs.rememberInProgressItems
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Tests fuer die "Angefangene Inhalte"-Zaehlung in der Sidebar (Task 10b). Der Zaehler muss
 * exakt der Anzahl der tatsaechlich angezeigten Karten entsprechen - deshalb leitet
 * calculateItemCounts() die Zahl aus demselben InProgressItemsState ab, den auch die Liste
 * rendert (rememberInProgressItems in InProgressContent.kt). Ein toter Progress-Eintrag
 * (Katalog-Eintrag fehlt) darf den Zaehler NICHT aufblasen.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ItemCountsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val credentials = Credentials(serverUrl = "http://server", username = "chris", password = "pw")

    private fun progress() = WatchProgress(
        position = 60_000,
        duration = 1_200_000,
        lastWatched = "2026-01-01T00:00:00Z",
        title = "Folge"
    )

    private fun progressService(flow: MutableStateFlow<Map<String, WatchProgress>>): WatchProgressService {
        val watchProgressService: WatchProgressService = mockk()
        every { watchProgressService.watchProgress } returns flow
        coEvery { watchProgressService.syncIfNeeded() } returns false
        coEvery { watchProgressService.cleanupCompletedProgress() } returns 0
        return watchProgressService
    }

    /** Snapshot pro Composition: (Sidebar-Zaehler, Kartenanzahl der Liste, Loader sichtbar). */
    private data class Frame(val counter: Int, val cards: Int, val loader: Boolean)

    private fun setContent(
        destination: HomeDestination,
        watchProgressService: WatchProgressService,
        cacheService: DataCacheService,
        xtreamRepository: XtreamRepository,
        frames: MutableList<Frame> = mutableListOf()
    ): () -> Map<String, Int> {
        var counts: Map<String, Int> = emptyMap()
        composeTestRule.setContent {
            val state: InProgressItemsState = rememberInProgressItems(
                destination = destination,
                watchProgressService = watchProgressService,
                cacheService = cacheService,
                xtreamRepository = xtreamRepository,
                credentials = credentials
            )
            counts = calculateItemCounts(state.items)
            frames += Frame(counts[IN_PROGRESS_CATEGORY_ID] ?: 0, state.items.size, state.isInitialLoading)
        }
        return { counts }
    }

    @Test
    fun `Serien-Destination zaehlt nur aufloesbare Serien`() {
        val cacheService: DataCacheService = mockk(relaxed = true)
        val xtreamRepository: XtreamRepository = mockk()

        val allProgress = mapOf(
            "series:10:101" to progress(),
            "series:20:201" to progress(),
            // seriesId=30 existiert nicht mehr im Katalog -> darf nicht mitgezaehlt werden
            "series:30:301" to progress()
        )
        coEvery { xtreamRepository.getSeriesInfo(credentials.username, credentials.password, 10) } returns
            Result.success(
                testSeries(seriesId = 10, seasons = listOf(testSeason(episodes = listOf(testEpisode(id = 101)))))
            )
        coEvery { xtreamRepository.getSeriesInfo(credentials.username, credentials.password, 20) } returns
            Result.success(
                testSeries(seriesId = 20, seasons = listOf(testSeason(episodes = listOf(testEpisode(id = 201)))))
            )
        coEvery { xtreamRepository.getSeriesInfo(credentials.username, credentials.password, 30) } returns
            Result.failure(Exception("series removed from provider catalog"))

        val counts = setContent(
            HomeDestination.Series,
            progressService(MutableStateFlow(allProgress)),
            cacheService,
            xtreamRepository
        )
        // Series resolution delays each API call by API_CALL_DELAY_MS (InProgressContent.kt);
        // advance the test clock so the LaunchedEffect's suspend work actually completes.
        composeTestRule.mainClock.advanceTimeBy(500L)
        composeTestRule.waitForIdle()

        assertEquals(2, counts()[IN_PROGRESS_CATEGORY_ID])
    }

    @Test
    fun `VOD-Destination zaehlt nur aufloesbare Filme`() {
        val cacheService: DataCacheService = mockk()
        val xtreamRepository: XtreamRepository = mockk(relaxed = true)

        val allProgress = mapOf(
            "movie:1" to progress(),
            "movie:2" to progress(),
            // streamId=3 existiert nicht mehr im Katalog -> darf nicht mitgezaehlt werden
            "movie:3" to progress()
        )
        coEvery { cacheService.loadMoviesByIds(listOf(1, 2, 3)) } returns listOf(
            testVodStream(streamId = 1),
            testVodStream(streamId = 2)
        )

        val counts = setContent(
            HomeDestination.VOD,
            progressService(MutableStateFlow(allProgress)),
            cacheService,
            xtreamRepository
        )
        composeTestRule.waitForIdle()

        assertEquals(2, counts()[IN_PROGRESS_CATEGORY_ID])
    }

    @Test
    fun `Fortschritts-Aenderung aktualisiert Zaehler und Liste gemeinsam ohne Loader und Drift`() {
        val cacheService: DataCacheService = mockk()
        val xtreamRepository: XtreamRepository = mockk(relaxed = true)

        val progressFlow = MutableStateFlow(mapOf("movie:1" to progress()))
        val watchProgressService = progressService(progressFlow)
        // Echte Suspension wie Disk-IO auf dem Geraet: nur so werden Zwischenzustaende waehrend
        // des Refreshs (Loader, halb aktualisierte Werte) in einer Composition sichtbar.
        coEvery { cacheService.loadMoviesByIds(any()) } coAnswers {
            delay(100L)
            firstArg<List<Int>>().map { testVodStream(streamId = it) }
        }

        val frames = mutableListOf<Frame>()
        val counts = setContent(HomeDestination.VOD, watchProgressService, cacheService, xtreamRepository, frames)
        composeTestRule.mainClock.advanceTimeBy(500L)
        composeTestRule.waitForIdle()
        assertEquals(1, counts()[IN_PROGRESS_CATEGORY_ID])

        // Zweiter Film wird gestartet - ohne Destination-Wechsel muessen Zaehler UND Liste
        // nachziehen (Gemini-Finding Runde 1).
        frames.clear()
        progressFlow.value = mapOf("movie:1" to progress(), "movie:2" to progress())
        // In kleinen Schritten vorspulen, damit Frames WAEHREND des laufenden Refreshs entstehen.
        repeat(10) { composeTestRule.mainClock.advanceTimeBy(50L) }
        composeTestRule.waitForIdle()

        assertEquals(2, counts()[IN_PROGRESS_CATEGORY_ID])
        assertTrue("Refresh muss recomposen", frames.isNotEmpty())
        // Kein transienter Drift: in JEDER Composition gilt Zaehler == Kartenanzahl.
        frames.forEach { assertEquals("Zaehler != Karten in $it", it.cards, it.counter) }
        // Reaktiv-Refresh darf den Loader nicht zeigen (sonst Grid weg -> D-Pad-Fokus weg).
        assertFalse("Loader im Reaktiv-Refresh: $frames", frames.any { it.loader })
        // Cleanup/Sync laufen einmal pro Destination, nicht pro Fortschritts-Emission.
        coVerify(exactly = 1) { watchProgressService.cleanupCompletedProgress() }
        coVerify(exactly = 1) { watchProgressService.syncIfNeeded() }
    }

    @Test
    fun `Cleanup-Emission loest keinen zweiten Ladedurchlauf aus`() {
        val cacheService: DataCacheService = mockk()
        val xtreamRepository: XtreamRepository = mockk(relaxed = true)

        val progressFlow = MutableStateFlow(
            mapOf(
                "movie:1" to progress(),
                // >= 90% - wird von cleanupCompletedProgress() entfernt
                "movie:9" to WatchProgress(1_150_000, 1_200_000, "2026-01-01T00:00:00Z", "Fertig")
            )
        )
        val watchProgressService = progressService(progressFlow)
        coEvery { watchProgressService.cleanupCompletedProgress() } answers {
            progressFlow.value = progressFlow.value - "movie:9"
            1
        }
        coEvery { cacheService.loadMoviesByIds(any()) } answers {
            firstArg<List<Int>>().map { testVodStream(streamId = it) }
        }

        val counts = setContent(HomeDestination.VOD, watchProgressService, cacheService, xtreamRepository)
        composeTestRule.mainClock.advanceTimeBy(500L)
        composeTestRule.waitForIdle()

        assertEquals(1, counts()[IN_PROGRESS_CATEGORY_ID])
        // Genau EINE Aufloesung - mit dem bereinigten Stand, ohne Selbst-Neutrigger.
        coVerify(exactly = 1) { cacheService.loadMoviesByIds(any()) }
        coVerify(exactly = 1) { cacheService.loadMoviesByIds(listOf(1)) }
    }

    @Test
    fun `Label pluralisiert nach Anzahl`() {
        assertEquals("Film", itemLabelForDestination(HomeDestination.VOD, 1))
        assertEquals("Filme", itemLabelForDestination(HomeDestination.VOD, 3))
        assertEquals("Serie", itemLabelForDestination(HomeDestination.Series, 1))
        assertEquals("Serien", itemLabelForDestination(HomeDestination.Series, 2))
        assertEquals("Titel", itemLabelForDestination(HomeDestination.LiveTV, 1))
    }
}
