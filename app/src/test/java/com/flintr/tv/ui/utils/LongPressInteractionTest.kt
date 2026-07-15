package com.flintr.tv.ui.utils

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Long-Press-Verhalten (Fernbedienung: OK gedrückt halten / Touch: halten)
 * über die virtuelle Test-Uhr: Timer feuert nach durationMillis,
 * frühes Loslassen bricht ab und löst den normalen Click aus.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class LongPressInteractionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val tag = "target"
    private val longPressDuration = 300L

    private var longPresses = 0
    private var normalClicks = 0

    private fun setLongPressTarget() {
        longPresses = 0
        normalClicks = 0
        val focusRequester = FocusRequester()
        composeTestRule.setContent {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .testTag(tag)
                    .focusRequester(focusRequester)
                    .onLongPress(
                        onLongPress = { longPresses++ },
                        onNormalClick = { normalClicks++ },
                        durationMillis = longPressDuration
                    )
                    .focusable()
            )
        }
        composeTestRule.runOnIdle {
            focusRequester.requestFocus()
        }
    }

    @Test
    fun keyHeldLongerThanDurationFiresLongPressAndSuppressesClick() {
        setLongPressTarget()

        composeTestRule.onNodeWithTag(tag).performKeyInput { keyDown(Key.DirectionCenter) }
        composeTestRule.mainClock.advanceTimeBy(longPressDuration + 100)
        composeTestRule.onNodeWithTag(tag).performKeyInput { keyUp(Key.DirectionCenter) }

        composeTestRule.runOnIdle {
            assertEquals(1, longPresses)
            assertEquals(0, normalClicks)
        }
    }

    @Test
    fun keyReleasedEarlyCancelsTimerAndFiresNormalClick() {
        setLongPressTarget()

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.onNodeWithTag(tag).performKeyInput { keyDown(Key.Enter) }
        composeTestRule.onNodeWithTag(tag).performKeyInput { keyUp(Key.Enter) }
        composeTestRule.mainClock.autoAdvance = true

        composeTestRule.runOnIdle {
            assertEquals(0, longPresses)
            assertEquals(1, normalClicks)
        }
    }

    @Test
    fun touchHeldLongerThanDurationFiresLongPressAndSuppressesClick() {
        setLongPressTarget()

        composeTestRule.onNodeWithTag(tag).performTouchInput { down(center) }
        composeTestRule.mainClock.advanceTimeBy(longPressDuration + 100)
        composeTestRule.onNodeWithTag(tag).performTouchInput { up() }

        composeTestRule.runOnIdle {
            assertEquals(1, longPresses)
            assertEquals(0, normalClicks)
        }
    }

    @Test
    fun touchShortTapFiresNormalClick() {
        setLongPressTarget()

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.onNodeWithTag(tag).performClick()
        composeTestRule.mainClock.autoAdvance = true

        composeTestRule.runOnIdle {
            assertEquals(0, longPresses)
            assertEquals(1, normalClicks)
        }
    }
}
