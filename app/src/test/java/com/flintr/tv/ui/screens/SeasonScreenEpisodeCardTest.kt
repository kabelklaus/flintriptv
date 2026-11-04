package com.flintr.tv.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flintr.tv.data.local.WatchProgressService
import com.flintr.tv.data.model.Episode
import com.flintr.tv.data.model.EpisodeInfo
import com.flintr.tv.data.model.Season
import com.flintr.tv.data.model.Series
import com.flintr.tv.ui.theme.FlintRTVTheme
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w1280dp-h720dp-land")
class SeasonScreenEpisodeCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun episode(
        id: Int = 11,
        episodeNum: Int = 2,
        plot: String? = "Geheimer Plot, der NICHT angezeigt werden darf",
        airDate: String? = "2020-05-03",
        duration: String? = "00:42:00",
        title: String = "Pilotfolge"
    ) = Episode(
        id = id,
        episodeNum = episodeNum,
        title = title,
        containerExtension = "mkv",
        info = EpisodeInfo(
            plot = plot,
            duration = duration,
            movieImage = null,
            releaseDate = null,
            rating = null,
            tmdbId = null,
            airDate = airDate
        ),
        subtitles = null,
        added = null,
        season = 1,
        directSource = null
    )

    private fun setCard(
        episode: Episode = episode(),
        progressState: EpisodeCardState = EpisodeCardState(
            progressPercent = 0f,
            isCompleted = false,
            remainingMinutes = null,
            isWatched = false
        )
    ) {
        composeTestRule.setContent {
            FlintRTVTheme {
                Box(modifier = Modifier.width(300.dp)) {
                    EpisodeCardContent(
                        episode = episode,
                        seasonNumber = 1,
                        seriesName = "Die Serie",
                        progressState = progressState,
                        fallbackImageUrl = null
                    )
                }
            }
        }
    }

    @Test
    fun zeigtTitelSxEInfoDauerUndAirDateInEinerZentriertenMetaZeile() {
        setCard()

        composeTestRule.onNodeWithTag("episode-card-title")
            .assertIsDisplayed()
            .assertTextContains("Pilotfolge")
        composeTestRule.onNodeWithTag("episode-card-info")
            .assertIsDisplayed()
            .assertTextEquals("S1E2 • 42m • 03.05.2020")
    }

    @Test
    fun episodenDatumAusIsoQuelleWirdDeutschAngezeigt() {
        setCard(episode = episode(episodeNum = 1, airDate = "2005-09-13", duration = "00:46:00"))

        composeTestRule.onNodeWithTag("episode-card-info")
            .assertTextEquals("S1E1 • 46m • 13.09.2005")
    }

    @Test
    fun seasonScreenNormalisiertSeasonLabelZuStaffel() {
        val season = Season(
            airDate = null,
            episodeCount = 1,
            id = null,
            name = "Season 1",
            overview = null,
            seasonNumber = 1,
            cover = null,
            episodes = listOf(episode(11, 1))
        )
        val series = Series(
            num = null,
            name = "Die Serie",
            seriesId = 501,
            cover = null,
            plot = null,
            cast = null,
            director = null,
            genre = null,
            releaseDate = null,
            rating = null,
            rating5Based = null,
            lastModified = null,
            categoryId = null,
            categoryName = null,
            youtubeTrailer = null,
            backdropPath = null,
            seasons = listOf(season)
        )
        val watchProgressService = mockk<WatchProgressService>(relaxed = true)

        composeTestRule.setContent {
            FlintRTVTheme {
                SeasonScreen(
                    series = series,
                    seasonNumber = 1,
                    watchProgressService = watchProgressService,
                    onBack = {},
                    onEpisodeClick = {}
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_000L)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("season-screen-title")
            .assertTextEquals("Staffel 1")
    }

    @Test
    fun zeigtKeinenOverviewTrotzVorhandenemPlot() {
        setCard(episode = episode(plot = "Geheimer Plot, der NICHT angezeigt werden darf"))

        composeTestRule.onNodeWithText(
            "Geheimer Plot, der NICHT angezeigt werden darf"
        ).assertDoesNotExist()
        composeTestRule.onNodeWithTag("episode-overview").assertDoesNotExist()
    }

    @Test
    fun titelDarfOhneBeschreibungZweiZeilenFuellen() {
        val langerTitel = "Ein ungewöhnlich langer Episodentitel für den Zeilenumbruch im Kartenlayout"
        setCard(episode = episode(title = langerTitel))

        // Ohne Overview-Feld (Xtream liefert keins) bekommt der Titel den vollen Platz: er wird
        // nicht nach einer Zeile abgeschnitten, sondern darf bis zu 2 Zeilen füllen.
        composeTestRule.onNodeWithTag("episode-card-title")
            .assertIsDisplayed()
            .assertTextEquals(langerTitel)
        composeTestRule.onNodeWithTag("episode-overview").assertDoesNotExist()
    }

    @Test
    fun zeigtProgressBarBeiVorhandenemWatchProgress() {
        setCard(
            progressState = EpisodeCardState(
                progressPercent = 0.4f,
                isCompleted = false,
                remainingMinutes = 25,
                isWatched = false
            )
        )

        composeTestRule.onNodeWithTag("episode-thumbnail-progress").assertIsDisplayed()
        composeTestRule.onNodeWithTag("episode-thumbnail-remaining").assertIsDisplayed()
    }

    @Test
    fun zeigtWatchedRibbonBeiGesehenerEpisode() {
        setCard(
            progressState = EpisodeCardState(
                progressPercent = 1f,
                isCompleted = true,
                remainingMinutes = null,
                isWatched = true
            )
        )

        composeTestRule.onNodeWithTag("episode-watched-ribbon", useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun seasonScreenZeigtEpisodenAberKeineStaffelAuswahlMehr() {
        val season = Season(
            airDate = null,
            episodeCount = 2,
            id = null,
            name = "Staffel 1",
            overview = null,
            seasonNumber = 1,
            cover = null,
            episodes = listOf(episode(11, 1), episode(12, 2))
        )
        val series = Series(
            num = null,
            name = "Die Serie",
            seriesId = 501,
            cover = null,
            plot = null,
            cast = null,
            director = null,
            genre = null,
            releaseDate = null,
            rating = null,
            rating5Based = null,
            lastModified = null,
            categoryId = null,
            categoryName = null,
            youtubeTrailer = null,
            backdropPath = null,
            seasons = listOf(season)
        )
        val watchProgressService = mockk<WatchProgressService>(relaxed = true)

        composeTestRule.setContent {
            FlintRTVTheme {
                SeasonScreen(
                    series = series,
                    seasonNumber = 1,
                    watchProgressService = watchProgressService,
                    onBack = {},
                    onEpisodeClick = {}
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_000L)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("season-screen-title")
            .assertIsDisplayed()
            .assertTextContains("Staffel 1")
        composeTestRule.onAllNodesWithTag("episode-card-title", useUnmergedTree = true)
            .assertCountEquals(2)
        composeTestRule.onNodeWithTag("season-tile-1").assertDoesNotExist()
    }

    @Test
    fun kartenSindVergroessertUndAnzahlBleibtProEpisodeErhalten() {
        val episodes = listOf(episode(11, 1), episode(12, 2), episode(13, 3))
        val season = Season(
            airDate = null,
            episodeCount = episodes.size,
            id = null,
            name = "Staffel 1",
            overview = null,
            seasonNumber = 1,
            cover = null,
            episodes = episodes
        )
        val series = Series(
            num = null,
            name = "Die Serie",
            seriesId = 501,
            cover = null,
            plot = null,
            cast = null,
            director = null,
            genre = null,
            releaseDate = null,
            rating = null,
            rating5Based = null,
            lastModified = null,
            categoryId = null,
            categoryName = null,
            youtubeTrailer = null,
            backdropPath = null,
            seasons = listOf(season)
        )
        val watchProgressService = mockk<WatchProgressService>(relaxed = true)

        composeTestRule.setContent {
            FlintRTVTheme {
                SeasonScreen(
                    series = series,
                    seasonNumber = 1,
                    watchProgressService = watchProgressService,
                    onBack = {},
                    onEpisodeClick = {}
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_000L)
        composeTestRule.waitForIdle()

        // 420dp statt der alten 300dp: ~3-4 statt 5 Karten pro Screen-Breite, aber die
        // Anzahl der gerenderten Karten folgt weiterhin 1:1 der Episodenliste (Rest per Scroll).
        composeTestRule.onAllNodesWithTag("episode-card", useUnmergedTree = true)
            .assertCountEquals(episodes.size)
        composeTestRule.onAllNodesWithTag("episode-card", useUnmergedTree = true)[0]
            .assertWidthIsEqualTo(420.dp)
    }
}
