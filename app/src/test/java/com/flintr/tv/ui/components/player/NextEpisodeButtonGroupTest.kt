package com.flintr.tv.ui.components.player

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flintr.tv.ui.theme.FlintRTVTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Klickverhalten der Next-Episode-Buttons als lokale Unit-Tests (Robolectric),
 * damit die Abdeckung in den Jacoco-Report einfließt, den Sonar konsumiert.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w1280dp-h720dp-land")
class NextEpisodeButtonGroupTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setGroup(
        autoPlayProgress: Float = 0f,
        onWatchCredits: () -> Unit = {},
        onNextEpisode: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            FlintRTVTheme {
                NextEpisodeButtonGroup(
                    autoPlayProgress = autoPlayProgress,
                    onWatchCredits = onWatchCredits,
                    onNextEpisode = onNextEpisode
                )
            }
        }
    }

    @Test
    fun zeigtBeideButtons() {
        setGroup()

        composeTestRule.onNodeWithText("Watch Credits").assertIsDisplayed()
        composeTestRule.onNodeWithText("Next Episode").assertIsDisplayed()
    }

    @Test
    fun nextEpisodeKlickLoestCallbackAus() {
        var nextClicked = false
        var creditsClicked = false
        setGroup(onWatchCredits = { creditsClicked = true }, onNextEpisode = { nextClicked = true })

        composeTestRule.onNodeWithText("Next Episode").performClick()

        assertTrue(nextClicked)
        assertFalse(creditsClicked)
    }

    @Test
    fun watchCreditsKlickLoestCallbackAus() {
        var creditsClicked = false
        setGroup(onWatchCredits = { creditsClicked = true })

        composeTestRule.onNodeWithText("Watch Credits").performClick()

        assertTrue(creditsClicked)
    }

    @Test
    fun laufenderAutoPlayFortschrittZeigtButtonsWeiterhin()  {
        setGroup(autoPlayProgress = 0.8f)

        composeTestRule.mainClock.advanceTimeBy(1_000L)
        composeTestRule.onNodeWithText("Next Episode").assertIsDisplayed()
    }
}
