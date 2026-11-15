package com.flintr.tv.data.model

/**
 * Anzeige-Daten fuer die Detail-Screens.
 *
 * Genre und Rating sind bewusst nullable: sie kommen EXKLUSIV aus
 * [com.flintr.tv.data.tmdb.TmdbMetaRepository] und werden nachtraeglich via
 * mergeTmdbMeta gefuellt. Ohne Match bleiben sie null; die Xtream-Werte aus
 * get_vod_info werden nie als Fallback angezeigt.
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

private const val CAST_SEPARATOR = ","

/**
 * Mappt die get_vod_info-Metadaten auf Anzeige-Daten.
 *
 * Cast/Director/Plot/Runtime/Trailer/Backdrop kommen aus Xtream. Genre/Rating
 * bleiben hier null und werden von den Detail-ViewModels nachtraeglich aus
 * TMDB ergaenzt.
 *
 * @param catalogTitle optionaler Titel aus dem Katalog, falls die Detail-Antwort
 *   keinen `name` liefert.
 */
fun mapVodInfoToDetail(
    info: VodInfo?,
    catalogTitle: String? = null
): DetailDisplayData? {
    val vod = info ?: return null

    val title = catalogTitle?.takeIf { it.isNotBlank() }
        ?: vod.name?.takeIf { it.isNotBlank() }

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
        genres = null,
        rating = null
    )
}
