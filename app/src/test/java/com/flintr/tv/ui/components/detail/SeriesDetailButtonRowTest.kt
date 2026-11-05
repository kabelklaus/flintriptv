package com.flintr.tv.ui.components.detail

import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flintr.tv.ui.theme.FlintRTVTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Guards the detail button row at its original content-width size: one row, Watch/Trailer/
 * Watchlist at natural size, Watchlist always rendered, and the in-button progress indicator
 * kept minimal (not tied to the button's full width).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w1280dp-h720dp-land")
class SeriesDetailButtonRowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun header(
        watchLabel: String = "S3 E1 fortsetzen",
        trailerAvailable: Boolean = true,
        watchProgress: Float? = null
    ) {
        composeTestRule.setContent {
            val watchFocus = remember { FocusRequester() }
            val trailerFocus = remember { FocusRequester() }
            val watchlistFocus = remember { FocusRequester() }
            FlintRTVTheme {
                DetailHeaderSection(
                    data = DetailHeaderData(
                        posterUrl = null,
                        title = "Mayor of Kingstown",
                        rating = 8.1,
                        meta = "2021",
                        genres = listOf("Drama"),
                        overview = null,
                        isInWatchlist = false,
                        trailerAvailable = trailerAvailable,
                        watchLabel = watchLabel,
                        watchProgress = watchProgress,
                        onWatch = {},
                        onTrailer = {},
                        onToggleWatchlist = {}
                    ),
                    testTagPrefix = "detail",
                    watchFocusRequester = watchFocus,
                    trailerFocusRequester = trailerFocus,
                    watchlistFocusRequester = watchlistFocus
                )
            }
        }
    }

    @Test
    fun tvBreite_bleibtEinzeiligContentBreitMitTrailerUndWatchlist() {
        val watchLabel = "S3 E1 fortsetzen"
        header(watchLabel = watchLabel)

        val watchBounds = composeTestRule.onNodeWithTag("detail-watch").getUnclippedBoundsInRoot()
        val trailerBounds = composeTestRule.onNodeWithTag("detail-trailer").getUnclippedBoundsInRoot()
        val watchlistBounds = composeTestRule.onNodeWithTag("detail-favorite").getUnclippedBoundsInRoot()

        assertTrue(
            "Watch/Trailer sind nicht mehr in einer Reihe: watchTop=${watchBounds.top} trailerTop=${trailerBounds.top}",
            watchBounds.top == trailerBounds.top
        )
        assertTrue(
            "Trailer/Favorit sind nicht mehr in einer Reihe: trailerTop=${trailerBounds.top} favoriteTop=${watchlistBounds.top}",
            trailerBounds.top == watchlistBounds.top
        )
        assertTrue(
            "Ansehen steht rechts von Trailer statt links davon: watchLeft=${watchBounds.left} trailerLeft=${trailerBounds.left}",
            watchBounds.left < trailerBounds.left
        )

        val watchWidth = watchBounds.right - watchBounds.left
        assertTrue(
            "Watch-Button ist auf Vollbreite gewachsen statt content-breit zu bleiben: $watchWidth",
            watchWidth < 400.dp
        )

        composeTestRule.onNodeWithText(watchLabel).assertExists()
    }

    @Test
    fun ansehenLabel_istSichtbarUndNichtLeer() {
        val watchLabel = "S1 E1 ansehen"
        header(watchLabel = watchLabel)

        composeTestRule.onNodeWithText(watchLabel).assertExists()
    }

    @Test
    fun watchlistButton_wirdAuchOhneTrailerImmerGerendert() {
        header(trailerAvailable = false)

        composeTestRule.onNodeWithTag("detail-favorite").assertExists()
        composeTestRule.onNodeWithTag("detail-trailer").assertDoesNotExist()
    }

    @Test
    fun fortschrittsbalken_istVorhandenUndAnnaeherndButtonbreit() {
        header(watchProgress = 42f)

        val watchBounds = composeTestRule.onNodeWithTag("detail-watch").getUnclippedBoundsInRoot()
        val barBounds = composeTestRule.onNodeWithTag("detail-watch-progress", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val watchWidth = watchBounds.right - watchBounds.left
        val barWidth = barBounds.right - barBounds.left
        val barHeight = barBounds.bottom - barBounds.top

        assertTrue(
            "Fortschrittsbalken soll ungefähr Button-Innenbreite haben (Button=$watchWidth, Balken=$barWidth)",
            barWidth in (watchWidth - 30.dp)..(watchWidth - 18.dp)
        )
        assertTrue(
            "Fortschrittsbalken ist nicht schlank genug: $barHeight",
            barHeight in 2.dp..3.dp
        )
    }
}
