package com.flintr.tv.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GitHubRelease(
    @Json(name = "tag_name")
    val tagName: String,
    @Json(name = "name")
    val name: String,
    @Json(name = "prerelease")
    val prerelease: Boolean,
    @Json(name = "published_at")
    val publishedAt: String,
    @Json(name = "body")
    val body: String? = null,
    @Json(name = "assets")
    val assets: List<GitHubAsset>
)

@JsonClass(generateAdapter = true)
data class GitHubAsset(
    @Json(name = "name")
    val name: String,
    @Json(name = "url")
    val url: String? = null,
    @Json(name = "browser_download_url")
    val browserDownloadUrl: String,
    @Json(name = "content_type")
    val contentType: String,
    @Json(name = "size")
    val size: Long
)
