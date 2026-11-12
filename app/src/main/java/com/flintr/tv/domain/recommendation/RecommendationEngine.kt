package com.flintr.tv.domain.recommendation

import com.flintr.tv.data.local.CachedData
import com.flintr.tv.data.model.Series
import com.flintr.tv.data.model.VodStream

data class RecommendationResult(
    val movies: List<VodStream>,
    val series: List<Series>
)

/**
 * Reine Auswahl-Maschinerie: synchron und ohne Netz/Repository. Die Discovery-Listen kommen
 * bereits in TMDB-Reihenfolge (vote_average) herein; diese Reihenfolge ist die Bewertung.
 *
 * Katalog-Items werden ausschliesslich ueber ihre numerische `tmdbId` zugeordnet. Items ohne
 * Treffer im Discover-Ergebnis fallen weg statt synthetisch aufgefuellt zu werden.
 */
object RecommendationEngine {
    const val COLD_START_MIN_SIGNALS = 5
    const val DEFAULT_LIMIT_PER_TYPE = 20
    const val DIVERSITY_MAX_PER_GENRE = 5
    const val TOP_GENRE_COUNT = 3

    private val NUMERIC_ID = Regex("\\d+")

    fun recommend(
        profile: GenreProfile,
        cached: CachedData,
        discoveredMoviesByGenre: Map<Int, List<Int>>,
        discoveredSeriesByGenre: Map<Int, List<Int>>,
        limitPerType: Int = DEFAULT_LIMIT_PER_TYPE
    ): RecommendationResult {
        val genreOrder = profile.weights.entries
            .filter { it.value > 0.0 }
            .sortedWith(compareByDescending<Map.Entry<Int, Double>> { it.value }.thenBy { it.key })
            .map { it.key }

        val movieIndex = tmdbIndex(cached.movies) { it.tmdbId }
        val seriesIndex = tmdbIndex(cached.series) { it.tmdbId }

        return RecommendationResult(
            movies = selectFromDiscover(
                genreOrder = genreOrder,
                discoveredByGenre = discoveredMoviesByGenre,
                index = movieIndex,
                excludedIds = profile.excludedMovieIds,
                idOf = { it.streamId },
                limit = limitPerType
            ),
            series = selectFromDiscover(
                genreOrder = genreOrder,
                discoveredByGenre = discoveredSeriesByGenre,
                index = seriesIndex,
                excludedIds = profile.excludedSeriesIds,
                idOf = { it.seriesId },
                limit = limitPerType
            )
        )
    }

    /** Similar-Row: TMDB-Reihenfolge der Recommendations bleibt erhalten. */
    fun selectSimilarMovies(
        anchorTmdbIds: List<Int>,
        cached: CachedData,
        excludedMovieIds: Set<Int>,
        limit: Int = DEFAULT_LIMIT_PER_TYPE
    ): List<VodStream> = selectSimilarMovies(
        anchorTmdbIds = anchorTmdbIds,
        index = tmdbIndex(cached.movies) { it.tmdbId },
        excludedMovieIds = excludedMovieIds,
        limit = limit
    )

    /**
     * Ueberladung mit fertigem tmdbId -> Katalog-Item-Index. Der Aufrufer kann dort auch
     * Reverse-Index-Treffer aus dem Search-Cache einmischen (Filme ohne Provider-tmdbId).
     */
    fun selectSimilarMovies(
        anchorTmdbIds: List<Int>,
        index: Map<Int, VodStream>,
        excludedMovieIds: Set<Int>,
        limit: Int = DEFAULT_LIMIT_PER_TYPE
    ): List<VodStream> = selectSimilar(
        anchorTmdbIds = anchorTmdbIds,
        index = index,
        excludedIds = excludedMovieIds,
        idOf = { it.streamId },
        limit = limit
    )

    fun selectSimilarSeries(
        anchorTmdbIds: List<Int>,
        cached: CachedData,
        excludedSeriesIds: Set<Int>,
        limit: Int = DEFAULT_LIMIT_PER_TYPE
    ): List<Series> = selectSimilarSeries(
        anchorTmdbIds = anchorTmdbIds,
        index = tmdbIndex(cached.series) { it.tmdbId },
        excludedSeriesIds = excludedSeriesIds,
        limit = limit
    )

