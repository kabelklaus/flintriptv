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
data class WatchlistEntry(
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
        ): WatchlistEntry {
            return WatchlistEntry(
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

/**
 * Converts a WatchlistEntry to a Firestore-compatible Map format.
 */
fun WatchlistEntry.toFirestoreMap(): Map<String, Any> {
    // wire format stable since v2.x, do not rename keys
    return hashMapOf(
        "id" to id,
        "type" to type.name,
        "streamId" to streamId,
        "name" to name,
        "metadata" to (metadata ?: emptyMap()),
        "addedAt" to addedAt
    )
}
