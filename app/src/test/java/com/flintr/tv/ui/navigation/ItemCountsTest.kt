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
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Tests fuer die "Angefangene Inhalte"-Zaehlung in der Sidebar (Task 10b). Der Zaehler muss
 * exakt der Anzahl der tatsaechlich angezeigten Karten entsprechen - deshalb loest
 * calculateItemCounts() ueber dieselbe katalog-aufloesende Pipeline auf wie die Liste
 * (siehe countInProgressItems/loadInProgressItemsCore in InProgressContent.kt). Ein toter
 * Progress-Eintrag (Katalog-Eintrag fehlt) darf den Zaehler NICHT aufblasen.
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

    @Test
    fun `Serien-Destination zaehlt nur aufloesbare Serien und nutzt Serien-Label`() {
        val watchProgressService: WatchProgressService = mockk()
        val cacheService: DataCacheService = mockk(relaxed = true)
        val xtreamRepository: XtreamRepository = mockk()

        coEvery { watchProgressService.getAllInProgress() } returns mapOf(
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

        var counts: Map<String, Int> = emptyMap()
        composeTestRule.setContent {
            counts = calculateItemCounts(
                selectedDestination = HomeDestination.Series,
                watchProgressService = watchProgressService,
                cacheService = cacheService,
                xtreamRepository = xtreamRepository,
                credentials = credentials
            )
        }
        // Series resolution delays each API call by API_CALL_DELAY_MS (InProgressContent.kt);
        // advance the test clock so the LaunchedEffect's suspend work actually completes.
        composeTestRule.mainClock.advanceTimeBy(500L)
        composeTestRule.waitForIdle()

        assertEquals(2, counts[IN_PROGRESS_CATEGORY_ID])
        assertEquals("Serien", itemLabelForDestination(HomeDestination.Series))
    }

    @Test
    fun `VOD-Destination zaehlt nur aufloesbare Filme und nutzt Filme-Label`() {
        val watchProgressService: WatchProgressService = mockk()
        val cacheService: DataCacheService = mockk()
        val xtreamRepository: XtreamRepository = mockk(relaxed = true)

        coEvery { watchProgressService.getAllInProgress() } returns mapOf(
            "movie:1" to progress(),
            "movie:2" to progress(),
            // streamId=3 existiert nicht mehr im Katalog -> darf nicht mitgezaehlt werden
            "movie:3" to progress()
        )
        coEvery { cacheService.loadMoviesByIds(listOf(1, 2, 3)) } returns listOf(
            testVodStream(streamId = 1),
            testVodStream(streamId = 2)
        )

        var counts: Map<String, Int> = emptyMap()
        composeTestRule.setContent {
            counts = calculateItemCounts(
                selectedDestination = HomeDestination.VOD,
                watchProgressService = watchProgressService,
                cacheService = cacheService,
                xtreamRepository = xtreamRepository,
                credentials = credentials
            )
        }
        composeTestRule.waitForIdle()

        assertEquals(2, counts[IN_PROGRESS_CATEGORY_ID])
        assertEquals("Filme", itemLabelForDestination(HomeDestination.VOD))
    }
}
