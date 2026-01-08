package com.flintr.tv.data.repository

import com.flintr.tv.data.api.XtreamApiService
import com.flintr.tv.data.model.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XtreamRepository @Inject constructor(
    private val apiService: XtreamApiService
) {
    // In-memory cache
    private var cachedServerInfo: ServerInfo? = null
    private var cachedLiveCategories: List<Category>? = null
    private var cachedLiveStreams: List<LiveStream>? = null
    private var cachedVodCategories: List<Category>? = null
    private var cachedVodStreams: List<VodStream>? = null
    private var cachedSeriesCategories: List<Category>? = null
    private var cachedSeries: List<Series>? = null

    suspend fun getServerInfo(username: String, password: String): Result<ServerInfo> {
        return try {
            val response = apiService.getServerInfo(username, password)
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

            val response = apiService.getLiveCategories(username, password)
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
            val response = if (categoryId != null) {
                apiService.getLiveStreamsByCategory(username, password, "get_live_streams", categoryId)
            } else {
                if (cachedLiveStreams != null) {
                    return Result.success(cachedLiveStreams!!)
                }
                apiService.getLiveStreams(username, password)
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

            val response = apiService.getVodCategories(username, password)
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
            val response = if (categoryId != null) {
                apiService.getVodStreamsByCategory(username, password, "get_vod_streams", categoryId)
            } else {
                if (cachedVodStreams != null) {
                    return Result.success(cachedVodStreams!!)
                }
                apiService.getVodStreams(username, password)
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

            val response = apiService.getSeriesCategories(username, password)
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
            val response = if (categoryId != null) {
                apiService.getSeriesByCategory(username, password, "get_series", categoryId)
            } else {
                if (cachedSeries != null) {
                    return Result.success(cachedSeries!!)
                }
                apiService.getSeries(username, password)
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
            val response = apiService.getSeriesInfo(username, password, "get_series_info", seriesId)
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
            val response = apiService.getShortEpg(username, password, "get_short_epg", streamId)
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
            val idsString = streamIds.joinToString(",")
            val response = apiService.getShortEpgList(username, password, "get_short_epg", idsString)
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
    }
}
