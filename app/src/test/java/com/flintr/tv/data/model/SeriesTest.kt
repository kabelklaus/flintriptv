package com.flintr.tv.data.model

import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regressionstest fuer den Xtream-`tmdb`-Feldnamen: die API liefert die TMDB-ID der Serie
 * als `"tmdb"`, nicht als `"tmdb_id"` - mit dem falschen JSON-Namen parst [Series.tmdbId]
 * immer null, siehe TMDB-Cast-Fotos-Slice.
 */
class SeriesTest {

    private val moshi = Moshi.Builder().build()
    private val adapter = moshi.adapter(Series::class.java)

    @Test
    fun `tmdb Feld aus Xtream-JSON wird als tmdbId geparst`() {
        val json = """
            {
                "name": "Serie",
                "series_id": 42,
                "tmdb": "123"
            }
        """.trimIndent()

        val series = adapter.fromJson(json)

        assertEquals("123", series?.tmdbId)
    }

    @Test
    fun `ohne tmdb Feld bleibt tmdbId null`() {
        val json = """
            {
                "name": "Serie",
                "series_id": 42
            }
        """.trimIndent()

        val series = adapter.fromJson(json)

        assertEquals(null, series?.tmdbId)
    }
}
