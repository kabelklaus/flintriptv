package com.flintr.tv.domain.recommendation

import com.flintr.tv.data.model.WatchlistEntry
import com.flintr.tv.data.model.WatchlistEntryType
import com.flintr.tv.data.model.RatingEntry
import com.flintr.tv.data.model.Series
import com.flintr.tv.data.model.VodStream
import com.flintr.tv.data.model.WatchProgress
import com.flintr.tv.data.model.WatchedItem
import com.flintr.tv.data.tmdb.TmdbMeta
import com.flintr.tv.ui.common.UiConstants
import java.time.Instant
import kotlin.math.exp
import kotlin.math.max

/**
 * Genre-Gewichte (Key = TMDB-Genre-ID) aus den Nutzer-Signalen, plus die IDs, die nie
 * empfohlen werden duerfen (Watchlist, abgeschlossen, angefangen).
 */
data class GenreProfile(
    val weights: Map<Int, Double>,
    val signalCount: Int,
    val excludedMovieIds: Set<Int> = emptySet(),
    val excludedSeriesIds: Set<Int> = emptySet()
)

/**
 * Baut ein [GenreProfile] aus Watchlist, Fortschritt, Verlauf und Ratings.
 *
 * Genre-Signale kommen ausschliesslich aus [TmdbMeta.genres] (TMDB-IDs) via [metaLookup].
 * Items ohne Meta liefern kein Genre-Signal; Xtream-`genre`/`rating` werden nicht gelesen.
 * Der Lookup ist synchron und darf stale sein (der Aufrufer liest den Meta-Flow).
 */
object UserContentProfile {
    private const val WEIGHT_WATCHLIST = 3.0
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

    /**
     * Bundles [applyWatchlistSignals]'s mutable accumulators (same backing collections as the
     * caller's locals) so the function stays under the parameter-count limit.
     */
    private data class ProfileAccumulator(
        val weights: MutableMap<Int, Double>,
        val coreSignalKeys: MutableSet<String>,
        val excludedMovieIds: MutableSet<Int>,
        val excludedSeriesIds: MutableSet<Int>
    )

    fun build(
        watchlistEntries: List<WatchlistEntry>,
        watchedHistory: Map<String, WatchedItem>,
        watchProgress: Map<String, WatchProgress>,
        ratings: Map<String, RatingEntry>,
        movies: List<VodStream>,
        series: List<Series>,
        metaLookup: (kind: String, contentId: Int) -> TmdbMeta?,
        nowMs: Long = System.currentTimeMillis()
    ): GenreProfile {
        val moviesById = movies.associateBy { it.streamId }
        val seriesById = series.associateBy { it.seriesId }

        val weights = mutableMapOf<Int, Double>()
        val coreSignalKeys = mutableSetOf<String>()
        val excludedMovieIds = mutableSetOf<Int>()
        val excludedSeriesIds = mutableSetOf<Int>()

        applyWatchlistSignals(
            watchlistEntries,
            moviesById,
            seriesById,
            nowMs,
            metaLookup,
            ProfileAccumulator(weights, coreSignalKeys, excludedMovieIds, excludedSeriesIds)
        )
        applyWatchedMovieExclusions(watchedHistory, excludedMovieIds)
        applyMovieProgressSignals(watchProgress, watchedHistory, moviesById, nowMs, metaLookup, weights, coreSignalKeys, excludedMovieIds)
        applySeriesProgressSignals(watchProgress, seriesById, nowMs, metaLookup, weights, coreSignalKeys, excludedSeriesIds)
        applyRatingSignals(ratings, moviesById, seriesById, nowMs, metaLookup, weights)

        return GenreProfile(
            weights = weights,
            signalCount = coreSignalKeys.size,
            excludedMovieIds = excludedMovieIds,
            excludedSeriesIds = excludedSeriesIds
        )
    }

