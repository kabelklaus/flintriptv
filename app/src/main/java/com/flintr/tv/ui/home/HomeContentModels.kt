package com.flintr.tv.ui.home

import com.flintr.tv.data.model.Favorite
import com.flintr.tv.data.model.FavoriteType
import com.flintr.tv.data.model.LiveStream
import com.flintr.tv.data.model.Series
import com.flintr.tv.data.model.VodStream
import com.flintr.tv.data.model.WatchProgress
import com.flintr.tv.utils.ContentParser
import java.time.Instant

private const val FEATURED_MOVIE_LIMIT = 5
private const val FEATURED_SERIES_LIMIT = 5
private const val MOVIE_PROGRESS_PREFIX = "movie:"
private const val SERIES_PROGRESS_PREFIX = "series:"

enum class HomeItemType {
    LIVE,
    MOVIE,
    SERIES
}

sealed class HomeFeaturedItem {
    abstract val title: String
    abstract val description: String?
    abstract val rating: String?
    abstract val imageUrl: String?
    abstract val timestamp: Long
    abstract val ratingValue: Double

    data class Movie(val vod: VodStream) : HomeFeaturedItem() {
        override val title: String = vod.name
        override val description: String? = vod.plot
        override val rating: String? = ContentParser.getRating(vod)
        override val imageUrl: String? = vod.backdropPath.firstUsable() ?: vod.cover.usableOrNull() ?: vod.streamIcon.usableOrNull()
        override val timestamp: Long = parseUnixTimestamp(vod.added)
        override val ratingValue: Double = rating.parseRating()
    }

    data class SeriesItem(val series: Series) : HomeFeaturedItem() {
        override val title: String = series.name
        override val description: String? = series.plot
        override val rating: String? = ContentParser.getRating(series)
        override val imageUrl: String? = series.backdropPath.firstUsable() ?: series.cover.usableOrNull()
        override val timestamp: Long = parseUnixTimestamp(series.lastModified)
        override val ratingValue: Double = rating.parseRating()
    }
}

sealed class HomeRowItem {
    abstract val type: HomeItemType
    abstract val sortTimestamp: Long

    data class Live(
        val stream: LiveStream,
        override val sortTimestamp: Long
    ) : HomeRowItem() {
        override val type: HomeItemType = HomeItemType.LIVE
    }

    data class Movie(
        val vod: VodStream,
        override val sortTimestamp: Long
    ) : HomeRowItem() {
        override val type: HomeItemType = HomeItemType.MOVIE
    }

    data class SeriesItem(
        val series: Series,
        override val sortTimestamp: Long
    ) : HomeRowItem() {
        override val type: HomeItemType = HomeItemType.SERIES
    }
}

fun selectFeaturedItems(
    movies: List<VodStream>,
    series: List<Series>,
    hiddenVodCategoryIds: Set<String>,
    hiddenSeriesCategoryIds: Set<String>
): List<HomeFeaturedItem> {
    val movieItems = movies
        .filterNotHidden(hiddenVodCategoryIds) { it.categoryId }
        .map { HomeFeaturedItem.Movie(it) }
        .sortedForHome()
        .take(FEATURED_MOVIE_LIMIT)

    val seriesItems = series
        .filterNotHidden(hiddenSeriesCategoryIds) { it.categoryId }
        .map { HomeFeaturedItem.SeriesItem(it) }
        .sortedForHome()
        .take(FEATURED_SERIES_LIMIT)

    return (movieItems + seriesItems).sortedForHome()
}

fun selectInProgressItems(
    allProgress: Map<String, WatchProgress>,
    movies: List<VodStream>,
    series: List<Series>
): List<HomeRowItem> {
    val moviesById = movies.associateBy { it.streamId }
    val seriesById = series.associateBy { it.seriesId }
    val movieItems = allProgress.mapNotNull { (key, progress) ->
        val streamId = key.removePrefixOrNull(MOVIE_PROGRESS_PREFIX)?.toIntOrNull() ?: return@mapNotNull null
        moviesById[streamId]?.let {
            HomeRowItem.Movie(it, parseIsoTimestamp(progress.lastWatched))
        }
    }

    val newestSeriesById = allProgress.mapNotNull { (key, progress) ->
        val seriesId = parseSeriesProgressKey(key) ?: return@mapNotNull null
        val seriesItem = seriesById[seriesId] ?: return@mapNotNull null
        HomeRowItem.SeriesItem(seriesItem, parseIsoTimestamp(progress.lastWatched))
    }.groupBy { it.series.seriesId }
        .values
        .mapNotNull { items -> items.maxByOrNull { it.sortTimestamp } }

    return (movieItems + newestSeriesById).sortedByDescending { it.sortTimestamp }
}

fun selectFavoriteItems(
    favorites: List<Favorite>,
    channels: List<LiveStream>,
    movies: List<VodStream>,
    series: List<Series>
): List<HomeRowItem> {
    val channelsById = channels.associateBy { it.streamId }
    val moviesById = movies.associateBy { it.streamId }
    val seriesById = series.associateBy { it.seriesId }

    return favorites.mapNotNull { favorite ->
        val timestamp = parseIsoTimestamp(favorite.addedAt)
        when (favorite.type) {
            FavoriteType.LIVE -> channelsById[favorite.streamId]?.let { HomeRowItem.Live(it, timestamp) }
            FavoriteType.MOVIE -> moviesById[favorite.streamId]?.let { HomeRowItem.Movie(it, timestamp) }
            FavoriteType.SERIES -> seriesById[favorite.streamId]?.let { HomeRowItem.SeriesItem(it, timestamp) }
        }
    }.sortedByDescending { it.sortTimestamp }
}

fun List<String?>?.firstUsable(): String? =
    this?.firstOrNull { !it.isNullOrBlank() }

private fun String?.usableOrNull(): String? =
    takeUnless { it.isNullOrBlank() }

private fun <T> List<T>.filterNotHidden(
    hiddenCategoryIds: Set<String>,
    categoryIdSelector: (T) -> String?
): List<T> {
    if (hiddenCategoryIds.isEmpty()) return this
    return filter { item ->
        val categoryId = categoryIdSelector(item)
        categoryId == null || categoryId !in hiddenCategoryIds
    }
}

private fun List<HomeFeaturedItem>.sortedForHome(): List<HomeFeaturedItem> =
    sortedWith(
        compareByDescending<HomeFeaturedItem> { it.timestamp }
            .thenByDescending { it.ratingValue }
    )

private fun String.removePrefixOrNull(prefix: String): String? =
    if (startsWith(prefix)) removePrefix(prefix) else null

private fun parseSeriesProgressKey(key: String): Int? {
    val parts = key.removePrefixOrNull(SERIES_PROGRESS_PREFIX)?.split(":") ?: return null
    if (parts.size != 2) return null
    return parts[0].toIntOrNull()
}

private fun parseUnixTimestamp(value: String?): Long =
    value?.toLongOrNull() ?: 0L

private fun parseIsoTimestamp(value: String?): Long =
    try {
        if (value == null) 0L else Instant.parse(value).toEpochMilli()
    } catch (_: Exception) {
        0L
    }

private fun String?.parseRating(): Double =
    this?.toDoubleOrNull() ?: 0.0
