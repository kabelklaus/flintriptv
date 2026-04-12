package com.flintr.tv.data.local

import com.squareup.moshi.Moshi
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataCacheServiceTest {

    private val moshi = Moshi.Builder().build()

    @Test
    fun `usable fallback cache accepts complete cached payload with counts`() {
        assertTrue(
            DataCacheService.hasUsableFallbackCacheData(
                moshi = moshi,
                channelsJson = """[{"name":"Live","stream_id":1}]""",
                moviesJson = """[{"name":"Movie","stream_id":2}]""",
                seriesJson = """[{"name":"Series","series_id":3}]""",
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
                moshi = moshi,
                channelsJson = """[{"name":"Live","stream_id":1}]""",
                moviesJson = """[{"name":"Movie","stream_id":2}]""",
                seriesJson = """[{"name":"Series","series_id":3}]""",
                channelsCount = -1,
                moviesCount = -1,
                seriesCount = -1
            )
        )
    }

    @Test
    fun `usable fallback cache rejects broken or incomplete payload`() {
        assertFalse(
            DataCacheService.hasUsableFallbackCacheData(
                moshi = moshi,
                channelsJson = """[{"name":"Live","stream_id":1}]""",
                moviesJson = """not-json""",
                seriesJson = """[{"name":"Series","series_id":3}]""",
                channelsCount = -1,
                moviesCount = -1,
                seriesCount = -1
            )
        )

        assertFalse(
            DataCacheService.hasUsableFallbackCacheData(
                moshi = moshi,
                channelsJson = """[{"name":"Live","stream_id":1}]""",
                moviesJson = """[]""",
                seriesJson = """[{"name":"Series","series_id":3}]""",
                channelsCount = -1,
                moviesCount = -1,
                seriesCount = -1
            )
        )
    }
}
