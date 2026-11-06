package com.flintr.tv.ui.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.input.key.Key
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flintr.tv.data.model.RatingEntry
import com.flintr.tv.ui.components.player.RatingDialog
import com.flintr.tv.ui.theme.FlintRTVTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w1280dp-h720dp-land")
class RatingDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setDialog(
        title: String = "Some Movie",
        onRated: (RatingEntry) -> Unit = {},
        onDismiss: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            FlintRTVTheme {
                RatingDialog(
                    title = title,
                    onRated = onRated,
                    onDismiss = onDismiss
                )
            }
        }
        // safeRequestFocus delays 50ms before its first attempt (and retries 50ms apart) to give
        // the dialog's FocusRequester node time to attach - advance the clock past that, matching
        // the pattern used by other safeRequestFocus-driven tests (e.g. CategoryRowFocusTest).
        composeTestRule.mainClock.advanceTimeBy(500L)
        composeTestRule.waitForIdle()
    }

    @Test
    fun zeigtTitelUndBeideButtons() {
        setDialog(title = "Some Movie")

        composeTestRule.onNodeWithText("Some Movie", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Daumen hoch").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Daumen runter").assertIsDisplayed()
    }

    @Test
    fun istUeberTestTagAlsZentrierterDialogAuffindbar() {
        setDialog(title = "Some Movie")

        composeTestRule.onNodeWithTag("rating-dialog").assertIsDisplayed()
    }

    @Test
    fun initialerFokusLiegtAufDaumenHoch() {
        setDialog()

        composeTestRule.onNodeWithContentDescription("Daumen hoch").assertIsFocused()
    }

    @Test
    fun klickAufDaumenHochLoestOnRatedMitRatingEinsAus() {
        var rated: RatingEntry? = null
        setDialog(title = "Some Movie", onRated = { rated = it })

        composeTestRule.onNodeWithContentDescription("Daumen hoch").performClick()

        assertEquals(1, rated?.rating)
        assertEquals("Some Movie", rated?.title)
    }

    @Test
    fun klickAufDaumenRunterLoestOnRatedMitRatingNullAus() {
        var rated: RatingEntry? = null
        setDialog(title = "Some Movie", onRated = { rated = it })

        composeTestRule.onNodeWithContentDescription("Daumen runter").performClick()

        assertEquals(0, rated?.rating)
    }

    @Test
    fun backSchliesstDialogOhneRating() {
        var rated: RatingEntry? = null
        var dismissed = false
        setDialog(onRated = { rated = it }, onDismiss = { dismissed = true })

        composeTestRule.onNodeWithContentDescription("Daumen hoch")
            .performKeyInput { pressKey(Key.Back) }

        assertTrue(dismissed)
        assertNull(rated)
    }
}
