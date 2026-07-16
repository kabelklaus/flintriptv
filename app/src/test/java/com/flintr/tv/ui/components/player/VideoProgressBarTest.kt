package com.flintr.tv.ui.components.player

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flintr.tv.ui.theme.FlintRTVTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Render- und Seek-Verhalten der Video-Fortschrittsleiste als lokale Unit-Tests
 * (Robolectric), damit die Abdeckung in den Jacoco-Report einfließt, den Sonar konsumiert.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w1280dp-h720dp-land")
class VideoProgressBarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun rendertAlleFortschrittsebenen() {
        composeTestRule.setContent {
            FlintRTVTheme {
                VideoProgressBar(
                    progress = 0.25f,
                    onSeek = {},
                    bufferedProgress = 0.5f
                )
            }
        }

        // Hintergrund (1.0), Puffer (0.5) und Position (0.25) sind eigene Indikatoren
        composeTestRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(1f, 0f..1f)))
            .assertIsDisplayed()
        composeTestRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(0.5f, 0f..1f)))
            .assertIsDisplayed()
        composeTestRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(0.25f, 0f..1f)))
            .assertIsDisplayed()
    }

    @Test
    fun pufferwertWirdAufGueltigenBereichBegrenzt() {
        composeTestRule.setContent {
            FlintRTVTheme {
                VideoProgressBar(
                    progress = 0.1f,
                    onSeek = {},
                    bufferedProgress = 1.7f
                )
            }
        }

        assertEquals(
            2,
            composeTestRule.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo(1f, 0f..1f)))
                .fetchSemanticsNodes().size
        )
    }

    @Test
    fun geSteLiefertDragCallbacksMitRelativerPosition() {
        var dragStarted = false
        var lastDragProgress = -1f
        var dragEndProgress = -1f
        composeTestRule.setContent {
            FlintRTVTheme {
                VideoProgressBar(
                    progress = 0f,
                    onSeek = {},
                    dragCallbacks = DragCallbacks(
                        onDragStart = { dragStarted = true },
                        onDragProgress = { lastDragProgress = it },
                        onDragEnd = { dragEndProgress = it }
                    )
                )
            }
        }

        composeTestRule.onRoot().performTouchInput {
            click(Offset(centerX, 10f))
        }
        composeTestRule.mainClock.advanceTimeBy(1_000L)
        composeTestRule.waitForIdle()

        assertTrue("Drag sollte gestartet sein", dragStarted)
        assertTrue("Fortschritt sollte um 0.5 liegen, war $lastDragProgress", lastDragProgress in 0.4f..0.6f)
        assertTrue("Endposition sollte um 0.5 liegen, war $dragEndProgress", dragEndProgress in 0.4f..0.6f)
    }

    @Test
    fun draggingVergroessertDenScrubberOhneAbsturz() {
        composeTestRule.setContent {
            FlintRTVTheme {
                VideoProgressBar(
                    progress = 0.5f,
                    onSeek = {},
                    isDragging = true
                )
            }
        }

        composeTestRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(0.5f, 0f..1f)))
            .assertIsDisplayed()
    }
}
