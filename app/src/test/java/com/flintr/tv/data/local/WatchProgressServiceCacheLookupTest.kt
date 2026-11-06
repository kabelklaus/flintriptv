package com.flintr.tv.data.local

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import com.flintr.tv.data.model.Category
import com.flintr.tv.data.model.WatchProgress
import com.flintr.tv.data.model.testLiveStream
import com.flintr.tv.data.model.testSeries
import com.flintr.tv.data.model.testVodStream
import com.flintr.tv.data.repository.UserRepository
import com.flintr.tv.testutil.silentCredentialsFlow
import com.squareup.moshi.Moshi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Cache-Metadaten-Aufloesung (Name/Genre/Kategorie) beim Als-gesehen-Markieren.
 */
@RunWith(RobolectricTestRunner::class)
class WatchProgressServiceCacheLookupTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var watchedHistoryService: WatchedHistoryService
    private lateinit var cacheService: DataCacheService
    private lateinit var service: WatchProgressService

    @Before
    fun setUp() {
        context.getSharedPreferences("watch_progress_prefs", Context.MODE_PRIVATE).edit {
            putBoolean("watch_progress_migrated", true)
        }

        val credentialsRepository = mockk<CredentialsRepository>()
        every { credentialsRepository.credentialsFlow } returns silentCredentialsFlow
        every { credentialsRepository.getCredentials() } returns
            Credentials(serverUrl = "http://srv", username = "chris", password = "pw")

        watchedHistoryService = mockk(relaxed = true)
        coEvery { watchedHistoryService.markAsWatched(any(), any(), any(), any(), any(), any(), any()) } returns true

        val userRepository = mockk<UserRepository>(relaxed = true)
        // coAnswers statt returns: mockk boxt Result (Value-Class) bei "returns" unzuverlaessig
        // doppelt, was in deleteProgress (Firestore-Fire-and-forget) zu einer CCE beim
        // result.fold()-Aufruf fuehrt.
        coEvery { userRepository.deleteWatchProgress(any(), any()) } coAnswers { Result.success(Unit) }

        cacheService = mockk(relaxed = true)
        coEvery { cacheService.loadCachedDataAsync() } returns CachedData(
            channels = listOf(testLiveStream(streamId = 100)),
            movies = listOf(
                testVodStream(name = "Der Film", streamId = 42, categoryId = "7").copy(genre = null)
            ),
            series = listOf(testSeries(name = "Die Serie", seriesId = 10, categoryId = "9")),
            liveCategories = emptyList(),
            vodCategories = listOf(Category(categoryId = "7", categoryName = "Filme DE")),
            seriesCategories = listOf(Category(categoryId = "9", categoryName = "Serien DE"))
        )

        service = WatchProgressService(
            context = context,
            moshi = Moshi.Builder().build(),
            watchlistRepository =mockk(relaxed = true),
            watchedHistoryService = watchedHistoryService,
            userRepository = userRepository,
            credentialsRepository = credentialsRepository,
            cacheService = cacheService,
            syncManager = FirestoreSyncManager(syncCooldownMs = 0L),
            ioDispatcher = Dispatchers.Unconfined
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun seedProgress(map: Map<String, WatchProgress>) {
        val field = WatchProgressService::class.java.getDeclaredField("_watchProgress")
        field.isAccessible = true
        (field.get(service) as MutableStateFlow<Map<String, WatchProgress>>).value = map
    }

    private fun progress(position: Long, duration: Long) =
        WatchProgress(position, duration, "2026-07-16T10:00:00Z", "Titel")

    @Test
    fun `Film-Metadaten werden beim Abschluss aus dem Cache aufgeloest`() = runBlocking {
        val saved = service.saveProgress("movie", 42, null, null, 3_500_000, 3_600_000, "Der Film")

        assertTrue(saved)
        coVerify {
            watchedHistoryService.markAsWatched("movie", 42, null, null, "Der Film", null, "Filme DE")
        }
    }

    @Test
    fun `Serien-Metadaten werden beim Cleanup aus dem Cache aufgeloest`() = runBlocking {
        seedProgress(mapOf("series:10:101" to progress(1_150_000, 1_200_000)))

        assertEquals(1, service.cleanupCompletedProgress())

        coVerify {
            watchedHistoryService.markAsWatched("series", 101, 10, 101, "Die Serie", null, "Serien DE")
        }
    }

    @Test
    fun `unbekannte Inhalte liefern leere Metadaten`() = runBlocking {
        seedProgress(mapOf("movie:999" to progress(3_500_000, 3_600_000)))

        assertEquals(1, service.cleanupCompletedProgress())

        coVerify { watchedHistoryService.markAsWatched("movie", 999, null, null, null, null, null) }
    }

    @Test
    fun `setSeriesEpisodeWatched nutzt Cache-Metadaten`() = runBlocking {
        assertTrue(service.setSeriesEpisodeWatched(10, 101, watched = true))

        coVerify {
            watchedHistoryService.markAsWatched("series", null, 10, 101, "Die Serie", null, "Serien DE")
        }
    }
}
