package com.flintr.tv.ui.tabs

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import android.util.Log
import kotlinx.coroutines.delay
import com.flintr.tv.data.local.Credentials
import com.flintr.tv.data.local.DataCacheService
import com.flintr.tv.data.local.WatchlistRepository
import com.flintr.tv.data.local.WatchProgressService
import com.flintr.tv.data.repository.XtreamRepository
import com.flintr.tv.data.model.Episode
import com.flintr.tv.data.model.Series
import com.flintr.tv.data.model.VodStream
import com.flintr.tv.data.model.WatchProgress
import com.flintr.tv.ui.components.VODCard
import com.flintr.tv.ui.components.VODCardParams
import com.flintr.tv.ui.screens.HomeDestination
import com.flintr.tv.ui.sidebars.ContentGridLayout
import com.flintr.tv.ui.sidebars.ContentGridLayoutParams
import com.flintr.tv.ui.common.UiConstants
import com.flintr.tv.utils.error.reportWarning

// Constants for in-progress content loading
private const val API_CALL_DELAY_MS = 100L
private const val MAX_IN_PROGRESS_SERIES_TO_LOAD = 10

// Sealed class to represent different in-progress content types.
// internal (not private): loadInProgressItemsCore()/countInProgressItems() expose it to
// AppNavigation.kt and tests as the single resolved-item source for both list and counter.
internal sealed class InProgressItem {
    internal data class Movie(val vod: VodStream, val progress: WatchProgress) : InProgressItem()
    internal data class EpisodeItem(val series: Series, val episode: Episode, val progress: WatchProgress) : InProgressItem()
}

/**
 * Parameters for InProgressContent composable.
 */
data class InProgressContentParams(
    val watchProgressService: WatchProgressService,
    val watchlistRepository: WatchlistRepository,
    val cacheService: DataCacheService,
    val focusRequester: FocusRequester,
    val onNavigateLeft: () -> Unit,
    val onVodClick: (VodStream) -> Unit,
    val onSeriesClick: (Series) -> Unit,
    val onEpisodeClick: (Series, Episode) -> Unit,
    val lazyGridState: LazyGridState,
    val focusRequesterMap: MutableMap<String, FocusRequester>? = null,
    val destination: HomeDestination,
    val xtreamRepository: XtreamRepository? = null,
    val credentials: Credentials? = null
)

/**
 * Creates the onNavigateLeft callback based on column index.
 */
private fun createOnNavigateLeft(
    columnIndex: Int,
    onNavigateLeft: () -> Unit
): (() -> Unit)? {
    return if (columnIndex == 0) {
        { onNavigateLeft() }
    } else {
        null
    }
}

/**
 * Data class to hold parsed progress keys.
 */
private data class ParsedProgressKeys(
    val movieStreamIds: List<Int>,
    val episodeSeriesIds: Set<Int>,
    val episodeProgressMap: Map<String, WatchProgress>
)

/**
 * Parses progress keys to extract movie stream IDs and series IDs.
 */
private fun parseProgressKeys(allProgress: Map<String, WatchProgress>): ParsedProgressKeys {
    val movieStreamIds = mutableListOf<Int>()
    val episodeSeriesIds = mutableSetOf<Int>()
    val episodeProgressMap = mutableMapOf<String, WatchProgress>()
    
    allProgress.forEach { (key, progress) ->
        when {
            key.startsWith(UiConstants.PROGRESS_KEY_MOVIE_PREFIX) -> {
                val streamId = key.removePrefix(UiConstants.PROGRESS_KEY_MOVIE_PREFIX).toIntOrNull()
                if (streamId != null) {
                    movieStreamIds.add(streamId)
                }
            }
            key.startsWith(UiConstants.PROGRESS_KEY_SERIES_PREFIX) -> {
                val parts = key.removePrefix(UiConstants.PROGRESS_KEY_SERIES_PREFIX).split(":")
                if (parts.size == 2) {
                    val seriesId = parts[0].toIntOrNull()
                    if (seriesId != null) {
                        episodeSeriesIds.add(seriesId)
                        episodeProgressMap[key] = progress
                    }
                }
            }
        }
    }
    
    return ParsedProgressKeys(movieStreamIds, episodeSeriesIds, episodeProgressMap)
}

/**
 * Creates a minimal series representation without seasons to save memory.
 */
