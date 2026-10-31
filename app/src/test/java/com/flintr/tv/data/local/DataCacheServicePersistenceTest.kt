package com.flintr.tv.data.local

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import com.flintr.tv.data.local.imdb.ImdbEnrichmentService
import com.flintr.tv.data.model.Category
import com.flintr.tv.data.model.testLiveStream
import com.flintr.tv.data.model.testSeries
import com.flintr.tv.data.model.testVodStream
import com.squareup.moshi.Moshi
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class DataCacheServicePersistenceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val moshi = Moshi.Builder().build()
    private lateinit var service: DataCacheService

    private val channels = listOf(testLiveStream(name = "Sender", streamId = 100))
    private val movies = listOf(testVodStream(name = "Film", streamId = 1))
    private val series = listOf(testSeries(name = "Serie", seriesId = 10))
    private val categories = listOf(Category(categoryId = "5", categoryName = "Kategorie"))

    // relaxed mock -> isAvailable=false (Boolean default), ensureLoaded() ist ein No-Op:
    // Enrichment ist fuer diese generischen Persistenz-Tests nicht der Testgegenstand,
    // siehe DataCacheServiceEnrichmentTest fuer die Merge-Regeln.
    private fun newImdbEnrichmentService(): ImdbEnrichmentService = mockk(relaxed = true)

    @Before
    fun setUp() {
        service = DataCacheService(context, moshi, Dispatchers.Unconfined, newImdbEnrichmentService())
        service.clearCache()
    }

    private fun saveDefaults(): Boolean = service.saveCachedData(
        channels = channels,
        movies = movies,
        series = series,
        liveCategories = categories,
        vodCategories = emptyList(),
        seriesCategories = emptyList()
    )

    @Test
    fun `saveCachedData speichert und laedt Daten verlustfrei`() {
        assertTrue(saveDefaults())

        val cached = service.loadCachedData()

        assertEquals(listOf(100), cached?.channels?.map { it.streamId })
        assertEquals(listOf(1), cached?.movies?.map { it.streamId })
        assertEquals(listOf(10), cached?.series?.map { it.seriesId })
        assertEquals(listOf("5"), cached?.liveCategories?.map { it.categoryId })
        assertTrue(cached?.vodCategories?.isEmpty() == true)
    }

    @Test
    fun `saveCachedData verweigert leere Inhalte`() {
        assertFalse(
            service.saveCachedData(
                channels = emptyList(),
                movies = movies,
                series = series,
                liveCategories = emptyList(),
                vodCategories = emptyList(),
                seriesCategories = emptyList()
            )
        )
        assertFalse(service.hasCacheData())
    }

    @Test
    fun `isCacheValid nur bei frischem vollstaendigem Cache`() {
        assertFalse(service.isCacheValid())

        assertTrue(saveDefaults())
        assertTrue(service.isCacheValid())
    }

    @Test
    fun `isCacheValid lehnt abgelaufenen Cache ab`() {
        assertTrue(saveDefaults())

        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
        val expired = format.format(Date(System.currentTimeMillis() - 25 * 60 * 60 * 1000L))
        context.getSharedPreferences("iptv_cache_prefs", Context.MODE_PRIVATE).edit {
            putString("iptv_cache_timestamp", expired)
        }

        assertFalse(service.isCacheValid())
    }

    @Test
    fun `isCacheValid lehnt kaputten Zeitstempel ab`() {
        assertTrue(saveDefaults())
        context.getSharedPreferences("iptv_cache_prefs", Context.MODE_PRIVATE).edit {
            putString("iptv_cache_timestamp", "kein-datum")
        }

        assertFalse(service.isCacheValid())
    }

    @Test
    fun `hasCacheData nutzt Counts und JSON-Fallback`() {
        assertFalse(service.hasCacheData())

        assertTrue(saveDefaults())
        assertTrue(service.hasCacheData())

        // Counts entfernen -> JSON-Fallback greift
        context.getSharedPreferences("iptv_cache_prefs", Context.MODE_PRIVATE).edit {
            remove("iptv_cache_channels_count")
            remove("iptv_cache_movies_count")
            remove("iptv_cache_series_count")
        }
        assertTrue(service.hasCacheData())
    }

    @Test
    fun `shouldUseCacheForBrowsing mit Cooldown und Fallback-Cache`() {
        assertFalse(service.shouldUseCacheForBrowsing())

        assertTrue(saveDefaults())
        assertTrue(service.shouldUseCacheForBrowsing())

        // Cache abgelaufen, aber Cooldown aktiv -> Fallback greift
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
        val expired = format.format(Date(System.currentTimeMillis() - 25 * 60 * 60 * 1000L))
        context.getSharedPreferences("iptv_cache_prefs", Context.MODE_PRIVATE).edit {
            putString("iptv_cache_timestamp", expired)
        }
        service.setFallbackCooldownTimestamp(System.currentTimeMillis())
        assertTrue(service.shouldUseCacheForBrowsing())

        service.clearFallbackCooldown()
        assertFalse(service.shouldUseCacheForBrowsing())
    }

    @Test
    fun `isFallbackCooldownActive beruecksichtigt Ablauf`() {
        assertFalse(service.isFallbackCooldownActive())

        service.setFallbackCooldownTimestamp(System.currentTimeMillis())
        assertTrue(service.isFallbackCooldownActive())

        service.setFallbackCooldownTimestamp(System.currentTimeMillis() - 25 * 60 * 60 * 1000L)
        assertFalse(service.isFallbackCooldownActive())
    }

    @Test
    fun `clearCache entfernt alle Daten`() {
        assertTrue(saveDefaults())
        service.clearCache()

        assertFalse(service.hasCacheData())
        assertNull(service.loadCachedData())
    }

    @Test
    fun `loadCachedDataAsync liefert dieselben Daten wie synchron`() = runBlocking {
        assertTrue(saveDefaults())

        val cached = service.loadCachedDataAsync()

        assertEquals(listOf("Sender"), cached?.channels?.map { it.name })
    }

    @Test
    fun `loadCachedDataAsync memoizes parsed data - second call returns same instance`() = runBlocking {
        assertTrue(saveDefaults())

        val first = service.loadCachedDataAsync()
        val second = service.loadCachedDataAsync()

        assertTrue(first != null)
        assertTrue(first === second)
    }

    @Test
    fun `saveCachedData invalidates memoized cache`() = runBlocking {
        assertTrue(saveDefaults())
        val first = service.loadCachedDataAsync()

        val extraChannel = testLiveStream(name = "Sender 2", streamId = 200)
        assertTrue(
            service.saveCachedData(
                channels = channels + extraChannel,
                movies = movies,
                series = series,
                liveCategories = categories,
                vodCategories = emptyList(),
                seriesCategories = emptyList()
            )
        )
        val second = service.loadCachedDataAsync()

        assertTrue(first !== second)
        assertEquals(channels.size + 1, second?.channels?.size)
    }

    @Test
    fun `clearCache invalidates memoized cache`() = runBlocking {
        assertTrue(saveDefaults())
        val first = service.loadCachedDataAsync()

        service.clearCache()
        val second = service.loadCachedDataAsync()

        assertTrue(first != null)
        assertNull(second)
    }

    @Test
    fun `loadItemsByIds filtert nach angefragten IDs`() = runBlocking {
        val moreMovies = listOf(
            testVodStream(name = "Film 1", streamId = 1),
            testVodStream(name = "Film 2", streamId = 2),
            testVodStream(name = "Film 3", streamId = 3)
        )
        assertTrue(
            service.saveCachedData(
                channels = channels,
                movies = moreMovies,
                series = series,
                liveCategories = emptyList(),
                vodCategories = emptyList(),
                seriesCategories = emptyList()
            )
        )

        assertEquals(listOf(1, 3), service.loadMoviesByIds(listOf(1, 3, 99))?.map { it.streamId })
        assertEquals(listOf(100), service.loadChannelsByIds(listOf(100))?.map { it.streamId })
        assertEquals(listOf(10), service.loadSeriesByIds(listOf(10))?.map { it.seriesId })
        assertEquals(emptyList<Int>(), service.loadMoviesByIds(emptyList())?.map { it.streamId })
    }

    @Test
    fun `loadItemsByIds liefert null ohne Cache`() = runBlocking {
        assertNull(service.loadMoviesByIds(listOf(1)))
        assertNull(service.loadCachedData())
    }

    private val legacyJsonKeys = listOf(
        "iptv_cache_channels",
        "iptv_cache_movies",
        "iptv_cache_series",
        "iptv_cache_live_categories",
        "iptv_cache_vod_categories",
        "iptv_cache_series_categories"
    )

    private val cacheFileNames = listOf(
        "channels.json",
        "movies.json",
        "series.json",
        "live_categories.json",
        "vod_categories.json",
        "series_categories.json"
    )

    private val cacheDir get() = File(context.filesDir, "iptv_cache")

    private fun prefs() = context.getSharedPreferences("iptv_cache_prefs", Context.MODE_PRIVATE)

    @Test
    fun `init entfernt Legacy-JSON-Keys aus den Prefs`() {
        prefs().edit {
            legacyJsonKeys.forEach { putString(it, "[]") }
            putString("iptv_cache_timestamp", "keep-me")
        }

        DataCacheService(context, moshi, Dispatchers.Unconfined, newImdbEnrichmentService())

        legacyJsonKeys.forEach { assertFalse("$it should be purged", prefs().contains(it)) }
        assertEquals("keep-me", prefs().getString("iptv_cache_timestamp", null))
    }

    @Test
    fun `saveCachedData schreibt Dateien statt Prefs-Strings`() {
        assertTrue(saveDefaults())

        cacheFileNames.forEach { assertTrue("$it should exist", File(cacheDir, it).isFile) }
        assertFalse(cacheDir.listFiles().orEmpty().any { it.name.endsWith(".tmp") })
        legacyJsonKeys.forEach { assertFalse("$it must not be in prefs", prefs().contains(it)) }
    }

    @Test
    fun `loadCachedData ohne Cache-Dateien liefert null`() {
        assertTrue(saveDefaults())
        // Fresh instance: no memoized data, counts still in prefs, files gone
        cacheDir.deleteRecursively()
        val fresh = DataCacheService(context, moshi, Dispatchers.Unconfined, newImdbEnrichmentService())

        assertNull(fresh.loadCachedData())
    }

    @Test
    fun `loadCachedData liest Dateien in neuer Instanz`() {
        assertTrue(saveDefaults())
        val fresh = DataCacheService(context, moshi, Dispatchers.Unconfined, newImdbEnrichmentService())

        val cached = fresh.loadCachedData()

        assertEquals(listOf(100), cached?.channels?.map { it.streamId })
        assertEquals(listOf(1), cached?.movies?.map { it.streamId })
        assertEquals(listOf(10), cached?.series?.map { it.seriesId })
        assertEquals(listOf("5"), cached?.liveCategories?.map { it.categoryId })
    }

    @Test
    fun `clearCache entfernt Dateien und Prefs`() {
        assertTrue(saveDefaults())
        service.clearCache()

        assertFalse(cacheDir.exists())
        assertTrue(prefs().all.isEmpty())
    }
}
