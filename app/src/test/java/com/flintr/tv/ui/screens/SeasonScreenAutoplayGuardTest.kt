package com.flintr.tv.ui.screens

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flintr.tv.data.local.WatchProgressService
import com.flintr.tv.data.model.Episode
import com.flintr.tv.data.model.Season
import com.flintr.tv.data.model.Series
import com.flintr.tv.ui.theme.FlintRTVTheme
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * TV-Abnahme 03.11.2026 (Chris, Doppel-OK-Repro): opening an unwatched season auto-focuses the
 * first episode card, and that card plays on OK - so an OK echo from the season-tile click that
 * opened this screen started playback nobody asked for.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w1280dp-h720dp-land")
class SeasonScreenAutoplayGuardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun episode(id: Int, episodeNum: Int) = Episode(
        id = id,
        episodeNum = episodeNum,
        title = "Episode $episodeNum",
        containerExtension = "mkv",
        info = null,
        subtitles = null,
        added = null,
        season = 1,
        directSource = null
    )

    private fun series(): Series {
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
        return Series(
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
    }

    private fun settle() {
        composeTestRule.mainClock.advanceTimeBy(1_000L)
        composeTestRule.waitForIdle()
    }

    @Test
    fun okEchoBeimOeffnenStartetKeineWiedergabe() {
        val clickedEpisodes = mutableListOf<Episode>()
        val watchProgressService = mockk<WatchProgressService>(relaxed = true)

        composeTestRule.setContent {
            FlintRTVTheme {
                SeasonScreen(
                    series = series(),
                    seasonNumber = 1,
                    watchProgressService = watchProgressService,
                    onBack = {},
                    onEpisodeClick = { clickedEpisodes.add(it) }
                )
            }
        }
        settle()

        val firstCard = composeTestRule.onAllNodesWithTag("episode-card", useUnmergedTree = true)[0]
        // The first card is pre-selected (highlighted) by design - that part must stay.
        firstCard.assertIsFocused()

        // Doppel-OK-Repro: two OK presses right after opening, no navigation in between.
        firstCard.performKeyInput { pressKey(Key.DirectionCenter) }
        firstCard.performKeyInput { pressKey(Key.DirectionCenter) }
        settle()

        assertTrue(
            "OK-Echo direkt beim Öffnen darf keine Wiedergabe auslösen, löste aber aus: $clickedEpisodes",
            clickedEpisodes.isEmpty()
        )
    }

    @Test
    fun nachExpliziterNavigationStartetOkDieWiedergabe() {
        val clickedEpisodes = mutableListOf<Episode>()
        val watchProgressService = mockk<WatchProgressService>(relaxed = true)

        composeTestRule.setContent {
            FlintRTVTheme {
                SeasonScreen(
                    series = series(),
                    seasonNumber = 1,
                    watchProgressService = watchProgressService,
                    onBack = {},
                    onEpisodeClick = { clickedEpisodes.add(it) }
                )
            }
        }
        settle()

        val firstCard = composeTestRule.onAllNodesWithTag("episode-card", useUnmergedTree = true)[0]
        firstCard.assertIsFocused()

        // Explicit navigation first - real focus moves to card 2 via its own
        // LaunchedEffect(isFocused); only then may OK play something.
        firstCard.performKeyInput { pressKey(Key.DirectionRight) }
        settle()
        val secondCard = composeTestRule.onAllNodesWithTag("episode-card", useUnmergedTree = true)[1]
        secondCard.assertIsFocused()
        secondCard.performKeyInput { pressKey(Key.DirectionCenter) }
        settle()

        assertEquals(1, clickedEpisodes.size)
        assertEquals(12, clickedEpisodes[0].id)
    }
}
