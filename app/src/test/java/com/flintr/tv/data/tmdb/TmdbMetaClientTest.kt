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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.atomic.AtomicInteger

/**
 * `BuildConfig.TMDB_API_KEY` ist eine Compile-Time-Konstante und laesst sich in
 * Tests nicht umschreiben. Tests mit echtem Netz-Call laufen deshalb nur, wenn
 * das lokale Build-Setup einen Key gesetzt hat; `assumeTrue` ueberspringt statt
 * zu failen, damit beide Umgebungen gruen bleiben.
 */
@RunWith(RobolectricTestRunner::class)
class TmdbMetaClientTest {

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

    private fun client(okHttpClient: OkHttpClient): TmdbMetaClient =
        TmdbMetaClient(okHttpClient, Dispatchers.Unconfined)

    private fun <T> TmdbResult<T>.dataOrNull(): T? = (this as? TmdbResult.Data<T>)?.value

    /** Live gegen api.themoviedb.org verifizierte Details (Godfather, 238). */
    private val movieJson = """
        {
          "id": 238,
          "vote_average": 8.7,
          "vote_count": 23952,
          "genres": [
            {"id": 18, "name": "Drama"},
            {"id": 80, "name": "Crime"}
          ],
          "release_date": "1972-03-14",
          "runtime": 175
        }
    """.trimIndent()

    /** Live gegen api.themoviedb.org verifizierte Details (Breaking Bad, 1396) - ohne runtime. */
    private val tvJson = """
        {
          "id": 1396,
          "vote_average": 8.959,
          "vote_count": 19086,
          "genres": [
            {"id": 18, "name": "Drama"}
          ],
          "first_air_date": "2008-01-20"
        }
    """.trimIndent()

    /** Live verifizierte Suchantwort (Marley & Me, 14306). */
    private val searchMovieJson = """
        {
          "page": 1,
          "results": [
            {"id": 14306, "title": "Marley & Me", "vote_average": 7.1, "vote_count": 4920}
          ],
          "total_results": 1
        }
    """.trimIndent()

    /** Live verifizierte TV-Suche (Supernatural, 1622). */
    private val searchTvJson = """
        {
          "page": 1,
          "results": [
            {"id": 1622, "name": "Supernatural"},
            {"id": 13836, "name": "Supernatural"}
          ],
          "total_results": 2
        }
    """.trimIndent()

    // -- Details --------------------------------------------------------

    @Test
    fun `movieMeta parst Fixture und baut Pfad mit api_key`() = runBlocking {
        assumeTrue(apiKey.isNotBlank())
        var capturedUrl: String? = null
        val (okHttpClient, hits) = fakeClient { request ->
            capturedUrl = request.url.toString()
            jsonResponse(request, 200, movieJson)
        }

        val meta = client(okHttpClient).movieMeta("238").dataOrNull()

        assertEquals(1, hits.get())
        assertTrue(capturedUrl!!.contains("movie/238"))
        assertTrue(capturedUrl!!.contains("api_key=$apiKey"))
        assertEquals(238, meta?.tmdbId)
        assertEquals(8.7, meta?.rating)
        assertEquals(23952, meta?.voteCount)
        assertEquals(listOf(TmdbGenre(18, "Drama"), TmdbGenre(80, "Crime")), meta?.genres)
        assertEquals("1972-03-14", meta?.releaseDate)
        assertEquals(175, meta?.runtime)
    }

    @Test
    fun `tvMeta setzt runtime null und uebernimmt first_air_date`() = runBlocking {
        assumeTrue(apiKey.isNotBlank())
        val (okHttpClient, _) = fakeClient { request -> jsonResponse(request, 200, tvJson) }

        val meta = client(okHttpClient).tvMeta("1396").dataOrNull()

        assertEquals(1396, meta?.tmdbId)
        assertEquals(8.959, meta?.rating)
        assertEquals(19086, meta?.voteCount)
        assertEquals(listOf(TmdbGenre(18, "Drama")), meta?.genres)
        assertEquals("2008-01-20", meta?.releaseDate)
        assertNull(meta?.runtime)
    }

