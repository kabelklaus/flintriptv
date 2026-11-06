package com.flintr.tv.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class EpgProgram(
    @Json(name = "id")
    val id: String,
    @Json(name = "epg_id")
    val epgId: String?,
    @Json(name = "title")
    val title: String,
    @Json(name = "lang")
    val lang: String?,
    @Json(name = "start")
    val start: String,
    @Json(name = "end")
    val end: String,
    @Json(name = "description")
    val description: String?,
    @Json(name = "channel_id")
    val channelId: String?,
    @Json(name = "start_timestamp")
    val startTimestamp: Long?,
    @Json(name = "stop_timestamp")
    val stopTimestamp: Long?,
    @Json(name = "now_playing")
    val nowPlaying: Boolean?,
    @Json(name = "has_archive")
    val hasArchive: Boolean?
)

