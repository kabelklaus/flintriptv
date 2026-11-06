package com.flintr.tv.ui.components.player

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flintr.tv.BuildConfig
import com.flintr.tv.ui.theme.FlintRTVTheme
import com.flintr.tv.ui.utils.FocusedButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * T8: the Debug button is a debug-build-only control. The debug side is asserted through the
 * rendered semantics; the release side is asserted through the same focus order the control bar
 * uses, because a release APK cannot be assembled locally (keystore).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w1280dp-h720dp-land")
class CustomPlayerControlsDebugButtonTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun debugButtonIstImDebugBuildSichtbar() {
        assertTrue("Unit tests must run against the debug variant", BuildConfig.DEBUG)

        composeTestRule.setContent {
            FlintRTVTheme {
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
                            onSeek = {}
                        ),
                        utilityCallbacks = PlayerUtilityCallbacks(
                            onDebug = {},
                            onSettings = {},
                            onFocusChange = {}
                        ),
                        contentType = "movie",
                        focusedButton = null
                    )
                }
            }
        }

        composeTestRule.onNodeWithContentDescription("Debug").assertIsDisplayed()
    }

    @Test
    fun releaseFocusOrderEnthaeltKeinenDebugButton() {
        val order = playerControlFocusOrder(
            hasNextEpisode = false,
            showRatingButtons = true,
            isLive = false,
            debugEnabled = false
        )

        assertFalse(FocusedButton.DEBUG in order)
        assertEquals(FocusedButton.SETTINGS, order.last())
        // Settings' left neighbour is the last visible button before it, never Debug.
        assertEquals(FocusedButton.THUMBS_DOWN, order[order.lastIndex - 1])
    }

    @Test
    fun debugFocusOrderEnthaeltDebugButton() {
        val order = playerControlFocusOrder(
            hasNextEpisode = false,
            showRatingButtons = true,
            isLive = false,
            debugEnabled = true
        )

        assertTrue(FocusedButton.DEBUG in order)
        assertEquals(FocusedButton.SETTINGS, order[order.indexOf(FocusedButton.DEBUG) + 1])
    }

    @Test
    fun liveFocusOrderEnthaeltKeineSeekButtons() {
        val order = playerControlFocusOrder(
            hasNextEpisode = false,
            showRatingButtons = false,
            isLive = true
        )

        assertFalse(FocusedButton.PREVIOUS in order)
        assertFalse(FocusedButton.NEXT in order)
        assertEquals(FocusedButton.PLAY_PAUSE, order.first())
        assertEquals(FocusedButton.SETTINGS, order.last())
    }
}
