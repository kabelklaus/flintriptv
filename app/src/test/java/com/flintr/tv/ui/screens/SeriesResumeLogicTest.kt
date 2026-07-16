package com.flintr.tv.ui.screens

import com.flintr.tv.data.local.SmartResumeTarget
import com.flintr.tv.data.model.testEpisode
import com.flintr.tv.data.model.testSeason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeriesResumeLogicTest {

    private val seasonOne = testSeason(
        seasonNumber = 1,
        episodes = listOf(
            testEpisode(id = 101, title = "S1F1", season = 1, episodeNum = 1),
            testEpisode(id = 102, title = "S1F2", season = 1, episodeNum = 2)
        )
    )

    private val seasonTwo = testSeason(
        seasonNumber = 2,
        episodes = listOf(
            testEpisode(id = 201, title = "S2F1", season = 2, episodeNum = 1),
            testEpisode(id = 202, title = "S2F2", season = 2, episodeNum = 2)
        )
    )

    // --- resolveResumeEpisode ---

    @Test
    fun `findet Ziel-Episode ueber episodeId in der richtigen Staffel`() {
        val result = SeriesResumeLogic.resolveResumeEpisode(
            seasons = listOf(seasonOne, seasonTwo),
            target = SmartResumeTarget(seasonNumber = 2, episodeNumber = 2, episodeId = 202)
        )

        assertEquals(202, result?.episode?.id)
        assertEquals(2, result?.seasonNumber)
        assertEquals(2, result?.episodeNumber)
    }

    @Test
    fun `findet Ziel-Episode ueber episodeNum wenn episodeId nicht passt`() {
        val result = SeriesResumeLogic.resolveResumeEpisode(
            seasons = listOf(seasonOne, seasonTwo),
            target = SmartResumeTarget(seasonNumber = 2, episodeNumber = 2, episodeId = 999)
        )

        assertEquals(202, result?.episode?.id)
        assertEquals(2, result?.episodeNumber)
    }

    @Test
    fun `faellt auf erste Episode der ersten Staffel zurueck wenn target null ist`() {
        val result = SeriesResumeLogic.resolveResumeEpisode(
            seasons = listOf(seasonOne, seasonTwo),
            target = null
        )

        assertEquals(101, result?.episode?.id)
        assertEquals(1, result?.seasonNumber)
        assertEquals(1, result?.episodeNumber)
    }

    @Test
    fun `faellt auf erste Episode zurueck wenn Ziel-Staffel nicht existiert`() {
        val result = SeriesResumeLogic.resolveResumeEpisode(
            seasons = listOf(seasonOne, seasonTwo),
            target = SmartResumeTarget(seasonNumber = 5, episodeNumber = 1, episodeId = 999)
        )

        assertEquals(101, result?.episode?.id)
        assertEquals(1, result?.seasonNumber)
    }

    @Test
    fun `ueberspringt Staffeln ohne Episoden beim Fallback`() {
        val emptySeason = testSeason(seasonNumber = 1, episodes = null)

        val result = SeriesResumeLogic.resolveResumeEpisode(
            seasons = listOf(emptySeason, seasonTwo),
            target = null
        )

        assertEquals(201, result?.episode?.id)
        assertEquals(2, result?.seasonNumber)
    }

    @Test
    fun `sortiert Staffeln vor dem Fallback nach Staffelnummer`() {
        val result = SeriesResumeLogic.resolveResumeEpisode(
            seasons = listOf(seasonTwo, seasonOne),
            target = null
        )

        assertEquals(101, result?.episode?.id)
        assertEquals(1, result?.seasonNumber)
    }

    @Test
    fun `liefert null wenn keine Episoden vorhanden sind`() {
        val emptySeason = testSeason(seasonNumber = 1, episodes = emptyList())

        assertNull(SeriesResumeLogic.resolveResumeEpisode(seasons = emptyList(), target = null))
        assertNull(SeriesResumeLogic.resolveResumeEpisode(seasons = listOf(emptySeason), target = null))
    }

    @Test
    fun `nutzt episodeId als Episodennummer wenn episodeNum fehlt`() {
        val season = testSeason(
            seasonNumber = 1,
            episodes = listOf(testEpisode(id = 500, episodeNum = null))
        )

        val result = SeriesResumeLogic.resolveResumeEpisode(seasons = listOf(season), target = null)

        assertEquals(500, result?.episodeNumber)
    }

    // --- determineResumeLabelType ---

    @Test
    fun `RESUME wenn Fortschritt auf der Ziel-Episode existiert`() {
        assertEquals(
            ResumeLabelType.RESUME,
            SeriesResumeLogic.determineResumeLabelType(
                hasProgressOnTarget = true,
                hasAnyWatchHistory = true
            )
        )
        assertEquals(
            ResumeLabelType.RESUME,
            SeriesResumeLogic.determineResumeLabelType(
                hasProgressOnTarget = true,
                hasAnyWatchHistory = false
            )
        )
    }

    @Test
    fun `NEXT wenn Historie existiert aber kein Fortschritt auf der Ziel-Episode`() {
        assertEquals(
            ResumeLabelType.NEXT,
            SeriesResumeLogic.determineResumeLabelType(
                hasProgressOnTarget = false,
                hasAnyWatchHistory = true
            )
        )
    }

    @Test
    fun `START wenn weder Fortschritt noch Historie existieren`() {
        assertEquals(
            ResumeLabelType.START,
            SeriesResumeLogic.determineResumeLabelType(
                hasProgressOnTarget = false,
                hasAnyWatchHistory = false
            )
        )
    }

    // --- buildResumeButtonLabel ---

    @Test
    fun `baut deutsche Labels fuer alle Typen`() {
        assertEquals(
            "Weiterschauen: S2E5",
            SeriesResumeLogic.buildResumeButtonLabel(ResumeLabelType.RESUME, seasonNumber = 2, episodeNumber = 5)
        )
        assertEquals(
            "Nächste Folge: S2E5",
            SeriesResumeLogic.buildResumeButtonLabel(ResumeLabelType.NEXT, seasonNumber = 2, episodeNumber = 5)
        )
        assertEquals(
            "Abspielen: S1E1",
            SeriesResumeLogic.buildResumeButtonLabel(ResumeLabelType.START, seasonNumber = 1, episodeNumber = 1)
        )
    }
}
