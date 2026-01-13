package com.flintr.tv.ui.tabs

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.flintr.tv.data.local.FavoritesRepository
import com.flintr.tv.data.model.Episode
import com.flintr.tv.data.model.Season
import com.flintr.tv.data.model.Series
import com.flintr.tv.ui.sidebars.ContentGridLayout
import com.flintr.tv.ui.components.VODCard
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.appColors
import com.flintr.tv.ui.utils.onClick
import com.flintr.tv.viewmodel.MainViewModel

@Composable
fun SeriesContent(
    viewModel: MainViewModel,
    selectedCategoryId: String?,
    focusRequester: FocusRequester,
    favoritesRepository: FavoritesRepository,
    onNavigateLeft: () -> Unit,
    onEpisodeClick: (Episode, Series) -> Unit,
    onSeriesClick: (Series) -> Unit,
    modifier: Modifier = Modifier
) {
    val series by viewModel.series.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(selectedCategoryId) {
        if (selectedCategoryId != null) {
            viewModel.loadSeries(selectedCategoryId)
        }
    }
    
    // Don't automatically request focus when series are loaded
    // Focus will be set manually when navigating from CategorySidebar
    // This keeps the focus on the Navbar when the app starts
    
    ContentGridLayout(
        items = series,
        isLoading = uiState.isLoading,
        focusRequester = focusRequester,
        modifier = modifier,
        keySelector = { seriesItem -> seriesItem.seriesId },
        content = { seriesItem, _, columnIndex, onFocusChanged ->
            SeriesItem(
                series = seriesItem,
                favoritesRepository = favoritesRepository,
                onSeriesClick = { onSeriesClick(seriesItem) },
                cardConfig = com.flintr.tv.ui.components.CardConfig(
                    isSelected = false,
                    onFocusChanged = onFocusChanged,
                    onNavigateLeft = if (columnIndex == 0) {
                        { onNavigateLeft() }
                    } else {
                        null
                    }
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


