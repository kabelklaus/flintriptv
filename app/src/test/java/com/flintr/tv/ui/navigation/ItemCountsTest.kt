package com.flintr.tv.ui.navigation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flintr.tv.data.local.WatchProgressService
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
 * Tests fuer die "Angefangene Inhalte"-Zaehlung in der Sidebar: Serien werden DISTINCT
 * gezaehlt (eine Serie mit mehreren angefangenen Episoden zaehlt einmal), nicht die
 * Episoden selbst. Siehe WatchProgressService.getInProgressSeriesCount.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ItemCountsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val watchProgressService: WatchProgressService = mockk()

    @Test
    fun `Serien-Destination zaehlt DISTINCT Serien und nutzt Serien-Label`() {
        coEvery { watchProgressService.getInProgressSeriesCount() } returns 2
        coEvery { watchProgressService.getInProgressMoviesCount() } returns 0

        var counts: Map<String, Int> = emptyMap()
        composeTestRule.setContent {
            counts = calculateItemCounts(HomeDestination.Series, watchProgressService)
        }
        composeTestRule.waitForIdle()

        assertEquals(2, counts[IN_PROGRESS_CATEGORY_ID])
        assertEquals("Serien", itemLabelForDestination(HomeDestination.Series))
    }

    @Test
    fun `VOD-Destination zaehlt Filme und nutzt Filme-Label`() {
        coEvery { watchProgressService.getInProgressMoviesCount() } returns 3
        coEvery { watchProgressService.getInProgressSeriesCount() } returns 0

        var counts: Map<String, Int> = emptyMap()
        composeTestRule.setContent {
            counts = calculateItemCounts(HomeDestination.VOD, watchProgressService)
        }
        composeTestRule.waitForIdle()

        assertEquals(3, counts[IN_PROGRESS_CATEGORY_ID])
        assertEquals("Filme", itemLabelForDestination(HomeDestination.VOD))
    }
}
