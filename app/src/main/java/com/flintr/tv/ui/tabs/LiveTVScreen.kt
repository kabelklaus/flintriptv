package com.flintr.tv.ui.tabs

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.flintr.tv.data.local.DataCacheService
import com.flintr.tv.data.local.FavoritesRepository
import com.flintr.tv.data.model.FavoriteType
import com.flintr.tv.data.model.LiveStream
import com.flintr.tv.ui.components.CardConfig
import com.flintr.tv.ui.sidebars.ContentGridLayout
import com.flintr.tv.ui.sidebars.ContentGridLayoutParams
import com.flintr.tv.ui.components.LiveStreamCard
import com.flintr.tv.viewmodel.MainViewModel
import com.flintr.tv.ui.common.UiConstants.FAVORITES_CATEGORY_ID
import com.flintr.tv.ui.common.UiConstants.IN_PROGRESS_CATEGORY_ID
import com.flintr.tv.ui.common.useFavorites

private const val TAG = "LiveTVContent"

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
    
    val isFavoritesCategory = params.selectedCategoryId == FAVORITES_CATEGORY_ID
    val isInProgressCategory = params.selectedCategoryId == IN_PROGRESS_CATEGORY_ID
    
    // Load favorites using the hook
    val favoritesState = useFavorites<LiveStream>(
        isFavoritesCategory = isFavoritesCategory,
        favoritesRepository = params.favoritesRepository,
        cacheService = params.cacheService,
        favoriteType = FavoriteType.LIVE,
        idSelector = { it.streamId }
    )
    
    // Load regular streams when not in favorites or in-progress category
    LaunchedEffect(params.selectedCategoryId) {
        if (!isFavoritesCategory && !isInProgressCategory && params.selectedCategoryId != null) {
            params.viewModel.loadLiveStreams(params.selectedCategoryId)
        }
    }
    
    // Don't automatically request focus when streams are loaded
    // Focus will be set manually when navigating from CategorySidebar
    // This keeps the focus on the Navbar when the app starts
    
    // In-progress category should not be shown in LiveTV (contains only movies/episodes)
    // Show empty list if in-progress category is selected
    val displayStreams = when {
        isFavoritesCategory -> favoritesState.items
        isInProgressCategory -> emptyList() // In-progress category not applicable for LiveTV
        else -> streams
    }
    val isLoading = when {
        isFavoritesCategory -> favoritesState.isLoading
        isInProgressCategory -> false // No loading needed for empty list
        else -> uiState.isLoading
    }
    
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


