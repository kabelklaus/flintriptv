package com.flintr.tv.ui.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.flintr.tv.data.local.FavoritesRepository
import com.flintr.tv.data.model.VodStream
import com.flintr.tv.ui.sidebars.ContentGridLayout
import com.flintr.tv.ui.components.VODCard
import com.flintr.tv.viewmodel.MainViewModel

@Composable
fun VODContent(
    viewModel: MainViewModel,
    selectedCategoryId: String?,
    focusRequester: FocusRequester,
    favoritesRepository: FavoritesRepository,
    onNavigateLeft: () -> Unit,
    onVodClick: (VodStream) -> Unit,
    modifier: Modifier = Modifier
) {
    val streams by viewModel.vodStreams.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(selectedCategoryId) {
        if (selectedCategoryId != null) {
            viewModel.loadVodStreams(selectedCategoryId)
        }
    }
    
    // Don't automatically request focus when streams are loaded
    // Focus will be set manually when navigating from CategorySidebar
    // This keeps the focus on the Navbar when the app starts
    
    ContentGridLayout(
        items = streams,
        isLoading = uiState.isLoading,
        focusRequester = focusRequester,
        modifier = modifier,
        content = { vod, _, columnIndex, onFocusChanged ->
            VODCard(
                vod = vod,
                onClick = { onVodClick(vod) },
                favoritesRepository = favoritesRepository,
                onFocusChanged = onFocusChanged,
                onNavigateLeft = if (columnIndex == 0) {
                    { onNavigateLeft() }
                } else {
                    null
                }
            )
        }
    )
}

