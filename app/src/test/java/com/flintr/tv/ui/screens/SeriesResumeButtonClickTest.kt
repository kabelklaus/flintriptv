package com.flintr.tv.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
 * Klickverhalten des Weiterschauen-Buttons als lokale Unit-Tests (Robolectric),
 * damit die Abdeckung in den Jacoco-Report einfließt, den Sonar konsumiert.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w1280dp-h720dp-land")
class SeriesResumeButtonClickTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setButtonContent(
        state: ResumeButtonUiState,
        onClick: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            FlintRTVTheme {
                SeriesResumeButton(
                    state = state,
                    onClick = onClick
                )
            }
        }
    }

    @Test
    fun zeigtDasLabelAn() {
        setButtonContent(ResumeButtonUiState(label = "Weiterschauen: S2E5", progressPercent = 0f))

        composeTestRule.onNodeWithTag("series-resume-button").assertIsDisplayed()
        composeTestRule.onNodeWithText("Weiterschauen: S2E5").assertIsDisplayed()
    }

    @Test
    fun klickLoestCallbackAus() {
        var clicked = false
        setButtonContent(
            state = ResumeButtonUiState(label = "Abspielen: S1E1", progressPercent = 0f),
            onClick = { clicked = true }
        )

        composeTestRule.onNodeWithTag("series-resume-button").performClick()

        assertTrue(clicked)
    }

    @Test
    fun zeigtFortschrittsbalkenNurBeiVorhandenemFortschritt() {
        setButtonContent(ResumeButtonUiState(label = "Weiterschauen: S2E5", progressPercent = 0.5f))

        composeTestRule.onNodeWithTag("series-resume-button-progress", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun verstecktFortschrittsbalkenOhneFortschritt() {
        setButtonContent(ResumeButtonUiState(label = "Abspielen: S1E1", progressPercent = 0f))

        composeTestRule.onNodeWithTag("series-resume-button-progress", useUnmergedTree = true)
            .assertDoesNotExist()
    }
}
