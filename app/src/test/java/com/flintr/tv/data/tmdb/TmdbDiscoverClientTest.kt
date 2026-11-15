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
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.atomic.AtomicInteger

/**
 * `BuildConfig.TMDB_API_KEY` ist eine Compile-Time-Konstante; Tests mit echtem
 * Netz-Pfad laufen nur mit lokal gesetztem Key und werden sonst uebersprungen.
 */
@RunWith(RobolectricTestRunner::class)
class TmdbDiscoverClientTest {

    private val apiKey = BuildConfig.TMDB_API_KEY

    private fun jsonResponse(request: Request, code: Int, json: String): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("ok")
            .body(json.toResponseBody("application/json".toMediaType()))
            .build()

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

    private fun client(okHttpClient: OkHttpClient): TmdbDiscoverClient =
        TmdbDiscoverClient(okHttpClient, Dispatchers.Unconfined)

    private fun <T> TmdbResult<T>.dataOrNull(): T? = (this as? TmdbResult.Data<T>)?.value

    /** Live verifizierte Discover-Form (Movie, Genre 28). */
    private val discoverMovieJson = """
        {
          "page": 1,
          "results": [
            {"id": 1560520, "title": "..."},
            {"id": 980431, "title": "..."}
          ],
          "total_results": 3219
        }
    """.trimIndent()

    /** Recommendations-Form mit den fuer den Katalog-Match benoetigten Titel-Feldern. */
    private val movieRecommendationTitlesJson = """
        {
          "page": 1,
          "results": [
            {"id": 240, "title": "The Godfather Part II", "release_date": "1974-12-20"},
            {"id": 242}
          ]
        }
    """.trimIndent()

    /** Live verifizierte Recommendations-Form (Movie 238). */
    private val movieRecommendationsJson = """
        {
          "page": 1,
          "results": [
            {"id": 240},
            {"id": 242}
          ]
        }
    """.trimIndent()

    @Test
    fun `discoverMovies baut Query korrekt und parst IDs`() = runBlocking {
        assumeTrue(apiKey.isNotBlank())
        var captured: Request? = null
        val (okHttpClient, hits) = fakeClient { request ->
            captured = request
            jsonResponse(request, 200, discoverMovieJson)
        }

        val ids = client(okHttpClient).discoverMovies(listOf(28)).dataOrNull()

        assertEquals(1, hits.get())
        assertEquals(listOf(1560520, 980431), ids)
        val url = captured!!.url
        assertTrue(url.toString().contains("discover/movie"))
        assertEquals("28", url.queryParameter("with_genres"))
        assertEquals("vote_average.desc", url.queryParameter("sort_by"))
        assertEquals("200", url.queryParameter("vote_count.gte"))
        assertEquals("1", url.queryParameter("page"))
    }

    @Test
    fun `zwei Genres werden komma-joined in der Query`() = runBlocking {
        assumeTrue(apiKey.isNotBlank())
        var captured: Request? = null
        val (okHttpClient, _) = fakeClient { request ->
            captured = request
            jsonResponse(request, 200, discoverMovieJson)
        }

        client(okHttpClient).discoverMovies(listOf(18, 28))

        assertEquals("18,28", captured!!.url.queryParameter("with_genres"))
    }

    @Test
    fun `discoverTv parst Fixture-IDs`() = runBlocking {
        assumeTrue(apiKey.isNotBlank())
        var captured: Request? = null
        val (okHttpClient, _) = fakeClient { request ->
            captured = request
            jsonResponse(request, 200, discoverMovieJson)
        }

        val ids = client(okHttpClient).discoverTv(listOf(18), page = 2).dataOrNull()

        assertEquals(listOf(1560520, 980431), ids)
        assertTrue(captured!!.url.toString().contains("discover/tv"))
        assertEquals("2", captured!!.url.queryParameter("page"))
    }

    @Test
    fun `leere results liefern Negative`() = runBlocking {
        assumeTrue(apiKey.isNotBlank())
        val (okHttpClient, _) = fakeClient { request ->
            jsonResponse(request, 200, """{"page": 1, "results": [], "total_results": 0}""")
        }

        assertEquals(TmdbResult.Negative, client(okHttpClient).discoverMovies(listOf(28)))
    }

    @Test
    fun `HTTP 500 liefert Transient`() = runBlocking {
        assumeTrue(apiKey.isNotBlank())
        val (okHttpClient, _) = fakeClient { request -> jsonResponse(request, 500, "server error") }

        assertEquals(TmdbResult.Transient, client(okHttpClient).discoverMovies(listOf(28)))
    }

    @Test
    fun `blanker Key liefert Transient ohne Netz-Hit`() = runBlocking {
        assumeTrue(apiKey.isBlank())
        val (okHttpClient, hits) = fakeClient { request -> jsonResponse(request, 200, discoverMovieJson) }

        assertEquals(TmdbResult.Transient, client(okHttpClient).discoverMovies(listOf(28)))
        assertEquals(0, hits.get())
    }

    @Test
    fun `leere genreIds liefern Negative ohne Netz-Hit`() = runBlocking {
        val (okHttpClient, hits) = fakeClient { request -> jsonResponse(request, 200, discoverMovieJson) }

        assertEquals(TmdbResult.Negative, client(okHttpClient).discoverMovies(emptyList()))
        assertEquals(0, hits.get())
    }

    @Test
    fun `movieRecommendations parst IDs und baut Pfad`() = runBlocking {
        assumeTrue(apiKey.isNotBlank())
        var captured: Request? = null
        val (okHttpClient, _) = fakeClient { request ->
            captured = request
            jsonResponse(request, 200, movieRecommendationsJson)
        }

        val ids = client(okHttpClient).movieRecommendations("238").dataOrNull()

        assertEquals(listOf(240, 242), ids)
        assertTrue(captured!!.url.toString().contains("movie/238/recommendations"))
        assertEquals("1", captured!!.url.queryParameter("page"))
    }

    @Test
    fun `movieRecommendationTitles parst title und release_date tolerant`() = runBlocking {
        assumeTrue(apiKey.isNotBlank())
        var captured: Request? = null
        val (okHttpClient, _) = fakeClient { request ->
            captured = request
            jsonResponse(request, 200, movieRecommendationTitlesJson)
        }

        val items = client(okHttpClient).movieRecommendationTitles("238").dataOrNull()

        assertEquals(
            listOf(
                TmdbRecItem(id = 240, title = "The Godfather Part II", releaseDate = "1974-12-20"),
                TmdbRecItem(id = 242, title = null, releaseDate = null)
            ),
            items
        )
        assertTrue(captured!!.url.toString().contains("movie/238/recommendations"))
        assertEquals("1", captured!!.url.queryParameter("page"))
    }

    @Test
    fun `non-numeric Anchor liefert Negative ohne Netz-Hit`() = runBlocking {
        val (okHttpClient, hits) = fakeClient { request ->
            jsonResponse(request, 200, movieRecommendationsJson)
        }

        assertEquals(TmdbResult.Negative, client(okHttpClient).movieRecommendations("abc"))
        assertEquals(0, hits.get())
    }
}
