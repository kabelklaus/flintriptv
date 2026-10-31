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

    @Suppress("S3776") // scoring spec (.hermes-plan/task_plan.md) ties these signal blocks to shared accumulators; splitting risks subtly changing the formula
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

        fun addGenreWeights(genres: List<String>, weight: Double, signalMs: Long?) {
            if (genres.isEmpty()) return
            val weighted = weight * ageDecayFactor(nowMs, signalMs)
            genres.forEach { genre -> weights[genre] = (weights[genre] ?: 0.0) + weighted }
        }

        favorites.forEach { favorite ->
            when (favorite.type) {
                FavoriteType.MOVIE -> {
                    excludedMovieIds += favorite.streamId
                    val vod = moviesById[favorite.streamId] ?: return@forEach
                    addGenreWeights(GenreNormalizer.normalize(vod.genre), WEIGHT_FAVORITE, parseInstantMs(favorite.addedAt))
                    coreSignalKeys += "fav_movie_${favorite.streamId}"
                }
                FavoriteType.SERIES -> {
                    excludedSeriesIds += favorite.streamId
                    val show = seriesById[favorite.streamId] ?: return@forEach
                    addGenreWeights(GenreNormalizer.normalize(show.genre), WEIGHT_FAVORITE, parseInstantMs(favorite.addedAt))
                    coreSignalKeys += "fav_series_${favorite.streamId}"
                }
                FavoriteType.LIVE -> Unit
            }
        }

        watchedHistory.keys.forEach { key ->
            if (key.startsWith(UiConstants.PROGRESS_KEY_MOVIE_PREFIX)) {
                key.removePrefix(UiConstants.PROGRESS_KEY_MOVIE_PREFIX).toIntOrNull()?.let { excludedMovieIds += it }
            }
        }

        watchProgress.forEach { (key, progress) ->
            if (!key.startsWith(UiConstants.PROGRESS_KEY_MOVIE_PREFIX)) return@forEach
            val streamId = key.removePrefix(UiConstants.PROGRESS_KEY_MOVIE_PREFIX).toIntOrNull() ?: return@forEach
            val vod = moviesById[streamId] ?: return@forEach
            val ratio = progressRatio(progress)
            val ageMs = parseInstantMs(progress.lastWatched)
            when {
                ratio >= MOVIE_COMPLETED_THRESHOLD -> {
                    excludedMovieIds += streamId
                    addGenreWeights(GenreNormalizer.normalize(vod.genre), WEIGHT_MOVIE_COMPLETED, ageMs)
                    coreSignalKeys += "movie_done_$streamId"
                }
                ratio > 0.0 && ratio < MOVIE_ABANDONED_MAX_PROGRESS && key !in watchedHistory -> {
                    addGenreWeights(GenreNormalizer.normalize(vod.genre), WEIGHT_MOVIE_ABANDONED, ageMs)
                }
            }
        }

        @Suppress("S6524") // mutated below via getOrPut(...).add(progress); immutable collection is not viable here
        val episodeProgressBySeriesId = mutableMapOf<Int, MutableList<WatchProgress>>()
        watchProgress.forEach { (key, progress) ->
            if (!key.startsWith(UiConstants.PROGRESS_KEY_SERIES_PREFIX)) return@forEach
            val parts = key.removePrefix(UiConstants.PROGRESS_KEY_SERIES_PREFIX).split(":")
            if (parts.size != 2) return@forEach
            val seriesId = parts[0].toIntOrNull() ?: return@forEach
            episodeProgressBySeriesId.getOrPut(seriesId) { mutableListOf() }.add(progress)
        }

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
                    addGenreWeights(GenreNormalizer.normalize(show.genre), WEIGHT_SERIES_ACTIVE, mostRecentMs)
                    coreSignalKeys += "series_active_$seriesId"
                }
                started.size in 1..4 -> {
                    val mostRecentMs = started.maxOfOrNull { parseInstantMs(it.lastWatched) ?: Long.MIN_VALUE }
                        ?.takeIf { it != Long.MIN_VALUE }
                    addGenreWeights(GenreNormalizer.normalize(show.genre), WEIGHT_SERIES_STARTED, mostRecentMs)
                }
            }
        }

        ratings.values.forEach { entry ->
            val genres = when (entry.contentType) {
                "movie" -> moviesById[entry.contentId]?.genre
                "series" -> seriesById[entry.contentId]?.genre
                else -> null
            }?.let { GenreNormalizer.normalize(it) } ?: return@forEach

            val weight = if (entry.rating == 1) WEIGHT_THUMB_UP else WEIGHT_THUMB_DOWN
            addGenreWeights(genres, weight, parseInstantMs(entry.ratedAt))
        }

        return GenreProfile(
            weights = weights,
            signalCount = coreSignalKeys.size,
            excludedMovieIds = excludedMovieIds,
            excludedSeriesIds = excludedSeriesIds
        )
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
