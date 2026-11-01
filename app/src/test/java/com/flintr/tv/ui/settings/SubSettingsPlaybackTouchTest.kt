package com.flintr.tv.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flintr.tv.data.model.VideoResizeMode
import com.flintr.tv.ui.theme.FlintRTVTheme
import com.flintr.tv.ui.theme.appColorsForTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Beweist per echtem Touch-Gesture (performTouchInput { click() }, nicht performClick()/Semantics),
 * dass ResizeModeSelector sein Dropdown öffnet. Regression für den gemeldeten Bug, bei dem
 * das Dropdown per Fernbedienung (D-Pad/Semantics) ging, per Touch-Tap aber nicht aufging.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w1280dp-h720dp-land")
class SubSettingsPlaybackTouchTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val label = "Seitenverhältnis"
    private val selectedMode = VideoResizeMode.FIT
    private val onlyInDropdownText = VideoResizeMode.ASPECT_16_9.displayName

    private fun setSelector(onModeSelected: (VideoResizeMode) -> Unit = {}) {
        composeTestRule.setContent {
            FlintRTVTheme {
                ResizeModeSelector(
                    selectedMode = selectedMode,
                    onModeSelected = onModeSelected,
                    colors = appColorsForTest()
                )
            }
        }
    }

    @Test
    fun resizeSelectorOpensDropdownOnTouchTapOnTheLabelBox() {
        setSelector()

        composeTestRule.onAllNodes(hasClickAction() and hasText(label)).onFirst().performTouchInput { click() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(onlyInDropdownText).assertIsDisplayed()
    }

    @Test
    fun resizeSelectorOpensDropdownOnTouchTapOnTheReadOnlyTextField() {
        setSelector()

        composeTestRule.onAllNodes(hasClickAction() and hasText(selectedMode.displayName)).onFirst().performTouchInput { click() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(onlyInDropdownText).assertIsDisplayed()
    }

    @Test
    fun tapTogglesDropdownClosedAgain() {
        setSelector()

        composeTestRule.onAllNodes(hasClickAction() and hasText(selectedMode.displayName)).onFirst().performTouchInput { click() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(onlyInDropdownText).assertIsDisplayed()

        composeTestRule.onAllNodes(hasClickAction() and hasText(selectedMode.displayName)).onFirst().performTouchInput { click() }
        composeTestRule.waitForIdle()

        assertEquals(
            0,
            composeTestRule.onAllNodesWithText(onlyInDropdownText).fetchSemanticsNodes().size
        )
    }
}
