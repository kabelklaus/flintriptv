package com.flintr.tv.ui.components.player

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flintr.tv.ui.theme.FlintRTVTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w1280dp-h720dp-land")
class SoftRatingOverlayTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setOverlay(
        onThumbsUp: () -> Unit = {},
        onThumbsDown: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            FlintRTVTheme {
                SoftRatingOverlay(onThumbsUp = onThumbsUp, onThumbsDown = onThumbsDown)
            }
        }
    }

    @Test
    fun zeigtBeideButtons() {
        setOverlay()

        composeTestRule.onNodeWithContentDescription("Gefällt mir").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Gefällt mir nicht").assertIsDisplayed()
    }

    @Test
    fun klickAufDaumenHochLoestCallbackAus() {
        var clicked = false
        setOverlay(onThumbsUp = { clicked = true })

        composeTestRule.onNodeWithContentDescription("Gefällt mir").performClick()

        assertTrue(clicked)
    }

    @Test
    fun klickAufDaumenRunterLoestCallbackAus() {
        var clicked = false
        setOverlay(onThumbsDown = { clicked = true })

        composeTestRule.onNodeWithContentDescription("Gefällt mir nicht").performClick()

        assertTrue(clicked)
    }
}
