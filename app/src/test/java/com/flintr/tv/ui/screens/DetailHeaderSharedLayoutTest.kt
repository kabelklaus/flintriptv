package com.flintr.tv.ui.screens

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.printToString
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flintr.tv.data.model.DetailDisplayData
import com.flintr.tv.data.model.Series
import com.flintr.tv.data.model.VodStream
import com.flintr.tv.ui.theme.FlintRTVTheme
import com.flintr.tv.viewmodel.MovieDetailUiState
import com.flintr.tv.viewmodel.SeasonUiModel
import com.flintr.tv.viewmodel.SeriesDetailUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Proves MovieDetailScreen and SeriesDetailScreen render the header through the exact same
 * shared composable (DetailHeaderSection) instead of two diverging copies, and that D-Pad Up
 * from the lowest section always finds its way back to the "Ansehen" button - the regression
 * reported on the Mi Box (scrolling down to "Könnte dir gefallen" then back up never refocused
 * the watch button).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w1280dp-h720dp-land")
class DetailHeaderSharedLayoutTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun movieDetail() = DetailDisplayData(
        title = "Der Film",
        plot = "Ein Plot über Dinosaurier.",
        cast = emptyList(),
        director = null,
        runtime = "2h 0min",
        releaseDate = "2020-05-01",
        youtubeTrailer = null,
        backdropPath = null,
        posterUrl = null,
        tmdbId = null,
        genres = listOf("Action", "Drama"),
        rating = 8.4
    )

    private fun seriesDetail() = DetailDisplayData(
        title = "Die Serie",
        plot = "Ein Serienplot.",
        cast = emptyList(),
        director = null,
        runtime = null,
        releaseDate = "2020-05-01",
        youtubeTrailer = null,
        backdropPath = null,
        posterUrl = null,
        tmdbId = null,
        genres = listOf("Action", "Drama"),
        rating = 8.4
    )

    private fun movie(id: Int, name: String) = VodStream(
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
        genre = null,
        backdropPath = null,
        youtubeTrailer = null,
        cover = null,
        tmdbId = null
    )

    private fun recommendedSeries(id: Int, name: String) = Series(
        num = null,
        name = name,
        seriesId = id,
        cover = null,
        plot = null,
        cast = null,
        director = null,
        genre = "Drama",
        releaseDate = null,
        rating = null,
        rating5Based = null,
        lastModified = null,
        categoryId = null,
        categoryName = null,
        youtubeTrailer = null,
        backdropPath = null,
        seasons = null
    )

    @Test
    fun filmHeaderRendertPosterTitelRatingGenresOverviewButtonsInDieserReihenfolge() {
        composeTestRule.setContent {
            FlintRTVTheme {
                MovieDetailScreen(uiState = MovieDetailUiState(isLoading = false, detail = movieDetail()))
            }
        }
        assertHeaderOrder(composeTestRule.onRoot().printToString(), "movie-detail")
    }

    @Test
    fun serienHeaderRendertPosterTitelRatingGenresOverviewButtonsInDerselbenReihenfolgeWieFilm() {
        composeTestRule.setContent {
            FlintRTVTheme {
                SeriesDetailScreen(uiState = SeriesDetailUiState(isLoading = false, detail = seriesDetail()))
            }
        }
        assertHeaderOrder(composeTestRule.onRoot().printToString(), "series-detail")
    }

    private fun assertHeaderOrder(tree: String, prefix: String) {
        val poster = tree.indexOf("$prefix-poster")
        val title = tree.indexOf("$prefix-title")
        val rating = tree.indexOf("$prefix-rating")
        val genres = tree.indexOf("$prefix-genres")
        val overview = tree.indexOf("$prefix-overview")
        val watch = tree.indexOf("$prefix-watch")
        val watchlist = tree.indexOf("$prefix-favorite")

        assertTrue("$prefix-poster fehlt", poster >= 0)
        assertTrue("$prefix-title fehlt", title >= 0)
        assertTrue("$prefix-rating fehlt", rating >= 0)
        assertTrue("$prefix-genres fehlt", genres >= 0)
        assertTrue("$prefix-overview fehlt", overview >= 0)
        assertTrue("$prefix-watch fehlt", watch >= 0)
        assertTrue("$prefix-favorite fehlt", watchlist >= 0)

        assertTrue("Poster muss vor Titel stehen ($prefix)", poster < title)
        assertTrue("Titel muss vor Rating stehen ($prefix)", title < rating)
        assertTrue("Rating muss vor Genres stehen ($prefix)", rating < genres)
        assertTrue("Genres muessen vor Overview stehen ($prefix)", genres < overview)
        assertTrue("Overview muss vor Ansehen stehen ($prefix)", overview < watch)
        assertTrue("Ansehen muss vor Favorit stehen ($prefix)", watch < watchlist)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun seriesFokusRueckwegVonEmpfehlungenUeberStaffelnZumAnsehenButton() {
        composeTestRule.setContent {
            FlintRTVTheme {
                SeriesDetailScreen(
                    uiState = SeriesDetailUiState(
                        isLoading = false,
                        detail = seriesDetail(),
                        seasons = listOf(
                            SeasonUiModel(
                                season = com.flintr.tv.data.model.Season(
                                    airDate = null,
                                    episodeCount = 4,
                                    id = null,
                                    name = "Staffel 1",
                                    overview = null,
                                    seasonNumber = 1,
                                    cover = null,
                                    episodes = emptyList()
                                ),
                                title = "Staffel 1",
                                posterUrl = null,
                                episodeCount = 4,
                                watchedCount = 0,
                                isFullyWatched = false
                            )
                        ),
                        recommendations = listOf(
                            recommendedSeries(21, "Empfohlene Serie A"),
                            recommendedSeries(22, "Empfohlene Serie B")
                        )
                    )
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_000L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("series-detail-watch").assertIsFocused()

        // Down from the header must land on the season row (real focus transition driven by
        // the app's own D-Pad wiring, not a test-injected focus jump).
        composeTestRule.onNodeWithTag("series-detail-watch").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        composeTestRule.mainClock.advanceTimeBy(500L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("season-tile-1").assertIsFocused()

        // Down from the season row must land on "Könnte dir gefallen".
        composeTestRule.onNodeWithTag("season-tile-1").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        composeTestRule.mainClock.advanceTimeBy(500L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("series-recommendation-21").assertIsFocused()

        // The regression: scrolling back Up from "Könnte dir gefallen" must go through the
        // season row and finally reach the header's "Ansehen" button again.
        composeTestRule.onNodeWithTag("series-recommendation-21").performKeyInput {
            pressKey(Key.DirectionUp)
        }
        composeTestRule.mainClock.advanceTimeBy(500L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("season-tile-1").assertIsFocused()

        composeTestRule.onNodeWithTag("season-tile-1").performKeyInput {
            pressKey(Key.DirectionUp)
        }
        composeTestRule.mainClock.advanceTimeBy(500L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("series-detail-watch").assertIsFocused()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun movieFokusRueckwegVonEmpfehlungenDirektZumAnsehenButton() {
        composeTestRule.setContent {
            FlintRTVTheme {
                MovieDetailScreen(
                    uiState = MovieDetailUiState(
                        isLoading = false,
                        detail = movieDetail(),
                        recommendations = listOf(
                            movie(11, "Empfohlener Film A"),
                            movie(12, "Empfohlener Film B")
                        )
                    )
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_000L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("movie-detail-watch").assertIsFocused()

        composeTestRule.onNodeWithTag("movie-detail-watch").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        composeTestRule.mainClock.advanceTimeBy(500L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("recommendation-11").assertIsFocused()

        // The regression: no season row for movies, so Up from "Könnte dir gefallen" must land
        // directly back on the header's "Ansehen" button.
        composeTestRule.onNodeWithTag("recommendation-11").performKeyInput {
            pressKey(Key.DirectionUp)
        }
        composeTestRule.mainClock.advanceTimeBy(500L)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("movie-detail-watch").assertIsFocused()
    }
}
