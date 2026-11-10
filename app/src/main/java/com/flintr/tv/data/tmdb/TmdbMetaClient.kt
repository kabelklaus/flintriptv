package com.flintr.tv.data.tmdb

import com.flintr.tv.BuildConfig
import com.flintr.tv.utils.error.reportHandled
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
    private sealed class FetchResult {
        data class Cacheable(val meta: TmdbMeta?) : FetchResult()
        data class Transient(val meta: TmdbMeta?) : FetchResult()
    }

    private val moshi = Moshi.Builder().build()
    private val movieAdapter = moshi.adapter(TmdbMovieDetailResponse::class.java)
    private val tvAdapter = moshi.adapter(TmdbTvDetailResponse::class.java)
    private val searchAdapter = moshi.adapter(TmdbSearchResponse::class.java)

    suspend fun movieMeta(tmdbId: String): TmdbResult<TmdbMeta> = meta("movie", tmdbId)

    suspend fun tvMeta(tmdbId: String): TmdbResult<TmdbMeta> = meta("tv", tmdbId)

    suspend fun searchMovieId(query: String, year: Int?): TmdbResult<Int> =
        searchId("movie", query, year)

    suspend fun searchTvId(query: String, year: Int?): TmdbResult<Int> =
        searchId("tv", query, year)

    private suspend fun meta(kind: String, tmdbId: String): TmdbResult<TmdbMeta> {
        // Blanker Key ist ein Konfigurationsfehler (transient), eine nicht-numerische ID
        // kann nie existieren und darf negativ gecacht werden.
        if (tmdbId.isBlank()) return TmdbResult.Transient
        if (!NUMERIC_ID.matches(tmdbId)) return TmdbResult.Negative
        if (BuildConfig.TMDB_API_KEY.isBlank()) return TmdbResult.Transient

        return when (val result = withContext(ioDispatcher) { fetchMeta(kind, tmdbId) }) {
            is FetchResult.Cacheable -> result.meta
                ?.let { TmdbResult.Data(it) }
                ?: TmdbResult.Negative
            is FetchResult.Transient -> TmdbResult.Transient
        }
    }

    private fun fetchMeta(kind: String, tmdbId: String): FetchResult {
        val apiKey = BuildConfig.TMDB_API_KEY
        val isMovie = kind == "movie"
        return try {
            val url = "$BASE_URL/$kind/$tmdbId?api_key=$apiKey"
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                when {
                    response.code == 404 -> FetchResult.Cacheable(null)
                    !response.isSuccessful -> {
                        reportHandled("$TAG.$kind", IOException("TMDB details HTTP ${response.code}"))
                        FetchResult.Transient(null)
                    }
                    else -> {
                        val json = response.body?.string()
                        val meta = json?.let { if (isMovie) parseMovie(it) else parseTv(it) }
                        if (meta == null) {
                            reportHandled("$TAG.$kind", IOException("TMDB details unparsable body"))
                            FetchResult.Transient(null)
                        } else {
                            FetchResult.Cacheable(meta)
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            reportHandled("$TAG.$kind", e)
            FetchResult.Transient(null)
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
