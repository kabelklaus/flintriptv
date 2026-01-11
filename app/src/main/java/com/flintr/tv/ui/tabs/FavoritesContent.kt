package com.flintr.tv.ui.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.flintr.tv.data.local.DataCacheService
import com.flintr.tv.data.local.FavoritesRepository
import com.flintr.tv.data.model.Favorite
import com.flintr.tv.data.model.FavoriteType
import com.flintr.tv.data.model.LiveStream
import com.flintr.tv.data.model.Series
import com.flintr.tv.data.model.VodStream
import com.flintr.tv.ui.components.CardConfig
import com.flintr.tv.ui.components.LiveStreamCard
import com.flintr.tv.ui.components.VODCard
import com.flintr.tv.ui.sidebars.ContentGridLayout

// Sealed class to represent different favorite content types
private sealed class FavoriteContentItem {
    data class Live(val stream: LiveStream, val favorite: Favorite) : FavoriteContentItem()
    data class Movie(val vod: VodStream, val favorite: Favorite) : FavoriteContentItem()
    data class SeriesItem(val series: Series, val favorite: Favorite) : FavoriteContentItem()
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
    val onSeriesClick: (Series) -> Unit
)

/**
 * Data class to hold maps for efficient favorite matching.
 */
private data class FavoriteMaps(
    val channelsMap: Map<Int, LiveStream>,
    val moviesMap: Map<Int, VodStream>,
    val seriesMap: Map<Int, Series>
)

/**
 * Creates maps for O(1) lookups from cached data.
 */
private fun createFavoriteMaps(cachedData: com.flintr.tv.data.local.CachedData): FavoriteMaps {
    return FavoriteMaps(
        channelsMap = cachedData.channels.associateBy<LiveStream, Int> { it.streamId },
        moviesMap = cachedData.movies.associateBy<VodStream, Int> { it.streamId },
        seriesMap = cachedData.series.associateBy<Series, Int> { it.seriesId }
    )
}

/**
 * Matches a single favorite to its corresponding content item.
 */
private fun matchFavoriteToContentItem(
    favorite: Favorite,
    maps: FavoriteMaps
): FavoriteContentItem? {
    return when (favorite.type) {
        FavoriteType.LIVE -> {
            maps.channelsMap[favorite.streamId]?.let { stream ->
                FavoriteContentItem.Live(stream, favorite)
            } ?: run {
                Log.w("FavoritesContent", "LiveStream not found for favorite: ${favorite.id}")
                null
            }
        }
        FavoriteType.MOVIE -> {
            maps.moviesMap[favorite.streamId]?.let { vod ->
                FavoriteContentItem.Movie(vod, favorite)
            } ?: run {
                Log.w("FavoritesContent", "VodStream not found for favorite: ${favorite.id}")
                null
            }
        }
        FavoriteType.SERIES -> {
            maps.seriesMap[favorite.streamId]?.let { series ->
                FavoriteContentItem.SeriesItem(series, favorite)
            } ?: run {
                Log.w("FavoritesContent", "Series not found for favorite: ${favorite.id}")
                null
            }
        }
    }
}

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
 * Renders a favorite content item as a card.
 */
@Composable
private fun renderFavoriteCard(
    item: FavoriteContentItem,
    index: Int,
    columnIndex: Int,
    params: FavoritesContentParams,
    favoriteItems: List<FavoriteContentItem>,
    onFocusChanged: ((Boolean) -> Unit)?
) {
    when (item) {
        is FavoriteContentItem.Live -> {
            LiveStreamCard(
                stream = item.stream,
                onClick = { params.onLiveStreamClick(item.stream) },
                favoritesRepository = params.favoritesRepository,
                config = CardConfig(
                    focusRequester = if (index == 0 && favoriteItems.isNotEmpty()) params.focusRequester else null,
                    onFocusChanged = onFocusChanged,
                    onNavigateLeft = createOnNavigateLeft(columnIndex, params.onNavigateLeft)
                )
            )
        }
        is FavoriteContentItem.Movie -> {
            VODCard(
                vod = item.vod,
                onClick = { params.onVodClick(item.vod) },
                favoritesRepository = params.favoritesRepository,
                onFocusChanged = onFocusChanged,
                onNavigateLeft = createOnNavigateLeft(columnIndex, params.onNavigateLeft)
            )
        }
        is FavoriteContentItem.SeriesItem -> {
            VODCard(
                series = item.series,
                onClick = { params.onSeriesClick(item.series) },
                favoritesRepository = params.favoritesRepository,
                onFocusChanged = onFocusChanged,
                onNavigateLeft = createOnNavigateLeft(columnIndex, params.onNavigateLeft)
            )
        }
    }
}

@Composable
fun FavoritesContent(
    params: FavoritesContentParams,
    modifier: Modifier = Modifier
) {
    var favorites by remember { mutableStateOf<List<Favorite>>(emptyList()) }
    var favoriteItems by remember { mutableStateOf<List<FavoriteContentItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    // Load favorites
    LaunchedEffect(Unit) {
        favorites = params.favoritesRepository.getAllFavorites()
        // isLoading bleibt true, bis Matching abgeschlossen ist
        Log.d("FavoritesContent", "Loaded ${favorites.size} favorites")
    }
    
    // Load cached data and match favorites with streams/series on background thread
    LaunchedEffect(favorites) {
        if (favorites.isEmpty()) {
            favoriteItems = emptyList()
            isLoading = false // Keine Favoriten = fertig
            return@LaunchedEffect
        }
        
        // Perform all heavy operations on background thread
        val items = withContext(Dispatchers.IO) {
            val cachedData = params.cacheService.loadCachedDataAsync()
            if (cachedData == null) {
                Log.w("FavoritesContent", "No cached data available")
                return@withContext emptyList<FavoriteContentItem>()
            }
            
            // Create maps for O(1) lookups instead of O(n) searches
            val maps = createFavoriteMaps(cachedData)
            
            // Match favorites using map lookups (O(1) instead of O(n))
            favorites.mapNotNull { favorite ->
                matchFavoriteToContentItem(favorite, maps)
            }
        }
        
        favoriteItems = items
        Log.d("FavoritesContent", "Matched ${items.size} favorites with cached data")
        
        // Set isLoading to false immediately after data is ready
        // The loading indicator will be hidden when items are actually rendered (via onItemsRendered)
        isLoading = false
    }
    
    ContentGridLayout(
        items = favoriteItems,
        isLoading = isLoading,
        focusRequester = params.focusRequester,
        modifier = modifier,
        keySelector = { item ->
            when (item) {
                is FavoriteContentItem.Live -> item.stream.streamId
                is FavoriteContentItem.Movie -> item.vod.streamId
                is FavoriteContentItem.SeriesItem -> item.series.seriesId
            }
        },
        onItemsRendered = {
            // Items are now rendered, loading is complete
            Log.d("FavoritesContent", "Items rendered, loading complete")
        },
        content = { item, index, columnIndex, onFocusChanged ->
            renderFavoriteCard(
                item = item,
                index = index,
                columnIndex = columnIndex,
                params = params,
                favoriteItems = favoriteItems,
                onFocusChanged = onFocusChanged
            )
        }
    )
}
