package com.flintr.tv.ui.navigation

import com.flintr.tv.data.local.Credentials
import com.flintr.tv.data.model.Episode
import com.flintr.tv.data.model.testSeries
import com.flintr.tv.data.model.testVodStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Phase-4 route contract: content clicks are detail-first.
 *
 * Movie/series/season routes carry IDs only (finding 3: no URLs or credentials in nav routes),
 * while the explicit "watch"/"resume" actions still go to the player with the identifiers the
 * existing smart-resume logic needs (contentType + streamId/seriesId/episodeId).
 */
@RunWith(RobolectricTestRunner::class)
class DetailNavigationTest {

    private val credentials = Credentials(serverUrl = "http://srv", username = "chris", password = "pw")

    @Test
    fun `movie detail route carries only the vod id`() {
        assertEquals("movie_detail/42", Screen.MovieDetail.createRoute(42))
        assertEquals("movie_detail/7", vodDetailRoute(7))
    }

    @Test
    fun `series detail route carries only the series id`() {
        assertEquals("series_detail/99", Screen.SeriesDetail.createRoute(99))
        assertEquals("series_detail/99", seriesDetailRoute(99))
    }

    @Test
    fun `season route carries series id and season number`() {
        assertEquals("season/99/2", Screen.Season.createRoute(99, 2))
        assertEquals("season/99/2", seasonRoute(99, 2))
    }

    @Test
    fun `detail routes never contain server URLs or credentials`() {
        val routes = listOf(
            vodDetailRoute(42),
            seriesDetailRoute(99),
            seasonRoute(99, 2)
        )

        routes.forEach { route ->
            assertFalse(route.contains(credentials.serverUrl))
            assertFalse(route.contains(credentials.username))
            assertFalse(route.contains(credentials.password))
            assertFalse(route.contains("http"))
        }
    }

    @Test
    fun `vod click routes to movie detail instead of player`() {
        val route = vodDetailRoute(testVodStream(streamId = 123).streamId)

        assertEquals("movie_detail/123", route)
        assertFalse(route.startsWith("player/"))
    }

    @Test
    fun `series click routes to series detail instead of legacy episodes screen`() {
        val route = seriesDetailRoute(testSeries(seriesId = 321).seriesId)

        assertEquals("series_detail/321", route)
        assertFalse(route.startsWith("series_episodes/"))
    }

    @Test
    fun `watch action builds player route with movie identifiers for smart resume`() {
        val route = moviePlayerRoute(
            credentials = credentials,
            vodId = 123,
            title = "Testfilm",
            containerExtension = "avi"
        )

        assertTrue(route.startsWith("player/"))
        assertTrue(route.contains("/movie/123/"))
        assertTrue(route.contains("123.avi"))
    }

    @Test
    fun `episode action builds player route with series and episode identifiers`() {
        val episode = Episode(
            id = 501,
            episodeNum = 4,
            title = "Folge 4",
            containerExtension = "ts",
            info = null,
            subtitles = null,
            added = null,
            season = 2,
            directSource = null
        )

        val route = episodePlayerRoute(
            credentials = credentials,
            seriesId = 99,
            episode = episode
        )

        assertTrue(route.startsWith("player/"))
        assertTrue(route.contains("/series/501/99/501/"))
        assertTrue(route.contains("501.ts"))
    }

    @Test
    fun `series detail watch action opens player route for the resolved episode`() {
        val episode = Episode(
            id = 22,
            episodeNum = 2,
            title = "Folge 2",
            containerExtension = "mkv",
            info = null,
            subtitles = null,
            added = null,
            season = 2,
            directSource = null
        )

        val route = seriesDetailWatchRoute(credentials, seriesId = 99, episode = episode)

        assertTrue(route.startsWith("player/"))
        assertTrue(route.contains("/series/22/99/22/"))
        assertTrue(route.contains("22.mkv"))
    }

    @Test
    fun `series recommendation click navigates to the clicked series detail`() {
        val navigated = mutableListOf<String>()

        seriesRecommendationClickHandler { navigated += it }(321)

        assertEquals(listOf("series_detail/321"), navigated)
    }

    @Test
    fun `movie recommendation click navigates to the clicked movie detail`() {
        val navigated = mutableListOf<String>()

        movieRecommendationClickHandler { navigated += it }(123)

        assertEquals(listOf("movie_detail/123"), navigated)
    }

    /**
     * F10 regression: SeriesDetailRoute used to omit onSeriesClick, so recommendation cards fell
     * through to the screen's empty default lambda. Both detail routes must wire their handler.
     */
    @Test
    fun `detail routes wire recommendation click handlers instead of empty defaults`() {
        val source = File("src/main/java/com/flintr/tv/ui/navigation/AppNavigation.kt").readText()

        assertTrue(
            routeBody(source, "SeriesDetailRoute")
                .contains("onSeriesClick = seriesRecommendationClickHandler")
        )
        assertTrue(
            routeBody(source, "MovieDetailRoute")
                .contains("onMovieClick = movieRecommendationClickHandler")
        )
    }

    private fun routeBody(source: String, name: String): String {
        val start = source.indexOf("private fun $name(")
        assertTrue("$name not found", start >= 0)
        val end = source.indexOf("@Composable", start).takeIf { it >= 0 } ?: source.length
        return source.substring(start, end)
    }
}
