package com.flintr.tv.data.tmdb

import com.flintr.tv.BuildConfig
import com.flintr.tv.utils.error.reportHandled
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** Ergebnis eines TMDB-Calls: Erfolg, definitiv nicht vorhanden (404/leer) oder transienter Fehler. */
sealed class TmdbResult<out T> {
    data class Data<out T>(val value: T) : TmdbResult<T>()
    data object Negative : TmdbResult<Nothing>()
    data object Transient : TmdbResult<Nothing>()
}

/** Ergebnis eines Kandidaten-Loads; gleiche Semantik wie [TmdbResult], aber mit Liste. */
sealed class FetchResult<out T> {
    data class Data<out T>(val value: T) : FetchResult<T>()
    data object Negative : FetchResult<Nothing>()
    data object Transient : FetchResult<Nothing>()
}

/** Ein TMDB-Suchtreffer fuer den strikten Title-Match; [year] kommt aus release/first_air_date. */
data class TmdbSearchCandidate(
    val tmdbId: Long,
    val title: String,
    val year: Int?
)

/** Wire-Format des Suchtreffers; Movie liefert `title`/`release_date`, TV `name`/`first_air_date`. */
@JsonClass(generateAdapter = true)
internal data class TmdbSearchCandidateWire(
    val id: Long = 0,
    val title: String? = null,
    val name: String? = null,
    @Json(name = "release_date") val releaseDate: String? = null,
    @Json(name = "first_air_date") val firstAirDate: String? = null
)

/** Antwort-Schema von `/search/{movie|tv}` fuer die Kandidatenliste. */
@JsonClass(generateAdapter = true)
internal data class TmdbSearchCandidatesResponse(
    val results: List<TmdbSearchCandidateWire> = emptyList()
)

/**
 * Laedt Rating, Genres und Basis-Daten eines Films bzw. einer Serie von TMDB und
 * loest Titel ueber die Suche einer tmdbId zu.
 *
 * Reines Add-on: ein leerer API-Key, ein 404 (unbekannte tmdbId), ein
 * Server-/Netzwerkfehler oder eine kaputte Antwort fuehren nur zu einem
 * [TmdbResult], nie zu einem Crash. Kein Caching - das uebernimmt das Repository.
 */
