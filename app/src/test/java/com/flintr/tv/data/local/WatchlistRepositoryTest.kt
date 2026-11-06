package com.flintr.tv.data.local

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import com.flintr.tv.data.model.WatchlistEntry
import com.flintr.tv.data.model.WatchlistEntryType
import com.flintr.tv.data.repository.UserRepository
import com.squareup.moshi.Moshi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import com.flintr.tv.testutil.silentCredentialsFlow
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
class WatchlistRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val moshi = Moshi.Builder().build()
    private val credentials = Credentials(serverUrl = "http://server", username = "chris", password = "pw")

    private lateinit var userRepository: UserRepository
    private lateinit var credentialsRepository: CredentialsRepository
    private lateinit var repository: WatchlistRepository

    @Before
    fun setUp() {
        context.getSharedPreferences("favorites_prefs", Context.MODE_PRIVATE).edit {
            putBoolean("favorites_migrated", true)
        }

        userRepository = mockk(relaxed = true)
        credentialsRepository = mockk()
        every { credentialsRepository.credentialsFlow } returns silentCredentialsFlow
        every { credentialsRepository.getCredentials() } returns credentials
        coEvery { userRepository.addToWatchlistAtomically(any(), any()) } returns Result.success(Unit)
        coEvery { userRepository.removeFromWatchlistAtomically(any(), any()) } returns Result.success(Unit)

        repository = WatchlistRepository(
            context = context,
            moshi = moshi,
            userRepository = userRepository,
            credentialsRepository = credentialsRepository,
            syncManager = FirestoreSyncManager(syncCooldownMs = 0L)
        )
    }

    private fun entry(id: String = "movie_1", type: WatchlistEntryType = WatchlistEntryType.MOVIE, streamId: Int = 1) =
        WatchlistEntry(
            id = id,
            type = type,
            streamId = streamId,
            name = "Testeintrag",
            metadata = null,
            addedAt = "2026-07-16T10:00:00Z"
        )

    @Test
    fun `addToWatchlist fuegt neuen Watchlist-Eintrag hinzu und synct atomar`() = runBlocking {
        assertTrue(repository.addToWatchlist(entry()))

        assertTrue(repository.isInWatchlist("movie_1"))
        assertEquals(1, repository.getAllWatchlistEntries().size)
        coVerify { userRepository.addToWatchlistAtomically("chris", any()) }
    }

    @Test
    fun `addToWatchlist lehnt Duplikate ab`() = runBlocking {
        assertTrue(repository.addToWatchlist(entry()))
        assertFalse(repository.addToWatchlist(entry()))
        assertEquals(1, repository.getAllWatchlistEntries().size)
    }

    @Test
    fun `addToWatchlist bleibt bei Firestore-Fehler optimistisch erfolgreich`() = runBlocking {
        coEvery { userRepository.addToWatchlistAtomically(any(), any()) } coAnswers { Result.failure(Exception("offline")) }

        assertTrue(repository.addToWatchlist(entry()))
        assertTrue(repository.isInWatchlist("movie_1"))
    }

    @Test
    fun `concurrent watchlist mutations do not lose updates`() {
        // addToWatchlist hat vor der letzten write-Anweisung keinen Suspension-Point,
        // daher reicht ein virtueller TestDispatcher nicht aus, um die Race zu
        // provozieren (der zweite Coroutine-Block würde nie mit dem ersten
        // interleaven). Ein CyclicBarrier(2) + echter Dispatchers.Default-Pool
        // erzwingt einen moeglichst gleichzeitigen Start beider addToWatchlist-Aufrufe;
        // ueber mehrere Runden erhoeht das die Trefferwahrscheinlichkeit fuer die Race.
        repeat(15) { round ->
            val startGate = java.util.concurrent.CyclicBarrier(2)
            val entryA = entry(id = "movie_a$round", streamId = round * 2)
            val entryB = entry(id = "movie_b$round", streamId = round * 2 + 1)

            runBlocking {
                coroutineScope {
                    val jobA = async(Dispatchers.Default) {
                        startGate.await()
                        repository.addToWatchlist(entryA)
                    }
                    val jobB = async(Dispatchers.Default) {
                        startGate.await()
                        repository.addToWatchlist(entryB)
                    }
                    awaitAll(jobA, jobB)
                }
            }

            assertTrue("Runde $round: ${entryA.id} fehlt", repository.isInWatchlist(entryA.id))
            assertTrue("Runde $round: ${entryB.id} fehlt", repository.isInWatchlist(entryB.id))
        }
    }

    @Test
    fun `gleichzeitiges Doppel-Hinzufuegen desselben Eintrags legt genau einen an`() {
        // Gleiches Muster wie oben (CyclicBarrier + Dispatchers.Default), aber DERSELBE Entry:
        // genau ein Aufruf darf gewinnen, kein Duplikat, nur ein Firestore-Sync pro Runde.
        repeat(15) { round ->
            val startGate = java.util.concurrent.CyclicBarrier(2)
            val same = entry(id = "movie_same$round", streamId = round)

            val results = runBlocking {
                coroutineScope {
                    val jobA = async(Dispatchers.Default) {
                        startGate.await()
                        repository.addToWatchlist(same)
                    }
                    val jobB = async(Dispatchers.Default) {
                        startGate.await()
                        repository.addToWatchlist(same)
                    }
                    awaitAll(jobA, jobB)
                }
            }

            assertEquals("Runde $round: Ergebnisse $results", 1, results.count { it })
            assertEquals("Runde $round: Duplikat", 1, runBlocking { repository.getAllWatchlistEntries() }.count { it.id == same.id })
            coVerify(exactly = 1) { userRepository.addToWatchlistAtomically("chris", same) }
        }
    }

    @Test
    fun `removeFromWatchlist entfernt vorhandenen Watchlist-Eintrag`() = runBlocking {
        repository.addToWatchlist(entry())

        assertTrue(repository.removeFromWatchlist("movie_1"))
        assertFalse(repository.isInWatchlist("movie_1"))
        coVerify { userRepository.removeFromWatchlistAtomically("chris", "movie_1") }
    }

    @Test
    fun `removeFromWatchlist ohne Treffer liefert false ohne Firestore-Aufruf`() = runBlocking {
        assertFalse(repository.removeFromWatchlist("unbekannt"))
        coVerify(exactly = 0) { userRepository.removeFromWatchlistAtomically(any(), any()) }
    }

    @Test
    fun `getWatchlistEntriesByType und Zaehler filtern korrekt`() = runBlocking {
        repository.addToWatchlist(entry(id = "movie_1", type = WatchlistEntryType.MOVIE, streamId = 1))
        repository.addToWatchlist(entry(id = "live_2", type = WatchlistEntryType.LIVE, streamId = 2))
        repository.addToWatchlist(entry(id = "movie_3", type = WatchlistEntryType.MOVIE, streamId = 3))

        assertEquals(listOf("movie_1", "movie_3"), repository.getWatchlistEntriesByType(WatchlistEntryType.MOVIE).map { it.id })
        assertEquals(2, repository.getWatchlistCountByType(WatchlistEntryType.MOVIE))
        assertEquals(1, repository.getWatchlistCountByType(WatchlistEntryType.LIVE))
        assertEquals(0, repository.getWatchlistCountByType(WatchlistEntryType.SERIES))
    }

    @Test
    fun `syncIfNeeded laedt Watchlist von Firestore`() = runBlocking {
        coEvery { userRepository.loadWatchlist("chris") } coAnswers { Result.success(listOf(entry())) }

        assertTrue(repository.syncIfNeeded())
        assertTrue(repository.isInWatchlist("movie_1"))
    }

    @Test
    fun `fehlgeschlagenes Laden laesst Liste leer`() = runBlocking {
        coEvery { userRepository.loadWatchlist("chris") } coAnswers { Result.failure(Exception("offline")) }

        assertTrue(repository.syncIfNeeded())
        assertTrue(repository.getAllWatchlistEntries().isEmpty())
    }

    @Test
    fun `clearWatchlist leert Liste und speichert nach Firestore`() = runBlocking {
        coEvery { userRepository.loadWatchlist("chris") } coAnswers { Result.success(listOf(entry())) }
        coEvery { userRepository.saveWatchlist("chris", emptyList()) } returns Result.success(Unit)

        repository.clearWatchlist()

        assertTrue(repository.getAllWatchlistEntries().isEmpty())
        coVerify(timeout = 2_000) { userRepository.saveWatchlist("chris", emptyList()) }
    }

    @Test
    fun `clearWatchlist bricht bei fehlgeschlagenem Pre-Load ab und erhaelt Watchlist`() = runBlocking {
        coEvery { userRepository.loadWatchlist("chris") } coAnswers { Result.success(listOf(entry())) }
        repository.syncIfNeeded()
        coEvery { userRepository.loadWatchlist("chris") } coAnswers { Result.failure(Exception("offline")) }

        repository.clearWatchlist()

        assertTrue(repository.isInWatchlist("movie_1"))
        assertEquals(1, repository.getAllWatchlistEntries().size)
        coVerify(exactly = 0) { userRepository.saveWatchlist(any(), emptyList()) }
    }

    @Test
    fun `Migration uebertraegt lokale Watchlist-Eintraege und setzt Flag`() = runBlocking {
        val prefs = context.getSharedPreferences("favorites_prefs", Context.MODE_PRIVATE)
        prefs.edit {
            putBoolean("favorites_migrated", false)
            putString(
                "iptv_favorites",
                """[{"id":"movie_7","type":"movie","streamId":7,"name":"Alt","addedAt":"2026-07-16T10:00:00Z"}]"""
            )
        }
        coEvery { userRepository.migrateWatchlistFromLocal("chris", any()) } coAnswers { Result.success(Unit) }
        coEvery { userRepository.loadWatchlist("chris") } coAnswers { Result.success(emptyList()) }

        assertTrue(repository.syncIfNeeded())

        coVerify {
            userRepository.migrateWatchlistFromLocal("chris", match { it.size == 1 && it.first().id == "movie_7" })
        }
        assertTrue(prefs.getBoolean("favorites_migrated", false))
        assertNull(prefs.getString("iptv_favorites", null))
    }

    @Test
    fun `fehlgeschlagene Migration setzt Flag nicht`() = runBlocking {
        val prefs = context.getSharedPreferences("favorites_prefs", Context.MODE_PRIVATE)
        prefs.edit {
            putBoolean("favorites_migrated", false)
            putString(
                "iptv_favorites",
                """[{"id":"movie_7","type":"movie","streamId":7,"name":"Alt","addedAt":"2026-07-16T10:00:00Z"}]"""
            )
        }
        coEvery { userRepository.migrateWatchlistFromLocal("chris", any()) } coAnswers { Result.failure(Exception("offline")) }
        coEvery { userRepository.loadWatchlist("chris") } coAnswers { Result.success(emptyList()) }

        assertTrue(repository.syncIfNeeded())

        assertFalse(prefs.getBoolean("favorites_migrated", true))
    }
}
