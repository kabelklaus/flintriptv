package com.flintr.tv.data.tmdb

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
class TmdbMetaCacheTest {

    private val now = 1_700_000_000_000L
    private val tempDir: File = Files.createTempDirectory("tmdb-meta-cache").toFile()

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun cache(nowMs: Long = now): TmdbMetaCache =
        TmdbMetaCache(tempDir, { nowMs })

    private fun meta(id: Int): TmdbMeta =
        TmdbMeta(
            tmdbId = id,
            rating = 8.0,
            voteCount = 100,
            genres = listOf(TmdbGenre(18, "Drama"))
        )

    private fun entry(
        meta: TmdbMeta? = meta(1),
        fetchedAtMs: Long = now,
        negative: Boolean = false,
        discoverIds: List<Int>? = null
    ) = TmdbMetaCacheEntry(meta, fetchedAtMs, negative, discoverIds)

    @Test
    fun `Roundtrip liefert identische Entries aus neuer Instanz`() = runBlocking {
        val entries = mapOf(
            "movie:1" to entry(meta(1), now),
            "tv:2" to entry(meta(2), now + 1, discoverIds = listOf(2, 3))
        )
        cache().putAll(entries)

        val loaded = cache().loadAll()

        assertEquals(entries, loaded)
    }

    @Test
    fun `isFresh respektiert die 24h-Grenze`() {
        assertTrue(TmdbMetaCache.isFresh(entry(fetchedAtMs = now - (TmdbMetaCache.TTL_MS - 60_000)), now))
        assertFalse(TmdbMetaCache.isFresh(entry(fetchedAtMs = now - (TmdbMetaCache.TTL_MS + 60_000)), now))
    }

    @Test
    fun `negative Entry wird geladen und ist nie fresh`() = runBlocking {
        val negative = entry(meta = null, fetchedAtMs = now, negative = true)
        cache().putAll(mapOf("movie:404" to negative))

        val loaded = cache().loadAll()

        assertEquals(negative, loaded["movie:404"])
        assertFalse(TmdbMetaCache.isFresh(negative, now))
        assertFalse(TmdbMetaCache.isFresh(negative, now + 100L * 24 * 60 * 60 * 1000))
    }

    @Test
    fun `Cap-Pruning behaelt die 5000 neuesten Entries`() = runBlocking {
        val entries = LinkedHashMap<String, TmdbMetaCacheEntry>()
        for (index in 0 until 5010) {
            entries["movie:$index"] = entry(meta(index), now + index)
        }

        cache().putAll(entries)
        val loaded = cache().loadAll()

        assertEquals(TmdbMetaCache.CAP, loaded.size)
        for (index in 0 until 10) {
            assertFalse(loaded.containsKey("movie:$index"))
        }
        assertTrue(loaded.containsKey("movie:10"))
        assertTrue(loaded.containsKey("movie:5009"))
    }

    @Test
    fun `korrupte Datei liefert leere Map und putAll repariert sie`() = runBlocking {
        File(tempDir, TmdbMetaCache.FILE_NAME).writeText("{kaputt")

        val broken = cache()
        assertTrue(broken.loadAll().isEmpty())

        broken.putAll(mapOf("movie:1" to entry(meta(1))))
        val loaded = cache().loadAll()

        assertEquals(1, loaded.size)
        assertEquals(meta(1), loaded["movie:1"]?.meta)
    }

    @Test
    fun `fehlende Datei liefert leere Map`() = runBlocking {
        assertTrue(cache().loadAll().isEmpty())
    }

    @Test
    fun `discoverIds roundtrippen mit meta null`() = runBlocking {
        val discover = entry(
            meta = null,
            fetchedAtMs = now,
            negative = false,
            discoverIds = listOf(11, 22, 33)
        )
        cache().putAll(mapOf("discover:trending" to discover))

        val loaded = cache().loadAll()["discover:trending"]

        assertEquals(discover, loaded)
        assertNull(loaded?.meta)
        assertFalse(loaded!!.negative)
    }

    @Test
    fun `putAll mergt neue Keys und ersetzt vorhandene`() = runBlocking {
        val instance = cache()
        instance.putAll(
            mapOf(
                "movie:1" to entry(meta(1), now),
                "movie:2" to entry(meta(2), now + 1)
            )
        )
        instance.putAll(
            mapOf(
                "movie:1" to entry(meta(3), now + 2),
                "movie:3" to entry(meta(4), now + 3)
            )
        )

        val loaded = instance.loadAll()

        assertEquals(3, loaded.size)
        assertEquals(meta(3), loaded["movie:1"]?.meta)
        assertEquals(meta(2), loaded["movie:2"]?.meta)
        assertEquals(meta(4), loaded["movie:3"]?.meta)
    }
}
