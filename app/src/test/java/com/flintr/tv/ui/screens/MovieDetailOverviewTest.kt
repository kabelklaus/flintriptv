package com.flintr.tv.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flintr.tv.data.model.DetailDisplayData
import com.flintr.tv.data.model.WatchProgress
import com.flintr.tv.ui.theme.FlintRTVTheme
import com.flintr.tv.viewmodel.MovieDetailUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w1280dp-h720dp-land")
class MovieDetailOverviewTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun detail(plot: String? = "Ein spannender Film über einen Test.") = DetailDisplayData(
        title = "Der Film",
        plot = plot,
        cast = emptyList(),
        director = null,
        runtime = "2h 0min",
        releaseDate = "2020-05-01",
        youtubeTrailer = null,
        backdropPath = null,
        posterUrl = null,
        tmdbId = null,
        genres = listOf("Action"),
        rating = 8.4
    )

    private fun setContent(state: MovieDetailUiState) {
        composeTestRule.setContent {
            FlintRTVTheme {
                MovieDetailScreen(uiState = state)
            }
        }
        composeTestRule.mainClock.advanceTimeBy(500L)
        composeTestRule.waitForIdle()
    }

    @Test
    fun zeigtOverviewTextAusPlot() {
        setContent(MovieDetailUiState(isLoading = false, detail = detail()))

        composeTestRule.onNodeWithTag("movie-detail-overview")
            .assertIsDisplayed()
            .assertTextContains("Ein spannender Film", substring = true)
    }

    @Test
    fun zeigtFortschrittsbalkenImWatchButtonBeiAngebrochenemFilm() {
        setContent(
            MovieDetailUiState(
                isLoading = false,
                detail = detail(),
                watchProgress = WatchProgress(
                    position = 3_600_000L,
                    duration = 7_200_000L,
                    lastWatched = "2026-10-30T20:00:00Z",
                    title = "Der Film"
                )
            )
        )

        composeTestRule.onNodeWithTag("movie-detail-watch-progress", useUnmergedTree = true)
            .assertExists()
        composeTestRule.onNodeWithTag("movie-detail-watch-progress-fill", useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun blendetFortschrittsbalkenOhneWatchProgressAus() {
        setContent(MovieDetailUiState(isLoading = false, detail = detail()))

        composeTestRule.onNodeWithTag("movie-detail-watch-progress", useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun mehrToggleErweitertLangenPlot() {
        val longPlot = "Am Anfang. " + "Sehr langer Text. ".repeat(40)
        setContent(MovieDetailUiState(isLoading = false, detail = detail(plot = longPlot)))

        composeTestRule.onNodeWithTag("movie-detail-overview-toggle").performClick()

        composeTestRule.onNodeWithTag("movie-detail-overview-toggle")
            .assertTextContains("Weniger anzeigen", substring = true)
    }
}
