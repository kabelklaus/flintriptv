package com.flintr.tv.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import com.flintr.tv.data.local.FavoritesRepository
import com.flintr.tv.data.local.WatchProgressService
import com.flintr.tv.data.model.Category
import com.flintr.tv.data.model.CategoryType
import com.flintr.tv.data.model.LiveStream
import com.flintr.tv.data.model.NavigationStateBeforePlayer
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
import com.flintr.tv.ui.screens.SeriesEpisodesScreen
import com.flintr.tv.viewmodel.MainViewModel
import com.flintr.tv.viewmodel.UpdateViewModel
import com.flintr.tv.data.model.UpdateInfo
import com.flintr.tv.utils.DownloadProgress
import com.flintr.tv.ui.utils.safeRequestFocus
import com.flintr.tv.ui.components.UpdateDialog
import com.flintr.tv.ui.components.SearchOverlay
import com.flintr.tv.ui.common.UiConstants.FAVORITES_CATEGORY_ID
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.res.stringResource
import com.flintr.tv.R
import com.flintr.tv.utils.loadPackageInfo

sealed class Screen(val route: String) {
    object InitialRouting : Screen("initial_routing")
    object Login : Screen("login")
    object DataLoading : Screen("data_loading")
    object Main : Screen("main")
    object Settings : Screen("settings")
    object Player : Screen("player/{streamUrl}/{contentType}/{streamId}/{seriesId}/{episodeId}/{title}") {
        const val EMPTY_TITLE_PLACEHOLDER = "__EMPTY_TITLE__"
        
        fun createRoute(streamUrl: String): String {
            val encoded = Uri.encode(streamUrl, null)
            return "player/$encoded/live/-1/-1/-1/$EMPTY_TITLE_PLACEHOLDER"
        }
        
        fun createRouteForMovie(streamUrl: String, streamId: Int, title: String): String {
            val encodedUrl = Uri.encode(streamUrl, null)
            val encodedTitle = if (title.isNotEmpty()) Uri.encode(title, null) else EMPTY_TITLE_PLACEHOLDER
            return "player/$encodedUrl/movie/$streamId/-1/-1/$encodedTitle"
        }
        
        fun createRouteForSeries(streamUrl: String, seriesId: Int, episodeId: Int, title: String): String {
            val encodedUrl = Uri.encode(streamUrl, null)
            val encodedTitle = if (title.isNotEmpty()) Uri.encode(title, null) else EMPTY_TITLE_PLACEHOLDER
            return "player/$encodedUrl/series/-1/$seriesId/$episodeId/$encodedTitle"
        }
        
        fun createRouteForLive(streamUrl: String): String {
            return createRoute(streamUrl)
        }
    }
    
