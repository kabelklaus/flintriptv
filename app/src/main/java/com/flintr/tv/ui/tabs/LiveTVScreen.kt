package com.flintr.tv.ui.tabs

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.flintr.tv.data.local.FavoritesRepository
import com.flintr.tv.data.model.LiveStream
import com.flintr.tv.ui.components.CardConfig
import com.flintr.tv.ui.sidebars.ContentGridLayout
import com.flintr.tv.ui.components.LiveStreamCard
import com.flintr.tv.viewmodel.MainViewModel

data class LiveTVContentParams(
    val viewModel: MainViewModel,
    val selectedCategoryId: String?,
    val focusRequester: FocusRequester,
    val favoritesRepository: FavoritesRepository,
    val onNavigateLeft: () -> Unit,
    val onChannelClick: (LiveStream) -> Unit,
    val modifier: Modifier = Modifier,
    val lazyGridState: LazyGridState,
    val focusRequesterMap: MutableMap<String, FocusRequester>? = null
)

@Composable
fun LiveTVContent(
    viewModel: MainViewModel,
    selectedCategoryId: String?,
    focusRequester: FocusRequester,
    favoritesRepository: FavoritesRepository,
    onNavigateLeft: () -> Unit,
    onChannelClick: (LiveStream) -> Unit,
    modifier: Modifier = Modifier,
    lazyGridState: LazyGridState = rememberLazyGridState(),
    focusRequesterMap: MutableMap<String, FocusRequester>? = null
) {
    val params = LiveTVContentParams(
        viewModel = viewModel,
        selectedCategoryId = selectedCategoryId,
        focusRequester = focusRequester,
        favoritesRepository = favoritesRepository,
        onNavigateLeft = onNavigateLeft,
        onChannelClick = onChannelClick,
        modifier = modifier,
        lazyGridState = lazyGridState,
        focusRequesterMap = focusRequesterMap
    )
    LiveTVContent(params)
}

@Composable
private fun LiveTVContent(params: LiveTVContentParams) {
    val streams by params.viewModel.liveStreams.collectAsState()
    val uiState by params.viewModel.uiState.collectAsState()
    
    LaunchedEffect(params.selectedCategoryId) {
        if (params.selectedCategoryId != null) {
            params.viewModel.loadLiveStreams(params.selectedCategoryId)
        }
    }
    
    // Don't automatically request focus when streams are loaded
    // Focus will be set manually when navigating from CategorySidebar
    // This keeps the focus on the Navbar when the app starts
    
    ContentGridLayout(
        items = streams,
        isLoading = uiState.isLoading,
        focusRequester = params.focusRequester,
        modifier = params.modifier,
        lazyGridState = params.lazyGridState,
        keySelector = { stream -> stream.streamId },
        contentIdSelector = { stream -> "live_${stream.streamId}" },
        focusRequesterMap = params.focusRequesterMap,
        content = { stream, index, columnIndex, onFocusChanged, cardFocusRequester ->
            LiveStreamCard(
                stream = stream,
                onClick = { params.onChannelClick(stream) },
                favoritesRepository = params.favoritesRepository,
                config = CardConfig(
                    focusRequester = if (index == 0 && streams.isNotEmpty()) params.focusRequester else null,
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


