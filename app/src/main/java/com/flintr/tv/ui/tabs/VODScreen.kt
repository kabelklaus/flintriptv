package com.flintr.tv.ui.tabs

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.flintr.tv.data.local.CategoryVisibilityService
import com.flintr.tv.data.local.CredentialsRepository
import com.flintr.tv.data.local.DataCacheService
import com.flintr.tv.data.local.WatchlistRepository
import com.flintr.tv.data.local.WatchProgressService
import com.flintr.tv.data.model.CategoryType
import com.flintr.tv.data.model.WatchlistEntryType
import com.flintr.tv.data.model.VodStream
import com.flintr.tv.data.repository.UserRepository
import com.flintr.tv.ui.sidebars.ContentGridLayout
import com.flintr.tv.ui.sidebars.ContentGridLayoutParams
import com.flintr.tv.ui.components.VODCard
import com.flintr.tv.ui.components.VODCardParams
import com.flintr.tv.viewmodel.MainViewModel
import com.flintr.tv.ui.common.UiConstants.WATCHLIST_CATEGORY_ID
import com.flintr.tv.ui.common.UiConstants.NEW_CATEGORY_ID
import com.flintr.tv.ui.common.UiConstants.PROGRESS_KEY_MOVIE_PREFIX
import com.flintr.tv.ui.common.asOwnSourcedItems
import com.flintr.tv.ui.common.useWatchlist
import com.flintr.tv.ui.common.ContentFilterUtils
import com.flintr.tv.utils.error.reportError

private const val TAG = "VODContent"

/**
 * Loads new category streams (all movies from last 7 days) and watched IDs.
 */
private suspend fun loadNewCategoryStreams(
    cacheService: DataCacheService,
    categoryVisibilityService: CategoryVisibilityService,
    watchProgressService: WatchProgressService,
    ioDispatcher: CoroutineDispatcher,
    onLoadingChanged: (Boolean) -> Unit,
    onStreamsLoaded: (List<VodStream>) -> Unit,
    onWatchedIdsLoaded: (Set<Int>) -> Unit
) {
    onLoadingChanged(true)
    withContext(ioDispatcher) {
        try {
            val cachedData = cacheService.loadCachedDataAsync()
            if (cachedData != null) {
                val recentMovies = ContentFilterUtils.filterRecentMovies(cachedData.movies)
                // Filter out movies from hidden categories
                val hiddenCategoryIds = categoryVisibilityService.getHiddenCategories(CategoryType.VOD)
                val filteredMovies = ContentFilterUtils.filterVodStreamsByHiddenCategories(recentMovies, hiddenCategoryIds)
                onStreamsLoaded(filteredMovies)
                // Load watched IDs for new category (all movies)
                onWatchedIdsLoaded(watchProgressService.getWatchedVodIds(null))
            }
        } catch (e: Exception) {
            reportError(TAG, "Error loading new category streams", e)
        } finally {
            onLoadingChanged(false)
        }
    }
}

/**
 * Loads watched VOD IDs for a specific category.
 */
private suspend fun loadWatchedVodIds(
    watchProgressService: WatchProgressService,
    categoryId: String?,
    onWatchedIdsLoaded: (Set<Int>) -> Unit
) {
    onWatchedIdsLoaded(watchProgressService.getWatchedVodIds(categoryId))
}

