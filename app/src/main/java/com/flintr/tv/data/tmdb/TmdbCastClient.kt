package com.flintr.tv.data.tmdb

import com.flintr.tv.BuildConfig
import com.flintr.tv.utils.error.reportHandled
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** Ein Besetzungsmitglied aus den TMDB-Credits (Movie oder TV). */
@JsonClass(generateAdapter = true)
data class TmdbCastMember(
    val id: Int,
    val name: String,
    val character: String? = null,
    @Json(name = "profile_path") val profilePath: String? = null,
    val order: Int = 0
)

/** Antwort-Schema von `/movie/{id}/credits`: `character` liegt direkt auf dem Cast-Eintrag. */
@JsonClass(generateAdapter = true)
internal data class TmdbCreditsResponse(
    val cast: List<TmdbCastMember> = emptyList()
)

/** Eine Rolle innerhalb von `TmdbAggregateCastMember.roles`; `character` kommt von hier. */
@JsonClass(generateAdapter = true)
internal data class TmdbAggregateCastRole(
    val character: String? = null
)

/**
 * Ein Cast-Eintrag aus `/tv/{id}/aggregate_credits`: anders als bei `/credits` liegt `character`
 * nicht direkt auf dem Eintrag, sondern in `roles[0].character` (roles kann leer sein, wenn TMDB
 * keine Rollenangabe hat).
 */
@JsonClass(generateAdapter = true)
internal data class TmdbAggregateCastMember(
    val id: Int,
    val name: String,
    @Json(name = "profile_path") val profilePath: String? = null,
    val order: Int = 0,
    val roles: List<TmdbAggregateCastRole>? = null
) {
    fun toCastMember() = TmdbCastMember(
        id = id,
        name = name,
        character = roles?.firstOrNull()?.character,
        profilePath = profilePath,
        order = order
    )
}

@JsonClass(generateAdapter = true)
internal data class TmdbAggregateCreditsResponse(
    val cast: List<TmdbAggregateCastMember> = emptyList()
)

/**
 * Laedt die Besetzung eines Films von TMDB (`/movie/{id}/credits`) bzw. einer Serie
 * (`/tv/{id}/aggregate_credits`) und cacht sie im Speicher.
 *
 * `/tv/{id}/credits` liefert nur die zuletzt ausgestrahlte Staffel (z.B. Supernatural: 3
 * Mitglieder statt ueber 2000) - `aggregate_credits` liefert die GESAMTE Besetzung ueber alle
 * Staffeln, in Billing-Reihenfolge. Das TV-Ergebnis wird auf [TV_CAST_CAP] Eintraege gekappt,
 * damit die volle Liste (zehntausende Zeilen moeglich) nie in Cache/State landet.
 *
 * Reines Add-on: ein leerer API-Key, ein 404 (unbekannte tmdbId), ein
 * Server-/Netzwerkfehler oder eine kaputte Antwort fuehren nur zu einer
 * leeren Liste, nie zu einem Crash. 404s und leere Ergebnisse werden
 * ebenfalls gecacht, damit ein Screen-Open nicht pro Aufruf erneut gegen
 * das Netz laeuft.
 */
@Singleton
class TmdbCastClient @Inject constructor(
    @Named("Tmdb") private val okHttpClient: OkHttpClient,
    @Named("IO") private val ioDispatcher: CoroutineDispatcher
) {

    companion object {
        private const val TAG = "TmdbCastClient"
        private const val BASE_URL = "https://api.themoviedb.org/3"
        private const val CACHE_CAPACITY = 250
        private const val TV_CAST_CAP = 25
        private val NUMERIC_ID = Regex("\\d+")
    }

    /** Ergebnis von [fetchCredits]: nur [Cacheable] darf im LRU landen, [Transient]-Fehler werden pro Aufruf erneut versucht. */
    private sealed class FetchResult {
        data class Cacheable(val members: List<TmdbCastMember>) : FetchResult()
        data class Transient(val members: List<TmdbCastMember>) : FetchResult()
    }

    private val moshi = Moshi.Builder().build()
    private val movieResponseAdapter = moshi.adapter(TmdbCreditsResponse::class.java)
    private val aggregateResponseAdapter = moshi.adapter(TmdbAggregateCreditsResponse::class.java)

    private val cacheMutex = Mutex()
    private val cache = object : LinkedHashMap<String, List<TmdbCastMember>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<TmdbCastMember>>): Boolean =
            size > CACHE_CAPACITY
    }

    suspend fun movieCredits(tmdbId: String): List<TmdbCastMember> = credits("movie", tmdbId)

    suspend fun tvCredits(tmdbId: String): List<TmdbCastMember> = credits("tv", tmdbId)

    private suspend fun credits(kind: String, tmdbId: String): List<TmdbCastMember> {
        if (!NUMERIC_ID.matches(tmdbId)) return emptyList()
        val apiKey = BuildConfig.TMDB_API_KEY
        if (apiKey.isBlank()) return emptyList()

        val cacheKey = "$kind:$tmdbId"
        cacheMutex.withLock { cache[cacheKey] }?.let { return it }

        return when (val result = withContext(ioDispatcher) { fetchCredits(kind, tmdbId, apiKey) }) {
            is FetchResult.Cacheable -> {
                cacheMutex.withLock { cache[cacheKey] = result.members }
                result.members
            }
            is FetchResult.Transient -> result.members
        }
    }

    private fun fetchCredits(kind: String, tmdbId: String, apiKey: String): FetchResult {
        val isMovie = kind == "movie"
        val endpoint = if (isMovie) "credits" else "aggregate_credits"
        return try {
            val url = "$BASE_URL/$kind/$tmdbId/$endpoint?api_key=$apiKey"
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                when {
                    response.code == 404 -> FetchResult.Cacheable(emptyList())
                    !response.isSuccessful -> {
                        reportHandled("$TAG.$kind", IOException("TMDB credits HTTP ${response.code}"))
                        FetchResult.Transient(emptyList())
                    }
                    else -> {
                        val json = response.body?.string()
                        val members = json?.let { if (isMovie) parseMovieCast(it) else parseTvCast(it) }
                        if (members == null) {
                            reportHandled("$TAG.$kind", IOException("TMDB credits unparsable body"))
                            FetchResult.Transient(emptyList())
                        } else {
                            val sorted = members.sortedBy { it.order }
                            FetchResult.Cacheable(if (isMovie) sorted else sorted.take(TV_CAST_CAP))
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            reportHandled("$TAG.$kind", e)
            FetchResult.Transient(emptyList())
        }
    }

    private fun parseMovieCast(json: String): List<TmdbCastMember>? =
        movieResponseAdapter.fromJson(json)?.cast

    private fun parseTvCast(json: String): List<TmdbCastMember>? =
        aggregateResponseAdapter.fromJson(json)?.cast?.map { it.toCastMember() }
}
