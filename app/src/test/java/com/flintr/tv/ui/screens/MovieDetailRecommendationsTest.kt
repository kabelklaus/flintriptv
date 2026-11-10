package com.flintr.tv.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flintr.tv.data.local.CachedData
import com.flintr.tv.data.model.DetailDisplayData
import com.flintr.tv.data.model.VodStream
import com.flintr.tv.domain.recommendation.RecommendationEngine
import com.flintr.tv.ui.theme.FlintRTVTheme
import com.flintr.tv.viewmodel.MovieDetailUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w1280dp-h720dp-land")
class MovieDetailRecommendationsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun movie(id: Int, name: String, genre: String = "Action", tmdbId: String? = null) = VodStream(
        num = null,
        name = name,
        streamType = "movie",
        streamId = id,
        streamIcon = null,
        rating = null,
        rating5Based = null,
        added = null,
        categoryId = null,
        categoryName = null,
        containerExtension = null,
        customSid = null,
        directSource = null,
        releaseDate = null,
        duration = null,
        plot = null,
        cast = null,
        director = null,
        genre = genre,
        backdropPath = null,
        youtubeTrailer = null,
        cover = null,
        tmdbId = tmdbId
    )

    private fun detail() = DetailDisplayData(
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
        genres = listOf("Action"),
        rating = 8.4
    )

    private fun setContent(
        recommendations: List<VodStream>,
        onMovieClick: (Int) -> Unit = {}
    ) {
        composeTestRule.setContent {
            FlintRTVTheme {
                MovieDetailScreen(
                    uiState = MovieDetailUiState(
                        isLoading = false,
                        detail = detail(),
                        recommendations = recommendations
                    ),
                    onMovieClick = onMovieClick
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(500L)
        composeTestRule.waitForIdle()
    }

    @Test
    fun zeigtEmpfehlungenUndKlickNavigiertZumFilm() {
        var clickedId: Int? = null
        setContent(
            recommendations = listOf(movie(11, "Empfohlener Film A"), movie(12, "Empfohlener Film B")),
            onMovieClick = { clickedId = it }
        )

        composeTestRule.onNodeWithTag("movie-detail-recommendations")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Empfohlener Film A").assertExists()

        composeTestRule.onNodeWithTag("recommendation-12").performClick()

        assertEquals(12, clickedId)
    }

    @Test
    fun blendetEmpfehlungenOhneDatenAus() {
        setContent(recommendations = emptyList())

        composeTestRule.onNodeWithTag("movie-detail-recommendations").assertDoesNotExist()
    }

    /**
     * Exclusion des aktuellen Films — TMDB-/recommendations liefern ihn normalerweise nicht,
     * die Engine schließt ihn defensiv aus.
     */
    @Test
    fun engineEmpfehlungNutztGenreMatchUndSchliesstAktuellenFilmAus() {
        val cached = CachedData(
            channels = emptyList(),
            movies = listOf(
                movie(1, "Aktueller Film", tmdbId = "1001"),
                movie(2, "Action Treffer", tmdbId = "1002"),
                movie(3, "Drama ohne Match", genre = "Drama", tmdbId = "1003")
            ),
            series = emptyList(),
            liveCategories = emptyList(),
            vodCategories = emptyList(),
            seriesCategories = emptyList()
        )

        val result = RecommendationEngine.selectSimilarMovies(
            anchorTmdbIds = listOf(1001, 1002),
            cached = cached,
            excludedMovieIds = setOf(1)
        )

        assertTrue(result.isNotEmpty())
        assertEquals(listOf(2), result.map { it.streamId })
        assertTrue("Aktueller Film darf nicht empfohlen werden", result.none { it.streamId == 1 })
    }

    @Test
    fun engineOhneAnchorIdsLiefertKeineEmpfehlungen() {
        val cached = CachedData(
            channels = emptyList(),
            movies = listOf(movie(2, "Action Treffer")),
            series = emptyList(),
            liveCategories = emptyList(),
            vodCategories = emptyList(),
            seriesCategories = emptyList()
        )

        assertTrue(RecommendationEngine.selectSimilarMovies(emptyList(), cached, emptySet()).isEmpty())
    }
}
