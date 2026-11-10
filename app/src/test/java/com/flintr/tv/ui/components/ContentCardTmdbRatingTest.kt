package com.flintr.tv.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flintr.tv.data.local.WatchlistRepository
import com.flintr.tv.data.model.Series
import com.flintr.tv.data.model.VodStream
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
 * Grid-Chip kommt ausschließlich aus [TmdbMetaRepository.metaFlow]; Xtream-Ratings und
 * der Search-Pfad werden für Karten nicht mehr verwendet.
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
        watchlistRepository = mockk(relaxed = true)
        every { watchlistRepository.isInWatchlist(any()) } returns false
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

        showMovie("14306")

        composeTestRule.onNodeWithText("8.4").assertIsDisplayed()
        assertEquals(listOf("movie" to "14306"), requests)
    }

    @Test
    fun filmOhneTmdbIdZeigtKeinenChipUndFragtNichtsAn() {
        showMovie(null)

        composeTestRule.onNodeWithText("8.4").assertDoesNotExist()
        assertTrue(requests.isEmpty())
    }

    @Test
    fun leererFlowZeigtChipErstNachMetaUpdate() {
        showMovie("14306")

        composeTestRule.onNodeWithText("8.4").assertDoesNotExist()

        metaFlow.value = mapOf("movie:14306" to meta(14306, 8.4))
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("8.4").assertIsDisplayed()
    }

    @Test
    fun serieMetaZeigtChipMitFormatierterBewertung() {
        metaFlow.value = mapOf("tv:1396" to meta(1396, 9.0))

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
