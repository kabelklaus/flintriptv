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

@Composable
fun FavoritesContent(
    favoritesRepository: FavoritesRepository,
    cacheService: DataCacheService,
    focusRequester: FocusRequester,
    onNavigateLeft: () -> Unit,
    onLiveStreamClick: (LiveStream) -> Unit,
    onVodClick: (VodStream) -> Unit,
    onSeriesClick: (Series) -> Unit,
    modifier: Modifier = Modifier
) {
    var favorites by remember { mutableStateOf<List<Favorite>>(emptyList()) }
    var favoriteItems by remember { mutableStateOf<List<FavoriteContentItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    // Load favorites
    LaunchedEffect(Unit) {
        favorites = favoritesRepository.getAllFavorites()
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
            val cachedData = cacheService.loadCachedDataAsync()
            if (cachedData == null) {
                Log.w("FavoritesContent", "No cached data available")
                return@withContext emptyList<FavoriteContentItem>()
            }
            
            // Create maps for O(1) lookups instead of O(n) searches
            val channelsMap = cachedData.channels.associateBy { it.streamId }
            val moviesMap = cachedData.movies.associateBy { it.streamId }
            val seriesMap = cachedData.series.associateBy { it.seriesId }
            
            // Match favorites using map lookups (O(1) instead of O(n))
            favorites.mapNotNull { favorite ->
                when (favorite.type) {
                    FavoriteType.LIVE -> {
                        channelsMap[favorite.streamId]?.let { stream ->
                            FavoriteContentItem.Live(stream, favorite)
                        } ?: run {
                            Log.w("FavoritesContent", "LiveStream not found for favorite: ${favorite.id}")
                            null
                        }
                    }
                    FavoriteType.MOVIE -> {
                        moviesMap[favorite.streamId]?.let { vod ->
                            FavoriteContentItem.Movie(vod, favorite)
                        } ?: run {
                            Log.w("FavoritesContent", "VodStream not found for favorite: ${favorite.id}")
                            null
                        }
                    }
                    FavoriteType.SERIES -> {
                        seriesMap[favorite.streamId]?.let { series ->
                            FavoriteContentItem.SeriesItem(series, favorite)
                        } ?: run {
                            Log.w("FavoritesContent", "Series not found for favorite: ${favorite.id}")
                            null
                        }
                    }
                }
            }
        }
        
        favoriteItems = items
        Log.d("FavoritesContent", "Matched ${items.size} favorites with cached data")
        
        // Wait for Compose to render the items before hiding loading indicator
        // Use withContext(Dispatchers.Main) to ensure we're on the main thread
        // and delay to allow rendering to complete
        // Based on logs, rendering takes ~700ms, so we wait longer to be safe
        withContext(Dispatchers.Main) {
            kotlinx.coroutines.delay(800) // Give Compose time to render the cards
            isLoading = false // Matching abgeschlossen und items gerendert = fertig
        }
    }
    
    ContentGridLayout(
        items = favoriteItems,
        isLoading = isLoading,
        focusRequester = focusRequester,
        modifier = modifier,
        content = { item, index, columnIndex, onFocusChanged ->
            when (item) {
                is FavoriteContentItem.Live -> {
                    LiveStreamCard(
                        stream = item.stream,
                        onClick = { onLiveStreamClick(item.stream) },
                        favoritesRepository = favoritesRepository,
                        config = CardConfig(
                            focusRequester = if (index == 0 && favoriteItems.isNotEmpty()) focusRequester else null,
                            onFocusChanged = onFocusChanged,
                            onNavigateLeft = if (columnIndex == 0) {
                                { onNavigateLeft() }
                            } else {
                                null
                            }
                        )
                    )
                }
                is FavoriteContentItem.Movie -> {
                    VODCard(
                        vod = item.vod,
                        onClick = { onVodClick(item.vod) },
                        favoritesRepository = favoritesRepository,
                        onFocusChanged = onFocusChanged,
                        onNavigateLeft = if (columnIndex == 0) {
                            { onNavigateLeft() }
                        } else {
                            null
                        }
                    )
                }
                is FavoriteContentItem.SeriesItem -> {
                    VODCard(
                        series = item.series,
                        onClick = { onSeriesClick(item.series) },
                        favoritesRepository = favoritesRepository,
                        onFocusChanged = onFocusChanged,
                        onNavigateLeft = if (columnIndex == 0) {
                            { onNavigateLeft() }
                        } else {
                            null
                        }
                    )
                }
            }
        }
    )
}
