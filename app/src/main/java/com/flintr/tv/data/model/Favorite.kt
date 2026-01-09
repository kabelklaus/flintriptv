package com.flintr.tv.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.time.Instant

enum class FavoriteType {
    @Json(name = "live")
    LIVE,
    @Json(name = "movie")
    MOVIE,
    @Json(name = "series")
    SERIES
}

@JsonClass(generateAdapter = true)
data class Favorite(
    @Json(name = "id")
    val id: String,
    @Json(name = "type")
    val type: FavoriteType,
    @Json(name = "streamId")
    val streamId: Int,
    @Json(name = "name")
    val name: String,
    @Json(name = "metadata")
    val metadata: Map<String, String?>? = null,
    @Json(name = "addedAt")
    val addedAt: String // ISO8601 format
) {
    companion object {
        fun create(
            id: String,
            type: FavoriteType,
            streamId: Int,
            name: String,
            metadata: Map<String, String?>? = null
        ): Favorite {
            return Favorite(
                id = id,
                type = type,
                streamId = streamId,
                name = name,
                metadata = metadata,
                addedAt = Instant.now().toString()
            )
        }
    }
}