    private fun applyWatchlistSignals(
        watchlistEntries: List<WatchlistEntry>,
        moviesById: Map<Int, VodStream>,
        seriesById: Map<Int, Series>,
        nowMs: Long,
        metaLookup: (kind: String, contentId: Int) -> TmdbMeta?,
        accumulator: ProfileAccumulator
    ) {
        watchlistEntries.forEach { entry ->
            when (entry.type) {
                WatchlistEntryType.MOVIE -> {
                    accumulator.excludedMovieIds += entry.streamId
                    val vod = moviesById[entry.streamId] ?: return@forEach
                    addGenreWeights(accumulator.weights, genreIds(metaLookup, "movie", vod.streamId), WEIGHT_WATCHLIST, parseInstantMs(entry.addedAt), nowMs)
                    accumulator.coreSignalKeys += "fav_movie_${entry.streamId}"
                }
                WatchlistEntryType.SERIES -> {
                    accumulator.excludedSeriesIds += entry.streamId
                    val show = seriesById[entry.streamId] ?: return@forEach
                    addGenreWeights(accumulator.weights, genreIds(metaLookup, "series", show.seriesId), WEIGHT_WATCHLIST, parseInstantMs(entry.addedAt), nowMs)
                    accumulator.coreSignalKeys += "fav_series_${entry.streamId}"
                }
                WatchlistEntryType.LIVE -> Unit
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
        metaLookup: (kind: String, contentId: Int) -> TmdbMeta?,
        weights: MutableMap<Int, Double>,
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
                    addGenreWeights(weights, genreIds(metaLookup, "movie", streamId), WEIGHT_MOVIE_COMPLETED, ageMs, nowMs)
                    coreSignalKeys += "movie_done_$streamId"
                }
                ratio > 0.0 && ratio < MOVIE_ABANDONED_MAX_PROGRESS && key !in watchedHistory -> {
                    addGenreWeights(weights, genreIds(metaLookup, "movie", streamId), WEIGHT_MOVIE_ABANDONED, ageMs, nowMs)
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
        metaLookup: (kind: String, contentId: Int) -> TmdbMeta?,
        weights: MutableMap<Int, Double>,
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
                    addGenreWeights(weights, genreIds(metaLookup, "series", seriesId), WEIGHT_SERIES_ACTIVE, mostRecentMs, nowMs)
                    coreSignalKeys += "series_active_$seriesId"
                }
                started.size in 1..4 -> {
                    val mostRecentMs = started.maxOfOrNull { parseInstantMs(it.lastWatched) ?: Long.MIN_VALUE }
                        ?.takeIf { it != Long.MIN_VALUE }
                    addGenreWeights(weights, genreIds(metaLookup, "series", seriesId), WEIGHT_SERIES_STARTED, mostRecentMs, nowMs)
                }
            }
        }
    }

    private fun applyRatingSignals(
        ratings: Map<String, RatingEntry>,
        moviesById: Map<Int, VodStream>,
        seriesById: Map<Int, Series>,
        nowMs: Long,
        metaLookup: (kind: String, contentId: Int) -> TmdbMeta?,
        weights: MutableMap<Int, Double>
    ) {
        ratings.values.forEach { entry ->
            val genres = when (entry.contentType) {
                "movie" -> genreIds(metaLookup, "movie", entry.contentId)
                "series" -> genreIds(metaLookup, "series", entry.contentId)
                else -> return@forEach
            }
            if (genres.isEmpty()) return@forEach

            val weight = if (entry.rating == 1) WEIGHT_THUMB_UP else WEIGHT_THUMB_DOWN
            addGenreWeights(weights, genres, weight, parseInstantMs(entry.ratedAt), nowMs)
        }
    }

    private fun genreIds(
        metaLookup: (kind: String, contentId: Int) -> TmdbMeta?,
        kind: String,
        contentId: Int
    ): List<Int> = metaLookup(kind, contentId)?.genres.orEmpty().map { it.id }

    private fun addGenreWeights(
        weights: MutableMap<Int, Double>,
        genres: List<Int>,
        weight: Double,
        signalMs: Long?,
        nowMs: Long
    ) {
        if (genres.isEmpty()) return
        val weighted = weight * ageDecayFactor(nowMs, signalMs)
        genres.forEach { genreId -> weights[genreId] = (weights[genreId] ?: 0.0) + weighted }
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
