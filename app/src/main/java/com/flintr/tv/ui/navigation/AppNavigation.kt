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
import com.flintr.tv.ui.utils.safeRequestFocus
import com.flintr.tv.ui.components.UpdateDialog
import com.flintr.tv.ui.components.SearchOverlay
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
    val credentials = credentialsRepository.getCredentials()
    val context = LocalContext.current
    var selectedDestination by remember { mutableStateOf<HomeDestination>(HomeDestination.LiveTV) }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    
    // Update check state
    var hasCheckedForUpdates by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    
    // Search overlay state
    var showSearchOverlay by remember { mutableStateOf(false) }
    val searchState by mainViewModel.searchState.collectAsState()
    
    // UpdateViewModel state
    val updateInfo by updateViewModel.updateInfo.collectAsState()
    val downloadProgress by updateViewModel.downloadProgress.collectAsState()
    val currentVersionFromViewModel by updateViewModel.currentVersion.collectAsState()
    
    // Load package info and check for updates once
    LaunchedEffect(Unit) {
        if (!hasCheckedForUpdates) {
            hasCheckedForUpdates = true
            val packageInfo = loadPackageInfo(context)
            val version = packageInfo?.versionName
            if (version != null) {
                updateViewModel.checkForUpdates(version)
            }
        }
    }
    
    // Show update dialog when update becomes available
    LaunchedEffect(updateInfo?.hasUpdate) {
        if (updateInfo?.hasUpdate == true && updateInfo?.downloadUrl != null) {
            showUpdateDialog = true
        }
    }
    
    // Auto-close dialog when installation starts
    LaunchedEffect(downloadProgress?.isComplete) {
        if (downloadProgress?.isComplete == true) {
            showUpdateDialog = false
        }
    }
    
    // Focus requesters for each panel
    val navbarFocusRequester = remember { FocusRequester() }
    val categorySidebarFocusRequester = remember { FocusRequester() }
    val contentListFocusRequester = remember { FocusRequester() }
    
    val liveCategories by mainViewModel.liveCategories.collectAsState()
    val vodCategories by mainViewModel.vodCategories.collectAsState()
    val seriesCategories by mainViewModel.seriesCategories.collectAsState()
    
    // Collect hidden categories state reactively
    val hiddenCategories by categoryVisibilityService.hiddenCategories.collectAsState()
    
    // Determine category type based on destination
    val categoryType = when (selectedDestination) {
        is HomeDestination.LiveTV -> CategoryType.LIVE_TV
        is HomeDestination.VOD -> CategoryType.VOD
        is HomeDestination.Series -> CategoryType.SERIES
        is HomeDestination.EPG -> CategoryType.LIVE_TV // Not used for EPG
        is HomeDestination.Favorites -> CategoryType.LIVE_TV // Not used for Favorites
        is HomeDestination.Search -> CategoryType.LIVE_TV // Not used for Search
        is HomeDestination.Settings -> CategoryType.LIVE_TV // Not used for Settings
    }
    
    // Select first category when destination changes or categories are loaded
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
        !(hiddenCategories[categoryType]?.contains(it.categoryId) ?: false)
    }
    
    // Sort categories the same way as in CategoryRow for consistency
    val sortedCategories = filteredCategories.sortedBy { it.categoryName }
    
    LaunchedEffect(selectedDestination, sortedCategories) {
        if (sortedCategories.isEmpty()) {
            selectedCategoryId = null
        } else {
            // Always select first category if none selected or selected category doesn't exist in current list
            val currentCategoryExists = selectedCategoryId != null && 
                sortedCategories.any { it.categoryId == selectedCategoryId }
            if (!currentCategoryExists) {
                selectedCategoryId = sortedCategories.first().categoryId
            }
        }
    }
    
    // Handle category hiding callback
    fun handleCategoryHidden(category: Category) {
        // Hide the category
        categoryVisibilityService.hideCategory(category.categoryId, categoryType)
        
        // If the hidden category was currently selected, select the first available category
        if (selectedCategoryId == category.categoryId) {
            val remainingCategories = sortedCategories.filter { it.categoryId != category.categoryId }
            selectedCategoryId = remainingCategories.firstOrNull()?.categoryId
        }
        
        // Show snackbar feedback
        snackbarMessage = "Kategorie wurde ausgeblendet"
    }
    
    // Show snackbar when message is set
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            snackbarMessage = null
        }
    }
    
    val showCategories = selectedDestination is HomeDestination.LiveTV ||
            selectedDestination is HomeDestination.VOD ||
            selectedDestination is HomeDestination.Series
    
    // Focus navigation functions (defined before LaunchedEffect to be available in closures)
    fun moveFocusToNavbar() {
        Log.i("TVFocus", "$LOG_TAG_MAIN_CONTENT_SCREEN 🎯 Requesting focus: NAVBAR")
        try {
            navbarFocusRequester.requestFocus()
        } catch (e: IllegalStateException) {
            Log.w("TVFocus", "$LOG_TAG_MAIN_CONTENT_SCREEN ⚠️ Navbar FocusRequester not initialized yet")
        }
    }
    
    fun moveFocusToCategorySidebar() {
        Log.i("TVFocus", "$LOG_TAG_MAIN_CONTENT_SCREEN 🎯 Requesting focus: CATEGORY_SIDEBAR")
        try {
            categorySidebarFocusRequester.requestFocus()
        } catch (e: IllegalStateException) {
            Log.w("TVFocus", "$LOG_TAG_MAIN_CONTENT_SCREEN ⚠️ CategorySidebar FocusRequester not initialized yet")
        }
    }
    
    fun moveFocusToContentList() {
        Log.i("TVFocus", "$LOG_TAG_MAIN_CONTENT_SCREEN 🎯 Requesting focus: CONTENT_LIST")
        try {
            contentListFocusRequester.requestFocus()
        } catch (e: IllegalStateException) {
            // FocusRequester not yet initialized - this can happen if called before content is loaded
            // The content will request focus automatically when streams are loaded
            Log.w("TVFocus", "$LOG_TAG_MAIN_CONTENT_SCREEN ⚠️ ContentList FocusRequester not initialized yet, will focus when content loads")
        }
    }
    
    // Track if initial focus has been set
    var initialFocusSet by remember { mutableStateOf(false) }
    var pendingFocusDestination by remember { mutableStateOf<HomeDestination?>(null) }
    var categorySidebarFocusSet by remember { mutableStateOf(false) }
    var contentListFocusSet by remember { mutableStateOf(false) }
    
    // State flags to trigger focus requests after components are ready
    var navbarReady by remember { mutableStateOf(false) }
    var categorySidebarReady by remember { mutableStateOf(false) }
    var contentListReady by remember { mutableStateOf(false) }
    
    // When destination changes, track which focus should be requested and reset flags
    LaunchedEffect(selectedDestination) {
        pendingFocusDestination = selectedDestination
        categorySidebarFocusSet = false
        contentListFocusSet = false
        categorySidebarReady = false
        contentListReady = false
    }
    
    // Set initial focus on Navbar when ready
    LaunchedEffect(navbarReady) {
        if (navbarReady && !initialFocusSet) {
            initialFocusSet = true
            navbarFocusRequester.safeRequestFocus(LOG_TAG_MAIN_CONTENT_SCREEN, "NAVBAR")
        }
    }
    
    // Set focus on CategorySidebar when ready and conditions are met
    LaunchedEffect(categorySidebarReady, pendingFocusDestination, showCategories, sortedCategories.size) {
        if (categorySidebarReady && 
            !categorySidebarFocusSet &&
            pendingFocusDestination != null && 
            pendingFocusDestination !is HomeDestination.Favorites &&
            showCategories && sortedCategories.isNotEmpty()) {
            categorySidebarFocusSet = true
            categorySidebarFocusRequester.safeRequestFocus(LOG_TAG_MAIN_CONTENT_SCREEN, "CATEGORY_SIDEBAR")
        }
    }
    
    // Set focus on ContentList when ready and conditions are met
    LaunchedEffect(contentListReady, pendingFocusDestination) {
        if (contentListReady && 
            !contentListFocusSet &&
            pendingFocusDestination is HomeDestination.Favorites) {
            contentListFocusSet = true
            contentListFocusRequester.safeRequestFocus(LOG_TAG_MAIN_CONTENT_SCREEN, "CONTENT_LIST")
        }
    }
    
    // Set initial focus on Navbar when app starts (via onNavbarReady callback)
    // When destination changes and categories are shown, move focus to CategoryRow
    // For Favorites (no categories), move focus directly to content list
    
    // Update Dialog
    val currentUpdateInfo = updateInfo
    val currentDownloadProgress = downloadProgress
    if (showUpdateDialog && currentUpdateInfo != null) {
        UpdateDialog(
            updateInfo = currentUpdateInfo,
            currentVersion = currentVersionFromViewModel,
            downloadProgress = currentDownloadProgress,
            isDownloading = currentDownloadProgress != null && !currentDownloadProgress.isComplete && currentDownloadProgress.error == null,
            onUpdateNow = {
                currentUpdateInfo.downloadUrl?.let { url ->
                    currentUpdateInfo.stableVersion?.let { version ->
                        updateViewModel.downloadUpdate(url, version)
                    }
                }
            },
            onLater = {
                showUpdateDialog = false
            }
        )
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        MainLayout(
            selectedDestination = selectedDestination,
            onDestinationChange = { destination ->
                if (destination is HomeDestination.Settings) {
                    // Navigate to separate Settings screen
                    navController.navigate(Screen.Settings.route)
                } else {
                    // Reset category selection and clear streams when switching tabs
                    selectedCategoryId = null
                    // Clear streams from old destination
                    clearStreamsForDestination(selectedDestination, mainViewModel)
                    // Clear streams from new destination to prevent showing old data
                    clearStreamsForDestination(destination, mainViewModel)
                    selectedDestination = destination
                }
            },
            showCategories = showCategories,
            categories = sortedCategories,
            selectedCategoryId = selectedCategoryId,
            onCategorySelected = { selectedCategoryId = it },
            navbarFocusRequester = navbarFocusRequester,
            categorySidebarFocusRequester = categorySidebarFocusRequester,
            contentListFocusRequester = contentListFocusRequester,
            onNavigateToNavbar = { moveFocusToNavbar() },
            onNavigateToCategorySidebar = { moveFocusToCategorySidebar() },
            onNavigateToContentList = { moveFocusToContentList() },
            categoryVisibilityService = categoryVisibilityService,
            categoryType = categoryType,
            onCategoryHidden = { category -> 
                Log.d("AppNavigation", "[CategoryHidden] Callback aufgerufen für Kategorie: ${category.categoryName} (${category.categoryId})")
                handleCategoryHidden(category) 
            },
            onSearchClick = {
                showSearchOverlay = true
            },
        onNavbarReady = {
            // Signal that navbar is ready for focus
            navbarReady = true
        },
        onCategorySidebarReady = {
            // Signal that category sidebar is ready for focus
            categorySidebarReady = true
        },
        onContentListReady = {
            // Signal that content list is ready for focus
            contentListReady = true
        },
        content = {
            when (selectedDestination) {
                is HomeDestination.LiveTV -> {
                    LiveTVContent(
                        viewModel = mainViewModel,
                        selectedCategoryId = selectedCategoryId,
                        focusRequester = contentListFocusRequester,
                        favoritesRepository = favoritesRepository,
                        onNavigateLeft = { moveFocusToCategorySidebar() },
                        onChannelClick = { stream ->
                            val creds = credentials ?: return@LiveTVContent
                            val streamUrl = stream.getStreamUrl(
                                creds.serverUrl,
                                creds.username,
                                creds.password
                            )
                            val route = Screen.Player.createRouteForLive(streamUrl)
                            navController.logAndNavigate(route, "AppNavigation") {
                                "📺 LiveTV onClick - stream.name: ${stream.name}, streamUrl: $streamUrl, route: $route"
                            }
                        }
                    )
                }
                is HomeDestination.VOD -> {
                    VODContent(
                        viewModel = mainViewModel,
                        selectedCategoryId = selectedCategoryId,
                        focusRequester = contentListFocusRequester,
                        favoritesRepository = favoritesRepository,
                        onNavigateLeft = { moveFocusToCategorySidebar() },
                        onVodClick = { vod ->
                            val creds = credentials ?: return@VODContent
                            val streamUrl = vod.getStreamUrl(
                                creds.serverUrl,
                                creds.username,
                                creds.password
                            )
                            val route = Screen.Player.createRouteForMovie(streamUrl, vod.streamId, vod.name)
                            navController.logAndNavigate(route, "AppNavigation") {
                                "🎬 VOD onClick - vod.name: ${vod.name}, vod.streamId: ${vod.streamId}, streamUrl: $streamUrl, route: $route"
                            }
                        }
                    )
                }
                is HomeDestination.Series -> {
                    SeriesContent(
                        params = SeriesContentParams(
                            viewModel = mainViewModel,
                            selectedCategoryId = selectedCategoryId,
                            focusRequester = contentListFocusRequester,
                            favoritesRepository = favoritesRepository,
                            onNavigateLeft = { moveFocusToCategorySidebar() },
                            onSeriesClick = { series ->
                                val route = Screen.SeriesEpisodes.createRoute(series.seriesId)
                                navController.logAndNavigate(route, "AppNavigation") {
                                    "📺 Series click - series.name: ${series.name}, seriesId: ${series.seriesId}, route: $route"
                                }
                            }
                        )
                    )
                }
                is HomeDestination.EPG -> {
                    EPGContent(
                        viewModel = mainViewModel,
                        onProgramClick = { _, stream ->
                            val creds = credentials ?: return@EPGContent
                            val streamUrl = stream.getStreamUrl(
                                creds.serverUrl,
                                creds.username,
                                creds.password
                            )
                            val route = Screen.Player.createRouteForLive(streamUrl)
                            navController.logAndNavigate(route, "AppNavigation") {
                                "📅 EPG onClick - stream.name: ${stream.name}, streamUrl: $streamUrl, route: $route"
                            }
                        }
                    )
                }
                is HomeDestination.Favorites -> {
                    val onLiveStreamClick: (LiveStream) -> Unit = { stream ->
                        val creds = credentials
                        if (creds != null) {
                            val streamUrl = stream.getStreamUrl(
                                creds.serverUrl,
                                creds.username,
                                creds.password
                            )
                            val route = Screen.Player.createRouteForLive(streamUrl)
                            navController.logAndNavigate(route, "AppNavigation") {
                                "⭐ Favorites LiveTV onClick - stream.name: ${stream.name}, streamUrl: $streamUrl, route: $route"
                            }
                        }
                    }
                    val onVodClick: (VodStream) -> Unit = { vod ->
                        val creds = credentials
                        if (creds != null) {
                            val streamUrl = vod.getStreamUrl(
                                creds.serverUrl,
                                creds.username,
                                creds.password
                            )
                            val route = Screen.Player.createRouteForMovie(streamUrl, vod.streamId, vod.name)
                            navController.logAndNavigate(route, "AppNavigation") {
                                "⭐ Favorites VOD onClick - vod.name: ${vod.name}, vod.streamId: ${vod.streamId}, streamUrl: $streamUrl, route: $route"
                            }
                        }
                    }
                    val onSeriesClick: (Series) -> Unit = { series ->
                        // Navigate to Series tab
                        selectedDestination = HomeDestination.Series
                        // Use ViewModel to handle business logic
                        val categoryId = mainViewModel.showSeriesFromFavorites(series)
                        if (categoryId != null) {
                            selectedCategoryId = categoryId
                        }
                    }
                    FavoritesContent(
                        params = FavoritesContentParams(
                            favoritesRepository = favoritesRepository,
                            cacheService = cacheService,
                            focusRequester = contentListFocusRequester,
                            onNavigateLeft = { moveFocusToNavbar() },
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
        )
        
        // Snackbar for feedback messages
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
        )
        
        // Search Overlay (rendered last to be on top)
        if (showSearchOverlay) {
            SearchOverlay(
                searchState = searchState,
                onQueryChange = { query ->
                    mainViewModel.searchContent(query)
                },
                onSearchImmediate = {
                    mainViewModel.searchContentImmediate()
                },
                onDismiss = {
                    mainViewModel.clearSearch()
                    showSearchOverlay = false
                    // Return focus to search button
                    moveFocusToNavbar()
                },
                onLiveStreamClick = { stream ->
                    val creds = credentials ?: return@SearchOverlay
                    val streamUrl = stream.getStreamUrl(
                        creds.serverUrl,
                        creds.username,
                        creds.password
                    )
                    val route = Screen.Player.createRouteForLive(streamUrl)
                    mainViewModel.clearSearch()
                    showSearchOverlay = false
                    navController.logAndNavigate(route, "AppNavigation") {
                        "🔍 Search LiveTV onClick - stream.name: ${stream.name}, streamUrl: $streamUrl, route: $route"
                    }
                },
                onVodClick = { vod ->
                    val creds = credentials ?: return@SearchOverlay
                    val streamUrl = vod.getStreamUrl(
                        creds.serverUrl,
                        creds.username,
                        creds.password
                    )
                    val route = Screen.Player.createRouteForMovie(streamUrl, vod.streamId, vod.name)
                    mainViewModel.clearSearch()
                    showSearchOverlay = false
                    navController.logAndNavigate(route, "AppNavigation") {
                        "🔍 Search VOD onClick - vod.name: ${vod.name}, vod.streamId: ${vod.streamId}, streamUrl: $streamUrl, route: $route"
                    }
                },
                onSeriesClick = { series ->
                    val route = Screen.SeriesEpisodes.createRoute(series.seriesId)
                    mainViewModel.clearSearch()
                    showSearchOverlay = false
                    navController.logAndNavigate(route, "AppNavigation") {
                        "🔍 Search Series onClick - series.name: ${series.name}, seriesId: ${series.seriesId}, route: $route"
                    }
                }
            )
        }
    }
}