data class VODContentParams(
    val viewModel: MainViewModel,
    val selectedCategoryId: String?,
    val focusRequester: FocusRequester,
    val watchlistRepository: WatchlistRepository,
    val userRepository: UserRepository,
    val credentialsRepository: CredentialsRepository,
    val cacheService: DataCacheService,
    val categoryVisibilityService: CategoryVisibilityService,
    val watchProgressService: WatchProgressService,
    val ioDispatcher: CoroutineDispatcher,
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
    // One reactive map in the grid - cards only do a local lookup instead of per-card IO.
    val watchProgressByKey by params.watchProgressService.watchProgress.collectAsState()
    
    val isWatchlistCategory = params.selectedCategoryId == WATCHLIST_CATEGORY_ID
    val isNewCategory = params.selectedCategoryId == NEW_CATEGORY_ID

    // State for new category filtered streams
    var newCategoryStreams by remember { mutableStateOf<List<VodStream>>(emptyList()) }
    var isLoadingNewCategory by remember { mutableStateOf(false) }

    // Load watchlist entries using the hook
    val watchlistState = useWatchlist<VodStream>(
        isWatchlistCategory = isWatchlistCategory,
        watchlistRepository = params.watchlistRepository,
        userRepository = params.userRepository,
        credentialsRepository = params.credentialsRepository,
        cacheService = params.cacheService,
        watchlistEntryType =WatchlistEntryType.MOVIE,
        idSelector = { it.streamId }
    )
    
    // Batch-load watched VOD IDs for the current category
    var watchedVodIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    
    // Load new category streams (all movies from last 7 days)
    LaunchedEffect(params.selectedCategoryId) {
        when {
            isNewCategory -> {
                loadNewCategoryStreams(
                    cacheService = params.cacheService,
                    categoryVisibilityService = params.categoryVisibilityService,
                    watchProgressService = params.watchProgressService,
                    ioDispatcher = params.ioDispatcher,
                    onLoadingChanged = { isLoadingNewCategory = it },
                    onStreamsLoaded = { newCategoryStreams = it },
                    onWatchedIdsLoaded = { watchedVodIds = it }
                )
            }
            isWatchlistCategory -> {
                // For the watchlist, load all watched VODs (category filtering not needed)
                loadWatchedVodIds(
                    watchProgressService = params.watchProgressService,
                    categoryId = null,
                    onWatchedIdsLoaded = { watchedVodIds = it }
                )
            }
            params.selectedCategoryId != null -> {
                params.viewModel.loadVodStreams(params.selectedCategoryId)
                // Load watched IDs for the category
                loadWatchedVodIds(
                    watchProgressService = params.watchProgressService,
                    categoryId = params.selectedCategoryId,
                    onWatchedIdsLoaded = { watchedVodIds = it }
                )
            }
        }
        // scrollToItem suspends until a layout pass arrives; with an empty/static grid that can
        // take arbitrarily long. Run it in a child job so the effect completes deterministically
        // and the previous category's scroll job is cancelled by the next key change.
        launch { params.lazyGridState.scrollToItem(0) }
    }
    
    // Don't automatically request focus when streams are loaded
    // Focus will be set manually when navigating from CategorySidebar
    // This keeps the focus on the Navbar when the app starts
    
    val displayStreams = remember(isWatchlistCategory, isNewCategory, watchlistState.items, newCategoryStreams, streams) {
        when {
            isWatchlistCategory -> watchlistState.items
            isNewCategory -> asOwnSourcedItems(newCategoryStreams)
            else -> asOwnSourcedItems(streams)
        }
    }
    val isLoading = when {
        isWatchlistCategory -> watchlistState.isLoading
        isNewCategory -> isLoadingNewCategory
        else -> uiState.isLoading
    }
    
    ContentGridLayout(
        params = ContentGridLayoutParams(
            items = displayStreams,
            isLoading = isLoading,
            focusRequester = params.focusRequester,
            modifier = params.modifier,
            lazyGridState = params.lazyGridState,
            keySelector = { vod -> "${vod.source.key}_${vod.item.streamId}" },
            contentIdSelector = { vod -> "vod_${vod.source.key}_${vod.item.streamId}" },
            focusRequesterMap = params.focusRequesterMap
        ),
        content = { vod, _, columnIndex, onFocusChanged, cardFocusRequester ->
            VODCard(
                params = VODCardParams(
                    vod = vod.item,
                    onClick = { params.onVodClick(vod.item) },
                    watchlistRepository = params.watchlistRepository,
                    watchProgressService = params.watchProgressService,
                    onFocusChanged = onFocusChanged,
                    onNavigateLeft = if (columnIndex == 0) {
                        { params.onNavigateLeft() }
                    } else {
                        null
                    },
                    focusRequester = cardFocusRequester,
                    isWatched = vod.item.streamId in watchedVodIds,
                    sourceBadge = vod.source.badge,
                    allowWatchlistActions = vod.allowWatchlistActions,
                    watchProgress = watchProgressByKey[
                        PROGRESS_KEY_MOVIE_PREFIX + vod.item.streamId
                    ],
                    watchProgressResolved = true
                )
            )
        }
    )
}
