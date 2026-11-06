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

    // Spiegelt die Moshi-Konfiguration aus AppModule.provideMoshi().
    private val moshi: Moshi = Moshi.Builder()
        .add(SeriesInfoResponseAdapterFactory())
        .add(EmptyArrayAsObjectAdapterFactory(setOf(EpisodeInfo::class.java, SeriesInfo::class.java)))
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
    fun episodeInfoEmptyArrayIsTolerated() {
        // Manche Xtream-Panels liefern pro Episode "info": [] statt eines Objekts.
        // Das darf nicht die ganze Serie killen (Crash-Pfad: $.episodes.21[133].info).
        val json = """
            {
              "episodes": {
                "21": [
                  {"id": 500, "title": "Mit Info", "info": {"plot": "Spannend", "duration": "00:42:00"}},
                  {"id": 501, "title": "Ohne Info", "info": []}
                ]
              }
            }
        """.trimIndent()

        val result = adapter.fromJson(json)

        val episodes = result?.episodes?.get("21")
        assertEquals(2, episodes?.size)
        assertEquals("Spannend", episodes?.get(0)?.info?.plot)
        assertNull(episodes?.get(1)?.info)
        assertEquals("Ohne Info", episodes?.get(1)?.title)
    }

    @Test
    fun topLevelInfoEmptyArrayIsTolerated() {
        // Auch das Serien-Level "info" kann als leeres Array kommen.
        val json = """
            {
              "info": [],
              "episodes": {"1": [{"id": 1, "title": "Pilot"}]}
            }
        """.trimIndent()

        val result = adapter.fromJson(json)

        assertNotNull(result)
        assertNull(result?.info)
        assertEquals("Pilot", result?.episodes?.get("1")?.first()?.title)
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
