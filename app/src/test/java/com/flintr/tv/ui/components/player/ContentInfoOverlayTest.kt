package com.flintr.tv.ui.components.player

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flintr.tv.data.model.testEpisode
import com.flintr.tv.data.model.testSeason
import com.flintr.tv.data.model.testSeries
import com.flintr.tv.ui.screens.SeriesContext
import com.flintr.tv.ui.theme.FlintRTVTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Formatierung des Content-Info-Overlays als lokale Unit-Tests (Robolectric),
 * damit die Abdeckung in den Jacoco-Report einfließt, den Sonar konsumiert.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w1280dp-h720dp-land")
class ContentInfoOverlayTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun seriesContext(
        seasonNumber: Int = 2,
        episodeNum: Int? = 5,
        episodeId: Int = 205,
        episodeTitle: String = "Finale"
    ): SeriesContext {
        val episode = testEpisode(id = episodeId, title = episodeTitle, episodeNum = episodeNum, season = seasonNumber)
        val season = testSeason(seasonNumber = seasonNumber, episodes = listOf(episode))
        return SeriesContext(
            series = testSeries(seasons = listOf(season)),
            currentSeason = season,
            currentEpisode = episode
        )
    }

    private fun setOverlay(contentType: String, title: String?, seriesData: SeriesContext?) {
        composeTestRule.setContent {
            FlintRTVTheme {
                ContentInfoOverlay(
                    contentType = contentType,
                    title = title,
                    seriesData = seriesData
                )
            }
        }
    }

    @Test
    fun serieZeigtStaffelEpisodeUndTitel() {
        setOverlay(contentType = "series", title = null, seriesData = seriesContext())

        composeTestRule.onNodeWithText("S2:E5 \"Finale\"").assertIsDisplayed()
    }

    @Test
    fun serieOhneEpisodentitelZeigtNurStaffelUndEpisode() {
        setOverlay(contentType = "series", title = null, seriesData = seriesContext(episodeTitle = ""))

        composeTestRule.onNodeWithText("S2:E5").assertIsDisplayed()
    }

    @Test
    fun serieNutztEpisodenIdAlsFallbackFuerNummer() {
        setOverlay(
            contentType = "series",
            title = null,
            seriesData = seriesContext(episodeNum = null, episodeId = 99)
        )

        composeTestRule.onNodeWithText("S2:E99 \"Finale\"").assertIsDisplayed()
    }

    @Test
    fun serieOhneKontextZeigtNichts() {
        setOverlay(contentType = "series", title = "ignoriert", seriesData = null)

        composeTestRule.onNodeWithText("ignoriert", substring = true).assertDoesNotExist()
    }

    @Test
    fun filmZeigtTitelInAnfuehrungszeichen() {
        setOverlay(contentType = "movie", title = "Der Film", seriesData = null)

        composeTestRule.onNodeWithText("\"Der Film\"").assertIsDisplayed()
    }

    @Test
    fun liveZeigtSendernamen() {
        setOverlay(contentType = "live", title = "Kanal Eins", seriesData = null)

        composeTestRule.onNodeWithText("\"Kanal Eins\"").assertIsDisplayed()
    }

    @Test
    fun filmMitLeeremTitelZeigtNichts() {
        setOverlay(contentType = "movie", title = "", seriesData = null)

        composeTestRule.onNodeWithText("\"\"").assertDoesNotExist()
    }

    @Test
    fun unbekannterContentTypeZeigtNichts() {
        setOverlay(contentType = "radio", title = "Titel", seriesData = null)

        composeTestRule.onNodeWithText("Titel", substring = true).assertDoesNotExist()
    }

    @Test
    fun langerTitelWirdMitSchlusszeichenGekuerzt() {
        val longTitle = "A".repeat(60)
        setOverlay(contentType = "movie", title = longTitle, seriesData = null)

        // "\"" + 60x A + "\"" (62 Zeichen) -> take(46) + "..." + "\""
        val expected = "\"" + "A".repeat(45) + "...\""
        composeTestRule.onNodeWithText(expected).assertIsDisplayed()
    }
}
