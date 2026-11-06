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
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Klick- und D-Pad-Verhalten der zentralen Interaktions-Modifier
 * (onClick, onDpadDirection, onDpadCenter, onBackButton).
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class InteractionUtilsClickTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val tag = "target"

    private fun setFocusedTarget(modifier: Modifier): FocusRequester {
        val focusRequester = FocusRequester()
        composeTestRule.setContent {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .testTag(tag)
                    .focusRequester(focusRequester)
                    .then(modifier)
            )
        }
        composeTestRule.runOnIdle {
            focusRequester.requestFocus()
        }
        return focusRequester
    }

    @Test
    fun onClickFiresOnTouchClick() {
        var clicks = 0
        setFocusedTarget(Modifier.onClick(onClick = { clicks++ }))

        composeTestRule.onNodeWithTag(tag).performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun onClickFiresOnDpadCenterAndEnter() {
        var clicks = 0
        setFocusedTarget(Modifier.onClick(onClick = { clicks++ }))

        composeTestRule.onNodeWithTag(tag).performKeyInput {
            pressKey(Key.DirectionCenter)
            pressKey(Key.Enter)
        }

        assertEquals(2, clicks)
    }

    @Test
    fun onClickDisabledIgnoresAllInput() {
        var clicks = 0
        setFocusedTarget(
            Modifier
                .onClick(onClick = { clicks++ }, enabled = false)
                .focusable()
        )

        composeTestRule.onNodeWithTag(tag).performClick()

        assertEquals(0, clicks)
    }

    @Test
    fun onDpadDirectionDispatchesToMatchingHandler() {
        val pressed = mutableListOf<String>()
        setFocusedTarget(
            Modifier
                .focusable()
                .onDpadDirection(
                    onLeft = { pressed.add("left"); true },
                    onRight = { pressed.add("right"); true },
                    onUp = { pressed.add("up"); true },
                    onDown = { pressed.add("down"); true }
                )
        )

        composeTestRule.onNodeWithTag(tag).performKeyInput {
            pressKey(Key.DirectionRight)
            pressKey(Key.DirectionLeft)
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionUp)
        }

        assertEquals(listOf("right", "left", "down", "up"), pressed)
    }

    @Test
    fun onDpadDirectionWithoutHandlerDoesNotIntercept() {
        var rightPresses = 0
        // Nur onRight registriert: Left/Up/Down dürfen nicht abgefangen werden
        setFocusedTarget(
            Modifier
                .focusable()
                .onDpadDirection(onRight = { rightPresses++; true })
        )

        composeTestRule.onNodeWithTag(tag).performKeyInput {
            pressKey(Key.DirectionLeft)
            pressKey(Key.DirectionUp)
            pressKey(Key.DirectionRight)
        }

        assertEquals(1, rightPresses)
    }

    @Test
    fun onDpadCenterIgnoresOtherKeys() {
        var presses = 0
        setFocusedTarget(
            Modifier
                .onDpadCenter(onPress = { presses++ })
                .focusable()
        )

        composeTestRule.onNodeWithTag(tag).performKeyInput {
            pressKey(Key.DirectionRight)
            pressKey(Key.A)
            pressKey(Key.DirectionCenter)
        }

        assertEquals(1, presses)
    }

    @Test
    fun onBackButtonFiresOnlyOnBackKey() {
        var backPresses = 0
        setFocusedTarget(
            Modifier
                .onBackButton(onBack = { backPresses++ })
                .focusable()
        )

        composeTestRule.onNodeWithTag(tag).performKeyInput {
            pressKey(Key.DirectionCenter)
            pressKey(Key.Back)
        }

        assertEquals(1, backPresses)
    }

    @Test
    fun getKeyNameMapsKnownAndUnknownKeys() {
        assertEquals("DirectionCenter", InteractionUtils.getKeyName(Key.DirectionCenter))
        assertEquals("Back", InteractionUtils.getKeyName(Key.Back))
        assertEquals("VolumeUp", InteractionUtils.getKeyName(Key.VolumeUp))
        assertEquals("Unknown(${Key.Zero})", InteractionUtils.getKeyName(Key.Zero))
    }
}
