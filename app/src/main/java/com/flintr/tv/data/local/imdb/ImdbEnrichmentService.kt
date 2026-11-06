package com.flintr.tv.data.local.imdb

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.flintr.tv.BuildConfig
import com.flintr.tv.data.model.GitHubAsset
import com.flintr.tv.data.model.GitHubRelease
import com.flintr.tv.utils.error.reportHandled
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** IMDb-Anreicherung (Rating, Stimmenzahl, Genres) fuer einen Katalog-Eintrag. */
data class ImdbEnrichment(
    val rating: Double,
    val numVotes: Int,
    val genres: List<String>
)

/** Minimaler Spiegel von `imdb-enrichment.meta.json`, nur fuer den Staleness-Check. */
@JsonClass(generateAdapter = true)
internal data class ImdbEnrichmentMeta(
    @Json(name = "built_at") val builtAt: String
)

/**
 * Laedt die IMDb-Enrichment-DB vom GitHub-Release `imdb-data`
 * (`kabelklaus/flintriptv`), entpackt sie nach `filesDir/imdb-enrichment.db`
 * und beantwortet Lookups per Primary-Key-Query ueber die Match-Kette aus
 * `tools/imdb_enrichment/validate_hits.py`. Die Daten werden nie komplett in
 * den Heap geladen.
 *
 * Reines Add-on: jeder Download-/Entpack-/Open-Fehler (auch ein Error wie OOM)
 * laesst den Service lediglich "unavailable" zurueck (kein Crash, kein
 * automatischer Retry).
 */
