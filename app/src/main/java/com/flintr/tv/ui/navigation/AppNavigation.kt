package com.flintr.tv.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.tvFocusRing
import com.flintr.tv.ui.utils.onClick
import androidx.compose.foundation.lazy.grid.LazyGridState
import android.net.Uri
import android.util.Log
import com.flintr.tv.BuildConfig
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.flintr.tv.data.local.CategoryVisibilityService
import com.flintr.tv.data.local.Credentials
import com.flintr.tv.data.local.CredentialsRepository
import com.flintr.tv.data.local.DataCacheService
import com.flintr.tv.data.local.WatchlistRepository
import com.flintr.tv.data.local.PlaybackSettingsRepository
import com.flintr.tv.data.local.RecommendationService
import com.flintr.tv.data.local.WatchProgressService
import com.flintr.tv.data.repository.UserRepository
import com.flintr.tv.data.repository.XtreamRepository
import com.flintr.tv.data.model.Category
import com.flintr.tv.data.model.CategoryType
import com.flintr.tv.data.model.LiveStream
import com.flintr.tv.data.model.NavigationStateBeforePlayer
import com.flintr.tv.data.model.Episode
import com.flintr.tv.data.model.Series
import com.flintr.tv.data.model.VodStream
import com.flintr.tv.ui.components.MainLayout
import com.flintr.tv.ui.components.MainLayoutParams
import com.flintr.tv.ui.screens.DataLoadingScreen
import com.flintr.tv.ui.screens.EPGContent
import com.flintr.tv.ui.screens.HomeDestination
import com.flintr.tv.ui.screens.LoginScreen
import com.flintr.tv.ui.screens.PlayerScreen
import com.flintr.tv.ui.screens.StreamInfo
import com.flintr.tv.ui.settings.SettingsScreen
import com.flintr.tv.ui.tabs.LiveTVContent
import com.flintr.tv.ui.tabs.LiveTVContentParams
import com.flintr.tv.ui.tabs.VODContent
import com.flintr.tv.ui.tabs.VODContentParams
import com.flintr.tv.ui.tabs.SeriesContent
import com.flintr.tv.ui.tabs.SeriesContentParams
import com.flintr.tv.ui.tabs.InProgressContent
import com.flintr.tv.ui.tabs.InProgressContentParams
import com.flintr.tv.ui.tabs.InProgressItem
import com.flintr.tv.ui.tabs.InProgressItemsState
import com.flintr.tv.ui.tabs.rememberInProgressItems
import com.flintr.tv.ui.tabs.SeriesItem
import com.flintr.tv.ui.sidebars.ContentGridLayout
import com.flintr.tv.ui.sidebars.ContentGridLayoutParams
import com.flintr.tv.ui.components.VODCard
import com.flintr.tv.ui.components.VODCardParams
import com.flintr.tv.ui.screens.MovieDetailScreen
import com.flintr.tv.ui.screens.SeasonScreen
import com.flintr.tv.ui.screens.SeriesDetailScreen
import com.flintr.tv.viewmodel.MainViewModel
import com.flintr.tv.viewmodel.MovieDetailViewModel
import com.flintr.tv.viewmodel.SeriesDetailViewModel
import com.flintr.tv.viewmodel.UpdateViewModel
import com.flintr.tv.viewmodel.NextEpisodeInfo
import com.flintr.tv.data.model.UpdateInfo
import com.flintr.tv.utils.DownloadProgress
import com.flintr.tv.ui.utils.safeRequestFocus
import com.flintr.tv.ui.components.UpdateDialog
import com.flintr.tv.ui.components.SearchOverlay
import com.flintr.tv.ui.components.SearchResultCallbacks
import com.flintr.tv.ui.common.UiConstants.WATCHLIST_CATEGORY_ID
import com.flintr.tv.ui.common.UiConstants.NEW_CATEGORY_ID
import com.flintr.tv.ui.common.UiConstants.IN_PROGRESS_CATEGORY_ID
import com.flintr.tv.ui.common.UiConstants.RECOMMENDED_CATEGORY_ID
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import com.flintr.tv.ui.theme.appColors
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.res.stringResource
import com.flintr.tv.R
import com.flintr.tv.utils.loadPackageInfo
import kotlinx.coroutines.CoroutineDispatcher
import com.flintr.tv.utils.error.reportWarning

sealed class Screen(val route: String) {
    object InitialRouting : Screen("initial_routing")
    object Login : Screen("login")
    object DataLoading : Screen("data_loading")
    object Main : Screen("main")
    object Settings : Screen("settings")
    object Player : Screen("player/{streamUrl}/{contentType}/{streamId}/{seriesId}/{episodeId}/{title}/{categoryId}/{categoryName}") {
        const val EMPTY_TITLE_PLACEHOLDER = "__EMPTY_TITLE__"
        const val EMPTY_CATEGORY_PLACEHOLDER = "__EMPTY_CATEGORY__"
        
        fun createRoute(streamUrl: String): String {
            val encoded = Uri.encode(streamUrl, null)
            return "player/$encoded/live/-1/-1/-1/$EMPTY_TITLE_PLACEHOLDER/$EMPTY_CATEGORY_PLACEHOLDER/$EMPTY_CATEGORY_PLACEHOLDER"
        }
        
        fun createRouteForMovie(streamUrl: String, streamId: Int, title: String): String {
            val encodedUrl = Uri.encode(streamUrl, null)
            val encodedTitle = if (title.isNotEmpty()) Uri.encode(title, null) else EMPTY_TITLE_PLACEHOLDER
            return "player/$encodedUrl/movie/$streamId/-1/-1/$encodedTitle/$EMPTY_CATEGORY_PLACEHOLDER/$EMPTY_CATEGORY_PLACEHOLDER"
        }
        
        fun createRouteForSeries(streamUrl: String, seriesId: Int, episodeId: Int, title: String): String {
            val encodedUrl = Uri.encode(streamUrl, null)
            val encodedTitle = if (title.isNotEmpty()) Uri.encode(title, null) else EMPTY_TITLE_PLACEHOLDER
            return "player/$encodedUrl/series/$episodeId/$seriesId/$episodeId/$encodedTitle/$EMPTY_CATEGORY_PLACEHOLDER/$EMPTY_CATEGORY_PLACEHOLDER"
        }
        
        fun createRouteForLive(streamUrl: String, channelName: String = "", categoryId: String? = null, categoryName: String? = null, streamId: Int? = null): String {
            val encodedUrl = Uri.encode(streamUrl, null)
            val encodedTitle = if (channelName.isNotEmpty()) Uri.encode(channelName, null) else EMPTY_TITLE_PLACEHOLDER
            val encodedCategoryId = if (categoryId != null) Uri.encode(categoryId, null) else EMPTY_CATEGORY_PLACEHOLDER
            val encodedCategoryName = if (categoryName != null) Uri.encode(categoryName, null) else EMPTY_CATEGORY_PLACEHOLDER
            val streamIdStr = streamId?.toString() ?: "-1"
            return "player/$encodedUrl/live/$streamIdStr/-1/-1/$encodedTitle/$encodedCategoryId/$encodedCategoryName"
        }
    }
    
    /**
     * Detail-first navigation (finding 3): only IDs are passed, never stream URLs or credentials.
     */
    object MovieDetail : Screen("movie_detail/{vodId}") {
        const val ARG_VOD_ID = "vodId"

        fun createRoute(vodId: Int): String = "movie_detail/$vodId"
    }

    object SeriesDetail : Screen("series_detail/{seriesId}") {
        const val ARG_SERIES_ID = "seriesId"

        fun createRoute(seriesId: Int): String = "series_detail/$seriesId"
    }

    object Season : Screen("season/{seriesId}/{seasonNumber}") {
        const val ARG_SERIES_ID = "seriesId"
        const val ARG_SEASON_NUMBER = "seasonNumber"

        fun createRoute(seriesId: Int, seasonNumber: Int): String =
            "season/$seriesId/$seasonNumber"
    }
}

/**
 * Handles deep link URIs and converts them to navigation routes.
 * 
 * @param uri Deep link URI (flintrtv://play?type=...&streamId=...)
 * @param credentials Credentials for building stream URLs
 * @param cacheService Optional DataCacheService to load container extension from cache
 * @return Navigation route string or null if invalid
 */
suspend fun handleDeepLink(
    uri: Uri,
    credentials: com.flintr.tv.data.local.Credentials?,
    cacheService: com.flintr.tv.data.local.DataCacheService? = null
): String? {
    if (credentials == null) {
        Log.w("AppNavigation", "Cannot handle deep link: no credentials")
        return null
    }
    
    val type = uri.getQueryParameter("type") ?: return null
    
    return when (type.lowercase()) {
        "vod" -> {
            val streamIdStr = uri.getQueryParameter("streamId")
            val streamId = streamIdStr?.toIntOrNull() ?: return null
            
            // Try to get extension from cache, fallback to "mkv"
            val extension = cacheService?.loadCachedDataAsync()
                ?.movies
                ?.find { it.streamId == streamId }
                ?.containerExtension
                ?: "mkv"
            
            val streamUrl = "${credentials.serverUrl}/movie/${credentials.username}/${credentials.password}/$streamId.$extension"
            
            // Try to get title from VOD data (optional)
            val title = uri.getQueryParameter("title") ?: ""
            
            Screen.Player.createRouteForMovie(streamUrl, streamId, title)
        }
        "series" -> {
            val seriesIdStr = uri.getQueryParameter("seriesId")
            val episodeIdStr = uri.getQueryParameter("episodeId")
            
            val seriesId = seriesIdStr?.toIntOrNull() ?: return null
            val episodeId = episodeIdStr?.toIntOrNull() ?: return null

            // Try to get extension from cache, fallback to "mkv"
            val extension = cacheService?.loadCachedDataAsync()
                ?.series
                ?.find { it.seriesId == seriesId }
                ?.seasons
                ?.asSequence()
                ?.flatMap { it.episodes?.asSequence() ?: emptySequence() }
                ?.find { it.id == episodeId }
                ?.containerExtension
                ?: "mkv"
            
            val streamUrl = "${credentials.serverUrl}/series/${credentials.username}/${credentials.password}/$episodeId.$extension"
            
            // Try to get title from episode data (optional)
            val title = uri.getQueryParameter("title") ?: ""
            
            Screen.Player.createRouteForSeries(streamUrl, seriesId, episodeId, title)
        }
        else -> {
            Log.w("AppNavigation", "Unknown deep link type: $type")
            null
        }
    }
}

private fun NavHostController.logAndNavigate(route: String, tag: String, message: () -> String) {
    if (BuildConfig.DEBUG) {
        android.util.Log.d(tag, message())
    }
    navigate(route)
}

/**
 * Navigates to the next episode in a series
 */
private fun navigateToNextEpisode(
    navController: NavHostController,
    credentials: Credentials?,
    seriesId: Int?,
    nextEpisodeInfo: NextEpisodeInfo
) {
    if (credentials == null || seriesId == null) return
    
    val streamUrl = nextEpisodeInfo.episode.getStreamUrl(
        credentials.serverUrl,
        credentials.username,
        credentials.password,
        seriesId
    )
    
    // Create SeriesContext for the next episode
    // We need to find the Series object - try to get it from MainViewModel or load it
    // For now, we'll pass null and let PlayerViewModel load it
    val route = Screen.Player.createRouteForSeries(
        streamUrl,
        seriesId,
        nextEpisodeInfo.episode.id,
        nextEpisodeInfo.episode.title
    )
    
    navController.navigate(route) {
        // Replace current player screen with next episode
        popUpTo(Screen.Player.route) { inclusive = true }
    }
}

