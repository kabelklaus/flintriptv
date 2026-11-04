package com.flintr.tv.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flintr.tv.data.model.testEpisode
import com.flintr.tv.data.model.testSeason
import com.flintr.tv.ui.theme.FlintRTVTheme
import com.flintr.tv.viewmodel.NextEpisodeInfo
import com.flintr.tv.viewmodel.NextEpisodeState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Task 14: the next-episode overlay is deliberately a small, discreet bottom-right group - not a
 * dialog, not a full-screen scrim - with "Next Episode" (play) and "Watch Credits" (dismiss the
 * countdown) actions.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w1280dp-h720dp-land")
class NextEpisodeOverlayTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val season = testSeason(
        seasonNumber = 1,
        episodes = listOf(
            testEpisode(id = 101, title = "Anfang", episodeNum = 1, season = 1),
            testEpisode(id = 102, title = "Weiter", episodeNum = 2, season = 1)
        )
    )

    private val info = NextEpisodeInfo(
        episode = season.episodes!!.last(),
        season = season,
        displayTitle = "S1E2 - Weiter"
    )

    private fun setOverlay(
        state: NextEpisodeState = NextEpisodeState.Visible(info, 0.4f),
        onWatchCredits: () -> Unit = {},
        onNextEpisode: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            FlintRTVTheme {
                NextEpisodeOverlay(
                    state = state,
                    onWatchCredits = onWatchCredits,
                    onNextEpisode = onNextEpisode
                )
            }
        }
    }

    @Test
    fun sichtbarerStateZeigtDekentesOverlayMitBeidenAktionen() {
        setOverlay()

        composeTestRule.onNodeWithTag("next-episode-overlay").assertIsDisplayed()
        composeTestRule.onNodeWithText("Next Episode").assertIsDisplayed()
        composeTestRule.onNodeWithText("Watch Credits").assertIsDisplayed()
    }

    @Test
    fun overlayIstNichtBildschirmfuellend() {
        setOverlay()

        // 1280dp viewport, overlay group ~368dp wide: playback stays visible, no scrim.
        val bounds = composeTestRule.onNodeWithTag("next-episode-overlay")
            .getUnclippedBoundsInRoot()
        assertTrue(bounds.right - bounds.left < 400.dp)
    }

    @Test
    fun nextEpisodeKlickSpieltWeiter() {
        var next = false
        var dismissed = false
        setOverlay(
            onWatchCredits = { dismissed = true },
            onNextEpisode = { next = true }
        )

        composeTestRule.onNodeWithText("Next Episode").performClick()

        assertTrue(next)
        assertFalse(dismissed)
    }

    @Test
    fun watchCreditsVerwirftDenCountdown() {
        var next = false
        var dismissed = false
        setOverlay(
            onWatchCredits = { dismissed = true },
            onNextEpisode = { next = true }
        )

        composeTestRule.onNodeWithText("Watch Credits").performClick()

        assertTrue(dismissed)
        assertFalse(next)
    }

    @Test
    fun hiddenStateRendertNichts() {
        setOverlay(state = NextEpisodeState.Hidden)

        composeTestRule.onNodeWithTag("next-episode-overlay").assertDoesNotExist()
        composeTestRule.onNodeWithText("Next Episode").assertDoesNotExist()
    }
}
