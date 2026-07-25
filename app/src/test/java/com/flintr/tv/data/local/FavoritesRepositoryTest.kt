package com.flintr.tv.data.local

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import com.flintr.tv.data.model.Favorite
import com.flintr.tv.data.model.FavoriteType
import com.flintr.tv.data.repository.UserRepository
import com.squareup.moshi.Moshi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import com.flintr.tv.testutil.silentCredentialsFlow
import io.mockk.mockk
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
class FavoritesRepositoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val moshi = Moshi.Builder().build()
    private val credentials = Credentials(serverUrl = "http://server", username = "chris", password = "pw")

    private lateinit var userRepository: UserRepository
    private lateinit var credentialsRepository: CredentialsRepository
    private lateinit var repository: FavoritesRepository

    @Before
    fun setUp() {
        context.getSharedPreferences("favorites_prefs", Context.MODE_PRIVATE).edit {
            putBoolean("favorites_migrated", true)
        }

        userRepository = mockk(relaxed = true)
        credentialsRepository = mockk()
        every { credentialsRepository.credentialsFlow } returns silentCredentialsFlow
        every { credentialsRepository.getCredentials() } returns credentials
        coEvery { userRepository.addFavoriteAtomically(any(), any()) } returns Result.success(Unit)
        coEvery { userRepository.removeFavoriteAtomically(any(), any()) } returns Result.success(Unit)

        repository = FavoritesRepository(
            context = context,
            moshi = moshi,
            userRepository = userRepository,
            credentialsRepository = credentialsRepository,
            syncManager = FirestoreSyncManager(syncCooldownMs = 0L)
        )
    }

    private fun favorite(id: String = "movie_1", type: FavoriteType = FavoriteType.MOVIE, streamId: Int = 1) =
        Favorite(
            id = id,
            type = type,
            streamId = streamId,
            name = "Testeintrag",
            metadata = null,
            addedAt = "2026-07-16T10:00:00Z"
        )

    @Test
    fun `addFavorite fuegt neuen Favoriten hinzu und synct atomar`() = runBlocking {
        assertTrue(repository.addFavorite(favorite()))

        assertTrue(repository.isFavorite("movie_1"))
        assertEquals(1, repository.getAllFavorites().size)
        coVerify { userRepository.addFavoriteAtomically("chris", any()) }
    }

    @Test
    fun `addFavorite lehnt Duplikate ab`() = runBlocking {
        assertTrue(repository.addFavorite(favorite()))
        assertFalse(repository.addFavorite(favorite()))
        assertEquals(1, repository.getAllFavorites().size)
    }

    @Test
    fun `addFavorite bleibt bei Firestore-Fehler optimistisch erfolgreich`() = runBlocking {
        coEvery { userRepository.addFavoriteAtomically(any(), any()) } coAnswers { Result.failure(Exception("offline")) }

        assertTrue(repository.addFavorite(favorite()))
        assertTrue(repository.isFavorite("movie_1"))
    }

    @Test
    fun `removeFavorite entfernt vorhandenen Favoriten`() = runBlocking {
        repository.addFavorite(favorite())

        assertTrue(repository.removeFavorite("movie_1"))
        assertFalse(repository.isFavorite("movie_1"))
        coVerify { userRepository.removeFavoriteAtomically("chris", "movie_1") }
    }

    @Test
    fun `removeFavorite ohne Treffer liefert false ohne Firestore-Aufruf`() = runBlocking {
        assertFalse(repository.removeFavorite("unbekannt"))
        coVerify(exactly = 0) { userRepository.removeFavoriteAtomically(any(), any()) }
    }

    @Test
    fun `getFavoritesByType und Zaehler filtern korrekt`() = runBlocking {
        repository.addFavorite(favorite(id = "movie_1", type = FavoriteType.MOVIE, streamId = 1))
        repository.addFavorite(favorite(id = "live_2", type = FavoriteType.LIVE, streamId = 2))
        repository.addFavorite(favorite(id = "movie_3", type = FavoriteType.MOVIE, streamId = 3))

        assertEquals(listOf("movie_1", "movie_3"), repository.getFavoritesByType(FavoriteType.MOVIE).map { it.id })
        assertEquals(2, repository.getFavoriteCountByType(FavoriteType.MOVIE))
        assertEquals(1, repository.getFavoriteCountByType(FavoriteType.LIVE))
        assertEquals(0, repository.getFavoriteCountByType(FavoriteType.SERIES))
    }

    @Test
    fun `syncIfNeeded laedt Favoriten von Firestore`() = runBlocking {
        coEvery { userRepository.loadFavorites("chris") } coAnswers { Result.success(listOf(favorite())) }

        assertTrue(repository.syncIfNeeded())
        assertTrue(repository.isFavorite("movie_1"))
    }

    @Test
    fun `fehlgeschlagenes Laden laesst Liste leer`() = runBlocking {
        coEvery { userRepository.loadFavorites("chris") } coAnswers { Result.failure(Exception("offline")) }

        assertTrue(repository.syncIfNeeded())
        assertTrue(repository.getAllFavorites().isEmpty())
    }

    @Test
    fun `clearAllFavorites leert Liste und speichert nach Firestore`() = runBlocking {
        coEvery { userRepository.loadFavorites("chris") } coAnswers { Result.success(listOf(favorite())) }
        coEvery { userRepository.saveFavorites("chris", emptyList()) } returns Result.success(Unit)

        repository.clearAllFavorites()

        assertTrue(repository.getAllFavorites().isEmpty())
        coVerify(timeout = 2_000) { userRepository.saveFavorites("chris", emptyList()) }
    }

    @Test
    fun `Migration uebertraegt lokale Favoriten und setzt Flag`() = runBlocking {
        val prefs = context.getSharedPreferences("favorites_prefs", Context.MODE_PRIVATE)
        prefs.edit {
            putBoolean("favorites_migrated", false)
            putString(
                "iptv_favorites",
                """[{"id":"movie_7","type":"movie","streamId":7,"name":"Alt","addedAt":"2026-07-16T10:00:00Z"}]"""
            )
        }
        coEvery { userRepository.migrateFavoritesFromLocal("chris", any()) } coAnswers { Result.success(Unit) }
        coEvery { userRepository.loadFavorites("chris") } coAnswers { Result.success(emptyList()) }

        assertTrue(repository.syncIfNeeded())

        coVerify {
            userRepository.migrateFavoritesFromLocal("chris", match { it.size == 1 && it.first().id == "movie_7" })
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
        coEvery { userRepository.migrateFavoritesFromLocal("chris", any()) } coAnswers { Result.failure(Exception("offline")) }
        coEvery { userRepository.loadFavorites("chris") } coAnswers { Result.success(emptyList()) }

        assertTrue(repository.syncIfNeeded())

        assertFalse(prefs.getBoolean("favorites_migrated", true))
    }
}
