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
import com.flintr.tv.data.model.VodStream
import com.flintr.tv.ui.sidebars.ContentGridLayout
import com.flintr.tv.ui.sidebars.ContentGridLayoutParams
import com.flintr.tv.ui.components.VODCard
import com.flintr.tv.ui.components.VODCardParams
import com.flintr.tv.viewmodel.MainViewModel

private const val FAVORITES_CATEGORY_ID = "__favorites__"

data class VODContentParams(
    val viewModel: MainViewModel,
    val selectedCategoryId: String?,
    val focusRequester: FocusRequester,
    val favoritesRepository: FavoritesRepository,
    val cacheService: DataCacheService,
    val onNavigateLeft: () -> Unit,
    val onVodClick: (VodStream) -> Unit,
    val modifier: Modifier = Modifier,
    val lazyGridState: LazyGridState,
    val focusRequesterMap: MutableMap<String, FocusRequester>? = null
)

@Composable
fun VODContent(params: VODContentParams) {
    val streams by params.viewModel.vodStreams.collectAsState()
    val uiState by params.viewModel.uiState.collectAsState()
    
    var favoriteVods by remember { mutableStateOf<List<VodStream>>(emptyList()) }
    var isLoadingFavorites by remember { mutableStateOf(false) }
    
    val isFavoritesCategory = params.selectedCategoryId == FAVORITES_CATEGORY_ID
    
    // Load favorites if favorites category is selected
    LaunchedEffect(params.selectedCategoryId) {
        if (isFavoritesCategory) {
            isLoadingFavorites = true
            val items = withContext(Dispatchers.IO) {
                val favorites = params.favoritesRepository.getFavoritesByType(FavoriteType.MOVIE)
                if (favorites.isEmpty()) {
                    return@withContext emptyList<VodStream>()
                }
                val cachedData = params.cacheService.loadCachedDataAsync()
                if (cachedData == null) {
                    Log.w("VODContent", "No cached data available for favorites")
                    return@withContext emptyList<VodStream>()
                }
                val moviesMap = cachedData.movies.associateBy { it.streamId }
                favorites.mapNotNull { favorite ->
                    moviesMap[favorite.streamId]
                }
            }
            favoriteVods = items
            isLoadingFavorites = false
        } else {
            favoriteVods = emptyList()
            if (params.selectedCategoryId != null) {
                params.viewModel.loadVodStreams(params.selectedCategoryId)
            }
        }
    }
    
    // Don't automatically request focus when streams are loaded
    // Focus will be set manually when navigating from CategorySidebar
    // This keeps the focus on the Navbar when the app starts
    
    val displayStreams = if (isFavoritesCategory) favoriteVods else streams
    val isLoading = if (isFavoritesCategory) isLoadingFavorites else uiState.isLoading
    
    ContentGridLayout(
        params = ContentGridLayoutParams(
            items = displayStreams,
            isLoading = isLoading,
            focusRequester = params.focusRequester,
            modifier = params.modifier,
            lazyGridState = params.lazyGridState,
            keySelector = { vod -> vod.streamId },
            contentIdSelector = { vod -> "vod_${vod.streamId}" },
            focusRequesterMap = params.focusRequesterMap
        ),
        content = { vod, _, columnIndex, onFocusChanged, cardFocusRequester ->
            VODCard(
                params = VODCardParams(
                    vod = vod,
                    onClick = { params.onVodClick(vod) },
                    favoritesRepository = params.favoritesRepository,
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