/**
 * Helper functions for generating content IDs according to the schema:
 * - Live TV: "live_${streamId}"
 * - VOD: "vod_${streamId}"
 * - Series: "series_${seriesId}_${seasonNum}_${episodeId}"
 */
private fun LiveStream.toContentId(): String = "live_$streamId"
private fun VodStream.toContentId(): String = "vod_$streamId"
private fun Series.toContentId(): String = "series_$seriesId"

/**
 * Detail-first click targets. Kept as small pure helpers so the Phase-4 route contract is
 * testable without a NavHost (IDs only, explicit player routes for "watch"/"resume").
 */
internal fun vodDetailRoute(vodId: Int): String = Screen.MovieDetail.createRoute(vodId)

internal fun seriesDetailRoute(seriesId: Int): String = Screen.SeriesDetail.createRoute(seriesId)

internal fun seasonRoute(seriesId: Int, seasonNumber: Int): String =
    Screen.Season.createRoute(seriesId, seasonNumber)

/** "Das könnte dir gefallen" cards on a detail screen open the recommended item's detail. */
internal fun movieRecommendationClickHandler(navigate: (String) -> Unit): (Int) -> Unit =
    { movieId -> navigate(vodDetailRoute(movieId)) }

internal fun seriesRecommendationClickHandler(navigate: (String) -> Unit): (Int) -> Unit =
    { seriesId -> navigate(seriesDetailRoute(seriesId)) }

internal fun moviePlayerRoute(
    credentials: Credentials,
    vodId: Int,
    title: String?,
    containerExtension: String?
): String {
    val extension = containerExtension ?: "mkv"
    val streamUrl = "${credentials.serverUrl}/movie/${credentials.username}/${credentials.password}/$vodId.$extension"
    return Screen.Player.createRouteForMovie(streamUrl, vodId, title.orEmpty())
}

internal fun episodePlayerRoute(
    credentials: Credentials,
    seriesId: Int,
    episode: Episode
): String {
    val streamUrl = episode.getStreamUrl(
        credentials.serverUrl,
        credentials.username,
        credentials.password,
        seriesId
    )
    return Screen.Player.createRouteForSeries(streamUrl, seriesId, episode.id, episode.title)
}

/**
 * The series-detail watch button uses the exact same player route contract as a SeasonScreen
 * episode click; only the target episode is resolved by SeriesDetailViewModel.
 */
internal fun seriesDetailWatchRoute(
    credentials: Credentials,
    seriesId: Int,
    episode: Episode
): String = episodePlayerRoute(credentials, seriesId, episode)

/**
 * Helper function to generate content ID for episode
 * Note: SeasonScreen is a separate screen, so we don't save navigation state there
 * The state is saved when navigating from the Series grid to SeriesDetailScreen
 */
private fun com.flintr.tv.data.model.Episode.toContentId(seriesId: Int, seasonNum: Int): String {
    return "series_${seriesId}_${seasonNum}_$id"
}

/**
 * Helper function to convert HomeDestination to DestinationType enum
 */
private fun HomeDestination.toDestinationType(): com.flintr.tv.data.model.DestinationType {
    return when (this) {
        is HomeDestination.LiveTV -> com.flintr.tv.data.model.DestinationType.LIVE_TV
        is HomeDestination.VOD -> com.flintr.tv.data.model.DestinationType.VOD
        is HomeDestination.Series -> com.flintr.tv.data.model.DestinationType.SERIES
        else -> com.flintr.tv.data.model.DestinationType.UNKNOWN
    }
}

/**
 * Helper function to convert DestinationType enum to HomeDestination
 */
private fun com.flintr.tv.data.model.DestinationType.toHomeDestination(): HomeDestination {
    return when (this) {
        com.flintr.tv.data.model.DestinationType.LIVE_TV -> HomeDestination.LiveTV
        com.flintr.tv.data.model.DestinationType.VOD -> HomeDestination.VOD
        com.flintr.tv.data.model.DestinationType.SERIES -> HomeDestination.Series
        com.flintr.tv.data.model.DestinationType.UNKNOWN -> HomeDestination.LiveTV // Default fallback
        else -> HomeDestination.LiveTV // Default fallback for WATCHLIST and other types
    }
}

private const val LOG_TAG_MAIN_CONTENT_SCREEN = "[MainContentScreen]"
private const val MIN_LOADING_DURATION_MS = 300L
private const val DESTINATION_KEY_LIVE_TV = "live_tv"
private const val DESTINATION_KEY_VOD = "vod"
private const val DESTINATION_KEY_SERIES = "series"
private const val DESTINATION_KEY_EPG = "epg"
private const val DESTINATION_KEY_SEARCH = "search"
private const val DESTINATION_KEY_SETTINGS = "settings"

/**
 * Converts HomeDestination to a saveable key for rememberSaveable state restoration.
 */
private fun HomeDestination.toSaveKey(): String = when (this) {
    is HomeDestination.LiveTV -> DESTINATION_KEY_LIVE_TV
    is HomeDestination.VOD -> DESTINATION_KEY_VOD
    is HomeDestination.Series -> DESTINATION_KEY_SERIES
    is HomeDestination.EPG -> DESTINATION_KEY_EPG
    is HomeDestination.Search -> DESTINATION_KEY_SEARCH
    is HomeDestination.Settings -> DESTINATION_KEY_SETTINGS
}

/**
 * Restores HomeDestination from saveable key with LiveTV as fallback.
 */
private fun String.toHomeDestination(): HomeDestination = when (this) {
    DESTINATION_KEY_LIVE_TV -> HomeDestination.LiveTV
    DESTINATION_KEY_VOD -> HomeDestination.VOD
    DESTINATION_KEY_SERIES -> HomeDestination.Series
    DESTINATION_KEY_EPG -> HomeDestination.EPG
    DESTINATION_KEY_SEARCH -> HomeDestination.Search
    DESTINATION_KEY_SETTINGS -> HomeDestination.Settings
    else -> HomeDestination.LiveTV
}

/**
 * Restores the destination (tab) and waits for UI propagation.
 */
private suspend fun restoreDestination(
    state: NavigationStateBeforePlayer,
    currentDestination: HomeDestination,
    onDestinationChanged: (HomeDestination) -> Unit
): HomeDestination {
    val destination = state.destinationType.toHomeDestination()
    if (currentDestination != destination) {
        onDestinationChanged(destination)
        // Wait for destination change to propagate (minimal delay for UI composition)
        kotlinx.coroutines.delay(50)
    }
    return destination
}

/**
 * Data class grouping parameters for category restoration
 */
private data class CategoryRestoreContext(
    val state: NavigationStateBeforePlayer,
    val destination: HomeDestination,
    val currentCategoryId: String?,
    val onCategoryIdChanged: (String?) -> Unit,
    val sortedCategories: List<Category>,
    val liveCategories: List<Category>,
    val vodCategories: List<Category>,
    val seriesCategories: List<Category>
)

/**
 * Restores the category with fallback logic if category no longer exists.
 */
private suspend fun restoreCategory(context: CategoryRestoreContext) {
    if (context.state.categoryId == null) return
    
    // Step 1: Check if category exists in sortedCategories (includes special categories like __in_progress__)
    val categoryExistsInSorted = context.sortedCategories.any { it.categoryId == context.state.categoryId }
    
    if (categoryExistsInSorted) {
        if (context.currentCategoryId != context.state.categoryId) {
            context.onCategoryIdChanged(context.state.categoryId)
        }
        return
    }
    
    // Step 2: If not found in sortedCategories, check in raw category lists
    // This handles cases where the category was filtered out (e.g., hidden categories)
    // but should still be restored if it exists in the raw data
    val categoryExistsInRaw = getRawCategoriesForDestination(
        destination = context.destination,
        liveCategories = context.liveCategories,
        vodCategories = context.vodCategories,
        seriesCategories = context.seriesCategories
    ).any { it.categoryId == context.state.categoryId }
    
    if (categoryExistsInRaw) {
        // Category exists in raw lists, restore it even if it's currently filtered/hidden
        if (context.currentCategoryId != context.state.categoryId) {
            context.onCategoryIdChanged(context.state.categoryId)
        }
        return
    }
    
    // Step 3: Category not found in either sortedCategories or raw lists - use fallback
    Log.w(LOG_TAG_MAIN_CONTENT_SCREEN, "Category ${context.state.categoryId} no longer exists, selecting first available category")
    val firstCategory = context.sortedCategories.firstOrNull()
    context.onCategoryIdChanged(firstCategory?.categoryId)
}

/**
 * Waits for content to be loaded with timeout. [isLoading] is polled live (not a
 * one-time snapshot) so the loop actually observes the load finishing.
 */
private suspend fun waitForContentLoaded(isLoading: () -> Boolean) {
    var retryCount = 0
    val maxRetries = 20
    while (isLoading() && retryCount < maxRetries) {
        kotlinx.coroutines.delay(100)
        retryCount++
    }
    if (retryCount >= maxRetries && isLoading()) {
        Log.w(LOG_TAG_MAIN_CONTENT_SCREEN, "Timeout waiting for content to load, proceeding anyway")
    }
}

/**
 * Restores the scroll position to the saved index and offset.
 */
private suspend fun restoreScrollPosition(
    state: NavigationStateBeforePlayer,
    lazyGridState: androidx.compose.foundation.lazy.grid.LazyGridState
) {
    try {
        if (state.scrollIndex >= 0) {
            lazyGridState.scrollToItem(
                index = state.scrollIndex,
                scrollOffset = state.scrollOffset
            )
            // Minimal delay for scroll animation to start (UI stabilization)
            kotlinx.coroutines.delay(50)
        }
    } catch (e: Exception) {
        reportWarning(LOG_TAG_MAIN_CONTENT_SCREEN, "Failed to restore scroll position", e)
    }
}

/**
 * Restores focus on the content card with retry logic and fallback.
 */
private suspend fun restoreFocus(
    state: NavigationStateBeforePlayer,
    focusRequesterMap: MutableMap<String, FocusRequester>
) {
    // Try to find FocusRequester, with retries if not immediately available
    var focusRetryCount = 0
    val maxFocusRetries = 10
    var focusRequester: FocusRequester? = null
    
    while (focusRetryCount < maxFocusRetries && focusRequester == null) {
        focusRequester = focusRequesterMap[state.contentId]
        if (focusRequester == null) {
            kotlinx.coroutines.delay(50)
            focusRetryCount++
        }
    }
    
    if (focusRequester != null) {
        val focused = focusRequester.safeRequestFocus(
            logTag = LOG_TAG_MAIN_CONTENT_SCREEN,
            targetName = "ContentCard_${state.contentId}"
        )
        if (focused) {
            Log.d(LOG_TAG_MAIN_CONTENT_SCREEN, "Focus restored on content card: ${state.contentId}")
        } else {
            requestFallbackFocus(focusRequesterMap)
        }
    } else {
        Log.w(LOG_TAG_MAIN_CONTENT_SCREEN, "FocusRequester not found for contentId: ${state.contentId} after retries, focusing first available card")
        requestFallbackFocus(focusRequesterMap)
    }
}

/**
 * Attempts to focus the first available card as fallback.
 * Only acts on a requester that is actually present in the map; a not-yet-attached
 * FocusRequester is a normal timing race, not a crash-worthy condition.
 */
