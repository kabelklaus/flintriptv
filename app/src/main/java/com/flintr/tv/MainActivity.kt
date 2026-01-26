package com.flintr.tv

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.flintr.tv.data.local.CategoryVisibilityService
import com.flintr.tv.data.local.CredentialsRepository
import com.flintr.tv.data.local.DataCacheService
import com.flintr.tv.data.local.FavoritesRepository
import com.flintr.tv.data.local.WatchProgressService
import com.flintr.tv.data.repository.XtreamRepository
import com.flintr.tv.ui.navigation.AppNavigation
import com.flintr.tv.ui.navigation.AppNavigationDependencies
import com.flintr.tv.ui.navigation.handleDeepLink
import com.flintr.tv.ui.theme.FlintRTVTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject
import javax.inject.Named

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }
    @Inject
    lateinit var credentialsRepository: CredentialsRepository

    @Inject
    lateinit var cacheService: DataCacheService

    @Inject
    lateinit var favoritesRepository: FavoritesRepository

    @Inject
    lateinit var watchProgressService: WatchProgressService

    @Inject
    lateinit var categoryVisibilityService: CategoryVisibilityService

    @Inject
    lateinit var xtreamRepository: XtreamRepository

    @Inject
    @Named("IO")
    lateinit var ioDispatcher: CoroutineDispatcher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FlintRTVTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val credentials = remember { credentialsRepository.getCredentials() }
                    
                    // Handle deep link from intent
                    DeepLinkHandler(
                        navController = navController,
                        intent = intent,
                        credentials = credentials
                    )
                    
                    AppNavigation(
                        navController = navController,
                        dependencies = AppNavigationDependencies(
                            credentialsRepository = credentialsRepository,
                            cacheService = cacheService,
                            favoritesRepository = favoritesRepository,
                            watchProgressService = watchProgressService,
                            categoryVisibilityService = categoryVisibilityService,
                            xtreamRepository = xtreamRepository,
                            ioDispatcher = ioDispatcher
                        )
                    )
                }
            }
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Deep link will be handled by DeepLinkHandler LaunchedEffect
    }
}

@Composable
private fun DeepLinkHandler(
    navController: androidx.navigation.NavHostController,
    intent: Intent,
    credentials: com.flintr.tv.data.local.Credentials?
) {
    LaunchedEffect(intent) {
        if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
            val uri = intent.data ?: return@LaunchedEffect
            if (uri.scheme == "flintrtv" && uri.host == "play") {
                Log.d("MainActivity", "Handling deep link: $uri")
                val route = handleDeepLink(uri, credentials)
                if (route != null) {
                    navController.navigate(route) {
                        // Clear back stack to start fresh
                        popUpTo(0) { inclusive = false }
                    }
                } else {
                    Log.w("MainActivity", "Failed to handle deep link: $uri")
                    // Fallback: navigate to main screen
                    navController.navigate("main") {
                        popUpTo(0) { inclusive = false }
                    }
                }
            }
        }
    }
}
