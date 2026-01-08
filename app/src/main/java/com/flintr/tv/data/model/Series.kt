package com.flintr.tv.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Series(
    @Json(name = "num")
    val num: Int?,
    @Json(name = "name")
    val name: String,
    @Json(name = "series_id")
    val seriesId: Int,
    @Json(name = "cover")
    val cover: String?,
    @Json(name = "plot")
    val plot: String?,
    @Json(name = "cast")
    val cast: String?,
    @Json(name = "director")
    val director: String?,
    @Json(name = "genre")
    val genre: String?,
    @Json(name = "releaseDate")
    val releaseDate: String?,
    @Json(name = "rating")
    val rating: String?,
    @Json(name = "rating_5based")
    val rating5Based: Double?,
    @Json(name = "category_id")
    val categoryId: String?,
    @Json(name = "category_name")
    val categoryName: String?,
    @Json(name = "youtube_trailer")
    val youtubeTrailer: String?,
    @Json(name = "backdrop_path")
    val backdropPath: List<String>?,
    @Json(name = "seasons")
    val seasons: List<Season>?
)

@JsonClass(generateAdapter = true)
data class Season(
    @Json(name = "air_date")
    val airDate: String?,
    @Json(name = "episode_count")
    val episodeCount: Int?,
    @Json(name = "id")
    val id: Int,
    @Json(name = "name")
    val name: String,
    @Json(name = "overview")
    val overview: String?,
    @Json(name = "season_number")
    val seasonNumber: Int,
    @Json(name = "cover")
    val cover: String?,
    @Json(name = "episodes")
    val episodes: List<Episode>?
)

@JsonClass(generateAdapter = true)
data class Episode(
    @Json(name = "id")
    val id: Int,
    @Json(name = "episode_num")
    val episodeNum: Int?,
    @Json(name = "title")
    val title: String,
    @Json(name = "container_extension")
    val containerExtension: String?,
    @Json(name = "info")
    val info: EpisodeInfo?,
    @Json(name = "subtitles")
    val subtitles: List<Subtitle>?,
    @Json(name = "added")
    val added: String?,
    @Json(name = "season")
    val season: Int?,
    @Json(name = "direct_source")
    val directSource: String?
) {
    fun getStreamUrl(baseUrl: String, username: String, password: String, seriesId: Int): String {
        val extension = containerExtension ?: "mkv"
        return "$baseUrl/series/$username/$password/$seriesId/$id.$extension"
    }
}

@JsonClass(generateAdapter = true)
data class EpisodeInfo(
    @Json(name = "plot")
    val plot: String?,
    @Json(name = "duration")
    val duration: String?,
    @Json(name = "movie_image")
    val movieImage: String?,
    @Json(name = "releaseDate")
    val releaseDate: String?,
    @Json(name = "rating")
    val rating: String?,
    @Json(name = "tmdb_id")
    val tmdbId: String?
)

@JsonClass(generateAdapter = true)
data class Subtitle(
    @Json(name = "url")
    val url: String?,
    @Json(name = "lang")
    val lang: String?
)
