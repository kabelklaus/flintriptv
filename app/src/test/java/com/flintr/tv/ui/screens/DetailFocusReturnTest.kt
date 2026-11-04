package com.flintr.tv.ui.screens

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flintr.tv.data.model.DetailDisplayData
import com.flintr.tv.data.model.Episode
import com.flintr.tv.data.model.Season
import com.flintr.tv.data.model.Series
import com.flintr.tv.data.model.VodStream
import com.flintr.tv.ui.theme.FlintRTVTheme
import com.flintr.tv.viewmodel.MovieDetailUiState
import com.flintr.tv.viewmodel.SeasonUiModel
import com.flintr.tv.viewmodel.SeriesDetailUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * TV-Abnahme 03.11.2026: from "Mehr anzeigen" (and every body section) D-Pad Up must lead back to
 * the watch button with the poster fully visible - before, the toggle had no Up handler at all.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w1280dp-h720dp-land")
class DetailFocusReturnTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val longPlot = "Ein sehr langer Plot über einen Medicus im Mittelalter. ".repeat(12)

    private fun detail(plot: String = longPlot) = DetailDisplayData(
        title = "Der Medicus",
        plot = plot,
        cast = listOf("Tom Payne", "Ben Kingsley"),
        director = "Philipp Stölzl",
        runtime = "2h 30min",
        releaseDate = "2013-12-25",
        youtubeTrailer = "abc123",
        backdropPath = null,
        posterUrl = null,
        tmdbId = null,
        genres = listOf("Drama"),
        rating = 7.2
    )

    private fun movie(id: Int) = VodStream(
        num = null, name = "Empfehlung $id", streamType = "movie", streamId = id,
        streamIcon = null, rating = null, rating5Based = null, added = null, categoryId = null,
        categoryName = null, containerExtension = null, customSid = null, directSource = null,
        releaseDate = null, duration = null, plot = null, cast = null, director = null,
        genre = "Drama", backdropPath = null, youtubeTrailer = null, cover = null, tmdbId = null
    )

    private fun series(id: Int) = Series(
        num = null, name = "Serie $id", seriesId = id, cover = null, plot = null, cast = null,
        director = null, genre = "Drama", releaseDate = null, rating = null, rating5Based = null,
        lastModified = null, categoryId = null, categoryName = null, youtubeTrailer = null,
        backdropPath = null, seasons = null
    )

    private fun season(number: Int) = SeasonUiModel(
        season = Season(
            airDate = null, episodeCount = 1, id = null, name = "Staffel $number", overview = null,
            seasonNumber = number, cover = null,
            episodes = listOf(
                Episode(
                    id = number * 100, episodeNum = 1, title = "Episode 1", containerExtension = "mkv",
                    info = null, subtitles = null, added = null, season = null, directSource = null
                )
            )
        ),
        title = "Staffel $number",
        posterUrl = null,
        episodeCount = 1,
        watchedCount = 0,
        isFullyWatched = false
    )

    private fun setMovie(plot: String = longPlot) {
        composeTestRule.setContent {
            FlintRTVTheme {
                MovieDetailScreen(
                    uiState = MovieDetailUiState(
                        isLoading = false,
                        detail = detail(plot),
                        recommendations = listOf(movie(11), movie(12))
                    )
                )
            }
        }
        settle()
    }

    private fun setSeries() {
        composeTestRule.setContent {
            FlintRTVTheme {
                SeriesDetailScreen(
                    uiState = SeriesDetailUiState(
                        isLoading = false,
                        detail = detail(),
                        seasons = listOf(season(1), season(2)),
                        recommendations = listOf(series(21), series(22))
                    )
                )
            }
        }
        settle()
    }

    private fun settle() {
        composeTestRule.mainClock.advanceTimeBy(1_000L)
        composeTestRule.waitForIdle()
    }

    private fun focus(tag: String) {
        val node = composeTestRule.onNodeWithTag(tag)
        node.performScrollTo()
        node.performSemanticsAction(SemanticsActions.RequestFocus)
        composeTestRule.waitForIdle()
        node.assertIsFocused()
    }

    private fun pressUp(tag: String) {
        composeTestRule.onNodeWithTag(tag).performKeyInput { pressKey(Key.DirectionUp) }
        settle()
    }

    /**
     * At scroll 0 the visible header block (poster + text) sits vertically centered in the
     * viewport: the remaining space above and below it is equal. Equality also proves the reveal
     * scrolled back to 0 - any scroll offset would shift both sides apart. [rowTag] is the
     * header row node; [rowTopInset] is the header's internal top padding inside that node's
     * bounds (50.dp when the tag sits outside the padding, 0.dp when inside).
     */
    private fun assertHeaderBlockCentered(rowTag: String, rowTopInset: Dp) {
        val root = composeTestRule.onRoot().getBoundsInRoot()
        val row = composeTestRule.onNodeWithTag(rowTag).getBoundsInRoot()
        val restAbove = row.top + rowTopInset - root.top
        val restBelow = root.bottom - row.bottom
        // 1.5dp tolerance: layout positions round to whole pixels, so perfectly equal space
        // (e.g. 172.5dp) can measure 1dp apart after quantization.
        assertEquals(
            "Header-Block nicht vertikal zentriert (oben=$restAbove, unten=$restBelow)",
            restAbove.value,
            restBelow.value,
            1.5f
        )
    }

    @Test
    fun movieOverviewToggleUpLandetAufWatchButtonUndZeigtPoster() {
        setMovie()

        // Scroll the page down first (Chris' situation: "Mehr anzeigen" below the fold).
        composeTestRule.onNodeWithTag("movie-detail-recommendations").performScrollTo()
        focus("movie-detail-overview-toggle")

        pressUp("movie-detail-overview-toggle")

        composeTestRule.onNodeWithTag("movie-detail-watch").assertIsFocused()
        assertHeaderBlockCentered("movie-detail-header-row", 50.dp)
    }

    @Test
    fun movieHeaderButtonsUpGehenZumToggleStattInsLeere() {
        setMovie()
        for (button in listOf("movie-detail-watch", "movie-detail-trailer", "movie-detail-favorite")) {
            focus(button)
            pressUp(button)
            composeTestRule.onNodeWithTag("movie-detail-overview-toggle").assertIsFocused()
        }
    }

    @Test
    fun movieWatchUpOhneToggleScrolltNachObenUndBehaeltFokus() {
        setMovie(plot = "Kurzer Plot.")
        composeTestRule.onNodeWithTag("movie-detail-overview-toggle").assertDoesNotExist()
        composeTestRule.onNodeWithTag("movie-detail-recommendations").performScrollTo()

        focus("movie-detail-watch")
        pressUp("movie-detail-watch")

        composeTestRule.onNodeWithTag("movie-detail-watch").assertIsFocused()
        assertHeaderBlockCentered("movie-detail-header-row", 50.dp)
    }

    @Test
    fun movieEmpfehlungUpLandetAufWatchButtonUndZeigtPoster() {
        setMovie()

        focus("recommendation-12")
        pressUp("recommendation-12")

        composeTestRule.onNodeWithTag("movie-detail-watch").assertIsFocused()
        assertHeaderBlockCentered("movie-detail-header-row", 50.dp)
    }

    @Test
    fun seriesOverviewToggleUpLandetAufWatchButton() {
        setSeries()
        composeTestRule.onNodeWithTag("series-detail-recommendations").performScrollTo()
        focus("series-detail-overview-toggle")

        pressUp("series-detail-overview-toggle")

        composeTestRule.onNodeWithTag("series-detail-watch").assertIsFocused()
    }

    @Test
    fun seriesStaffelUndEmpfehlungUpLandenAufWatchButtonUndZeigenPoster() {
        setSeries()

        focus("season-tile-2")
        pressUp("season-tile-2")
        composeTestRule.onNodeWithTag("series-detail-watch").assertIsFocused()
        assertHeaderBlockCentered("series-detail-header-row", 50.dp)

        // Recommendations step through the season row first (DetailHeaderSharedLayoutTest
        // contract), and the next Up ends on the watch button with the poster visible.
        focus("series-recommendation-22")
        pressUp("series-recommendation-22")
        composeTestRule.onNodeWithTag("season-tile-1").assertIsFocused()
        pressUp("season-tile-1")
        composeTestRule.onNodeWithTag("series-detail-watch").assertIsFocused()
        assertHeaderBlockCentered("series-detail-header-row", 50.dp)
    }

    @Test
    fun seriesEmpfehlungOhneStaffelnUpLandetDirektAufWatchButton() {
        composeTestRule.setContent {
            FlintRTVTheme {
                SeriesDetailScreen(
                    uiState = SeriesDetailUiState(
                        isLoading = false,
                        detail = detail(),
                        recommendations = listOf(series(21), series(22))
                    )
                )
            }
        }
        settle()

        focus("series-recommendation-22")
        pressUp("series-recommendation-22")

        composeTestRule.onNodeWithTag("series-detail-watch").assertIsFocused()
        assertHeaderBlockCentered("series-detail-header-row", 50.dp)
    }

    @Test
    fun movieLadeSkeletonHatDieselbePosterGeometrieWieDerFertigeHeader() {
        val catalog = movie(5).copy(name = "Der Medicus", duration = "2h 30min")
        composeTestRule.setContent {
            FlintRTVTheme {
                MovieDetailScreen(uiState = MovieDetailUiState(isLoading = true, baseStream = catalog))
            }
        }
        settle()

        composeTestRule.onNodeWithTag("movie-detail-skeleton").assertExists()
        // No half-built header: no interactive buttons while the API is still answering.
        composeTestRule.onNodeWithTag("movie-detail-watch").assertDoesNotExist()
        val bounds = composeTestRule.onNodeWithTag("movie-detail-poster").getBoundsInRoot()
        assertDp(250.dp, bounds.right - bounds.left)
        assertDp(375.dp, bounds.bottom - bounds.top)
        // Vertically centered, identical to the loaded content's header geometry.
        assertHeaderBlockCentered("movie-detail-skeleton", 0.dp)
    }

    private fun assertDp(expected: Dp, actual: Dp) = assertEquals(expected.value, actual.value, 0.5f)

    @Test
    fun seriesLadezustandZeigtSkeletonStattZentriertemSpinner() {
        composeTestRule.setContent {
            FlintRTVTheme {
                SeriesDetailScreen(uiState = SeriesDetailUiState(isLoading = true))
            }
        }
        settle()

        composeTestRule.onNodeWithTag("series-detail-loading").assertExists()
        composeTestRule.onNodeWithTag("series-detail-skeleton").assertExists()
        composeTestRule.onNodeWithTag("series-detail-watch").assertDoesNotExist()
        assertHeaderBlockCentered("series-detail-skeleton", 0.dp)
    }
}
