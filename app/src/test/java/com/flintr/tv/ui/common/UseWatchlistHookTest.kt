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

    private val ownWatchlist = MutableStateFlow<List<WatchlistEntry>>(emptyList())

    @Before
    fun setUp() {
        watchlistRepository = mockk()
        userRepository = mockk()
        credentialsRepository = mockk()
        cacheService = mockk()

        every { watchlistRepository.entries } returns ownWatchlist
        coEvery { watchlistRepository.syncIfNeeded() } returns true
        every { credentialsRepository.getCredentials() } returns
            Credentials(serverUrl = "http://srv", username = "chris", password = "pw")
        coEvery { userRepository.loadFriends("chris") } coAnswers { Result.success(emptyList()) }
        coEvery { cacheService.loadMoviesByIds(any()) } returns emptyList()
    }

    private fun entry(id: String, streamId: Int) = WatchlistEntry(
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
                watchlistEntryType =WatchlistEntryType.MOVIE,
                idSelector = { it.streamId }
            )
        }
        composeTestRule.waitForIdle()
        return { state!! }
    }

    @Test
    fun `ausserhalb der Watchlist-Kategorie bleibt die Liste leer`() {
        ownWatchlist.value = listOf(entry("movie_1", 1))

        val state = collectState(isWatchlistCategory = false)

        assertTrue(state().items.isEmpty())
        assertFalse(state().isLoading)
    }

    @Test
    fun `eigene Watchlist-Eintraege werden aus dem Cache aufgeloest`() {
        ownWatchlist.value = listOf(entry("movie_1", 1), entry("movie_2", 2))
        coEvery { cacheService.loadMoviesByIds(listOf(1, 2)) } returns listOf(
            testVodStream(name = "Film 1", streamId = 1),
            testVodStream(name = "Film 2", streamId = 2)
        )

        val state = collectState()

        assertEquals(listOf(1, 2), state().items.map { it.item.streamId })
        assertTrue(state().items.all { it.source.isOwn && it.allowWatchlistActions })
    }

    @Test
    fun `fehlender Cache liefert leere Watchlist`() {
        ownWatchlist.value = listOf(entry("movie_1", 1))
        coEvery { cacheService.loadMoviesByIds(any()) } returns null

        val state = collectState()

        assertTrue(state().items.isEmpty())
    }

    @Test
    fun `Freundes-Watchlist-Eintraege werden mit Badge angehaengt`() {
        ownWatchlist.value = listOf(entry("movie_1", 1))
        coEvery { cacheService.loadMoviesByIds(listOf(1)) } returns listOf(testVodStream(name = "Eigener", streamId = 1))
        coEvery { userRepository.loadFriends("chris") } coAnswers {
            Result.success(listOf(FriendEntry(username = "thomas", alias = "Tom", addedAt = "2026-07-16T10:00:00Z")))
        }
        coEvery { userRepository.loadWatchlist("thomas") } coAnswers {
            Result.success(listOf(entry("movie_9", 9)))
        }
        coEvery { cacheService.loadMoviesByIds(listOf(9)) } returns listOf(testVodStream(name = "Fremder", streamId = 9))

        val state = collectState()

        assertEquals(listOf(1, 9), state().items.map { it.item.streamId })
        val friendItem = state().items.last()
        assertFalse(friendItem.source.isOwn)
        assertFalse(friendItem.allowWatchlistActions)
        assertEquals("To", friendItem.source.badge)
    }

    @Test
    fun `Fehler beim Laden der Freunde liefert nur eigene Watchlist-Eintraege`() {
        ownWatchlist.value = listOf(entry("movie_1", 1))
        coEvery { cacheService.loadMoviesByIds(listOf(1)) } returns listOf(testVodStream(streamId = 1))
        coEvery { userRepository.loadFriends("chris") } coAnswers { Result.failure(Exception("offline")) }

        val state = collectState()

        assertEquals(1, state().items.size)
        assertTrue(state().items.first().source.isOwn)
    }

    @Test
    fun `Freunde ohne passende Watchlist-Eintraege werden uebersprungen`() {
        coEvery { userRepository.loadFriends("chris") } coAnswers {
            Result.success(listOf(FriendEntry(username = "anna", alias = null, addedAt = "2026-07-16T10:00:00Z")))
        }
        coEvery { userRepository.loadWatchlist("anna") } coAnswers { Result.success(emptyList()) }

        val state = collectState()

        assertTrue(state().items.isEmpty())
    }

    @Test
    fun `ohne aktiven Nutzer werden keine Freunde geladen`() {
        every { credentialsRepository.getCredentials() } returns null
        ownWatchlist.value = listOf(entry("movie_1", 1))
        coEvery { cacheService.loadMoviesByIds(listOf(1)) } returns listOf(testVodStream(streamId = 1))

        val state = collectState()

        assertEquals(1, state().items.size)
    }
}
