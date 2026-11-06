package com.flintr.tv.ui.tabs

import com.flintr.tv.data.local.Credentials
import com.flintr.tv.data.local.DataCacheService
import com.flintr.tv.data.model.WatchProgress
import com.flintr.tv.data.model.testEpisode
import com.flintr.tv.data.model.testSeason
import com.flintr.tv.data.model.testSeries
import com.flintr.tv.data.model.testVodStream
import com.flintr.tv.data.repository.XtreamRepository
import com.flintr.tv.ui.common.UiConstants.IN_PROGRESS_CATEGORY_ID
import com.flintr.tv.ui.navigation.calculateItemCounts
import com.flintr.tv.ui.screens.HomeDestination
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Task 10b: Der Sidebar-Zaehler (calculateItemCounts) muss immer dieselbe Zahl liefern wie die
 * Liste Karten hat, weil er aus derselben Item-Liste (InProgressItemsState.items) abgeleitet
 * wird, die loadInProgressItemsCore() liefert. Ein Progress-Eintrag, dessen Serie/Episode/Film nicht mehr
 * im Katalog existiert, wird aus BEIDEN verworfen - aber nicht geloescht, die Rohdaten
 * (WatchProgress) bleiben erhalten.
 */
@RunWith(RobolectricTestRunner::class)
class InProgressContentCountTest {

    private val credentials = Credentials(serverUrl = "http://server", username = "chris", password = "pw")

    private fun progress() = WatchProgress(
        position = 60_000,
        duration = 1_200_000,
        lastWatched = "2026-01-01T00:00:00Z",
        title = "Folge"
    )

    private fun counterFor(items: List<InProgressItem>): Int? =
        calculateItemCounts(items)[IN_PROGRESS_CATEGORY_ID]

    @Test
    fun `Serien - toter Katalogeintrag wird aus Liste UND Zaehler verworfen`() = runBlocking {
        val xtreamRepository: XtreamRepository = mockk()
        coEvery { xtreamRepository.getSeriesInfo(credentials.username, credentials.password, 10) } returns
            Result.success(
                testSeries(seriesId = 10, seasons = listOf(testSeason(episodes = listOf(testEpisode(id = 101)))))
            )
        coEvery { xtreamRepository.getSeriesInfo(credentials.username, credentials.password, 20) } returns
            Result.success(
                testSeries(seriesId = 20, seasons = listOf(testSeason(episodes = listOf(testEpisode(id = 201)))))
            )
        // seriesId=30 existiert nicht mehr im Katalog (z.B. vom Provider entfernt)
        coEvery { xtreamRepository.getSeriesInfo(credentials.username, credentials.password, 30) } returns
            Result.failure(Exception("series removed from provider catalog"))

        val allProgress = mapOf(
            "series:10:101" to progress(),
            "series:20:201" to progress(),
            "series:30:301" to progress()
        )

        val resolvedItems = loadInProgressItemsCore(
            destination = HomeDestination.Series,
            cacheService = mockk(relaxed = true),
            xtreamRepository = xtreamRepository,
            credentials = credentials,
            allProgress = allProgress
        )

        assertEquals(2, resolvedItems.size)
        assertEquals(resolvedItems.size, counterFor(resolvedItems))
    }

    @Test
    fun `Filme - toter Katalogeintrag wird aus Liste UND Zaehler verworfen`() = runBlocking {
        val cacheService: DataCacheService = mockk()
        // streamId=3 existiert nicht mehr im Katalog
        coEvery { cacheService.loadMoviesByIds(listOf(1, 2, 3)) } returns listOf(
            testVodStream(streamId = 1),
            testVodStream(streamId = 2)
        )

        val allProgress = mapOf(
            "movie:1" to progress(),
            "movie:2" to progress(),
            "movie:3" to progress()
        )

        val resolvedItems = loadInProgressItemsCore(
            destination = HomeDestination.VOD,
            cacheService = cacheService,
            xtreamRepository = null,
            credentials = null,
            allProgress = allProgress
        )

        assertEquals(2, resolvedItems.size)
        assertEquals(resolvedItems.size, counterFor(resolvedItems))
    }

    @Test
    fun `leere Liste blendet den Zaehler aus`() {
        assertEquals(null, counterFor(emptyList()))
    }
}
