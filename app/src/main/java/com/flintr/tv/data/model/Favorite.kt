package com.flintr.tv.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.time.Instant

@JsonClass(generateAdapter = true)
data class Favorite(
    @Json(name = "id")
    val id: String,
    @Json(name = "type")
    val type: String, // "live", "movie", or "series"
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
            type: String,
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
