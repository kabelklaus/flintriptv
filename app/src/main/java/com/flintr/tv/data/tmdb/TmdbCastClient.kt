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

@JsonClass(generateAdapter = true)
internal data class TmdbCreditsResponse(
    val cast: List<TmdbCastMember> = emptyList()
)

/**
 * Laedt die Besetzung eines Films oder einer Serie von TMDB
 * (`/movie/{id}/credits`, `/tv/{id}/credits`) und cacht sie im Speicher.
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
        private val NUMERIC_ID = Regex("\\d+")
    }

    /** Ergebnis von [fetchCredits]: nur [Cacheable] darf im LRU landen, [Transient]-Fehler werden pro Aufruf erneut versucht. */
    private sealed class FetchResult {
        data class Cacheable(val members: List<TmdbCastMember>) : FetchResult()
        data class Transient(val members: List<TmdbCastMember>) : FetchResult()
    }

    private val moshi = Moshi.Builder().build()
    private val responseAdapter = moshi.adapter(TmdbCreditsResponse::class.java)

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
        return try {
            val url = "$BASE_URL/$kind/$tmdbId/credits?api_key=$apiKey"
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                when {
                    response.code == 404 -> FetchResult.Cacheable(emptyList())
                    !response.isSuccessful -> {
                        reportHandled("$TAG.$kind", IOException("TMDB credits HTTP ${response.code}"))
                        FetchResult.Transient(emptyList())
                    }
                    else -> {
                        val body = response.body
                        val parsed = body?.let { responseAdapter.fromJson(it.string()) }
                        if (parsed == null) {
                            reportHandled("$TAG.$kind", IOException("TMDB credits unparsable body"))
                            FetchResult.Transient(emptyList())
                        } else {
                            FetchResult.Cacheable(parsed.cast.sortedBy { it.order })
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
}
