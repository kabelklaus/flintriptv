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
import com.flintr.tv.data.local.FavoritesRepository
import com.flintr.tv.data.model.WatchlistEntry
import com.flintr.tv.data.model.FavoriteType
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

// Sealed class to represent different favorite content types
private sealed class FavoriteContentItem {
    data class Live(val stream: LiveStream, val favorite: WatchlistEntry) : FavoriteContentItem()
    data class Movie(val vod: VodStream, val favorite: WatchlistEntry) : FavoriteContentItem()
    data class SeriesItem(val series: Series, val favorite: WatchlistEntry) : FavoriteContentItem()
}

/**
 * Parameters for FavoritesContent composable.
 * Encapsulates all parameters to reduce function parameter count.
 */
data class FavoritesContentParams(
    val favoritesRepository: FavoritesRepository,
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
 * Data class to hold loaded favorite data.
 */
private data class FavoriteData(
    val channels: List<LiveStream>,
    val movies: List<VodStream>,
    val series: List<Series>
)

/**
 * Loads favorite data (channels, movies, series) based on favorite IDs.
 */
private suspend fun loadFavoriteData(
    favorites: List<WatchlistEntry>,
    cacheService: DataCacheService
): FavoriteData {
    val liveStreamIds = favorites.filter { it.type == FavoriteType.LIVE }.mapNotNull { it.streamId }
    val movieStreamIds = favorites.filter { it.type == FavoriteType.MOVIE }.mapNotNull { it.streamId }
    val seriesIds = favorites.filter { it.type == FavoriteType.SERIES }.mapNotNull { it.streamId }
    
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
    
    return FavoriteData(channels, movies, series)
}

/**
 * Matches live stream favorites with loaded channels.
 */
private fun matchLiveStreamFavorites(
    favorites: List<WatchlistEntry>,
    channelsMap: Map<Int, LiveStream>
): List<FavoriteContentItem.Live> {
    return favorites
        .filter { it.type == FavoriteType.LIVE }
        .mapNotNull { favorite ->
            channelsMap[favorite.streamId]?.let { stream ->
                FavoriteContentItem.Live(stream, favorite)
            } ?: run {
                Log.w("FavoritesContent", "LiveStream not found for favorite: ${favorite.id}")
                null
            }
        }
}

/**
 * Matches movie favorites with loaded movies.
 */
private fun matchMovieFavorites(
    favorites: List<WatchlistEntry>,
    moviesMap: Map<Int, VodStream>
): List<FavoriteContentItem.Movie> {
    return favorites
        .filter { it.type == FavoriteType.MOVIE }
        .mapNotNull { favorite ->
            moviesMap[favorite.streamId]?.let { vod ->
                FavoriteContentItem.Movie(vod, favorite)
            } ?: run {
                Log.w("FavoritesContent", "VodStream not found for favorite: ${favorite.id}")
                null
            }
        }
}

/**
 * Matches series favorites with loaded series.
 */
private fun matchSeriesFavorites(
    favorites: List<WatchlistEntry>,
    seriesMap: Map<Int, Series>
): List<FavoriteContentItem.SeriesItem> {
    return favorites
        .filter { it.type == FavoriteType.SERIES }
        .mapNotNull { favorite ->
            seriesMap[favorite.streamId]?.let { seriesItem ->
                FavoriteContentItem.SeriesItem(seriesItem, favorite)
            } ?: run {
                Log.w("FavoritesContent", "Series not found for favorite: ${favorite.id}")
                null
            }
        }
}

/**
 * Matches all favorites to content items.
 */
private suspend fun matchFavoritesToItems(
    favorites: List<WatchlistEntry>,
    cacheService: DataCacheService
): List<FavoriteContentItem> {
    val favoriteData = loadFavoriteData(favorites, cacheService)
    
    val channelsMap = favoriteData.channels.associateBy { it.streamId }
    val moviesMap = favoriteData.movies.associateBy { it.streamId }
    val seriesMap = favoriteData.series.associateBy { it.seriesId }
    
    val liveItems = matchLiveStreamFavorites(favorites, channelsMap)
    val movieItems = matchMovieFavorites(favorites, moviesMap)
    val seriesItems = matchSeriesFavorites(favorites, seriesMap)
    
    return liveItems + movieItems + seriesItems
}

/**
 * Renders a favorite content item as a card.
 */
@Composable
private fun FavoriteCard(
    item: FavoriteContentItem,
    index: Int,
    columnIndex: Int,
    params: FavoritesContentParams,
    onFocusChanged: ((Boolean) -> Unit)?,
    cardFocusRequester: androidx.compose.ui.focus.FocusRequester?
) {
    when (item) {
        is FavoriteContentItem.Live -> {
            LiveStreamCard(
                params = LiveStreamCardParams(
                    stream = item.stream,
                    onClick = { params.onLiveStreamClick(item.stream) },
                    favoritesRepository = params.favoritesRepository,
                    config = CardConfig(
                        focusRequester = cardFocusRequester ?: if (index == 0) params.focusRequester else null,
                        onFocusChanged = onFocusChanged,
                        onNavigateLeft = createOnNavigateLeft(columnIndex, params.onNavigateLeft)
                    )
                )
            )
        }
        is FavoriteContentItem.Movie -> {
            VODCard(
                params = VODCardParams(
                    vod = item.vod,
                    onClick = { params.onVodClick(item.vod) },
                    favoritesRepository = params.favoritesRepository,
                    onFocusChanged = onFocusChanged,
                    onNavigateLeft = createOnNavigateLeft(columnIndex, params.onNavigateLeft),
                    focusRequester = cardFocusRequester
                )
            )
        }
        is FavoriteContentItem.SeriesItem -> {
            VODCard(
                params = VODCardParams(
                    series = item.series,
                    onClick = { params.onSeriesClick(item.series) },
                    favoritesRepository = params.favoritesRepository,
                    onFocusChanged = onFocusChanged,
                    onNavigateLeft = createOnNavigateLeft(columnIndex, params.onNavigateLeft),
                    focusRequester = cardFocusRequester
                )
            )
        }
    }
}

@Composable
fun FavoritesContent(
    params: FavoritesContentParams,
    modifier: Modifier = Modifier
) {
    var favorites by remember { mutableStateOf<List<WatchlistEntry>>(emptyList()) }
    var favoriteItems by remember { mutableStateOf<List<FavoriteContentItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var retryTrigger by remember { mutableStateOf(0) }
    val loadError by params.favoritesRepository.loadError.collectAsState()

    // Load favorites; retryTrigger > 0 means the user tapped the error state's retry button
    LaunchedEffect(retryTrigger) {
        if (retryTrigger == 0) {
            // Sync with Firestore before loading (respects cooldown)
            params.favoritesRepository.syncIfNeeded()
        } else {
            isLoading = true
            // Explicit user retry: bypass the sync cooldown
            params.favoritesRepository.retryLoadFromFirestore()
        }
        favorites = params.favoritesRepository.getAllFavorites()
        // isLoading bleibt true, bis Matching abgeschlossen ist
        Log.d("FavoritesContent", "Loaded ${favorites.size} favorites")
    }
    
    // Load only needed streams/series and match favorites on background thread.
    // Keyed on retryTrigger too: if a retry still yields an empty (structurally equal)
    // list, "favorites" alone wouldn't change and this effect wouldn't rerun, leaving
    // isLoading stuck at true from the retry branch above.
    LaunchedEffect(favorites, retryTrigger) {
        if (favorites.isEmpty()) {
            favoriteItems = emptyList()
            isLoading = false // Keine Favoriten = fertig
            return@LaunchedEffect
        }
        
        val items = matchFavoritesToItems(favorites, params.cacheService)
        
        favoriteItems = items
        Log.d("FavoritesContent", "Matched ${items.size} favorites with cached data (loaded ${favorites.size} favorites)")
        
        // Set isLoading to false immediately after data is ready
        // The loading indicator will be hidden when items are actually rendered (via onItemsRendered)
        isLoading = false
    }
    
    ContentGridLayout(
        params = ContentGridLayoutParams(
            items = favoriteItems,
            isLoading = isLoading,
            focusRequester = params.focusRequester,
            modifier = modifier,
            lazyGridState = params.lazyGridState,
            keySelector = { item ->
                when (item) {
                    is FavoriteContentItem.Live -> item.favorite.id
                    is FavoriteContentItem.Movie -> item.favorite.id
                    is FavoriteContentItem.SeriesItem -> item.favorite.id
                }
            },
            contentIdSelector = { item ->
                when (item) {
                    is FavoriteContentItem.Live -> "live_${item.stream.streamId}"
                    is FavoriteContentItem.Movie -> "vod_${item.vod.streamId}"
                    is FavoriteContentItem.SeriesItem -> "series_${item.series.seriesId}"
                }
            },
            focusRequesterMap = params.focusRequesterMap,
            onItemsRendered = {
                // Items are now rendered, loading is complete
                Log.d("FavoritesContent", "Items rendered, loading complete")
            },
            // Not an error state — an empty favorites list is expected, so it must not
            // read like ContentGridLayout's default "failed to load" message.
            emptyStateText = "Noch keine Favoriten",
            errorMessage = loadError,
            // Only offer retry for an actual load error, not for a legitimately empty list.
            onRetry = loadError?.let { { retryTrigger++ } }
        ),
        content = { item, index, columnIndex, onFocusChanged, cardFocusRequester ->
            FavoriteCard(
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
