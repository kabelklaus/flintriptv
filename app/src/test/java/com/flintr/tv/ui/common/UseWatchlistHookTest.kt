package com.flintr.tv.ui.common

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flintr.tv.data.local.Credentials
import com.flintr.tv.data.local.CredentialsRepository
import com.flintr.tv.data.local.DataCacheService
import com.flintr.tv.data.local.WatchlistRepository
import com.flintr.tv.data.model.WatchlistEntry
import com.flintr.tv.data.model.WatchlistEntryType
import com.flintr.tv.data.model.FriendEntry
import com.flintr.tv.data.model.VodStream
import com.flintr.tv.data.model.testVodStream
import com.flintr.tv.data.repository.UserRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Verhalten des useWatchlist-Hooks als lokale Unit-Tests (Robolectric),
 * damit die Abdeckung in den Jacoco-Report einfließt, den Sonar konsumiert.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w1280dp-h720dp-land")
class UseWatchlistHookTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var watchlistRepository: WatchlistRepository
    private lateinit var userRepository: UserRepository
    private lateinit var credentialsRepository: CredentialsRepository
    private lateinit var cacheService: DataCacheService

    private val ownFavorites = MutableStateFlow<List<WatchlistEntry>>(emptyList())

    @Before
    fun setUp() {
        watchlistRepository = mockk()
        userRepository = mockk()
        credentialsRepository = mockk()
        cacheService = mockk()

        every { watchlistRepository.favorites } returns ownFavorites
        coEvery { watchlistRepository.syncIfNeeded() } returns true
        every { credentialsRepository.getCredentials() } returns
            Credentials(serverUrl = "http://srv", username = "chris", password = "pw")
        coEvery { userRepository.loadFriends("chris") } coAnswers { Result.success(emptyList()) }
        coEvery { cacheService.loadMoviesByIds(any()) } returns emptyList()
    }

    private fun favorite(id: String, streamId: Int) = WatchlistEntry(
        id = id,
        type = WatchlistEntryType.MOVIE,
        streamId = streamId,
        name = "Fav $streamId",
        metadata = null,
        addedAt = "2026-07-16T10:00:00Z"
    )

    private fun collectState(isWatchlistCategory: Boolean = true): () -> WatchlistState<SourcedWatchlistItem<VodStream>> {
        var state: WatchlistState<SourcedWatchlistItem<VodStream>>? = null
        composeTestRule.setContent {
            state = useWatchlist(
                isWatchlistCategory = isWatchlistCategory,
                watchlistRepository = watchlistRepository,
                userRepository = userRepository,
                credentialsRepository = credentialsRepository,
                cacheService = cacheService,
                favoriteType = WatchlistEntryType.MOVIE,
                idSelector = { it.streamId }
            )
        }
        composeTestRule.waitForIdle()
        return { state!! }
    }

    @Test
    fun `ausserhalb der Favoriten-Kategorie bleibt die Liste leer`() {
        ownFavorites.value = listOf(favorite("movie_1", 1))

        val state = collectState(isWatchlistCategory = false)

        assertTrue(state().items.isEmpty())
        assertFalse(state().isLoading)
    }

    @Test
    fun `eigene Favoriten werden aus dem Cache aufgeloest`() {
        ownFavorites.value = listOf(favorite("movie_1", 1), favorite("movie_2", 2))
        coEvery { cacheService.loadMoviesByIds(listOf(1, 2)) } returns listOf(
            testVodStream(name = "Film 1", streamId = 1),
            testVodStream(name = "Film 2", streamId = 2)
        )

        val state = collectState()

        assertEquals(listOf(1, 2), state().items.map { it.item.streamId })
        assertTrue(state().items.all { it.source.isOwn && it.allowFavoriteActions })
    }

    @Test
    fun `fehlender Cache liefert leere Favoritenliste`() {
        ownFavorites.value = listOf(favorite("movie_1", 1))
        coEvery { cacheService.loadMoviesByIds(any()) } returns null

        val state = collectState()

        assertTrue(state().items.isEmpty())
    }

    @Test
    fun `Freundes-Favoriten werden mit Badge angehaengt`() {
        ownFavorites.value = listOf(favorite("movie_1", 1))
        coEvery { cacheService.loadMoviesByIds(listOf(1)) } returns listOf(testVodStream(name = "Eigener", streamId = 1))
        coEvery { userRepository.loadFriends("chris") } coAnswers {
            Result.success(listOf(FriendEntry(username = "thomas", alias = "Tom", addedAt = "2026-07-16T10:00:00Z")))
        }
        coEvery { userRepository.loadFavorites("thomas") } coAnswers {
            Result.success(listOf(favorite("movie_9", 9)))
        }
        coEvery { cacheService.loadMoviesByIds(listOf(9)) } returns listOf(testVodStream(name = "Fremder", streamId = 9))

        val state = collectState()

        assertEquals(listOf(1, 9), state().items.map { it.item.streamId })
        val friendItem = state().items.last()
        assertFalse(friendItem.source.isOwn)
        assertFalse(friendItem.allowFavoriteActions)
        assertEquals("To", friendItem.source.badge)
    }

    @Test
    fun `Fehler beim Laden der Freunde liefert nur eigene Favoriten`() {
        ownFavorites.value = listOf(favorite("movie_1", 1))
        coEvery { cacheService.loadMoviesByIds(listOf(1)) } returns listOf(testVodStream(streamId = 1))
        coEvery { userRepository.loadFriends("chris") } coAnswers { Result.failure(Exception("offline")) }

        val state = collectState()

        assertEquals(1, state().items.size)
        assertTrue(state().items.first().source.isOwn)
    }

    @Test
    fun `Freunde ohne passende Favoriten werden uebersprungen`() {
        coEvery { userRepository.loadFriends("chris") } coAnswers {
            Result.success(listOf(FriendEntry(username = "anna", alias = null, addedAt = "2026-07-16T10:00:00Z")))
        }
        coEvery { userRepository.loadFavorites("anna") } coAnswers { Result.success(emptyList()) }

        val state = collectState()

        assertTrue(state().items.isEmpty())
    }

    @Test
    fun `ohne aktiven Nutzer werden keine Freunde geladen`() {
        every { credentialsRepository.getCredentials() } returns null
        ownFavorites.value = listOf(favorite("movie_1", 1))
        coEvery { cacheService.loadMoviesByIds(listOf(1)) } returns listOf(testVodStream(streamId = 1))

        val state = collectState()

        assertEquals(1, state().items.size)
    }
}
