package com.flintr.tv.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
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
class MovieDetailCastTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun detail(cast: List<String>, director: String?) = DetailDisplayData(
        title = "Der Film",
        plot = "Ein Plot.",
        cast = cast,
        director = director,
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
    fun zeigtCastNamenUndRegie() {
        setContent(
            MovieDetailUiState(
                isLoading = false,
                detail = detail(cast = listOf("Alice", "Bob"), director = "Regisseurin")
            )
        )

        composeTestRule.onNodeWithTag("movie-detail-cast").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Alice").assertExists()
        composeTestRule.onNodeWithText("Bob").assertExists()
        composeTestRule.onNodeWithTag("movie-detail-director")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("Regisseurin", substring = true)
    }

    @Test
    fun zeigtBriefmarkeAlsUnterlayAuchMitCastPhotoMatch() {
        setContent(
            MovieDetailUiState(
                isLoading = false,
                detail = detail(cast = listOf("Alice", "Bob"), director = "Regisseurin"),
                castPhotos = mapOf("Alice" to "https://img/alice.jpg")
            )
        )

        composeTestRule.onNodeWithTag("movie-detail-cast").performScrollTo().assertIsDisplayed()
        // Die Initialen-Briefmarke liegt als Unterlay immer da, unabhaengig vom Ladeergebnis
        // des TMDB-Fotos - ein Fehler beim Laden darf nie zu einer leeren Kachel fuehren.
        composeTestRule.onNodeWithText("A").assertExists()
        // Bob bleibt ohne Match ebenfalls bei der Briefmarke.
        composeTestRule.onNodeWithText("B").assertExists()
    }

    @Test
    fun blendetCastUndRegieOhneDatenAus() {
        setContent(
            MovieDetailUiState(
                isLoading = false,
                detail = detail(cast = emptyList(), director = null)
            )
        )

        composeTestRule.onNodeWithTag("movie-detail-cast").assertDoesNotExist()
        composeTestRule.onNodeWithTag("movie-detail-director").assertDoesNotExist()
    }
}
