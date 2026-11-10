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
    private lateinit var discoverClient: TmdbDiscoverClient

    @Before
    fun setUp() {
        cache = TmdbMetaCache(tempDir, { nowRef.get() })
        client = mockk()
        discoverClient = mockk()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun repository(): TmdbMetaRepository =
        TmdbMetaRepository(client, discoverClient, cache, TitleNormalizer, Dispatchers.IO)

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
        negative: Boolean = false,
        discoverIds: List<Int>? = null
    ) = TmdbMetaCacheEntry(
        meta = meta,
        fetchedAtMs = fetchedAtMs,
        negative = negative,
        discoverIds = discoverIds
    )

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

    @Test
    fun `discoverMovieIds cached am sortierten Genre-Key und trifft beim zweiten Call`() = runBlocking {
        coEvery { discoverClient.discoverMovies(listOf(18, 28), 1) } returns
            TmdbResult.Data(listOf(1560520, 980431))
        val repo = repository()

        val first = repo.discoverMovieIds(listOf(18, 28), pages = 1)
        val stored = cache.loadAll()["discover:movie:18|28:1"]

        assertEquals(listOf(1560520, 980431), first)
        assertEquals(listOf(1560520, 980431), stored!!.discoverIds)
        assertFalse(stored.negative)
        assertTrue(TmdbMetaCache.isFresh(stored, nowRef.get()))

        val second = repo.discoverMovieIds(listOf(28, 18), pages = 1)

        assertEquals(first, second)
        coVerify(exactly = 1) { discoverClient.discoverMovies(listOf(18, 28), 1) }
    }

    @Test
    fun `discover Transient liefert leer und schreibt nichts`() = runBlocking {
        coEvery { discoverClient.discoverMovies(listOf(18), 1) } returns TmdbResult.Transient
        val repo = repository()

        val first = repo.discoverMovieIds(listOf(18), pages = 1)
        assertTrue(first.isEmpty())
        assertNull(cache.loadAll()["discover:movie:18:1"])

        val second = repo.discoverMovieIds(listOf(18), pages = 1)
        assertTrue(second.isEmpty())
        coVerify(exactly = 2) { discoverClient.discoverMovies(listOf(18), 1) }
    }

    @Test
    fun `discover Transient liefert abgelaufene stale IDs`() = runBlocking {
        cache.putAll(
            mapOf(
                "discover:movie:18|28:1" to entry(
                    meta = null,
                    fetchedAtMs = staleAt(),
                    discoverIds = listOf(1, 2, 3)
                )
            )
        )
        coEvery { discoverClient.discoverMovies(listOf(18, 28), 1) } returns TmdbResult.Transient
        val repo = repository()

        val ids = repo.discoverMovieIds(listOf(18, 28), pages = 1)

        assertEquals(listOf(1, 2, 3), ids)
    }

    // -- resolveCardMeta -------------------------------------------------

    @Test
    fun `resolveCardMeta mit tmdbId umgeht die Suche`() = runBlocking {
        coEvery { client.movieMeta("238") } returns TmdbResult.Data(meta(238))
        val repo = repository()

        val result = repo.resolveCardMeta("movie", "Der Pate", 1972, "238")

        assertEquals(meta(238), result)
        coVerify(exactly = 0) { client.searchCandidates(any(), any(), any()) }
    }

    @Test
    fun `resolveCardMeta strikter Match cached Search-Key positiv und sucht nur einmal`() = runBlocking {
        coEvery { client.searchCandidates("movie", "testfilm", 2020) } returns
            FetchResult.Data(listOf(TmdbSearchCandidate(42, "Testfilm", 2020)))
        coEvery { client.movieMeta("42") } returns TmdbResult.Data(meta(42))
        val repo = repository()

        val first = repo.resolveCardMeta("movie", "Testfilm", 2020, null)
        val stored = cache.loadAll()["search:movie:testfilm|2020"]
        val second = repo.resolveCardMeta("movie", "Testfilm", 2020, null)

        assertEquals(meta(42), first)
        assertEquals(meta(42), second)
        assertFalse(stored!!.negative)
        assertEquals(42, stored.meta?.tmdbId)
        coVerify(exactly = 1) { client.searchCandidates("movie", "testfilm", 2020) }
        coVerify(exactly = 1) { client.movieMeta("42") }
    }

    @Test
    fun `resolveCardMeta ohne Match schreibt Negative und sucht kein zweites Mal`() = runBlocking {
        coEvery { client.searchCandidates("movie", "testfilm", 2020) } returns
            FetchResult.Data(listOf(TmdbSearchCandidate(7, "Anderer Film", 2020)))
        val repo = repository()

        val first = repo.resolveCardMeta("movie", "Testfilm", 2020, null)
        val second = repo.resolveCardMeta("movie", "Testfilm", 2020, null)

        assertNull(first)
        assertNull(second)
        assertTrue(cache.loadAll()["search:movie:testfilm|2020"]!!.negative)
        coVerify(exactly = 1) { client.searchCandidates("movie", "testfilm", 2020) }
        coVerify(exactly = 0) { client.movieMeta(any()) }
    }

    @Test
    fun `resolveCardMeta fuzzy Titel matcht via Normalizer`() = runBlocking {
        coEvery { client.searchCandidates("movie", "marley me", 2008) } returns
            FetchResult.Data(listOf(TmdbSearchCandidate(14306, "Marley & Me", 2008)))
        coEvery { client.movieMeta("14306") } returns TmdbResult.Data(meta(14306))
        val repo = repository()

        val result = repo.resolveCardMeta(
            "movie",
            "EN - MARLEY & ME (2008) (Ger Sub)",
            null,
            null
        )

        assertEquals(meta(14306), result)
        assertTrue(cache.loadAll().containsKey("search:movie:marley me|2008"))
    }

    @Test
    fun `resolveCardMeta Jahr-Abweichung groesser eins matcht nicht`() = runBlocking {
        coEvery { client.searchCandidates("movie", "testfilm", 2020) } returns
            FetchResult.Data(listOf(TmdbSearchCandidate(42, "Testfilm", 2023)))
        val repo = repository()

        val result = repo.resolveCardMeta("movie", "Testfilm", 2020, null)

        assertNull(result)
        assertTrue(cache.loadAll()["search:movie:testfilm|2020"]!!.negative)
        coVerify(exactly = 0) { client.movieMeta(any()) }
    }

    // -- Katalog-Praefix-Strip -------------------------------------------

    @Test
    fun `resolveCardMeta strippt A-plus-Praefix vor der Suche`() = runBlocking {
        coEvery { client.searchCandidates("movie", "luck", 2022) } returns
            FetchResult.Data(listOf(TmdbSearchCandidate(100, "Luck", 2022)))
        coEvery { client.movieMeta("100") } returns TmdbResult.Data(meta(100))
        val repo = repository()

        val result = repo.resolveCardMeta("movie", "A+ Luck (2022)", null, null)

        assertEquals(meta(100), result)
        coVerify(exactly = 1) { client.searchCandidates("movie", "luck", 2022) }
    }

    @Test
    fun `resolveCardMeta strippt EN-Dash-Praefix vor der Suche`() = runBlocking {
        coEvery { client.searchCandidates("movie", "the handmaid s tale", 2017) } returns
            FetchResult.Data(listOf(TmdbSearchCandidate(200, "The Handmaid's Tale", 2017)))
        coEvery { client.movieMeta("200") } returns TmdbResult.Data(meta(200))
        val repo = repository()

        val result = repo.resolveCardMeta("movie", "EN - The Handmaid's Tale (2017)", null, null)

        assertEquals(meta(200), result)
        coVerify(exactly = 1) { client.searchCandidates("movie", "the handmaid s tale", 2017) }
    }

    @Test
    fun `resolveCardMeta strippt E T mit Punkt nicht`() = runBlocking {
        coEvery { client.searchCandidates("movie", "e t", 1982) } returns
            FetchResult.Data(listOf(TmdbSearchCandidate(300, "E.T.", 1982)))
        coEvery { client.movieMeta("300") } returns TmdbResult.Data(meta(300))
        val repo = repository()

        val result = repo.resolveCardMeta("movie", "E.T. (1982)", null, null)

        assertEquals(meta(300), result)
        coVerify(exactly = 1) { client.searchCandidates("movie", "e t", 1982) }
    }

    @Test
    fun `Suchtitel mit Pipe kollidiert nicht mit dem Key-Trennzeichen`() = runBlocking {
        coEvery { client.searchCandidates("movie", "film | story", 2021) } returns
            FetchResult.Data(listOf(TmdbSearchCandidate(400, "Film | Story", 2021)))
        coEvery { client.movieMeta("400") } returns TmdbResult.Data(meta(400))
        val repo = repository()

        val result = repo.resolveCardMeta("movie", "Film | Story (2021)", 2021, null)

        assertEquals(meta(400), result)
        assertTrue(cache.loadAll().containsKey("search:movie:film   story|2021"))
    }

    // -- searchDetail synthetischer Marker --------------------------------

    @Test
    fun `searchDetail ignoriert frischen synthetischen Search-Key-Treffer`() = runBlocking {
        val synthetic = TmdbMeta(tmdbId = 42, rating = 0.0, voteCount = 0, genres = emptyList())
        cache.putAll(mapOf("search:movie:testfilm|2020" to entry(synthetic, nowRef.get())))
        coEvery { client.searchMovieId("testfilm", 2020) } returns TmdbResult.Data(42)
        coEvery { client.movieMeta("42") } returns TmdbResult.Data(meta(42))
        val repo = repository()

        val result = repo.detailMeta("movie", null, "Testfilm", 2020)

        assertEquals(meta(42), result)
        coVerify(exactly = 1) { client.searchMovieId("testfilm", 2020) }
        coVerify(exactly = 1) { client.movieMeta("42") }
    }

    // -- cardMetaState ----------------------------------------------------

    @Test
    fun `cardMetaState mit negativ gecachter ID liefert Negative`() = runBlocking {
        cache.putAll(mapOf("movie:5" to entry(null, nowRef.get(), negative = true)))
        val repo = repository()

        val state = repo.cardMetaState("movie", "5", null, null)

        assertEquals(TmdbCardState.Negative, state)
        coVerify(exactly = 0) { client.movieMeta(any()) }
    }

    @Test
    fun `cardMetaState ohne tmdbId und ohne Match liefert Negative`() = runBlocking {
        coEvery { client.searchCandidates("movie", "testfilm", 2020) } returns
            FetchResult.Data(listOf(TmdbSearchCandidate(7, "Anderer Film", 2020)))
        val repo = repository()

        val state = repo.cardMetaState("movie", null, "Testfilm", 2020)

        assertEquals(TmdbCardState.Negative, state)
    }

    @Test
    fun `cardMetaState ohne ID und ohne Titel liefert Negative`() = runBlocking {
        val repo = repository()

        val state = repo.cardMetaState("movie", null, null, null)

        assertEquals(TmdbCardState.Negative, state)
    }

    @Test
    fun `recommendationsFor cached Hit und Miss am rec-Key`() = runBlocking {
        coEvery { discoverClient.movieRecommendations("238") } returns
            TmdbResult.Data(listOf(240, 242))
        val repo = repository()

        val first = repo.recommendationsFor("movie", "238")
        val stored = cache.loadAll()["discover:rec:movie:238"]

        assertEquals(listOf(240, 242), first)
        assertEquals(listOf(240, 242), stored!!.discoverIds)

        val second = repo.recommendationsFor("movie", "238")

        assertEquals(first, second)
        coVerify(exactly = 1) { discoverClient.movieRecommendations("238") }
    }
}