    object SeriesEpisodes : Screen("series_episodes/{seriesId}") {
        fun createRoute(seriesId: Int): String {
            return "series_episodes/$seriesId"
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
 * Helper functions for generating content IDs according to the schema:
 * - Live TV: "live_${streamId}"
 * - VOD: "vod_${streamId}"
 * - Series: "series_${seriesId}_${seasonNum}_${episodeId}"
 */
private fun LiveStream.toContentId(): String = "live_$streamId"
private fun VodStream.toContentId(): String = "vod_$streamId"
private fun Series.toContentId(): String = "series_$seriesId"

/**
 * Helper function to generate content ID for episode
 * Note: SeriesEpisodesScreen is a separate screen, so we don't save navigation state there
 * The state is saved when navigating from Series screen to SeriesEpisodesScreen
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
        else -> HomeDestination.LiveTV // Default fallback for FAVORITES and other types
    }
}

private const val LOG_TAG_MAIN_CONTENT_SCREEN = "[MainContentScreen]"

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
 * Restores the category with fallback logic if category no longer exists.
 */
private suspend fun restoreCategory(
    state: NavigationStateBeforePlayer,
    destination: HomeDestination,
    currentCategoryId: String?,
    onCategoryIdChanged: (String?) -> Unit,
    liveCategories: List<Category>,
    vodCategories: List<Category>,
    seriesCategories: List<Category>
) {
    if (state.categoryId == null) return
    
    // Check if category still exists
    val categoryExists = when (destination) {
        is HomeDestination.LiveTV -> liveCategories.any { it.categoryId == state.categoryId }
        is HomeDestination.VOD -> vodCategories.any { it.categoryId == state.categoryId }
        is HomeDestination.Series -> seriesCategories.any { it.categoryId == state.categoryId }
        else -> false
    }
    
    if (categoryExists) {
        if (currentCategoryId != state.categoryId) {
            onCategoryIdChanged(state.categoryId)
        }
    } else {
        // Fallback: Select first available category
        Log.w(LOG_TAG_MAIN_CONTENT_SCREEN, "Category ${state.categoryId} no longer exists, selecting first available category")
        val firstCategory = when (destination) {
            is HomeDestination.LiveTV -> liveCategories.firstOrNull()
            is HomeDestination.VOD -> vodCategories.firstOrNull()
            is HomeDestination.Series -> seriesCategories.firstOrNull()
            else -> null
        }
        onCategoryIdChanged(firstCategory?.categoryId)
    }
}

/**
 * Waits for content to be loaded with timeout.
 */
private suspend fun waitForContentLoaded(isLoading: Boolean) {
    var retryCount = 0
    val maxRetries = 20
    while (isLoading && retryCount < maxRetries) {
        kotlinx.coroutines.delay(100)
        retryCount++
    }
    if (retryCount >= maxRetries && isLoading) {
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
        Log.w(LOG_TAG_MAIN_CONTENT_SCREEN, "Failed to restore scroll position: ${e.message}")
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
        try {
            focusRequester.requestFocus()
            Log.d(LOG_TAG_MAIN_CONTENT_SCREEN, "Focus restored on content card: ${state.contentId}")
        } catch (e: Exception) {
            Log.w(LOG_TAG_MAIN_CONTENT_SCREEN, "Failed to restore focus: ${e.message}")
            requestFallbackFocus(focusRequesterMap)
        }
    } else {
        Log.w(LOG_TAG_MAIN_CONTENT_SCREEN, "FocusRequester not found for contentId: ${state.contentId} after retries, focusing first available card")
        requestFallbackFocus(focusRequesterMap)
    }
}

/**
 * Attempts to focus the first available card as fallback.
 */
private fun requestFallbackFocus(focusRequesterMap: MutableMap<String, FocusRequester>) {
    try {
        focusRequesterMap.values.firstOrNull()?.requestFocus()
    } catch (e: Exception) {
        Log.w(LOG_TAG_MAIN_CONTENT_SCREEN, "Fallback focus failed: ${e.message}")
    }
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
    val isLoading: Boolean,
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
    restoreCategory(context.state, destination, context.currentCategoryId, context.onCategoryIdChanged, context.liveCategories, context.vodCategories, context.seriesCategories)
    
    // 3. Wait for content to be loaded (state-based check instead of fixed delay)
    waitForContentLoaded(context.isLoading)
    
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
    val favoritesRepository: FavoritesRepository,
    val watchProgressService: WatchProgressService,
    val categoryVisibilityService: CategoryVisibilityService
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
            LoginScreen(
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
                }
            )
        }

        composable(Screen.Main.route) {
            MainContentScreen(
                navController = navController,
                mainViewModel = mainViewModel,
                credentialsRepository = dependencies.credentialsRepository,
                favoritesRepository = dependencies.favoritesRepository,
                cacheService = dependencies.cacheService,
                categoryVisibilityService = dependencies.categoryVisibilityService
            )
        }
        
