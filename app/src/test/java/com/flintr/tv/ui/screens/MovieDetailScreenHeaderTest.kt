package com.flintr.tv.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flintr.tv.data.model.DetailDisplayData
import com.flintr.tv.ui.theme.FlintRTVTheme
import com.flintr.tv.viewmodel.MovieDetailUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w1280dp-h720dp-land")
class MovieDetailScreenHeaderTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun detail(
        genres: List<String>? = listOf("Action", "Drama"),
        rating: Double? = 8.4
    ) = DetailDisplayData(
        title = "Der Film",
        plot = "Ein Plot.",
        cast = emptyList(),
        director = null,
        runtime = "2h 0min",
        releaseDate = "2020-05-01",
        youtubeTrailer = null,
        backdropPath = null,
        posterUrl = null,
        tmdbId = null,
        genres = genres,
        rating = rating
    )

    private fun setContent(state: MovieDetailUiState) {
        composeTestRule.setContent {
            FlintRTVTheme {
                MovieDetailScreen(uiState = state)
            }
        }
    }

    @Test
    fun zeigtTitelMetaUndGenresAusDetailState() {
        setContent(MovieDetailUiState(isLoading = false, detail = detail()))

        composeTestRule.onNodeWithTag("movie-detail-title")
            .assertIsDisplayed()
            .assertTextContains("Der Film")
        composeTestRule.onNodeWithTag("movie-detail-meta")
            .assertIsDisplayed()
            .assertTextContains("2h 0min", substring = true)
        composeTestRule.onNodeWithTag("movie-detail-genres")
            .assertIsDisplayed()
            .assertTextContains("Action", substring = true)
    }

    @Test
    fun zeigtImdbRatingWennVorhanden() {
        setContent(MovieDetailUiState(isLoading = false, detail = detail(rating = 8.4)))

        composeTestRule.onNodeWithTag("movie-detail-rating").assertIsDisplayed()
        composeTestRule.onNodeWithText("8.4").assertExists()
    }

    @Test
    fun blendetRatingUndGenresOhneImdbMatchAus() {
        setContent(
            MovieDetailUiState(
                isLoading = false,
                detail = detail(genres = null, rating = null)
            )
        )

        composeTestRule.onNodeWithTag("movie-detail-rating").assertDoesNotExist()
        composeTestRule.onNodeWithTag("movie-detail-genres").assertDoesNotExist()
    }

    @Test
    fun zeigtBackdropUndPosterContainer() {
        setContent(MovieDetailUiState(isLoading = false, detail = detail()))

        composeTestRule.onNodeWithTag("movie-detail-backdrop").assertExists()
        composeTestRule.onNodeWithTag("movie-detail-poster").assertExists()
    }
}
