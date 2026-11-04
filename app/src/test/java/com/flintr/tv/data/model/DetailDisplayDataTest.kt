package com.flintr.tv.data.model

import com.flintr.tv.data.local.imdb.ImdbEnrichment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Detail-Mapping: Genre und Rating kommen EXKLUSIV aus dem IMDb-Enrichment
 * (Chris-Spec 30.10.). Ohne Match bleiben beide null — die Xtream-Werte aus
 * get_vod_info duerfen niemals in die Anzeige-Daten durchsickern.
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
    fun `with imdb match genres and rating are taken from enrichment`() {
        val enrichment = ImdbEnrichment(
            rating = 8.8,
            numVotes = 2_500_000,
            genres = listOf("Action", "Sci-Fi", "Thriller")
        )

        val detail = mapVodInfoToDetail(xtreamInfo, imdbLookup = { enrichment })

        assertEquals(listOf("Action", "Sci-Fi", "Thriller"), detail?.genres)
        assertEquals(8.8, detail?.rating ?: Double.NaN, 0.0)
        // Xtream-Werte duerfen nicht durchschlagen.
        assertFalse(detail?.genres?.contains("Science Fiction") == true)
        assertFalse(detail?.rating == 9.9)
    }

    @Test
    fun `without imdb match genre and rating stay null`() {
        val detail = mapVodInfoToDetail(xtreamInfo, imdbLookup = { null })

        assertNull(detail?.genres)
        assertNull(detail?.rating)
    }

    @Test
    fun `xtream fields are mapped for display`() {
        val detail = mapVodInfoToDetail(xtreamInfo, imdbLookup = { null })

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
        val detail = mapVodInfoToDetail(xtreamInfo.copy(runtime = null), imdbLookup = { null })

        assertEquals("02:28:00", detail?.runtime)
    }

    @Test
    fun `poster falls back to cover_big`() {
        val detail = mapVodInfoToDetail(xtreamInfo.copy(movieImage = null), imdbLookup = { null })

        assertEquals("https://img/cover_big.jpg", detail?.posterUrl)
    }

    @Test
    fun `lookup uses title and release year`() {
        var lookupKey: String? = null

        mapVodInfoToDetail(xtreamInfo) { key ->
            lookupKey = key
            null
        }

        assertEquals("Inception (2010)", lookupKey)
    }

    @Test
    fun `blank title skips imdb lookup`() {
        var called = false

        val detail = mapVodInfoToDetail(
            xtreamInfo.copy(name = "  ", releasedate = null)
        ) {
            called = true
            null
        }

        assertFalse(called)
        assertNull(detail?.genres)
        assertNull(detail?.rating)
    }

    @Test
    fun `null info yields null detail`() {
        assertNull(mapVodInfoToDetail(null, imdbLookup = { null }))
    }

    @Test
    fun `empty cast string yields empty list`() {
        val detail = mapVodInfoToDetail(xtreamInfo.copy(cast = "  "), imdbLookup = { null })

        assertTrue(detail?.cast?.isEmpty() == true)
    }
}
