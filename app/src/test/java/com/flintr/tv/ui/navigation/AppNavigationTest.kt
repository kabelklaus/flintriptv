package com.flintr.tv.ui.navigation

import android.net.Uri
import com.flintr.tv.data.local.CachedData
import com.flintr.tv.data.local.Credentials
import com.flintr.tv.data.local.DataCacheService
import com.flintr.tv.data.model.Episode
import com.flintr.tv.data.model.Season
import com.flintr.tv.data.model.testSeries
import com.flintr.tv.data.model.testVodStream
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppNavigationTest {

    private val credentials = Credentials(serverUrl = "http://srv", username = "chris", password = "pw")

    @Test
    fun `handleDeepLink ohne Credentials liefert null`() = runBlocking {
        val uri = Uri.parse("flintrtv://play?type=vod&streamId=5")

        val route = handleDeepLink(uri, credentials = null)

        assertNull(route)
    }

    @Test
    fun `handleDeepLink ohne type liefert null`() = runBlocking {
        val uri = Uri.parse("flintrtv://play?streamId=5")

        val route = handleDeepLink(uri, credentials)

        assertNull(route)
    }

    @Test
    fun `handleDeepLink mit unbekanntem type liefert null`() = runBlocking {
        val uri = Uri.parse("flintrtv://play?type=music&streamId=5")

        val route = handleDeepLink(uri, credentials)

        assertNull(route)
    }

    @Test
    fun `handleDeepLink vod ohne streamId liefert null`() = runBlocking {
        val uri = Uri.parse("flintrtv://play?type=vod")

        val route = handleDeepLink(uri, credentials)

        assertNull(route)
    }

    @Test
    fun `handleDeepLink vod ohne Cache faellt auf mkv zurueck`() = runBlocking {
        val uri = Uri.parse("flintrtv://play?type=vod&streamId=5&title=Testfilm")

        val route = handleDeepLink(uri, credentials, cacheService = null)

        assertTrue(route!!.startsWith("player/"))
        assertTrue(route.contains("5.mkv"))
    }

    @Test
    fun `handleDeepLink vod nutzt Container-Extension aus dem Cache`() = runBlocking {
        val cacheService = mockk<DataCacheService>()
        coEvery { cacheService.loadCachedDataAsync() } returns CachedData(
            channels = emptyList(),
            movies = listOf(testVodStream(streamId = 5, containerExtension = "avi")),
            series = emptyList(),
            liveCategories = emptyList(),
            vodCategories = emptyList(),
            seriesCategories = emptyList()
        )
        val uri = Uri.parse("flintrtv://play?type=vod&streamId=5")

        val route = handleDeepLink(uri, credentials, cacheService)

        assertTrue(route!!.contains("5.avi"))
    }

    @Test
    fun `handleDeepLink series ohne episodeId liefert null`() = runBlocking {
        val uri = Uri.parse("flintrtv://play?type=series&seriesId=10")

        val route = handleDeepLink(uri, credentials)

        assertNull(route)
    }

    @Test
    fun `handleDeepLink series ohne Cache faellt auf mkv zurueck`() = runBlocking {
        val uri = Uri.parse("flintrtv://play?type=series&seriesId=10&episodeId=101")

        val route = handleDeepLink(uri, credentials, cacheService = null)

        assertTrue(route!!.contains("101.mkv"))
    }

    @Test
    fun `handleDeepLink series nutzt Container-Extension der passenden Episode aus dem Cache`() = runBlocking {
        val cacheService = mockk<DataCacheService>()
        val episode = Episode(
            id = 101,
            episodeNum = 1,
            title = "Folge 1",
            containerExtension = "ts",
            info = null,
            subtitles = null,
            added = null,
            season = 1,
            directSource = null
        )
        coEvery { cacheService.loadCachedDataAsync() } returns CachedData(
            channels = emptyList(),
            movies = emptyList(),
            series = listOf(
                testSeries(
                    seriesId = 10,
                    seasons = listOf(
                        Season(
                            airDate = null,
                            episodeCount = 1,
                            id = 1,
                            name = "Staffel 1",
                            overview = null,
                            seasonNumber = 1,
                            cover = null,
                            episodes = listOf(episode)
                        )
                    )
                )
            ),
            liveCategories = emptyList(),
            vodCategories = emptyList(),
            seriesCategories = emptyList()
        )
        val uri = Uri.parse("flintrtv://play?type=series&seriesId=10&episodeId=101")

        val route = handleDeepLink(uri, credentials, cacheService)

        assertTrue(route!!.contains("101.ts"))
    }
}
