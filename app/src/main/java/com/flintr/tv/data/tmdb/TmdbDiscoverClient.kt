package com.flintr.tv.data.tmdb

import com.flintr.tv.BuildConfig
import com.flintr.tv.utils.error.reportHandled
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

/**
 * Transport-Client fuer TMDB `/discover` und `/{movie,tv}/{id}/recommendations`.
 *
 * Liefert nur die tmdbIds der Ergebnisse. Ein blanker Key, HTTP-Fehler,
 * unparsbare Bodies und Netzfehler sind [TmdbResult.Transient]; 404 und leere
 * results sind [TmdbResult.Negative]. Kein Caching - das uebernimmt das Repository.
 */
@Singleton
class TmdbDiscoverClient @Inject constructor(
    @Named("Tmdb") private val okHttpClient: OkHttpClient,
    @Named("IO") private val ioDispatcher: CoroutineDispatcher
) {

    companion object {
        const val DEFAULT_MIN_VOTES = 200
        private const val TAG = "TmdbDiscoverClient"
        private const val BASE_URL = "https://api.themoviedb.org/3"
        private val NUMERIC_ID = Regex("\\d+")
    }

    private val moshi = Moshi.Builder().build()
    private val discoverAdapter = moshi.adapter(TmdbDiscoverResponse::class.java)
    private val recommendationsAdapter = moshi.adapter(TmdbRecommendationsResponse::class.java)

    suspend fun discoverMovies(
        genreIds: List<Int>,
        page: Int = 1,
        minVotes: Int = DEFAULT_MIN_VOTES
    ): TmdbResult<List<Int>> = discover("movie", genreIds, page, minVotes)

    suspend fun discoverTv(
        genreIds: List<Int>,
        page: Int = 1,
        minVotes: Int = DEFAULT_MIN_VOTES
    ): TmdbResult<List<Int>> = discover("tv", genreIds, page, minVotes)

    suspend fun movieRecommendations(tmdbId: String, page: Int = 1): TmdbResult<List<Int>> =
        recommendations("movie", tmdbId, page)

    suspend fun tvRecommendations(tmdbId: String, page: Int = 1): TmdbResult<List<Int>> =
        recommendations("tv", tmdbId, page)

    private suspend fun discover(
        kind: String,
        genreIds: List<Int>,
        page: Int,
        minVotes: Int
    ): TmdbResult<List<Int>> {
        if (BuildConfig.TMDB_API_KEY.isBlank()) return TmdbResult.Transient
        if (genreIds.isEmpty()) return TmdbResult.Negative

        return withContext(ioDispatcher) {
            try {
                // Query erwartet Komma; der Pipe-Join ist ausschliesslich der Cache-Key.
                val urlBuilder = "$BASE_URL/discover/$kind".toHttpUrl().newBuilder()
                    .addQueryParameter("api_key", BuildConfig.TMDB_API_KEY)
                    .addQueryParameter("with_genres", genreIds.joinToString(","))
                    .addQueryParameter("sort_by", "vote_average.desc")
                    .addQueryParameter("vote_count.gte", minVotes.toString())
                    .addQueryParameter("page", page.toString())
                val request = Request.Builder().url(urlBuilder.build()).build()

                okHttpClient.newCall(request).execute().use { response ->
                    when {
                        response.code == 404 -> TmdbResult.Negative
                        !response.isSuccessful -> {
                            reportHandled("$TAG.discover.$kind", IOException("TMDB discover HTTP ${response.code}"))
                            TmdbResult.Transient
                        }
                        else -> {
                            val json = response.body?.string()
                            val ids = json?.let { discoverAdapter.fromJson(it)?.results?.map { r -> r.id } }
                            if (ids == null) {
                                reportHandled("$TAG.discover.$kind", IOException("TMDB discover unparsable body"))
                                TmdbResult.Transient
                            } else if (ids.isEmpty()) {
                                TmdbResult.Negative
                            } else {
                                TmdbResult.Data(ids)
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportHandled("$TAG.discover.$kind", e)
                TmdbResult.Transient
            }
        }
    }

    private suspend fun recommendations(
        kind: String,
        tmdbId: String,
        page: Int
    ): TmdbResult<List<Int>> {
        if (BuildConfig.TMDB_API_KEY.isBlank()) return TmdbResult.Transient
        if (!NUMERIC_ID.matches(tmdbId)) return TmdbResult.Negative

        return withContext(ioDispatcher) {
            try {
                val urlBuilder = "$BASE_URL/$kind/$tmdbId/recommendations".toHttpUrl().newBuilder()
                    .addQueryParameter("api_key", BuildConfig.TMDB_API_KEY)
                    .addQueryParameter("page", page.toString())
                val request = Request.Builder().url(urlBuilder.build()).build()

                okHttpClient.newCall(request).execute().use { response ->
                    when {
                        response.code == 404 -> TmdbResult.Negative
                        !response.isSuccessful -> {
                            reportHandled("$TAG.rec.$kind", IOException("TMDB recommendations HTTP ${response.code}"))
                            TmdbResult.Transient
                        }
                        else -> {
                            val json = response.body?.string()
                            val ids = json?.let { recommendationsAdapter.fromJson(it)?.results?.map { r -> r.id } }
                            if (ids == null) {
                                reportHandled("$TAG.rec.$kind", IOException("TMDB recommendations unparsable body"))
                                TmdbResult.Transient
                            } else if (ids.isEmpty()) {
                                TmdbResult.Negative
                            } else {
                                TmdbResult.Data(ids)
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportHandled("$TAG.rec.$kind", e)
                TmdbResult.Transient
            }
        }
    }
}

/** Antwort-Schema von `/discover/{movie,tv}`; nur die IDs werden gebraucht. */
@JsonClass(generateAdapter = true)
internal data class TmdbDiscoverResponse(
    val results: List<TmdbDiscoverResult> = emptyList()
)

@JsonClass(generateAdapter = true)
internal data class TmdbDiscoverResult(
    val id: Int
)

/** Antwort-Schema von `/{movie,tv}/{id}/recommendations`. */
@JsonClass(generateAdapter = true)
internal data class TmdbRecommendationsResponse(
    val results: List<TmdbRecommendationResult> = emptyList()
)

@JsonClass(generateAdapter = true)
internal data class TmdbRecommendationResult(
    val id: Int
)
