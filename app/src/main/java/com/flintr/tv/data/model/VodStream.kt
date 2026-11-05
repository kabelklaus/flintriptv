package com.flintr.tv.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.net.URLEncoder

@JsonClass(generateAdapter = true)
data class VodStream(
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
    @Json(name = "rating")
    val rating: String?,
    @Json(name = "rating_5based")
    val rating5Based: Double?,
    @Json(name = "added")
    val added: String?,
    @Json(name = "category_id")
    val categoryId: String?,
    @Json(name = "category_name")
    val categoryName: String?,
    @Json(name = "container_extension")
    val containerExtension: String?,
    @Json(name = "custom_sid")
    val customSid: String?,
    @Json(name = "direct_source")
    val directSource: String?,
    @Json(name = "releaseDate")
    val releaseDate: String?,
    @Json(name = "duration")
    val duration: String?,
    @Json(name = "plot")
    val plot: String?,
    @Json(name = "cast")
    val cast: String?,
    @Json(name = "director")
    val director: String?,
    @Json(name = "genre")
    val genre: String?,
    @Json(name = "backdrop_path")
    val backdropPath: List<String>?,
    @Json(name = "youtube_trailer")
    val youtubeTrailer: String?,
    @Json(name = "cover")
    val cover: String?,
    @Json(name = "tmdb_id")
    val tmdbId: String?
) {
    fun getStreamUrl(baseUrl: String, username: String, password: String): String {
        val extension = containerExtension ?: "mkv"
        return "$baseUrl/movie/$username/$password/$streamId.$extension"
    }

    fun toFavoriteId(): String {
        val encodedName = URLEncoder.encode(name, "UTF-8")
        return "movie:$streamId:$encodedName"
    }

    fun toWatchlistEntry(metadata: Map<String, String?>? = null): WatchlistEntry {
        val metadataWithCover = metadata?.toMutableMap() ?: mutableMapOf()
        cover?.let { metadataWithCover["cover"] = it }
        streamIcon?.let { metadataWithCover["streamIcon"] = it }
        return WatchlistEntry.create(
            id = toFavoriteId(),
            type = WatchlistEntryType.MOVIE,
            streamId = streamId,
            name = name,
            metadata = metadataWithCover
        )
    }
}

