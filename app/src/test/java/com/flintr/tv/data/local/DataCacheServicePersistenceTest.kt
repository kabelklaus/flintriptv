package com.flintr.tv.data.local

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import com.flintr.tv.data.model.Category
import com.flintr.tv.data.model.testLiveStream
import com.flintr.tv.data.model.testSeries
import com.flintr.tv.data.model.testVodStream
import com.squareup.moshi.Moshi
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

    @Before
    fun setUp() {
        service = DataCacheService(context, moshi, Dispatchers.Unconfined)
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
}