private fun createMinimalSeries(series: Series): Series {
    return Series(
        num = series.num,
        name = series.name,
        seriesId = series.seriesId,
        cover = series.cover,
        plot = series.plot,
        cast = series.cast,
        director = series.director,
        genre = series.genre,
        releaseDate = series.releaseDate,
        rating = series.rating,
        rating5Based = series.rating5Based,
        lastModified = series.lastModified,
        categoryId = series.categoryId,
        categoryName = series.categoryName,
        youtubeTrailer = series.youtubeTrailer,
        backdropPath = series.backdropPath,
        seasons = null // Don't store seasons to save memory
    )
}

/**
 * Finds an episode in a series by episode ID.
 */
private fun findEpisodeInSeries(series: Series, episodeId: Int): Episode? {
    series.seasons?.forEach { season ->
        val episode = season.episodes?.find { it.id == episodeId }
        if (episode != null) {
            return episode
        }
    }
    return null
}

/**
 * Loads in-progress movies.
 */
private suspend fun loadInProgressMovies(
    allProgress: Map<String, WatchProgress>,
    movieStreamIds: List<Int>,
    cacheService: DataCacheService
): List<InProgressItem.Movie> {
    if (movieStreamIds.isEmpty()) {
        return emptyList()
    }
    
    val movies = cacheService.loadMoviesByIds(movieStreamIds)
    if (movies == null) {
        Log.w("InProgressContent", "Failed to load movies by IDs")
        return emptyList()
    }
    
    val moviesMap = movies.associateBy { it.streamId }
    val items = mutableListOf<InProgressItem.Movie>()
    
    allProgress.filterKeys { it.startsWith(UiConstants.PROGRESS_KEY_MOVIE_PREFIX) }.forEach { (key, progress) ->
        val streamId = key.removePrefix(UiConstants.PROGRESS_KEY_MOVIE_PREFIX).toIntOrNull()
        if (streamId != null) {
            val vod = moviesMap[streamId]
            if (vod != null) {
                items.add(InProgressItem.Movie(vod, progress))
            }
        }
    }
    
    return items
}

/**
 * Processes episodes for a series and creates EpisodeItem objects.
 */
private fun processEpisodesForSeries(
    series: Series,
    episodesForSeries: Map<String, WatchProgress>,
    minimalSeries: Series,
    seriesId: Int
): List<InProgressItem.EpisodeItem> {
    val items = mutableListOf<InProgressItem.EpisodeItem>()
    
    episodesForSeries.forEach { (key, progress) ->
        val parts = key.removePrefix(UiConstants.PROGRESS_KEY_SERIES_PREFIX).split(":")
        if (parts.size == 2) {
            val episodeId = parts[1].toIntOrNull()
            if (episodeId != null) {
                Log.d("InProgressContent", "Processing episode: seriesId=$seriesId, episodeId=$episodeId")
                val episode = findEpisodeInSeries(series, episodeId)
                if (episode != null) {
                    Log.d("InProgressContent", "Found episode: seriesId=$seriesId, episodeId=$episodeId, title=${episode.title}")
                    items.add(InProgressItem.EpisodeItem(minimalSeries, episode, progress))
                } else {
                    Log.w("InProgressContent", "Episode not found: seriesId=$seriesId, episodeId=$episodeId, seasons=${series.seasons?.size ?: 0}")
                }
            }
        }
    }
    
    return items
}

/**
 * Loads and processes a single series with its episodes.
 */
private suspend fun loadAndProcessSeries(
    seriesId: Int,
    episodesForSeries: Map<String, WatchProgress>,
    xtreamRepository: XtreamRepository,
    credentials: Credentials,
    index: Int
): List<InProgressItem.EpisodeItem> {
    // Add small delay between API calls to allow GC
    if (index > 0) {
        delay(API_CALL_DELAY_MS)
    }
    
    val result = xtreamRepository.getSeriesInfo(
        credentials.username,
        credentials.password,
        seriesId
    )
    
    val items = mutableListOf<InProgressItem.EpisodeItem>()
    result.onSuccess { series ->
        Log.d("InProgressContent", "Loaded full series info for seriesId=$seriesId, seasons=${series.seasons?.size ?: 0}")
        val minimalSeries = createMinimalSeries(series)
        items.addAll(processEpisodesForSeries(series, episodesForSeries, minimalSeries, seriesId))
    }.onFailure { e ->
        reportWarning("InProgressContent", "Failed to load series info for seriesId=$seriesId", e)
    }
    
    return items
}

/**
 * Loads in-progress episodes.
 */
