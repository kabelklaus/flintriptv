package com.flintr.tv.data.tmdb

import com.flintr.tv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.atomic.AtomicInteger

/**
 * `BuildConfig.TMDB_API_KEY` ist eine Compile-Time-Konstante aus
 * `local.properties`/ENV und laesst sich in Tests nicht umschreiben
 * (Robolectric/JDK21 verweigert das Schreiben finaler Felder). Tests, die
 * einen echten Netz-Call brauchen, laufen deshalb nur, wenn das lokale
 * Build-Setup tatsaechlich einen Key gesetzt hat; der Leer-Key-Test laeuft
 * nur, wenn keiner gesetzt ist. `assumeTrue` ueberspringt statt failt, damit
 * beide Umgebungen gruen bleiben.
 */
@RunWith(RobolectricTestRunner::class)
class TmdbCastClientTest {

    private val apiKey = BuildConfig.TMDB_API_KEY

    // -- Helpers -------------------------------------------------------

    private fun jsonResponse(request: Request, code: Int, json: String): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("ok")
            .body(json.toResponseBody("application/json".toMediaType()))
            .build()

    /** OkHttp-Client, der jeden Request faket; [AtomicInteger] zaehlt die Interceptor-Treffer. */
    private fun fakeClient(handler: (Request) -> Response): Pair<OkHttpClient, AtomicInteger> {
        val hits = AtomicInteger(0)
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                hits.incrementAndGet()
                handler(chain.request())
            }
            .build()
        return client to hits
    }

    private fun client(okHttpClient: OkHttpClient): TmdbCastClient =
        TmdbCastClient(okHttpClient, Dispatchers.Unconfined)

    /** Live gegen api.themoviedb.org verifizierte cast[]-Felder (Movie 550), inkl. profile_path: null. */
    private val castJson = """
        {
          "cast": [
            {
              "adult": false,
              "cast_id": 1,
              "character": "Frank Abagnale Jr.",
              "credit_id": "abc",
              "gender": 2,
              "id": 1234,
              "known_for_department": "Acting",
              "name": "Leonardo DiCaprio",
              "original_name": "Leonardo DiCaprio",
              "popularity": 10.0,
              "profile_path": "/abc.jpg",
              "order": 0
            },
            {
              "adult": false,
              "cast_id": 2,
              "character": "Carl Hanratty",
              "credit_id": "def",
              "gender": 2,
              "id": 5678,
              "known_for_department": "Acting",
              "name": "Tom Hanks",
              "original_name": "Tom Hanks",
              "popularity": 9.0,
              "profile_path": null,
              "order": 1
            }
          ],
          "crew": [],
          "id": 550
        }
    """.trimIndent()

    // -- URL-Bildung ----------------------------------------------------

    @Test
    fun `movieCredits baut Pfad und api_key fuer movie`() = runBlocking {
        assumeTrue(apiKey.isNotBlank())
        var capturedUrl: String? = null
        val (okHttpClient, hits) = fakeClient { request ->
            capturedUrl = request.url.toString()
            jsonResponse(request, 200, castJson)
        }

        client(okHttpClient).movieCredits("550")

        assertEquals(1, hits.get())
        assertTrue(capturedUrl!!.startsWith("https://api.themoviedb.org/3/movie/550/credits"))
        assertTrue(capturedUrl!!.contains("api_key=$apiKey"))
    }

    @Test
    fun `tvCredits baut Pfad fuer tv`() = runBlocking {
        assumeTrue(apiKey.isNotBlank())
        var capturedUrl: String? = null
        val (okHttpClient, _) = fakeClient { request ->
            capturedUrl = request.url.toString()
            jsonResponse(request, 200, castJson)
        }

        client(okHttpClient).tvCredits("1399")

        assertTrue(capturedUrl!!.startsWith("https://api.themoviedb.org/3/tv/1399/credits"))
    }

    // -- Parsing ----------------------------------------------------------

    @Test
    fun `parsing uebernimmt Felder inkl null profile_path, sortiert nach order`() = runBlocking {
        assumeTrue(apiKey.isNotBlank())
        val (okHttpClient, _) = fakeClient { request -> jsonResponse(request, 200, castJson) }

        val result = client(okHttpClient).movieCredits("550")

        assertEquals(2, result.size)
        assertEquals("Leonardo DiCaprio", result[0].name)
        assertEquals("Frank Abagnale Jr.", result[0].character)
        assertEquals("/abc.jpg", result[0].profilePath)
        assertEquals(0, result[0].order)
        assertEquals("Tom Hanks", result[1].name)
        assertNull(result[1].profilePath)
        assertEquals(1, result[1].order)
    }

    // -- Cache ------------------------------------------------------------

    @Test
    fun `zweiter Call mit gleichem Key trifft nur einmal das Netz`() = runBlocking {
        assumeTrue(apiKey.isNotBlank())
        val (okHttpClient, hits) = fakeClient { request -> jsonResponse(request, 200, castJson) }
        val tmdbCastClient = client(okHttpClient)

        tmdbCastClient.movieCredits("550")
        tmdbCastClient.movieCredits("550")

        assertEquals(1, hits.get())
    }

    @Test
    fun `404 wird negativ gecacht`() = runBlocking {
        assumeTrue(apiKey.isNotBlank())
        val (okHttpClient, hits) = fakeClient { request -> jsonResponse(request, 404, "") }
        val tmdbCastClient = client(okHttpClient)

        val first = tmdbCastClient.movieCredits("999999")
        val second = tmdbCastClient.movieCredits("999999")

        assertTrue(first.isEmpty())
        assertTrue(second.isEmpty())
        assertEquals(1, hits.get())
    }

    @Test
    fun `LRU-Cap wirft aelteste Keys hinaus`() = runBlocking {
        assumeTrue(apiKey.isNotBlank())
        val (okHttpClient, hits) = fakeClient { request -> jsonResponse(request, 200, castJson) }
        val tmdbCastClient = client(okHttpClient)

        for (id in 1..251) {
            tmdbCastClient.movieCredits(id.toString())
        }
        val hitsAfterFill = hits.get()

        tmdbCastClient.movieCredits("251")
        assertEquals(hitsAfterFill, hits.get())

        tmdbCastClient.movieCredits("1")
        assertEquals(hitsAfterFill + 1, hits.get())
    }

    // -- Fail-soft ---------------------------------------------------------

    @Test
    fun `500 ist fail-soft und wirft nicht`() = runBlocking {
        assumeTrue(apiKey.isNotBlank())
        val (okHttpClient, _) = fakeClient { request -> jsonResponse(request, 500, "server error") }

        val result = client(okHttpClient).movieCredits("550")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `500 wird nicht gecacht, zweiter Call geht erneut gegen das Netz`() = runBlocking {
        assumeTrue(apiKey.isNotBlank())
        val (okHttpClient, hits) = fakeClient { request -> jsonResponse(request, 500, "server error") }
        val tmdbCastClient = client(okHttpClient)

        val first = tmdbCastClient.movieCredits("550")
        val second = tmdbCastClient.movieCredits("550")

        assertTrue(first.isEmpty())
        assertTrue(second.isEmpty())
        assertEquals(2, hits.get())
    }

    @Test
    fun `leeres cast-Array aus 200er Antwort wird gecacht`() = runBlocking {
        assumeTrue(apiKey.isNotBlank())
        val (okHttpClient, hits) = fakeClient { request -> jsonResponse(request, 200, """{"cast": []}""") }
        val tmdbCastClient = client(okHttpClient)

        val first = tmdbCastClient.movieCredits("550")
        val second = tmdbCastClient.movieCredits("550")

        assertTrue(first.isEmpty())
        assertTrue(second.isEmpty())
        assertEquals(1, hits.get())
    }

    @Test
    fun `kaputter Body ist fail-soft und wirft nicht`() = runBlocking {
        assumeTrue(apiKey.isNotBlank())
        val (okHttpClient, _) = fakeClient { request -> jsonResponse(request, 200, "{not json") }

        val result = client(okHttpClient).movieCredits("550")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `leerer BuildConfig-Key macht keinen Netz-Call`() = runBlocking {
        assumeTrue(apiKey.isBlank())
        val (okHttpClient, hits) = fakeClient { request -> jsonResponse(request, 200, castJson) }

        val result = client(okHttpClient).movieCredits("550")

        assertTrue(result.isEmpty())
        assertEquals(0, hits.get())
    }

    @Test
    fun `leere tmdbId macht keinen Netz-Call`() = runBlocking {
        val (okHttpClient, hits) = fakeClient { request -> jsonResponse(request, 200, castJson) }

        val result = client(okHttpClient).movieCredits("")

        assertTrue(result.isEmpty())
        assertEquals(0, hits.get())
    }

    @Test
    fun `nicht-numerische tmdbId macht keinen Netz-Call und wirft nicht`() = runBlocking {
        val (okHttpClient, hits) = fakeClient { request -> jsonResponse(request, 200, castJson) }
        val tmdbCastClient = client(okHttpClient)

        for (tmdbId in listOf("550abc", "12/34", "#x", "a".repeat(300))) {
            val result = tmdbCastClient.movieCredits(tmdbId)
            assertTrue(result.isEmpty())
        }

        assertEquals(0, hits.get())
    }
}
