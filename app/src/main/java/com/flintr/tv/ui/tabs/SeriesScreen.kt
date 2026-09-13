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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import com.flintr.tv.data.local.CategoryVisibilityService
import com.flintr.tv.data.local.CredentialsRepository
import com.flintr.tv.data.local.DataCacheService
import com.flintr.tv.data.local.FavoritesRepository
import com.flintr.tv.data.model.CategoryType
import com.flintr.tv.data.model.FavoriteType
import com.flintr.tv.data.model.Series
import com.flintr.tv.data.repository.UserRepository
import com.flintr.tv.ui.components.VODCard
import com.flintr.tv.ui.components.VODCardParams
import com.flintr.tv.ui.sidebars.ContentGridLayout
import com.flintr.tv.ui.sidebars.ContentGridLayoutParams
import com.flintr.tv.viewmodel.MainViewModel
import com.flintr.tv.ui.common.UiConstants.FAVORITES_CATEGORY_ID
import com.flintr.tv.ui.common.UiConstants.NEW_CATEGORY_ID
import com.flintr.tv.ui.common.asOwnSourcedItems
import com.flintr.tv.ui.common.useFavorites
import com.flintr.tv.ui.common.ContentFilterUtils
import com.flintr.tv.utils.error.reportError

private const val TAG = "SeriesContent"

/**
 * Logs debug information about series with and without lastModified field.
 */
private fun logSeriesDebugInfo(
    seriesWithLastModified: List<Series>,
    seriesWithoutLastModified: List<Series>,
    sevenDaysAgo: Long,
    totalCount: Int
) {
    android.util.Log.d(TAG, "Loading new category series: total=$totalCount, sevenDaysAgo=$sevenDaysAgo")
    android.util.Log.d(TAG, "Series with lastModified: ${seriesWithLastModified.size}, without: ${seriesWithoutLastModified.size}")
    
    if (seriesWithLastModified.isNotEmpty()) {
        val sample = seriesWithLastModified.take(5)
        sample.forEach { s ->
            val ts = s.lastModified?.toLongOrNull()
            android.util.Log.d(TAG, "Sample: ${s.name}, lastModified=${s.lastModified}, parsed=$ts, isRecent=${ts?.let { it >= sevenDaysAgo }}")
        }
    }
    
    if (seriesWithoutLastModified.isNotEmpty()) {
        android.util.Log.w(TAG, "Found ${seriesWithoutLastModified.size} series without lastModified field - cache may need refresh")
        val sample = seriesWithoutLastModified.take(3)
        sample.forEach { s ->
            android.util.Log.d(TAG, "Sample without lastModified: ${s.name}, seriesId=${s.seriesId}")
        }
    }
}

/**
 * Validates cache data and logs warnings if needed.
 */
private fun validateCacheData(
    seriesWithLastModified: List<Series>,
    allSeries: List<Series>
) {
    // If no series with lastModified found, the cache likely contains old data
    // In this case, show empty list (user needs to reload data)
    if (seriesWithLastModified.isEmpty() && allSeries.isNotEmpty()) {
        android.util.Log.w(TAG, "No series with lastModified field found. Cache needs to be refreshed. Please reload data in settings.")
    }
}

/**
 * Loads new category series (all series modified in last 7 days).
 */
private suspend fun loadNewCategorySeries(
    cacheService: DataCacheService,
    categoryVisibilityService: CategoryVisibilityService,
    ioDispatcher: CoroutineDispatcher,
    onLoadingChanged: (Boolean) -> Unit,
    onSeriesLoaded: (List<Series>) -> Unit
) {
    onLoadingChanged(true)
    withContext(ioDispatcher) {
        try {
            val cachedData = cacheService.loadCachedDataAsync()
            if (cachedData != null) {
                val sevenDaysAgo = ContentFilterUtils.sevenDaysAgoInSeconds()
                
                val seriesWithLastModified = cachedData.series.filter { it.lastModified != null }
                val seriesWithoutLastModified = cachedData.series.filter { it.lastModified == null }
                
                logSeriesDebugInfo(
                    seriesWithLastModified = seriesWithLastModified,
                    seriesWithoutLastModified = seriesWithoutLastModified,
                    sevenDaysAgo = sevenDaysAgo,
                    totalCount = cachedData.series.size
                )
                
                val recentSeries = ContentFilterUtils.filterRecentSeries(cachedData.series)
                validateCacheData(seriesWithLastModified, cachedData.series)
                
                // Filter out series from hidden categories
                val hiddenCategoryIds = categoryVisibilityService.getHiddenCategories(CategoryType.SERIES)
                val filteredSeries = ContentFilterUtils.filterSeriesByHiddenCategories(recentSeries, hiddenCategoryIds)
                
                android.util.Log.d(TAG, "Filtered new category series: ${filteredSeries.size}")
                onSeriesLoaded(filteredSeries)
            } else {
                android.util.Log.w(TAG, "Cached data is null")
            }
        } catch (e: Exception) {
            reportError(TAG, "Error loading new category series", e)
        } finally {
            onLoadingChanged(false)
        }
    }
}

