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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.flintr.tv.data.local.DataCacheService
import com.flintr.tv.data.local.FavoritesRepository
import com.flintr.tv.data.model.FavoriteType
import com.flintr.tv.data.model.LiveStream
import com.flintr.tv.ui.components.CardConfig
import com.flintr.tv.ui.sidebars.ContentGridLayout
import com.flintr.tv.ui.sidebars.ContentGridLayoutParams
import com.flintr.tv.ui.components.LiveStreamCard
import com.flintr.tv.viewmodel.MainViewModel

private const val FAVORITES_CATEGORY_ID = "__favorites__"

data class LiveTVContentParams(
    val viewModel: MainViewModel,
    val selectedCategoryId: String?,
    val focusRequester: FocusRequester,
    val favoritesRepository: FavoritesRepository,
    val cacheService: DataCacheService,
    val onNavigateLeft: () -> Unit,
    val onChannelClick: (LiveStream) -> Unit,
    val modifier: Modifier = Modifier,
    val lazyGridState: LazyGridState,
    val focusRequesterMap: MutableMap<String, FocusRequester>? = null
)

@Composable
fun LiveTVContent(params: LiveTVContentParams) {
    val streams by params.viewModel.liveStreams.collectAsState()
    val uiState by params.viewModel.uiState.collectAsState()
    
    var favoriteStreams by remember { mutableStateOf<List<LiveStream>>(emptyList()) }
    var isLoadingFavorites by remember { mutableStateOf(false) }
    
    val isFavoritesCategory = params.selectedCategoryId == FAVORITES_CATEGORY_ID
    
    // Load favorites if favorites category is selected
    LaunchedEffect(params.selectedCategoryId) {
        if (isFavoritesCategory) {
            isLoadingFavorites = true
            val items = withContext(Dispatchers.IO) {
                val favorites = params.favoritesRepository.getFavoritesByType(FavoriteType.LIVE)
                if (favorites.isEmpty()) {
                    return@withContext emptyList<LiveStream>()
                }
                val cachedData = params.cacheService.loadCachedDataAsync()
                if (cachedData == null) {
                    Log.w("LiveTVContent", "No cached data available for favorites")
                    return@withContext emptyList<LiveStream>()
                }
                val channelsMap = cachedData.channels.associateBy { it.streamId }
                favorites.mapNotNull { favorite ->
                    channelsMap[favorite.streamId]
                }
            }
            favoriteStreams = items
            isLoadingFavorites = false
        } else {
            favoriteStreams = emptyList()
            if (params.selectedCategoryId != null) {
                params.viewModel.loadLiveStreams(params.selectedCategoryId)
            }
        }
    }
    
    // Don't automatically request focus when streams are loaded
    // Focus will be set manually when navigating from CategorySidebar
    // This keeps the focus on the Navbar when the app starts
    
    val displayStreams = if (isFavoritesCategory) favoriteStreams else streams
    val isLoading = if (isFavoritesCategory) isLoadingFavorites else uiState.isLoading
    
    ContentGridLayout(
        params = ContentGridLayoutParams(
            items = displayStreams,
            isLoading = isLoading,
            focusRequester = params.focusRequester,
            modifier = params.modifier,
            lazyGridState = params.lazyGridState,
            keySelector = { stream -> stream.streamId },
            contentIdSelector = { stream -> "live_${stream.streamId}" },
            focusRequesterMap = params.focusRequesterMap
        ),
        content = { stream, index, columnIndex, onFocusChanged, cardFocusRequester ->
            LiveStreamCard(
                stream = stream,
                onClick = { params.onChannelClick(stream) },
                favoritesRepository = params.favoritesRepository,
                config = CardConfig(
                    focusRequester = if (index == 0 && displayStreams.isNotEmpty()) params.focusRequester else null,
                    onFocusChanged = onFocusChanged,
                    onNavigateLeft = if (columnIndex == 0) {
                        { params.onNavigateLeft() }
                    } else {
                        null
                    }
                ),
                focusRequester = cardFocusRequester
            )
        }
    )
}


