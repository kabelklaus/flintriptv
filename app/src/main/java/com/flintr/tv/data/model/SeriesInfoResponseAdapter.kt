package com.flintr.tv.data.model

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

/**
 * Custom Moshi adapter for SeriesInfoResponse that handles both empty arrays and objects.
 * The API sometimes returns an empty array [] instead of an object when series info is not available.
 */
class SeriesInfoResponseAdapter(
    private val delegate: JsonAdapter<SeriesInfoResponse>
) : JsonAdapter<SeriesInfoResponse>() {

    override fun fromJson(reader: JsonReader): SeriesInfoResponse? {
        return when (reader.peek()) {
            JsonReader.Token.BEGIN_ARRAY -> {
                // Handle empty array case
                reader.beginArray()
                val isEmpty = !reader.hasNext()
                reader.endArray()
                
                if (isEmpty) {
                    // Return empty SeriesInfoResponse with all null fields
                    SeriesInfoResponse(
                        seasons = null,
                        info = null,
                        episodes = null
                    )
                } else {
                    // Non-empty array - this shouldn't happen, but delegate to standard adapter
                    throw IllegalStateException("Expected empty array or object, got non-empty array")
                }
            }
            JsonReader.Token.BEGIN_OBJECT -> {
                // Normal case: parse as object using delegate
                delegate.fromJson(reader)
            }
            JsonReader.Token.NULL -> {
                reader.nextNull<Any>()
                null
            }
            else -> {
                throw IllegalStateException("Expected array or object, got ${reader.peek()}")
            }
        }
    }

    override fun toJson(writer: JsonWriter, value: SeriesInfoResponse?) {
        delegate.toJson(writer, value)
    }
}

/**
 * Factory for SeriesInfoResponseAdapter that creates the adapter with proper delegate handling.
 */
class SeriesInfoResponseAdapterFactory : JsonAdapter.Factory {
    override fun create(type: java.lang.reflect.Type, annotations: MutableSet<out Annotation>, moshi: Moshi): JsonAdapter<*>? {
        if (type != SeriesInfoResponse::class.java) {
            return null
        }
        
        // Create delegate adapter that manually parses the object fields
        // We parse manually since generateAdapter = false for SeriesInfoResponse
        val delegate = object : JsonAdapter<SeriesInfoResponse>() {
            private val seasonsAdapter: JsonAdapter<List<Season>?> = moshi.adapter(
                Types.newParameterizedType(List::class.java, Season::class.java)
            )
            private val infoAdapter: JsonAdapter<SeriesInfo?> = moshi.adapter(SeriesInfo::class.java)
            private val episodesAdapter: JsonAdapter<Map<String, List<Episode>>?> = moshi.adapter(
                Types.newParameterizedType(
                    Map::class.java,
                    String::class.java,
                    Types.newParameterizedType(List::class.java, Episode::class.java)
                )
            )
            
            override fun fromJson(reader: JsonReader): SeriesInfoResponse? {
                reader.beginObject()
                var seasons: List<Season>? = null
                var info: SeriesInfo? = null
                var episodes: Map<String, List<Episode>>? = null
                
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "seasons" -> seasons = seasonsAdapter.fromJson(reader)
                        "info" -> info = infoAdapter.fromJson(reader)
                        "episodes" -> episodes = episodesAdapter.fromJson(reader)
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                
                return SeriesInfoResponse(seasons, info, episodes)
            }
            
            override fun toJson(writer: JsonWriter, value: SeriesInfoResponse?) {
                if (value == null) {
                    writer.nullValue()
                    return
                }
                writer.beginObject()
                value.seasons?.let {
                    writer.name("seasons")
                    seasonsAdapter.toJson(writer, it)
                }
                value.info?.let {
                    writer.name("info")
                    infoAdapter.toJson(writer, it)
                }
                value.episodes?.let {
                    writer.name("episodes")
                    episodesAdapter.toJson(writer, it)
                }
                writer.endObject()
            }
        }
        
        return SeriesInfoResponseAdapter(delegate)
    }
}