        composable(Screen.Settings.route) {
            SettingsScreen(
                focusRequester = remember { FocusRequester() },
                onNavigateLeft = { navController.popBackStack() },
                categoryVisibilityService = dependencies.categoryVisibilityService,
                mainViewModel = mainViewModel,
                credentialsRepository = dependencies.credentialsRepository
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
            
            val streamId = streamIdStr.toIntOrNull()?.takeIf { it >= 0 }
            val seriesId = seriesIdStr.toIntOrNull()?.takeIf { it >= 0 }
            val episodeId = episodeIdStr.toIntOrNull()?.takeIf { it >= 0 }
            
            PlayerScreen(
                streamInfo = StreamInfo(
                    streamUrl = streamUrl,
                    contentType = contentType,
                    streamId = streamId,
                    seriesId = seriesId,
                    episodeId = episodeId,
                    title = title.takeIf { it.isNotEmpty() }
                ),
                watchProgressService = dependencies.watchProgressService,
                onBack = { navController.popBackStack() }
            )
        }
        
        composable(
            route = Screen.SeriesEpisodes.route,
            arguments = listOf(
                navArgument("seriesId") {
                    type = NavType.IntType
                }
            )
        ) { backStackEntry ->
            val seriesId = backStackEntry.arguments?.getInt("seriesId") ?: -1
            SeriesEpisodesRoute(
                seriesId = seriesId,
                mainViewModel = mainViewModel,
                dependencies = dependencies,
                navController = navController
            )
        }
    }
}

@Composable
private fun SeriesEpisodesRoute(
    seriesId: Int,
    mainViewModel: MainViewModel,
    dependencies: AppNavigationDependencies,
    navController: NavHostController
) {
    val selectedSeries by mainViewModel.selectedSeries.collectAsState()
    
    // Load series info only if not already loaded for this seriesId
    LaunchedEffect(seriesId) {
        if (seriesId >= 0 && selectedSeries?.seriesId != seriesId) {
            mainViewModel.loadSeriesInfo(seriesId)
        }
    }
    
    if (selectedSeries != null && selectedSeries!!.seriesId == seriesId) {
        SeriesEpisodesScreen(
            series = selectedSeries!!,
            mainViewModel = mainViewModel,
            watchProgressService = dependencies.watchProgressService,
            onBack = { navController.popBackStack() },
            onEpisodeClick = { episode ->
                val creds = dependencies.credentialsRepository.getCredentials()
                if (creds != null) {
                    // Save navigation state before navigating to player
                    // Note: SeriesEpisodesScreen uses LazyRow, not LazyGrid, so we use 0 for scrollIndex/scrollOffset
                    // The actual restoration will be handled by focusing the episode card
                    val seasonNum = episode.season ?: 1
                    mainViewModel.saveNavigationStateBeforePlayer(
                        NavigationStateBeforePlayer(
                            destinationType = com.flintr.tv.data.model.DestinationType.SERIES,
                            categoryId = selectedSeries!!.categoryId,
                            contentId = episode.toContentId(selectedSeries!!.seriesId, seasonNum),
                            scrollIndex = 0, // LazyRow doesn't have firstVisibleItemIndex, use 0
                            scrollOffset = 0  // LazyRow doesn't have firstVisibleItemScrollOffset, use 0
                        )
                    )
                    val streamUrl = episode.getStreamUrl(
                        creds.serverUrl,
                        creds.username,
                        creds.password,
                        selectedSeries!!.seriesId
                    )
                    val route = Screen.Player.createRouteForSeries(
                        streamUrl,
                        selectedSeries!!.seriesId,
                        episode.id,
                        episode.title
                    )
                    navController.logAndNavigate(route, "AppNavigation") {
                        "📺 SeriesEpisodes onClick - episode.title: ${episode.title}, seriesId: ${selectedSeries!!.seriesId}, episodeId: ${episode.id}, streamUrl: $streamUrl, route: $route"
                    }
                }
            }
        )
    } else {
        // Show loading state
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
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
    val favoritesRepository: FavoritesRepository,
    val cacheService: DataCacheService,
    val contentListFocusRequester: FocusRequester,
    val navController: NavHostController,
    val lazyGridState: LazyGridState,
    val focusRequesterMap: MutableMap<String, FocusRequester>,
    val onNavigateToCategorySidebar: () -> Unit,
    val onNavigateToNavbar: () -> Unit
)

/**
 * Composable rendering the main content body based on selected destination
 */
@Composable
private fun MainContentBody(
    params: MainContentBodyParams
) {
    when (params.destination) {
        is HomeDestination.LiveTV -> {
            LiveTVContent(
                params = LiveTVContentParams(
                    viewModel = params.mainViewModel,
                    selectedCategoryId = params.selectedCategoryId,
                    focusRequester = params.contentListFocusRequester,
                    favoritesRepository = params.favoritesRepository,
                    cacheService = params.cacheService,
                    onNavigateLeft = params.onNavigateToCategorySidebar,
                    lazyGridState = params.lazyGridState,
                    focusRequesterMap = params.focusRequesterMap,
                    onChannelClick = { stream ->
                        val creds = params.credentials
                        if (creds != null) {
                            // Save navigation state before navigating to player
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
                            val route = Screen.Player.createRouteForLive(streamUrl)
                            params.navController.logAndNavigate(route, "AppNavigation") {
                                "📺 LiveTV onClick - stream.name: ${stream.name}, streamUrl: $streamUrl, route: $route"
                            }
                        }
                    }
                )
            )
        }
        is HomeDestination.VOD -> {
            VODContent(
                params = VODContentParams(
                    viewModel = params.mainViewModel,
                    selectedCategoryId = params.selectedCategoryId,
                    focusRequester = params.contentListFocusRequester,
                    favoritesRepository = params.favoritesRepository,
                    cacheService = params.cacheService,
                    onNavigateLeft = params.onNavigateToCategorySidebar,
                    lazyGridState = params.lazyGridState,
                    focusRequesterMap = params.focusRequesterMap,
                    onVodClick = { vod ->
                        val creds = params.credentials
                        if (creds != null) {
                            // Save navigation state before navigating to player
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
                            val streamUrl = vod.getStreamUrl(
                                creds.serverUrl,
                                creds.username,
                                creds.password
                            )
                            val route = Screen.Player.createRouteForMovie(streamUrl, vod.streamId, vod.name)
                            params.navController.logAndNavigate(route, "AppNavigation") {
                                "🎬 VOD onClick - vod.name: ${vod.name}, vod.streamId: ${vod.streamId}, streamUrl: $streamUrl, route: $route"
                            }
                        }
                    }
                )
            )
        }
        is HomeDestination.Series -> {
            SeriesContent(
                params = SeriesContentParams(
                    viewModel = params.mainViewModel,
                    selectedCategoryId = params.selectedCategoryId,
                    focusRequester = params.contentListFocusRequester,
                    favoritesRepository = params.favoritesRepository,
                    cacheService = params.cacheService,
                    onNavigateLeft = params.onNavigateToCategorySidebar,
                    lazyGridState = params.lazyGridState,
                    focusRequesterMap = params.focusRequesterMap,
                    onSeriesClick = { series ->
                        // Save navigation state before navigating to SeriesEpisodesScreen
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
                        val route = Screen.SeriesEpisodes.createRoute(series.seriesId)
                        params.navController.logAndNavigate(route, "AppNavigation") {
                            "📺 Series click - series.name: ${series.name}, seriesId: ${series.seriesId}, route: $route"
                        }
                    }
                )
            )
        }
        is HomeDestination.EPG -> {
            EPGContent(
                viewModel = params.mainViewModel,
                onProgramClick = { _, stream ->
                    val creds = params.credentials ?: return@EPGContent
                    // EPG doesn't have a grid, so we don't save navigation state here
                    // EPG navigation is out of scope for this story
                    val streamUrl = stream.getStreamUrl(
                        creds.serverUrl,
                        creds.username,
                        creds.password
                    )
                    val route = Screen.Player.createRouteForLive(streamUrl)
                    params.navController.logAndNavigate(route, "AppNavigation") {
                        "📅 EPG onClick - stream.name: ${stream.name}, streamUrl: $streamUrl, route: $route"
                    }
                }
            )
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
    // Determine category type based on destination
    val categoryType: CategoryType? = when (selectedDestination) {
        is HomeDestination.LiveTV -> CategoryType.LIVE_TV
        is HomeDestination.VOD -> CategoryType.VOD
        is HomeDestination.Series -> CategoryType.SERIES
        else -> null // EPG, Search, Settings don't have categories
    }
    
    // Select categories based on destination
    val categories = when (selectedDestination) {
        is HomeDestination.LiveTV -> liveCategories
        is HomeDestination.VOD -> vodCategories
        is HomeDestination.Series -> seriesCategories
        is HomeDestination.EPG -> emptyList()
        is HomeDestination.Search -> emptyList() // Search doesn't have categories
        is HomeDestination.Settings -> emptyList() // Settings navigates to separate screen
    }
    
    // Filter out hidden categories using StateFlow
    val filteredCategories = categories.filter { 
        categoryType?.let { type ->
            !(hiddenCategories[type]?.contains(it.categoryId) ?: false)
        } ?: true // If no categoryType, don't filter
    }
    
    // Sort categories the same way as in CategoryRow for consistency
    val sortedCategories = filteredCategories.sortedBy { it.categoryName }
    
    // Create favorites category and prepend to sorted categories
    val favoritesCategoryName = stringResource(R.string.favorites)
    val categoriesWithFavorites = remember(sortedCategories, selectedDestination, favoritesCategoryName) {
        if (selectedDestination is HomeDestination.LiveTV ||
            selectedDestination is HomeDestination.VOD ||
            selectedDestination is HomeDestination.Series) {
            val favoritesCategory = Category(
                categoryId = FAVORITES_CATEGORY_ID,
                categoryName = favoritesCategoryName,
                parentId = 0
            )
            listOf(favoritesCategory) + sortedCategories
        } else {
            sortedCategories
        }
    }
    
    val showCategories = selectedDestination is HomeDestination.LiveTV ||
            selectedDestination is HomeDestination.VOD ||
            selectedDestination is HomeDestination.Series
    
    return CategoryManagementResult(
        categoryType = categoryType,
        sortedCategories = categoriesWithFavorites,
        showCategories = showCategories
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
        
        if (sortedCategories.isEmpty()) {
            onCategoryIdChanged(null)
        } else {
            // Always select first category if none selected or selected category doesn't exist in current list
            val currentCategoryExists = selectedCategoryId != null && 
                sortedCategories.any { it.categoryId == selectedCategoryId }
            if (!currentCategoryExists) {
                onCategoryIdChanged(sortedCategories.first().categoryId)
            }
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
    private val logTag: String
) {
    fun moveFocusToNavbar() {
        Log.i("TVFocus", "$logTag 🎯 Requesting focus: NAVBAR")
        try {
            navbarFocusRequester.requestFocus()
        } catch (e: IllegalStateException) {
            Log.w("TVFocus", "$logTag ⚠️ Navbar FocusRequester not initialized yet")
        }
    }
    
    fun moveFocusToCategorySidebar() {
        Log.i("TVFocus", "$logTag 🎯 Requesting focus: CATEGORY_SIDEBAR")
        try {
            categorySidebarFocusRequester.requestFocus()
        } catch (e: IllegalStateException) {
            Log.w("TVFocus", "$logTag ⚠️ CategorySidebar FocusRequester not initialized yet")
        }
    }
    
    fun moveFocusToContentList() {
        Log.i("TVFocus", "$logTag 🎯 Requesting focus: CONTENT_LIST")
        try {
            contentListFocusRequester.requestFocus()
        } catch (e: IllegalStateException) {
            // FocusRequester not yet initialized - this can happen if called before content is loaded
            // The content will request focus automatically when streams are loaded
            Log.w("TVFocus", "$logTag ⚠️ ContentList FocusRequester not initialized yet, will focus when content loads")
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
private fun handleCategoryHidden(
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
            val route = Screen.Player.createRouteForLive(streamUrl)
            mainViewModel.clearSearch()
            onDismiss()
            navController.logAndNavigate(route, "AppNavigation") {
                "🔍 Search LiveTV onClick - stream.name: ${stream.name}, streamUrl: $streamUrl, route: $route"
            }
        },
        onVodClick = { vod ->
            val creds = credentials ?: return@SearchClickHandlers
            val streamUrl = vod.getStreamUrl(
                creds.serverUrl,
                creds.username,
                creds.password
            )
            val route = Screen.Player.createRouteForMovie(streamUrl, vod.streamId, vod.name)
            mainViewModel.clearSearch()
            onDismiss()
            navController.logAndNavigate(route, "AppNavigation") {
                "🔍 Search VOD onClick - vod.name: ${vod.name}, vod.streamId: ${vod.streamId}, streamUrl: $streamUrl, route: $route"
            }
        },
        onSeriesClick = { series ->
            val route = Screen.SeriesEpisodes.createRoute(series.seriesId)
            mainViewModel.clearSearch()
            onDismiss()
            navController.logAndNavigate(route, "AppNavigation") {
                "🔍 Search Series onClick - series.name: ${series.name}, seriesId: ${series.seriesId}, route: $route"
            }
        }
    )
}

@Composable
fun MainContentScreen(
    navController: NavHostController,
    mainViewModel: MainViewModel,
    credentialsRepository: CredentialsRepository,
    favoritesRepository: FavoritesRepository,
    cacheService: DataCacheService,
    categoryVisibilityService: CategoryVisibilityService,
    updateViewModel: UpdateViewModel = hiltViewModel()
) {
    // State initialization
    val credentials = credentialsRepository.getCredentials()
    val context = LocalContext.current
    var selectedDestination by remember { mutableStateOf<HomeDestination>(HomeDestination.LiveTV) }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    var hasCheckedForUpdates by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showSearchOverlay by remember { mutableStateOf(false) }
    val focusState = rememberMainContentFocusState()
    
    // ViewModel state collection
    val searchState by mainViewModel.searchState.collectAsState()
    val updateInfo by updateViewModel.updateInfo.collectAsState()
    val downloadProgress by updateViewModel.downloadProgress.collectAsState()
    val currentVersionFromViewModel by updateViewModel.currentVersion.collectAsState()
    val liveCategories by mainViewModel.liveCategories.collectAsState()
    val vodCategories by mainViewModel.vodCategories.collectAsState()
    val seriesCategories by mainViewModel.seriesCategories.collectAsState()
    val hiddenCategories by categoryVisibilityService.hiddenCategories.collectAsState()
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
    
    // Focus manager
    val focusManager = remember {
        FocusManager(navbarFocusRequester, categorySidebarFocusRequester, contentListFocusRequester, LOG_TAG_MAIN_CONTENT_SCREEN)
    }
    
    // Category management
    val categoryManagement = useCategoryManagement(
        selectedDestination = selectedDestination,
        liveCategories = liveCategories,
        vodCategories = vodCategories,
        seriesCategories = seriesCategories,
        hiddenCategories = hiddenCategories
    )
    
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
    
    // Check for navigation state when returning from Player or SeriesEpisodesScreen
    // This LaunchedEffect runs when the screen is first composed (e.g., after process death)
    LaunchedEffect(Unit) {
        mainViewModel.consumeNavigationState()
    }
    
    // Navigation state restoration effect
    // Combines both restoration logic and state consumption check
    LaunchedEffect(pendingNavigationRestore) {
        val state = pendingNavigationRestore
        if (state != null) {
            // Restore navigation state using extracted function
            restoreNavigationState(
                context = NavigationRestoreContext(
                    state = state,
                    currentDestination = selectedDestination,
                    currentCategoryId = selectedCategoryId,
                    onDestinationChanged = { selectedDestination = it },
                    onCategoryIdChanged = { selectedCategoryId = it },
                    liveCategories = liveCategories,
                    vodCategories = vodCategories,
                    seriesCategories = seriesCategories,
                    isLoading = uiState.isLoading,
                    lazyGridState = lazyGridState,
                    focusRequesterMap = focusRequesterMap,
                    mainViewModel = mainViewModel
                )
            )
        } else {
            // If pendingNavigationRestore is null but there might be a state in SavedStateHandle,
            // try to consume it (this handles the case when returning from SeriesEpisodesScreen)
            // Small delay to ensure navigation is complete
            kotlinx.coroutines.delay(100)
            mainViewModel.consumeNavigationState()
        }
    }
    
    // Handler functions
    fun handleDestinationChange(destination: HomeDestination) {
        if (destination is HomeDestination.Settings) {
            navController.navigate(Screen.Settings.route)
        } else {
            selectedCategoryId = null
            clearStreamsForDestination(selectedDestination, mainViewModel)
            clearStreamsForDestination(destination, mainViewModel)
            selectedDestination = destination
        }
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
    
    // No item counts displayed for categories
    val itemCounts = emptyMap<String, Int>()
    
    // Main UI
    Box(modifier = Modifier.fillMaxSize()) {
        MainLayout(
            params = MainLayoutParams(
                selectedDestination = selectedDestination,
                onDestinationChange = ::handleDestinationChange,
                showCategories = categoryManagement.showCategories,
                categories = categoryManagement.sortedCategories,
                selectedCategoryId = selectedCategoryId,
                onCategorySelected = { selectedCategoryId = it },
                itemCounts = itemCounts,
                navbarFocusRequester = navbarFocusRequester,
                categorySidebarFocusRequester = categorySidebarFocusRequester,
                onNavigateToNavbar = { focusManager.moveFocusToNavbar() },
                onNavigateToCategorySidebar = { focusManager.moveFocusToCategorySidebar() },
                onNavigateToContentList = { focusManager.moveFocusToContentList() },
                onCategoryHidden = { category ->
                    Log.d("AppNavigation", "[CategoryHidden] Callback called for category: ${category.categoryName} (${category.categoryId})")
                    handleCategoryHidden(
                        category = category,
                        context = CategoryHiddenContext(
                            categoryType = categoryManagement.categoryType,
                            selectedCategoryId = selectedCategoryId,
                            sortedCategories = categoryManagement.sortedCategories,
                            categoryVisibilityService = categoryVisibilityService,
                            onCategoryIdChanged = { selectedCategoryId = it },
                            onSnackbarMessage = { snackbarMessage = it },
                            categoryHiddenMessage = categoryHiddenMessage
                        )
                    )
                },
                onSearchClick = { showSearchOverlay = true },
                onNavbarReady = { focusState.navbarReady = true },
                onCategorySidebarReady = { focusState.categorySidebarReady = true },
                onContentListReady = { focusState.contentListReady = true }
            ),
            content = {
                MainContentBody(
                    params = MainContentBodyParams(
                        destination = selectedDestination,
                        selectedCategoryId = selectedCategoryId,
                        credentials = credentials,
                        mainViewModel = mainViewModel,
                        favoritesRepository = favoritesRepository,
                        cacheService = cacheService,
                        contentListFocusRequester = contentListFocusRequester,
                        navController = navController,
                        lazyGridState = lazyGridState,
                        focusRequesterMap = focusRequesterMap,
                        onNavigateToCategorySidebar = { focusManager.moveFocusToCategorySidebar() },
                        onNavigateToNavbar = { focusManager.moveFocusToNavbar() }
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
                onDismiss = {
                    mainViewModel.clearSearch()
                    showSearchOverlay = false
                    focusManager.moveFocusToNavbar()
                },
                onLiveStreamClick = searchClickHandlers.onLiveStreamClick,
                onVodClick = searchClickHandlers.onVodClick,
                onSeriesClick = searchClickHandlers.onSeriesClick
            )
        }
    }
}


