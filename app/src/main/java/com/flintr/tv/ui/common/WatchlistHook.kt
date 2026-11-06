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
import com.flintr.tv.data.local.WatchlistRepository
import com.flintr.tv.data.model.WatchlistEntry
import com.flintr.tv.data.model.WatchlistEntryType
import com.flintr.tv.data.model.FriendEntry
import com.flintr.tv.data.repository.UserRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import com.flintr.tv.utils.error.reportError

private const val TAG = "FavoritesHook"

data class WatchlistState<T>(
    val items: List<T>,
    val isLoading: Boolean
)

data class WatchlistEntrySource(
    val isOwn: Boolean,
    val username: String? = null,
    val alias: String? = null,
    val badge: String? = null
) {
    val key: String
        get() = if (isOwn) "own" else "friend_${username ?: "unknown"}"

    companion object {
        fun own(): WatchlistEntrySource = WatchlistEntrySource(isOwn = true)

        fun friend(entry: FriendEntry): WatchlistEntrySource {
            return WatchlistEntrySource(
                isOwn = false,
                username = entry.username,
                alias = entry.alias,
                badge = buildFriendBadge(entry.alias, entry.username)
            )
        }
    }
}

data class SourcedWatchlistItem<T>(
    val item: T,
    val source: WatchlistEntrySource,
    val allowWatchlistActions: Boolean
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

internal fun filterWatchlistByType(
    entries: List<WatchlistEntry>,
    watchlistEntryType: WatchlistEntryType
): List<WatchlistEntry> {
    return entries.filter { it.type == watchlistEntryType }
}

internal fun <T> asOwnSourcedItems(items: List<T>): List<SourcedWatchlistItem<T>> {
    return items.map { item ->
        SourcedWatchlistItem(
            item = item,
            source = WatchlistEntrySource.own(),
            allowWatchlistActions = true
        )
    }
}

internal fun <T> combineSourcedItems(
    ownItems: List<T>,
    friendItems: List<Pair<FriendEntry, List<T>>>
): List<SourcedWatchlistItem<T>> {
    val sourcedOwnItems = asOwnSourcedItems(ownItems)
    val sourcedFriendItems = friendItems.flatMap { (friend, items) ->
        val source = WatchlistEntrySource.friend(friend)
        items.map { item ->
            SourcedWatchlistItem(
                item = item,
                source = source,
                allowWatchlistActions = false
            )
        }
    }

    return sourcedOwnItems + sourcedFriendItems
}

private suspend fun <T : Any> loadWatchlistItems(
    entries: List<WatchlistEntry>,
    cacheService: DataCacheService,
    watchlistEntryType: WatchlistEntryType,
    idSelector: (T) -> Int
): List<T> {
    if (entries.isEmpty()) {
        return emptyList()
    }

    val streamIds = entries.mapNotNull { it.streamId }
    val items = when (watchlistEntryType) {
        WatchlistEntryType.LIVE -> {
            val channels = cacheService.loadChannelsByIds(streamIds)
            @Suppress("UNCHECKED_CAST")
            (channels ?: emptyList()) as List<T>
        }
        WatchlistEntryType.MOVIE -> {
            val movies = cacheService.loadMoviesByIds(streamIds)
            @Suppress("UNCHECKED_CAST")
            (movies ?: emptyList()) as List<T>
        }
        WatchlistEntryType.SERIES -> {
            val series = cacheService.loadSeriesByIds(streamIds)
            @Suppress("UNCHECKED_CAST")
            (series ?: emptyList()) as List<T>
        }
    }

    if (items.isEmpty()) {
        Log.w(TAG, "No cached data available for favorites of type: $watchlistEntryType")
        return emptyList()
    }

    val itemsMap = items.associateBy(idSelector)
    return entries.mapNotNull { entry ->
        itemsMap[entry.streamId]
    }
}

private suspend fun <T : Any> loadFriendWatchlistItems(
    currentUsername: String,
    userRepository: UserRepository,
    cacheService: DataCacheService,
    watchlistEntryType: WatchlistEntryType,
    idSelector: (T) -> Int
): List<Pair<FriendEntry, List<T>>> {
    val friends = userRepository.loadFriends(currentUsername).getOrElse { error ->
        reportError(TAG, "Failed to load friends for favorites", error)
        return emptyList()
    }

    return coroutineScope {
        friends.map { friend ->
            async {
                loadSingleFriendWatchlistItems(
                    friend = friend,
                    userRepository = userRepository,
                    cacheService = cacheService,
                    watchlistEntryType = watchlistEntryType,
                    idSelector = idSelector
                )
            }
        }.awaitAll().filterNotNull()
    }
}

private suspend fun <T : Any> loadSingleFriendWatchlistItems(
    friend: FriendEntry,
    userRepository: UserRepository,
    cacheService: DataCacheService,
    watchlistEntryType: WatchlistEntryType,
    idSelector: (T) -> Int
): Pair<FriendEntry, List<T>>? {
    val entries = userRepository.loadWatchlist(friend.username).getOrElse { error ->
        reportError(TAG, "Failed to load favorites for friend: ${friend.username}", error)
        return null
    }

    val filteredEntries = filterWatchlistByType(entries, watchlistEntryType)
    if (filteredEntries.isEmpty()) {
        return null
    }

    val items = loadWatchlistItems(
        entries = filteredEntries,
        cacheService = cacheService,
        watchlistEntryType = watchlistEntryType,
        idSelector = idSelector
    )

    if (items.isEmpty()) {
        return null
    }

    return friend to items
}

@Composable
fun <T : Any> useWatchlist(
    isWatchlistCategory: Boolean,
    watchlistRepository: WatchlistRepository,
    userRepository: UserRepository,
    credentialsRepository: CredentialsRepository,
    cacheService: DataCacheService,
    watchlistEntryType: WatchlistEntryType,
    idSelector: (T) -> Int
): WatchlistState<SourcedWatchlistItem<T>> {
    var ownItems by remember { mutableStateOf<List<T>>(emptyList()) }
    var friendItems by remember { mutableStateOf<List<Pair<FriendEntry, List<T>>>>(emptyList()) }
    var isLoadingOwnWatchlist by remember { mutableStateOf(false) }
    var isLoadingFriendWatchlists by remember { mutableStateOf(false) }
    val watchlistItems by remember(ownItems, friendItems) {
        derivedStateOf {
            combineSourcedItems(
                ownItems = ownItems,
                friendItems = friendItems
            )
        }
    }

    LaunchedEffect(isWatchlistCategory, watchlistEntryType) {
        if (!isWatchlistCategory) {
            ownItems = emptyList()
            friendItems = emptyList()
            isLoadingOwnWatchlist = false
            return@LaunchedEffect
        }

        watchlistRepository.syncIfNeeded()

        watchlistRepository.entries.collect { allEntries ->
            isLoadingOwnWatchlist = true
            ownItems = loadWatchlistItems(
                entries = filterWatchlistByType(allEntries, watchlistEntryType),
                watchlistEntryType = watchlistEntryType,
                cacheService = cacheService,
                idSelector = idSelector
            )
            isLoadingOwnWatchlist = false
        }
    }

    LaunchedEffect(isWatchlistCategory, watchlistEntryType) {
        if (!isWatchlistCategory) {
            friendItems = emptyList()
            isLoadingFriendWatchlists = false
            return@LaunchedEffect
        }

        val currentUsername = credentialsRepository.getCredentials()?.username
        isLoadingFriendWatchlists = true
        friendItems = if (currentUsername.isNullOrBlank()) {
            emptyList()
        } else {
            loadFriendWatchlistItems(
                currentUsername = currentUsername,
                userRepository = userRepository,
                cacheService = cacheService,
                watchlistEntryType = watchlistEntryType,
                idSelector = idSelector
            )
        }
        isLoadingFriendWatchlists = false
    }

    return WatchlistState(
        items = watchlistItems,
        isLoading = isLoadingOwnWatchlist || isLoadingFriendWatchlists
    )
}
