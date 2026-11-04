package com.flintr.tv.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flintr.tv.data.model.DetailDisplayData
import com.flintr.tv.data.model.VodStream
import com.flintr.tv.ui.theme.FlintRTVTheme
import com.flintr.tv.viewmodel.MovieDetailUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w1280dp-h720dp-land")
class MovieDetailLoadingTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val catalogMovie = VodStream(
        num = 1,
        name = "DE - Dinosaurier (2000)",
        streamType = "movie",
        streamId = 2020743,
        streamIcon = "https://img/poster.jpg",
        rating = "7.1",
        rating5Based = null,
        added = null,
        categoryId = "1",
        categoryName = "Filme",
        containerExtension = "mkv",
        customSid = null,
        directSource = null,
        releaseDate = "2000-01-01",
        duration = "1h 22min",
        plot = null,
        cast = null,
        director = null,
        genre = "Xtream-Family",
        backdropPath = null,
        youtubeTrailer = null,
        cover = "https://img/cover.jpg",
        tmdbId = null
    )

    private fun detail() = DetailDisplayData(
        title = "DE - Dinosaurier (2000)",
        plot = "Ein Plot.",
        cast = emptyList(),
        director = null,
        runtime = "1h 22min",
        releaseDate = "2000-01-01",
        youtubeTrailer = null,
        backdropPath = null,
        posterUrl = null,
        tmdbId = null,
        genres = listOf("Animation"),
        rating = 7.8
    )

    private fun setContent(state: MovieDetailUiState) {
        composeTestRule.setContent {
            FlintRTVTheme {
                MovieDetailScreen(uiState = state)
            }
        }
    }

    @Test
    fun zeigtKatalogTitelUndLadeindikatorSofortWaehrendDesLadens() {
        setContent(MovieDetailUiState(isLoading = true, baseStream = catalogMovie))

        composeTestRule.onNodeWithTag("movie-detail-title")
            .assertIsDisplayed()
            .assertTextContains("DE - Dinosaurier (2000)")
        composeTestRule.onNodeWithTag("movie-detail-poster").assertExists()
        composeTestRule.onNodeWithTag("movie-detail-loading-indicator").assertIsDisplayed()
        composeTestRule.onNodeWithText("Details werden geladen…").assertExists()
        // Genre/Rating sind IMDb-exklusiv - im Katalog-Basisstand duerfen sie nicht erscheinen.
        composeTestRule.onNodeWithTag("movie-detail-rating").assertDoesNotExist()
        composeTestRule.onNodeWithTag("movie-detail-genres").assertDoesNotExist()
    }

    @Test
    fun zeigtNurSpinnerOhneKatalogDaten() {
        setContent(MovieDetailUiState(isLoading = true, baseStream = null))

        composeTestRule.onNodeWithTag("movie-detail-loading").assertExists()
        composeTestRule.onNodeWithTag("movie-detail-title").assertDoesNotExist()
    }

    @Test
    fun ladeTextVerschwindetSobaldDetailsFertigSind() {
        setContent(
            MovieDetailUiState(
                isLoading = false,
                baseStream = catalogMovie,
                detail = detail()
            )
        )

        composeTestRule.onNodeWithText("Details werden geladen…").assertDoesNotExist()
        composeTestRule.onNodeWithTag("movie-detail-watch").assertIsDisplayed()
    }
}
