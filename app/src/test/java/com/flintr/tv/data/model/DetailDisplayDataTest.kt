package com.flintr.tv.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Detail-Mapping: Genre und Rating kommen EXKLUSIV aus TMDB und bleiben beim
 * Basis-Mapping null — die Xtream-Werte aus get_vod_info duerfen niemals in die
 * Anzeige-Daten durchsickern.
 */
class DetailDisplayDataTest {

    private val xtreamInfo = VodInfo(
        name = "Inception",
        plot = "Ein Dieb stiehlt Traeume.",
        cast = "Leonardo DiCaprio, Elliot Page, Tom Hardy",
        director = "Christopher Nolan",
        genre = "Action, Science Fiction",
        duration = "02:28:00",
        durationSecs = "8880",
        runtime = "148 min",
        releasedate = "2010-07-16",
        rating = "9.9",
        youtubeTrailer = "YoHD9XEInc0",
        backdropPath = listOf("https://img/backdrop.jpg"),
        movieImage = "https://img/poster.jpg",
        coverBig = "https://img/cover_big.jpg",
        tmdbId = "27205"
    )

    @Test
    fun `without tmdb meta genre and rating stay null`() {
        val detail = mapVodInfoToDetail(xtreamInfo)

        assertNull(detail?.genres)
        assertNull(detail?.rating)
    }

    @Test
    fun `xtream fields are mapped for display`() {
        val detail = mapVodInfoToDetail(xtreamInfo)

        assertEquals("Inception", detail?.title)
        assertEquals("Ein Dieb stiehlt Traeume.", detail?.plot)
        assertEquals(
            listOf("Leonardo DiCaprio", "Elliot Page", "Tom Hardy"),
            detail?.cast
        )
        assertEquals("Christopher Nolan", detail?.director)
        assertEquals("148 min", detail?.runtime)
        assertEquals("2010-07-16", detail?.releaseDate)
        assertEquals("YoHD9XEInc0", detail?.youtubeTrailer)
        assertEquals(listOf("https://img/backdrop.jpg"), detail?.backdropPath)
        assertEquals("https://img/poster.jpg", detail?.posterUrl)
        assertEquals("27205", detail?.tmdbId)
    }

    @Test
    fun `runtime falls back to duration when runtime is missing`() {
        val detail = mapVodInfoToDetail(xtreamInfo.copy(runtime = null))

        assertEquals("02:28:00", detail?.runtime)
    }

    @Test
    fun `poster falls back to cover_big`() {
        val detail = mapVodInfoToDetail(xtreamInfo.copy(movieImage = null))

        assertEquals("https://img/cover_big.jpg", detail?.posterUrl)
    }

    @Test
    fun `null info yields null detail`() {
        assertNull(mapVodInfoToDetail(null))
    }

    @Test
    fun `empty cast string yields empty list`() {
        val detail = mapVodInfoToDetail(xtreamInfo.copy(cast = "  "))

        assertTrue(detail?.cast?.isEmpty() == true)
    }
}
