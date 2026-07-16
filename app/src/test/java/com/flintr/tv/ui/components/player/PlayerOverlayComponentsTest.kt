package com.flintr.tv.ui.components.player

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flintr.tv.ui.theme.FlintRTVTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Verhalten der kleinen Player-Overlay-Komponenten als lokale Unit-Tests (Robolectric),
 * damit die Abdeckung in den Jacoco-Report einfließt, den Sonar konsumiert.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w1280dp-h720dp-land")
class PlayerOverlayComponentsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // --- PlayerLoadingIndicator ---

    @Test
    fun ladeindikatorErscheintNurBeimBuffering() {
        composeTestRule.setContent {
            FlintRTVTheme {
                PlayerLoadingIndicator(isBuffering = true)
            }
        }

        composeTestRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
            .assertIsDisplayed()
    }

    @Test
    fun ladeindikatorFehltOhneBuffering() {
        composeTestRule.setContent {
            FlintRTVTheme {
                PlayerLoadingIndicator(isBuffering = false)
            }
        }

        composeTestRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
            .assertDoesNotExist()
    }

    // --- CentralPlayPauseButton ---

    @Test
    fun playPauseButtonZeigtPauseBeimAbspielen() {
        composeTestRule.setContent {
            FlintRTVTheme {
                CentralPlayPauseButton(isPlaying = true, onClick = {})
            }
        }

        composeTestRule.onNodeWithContentDescription("Pause").assertIsDisplayed()
    }

    @Test
    fun playPauseButtonZeigtPlayImPausierten() {
        composeTestRule.setContent {
            FlintRTVTheme {
                CentralPlayPauseButton(isPlaying = false, onClick = {})
            }
        }

        composeTestRule.onNodeWithContentDescription("Play").assertIsDisplayed()
    }

    @Test
    fun playPauseButtonKlickLoestCallbackAus() {
        var clicked = false
        composeTestRule.setContent {
            FlintRTVTheme {
                CentralPlayPauseButton(isPlaying = false, onClick = { clicked = true })
            }
        }

        composeTestRule.onNodeWithContentDescription("Play").performClick()

        assertTrue(clicked)
    }

    // --- PlayerErrorDialog ---

    @Test
    fun fehlerdialogZeigtTitelUndMeldung() {
        composeTestRule.setContent {
            FlintRTVTheme {
                PlayerErrorDialog(
                    errorMessage = "Stream nicht erreichbar",
                    onRetry = {},
                    onDismiss = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Wiedergabefehler").assertIsDisplayed()
        composeTestRule.onNodeWithText("Stream nicht erreichbar").assertIsDisplayed()
    }

    @Test
    fun fehlerdialogFehltOhneFehlermeldung() {
        composeTestRule.setContent {
            FlintRTVTheme {
                PlayerErrorDialog(errorMessage = null, onRetry = {}, onDismiss = {})
            }
        }

        composeTestRule.onNodeWithText("Wiedergabefehler").assertDoesNotExist()
    }

    @Test
    fun fehlerdialogButtonsLoesenCallbacksAus() {
        var retried = false
        var dismissed = false
        composeTestRule.setContent {
            FlintRTVTheme {
                PlayerErrorDialog(
                    errorMessage = "Fehler",
                    onRetry = { retried = true },
                    onDismiss = { dismissed = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Erneut versuchen").performClick()
        composeTestRule.onNodeWithText("Zurück").performClick()

        assertTrue(retried)
        assertTrue(dismissed)
    }
}