@Singleton
class ImdbEnrichmentService @Inject constructor(
    @ApplicationContext private val context: Context,
    moshi: Moshi,
    @Named("GitHub") private val gitHubOkHttpClient: OkHttpClient,
    @Named("Download") private val downloadOkHttpClient: OkHttpClient,
    @Named("IO") private val ioDispatcher: CoroutineDispatcher
) {

    companion object {
        private const val TAG = "ImdbEnrichmentService"
        private const val RELEASE_TAG = "imdb-data"
        private const val DB_GZ_ASSET_NAME = "imdb-enrichment.db.gz"
        private const val META_ASSET_NAME = "imdb-enrichment.meta.json"
        private const val DB_FILE_NAME = "imdb-enrichment.db"
        private const val DB_GZ_FILE_NAME = "imdb-enrichment.db.gz"
        private const val META_FILE_NAME = "imdb_enrichment.meta.json"
        /** Cache-Dateien des frueheren JSON-Formats, werden beim Laden entfernt. */
        private val LEGACY_FILE_NAMES = listOf(
            "imdb_enrichment.json.gz",
            "imdb_enrichment.json.gz.tmp",
            "imdb-enrichment.json.gz"
        )
        private const val LOOKUP_SQL = "SELECT rating, numvotes, genres FROM enrich WHERE key = ?"
        private const val COPY_BUFFER_SIZE = 64 * 1024
        private val STALE_THRESHOLD: Duration = Duration.ofDays(14)
    }

    private val loadMutex = Mutex()
    private val releaseAdapter = moshi.adapter(GitHubRelease::class.java)
    private val metaAdapter = moshi.adapter(ImdbEnrichmentMeta::class.java)

    @Volatile
    private var database: SQLiteDatabase? = null

    /** `true`, sobald die Enrichment-DB erfolgreich geoeffnet ist. */
    val isAvailable: Boolean
        get() = database != null

    /**
     * Stellt sicher, dass die Enrichment-DB geoeffnet ist: laedt neu, wenn die
     * DB-Datei fehlt, `built_at` aelter als 14 Tage ist, oder `force = true`
     * gesetzt ist. Scheitert Download, Entpacken oder Oeffnen, bleibt der
     * Service unavailable -- ohne Crash und ohne automatischen Retry-Loop.
     */
    suspend fun ensureLoaded(force: Boolean = false): Unit = withContext(ioDispatcher) {
        loadMutex.withLock {
            try {
                loadLocked(force)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                reportHandled("$TAG.ensureLoaded", t)
            }
        }
    }

    private fun loadLocked(force: Boolean) {
        deleteLegacyFiles()
        val dbFile = File(context.filesDir, DB_FILE_NAME)
        val metaFile = File(context.filesDir, META_FILE_NAME)
        val needsRefresh = force || !dbFile.exists() || !metaFile.exists() || isStale(metaFile)

        var refreshed = false
        if (needsRefresh) {
            refreshed = try {
                downloadAndCache(dbFile, metaFile)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                reportHandled("$TAG.ensureLoaded.download", t)
                false
            }
        }

        if (refreshed || (database == null && dbFile.exists())) {
            database = try {
                openDatabase(dbFile)
            } catch (t: Throwable) {
                reportHandled("$TAG.ensureLoaded.open", t)
                if (!dbFile.delete()) dbFile.deleteOnExit()
                null
            }
        }
    }

    /**
     * Fuehrt die Match-Kette aus `validate_hits.py` nach: Laengen-Varianten von
     * voll bis 1 Wort (von rechts gekuerzt), pro Variante Jahres-Toleranz
     * (exakt, -1, +1, ohne Jahr). Erster Treffer gewinnt; `null` bei no-match
     * oder wenn der Service unavailable ist.
     */
    fun lookup(rawName: String): ImdbEnrichment? {
        val db = database ?: return null
        val (clean, year) = ImdbKeyNormalizer.normalize(rawName)
        if (clean.isBlank()) return null

        return try {
            val words = clean.split(" ")
            for (position in words.indices) {
                val variant = words.subList(0, words.size - position).joinToString(" ")
                for (key in keyCandidates(variant, year)) {
                    db.rawQuery(LOOKUP_SQL, arrayOf(key)).use { cursor ->
                        if (cursor.moveToFirst()) return toEnrichment(cursor)
                    }
                }
            }
            null
        } catch (e: Exception) {
            // Die DB kann waehrend eines Refreshs geschlossen werden.
            null
        }
    }

    private fun keyCandidates(clean: String, year: Int?): List<String> {
        if (year == null) return listOf(ImdbKeyNormalizer.makeKey(clean, null))
        return listOf(
            ImdbKeyNormalizer.makeKey(clean, year),
            ImdbKeyNormalizer.makeKey(clean, year - 1),
            ImdbKeyNormalizer.makeKey(clean, year + 1),
            ImdbKeyNormalizer.makeKey(clean, null)
        )
    }

    private fun toEnrichment(cursor: Cursor): ImdbEnrichment? {
        if (cursor.isNull(0) || cursor.isNull(1)) return null
        val rating = cursor.getDouble(0)
        val numVotes = cursor.getInt(1)
        val genres = cursor.getString(2).orEmpty().split(",").filter { it.isNotBlank() }
        return ImdbEnrichment(rating, numVotes, genres)
    }

    private fun isStale(metaFile: File): Boolean {
        return try {
            val meta = metaAdapter.fromJson(metaFile.readText()) ?: return true
            Duration.between(Instant.parse(meta.builtAt), Instant.now()) > STALE_THRESHOLD
        } catch (e: Exception) {
            true
        }
    }

    private fun deleteLegacyFiles() {
        LEGACY_FILE_NAMES.forEach { File(context.filesDir, it).delete() }
    }

    /**
     * Laedt die gz-DB als Stream auf Disk, entpackt sie streamend und ersetzt
     * erst danach die geoeffnete DB. Schlaegt ein Schritt fehl, bleibt die
     * bisherige DB in Benutzung.
     */
    private fun downloadAndCache(dbFile: File, metaFile: File): Boolean {
        val release = fetchReleaseByTag(RELEASE_TAG) ?: return false
        val dbGzAsset = release.assets.firstOrNull { it.name == DB_GZ_ASSET_NAME } ?: return false
        val metaAsset = release.assets.firstOrNull { it.name == META_ASSET_NAME } ?: return false

        val gzFile = File(context.filesDir, DB_GZ_FILE_NAME)
        val unpackedTmp = File(context.filesDir, "$DB_FILE_NAME.tmp")
        try {
            if (!downloadAssetToFile(dbGzAsset, gzFile)) return false
            val metaBytes = downloadAssetBytes(metaAsset) ?: return false

            gunzipToFile(gzFile, unpackedTmp)
            closeDatabase()
            renameOrThrow(unpackedTmp, dbFile)
            writeAtomically(metaFile, metaBytes)
            return true
        } finally {
            gzFile.delete()
            unpackedTmp.delete()
        }
    }

    private fun fetchReleaseByTag(tag: String): GitHubRelease? {
        val url = "https://api.github.com/repos/${BuildConfig.GITHUB_REPO_OWNER}/" +
            "${BuildConfig.GITHUB_REPO_NAME}/releases/tags/$tag"
        val request = Request.Builder().url(url).build()
        gitHubOkHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body ?: return null
            return releaseAdapter.fromJson(body.string())
        }
    }

    private fun assetRequest(asset: GitHubAsset): Request =
        Request.Builder()
            .url(asset.url ?: asset.browserDownloadUrl)
            .header("Accept", "application/octet-stream")
            .build()

    private fun downloadAssetBytes(asset: GitHubAsset): ByteArray? {
        downloadOkHttpClient.newCall(assetRequest(asset)).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.bytes()
        }
    }

    private fun downloadAssetToFile(asset: GitHubAsset, target: File): Boolean {
        downloadOkHttpClient.newCall(assetRequest(asset)).execute().use { response ->
            if (!response.isSuccessful) return false
            val body = response.body ?: return false
            body.byteStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output, COPY_BUFFER_SIZE) }
            }
            return true
        }
    }

    /** Entpackt mit konstantem Speicherbedarf, unabhaengig von der DB-Groesse. */
    private fun gunzipToFile(gzFile: File, target: File) {
        gzFile.inputStream().use { raw ->
            GZIPInputStream(raw, COPY_BUFFER_SIZE).use { input ->
                target.outputStream().use { output -> input.copyTo(output, COPY_BUFFER_SIZE) }
            }
        }
    }

    private fun openDatabase(dbFile: File): SQLiteDatabase {
        val db = SQLiteDatabase.openDatabase(
            dbFile.path,
            null,
            SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
        )
        try {
            // SQLite prueft Header und Schema erst bei der ersten Query.
            db.rawQuery("SELECT key FROM enrich LIMIT 1", null).use { it.moveToFirst() }
        } catch (t: Throwable) {
            db.close()
            throw t
        }
        return db
    }

    private fun closeDatabase() {
        val db = database ?: return
        database = null
        db.close()
    }

    private fun writeAtomically(target: File, bytes: ByteArray) {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeBytes(bytes)
        renameOrThrow(tmp, target)
    }

    private fun renameOrThrow(source: File, target: File) {
        if (!source.renameTo(target)) {
            if (!source.delete()) source.deleteOnExit()
            throw IOException("Failed to rename ${source.name} to ${target.name}")
        }
    }
}
