package com.flintr.tv.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.flintr.tv.data.local.imdb.ImdbEnrichmentService
import com.flintr.tv.data.model.*
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import com.flintr.tv.utils.error.reportError
import com.flintr.tv.utils.error.reportHandled
import com.flintr.tv.utils.error.reportControlFlow

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
    // category navigation. @Volatile is enough — a duplicate parse on a
    // race is harmless (both results are valid).
    @Volatile
    private var memoizedCache: CachedData? = null

    // Catalog lists are streamed to files instead of SharedPreferences: a
    // 57k-movie list as one String OOMs the 256 MB heap, and prefs keep every
    // value resident for the whole process lifetime.
    private val cacheDir: File get() = File(context.filesDir, CACHE_DIR_NAME)

    init {
        // Pre-file cache format stored the lists as prefs strings. Drop them so the
        // ~50 MB prefs XML stops being loaded on startup; the next refresh rewrites
        // the catalog in file form.
        if (LEGACY_JSON_KEYS.any { sharedPreferences.contains(it) }) {
            sharedPreferences.edit {
                LEGACY_JSON_KEYS.forEach { remove(it) }
            }
        }
    }

    companion object {
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
        private const val KEY_CACHE_CHANNELS_COUNT = "iptv_cache_channels_count"
        private const val KEY_CACHE_MOVIES_COUNT = "iptv_cache_movies_count"
        private const val KEY_CACHE_SERIES_COUNT = "iptv_cache_series_count"
        private const val KEY_FALLBACK_COOLDOWN_TIMESTAMP = "iptv_fallback_cooldown_timestamp"
        private const val CACHE_VALIDITY_DURATION = 24 * 60 * 60 * 1000L // 24 hours in milliseconds
        private val ISO8601_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)

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
            ISO8601_FORMAT.parse(timestampString)?.time ?: return false
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

    fun saveCachedData(
        channels: List<LiveStream>,
        movies: List<VodStream>,
        series: List<Series>,
        liveCategories: List<Category>,
        vodCategories: List<Category>,
        seriesCategories: List<Category>
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
        try {
            val (enrichedMovies, enrichedSeries) = applyImdbEnrichment(movies, series)
            val timestamp = ISO8601_FORMAT.format(Date())

            writeList(FILE_CHANNELS, listAdapter(LiveStream::class.java), channels)
            writeList(FILE_MOVIES, listAdapter(VodStream::class.java), enrichedMovies)
            writeList(FILE_SERIES, listAdapter(Series::class.java), enrichedSeries)

            val categoryAdapter = listAdapter(Category::class.java)
            writeList(FILE_LIVE_CATEGORIES, categoryAdapter, liveCategories)
            writeList(FILE_VOD_CATEGORIES, categoryAdapter, vodCategories)
            writeList(FILE_SERIES_CATEGORIES, categoryAdapter, seriesCategories)

            sharedPreferences.edit {
                putString(KEY_CACHE_TIMESTAMP, timestamp)
                putInt(KEY_CACHE_CHANNELS_COUNT, channels.size)
                putInt(KEY_CACHE_MOVIES_COUNT, enrichedMovies.size)
                putInt(KEY_CACHE_SERIES_COUNT, enrichedSeries.size)
            }
            memoizedCache = CachedData(channels, enrichedMovies, enrichedSeries, liveCategories, vodCategories, seriesCategories)
            android.util.Log.d("DataCacheService", "Cache saved successfully: $timestamp | Channels: ${channels.size}, Movies: ${enrichedMovies.size}, Series: ${enrichedSeries.size}")
            return true
        } catch (e: Exception) {
            // Log error but don't crash
            reportError("DataCacheService", "Failed to save cache", e)
            return false
        }
    }

    /**
     * Reichert Movies/Series einmalig vor dem Schreiben mit IMDb-Daten an (Chris-Spec 30.10.):
     * Genres kommen exklusiv aus IMDb, Xtream-Ratings werden abgeloest. Ist der Enrichment-Service
     * nicht verfuegbar (z.B. noch kein Download), bleiben die Xtream-Rohwerte unveraendert --
     * nur ein versuchter Lookup ohne Treffer nullt die Felder.
     */
    private fun applyImdbEnrichment(
        movies: List<VodStream>,
        series: List<Series>
    ): Pair<List<VodStream>, List<Series>> {
        // Blockiert den Aufrufer fuer Download + Entpacken der IMDb-DB: saveCachedData() darf
        // nie auf Main laufen. Offen: Umstellung auf suspend (Folgeauftrag "ANR-Glaettung").
        runBlocking { imdbEnrichmentService.ensureLoaded() }

        if (!imdbEnrichmentService.isAvailable) {
            android.util.Log.d("DataCacheService", "IMDb enrichment unavailable, keeping Xtream genre/rating values")
            return movies to series
        }

        val enrichedMovies = movies.map { movie ->
            val enrichment = imdbEnrichmentService.lookup(movie.name)
            movie.copy(
                genre = enrichment?.genres?.joinToString(", "),
                rating = enrichment?.rating?.let { String.format(Locale.US, "%.1f", it) },
                rating5Based = enrichment?.rating?.let { it / 2.0 }
            )
        }
        val enrichedSeries = series.map { item ->
            val enrichment = imdbEnrichmentService.lookup(item.name)
            item.copy(
                genre = enrichment?.genres?.joinToString(", "),
                rating = enrichment?.rating?.let { String.format(Locale.US, "%.1f", it) },
                rating5Based = enrichment?.rating?.let { it / 2.0 }
            )
        }
        return enrichedMovies to enrichedSeries
    }

    private fun <T> listAdapter(itemClass: Class<T>): JsonAdapter<List<T>> =
        moshi.adapter(Types.newParameterizedType(List::class.java, itemClass))

    /** Streams [items] to a temp file and renames it into place, so readers never see a partial file. */
    private fun <T> writeList(fileName: String, adapter: JsonAdapter<List<T>>, items: List<T>) {
        cacheDir.mkdirs()
        val tmp = File(cacheDir, "$fileName.tmp")
        val target = File(cacheDir, fileName)
        try {
            tmp.sink().buffer().use { sink -> adapter.toJson(sink, items) }
        } catch (t: Throwable) {
            if (!tmp.delete()) tmp.deleteOnExit()
            throw t
        }
        if (!tmp.renameTo(target)) {
            if (!tmp.delete()) tmp.deleteOnExit()
            throw IOException("rename failed: $fileName")
        }
    }

    private fun <T> readList(fileName: String, adapter: JsonAdapter<List<T>>): List<T>? {
        val file = File(cacheDir, fileName)
        if (!file.exists()) return null
        return file.source().buffer().use { adapter.fromJson(it) }
    }

    /**
     * Internal helper function that loads cached data from the cache files.
     * This contains the common logic used by both loadCachedData() and loadCachedDataAsync().
     */
    private fun loadCachedDataInternal(): CachedData? {
        memoizedCache?.let { return it }
        try {
            val channels = readList(FILE_CHANNELS, listAdapter(LiveStream::class.java)) ?: return null
            val movies = readList(FILE_MOVIES, listAdapter(VodStream::class.java)) ?: return null
            val series = readList(FILE_SERIES, listAdapter(Series::class.java)) ?: return null

            val categoryAdapter = listAdapter(Category::class.java)
            val liveCategories = readList(FILE_LIVE_CATEGORIES, categoryAdapter) ?: emptyList()
            val vodCategories = readList(FILE_VOD_CATEGORIES, categoryAdapter) ?: emptyList()
            val seriesCategories = readList(FILE_SERIES_CATEGORIES, categoryAdapter) ?: emptyList()

            val data = CachedData(
                channels = channels,
                movies = movies,
                series = series,
                liveCategories = liveCategories,
                vodCategories = vodCategories,
                seriesCategories = seriesCategories
            )
            memoizedCache = data
            return data
        } catch (e: Exception) {
            reportError("DataCacheService", "Failed to load cache", e)
            return null
        }
    }

    fun loadCachedData(): CachedData? {
        return loadCachedDataInternal()
    }

    /**
     * Loads cached data asynchronously on a background thread.
     * Use this instead of loadCachedData() to avoid blocking the main thread.
     */
    suspend fun loadCachedDataAsync(): CachedData? = withContext(ioDispatcher) {
        loadCachedDataInternal()
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

    fun clearCache() {
        memoizedCache = null
        sharedPreferences.edit { clear() }
        cacheDir.deleteRecursively()
    }

    /**
     * Generic helper to load specific items by their IDs from a cached JSON list.
     * This is more memory-efficient than loading all items when only a few are needed.
     *
     * @param T The type of item to load (e.g., LiveStream, VodStream, Series).
     * @param fileName The cache file holding the JSON list.
     * @param ids The list of IDs to load.
     * @param itemClass The class of the item, for Moshi type creation.
     * @param idSelector A function to extract the ID from an item.
     * @return A list of items matching the IDs, or null if the cache is invalid.
     */
    private suspend fun <T : Any> loadItemsByIds(
        fileName: String,
        ids: List<Int>,
        itemClass: Class<T>,
        idSelector: (T) -> Int
    ): List<T>? = withContext(ioDispatcher) {
        if (ids.isEmpty()) {
            return@withContext emptyList()
        }

        try {
            val allItems = readList(fileName, listAdapter(itemClass)) ?: return@withContext null

            // Use a map for efficient O(1) lookup instead of filtering
            val itemsById = allItems.associateBy(idSelector)
            val filteredItems = ids.mapNotNull { itemsById[it] }

            android.util.Log.d("DataCacheService", "Loaded ${filteredItems.size} ${itemClass.simpleName}s from ${allItems.size} total (requested ${ids.size} IDs)")
            return@withContext filteredItems
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
        loadItemsByIds(FILE_CHANNELS, streamIds, LiveStream::class.java) { it.streamId }

    /**
     * Loads only specific movies by their stream IDs from cache.
     * This is more memory-efficient than loading all movies when only a few are needed.
     * 
     * @param streamIds List of stream IDs to load
     * @return List of VodStream objects matching the IDs, or null if cache is invalid
     */
    suspend fun loadMoviesByIds(streamIds: List<Int>): List<VodStream>? =
        loadItemsByIds(FILE_MOVIES, streamIds, VodStream::class.java) { it.streamId }

    /**
     * Loads only specific series by their series IDs from cache.
     * This is more memory-efficient than loading all series when only a few are needed.
     * 
     * @param seriesIds List of series IDs to load
     * @return List of Series objects matching the IDs, or null if cache is invalid
     */
    suspend fun loadSeriesByIds(seriesIds: List<Int>): List<Series>? =
        loadItemsByIds(FILE_SERIES, seriesIds, Series::class.java) { it.seriesId }
}
