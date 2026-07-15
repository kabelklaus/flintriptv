package com.flintr.tv.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * toSeries(): Zusammenführen von Seasons und Episoden aus der get_series_info-Antwort.
 */
class SeriesInfoResponseTest {

    private fun seriesInfo(name: String? = "Dark") = SeriesInfo(
        name = name,
        cover = "http://cover.jpg",
        plot = "Plot",
        cast = null,
        director = null,
        genre = "Mystery",
        releaseDate = null,
        releaseDateAlt = null,
        rating = "9.0",
        rating5Based = 4.5,
        categoryId = "12",
        youtubeTrailer = null,
        backdropPath = null
    )

    @Test
    fun missingSeasonsAreCreatedFromEpisodeMap() {
        val response = SeriesInfoResponse(
            seasons = null,
            info = seriesInfo(),
            episodes = mapOf(
                "2" to listOf(testEpisode(id = 3, season = 2)),
                "1" to listOf(testEpisode(id = 1, season = 1), testEpisode(id = 2, season = 1))
            )
        )

        val series = response.toSeries(seriesId = 77)

        assertEquals(77, series.seriesId)
        assertEquals("Dark", series.name)
        assertEquals(listOf(1, 2), series.seasons?.map { it.seasonNumber })
        assertEquals("Staffel 1", series.seasons?.first()?.name)
        assertEquals(2, series.seasons?.first()?.episodeCount)
        assertEquals(listOf(1, 2), series.seasons?.first()?.episodes?.map { it.id })
    }

    @Test
    fun nonNumericSeasonKeysAreGroupedIntoSeasonZero() {
        val response = SeriesInfoResponse(
            seasons = emptyList(),
            info = seriesInfo(),
            episodes = mapOf(
                "specials" to listOf(testEpisode(id = 9, season = null)),
                "1" to listOf(testEpisode(id = 1, season = 1))
            )
        )

        val series = response.toSeries(seriesId = 1)

        assertEquals(listOf(0, 1), series.seasons?.map { it.seasonNumber })
    }

    @Test
    fun existingSeasonsAreMergedWithTheirEpisodes() {
        val response = SeriesInfoResponse(
            seasons = listOf(
                testSeason(seasonNumber = 1, name = "Season 1"),
                testSeason(seasonNumber = 2, name = "Season 2")
            ),
            info = seriesInfo(),
            episodes = mapOf(
                "1" to listOf(testEpisode(id = 1, season = 1))
                // Season 2 hat keine Episoden und muss entfallen
            )
        )

        val series = response.toSeries(seriesId = 1)

        assertEquals(listOf("Season 1"), series.seasons?.map { it.name })
        assertEquals(listOf(1), series.seasons?.first()?.episodes?.map { it.id })
    }

    @Test
    fun missingInfoFallsBackToEmptyName() {
        val response = SeriesInfoResponse(seasons = null, info = null, episodes = null)

        val series = response.toSeries(seriesId = 5)

        assertEquals("", series.name)
        assertTrue(series.seasons.isNullOrEmpty())
    }

    @Test
    fun releaseDateFallsBackToAlternateField() {
        val info = seriesInfo().copy(releaseDate = null, releaseDateAlt = "2017-12-01")
        val response = SeriesInfoResponse(seasons = null, info = info, episodes = null)

        assertEquals("2017-12-01", response.toSeries(seriesId = 1).releaseDate)
    }
}