/**
 * Parameters for SeriesContent composable.
 * Encapsulates all parameters to reduce function parameter count.
 */
data class SeriesContentParams(
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
    
    val isFavoritesCategory = params.selectedCategoryId == FAVORITES_CATEGORY_ID
    val isNewCategory = params.selectedCategoryId == NEW_CATEGORY_ID
    
    // State for new category filtered series
    var newCategorySeries by remember { mutableStateOf<List<Series>>(emptyList()) }
    var isLoadingNewCategory by remember { mutableStateOf(false) }
    
    // Load favorites using the hook
    val favoritesState = useFavorites<Series>(
        isFavoritesCategory = isFavoritesCategory,
        favoritesRepository = params.favoritesRepository,
        userRepository = params.userRepository,
        credentialsRepository = params.credentialsRepository,
        cacheService = params.cacheService,
        favoriteType = FavoriteType.SERIES,
        idSelector = { it.seriesId }
    )
    
    // Load new category series (all series modified in last 7 days)
    LaunchedEffect(params.selectedCategoryId) {
        // Reset scroll position when category changes
        params.lazyGridState.scrollToItem(0)

        when {
            isNewCategory -> {
                loadNewCategorySeries(
                    cacheService = params.cacheService,
                    categoryVisibilityService = params.categoryVisibilityService,
                    ioDispatcher = params.ioDispatcher,
                    onLoadingChanged = { isLoadingNewCategory = it },
                    onSeriesLoaded = { newCategorySeries = it }
                )
            }
            !isFavoritesCategory -> {
                // selectedCategoryId can legitimately be null (e.g. no category selected yet,
                // or a stale/foreign category from navigation-state restore that no longer
                // resolves to a real sidebar entry) - loadSeries(null) still loads all series
                // instead of silently showing "no content" with nothing ever requested.
                params.viewModel.loadSeries(params.selectedCategoryId)
            }
        }
    }
    
    // Don't automatically request focus when series are loaded
    // Focus will be set manually when navigating from CategorySidebar
    // This keeps the focus on the Navbar when the app starts
    
    val displaySeries = when {
        isFavoritesCategory -> favoritesState.items
        isNewCategory -> asOwnSourcedItems(newCategorySeries)
        else -> asOwnSourcedItems(series)
    }
    val isLoading = when {
        isFavoritesCategory -> favoritesState.isLoading
        isNewCategory -> isLoadingNewCategory
        else -> uiState.isLoading
    }
    
    ContentGridLayout(
        params = ContentGridLayoutParams(
            items = displaySeries,
            isLoading = isLoading,
            focusRequester = params.focusRequester,
            modifier = modifier,
            lazyGridState = params.lazyGridState,
            keySelector = { seriesItem -> "${seriesItem.source.key}_${seriesItem.item.seriesId}" },
            contentIdSelector = { seriesItem -> "series_${seriesItem.source.key}_${seriesItem.item.seriesId}" },
            focusRequesterMap = params.focusRequesterMap
        ),
        content = { seriesItem, _, columnIndex, onFocusChanged, cardFocusRequester ->
            SeriesItem(
                series = seriesItem.item,
                favoritesRepository = params.favoritesRepository,
                onSeriesClick = { params.onSeriesClick(seriesItem.item) },
                cardConfig = com.flintr.tv.ui.components.CardConfig(
                    isSelected = false,
                    onFocusChanged = onFocusChanged,
                    onNavigateLeft = if (columnIndex == 0) {
                        { params.onNavigateLeft() }
                    } else {
                        null
                    },
                    focusRequester = cardFocusRequester
                ),
                sourceBadge = seriesItem.source.badge,
                allowFavoriteActions = seriesItem.allowFavoriteActions
            )
        }
    )
}

@Composable
fun SeriesItem(
    series: Series,
    favoritesRepository: FavoritesRepository,
    onSeriesClick: () -> Unit,
    cardConfig: com.flintr.tv.ui.components.CardConfig = com.flintr.tv.ui.components.CardConfig(),
    sourceBadge: String? = null,
    allowFavoriteActions: Boolean = true
) {
    VODCard(
        params = VODCardParams(
            series = series,
            onClick = onSeriesClick,
            favoritesRepository = favoritesRepository,
            isSelected = cardConfig.isSelected,
            onFocusChanged = cardConfig.onFocusChanged,
            onNavigateLeft = cardConfig.onNavigateLeft,
            focusRequester = cardConfig.focusRequester,
            sourceBadge = sourceBadge,
            allowFavoriteActions = allowFavoriteActions
        )
    )
}

