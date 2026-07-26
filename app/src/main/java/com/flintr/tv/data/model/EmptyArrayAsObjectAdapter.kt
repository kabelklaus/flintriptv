package com.flintr.tv.data.model

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import java.lang.reflect.Type

/**
 * Wraps an object adapter so that a JSON array where an object is expected is
 * treated as null instead of throwing.
 *
 * Some Xtream-Codes panels emit `[]` (PHP's empty-array quirk) instead of `{}`
 * for optional info objects — e.g. a single episode without metadata produces
 * `"info": []`, which otherwise fails the whole series load with
 * `Expected BEGIN_OBJECT but was BEGIN_ARRAY`. This mirrors the top-level
 * handling in [SeriesInfoResponseAdapter], but for nested object fields.
 */
class EmptyArrayAsObjectAdapter<T>(
    private val delegate: JsonAdapter<T>
) : JsonAdapter<T>() {

    override fun fromJson(reader: JsonReader): T? {
        if (reader.peek() == JsonReader.Token.BEGIN_ARRAY) {
            // Consume the array (empty or not) and treat the field as absent.
            reader.skipValue()
            return null
        }
        return delegate.fromJson(reader)
    }

    override fun toJson(writer: JsonWriter, value: T?) {
        delegate.toJson(writer, value)
    }
}

/**
 * Registers [EmptyArrayAsObjectAdapter] for the given object types so that a
 * stray `[]` where an object is expected yields null instead of crashing the
 * whole response.
 */
class EmptyArrayAsObjectAdapterFactory(
    private val types: Set<Type>
) : JsonAdapter.Factory {
    override fun create(
        type: Type,
        annotations: MutableSet<out Annotation>,
        moshi: Moshi
    ): JsonAdapter<*>? {
        if (annotations.isNotEmpty() || type !in types) {
            return null
        }
        val delegate = moshi.nextAdapter<Any>(this, type, annotations)
        return EmptyArrayAsObjectAdapter(delegate)
    }
}
