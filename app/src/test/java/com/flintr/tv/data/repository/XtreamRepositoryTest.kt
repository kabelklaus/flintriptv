package com.flintr.tv.data.repository

import com.flintr.tv.data.local.CachedData
import com.flintr.tv.data.local.Credentials
import com.flintr.tv.data.local.CredentialsRepository
import com.flintr.tv.data.local.DataCacheService
import com.flintr.tv.data.model.SeriesInfoResponseAdapterFactory
import com.flintr.tv.data.model.testLiveStream
import com.flintr.tv.data.model.testSeries
import com.flintr.tv.data.model.testVodStream
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class XtreamRepositoryTest {

    private val server = MockWebServer()
    private lateinit var credentialsRepository: CredentialsRepository
    private lateinit var cacheService: DataCacheService
    private lateinit var repository: XtreamRepository
    private lateinit var credentialsFlow: MutableStateFlow<Credentials?>

    @Before
    fun setUp() {
        server.start()
        credentialsFlow = MutableStateFlow(
            Credentials(serverUrl = server.url("/").toString().removeSuffix("/"), username = "chris", password = "pw")
        )
        credentialsRepository = mockk()
        every { credentialsRepository.credentialsFlow } returns credentialsFlow
        cacheService = mockk(relaxed = true)

        repository = XtreamRepository(
            credentialsRepository = credentialsRepository,
            okHttpClient = OkHttpClient(),
            moshi = Moshi.Builder()
                .add(SeriesInfoResponseAdapterFactory())
                .addLast(KotlinJsonAdapterFactory())
                .build(),
            cacheService = cacheService
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun enqueueJson(body: String, code: Int = 200) {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))
    }

    @Test
    fun `getServerInfo liefert geparste Antwort`() = runBlocking {
        enqueueJson("""{"server_info":{"url":"srv"},"user_info":{"username":"chris","auth":1}}""")

        val result = repository.getServerInfo("chris", "pw")

        assertTrue(result.isSuccess)
        assertEquals("srv", result.getOrNull()?.serverInfo?.url)
    }

    @Test
    fun `getServerInfo meldet HTTP-Fehler`() = runBlocking {
        enqueueJson("kaputt", code = 500)

        val result = repository.getServerInfo("chris", "pw")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("500"))
    }

    @Test
    fun `ohne Credentials wird API-Fehler gemeldet`() = runBlocking {
        credentialsFlow.value = null

        val result = repository.getServerInfo("chris", "pw")

        assertTrue(result.isFailure)
        assertEquals(XtreamRepository.ERROR_API_SERVICE_NOT_AVAILABLE, result.exceptionOrNull()?.message)
    }

    @Test
    fun `getLiveCategories cached im Speicher`() = runBlocking {
        enqueueJson("""[{"category_id":"1","category_name":"Live DE"}]""")

        val first = repository.getLiveCategories("chris", "pw")
        val second = repository.getLiveCategories("chris", "pw")

        assertEquals(listOf("Live DE"), first.getOrNull()?.map { it.categoryName })
        assertEquals(first.getOrNull(), second.getOrNull())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `clearCache erzwingt neuen API-Aufruf`() = runBlocking {
        enqueueJson("""[{"category_id":"1","category_name":"Live DE"}]""")
        repository.getLiveCategories("chris", "pw")

        repository.clearCache()
        enqueueJson("""[{"category_id":"2","category_name":"Live EN"}]""")
        val result = repository.getLiveCategories("chris", "pw")

        assertEquals(listOf("Live EN"), result.getOrNull()?.map { it.categoryName })
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `loadLiveStreamsFromApi laedt Sender`() = runBlocking {
        enqueueJson("""[{"name":"ARD HD","stream_id":100}]""")

        val result = repository.loadLiveStreamsFromApi("chris", "pw")

        assertEquals(listOf(100), result.getOrNull()?.map { it.streamId })
    }

    @Test
    fun `loadVodStreamsFromApi meldet HTTP-Fehler`() = runBlocking {
        enqueueJson("nope", code = 403)

        val result = repository.loadVodStreamsFromApi("chris", "pw")

        assertTrue(result.exceptionOrNull()!!.message!!.contains("403"))
    }

    @Test
    fun `loadSeriesFromApi laedt Serienliste`() = runBlocking {
        enqueueJson("""[{"name":"Dark","series_id":10}]""")

        val result = repository.loadSeriesFromApi("chris", "pw")

        assertEquals(listOf(10), result.getOrNull()?.map { it.seriesId })
    }

    @Test
    fun `getLiveStreams nutzt persistenten Cache mit Kategoriefilter`() = runBlocking {
        every { cacheService.shouldUseCacheForBrowsing() } returns true
        coEvery { cacheService.loadCachedDataAsync() } returns CachedData(
            channels = listOf(
                testLiveStream(name = "ARD", streamId = 1, categoryId = "7"),
                testLiveStream(name = "ZDF", streamId = 2, categoryId = "8")
            ),
            movies = listOf(testVodStream(streamId = 5, categoryId = "9")),
            series = listOf(testSeries(seriesId = 10, categoryId = "12")),
            liveCategories = emptyList(),
            vodCategories = emptyList(),
            seriesCategories = emptyList()
        )

        assertEquals(listOf("ARD"), repository.getLiveStreams("chris", "pw", "7").getOrNull()?.map { it.name })
        assertEquals(2, repository.getLiveStreams("chris", "pw", null).getOrNull()?.size)
        assertEquals(listOf(5), repository.getVodStreams("chris", "pw", "9").getOrNull()?.map { it.streamId })
        assertEquals(listOf(10), repository.getSeries("chris", "pw", "12").getOrNull()?.map { it.seriesId })
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `getLiveStreams ohne Cache meldet Fehler`() = runBlocking {
        every { cacheService.shouldUseCacheForBrowsing() } returns false

        val result = repository.getLiveStreams("chris", "pw")

        assertEquals(XtreamRepository.ERROR_NO_CACHE_AVAILABLE, result.exceptionOrNull()?.message)
    }

    @Test
    fun `getSeriesInfo parst Episoden zur Serie`() = runBlocking {
        enqueueJson(
            """{"seasons":[],"info":{"name":"Dark","category_id":"12"},
                "episodes":{"1":[{"id":"101","episode_num":1,"title":"Folge 1","season":1}]}}"""
        )

        val result = repository.getSeriesInfo("chris", "pw", 10)

        assertTrue(result.isSuccess)
        val series = result.getOrNull()!!
        assertEquals("Dark", series.name)
        assertEquals(listOf(101), series.seasons?.first()?.episodes?.map { it.id })
    }

    @Test
    fun `getSeriesInfo wiederholt bei Serverfehler und leerer Antwort`() = runBlocking {
        enqueueJson("kaputt", code = 502)
        enqueueJson("""{"seasons":[],"info":{"name":"Dark"},"episodes":{}}""")
        enqueueJson(
            """{"seasons":[],"info":{"name":"Dark"},
                "episodes":{"1":[{"id":"101","episode_num":1,"title":"Folge 1","season":1}]}}"""
        )

        val result = repository.getSeriesInfo("chris", "pw", 10)

        assertTrue(result.isSuccess)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `getSeriesInfo bricht bei Client-Fehler sofort ab`() = runBlocking {
        enqueueJson("nope", code = 404)

        val result = repository.getSeriesInfo("chris", "pw", 10)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("404"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `getVodCategories und getSeriesCategories cachen im Speicher`() = runBlocking {
        enqueueJson("""[{"category_id":"2","category_name":"Filme"}]""")
        enqueueJson("""[{"category_id":"3","category_name":"Serien"}]""")

        assertEquals(listOf("Filme"), repository.getVodCategories("chris", "pw").getOrNull()?.map { it.categoryName })
        assertEquals(listOf("Serien"), repository.getSeriesCategories("chris", "pw").getOrNull()?.map { it.categoryName })
        // Zweite Aufrufe kommen aus dem Speicher-Cache
        repository.getVodCategories("chris", "pw")
        repository.getSeriesCategories("chris", "pw")
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `Kategorie-Endpunkte melden HTTP-Fehler`() = runBlocking {
        enqueueJson("nope", code = 500)
        enqueueJson("nope", code = 500)

        assertTrue(repository.getVodCategories("chris", "pw").exceptionOrNull()!!.message!!.contains("500"))
        assertTrue(repository.getSeriesCategories("chris", "pw").exceptionOrNull()!!.message!!.contains("500"))
    }

    @Test
    fun `getEpgList flacht Programme mehrerer Sender ab`() = runBlocking {
        enqueueJson(
            """{"101":[{"id":"1","title":"Eins","start":"a","end":"b"}],
                "102":[{"id":"2","title":"Zwei","start":"a","end":"b"}]}"""
        )

        val result = repository.getEpgList("chris", "pw", listOf(101, 102))

        assertEquals(setOf("Eins", "Zwei"), result.getOrNull()?.map { it.title }?.toSet())
    }

    @Test
    fun `getEpg meldet HTTP-Fehler`() = runBlocking {
        enqueueJson("nope", code = 500)

        assertTrue(repository.getEpg("chris", "pw", 101).exceptionOrNull()!!.message!!.contains("500"))
    }

    @Test
    fun `getEpg flacht die Programm-Map ab`() = runBlocking {
        enqueueJson(
            """{"epg_listings":[],"101":[{"id":"1","title":"Sendung","start":"2026-07-16 10:00:00","end":"2026-07-16 11:00:00"}]}"""
        )

        val result = repository.getEpg("chris", "pw", 101)

        assertTrue(result.isSuccess)
        assertEquals(listOf("Sendung"), result.getOrNull()?.map { it.title })
    }
}
