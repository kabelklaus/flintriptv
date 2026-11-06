package com.flintr.tv.data.local

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import com.flintr.tv.data.local.imdb.ImdbEnrichmentService
import com.flintr.tv.data.model.Category
import com.flintr.tv.data.model.VodStream
import com.flintr.tv.data.model.testLiveStream
import com.flintr.tv.data.model.testSeries
import com.flintr.tv.data.model.testVodStream
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.lang.reflect.Type
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
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

    private fun saveDefaults(): Boolean = runBlocking {
        service.saveCachedData(
            channels = channels,
            movies = movies,
            series = series,
            liveCategories = categories,
            vodCategories = emptyList(),
            seriesCategories = emptyList()
        )
    }

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
    fun `saveCachedData verweigert leere Inhalte`() = runBlocking {
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
    fun `chunked Speichern meldet Fortschritt und bleibt mit dem Lesepfad kompatibel`() = runBlocking {
        val manyMovies = (1..DataCacheService.SAVE_CHUNK_SIZE * 2 + 500).map {
            testVodStream(name = "Film $it", streamId = it)
        }
        val reported = mutableListOf<Int>()

        assertTrue(
            service.saveCachedData(
                channels = channels,
                movies = manyMovies,
                series = series,
                liveCategories = categories,
                vodCategories = emptyList(),
                seriesCategories = emptyList(),
                onProgress = { reported += it }
            )
        )

        val total = channels.size + manyMovies.size + series.size
        // channels (1 chunk) + movies (3 chunks) + series (1 chunk)
        assertEquals(5, reported.size)
        assertEquals(reported.sorted(), reported)
        assertEquals(total, reported.last())

        // Format unchanged: byte-identical to what the List adapter wrote before chunking
        val listAdapter = moshi.adapter<List<VodStream>>(
            Types.newParameterizedType(List::class.java, VodStream::class.java)
        )
        assertEquals(listAdapter.toJson(manyMovies), File(cacheDir, "movies.json").readText())

        val fresh = DataCacheService(context, moshi, Dispatchers.Unconfined, newImdbEnrichmentService())
        assertEquals(manyMovies.map { it.streamId }, fresh.loadCachedData()?.movies?.map { it.streamId })
    }

    @Test
    fun `clearCache entfernt Dateien und Prefs`() {
        assertTrue(saveDefaults())
        service.clearCache()

        assertFalse(cacheDir.exists())
        assertTrue(prefs().all.isEmpty())
    }

    @Test
    fun `init loescht verwaiste Temp-Dateien`() {
        assertTrue(saveDefaults())
        val orphan = File(cacheDir, "movies.json.tmp").apply { writeText("[") }

        DataCacheService(context, moshi, Dispatchers.Unconfined, newImdbEnrichmentService())

        assertFalse(orphan.exists())
        assertTrue(File(cacheDir, "movies.json").isFile)
    }

    @Test
    fun `Versionssprung verwirft den alten Cache explizit`() {
        assertTrue(saveDefaults())
        assertEquals(DataCacheService.CACHE_VERSION, prefs().getInt(DataCacheService.KEY_CACHE_VERSION, -1))
        prefs().edit { putInt(DataCacheService.KEY_CACHE_VERSION, DataCacheService.CACHE_VERSION - 1) }

        val fresh = DataCacheService(context, moshi, Dispatchers.Unconfined, newImdbEnrichmentService())

        assertFalse(cacheDir.exists())
        assertFalse(fresh.hasCacheData())
        assertNull(fresh.loadCachedData())
        assertEquals(DataCacheService.CACHE_VERSION, prefs().getInt(DataCacheService.KEY_CACHE_VERSION, -1))
    }

    @Test
    fun `Installationen ohne gespeicherte Version behalten ihren Datei-Cache`() {
        assertTrue(saveDefaults())
        prefs().edit { remove(DataCacheService.KEY_CACHE_VERSION) }

        val fresh = DataCacheService(context, moshi, Dispatchers.Unconfined, newImdbEnrichmentService())

        assertEquals(listOf(1), fresh.loadCachedData()?.movies?.map { it.streamId })
    }

    /** Records which threads (de)serialize VodStreams and how many were parsed. */
    private class VodStreamProbe : JsonAdapter.Factory {
        val threads: MutableSet<String> = ConcurrentHashMap.newKeySet()
        val parsed = AtomicInteger()

        // Coroutine debug mode appends " @coroutine#N" to the thread name.
        private fun record() {
            threads += Thread.currentThread().name.substringBefore(" @coroutine#")
        }

        override fun create(type: Type, annotations: Set<Annotation>, moshi: Moshi): JsonAdapter<*>? {
            if (type != VodStream::class.java) return null
            val delegate = moshi.nextAdapter<VodStream>(this, type, annotations)
            return object : JsonAdapter<VodStream>() {
                override fun fromJson(reader: JsonReader): VodStream? {
                    record()
                    parsed.incrementAndGet()
                    return delegate.fromJson(reader)
                }

                override fun toJson(writer: JsonWriter, value: VodStream?) {
                    record()
                    delegate.toJson(writer, value)
                }
            }
        }
    }

    // Unconfined (used above) runs everything on the test thread, so a dropped
    // withContext(ioDispatcher) would go unnoticed there. A named IO pool makes it fail.
    @Test
    fun `Cache-Read und -Write laufen nie auf dem aufrufenden Main-Thread`() {
        val probe = VodStreamProbe()
        val probedMoshi = Moshi.Builder().add(probe).build()
        val executor = Executors.newFixedThreadPool(2) { Thread(it, "cache-io-test") }
        val io = executor.asCoroutineDispatcher()
        val callerThread = Thread.currentThread().name
        try {
            runBlocking {
                val writer = DataCacheService(context, probedMoshi, io, newImdbEnrichmentService())
                assertTrue(
                    writer.saveCachedData(channels, movies, series, categories, emptyList(), emptyList())
                )
                val reader = DataCacheService(context, probedMoshi, io, newImdbEnrichmentService())
                assertEquals(listOf(1), reader.loadMoviesByIds(listOf(1))?.map { it.streamId })
                assertEquals(listOf(1), reader.loadCachedDataAsync()?.movies?.map { it.streamId })
            }
        } finally {
            executor.shutdownNow()
        }

        assertTrue("no VodStream was (de)serialized", probe.threads.isNotEmpty())
        assertFalse("cache I/O ran on $callerThread", callerThread in probe.threads)
        assertEquals(setOf("cache-io-test"), probe.threads.toSet())
    }

    @Test
    fun `57k Filme - Parse, ById-Filter und Memo-Pfad ohne erneuten Full-Parse`() = runBlocking {
        val probe = VodStreamProbe()
        val probedMoshi = Moshi.Builder().add(probe).build()
        val manyMovies = (1..57_000).map { testVodStream(name = "Film $it", streamId = it) }
        val writer = DataCacheService(context, probedMoshi, Dispatchers.Unconfined, newImdbEnrichmentService())
        assertTrue(writer.saveCachedData(channels, manyMovies, series, categories, emptyList(), emptyList()))

        val reader = DataCacheService(context, probedMoshi, Dispatchers.Unconfined, newImdbEnrichmentService())
        val wanted = listOf(57_000, 1, 28_500, 99_999)

        // No memo yet: streamed from the file, only the matches are kept.
        assertEquals(listOf(57_000, 1, 28_500), reader.loadMoviesByIds(wanted)?.map { it.streamId })
        assertEquals(57_000, probe.parsed.get())

        val cached = reader.loadCachedDataAsync()
        assertEquals(manyMovies.map { it.streamId }, cached?.movies?.map { it.streamId })
        assertEquals(114_000, probe.parsed.get())

        assertEquals(listOf(57_000, 1, 28_500), reader.loadMoviesByIds(wanted)?.map { it.streamId })
        assertEquals(listOf(100), reader.loadChannelsByIds(listOf(100))?.map { it.streamId })
        assertTrue(cached === reader.loadCachedDataAsync())
        assertEquals("memo path must not parse the file again", 114_000, probe.parsed.get())
    }

    @Test
    fun `parallele Erst-Loads teilen sich einen Parse`() {
        val probe = VodStreamProbe()
        val probedMoshi = Moshi.Builder().add(probe).build()
        val manyMovies = (1..5_000).map { testVodStream(name = "Film $it", streamId = it) }
        runBlocking {
            DataCacheService(context, probedMoshi, Dispatchers.Unconfined, newImdbEnrichmentService())
                .saveCachedData(channels, manyMovies, series, categories, emptyList(), emptyList())
        }
        val executor = Executors.newFixedThreadPool(4)
        val reader = DataCacheService(context, probedMoshi, executor.asCoroutineDispatcher(), newImdbEnrichmentService())
        probe.parsed.set(0)
        try {
            val results = runBlocking {
                (1..4).map { async { reader.loadCachedDataAsync() } }.awaitAll()
            }
            assertTrue(results.all { it != null && it === results.first() })
        } finally {
            executor.shutdownNow()
        }
        assertEquals(manyMovies.size, probe.parsed.get())
    }
}
