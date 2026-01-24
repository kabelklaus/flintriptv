package com.flintr.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.flintr.tv.ui.theme.FlintRTVTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject
import javax.inject.Named

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
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
}