private suspend fun requestFallbackFocus(focusRequesterMap: MutableMap<String, FocusRequester>) {
    val fallbackFocusRequester = focusRequesterMap.values.firstOrNull() ?: return
    fallbackFocusRequester.safeRequestFocus(logTag = LOG_TAG_MAIN_CONTENT_SCREEN, targetName = "FallbackContentCard")
}

/**
 * Data class grouping parameters for navigation state restoration
 */
private data class NavigationRestoreContext(
    val state: NavigationStateBeforePlayer,
    val currentDestination: HomeDestination,
    val currentCategoryId: String?,
    val onDestinationChanged: (HomeDestination) -> Unit,
    val onCategoryIdChanged: (String?) -> Unit,
    val liveCategories: List<Category>,
    val vodCategories: List<Category>,
    val seriesCategories: List<Category>,
    val sortedCategories: List<Category>,
    val lazyGridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    val focusRequesterMap: MutableMap<String, FocusRequester>,
    val mainViewModel: com.flintr.tv.viewmodel.MainViewModel
)

/**
 * Restores navigation state (destination, category, scroll position, focus)
 * This function handles the complete restoration process with fallbacks.
 */
private suspend fun restoreNavigationState(context: NavigationRestoreContext) {
    Log.d(LOG_TAG_MAIN_CONTENT_SCREEN, "Restoring navigation state: destinationType=${context.state.destinationType}, categoryId=${context.state.categoryId}, contentId=${context.state.contentId}, scrollIndex=${context.state.scrollIndex}, scrollOffset=${context.state.scrollOffset}")
    
    // 1. Set destination (tab) - with fallback
    val destination = restoreDestination(context.state, context.currentDestination, context.onDestinationChanged)
    
    // 2. Set category (if available) - with fallback
    restoreCategory(
        CategoryRestoreContext(
            state = context.state,
            destination = destination,
            currentCategoryId = context.currentCategoryId,
            onCategoryIdChanged = context.onCategoryIdChanged,
            sortedCategories = context.sortedCategories,
            liveCategories = context.liveCategories,
            vodCategories = context.vodCategories,
            seriesCategories = context.seriesCategories
        )
    )
    
    // 3. Wait for content to be loaded (polls live state instead of a fixed delay)
    waitForContentLoaded { context.mainViewModel.uiState.value.isLoading }
    
    // 4. Restore scroll position
    restoreScrollPosition(context.state, context.lazyGridState)
    
    // 5. Set focus on content card - wait for FocusRequester to be available
    restoreFocus(context.state, context.focusRequesterMap)
    
    // 6. Clear pending navigation restore
    context.mainViewModel.clearPendingNavigationRestore()
    Log.d(LOG_TAG_MAIN_CONTENT_SCREEN, "Navigation state restoration completed")
}

/**
 * Data class grouping search click handlers for MainContentScreen
 */
private data class SearchClickHandlers(
    val onLiveStreamClick: (LiveStream) -> Unit,
    val onVodClick: (VodStream) -> Unit,
    val onSeriesClick: (Series) -> Unit
)

@Composable
private fun InitialRoutingScreen(
    navController: NavHostController,
    credentialsRepository: CredentialsRepository,
    cacheService: DataCacheService
) {
    LaunchedEffect(Unit) {
        val destination = if (credentialsRepository.hasCredentials()) {
            // Check if cache is valid, if yes go to Main, otherwise to DataLoading
            val isCacheValid = cacheService.isCacheValid()
            if (isCacheValid) {
                Screen.Main.route
            } else {
                Screen.DataLoading.route
            }
        } else {
            Screen.Login.route
        }
        
        navController.navigate(destination) {
            popUpTo(Screen.InitialRouting.route) { inclusive = true }
        }
    }
    
    // Minimal UI - empty box to prevent flicker
    Box(modifier = Modifier.fillMaxSize())
}

/**
 * Data class grouping dependencies for AppNavigation
 */
data class AppNavigationDependencies(
    val credentialsRepository: CredentialsRepository,
    val cacheService: DataCacheService,
    val watchlistRepository: WatchlistRepository,
    val watchProgressService: WatchProgressService,
    val categoryVisibilityService: CategoryVisibilityService,
    val playbackSettingsRepository: PlaybackSettingsRepository,
    val xtreamRepository: XtreamRepository,
    val userRepository: UserRepository,
    val recommendationService: RecommendationService,
    val ioDispatcher: CoroutineDispatcher
)

