package com.flintr.tv.ui.common

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.flintr.tv.data.local.CredentialsRepository
import com.flintr.tv.data.local.DataCacheService
import com.flintr.tv.data.local.FavoritesRepository
import com.flintr.tv.data.model.Favorite
import com.flintr.tv.data.model.FavoriteType
import com.flintr.tv.data.model.FriendEntry
import com.flintr.tv.data.repository.UserRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import com.flintr.tv.utils.error.reportError

private const val TAG = "FavoritesHook"

data class FavoritesState<T>(
    val items: List<T>,
    val isLoading: Boolean
)

data class FavoriteSource(
    val isOwn: Boolean,
    val username: String? = null,
    val alias: String? = null,
    val badge: String? = null
) {
    val key: String
        get() = if (isOwn) "own" else "friend_${username ?: "unknown"}"

    companion object {
        fun own(): FavoriteSource = FavoriteSource(isOwn = true)

        fun friend(entry: FriendEntry): FavoriteSource {
            return FavoriteSource(
                isOwn = false,
                username = entry.username,
                alias = entry.alias,
                badge = buildFriendBadge(entry.alias, entry.username)
            )
        }
    }
}

data class SourcedFavoriteItem<T>(
    val item: T,
    val source: FavoriteSource,
    val allowFavoriteActions: Boolean
)

internal fun buildFriendBadge(
    alias: String?,
    username: String
): String {
    val source = alias?.trim().takeIf { !it.isNullOrBlank() } ?: username
    val alphanumeric = source.filter(Char::isLetterOrDigit)
    if (alphanumeric.isBlank()) {
        return "??"
    }

    val normalized = alphanumeric.take(2).lowercase()
    return normalized.replaceFirstChar { it.uppercase() }
}

internal fun filterFavoritesByType(
    favorites: List<Favorite>,
    favoriteType: FavoriteType
): List<Favorite> {
    return favorites.filter { it.type == favoriteType }
}

internal fun <T> asOwnSourcedItems(items: List<T>): List<SourcedFavoriteItem<T>> {
    return items.map { item ->
        SourcedFavoriteItem(
            item = item,
            source = FavoriteSource.own(),
            allowFavoriteActions = true
        )
    }
}

internal fun <T> combineSourcedItems(
    ownItems: List<T>,
    friendItems: List<Pair<FriendEntry, List<T>>>
): List<SourcedFavoriteItem<T>> {
    val sourcedOwnItems = asOwnSourcedItems(ownItems)
    val sourcedFriendItems = friendItems.flatMap { (friend, items) ->
        val source = FavoriteSource.friend(friend)
        items.map { item ->
            SourcedFavoriteItem(
                item = item,
                source = source,
                allowFavoriteActions = false
            )
        }
    }

    return sourcedOwnItems + sourcedFriendItems
}

private suspend fun <T : Any> loadFavoriteItems(
    favorites: List<Favorite>,
    cacheService: DataCacheService,
    favoriteType: FavoriteType,
    idSelector: (T) -> Int
): List<T> {
    if (favorites.isEmpty()) {
        return emptyList()
    }

    val streamIds = favorites.mapNotNull { it.streamId }
    val items = when (favoriteType) {
        FavoriteType.LIVE -> {
            val channels = cacheService.loadChannelsByIds(streamIds)
            @Suppress("UNCHECKED_CAST")
            (channels ?: emptyList()) as List<T>
        }
        FavoriteType.MOVIE -> {
            val movies = cacheService.loadMoviesByIds(streamIds)
            @Suppress("UNCHECKED_CAST")
            (movies ?: emptyList()) as List<T>
        }
        FavoriteType.SERIES -> {
            val series = cacheService.loadSeriesByIds(streamIds)
            @Suppress("UNCHECKED_CAST")
            (series ?: emptyList()) as List<T>
        }
    }

    if (items.isEmpty()) {
        Log.w(TAG, "No cached data available for favorites of type: $favoriteType")
        return emptyList()
    }

    val itemsMap = items.associateBy(idSelector)
    return favorites.mapNotNull { favorite ->
        itemsMap[favorite.streamId]
    }
}

