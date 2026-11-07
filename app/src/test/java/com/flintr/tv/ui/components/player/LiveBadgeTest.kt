package com.flintr.tv.ui.components.player

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flintr.tv.ui.theme.FlintRTVTheme
import com.flintr.tv.ui.theme.SharedColors
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w1280dp-h720dp-land")
class LiveBadgeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun pausierterLiveStreamNutztGrauenPunkt() {
        assertEquals(Color.Gray, liveBadgeDotColor(isPlaying = false))
    }

    @Test
    fun laufenderLiveStreamNutztRotenPunkt() {
        assertEquals(SharedColors.red, liveBadgeDotColor(isPlaying = true))
    }

    @Test
    fun badgeIstMitTestTagGerendert() {
        composeTestRule.setContent {
            FlintRTVTheme {
                LiveBadge(isPlaying = false)
            }
        }

        composeTestRule.onNodeWithTag("player-live-badge").assertIsDisplayed()
    }
}