    /** Serien-Analog zu [selectSimilarMovies] mit fertigem Index. */
    fun selectSimilarSeries(
        anchorTmdbIds: List<Int>,
        index: Map<Int, Series>,
        excludedSeriesIds: Set<Int>,
        limit: Int = DEFAULT_LIMIT_PER_TYPE
    ): List<Series> = selectSimilar(
        anchorTmdbIds = anchorTmdbIds,
        index = index,
        excludedIds = excludedSeriesIds,
        idOf = { it.seriesId },
        limit = limit
    )

    /** Bundelt [selectFromDiscover]'s Lookup-/Filter-Eingaben, damit die Helper unter dem Parameter-Limit bleiben. */
    private class DiscoverContext<T>(
        val genreOrder: List<Int>,
        val discoveredByGenre: Map<Int, List<Int>>,
        val index: Map<Int, T>,
        val excludedIds: Set<Int>,
        val idOf: (T) -> Int
    )

    private fun <T> selectFromDiscover(
        genreOrder: List<Int>,
        discoveredByGenre: Map<Int, List<Int>>,
        index: Map<Int, T>,
        excludedIds: Set<Int>,
        idOf: (T) -> Int,
        limit: Int
    ): List<T> {
        if (limit <= 0) return emptyList()
        val result = ArrayList<T>(limit)
        val used = mutableSetOf<Int>()
        val context = DiscoverContext(genreOrder, discoveredByGenre, index, excludedIds, idOf)

        fillDiverseByGenre(context, limit, result, used)
        if (result.size < limit) {
            fillRemaining(context, limit, result, used)
        }

        return result
    }

    /** Phase 1: pro Genre max. DIVERSITY_MAX_PER_GENRE Katalog-Treffer. */
    private fun <T> fillDiverseByGenre(
        context: DiscoverContext<T>,
        limit: Int,
        result: ArrayList<T>,
        used: MutableSet<Int>
    ) {
        for (genreId in context.genreOrder) {
            if (result.size >= limit) break
            var takenForGenre = 0
            for (tmdbId in context.discoveredByGenre[genreId].orEmpty()) {
                if (result.size >= limit || takenForGenre >= DIVERSITY_MAX_PER_GENRE) break
                if (addIfEligible(context, tmdbId, used, result)) takenForGenre++
            }
        }
    }

    /** Phase 2: Rest in derselben Genre-/TMDB-Reihenfolge auffuellen, ohne Cap. */
    private fun <T> fillRemaining(
        context: DiscoverContext<T>,
        limit: Int,
        result: ArrayList<T>,
        used: MutableSet<Int>
    ) {
        for (genreId in context.genreOrder) {
            for (tmdbId in context.discoveredByGenre[genreId].orEmpty()) {
                if (result.size >= limit) break
                addIfEligible(context, tmdbId, used, result)
            }
        }
    }

    private fun <T> addIfEligible(
        context: DiscoverContext<T>,
        tmdbId: Int,
        used: MutableSet<Int>,
        result: ArrayList<T>
    ): Boolean {
        val item = context.index[tmdbId] ?: return false
        val itemId = context.idOf(item)
        if (itemId in context.excludedIds || !used.add(itemId)) return false
        result += item
        return true
    }

    private fun <T> selectSimilar(
        anchorTmdbIds: List<Int>,
        index: Map<Int, T>,
        excludedIds: Set<Int>,
        idOf: (T) -> Int,
        limit: Int
    ): List<T> {
        if (limit <= 0) return emptyList()
        val result = ArrayList<T>(minOf(limit, anchorTmdbIds.size))
        val used = mutableSetOf<Int>()
        for (tmdbId in anchorTmdbIds) {
            if (result.size >= limit) break
            val item = index[tmdbId] ?: continue
            val itemId = idOf(item)
            if (itemId in excludedIds || !used.add(itemId)) continue
            result += item
        }
        return result
    }

    private fun <T> tmdbIndex(
        items: List<T>,
        tmdbIdOf: (T) -> String?
    ): Map<Int, T> = items
        .asSequence()
        .mapNotNull { item ->
            val tmdbId = tmdbIdOf(item)?.takeIf { NUMERIC_ID.matches(it) }?.toIntOrNull() ?: return@mapNotNull null
            tmdbId to item
        }
        .associateBy({ it.first }, { it.second })
}
