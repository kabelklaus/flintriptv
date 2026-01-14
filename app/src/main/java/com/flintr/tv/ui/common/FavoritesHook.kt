package com.flintr.tv.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.flintr.tv.data.local.DataCacheService
import com.flintr.tv.data.local.FavoritesRepository
import com.flintr.tv.data.model.FavoriteType
import com.flintr.tv.data.local.CachedData

private const val TAG = "FavoritesHook"

/**
 * State class for favorites loading result
 */
data class FavoritesState<T>(
    val items: List<T>,
    val isLoading: Boolean
)

/**
 * Loads favorite items from repository and maps them to content items
 */
private suspend fun <T : Any> loadFavoriteItems(
    favoritesRepository: FavoritesRepository,
    cacheService: DataCacheService,
    favoriteType: FavoriteType,
    dataSelector: (CachedData) -> List<T>,
    idSelector: (T) -> Int
): List<T> {
    val favorites = favoritesRepository.getFavoritesByType(favoriteType)
    if (favorites.isEmpty()) {
        return emptyList()
    }
    
    val cachedData = cacheService.loadCachedDataAsync()
    if (cachedData == null) {
        Log.w(TAG, "No cached data available for favorites")
        return emptyList()
    }
    
    val itemsMap = dataSelector(cachedData).associateBy(idSelector)
    return favorites.mapNotNull { favorite ->
        itemsMap[favorite.streamId]
    }
}

/**
 * Periodically checks for favorites changes and updates the key if changed
 */
private suspend fun checkFavoritesChanges(
    favoritesRepository: FavoritesRepository,
    favoriteType: FavoriteType,
    getCurrentIdsKey: () -> List<String>,
    isFavoritesCategory: Boolean,
    onIdsChanged: (List<String>) -> Unit
) {
    while (isFavoritesCategory) {
        kotlinx.coroutines.delay(500) // Check every 500ms
        
        val currentFavorites = withContext(Dispatchers.IO) {
            favoritesRepository.getFavoritesByType(favoriteType)
        }
        val currentIds = currentFavorites.map { it.id }
        val currentIdsKey = getCurrentIdsKey()
        
        // Update key if favorites changed, which will trigger reload
        if (currentIds != currentIdsKey) {
            onIdsChanged(currentIds)
        }
    }
}

/**
 * Custom hook for loading favorites of a specific type.
 * 
 * This hook centralizes the logic for:
 * - Loading favorites from repository
 * - Loading cached data
 * - Mapping favorites to their corresponding content items
 * - Managing loading state
 * 
 * @param isFavoritesCategory Whether the favorites category is currently selected
 * @param favoritesRepository Repository for accessing favorites
 * @param cacheService Service for loading cached data
 * @param favoriteType The type of favorites to load (LIVE, MOVIE, SERIES)
 * @param dataSelector Function to extract the relevant list from CachedData
 * @param idSelector Function to extract the ID from a content item for mapping
 * @return FavoritesState containing the loaded items and loading state
 */
@Composable
fun <T : Any> useFavorites(
    isFavoritesCategory: Boolean,
    favoritesRepository: FavoritesRepository,
    cacheService: DataCacheService,
    favoriteType: FavoriteType,
    dataSelector: (CachedData) -> List<T>,
    idSelector: (T) -> Int
): FavoritesState<T> {
    var favoriteItems by remember { mutableStateOf<List<T>>(emptyList()) }
    var isLoadingFavorites by remember { mutableStateOf(false) }
    
    // Track favorites IDs to detect changes
    var favoritesIdsKey by remember { mutableStateOf<List<String>>(emptyList()) }
    
    // Load favorites and use IDs as key to detect changes
    LaunchedEffect(isFavoritesCategory, favoriteType) {
        if (!isFavoritesCategory) {
            favoriteItems = emptyList()
            favoritesIdsKey = emptyList()
            return@LaunchedEffect
        }
        
        // Load favorites IDs to use as key
        val favorites = withContext(Dispatchers.IO) {
            favoritesRepository.getFavoritesByType(favoriteType)
        }
        favoritesIdsKey = favorites.map { it.id }
    }
    
    // Reload content when favorites IDs change (detected via key change)
    LaunchedEffect(isFavoritesCategory, favoritesIdsKey) {
        if (!isFavoritesCategory) {
            favoriteItems = emptyList()
            return@LaunchedEffect
        }
        
        isLoadingFavorites = true
        val items = withContext(Dispatchers.IO) {
            loadFavoriteItems(
                favoritesRepository = favoritesRepository,
                cacheService = cacheService,
                favoriteType = favoriteType,
                dataSelector = dataSelector,
                idSelector = idSelector
            )
        }
        favoriteItems = items
        isLoadingFavorites = false
    }
    
    // Periodically check for favorites changes when in favorites category
    LaunchedEffect(isFavoritesCategory, favoriteType) {
        if (!isFavoritesCategory) {
            return@LaunchedEffect
        }
        
        checkFavoritesChanges(
            favoritesRepository = favoritesRepository,
            favoriteType = favoriteType,
            getCurrentIdsKey = { favoritesIdsKey },
            isFavoritesCategory = isFavoritesCategory,
            onIdsChanged = { favoritesIdsKey = it }
        )
    }
    
    return FavoritesState(
        items = favoriteItems,
        isLoading = isLoadingFavorites
    )
}
