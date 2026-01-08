package com.flintr.tv.data.repository

import com.flintr.tv.data.api.XtreamApiService
import com.flintr.tv.data.local.CredentialsRepository
import com.flintr.tv.data.model.*
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.annotation.Nullable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XtreamRepository @Inject constructor(
    @Nullable private val apiService: XtreamApiService?,
    private val credentialsRepository: CredentialsRepository,
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi
) {
    companion object {
        const val ERROR_API_SERVICE_NOT_AVAILABLE = "API service not available. Please log in first."
    }
    // Helper function to require API service or throw exception
    private fun requireApiService(): XtreamApiService {
        return getApiService() ?: throw Exception(ERROR_API_SERVICE_NOT_AVAILABLE)
    }
    
    // Lazy initialization of API service if not provided
    private fun getApiService(): XtreamApiService? {
        if (apiService != null) {
            return apiService
        }
        
        val credentials = credentialsRepository.getCredentials()
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
    // In-memory cache
    private var cachedServerInfo: ServerInfo? = null
    private var cachedLiveCategories: List<Category>? = null
    private var cachedLiveStreams: List<LiveStream>? = null
    private var cachedVodCategories: List<Category>? = null
    private var cachedVodStreams: List<VodStream>? = null
    private var cachedSeriesCategories: List<Category>? = null
    private var cachedSeries: List<Series>? = null
    
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
            Result.failure(e)
        }
    }

    suspend fun getLiveStreams(username: String, password: String, categoryId: String? = null): Result<List<LiveStream>> {
        return try {
            val service = requireApiService()
            val response = if (categoryId != null) {
                service.getLiveStreamsByCategory(username, password, "get_live_streams", categoryId)
            } else {
                if (cachedLiveStreams != null) {
                    return Result.success(cachedLiveStreams!!)
                }
                service.getLiveStreams(username, password)
            }

            if (response.isSuccessful && response.body() != null) {
                val streams = response.body()!!
                if (categoryId == null) {
                    cachedLiveStreams = streams
                }
                Result.success(streams)
            } else {
                Result.failure(Exception("Failed to get live streams: ${response.code()}"))
            }
        } catch (e: Exception) {
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
            Result.failure(e)
        }
    }

    suspend fun getVodStreams(username: String, password: String, categoryId: String? = null): Result<List<VodStream>> {
        return try {
            val service = requireApiService()
            val response = if (categoryId != null) {
                service.getVodStreamsByCategory(username, password, "get_vod_streams", categoryId)
            } else {
                if (cachedVodStreams != null) {
                    return Result.success(cachedVodStreams!!)
                }
                service.getVodStreams(username, password)
            }

            if (response.isSuccessful && response.body() != null) {
                val streams = response.body()!!
                if (categoryId == null) {
                    cachedVodStreams = streams
                }
                Result.success(streams)
            } else {
                Result.failure(Exception("Failed to get VOD streams: ${response.code()}"))
            }
        } catch (e: Exception) {
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
            Result.failure(e)
        }
    }

    suspend fun getSeries(username: String, password: String, categoryId: String? = null): Result<List<Series>> {
        return try {
            val service = requireApiService()
            val response = if (categoryId != null) {
                service.getSeriesByCategory(username, password, "get_series", categoryId)
            } else {
                if (cachedSeries != null) {
                    return Result.success(cachedSeries!!)
                }
                service.getSeries(username, password)
            }

            if (response.isSuccessful && response.body() != null) {
                val series = response.body()!!
                if (categoryId == null) {
                    cachedSeries = series
                }
                Result.success(series)
            } else {
                Result.failure(Exception("Failed to get series: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSeriesInfo(username: String, password: String, seriesId: Int): Result<Series> {
        return try {
            val service = requireApiService()
            val response = service.getSeriesInfo(username, password, "get_series_info", seriesId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to get series info: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
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
            Result.failure(e)
        }
    }

    fun clearCache() {
        cachedServerInfo = null
        cachedLiveCategories = null
        cachedLiveStreams = null
        cachedVodCategories = null
        cachedVodStreams = null
        cachedSeriesCategories = null
        cachedSeries = null
        dynamicApiService = null
        dynamicApiServiceBaseUrl = null
    }
}
