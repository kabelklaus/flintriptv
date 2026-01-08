package com.flintr.tv.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LiveStream(
    @Json(name = "num")
    val num: Int?,
    @Json(name = "name")
    val name: String,
    @Json(name = "stream_type")
    val streamType: String?,
    @Json(name = "stream_id")
    val streamId: Int,
    @Json(name = "stream_icon")
    val streamIcon: String?,
    @Json(name = "epg_channel_id")
    val epgChannelId: String?,
    @Json(name = "added")
    val added: String?,
    @Json(name = "category_id")
    val categoryId: String?,
    @Json(name = "category_name")
    val categoryName: String?,
    @Json(name = "custom_sid")
    val customSid: String?,
    @Json(name = "tv_archive")
    val tvArchive: Int?,
    @Json(name = "direct_source")
    val directSource: String?,
    @Json(name = "tv_archive_duration")
    val tvArchiveDuration: Int?
) {
    fun getStreamUrl(baseUrl: String, username: String, password: String): String {
        return "$baseUrl/live/$username/$password/$streamId.m3u8"
    }
}
