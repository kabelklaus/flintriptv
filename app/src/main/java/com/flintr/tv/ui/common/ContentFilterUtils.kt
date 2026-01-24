package com.flintr.tv.ui.common

import com.flintr.tv.data.model.LiveStream
import com.flintr.tv.data.model.Series
import com.flintr.tv.data.model.VodStream
import java.util.concurrent.TimeUnit

/**
 * Utility functions for filtering content by recency.
 * Provides generic filtering logic for content added/modified in the last 7 days.
 */
object ContentFilterUtils {
    private val SEVEN_DAYS_IN_SECONDS = TimeUnit.DAYS.toSeconds(7)

    /**
     * Calculates the timestamp for 7 days ago in seconds.
     */
    fun sevenDaysAgoInSeconds(): Long {
        val currentTimestamp = System.currentTimeMillis() / 1000
        return currentTimestamp - SEVEN_DAYS_IN_SECONDS
    }

    /**
     * Generic function to filter content items by timestamp.
     * 
     * @param items The list of items to filter
     * @param timestampSelector Function to extract the timestamp string from an item
     * @return Filtered list containing only items added/modified in the last 7 days
     */
    fun <T> filterRecentContent(
        items: List<T>,
        timestampSelector: (T) -> String?
    ): List<T> {
        val sevenDaysAgo = sevenDaysAgoInSeconds()
        return items.filter { item ->
            timestampSelector(item)?.toLongOrNull()?.let { timestamp ->
                timestamp >= sevenDaysAgo
            } ?: false
        }
    }

    /**
     * Filters LiveStream items that were added in the last 7 days.
     */
    fun filterRecentStreams(streams: List<LiveStream>): List<LiveStream> {
        return filterRecentContent(streams) { it.added }
    }

    /**
     * Filters VodStream items that were added in the last 7 days.
     */
    fun filterRecentMovies(movies: List<VodStream>): List<VodStream> {
        return filterRecentContent(movies) { it.added }
    }

    /**
     * Filters Series items that were modified in the last 7 days.
     * Note: Series uses lastModified instead of added.
     */
    fun filterRecentSeries(series: List<Series>): List<Series> {
        return filterRecentContent(series) { it.lastModified }
    }

    /**
     * Generic function to filter items by excluding those from hidden categories.
     * Items without categoryId are not filtered (remain visible).
     * 
     * @param items The list of items to filter
     * @param hiddenCategoryIds Set of category IDs that should be hidden
     * @param categoryIdSelector Function to extract the categoryId from an item
     * @return Filtered list excluding items from hidden categories
     */
    private fun <T> filterByHiddenCategories(
        items: List<T>,
        hiddenCategoryIds: Set<String>,
        categoryIdSelector: (T) -> String?
    ): List<T> {
        if (hiddenCategoryIds.isEmpty()) {
            return items
        }
        return items.filter { item ->
            val categoryId = categoryIdSelector(item)
            categoryId == null || !hiddenCategoryIds.contains(categoryId)
        }
    }

    /**
     * Filters LiveStream items by excluding those from hidden categories.
     * Streams without categoryId are not filtered (remain visible).
     * 
     * @param streams The list of streams to filter
     * @param hiddenCategoryIds Set of category IDs that should be hidden
     * @return Filtered list excluding streams from hidden categories
     */
    fun filterLiveStreamsByHiddenCategories(
        streams: List<LiveStream>,
        hiddenCategoryIds: Set<String>
    ): List<LiveStream> {
        return filterByHiddenCategories(streams, hiddenCategoryIds) { it.categoryId }
    }

    /**
     * Filters VodStream items by excluding those from hidden categories.
     * Streams without categoryId are not filtered (remain visible).
     * 
     * @param movies The list of movies to filter
     * @param hiddenCategoryIds Set of category IDs that should be hidden
     * @return Filtered list excluding movies from hidden categories
     */
    fun filterVodStreamsByHiddenCategories(
        movies: List<VodStream>,
        hiddenCategoryIds: Set<String>
    ): List<VodStream> {
        return filterByHiddenCategories(movies, hiddenCategoryIds) { it.categoryId }
    }

    /**
     * Filters Series items by excluding those from hidden categories.
     * Series without categoryId are not filtered (remain visible).
     * 
     * @param series The list of series to filter
     * @param hiddenCategoryIds Set of category IDs that should be hidden
     * @return Filtered list excluding series from hidden categories
     */
    fun filterSeriesByHiddenCategories(
        series: List<Series>,
        hiddenCategoryIds: Set<String>
    ): List<Series> {
        return filterByHiddenCategories(series, hiddenCategoryIds) { it.categoryId }
    }
}
