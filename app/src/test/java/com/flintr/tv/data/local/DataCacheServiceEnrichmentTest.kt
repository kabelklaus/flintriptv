package com.flintr.tv.data.local

import androidx.test.core.app.ApplicationProvider
import com.flintr.tv.data.local.imdb.ImdbEnrichment
import com.flintr.tv.data.local.imdb.ImdbEnrichmentService
import com.flintr.tv.data.model.testLiveStream
import com.flintr.tv.data.model.testSeries
import com.flintr.tv.data.model.testVodStream
import com.squareup.moshi.Moshi
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Deckt die Merge-Regeln aus Chris-Spec 30.10. ab: IMDb-Genres loesen Xtream-Genres
 * exklusiv ab, Xtream-Ratings werden komplett ersetzt -- ausser der Enrichment-Service
 * ist unavailable, dann bleiben die Xtream-Rohwerte unveraendert.
 */
@RunWith(RobolectricTestRunner::class)
class DataCacheServiceEnrichmentTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val moshi = Moshi.Builder().build()
    private lateinit var imdbEnrichmentService: ImdbEnrichmentService
    private lateinit var service: DataCacheService

    private val channels = listOf(testLiveStream(name = "Sender", streamId = 100))

    private fun newService(): DataCacheService =
        DataCacheService(context, moshi, Dispatchers.Unconfined, imdbEnrichmentService).also { it.clearCache() }

    @Before
    fun setUp() {
        imdbEnrichmentService = mockk()
        coEvery { imdbEnrichmentService.ensureLoaded() } returns Unit
        // Fallback fuer Namen, die ein Test nicht explizit stubbt (z.B. die Series
        // in den Movie-fokussierten Tests) -- bleibt sonst ein strict-mockk-Fehler.
        every { imdbEnrichmentService.lookup(any()) } returns null
        service = newService()
    }

    @Test
    fun `Match ueberschreibt Genre und setzt Rating aus IMDb`() {
        every { imdbEnrichmentService.isAvailable } returns true
        every { imdbEnrichmentService.lookup("Film") } returns ImdbEnrichment(
            rating = 7.0,
            numVotes = 1000,
            genres = listOf("Drama", "Family")
        )
        val movies = listOf(testVodStream(name = "Film", streamId = 1, rating = "3", rating5Based = 1.5).copy(genre = "Xtream-Genre"))

        assertTrue(service.saveCachedData(channels, movies, listOf(testSeries()), emptyList(), emptyList(), emptyList()))

        val saved = service.loadCachedData()!!.movies.single()
        assertEquals("Drama, Family", saved.genre)
        assertEquals("7.0", saved.rating)
        assertEquals(3.5, saved.rating5Based)
    }

    @Test
    fun `Kein Match nullt genre rating und rating5Based auch wenn Xtream-Werte vorhanden waren`() {
        every { imdbEnrichmentService.isAvailable } returns true
        every { imdbEnrichmentService.lookup(any()) } returns null
        val movies = listOf(
            testVodStream(name = "Unbekannter Film", streamId = 1, rating = "3", rating5Based = 1.5).copy(genre = "Xtream-Genre")
        )
        val series = listOf(testSeries(name = "Unbekannte Serie", rating = "4", rating5Based = 2.0).copy(genre = "Xtream-Serien-Genre"))

        assertTrue(service.saveCachedData(channels, movies, series, emptyList(), emptyList(), emptyList()))

        val cached = service.loadCachedData()!!
        val savedMovie = cached.movies.single()
        assertNull(savedMovie.genre)
        assertNull(savedMovie.rating)
        assertNull(savedMovie.rating5Based)

        val savedSeries = cached.series.single()
        assertNull(savedSeries.genre)
        assertNull(savedSeries.rating)
        assertNull(savedSeries.rating5Based)
    }

    @Test
    fun `Service unavailable laesst Items unveraendert`() {
        every { imdbEnrichmentService.isAvailable } returns false
        val movies = listOf(
            testVodStream(name = "Film", streamId = 1, rating = "3", rating5Based = 1.5).copy(genre = "Xtream-Genre")
        )
        val series = listOf(testSeries(name = "Serie", rating = "4", rating5Based = 2.0).copy(genre = "Xtream-Serien-Genre"))

        assertTrue(service.saveCachedData(channels, movies, series, emptyList(), emptyList(), emptyList()))

        val cached = service.loadCachedData()!!
        val savedMovie = cached.movies.single()
        assertEquals("Xtream-Genre", savedMovie.genre)
        assertEquals("3", savedMovie.rating)
        assertEquals(1.5, savedMovie.rating5Based)

        val savedSeries = cached.series.single()
        assertEquals("Xtream-Serien-Genre", savedSeries.genre)
        assertEquals("4", savedSeries.rating)
        assertEquals(2.0, savedSeries.rating5Based)
    }

    @Test
    fun `rating5Based ist rating durch zwei`() {
        every { imdbEnrichmentService.isAvailable } returns true
        every { imdbEnrichmentService.lookup("Film") } returns ImdbEnrichment(rating = 7.4, numVotes = 1, genres = listOf("Drama"))
        val movies = listOf(testVodStream(name = "Film", streamId = 1))

        assertTrue(service.saveCachedData(channels, movies, listOf(testSeries()), emptyList(), emptyList(), emptyList()))

        val saved = service.loadCachedData()!!.movies.single()
        assertEquals("7.4", saved.rating)
        assertEquals(3.7, saved.rating5Based)
    }

    @Test
    fun `Andere Felder bleiben unveraendert`() {
        every { imdbEnrichmentService.isAvailable } returns true
        every { imdbEnrichmentService.lookup("Film") } returns ImdbEnrichment(rating = 7.0, numVotes = 1, genres = listOf("Drama"))
        val movies = listOf(testVodStream(name = "Film", streamId = 42, categoryId = "7", containerExtension = "mp4"))

        assertTrue(service.saveCachedData(channels, movies, listOf(testSeries()), emptyList(), emptyList(), emptyList()))

        val saved = service.loadCachedData()!!.movies.single()
        assertEquals("Film", saved.name)
        assertEquals(42, saved.streamId)
        assertEquals("7", saved.categoryId)
        assertEquals("mp4", saved.containerExtension)
    }

    @Test
    fun `Genres werden mit Komma verbunden`() {
        every { imdbEnrichmentService.isAvailable } returns true
        every { imdbEnrichmentService.lookup("Film") } returns ImdbEnrichment(rating = 5.0, numVotes = 1, genres = listOf("Drama", "Family"))
        val movies = listOf(testVodStream(name = "Film", streamId = 1))

        assertTrue(service.saveCachedData(channels, movies, listOf(testSeries()), emptyList(), emptyList(), emptyList()))

        assertEquals("Drama, Family", service.loadCachedData()!!.movies.single().genre)
    }
}
