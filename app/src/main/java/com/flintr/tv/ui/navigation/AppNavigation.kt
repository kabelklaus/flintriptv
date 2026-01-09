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
import android.util.Log
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.flintr.tv.data.local.CredentialsRepository
import com.flintr.tv.data.local.DataCacheService
import com.flintr.tv.data.model.Episode
import com.flintr.tv.data.model.EpgProgram
import com.flintr.tv.data.model.LiveStream
import com.flintr.tv.data.model.Series
import com.flintr.tv.ui.components.MainLayout
import com.flintr.tv.ui.screens.DataLoadingScreen
import com.flintr.tv.ui.screens.EPGContent
import com.flintr.tv.ui.screens.HomeDestination
import com.flintr.tv.ui.screens.LoginScreen
import com.flintr.tv.ui.screens.PlayerScreen
import com.flintr.tv.ui.settings.SettingsScreen
import com.flintr.tv.ui.tabs.LiveTVContent
import com.flintr.tv.ui.tabs.VODContent
import com.flintr.tv.ui.tabs.SeriesContent
import com.flintr.tv.viewmodel.MainViewModel
import com.flintr.tv.viewmodel.PlayerViewModel

sealed class Screen(val route: String) {
    object InitialRouting : Screen("initial_routing")
    object Login : Screen("login")
    object DataLoading : Screen("data_loading")
    object Main : Screen("main")
    object Settings : Screen("settings")
    object Player : Screen("player/{streamUrl}") {
        fun createRoute(streamUrl: String): String {
            val encoded = java.net.URLEncoder.encode(streamUrl, "UTF-8")
            return "player/$encoded"
        }
    }
}

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
                credentialsRepository = credentialsRepository
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
                }
            )
        ) { backStackEntry ->
            val streamUrl = backStackEntry.arguments?.getString("streamUrl") ?: ""
            PlayerScreen(
                streamUrl = streamUrl,
                playerViewModel = playerViewModel,
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
        is HomeDestination.Settings -> {
            // Settings navigates to separate screen, no streams to clear
        }
    }
}

@Composable
fun MainContentScreen(
    navController: NavHostController,
    mainViewModel: MainViewModel,
    credentialsRepository: CredentialsRepository
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
    
    // Set initial focus on Navbar when app starts
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(200) // Small delay to ensure UI is laid out
        try {
            navbarFocusRequester.requestFocus()
            Log.i("TVFocus", "[MainContentScreen] 🎯 Initial focus set to NAVBAR")
        } catch (e: IllegalStateException) {
            Log.w("TVFocus", "[MainContentScreen] ⚠️ Navbar FocusRequester not initialized yet")
        }
    }
    
    // Focus navigation functions
    fun moveFocusToNavbar() {
        Log.i("TVFocus", "[MainContentScreen] 🎯 Requesting focus: NAVBAR")
        try {
            navbarFocusRequester.requestFocus()
        } catch (e: IllegalStateException) {
            Log.w("TVFocus", "[MainContentScreen] ⚠️ Navbar FocusRequester not initialized yet")
        }
    }
    
    fun moveFocusToCategorySidebar() {
        Log.i("TVFocus", "[MainContentScreen] 🎯 Requesting focus: CATEGORY_SIDEBAR")
        try {
            categorySidebarFocusRequester.requestFocus()
        } catch (e: IllegalStateException) {
            Log.w("TVFocus", "[MainContentScreen] ⚠️ CategorySidebar FocusRequester not initialized yet")
        }
    }
    
    fun moveFocusToContentList() {
        Log.i("TVFocus", "[MainContentScreen] 🎯 Requesting focus: CONTENT_LIST")
        try {
            contentListFocusRequester.requestFocus()
        } catch (e: IllegalStateException) {
            // FocusRequester not yet initialized - this can happen if called before content is loaded
            // The content will request focus automatically when streams are loaded
            Log.w("TVFocus", "[MainContentScreen] ⚠️ ContentList FocusRequester not initialized yet, will focus when content loads")
        }
    }
    
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
        content = {
            when (selectedDestination) {
                is HomeDestination.LiveTV -> {
                    LiveTVContent(
                        viewModel = mainViewModel,
                        selectedCategoryId = selectedCategoryId,
                        focusRequester = contentListFocusRequester,
                        onNavigateLeft = { moveFocusToCategorySidebar() },
                        onChannelClick = { stream ->
                            val creds = credentials ?: return@LiveTVContent
                            val streamUrl = stream.getStreamUrl(
                                creds.serverUrl,
                                creds.username,
                                creds.password
                            )
                            navController.navigate(Screen.Player.createRoute(streamUrl))
                        }
                    )
                }
                is HomeDestination.VOD -> {
                    VODContent(
                        viewModel = mainViewModel,
                        selectedCategoryId = selectedCategoryId,
                        focusRequester = contentListFocusRequester,
                        onNavigateLeft = { moveFocusToCategorySidebar() },
                        onVodClick = { vod ->
                            val creds = credentials ?: return@VODContent
                            val streamUrl = vod.getStreamUrl(
                                creds.serverUrl,
                                creds.username,
                                creds.password
                            )
                            navController.navigate(Screen.Player.createRoute(streamUrl))
                        }
                    )
                }
                is HomeDestination.Series -> {
                    SeriesContent(
                        viewModel = mainViewModel,
                        selectedCategoryId = selectedCategoryId,
                        focusRequester = contentListFocusRequester,
                        onNavigateLeft = { moveFocusToCategorySidebar() },
                        onEpisodeClick = { episode, series ->
                            val creds = credentials ?: return@SeriesContent
                            val streamUrl = episode.getStreamUrl(
                                creds.serverUrl,
                                creds.username,
                                creds.password,
                                series.seriesId
                            )
                            navController.navigate(Screen.Player.createRoute(streamUrl))
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
                            navController.navigate(Screen.Player.createRoute(streamUrl))
                        }
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

