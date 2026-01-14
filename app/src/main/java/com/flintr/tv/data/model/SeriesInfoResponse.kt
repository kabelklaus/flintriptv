package com.flintr.tv.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Response model for get_series_info API call
 * The structure is different from the regular Series model:
 * - Series info is nested in "info" object
 * - Episodes are grouped by season number in "episodes" object
 */
@JsonClass(generateAdapter = false)
data class SeriesInfoResponse(
    @Json(name = "seasons")
    val seasons: List<Season>?,
    @Json(name = "info")
    val info: SeriesInfo?,
    @Json(name = "episodes")
    val episodes: Map<String, List<Episode>>?
) {
    /**
     * Convert SeriesInfoResponse to Series model
     */
    fun toSeries(seriesId: Int): Series {
        val seriesInfo = info
        
        // Merge episodes into seasons
        val seasonsWithEpisodes = if (seasons.isNullOrEmpty() && episodes != null) {
            // If seasons array is empty, create seasons from episodes
            // Group by season number to avoid duplicates from non-numeric keys
            episodes.entries
                .groupBy { (key, _) -> key.toIntOrNull() ?: 0 }
                .map { (seasonNumber, entries) ->
                    val allEpisodes = entries.flatMap { it.value }
                    // Only create season if it has episodes
                    if (allEpisodes.isNotEmpty()) {
                        Season(
                            airDate = null,
                            episodeCount = allEpisodes.size,
                            id = null,
                            name = "Staffel $seasonNumber",
                            overview = null,
                            seasonNumber = seasonNumber,
                            cover = null,
                            episodes = allEpisodes
                        )
                    } else null
                }
                .filterNotNull()
                .sortedBy { it.seasonNumber }
        } else {
            seasons?.mapNotNull { season ->
                val seasonNumber = season.seasonNumber
                val seasonEpisodes = episodes?.get(seasonNumber.toString())
                // Only include seasons that have episodes
                seasonEpisodes?.takeIf { it.isNotEmpty() }?.let {
                    season.copy(episodes = it)
                }
            } ?: emptyList()
        }
        
        return Series(
            num = null,
            name = seriesInfo?.name ?: "",
            seriesId = seriesId,
            cover = seriesInfo?.cover,
            plot = seriesInfo?.plot,
            cast = seriesInfo?.cast,
            director = seriesInfo?.director,
            genre = seriesInfo?.genre,
            releaseDate = seriesInfo?.releaseDate ?: seriesInfo?.releaseDateAlt,
            rating = seriesInfo?.rating,
            rating5Based = seriesInfo?.rating5Based,
            categoryId = seriesInfo?.categoryId,
            categoryName = null,
            youtubeTrailer = seriesInfo?.youtubeTrailer,
            backdropPath = seriesInfo?.backdropPath,
            seasons = seasonsWithEpisodes
        )
    }
}

@JsonClass(generateAdapter = true)
data class SeriesInfo(
    @Json(name = "name")
    val name: String?,
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
    @Json(name = "release_date")
    val releaseDateAlt: String?,
    @Json(name = "rating")
    val rating: String?,
    @Json(name = "rating_5based")
    val rating5Based: Double?,
    @Json(name = "category_id")
    val categoryId: String?,
    @Json(name = "youtube_trailer")
    val youtubeTrailer: String?,
    @Json(name = "backdrop_path")
    val backdropPath: List<String>?
)