    @Test
    fun `404 auf Details liefert Negative`() = runBlocking {
        assumeTrue(apiKey.isNotBlank())
        val (okHttpClient, hits) = fakeClient { request -> jsonResponse(request, 404, "") }

        val result = client(okHttpClient).movieMeta("999999")

        assertEquals(TmdbResult.Negative, result)
        assertEquals(1, hits.get())
    }

    @Test
    fun `HTTP 500 liefert Transient`() = runBlocking {
        assumeTrue(apiKey.isNotBlank())
        val (okHttpClient, _) = fakeClient { request -> jsonResponse(request, 500, "server error") }

        val result = client(okHttpClient).movieMeta("238")

        assertEquals(TmdbResult.Transient, result)
    }

    @Test
    fun `unparsbarer Body liefert Transient`() = runBlocking {
        assumeTrue(apiKey.isNotBlank())
        val (okHttpClient, _) = fakeClient { request -> jsonResponse(request, 200, "{not json") }

        val result = client(okHttpClient).movieMeta("238")

        assertEquals(TmdbResult.Transient, result)
    }

    // -- Suche ----------------------------------------------------------

    @Test
    fun `searchMovieId liefert erstes Result und encodiert Ampersand`() = runBlocking {
        assumeTrue(apiKey.isNotBlank())
        var capturedUrl: String? = null
        val (okHttpClient, _) = fakeClient { request ->
            capturedUrl = request.url.toString()
            jsonResponse(request, 200, searchMovieJson)
        }

        val id = client(okHttpClient).searchMovieId("Marley & Me", null).dataOrNull()

        assertEquals(14306, id)
        assertTrue(capturedUrl!!.contains("search/movie"))
        assertTrue(capturedUrl!!.contains("Marley%20%26%20Me"))
    }

    @Test
    fun `searchTvId liefert erstes Result`() = runBlocking {
        assumeTrue(apiKey.isNotBlank())
        val (okHttpClient, _) = fakeClient { request -> jsonResponse(request, 200, searchTvJson) }

        val id = client(okHttpClient).searchTvId("Supernatural", null).dataOrNull()

        assertEquals(1622, id)
    }

    @Test
    fun `leere Suchergebnisse liefern Negative`() = runBlocking {
        assumeTrue(apiKey.isNotBlank())
        val (okHttpClient, _) = fakeClient { request ->
            jsonResponse(request, 200, """{"page": 1, "results": [], "total_results": 0}""")
        }

        val result = client(okHttpClient).searchMovieId("Nichts", null)

        assertEquals(TmdbResult.Negative, result)
    }

    @Test
    fun `year wird nur bei non-null angehaengt`() = runBlocking {
        assumeTrue(apiKey.isNotBlank())
        var capturedUrl: String? = null
        val (okHttpClient, _) = fakeClient { request ->
            capturedUrl = request.url.toString()
            jsonResponse(request, 200, searchMovieJson)
        }
        val tmdbMetaClient = client(okHttpClient)

        tmdbMetaClient.searchMovieId("X", 2008)
        assertTrue(capturedUrl!!.contains("year=2008"))

        capturedUrl = null
        tmdbMetaClient.searchMovieId("X", null)
        assertFalse(capturedUrl!!.contains("year="))
    }

    // -- Guards ---------------------------------------------------------

    @Test
    fun `nicht-numerische tmdbId liefert Negative ohne Netz-Call`() = runBlocking {
        val (okHttpClient, hits) = fakeClient { request -> jsonResponse(request, 200, movieJson) }

        val result = client(okHttpClient).movieMeta("abc")

        assertEquals(TmdbResult.Negative, result)
        assertEquals(0, hits.get())
    }

    @Test
    fun `blanker Key liefert Transient ohne Netz-Call`() = runBlocking {
        val (okHttpClient, hits) = fakeClient { request -> jsonResponse(request, 200, movieJson) }

        val result = client(okHttpClient).movieMeta("  ")

        assertEquals(TmdbResult.Transient, result)
        assertEquals(0, hits.get())
    }
}
