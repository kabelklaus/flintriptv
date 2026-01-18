package com.flintr.tv.data.api

import com.flintr.tv.data.model.*
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface XtreamApiService {
    
    @GET("player_api.php")
    suspend fun getServerInfo(
        @Query("username") username: String,
        @Query("password") password: String
    ): Response<com.flintr.tv.data.model.ServerInfo>
    
    @GET("player_api.php")
    suspend fun getLiveCategories(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_live_categories"
    ): Response<List<Category>>
    
    @GET("player_api.php")
    suspend fun getLiveStreams(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_live_streams"
    ): Response<List<LiveStream>>
    
    @GET("player_api.php")
    suspend fun getLiveStreamsByCategory(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_live_streams",
        @Query("category_id") categoryId: String
    ): Response<List<LiveStream>>
    
    @GET("player_api.php")
    suspend fun getVodCategories(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_vod_categories"
    ): Response<List<Category>>
    
    @GET("player_api.php")
    suspend fun getVodStreams(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_vod_streams"
    ): Response<List<VodStream>>
    
    @GET("player_api.php")
    suspend fun getVodStreamsByCategory(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_vod_streams",
        @Query("category_id") categoryId: String
    ): Response<List<VodStream>>
    
    @GET("player_api.php")
    suspend fun getSeriesCategories(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_series_categories"
    ): Response<List<Category>>
    
    @GET("player_api.php")
    suspend fun getSeries(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_series"
    ): Response<List<Series>>
    
    @GET("player_api.php")
    suspend fun getSeriesByCategory(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_series",
        @Query("category_id") categoryId: String
    ): Response<List<Series>>
    
    @GET("player_api.php")
    suspend fun getSeriesInfo(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_series_info",
        @Query("series_id") seriesId: Int
    ): Response<com.flintr.tv.data.model.SeriesInfoResponse>
    
    @GET("player_api.php")
    suspend fun getShortEpg(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_short_epg",
        @Query("stream_id") streamId: Int
    ): Response<Map<String, List<EpgProgram>>>
    
    @GET("player_api.php")
    suspend fun getShortEpgList(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_short_epg",
        @Query("stream_ids") streamIds: String
    ): Response<Map<String, List<EpgProgram>>>
}
