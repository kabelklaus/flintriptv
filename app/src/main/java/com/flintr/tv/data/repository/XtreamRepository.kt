package com.flintr.tv.data.repository

import android.util.Log
import com.flintr.tv.data.api.XtreamApiService
import com.flintr.tv.data.local.CachedData
import com.flintr.tv.data.local.CredentialsRepository
import com.flintr.tv.data.local.DataCacheService
import com.flintr.tv.data.model.*
import com.squareup.moshi.Moshi
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Inject
import javax.inject.Singleton
import com.flintr.tv.utils.error.reportHandled

@Singleton
class XtreamRepository @Inject constructor(
    private val credentialsRepository: CredentialsRepository,
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi,
    private val cacheService: DataCacheService
) {
    companion object {
        const val ERROR_API_SERVICE_NOT_AVAILABLE = "API service not available. Please log in first."
        const val ERROR_NO_CACHE_AVAILABLE = "No cache available. Please load data first."
    }
    // Helper function to require API service or throw exception
    private fun requireApiService(): XtreamApiService {
        return getApiService() ?: throw Exception(ERROR_API_SERVICE_NOT_AVAILABLE)
    }
    
    // Lazy initialization of API service based on the current credentials
    @Synchronized
    private fun getApiService(): XtreamApiService? {
        val credentials = credentialsRepository.credentialsFlow.value
        if (credentials == null) {
            // Invalidate cached service if user logs out
            dynamicApiService = null
            dynamicApiServiceBaseUrl = null
            return null
        }
        
        val baseUrl = credentials.serverUrl.let { if (it.endsWith("/")) it else "$it/" }

        // Return cached service if it exists and baseUrl is the same
        if (dynamicApiService != null && dynamicApiServiceBaseUrl == baseUrl) {
            return dynamicApiService
        }
        
        // Create Retrofit dynamically
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
        
        dynamicApiService = retrofit.create(XtreamApiService::class.java)
        dynamicApiServiceBaseUrl = baseUrl
        return dynamicApiService
    }
    // In-memory cache (only for small data structures)
    private var cachedServerInfo: ServerInfo? = null
    private var cachedLiveCategories: List<Category>? = null
    private var cachedVodCategories: List<Category>? = null
    private var cachedSeriesCategories: List<Category>? = null
    // Note: Large lists (LiveStreams, VodStreams, Series) are not cached in-memory
    // to avoid OutOfMemoryError. They are only loaded from persistent cache when needed.
    
    // Caching for dynamically created API service
    private var dynamicApiService: XtreamApiService? = null
    private var dynamicApiServiceBaseUrl: String? = null

    suspend fun getServerInfo(username: String, password: String): Result<ServerInfo> {
        return try {
            val service = requireApiService()
            val response = service.getServerInfo(username, password)
            if (response.isSuccessful && response.body() != null) {
                cachedServerInfo = response.body()
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to get server info: ${response.code()}"))
            }
        } catch (e: Exception) {
            reportHandled("XtreamRepository.getServerInfo", e)
            Result.failure(e)
        }
    }

    suspend fun getLiveCategories(username: String, password: String): Result<List<Category>> {
        return try {
            if (cachedLiveCategories != null) {
                return Result.success(cachedLiveCategories!!)
            }

            val service = requireApiService()
            val response = service.getLiveCategories(username, password)
            if (response.isSuccessful && response.body() != null) {
                val categories = response.body()!!
                cachedLiveCategories = categories
                Result.success(categories)
            } else {
                Result.failure(Exception("Failed to get live categories: ${response.code()}"))
            }
        } catch (e: Exception) {
            reportHandled("XtreamRepository.getLiveCategories", e)
            Result.failure(e)
        }
    }

    /**
     * Generic helper function to retrieve cached data from persistent cache only.
     * In-memory caching for large lists is removed to prevent OutOfMemoryError.
     * @param getFromCachedData Lambda to extract the data from CachedData
     * @param categoryId Optional category ID to filter by
     * @param filterByCategory Lambda to filter data by categoryId (returns true if item matches categoryId)
     * @return Result containing the cached data or an error if no cache is available
     */
    private suspend inline fun <T> getCachedData(
        getFromCachedData: (CachedData) -> List<T>,
        categoryId: String? = null,
        filterByCategory: (T, String) -> Boolean = { _, _ -> true }
    ): Result<List<T>> {
        return try {
            // Only use persistent cache (no in-memory cache for large lists)
            val allData = if (cacheService.shouldUseCacheForBrowsing()) {
                val cachedData = cacheService.loadCachedDataAsync()
                if (cachedData != null) {
                    getFromCachedData(cachedData)
                } else {
                    null
                }
            } else {
                null
            }

            // If we have cached data, filter by categoryId if needed
            if (allData != null) {
                val filteredData = if (categoryId != null) {
                    allData.filter { filterByCategory(it, categoryId) }
                } else {
                    allData
                }
                return Result.success(filteredData)
            }

            // No cache available - return error (no API calls allowed)
            Result.failure(Exception(ERROR_NO_CACHE_AVAILABLE))
        } catch (e: Exception) {
            reportHandled("XtreamRepository.getLiveCategories", e)
            Result.failure(e)
        }
    }

    suspend fun getLiveStreams(username: String, password: String, categoryId: String? = null): Result<List<LiveStream>> {
        return getCachedData(
            getFromCachedData = { it.channels },
            categoryId = categoryId,
            filterByCategory = { stream, catId -> stream.categoryId == catId }
        )
    }

    suspend fun loadLiveStreamsFromApi(username: String, password: String): Result<List<LiveStream>> {
        return try {
            val service = requireApiService()
            val response = service.getLiveStreams(username, password)
            if (response.isSuccessful && response.body() != null) {
                val streams = response.body()!!
                // Don't cache in-memory to prevent OutOfMemoryError
                Result.success(streams)
            } else {
                Result.failure(Exception("Failed to get live streams: ${response.code()}"))
            }
        } catch (e: Exception) {
            reportHandled("XtreamRepository.loadLiveStreamsFromApi", e)
            Result.failure(e)
        }
    }

    suspend fun getVodCategories(username: String, password: String): Result<List<Category>> {
        return try {
            if (cachedVodCategories != null) {
                return Result.success(cachedVodCategories!!)
            }

            val service = requireApiService()
            val response = service.getVodCategories(username, password)
            if (response.isSuccessful && response.body() != null) {
                val categories = response.body()!!
                cachedVodCategories = categories
                Result.success(categories)
            } else {
                Result.failure(Exception("Failed to get VOD categories: ${response.code()}"))
            }
        } catch (e: Exception) {
            reportHandled("XtreamRepository.getVodCategories", e)
            Result.failure(e)
        }
    }

    suspend fun getVodStreams(username: String, password: String, categoryId: String? = null): Result<List<VodStream>> {
        return getCachedData(
            getFromCachedData = { it.movies },
            categoryId = categoryId,
            filterByCategory = { stream, catId -> stream.categoryId == catId }
        )
    }

    suspend fun loadVodStreamsFromApi(username: String, password: String): Result<List<VodStream>> {
        return try {
            val service = requireApiService()
            val response = service.getVodStreams(username, password)
            if (response.isSuccessful && response.body() != null) {
                val streams = response.body()!!
                // Don't cache in-memory to prevent OutOfMemoryError
                Result.success(streams)
            } else {
                Result.failure(Exception("Failed to get VOD streams: ${response.code()}"))
            }
        } catch (e: Exception) {
            reportHandled("XtreamRepository.loadVodStreamsFromApi", e)
            Result.failure(e)
        }
    }

    suspend fun getSeriesCategories(username: String, password: String): Result<List<Category>> {
        return try {
            if (cachedSeriesCategories != null) {
                return Result.success(cachedSeriesCategories!!)
            }

            val service = requireApiService()
            val response = service.getSeriesCategories(username, password)
            if (response.isSuccessful && response.body() != null) {
                val categories = response.body()!!
                cachedSeriesCategories = categories
                Result.success(categories)
            } else {
                Result.failure(Exception("Failed to get series categories: ${response.code()}"))
            }
        } catch (e: Exception) {
            reportHandled("XtreamRepository.getSeriesCategories", e)
            Result.failure(e)
        }
    }

    suspend fun getSeries(username: String, password: String, categoryId: String? = null): Result<List<Series>> {
        return getCachedData(
            getFromCachedData = { it.series },
            categoryId = categoryId,
            filterByCategory = { series, catId -> series.categoryId == catId }
        )
    }

    suspend fun loadSeriesFromApi(username: String, password: String): Result<List<Series>> {
        return try {
            val service = requireApiService()
            val response = service.getSeries(username, password)
            if (response.isSuccessful && response.body() != null) {
                val series = response.body()!!
                // Don't cache in-memory to prevent OutOfMemoryError
                Result.success(series)
            } else {
                Result.failure(Exception("Failed to get series: ${response.code()}"))
            }
        } catch (e: Exception) {
            reportHandled("XtreamRepository.loadSeriesFromApi", e)
            Result.failure(e)
        }
    }

    suspend fun getSeriesInfo(username: String, password: String, seriesId: Int): Result<Series> {
        return getSeriesInfoWithRetry(username, password, seriesId)
    }

    private suspend fun getSeriesInfoWithRetry(
        username: String,
        password: String,
        seriesId: Int
    ): Result<Series> {
        val delays = listOf(0L, 500L, 1000L)
        var lastError: Exception? = null

        for ((attempt, delayMs) in delays.withIndex()) {
            if (delayMs > 0) {
                delay(delayMs)
                Log.d("XtreamRepository", "Retrying getSeriesInfo for seriesId=$seriesId (attempt ${attempt + 1})")
            }

            val result = executeSeriesInfoCall(username, password, seriesId, attempt, delays.size)
            when {
                result?.isSuccess == true -> {
                    if (attempt > 0) {
                        Log.d("XtreamRepository", "Successfully loaded series info for seriesId=$seriesId after ${attempt + 1} attempts")
                    }
                    return result
                }
                result == null -> {
                    lastError = Exception("Failed to get series info: temporary error")
                    continue // Retry
                }
                else -> return result // Non-retryable error
            }
        }

        return Result.failure(lastError ?: Exception("Failed to get series info after retries"))
    }

    private suspend fun executeSeriesInfoCall(
        username: String,
        password: String,
        seriesId: Int,
        attempt: Int,
        maxAttempts: Int
    ): Result<Series>? {
        return try {
            val service = requireApiService()
            val response = service.getSeriesInfo(username, password, "get_series_info", seriesId)

            if (!response.isSuccessful || response.body() == null) {
                val errorCode = response.code()
                return handleServerError(errorCode, seriesId, attempt, maxAttempts)
            }

            val seriesInfoResponse = response.body()!!
            val series = seriesInfoResponse.toSeries(seriesId)
            
            if (isSeriesEmpty(seriesInfoResponse)) {
                return handleEmptySeriesResponse(seriesId, attempt, maxAttempts)
            }

            return Result.success(series)
        } catch (e: Exception) {
            reportHandled(
                "XtreamRepository.executeSeriesInfoCall",
                e,
                mapOf("seriesId" to seriesId.toString(), "attempt" to attempt.toString())
            )
            // Network errors are retryable, other exceptions are not
            when (e) {
                is java.net.SocketTimeoutException,
                is java.net.ConnectException,
                is java.net.UnknownHostException,
                is java.io.IOException -> {
                    null // Signal for retry
                }
                else -> {
                    Result.failure(e) // Non-retryable error
                }
            }
        }
    }

    private fun isSeriesEmpty(seriesInfoResponse: SeriesInfoResponse): Boolean {
        return seriesInfoResponse.episodes.isNullOrEmpty() &&
               seriesInfoResponse.seasons.isNullOrEmpty()
    }

    private fun handleEmptySeriesResponse(
        seriesId: Int,
        attempt: Int,
        maxAttempts: Int
    ): Result<Series>? {
        if (attempt < maxAttempts - 1) {
            Log.w("XtreamRepository", "Empty series info response for seriesId=$seriesId, will retry (attempt ${attempt + 1}/$maxAttempts)")
            return null // Signal for retry
        }
        return Result.failure(Exception("Failed to get series info: empty response after $maxAttempts attempts"))
    }

    private fun handleServerError(
        errorCode: Int,
        seriesId: Int,
        attempt: Int,
        maxAttempts: Int
    ): Result<Series>? {
        if (errorCode in 500..599 && attempt < maxAttempts - 1) {
            Log.w("XtreamRepository", "Server error $errorCode for seriesId=$seriesId, will retry (attempt ${attempt + 1}/$maxAttempts)")
            return null // Signal for retry
        }
        return Result.failure(Exception("Failed to get series info: $errorCode"))
    }

    suspend fun getEpg(username: String, password: String, streamId: Int): Result<List<EpgProgram>> {
        return try {
            val service = requireApiService()
            val response = service.getShortEpg(username, password, "get_short_epg", streamId)
            if (response.isSuccessful && response.body() != null) {
                val programs = response.body()!!.values.flatten()
                Result.success(programs)
            } else {
                Result.failure(Exception("Failed to get EPG: ${response.code()}"))
            }
        } catch (e: Exception) {
            reportHandled("XtreamRepository.getEpg", e)
            Result.failure(e)
        }
    }

    suspend fun getEpgList(username: String, password: String, streamIds: List<Int>): Result<List<EpgProgram>> {
        return try {
            val service = requireApiService()
            val idsString = streamIds.joinToString(",")
            val response = service.getShortEpgList(username, password, "get_short_epg", idsString)
            if (response.isSuccessful && response.body() != null) {
                val programs = response.body()!!.values.flatten()
                Result.success(programs)
            } else {
                Result.failure(Exception("Failed to get EPG list: ${response.code()}"))
            }
        } catch (e: Exception) {
            reportHandled("XtreamRepository.getEpgList", e)
            Result.failure(e)
        }
    }

    fun clearCache() {
        cachedServerInfo = null
        cachedLiveCategories = null
        cachedVodCategories = null
        cachedSeriesCategories = null
        // Note: Large lists are no longer cached in-memory
        dynamicApiService = null
        dynamicApiServiceBaseUrl = null
    }
}
