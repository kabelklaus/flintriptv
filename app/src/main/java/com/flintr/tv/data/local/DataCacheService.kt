package com.flintr.tv.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.flintr.tv.data.model.*
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okio.Buffer
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
    @Named("IO") private val ioDispatcher: CoroutineDispatcher
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

    companion object {
        private const val KEY_CACHE_TIMESTAMP = "iptv_cache_timestamp"
        private const val KEY_CACHE_CHANNELS = "iptv_cache_channels"
        private const val KEY_CACHE_MOVIES = "iptv_cache_movies"
        private const val KEY_CACHE_SERIES = "iptv_cache_series"
        private const val KEY_CACHE_LIVE_CATEGORIES = "iptv_cache_live_categories"
        private const val KEY_CACHE_VOD_CATEGORIES = "iptv_cache_vod_categories"
        private const val KEY_CACHE_SERIES_CATEGORIES = "iptv_cache_series_categories"
        private const val KEY_CACHE_CHANNELS_COUNT = "iptv_cache_channels_count"
        private const val KEY_CACHE_MOVIES_COUNT = "iptv_cache_movies_count"
        private const val KEY_CACHE_SERIES_COUNT = "iptv_cache_series_count"
        private const val KEY_FALLBACK_COOLDOWN_TIMESTAMP = "iptv_fallback_cooldown_timestamp"
        private const val CACHE_VALIDITY_DURATION = 24 * 60 * 60 * 1000L // 24 hours in milliseconds
        private val ISO8601_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)

        internal fun hasUsableFallbackCacheData(
            moshi: Moshi,
            channelsJson: String?,
            moviesJson: String?,
            seriesJson: String?,
            channelsCount: Int,
            moviesCount: Int,
            seriesCount: Int
        ): Boolean {
            if (channelsJson.isNullOrBlank() || moviesJson.isNullOrBlank() || seriesJson.isNullOrBlank()) {
                return false
            }

            if (channelsCount > 0 && moviesCount > 0 && seriesCount > 0) {
                return true
            }

            return try {
                val liveAdapter = moshi.adapter(LiveStream::class.java)
                if (!isNonEmptyValidArray(channelsJson, liveAdapter)) return false

                val vodAdapter = moshi.adapter(VodStream::class.java)
                if (!isNonEmptyValidArray(moviesJson, vodAdapter)) return false

                val seriesAdapter = moshi.adapter(Series::class.java)
                isNonEmptyValidArray(seriesJson, seriesAdapter)
            } catch (e: Exception) {
                reportControlFlow("DataCacheService.isNonEmptyValidSeriesArray", e)
                false
            }
        }

        private fun <T> isNonEmptyValidArray(
            json: String,
            adapter: JsonAdapter<T>
        ): Boolean {
            val reader = JsonReader.of(Buffer().writeUtf8(json))
            reader.beginArray()

            var foundItem = false
            while (reader.hasNext()) {
                if (adapter.fromJson(reader) == null) {
                    return false
                }
                foundItem = true
            }

            reader.endArray()
            return foundItem
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
        val channelsJson = sharedPreferences.getString(KEY_CACHE_CHANNELS, null)
        val moviesJson = sharedPreferences.getString(KEY_CACHE_MOVIES, null)
        val seriesJson = sharedPreferences.getString(KEY_CACHE_SERIES, null)

        val channelsCount = sharedPreferences.getInt(KEY_CACHE_CHANNELS_COUNT, -1)
        val moviesCount = sharedPreferences.getInt(KEY_CACHE_MOVIES_COUNT, -1)
        val seriesCount = sharedPreferences.getInt(KEY_CACHE_SERIES_COUNT, -1)

        return hasUsableFallbackCacheData(
            moshi = moshi,
            channelsJson = channelsJson,
            moviesJson = moviesJson,
            seriesJson = seriesJson,
            channelsCount = channelsCount,
            moviesCount = moviesCount,
            seriesCount = seriesCount
        )
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
            val timestamp = ISO8601_FORMAT.format(Date())

            val listType = Types.newParameterizedType(List::class.java, LiveStream::class.java)
            val adapter = moshi.adapter<List<LiveStream>>(listType)
            val channelsJson = adapter.toJson(channels)

            val vodListType = Types.newParameterizedType(List::class.java, VodStream::class.java)
            val vodAdapter = moshi.adapter<List<VodStream>>(vodListType)
            val moviesJson = vodAdapter.toJson(movies)

            val seriesListType = Types.newParameterizedType(List::class.java, Series::class.java)
            val seriesAdapter = moshi.adapter<List<Series>>(seriesListType)
            val seriesJson = seriesAdapter.toJson(series)

            val categoryListType = Types.newParameterizedType(List::class.java, Category::class.java)
            val categoryAdapter = moshi.adapter<List<Category>>(categoryListType)
            val liveCategoriesJson = categoryAdapter.toJson(liveCategories)
            val vodCategoriesJson = categoryAdapter.toJson(vodCategories)
            val seriesCategoriesJson = categoryAdapter.toJson(seriesCategories)

            sharedPreferences.edit {
                putString(KEY_CACHE_TIMESTAMP, timestamp)
                putString(KEY_CACHE_CHANNELS, channelsJson)
                putString(KEY_CACHE_MOVIES, moviesJson)
                putString(KEY_CACHE_SERIES, seriesJson)
                putString(KEY_CACHE_LIVE_CATEGORIES, liveCategoriesJson)
                putString(KEY_CACHE_VOD_CATEGORIES, vodCategoriesJson)
                putString(KEY_CACHE_SERIES_CATEGORIES, seriesCategoriesJson)
                putInt(KEY_CACHE_CHANNELS_COUNT, channels.size)
                putInt(KEY_CACHE_MOVIES_COUNT, movies.size)
                putInt(KEY_CACHE_SERIES_COUNT, series.size)
            }
            memoizedCache = CachedData(channels, movies, series, liveCategories, vodCategories, seriesCategories)
            android.util.Log.d("DataCacheService", "Cache saved successfully: $timestamp | Channels: ${channels.size}, Movies: ${movies.size}, Series: ${series.size}")
            return true
        } catch (e: Exception) {
            // Log error but don't crash
            reportError("DataCacheService", "Failed to save cache", e)
            return false
        }
    }

    /**
     * Internal helper function that loads cached data from SharedPreferences.
     * This contains the common logic used by both loadCachedData() and loadCachedDataAsync().
     */
    private fun loadCachedDataInternal(): CachedData? {
        memoizedCache?.let { return it }
        try {
            val channelsJson = sharedPreferences.getString(KEY_CACHE_CHANNELS, null)
            val moviesJson = sharedPreferences.getString(KEY_CACHE_MOVIES, null)
            val seriesJson = sharedPreferences.getString(KEY_CACHE_SERIES, null)
            val liveCategoriesJson = sharedPreferences.getString(KEY_CACHE_LIVE_CATEGORIES, null)
            val vodCategoriesJson = sharedPreferences.getString(KEY_CACHE_VOD_CATEGORIES, null)
            val seriesCategoriesJson = sharedPreferences.getString(KEY_CACHE_SERIES_CATEGORIES, null)

            if (channelsJson.isNullOrBlank() || moviesJson.isNullOrBlank() || seriesJson.isNullOrBlank()) {
                return null
            }

            val listType = Types.newParameterizedType(List::class.java, LiveStream::class.java)
            val adapter = moshi.adapter<List<LiveStream>>(listType)
            val channels = adapter.fromJson(channelsJson) ?: return null

            val vodListType = Types.newParameterizedType(List::class.java, VodStream::class.java)
            val vodAdapter = moshi.adapter<List<VodStream>>(vodListType)
            val movies = vodAdapter.fromJson(moviesJson) ?: return null

            val seriesListType = Types.newParameterizedType(List::class.java, Series::class.java)
            val seriesAdapter = moshi.adapter<List<Series>>(seriesListType)
            val series = seriesAdapter.fromJson(seriesJson) ?: return null

            val categoryListType = Types.newParameterizedType(List::class.java, Category::class.java)
            val categoryAdapter = moshi.adapter<List<Category>>(categoryListType)
            val liveCategories = liveCategoriesJson?.let { categoryAdapter.fromJson(it) } ?: emptyList()
            val vodCategories = vodCategoriesJson?.let { categoryAdapter.fromJson(it) } ?: emptyList()
            val seriesCategories = seriesCategoriesJson?.let { categoryAdapter.fromJson(it) } ?: emptyList()

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

        // Fallback: check JSON strings exist (for old cache without count keys)
        val channelsJson = sharedPreferences.getString(KEY_CACHE_CHANNELS, null)
        val moviesJson = sharedPreferences.getString(KEY_CACHE_MOVIES, null)
        val seriesJson = sharedPreferences.getString(KEY_CACHE_SERIES, null)
        val hasJson = !channelsJson.isNullOrBlank() && !moviesJson.isNullOrBlank() && !seriesJson.isNullOrBlank()
        android.util.Log.d("DataCacheService", "hasCacheData: json fallback=" +
            "channels=${channelsJson?.length ?: "null"}, movies=${moviesJson?.length ?: "null"}, series=${seriesJson?.length ?: "null"} -> $hasJson")
        return hasJson
    }

    fun hasUsableFallbackCache(): Boolean {
        return hasUsableFallbackCacheData(
            moshi = moshi,
            channelsJson = sharedPreferences.getString(KEY_CACHE_CHANNELS, null),
            moviesJson = sharedPreferences.getString(KEY_CACHE_MOVIES, null),
            seriesJson = sharedPreferences.getString(KEY_CACHE_SERIES, null),
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
    }

    /**
     * Generic helper to load specific items by their IDs from a cached JSON list.
     * This is more memory-efficient than loading all items when only a few are needed.
     *
     * @param T The type of item to load (e.g., LiveStream, VodStream, Series).
     * @param cacheKey The SharedPreferences key for the JSON list.
     * @param ids The list of IDs to load.
     * @param itemClass The class of the item, for Moshi type creation.
     * @param idSelector A function to extract the ID from an item.
     * @return A list of items matching the IDs, or null if the cache is invalid.
     */
    private suspend fun <T : Any> loadItemsByIds(
        cacheKey: String,
        ids: List<Int>,
        itemClass: Class<T>,
        idSelector: (T) -> Int
    ): List<T>? = withContext(ioDispatcher) {
        if (ids.isEmpty()) {
            return@withContext emptyList()
        }

        try {
            val json = sharedPreferences.getString(cacheKey, null)
            if (json.isNullOrBlank()) {
                return@withContext null
            }

            val listType = Types.newParameterizedType(List::class.java, itemClass)
            val adapter = moshi.adapter<List<T>>(listType)
            val allItems = adapter.fromJson(json) ?: return@withContext null

            // Use a map for efficient O(1) lookup instead of filtering
            val itemsById = allItems.associateBy(idSelector)
            val filteredItems = ids.mapNotNull { itemsById[it] }

            android.util.Log.d("DataCacheService", "Loaded ${filteredItems.size} ${itemClass.simpleName}s from ${allItems.size} total (requested ${ids.size} IDs)")
            return@withContext filteredItems
        } catch (e: Exception) {
            reportError("DataCacheService", "Failed to load ${itemClass.simpleName}s by IDs for key $cacheKey", e)
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
        loadItemsByIds(KEY_CACHE_CHANNELS, streamIds, LiveStream::class.java) { it.streamId }

    /**
     * Loads only specific movies by their stream IDs from cache.
     * This is more memory-efficient than loading all movies when only a few are needed.
     * 
     * @param streamIds List of stream IDs to load
     * @return List of VodStream objects matching the IDs, or null if cache is invalid
     */
    suspend fun loadMoviesByIds(streamIds: List<Int>): List<VodStream>? =
        loadItemsByIds(KEY_CACHE_MOVIES, streamIds, VodStream::class.java) { it.streamId }

    /**
     * Loads only specific series by their series IDs from cache.
     * This is more memory-efficient than loading all series when only a few are needed.
     * 
     * @param seriesIds List of series IDs to load
     * @return List of Series objects matching the IDs, or null if cache is invalid
     */
    suspend fun loadSeriesByIds(seriesIds: List<Int>): List<Series>? =
        loadItemsByIds(KEY_CACHE_SERIES, seriesIds, Series::class.java) { it.seriesId }
}