private suspend fun loadInProgressEpisodes(
    episodeSeriesIds: Set<Int>,
    episodeProgressMap: Map<String, WatchProgress>,
    xtreamRepository: XtreamRepository,
    credentials: Credentials
): List<InProgressItem.EpisodeItem> {
    if (episodeSeriesIds.isEmpty()) {
        return emptyList()
    }
    
    val items = mutableListOf<InProgressItem.EpisodeItem>()
    
    // Load full series info for each series with progress (sequentially to avoid OOM)
    // Limit series count to avoid memory issues
    val limitedSeriesIds = episodeSeriesIds.take(MAX_IN_PROGRESS_SERIES_TO_LOAD)
    if (episodeSeriesIds.size > MAX_IN_PROGRESS_SERIES_TO_LOAD) {
        Log.w("InProgressContent", "Limiting to first $MAX_IN_PROGRESS_SERIES_TO_LOAD series to avoid OOM (${episodeSeriesIds.size} total)")
    }
    
    limitedSeriesIds.forEachIndexed { index, seriesId ->
        // Get episodes for this series first
        val episodesForSeries = episodeProgressMap.filterKeys { 
            it.startsWith("${UiConstants.PROGRESS_KEY_SERIES_PREFIX}$seriesId:") 
        }
        
        if (episodesForSeries.isEmpty()) {
            return@forEachIndexed
        }
        
        val seriesItems = loadAndProcessSeries(
            seriesId = seriesId,
            episodesForSeries = episodesForSeries,
            xtreamRepository = xtreamRepository,
            credentials = credentials,
            index = index
        )
        items.addAll(seriesItems)
    }
    
    return items
}

/**
 * Deduplicates series items, keeping only the most recent episode per series.
 */
private fun deduplicateSeriesItems(items: List<InProgressItem>): List<InProgressItem> {
    val movies = items.filterIsInstance<InProgressItem.Movie>()
    val episodes = items.filterIsInstance<InProgressItem.EpisodeItem>()
    
    // Group episodes by seriesId and keep only the most recent one per series
    val deduplicatedEpisodes = episodes
        .groupBy { it.series.seriesId }
        .mapValues { (_, items) ->
            items.maxByOrNull { it.progress.lastWatched }
        }
        .values
        .filterNotNull()
    
    return movies + deduplicatedEpisodes
}

/**
 * Renders an in-progress content item as a card.
 */
@Composable
private fun RenderInProgressCard(
    item: InProgressItem,
    columnIndex: Int,
    params: InProgressContentParams,
    onFocusChanged: ((Boolean) -> Unit)?,
    cardFocusRequester: androidx.compose.ui.focus.FocusRequester?
) {
    when (item) {
        is InProgressItem.Movie -> {
            VODCard(
                params = VODCardParams(
                    vod = item.vod,
                    onClick = { params.onVodClick(item.vod) },
                    watchlistRepository = params.watchlistRepository,
                    watchProgressService = params.watchProgressService,
                    onFocusChanged = onFocusChanged,
                    onNavigateLeft = createOnNavigateLeft(columnIndex, params.onNavigateLeft),
                    focusRequester = cardFocusRequester
                )
            )
        }
        is InProgressItem.EpisodeItem -> {
            // For episodes, show the series card and navigate to series episodes screen
            // The user can then select the specific episode
            VODCard(
                params = VODCardParams(
                    series = item.series,
                    onClick = { params.onSeriesClick(item.series) },
                    watchlistRepository = params.watchlistRepository,
                    onFocusChanged = onFocusChanged,
                    onNavigateLeft = createOnNavigateLeft(columnIndex, params.onNavigateLeft),
                    focusRequester = cardFocusRequester
                )
            )
        }
    }
}

/**
 * Loads and resolves all in-progress items against the catalog for the given destination.
 * This is the SINGLE source of truth for "Angefangene Inhalte": both the rendered grid
 * ([InProgressContent]) and the sidebar counter ([countInProgressItems]) call this same
 * function, so a progress entry whose series/episode/movie no longer exists in the catalog
 * is dropped from both consistently (see Task 10b - counter must equal card count).
 */
