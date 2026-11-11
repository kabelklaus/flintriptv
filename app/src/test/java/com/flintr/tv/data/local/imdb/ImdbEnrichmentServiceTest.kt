package com.flintr.tv.data.local.imdb

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.zip.GZIPOutputStream

@RunWith(RobolectricTestRunner::class)
class ImdbEnrichmentServiceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val moshi: Moshi = Moshi.Builder().build()

    private val dbFile get() = File(context.filesDir, "imdb-enrichment.db")
    private val metaFile get() = File(context.filesDir, "imdb_enrichment.meta.json")
    private val legacyGzFile get() = File(context.filesDir, "imdb_enrichment.json.gz")
    private val legacyAssetNamedGzFile get() = File(context.filesDir, "imdb-enrichment.json.gz")

    @After
    fun tearDown() {
        context.filesDir.listFiles()
            ?.filter { it.name.startsWith("imdb") }
            ?.forEach { it.delete() }
    }

    // -- Helpers -------------------------------------------------------

    private data class Row(val key: String, val rating: Double, val numVotes: Int, val genres: String)

    /** Baut eine Enrichment-DB im selben Schema, das `build_enrichment.py` erzeugt. */
    private fun buildDb(target: File, vararg rows: Row) {
        target.delete()
        SQLiteDatabase.openOrCreateDatabase(target, null).use { db ->
            db.execSQL(
                "CREATE TABLE enrich(key TEXT PRIMARY KEY, rating REAL, numvotes INTEGER, genres TEXT) WITHOUT ROWID"
            )
            rows.forEach { row ->
                db.execSQL(
                    "INSERT INTO enrich(key, rating, numvotes, genres) VALUES (?, ?, ?, ?)",
                    arrayOf<Any>(row.key, row.rating, row.numVotes, row.genres)
                )
            }
        }
    }

    private fun gzippedDbOf(vararg rows: Row): ByteArray {
        val scratch = File(context.cacheDir, "fixture-${System.nanoTime()}.db")
        try {
            buildDb(scratch, *rows)
            return gzipOf(scratch.readBytes())
        } finally {
            scratch.delete()
        }
    }

    private fun gzipOf(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(bytes) }
        return out.toByteArray()
    }

    private fun seedCache(vararg rows: Row, builtAt: Instant = Instant.now()) {
        buildDb(dbFile, *rows)
        metaFile.writeText("""{"built_at":"$builtAt"}""")
    }

    private fun fakeClient(handler: (Request) -> Response): OkHttpClient =
        OkHttpClient.Builder().addInterceptor { chain -> handler(chain.request()) }.build()

    private fun jsonResponse(request: Request, code: Int, json: String): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("ok")
            .body(json.toResponseBody("application/json".toMediaType()))
            .build()

    private fun bytesResponse(request: Request, code: Int, bytes: ByteArray): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("ok")
            .body(bytes.toResponseBody("application/octet-stream".toMediaType()))
            .build()

    private fun releaseJson(gzUrl: String, metaUrl: String): String = """
        {
          "tag_name": "imdb-data",
          "name": "imdb-data",
          "prerelease": false,
          "published_at": "2026-01-01T00:00:00Z",
          "assets": [
            {"name": "imdb-enrichment.db.gz", "url": "$gzUrl", "browser_download_url": "$gzUrl", "content_type": "application/gzip", "size": 10},
            {"name": "imdb-enrichment.meta.json", "url": "$metaUrl", "browser_download_url": "$metaUrl", "content_type": "application/json", "size": 10}
          ]
        }
    """.trimIndent()

    /** Service mit Netzwerk-Clients, die immer 404 liefern -- fuer Tests, die rein vom Disk-Cache lesen. */
    private fun newService(): ImdbEnrichmentService = ImdbEnrichmentService(
        context,
        moshi,
        fakeClient { req -> jsonResponse(req, 404, "not found") },
        fakeClient { req -> bytesResponse(req, 404, ByteArray(0)) },
        Dispatchers.Unconfined
    )

    /** Service, dessen Release `imdb-data` die uebergebene gz-DB und eine frische meta.json liefert. */
    private fun downloadingService(dbGzBytes: ByteArray): ImdbEnrichmentService {
        val gitHubClient = fakeClient { req ->
            jsonResponse(req, 200, releaseJson(gzUrl = "https://fake/gz", metaUrl = "https://fake/meta"))
        }
        val downloadClient = fakeClient { req ->
            when (req.url.toString()) {
                "https://fake/gz" -> bytesResponse(req, 200, dbGzBytes)
                "https://fake/meta" -> bytesResponse(
                    req,
                    200,
                    """{"built_at":"${Instant.now()}"}""".toByteArray(Charsets.UTF_8)
                )
                else -> bytesResponse(req, 404, ByteArray(0))
            }
        }
        return ImdbEnrichmentService(context, moshi, gitHubClient, downloadClient, Dispatchers.Unconfined)
    }

    // -- 1. Exakter Treffer ---------------------------------------------

    @Test
    fun `lookup findet exakten Treffer`() = runBlocking {
        seedCache(Row("marley me|2008", 8.1, 500000, "Comedy,Drama"))
        val service = newService()
        service.ensureLoaded()

        val result = service.lookup("EN - Marley & Me (2008)")

        assertEquals(8.1, result?.rating ?: 0.0, 0.0001)
        assertEquals(500000, result?.numVotes)
        assertEquals(listOf("Comedy", "Drama"), result?.genres)
    }

    // -- 2. Year-shift in beide Richtungen --------------------------------

    @Test
    fun `lookup findet Jahres-Verschiebung in beide Richtungen`() = runBlocking {
        seedCache(Row("x|2005", 7.0, 100, "Action"))
        val service = newService()
        service.ensureLoaded()

        assertEquals(7.0, service.lookup("X (2006)")?.rating ?: 0.0, 0.0001)
        assertEquals(7.0, service.lookup("X (2004)")?.rating ?: 0.0, 0.0001)
    }

    // -- 3. Bare-Fallback -------------------------------------------------

    @Test
    fun `lookup faellt auf bare key zurueck wenn nur dieser existiert`() = runBlocking {
        seedCache(Row("x|", 6.5, 80, "Drama"))
        val service = newService()
        service.ensureLoaded()

        assertEquals(6.5, service.lookup("X (1995)")?.rating ?: 0.0, 0.0001)
    }

    // -- 4. Shortening bis 1 Wort ------------------------------------------

    @Test
    fun `lookup kuerzt den Titel bis auf ein Wort`() = runBlocking {
        seedCache(Row("munich|2005", 7.6, 200000, "Drama,Thriller"))
        val service = newService()
        service.ensureLoaded()

        assertEquals(7.6, service.lookup("Munich (2005) STEVEN SPIELBERG")?.rating ?: 0.0, 0.0001)
    }

    // -- 5. Prioritaet exaktes Jahr vor Year-Shift -------------------------

    @Test
    fun `lookup bevorzugt exaktes Jahr vor Jahres-Verschiebung`() = runBlocking {
        seedCache(Row("x|2006", 8.0, 1, "Drama"), Row("x|2005", 1.0, 1, "Drama"))
        val service = newService()
        service.ensureLoaded()

        assertEquals(8.0, service.lookup("X (2006)")?.rating ?: 0.0, 0.0001)
    }

    // -- 6. Kein Match / kaputte oder fehlende Datei -----------------------

    @Test
    fun `lookup liefert null ohne Treffer`() = runBlocking {
        seedCache(Row("something else|2000", 5.0, 1, "Drama"))
        val service = newService()
        service.ensureLoaded()

        assertTrue(service.isAvailable)
        assertNull(service.lookup("Completely Unrelated Title (1999)"))
    }

    @Test
    fun `ohne Cache-Datei bleibt der Service unavailable ohne Crash`() = runBlocking {
        val service = newService()

        service.ensureLoaded()

        assertFalse(service.isAvailable)
        assertNull(service.lookup("Marley & Me (2008)"))
    }

    @Test
    fun `kaputte DB-Datei macht den Service unavailable ohne Crash`() = runBlocking {
        dbFile.writeBytes(byteArrayOf(1, 2, 3, 4))
        metaFile.writeText("""{"built_at":"${Instant.now()}"}""")
        val service = newService()

        service.ensureLoaded()

        assertFalse(service.isAvailable)
        assertNull(service.lookup("Marley & Me (2008)"))
    }

    @Test
    fun `leere DB-Datei macht den Service unavailable ohne Crash`() = runBlocking {
        dbFile.writeBytes(ByteArray(0))
        metaFile.writeText("""{"built_at":"${Instant.now()}"}""")
        val service = newService()

        service.ensureLoaded()

        assertFalse(service.isAvailable)
        assertNull(service.lookup("Marley & Me (2008)"))
    }

    @Test
    fun `kaputter gz-Download macht den Service unavailable ohne Crash`() = runBlocking {
        val service = downloadingService(byteArrayOf(0x1f, 0x8b.toByte(), 1, 2, 3))

        service.ensureLoaded()

        assertFalse(service.isAvailable)
        assertNull(service.lookup("Marley & Me (2008)"))
        assertFalse(dbFile.exists())
    }

    @Test
    fun `ensureLoaded faengt auch Errors statt die App zu beenden`() = runBlocking {
        val service = ImdbEnrichmentService(
            context,
            moshi,
            fakeClient { throw OutOfMemoryError("simulated") },
            fakeClient { req -> bytesResponse(req, 404, ByteArray(0)) },
            Dispatchers.Unconfined
        )

        service.ensureLoaded()

        assertFalse(service.isAvailable)
        assertNull(service.lookup("Marley & Me (2008)"))
    }

    // -- 7. Download + Streaming-Entpacken ---------------------------------

    @Test
    fun `heruntergeladene gz-DB wird zu lesbarer DB entpackt`() = runBlocking {
        val service = downloadingService(gzippedDbOf(Row("marley me|2008", 8.1, 500000, "Comedy,Drama")))

        service.ensureLoaded()

        assertTrue(service.isAvailable)
        assertTrue(dbFile.exists())
        assertFalse(File(context.filesDir, "imdb-enrichment.db.gz").exists())
        assertFalse(File(context.filesDir, "imdb-enrichment.db.tmp").exists())
        assertEquals(500000, service.lookup("Marley & Me (2008)")?.numVotes)
    }

    // -- 8. Migration vom JSON-Format ---------------------------------------

    @Test
    fun `alte JSON-Cache-Dateien werden entfernt`() = runBlocking {
        legacyGzFile.writeBytes(gzipOf("""{"x|2005":[7.0,100,"Action"]}""".toByteArray(Charsets.UTF_8)))
        legacyAssetNamedGzFile.writeBytes(ByteArray(10))
        seedCache(Row("x|2005", 7.0, 100, "Action"))
        val service = newService()

        service.ensureLoaded()

        assertFalse(legacyGzFile.exists())
        assertFalse(legacyAssetNamedGzFile.exists())
        assertNotNull(service.lookup("X (2005)"))
    }

    // -- 9. Staleness --------------------------------------------------------

    @Test
    fun `ensureLoaded laedt neu wenn built_at aelter als 14 Tage ist`() = runBlocking {
        seedCache(Row("old|1999", 1.0, 1, "Drama"), builtAt = Instant.now().minus(Duration.ofDays(15)))

        var githubCalls = 0
        var downloadCalls = 0
        val gitHubClient = fakeClient { req ->
            githubCalls++
            jsonResponse(req, 200, releaseJson(gzUrl = "https://fake/gz", metaUrl = "https://fake/meta"))
        }
        val newDbGz = gzippedDbOf(Row("new|2020", 9.0, 1, "Drama"))
        val downloadClient = fakeClient { req ->
            downloadCalls++
            when (req.url.toString()) {
                "https://fake/gz" -> bytesResponse(req, 200, newDbGz)
                "https://fake/meta" -> bytesResponse(
                    req,
                    200,
                    """{"built_at":"${Instant.now()}"}""".toByteArray(Charsets.UTF_8)
                )
                else -> bytesResponse(req, 404, ByteArray(0))
            }
        }

        val service = ImdbEnrichmentService(context, moshi, gitHubClient, downloadClient, Dispatchers.Unconfined)
        service.ensureLoaded()

        assertTrue(githubCalls > 0)
        assertTrue(downloadCalls > 0)
        assertNotNull(service.lookup("New (2020)"))
        assertNull(service.lookup("Old (1999)"))
    }

    @Test
    fun `Refresh ersetzt eine bereits geoeffnete DB`() = runBlocking {
        seedCache(Row("old|1999", 1.0, 1, "Drama"))
        val service = downloadingService(gzippedDbOf(Row("new|2020", 9.0, 1, "Drama")))
        service.ensureLoaded()
        assertNotNull(service.lookup("Old (1999)"))

        service.ensureLoaded(force = true)

        assertNotNull(service.lookup("New (2020)"))
        assertNull(service.lookup("Old (1999)"))
    }

    @Test
    fun `ensureLoaded laedt nicht neu wenn built_at juenger als 14 Tage ist`() = runBlocking {
        seedCache(Row("fresh|2021", 5.0, 1, "Drama"), builtAt = Instant.now().minus(Duration.ofDays(1)))

        var githubCalls = 0
        val gitHubClient = fakeClient { req ->
            githubCalls++
            jsonResponse(req, 200, releaseJson("https://fake/gz", "https://fake/meta"))
        }
        val downloadClient = fakeClient { req -> bytesResponse(req, 404, ByteArray(0)) }

        val service = ImdbEnrichmentService(context, moshi, gitHubClient, downloadClient, Dispatchers.Unconfined)
        service.ensureLoaded()

        assertEquals(0, githubCalls)
        assertNotNull(service.lookup("Fresh (2021)"))
    }

    // -- 10. Genres-Parsing -----------------------------------------------

    @Test
    fun `genres werden an Kommas aufgeteilt, leerer String wird leere Liste`() = runBlocking {
        seedCache(
            Row("drama family|2000", 5.0, 1, "Drama,Family"),
            Row("no genre|2001", 5.0, 1, "")
        )
        val service = newService()
        service.ensureLoaded()

        assertEquals(listOf("Drama", "Family"), service.lookup("Drama Family (2000)")?.genres)
        assertEquals(emptyList<String>(), service.lookup("No Genre (2001)")?.genres)
    }
}
