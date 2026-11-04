package com.flintr.tv.data.model

import com.flintr.tv.data.local.imdb.ImdbEnrichment

/**
 * Anzeige-Daten fuer die Detail-Screens.
 *
 * Genre und Rating sind bewusst nullable: laut Chris-Spec (30.10.) kommen sie
 * EXKLUSIV aus dem IMDb-Enrichment. Ohne Match bleiben sie null; die
 * Xtream-Werte aus get_vod_info werden nie als Fallback angezeigt.
 */
data class DetailDisplayData(
    val title: String?,
    val plot: String?,
    val cast: List<String>,
    val director: String?,
    val runtime: String?,
    val releaseDate: String?,
    val youtubeTrailer: String?,
    val backdropPath: List<String>?,
    val posterUrl: String?,
    val tmdbId: String?,
    val genres: List<String>?,
    val rating: Double?
)

private val YEAR_PATTERN = Regex("(?:19|20)\\d{2}")
private val TITLE_YEAR_PATTERN = Regex("\\((?:19|20)\\d{2}\\)")
private val CAST_SEPARATOR = ","

/**
 * Mappt die get_vod_info-Metadaten auf Anzeige-Daten.
 *
 * Cast/Director/Plot/Runtime/Trailer/Backdrop kommen aus Xtream; Genre/Rating
 * ausschliesslich aus [imdbLookup]. Der Lookup-Key ist "Titel (Jahr)" — das
 * Jahr stammt bevorzugt aus `releasedate`, sonst aus dem Titel. Ohne Titel
 * wird der Lookup gar nicht erst aufgerufen.
 *
 * @param catalogTitle optionaler Titel aus dem Katalog, falls die Detail-Antwort
 *   keinen `name` liefert.
 */
fun mapVodInfoToDetail(
    info: VodInfo?,
    catalogTitle: String? = null,
    imdbLookup: (String) -> ImdbEnrichment?
): DetailDisplayData? {
    val vod = info ?: return null

    val title = catalogTitle?.takeIf { it.isNotBlank() }
        ?: vod.name?.takeIf { it.isNotBlank() }

    val yearFromTitle = title?.let { YEAR_PATTERN.find(it)?.value?.toInt() }
    val year = vod.releasedate?.let { YEAR_PATTERN.find(it)?.value?.toInt() } ?: yearFromTitle

    val enrichment = title?.let { rawTitle ->
        val lookupTitle = if (yearFromTitle != null) {
            rawTitle.replace(TITLE_YEAR_PATTERN, "").trim()
        } else {
            rawTitle
        }
        val lookupName = if (year != null) "$lookupTitle ($year)" else lookupTitle
        imdbLookup(lookupName)
    }

    return DetailDisplayData(
        title = title,
        plot = vod.plot,
        cast = vod.cast
            ?.split(CAST_SEPARATOR)
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList(),
        director = vod.director,
        runtime = vod.runtime?.takeIf { it.isNotBlank() } ?: vod.duration,
        releaseDate = vod.releasedate,
        youtubeTrailer = vod.youtubeTrailer,
        backdropPath = vod.backdropPath,
        posterUrl = vod.movieImage?.takeIf { it.isNotBlank() } ?: vod.coverBig,
        tmdbId = vod.tmdbId,
        genres = enrichment?.genres,
        rating = enrichment?.rating
    )
}
