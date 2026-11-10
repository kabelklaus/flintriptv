package com.flintr.tv.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flintr.tv.data.local.WatchlistRepository
import com.flintr.tv.data.model.Series
import com.flintr.tv.data.model.VodStream
import com.flintr.tv.data.tmdb.TmdbCardState
import com.flintr.tv.data.tmdb.TmdbMeta
import com.flintr.tv.data.tmdb.TmdbMetaRepository
import com.flintr.tv.ui.theme.FlintRTVTheme
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Grid-Chip: Treffer in [TmdbMetaRepository.metaFlow] zeigt sofort, sonst entscheidet
 * [TmdbMetaRepository.cardMetaState] zwischen Meta, Negative (Chip aus) und Transient (Skeleton).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w1280dp-h720dp-land")
class ContentCardTmdbRatingTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var metaFlow: MutableStateFlow<Map<String, TmdbMeta>>
    private lateinit var repository: TmdbMetaRepository
    private lateinit var watchlistRepository: WatchlistRepository
    private val requests = mutableListOf<Pair<String, String>>()

    @Before
    fun setUp() {
        metaFlow = MutableStateFlow(emptyMap())
        repository = mockk()
        every { repository.metaFlow } returns metaFlow
        coEvery { repository.request(any(), any()) } coAnswers {
            requests += firstArg<String>() to secondArg<String>()
        }
        coEvery {
            repository.cardMetaState(any(), any(), any(), any())
        } returns TmdbCardState.Negative
        watchlistRepository = mockk(relaxed = true)
        every { watchlistRepository.isInWatchlist(any()) } returns false
    }

    private fun stubCardState(state: TmdbCardState, recordRequest: Boolean = false) {
        coEvery {
            repository.cardMetaState(any(), any(), any(), any())
        } coAnswers {
            if (recordRequest) {
                val tmdbId = secondArg<String?>()
                if (tmdbId != null) requests += firstArg<String>() to tmdbId
            }
            state
        }
    }

    private fun showMovie(tmdbId: String?) {
        composeTestRule.setContent {
            FlintRTVTheme {
                CompositionLocalProvider(LocalTmdbMetaRepository provides repository) {
                    VODCard(
                        VODCardParams(
                            vod = vod(tmdbId),
                            onClick = {},
                            watchlistRepository = watchlistRepository
                        )
                    )
                }
            }
        }
    }

    private fun showSeries(tmdbId: String?) {
        composeTestRule.setContent {
            FlintRTVTheme {
                CompositionLocalProvider(LocalTmdbMetaRepository provides repository) {
                    VODCard(
                        VODCardParams(
                            series = series(tmdbId),
                            onClick = {},
                            watchlistRepository = watchlistRepository
                        )
                    )
                }
            }
        }
    }

    @Test
    fun filmMetaZeigtChipMitFormatierterBewertung() {
        metaFlow.value = mapOf("movie:14306" to meta(14306, 8.4))
        stubCardState(TmdbCardState.Transient, recordRequest = true)

        showMovie("14306")

        composeTestRule.onNodeWithText("8.4").assertIsDisplayed()
        assertEquals(listOf("movie" to "14306"), requests)
    }

    @Test
    fun filmOhneTmdbIdOhneTrefferZeigtKeinenChip() {
        showMovie(null)

        composeTestRule.onAllNodesWithText("8.4").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("rating-skeleton").assertCountEquals(0)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun filmOhneTmdbIdZeigtChipNachSearchResolve() {
        coEvery {
            repository.cardMetaState("movie", null, "Testfilm", null)
        } returns TmdbCardState.Meta(meta(14306, 8.4))

        showMovie(null)

        composeTestRule.onNodeWithText("8.4").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("rating-skeleton").assertCountEquals(0)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun filmMitTmdbIdZeigtSkeletonSolangeDerFlowKeinenTrefferHat() {
        // metaFlow bleibt in diesem Test leer und cardMetaState meldet Transient: der Chip bleibt
        // deterministisch in "Loading" - keine Timer-Race auf einen bestimmten Frame noetig.
        stubCardState(TmdbCardState.Transient, recordRequest = true)

        showMovie("14306")

        composeTestRule.onNodeWithTag("rating-skeleton").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("8.4").assertCountEquals(0)
        assertEquals(listOf("movie" to "14306"), requests)
    }

    @Test
    fun filmMitNegativerIdZeigtWederChipNochSkeleton() {
        stubCardState(TmdbCardState.Negative)

        showMovie("99999")

        composeTestRule.onAllNodesWithText("8.4").assertCountEquals(0)
        composeTestRule.onAllNodesWithTag("rating-skeleton").assertCountEquals(0)
    }

    @Test
    fun filmMitTransienterIdZeigtSkeleton() {
        stubCardState(TmdbCardState.Transient)

        showMovie("99999")

        composeTestRule.onNodeWithTag("rating-skeleton").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("8.4").assertCountEquals(0)
    }

    @Test
    fun leererFlowZeigtChipErstNachMetaUpdate() {
        stubCardState(TmdbCardState.Transient, recordRequest = true)

        showMovie("14306")

        composeTestRule.onAllNodesWithText("8.4").assertCountEquals(0)

        metaFlow.value = mapOf("movie:14306" to meta(14306, 8.4))
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("8.4").assertIsDisplayed()
    }

    @Test
    fun serieMetaZeigtChipMitFormatierterBewertung() {
        metaFlow.value = mapOf("tv:1396" to meta(1396, 9.0))
        stubCardState(TmdbCardState.Transient, recordRequest = true)

        showSeries("1396")

        composeTestRule.onNodeWithText("9.0").assertIsDisplayed()
        assertEquals(listOf("tv" to "1396"), requests)
    }

    private fun meta(tmdbId: Int, rating: Double) = TmdbMeta(
        tmdbId = tmdbId,
        rating = rating,
        voteCount = 100,
        genres = emptyList()
    )

    private fun vod(tmdbId: String?) = VodStream(
        num = null,
        name = "Testfilm",
        streamType = null,
        streamId = 1,
        streamIcon = null,
        rating = null,
        rating5Based = null,
        added = null,
        categoryId = null,
        categoryName = null,
        containerExtension = null,
        customSid = null,
        directSource = null,
        releaseDate = null,
        duration = null,
        plot = null,
        cast = null,
        director = null,
        genre = null,
        backdropPath = null,
        youtubeTrailer = null,
        cover = null,
        tmdbId = tmdbId
    )

    private fun series(tmdbId: String?) = Series(
        num = null,
        name = "Testserie",
        seriesId = 10,
        cover = null,
        plot = null,
        cast = null,
        director = null,
        genre = null,
        releaseDate = null,
        rating = null,
        rating5Based = null,
        lastModified = null,
        categoryId = null,
        categoryName = null,
        youtubeTrailer = null,
        backdropPath = null,
        seasons = null,
        tmdbId = tmdbId
    )
}
