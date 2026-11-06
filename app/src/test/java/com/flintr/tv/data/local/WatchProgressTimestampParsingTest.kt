package com.flintr.tv.data.local

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import com.flintr.tv.data.model.WatchedItem
import com.flintr.tv.data.repository.UserRepository
import com.flintr.tv.testutil.silentCredentialsFlow
import com.squareup.moshi.Moshi
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Timestamp robustness for the shared watched-history source used by the series-detail ribbons
 * and resume logic: a malformed date must never make an episode disappear.
 */
@RunWith(RobolectricTestRunner::class)
class WatchProgressTimestampParsingTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val moshi = Moshi.Builder().build()
    private val credentials = Credentials(serverUrl = "http://server", username = "chris", password = "pw")
    private lateinit var userRepository: UserRepository
    private lateinit var service: WatchedHistoryService

    @Before
    fun setUp() {
        context.getSharedPreferences("watched_history_prefs", Context.MODE_PRIVATE).edit {
            putBoolean("watched_history_migrated", true)
        }
        userRepository = mockk(relaxed = true)
        val credentialsRepository = mockk<CredentialsRepository>()
        every { credentialsRepository.credentialsFlow } returns silentCredentialsFlow
        every { credentialsRepository.getCredentials() } returns credentials
        coEvery { userRepository.saveWatchedHistory("chris", any()) } returns Result.success(Unit)
        service = WatchedHistoryService(
            context = context,
            moshi = moshi,
            userRepository = userRepository,
            credentialsRepository = credentialsRepository,
            syncManager = FirestoreSyncManager(syncCooldownMs = 0L),
            ioDispatcher = Dispatchers.Unconfined
        )
    }

    private fun watchedItem(watchedAt: String) = WatchedItem(
        contentType = "series",
        streamId = null,
        seriesId = 10,
        episodeId = 101,
        name = null,
        genre = null,
        categoryName = null,
        watchedAt = watchedAt
    )

    @Suppress("UNCHECKED_CAST")
    private fun seedHistory(item: WatchedItem) {
        val field = WatchedHistoryService::class.java.getDeclaredField("_watchedHistory")
        field.isAccessible = true
        (field.get(service) as MutableStateFlow<Map<String, WatchedItem>>).value =
            mapOf("series:10:101" to item)
    }

    @Test
    fun `getLatestWatchedEpisode vergisst Episode mit kaputtem Zeitstempel nicht`() = runBlocking {
        seedHistory(watchedItem(watchedAt = "kaputt"))

        val latest = service.getLatestWatchedEpisode(10)

        assertEquals(101, latest?.first)
        assertEquals(0L, latest?.second)
    }

    @Test
    fun `getLatestWatchedEpisode parst Offset-Zeitstempel`() = runBlocking {
        seedHistory(watchedItem(watchedAt = "2026-07-15T12:00:00+02:00"))

        assertEquals(101, service.getLatestWatchedEpisode(10)?.first)
    }

    @Test
    fun `getLatestWatchedEpisode liefert weiterhin guten Zeitstempel`() = runBlocking {
        seedHistory(watchedItem(watchedAt = "2026-07-15T10:00:00Z"))

        assertEquals(101, service.getLatestWatchedEpisode(10)?.first)
    }
}
