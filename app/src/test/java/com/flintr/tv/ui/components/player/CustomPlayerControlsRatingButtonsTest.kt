package com.flintr.tv.ui.components.player

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flintr.tv.ui.theme.FlintRTVTheme
import com.flintr.tv.ui.utils.FocusedButton
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Verifies Task F1's thumbs up/down control-bar buttons: they render, trigger direct
 * rating callbacks without a dialog, and sit between "10 Sekunden vor" and "Debug" in
 * the D-Pad focus loop.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w1280dp-h720dp-land")
class CustomPlayerControlsRatingButtonsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setControls(
        initialFocusedButton: FocusedButton? = null,
        ratedThumb: Int? = null,
        onThumbsUp: () -> Unit = {},
        onThumbsDown: () -> Unit = {},
        showRatingButtons: Boolean = true
    ) {
        composeTestRule.setContent {
            FlintRTVTheme {
                var focusedButton by remember { mutableStateOf(initialFocusedButton) }
                Box {
                    CustomPlayerControls(
                        state = PlayerState(
                            isPlaying = true,
                            currentPosition = 0L,
                            duration = 100_000L,
                            progress = 0f
                        ),
                        callbacks = PlayerCallbacks(
                            onPrevious = {},
                            onPlayPause = {},
                            onNext = {},
                            onSeek = {},
                            onThumbsUp = onThumbsUp,
                            onThumbsDown = onThumbsDown
                        ),
                        utilityCallbacks = PlayerUtilityCallbacks(
                            onDebug = {},
                            onSettings = {},
                            onFocusChange = { focusedButton = it }
                        ),
                        contentType = "movie",
                        focusedButton = focusedButton,
                        ratingUiState = RatingUiState(
                            ratedThumb = ratedThumb,
                            showRatingButtons = showRatingButtons
                        )
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun zeigtThumbsUpUndDownButtons() {
        setControls()

        composeTestRule.onNodeWithContentDescription("Gefällt mir").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Gefällt mir nicht").assertIsDisplayed()
    }

    @Test
    fun klickAufThumbsUpLoestDirektesRatingOhneDialogAus() {
        var clicked = false
        setControls(onThumbsUp = { clicked = true })

        composeTestRule.onNodeWithContentDescription("Gefällt mir").performClick()

        assertTrue(clicked)
    }

    @Test
    fun klickAufThumbsDownLoestDirektesRatingOhneDialogAus() {
        var clicked = false
        setControls(onThumbsDown = { clicked = true })

        composeTestRule.onNodeWithContentDescription("Gefällt mir nicht").performClick()

        assertTrue(clicked)
    }

    @Test
    fun fokusLoopVonForward10ZuThumbsZuDebug() {
        setControls(initialFocusedButton = FocusedButton.NEXT)

        composeTestRule.onNodeWithContentDescription("10 Sekunden vor")
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeTestRule.onNodeWithContentDescription("Gefällt mir").assertIsFocused()

        composeTestRule.onNodeWithContentDescription("Gefällt mir")
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeTestRule.onNodeWithContentDescription("Gefällt mir nicht").assertIsFocused()

        composeTestRule.onNodeWithContentDescription("Gefällt mir nicht")
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeTestRule.onNodeWithContentDescription("Debug").assertIsFocused()

        composeTestRule.onNodeWithContentDescription("Debug")
            .performKeyInput { pressKey(Key.DirectionLeft) }
        composeTestRule.onNodeWithContentDescription("Gefällt mir nicht").assertIsFocused()
    }

    @Test
    fun beiLiveWerdenThumbsAusgeblendetUndFokusLoopSpringtDirektZuDebug() {
        setControls(initialFocusedButton = FocusedButton.NEXT, showRatingButtons = false)

        composeTestRule.onNodeWithContentDescription("Gefällt mir").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Gefällt mir nicht").assertDoesNotExist()

        composeTestRule.onNodeWithContentDescription("10 Sekunden vor")
            .performKeyInput { pressKey(Key.DirectionRight) }
        composeTestRule.onNodeWithContentDescription("Debug").assertIsFocused()

        composeTestRule.onNodeWithContentDescription("Debug")
            .performKeyInput { pressKey(Key.DirectionLeft) }
        composeTestRule.onNodeWithContentDescription("10 Sekunden vor").assertIsFocused()
    }
}
