package com.flintr.tv.data.repository

import com.flintr.tv.data.local.Credentials
import com.flintr.tv.data.local.CredentialsRepository
import com.flintr.tv.data.local.DataCacheService
import com.flintr.tv.data.model.VodInfoResponseAdapterFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class XtreamRepositoryVodInfoTest {

    private val server = MockWebServer()
    private lateinit var repository: XtreamRepository
    private lateinit var credentialsFlow: MutableStateFlow<Credentials?>

    @Before
    fun setUp() {
        server.start()
        credentialsFlow = MutableStateFlow(
            Credentials(
                serverUrl = server.url("/").toString().removeSuffix("/"),
                username = "chris",
                password = "pw"
            )
        )
        val credentialsRepository = mockk<CredentialsRepository>()
        every { credentialsRepository.credentialsFlow } returns credentialsFlow

        repository = XtreamRepository(
            credentialsRepository = credentialsRepository,
            okHttpClient = OkHttpClient(),
            moshi = Moshi.Builder()
                .add(VodInfoResponseAdapterFactory())
                .addLast(KotlinJsonAdapterFactory())
                .build(),
            cacheService = mockk(relaxed = true)
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
    fun `loadVodInfo liefert Detail-Metadaten`() = runBlocking {
        enqueueJson(
            """
            {
              "info": {
                "name": "Inception",
                "plot": "Ein Dieb stiehlt Traeume.",
                "cast": "Leonardo DiCaprio, Elliot Page",
                "director": "Christopher Nolan",
                "genre": "Action, Science Fiction",
                "duration": "02:28:00",
                "duration_secs": "8880",
                "runtime": "148 min",
                "releasedate": "2010-07-16",
                "rating": "8.8",
                "youtube_trailer": "YoHD9XEInc0",
                "backdrop_path": ["https://img/backdrop.jpg"],
                "movie_image": "https://img/poster.jpg",
                "cover_big": "https://img/cover_big.jpg",
                "tmdb_id": "27205"
              }
            }
            """.trimIndent()
        )

        val result = repository.loadVodInfo("chris", "pw", vodId = 42)

        assertTrue(result.isSuccess)
        val info = result.getOrNull()
        assertEquals("Inception", info?.name)
        assertEquals("Christopher Nolan", info?.director)
        assertEquals("148 min", info?.runtime)
        assertEquals(listOf("https://img/backdrop.jpg"), info?.backdropPath)
        assertEquals("27205", info?.tmdbId)

        val request = server.takeRequest()
        assertEquals("get_vod_info", request.requestUrl?.queryParameter("action"))
        assertEquals("42", request.requestUrl?.queryParameter("vod_id"))
    }

    @Test
    fun `loadVodInfo meldet HTTP-Fehler als failure`() = runBlocking {
        enqueueJson("kaputt", code = 500)

        val result = repository.loadVodInfo("chris", "pw", vodId = 42)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("500"))
    }

    @Test
    fun `loadVodInfo liefert null bei leerem info-Array`() = runBlocking {
        enqueueJson("""{"info": []}""")

        val result = repository.loadVodInfo("chris", "pw", vodId = 42)

        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
    }

    @Test
    fun `loadVodInfo liefert null bei info null`() = runBlocking {
        enqueueJson("""{"info": null}""")

        val result = repository.loadVodInfo("chris", "pw", vodId = 42)

        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
    }
}