private suspend fun <T : Any> loadFriendFavoriteItems(
    currentUsername: String,
    userRepository: UserRepository,
    cacheService: DataCacheService,
    favoriteType: FavoriteType,
    idSelector: (T) -> Int
): List<Pair<FriendEntry, List<T>>> {
    val friends = userRepository.loadFriends(currentUsername).getOrElse { error ->
        reportError(TAG, "Failed to load friends for favorites", error)
        return emptyList()
    }

    return coroutineScope {
        friends.map { friend ->
            async {
                loadSingleFriendFavoriteItems(
                    friend = friend,
                    userRepository = userRepository,
                    cacheService = cacheService,
                    favoriteType = favoriteType,
                    idSelector = idSelector
                )
            }
        }.awaitAll().filterNotNull()
    }
}

private suspend fun <T : Any> loadSingleFriendFavoriteItems(
    friend: FriendEntry,
    userRepository: UserRepository,
    cacheService: DataCacheService,
    favoriteType: FavoriteType,
    idSelector: (T) -> Int
): Pair<FriendEntry, List<T>>? {
    val favorites = userRepository.loadFavorites(friend.username).getOrElse { error ->
        reportError(TAG, "Failed to load favorites for friend: ${friend.username}", error)
        return null
    }

    val filteredFavorites = filterFavoritesByType(favorites, favoriteType)
    if (filteredFavorites.isEmpty()) {
        return null
    }

    val items = loadFavoriteItems(
        favorites = filteredFavorites,
        cacheService = cacheService,
        favoriteType = favoriteType,
        idSelector = idSelector
    )

    if (items.isEmpty()) {
        return null
    }

    return friend to items
}

@Composable
fun <T : Any> useFavorites(
    isFavoritesCategory: Boolean,
    favoritesRepository: FavoritesRepository,
    userRepository: UserRepository,
    credentialsRepository: CredentialsRepository,
    cacheService: DataCacheService,
    favoriteType: FavoriteType,
    idSelector: (T) -> Int
): FavoritesState<SourcedFavoriteItem<T>> {
    var ownItems by remember { mutableStateOf<List<T>>(emptyList()) }
    var friendItems by remember { mutableStateOf<List<Pair<FriendEntry, List<T>>>>(emptyList()) }
    var isLoadingOwnFavorites by remember { mutableStateOf(false) }
    var isLoadingFriendFavorites by remember { mutableStateOf(false) }
    val favoriteItems by remember(ownItems, friendItems) {
        derivedStateOf {
            combineSourcedItems(
                ownItems = ownItems,
                friendItems = friendItems
            )
        }
    }

    LaunchedEffect(isFavoritesCategory, favoriteType) {
        if (!isFavoritesCategory) {
            ownItems = emptyList()
            friendItems = emptyList()
            isLoadingOwnFavorites = false
            return@LaunchedEffect
        }

        favoritesRepository.syncIfNeeded()

        favoritesRepository.favorites.collect { allFavorites ->
            isLoadingOwnFavorites = true
            ownItems = loadFavoriteItems(
                favorites = filterFavoritesByType(allFavorites, favoriteType),
                favoriteType = favoriteType,
                cacheService = cacheService,
                idSelector = idSelector
            )
            isLoadingOwnFavorites = false
        }
    }

    LaunchedEffect(isFavoritesCategory, favoriteType) {
        if (!isFavoritesCategory) {
            friendItems = emptyList()
            isLoadingFriendFavorites = false
            return@LaunchedEffect
        }

        val currentUsername = credentialsRepository.getCredentials()?.username
        isLoadingFriendFavorites = true
        friendItems = if (currentUsername.isNullOrBlank()) {
            emptyList()
        } else {
            loadFriendFavoriteItems(
                currentUsername = currentUsername,
                userRepository = userRepository,
                cacheService = cacheService,
                favoriteType = favoriteType,
                idSelector = idSelector
            )
        }
        isLoadingFriendFavorites = false
    }

    return FavoritesState(
        items = favoriteItems,
        isLoading = isLoadingOwnFavorites || isLoadingFriendFavorites
    )
}
