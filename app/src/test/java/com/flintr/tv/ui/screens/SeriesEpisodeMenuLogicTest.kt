package com.flintr.tv.ui.screens

import com.flintr.tv.data.model.testEpisode
import com.flintr.tv.data.model.testSeason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeriesEpisodeMenuLogicTest {

    private val seasonOne = testSeason(
        seasonNumber = 1,
        episodes = listOf(
            testEpisode(id = 101, episodeNum = 1),
            testEpisode(id = 102, episodeNum = 2)
        )
    )

    private val seasonTwo = testSeason(
        seasonNumber = 2,
        episodes = listOf(
            testEpisode(id = 201, episodeNum = 1),
            testEpisode(id = 202, episodeNum = 2)
        )
    )

    // --- buildEpisodeMenuOptions ---

    @Test
    fun `Menue bietet Als-gesehen-markieren fuer ungesehene Episode`() {
        assertEquals(
            listOf("Als gesehen markieren", "Alle vorherigen als gesehen markieren"),
            SeriesEpisodeMenuLogic.buildEpisodeMenuOptions(isWatched = false)
        )
    }

    @Test
    fun `Menue bietet Als-ungesehen-markieren fuer gesehene Episode`() {
        assertEquals(
            listOf("Als ungesehen markieren", "Alle vorherigen als gesehen markieren"),
            SeriesEpisodeMenuLogic.buildEpisodeMenuOptions(isWatched = true)
        )
    }

    // --- menuActionForOption ---

    @Test
    fun `Option 0 toggelt den Gesehen-Status`() {
        assertEquals(
            EpisodeMenuAction.MARK_WATCHED,
            SeriesEpisodeMenuLogic.menuActionForOption(index = 0, isWatched = false)
        )
        assertEquals(
            EpisodeMenuAction.MARK_UNWATCHED,
            SeriesEpisodeMenuLogic.menuActionForOption(index = 0, isWatched = true)
        )
    }

    @Test
    fun `Option 1 markiert alle vorherigen als gesehen`() {
        assertEquals(
            EpisodeMenuAction.MARK_PREVIOUS_WATCHED,
            SeriesEpisodeMenuLogic.menuActionForOption(index = 1, isWatched = false)
        )
    }

    @Test
    fun `unbekannter Index liefert null`() {
        assertNull(SeriesEpisodeMenuLogic.menuActionForOption(index = 2, isWatched = false))
        assertNull(SeriesEpisodeMenuLogic.menuActionForOption(index = -1, isWatched = true))
    }

    // --- previousEpisodeIds ---

    @Test
    fun `liefert alle Episoden vor dem Ziel ueber Staffelgrenzen hinweg`() {
        val result = SeriesEpisodeMenuLogic.previousEpisodeIds(
            seasons = listOf(seasonOne, seasonTwo),
            targetSeasonNumber = 2,
            targetEpisodeId = 202,
            alreadyWatchedIds = emptySet()
        )

        assertEquals(listOf(101, 102, 201), result)
    }

    @Test
    fun `liefert leere Liste fuer die erste Episode`() {
        val result = SeriesEpisodeMenuLogic.previousEpisodeIds(
            seasons = listOf(seasonOne, seasonTwo),
            targetSeasonNumber = 1,
            targetEpisodeId = 101,
            alreadyWatchedIds = emptySet()
        )

        assertEquals(emptyList<Int>(), result)
    }

    @Test
    fun `liefert leere Liste wenn Ziel-Episode nicht gefunden wird`() {
        val result = SeriesEpisodeMenuLogic.previousEpisodeIds(
            seasons = listOf(seasonOne, seasonTwo),
            targetSeasonNumber = 3,
            targetEpisodeId = 999,
            alreadyWatchedIds = emptySet()
        )

        assertEquals(emptyList<Int>(), result)
    }

    @Test
    fun `sortiert unsortierte Staffeln nach Staffelnummer`() {
        val result = SeriesEpisodeMenuLogic.previousEpisodeIds(
            seasons = listOf(seasonTwo, seasonOne),
            targetSeasonNumber = 2,
            targetEpisodeId = 201,
            alreadyWatchedIds = emptySet()
        )

        assertEquals(listOf(101, 102), result)
    }

    @Test
    fun `ueberspringt Staffeln ohne Episoden`() {
        val emptySeason = testSeason(seasonNumber = 1, episodes = null)

        val result = SeriesEpisodeMenuLogic.previousEpisodeIds(
            seasons = listOf(emptySeason, seasonTwo),
            targetSeasonNumber = 2,
            targetEpisodeId = 202,
            alreadyWatchedIds = emptySet()
        )

        assertEquals(listOf(201), result)
    }

    @Test
    fun `filtert bereits gesehene Episoden heraus`() {
        val result = SeriesEpisodeMenuLogic.previousEpisodeIds(
            seasons = listOf(seasonOne, seasonTwo),
            targetSeasonNumber = 2,
            targetEpisodeId = 202,
            alreadyWatchedIds = setOf(101, 201)
        )

        assertEquals(listOf(102), result)
    }
}
