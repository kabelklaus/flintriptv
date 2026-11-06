package com.flintr.tv.utils

import com.flintr.tv.data.model.EpisodeInfo
import com.flintr.tv.data.model.testEpisode
import com.flintr.tv.data.model.testSeason
import com.flintr.tv.data.model.testSeries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Episoden-Anzeige-Helfer: Titel-Bereinigung und Bild-Fallbacks.
 */
class ContentParserEpisodeDisplayTest {

    private fun episodeInfoWithImage(image: String?) = EpisodeInfo(
        plot = null,
        duration = null,
        movieImage = image,
        releaseDate = null,
        rating = null,
        tmdbId = null
    )

    // --- extractEpisodeName ---

    @Test
    fun `extrahiert Episodennamen nach dem SxxExx-Code`() {
        assertEquals(
            "Wir müssen jetzt los",
            ContentParser.extractEpisodeName(
                rawTitle = "DE - The Boys - S02E05 - Wir müssen jetzt los",
                seriesName = "DE - The Boys (2019) (US)"
            )
        )
    }

    @Test
    fun `extrahiert Episodennamen trotz Jahres- und Laendersuffix im Serientitel`() {
        assertEquals(
            "Der Knappe",
            ContentParser.extractEpisodeName(
                rawTitle = "DE - A Knight of the Seven Kingdoms (2026) (US) - S01E03 - Der Knappe",
                seriesName = "DE - A Knight of the Seven Kingdoms (2026) (US)"
            )
        )
    }

    @Test
    fun `entfernt Sprach- und Serienpraefix wenn kein Episodencode vorhanden ist`() {
        assertEquals(
            "Sabito und Makomo",
            ContentParser.extractEpisodeName(
                rawTitle = "DE - Demon Slayer - Sabito und Makomo",
                seriesName = "DE - Demon Slayer (2019)"
            )
        )
    }

    @Test
    fun `laesst schlichte Episodentitel unveraendert`() {
        assertEquals(
            "Drohnen",
            ContentParser.extractEpisodeName(
                rawTitle = "Drohnen",
                seriesName = "DE - Mayor of Kingstown (2021)"
            )
        )
    }

    @Test
    fun `liefert null wenn nach dem Code nichts mehr kommt`() {
        assertNull(
            ContentParser.extractEpisodeName(
                rawTitle = "DE - The Boys - S02E05",
                seriesName = "DE - The Boys (2019) (US)"
            )
        )
    }

    @Test
    fun `liefert null fuer leere Titel`() {
        assertNull(ContentParser.extractEpisodeName(rawTitle = null, seriesName = null))
        assertNull(ContentParser.extractEpisodeName(rawTitle = "   ", seriesName = null))
    }

    @Test
    fun `verkraftet Doppelpunkt als Trenner nach dem Code`() {
        assertEquals(
            "Der Knappe",
            ContentParser.extractEpisodeName(rawTitle = "S01E03: Der Knappe", seriesName = null)
        )
    }

    // --- seriesBackdropCandidates ---

    @Test
    fun `listet Backdrop vor Cover als Kandidaten`() {
        val series = testSeries(
            backdropPath = listOf("https://img/backdrop.jpg"),
            cover = "https://img/cover.jpg"
        )

        assertEquals(
            listOf("https://img/backdrop.jpg", "https://img/cover.jpg"),
            ContentParser.seriesBackdropCandidates(series)
        )
    }

    @Test
    fun `nimmt erstes Episodenbild als Kandidat wenn Backdrop fehlt`() {
        val series = testSeries(
            backdropPath = null,
            seasons = listOf(
                testSeason(
                    seasonNumber = 1,
                    episodes = listOf(
                        testEpisode(id = 1, info = episodeInfoWithImage(null)),
                        testEpisode(id = 2, info = episodeInfoWithImage("https://img/e2.jpg"))
                    )
                )
            )
        )

        assertEquals(listOf("https://img/e2.jpg"), ContentParser.seriesBackdropCandidates(series))
    }

    @Test
    fun `nimmt Cover als Kandidat wenn weder Backdrop noch Episodenbilder existieren`() {
        val series = testSeries(
            backdropPath = emptyList(),
            cover = "https://img/cover.jpg",
            seasons = listOf(testSeason(seasonNumber = 1, episodes = listOf(testEpisode(id = 1))))
        )

        assertEquals(listOf("https://img/cover.jpg"), ContentParser.seriesBackdropCandidates(series))
    }

    @Test
    fun `liefert leere Liste wenn gar kein Bild verfuegbar ist`() {
        assertEquals(emptyList<String>(), ContentParser.seriesBackdropCandidates(testSeries()))
    }

    // --- imageCandidates ---

    @Test
    fun `behaelt Reihenfolge Episodenbild vor Fallback`() {
        assertEquals(
            listOf("https://img/episode.jpg", "https://img/backdrop.jpg"),
            ContentParser.imageCandidates("https://img/episode.jpg", "https://img/backdrop.jpg")
        )
    }

    @Test
    fun `filtert leere und doppelte Kandidaten heraus`() {
        assertEquals(
            listOf("https://img/backdrop.jpg"),
            ContentParser.imageCandidates(null, "", "https://img/backdrop.jpg", "https://img/backdrop.jpg")
        )
    }

    @Test
    fun `liefert leere Liste wenn alle Kandidaten fehlen`() {
        assertEquals(emptyList<String>(), ContentParser.imageCandidates(null, "", "   "))
    }
}