internal suspend fun loadInProgressItemsCore(
    destination: HomeDestination,
    cacheService: DataCacheService,
    xtreamRepository: XtreamRepository?,
    credentials: Credentials?,
    allProgress: Map<String, WatchProgress>
): List<InProgressItem> {
    if (allProgress.isEmpty()) {
        return emptyList()
    }

    val parsed = parseProgressKeys(allProgress)
    val itemsList = mutableListOf<InProgressItem>()

    // Load movies only for VOD destination
    if (destination is HomeDestination.VOD) {
        val movies = loadInProgressMovies(
            allProgress = allProgress,
            movieStreamIds = parsed.movieStreamIds,
            cacheService = cacheService
        )
        itemsList.addAll(movies)
    }

    // Load episodes only for Series destination
    if (destination is HomeDestination.Series) {
        if (credentials != null && xtreamRepository != null) {
            val episodes = loadInProgressEpisodes(
                episodeSeriesIds = parsed.episodeSeriesIds,
                episodeProgressMap = parsed.episodeProgressMap,
                xtreamRepository = xtreamRepository,
                credentials = credentials
            )
            itemsList.addAll(episodes)
        } else {
            Log.w("InProgressContent", "Cannot load series info: credentials or repository missing")
        }
    }

    // Deduplicate series items (keep only most recent episode per series)
    val deduplicatedItems = deduplicateSeriesItems(itemsList)

    // Sort by lastWatched (most recent first)
    return deduplicatedItems.sortedByDescending { item ->
        when (item) {
            is InProgressItem.Movie -> item.progress.lastWatched
            is InProgressItem.EpisodeItem -> item.progress.lastWatched
        }
    }
}

/**
 * Loads all in-progress items based on the destination.
 */
private suspend fun loadInProgressItems(
    params: InProgressContentParams,
    allProgress: Map<String, WatchProgress>
): List<InProgressItem> = loadInProgressItemsCore(
    destination = params.destination,
    cacheService = params.cacheService,
    xtreamRepository = params.xtreamRepository,
    credentials = params.credentials,
    allProgress = allProgress
)

/**
 * Resolved count of in-progress items for the sidebar counter. Resolves against the catalog
 * via [loadInProgressItemsCore] - the exact pipeline the grid uses - so the count always
 * matches the number of cards actually shown (dead progress entries don't inflate it).
 */
internal suspend fun countInProgressItems(
    destination: HomeDestination,
    watchProgressService: WatchProgressService,
    cacheService: DataCacheService,
    xtreamRepository: XtreamRepository?,
    credentials: Credentials?
): Int {
    val allProgress = watchProgressService.getAllInProgress()
    return loadInProgressItemsCore(
        destination = destination,
        cacheService = cacheService,
        xtreamRepository = xtreamRepository,
        credentials = credentials,
        allProgress = allProgress
    ).size
}

@Composable
fun InProgressContent(
    params: InProgressContentParams,
    modifier: Modifier = Modifier
) {
    var inProgressItems by remember { mutableStateOf<List<InProgressItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    // Load in-progress items
    LaunchedEffect(params.destination) {
        isLoading = true
        
        // Sync with Firestore before loading (respects cooldown)
        params.watchProgressService.syncIfNeeded()
        
        // Cleanup completed entries (>= 90%) before loading
        Log.d("InProgressContent", "Calling cleanupCompletedProgress()...")
        val cleanedCount = params.watchProgressService.cleanupCompletedProgress()
        Log.d("InProgressContent", "cleanupCompletedProgress() returned: $cleanedCount entries cleaned")
        
        val allProgress = params.watchProgressService.getAllInProgress()
        val items = loadInProgressItems(params, allProgress)
        
        inProgressItems = items
        Log.d("InProgressContent", "Loaded ${items.size} in-progress items")
        isLoading = false
    }
    
    ContentGridLayout(
        params = ContentGridLayoutParams(
            items = inProgressItems,
            isLoading = isLoading,
            focusRequester = params.focusRequester,
            modifier = modifier,
            lazyGridState = params.lazyGridState,
            keySelector = { item ->
                when (item) {
                    is InProgressItem.Movie -> item.vod.streamId
                    is InProgressItem.EpisodeItem -> "episode_${item.series.seriesId}_${item.episode.id}"
                }
            },
            contentIdSelector = { item ->
                when (item) {
                    is InProgressItem.Movie -> "vod_${item.vod.streamId}"
                    is InProgressItem.EpisodeItem -> "episode_${item.series.seriesId}_${item.episode.id}"
                }
            },
            focusRequesterMap = params.focusRequesterMap,
            onItemsRendered = {
                Log.d("InProgressContent", "Items rendered, loading complete")
            }
        ),
        content = { item, _, columnIndex, onFocusChanged, cardFocusRequester ->
            RenderInProgressCard(
                item = item,
                columnIndex = columnIndex,
                params = params,
                onFocusChanged = onFocusChanged,
                cardFocusRequester = cardFocusRequester
            )
        }
    )
}
