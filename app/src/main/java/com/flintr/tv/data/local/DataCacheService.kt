package com.flintr.tv.data.local

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.edit
import com.flintr.tv.data.local.imdb.ImdbEnrichmentService
import com.flintr.tv.data.local.imdb.ImdbEnrichmentTrace
import com.flintr.tv.data.model.*
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import com.flintr.tv.utils.error.reportError
import com.flintr.tv.utils.error.reportHandled
import com.flintr.tv.utils.error.reportControlFlow
import com.google.firebase.perf.FirebasePerformance
import com.google.firebase.perf.metrics.Trace

/**
 * Thin, failure-tolerant wrapper around Firebase Performance custom traces.
 *
 * A missing FirebaseApp (unit tests, Robolectric) or an unavailable perf SDK must never
 * affect the measured work, so every operation degrades to a no-op. Trace start/stop are
 * thread-tolerant, so [start] may be called from the caller's context (including
 * `Main.immediate`); the measured work itself still runs on its IO dispatcher.
 */
internal object PerfTrace {
    fun start(name: String): Trace? {
        return try {
            FirebasePerformance.getInstance().newTrace(name).also { it.start() }
        } catch (t: Throwable) {
            null
        }
    }

    fun stop(trace: Trace?) {
        if (trace == null) return
        try {
            trace.stop()
        } catch (t: Throwable) {
            // Measurement must not affect the measured path.
        }
    }

    fun increment(trace: Trace?, metric: String, value: Long = 1L) {
        if (trace == null) return
        try {
            trace.incrementMetric(metric, value)
        } catch (t: Throwable) {
            // Measurement must not affect the measured path.
        }
    }
}

data class CachedData(
    val channels: List<LiveStream>,
    val movies: List<VodStream>,
    val series: List<Series>,
    val liveCategories: List<Category>,
    val vodCategories: List<Category>,
    val seriesCategories: List<Category>
)

