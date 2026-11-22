package com.flintr.tv.ui.screens

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.printToString
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flintr.tv.data.model.DetailDisplayData
import com.flintr.tv.data.model.Episode
import com.flintr.tv.data.model.Season
import com.flintr.tv.data.model.Series
import com.flintr.tv.ui.theme.FlintRTVTheme
import com.flintr.tv.viewmodel.SeasonUiModel
import com.flintr.tv.viewmodel.SeriesDetailUiState
import com.flintr.tv.viewmodel.WatchTargetUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w1280dp-h720dp-land")
class SeriesDetailScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun detail(
        genres: List<String>? = listOf("Drama", "Mystery"),
        rating: Double? = 8.1,
        youtubeTrailer: String? = null
    ) = DetailDisplayData(
        title = "Die Serie",
        plot = "Ein Serienplot.",
        cast = listOf("Alice", "Bob"),
        director = "Regie",
        runtime = null,
        releaseDate = "2020-05-01",
        youtubeTrailer = youtubeTrailer,
        backdropPath = null,
        posterUrl = null,
        tmdbId = null,
        genres = genres,
        rating = rating
    )

    private fun episode(id: Int) = Episode(
        id = id,
        episodeNum = id,
        title = "Episode $id",
        containerExtension = "mkv",
        info = null,
        subtitles = null,
        added = null,
        season = null,
        directSource = null
    )

    private fun seasonUi(
        number: Int,
        episodeCount: Int,
        watchedCount: Int
    ) = SeasonUiModel(
        season = Season(
            airDate = null,
            episodeCount = episodeCount,
            id = null,
            name = "Staffel $number",
            overview = null,
            seasonNumber = number,
            cover = null,
            episodes = List(episodeCount) { episode(number * 100 + it) }
        ),
        title = "Staffel $number",
        posterUrl = null,
        episodeCount = episodeCount,
        watchedCount = watchedCount,
        isFullyWatched = episodeCount > 0 && watchedCount >= episodeCount
    )

    private fun recommendedSeries(id: Int, name: String, genre: String = "Drama") = Series(
        num = null,
        name = name,
        seriesId = id,
        cover = null,
        plot = null,
        cast = null,
        director = null,
        genre = genre,
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

    private fun setContent(
        state: SeriesDetailUiState,
        onSeasonClick: (Int) -> Unit = {},
        onSeriesClick: (Int) -> Unit = {},
        onWatchClick: (Episode) -> Unit = {}
    ) {
        composeTestRule.setContent {
            FlintRTVTheme {
                SeriesDetailScreen(
                    uiState = state,
                    onSeasonClick = onSeasonClick,
                    onSeriesClick = onSeriesClick,
                    onWatchClick = onWatchClick
                )
            }
        }
    }

    @Test
    fun zeigtTitelMetaGenresUndTmdbRating() {
        setContent(SeriesDetailUiState(isLoading = false, detail = detail()))

        composeTestRule.onNodeWithTag("series-detail-title")
            .assertIsDisplayed()
            .assertTextContains("Die Serie")
        composeTestRule.onNodeWithTag("series-detail-rating").assertIsDisplayed()
        composeTestRule.onNodeWithText("8.1").assertExists()
        composeTestRule.onNodeWithText("TMDB").assertExists()
        composeTestRule.onNodeWithTag("series-detail-genres")
            .assertIsDisplayed()
            .assertTextContains("Drama, Mystery")
    }

    @Test
    fun blendetRatingUndGenresOhneMetaMatchAus() {
        setContent(
            SeriesDetailUiState(
                isLoading = false,
                detail = detail(genres = null, rating = null)
            )
        )

        composeTestRule.onNodeWithTag("series-detail-rating").assertDoesNotExist()
        composeTestRule.onNodeWithTag("series-detail-genres").assertDoesNotExist()
        // Xtream-Felder sind nicht Teil des Display-Modells und dürfen hier nie auftauchen.
        composeTestRule.onNodeWithText("Xtream-Genre").assertDoesNotExist()
    }

    @Test
    fun rendertStaffelKachelnMitPosterUndTitel() {
        setContent(
            SeriesDetailUiState(
                isLoading = false,
                detail = detail(),
                seasons = listOf(seasonUi(1, 4, 0), seasonUi(2, 3, 3))
            )
        )

        composeTestRule.onNodeWithTag("series-detail-season-row").assertExists()
        composeTestRule.onNodeWithTag("season-tile-1").assertExists()
        composeTestRule.onNodeWithTag("season-tile-2").assertExists()
        composeTestRule.onNodeWithTag("season-poster-1", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("season-title-1", useUnmergedTree = true)
            .assertExists()
            .assertTextContains("Staffel 1")
    }

    @Test
    fun zeigtWatchedRibbonNurBeiKomplettGesehenerStaffel() {
        setContent(
            SeriesDetailUiState(
                isLoading = false,
                detail = detail(),
                seasons = listOf(seasonUi(1, 4, 2), seasonUi(2, 3, 3))
            )
        )

        composeTestRule.onNodeWithTag("season-watched-ribbon-1", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("season-watched-ribbon-2", useUnmergedTree = true).assertExists()
    }

    @Test
    fun staffelKlickMeldetStaffelnummer() {
        var clicked: Int? = null
        setContent(
            state = SeriesDetailUiState(
                isLoading = false,
                detail = detail(),
                seasons = listOf(seasonUi(1, 4, 0), seasonUi(2, 3, 0))
            ),
            onSeasonClick = { clicked = it }
        )

        composeTestRule.onNodeWithTag("series-detail-seasons").performScrollTo()
        composeTestRule.onNodeWithTag("season-tile-2", useUnmergedTree = true)
            .assertHasClickAction()
            .performClick()
        composeTestRule.mainClock.advanceTimeBy(200L)
        composeTestRule.waitForIdle()

        assertEquals(2, clicked)
    }

    @Test
    fun zeigtHinweisWennKeineStaffelnVorhandenSind() {
        setContent(
            SeriesDetailUiState(
                isLoading = false,
                detail = detail(),
                seasons = emptyList(),
                seasonsHint = "Für diese Serie sind derzeit keine Staffeln und Episoden verfügbar."
            )
        )

        composeTestRule.onNodeWithTag("series-detail-no-seasons").assertExists()
    }

    @Test
    fun initialFokusLiegtAufErsterAktionNichtAufLazyKachel() {
        setContent(
            SeriesDetailUiState(
                isLoading = false,
                detail = detail(),
                seasons = listOf(seasonUi(1, 4, 0))
            )
        )
        composeTestRule.mainClock.advanceTimeBy(1_000L)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("series-detail-watch").assertIsFocused()
    }

    @Test
    fun aktionsButtonsErscheinenInReihenfolgeAnsehenTrailerWatchlist() {
        setContent(
            SeriesDetailUiState(
                isLoading = false,
                detail = detail(youtubeTrailer = "https://www.youtube.com/watch?v=abc123")
            )
        )

        val tree = composeTestRule.onRoot().printToString()
        val watchIndex = tree.indexOf("series-detail-watch")
        val trailerIndex = tree.indexOf("series-detail-trailer")
        val watchlistIndex = tree.indexOf("series-detail-watchlist")

        assertTrue("Ansehen-Button fehlt", watchIndex >= 0)
        assertTrue("Trailer-Button fehlt", trailerIndex >= 0)
        assertTrue("Watchlist-Button fehlt", watchlistIndex >= 0)
        assertTrue("Ansehen muss vor Trailer stehen", watchIndex < trailerIndex)
        assertTrue("Trailer muss vor Watchlist stehen", trailerIndex < watchlistIndex)
    }

    @Test
    fun rendertStaffelnVorBesetzungUndRegie() {
        setContent(
            SeriesDetailUiState(
                isLoading = false,
                detail = detail(),
                seasons = listOf(seasonUi(1, 4, 0))
            )
        )

        val tree = composeTestRule.onRoot().printToString()
        val seasonsIndex = tree.indexOf("Staffeln")
        val castIndex = tree.indexOf("Besetzung")
        val directorIndex = tree.indexOf("Regie")

        assertTrue("Staffeln-Label fehlt", seasonsIndex >= 0)
        assertTrue("Besetzung-Label fehlt", castIndex >= 0)
        assertTrue("Regie-Label fehlt", directorIndex >= 0)
        assertTrue("Staffeln muss vor Besetzung stehen", seasonsIndex < castIndex)
        assertTrue("Besetzung muss vor Regie stehen", castIndex < directorIndex)
    }

    @Test
    fun zeigtEmpfehlungenUndKlickMeldetSeriesId() {
        var clickedId: Int? = null
        setContent(
            state = SeriesDetailUiState(
                isLoading = false,
                detail = detail(),
                recommendations = listOf(
                    recommendedSeries(21, "Empfohlene Serie A"),
                    recommendedSeries(22, "Empfohlene Serie B")
                )
            ),
            onSeriesClick = { clickedId = it }
        )

        composeTestRule.onNodeWithTag("series-detail-recommendations")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Empfohlene Serie A").assertExists()

        composeTestRule.onNodeWithTag("series-recommendation-22").performClick()

        assertEquals(22, clickedId)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun dpadOkAufEmpfehlungMeldetSeriesId() {
        var clickedId: Int? = null
        setContent(
            state = SeriesDetailUiState(
                isLoading = false,
                detail = detail(),
                recommendations = listOf(
                    recommendedSeries(21, "Empfohlene Serie A"),
                    recommendedSeries(22, "Empfohlene Serie B")
                )
            ),
            onSeriesClick = { clickedId = it }
        )
        // Let the initial watch-button focus settle so it can't steal focus back from the card.
        composeTestRule.mainClock.advanceTimeBy(1_000L)
        composeTestRule.waitForIdle()

        val card = composeTestRule.onNodeWithTag("series-recommendation-21")
        card.performScrollTo()
        card.performSemanticsAction(SemanticsActions.RequestFocus)
        composeTestRule.waitForIdle()
        card.assertIsFocused()
        card.performKeyInput { pressKey(Key.DirectionCenter) }

        assertEquals(21, clickedId)
    }

    @Test
    fun blendetEmpfehlungenOhneDatenAus() {
        setContent(
            SeriesDetailUiState(
                isLoading = false,
                detail = detail(),
                recommendations = emptyList()
            )
        )

        composeTestRule.onNodeWithTag("series-detail-recommendations").assertDoesNotExist()
    }

    @Test
    fun zeigtZielEpisodeImAnsehenButtonOhneFortschrittsbalken() {
        val target = WatchTargetUiModel(
            seasonNumber = 1,
            episodeNumber = 1,
            episode = episode(11),
            label = "S1 E1 ansehen"
        )
        setContent(
            SeriesDetailUiState(
                isLoading = false,
                detail = detail(),
                watchTarget = target,
                watchLabel = target.label
            )
        )

        composeTestRule.onNodeWithTag("series-detail-watch")
            .assertTextContains("S1 E1 ansehen")
        composeTestRule.onNodeWithTag("series-detail-watch-progress", useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun zeigtFortschrittsbalkenImButtonNurBeiAngebrochenerZielEpisode() {
        val target = WatchTargetUiModel(
            seasonNumber = 2,
            episodeNumber = 2,
            episode = episode(22),
            label = "S2 E2 fortsetzen",
            watchProgress = 50f
        )
        setContent(
            SeriesDetailUiState(
                isLoading = false,
                detail = detail(),
                watchTarget = target,
                watchLabel = target.label,
                watchProgress = target.watchProgress
            )
        )

        composeTestRule.onNodeWithTag("series-detail-watch")
            .assertTextContains("S2 E2 fortsetzen")
        composeTestRule.onNodeWithTag("series-detail-watch-progress", useUnmergedTree = true)
            .assertExists()
        composeTestRule.onNodeWithTag("series-detail-watch-progress-fill", useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun ansehenKlickMeldetGenauDieZielEpisode() {
        var clickedEpisodeId: Int? = null
        val target = WatchTargetUiModel(
            seasonNumber = 2,
            episodeNumber = 2,
            episode = episode(22),
            label = "S2 E2 fortsetzen",
            watchProgress = 25f
        )
        setContent(
            state = SeriesDetailUiState(
                isLoading = false,
                detail = detail(),
                watchTarget = target,
                watchLabel = target.label,
                watchProgress = target.watchProgress
            ),
            onWatchClick = { clickedEpisodeId = it.id }
        )

        composeTestRule.onNodeWithTag("series-detail-watch").performClick()
        composeTestRule.waitForIdle()

        assertEquals(22, clickedEpisodeId)
    }
}
