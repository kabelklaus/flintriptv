package com.flintr.tv.domain.recommendation

import com.flintr.tv.data.model.Favorite
import com.flintr.tv.data.model.FavoriteType
import com.flintr.tv.data.model.RatingEntry
import com.flintr.tv.data.model.Series
import com.flintr.tv.data.model.VodStream
import com.flintr.tv.data.model.WatchProgress
import com.flintr.tv.data.model.WatchedItem
import com.flintr.tv.ui.common.UiConstants
import java.time.Instant
import kotlin.math.exp
import kotlin.math.max

/**
 * Genre-weight map built from a user's signals, plus the content IDs that must never be
 * recommended because the user already knows them (favorited, finished, or in progress).
 */
data class GenreProfile(
    val weights: Map<String, Double>,
    val signalCount: Int,
    val excludedMovieIds: Set<Int> = emptySet(),
    val excludedSeriesIds: Set<Int> = emptySet()
)

/**
 * Builds a [GenreProfile] from favorites, watch progress, watched history and ratings,
 * per the scoring spec in .hermes-plan/task_plan.md.
 */
object UserContentProfile {
    private const val WEIGHT_FAVORITE = 3.0
    private const val WEIGHT_SERIES_ACTIVE = 2.5
    private const val WEIGHT_SERIES_STARTED = 1.0
    private const val WEIGHT_MOVIE_COMPLETED = 1.5
    private const val WEIGHT_MOVIE_ABANDONED = -1.0
    private const val WEIGHT_THUMB_UP = 1.0
    private const val WEIGHT_THUMB_DOWN = -2.0

    private const val SERIES_ACTIVE_MIN_STARTED_EPISODES = 5
    private const val SERIES_ACTIVE_MIN_COMPLETED_EPISODES = 3
    private const val EPISODE_STARTED_THRESHOLD = 0.2
    private const val EPISODE_COMPLETED_THRESHOLD = 0.9
    private const val MOVIE_COMPLETED_THRESHOLD = 0.9
    private const val MOVIE_ABANDONED_MAX_PROGRESS = 0.2

    private const val AGE_DECAY_DIVISOR_DAYS = 90.0
    private const val AGE_DECAY_MIN_FACTOR = 0.3
    private const val MS_PER_DAY = 24.0 * 60.0 * 60.0 * 1000.0

    fun build(
        favorites: List<Favorite>,
        watchedHistory: Map<String, WatchedItem>,
        watchProgress: Map<String, WatchProgress>,
        ratings: Map<String, RatingEntry>,
        movies: List<VodStream>,
        series: List<Series>,
        nowMs: Long = System.currentTimeMillis()
    ): GenreProfile {
        val moviesById = movies.associateBy { it.streamId }
        val seriesById = series.associateBy { it.seriesId }

        val weights = mutableMapOf<String, Double>()
        val coreSignalKeys = mutableSetOf<String>()
        val excludedMovieIds = mutableSetOf<Int>()
        val excludedSeriesIds = mutableSetOf<Int>()

        applyFavoriteSignals(favorites, moviesById, seriesById, nowMs, weights, coreSignalKeys, excludedMovieIds, excludedSeriesIds)
        applyWatchedMovieExclusions(watchedHistory, excludedMovieIds)
        applyMovieProgressSignals(watchProgress, watchedHistory, moviesById, nowMs, weights, coreSignalKeys, excludedMovieIds)
        applySeriesProgressSignals(watchProgress, seriesById, nowMs, weights, coreSignalKeys, excludedSeriesIds)
        applyRatingSignals(ratings, moviesById, seriesById, nowMs, weights)

        return GenreProfile(
            weights = weights,
            signalCount = coreSignalKeys.size,
            excludedMovieIds = excludedMovieIds,
            excludedSeriesIds = excludedSeriesIds
        )
    }

