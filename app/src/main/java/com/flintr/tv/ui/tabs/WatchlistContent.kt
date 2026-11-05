package com.flintr.tv.ui.tabs

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import android.util.Log
import com.flintr.tv.data.local.DataCacheService
import com.flintr.tv.data.local.WatchlistRepository
import com.flintr.tv.data.model.WatchlistEntry
import com.flintr.tv.data.model.WatchlistEntryType
import com.flintr.tv.data.model.LiveStream
import com.flintr.tv.data.model.Series
import com.flintr.tv.data.model.VodStream
import com.flintr.tv.ui.components.CardConfig
import com.flintr.tv.ui.components.LiveStreamCard
import com.flintr.tv.ui.components.LiveStreamCardParams
import com.flintr.tv.ui.components.VODCard
import com.flintr.tv.ui.components.VODCardParams
import com.flintr.tv.ui.sidebars.ContentGridLayout
import com.flintr.tv.ui.sidebars.ContentGridLayoutParams

// Sealed class to represent different watchlist content types
private sealed class WatchlistContentItem {
    data class Live(val stream: LiveStream, val entry: WatchlistEntry) : WatchlistContentItem()
    data class Movie(val vod: VodStream, val entry: WatchlistEntry) : WatchlistContentItem()
    data class SeriesItem(val series: Series, val entry: WatchlistEntry) : WatchlistContentItem()
}

/**
 * Parameters for WatchlistContent composable.
 * Encapsulates all parameters to reduce function parameter count.
 */
data class WatchlistContentParams(
    val watchlistRepository: WatchlistRepository,
    val cacheService: DataCacheService,
    val focusRequester: FocusRequester,
    val onNavigateLeft: () -> Unit,
    val onLiveStreamClick: (LiveStream) -> Unit,
    val onVodClick: (VodStream) -> Unit,
    val onSeriesClick: (Series) -> Unit,
    val lazyGridState: LazyGridState,
    val focusRequesterMap: MutableMap<String, FocusRequester>? = null
)


/**
 * Creates the onNavigateLeft callback based on column index.
 */
private fun createOnNavigateLeft(
    columnIndex: Int,
    onNavigateLeft: () -> Unit
): (() -> Unit)? {
    return if (columnIndex == 0) {
        { onNavigateLeft() }
    } else {
        null
    }
}

/**
 * Data class to hold loaded watchlist data.
 */
private data class WatchlistData(
    val channels: List<LiveStream>,
    val movies: List<VodStream>,
    val series: List<Series>
)

/**
 * Loads watchlist data (channels, movies, series) based on watchlist entry IDs.
 */
private suspend fun loadWatchlistData(
    entries: List<WatchlistEntry>,
    cacheService: DataCacheService
): WatchlistData {
    val liveStreamIds = entries.filter { it.type == WatchlistEntryType.LIVE }.mapNotNull { it.streamId }
    val movieStreamIds = entries.filter { it.type == WatchlistEntryType.MOVIE }.mapNotNull { it.streamId }
    val seriesIds = entries.filter { it.type == WatchlistEntryType.SERIES }.mapNotNull { it.streamId }

    val channels = if (liveStreamIds.isNotEmpty()) {
        cacheService.loadChannelsByIds(liveStreamIds) ?: emptyList()
    } else {
        emptyList()
    }

    val movies = if (movieStreamIds.isNotEmpty()) {
        cacheService.loadMoviesByIds(movieStreamIds) ?: emptyList()
    } else {
        emptyList()
    }

    val series = if (seriesIds.isNotEmpty()) {
        cacheService.loadSeriesByIds(seriesIds) ?: emptyList()
    } else {
        emptyList()
    }

    return WatchlistData(channels, movies, series)
}

/**
 * Matches live stream watchlist entries with loaded channels.
 */
