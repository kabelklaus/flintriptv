package com.flintr.tv.data.model

data class RatingEntry(
    val contentType: String,   // "movie" | "series"
    val contentId: Int,        // streamId (movie) bzw. seriesId (series)
    val title: String?,
    val rating: Int,           // 1 = thumbs up, 0 = thumbs down
    val ratedAt: String        // ISO-8601, Instant.now()
)

fun RatingEntry.toFirestoreMap(): Map<String, Any?> = hashMapOf(
    "contentType" to contentType,
    "contentId" to contentId,
    "title" to title,
    "rating" to rating,
    "ratedAt" to ratedAt
)
