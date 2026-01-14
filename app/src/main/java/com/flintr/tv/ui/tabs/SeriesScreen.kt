package com.flintr.tv.ui.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.flintr.tv.data.local.FavoritesRepository
import com.flintr.tv.data.model.Series
import com.flintr.tv.ui.components.VODCard
import com.flintr.tv.ui.sidebars.ContentGridLayout
import com.flintr.tv.viewmodel.MainViewModel

/**
 * Parameters for SeriesContent composable.
 * Encapsulates all parameters to reduce function parameter count.
 */
data class SeriesContentParams(
    val viewModel: MainViewModel,
    val selectedCategoryId: String?,
    val focusRequester: FocusRequester,
    val favoritesRepository: FavoritesRepository,
    val onNavigateLeft: () -> Unit,
    val onSeriesClick: (Series) -> Unit,
    val lazyGridState: LazyGridState,
    val focusRequesterMap: MutableMap<String, FocusRequester>? = null
)

@Composable
fun SeriesContent(
    params: SeriesContentParams,
    modifier: Modifier = Modifier
) {
    val series by params.viewModel.series.collectAsState()
    val uiState by params.viewModel.uiState.collectAsState()
    
    LaunchedEffect(params.selectedCategoryId) {
        if (params.selectedCategoryId != null) {
            params.viewModel.loadSeries(params.selectedCategoryId)
        }
    }
    
    // Don't automatically request focus when series are loaded
    // Focus will be set manually when navigating from CategorySidebar
    // This keeps the focus on the Navbar when the app starts
    
    ContentGridLayout(
        items = series,
        isLoading = uiState.isLoading,
        focusRequester = params.focusRequester,
        modifier = modifier,
        lazyGridState = params.lazyGridState,
        keySelector = { seriesItem -> seriesItem.seriesId },
        contentIdSelector = { seriesItem -> "series_${seriesItem.seriesId}" },
        focusRequesterMap = params.focusRequesterMap,
        content = { seriesItem, _, columnIndex, onFocusChanged, cardFocusRequester ->
            SeriesItem(
                series = seriesItem,
                favoritesRepository = params.favoritesRepository,
                onSeriesClick = { params.onSeriesClick(seriesItem) },
                cardConfig = com.flintr.tv.ui.components.CardConfig(
                    isSelected = false,
                    onFocusChanged = onFocusChanged,
                    onNavigateLeft = if (columnIndex == 0) {
                        { params.onNavigateLeft() }
                    } else {
                        null
                    },
                    focusRequester = cardFocusRequester
                )
            )
        }
    )
}

@Composable
fun SeriesItem(
    series: Series,
    favoritesRepository: FavoritesRepository,
    onSeriesClick: () -> Unit,
    cardConfig: com.flintr.tv.ui.components.CardConfig = com.flintr.tv.ui.components.CardConfig()
) {
    VODCard(
        series = series,
        onClick = onSeriesClick,
        favoritesRepository = favoritesRepository,
        isSelected = cardConfig.isSelected,
        onFocusChanged = cardConfig.onFocusChanged,
        onNavigateLeft = cardConfig.onNavigateLeft
    )
}


