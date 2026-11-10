package com.flintr.tv.data.tmdb

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Ein Genre aus den TMDB-Details (Movie oder TV). */
@JsonClass(generateAdapter = true)
data class TmdbGenre(
    val id: Int,
    val name: String
)

/** Metadaten eines Films bzw. einer Serie; [runtime] ist nur bei Filmen gesetzt. */
@JsonClass(generateAdapter = true)
data class TmdbMeta(
    val tmdbId: Int,
    val rating: Double,
    val voteCount: Int,
    val genres: List<TmdbGenre>,
    val releaseDate: String? = null,
    val runtime: Int? = null
)

/** Antwort-Schema von `/movie/{id}`. */
@JsonClass(generateAdapter = true)
internal data class TmdbMovieDetailResponse(
    val id: Int = 0,
    @Json(name = "vote_average") val rating: Double = 0.0,
    @Json(name = "vote_count") val voteCount: Int = 0,
    val genres: List<TmdbGenre> = emptyList(),
    @Json(name = "release_date") val releaseDate: String? = null,
    val runtime: Int? = null
)

/** Antwort-Schema von `/tv/{id}`; Serien liefern kein `runtime`. */
@JsonClass(generateAdapter = true)
internal data class TmdbTvDetailResponse(
    val id: Int = 0,
    @Json(name = "vote_average") val rating: Double = 0.0,
    @Json(name = "vote_count") val voteCount: Int = 0,
    val genres: List<TmdbGenre> = emptyList(),
    @Json(name = "first_air_date") val firstAirDate: String? = null
)

/** Antwort-Schema der Suche (`/search/movie` und `/search/tv`). */
@JsonClass(generateAdapter = true)
internal data class TmdbSearchResponse(
    val results: List<TmdbSearchResult> = emptyList()
)

/** Ein Suchtreffer; nur die ID wird fuer die Zuordnung gebraucht. */
@JsonClass(generateAdapter = true)
internal data class TmdbSearchResult(
    val id: Int
)