private fun matchLiveStreamEntries(
    entries: List<WatchlistEntry>,
    channelsMap: Map<Int, LiveStream>
): List<WatchlistContentItem.Live> {
    return entries
        .filter { it.type == WatchlistEntryType.LIVE }
        .mapNotNull { entry ->
            channelsMap[entry.streamId]?.let { stream ->
                WatchlistContentItem.Live(stream, entry)
            } ?: run {
                Log.w("FavoritesContent", "LiveStream not found for favorite: ${entry.id}")
                null
            }
        }
}

/**
 * Matches movie watchlist entries with loaded movies.
 */
private fun matchMovieEntries(
    entries: List<WatchlistEntry>,
    moviesMap: Map<Int, VodStream>
): List<WatchlistContentItem.Movie> {
    return entries
        .filter { it.type == WatchlistEntryType.MOVIE }
        .mapNotNull { entry ->
            moviesMap[entry.streamId]?.let { vod ->
                WatchlistContentItem.Movie(vod, entry)
            } ?: run {
                Log.w("FavoritesContent", "VodStream not found for favorite: ${entry.id}")
                null
            }
        }
}

/**
 * Matches series watchlist entries with loaded series.
 */
private fun matchSeriesEntries(
    entries: List<WatchlistEntry>,
    seriesMap: Map<Int, Series>
): List<WatchlistContentItem.SeriesItem> {
    return entries
        .filter { it.type == WatchlistEntryType.SERIES }
        .mapNotNull { entry ->
            seriesMap[entry.streamId]?.let { seriesItem ->
                WatchlistContentItem.SeriesItem(seriesItem, entry)
            } ?: run {
                Log.w("FavoritesContent", "Series not found for favorite: ${entry.id}")
                null
            }
        }
}

/**
 * Matches all watchlist entries to content items.
 */
private suspend fun matchEntriesToItems(
    entries: List<WatchlistEntry>,
    cacheService: DataCacheService
): List<WatchlistContentItem> {
    val watchlistData = loadWatchlistData(entries, cacheService)

    val channelsMap = watchlistData.channels.associateBy { it.streamId }
    val moviesMap = watchlistData.movies.associateBy { it.streamId }
    val seriesMap = watchlistData.series.associateBy { it.seriesId }

    val liveItems = matchLiveStreamEntries(entries, channelsMap)
    val movieItems = matchMovieEntries(entries, moviesMap)
    val seriesItems = matchSeriesEntries(entries, seriesMap)

    return liveItems + movieItems + seriesItems
}

/**
 * Renders a watchlist content item as a card.
 */
@Composable
private fun WatchlistCard(
    item: WatchlistContentItem,
    index: Int,
    columnIndex: Int,
    params: WatchlistContentParams,
    onFocusChanged: ((Boolean) -> Unit)?,
    cardFocusRequester: androidx.compose.ui.focus.FocusRequester?
) {
    when (item) {
        is WatchlistContentItem.Live -> {
            LiveStreamCard(
                params = LiveStreamCardParams(
                    stream = item.stream,
                    onClick = { params.onLiveStreamClick(item.stream) },
                    watchlistRepository = params.watchlistRepository,
                    config = CardConfig(
                        focusRequester = cardFocusRequester ?: if (index == 0) params.focusRequester else null,
                        onFocusChanged = onFocusChanged,
                        onNavigateLeft = createOnNavigateLeft(columnIndex, params.onNavigateLeft)
                    )
                )
            )
        }
        is WatchlistContentItem.Movie -> {
            VODCard(
                params = VODCardParams(
                    vod = item.vod,
                    onClick = { params.onVodClick(item.vod) },
                    watchlistRepository = params.watchlistRepository,
                    onFocusChanged = onFocusChanged,
                    onNavigateLeft = createOnNavigateLeft(columnIndex, params.onNavigateLeft),
                    focusRequester = cardFocusRequester
                )
            )
        }
        is WatchlistContentItem.SeriesItem -> {
            VODCard(
                params = VODCardParams(
                    series = item.series,
                    onClick = { params.onSeriesClick(item.series) },
                    watchlistRepository = params.watchlistRepository,
                    onFocusChanged = onFocusChanged,
                    onNavigateLeft = createOnNavigateLeft(columnIndex, params.onNavigateLeft),
                    focusRequester = cardFocusRequester
                )
            )
        }
    }
}

