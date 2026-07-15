package com.flintr.tv.data.model

import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Moshi-Adapter: Die API liefert für fehlende Serieninfos ein leeres Array
 * statt eines Objekts — der Adapter muss beide Formate verkraften.
 */
class SeriesInfoResponseAdapterTest {

    private val moshi: Moshi = Moshi.Builder()
        .add(SeriesInfoResponseAdapterFactory())
        .build()

    private val adapter = moshi.adapter(SeriesInfoResponse::class.java)

    @Test
    fun emptyArrayYieldsEmptyResponse() {
        val result = adapter.fromJson("[]")

        assertNotNull(result)
        assertNull(result?.seasons)
        assertNull(result?.info)
        assertNull(result?.episodes)
    }

    @Test
    fun jsonNullYieldsNull() {
        assertNull(adapter.fromJson("null"))
    }

    @Test
    fun regularObjectIsParsedWithNestedEpisodes() {
        val json = """
            {
              "info": {"name": "Dark", "rating_5based": 4.5},
              "episodes": {
                "1": [{"id": 100, "title": "Geheimnisse", "season": 1}]
              },
              "unbekanntes_feld": "wird ignoriert"
            }
        """.trimIndent()

        val result = adapter.fromJson(json)

        assertEquals("Dark", result?.info?.name)
        assertEquals(4.5, result?.info?.rating5Based)
        assertEquals(1, result?.episodes?.get("1")?.size)
        assertEquals("Geheimnisse", result?.episodes?.get("1")?.first()?.title)
    }

    @Test
    fun nonEmptyArrayIsRejected() {
        // Moshi wirft bereits beim endArray() über dem ungelesenen Element
        assertThrows(com.squareup.moshi.JsonDataException::class.java) {
            adapter.fromJson("[{\"id\": 1}]")
        }
    }

    @Test
    fun scalarValueIsRejected() {
        assertThrows(IllegalStateException::class.java) {
            adapter.fromJson("\"kaputt\"")
        }
    }

    @Test
    fun toJsonWritesOnlyPresentFields() {
        val response = SeriesInfoResponse(
            seasons = null,
            info = SeriesInfo(
                name = "Dark",
                cover = null, plot = null, cast = null, director = null,
                genre = null, releaseDate = null, releaseDateAlt = null,
                rating = null, rating5Based = null, categoryId = null,
                youtubeTrailer = null, backdropPath = null
            ),
            episodes = null
        )

        val json = adapter.toJson(response)

        assertTrue(json.contains("\"info\""))
        assertTrue(json.contains("\"Dark\""))
        assertEquals(false, json.contains("\"seasons\""))
        assertEquals(false, json.contains("\"episodes\""))
    }

    @Test
    fun factoryIgnoresOtherTypes() {
        assertNull(
            SeriesInfoResponseAdapterFactory().create(String::class.java, mutableSetOf(), moshi)
        )
    }
}
