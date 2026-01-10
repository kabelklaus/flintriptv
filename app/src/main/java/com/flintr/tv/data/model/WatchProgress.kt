package com.flintr.tv.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class WatchProgress(
    @Json(name = "position")
    val position: Long, // Millisekunden
    @Json(name = "duration")
    val duration: Long, // Millisekunden
    @Json(name = "lastWatched")
    val lastWatched: String, // ISO8601 format
    @Json(name = "title")
    val title: String
)
