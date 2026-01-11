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
import com.flintr.tv.data.local.CredentialsRepository
import com.flintr.tv.data.local.DataCacheService
import com.flintr.tv.data.local.FavoritesRepository
import com.flintr.tv.data.local.WatchProgressService
import com.flintr.tv.data.model.Episode
import com.flintr.tv.data.model.EpgProgram
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
import com.flintr.tv.ui.tabs.FavoritesContent
import com.flintr.tv.ui.tabs.FavoritesContentParams
import com.flintr.tv.viewmodel.MainViewModel
import com.flintr.tv.viewmodel.PlayerViewModel
import com.flintr.tv.ui.utils.safeRequestFocus

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

@Composable
fun AppNavigation(
    navController: NavHostController,
    credentialsRepository: CredentialsRepository,
    cacheService: DataCacheService,
    favoritesRepository: FavoritesRepository,
    watchProgressService: WatchProgressService,
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
                credentialsRepository = credentialsRepository,
                cacheService = cacheService
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
                onLoadingComplete = { cachedData ->
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
                credentialsRepository = credentialsRepository,
                favoritesRepository = favoritesRepository,
                cacheService = cacheService
            )
        }
        
        composable(Screen.Settings.route) {
            SettingsScreen(
                focusRequester = remember { FocusRequester() },
                onNavigateLeft = { navController.popBackStack() }
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
            
            // Debug logs to track navigation arguments
            if (BuildConfig.DEBUG) {
                android.util.Log.d("AppNavigation", "🔗 Navigation to PlayerScreen")
                android.util.Log.d("AppNavigation", "🔗 Raw streamUrl from args: $streamUrlRaw")
                android.util.Log.d("AppNavigation", "🔗 Decoded streamUrl: $streamUrl")
                android.util.Log.d("AppNavigation", "🔗 contentType: $contentType, streamId: $streamId, seriesId: $seriesId, episodeId: $episodeId, title: $title")
            }
            
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
                watchProgressService = watchProgressService,
                onBack = { navController.popBackStack() }
            )
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
    cacheService: DataCacheService
) {
    val credentials = credentialsRepository.getCredentials()
    var selectedDestination by remember { mutableStateOf<HomeDestination>(HomeDestination.LiveTV) }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    
    // Focus requesters for each panel
    val navbarFocusRequester = remember { FocusRequester() }
    val categorySidebarFocusRequester = remember { FocusRequester() }
    val contentListFocusRequester = remember { FocusRequester() }
    
    val liveCategories by mainViewModel.liveCategories.collectAsState()
    val vodCategories by mainViewModel.vodCategories.collectAsState()
    val seriesCategories by mainViewModel.seriesCategories.collectAsState()
    
    // Select first category when destination changes or categories are loaded
    val categories = when (selectedDestination) {
        is HomeDestination.LiveTV -> liveCategories
        is HomeDestination.VOD -> vodCategories
        is HomeDestination.Series -> seriesCategories
        is HomeDestination.EPG -> emptyList()
        is HomeDestination.Favorites -> emptyList() // Favorites doesn't have categories
        is HomeDestination.Settings -> emptyList() // Settings navigates to separate screen
    }
    
    // Sort categories the same way as in CategoryRow for consistency
    val sortedCategories = categories.sortedBy { it.categoryName }
    
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
        categories = categories,
        selectedCategoryId = selectedCategoryId,
        onCategorySelected = { selectedCategoryId = it },
        navbarFocusRequester = navbarFocusRequester,
        categorySidebarFocusRequester = categorySidebarFocusRequester,
        contentListFocusRequester = contentListFocusRequester,
        onNavigateToNavbar = { moveFocusToNavbar() },
        onNavigateToCategorySidebar = { moveFocusToCategorySidebar() },
        onNavigateToContentList = { moveFocusToContentList() },
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
                        viewModel = mainViewModel,
                        selectedCategoryId = selectedCategoryId,
                        focusRequester = contentListFocusRequester,
                        favoritesRepository = favoritesRepository,
                        onNavigateLeft = { moveFocusToCategorySidebar() },
                        onEpisodeClick = { episode, series ->
                            val creds = credentials ?: return@SeriesContent
                            val streamUrl = episode.getStreamUrl(
                                creds.serverUrl,
                                creds.username,
                                creds.password,
                                series.seriesId
                            )
                            val route = Screen.Player.createRouteForSeries(streamUrl, series.seriesId, episode.id, episode.title)
                            navController.logAndNavigate(route, "AppNavigation") {
                                "📺 Series onClick - episode.title: ${episode.title}, seriesId: ${series.seriesId}, episodeId: ${episode.id}, streamUrl: $streamUrl, route: $route"
                            }
                        }
                    )
                }
                is HomeDestination.EPG -> {
                    EPGContent(
                        viewModel = mainViewModel,
                        onProgramClick = { program, stream ->
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
                is HomeDestination.Settings -> {
                    // Settings navigates to separate screen, this should not be reached
                    // but kept for exhaustiveness
                    Box {}
                }
            }
        }
    )
}

