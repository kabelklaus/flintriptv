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
import com.flintr.tv.ui.tabs.VODContent
import com.flintr.tv.ui.tabs.SeriesContent
import com.flintr.tv.ui.tabs.SeriesContentParams
import com.flintr.tv.ui.tabs.FavoritesContent
import com.flintr.tv.ui.tabs.FavoritesContentParams
import com.flintr.tv.ui.screens.SeriesEpisodesScreen
import com.flintr.tv.viewmodel.MainViewModel
import com.flintr.tv.viewmodel.PlayerViewModel
import com.flintr.tv.viewmodel.UpdateViewModel
import com.flintr.tv.data.model.UpdateInfo
import com.flintr.tv.utils.DownloadProgress
import com.flintr.tv.ui.utils.safeRequestFocus
import com.flintr.tv.ui.components.UpdateDialog
import com.flintr.tv.ui.components.SearchOverlay
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

private const val LOG_TAG_MAIN_CONTENT_SCREEN = "[MainContentScreen]"

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
    mainViewModel: MainViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel()
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
                playerViewModel = playerViewModel,
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
        is HomeDestination.Favorites -> {
            // Favorites doesn't have streams to clear
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
    val onNavigateToCategorySidebar: () -> Unit,
    val onNavigateToNavbar: () -> Unit,
    val onFavoritesSeriesClick: (Series) -> Unit
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
                viewModel = params.mainViewModel,
                selectedCategoryId = params.selectedCategoryId,
                focusRequester = params.contentListFocusRequester,
                favoritesRepository = params.favoritesRepository,
                onNavigateLeft = params.onNavigateToCategorySidebar,
                onChannelClick = { stream ->
                    val creds = params.credentials ?: return@LiveTVContent
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
            )
        }
        is HomeDestination.VOD -> {
            VODContent(
                viewModel = params.mainViewModel,
                selectedCategoryId = params.selectedCategoryId,
                focusRequester = params.contentListFocusRequester,
                favoritesRepository = params.favoritesRepository,
                onNavigateLeft = params.onNavigateToCategorySidebar,
                onVodClick = { vod ->
                    val creds = params.credentials ?: return@VODContent
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
            )
        }
        is HomeDestination.Series -> {
            SeriesContent(
                params = SeriesContentParams(
                    viewModel = params.mainViewModel,
                    selectedCategoryId = params.selectedCategoryId,
                    focusRequester = params.contentListFocusRequester,
                    favoritesRepository = params.favoritesRepository,
                    onNavigateLeft = params.onNavigateToCategorySidebar,
                    onSeriesClick = { series ->
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
        is HomeDestination.Favorites -> {
            val onLiveStreamClick: (LiveStream) -> Unit = { stream ->
                val creds = params.credentials
                if (creds != null) {
                    val streamUrl = stream.getStreamUrl(
                        creds.serverUrl,
                        creds.username,
                        creds.password
                    )
                    val route = Screen.Player.createRouteForLive(streamUrl)
                    params.navController.logAndNavigate(route, "AppNavigation") {
                        "⭐ Favorites LiveTV onClick - stream.name: ${stream.name}, streamUrl: $streamUrl, route: $route"
                    }
                }
            }
            val onVodClick: (VodStream) -> Unit = { vod ->
                val creds = params.credentials
                if (creds != null) {
                    val streamUrl = vod.getStreamUrl(
                        creds.serverUrl,
                        creds.username,
                        creds.password
                    )
                    val route = Screen.Player.createRouteForMovie(streamUrl, vod.streamId, vod.name)
                    params.navController.logAndNavigate(route, "AppNavigation") {
                        "⭐ Favorites VOD onClick - vod.name: ${vod.name}, vod.streamId: ${vod.streamId}, streamUrl: $streamUrl, route: $route"
                    }
                }
            }
            val onSeriesClick: (Series) -> Unit = params.onFavoritesSeriesClick
            FavoritesContent(
                params = FavoritesContentParams(
                    favoritesRepository = params.favoritesRepository,
                    cacheService = params.cacheService,
                    focusRequester = params.contentListFocusRequester,
                    onNavigateLeft = params.onNavigateToNavbar,
                    onLiveStreamClick = onLiveStreamClick,
                    onVodClick = onVodClick,
                    onSeriesClick = onSeriesClick
                )
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
        else -> null // EPG, Favorites, Search, Settings don't have categories
    }
    
    // Select categories based on destination
    val categories = when (selectedDestination) {
        is HomeDestination.LiveTV -> liveCategories
        is HomeDestination.VOD -> vodCategories
        is HomeDestination.Series -> seriesCategories
        is HomeDestination.EPG -> emptyList()
        is HomeDestination.Favorites -> emptyList() // Favorites doesn't have categories
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
    
    val showCategories = selectedDestination is HomeDestination.LiveTV ||
            selectedDestination is HomeDestination.VOD ||
            selectedDestination is HomeDestination.Series
    
    return CategoryManagementResult(
        categoryType = categoryType,
        sortedCategories = sortedCategories,
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
    onCategoryIdChanged: (String?) -> Unit
) {
    LaunchedEffect(selectedDestination, sortedCategories) {
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
            pendingFocusDestination !is HomeDestination.Favorites &&
            showCategories && sortedCategories.isNotEmpty()) {
            onFocusSet()
            categorySidebarFocusRequester.safeRequestFocus(LOG_TAG_MAIN_CONTENT_SCREEN, "CATEGORY_SIDEBAR")
        }
    }
}

/**
 * LaunchedEffect for setting focus on ContentList when ready
 */
@Composable
private fun ContentListFocusEffect(
    contentListReady: Boolean,
    contentListFocusSet: Boolean,
    pendingFocusDestination: HomeDestination?,
    contentListFocusRequester: FocusRequester,
    onFocusSet: () -> Unit
) {
    LaunchedEffect(contentListReady, pendingFocusDestination) {
        if (contentListReady && 
            !contentListFocusSet &&
            pendingFocusDestination is HomeDestination.Favorites) {
            onFocusSet()
            contentListFocusRequester.safeRequestFocus(LOG_TAG_MAIN_CONTENT_SCREEN, "CONTENT_LIST")
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
 * Handler function for category hiding logic
 */
private fun handleCategoryHidden(
    category: Category,
    categoryType: CategoryType?,
    selectedCategoryId: String?,
    sortedCategories: List<Category>,
    categoryVisibilityService: CategoryVisibilityService,
    onCategoryIdChanged: (String?) -> Unit,
    onSnackbarMessage: (String) -> Unit,
    categoryHiddenMessage: String
) {
    // Hide the category (only if categoryType is not null)
    categoryType?.let { type ->
        categoryVisibilityService.hideCategory(category.categoryId, type)
        
        // If the hidden category was currently selected, select the first available category
        if (selectedCategoryId == category.categoryId) {
            val remainingCategories = sortedCategories.filter { it.categoryId != category.categoryId }
            onCategoryIdChanged(remainingCategories.firstOrNull()?.categoryId)
        }
        
        // Show snackbar feedback
        onSnackbarMessage(categoryHiddenMessage)
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
    
    // Focus requesters
    val navbarFocusRequester = remember { FocusRequester() }
    val categorySidebarFocusRequester = remember { FocusRequester() }
    val contentListFocusRequester = remember { FocusRequester() }
    
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
    
    ContentListFocusEffect(
        contentListReady = focusState.contentListReady,
        contentListFocusSet = focusState.contentListFocusSet,
        pendingFocusDestination = focusState.pendingFocusDestination,
        contentListFocusRequester = contentListFocusRequester,
        onFocusSet = { focusState.contentListFocusSet = true }
    )
    
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
    
    fun handleFavoritesSeriesClick(series: Series) {
        handleDestinationChange(HomeDestination.Series)
        val categoryId = mainViewModel.showSeriesFromFavorites(series)
        if (categoryId != null) {
            selectedCategoryId = categoryId
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
                navbarFocusRequester = navbarFocusRequester,
                categorySidebarFocusRequester = categorySidebarFocusRequester,
                onNavigateToNavbar = { focusManager.moveFocusToNavbar() },
                onNavigateToCategorySidebar = { focusManager.moveFocusToCategorySidebar() },
                onNavigateToContentList = { focusManager.moveFocusToContentList() },
                onCategoryHidden = { category ->
                    Log.d("AppNavigation", "[CategoryHidden] Callback called for category: ${category.categoryName} (${category.categoryId})")
                    handleCategoryHidden(
                        category = category,
                        categoryType = categoryManagement.categoryType,
                        selectedCategoryId = selectedCategoryId,
                        sortedCategories = categoryManagement.sortedCategories,
                        categoryVisibilityService = categoryVisibilityService,
                        onCategoryIdChanged = { selectedCategoryId = it },
                        onSnackbarMessage = { snackbarMessage = it },
                        categoryHiddenMessage = categoryHiddenMessage
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
                        onNavigateToCategorySidebar = { focusManager.moveFocusToCategorySidebar() },
                        onNavigateToNavbar = { focusManager.moveFocusToNavbar() },
                        onFavoritesSeriesClick = ::handleFavoritesSeriesClick
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


