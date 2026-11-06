package com.flintr.tv.ui.components

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flintr.tv.data.model.RatingEntry
import com.flintr.tv.ui.theme.FlintRTVTheme
import com.flintr.tv.ui.theme.appColors
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Verhalten von WatchedBadge und InProgressIndicator als lokale Unit-Tests (Robolectric),
 * damit die Abdeckung in den Jacoco-Report einfließt, den Sonar konsumiert.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w1280dp-h720dp-land")
class BadgeAndIndicatorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun watchedBadgeZeigtGeschautIcon() {
        composeTestRule.setContent {
            FlintRTVTheme {
                WatchedBadge()
            }
        }

        composeTestRule.onNodeWithContentDescription("Geschaut").assertIsDisplayed()
    }

    private fun ratingEntry(rating: Int) = RatingEntry(
        contentType = "movie",
        contentId = 123,
        title = "Some Movie",
        rating = rating,
        ratedAt = "2026-09-13T10:00:00Z"
    )

    private fun setRatingUpBadge(rating: RatingEntry?) {
        composeTestRule.setContent {
            FlintRTVTheme {
                RatingUpBadge(rating = rating)
            }
        }
    }

    @Test
    fun ratingUpBadgeZeigtIconBeiRatingEins() {
        setRatingUpBadge(ratingEntry(rating = 1))

        composeTestRule.onNodeWithContentDescription("Gefällt dir").assertIsDisplayed()
    }

    @Test
    fun ratingUpBadgeFehltBeiRatingNull() {
        setRatingUpBadge(ratingEntry(rating = 0))

        composeTestRule.onNodeWithContentDescription("Gefällt dir").assertDoesNotExist()
    }

    @Test
    fun ratingUpBadgeFehltOhneRating() {
        setRatingUpBadge(rating = null)

        composeTestRule.onNodeWithContentDescription("Gefällt dir").assertDoesNotExist()
    }

    private fun setIndicator(progressPercent: Float, remainingMinutes: Int?) {
        composeTestRule.setContent {
            FlintRTVTheme {
                InProgressIndicator(
                    progressPercent = progressPercent,
                    remainingMinutes = remainingMinutes,
                    colors = appColors()
                )
            }
        }
    }

    @Test
    fun indikatorZeigtFortschrittUndRestzeit() {
        setIndicator(progressPercent = 0.5f, remainingMinutes = 25)

        composeTestRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(0.5f, 0f..1f)))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("25m verbleibend").assertIsDisplayed()
    }

    @Test
    fun indikatorOhneRestzeitZeigtNurBalken() {
        setIndicator(progressPercent = 0.3f, remainingMinutes = null)

        composeTestRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(0.3f, 0f..1f)))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("verbleibend", substring = true).assertDoesNotExist()
    }

    @Test
    fun indikatorFehltOhneFortschritt() {
        setIndicator(progressPercent = 0f, remainingMinutes = 10)

        composeTestRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(0f, 0f..1f)))
            .assertDoesNotExist()
        composeTestRule.onNodeWithText("verbleibend", substring = true).assertDoesNotExist()
    }
}
