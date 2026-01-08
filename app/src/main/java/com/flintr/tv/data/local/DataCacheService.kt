package com.flintr.tv.data.local

import android.content.Context
import android.content.SharedPreferences
import com.flintr.tv.data.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

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
    private val moshi: Moshi
) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        "iptv_cache_prefs",
        Context.MODE_PRIVATE
    )

    companion object {
        private const val KEY_CACHE_TIMESTAMP = "iptv_cache_timestamp"
        private const val KEY_CACHE_CHANNELS = "iptv_cache_channels"
        private const val KEY_CACHE_MOVIES = "iptv_cache_movies"
        private const val KEY_CACHE_SERIES = "iptv_cache_series"
        private const val KEY_CACHE_LIVE_CATEGORIES = "iptv_cache_live_categories"
        private const val KEY_CACHE_VOD_CATEGORIES = "iptv_cache_vod_categories"
        private const val KEY_CACHE_SERIES_CATEGORIES = "iptv_cache_series_categories"
        private const val CACHE_VALIDITY_DURATION = 24 * 60 * 60 * 1000L // 24 hours in milliseconds
        private val ISO8601_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
    }

    fun isCacheValid(): Boolean {
        val timestampString = sharedPreferences.getString(KEY_CACHE_TIMESTAMP, null)
            ?: return false

        val timestamp = try {
            ISO8601_FORMAT.parse(timestampString)?.time ?: return false
        } catch (e: Exception) {
            return false
        }

        val now = System.currentTimeMillis()
        val age = now - timestamp

        if (age > CACHE_VALIDITY_DURATION) {
            return false
        }

        // Check if all required data exists and is not empty
        val channelsJson = sharedPreferences.getString(KEY_CACHE_CHANNELS, null)
        val moviesJson = sharedPreferences.getString(KEY_CACHE_MOVIES, null)
        val seriesJson = sharedPreferences.getString(KEY_CACHE_SERIES, null)

        if (channelsJson.isNullOrBlank() || moviesJson.isNullOrBlank() || seriesJson.isNullOrBlank()) {
            return false
        }

        // Try to parse to verify they're valid JSON and not empty
        try {
            val listType = Types.newParameterizedType(List::class.java, LiveStream::class.java)
            val adapter = moshi.adapter<List<LiveStream>>(listType)
            val channels = adapter.fromJson(channelsJson)
            if (channels.isNullOrEmpty()) return false

            val vodListType = Types.newParameterizedType(List::class.java, VodStream::class.java)
            val vodAdapter = moshi.adapter<List<VodStream>>(vodListType)
            val movies = vodAdapter.fromJson(moviesJson)
            if (movies.isNullOrEmpty()) return false

            val seriesListType = Types.newParameterizedType(List::class.java, Series::class.java)
            val seriesAdapter = moshi.adapter<List<Series>>(seriesListType)
            val series = seriesAdapter.fromJson(seriesJson)
            if (series.isNullOrEmpty()) return false
        } catch (e: Exception) {
            return false
        }

        return true
    }

    fun saveCachedData(
        channels: List<LiveStream>,
        movies: List<VodStream>,
        series: List<Series>,
        liveCategories: List<Category>,
        vodCategories: List<Category>,
        seriesCategories: List<Category>
    ) {
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

            sharedPreferences.edit()
                .putString(KEY_CACHE_TIMESTAMP, timestamp)
                .putString(KEY_CACHE_CHANNELS, channelsJson)
                .putString(KEY_CACHE_MOVIES, moviesJson)
                .putString(KEY_CACHE_SERIES, seriesJson)
                .putString(KEY_CACHE_LIVE_CATEGORIES, liveCategoriesJson)
                .putString(KEY_CACHE_VOD_CATEGORIES, vodCategoriesJson)
                .putString(KEY_CACHE_SERIES_CATEGORIES, seriesCategoriesJson)
                .apply()
        } catch (e: Exception) {
            // Log error but don't crash
            android.util.Log.e("DataCacheService", "Failed to save cache", e)
        }
    }

    fun loadCachedData(): CachedData? {
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

            return CachedData(
                channels = channels,
                movies = movies,
                series = series,
                liveCategories = liveCategories,
                vodCategories = vodCategories,
                seriesCategories = seriesCategories
            )
        } catch (e: Exception) {
            android.util.Log.e("DataCacheService", "Failed to load cache", e)
            return null
        }
    }

    fun clearCache() {
        sharedPreferences.edit().clear().apply()
    }
}

