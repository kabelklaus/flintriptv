package com.flintr.tv.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flintr.tv.ui.theme.FlintRTVTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Sichtbarkeit des Fortschritts-Overlays auf dem Episoden-Thumbnail als lokale
 * Unit-Tests (Robolectric), damit die Abdeckung in den Jacoco-Report einfließt.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w1280dp-h720dp-land")
class EpisodeThumbnailProgressTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setThumbnailContent(
        progressPercent: Float,
        remainingMinutes: Int? = null,
        showWatchedBadge: Boolean = false
    ) {
        composeTestRule.setContent {
            FlintRTVTheme {
                EpisodeThumbnail(
                    imageCandidates = emptyList(),
                    showWatchedBadge = showWatchedBadge,
                    progressPercent = progressPercent,
                    remainingMinutes = remainingMinutes
                )
            }
        }
    }

    @Test
    fun zeigtFortschrittsOverlayBeiLaufenderEpisode() {
        setThumbnailContent(progressPercent = 0.5f)

        composeTestRule.onNodeWithTag("episode-thumbnail-progress", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun verstecktOverlayOhneFortschritt() {
        setThumbnailContent(progressPercent = 0f)

        composeTestRule.onNodeWithTag("episode-thumbnail-progress", useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun zeigtRestminutenChipWennVorhanden() {
        setThumbnailContent(progressPercent = 0.5f, remainingMinutes = 23)

        composeTestRule.onNodeWithTag("episode-thumbnail-remaining", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun verstecktRestminutenChipOhneWert() {
        setThumbnailContent(progressPercent = 0.5f, remainingMinutes = null)

        composeTestRule.onNodeWithTag("episode-thumbnail-remaining", useUnmergedTree = true)
            .assertDoesNotExist()
    }
}