    private fun applyFavoriteSignals(
        favorites: List<Favorite>,
        moviesById: Map<Int, VodStream>,
        seriesById: Map<Int, Series>,
        nowMs: Long,
        weights: MutableMap<String, Double>,
        coreSignalKeys: MutableSet<String>,
        excludedMovieIds: MutableSet<Int>,
        excludedSeriesIds: MutableSet<Int>
    ) {
        favorites.forEach { favorite ->
            when (favorite.type) {
                FavoriteType.MOVIE -> {
                    excludedMovieIds += favorite.streamId
                    val vod = moviesById[favorite.streamId] ?: return@forEach
                    addGenreWeights(weights, GenreNormalizer.normalize(vod.genre), WEIGHT_FAVORITE, parseInstantMs(favorite.addedAt), nowMs)
                    coreSignalKeys += "fav_movie_${favorite.streamId}"
                }
                FavoriteType.SERIES -> {
                    excludedSeriesIds += favorite.streamId
                    val show = seriesById[favorite.streamId] ?: return@forEach
                    addGenreWeights(weights, GenreNormalizer.normalize(show.genre), WEIGHT_FAVORITE, parseInstantMs(favorite.addedAt), nowMs)
                    coreSignalKeys += "fav_series_${favorite.streamId}"
                }
                FavoriteType.LIVE -> Unit
            }
        }
    }

    private fun applyWatchedMovieExclusions(
        watchedHistory: Map<String, WatchedItem>,
        excludedMovieIds: MutableSet<Int>
    ) {
        watchedHistory.keys.forEach { key ->
            if (key.startsWith(UiConstants.PROGRESS_KEY_MOVIE_PREFIX)) {
                key.removePrefix(UiConstants.PROGRESS_KEY_MOVIE_PREFIX).toIntOrNull()?.let { excludedMovieIds += it }
            }
        }
    }

    private fun applyMovieProgressSignals(
        watchProgress: Map<String, WatchProgress>,
        watchedHistory: Map<String, WatchedItem>,
        moviesById: Map<Int, VodStream>,
        nowMs: Long,
        weights: MutableMap<String, Double>,
        coreSignalKeys: MutableSet<String>,
        excludedMovieIds: MutableSet<Int>
    ) {
        watchProgress.forEach { (key, progress) ->
            if (!key.startsWith(UiConstants.PROGRESS_KEY_MOVIE_PREFIX)) return@forEach
            val streamId = key.removePrefix(UiConstants.PROGRESS_KEY_MOVIE_PREFIX).toIntOrNull() ?: return@forEach
            val vod = moviesById[streamId] ?: return@forEach
            val ratio = progressRatio(progress)
            val ageMs = parseInstantMs(progress.lastWatched)
            when {
                ratio >= MOVIE_COMPLETED_THRESHOLD -> {
                    excludedMovieIds += streamId
                    addGenreWeights(weights, GenreNormalizer.normalize(vod.genre), WEIGHT_MOVIE_COMPLETED, ageMs, nowMs)
                    coreSignalKeys += "movie_done_$streamId"
                }
                ratio > 0.0 && ratio < MOVIE_ABANDONED_MAX_PROGRESS && key !in watchedHistory -> {
                    addGenreWeights(weights, GenreNormalizer.normalize(vod.genre), WEIGHT_MOVIE_ABANDONED, ageMs, nowMs)
                }
            }
        }
    }

