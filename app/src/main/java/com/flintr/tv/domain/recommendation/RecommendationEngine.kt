package com.flintr.tv.domain.recommendation

import com.flintr.tv.data.local.CachedData
import com.flintr.tv.data.model.Series
import com.flintr.tv.data.model.VodStream
import com.flintr.tv.utils.ContentParser

data class RecommendationResult(
    val movies: List<VodStream>,
    val series: List<Series>
)

/**
 * Scores the catalog against a [GenreProfile] and returns the top candidates per type,
 * per the scoring spec in .hermes-plan/task_plan.md.
 */
object RecommendationEngine {
    private const val COLD_START_MIN_SIGNALS = 5
    private const val DEFAULT_LIMIT_PER_TYPE = 20
    private const val DIVERSITY_MAX_PER_GENRE = 5

    fun recommend(
        profile: GenreProfile,
        cached: CachedData,
        limitPerType: Int = DEFAULT_LIMIT_PER_TYPE
    ): RecommendationResult {
        if (profile.signalCount < COLD_START_MIN_SIGNALS) {
            return RecommendationResult(emptyList(), emptyList())
        }

        val genreMovies = selectWithGenreDiversity(
            rankMovies(cached.movies, profile),
            limitPerType,
            profile
        ) { it.genre }

        val movies = genreMovies.ifEmpty {
            cached.movies
                .asSequence()
                .filter { it.streamId !in profile.excludedMovieIds }
                .sortedWith(
                    compareByDescending<VodStream> { ratingOf(it) }.thenBy { it.name }
                )
                .take(limitPerType)
                .toList()
        }

        val genreSeries = selectWithGenreDiversity(
            rankSeries(cached.series, profile),
            limitPerType,
            profile
        ) { it.genre }

        val series = genreSeries.ifEmpty {
            cached.series
                .asSequence()
                .filter { it.seriesId !in profile.excludedSeriesIds }
                .sortedWith(
                    compareByDescending<Series> { ratingOf(it) }.thenBy { it.name }
                )
                .take(limitPerType)
                .toList()
        }

        return RecommendationResult(movies = movies, series = series)
    }

    /**
     * Similar-Movie row for a detail screen: reuses the exact same scoring and diversity
     * pipeline as [recommend], only the profile is built from the anchor movie's genres instead
     * of the user's signals. Deliberately no fallback list - without a genre match the section
     * stays hidden rather than showing unrelated titles.
     */
    fun recommendSimilar(
        anchorGenres: List<String>?,
        cached: CachedData,
        excludedMovieIds: Set<Int> = emptySet(),
        limit: Int = DEFAULT_LIMIT_PER_TYPE
    ): List<VodStream> {
        val tokens = anchorGenres.orEmpty()
            .flatMap { GenreNormalizer.normalize(it) }
            .distinct()
        if (tokens.isEmpty()) return emptyList()

        val anchorProfile = GenreProfile(
            weights = tokens.associateWith { 1.0 },
            signalCount = tokens.size,
            excludedMovieIds = excludedMovieIds
        )
        return selectWithGenreDiversity(
            rankMovies(cached.movies, anchorProfile),
            limit,
            anchorProfile
        ) { it.genre }
    }

    /**
     * Similar-Series row for SeriesDetailScreen: mirrors [recommendSimilar] but ranks the series
     * catalog instead of movies, since the detail screen's anchor is a series, not a movie.
     * Same scoring/diversity pipeline, no second matching implementation.
     */
    fun recommendSimilarSeries(
        anchorGenres: List<String>?,
        cached: CachedData,
        excludedSeriesIds: Set<Int> = emptySet(),
        limit: Int = DEFAULT_LIMIT_PER_TYPE
    ): List<Series> {
        val tokens = anchorGenres.orEmpty()
            .flatMap { GenreNormalizer.normalize(it) }
            .distinct()
        if (tokens.isEmpty()) return emptyList()

        val anchorProfile = GenreProfile(
            weights = tokens.associateWith { 1.0 },
            signalCount = tokens.size,
            excludedSeriesIds = excludedSeriesIds
        )
        return selectWithGenreDiversity(
            rankSeries(cached.series, anchorProfile),
            limit,
            anchorProfile
        ) { it.genre }
    }

    private fun rankMovies(movies: List<VodStream>, profile: GenreProfile): List<VodStream> =
        movies
            .asSequence()
            .filter { it.streamId !in profile.excludedMovieIds }
            .map { it to score(it.genre, profile) }
            .filter { (_, score) -> score > 0.0 }
            .sortedWith(
                compareByDescending<Pair<VodStream, Double>> { it.second }
                    .thenByDescending { ratingOf(it.first) }
                    .thenBy { it.first.name }
            )
            .map { it.first }
            .toList()

    private fun rankSeries(series: List<Series>, profile: GenreProfile): List<Series> =
        series
            .asSequence()
            .filter { it.seriesId !in profile.excludedSeriesIds }
            .map { it to score(it.genre, profile) }
            .filter { (_, score) -> score > 0.0 }
            .sortedWith(
                compareByDescending<Pair<Series, Double>> { it.second }
                    .thenByDescending { ratingOf(it.first) }
                    .thenBy { it.first.name }
            )
            .map { it.first }
            .toList()

    private fun score(rawGenre: String?, profile: GenreProfile): Double {
        return GenreNormalizer.normalize(rawGenre).sumOf { genre -> profile.weights[genre] ?: 0.0 }
    }

    /**
     * Caps the genre-sorted [sorted] candidates at [DIVERSITY_MAX_PER_GENRE] per anchor genre
     * (phase 1), then fills any remaining slots up to [limitPerType] from the leftover
     * candidates in the same order, ignoring the cap (phase 2).
     */
    private fun <T> selectWithGenreDiversity(
        sorted: List<T>,
        limitPerType: Int,
        profile: GenreProfile,
        genreOf: (T) -> String?
    ): List<T> {
        val perGenreCount = mutableMapOf<String, Int>()
        val taken = BooleanArray(sorted.size)
        val result = ArrayList<T>(minOf(limitPerType, sorted.size))

        for (index in sorted.indices) {
            if (result.size >= limitPerType) break
            val anchor = anchorGenre(genreOf(sorted[index]), profile)
            val count = perGenreCount.getOrDefault(anchor, 0)
            if (count < DIVERSITY_MAX_PER_GENRE) {
                result += sorted[index]
                perGenreCount[anchor] = count + 1
                taken[index] = true
            }
        }

        if (result.size < limitPerType) {
            for (index in sorted.indices) {
                if (result.size >= limitPerType) break
                if (!taken[index]) result += sorted[index]
            }
        }

        return result
    }

    private fun anchorGenre(rawGenre: String?, profile: GenreProfile): String {
        return GenreNormalizer.normalize(rawGenre)
            .sortedWith(compareByDescending<String> { profile.weights[it] ?: 0.0 }.thenBy { it })
            .first()
    }

    private fun ratingOf(item: Any): Double = ContentParser.getRating(item)?.toDoubleOrNull() ?: 0.0
}