@Composable
fun WatchlistContent(
    params: WatchlistContentParams,
    modifier: Modifier = Modifier
) {
    var entries by remember { mutableStateOf<List<WatchlistEntry>>(emptyList()) }
    var watchlistItems by remember { mutableStateOf<List<WatchlistContentItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var retryTrigger by remember { mutableStateOf(0) }
    val loadError by params.watchlistRepository.loadError.collectAsState()

    // Load watchlist entries; retryTrigger > 0 means the user tapped the error state's retry button
    LaunchedEffect(retryTrigger) {
        if (retryTrigger == 0) {
            // Sync with Firestore before loading (respects cooldown)
            params.watchlistRepository.syncIfNeeded()
        } else {
            isLoading = true
            // Explicit user retry: bypass the sync cooldown
            params.watchlistRepository.retryLoadFromFirestore()
        }
        entries = params.watchlistRepository.getAllWatchlistEntries()
        // isLoading bleibt true, bis Matching abgeschlossen ist
        Log.d("FavoritesContent", "Loaded ${entries.size} favorites")
    }

    // Load only needed streams/series and match watchlist entries on background thread.
    // Keyed on retryTrigger too: if a retry still yields an empty (structurally equal)
    // list, "entries" alone wouldn't change and this effect wouldn't rerun, leaving
    // isLoading stuck at true from the retry branch above.
    LaunchedEffect(entries, retryTrigger) {
        if (entries.isEmpty()) {
            watchlistItems = emptyList()
            isLoading = false // Keine Watchlist-Eintraege = fertig
            return@LaunchedEffect
        }

        val items = matchEntriesToItems(entries, params.cacheService)

        watchlistItems = items
        Log.d("FavoritesContent", "Matched ${items.size} favorites with cached data (loaded ${entries.size} favorites)")

        // Set isLoading to false immediately after data is ready
        // The loading indicator will be hidden when items are actually rendered (via onItemsRendered)
        isLoading = false
    }

    ContentGridLayout(
        params = ContentGridLayoutParams(
            items = watchlistItems,
            isLoading = isLoading,
            focusRequester = params.focusRequester,
            modifier = modifier,
            lazyGridState = params.lazyGridState,
            keySelector = { item ->
                when (item) {
                    is WatchlistContentItem.Live -> item.entry.id
                    is WatchlistContentItem.Movie -> item.entry.id
                    is WatchlistContentItem.SeriesItem -> item.entry.id
                }
            },
            contentIdSelector = { item ->
                when (item) {
                    is WatchlistContentItem.Live -> "live_${item.stream.streamId}"
                    is WatchlistContentItem.Movie -> "vod_${item.vod.streamId}"
                    is WatchlistContentItem.SeriesItem -> "series_${item.series.seriesId}"
                }
            },
            focusRequesterMap = params.focusRequesterMap,
            onItemsRendered = {
                // Items are now rendered, loading is complete
                Log.d("FavoritesContent", "Items rendered, loading complete")
            },
            // Not an error state — an empty watchlist is expected, so it must not
            // read like ContentGridLayout's default "failed to load" message.
            emptyStateText = "Noch keine Watchlist-Einträge",
            errorMessage = loadError,
            // Only offer retry for an actual load error, not for a legitimately empty list.
            onRetry = loadError?.let { { retryTrigger++ } }
        ),
        content = { item, index, columnIndex, onFocusChanged, cardFocusRequester ->
            WatchlistCard(
                item = item,
                index = index,
                columnIndex = columnIndex,
                params = params,
                onFocusChanged = onFocusChanged,
                cardFocusRequester = cardFocusRequester
            )
        }
    )
}