    /**
     * Groups episode progress by series id via [groupBy] (immutable [Map]/[List]) instead of an
     * imperative mutable accumulator - same grouping, same "key must split into exactly 2 parts"
     * filter as before.
     */
    private fun applySeriesProgressSignals(
        watchProgress: Map<String, WatchProgress>,
        seriesById: Map<Int, Series>,
        nowMs: Long,
        weights: MutableMap<String, Double>,
        coreSignalKeys: MutableSet<String>,
        excludedSeriesIds: MutableSet<Int>
    ) {
        val episodeProgressBySeriesId: Map<Int, List<WatchProgress>> = watchProgress.entries
            .filter { (key, _) -> key.startsWith(UiConstants.PROGRESS_KEY_SERIES_PREFIX) }
            .mapNotNull { (key, progress) ->
                val parts = key.removePrefix(UiConstants.PROGRESS_KEY_SERIES_PREFIX).split(":")
                if (parts.size != 2) return@mapNotNull null
                val seriesId = parts[0].toIntOrNull() ?: return@mapNotNull null
                seriesId to progress
            }
            .groupBy({ it.first }, { it.second })

        episodeProgressBySeriesId.forEach { (seriesId, episodeProgresses) ->
            excludedSeriesIds += seriesId
            val show = seriesById[seriesId] ?: return@forEach

            val started = episodeProgresses.filter { progressRatio(it) >= EPISODE_STARTED_THRESHOLD }
            val completed = episodeProgresses.filter { progressRatio(it) >= EPISODE_COMPLETED_THRESHOLD }
            val isActive = started.size >= SERIES_ACTIVE_MIN_STARTED_EPISODES ||
                completed.size >= SERIES_ACTIVE_MIN_COMPLETED_EPISODES

            when {
                isActive -> {
                    val mostRecentMs = started.maxOfOrNull { parseInstantMs(it.lastWatched) ?: Long.MIN_VALUE }
                        ?.takeIf { it != Long.MIN_VALUE }
                    addGenreWeights(weights, GenreNormalizer.normalize(show.genre), WEIGHT_SERIES_ACTIVE, mostRecentMs, nowMs)
                    coreSignalKeys += "series_active_$seriesId"
                }
                started.size in 1..4 -> {
                    val mostRecentMs = started.maxOfOrNull { parseInstantMs(it.lastWatched) ?: Long.MIN_VALUE }
                        ?.takeIf { it != Long.MIN_VALUE }
                    addGenreWeights(weights, GenreNormalizer.normalize(show.genre), WEIGHT_SERIES_STARTED, mostRecentMs, nowMs)
                }
            }
        }
    }

    private fun applyRatingSignals(
        ratings: Map<String, RatingEntry>,
        moviesById: Map<Int, VodStream>,
        seriesById: Map<Int, Series>,
        nowMs: Long,
        weights: MutableMap<String, Double>
    ) {
        ratings.values.forEach { entry ->
            val genres = when (entry.contentType) {
                "movie" -> moviesById[entry.contentId]?.genre
                "series" -> seriesById[entry.contentId]?.genre
                else -> null
            }?.let { GenreNormalizer.normalize(it) } ?: return@forEach

            val weight = if (entry.rating == 1) WEIGHT_THUMB_UP else WEIGHT_THUMB_DOWN
            addGenreWeights(weights, genres, weight, parseInstantMs(entry.ratedAt), nowMs)
        }
    }

    private fun addGenreWeights(
        weights: MutableMap<String, Double>,
        genres: List<String>,
        weight: Double,
        signalMs: Long?,
        nowMs: Long
    ) {
        if (genres.isEmpty()) return
        val weighted = weight * ageDecayFactor(nowMs, signalMs)
        genres.forEach { genre -> weights[genre] = (weights[genre] ?: 0.0) + weighted }
    }

    private fun progressRatio(progress: WatchProgress): Double {
        if (progress.duration <= 0) return 0.0
        return (progress.position.toDouble() / progress.duration.toDouble()).coerceIn(0.0, 1.0)
    }

    private fun parseInstantMs(raw: String?): Long? {
        if (raw == null) return null
        return try {
            Instant.parse(raw).toEpochMilli()
        } catch (e: Exception) {
            null
        }
    }

    private fun ageDecayFactor(nowMs: Long, signalMs: Long?): Double {
        if (signalMs == null) return 1.0
        val ageDays = (nowMs - signalMs).toDouble() / MS_PER_DAY
        if (ageDays <= 0.0) return 1.0
        return max(AGE_DECAY_MIN_FACTOR, exp(-ageDays / AGE_DECAY_DIVISOR_DAYS))
    }
}
