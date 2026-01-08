package com.flintr.tv.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ServerInfo(
    @Json(name = "server_info")
    val serverInfo: ServerInfoDetails?,
    @Json(name = "user_info")
    val userInfo: UserInfo?
)

@JsonClass(generateAdapter = true)
data class ServerInfoDetails(
    @Json(name = "url")
    val url: String?,
    @Json(name = "port")
    val port: String?,
    @Json(name = "https_port")
    val httpsPort: String?,
    @Json(name = "server_protocol")
    val serverProtocol: String?,
    @Json(name = "rtmp_port")
    val rtmpPort: String?,
    @Json(name = "timezone")
    val timezone: String?,
    @Json(name = "timestamp_now")
    val timestampNow: Long?,
    @Json(name = "time_now")
    val timeNow: String?
)

@JsonClass(generateAdapter = true)
data class UserInfo(
    @Json(name = "username")
    val username: String?,
    @Json(name = "password")
    val password: String?,
    @Json(name = "message")
    val message: String?,
    @Json(name = "auth")
    val auth: Int?,
    @Json(name = "status")
    val status: String?,
    @Json(name = "exp_date")
    val expDate: String?,
    @Json(name = "is_trial")
    val isTrial: String?,
    @Json(name = "active_cons")
    val activeCons: String?,
    @Json(name = "created_at")
    val createdAt: String?,
    @Json(name = "max_connections")
    val maxConnections: String?,
    @Json(name = "allowed_output_formats")
    val allowedOutputFormats: List<String>?
)

