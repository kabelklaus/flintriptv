package com.flintr.tv.data.local.imdb

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDoneException
import android.database.sqlite.SQLiteStatement
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
        // SQLiteStatement can only return scalar values, so the three match columns
        // are combined in SQL and split without a Cursor or result-row allocations.
        private const val LOOKUP_SQL =
            "SELECT ifnull(rating, '') || char(31) || ifnull(numvotes, '') || char(31) || " +
                "ifnull(genres, '') FROM enrich WHERE key = ?"
        private const val LOOKUP_FIELD_SEPARATOR = '\u001F'
        private const val COPY_BUFFER_SIZE = 64 * 1024
        private val STALE_THRESHOLD: Duration = Duration.ofDays(14)
    }

    private val loadMutex = Mutex()
    private val releaseAdapter = moshi.adapter(GitHubRelease::class.java)
    private val metaAdapter = moshi.adapter(ImdbEnrichmentMeta::class.java)

    private class OpenDatabase(
        val database: SQLiteDatabase,
        val lookup: SQLiteStatement
    )

    @Volatile
    private var openedDatabase: OpenDatabase? = null

    /** `true`, sobald die Enrichment-DB erfolgreich geoeffnet ist. */
    val isAvailable: Boolean
        get() = openedDatabase != null

    /**
     * Legacy-Kombi aus Oeffnen und Refresh: laedt neu, wenn die DB-Datei fehlt,
     * `built_at` aelter als 14 Tage ist, oder `force = true` gesetzt ist.
     * `withRefresh = false` oeffnet nur eine bereits vorhandene DB (kein Netz).
     * Neue Aufrufer sollen [openIfAvailable] und [refreshIfStale] getrennt nutzen.
     */
    suspend fun ensureLoaded(force: Boolean = false, withRefresh: Boolean = true): Unit =
        withLoadLock("$TAG.ensureLoaded") {
            if (withRefresh) refreshLocked(force) else openIfAvailableLocked()
        }

    /** Oeffnet eine vorhandene DB-Datei ohne Netzwerk/Download und ohne TTL-Pruefung. */
    suspend fun openIfAvailable(): Unit =
        withLoadLock("$TAG.openIfAvailable") { openIfAvailableLocked() }

    /** Refresht nur bei fehlender/staler DB oder `force = true`; oeffnet danach neu. */
    suspend fun refreshIfStale(force: Boolean = false): Unit =
        withLoadLock("$TAG.refreshIfStale") { refreshLocked(force) }

    private suspend fun withLoadLock(reportTag: String, block: () -> Unit) =
        withContext(ioDispatcher) {
            loadMutex.withLock {
                try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    reportHandled(reportTag, t)
                }
            }
        }

    private fun openIfAvailableLocked() {
        if (openedDatabase != null) return
        val dbFile = File(context.filesDir, DB_FILE_NAME)
        if (!dbFile.exists()) return
        openedDatabase = try {
            openDatabase(dbFile)
        } catch (t: Throwable) {
            reportHandled("$TAG.openIfAvailable.open", t)
            if (!dbFile.delete()) dbFile.deleteOnExit()
            null
        }
    }

    private fun refreshLocked(force: Boolean) {
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
                reportHandled("$TAG.refresh.download", t)
                false
            }
        }

        if (refreshed || (openedDatabase == null && dbFile.exists())) {
            openedDatabase = try {
                openDatabase(dbFile)
            } catch (t: Throwable) {
                reportHandled("$TAG.refresh.open", t)
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
        val open = openedDatabase ?: return null
        val (clean, year) = ImdbKeyNormalizer.normalize(rawName)
        if (clean.isBlank()) return null

        return try {
            val wordCount = clean.count { it == ' ' } + 1
            var variant = clean
            synchronized(open) {
                repeat(wordCount) {
                    val candidateCount = if (year == null) 1 else 4
                    for (index in 0 until candidateCount) {
                        val key = ImdbKeyNormalizer.makeKey(variant, candidateYear(year, index))
                        open.lookup.clearBindings()
                        open.lookup.bindString(1, key)
                        val row = try {
                            open.lookup.simpleQueryForString()
                        } catch (e: SQLiteDoneException) {
                            null
                        } ?: continue
                        return toEnrichment(row)
                    }
                    // Naechste, um das letzte Wort gekuerzte Variante (wie subList+joinToString).
                    variant = variant.substringBeforeLast(' ', "")
                }
            }
            null
        } catch (e: Exception) {
            // Die DB kann waehrend eines Refreshs geschlossen werden.
            null
        }
    }

    private fun candidateYear(year: Int?, index: Int): Int? = when (index) {
        0 -> year
        1 -> year?.minus(1)
        2 -> year?.plus(1)
        else -> null
    }

    private fun toEnrichment(row: String): ImdbEnrichment? {
        val firstSeparator = row.indexOf(LOOKUP_FIELD_SEPARATOR)
        if (firstSeparator < 0) return null
        val secondSeparator = row.indexOf(LOOKUP_FIELD_SEPARATOR, firstSeparator + 1)
        if (secondSeparator < 0) return null

        val ratingText = row.substring(0, firstSeparator)
        val votesText = row.substring(firstSeparator + 1, secondSeparator)
        if (ratingText.isEmpty() || votesText.isEmpty()) return null

        val rating = ratingText.toDoubleOrNull() ?: return null
        val numVotes = votesText.toIntOrNull() ?: return null
        val genres = row.substring(secondSeparator + 1).split(",").filter { it.isNotBlank() }
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

    private fun openDatabase(dbFile: File): OpenDatabase {
        val db = SQLiteDatabase.openDatabase(
            dbFile.path,
            null,
            SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
        )
        try {
            // SQLite prueft Header und Schema erst bei der ersten Query.
            db.compileStatement("SELECT key FROM enrich LIMIT 1").use {
                try {
                    it.simpleQueryForString()
                } catch (e: SQLiteDoneException) {
                    // Leere, aber schema-gueltige Enrichment-DB ist weiterhin nutzbar.
                }
            }
            return OpenDatabase(db, db.compileStatement(LOOKUP_SQL))
        } catch (t: Throwable) {
            db.close()
            throw t
        }
    }

    private fun closeDatabase() {
        val open = openedDatabase ?: return
        openedDatabase = null
        synchronized(open) {
            try {
                open.lookup.close()
            } finally {
                open.database.close()
            }
        }
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
