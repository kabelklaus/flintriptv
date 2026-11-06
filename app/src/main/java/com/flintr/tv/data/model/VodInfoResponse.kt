package com.flintr.tv.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi

/**
 * Response model for get_vod_info API call.
 * Mirrors SeriesInfoResponse's nesting: movie metadata lives under "info".
 */
@JsonClass(generateAdapter = false)
data class VodInfoResponse(
    @Json(name = "info")
    val info: VodInfo?
)

/**
 * Movie metadata from get_vod_info. Field names mirror the live API response.
 *
 * [name] is part of the probe and is needed to build the IMDb lookup key
 * (title + release year). Xtream [genre] and [rating] are parsed because the
 * panel delivers them, but display selection is IMDb-only and happens in the
 * detail mapper — never here.
 */
@JsonClass(generateAdapter = true)
data class VodInfo(
    @Json(name = "name")
    val name: String?,
    @Json(name = "plot")
    val plot: String?,
    @Json(name = "cast")
    val cast: String?,
    @Json(name = "director")
    val director: String?,
    @Json(name = "genre")
    val genre: String?,
    @Json(name = "duration")
    val duration: String?,
    @Json(name = "duration_secs")
    val durationSecs: String?,
    @Json(name = "runtime")
    val runtime: String?,
    @Json(name = "releasedate")
    val releasedate: String?,
    @Json(name = "rating")
    val rating: String?,
    @Json(name = "youtube_trailer")
    val youtubeTrailer: String?,
    @Json(name = "backdrop_path")
    val backdropPath: List<String>?,
    @Json(name = "movie_image")
    val movieImage: String?,
    @Json(name = "cover_big")
    val coverBig: String?,
    @Json(name = "tmdb_id")
    val tmdbId: String?
)

/**
 * Custom Moshi adapter for [VodInfoResponse] (mirrors [SeriesInfoResponseAdapter]):
 * - a top-level `[]` yields an empty response
 * - `"info": []` yields null info (PHP empty-array quirk)
 */
class VodInfoResponseAdapter(
    private val delegate: JsonAdapter<VodInfoResponse>
) : JsonAdapter<VodInfoResponse>() {

    override fun fromJson(reader: JsonReader): VodInfoResponse? {
        return when (reader.peek()) {
            JsonReader.Token.BEGIN_ARRAY -> {
                reader.beginArray()
                val isEmpty = !reader.hasNext()
                reader.endArray()

                if (isEmpty) {
                    VodInfoResponse(info = null)
                } else {
                    error("Expected empty array or object, got non-empty array")
                }
            }
            JsonReader.Token.BEGIN_OBJECT -> delegate.fromJson(reader)
            JsonReader.Token.NULL -> {
                reader.nextNull<Any>()
                null
            }
            else -> error("Expected array or object, got ${reader.peek()}")
        }
    }

    override fun toJson(writer: JsonWriter, value: VodInfoResponse?) {
        delegate.toJson(writer, value)
    }
}

/**
 * Factory for [VodInfoResponseAdapter]. The delegate parses the object manually
 * so a nested `info` array can be tolerated without changing the global
 * EmptyArrayAsObject registrations.
 */
class VodInfoResponseAdapterFactory : JsonAdapter.Factory {
    override fun create(
        type: java.lang.reflect.Type,
        annotations: MutableSet<out Annotation>,
        moshi: Moshi
    ): JsonAdapter<*>? {
        if (type != VodInfoResponse::class.java) {
            return null
        }

        val infoAdapter: JsonAdapter<VodInfo?> = moshi.adapter(VodInfo::class.java)
        val delegate = object : JsonAdapter<VodInfoResponse>() {
            override fun fromJson(reader: JsonReader): VodInfoResponse? {
                reader.beginObject()
                var info: VodInfo? = null

                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "info" -> {
                            info = if (reader.peek() == JsonReader.Token.BEGIN_ARRAY) {
                                reader.skipValue()
                                null
                            } else {
                                infoAdapter.fromJson(reader)
                            }
                        }
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()

                return VodInfoResponse(info)
            }

            override fun toJson(writer: JsonWriter, value: VodInfoResponse?) {
                if (value == null) {
                    writer.nullValue()
                    return
                }
                writer.beginObject()
                value.info?.let {
                    writer.name("info")
                    infoAdapter.toJson(writer, it)
                }
                writer.endObject()
            }
        }

        return VodInfoResponseAdapter(delegate)
    }
}
