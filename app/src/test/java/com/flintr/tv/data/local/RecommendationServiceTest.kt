package com.flintr.tv.data.local

import com.flintr.tv.data.model.Category
import com.flintr.tv.data.model.WatchlistEntry
import com.flintr.tv.data.model.WatchlistEntryType
import com.flintr.tv.data.model.Series
import com.flintr.tv.data.model.VodStream
import com.flintr.tv.data.repository.UserRepository
import com.flintr.tv.testutil.silentCredentialsFlow
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecommendationServiceTest {

    private val credentials = Credentials(serverUrl = "http://server", username = "chris", password = "pw")

    private lateinit var watchlistRepository: WatchlistRepository
    private lateinit var watchedHistoryService: WatchedHistoryService
    private lateinit var watchProgressService: WatchProgressService
    private lateinit var userRepository: UserRepository
    private lateinit var credentialsRepository: CredentialsRepository
    private lateinit var cacheService: DataCacheService
    private lateinit var service: RecommendationService

    private lateinit var watchlistFlow: MutableStateFlow<List<WatchlistEntry>>
    private lateinit var watchedHistoryFlow: MutableStateFlow<Map<String, com.flintr.tv.data.model.WatchedItem>>
    private lateinit var watchProgressFlow: MutableStateFlow<Map<String, com.flintr.tv.data.model.WatchProgress>>

    private fun testVod(streamId: Int, genre: String) = VodStream(
        num = null, name = "Movie$streamId", streamType = null, streamId = streamId,
        streamIcon = null, rating = null, rating5Based = null, added = null,
        categoryId = null, categoryName = null, containerExtension = null, customSid = null,
        directSource = null, releaseDate = null, duration = null, plot = null, cast = null,
        director = null, genre = genre, backdropPath = null, youtubeTrailer = null,
        cover = null, tmdbId = null
    )

    @Before
    fun setUp() {
        watchlistFlow = MutableStateFlow<List<WatchlistEntry>>(emptyList())
        watchedHistoryFlow = MutableStateFlow<Map<String, com.flintr.tv.data.model.WatchedItem>>(emptyMap())
        watchProgressFlow = MutableStateFlow<Map<String, com.flintr.tv.data.model.WatchProgress>>(emptyMap())

        watchlistRepository = mockk(relaxed = true)
        watchedHistoryService = mockk(relaxed = true)
        watchProgressService = mockk(relaxed = true)
        userRepository = mockk(relaxed = true)
        credentialsRepository = mockk()
        cacheService = mockk(relaxed = true)

        every { watchlistRepository.entries } returns watchlistFlow
        every { watchedHistoryService.watchedHistory } returns watchedHistoryFlow
        every { watchProgressService.watchProgress } returns watchProgressFlow
        every { credentialsRepository.credentialsFlow } returns silentCredentialsFlow
        every { credentialsRepository.getCredentials() } returns credentials
        coEvery { userRepository.loadAllRatings(any()) } returns Result.success(emptyMap())

        val catalogMovies = (1..5).map { testVod(it, "Action") } + listOf(
            testVod(101, "Action"),
            testVod(102, "Action")
        )
        coEvery { cacheService.loadCachedDataAsync() } returns CachedData(
            channels = emptyList(),
            movies = catalogMovies,
            series = emptyList<Series>(),
            liveCategories = emptyList<Category>(),
            vodCategories = emptyList<Category>(),
            seriesCategories = emptyList<Category>()
        )

        service = RecommendationService(
            watchlistRepository = watchlistRepository,
            watchedHistoryService = watchedHistoryService,
            watchProgressService = watchProgressService,
            userRepository = userRepository,
            credentialsRepository = credentialsRepository,
            cacheService = cacheService,
            ioDispatcher = Dispatchers.Unconfined
        )
    }

    @After
    fun tearDown() {
        val field = RecommendationService::class.java.getDeclaredField("serviceScope")
        field.isAccessible = true
        val scope = field.get(service) as CoroutineScope
        runBlocking { scope.coroutineContext[Job]?.cancelAndJoin() }
    }

    private fun entry(streamId: Int) = WatchlistEntry.create(
        id = "movie:$streamId:x",
        type = WatchlistEntryType.MOVIE,
        streamId = streamId,
        name = "Movie$streamId"
    )

    @Test
    fun `adding watchlist entries recomputes recommendations`() = runBlocking {
        delay(400)
        assertTrue(service.recommendations.value.movies.isEmpty())

        watchlistFlow.value = (1..5).map { entry(it) }
        delay(400)

        val recommendedIds = service.recommendations.value.movies.map { it.streamId }
        assertEquals(setOf(101, 102), recommendedIds.toSet())
        assertTrue((1..5).none { it in recommendedIds })
    }
}
