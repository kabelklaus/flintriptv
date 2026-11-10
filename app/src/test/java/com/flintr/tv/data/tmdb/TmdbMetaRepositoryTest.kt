package com.flintr.tv.data.tmdb

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

@RunWith(RobolectricTestRunner::class)
class TmdbMetaRepositoryTest {

    private val tempDir: File = Files.createTempDirectory("tmdb-meta-repo").toFile()
    private val nowRef = AtomicLong(System.currentTimeMillis())

    private lateinit var cache: TmdbMetaCache
    private lateinit var client: TmdbMetaClient

    @Before
    fun setUp() {
        cache = TmdbMetaCache(tempDir, { nowRef.get() })
        client = mockk()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun repository(): TmdbMetaRepository =
        TmdbMetaRepository(client, cache, TitleNormalizer, Dispatchers.IO)

    private fun meta(id: Int, rating: Double = 8.0): TmdbMeta =
        TmdbMeta(
            tmdbId = id,
            rating = rating,
            voteCount = 100,
            genres = listOf(TmdbGenre(18, "Drama"))
        )

    private fun entry(
        meta: TmdbMeta?,
        fetchedAtMs: Long = nowRef.get(),
        negative: Boolean = false
    ) = TmdbMetaCacheEntry(meta = meta, fetchedAtMs = fetchedAtMs, negative = negative)

    private fun staleAt(): Long = nowRef.get() - TmdbMetaCache.TTL_MS - 60_000

    @Test
    fun `frischer Cache-Hit loest keinen Client-Call aus`() = runBlocking {
        val cached = meta(1)
        cache.putAll(mapOf("movie:1" to entry(cached, nowRef.get())))
        val repo = repository()

        repo.request("movie", "1")

        coVerify(exactly = 0) { client.movieMeta(any()) }
        assertEquals(cached, repo.metaFlow.value["movie:1"])
    }

    @Test
    fun `Miss fetcht und legt Meta im Flow ab`() = runBlocking {
        coEvery { client.movieMeta("1") } returns TmdbResult.Data(meta(1))
        val repo = repository()

        repo.request("movie", "1")

        coVerify(exactly = 1) { client.movieMeta("1") }
        assertEquals(meta(1), repo.metaFlow.value["movie:1"])
    }

    @Test
    fun `sequenzielles Duplikat fetcht nur einmal`() = runBlocking {
        coEvery { client.movieMeta("1") } returns TmdbResult.Data(meta(1))
        val repo = repository()

        repo.request("movie", "1")
        repo.request("movie", "1")

        coVerify(exactly = 1) { client.movieMeta("1") }
    }

    @Test
    fun `Negative wird 24h gecacht und nicht in den Flow gelegt`() = runBlocking {
        coEvery { client.movieMeta("1") } returns TmdbResult.Negative
        val repo = repository()

        repo.request("movie", "1")

        val stored = cache.loadAll()["movie:1"]
        assertTrue(stored!!.negative)
        assertFalse(repo.metaFlow.value.containsKey("movie:1"))

        repo.request("movie", "1")
        coVerify(exactly = 1) { client.movieMeta("1") }
    }

    @Test
    fun `Transient schreibt nichts und wird erneut versucht`() = runBlocking {
        coEvery { client.movieMeta("1") } returns TmdbResult.Transient
        val repo = repository()

        repo.request("movie", "1")
        assertNull(cache.loadAll()["movie:1"])

        repo.request("movie", "1")
        coVerify(exactly = 2) { client.movieMeta("1") }
    }

    @Test
    fun `Transient laesst Stale-Meta und Flow unveraendert`() = runBlocking {
        val stale = entry(meta(1), staleAt())
        cache.putAll(mapOf("movie:1" to stale))
        coEvery { client.movieMeta("1") } returns TmdbResult.Transient
        val repo = repository()

        repo.request("movie", "1")

        assertEquals(meta(1), repo.metaFlow.value["movie:1"])
        assertEquals(stale.fetchedAtMs, cache.loadAll()["movie:1"]!!.fetchedAtMs)
    }

    @Test
    fun `Stale-Refetch aktualisiert fetchedAt und ist danach fresh`() = runBlocking {
        cache.putAll(mapOf("movie:1" to entry(meta(1), staleAt())))
        coEvery { client.movieMeta("1") } returns TmdbResult.Data(meta(1, rating = 9.0))
        val repo = repository()

        repo.request("movie", "1")

        coVerify(exactly = 1) { client.movieMeta("1") }
        val stored = cache.loadAll()["movie:1"]!!
        assertEquals(9.0, stored.meta!!.rating, 0.0)
        assertTrue(TmdbMetaCache.isFresh(stored, nowRef.get()))
        assertTrue(stored.fetchedAtMs > staleAt())
    }

    @Test
    fun `Batch von vier IDs wird je einmal gefetcht und danach nicht erneut`() = runBlocking {
        val ids = listOf(1, 2, 3, 4)
        val hits = AtomicInteger(0)
        ids.forEach { id ->
            coEvery { client.movieMeta(id.toString()) } answers {
                hits.incrementAndGet()
                TmdbResult.Data(meta(id))
            }
        }
        val repo = repository()

        coroutineScope {
            ids.map { id -> async { repo.request("movie", id.toString()) } }.awaitAll()
        }

        assertEquals(4, hits.get())
        assertEquals(4, repo.metaFlow.value.size)

        coroutineScope {
            ids.map { id -> async { repo.request("movie", id.toString()) } }.awaitAll()
        }

        assertEquals(4, hits.get())
    }

    @Test
    fun `detailMeta sucht, fetcht Details und legt Meta unter beiden Keys ab`() = runBlocking {
        coEvery { client.searchMovieId("marley me", 2008) } returns TmdbResult.Data(14306)
        coEvery { client.movieMeta("14306") } returns TmdbResult.Data(meta(14306))
        val repo = repository()

        val result = repo.detailMeta(
            "movie",
            null,
            "EN - Marley & Me (2008) (Ger Sub)",
            null
        )

        assertEquals(meta(14306), result)
        assertEquals(meta(14306), repo.metaFlow.value["movie:14306"])
        assertEquals(meta(14306), repo.metaFlow.value["search:movie:marley me|2008"])

        repo.detailMeta("movie", null, "EN - Marley & Me (2008) (Ger Sub)", null)

        coVerify(exactly = 1) { client.searchMovieId("marley me", 2008) }
        coVerify(exactly = 1) { client.movieMeta("14306") }
    }

    @Test
    fun `detailMeta cached Search-Negative und sucht kein zweites Mal`() = runBlocking {
        coEvery { client.searchMovieId("nichts", null) } returns TmdbResult.Negative
        val repo = repository()

        val first = repo.detailMeta("movie", null, "Nichts", null)
        val second = repo.detailMeta("movie", null, "Nichts", null)

        assertNull(first)
        assertNull(second)
        assertTrue(cache.loadAll()["search:movie:nichts|"]!!.negative)
        coVerify(exactly = 1) { client.searchMovieId("nichts", null) }
        coVerify(exactly = 0) { client.movieMeta(any()) }
    }

    @Test
    fun `detailMeta mit numerischer ID umgeht die Suche`() = runBlocking {
        coEvery { client.movieMeta("238") } returns TmdbResult.Data(meta(238))
        val repo = repository()

        val result = repo.detailMeta("movie", "238", "Der Pate", 1972)

        assertEquals(meta(238), result)
        coVerify(exactly = 0) { client.searchMovieId(any(), any()) }
    }

    @Test
    fun `metaFlow filtert Negative heraus`() = runBlocking {
        coEvery { client.movieMeta("1") } returns TmdbResult.Negative
        coEvery { client.movieMeta("2") } returns TmdbResult.Data(meta(2))
        val repo = repository()

        repo.request("movie", "1")
        repo.request("movie", "2")

        assertEquals(1, repo.metaFlow.value.size)
        assertFalse(repo.metaFlow.value.containsKey("movie:1"))
        assertTrue(repo.metaFlow.value.containsKey("movie:2"))
    }
}
