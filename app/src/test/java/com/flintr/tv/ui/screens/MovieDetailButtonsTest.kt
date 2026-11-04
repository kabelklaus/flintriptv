package com.flintr.tv.ui.screens

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flintr.tv.data.model.DetailDisplayData
import com.flintr.tv.ui.theme.FlintRTVTheme
import com.flintr.tv.viewmodel.MovieDetailUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w1280dp-h720dp-land")
class MovieDetailButtonsTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun detail(trailer: String? = "abc123") = DetailDisplayData(
        title = "Der Film",
        plot = "Ein Plot.",
        cast = emptyList(),
        director = null,
        runtime = "2h 0min",
        releaseDate = "2020-05-01",
        youtubeTrailer = trailer,
        backdropPath = null,
        posterUrl = null,
        tmdbId = null,
        genres = listOf("Action"),
        rating = 8.4
    )

    private fun setContent(
        isFavorite: Boolean = false,
        onWatchClick: () -> Unit = {},
        onToggleFavorite: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            FlintRTVTheme {
                MovieDetailScreen(
                    uiState = MovieDetailUiState(
                        isLoading = false,
                        detail = detail(),
                        isFavorite = isFavorite
                    ),
                    onWatchClick = onWatchClick,
                    onToggleFavorite = onToggleFavorite
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1000L)
        composeTestRule.waitForIdle()
    }

    @Test
    fun ansehenKlickRuftCallbackAuf() {
        var clicks = 0
        setContent(onWatchClick = { clicks++ })

        composeTestRule.onNodeWithTag("movie-detail-watch").performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun favoritKlickRuftToggleCallbackAuf() {
        var toggles = 0
        setContent(onToggleFavorite = { toggles++ })

        composeTestRule.onNodeWithTag("movie-detail-favorite").performClick()

        assertEquals(1, toggles)
    }

    @Test
    fun favoritLabelZeigtAktivenZustand() {
        setContent(isFavorite = true)

        composeTestRule.onNodeWithTag("movie-detail-favorite")
            .assertContentDescriptionEquals("Favorit")
    }

    @Test
    fun trailerKlickFeuertYouTubeIntentMitVideoId() {
        setContent()

        composeTestRule.onNodeWithTag("movie-detail-trailer").performClick()

        val intent = shadowOf(composeTestRule.activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("https://www.youtube.com/watch?v=abc123", intent.data.toString())
    }

    @Test
    fun ansehenHatInitialenFokus() {
        setContent()

        composeTestRule.onNodeWithTag("movie-detail-watch").assertIsFocused()
    }
}
