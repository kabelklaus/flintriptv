package com.flintr.tv.data.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DataCacheServiceTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun jsonFile(name: String, content: String): File =
        tmp.newFile(name).apply { writeText(content) }

    @Test
    fun `usable fallback cache accepts complete cached payload with counts`() {
        assertTrue(
            DataCacheService.hasUsableFallbackCacheData(
                channelsFile = jsonFile("channels.json", """[{"name":"Live","stream_id":1}]"""),
                moviesFile = jsonFile("movies.json", """[{"name":"Movie","stream_id":2}]"""),
                seriesFile = jsonFile("series.json", """[{"name":"Series","series_id":3}]"""),
                channelsCount = 1,
                moviesCount = 1,
                seriesCount = 1
            )
        )
    }

    @Test
    fun `usable fallback cache parses legacy payload without counts`() {
        assertTrue(
            DataCacheService.hasUsableFallbackCacheData(
                channelsFile = jsonFile("channels.json", """[{"name":"Live","stream_id":1}]"""),
                moviesFile = jsonFile("movies.json", """[{"name":"Movie","stream_id":2}]"""),
                seriesFile = jsonFile("series.json", """[{"name":"Series","series_id":3}]"""),
                channelsCount = -1,
                moviesCount = -1,
                seriesCount = -1
            )
        )
    }

    @Test
    fun `usable fallback cache rejects broken or incomplete payload`() {
        val channels = jsonFile("channels.json", """[{"name":"Live","stream_id":1}]""")
        val series = jsonFile("series.json", """[{"name":"Series","series_id":3}]""")

        assertFalse(
            DataCacheService.hasUsableFallbackCacheData(
                channelsFile = channels,
                moviesFile = jsonFile("broken.json", """not-json"""),
                seriesFile = series,
                channelsCount = -1,
                moviesCount = -1,
                seriesCount = -1
            )
        )

        assertFalse(
            DataCacheService.hasUsableFallbackCacheData(
                channelsFile = channels,
                moviesFile = jsonFile("empty_array.json", """[]"""),
                seriesFile = series,
                channelsCount = -1,
                moviesCount = -1,
                seriesCount = -1
            )
        )
    }

    @Test
    fun `usable fallback cache rejects missing or empty files even with counts`() {
        val channels = jsonFile("channels.json", """[{"name":"Live","stream_id":1}]""")
        val series = jsonFile("series.json", """[{"name":"Series","series_id":3}]""")

        assertFalse(
            DataCacheService.hasUsableFallbackCacheData(
                channelsFile = channels,
                moviesFile = File(tmp.root, "does_not_exist.json"),
                seriesFile = series,
                channelsCount = 1,
                moviesCount = 1,
                seriesCount = 1
            )
        )

        assertFalse(
            DataCacheService.hasUsableFallbackCacheData(
                channelsFile = channels,
                moviesFile = jsonFile("zero_bytes.json", ""),
                seriesFile = series,
                channelsCount = 1,
                moviesCount = 1,
                seriesCount = 1
            )
        )

        assertFalse(
            DataCacheService.hasUsableFallbackCacheData(
                channelsFile = null,
                moviesFile = channels,
                seriesFile = series,
                channelsCount = 1,
                moviesCount = 1,
                seriesCount = 1
            )
        )
    }
}
