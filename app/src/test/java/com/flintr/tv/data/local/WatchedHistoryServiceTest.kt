package com.flintr.tv.data.local

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import com.flintr.tv.data.model.WatchedItem
import com.flintr.tv.data.repository.UserRepository
import com.squareup.moshi.Moshi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import com.flintr.tv.testutil.silentCredentialsFlow
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WatchedHistoryServiceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val moshi = Moshi.Builder().build()
    private val credentials = Credentials(serverUrl = "http://server", username = "chris", password = "pw")

    private lateinit var userRepository: UserRepository
    private lateinit var credentialsRepository: CredentialsRepository
    private lateinit var service: WatchedHistoryService

    @Before
    fun setUp() {
        context.getSharedPreferences("watched_history_prefs", Context.MODE_PRIVATE).edit {
            putBoolean("watched_history_migrated", true)
        }

        userRepository = mockk(relaxed = true)
        credentialsRepository = mockk()
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

    private fun watchedItem(
        contentType: String = "movie",
        streamId: Int? = 1,
        seriesId: Int? = null,
        episodeId: Int? = null,
        watchedAt: String = "2026-07-16T10:00:00Z"
    ) = WatchedItem(
        contentType = contentType,
        streamId = streamId,
        seriesId = seriesId,
        episodeId = episodeId,
        name = null,
        genre = null,
        categoryName = null,
        watchedAt = watchedAt
    )

    @Suppress("UNCHECKED_CAST")
    private fun seedHistory(map: Map<String, WatchedItem>) {
        val field = WatchedHistoryService::class.java.getDeclaredField("_watchedHistory")
        field.isAccessible = true
        (field.get(service) as MutableStateFlow<Map<String, WatchedItem>>).value = map
    }

    @Test
    fun `markAsWatched fuegt Eintrag hinzu und speichert nach Firestore`() = runBlocking {
        assertTrue(service.markAsWatched("movie", 42, null, null, "Film", "Drama", "Filme DE"))

        assertTrue(service.isWatched("movie", 42, null, null))
        coVerify(timeout = 2_000) {
            userRepository.saveWatchedHistory("chris", match { it.containsKey("movie:42") })
        }
    }

    @Test
    fun `markAsWatched ohne streamId fuer Filme liefert false`() = runBlocking {
        assertFalse(service.markAsWatched("movie", null, null, null))
        assertFalse(service.markAsWatched("radio", 1, null, null))
    }

    @Test
    fun `markAsUnwatched entfernt vorhandenen Eintrag`() = runBlocking {
        seedHistory(mapOf("movie:42" to watchedItem()))

        assertTrue(service.markAsUnwatched("movie", 42, null, null))
        assertFalse(service.isWatched("movie", 42, null, null))
    }

    @Test
    fun `markAsUnwatched ohne Eintrag ist erfolgreich ohne Firestore-Aufruf`() = runBlocking {
        assertTrue(service.markAsUnwatched("movie", 999, null, null))
        coVerify(exactly = 0) { userRepository.saveWatchedHistory(any(), any()) }
    }

    @Test
    fun `markAsUnwatched liefert false bei nicht generierbarem Schluessel`() = runBlocking {
        assertFalse(service.markAsUnwatched("movie", null, null, null))
    }

    @Test
    fun `isWatched liefert false bei nicht generierbarem Schluessel`() = runBlocking {
        assertFalse(service.isWatched("series", null, 10, null))
    }

    @Test
    fun `markAsWatched setzt und entfernt eine einzelne Serienepisode direkt`() = runBlocking {
        assertTrue(service.markAsWatched("series", null, 10, 101, "Serie", "Drama", "Serien DE"))
        assertTrue(service.isWatched("series", null, 10, 101))

        assertTrue(service.markAsUnwatched("series", null, 10, 101))
        assertFalse(service.isWatched("series", null, 10, 101))
    }

    @Test
    fun `markAsWatched speichert lokal auch ohne Zugangsdaten fuer Firestore-Sync`() = runBlocking {
        every { credentialsRepository.getCredentials() } returns null

        assertTrue(service.markAsWatched("movie", 55, null, null))

        assertTrue(service.isWatched("movie", 55, null, null))
        coVerify(exactly = 0) { userRepository.saveWatchedHistory(any(), any()) }
    }

    @Test
    fun `markSeriesEpisodesAsWatched speichert alle Episoden in einem Rutsch`() = runBlocking {
        assertTrue(service.markSeriesEpisodesAsWatched(10, listOf(101, 102), "Serie", null, null))

        assertTrue(service.isWatched("series", null, 10, 101))
        assertTrue(service.isWatched("series", null, 10, 102))
        coVerify(timeout = 2_000, exactly = 1) { userRepository.saveWatchedHistory("chris", any()) }
    }

    @Test
    fun `markSeriesEpisodesAsWatched mit leerer Liste ist erfolgreich`() = runBlocking {
        assertTrue(service.markSeriesEpisodesAsWatched(10, emptyList()))
        coVerify(exactly = 0) { userRepository.saveWatchedHistory(any(), any()) }
    }

    @Test
    fun `getWatchedEpisodeIds filtert nach Serie`() = runBlocking {
        seedHistory(
            mapOf(
                "series:10:101" to watchedItem("series", null, 10, 101),
                "series:10:102" to watchedItem("series", null, 10, 102),
                "series:99:555" to watchedItem("series", null, 99, 555),
                "movie:1" to watchedItem()
            )
        )

        assertEquals(setOf(101, 102), service.getWatchedEpisodeIds(10))
        assertEquals(emptySet<Int>(), service.getWatchedEpisodeIds(7))
    }

    @Test
    fun `getWatchedVodIds liefert alle Film-IDs`() = runBlocking {
        seedHistory(
            mapOf(
                "movie:1" to watchedItem(streamId = 1),
                "movie:2" to watchedItem(streamId = 2),
                "series:10:101" to watchedItem("series", null, 10, 101)
            )
        )

        assertEquals(setOf(1, 2), service.getWatchedVodIds())
    }

    @Test
    fun `getLatestWatchedEpisode liefert Episode mit neuestem Zeitstempel`() = runBlocking {
        seedHistory(
            mapOf(
                "series:10:101" to watchedItem("series", null, 10, 101, watchedAt = "2026-07-14T10:00:00Z"),
                "series:10:102" to watchedItem("series", null, 10, 102, watchedAt = "2026-07-15T10:00:00Z"),
                "series:10:103" to watchedItem("series", null, 10, 103, watchedAt = "kaputt")
            )
        )

        val latest = service.getLatestWatchedEpisode(10)

        assertEquals(102, latest?.first)
        assertNull(service.getLatestWatchedEpisode(7))
    }

    @Test
    fun `syncIfNeeded laedt Historie von Firestore`() = runBlocking {
        coEvery { userRepository.loadWatchedHistory("chris") } coAnswers {
            Result.success(mapOf("movie:1" to watchedItem()))
        }

        assertTrue(service.syncIfNeeded())
        assertTrue(service.isWatched("movie", 1, null, null))
    }

    @Test
    fun `fehlgeschlagenes Laden laesst Zustand leer`() = runBlocking {
        coEvery { userRepository.loadWatchedHistory("chris") } coAnswers { Result.failure(Exception("offline")) }

        assertTrue(service.syncIfNeeded())
        assertFalse(service.isWatched("movie", 1, null, null))
    }

    @Test
    fun `Migration uebertraegt lokale Daten und setzt Flag`() = runBlocking {
        val prefs = context.getSharedPreferences("watched_history_prefs", Context.MODE_PRIVATE)
        prefs.edit {
            putBoolean("watched_history_migrated", false)
            putString(
                "iptv_watched_history",
                """{"movie:7":{"contentType":"movie","streamId":7,"watchedAt":"2026-07-16T10:00:00Z"}}"""
            )
        }
        coEvery { userRepository.migrateWatchedHistoryFromLocal("chris", any()) } coAnswers { Result.success(Unit) }
        coEvery { userRepository.loadWatchedHistory("chris") } coAnswers { Result.success(emptyMap()) }

        assertTrue(service.syncIfNeeded())

        coVerify {
            userRepository.migrateWatchedHistoryFromLocal("chris", match { it.containsKey("movie:7") })
        }
        assertTrue(prefs.getBoolean("watched_history_migrated", false))
        assertNull(prefs.getString("iptv_watched_history", null))
    }

    @Test
    fun `Migration ohne lokale Daten markiert Flag ohne Firestore-Aufruf`() = runBlocking {
        val prefs = context.getSharedPreferences("watched_history_prefs", Context.MODE_PRIVATE)
        prefs.edit {
            putBoolean("watched_history_migrated", false)
            remove("iptv_watched_history")
        }
        coEvery { userRepository.loadWatchedHistory("chris") } coAnswers { Result.success(emptyMap()) }

        assertTrue(service.syncIfNeeded())

        coVerify(exactly = 0) { userRepository.migrateWatchedHistoryFromLocal(any(), any()) }
        assertTrue(prefs.getBoolean("watched_history_migrated", false))
    }

    @Test
    fun `fehlgeschlagene Migration setzt Flag nicht`() = runBlocking {
        val prefs = context.getSharedPreferences("watched_history_prefs", Context.MODE_PRIVATE)
        prefs.edit {
            putBoolean("watched_history_migrated", false)
            putString(
                "iptv_watched_history",
                """{"movie:7":{"contentType":"movie","streamId":7,"watchedAt":"2026-07-16T10:00:00Z"}}"""
            )
        }
        coEvery { userRepository.migrateWatchedHistoryFromLocal("chris", any()) } coAnswers { Result.failure(Exception("offline")) }
        coEvery { userRepository.loadWatchedHistory("chris") } coAnswers { Result.success(emptyMap()) }

        assertTrue(service.syncIfNeeded())

        assertFalse(prefs.getBoolean("watched_history_migrated", true))
    }
}
