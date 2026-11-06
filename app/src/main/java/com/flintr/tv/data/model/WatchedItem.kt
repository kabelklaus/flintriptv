package com.flintr.tv.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class WatchedItem(
    @Json(name = "contentType")
    val contentType: String, // "movie" or "series"
    @Json(name = "streamId")
    val streamId: Int?, // For movies
    @Json(name = "seriesId")
    val seriesId: Int?, // For episodes
    @Json(name = "episodeId")
    val episodeId: Int?, // For episodes
    @Json(name = "name")
    val name: String?,
    @Json(name = "genre")
    val genre: String?,
    @Json(name = "categoryName")
    val categoryName: String?,
    @Json(name = "watchedAt")
    val watchedAt: String // ISO8601 format timestamp
)