@Singleton
class DataCacheService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi,
    @Named("IO") private val ioDispatcher: CoroutineDispatcher,
    private val imdbEnrichmentService: ImdbEnrichmentService
) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        "iptv_cache_prefs",
        Context.MODE_PRIVATE
    )

    // Memoized parsed catalog: avoids re-parsing multi-MB JSON on every
    // category navigation. Parsing is single-flight via [parseMutex]: concurrent
    // callers would otherwise each hold a full 57k-item copy at once.
    @Volatile
    private var memoizedCache: CachedData? = null
    private val parseMutex = Mutex()

    // Bumped by save/clear so a parse that raced with them cannot memoize stale files.
    private val cacheGeneration = AtomicInteger(0)

    private val backgroundScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    // Serializes the IMDb DB enrichment phase of saveCachedData against the background
    // >14d refresh's ensureLoaded(): without this, a refresh can close/reopen the DB
    // mid-enrichment of the next save, making lookups return null. A Mutex (rather than
    // tracking/joining the refresh Job) keeps both sides symmetric and lets an
    // unrelated save proceed immediately when no refresh is in flight.
    private val imdbDbMutex = Mutex()

    // Catalog lists are streamed to files instead of SharedPreferences: a
    // 57k-movie list as one String OOMs the 256 MB heap, and prefs keep every
    // value resident for the whole process lifetime.
    private val cacheDir: File get() = File(context.filesDir, CACHE_DIR_NAME)

    init {
        // A process killed mid-save leaves its temp file behind; nothing else ever removes it.
        cacheDir.listFiles { file -> file.name.endsWith(TMP_SUFFIX) }?.forEach { it.delete() }

        // Version 0 stored the lists as prefs strings. Drop them so the ~50 MB prefs
        // XML stops being loaded on startup; the next refresh rewrites the catalog in
        // file form.
        if (LEGACY_JSON_KEYS.any { sharedPreferences.contains(it) }) {
            sharedPreferences.edit {
                LEGACY_JSON_KEYS.forEach { remove(it) }
            }
        }

        // Installs without a stored version already write the version-1 file format.
        val storedVersion = sharedPreferences.getInt(KEY_CACHE_VERSION, CACHE_VERSION)
        if (storedVersion != CACHE_VERSION) {
            android.util.Log.i("DataCacheService", "Cache version $storedVersion -> $CACHE_VERSION, discarding cached catalog")
            cacheDir.deleteRecursively()
            sharedPreferences.edit {
                remove(KEY_CACHE_TIMESTAMP)
                remove(KEY_CACHE_CHANNELS_COUNT)
                remove(KEY_CACHE_MOVIES_COUNT)
                remove(KEY_CACHE_SERIES_COUNT)
                putInt(KEY_CACHE_VERSION, CACHE_VERSION)
            }
        }
    }

    companion object {
        /**
         * Format version of the files in [CACHE_DIR_NAME]. Bump it whenever a change makes
         * existing cache files unreadable or semantically wrong (field renames, changed
         * enrichment rules, a different container format): on startup a stored version that
         * differs from this one deletes the cache files and their prefs metadata, forcing a
         * full resync. Version 0 was the prefs-string format, handled by [LEGACY_JSON_KEYS].
         */
        internal const val CACHE_VERSION = 1
        internal const val KEY_CACHE_VERSION = "iptv_cache_version"
        private const val KEY_CACHE_TIMESTAMP = "iptv_cache_timestamp"
        private val LEGACY_JSON_KEYS = listOf(
            "iptv_cache_channels",
            "iptv_cache_movies",
            "iptv_cache_series",
            "iptv_cache_live_categories",
            "iptv_cache_vod_categories",
            "iptv_cache_series_categories"
        )
        internal const val CACHE_DIR_NAME = "iptv_cache"
        internal const val FILE_CHANNELS = "channels.json"
        internal const val FILE_MOVIES = "movies.json"
        internal const val FILE_SERIES = "series.json"
        internal const val FILE_LIVE_CATEGORIES = "live_categories.json"
        internal const val FILE_VOD_CATEGORIES = "vod_categories.json"
        internal const val FILE_SERIES_CATEGORIES = "series_categories.json"
        internal const val SAVE_CHUNK_SIZE = 2_000
        internal const val CANCEL_CHECK_INTERVAL_ITEMS = 5_000
        private const val TMP_SUFFIX = ".tmp"
        private const val KEY_CACHE_CHANNELS_COUNT = "iptv_cache_channels_count"
        private const val KEY_CACHE_MOVIES_COUNT = "iptv_cache_movies_count"
        private const val KEY_CACHE_SERIES_COUNT = "iptv_cache_series_count"
        private const val KEY_FALLBACK_COOLDOWN_TIMESTAMP = "iptv_fallback_cooldown_timestamp"
        private const val CACHE_VALIDITY_DURATION = 24 * 60 * 60 * 1000L // 24 hours in milliseconds
        private val ISO8601_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)

        internal fun hasUsableFallbackCacheData(
            channelsFile: File?,
            moviesFile: File?,
            seriesFile: File?,
            channelsCount: Int,
            moviesCount: Int,
            seriesCount: Int
        ): Boolean {
            if (!isNonEmptyFile(channelsFile) || !isNonEmptyFile(moviesFile) || !isNonEmptyFile(seriesFile)) {
                return false
            }

            if (channelsCount > 0 && moviesCount > 0 && seriesCount > 0) {
                return true
            }

            return try {
                isNonEmptyValidArray(channelsFile!!) &&
                    isNonEmptyValidArray(moviesFile!!) &&
                    isNonEmptyValidArray(seriesFile!!)
            } catch (e: Exception) {
                reportControlFlow("DataCacheService.isNonEmptyValidArray", e)
                false
            }
        }

        private fun isNonEmptyFile(file: File?): Boolean =
            file != null && file.isFile && file.length() > 0

        // Streaming peek: only reads up to the first element token, so a
        // multi-MB catalog file is never materialized for this check.
        private fun isNonEmptyValidArray(file: File): Boolean =
            file.source().buffer().use { s ->
                val r = JsonReader.of(s)
                r.beginArray()
                r.hasNext()
            }
    }

    fun isCacheValid(): Boolean {
        val timestampString = sharedPreferences.getString(KEY_CACHE_TIMESTAMP, null)
            ?: return false

        val timestamp = try {
            OffsetDateTime.parse(timestampString, ISO8601_FORMAT).toInstant().toEpochMilli()
        } catch (e: Exception) {
            reportHandled("DataCacheService.isCacheValid", e)
            return false
        }

        val now = System.currentTimeMillis()
        val age = now - timestamp

        if (age > CACHE_VALIDITY_DURATION) {
            return false
        }

        // Check if all required data exists and is not empty using stored counts
        return hasUsableFallbackCache()
    }

    /**
     * Streams the catalog to the cache files in chunks of [SAVE_CHUNK_SIZE]: each chunk is
     * enriched and written on [ioDispatcher], then [onProgress] receives the running total of
     * written channels + movies + series in the caller's context (safe to `emit` from a flow).
     * The file format is the same JSON array the list adapter writes, so [readList] is unchanged.
     */
    suspend fun saveCachedData(
        channels: List<LiveStream>,
        movies: List<VodStream>,
        series: List<Series>,
        liveCategories: List<Category>,
        vodCategories: List<Category>,
        seriesCategories: List<Category>,
        onProgress: suspend (savedItems: Int) -> Unit = {}
    ): Boolean {
        // NOTE: Use || (not &&) here — all three content types must be present.
        // isCacheValid(), hasCacheData(), and loadCachedDataInternal() all require
        // channels, movies, AND series to be non-empty. Saving partial data would
        // create a cache that immediately fails validation elsewhere.
        if (channels.isEmpty() || movies.isEmpty() || series.isEmpty()) {
            android.util.Log.w("DataCacheService", "Refusing to save empty data to cache: " +
                "channels=${channels.size}, movies=${movies.size}, series=${series.size}")
            return false
        }
        val trace = PerfTrace.start("flintr_cache_save")
        try {
            val imdbWasOpen = imdbEnrichmentService.isAvailable
            val timestamp = ZonedDateTime.now().format(ISO8601_FORMAT)

            var savedItems = 0
            val onChunkWritten: suspend (Int) -> Unit = { written ->
                savedItems += written
                PerfTrace.increment(trace, "chunks_written")
                onProgress(savedItems)
            }
            writeListChunked(FILE_CHANNELS, LiveStream::class.java, channels, transform = null, onChunkWritten)
            // Held for the whole enrichment phase so a concurrent background refresh
            // (see imdbDbMutex) can't close/reopen the DB mid-lookup.
            val (enrichedMovies, enrichedSeries) = imdbDbMutex.withLock {
                val enricher = imdbEnricher()
                ImdbEnrichmentTrace.start()
                try {
                    val enrichedMovies = writeListChunked(FILE_MOVIES, VodStream::class.java, movies, enricher?.let { it::enrichMovie }, onChunkWritten)
                    val enrichedSeries = writeListChunked(FILE_SERIES, Series::class.java, series, enricher?.let { it::enrichSeries }, onChunkWritten)
                    enrichedMovies to enrichedSeries
                } finally {
                    ImdbEnrichmentTrace.stop()
                }
            }

            withContext(ioDispatcher) {
                val categoryAdapter = listAdapter(Category::class.java)
                writeList(FILE_LIVE_CATEGORIES, categoryAdapter, liveCategories)
                writeList(FILE_VOD_CATEGORIES, categoryAdapter, vodCategories)
                writeList(FILE_SERIES_CATEGORIES, categoryAdapter, seriesCategories)
            }

            sharedPreferences.edit {
                putString(KEY_CACHE_TIMESTAMP, timestamp)
                putInt(KEY_CACHE_CHANNELS_COUNT, channels.size)
                putInt(KEY_CACHE_MOVIES_COUNT, enrichedMovies.size)
                putInt(KEY_CACHE_SERIES_COUNT, enrichedSeries.size)
                putInt(KEY_CACHE_VERSION, CACHE_VERSION)
            }
            cacheGeneration.incrementAndGet()
            memoizedCache = CachedData(channels, enrichedMovies, enrichedSeries, liveCategories, vodCategories, seriesCategories)
            android.util.Log.d("DataCacheService", "Cache saved successfully: $timestamp | Channels: ${channels.size}, Movies: ${enrichedMovies.size}, Series: ${enrichedSeries.size}")
            // The >14d IMDb refresh is a multi-MB download. With a DB already open this save
            // used it as-is; the refresh runs afterwards so it neither delays the save nor
            // closes the DB mid-enrichment. The next save picks up the new DB. imdbDbMutex
            // keeps this refresh from overlapping that next save's own enrichment phase.
            if (imdbWasOpen) backgroundScope.launch { imdbDbMutex.withLock { imdbEnrichmentService.ensureLoaded() } }
            PerfTrace.increment(trace, "items_saved", savedItems.toLong())
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Log error but don't crash
            reportError("DataCacheService", "Failed to save cache", e)
            return false
        } finally {
            PerfTrace.stop(trace)
        }
    }

    /**
     * Reichert Movies/Series einmalig vor dem Schreiben mit IMDb-Daten an (Chris-Spec 30.10.):
     * Genres kommen exklusiv aus IMDb, Xtream-Ratings werden abgeloest. Ist der Enrichment-Service
     * nicht verfuegbar (z.B. noch kein Download), liefert dies null und die Xtream-Rohwerte bleiben
     * unveraendert -- nur ein versuchter Lookup ohne Treffer nullt die Felder.
     */
    private suspend fun imdbEnricher(): ImdbEnricher? {
        if (!imdbEnrichmentService.isAvailable) imdbEnrichmentService.ensureLoaded()
        if (!imdbEnrichmentService.isAvailable) {
            android.util.Log.d("DataCacheService", "IMDb enrichment unavailable, keeping Xtream genre/rating values")
            return null
        }
        return ImdbEnricher(imdbEnrichmentService)
    }

    private class ImdbEnricher(private val service: ImdbEnrichmentService) {
        // IMDb ratings have ~100 distinct values: format each once instead of
        // allocating a Formatter for every one of the ~70k titles.
        private val ratingLabels = HashMap<Double, String>()

        fun enrichMovie(movie: VodStream): VodStream {
            val enrichment = service.lookup(movie.name)
            return movie.copy(
                genre = enrichment?.genres?.joinToString(", "),
                rating = enrichment?.rating?.let(::ratingLabel),
                rating5Based = enrichment?.rating?.let { it / 2.0 }
            )
        }

        fun enrichSeries(item: Series): Series {
            val enrichment = service.lookup(item.name)
            return item.copy(
                genre = enrichment?.genres?.joinToString(", "),
                rating = enrichment?.rating?.let(::ratingLabel),
                rating5Based = enrichment?.rating?.let { it / 2.0 }
            )
        }

        private fun ratingLabel(rating: Double): String =
            ratingLabels.getOrPut(rating) { String.format(Locale.US, "%.1f", rating) }
    }

    private fun <T> listAdapter(itemClass: Class<T>): JsonAdapter<List<T>> =
        moshi.adapter(Types.newParameterizedType(List::class.java, itemClass))

    /** Streams [items] to a temp file and renames it into place, so readers never see a partial file. */
    private fun <T> writeList(fileName: String, adapter: JsonAdapter<List<T>>, items: List<T>) {
        writeAtomically(fileName) { writer -> adapter.toJson(writer, items) }
    }

    /**
     * Writes [items] as one JSON array, [SAVE_CHUNK_SIZE] elements at a time, yielding between
     * chunks. Returns the stored list: [items] itself without [transform], otherwise the
     * transformed items (kept for the memoized cache).
     */
    private suspend fun <T> writeListChunked(
        fileName: String,
        itemClass: Class<T>,
        items: List<T>,
        transform: ((T) -> T)?,
        onChunkWritten: suspend (Int) -> Unit
    ): List<T> {
        val callerContext = coroutineContext
        return withContext(ioDispatcher) {
            val adapter = moshi.adapter(itemClass)
            val transformed: MutableList<T>? = transform?.let { ArrayList(items.size) }
            writeAtomically(fileName) { writer ->
                writer.beginArray()
                for (start in items.indices step SAVE_CHUNK_SIZE) {
                    val end = minOf(start + SAVE_CHUNK_SIZE, items.size)
                    for (index in start until end) {
                        val item = if (transform != null) transform(items[index]) else items[index]
                        transformed?.add(item)
                        adapter.toJson(writer, item)
                    }
                    withContext(callerContext) {
                        onChunkWritten(end - start)
                    }
                    yield()
                }
                writer.endArray()
            }
            transformed ?: items
        }
    }

    private inline fun writeAtomically(fileName: String, write: (JsonWriter) -> Unit) {
        cacheDir.mkdirs()
        val tmp = File(cacheDir, "$fileName$TMP_SUFFIX")
        val target = File(cacheDir, fileName)
        JsonWriter.of(tmp.sink().buffer()).use { writer -> write(writer) }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } else if (!tmp.renameTo(target)) {
                throw IOException("rename failed: $fileName")
            }
        } catch (e: IOException) {
            if (!tmp.delete()) tmp.deleteOnExit()
            throw e
        }
    }

    /**
     * Streams the JSON array in [fileName] element by element, so callers decide what to keep
     * instead of always materializing the whole list. Checks for cancellation every
     * [CANCEL_CHECK_INTERVAL_ITEMS] items. Returns false if the file does not exist.
     */
    private suspend fun <T> readEach(fileName: String, itemClass: Class<T>, onItem: (T) -> Unit): Boolean {
        val file = File(cacheDir, fileName)
        if (!file.exists()) return false
        val adapter = moshi.adapter(itemClass)
        file.source().buffer().use { source ->
            val reader = JsonReader.of(source)
            reader.beginArray()
            var count = 0
            while (reader.hasNext()) {
                adapter.fromJson(reader)?.let(onItem)
                if (++count % CANCEL_CHECK_INTERVAL_ITEMS == 0) currentCoroutineContext().ensureActive()
            }
            reader.endArray()
        }
        return true
    }

    private suspend fun <T> readList(fileName: String, itemClass: Class<T>): List<T>? {
        val items = ArrayList<T>()
        return if (readEach(fileName, itemClass) { items.add(it) }) items else null
    }

    private suspend fun parseCachedData(): CachedData? {
        val generation = cacheGeneration.get()
        try {
            val channels = readList(FILE_CHANNELS, LiveStream::class.java) ?: return null
            val movies = readList(FILE_MOVIES, VodStream::class.java) ?: return null
            val series = readList(FILE_SERIES, Series::class.java) ?: return null

            val data = CachedData(
                channels = channels,
                movies = movies,
                series = series,
                liveCategories = readList(FILE_LIVE_CATEGORIES, Category::class.java) ?: emptyList(),
                vodCategories = readList(FILE_VOD_CATEGORIES, Category::class.java) ?: emptyList(),
                seriesCategories = readList(FILE_SERIES_CATEGORIES, Category::class.java) ?: emptyList()
            )
            if (generation == cacheGeneration.get()) memoizedCache = data
            return data
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            reportError("DataCacheService", "Failed to load cache", e)
            return null
        }
    }

    /** Blocking variant of [loadCachedDataAsync]; never call it from the main thread. */
    fun loadCachedData(): CachedData? = runBlocking { loadCachedDataAsync() }

    /**
     * Returns the memoized catalog or parses the cache files on [ioDispatcher]. Concurrent
     * callers share a single parse: whoever waits on [parseMutex] gets the memoized result.
     */
    suspend fun loadCachedDataAsync(): CachedData? = withContext(ioDispatcher) {
        memoizedCache ?: parseMutex.withLock { memoizedCache ?: parseCachedData() }
    }

    fun hasCacheData(): Boolean {
        val channelsCount = sharedPreferences.getInt(KEY_CACHE_CHANNELS_COUNT, -1)
        val moviesCount = sharedPreferences.getInt(KEY_CACHE_MOVIES_COUNT, -1)
        val seriesCount = sharedPreferences.getInt(KEY_CACHE_SERIES_COUNT, -1)

        android.util.Log.d("DataCacheService", "hasCacheData: counts=$channelsCount/$moviesCount/$seriesCount")

        if (channelsCount > 0 && moviesCount > 0 && seriesCount > 0) {
            return true
        }

        // Fallback: check cache files exist (for cache without count keys)
        val channelsFile = File(cacheDir, FILE_CHANNELS)
        val moviesFile = File(cacheDir, FILE_MOVIES)
        val seriesFile = File(cacheDir, FILE_SERIES)
        val hasFiles = channelsFile.length() > 0 && moviesFile.length() > 0 && seriesFile.length() > 0
        android.util.Log.d("DataCacheService", "hasCacheData: file fallback=" +
            "channels=${channelsFile.length()}, movies=${moviesFile.length()}, series=${seriesFile.length()} -> $hasFiles")
        return hasFiles
    }

    fun hasUsableFallbackCache(): Boolean {
        return hasUsableFallbackCacheData(
            channelsFile = File(cacheDir, FILE_CHANNELS),
            moviesFile = File(cacheDir, FILE_MOVIES),
            seriesFile = File(cacheDir, FILE_SERIES),
            channelsCount = sharedPreferences.getInt(KEY_CACHE_CHANNELS_COUNT, -1),
            moviesCount = sharedPreferences.getInt(KEY_CACHE_MOVIES_COUNT, -1),
            seriesCount = sharedPreferences.getInt(KEY_CACHE_SERIES_COUNT, -1)
        )
    }

    fun shouldUseCacheForBrowsing(): Boolean {
        return isCacheValid() || (isFallbackCooldownActive() && hasUsableFallbackCache())
    }

    fun setFallbackCooldownTimestamp(timestamp: Long) {
        sharedPreferences.edit {
            putLong(KEY_FALLBACK_COOLDOWN_TIMESTAMP, timestamp)
        }
    }

    fun isFallbackCooldownActive(): Boolean {
        val cooldownTimestamp = sharedPreferences.getLong(KEY_FALLBACK_COOLDOWN_TIMESTAMP, 0L)
        if (cooldownTimestamp == 0L) return false
        return (System.currentTimeMillis() - cooldownTimestamp) < CACHE_VALIDITY_DURATION
    }

    fun clearFallbackCooldown() {
        sharedPreferences.edit {
            remove(KEY_FALLBACK_COOLDOWN_TIMESTAMP)
        }
    }

    /**
     * Verwirft nur das im RAM gepinnte [CachedData]; Cache-Dateien und Prefs bleiben
     * unangetastet, sodass der naechste Zugriff sie neu einliest. [cacheGeneration] sorgt
     * dafuer, dass ein bereits laufender Parse sein Ergebnis nicht mehr memoisiert. Bewusst
     * ohne [parseMutex], damit der Aufruf aus onTrimMemory nicht auf einen multi-MB-Parse
     * warten muss.
     */
    fun trimCachedCatalog() {
        cacheGeneration.incrementAndGet()
        memoizedCache = null
    }

    fun clearCache() {
        // Take parseMutex so an in-flight parse can neither repopulate memoizedCache after
        // the invalidation below nor read the files while they are being deleted.
        runBlocking {
            parseMutex.withLock {
                cacheGeneration.incrementAndGet()
                memoizedCache = null
            }
        }
        sharedPreferences.edit { clear() }
        cacheDir.deleteRecursively()
    }

    /**
     * Loads specific items by their IDs: filtered from the memoized catalog when present,
     * otherwise streamed from [fileName] keeping only the matches, so a lookup never
     * materializes the full list.
     *
     * @param fileName The cache file holding the JSON list.
     * @param ids The list of IDs to load.
     * @param itemClass The class of the item, for Moshi type creation.
     * @param memoSelector The list in the memoized catalog that mirrors [fileName].
     * @param idSelector A function to extract the ID from an item.
     * @return A list of items matching the IDs, or null if the cache is invalid.
     */
    private suspend fun <T : Any> loadItemsByIds(
        fileName: String,
        ids: List<Int>,
        itemClass: Class<T>,
        memoSelector: (CachedData) -> List<T>,
        idSelector: (T) -> Int
    ): List<T>? = withContext(ioDispatcher) {
        if (ids.isEmpty()) {
            return@withContext emptyList()
        }

        try {
            val wanted = ids.toHashSet()
            val itemsById = HashMap<Int, T>()
            var total = 0
            val keepIfWanted: (T) -> Unit = { item ->
                total++
                val id = idSelector(item)
                if (id in wanted) itemsById[id] = item
            }
            val memoItems = memoizedCache?.let(memoSelector)
            if (memoItems != null) {
                memoItems.forEach(keepIfWanted)
            } else if (!readEach(fileName, itemClass, keepIfWanted)) {
                return@withContext null
            }
            val filteredItems = ids.mapNotNull { itemsById[it] }
            val origin = if (memoItems != null) "memo" else "file"

            android.util.Log.d("DataCacheService", "Loaded ${filteredItems.size} ${itemClass.simpleName}s from $total total (requested ${ids.size} IDs, from $origin)")
            return@withContext filteredItems
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            reportError("DataCacheService", "Failed to load ${itemClass.simpleName}s by IDs from $fileName", e)
            return@withContext null
        }
    }

    /**
     * Loads only specific channels by their stream IDs from cache.
     * This is more memory-efficient than loading all channels when only a few are needed.
     * 
     * @param streamIds List of stream IDs to load
     * @return List of LiveStream objects matching the IDs, or null if cache is invalid
     */
    suspend fun loadChannelsByIds(streamIds: List<Int>): List<LiveStream>? =
        loadItemsByIds(FILE_CHANNELS, streamIds, LiveStream::class.java, CachedData::channels) { it.streamId }

    /**
     * Loads only specific movies by their stream IDs from cache.
     * This is more memory-efficient than loading all movies when only a few are needed.
     * 
     * @param streamIds List of stream IDs to load
     * @return List of VodStream objects matching the IDs, or null if cache is invalid
     */
    suspend fun loadMoviesByIds(streamIds: List<Int>): List<VodStream>? =
        loadItemsByIds(FILE_MOVIES, streamIds, VodStream::class.java, CachedData::movies) { it.streamId }

    /**
     * Loads only specific series by their series IDs from cache.
     * This is more memory-efficient than loading all series when only a few are needed.
     * 
     * @param seriesIds List of series IDs to load
     * @return List of Series objects matching the IDs, or null if cache is invalid
     */
    suspend fun loadSeriesByIds(seriesIds: List<Int>): List<Series>? =
        loadItemsByIds(FILE_SERIES, seriesIds, Series::class.java, CachedData::series) { it.seriesId }
}