@Singleton
class TmdbMetaClient @Inject constructor(
    @Named("Tmdb") private val okHttpClient: OkHttpClient,
    @Named("IO") private val ioDispatcher: CoroutineDispatcher
) {

    companion object {
        private const val TAG = "TmdbMetaClient"
        private const val BASE_URL = "https://api.themoviedb.org/3"
        private val NUMERIC_ID = Regex("\\d+")
    }

    /** Ergebnis von [fetchMeta]: nur [Cacheable] darf das Repository cachen, [Transient]-Fehler werden erneut versucht. */
    private sealed class DetailFetchResult {
        data class Cacheable(val meta: TmdbMeta?) : DetailFetchResult()
        data class Transient(val meta: TmdbMeta?) : DetailFetchResult()
    }

    private val moshi = Moshi.Builder().build()
    private val movieAdapter = moshi.adapter(TmdbMovieDetailResponse::class.java)
    private val tvAdapter = moshi.adapter(TmdbTvDetailResponse::class.java)
    private val searchAdapter = moshi.adapter(TmdbSearchResponse::class.java)
    private val candidatesAdapter = moshi.adapter(TmdbSearchCandidatesResponse::class.java)

    suspend fun movieMeta(tmdbId: String): TmdbResult<TmdbMeta> = meta("movie", tmdbId)

    suspend fun tvMeta(tmdbId: String): TmdbResult<TmdbMeta> = meta("tv", tmdbId)

    suspend fun searchMovieId(query: String, year: Int?): TmdbResult<Int> =
        searchId("movie", query, year)

    suspend fun searchTvId(query: String, year: Int?): TmdbResult<Int> =
        searchId("tv", query, year)

    /**
     * Liefert alle Suchkandidaten (id, Titel, Jahr) fuer den strikten Match im Repository.
     * Movie nutzt `title`/`release_date`, TV `name`/`first_air_date`.
     */
    suspend fun searchCandidates(
        kind: String,
        title: String,
        year: Int?
    ): FetchResult<List<TmdbSearchCandidate>> {
        // Blanker Query/Key ist ein Konfigurations- bzw. Aufruffehler, kein negatives Ergebnis.
        if (title.isBlank() || BuildConfig.TMDB_API_KEY.isBlank()) return FetchResult.Transient

        return withContext(ioDispatcher) {
            try {
                val urlBuilder = "$BASE_URL/search/$kind".toHttpUrl().newBuilder()
                    .addQueryParameter("api_key", BuildConfig.TMDB_API_KEY)
                    .addQueryParameter("query", title)
                if (year != null) urlBuilder.addQueryParameter("year", year.toString())
                val request = Request.Builder().url(urlBuilder.build()).build()

                okHttpClient.newCall(request).execute().use { response ->
                    when {
                        response.code == 404 -> FetchResult.Negative
                        !response.isSuccessful -> {
                            reportHandled("$TAG.searchCandidates.$kind", IOException("TMDB search HTTP ${response.code}"))
                            FetchResult.Transient
                        }
                        else -> {
                            val json = response.body?.string()
                            val results = json?.let { candidatesAdapter.fromJson(it)?.results }
                            if (results == null) {
                                reportHandled("$TAG.searchCandidates.$kind", IOException("TMDB search unparsable body"))
                                FetchResult.Transient
                            } else if (results.isEmpty()) {
                                FetchResult.Negative
                            } else {
                                FetchResult.Data(results.map { it.toCandidate() })
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportHandled("$TAG.searchCandidates.$kind", e)
                FetchResult.Transient
            }
        }
    }

    private fun TmdbSearchCandidateWire.toCandidate(): TmdbSearchCandidate =
        TmdbSearchCandidate(
            tmdbId = id,
            title = title ?: name.orEmpty(),
            year = (releaseDate ?: firstAirDate)?.take(4)?.toIntOrNull()
        )

    private suspend fun meta(kind: String, tmdbId: String): TmdbResult<TmdbMeta> {
        // Blanker Key ist ein Konfigurationsfehler (transient), eine nicht-numerische ID
        // kann nie existieren und darf negativ gecacht werden.
        if (tmdbId.isBlank()) return TmdbResult.Transient
        if (!NUMERIC_ID.matches(tmdbId)) return TmdbResult.Negative
        if (BuildConfig.TMDB_API_KEY.isBlank()) return TmdbResult.Transient

        return when (val result = withContext(ioDispatcher) { fetchMeta(kind, tmdbId) }) {
            is DetailFetchResult.Cacheable -> result.meta
                ?.let { TmdbResult.Data(it) }
                ?: TmdbResult.Negative
            is DetailFetchResult.Transient -> TmdbResult.Transient
        }
    }

    private fun fetchMeta(kind: String, tmdbId: String): DetailFetchResult {
        val apiKey = BuildConfig.TMDB_API_KEY
        val isMovie = kind == "movie"
        return try {
            val url = "$BASE_URL/$kind/$tmdbId?api_key=$apiKey"
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                when {
                    response.code == 404 -> DetailFetchResult.Cacheable(null)
                    !response.isSuccessful -> {
                        reportHandled("$TAG.$kind", IOException("TMDB details HTTP ${response.code}"))
                        DetailFetchResult.Transient(null)
                    }
                    else -> {
                        val json = response.body?.string()
                        val meta = json?.let { if (isMovie) parseMovie(it) else parseTv(it) }
                        if (meta == null) {
                            reportHandled("$TAG.$kind", IOException("TMDB details unparsable body"))
                            DetailFetchResult.Transient(null)
                        } else {
                            DetailFetchResult.Cacheable(meta)
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            reportHandled("$TAG.$kind", e)
            DetailFetchResult.Transient(null)
        }
    }

    private suspend fun searchId(kind: String, query: String, year: Int?): TmdbResult<Int> {
        val apiKey = BuildConfig.TMDB_API_KEY
        if (apiKey.isBlank()) return TmdbResult.Transient

        return withContext(ioDispatcher) {
            try {
                // HttpUrl-Builder: encodiert Sonderzeichen wie `&` im Titel korrekt.
                val urlBuilder = "$BASE_URL/search/$kind".toHttpUrl().newBuilder()
                    .addQueryParameter("api_key", apiKey)
                    .addQueryParameter("query", query)
                if (year != null) urlBuilder.addQueryParameter("year", year.toString())
                val request = Request.Builder().url(urlBuilder.build()).build()

                okHttpClient.newCall(request).execute().use { response ->
                    when {
                        response.code == 404 -> TmdbResult.Negative
                        !response.isSuccessful -> {
                            reportHandled("$TAG.search.$kind", IOException("TMDB search HTTP ${response.code}"))
                            TmdbResult.Transient
                        }
                        else -> {
                            val json = response.body?.string()
                            val results = json?.let { searchAdapter.fromJson(it)?.results }
                            if (results == null) {
                                reportHandled("$TAG.search.$kind", IOException("TMDB search unparsable body"))
                                TmdbResult.Transient
                            } else {
                                results.firstOrNull()?.id
                                    ?.let { TmdbResult.Data(it) }
                                    ?: TmdbResult.Negative
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportHandled("$TAG.search.$kind", e)
                TmdbResult.Transient
            }
        }
    }

    private fun parseMovie(json: String): TmdbMeta? =
        movieAdapter.fromJson(json)?.let { response ->
            TmdbMeta(
                tmdbId = response.id,
                rating = response.rating,
                voteCount = response.voteCount,
                genres = response.genres,
                releaseDate = response.releaseDate,
                runtime = response.runtime
            )
        }

    private fun parseTv(json: String): TmdbMeta? =
        tvAdapter.fromJson(json)?.let { response ->
            TmdbMeta(
                tmdbId = response.id,
                rating = response.rating,
                voteCount = response.voteCount,
                genres = response.genres,
                releaseDate = response.firstAirDate,
                runtime = null
            )
        }
}