@Composable
fun AppNavigation(
    navController: NavHostController,
    dependencies: AppNavigationDependencies,
    mainViewModel: MainViewModel = hiltViewModel()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.InitialRouting.route
    ) {
        composable(Screen.InitialRouting.route) {
            InitialRoutingScreen(
                navController = navController,
                credentialsRepository = dependencies.credentialsRepository,
                cacheService = dependencies.cacheService
            )
        }

        composable(Screen.Login.route) {
            val activeCredentials by dependencies.credentialsRepository.credentialsFlow.collectAsState()
            val loginPrefill = remember { dependencies.credentialsRepository.getLoginPrefill() }
            LoginScreen(
                initialServerUrl = activeCredentials?.serverUrl ?: "",
                initialUsername = loginPrefill.username,
                initialPassword = loginPrefill.password,
                onLoginSuccess = {
                    // Navigate to DataLoadingScreen after successful login
                    navController.navigate(Screen.DataLoading.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.DataLoading.route) {
            DataLoadingScreen(
                onLoadingComplete = { _ ->
                    // Refresh MainViewModel data after successful loading
                    mainViewModel.refreshData()
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.DataLoading.route) { inclusive = true }
                    }
                },
                onReconfigureServer = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.DataLoading.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Main.route) {
            MainContentScreen(
                navController = navController,
                mainViewModel = mainViewModel,
                dependencies = dependencies
            )
        }
        
        composable(Screen.Settings.route) {
            SettingsScreen(
                focusRequester = remember { FocusRequester() },
                onNavigateLeft = { navController.popBackStack() },
                categoryVisibilityService = dependencies.categoryVisibilityService,
                mainViewModel = mainViewModel,
                credentialsRepository = dependencies.credentialsRepository,
                playbackSettingsRepository = dependencies.playbackSettingsRepository
            )
        }
        
        composable(
            route = Screen.Player.route,
            arguments = listOf(
                navArgument("streamUrl") {
                    type = NavType.StringType
                },
                navArgument("contentType") {
                    type = NavType.StringType
                    defaultValue = "live"
                },
                navArgument("streamId") {
                    type = NavType.StringType
                    defaultValue = "-1"
                },
                navArgument("seriesId") {
                    type = NavType.StringType
                    defaultValue = "-1"
                },
                navArgument("episodeId") {
                    type = NavType.StringType
                    defaultValue = "-1"
                },
                navArgument("title") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("categoryId") {
                    type = NavType.StringType
                    defaultValue = Screen.Player.EMPTY_CATEGORY_PLACEHOLDER
                },
                navArgument("categoryName") {
                    type = NavType.StringType
                    defaultValue = Screen.Player.EMPTY_CATEGORY_PLACEHOLDER
                }
            )
        ) { backStackEntry ->
            val streamUrlRaw = backStackEntry.arguments?.getString("streamUrl") ?: ""
            val streamUrl = Uri.decode(streamUrlRaw)
            val contentType = backStackEntry.arguments?.getString("contentType") ?: "live"
            val streamIdStr = backStackEntry.arguments?.getString("streamId") ?: "-1"
            val seriesIdStr = backStackEntry.arguments?.getString("seriesId") ?: "-1"
            val episodeIdStr = backStackEntry.arguments?.getString("episodeId") ?: "-1"
            val titleRaw = backStackEntry.arguments?.getString("title") ?: ""
            val title = if (titleRaw == Screen.Player.EMPTY_TITLE_PLACEHOLDER) "" else Uri.decode(titleRaw)
            val categoryIdRaw = backStackEntry.arguments?.getString("categoryId") ?: Screen.Player.EMPTY_CATEGORY_PLACEHOLDER
            val categoryNameRaw = backStackEntry.arguments?.getString("categoryName") ?: Screen.Player.EMPTY_CATEGORY_PLACEHOLDER
            val categoryId = if (categoryIdRaw != Screen.Player.EMPTY_CATEGORY_PLACEHOLDER) Uri.decode(categoryIdRaw) else null
            val categoryName = if (categoryNameRaw != Screen.Player.EMPTY_CATEGORY_PLACEHOLDER) Uri.decode(categoryNameRaw) else null
            
            val streamId = streamIdStr.toIntOrNull()?.takeIf { it >= 0 }
            val seriesId = seriesIdStr.toIntOrNull()?.takeIf { it >= 0 }
            val episodeId = episodeIdStr.toIntOrNull()?.takeIf { it >= 0 }
            
            val credentials = dependencies.credentialsRepository.getCredentials()
            PlayerScreen(
                streamInfo = StreamInfo(
                    streamUrl = streamUrl,
                    contentType = contentType,
                    streamId = streamId,
                    seriesId = seriesId,
                    episodeId = episodeId,
                    title = title.takeIf { it.isNotEmpty() },
                    categoryId = categoryId,
                    categoryName = categoryName
                ),
                watchProgressService = dependencies.watchProgressService,
                onBack = { navController.popBackStack() },
                onNextEpisode = { nextEpisodeInfo ->
                    navigateToNextEpisode(
                        navController = navController,
                        credentials = credentials,
                        seriesId = seriesId,
                        nextEpisodeInfo = nextEpisodeInfo
                    )
                }
            )
        }
        
        composable(
            route = Screen.MovieDetail.route,
            arguments = listOf(
                navArgument(Screen.MovieDetail.ARG_VOD_ID) { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val vodId = backStackEntry.arguments?.getInt(Screen.MovieDetail.ARG_VOD_ID) ?: -1
            MovieDetailRoute(
                vodId = vodId,
                dependencies = dependencies,
                navController = navController
            )
        }

        composable(
            route = Screen.SeriesDetail.route,
            arguments = listOf(
                navArgument(Screen.SeriesDetail.ARG_SERIES_ID) { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val seriesId =
                backStackEntry.arguments?.getInt(Screen.SeriesDetail.ARG_SERIES_ID) ?: -1
            SeriesDetailRoute(
                seriesId = seriesId,
                mainViewModel = mainViewModel,
                dependencies = dependencies,
                navController = navController
            )
        }

        composable(
            route = Screen.Season.route,
            arguments = listOf(
                navArgument(Screen.Season.ARG_SERIES_ID) { type = NavType.IntType },
                navArgument(Screen.Season.ARG_SEASON_NUMBER) { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val seriesId =
                backStackEntry.arguments?.getInt(Screen.Season.ARG_SERIES_ID) ?: -1
            val seasonNumber =
                backStackEntry.arguments?.getInt(Screen.Season.ARG_SEASON_NUMBER) ?: 1
            SeasonRoute(
                seriesId = seriesId,
                seasonNumber = seasonNumber,
                mainViewModel = mainViewModel,
                dependencies = dependencies,
                navController = navController
            )
        }
    }
}

/**
 * Determines which category ID to save as the sidebar restore target when navigating
 * from an episode to the Player.
 *
 * The saved [pendingNavigationState] (from entering this series via the Series tab) reflects
 * the sidebar category the user actually had selected, which may be a synthetic pseudo-category
 * ("Neu"/Watchlist) that never matches [seriesCategoryId] (the series' own backend category).
 * Falling back to [seriesCategoryId] only when no pending state exists (e.g. entering via search
 * or a deep link, which don't save one) avoids restoreCategory() silently switching the sidebar
 * selection away from a pseudo-category on return - see bugfix-series-empty-report.md.
 */
internal fun resolveEpisodeOriginCategoryId(
    pendingNavigationState: NavigationStateBeforePlayer?,
    seriesCategoryId: String?
): String? = pendingNavigationState?.categoryId ?: seriesCategoryId

@Composable
private fun MovieDetailRoute(
    vodId: Int,
    dependencies: AppNavigationDependencies,
    navController: NavHostController
) {
    val viewModel: MovieDetailViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    MovieDetailScreen(
        uiState = uiState,
        onBackClick = { navController.popBackStack() },
        onWatchClick = {
            val credentials = dependencies.credentialsRepository.getCredentials()
            if (credentials != null) {
                coroutineScope.launch {
                    val extension = runCatching {
                        dependencies.cacheService.loadCachedDataAsync()
                            ?.movies
                            ?.firstOrNull { it.streamId == vodId }
                            ?.containerExtension
                    }.getOrNull()
                    val route = moviePlayerRoute(
                        credentials = credentials,
                        vodId = vodId,
                        title = uiState.detail?.title,
                        containerExtension = extension
                    )
                    navController.logAndNavigate(route, "AppNavigation") {
                        "🎬 MovieDetail watch - vodId: $vodId, route: $route"
                    }
                }
            }
        },
        onToggleWatchlist = viewModel::toggleWatchlist,
        onMovieClick = movieRecommendationClickHandler { route -> navController.navigate(route) },
        onRetry = viewModel::retry
    )
}

@Composable
private fun SeriesDetailRoute(
    seriesId: Int,
    mainViewModel: MainViewModel,
    dependencies: AppNavigationDependencies,
    navController: NavHostController
) {
    val viewModel: SeriesDetailViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsState()

    SeriesDetailScreen(
        uiState = uiState,
        onBackClick = { navController.popBackStack() },
        onToggleWatchlist = viewModel::toggleWatchlist,
        onWatchClick = { episode ->
            val series = uiState.series
            val credentials = dependencies.credentialsRepository.getCredentials()
            if (series != null && credentials != null) {
                val seasonNumber = episode.season
                    ?: uiState.watchTarget?.seasonNumber
                    ?: 1
                val originCategoryId = resolveEpisodeOriginCategoryId(
                    pendingNavigationState = mainViewModel.peekPendingNavigationState(),
                    seriesCategoryId = series.categoryId
                )
                mainViewModel.saveNavigationStateBeforePlayer(
                    NavigationStateBeforePlayer(
                        destinationType = com.flintr.tv.data.model.DestinationType.SERIES,
                        categoryId = originCategoryId,
                        contentId = episode.toContentId(series.seriesId, seasonNumber),
                        scrollIndex = 0,
                        scrollOffset = 0
                    )
                )
                val route = seriesDetailWatchRoute(
                    credentials = credentials,
                    seriesId = series.seriesId,
                    episode = episode
                )
                navController.logAndNavigate(route, "AppNavigation") {
                    "📺 SeriesDetail watch - episode.title: ${episode.title}, seriesId: ${series.seriesId}, episodeId: ${episode.id}, route: $route"
                }
            }
        },
        onSeasonClick = { seasonNumber ->
            navController.navigate(seasonRoute(seriesId, seasonNumber))
        },
        onSeriesClick = seriesRecommendationClickHandler { route -> navController.navigate(route) },
        onRetry = viewModel::retry
    )
}

@Composable
private fun SeasonRoute(
    seriesId: Int,
    seasonNumber: Int,
    mainViewModel: MainViewModel,
    dependencies: AppNavigationDependencies,
    navController: NavHostController
) {
    val selectedSeries by mainViewModel.selectedSeries.collectAsState()

    // Same loading path as the former SeriesEpisodesRoute: the series object (including all
    // seasons/episodes) comes from MainViewModel, SeasonScreen only renders the chosen season.
    LaunchedEffect(seriesId) {
        if (seriesId >= 0 && selectedSeries?.seriesId != seriesId) {
            mainViewModel.loadSeriesInfo(seriesId)
        }
    }

    val series = selectedSeries
    if (series != null && series.seriesId == seriesId) {
        SeasonScreen(
            series = series,
            seasonNumber = seasonNumber,
            watchProgressService = dependencies.watchProgressService,
            onBack = { navController.popBackStack() },
            onEpisodeClick = { episode ->
                val credentials = dependencies.credentialsRepository.getCredentials()
                if (credentials != null) {
                    val episodeSeasonNumber = episode.season ?: seasonNumber
                    val originCategoryId = resolveEpisodeOriginCategoryId(
                        pendingNavigationState = mainViewModel.peekPendingNavigationState(),
                        seriesCategoryId = series.categoryId
                    )
                    mainViewModel.saveNavigationStateBeforePlayer(
                        NavigationStateBeforePlayer(
                            destinationType = com.flintr.tv.data.model.DestinationType.SERIES,
                            categoryId = originCategoryId,
                            contentId = episode.toContentId(series.seriesId, episodeSeasonNumber),
                            scrollIndex = 0,
                            scrollOffset = 0
                        )
                    )
                    val route = episodePlayerRoute(
                        credentials = credentials,
                        seriesId = series.seriesId,
                        episode = episode
                    )
                    navController.logAndNavigate(route, "AppNavigation") {
                        "📺 Season onClick - episode.title: ${episode.title}, seriesId: ${series.seriesId}, episodeId: ${episode.id}, route: $route"
                    }
                }
            },
            onLoadSeriesInfo = mainViewModel::loadSeriesInfo
        )
    } else {
        SeasonLoading()
    }
}

@Composable
private fun SeasonLoading() {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.safeRequestFocus("AppNavigation", "SEASON_LOADING")
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

private fun clearStreamsForDestination(
    destination: HomeDestination,
    viewModel: MainViewModel
) {
    when (destination) {
        is HomeDestination.LiveTV -> viewModel.clearLiveStreams()
        is HomeDestination.VOD -> viewModel.clearVodStreams()
        is HomeDestination.Series -> viewModel.clearSeries()
        is HomeDestination.EPG -> {
            // EPG doesn't have streams to clear
        }
        is HomeDestination.Search -> {
            // Search doesn't have streams to clear
        }
        is HomeDestination.Settings -> {
            // Settings navigates to separate screen, no streams to clear
        }
    }
}

/**
 * Data class grouping parameters for MainContentBody
 */
private data class MainContentBodyParams(
    val destination: HomeDestination,
    val selectedCategoryId: String?,
    val credentials: Credentials?,
    val mainViewModel: MainViewModel,
    val contentListFocusRequester: FocusRequester,
    val navController: NavHostController,
    val lazyGridState: LazyGridState,
    val focusRequesterMap: MutableMap<String, FocusRequester>,
    val onNavigateToCategorySidebar: () -> Unit,
    val onNavigateToNavbar: () -> Unit,
    val dependencies: AppNavigationDependencies,
    val inProgressItems: InProgressItemsState
)

/**
 * Creates a click handler for LiveStream navigation.
 */
private fun createLiveStreamClickHandler(
    params: MainContentBodyParams
): (LiveStream) -> Unit {
    return { stream ->
        params.credentials?.let { creds ->
            val scrollIndex = params.lazyGridState.firstVisibleItemIndex
            val scrollOffset = params.lazyGridState.firstVisibleItemScrollOffset
            params.mainViewModel.saveNavigationStateBeforePlayer(
                NavigationStateBeforePlayer(
                    destinationType = params.destination.toDestinationType(),
                    categoryId = params.selectedCategoryId,
                    contentId = stream.toContentId(),
                    scrollIndex = scrollIndex,
                    scrollOffset = scrollOffset
                )
            )
            val streamUrl = stream.getStreamUrl(
                creds.serverUrl,
                creds.username,
                creds.password
            )
            val route = Screen.Player.createRouteForLive(
                streamUrl, 
                stream.name,
                stream.categoryId,
                stream.categoryName,
                stream.streamId
            )
            params.navController.logAndNavigate(route, "AppNavigation") {
                "📺 LiveTV onClick - stream.name: ${stream.name}, streamUrl: $streamUrl, route: $route"
            }
        }
    }
}

/**
 * Creates a click handler for VOD navigation.
 *
 * Detail-first (Phase 4): a movie click opens [Screen.MovieDetail]; the explicit "watch"/"resume"
 * action on that screen is what starts [Screen.Player].
 */
private fun createVodClickHandler(
    params: MainContentBodyParams
): (VodStream) -> Unit {
    return { vod ->
        params.credentials?.let {
            val scrollIndex = params.lazyGridState.firstVisibleItemIndex
            val scrollOffset = params.lazyGridState.firstVisibleItemScrollOffset
            params.mainViewModel.saveNavigationStateBeforePlayer(
                NavigationStateBeforePlayer(
                    destinationType = params.destination.toDestinationType(),
                    categoryId = params.selectedCategoryId,
                    contentId = vod.toContentId(),
                    scrollIndex = scrollIndex,
                    scrollOffset = scrollOffset
                )
            )
            val route = vodDetailRoute(vod.streamId)
            params.navController.logAndNavigate(route, "AppNavigation") {
                "🎬 VOD onClick - vod.name: ${vod.name}, vod.streamId: ${vod.streamId}, route: $route"
            }
        }
    }
}

/**
 * Creates a click handler for Series navigation.
 *
 * Detail-first (Phase 4): a series click opens [Screen.SeriesDetail], which owns the season tiles.
 */
private fun createSeriesClickHandler(
    params: MainContentBodyParams
): (Series) -> Unit {
    return { series ->
        val scrollIndex = params.lazyGridState.firstVisibleItemIndex
        val scrollOffset = params.lazyGridState.firstVisibleItemScrollOffset
        params.mainViewModel.saveNavigationStateBeforePlayer(
            NavigationStateBeforePlayer(
                destinationType = params.destination.toDestinationType(),
                categoryId = params.selectedCategoryId,
                contentId = "series_${series.seriesId}",
                scrollIndex = scrollIndex,
                scrollOffset = scrollOffset
            )
        )
        val route = seriesDetailRoute(series.seriesId)
        params.navController.logAndNavigate(route, "AppNavigation") {
            "📺 Series click - series.name: ${series.name}, seriesId: ${series.seriesId}, route: $route"
        }
    }
}

/**
 * Creates click handlers for InProgressContent (VOD, Series, Episode).
 *
 * Movies/series are detail-first even when watch progress exists: the detail screen shows the
 * "Fortsetzen" affordance instead of jumping straight into the player. Episodes are already a
 * concrete playback choice (from the season card) and keep navigating to the player directly.
 */
private fun createInProgressClickHandlers(
    params: MainContentBodyParams
): Triple<(VodStream) -> Unit, (Series) -> Unit, (Series, Episode) -> Unit> {
    val onVodClick: (VodStream) -> Unit = { vod ->
        params.credentials?.let {
            val scrollIndex = params.lazyGridState.firstVisibleItemIndex
            val scrollOffset = params.lazyGridState.firstVisibleItemScrollOffset
            params.mainViewModel.saveNavigationStateBeforePlayer(
                NavigationStateBeforePlayer(
                    destinationType = params.destination.toDestinationType(),
                    categoryId = params.selectedCategoryId,
                    contentId = vod.toContentId(),
                    scrollIndex = scrollIndex,
                    scrollOffset = scrollOffset
                )
            )
            val route = vodDetailRoute(vod.streamId)
            params.navController.logAndNavigate(route, "AppNavigation") {
                "🎬 InProgress VOD onClick - vod.name: ${vod.name}, vod.streamId: ${vod.streamId}, route: $route"
            }
        }
    }
    
    val onSeriesClick: (Series) -> Unit = { series ->
        val scrollIndex = params.lazyGridState.firstVisibleItemIndex
        val scrollOffset = params.lazyGridState.firstVisibleItemScrollOffset
        params.mainViewModel.saveNavigationStateBeforePlayer(
            NavigationStateBeforePlayer(
                destinationType = params.destination.toDestinationType(),
                categoryId = params.selectedCategoryId,
                contentId = "series_${series.seriesId}",
                scrollIndex = scrollIndex,
                scrollOffset = scrollOffset
            )
        )
        val route = seriesDetailRoute(series.seriesId)
        params.navController.logAndNavigate(route, "AppNavigation") {
            "📺 InProgress Series click - series.name: ${series.name}, seriesId: ${series.seriesId}, route: $route"
        }
    }
    
    val onEpisodeClick: (Series, Episode) -> Unit = { series, episode ->
        params.credentials?.let { creds ->
            val seasonNum = episode.season ?: 1
            params.mainViewModel.saveNavigationStateBeforePlayer(
                NavigationStateBeforePlayer(
                    destinationType = params.destination.toDestinationType(),
                    categoryId = params.selectedCategoryId,
                    contentId = episode.toContentId(series.seriesId, seasonNum),
                    scrollIndex = params.lazyGridState.firstVisibleItemIndex,
                    scrollOffset = params.lazyGridState.firstVisibleItemScrollOffset
                )
            )
            val route = episodePlayerRoute(creds, series.seriesId, episode)
            params.navController.logAndNavigate(route, "AppNavigation") {
                "📺 InProgress Episode click - episode.title: ${episode.title}, seriesId: ${series.seriesId}, episodeId: ${episode.id}, route: $route"
            }
        }
    }
    
    return Triple(onVodClick, onSeriesClick, onEpisodeClick)
}

/**
 * Renders LiveTV content section.
 */
@Composable
private fun LiveTVContentSection(params: MainContentBodyParams) {
    LiveTVContent(
        params = LiveTVContentParams(
            viewModel = params.mainViewModel,
            selectedCategoryId = params.selectedCategoryId,
            focusRequester = params.contentListFocusRequester,
            watchlistRepository = params.dependencies.watchlistRepository,
            userRepository = params.dependencies.userRepository,
            credentialsRepository = params.dependencies.credentialsRepository,
            cacheService = params.dependencies.cacheService,
            categoryVisibilityService = params.dependencies.categoryVisibilityService,
            ioDispatcher = params.dependencies.ioDispatcher,
            onNavigateLeft = params.onNavigateToCategorySidebar,
            lazyGridState = params.lazyGridState,
            focusRequesterMap = params.focusRequesterMap,
            onChannelClick = createLiveStreamClickHandler(params)
        )
    )
}

/**
 * Renders InProgressContent with handlers.
 */
@Composable
private fun InProgressContentSection(params: MainContentBodyParams) {
    val (onVodClick, onSeriesClick, onEpisodeClick) = createInProgressClickHandlers(params)
    InProgressContent(
        params = InProgressContentParams(
            itemsState = params.inProgressItems,
            watchProgressService = params.dependencies.watchProgressService,
            watchlistRepository = params.dependencies.watchlistRepository,
            focusRequester = params.contentListFocusRequester,
            onNavigateLeft = params.onNavigateToCategorySidebar,
            onVodClick = onVodClick,
            onSeriesClick = onSeriesClick,
            onEpisodeClick = onEpisodeClick,
            lazyGridState = params.lazyGridState,
            focusRequesterMap = params.focusRequesterMap
        )
    )
}

/**
 * Renders the "Für dich" recommendations grid for VOD movies.
 */
@Composable
private fun RecommendedVodContentSection(params: MainContentBodyParams) {
    val recommendations by params.dependencies.recommendationService.recommendations.collectAsState()
    val onVodClick = createVodClickHandler(params)

    ContentGridLayout(
        params = ContentGridLayoutParams(
            items = recommendations.movies,
            isLoading = false,
            focusRequester = params.contentListFocusRequester,
            lazyGridState = params.lazyGridState,
            keySelector = { vod -> vod.streamId },
            contentIdSelector = { vod -> "vod_${vod.streamId}" },
            focusRequesterMap = params.focusRequesterMap
        ),
        content = { vod, _, columnIndex, onFocusChanged, cardFocusRequester ->
            VODCard(
                params = VODCardParams(
                    vod = vod,
                    onClick = { onVodClick(vod) },
                    watchlistRepository = params.dependencies.watchlistRepository,
                    watchProgressService = params.dependencies.watchProgressService,
                    onFocusChanged = onFocusChanged,
                    onNavigateLeft = if (columnIndex == 0) {
                        { params.onNavigateToCategorySidebar() }
                    } else {
                        null
                    },
                    focusRequester = cardFocusRequester
                )
            )
        }
    )
}

/**
 * Renders the "Für dich" recommendations grid for series.
 */
@Composable
private fun RecommendedSeriesContentSection(params: MainContentBodyParams) {
    val recommendations by params.dependencies.recommendationService.recommendations.collectAsState()
    val onSeriesClick = createSeriesClickHandler(params)

    ContentGridLayout(
        params = ContentGridLayoutParams(
            items = recommendations.series,
            isLoading = false,
            focusRequester = params.contentListFocusRequester,
            lazyGridState = params.lazyGridState,
            keySelector = { series -> series.seriesId },
            contentIdSelector = { series -> "series_${series.seriesId}" },
            focusRequesterMap = params.focusRequesterMap
        ),
        content = { series, _, columnIndex, onFocusChanged, cardFocusRequester ->
            SeriesItem(
                series = series,
                watchlistRepository = params.dependencies.watchlistRepository,
                onSeriesClick = { onSeriesClick(series) },
                cardConfig = com.flintr.tv.ui.components.CardConfig(
                    isSelected = false,
                    onFocusChanged = onFocusChanged,
                    onNavigateLeft = if (columnIndex == 0) {
                        { params.onNavigateToCategorySidebar() }
                    } else {
                        null
                    },
                    focusRequester = cardFocusRequester
                )
            )
        }
    )
}

/**
 * Renders VOD content section (with InProgress/Recommended support).
 */
@Composable
private fun VODContentSection(params: MainContentBodyParams) {
    if (params.selectedCategoryId == IN_PROGRESS_CATEGORY_ID) {
        InProgressContentSection(params)
    } else if (params.selectedCategoryId == RECOMMENDED_CATEGORY_ID) {
        RecommendedVodContentSection(params)
    } else {
        VODContent(
            params = VODContentParams(
                viewModel = params.mainViewModel,
                selectedCategoryId = params.selectedCategoryId,
                focusRequester = params.contentListFocusRequester,
                watchlistRepository = params.dependencies.watchlistRepository,
                userRepository = params.dependencies.userRepository,
                credentialsRepository = params.dependencies.credentialsRepository,
                cacheService = params.dependencies.cacheService,
                categoryVisibilityService = params.dependencies.categoryVisibilityService,
                watchProgressService = params.dependencies.watchProgressService,
                ioDispatcher = params.dependencies.ioDispatcher,
                onNavigateLeft = params.onNavigateToCategorySidebar,
                lazyGridState = params.lazyGridState,
                focusRequesterMap = params.focusRequesterMap,
                onVodClick = createVodClickHandler(params)
            )
        )
    }
}

/**
 * Renders Series content section (with InProgress/Recommended support).
 */
@Composable
private fun SeriesContentSection(params: MainContentBodyParams) {
    if (params.selectedCategoryId == IN_PROGRESS_CATEGORY_ID) {
        InProgressContentSection(params)
    } else if (params.selectedCategoryId == RECOMMENDED_CATEGORY_ID) {
        RecommendedSeriesContentSection(params)
    } else {
        SeriesContent(
            params = SeriesContentParams(
                viewModel = params.mainViewModel,
                selectedCategoryId = params.selectedCategoryId,
                focusRequester = params.contentListFocusRequester,
                watchlistRepository = params.dependencies.watchlistRepository,
                userRepository = params.dependencies.userRepository,
                credentialsRepository = params.dependencies.credentialsRepository,
                cacheService = params.dependencies.cacheService,
                categoryVisibilityService = params.dependencies.categoryVisibilityService,
                ioDispatcher = params.dependencies.ioDispatcher,
                onNavigateLeft = params.onNavigateToCategorySidebar,
                lazyGridState = params.lazyGridState,
                focusRequesterMap = params.focusRequesterMap,
                onSeriesClick = createSeriesClickHandler(params)
            )
        )
    }
}

/**
 * Renders EPG content section.
 */
@Composable
private fun EPGContentSection(params: MainContentBodyParams) {
    EPGContent(viewModel = params.mainViewModel)
}

/**
 * Composable rendering the main content body based on selected destination
 */
@Composable
private fun MainContentBody(
    params: MainContentBodyParams
) {
    when (params.destination) {
        is HomeDestination.LiveTV -> {
            LiveTVContentSection(params)
        }
        is HomeDestination.VOD -> {
            VODContentSection(params)
        }
        is HomeDestination.Series -> {
            SeriesContentSection(params)
        }
        is HomeDestination.EPG -> {
            EPGContentSection(params)
        }
        is HomeDestination.Search -> {
            // Search screen will be implemented in Story 12.2
            Box {}
        }
        is HomeDestination.Settings -> {
            // Settings navigates to separate screen, this should not be reached
            // but kept for exhaustiveness
            Box {}
        }
    }
}

/**
 * Composable for update dialog display logic
 */
@Composable
private fun UpdateDialogContent(
    showUpdateDialog: Boolean,
    updateInfo: UpdateInfo?,
    currentVersion: String?,
    downloadProgress: DownloadProgress?,
    updateViewModel: UpdateViewModel,
    onDismiss: () -> Unit
) {
    if (showUpdateDialog && updateInfo != null) {
        UpdateDialog(
            updateInfo = updateInfo,
            currentVersion = currentVersion,
            downloadProgress = downloadProgress,
            isDownloading = downloadProgress != null && !downloadProgress.isComplete && downloadProgress.error == null,
            onUpdateNow = {
                updateInfo.downloadUrl?.let { url ->
                    updateInfo.stableVersion?.let { version ->
                        updateViewModel.downloadUpdate(url, version)
                    }
                }
            },
            onLater = onDismiss
        )
    }
}

/**
 * Data class grouping category management results
 */
private data class CategoryManagementResult(
    val categoryType: CategoryType?,
    val sortedCategories: List<Category>,
    val showCategories: Boolean
)

/**
 * Determines if the destination supports categories
 */
private fun HomeDestination.supportsCategories(): Boolean =
    this is HomeDestination.LiveTV || this is HomeDestination.VOD || this is HomeDestination.Series

/**
 * Determines if the destination supports in-progress category
 */
private fun HomeDestination.supportsInProgress(): Boolean =
    this is HomeDestination.VOD || this is HomeDestination.Series

/**
 * Gets the category type for a destination
 */
private fun HomeDestination.toCategoryType(): CategoryType? = when (this) {
    is HomeDestination.LiveTV -> CategoryType.LIVE_TV
    is HomeDestination.VOD -> CategoryType.VOD
    is HomeDestination.Series -> CategoryType.SERIES
    else -> null
}

/**
 * Gets the raw categories for a destination
 */
private fun getRawCategoriesForDestination(
    destination: HomeDestination,
    liveCategories: List<Category>,
    vodCategories: List<Category>,
    seriesCategories: List<Category>
): List<Category> = when (destination) {
    is HomeDestination.LiveTV -> liveCategories
    is HomeDestination.VOD -> vodCategories
    is HomeDestination.Series -> seriesCategories
    else -> emptyList()
}

/**
 * Filters out hidden categories
 */
private fun filterHiddenCategories(
    categories: List<Category>,
    categoryType: CategoryType?,
    hiddenCategories: Map<CategoryType, Set<String>>
): List<Category> {
    if (categoryType == null) return categories
    val hiddenSet = hiddenCategories[categoryType] ?: return categories
    return categories.filter { it.categoryId !in hiddenSet }
}

/**
 * Creates special categories. Fixed order: watchlist, in-progress, recommended, new.
 */
internal fun createSpecialCategories(
    destination: HomeDestination,
    sortedCategories: List<Category>,
    watchlistCategoryName: String,
    newCategoryName: String,
    inProgressCategoryName: String,
    recommendedCategoryName: String
): List<Category> {
    if (!destination.supportsCategories()) return sortedCategories

    val watchlistCategory = Category(WATCHLIST_CATEGORY_ID, watchlistCategoryName, 0)
    val newCategory = Category(NEW_CATEGORY_ID, newCategoryName, 0)

    return if (destination.supportsInProgress()) {
        val recommendedCategory = Category(RECOMMENDED_CATEGORY_ID, recommendedCategoryName, 0)
        val inProgressCategory = Category(IN_PROGRESS_CATEGORY_ID, inProgressCategoryName, 0)
        listOf(watchlistCategory, inProgressCategory, recommendedCategory, newCategory) + sortedCategories
    } else {
        listOf(watchlistCategory, newCategory) + sortedCategories
    }
}

/**
 * Composable function managing category logic (filtering, sorting, type determination)
 */
@Composable
private fun useCategoryManagement(
    selectedDestination: HomeDestination,
    liveCategories: List<Category>,
    vodCategories: List<Category>,
    seriesCategories: List<Category>,
    hiddenCategories: Map<CategoryType, Set<String>>
): CategoryManagementResult {
    val categoryType = selectedDestination.toCategoryType()
    val rawCategories = getRawCategoriesForDestination(selectedDestination, liveCategories, vodCategories, seriesCategories)
    val filteredCategories = filterHiddenCategories(rawCategories, categoryType, hiddenCategories)
    val sortedCategories = filteredCategories.sortedBy { it.categoryName }

    val watchlistCategoryName = stringResource(R.string.watchlist)
    val newCategoryName = stringResource(R.string.new_category)
    val inProgressCategoryName = stringResource(R.string.in_progress_category)
    val recommendedCategoryName = stringResource(R.string.recommended_for_you)

    val categoriesWithSpecial = remember(sortedCategories, selectedDestination, watchlistCategoryName, newCategoryName, inProgressCategoryName, recommendedCategoryName) {
        createSpecialCategories(selectedDestination, sortedCategories, watchlistCategoryName, newCategoryName, inProgressCategoryName, recommendedCategoryName)
    }

    return CategoryManagementResult(
        categoryType = categoryType,
        sortedCategories = categoriesWithSpecial,
        showCategories = selectedDestination.supportsCategories()
    )
}

/**
 * LaunchedEffect for checking updates once
 */
@Composable
private fun UpdateCheckEffect(
    hasCheckedForUpdates: Boolean,
    updateViewModel: UpdateViewModel,
    context: android.content.Context,
    onUpdateChecked: () -> Unit
) {
    LaunchedEffect(Unit) {
        if (!hasCheckedForUpdates) {
            onUpdateChecked()
            val packageInfo = loadPackageInfo(context)
            val version = packageInfo?.versionName
            if (version != null) {
                updateViewModel.checkForUpdates(version)
            }
        }
    }
}

/**
 * LaunchedEffect for showing update dialog when update becomes available
 */
@Composable
private fun UpdateDialogEffect(
    updateInfo: UpdateInfo?,
    onShowDialog: () -> Unit
) {
    LaunchedEffect(updateInfo?.hasUpdate) {
        if (updateInfo?.hasUpdate == true && updateInfo.downloadUrl != null) {
            onShowDialog()
        }
    }
}

/**
 * LaunchedEffect for auto-closing dialog when installation starts
 */
@Composable
private fun AutoCloseDialogEffect(
    downloadProgress: DownloadProgress?,
    onCloseDialog: () -> Unit
) {
    LaunchedEffect(downloadProgress?.isComplete) {
        if (downloadProgress?.isComplete == true) {
            onCloseDialog()
        }
    }
}

/**
 * Determines the target category ID based on the current sorted categories and selection.
 * Returns null if the list is empty, the current ID if it exists in the list,
 * or the first category's ID otherwise.
 */
private fun resolveTargetCategoryId(
    sortedCategories: List<Category>,
    selectedCategoryId: String?
): String? {
    if (sortedCategories.isEmpty()) return null
    return if (sortedCategories.any { it.categoryId == selectedCategoryId }) {
        selectedCategoryId
    } else {
        sortedCategories.first().categoryId
    }
}

/**
 * Clears the content-card focus requester map whenever the selected category changes,
 * so FocusRequester entries for cards no longer on screen don't accumulate unbounded
 * as the user browses categories. Keyed on selectedCategoryId so it only runs on a real
 * category switch, not on every recomposition; new entries are then created fresh via
 * getOrPut as ContentGridLayout renders the new category's cards.
 */
@Composable
private fun FocusRequesterMapClearEffect(
    selectedCategoryId: String?,
    focusRequesterMap: MutableMap<String, FocusRequester>
) {
    LaunchedEffect(selectedCategoryId) {
        focusRequesterMap.clear()
    }
}

/**
 * LaunchedEffect for category selection when destination or categories change
 */
@Composable
private fun CategorySelectionEffect(
    selectedDestination: HomeDestination,
    sortedCategories: List<Category>,
    selectedCategoryId: String?,
    pendingNavigationRestore: NavigationStateBeforePlayer?,
    onCategoryIdChanged: (String?) -> Unit
) {
    LaunchedEffect(selectedDestination, sortedCategories) {
        // Don't override category selection if navigation restoration is in progress
        if (pendingNavigationRestore != null) {
            return@LaunchedEffect
        }

        val newCategoryId = resolveTargetCategoryId(sortedCategories, selectedCategoryId)
        if (newCategoryId != selectedCategoryId) {
            onCategoryIdChanged(newCategoryId)
        }
    }
}

/**
 * LaunchedEffect for showing snackbar messages
 */
@Composable
private fun SnackbarEffect(
    snackbarMessage: String?,
    snackbarHostState: SnackbarHostState,
    onMessageShown: () -> Unit
) {
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            onMessageShown()
        }
    }
}

/**
 * LaunchedEffect for handling destination changes and resetting focus flags
 */
@Composable
private fun DestinationChangeEffect(
    selectedDestination: HomeDestination,
    onDestinationChanged: (HomeDestination) -> Unit,
    onResetFocusFlags: () -> Unit
) {
    LaunchedEffect(selectedDestination) {
        onDestinationChanged(selectedDestination)
        onResetFocusFlags()
    }
}

/**
 * LaunchedEffect for setting initial focus on Navbar when ready
 */
@Composable
private fun NavbarFocusEffect(
    navbarReady: Boolean,
    initialFocusSet: Boolean,
    navbarFocusRequester: FocusRequester,
    onFocusSet: () -> Unit
) {
    LaunchedEffect(navbarReady) {
        if (navbarReady && !initialFocusSet) {
            onFocusSet()
            navbarFocusRequester.safeRequestFocus(LOG_TAG_MAIN_CONTENT_SCREEN, "NAVBAR")
        }
    }
}

/**
 * LaunchedEffect for setting focus on CategorySidebar when ready
 */
@Composable
private fun CategorySidebarFocusEffect(
    categorySidebarReady: Boolean,
    categorySidebarFocusSet: Boolean,
    pendingFocusDestination: HomeDestination?,
    showCategories: Boolean,
    sortedCategories: List<Category>,
    categorySidebarFocusRequester: FocusRequester,
    onFocusSet: () -> Unit
) {
    LaunchedEffect(categorySidebarReady, pendingFocusDestination, showCategories, sortedCategories.size) {
        if (categorySidebarReady && 
            !categorySidebarFocusSet &&
            pendingFocusDestination != null && 
            showCategories && sortedCategories.isNotEmpty()) {
            onFocusSet()
            categorySidebarFocusRequester.safeRequestFocus(LOG_TAG_MAIN_CONTENT_SCREEN, "CATEGORY_SIDEBAR")
        }
    }
}

/**
 * LaunchedEffect for handling navigation state restoration
 */
@Composable
private fun NavigationRestoreEffect(
    pendingNavigationRestore: NavigationStateBeforePlayer?,
    restoreContext: NavigationRestoreContext?,
    mainViewModel: MainViewModel
) {
    // Check for navigation state when returning from Player or the detail screens
    // This LaunchedEffect runs when the screen is first composed (e.g., after process death)
    LaunchedEffect(Unit) {
        mainViewModel.consumeNavigationState()
    }

    // Navigation state restoration effect
    // Combines both restoration logic and state consumption check
    LaunchedEffect(pendingNavigationRestore) {
        if (pendingNavigationRestore != null && restoreContext != null) {
            // Restore navigation state using extracted function
            restoreNavigationState(context = restoreContext)
        } else {
            // If pendingNavigationRestore is null but there might be a state in SavedStateHandle,
            // try to consume it (this handles the case when returning from a detail screen)
            mainViewModel.consumeNavigationState()
        }
    }
}

/**
 * Parameters for [MainContentScaffold]
 */
private data class MainContentScaffoldParams(
    val showLoading: Boolean,
    val uiState: com.flintr.tv.viewmodel.MainUiState,
    val selectedDestination: HomeDestination,
    val onDestinationChangeHandler: (HomeDestination) -> Unit,
    val categoryManagement: CategoryManagementResult,
    val selectedCategoryId: String?,
    val onCategorySelected: (String?) -> Unit,
    val itemCounts: Map<String, Int>,
    val inProgressItems: InProgressItemsState,
    val navbarFocusRequester: FocusRequester,
    val categorySidebarFocusRequester: FocusRequester,
    val categoryFocusRequesterMap: MutableMap<String, FocusRequester>,
    val focusManager: FocusManager,
    val onCategoryHidden: (Category) -> Unit,
    val onSearchClick: () -> Unit,
    val focusState: MainContentFocusState,
    val categorySidebarListState: androidx.compose.foundation.lazy.LazyListState,
    val snackbarHostState: SnackbarHostState,
    val showSearchOverlay: Boolean,
    val onSearchOverlayDismiss: () -> Unit,
    val searchState: com.flintr.tv.data.model.SearchState,
    val mainViewModel: MainViewModel,
    val searchClickHandlers: SearchClickHandlers,
    val credentials: Credentials?,
    val navController: NavHostController,
    val contentListFocusRequester: FocusRequester,
    val lazyGridState: LazyGridState,
    val focusRequesterMap: MutableMap<String, FocusRequester>,
    val dependencies: AppNavigationDependencies
)

/**
 * Composable rendering the main content scaffold (Loading, Error, or Main UI)
 */
@Composable
private fun MainContentScaffold(params: MainContentScaffoldParams) = with(params) {
    // Full-screen error only when there is nothing else to show (no categories loaded at
    // all yet). Once content is mounted, a later error must not unmount MainLayout/
    // MainContentBody - that would retrigger the tab screens' LaunchedEffects, the same
    // failure mode as yesterday's loop fix (see F3). Those errors are surfaced via snackbar
    // instead (see MainContentScreen).
    if (uiState.error != null && !uiState.isLoading && !showLoading && categoryManagement.sortedCategories.isEmpty()) {
        val colors = appColors()
        var retryFocused by remember { mutableStateOf(false) }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Dimensions.spacingXL),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = uiState.error,
                color = colors.dangerText,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(Dimensions.spacingLG))
            Row(
                modifier = Modifier
                    .onFocusChanged { retryFocused = it.isFocused }
                    .focusable()
                    .tvFocusRing(isFocused = retryFocused, shape = RoundedCornerShape(Dimensions.radiusMD))
                    .background(colors.buttonPrimary, RoundedCornerShape(Dimensions.radiusMD))
                    .onClick(onClick = { mainViewModel.refreshData() })
                    .padding(horizontal = Dimensions.spacingLG, vertical = Dimensions.spacingSM),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingXS)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = Color.White
                )
                Text(
                    text = "Erneut versuchen",
                    color = Color.White
                )
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            MainLayout(
                params = MainLayoutParams(
                    selectedDestination = selectedDestination,
                    onDestinationChange = onDestinationChangeHandler,
                    showCategories = categoryManagement.showCategories,
                    categories = categoryManagement.sortedCategories,
                    selectedCategoryId = selectedCategoryId,
                    onCategorySelected = onCategorySelected,
                    itemCounts = itemCounts,
                    itemLabelBuilder = { count -> itemLabelForDestination(selectedDestination, count) },
                    navbarFocusRequester = navbarFocusRequester,
                    categorySidebarFocusRequester = categorySidebarFocusRequester,
                    categoryFocusRequesterMap = categoryFocusRequesterMap,
                    onNavigateToNavbar = { focusManager.moveFocusToNavbar() },
                    onNavigateToCategorySidebar = { focusManager.moveFocusToCategorySidebar() },
                    onNavigateToContentList = { focusManager.moveFocusToContentList() },
                    onCategoryHidden = onCategoryHidden,
                    onSearchClick = onSearchClick,
                    onNavbarReady = { focusState.navbarReady = true },
                    onCategorySidebarReady = { focusState.categorySidebarReady = true },
                    onContentListReady = { focusState.contentListReady = true },
                    categorySidebarListState = categorySidebarListState
                ),
                content = {
                    MainContentBody(
                        params = MainContentBodyParams(
                            destination = selectedDestination,
                            selectedCategoryId = selectedCategoryId,
                            credentials = credentials,
                            mainViewModel = mainViewModel,
                            contentListFocusRequester = contentListFocusRequester,
                            navController = navController,
                            lazyGridState = lazyGridState,
                            focusRequesterMap = focusRequesterMap,
                            onNavigateToCategorySidebar = { focusManager.moveFocusToCategorySidebar() },
                            onNavigateToNavbar = { focusManager.moveFocusToNavbar() },
                            dependencies = dependencies,
                            inProgressItems = inProgressItems
                        )
                    )
                }
            )

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
            )

            if (showSearchOverlay) {
                SearchOverlay(
                    searchState = searchState,
                    onQueryChange = { mainViewModel.searchContent(it) },
                    onSearchImmediate = { mainViewModel.searchContentImmediate() },
                    onSearchScopeToggle = { mainViewModel.toggleSearchScope(it) },
                    onDismiss = {
                        mainViewModel.clearSearch()
                        onSearchOverlayDismiss()
                        focusManager.moveFocusToNavbar()
                    },
                    resultCallbacks = SearchResultCallbacks(
                        onLiveStreamClick = searchClickHandlers.onLiveStreamClick,
                        onVodClick = searchClickHandlers.onVodClick,
                        onSeriesClick = searchClickHandlers.onSeriesClick
                    ),
                    watchlistRepository = dependencies.watchlistRepository
                )
            }

            // No app-wide overlay spinner here: VOD/LiveTV/Series screens already render
            // their own ContentGridLayout spinner from uiState.isLoading. An overlay on top
            // of that produced two simultaneous spinners during a category load (B2).
            // showLoading is still used above to gate the full-screen error branch.
        }
    }
}

/**
 * State holder class encapsulating focus-related state
 */
private class MainContentFocusState {
    var initialFocusSet by mutableStateOf(false)
    var pendingFocusDestination by mutableStateOf<HomeDestination?>(null)
    var categorySidebarFocusSet by mutableStateOf(false)
    var contentListFocusSet by mutableStateOf(false)
    var navbarReady by mutableStateOf(false)
    var categorySidebarReady by mutableStateOf(false)
    var contentListReady by mutableStateOf(false)

    fun resetFocusFlags() {
        categorySidebarFocusSet = false
        contentListFocusSet = false
        categorySidebarReady = false
        contentListReady = false
    }
}

@Composable
private fun rememberMainContentFocusState(): MainContentFocusState {
    return remember { MainContentFocusState() }
}

/**
 * Focus management class encapsulating focus navigation logic
 */
private class FocusManager(
    private val navbarFocusRequester: FocusRequester,
    private val categorySidebarFocusRequester: FocusRequester,
    private val contentListFocusRequester: FocusRequester,
    private val coroutineScope: CoroutineScope,
    private val logTag: String,
    private val categorySidebarListState: androidx.compose.foundation.lazy.LazyListState? = null,
    private val sortedCategories: List<com.flintr.tv.data.model.Category> = emptyList(),
    private val selectedCategoryId: String? = null
) {
    fun moveFocusToNavbar() {
        Log.i("TVFocus", "$logTag 🎯 Requesting focus: NAVBAR")
        coroutineScope.launch {
            navbarFocusRequester.safeRequestFocus(
                logTag = "TVFocus",
                targetName = "Navbar"
            )
        }
    }

    fun moveFocusToCategorySidebar() {
        Log.i("TVFocus", "$logTag 🎯 Requesting focus: CATEGORY_SIDEBAR")
        coroutineScope.launch {
            categorySidebarFocusRequester.safeRequestFocus(
                logTag = "TVFocus",
                targetName = "CategorySidebar"
            )
            
            // Scroll to selected category if one is selected
            if (selectedCategoryId != null && categorySidebarListState != null && sortedCategories.isNotEmpty()) {
                val categoryIndex = sortedCategories.indexOfFirst { it.categoryId == selectedCategoryId }
                if (categoryIndex >= 0) {
                    try {
                        categorySidebarListState.animateScrollToItem(categoryIndex)
                        Log.d("TVFocus", "$logTag 📜 Scrolled to category at index $categoryIndex")
                    } catch (e: Exception) {
                        reportWarning("TVFocus", "$logTag ⚠️ Failed to scroll to category", e)
                    }
                }
            }
        }
    }

    fun moveFocusToContentList() {
        Log.i("TVFocus", "$logTag 🎯 Requesting focus: CONTENT_LIST")
        coroutineScope.launch {
            contentListFocusRequester.safeRequestFocus(
                logTag = "TVFocus",
                targetName = "ContentList"
            )
        }
    }
}

/**
 * Data class grouping parameters for category hiding logic
 */
private data class CategoryHiddenContext(
    val categoryType: CategoryType?,
    val selectedCategoryId: String?,
    val sortedCategories: List<Category>,
    val categoryVisibilityService: CategoryVisibilityService,
    val onCategoryIdChanged: (String?) -> Unit,
    val onSnackbarMessage: (String) -> Unit,
    val categoryHiddenMessage: String
)

/**
 * Handler function for category hiding logic
 */
private suspend fun handleCategoryHidden(
    category: Category,
    context: CategoryHiddenContext
) {
    // Hide the category (only if categoryType is not null)
    context.categoryType?.let { type ->
        context.categoryVisibilityService.hideCategory(category.categoryId, type)
        
        // If the hidden category was currently selected, select the first available category
        if (context.selectedCategoryId == category.categoryId) {
            val remainingCategories = context.sortedCategories.filter { it.categoryId != category.categoryId }
            context.onCategoryIdChanged(remainingCategories.firstOrNull()?.categoryId)
        }
        
        // Show snackbar feedback
        context.onSnackbarMessage(context.categoryHiddenMessage)
    }
}

/**
 * Creates search click handlers for SearchOverlay
 */
private fun createSearchClickHandlers(
    credentials: Credentials?,
    navController: NavHostController,
    mainViewModel: MainViewModel,
    onDismiss: () -> Unit
): SearchClickHandlers {
    return SearchClickHandlers(
        onLiveStreamClick = { stream ->
            val creds = credentials ?: return@SearchClickHandlers
            val streamUrl = stream.getStreamUrl(
                creds.serverUrl,
                creds.username,
                creds.password
            )
            val route = Screen.Player.createRouteForLive(
                streamUrl, 
                stream.name,
                stream.categoryId,
                stream.categoryName,
                stream.streamId
            )
            mainViewModel.clearSearch()
            onDismiss()
            navController.logAndNavigate(route, "AppNavigation") {
                "🔍 Search LiveTV onClick - stream.name: ${stream.name}, streamUrl: $streamUrl, route: $route"
            }
        },
        onVodClick = { vod ->
            if (credentials == null) return@SearchClickHandlers
            val route = vodDetailRoute(vod.streamId)
            mainViewModel.clearSearch()
            onDismiss()
            navController.logAndNavigate(route, "AppNavigation") {
                "🔍 Search VOD onClick - vod.name: ${vod.name}, vod.streamId: ${vod.streamId}, route: $route"
            }
        },
        onSeriesClick = { series ->
            val route = seriesDetailRoute(series.seriesId)
            mainViewModel.clearSearch()
            onDismiss()
            navController.logAndNavigate(route, "AppNavigation") {
                "🔍 Search Series onClick - series.name: ${series.name}, seriesId: ${series.seriesId}, route: $route"
            }
        }
    )
}

/**
 * Handles destination changes and related side effects.
 */
private fun handleDestinationChangeInternal(
    destination: HomeDestination,
    navController: NavHostController,
    mainViewModel: MainViewModel,
    coroutineScope: CoroutineScope,
    categoryVisibilityService: CategoryVisibilityService,
    onCategoryIdChanged: (String?) -> Unit,
    onDestinationChanged: (HomeDestination) -> Unit
) {
    if (destination is HomeDestination.Settings) {
        navController.navigate(Screen.Settings.route)
    } else {
        onCategoryIdChanged(null)
        onDestinationChanged(destination).let {
            clearStreamsForDestination(destination, mainViewModel)
        }
        // Smart sync: Only sync if enough time has passed since last sync
        // Only sync for content tabs (LiveTV, VOD, Series, EPG), not for Search/Settings
        if (destination in listOf(
                HomeDestination.LiveTV,
                HomeDestination.VOD,
                HomeDestination.Series,
                HomeDestination.EPG
            )
        ) {
            coroutineScope.launch {
                categoryVisibilityService.syncIfNeeded()
            }
        }
    }
}

/**
 * Sidebar-Zaehler = Anzahl der Karten, die die "Angefangene Inhalte"-Liste rendert: Aufrufer
 * uebergibt [InProgressItemsState.items] - dieselbe Liste, die das Grid anzeigt (Task 10b). Keine
 * eigene Aufloesung - Zaehler und Liste koennen dadurch nie auseinanderlaufen.
 */
internal fun calculateItemCounts(inProgressItems: List<InProgressItem>): Map<String, Int> {
    val inProgressCount = inProgressItems.size
    return if (inProgressCount > 0) {
        mapOf(IN_PROGRESS_CATEGORY_ID to inProgressCount)
    } else {
        emptyMap()
    }
}

/**
 * Label fuer die Item-Zaehlung pro Destination (z.B. "1 Film" / "3 Filme", "1 Serie" /
 * "2 Serien" bei Angefangene Inhalte). Niemals "Kanaele" - das bezeichnet nur echte
 * Live-TV-Sender.
 */
internal fun itemLabelForDestination(destination: HomeDestination, count: Int): String = when (destination) {
    is HomeDestination.VOD -> if (count == 1) "Film" else "Filme"
    is HomeDestination.Series -> if (count == 1) "Serie" else "Serien"
    else -> "Titel"
}

@Composable
fun MainContentScreen(
    navController: NavHostController,
    mainViewModel: MainViewModel,
    dependencies: AppNavigationDependencies,
    updateViewModel: UpdateViewModel = hiltViewModel()
) {
    // State initialization
    val credentials = dependencies.credentialsRepository.getCredentials()
    val context = LocalContext.current
    var selectedDestinationKey by rememberSaveable { mutableStateOf(DESTINATION_KEY_LIVE_TV) }
    val selectedDestination = selectedDestinationKey.toHomeDestination()
    var selectedCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    var hasCheckedForUpdates by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showSearchOverlay by remember { mutableStateOf(false) }
    val focusState = rememberMainContentFocusState()
    val coroutineScope = rememberCoroutineScope()
    
    // ViewModel state collection
    val searchState by mainViewModel.searchState.collectAsState()
    val updateInfo by updateViewModel.updateInfo.collectAsState()
    val downloadProgress by updateViewModel.downloadProgress.collectAsState()
    val currentVersionFromViewModel by updateViewModel.currentVersion.collectAsState()
    val liveCategories by mainViewModel.liveCategories.collectAsState()
    val vodCategories by mainViewModel.vodCategories.collectAsState()
    val seriesCategories by mainViewModel.seriesCategories.collectAsState()
    val hiddenCategories by dependencies.categoryVisibilityService.hiddenCategories.collectAsState()
    val pendingNavigationRestore by mainViewModel.pendingNavigationRestore.collectAsState()
    val uiState by mainViewModel.uiState.collectAsState()
    
    // Focus requesters
    val navbarFocusRequester = remember { FocusRequester() }
    val categorySidebarFocusRequester = remember { FocusRequester() }
    val contentListFocusRequester = remember { FocusRequester() }
    
    // LazyGridState for content grid (shared across all tabs)
    val lazyGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    
    // FocusRequester map for content cards (contentId -> FocusRequester)
    val focusRequesterMap = remember { mutableMapOf<String, FocusRequester>() }
    FocusRequesterMapClearEffect(
        selectedCategoryId = selectedCategoryId,
        focusRequesterMap = focusRequesterMap
    )

    // FocusRequester map for category chips (categoryId -> FocusRequester)
    val categoryFocusRequesterMap = remember { mutableMapOf<String, FocusRequester>() }

    // LazyListState for category sidebar scrolling
    val categorySidebarListState = androidx.compose.foundation.lazy.rememberLazyListState()

    // Category management
    val categoryManagement = useCategoryManagement(
        selectedDestination = selectedDestination,
        liveCategories = liveCategories,
        vodCategories = vodCategories,
        seriesCategories = seriesCategories,
        hiddenCategories = hiddenCategories
    )
    
    // Focus manager
    val focusManager = remember(coroutineScope, categorySidebarListState, categoryManagement.sortedCategories, selectedCategoryId) {
        FocusManager(
            navbarFocusRequester, 
            categorySidebarFocusRequester, 
            contentListFocusRequester, 
            coroutineScope, 
            LOG_TAG_MAIN_CONTENT_SCREEN,
            categorySidebarListState,
            categoryManagement.sortedCategories,
            selectedCategoryId
        )
    }
    
    // LaunchedEffects
    UpdateCheckEffect(
        hasCheckedForUpdates = hasCheckedForUpdates,
        updateViewModel = updateViewModel,
        context = context,
        onUpdateChecked = { hasCheckedForUpdates = true }
    )
    
    UpdateDialogEffect(
        updateInfo = updateInfo,
        onShowDialog = { showUpdateDialog = true }
    )
    
    AutoCloseDialogEffect(
        downloadProgress = downloadProgress,
        onCloseDialog = { showUpdateDialog = false }
    )
    
    CategorySelectionEffect(
        selectedDestination = selectedDestination,
        sortedCategories = categoryManagement.sortedCategories,
        selectedCategoryId = selectedCategoryId,
        pendingNavigationRestore = pendingNavigationRestore,
        onCategoryIdChanged = { selectedCategoryId = it }
    )
    
    SnackbarEffect(
        snackbarMessage = snackbarMessage,
        snackbarHostState = snackbarHostState,
        onMessageShown = { snackbarMessage = null }
    )

    // Once categories are already loaded, route content-load errors to a snackbar instead
    // of letting MainContentScaffold's full-screen error branch unmount Main content (F3).
    LaunchedEffect(uiState.error) {
        val error = uiState.error
        if (error != null && categoryManagement.sortedCategories.isNotEmpty()) {
            snackbarMessage = error
            mainViewModel.clearError()
        }
    }
    
    DestinationChangeEffect(
        selectedDestination = selectedDestination,
        onDestinationChanged = { focusState.pendingFocusDestination = it },
        onResetFocusFlags = { focusState.resetFocusFlags() }
    )
    
    NavbarFocusEffect(
        navbarReady = focusState.navbarReady,
        initialFocusSet = focusState.initialFocusSet,
        navbarFocusRequester = navbarFocusRequester,
        onFocusSet = { focusState.initialFocusSet = true }
    )
    
    CategorySidebarFocusEffect(
        categorySidebarReady = focusState.categorySidebarReady,
        categorySidebarFocusSet = focusState.categorySidebarFocusSet,
        pendingFocusDestination = focusState.pendingFocusDestination,
        showCategories = categoryManagement.showCategories,
        sortedCategories = categoryManagement.sortedCategories,
        categorySidebarFocusRequester = categorySidebarFocusRequester,
        onFocusSet = { focusState.categorySidebarFocusSet = true }
    )
    
    // Navigation state restoration effect
    NavigationRestoreEffect(
        pendingNavigationRestore = pendingNavigationRestore,
        restoreContext = pendingNavigationRestore?.let { state ->
            NavigationRestoreContext(
                state = state,
                currentDestination = selectedDestination,
                currentCategoryId = selectedCategoryId,
                onDestinationChanged = { selectedDestinationKey = it.toSaveKey() },
                onCategoryIdChanged = { selectedCategoryId = it },
                liveCategories = liveCategories,
                vodCategories = vodCategories,
                seriesCategories = seriesCategories,
                sortedCategories = categoryManagement.sortedCategories,
                lazyGridState = lazyGridState,
                focusRequesterMap = focusRequesterMap,
                mainViewModel = mainViewModel
            )
        },
        mainViewModel = mainViewModel
    )
    
    // Handler function wrapper
    val onDestinationChangeHandler: (HomeDestination) -> Unit = { destination ->
        handleDestinationChangeInternal(
            destination = destination,
            navController = navController,
            mainViewModel = mainViewModel,
            coroutineScope = coroutineScope,
            categoryVisibilityService = dependencies.categoryVisibilityService,
            onCategoryIdChanged = { selectedCategoryId = it },
            onDestinationChanged = { newDestination ->
                clearStreamsForDestination(selectedDestination, mainViewModel)
                selectedDestinationKey = newDestination.toSaveKey()
            }
        )
    }
    
    // Search click handlers
    val searchClickHandlers = remember(credentials, showSearchOverlay) {
        createSearchClickHandlers(
            credentials = credentials,
            navController = navController,
            mainViewModel = mainViewModel,
            onDismiss = {
                showSearchOverlay = false
                focusManager.moveFocusToNavbar()
            }
        )
    }
    
    // Update Dialog
    UpdateDialogContent(
        showUpdateDialog = showUpdateDialog,
        updateInfo = updateInfo,
        currentVersion = currentVersionFromViewModel,
        downloadProgress = downloadProgress,
        updateViewModel = updateViewModel,
        onDismiss = { showUpdateDialog = false }
    )
    
    // String resources
    val categoryHiddenMessage = stringResource(R.string.category_hidden)
    
    // Single in-progress pipeline: feeds the "Angefangene Inhalte" grid AND its sidebar counter
    val inProgressItems = rememberInProgressItems(
        destination = selectedDestination,
        watchProgressService = dependencies.watchProgressService,
        cacheService = dependencies.cacheService,
        xtreamRepository = dependencies.xtreamRepository,
        credentials = credentials
    )
    val itemCounts = calculateItemCounts(inProgressItems.items)
    
    // Loading indicator with minimum display duration
    var loadingStartTime by remember { mutableStateOf(0L) }
    var showLoading by remember { mutableStateOf(uiState.isLoading) }

    LaunchedEffect(uiState.isLoading) {
        if (uiState.isLoading) {
            loadingStartTime = System.currentTimeMillis()
            showLoading = true
        } else if (loadingStartTime > 0L) {
            val elapsed = System.currentTimeMillis() - loadingStartTime
            val remaining = MIN_LOADING_DURATION_MS - elapsed
            if (remaining > 0) {
                kotlinx.coroutines.delay(remaining)
            }
            showLoading = false
        }
    }

    // Main UI
    MainContentScaffold(
        params = MainContentScaffoldParams(
            showLoading = showLoading,
            uiState = uiState,
            selectedDestination = selectedDestination,
            onDestinationChangeHandler = onDestinationChangeHandler,
            categoryManagement = categoryManagement,
            selectedCategoryId = selectedCategoryId,
            onCategorySelected = { selectedCategoryId = it },
            itemCounts = itemCounts,
            inProgressItems = inProgressItems,
            navbarFocusRequester = navbarFocusRequester,
            categorySidebarFocusRequester = categorySidebarFocusRequester,
            categoryFocusRequesterMap = categoryFocusRequesterMap,
            focusManager = focusManager,
            onCategoryHidden = { category ->
                Log.d("AppNavigation", "[CategoryHidden] Callback called for category: ${category.categoryName} (${category.categoryId})")
                coroutineScope.launch {
                    handleCategoryHidden(
                        category = category,
                        context = CategoryHiddenContext(
                            categoryType = categoryManagement.categoryType,
                            selectedCategoryId = selectedCategoryId,
                            sortedCategories = categoryManagement.sortedCategories,
                            categoryVisibilityService = dependencies.categoryVisibilityService,
                            onCategoryIdChanged = { selectedCategoryId = it },
                            onSnackbarMessage = { snackbarMessage = it },
                            categoryHiddenMessage = categoryHiddenMessage
                        )
                    )
                }
            },
            onSearchClick = { showSearchOverlay = true },
            focusState = focusState,
            categorySidebarListState = categorySidebarListState,
            snackbarHostState = snackbarHostState,
            showSearchOverlay = showSearchOverlay,
            onSearchOverlayDismiss = { showSearchOverlay = false },
            searchState = searchState,
            mainViewModel = mainViewModel,
            searchClickHandlers = searchClickHandlers,
            credentials = credentials,
            navController = navController,
            contentListFocusRequester = contentListFocusRequester,
            lazyGridState = lazyGridState,
            focusRequesterMap = focusRequesterMap,
            dependencies = dependencies
        )
    )
}
