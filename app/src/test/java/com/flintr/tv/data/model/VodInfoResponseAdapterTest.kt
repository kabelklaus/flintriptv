package com.flintr.tv.data.model

import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Moshi-Adapter fuer get_vod_info: Das Panel liefert `info` normalerweise als
 * Objekt, bei fehlenden Metadaten aber als leeres Array (`[]`) — das darf die
 * Detailseite nicht mit einer JsonDataException killen.
 */
class VodInfoResponseAdapterTest {

    // Spiegelt die Moshi-Konfiguration aus AppModule.provideMoshi().
    private val moshi: Moshi = Moshi.Builder()
        .add(VodInfoResponseAdapterFactory())
        .build()

    private val adapter = moshi.adapter(VodInfoResponse::class.java)

    @Test
    fun `info as object parses fields`() {
        val json = """
            {
              "info": {
                "name": "Inception",
                "plot": "Ein Dieb stiehlt Traeume.",
                "cast": "Leonardo DiCaprio, Elliot Page",
                "director": "Christopher Nolan",
                "genre": "Action, Science Fiction",
                "duration": "02:28:00",
                "duration_secs": "8880",
                "runtime": "148 min",
                "releasedate": "2010-07-16",
                "rating": "8.8",
                "youtube_trailer": "YoHD9XEInc0",
                "backdrop_path": ["https://img/backdrop.jpg"],
                "movie_image": "https://img/poster.jpg",
                "cover_big": "https://img/cover_big.jpg",
                "tmdb_id": "27205"
              },
              "unbekanntes_feld": "wird ignoriert"
            }
        """.trimIndent()

        val result = adapter.fromJson(json)

        val info = result?.info
        assertNotNull(info)
        assertEquals("Inception", info?.name)
        assertEquals("Ein Dieb stiehlt Traeume.", info?.plot)
        assertEquals("Leonardo DiCaprio, Elliot Page", info?.cast)
        assertEquals("Christopher Nolan", info?.director)
        assertEquals("Action, Science Fiction", info?.genre)
        assertEquals("02:28:00", info?.duration)
        assertEquals("8880", info?.durationSecs)
        assertEquals("148 min", info?.runtime)
        assertEquals("2010-07-16", info?.releasedate)
        assertEquals("8.8", info?.rating)
        assertEquals("YoHD9XEInc0", info?.youtubeTrailer)
        assertEquals(listOf("https://img/backdrop.jpg"), info?.backdropPath)
        assertEquals("https://img/poster.jpg", info?.movieImage)
        assertEquals("https://img/cover_big.jpg", info?.coverBig)
        assertEquals("27205", info?.tmdbId)
    }

    @Test
    fun `info as empty array yields null info`() {
        val result = adapter.fromJson("""{"info": []}""")

        assertNotNull(result)
        assertNull(result?.info)
    }

    @Test
    fun `info as json null yields null info`() {
        val result = adapter.fromJson("""{"info": null}""")

        assertNotNull(result)
        assertNull(result?.info)
    }

    @Test
    fun `top level empty array yields empty response`() {
        val result = adapter.fromJson("[]")

        assertNotNull(result)
        assertNull(result?.info)
    }

    @Test
    fun `json null yields null`() {
        assertNull(adapter.fromJson("null"))
    }

    @Test
    fun `factory ignores other types`() {
        assertNull(
            VodInfoResponseAdapterFactory().create(String::class.java, mutableSetOf(), moshi)
        )
    }
}
