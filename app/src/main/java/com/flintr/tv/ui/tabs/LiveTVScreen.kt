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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import com.flintr.tv.data.local.CategoryVisibilityService
import com.flintr.tv.data.local.CredentialsRepository
import com.flintr.tv.data.local.DataCacheService
import com.flintr.tv.data.local.FavoritesRepository
import com.flintr.tv.data.model.CategoryType
import com.flintr.tv.data.model.FavoriteType
import com.flintr.tv.data.model.LiveStream
import com.flintr.tv.data.repository.UserRepository
import com.flintr.tv.ui.components.CardConfig
import com.flintr.tv.ui.sidebars.ContentGridLayout
import com.flintr.tv.ui.sidebars.ContentGridLayoutParams
import com.flintr.tv.ui.components.LiveStreamCard
import com.flintr.tv.ui.components.LiveStreamCardParams
import com.flintr.tv.viewmodel.MainViewModel
import com.flintr.tv.ui.common.UiConstants.FAVORITES_CATEGORY_ID
import com.flintr.tv.ui.common.UiConstants.NEW_CATEGORY_ID
import com.flintr.tv.ui.common.UiConstants.IN_PROGRESS_CATEGORY_ID
import com.flintr.tv.ui.common.asOwnSourcedItems
import com.flintr.tv.ui.common.useFavorites
import com.flintr.tv.ui.common.ContentFilterUtils
import com.flintr.tv.utils.error.reportError

private const val TAG = "LiveTVContent"

/**
 * Loads new category streams (all channels from last 7 days).
 */
private suspend fun loadNewCategoryStreams(
    cacheService: DataCacheService,
    categoryVisibilityService: CategoryVisibilityService,
    ioDispatcher: CoroutineDispatcher,
    onLoadingChanged: (Boolean) -> Unit,
    onStreamsLoaded: (List<LiveStream>) -> Unit
) {
    onLoadingChanged(true)
    withContext(ioDispatcher) {
        try {
            val cachedData = cacheService.loadCachedDataAsync()
            if (cachedData != null) {
                val recentStreams = ContentFilterUtils.filterRecentStreams(cachedData.channels)
                // Filter out streams from hidden categories
                val hiddenCategoryIds = categoryVisibilityService.getHiddenCategories(CategoryType.LIVE_TV)
                val filteredStreams = ContentFilterUtils.filterLiveStreamsByHiddenCategories(recentStreams, hiddenCategoryIds)
                onStreamsLoaded(filteredStreams)
            }
        } catch (e: Exception) {
            reportError(TAG, "Error loading new category streams", e)
        } finally {
            onLoadingChanged(false)
        }
    }
}

data class LiveTVContentParams(
    val viewModel: MainViewModel,
    val selectedCategoryId: String?,
    val focusRequester: FocusRequester,
    val favoritesRepository: FavoritesRepository,
    val userRepository: UserRepository,
    val credentialsRepository: CredentialsRepository,
    val cacheService: DataCacheService,
    val categoryVisibilityService: CategoryVisibilityService,
    val ioDispatcher: CoroutineDispatcher,
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
    val isNewCategory = params.selectedCategoryId == NEW_CATEGORY_ID
    val isInProgressCategory = params.selectedCategoryId == IN_PROGRESS_CATEGORY_ID
    
    // State for new category filtered streams
    var newCategoryStreams by remember { mutableStateOf<List<LiveStream>>(emptyList()) }
    var isLoadingNewCategory by remember { mutableStateOf(false) }
    
    // Load favorites using the hook
    val favoritesState = useFavorites<LiveStream>(
        isFavoritesCategory = isFavoritesCategory,
        favoritesRepository = params.favoritesRepository,
        userRepository = params.userRepository,
        credentialsRepository = params.credentialsRepository,
        cacheService = params.cacheService,
        favoriteType = FavoriteType.LIVE,
        idSelector = { it.streamId }
    )
    
    // Load new category streams (all channels from last 7 days)
    LaunchedEffect(params.selectedCategoryId) {
        // Reset scroll position when category changes
        params.lazyGridState.scrollToItem(0)

        when {
            isNewCategory -> {
                loadNewCategoryStreams(
                    cacheService = params.cacheService,
                    categoryVisibilityService = params.categoryVisibilityService,
                    ioDispatcher = params.ioDispatcher,
                    onLoadingChanged = { isLoadingNewCategory = it },
                    onStreamsLoaded = { newCategoryStreams = it }
                )
            }
            !isFavoritesCategory && !isInProgressCategory && params.selectedCategoryId != null -> {
                params.viewModel.loadLiveStreams(params.selectedCategoryId)
            }
        }
    }
    
    // Don't automatically request focus when streams are loaded
    // Focus will be set manually when navigating from CategorySidebar
    // This keeps the focus on the Navbar when the app starts
    
    // In-progress category should not be shown in LiveTV (contains only movies/episodes)
    // Show empty list if in-progress category is selected
    val displayStreams = when {
        isFavoritesCategory -> favoritesState.items
        isNewCategory -> asOwnSourcedItems(newCategoryStreams)
        isInProgressCategory -> emptyList() // In-progress category not applicable for LiveTV
        else -> asOwnSourcedItems(streams)
    }
    val isLoading = when {
        isFavoritesCategory -> favoritesState.isLoading
        isNewCategory -> isLoadingNewCategory
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
            keySelector = { stream -> "${stream.source.key}_${stream.item.streamId}" },
            contentIdSelector = { stream -> "live_${stream.source.key}_${stream.item.streamId}" },
            focusRequesterMap = params.focusRequesterMap
        ),
        content = { stream, index, columnIndex, onFocusChanged, cardFocusRequester ->
            LiveStreamCard(
                params = LiveStreamCardParams(
                    stream = stream.item,
                    onClick = { params.onChannelClick(stream.item) },
                    favoritesRepository = params.favoritesRepository,
                    config = CardConfig(
                        focusRequester = cardFocusRequester ?: if (index == 0 && displayStreams.isNotEmpty()) params.focusRequester else null,
                        onFocusChanged = onFocusChanged,
                        onNavigateLeft = if (columnIndex == 0) {
                            { params.onNavigateLeft() }
                        } else {
                            null
                        }
                    ),
                    sourceBadge = stream.source.badge,
                    allowFavoriteActions = stream.allowFavoriteActions
                )
            )
        }
    )
}
