package com.flintr.tv.ui.tabs

import androidx.compose.foundation.layout.*
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
import com.flintr.tv.data.model.Series
import com.flintr.tv.ui.components.VODCard
import com.flintr.tv.ui.components.VODCardParams
import com.flintr.tv.ui.sidebars.ContentGridLayout
import com.flintr.tv.ui.sidebars.ContentGridLayoutParams
import com.flintr.tv.viewmodel.MainViewModel

private const val FAVORITES_CATEGORY_ID = "__favorites__"

/**
 * Parameters for SeriesContent composable.
 * Encapsulates all parameters to reduce function parameter count.
 */
data class SeriesContentParams(
    val viewModel: MainViewModel,
    val selectedCategoryId: String?,
    val focusRequester: FocusRequester,
    val favoritesRepository: FavoritesRepository,
    val cacheService: DataCacheService,
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
    
    var favoriteSeries by remember { mutableStateOf<List<Series>>(emptyList()) }
    var isLoadingFavorites by remember { mutableStateOf(false) }
    
    val isFavoritesCategory = params.selectedCategoryId == FAVORITES_CATEGORY_ID
    
    // Load favorites if favorites category is selected
    LaunchedEffect(params.selectedCategoryId) {
        if (isFavoritesCategory) {
            isLoadingFavorites = true
            val items = withContext(Dispatchers.IO) {
                val favorites = params.favoritesRepository.getFavoritesByType(FavoriteType.SERIES)
                if (favorites.isEmpty()) {
                    return@withContext emptyList<Series>()
                }
                val cachedData = params.cacheService.loadCachedDataAsync()
                if (cachedData == null) {
                    Log.w("SeriesContent", "No cached data available for favorites")
                    return@withContext emptyList<Series>()
                }
                val seriesMap = cachedData.series.associateBy { it.seriesId }
                favorites.mapNotNull { favorite ->
                    seriesMap[favorite.streamId]
                }
            }
            favoriteSeries = items
            isLoadingFavorites = false
        } else {
            favoriteSeries = emptyList()
            if (params.selectedCategoryId != null) {
                params.viewModel.loadSeries(params.selectedCategoryId)
            }
        }
    }
    
    // Don't automatically request focus when series are loaded
    // Focus will be set manually when navigating from CategorySidebar
    // This keeps the focus on the Navbar when the app starts
    
    val displaySeries = if (isFavoritesCategory) favoriteSeries else series
    val isLoading = if (isFavoritesCategory) isLoadingFavorites else uiState.isLoading
    
    ContentGridLayout(
        params = ContentGridLayoutParams(
            items = displaySeries,
            isLoading = isLoading,
            focusRequester = params.focusRequester,
            modifier = modifier,
            lazyGridState = params.lazyGridState,
            keySelector = { seriesItem -> seriesItem.seriesId },
            contentIdSelector = { seriesItem -> "series_${seriesItem.seriesId}" },
            focusRequesterMap = params.focusRequesterMap
        ),
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
        params = VODCardParams(
            series = series,
            onClick = onSeriesClick,
            favoritesRepository = favoritesRepository,
            isSelected = cardConfig.isSelected,
            onFocusChanged = cardConfig.onFocusChanged,
            onNavigateLeft = cardConfig.onNavigateLeft,
            focusRequester = cardConfig.focusRequester
        )
    )
}


