package com.flintr.tv.data.tmdb

import android.content.Context
import android.os.Build
import android.util.Log
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ein Eintrag im persistenten TMDB-Metadaten-Cache. [meta] ist null, wenn der
 * Eintrag ein negatives Ergebnis ([negative] = true, 404 bzw. Search-Miss)
 * repraesentiert. [discoverIds] haelt spaetere Discover-Result-Listen; [recItems]
 * Recommendations inklusive Titel/Release-Datum fuer den direkten Katalog-Match.
 */
@JsonClass(generateAdapter = true)
data class TmdbMetaCacheEntry(
    val meta: TmdbMeta? = null,
    val fetchedAtMs: Long = 0,
    val negative: Boolean = false,
    val discoverIds: List<Int>? = null,
    val recItems: List<TmdbRecItem>? = null
)

/**
 * Persistenter Datei-Cache fuer TMDB-Metadaten mit 24-h-TTL.
 *
 * Der Zustand wird nach dem ersten Lesen memoisiert; wiederholte [loadAll]-Aufrufe
 * ohne zwischenzeitliches [putAll] lesen nicht erneut von der Platte. Schreibvorgaenge
 * laufen single-flight ueber [mutex] und atomar ueber eine Temp-Datei plus Rename.
 *
 * Der primaere Konstruktor nimmt Verzeichnis und Uhr explizit an, damit Tests
 * deterministisch arbeiten koennen. Der [Inject]-Konstruktor delegiert nur mit den
 * Produktions-Defaults (filesDir und System-Uhr); Default-Parameter direkt am
 * [Inject]-Konstruktor sind hier bewusst vermieden.
 */
@Singleton
class TmdbMetaCache internal constructor(
    private val directory: File,
    private val clock: () -> Long
) {

    @Inject
    constructor(@ApplicationContext context: Context) : this(
        directory = context.filesDir,
        clock = System::currentTimeMillis
    )

    companion object {
        const val CAP = 5000
        const val TTL_MS = 24L * 60 * 60 * 1000

        internal const val FILE_NAME = "tmdb_meta_cache.json"
        private const val TMP_SUFFIX = ".tmp"
        private const val TAG = "TmdbMetaCache"

        /** Nur nicht-negative Eintraege innerhalb der TTL gelten als frisch. */
        fun isFresh(entry: TmdbMetaCacheEntry, nowMs: Long): Boolean =
            !entry.negative && nowMs - entry.fetchedAtMs < TTL_MS
    }

    private val moshi = Moshi.Builder().build()
    private val adapter: JsonAdapter<Map<String, TmdbMetaCacheEntry>> = moshi.adapter(
        Types.newParameterizedType(
            Map::class.java,
            String::class.java,
            TmdbMetaCacheEntry::class.java
        )
    )

    @Volatile
    private var memoized: Map<String, TmdbMetaCacheEntry>? = null

    private val mutex = Mutex()

    /** Liefert den memoisierten Cache oder liest ihn einmalig von der Platte. */
    suspend fun loadAll(): Map<String, TmdbMetaCacheEntry> = mutex.withLock {
        memoized ?: withContext(Dispatchers.IO) { readFromDisk() }.also { memoized = it }
    }

    /** Merged [entries] in den Cache, prunt auf [CAP] und schreibt atomar. */
    suspend fun putAll(entries: Map<String, TmdbMetaCacheEntry>) = mutex.withLock {
        val current = memoized ?: withContext(Dispatchers.IO) { readFromDisk() }
        val merged = LinkedHashMap(current).apply { putAll(entries) }
        val pruned = if (merged.size <= CAP) merged else prune(merged)
        memoized = pruned
        withContext(Dispatchers.IO) {
            try {
                writeAtomically(pruned)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Der RAM-Zustand bleibt nutzbar; der naechste putAll versucht es erneut.
                Log.w(TAG, "Cache konnte nicht geschrieben werden", e)
            }
        }
    }

    /** Behaelt die [CAP] Eintraege mit dem groessten [TmdbMetaCacheEntry.fetchedAtMs]. */
    private fun prune(entries: Map<String, TmdbMetaCacheEntry>): Map<String, TmdbMetaCacheEntry> {
        val kept = LinkedHashMap<String, TmdbMetaCacheEntry>(CAP)
        entries.entries
            .sortedByDescending { it.value.fetchedAtMs }
            .take(CAP)
            .forEach { kept[it.key] = it.value }
        return kept
    }

    /** Defensiv: fehlende, leere oder korrupte Dateien ergeben eine leere Map. */
    private fun readFromDisk(): Map<String, TmdbMetaCacheEntry> {
        val file = File(directory, FILE_NAME)
        if (!file.isFile) return emptyMap()
        return try {
            file.source().buffer().use { source ->
                adapter.fromJson(source) ?: emptyMap()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cache-Datei unlesbar, ignoriere sie", e)
            emptyMap()
        }
    }

    /** Schreibt erst die Temp-Datei und benennt sie dann atomar um. */
    private fun writeAtomically(entries: Map<String, TmdbMetaCacheEntry>) {
        directory.mkdirs()
        val tmp = File(directory, "$FILE_NAME$TMP_SUFFIX")
        val target = File(directory, FILE_NAME)
        try {
            tmp.sink().buffer().use { sink ->
                JsonWriter.of(sink).use { writer -> adapter.toJson(writer, entries) }
            }
            moveIntoPlace(tmp, target)
        } catch (e: Exception) {
            tmp.delete()
            throw e
        }
    }

    /**
     * API >= 26 nutzt [Files.move] mit [StandardCopyOption.ATOMIC_MOVE] und
     * [StandardCopyOption.REPLACE_EXISTING] kombiniert, darunter bleibt [File.renameTo]
     * (desugar_jdk_libs hat kein java.nio.file).
     */
    private fun moveIntoPlace(tmp: File, target: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                Files.move(
                    tmp.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (e: AtomicMoveNotSupportedException) {
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } else if (!tmp.renameTo(target)) {
            throw IOException("rename failed: $FILE_NAME")
        }
    }
}
