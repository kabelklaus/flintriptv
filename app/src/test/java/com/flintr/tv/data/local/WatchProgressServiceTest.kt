package com.flintr.tv.data.local

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import com.flintr.tv.data.model.Series
import com.flintr.tv.data.model.WatchProgress
import com.flintr.tv.data.model.testEpisode
import com.flintr.tv.data.model.testSeason
import com.flintr.tv.data.model.testSeries
import com.flintr.tv.data.repository.UserRepository
import com.squareup.moshi.Moshi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import com.flintr.tv.testutil.silentCredentialsFlow
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
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

@RunWith(RobolectricTestRunner::class)
class WatchProgressServiceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val moshi = Moshi.Builder().build()
    private val credentials = Credentials(serverUrl = "http://server", username = "chris", password = "pw")

    private lateinit var userRepository: UserRepository
    private lateinit var credentialsRepository: CredentialsRepository
    private lateinit var watchedHistoryService: WatchedHistoryService
    private lateinit var favoritesRepository: FavoritesRepository
    private lateinit var cacheService: DataCacheService
    private lateinit var service: WatchProgressService

    @Before
    fun setUp() {
        // Migration standardmäßig als abgeschlossen markieren
        context.getSharedPreferences("watch_progress_prefs", Context.MODE_PRIVATE).edit {
            putBoolean("watch_progress_migrated", true)
        }

        userRepository = mockk(relaxed = true)
        credentialsRepository = mockk()
        watchedHistoryService = mockk(relaxed = true)
        favoritesRepository = mockk(relaxed = true)
        cacheService = mockk(relaxed = true)

        // Init-Collect bleibt inert (null-Credentials); Laden wird explizit über syncIfNeeded getrieben
        every { credentialsRepository.credentialsFlow } returns silentCredentialsFlow
        every { credentialsRepository.getCredentials() } returns credentials
        coEvery { cacheService.loadCachedDataAsync() } returns null
        coEvery { watchedHistoryService.markAsWatched(any(), any(), any(), any(), any(), any(), any()) } returns true
        coEvery { watchedHistoryService.markAsUnwatched(any(), any(), any(), any()) } returns true
        coEvery { watchedHistoryService.markSeriesEpisodesAsWatched(any(), any(), any(), any(), any()) } returns true
        // coAnswers statt returns: mockk boxt Result (Value-Class) bei "returns" unzuverlaessig
        // doppelt, was in deleteProgress (Firestore-Fire-and-forget) zu einer CCE beim
        // result.fold()-Aufruf fuehrt (siehe seedProgress-Kommentar oben fuer denselben Bug).
        coEvery { userRepository.deleteWatchProgress(any(), any()) } coAnswers { Result.success(Unit) }

        service = WatchProgressService(
            context = context,
            moshi = moshi,
            favoritesRepository = favoritesRepository,
            watchedHistoryService = watchedHistoryService,
            userRepository = userRepository,
            credentialsRepository = credentialsRepository,
            cacheService = cacheService,
            syncManager = FirestoreSyncManager(syncCooldownMs = 0L),
            ioDispatcher = Dispatchers.Unconfined
        )
    }

    @After
    fun tearDown() {
        // Hintergrund-Coroutinen des Service beenden und abwarten (Reflection wie seedProgress):
        // serviceScope (Init-Collector, Fire-and-forget-Firestore-Saves) laeuft sonst nach dem
        // Test weiter, und Mock-Zugriffe daraus korrumpieren die every{}-Aufzeichnung
        // nachfolgender Tests, weil der MockK-Recorder global ist.
        val field = WatchProgressService::class.java.getDeclaredField("serviceScope")
        field.isAccessible = true
        val scope = field.get(service) as CoroutineScope
        runBlocking { scope.coroutineContext[Job]?.cancelAndJoin() }
    }

    private fun progress(position: Long, duration: Long, title: String = "Titel") = WatchProgress(
        position = position,
        duration = duration,
        lastWatched = "2026-07-16T10:00:00Z",
        title = title
    )

    /**
     * Setzt den Fortschritts-Zustand direkt (Reflection), da mockk suspend-Funktionen
     * mit Result-Rueckgabe (Value-Class) doppelt boxt und der Firestore-Ladepfad
     * dadurch nicht zuverlaessig stubbar ist.
     */
    @Suppress("UNCHECKED_CAST")
    private fun seedProgress(map: Map<String, WatchProgress>) {
        val field = WatchProgressService::class.java.getDeclaredField("_watchProgress")
        field.isAccessible = true
        (field.get(service) as MutableStateFlow<Map<String, WatchProgress>>).value = map
    }

    // --- saveProgress Validierung ---

    @Test
    fun `saveProgress ueberspringt Live-Streams`() = runBlocking {
        assertFalse(service.saveProgress("live", 1, null, null, 60_000, 3_600_000, "Sender"))
    }

    @Test
    fun `saveProgress ueberspringt ungueltige Dauer und Position`() = runBlocking {
        assertFalse(service.saveProgress("movie", 1, null, null, 60_000, 0, "Film"))
        assertFalse(service.saveProgress("movie", 1, null, null, -5, 3_600_000, "Film"))
    }

    @Test
    fun `saveProgress ueberspringt zu kurze Positionen`() = runBlocking {
        assertFalse(service.saveProgress("movie", 1, null, null, 10_000, 3_600_000, "Film"))
    }

    @Test
    fun `saveProgress speichert gueltigen Fortschritt`() = runBlocking {
        val saved = service.saveProgress("movie", 42, null, null, 600_000, 3_600_000, "Film")

        assertTrue(saved)
        assertEquals(600_000L, service.getProgress("movie", 42, null, null))
        coVerify(timeout = 2_000) { userRepository.saveWatchProgress("chris", any()) }
    }

    @Test
    fun `saveProgress ab 90 Prozent markiert als gesehen und entfernt Fortschritt`() = runBlocking {
        seedProgress(mapOf("movie:42" to progress(600_000, 3_600_000, "Film")))

        val result = service.saveProgress("movie", 42, null, null, 3_500_000, 3_600_000, "Film")

        assertTrue(result)
        assertNull(service.getProgress("movie", 42, null, null))
        coVerify { watchedHistoryService.markAsWatched("movie", 42, null, null, null, null, null) }
        coVerify(timeout = 2_000) { favoritesRepository.removeFavorite("movie:42:Film") }
    }

    @Test
    fun `saveProgress ohne streamId fuer Filme liefert false`() = runBlocking {
        assertFalse(service.saveProgress("movie", null, null, null, 600_000, 3_600_000, "Film"))
    }

    @Test
    fun `saveProgress mit unbekanntem contentType liefert false`() = runBlocking {
        assertFalse(service.saveProgress("radio", 1, null, null, 600_000, 3_600_000, "Radio"))
    }

    @Test
    fun `saveProgress speichert Serienfortschritt`() = runBlocking {
        val saved = service.saveProgress("series", null, 10, 101, 600_000, 1_200_000, "Folge 1")

        assertTrue(saved)
        assertEquals(600_000L, service.getProgress("series", null, 10, 101))
    }

    @Test
    fun `saveProgress markiert Serienepisode ab 90 Prozent als gesehen`() = runBlocking {
        seedProgress(mapOf("series:10:101" to progress(600_000, 1_200_000)))

        val result = service.saveProgress("series", null, 10, 101, 1_150_000, 1_200_000, "Folge 1")

        assertTrue(result)
        assertNull(service.getProgress("series", null, 10, 101))
        coVerify { watchedHistoryService.markAsWatched("series", null, 10, 101, null, null, null) }
    }

    @Test
    fun `getProgress mit unvollstaendigem Serien-Schluessel liefert null statt Crash`() = runBlocking {
        assertNull(service.getProgress("series", null, 10, null))
    }

    // --- Abfragen ---

    @Test
    fun `getProgress liefert null ohne Eintrag`() = runBlocking {
        assertNull(service.getProgress("movie", 999, null, null))
        assertNull(service.getWatchProgress("series", null, 1, 100))
    }

    @Test
    fun `getWatchProgress liefert vollstaendigen Eintrag`() = runBlocking {
        seedProgress(mapOf("series:10:101" to progress(120_000, 1_200_000, "Folge 1")))

        val wp = service.getWatchProgress("series", null, 10, 101)

        assertEquals(120_000L, wp?.position)
        assertEquals("Folge 1", wp?.title)
    }

    @Test
    fun `getAllInProgress und Zaehler filtern nach Praefix`() = runBlocking {
        seedProgress(
            mapOf(
                "movie:1" to progress(60_000, 3_600_000),
                "movie:2" to progress(60_000, 3_600_000),
                "series:10:101" to progress(60_000, 1_200_000)
            )
        )

        assertEquals(3, service.getAllInProgress().size)
        assertEquals(setOf("movie:1", "movie:2"), service.getInProgressMovies().keys)
        assertEquals(setOf("series:10:101"), service.getInProgressEpisodes().keys)
        assertEquals(3, service.getInProgressCount())
        assertEquals(2, service.getInProgressMoviesCount())
        assertEquals(1, service.getInProgressEpisodesCount())
    }

    @Test
    fun `syncIfNeeded laedt Fortschritt von Firestore`() = runBlocking {
        coEvery { userRepository.loadWatchProgress("chris") } coAnswers {
            Result.success(mapOf("movie:1" to progress(60_000, 3_600_000)))
        }

        assertTrue(service.syncIfNeeded())
        assertEquals(1, service.getInProgressCount())
    }

    @Test
    fun `fehlgeschlagenes Laden setzt Zustand auf leer`() = runBlocking {
        coEvery { userRepository.loadWatchProgress("chris") } coAnswers { Result.failure(Exception("offline")) }

        assertTrue(service.syncIfNeeded())
        assertEquals(0, service.getInProgressCount())
    }

    // --- Migration ---

    @Test
    fun `Migration uebertraegt lokale Daten und setzt Flag`() = runBlocking {
        val prefs = context.getSharedPreferences("watch_progress_prefs", Context.MODE_PRIVATE)
        prefs.edit {
            putBoolean("watch_progress_migrated", false)
            putString(
                "iptv_watch_progress_data",
                """{"movie:7":{"position":60000,"duration":3600000,"lastWatched":"2026-07-16T10:00:00Z","title":"Alt"}}"""
            )
        }
        coEvery { userRepository.migrateWatchProgressFromLocal("chris", any()) } coAnswers { Result.success(Unit) }
        coEvery { userRepository.loadWatchProgress("chris") } coAnswers { Result.success(emptyMap()) }

        assertTrue(service.syncIfNeeded())

        coVerify {
            userRepository.migrateWatchProgressFromLocal(
                "chris",
                match { it.keys == setOf("movie:7") && it.getValue("movie:7").position == 60_000L }
            )
        }
        assertTrue(prefs.getBoolean("watch_progress_migrated", false))
        assertNull(prefs.getString("iptv_watch_progress_data", null))
    }

    @Test
    fun `fehlgeschlagene Migration setzt Flag nicht`() = runBlocking {
        val prefs = context.getSharedPreferences("watch_progress_prefs", Context.MODE_PRIVATE)
        prefs.edit {
            putBoolean("watch_progress_migrated", false)
            putString(
                "iptv_watch_progress_data",
                """{"movie:7":{"position":60000,"duration":3600000,"lastWatched":"2026-07-16T10:00:00Z","title":"Alt"}}"""
            )
        }
        coEvery { userRepository.migrateWatchProgressFromLocal("chris", any()) } coAnswers { Result.failure(Exception("offline")) }
        coEvery { userRepository.loadWatchProgress("chris") } coAnswers { Result.success(emptyMap()) }

        assertTrue(service.syncIfNeeded())

        assertFalse(prefs.getBoolean("watch_progress_migrated", true))
    }

    // --- Cleanup ---

    @Test
    fun `cleanupCompletedProgress raeumt abgeschlossene Eintraege auf`() = runBlocking {
        seedProgress(
            mapOf(
                "movie:1" to progress(3_500_000, 3_600_000),   // abgeschlossen
                "movie:2" to progress(600_000, 3_600_000),     // laeuft noch
                "series:10:101" to progress(1_150_000, 1_200_000), // abgeschlossen
                "kaputt" to progress(3_500_000, 3_600_000)     // unbekannter Key
            )
        )

        val cleaned = service.cleanupCompletedProgress()

        assertEquals(2, cleaned)
        assertNull(service.getProgress("movie", 1, null, null))
        assertEquals(600_000L, service.getProgress("movie", 2, null, null))
        coVerify { watchedHistoryService.markAsWatched("movie", 1, null, null, null, null, null) }
        coVerify { watchedHistoryService.markAsWatched("series", 101, 10, 101, null, null, null) }
    }

    @Test
    fun `cleanupCompletedProgress ohne Eintraege liefert 0`() = runBlocking {
        assertEquals(0, service.cleanupCompletedProgress())
    }

    @Test
    fun `cleanupCompletedProgress ignoriert Eintraege mit nicht parsbarem Schluessel`() = runBlocking {
        seedProgress(
            mapOf(
                "movie:abc" to progress(3_500_000, 3_600_000),
                "series:10" to progress(3_500_000, 3_600_000),
                "series:abc:101" to progress(3_500_000, 3_600_000)
            )
        )

        val cleaned = service.cleanupCompletedProgress()

        assertEquals(0, cleaned)
        coVerify(exactly = 0) { watchedHistoryService.markAsWatched(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `cleanupCompletedProgress ignoriert Eintraege mit ungueltiger Dauer`() = runBlocking {
        seedProgress(mapOf("movie:1" to progress(3_500_000, 0)))

        assertEquals(0, service.cleanupCompletedProgress())
    }

    // --- Smart Resume ---

    private fun seriesFixture(): Series {
        val s1 = testSeason(
            seasonNumber = 1,
            episodes = listOf(
                testEpisode(id = 101, title = "S1F1", episodeNum = 1, season = 1),
                testEpisode(id = 102, title = "S1F2", episodeNum = 2, season = 1)
            )
        )
        val s2 = testSeason(
            seasonNumber = 2,
            episodes = listOf(testEpisode(id = 201, title = "S2F1", episodeNum = 1, season = 2))
        )
        return testSeries(seriesId = 10, seasons = listOf(s1, s2))
    }

    @Test
    fun `getLastInteraction bevorzugt den neuesten Zeitstempel`() = runBlocking {
        seedProgress(
            mapOf(
                "series:10:101" to WatchProgress(600_000, 1_200_000, "2026-07-15T10:00:00Z", "S1F1")
            )
        )
        coEvery { watchedHistoryService.getLatestWatchedEpisode(10) } returns
            (102 to 1_800_000_000_000L) // 2027 — neuer als der Fortschritt

        val interaction = service.getLastInteraction(10, seriesFixture())

        assertEquals(102, interaction?.episodeId)
        assertEquals(1.0f, interaction!!.progress, 0.001f)
    }

    @Test
    fun `getLastInteraction liefert Fortschritts-Eintrag ohne Historie`() = runBlocking {
        seedProgress(mapOf("series:10:101" to WatchProgress(600_000, 1_200_000, "2026-07-15T10:00:00Z", "S1F1")))
        coEvery { watchedHistoryService.getLatestWatchedEpisode(10) } returns null

        val interaction = service.getLastInteraction(10, seriesFixture())

        assertEquals(101, interaction?.episodeId)
        assertEquals(0.5f, interaction!!.progress, 0.001f)
        assertEquals(1, interaction.seasonNumber)
        assertEquals(1, interaction.episodeNumber)
    }

    @Test
    fun `getSmartResumeTarget Fall A setzt laufende Episode fort`() = runBlocking {
        seedProgress(mapOf("series:10:102" to WatchProgress(600_000, 1_200_000, "2026-07-15T10:00:00Z", "S1F2")))
        coEvery { watchedHistoryService.getLatestWatchedEpisode(10) } returns null

        val target = service.getSmartResumeTarget(10, seriesFixture())

        assertEquals(102, target?.episodeId)
    }

    @Test
    fun `getSmartResumeTarget Fall B springt ueber Staffelgrenze zur naechsten Episode`() = runBlocking {
        coEvery { watchedHistoryService.getLatestWatchedEpisode(10) } returns (102 to 1_752_600_000_000L)

        val target = service.getSmartResumeTarget(10, seriesFixture())

        // S1E2 abgeschlossen -> naechste ist S2E1
        assertEquals(201, target?.episodeId)
        assertEquals(2, target?.seasonNumber)
    }

    @Test
    fun `getSmartResumeTarget Fall C faellt auf erste Episode zurueck`() = runBlocking {
        coEvery { watchedHistoryService.getLatestWatchedEpisode(10) } returns null

        // Keine Historie
        assertEquals(101, service.getSmartResumeTarget(10, seriesFixture())?.episodeId)

        // Letzte Episode abgeschlossen -> keine naechste -> Fallback S1E1
        coEvery { watchedHistoryService.getLatestWatchedEpisode(10) } returns (201 to 1_752_600_000_000L)
        assertEquals(101, service.getSmartResumeTarget(10, seriesFixture())?.episodeId)
    }

    @Test
    fun `getSmartResumeTarget ohne Episoden liefert null`() = runBlocking {
        val leer = testSeries(seriesId = 10, seasons = listOf(testSeason(seasonNumber = 1, episodes = emptyList())))
        assertNull(service.getSmartResumeTarget(10, leer))
        assertNull(service.getSmartResumeTarget(10, testSeries(seriesId = 10, seasons = null)))
    }

    @Test
    fun `getLastInteraction ignoriert Eintraege mit ungueltiger Dauer oder Zeitstempel`() = runBlocking {
        seedProgress(
            mapOf(
                "series:10:101" to WatchProgress(600_000, 0, "2026-07-15T10:00:00Z", "S1F1"),
                "series:10:102" to WatchProgress(600_000, 1_200_000, "kein-datum", "S1F2")
            )
        )
        coEvery { watchedHistoryService.getLatestWatchedEpisode(10) } returns null

        assertNull(service.getLastInteraction(10, seriesFixture()))
    }

    @Test
    fun `findNextEpisode liefert null wenn Folgestaffel keine Episoden hat`() = runBlocking {
        val series = testSeries(
            seriesId = 20,
            seasons = listOf(
                testSeason(seasonNumber = 1, episodes = listOf(testEpisode(id = 101, episodeNum = 1, season = 1))),
                testSeason(seasonNumber = 2, episodes = emptyList())
            )
        )
        coEvery { watchedHistoryService.getLatestWatchedEpisode(20) } returns (101 to 1_752_600_000_000L)

        // Letzte Episode abgeschlossen, Folgestaffel ist leer -> Fallback auf erste Episode
        assertEquals(101, service.getSmartResumeTarget(20, series)?.episodeId)
    }

    @Test
    fun `getSmartResumeTarget liefert null wenn erste Staffel keine Episoden hat`() = runBlocking {
        val series = testSeries(
            seriesId = 30,
            seasons = listOf(
                testSeason(seasonNumber = 1, episodes = emptyList()),
                testSeason(seasonNumber = 2, episodes = listOf(testEpisode(id = 201, episodeNum = 1, season = 2)))
            )
        )

        assertNull(service.getSmartResumeTarget(30, series))
    }

    @Test
    fun `saveProgress speichert lokal auch ohne Zugangsdaten fuer Firestore-Sync`() = runBlocking {
        every { credentialsRepository.getCredentials() } returns null

        val saved = service.saveProgress("movie", 77, null, null, 600_000, 3_600_000, "Film")

        assertTrue(saved)
        assertEquals(600_000L, service.getProgress("movie", 77, null, null))
        coVerify(exactly = 0) { userRepository.saveWatchProgress(any(), any()) }
    }

    @Test
    fun `deleteProgress ueberspringt Firestore-Loeschung ohne Zugangsdaten`() = runBlocking {
        seedProgress(mapOf("movie:88" to progress(600_000, 3_600_000, "Film")))
        every { credentialsRepository.getCredentials() } returns null

        val result = service.saveProgress("movie", 88, null, null, 3_500_000, 3_600_000, "Film")

        assertTrue(result)
        assertNull(service.getProgress("movie", 88, null, null))
        coVerify(exactly = 0) { userRepository.deleteWatchProgress(any(), any()) }
    }

    @Test
    fun `Migration ohne lokale Daten markiert Flag ohne Firestore-Aufruf`() = runBlocking {
        val prefs = context.getSharedPreferences("watch_progress_prefs", Context.MODE_PRIVATE)
        prefs.edit {
            putBoolean("watch_progress_migrated", false)
            remove("iptv_watch_progress_data")
        }
        coEvery { userRepository.loadWatchProgress("chris") } coAnswers { Result.success(emptyMap()) }

        assertTrue(service.syncIfNeeded())

        coVerify(exactly = 0) { userRepository.migrateWatchProgressFromLocal(any(), any()) }
        assertTrue(prefs.getBoolean("watch_progress_migrated", false))
    }

    // --- Manuelles Setzen des Gesehen-Status ---

    @Test
    fun `setSeriesEpisodeWatched markiert und entfernt Fortschritt`() = runBlocking {
        seedProgress(mapOf("series:10:101" to progress(600_000, 1_200_000)))

        assertTrue(service.setSeriesEpisodeWatched(10, 101, watched = true))

        coVerify { watchedHistoryService.markAsWatched("series", null, 10, 101, null, null, null) }
        assertNull(service.getProgress("series", null, 10, 101))
    }

    @Test
    fun `setSeriesEpisodeWatched false markiert als ungesehen`() = runBlocking {
        assertTrue(service.setSeriesEpisodeWatched(10, 101, watched = false))
        coVerify { watchedHistoryService.markAsUnwatched("series", null, 10, 101) }
    }

    @Test
    fun `markSeriesEpisodesAsWatched delegiert und entfernt Fortschritte`() = runBlocking {
        seedProgress(mapOf("series:10:101" to progress(600_000, 1_200_000)))

        assertTrue(service.markSeriesEpisodesAsWatched(10, listOf(101, 102)))

        coVerify { watchedHistoryService.markSeriesEpisodesAsWatched(10, listOf(101, 102), null, null, null) }
        assertNull(service.getProgress("series", null, 10, 101))
    }

    @Test
    fun `markSeriesEpisodesAsWatched mit leerer Liste ist erfolgreich ohne Aufrufe`() = runBlocking {
        assertTrue(service.markSeriesEpisodesAsWatched(10, emptyList()))
        coVerify(exactly = 0) { watchedHistoryService.markSeriesEpisodesAsWatched(any(), any(), any(), any(), any()) }
    }

    // --- Delegationen ---

    @Test
    fun `isWatched und Batch-Abfragen delegieren an WatchedHistoryService`() = runBlocking {
        coEvery { watchedHistoryService.isWatched("movie", 1, null, null) } returns true
        coEvery { watchedHistoryService.getWatchedEpisodeIds(10) } returns setOf(101)
        coEvery { watchedHistoryService.getWatchedVodIds(null) } returns setOf(1, 2)

        assertTrue(service.isWatched("movie", 1, null, null))
        assertEquals(setOf(101), service.getWatchedEpisodeIds(10))
        assertEquals(setOf(1, 2), service.getWatchedVodIds())
    }
}
