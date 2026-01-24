package com.flintr.tv.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import com.flintr.tv.data.local.WatchProgressService
import com.flintr.tv.data.model.Episode
import com.flintr.tv.data.model.Season
import com.flintr.tv.data.model.Series
import com.flintr.tv.ui.components.WatchedBadge
import com.flintr.tv.ui.theme.AppColors
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.appColors
import com.flintr.tv.ui.utils.onClick
import com.flintr.tv.ui.utils.onDpadDirection
import com.flintr.tv.ui.utils.onBackButton
import com.flintr.tv.ui.utils.InteractionUtils.tvFocusable
import com.flintr.tv.ui.utils.safeRequestFocus
import com.flintr.tv.utils.ContentParser
import kotlinx.coroutines.launch

// Navigation direction enum
private enum class NavigationDirection {
    LEFT, RIGHT
}

// Data classes for EpisodeDetailCard
private data class EpisodeCardState(
    val progressPercent: Float,
    val isCompleted: Boolean,
    val remainingMinutes: Int?,
    val isWatched: Boolean
)

private data class EpisodeCardConfig(
    val episode: Episode,
    val season: Season,
    val series: Series,
    val watchProgressService: WatchProgressService,
    val watchedEpisodeIds: Set<Int>
)

private data class EpisodeCardCallbacks(
    val onClick: () -> Unit,
    val onFocusChanged: (Boolean) -> Unit,
    val onNavigateLeft: () -> Unit,
    val onNavigateRight: () -> Unit
)

private data class SeasonNavigationParams(
    val seasons: List<Season>,
    val selectedSeasonIndex: Int,
    val selectedSeason: Season?,
    val series: Series,
    val watchProgressService: WatchProgressService,
    val watchedEpisodeIds: Set<Int>,
    val initialEpisodeIndex: Int?,
    val smartResumeSeasonIndex: Int?
)

private data class EpisodesListParams(
    val episodes: List<Episode>,
    val season: Season,
    val series: Series,
    val watchProgressService: WatchProgressService,
    val watchedEpisodeIds: Set<Int>,
    val initialEpisodeIndex: Int?
)

private data class EpisodeFocusState(
    val focusedEpisodeIndex: androidx.compose.runtime.MutableState<Int>,
    val listState: androidx.compose.foundation.lazy.LazyListState,
    val focusRequesters: List<FocusRequester>
)

/**
 * Manages episode focus state and handles initial focus setup
 */
@Composable
private fun rememberEpisodeFocus(
    episodes: List<Episode>,
    initialEpisodeIndex: Int?
): EpisodeFocusState {
    val focusedEpisodeIndex = remember(episodes) { 
        mutableStateOf(initialEpisodeIndex?.coerceIn(0, episodes.size - 1) ?: 0) 
    }
    val listState = rememberLazyListState()
    val focusRequesters = remember { episodes.map { FocusRequester() } }
    
    // Update focusedEpisodeIndex when initialEpisodeIndex changes
    LaunchedEffect(initialEpisodeIndex) {
        if (initialEpisodeIndex != null && initialEpisodeIndex in 0 until episodes.size) {
            focusedEpisodeIndex.value = initialEpisodeIndex
        }
    }
    
    // Set initial focus on target episode when list is displayed
    LaunchedEffect(episodes.size, initialEpisodeIndex) {
        if (episodes.isEmpty()) return@LaunchedEffect
        
        val targetIndex = initialEpisodeIndex?.coerceIn(0, episodes.size - 1) ?: 0
        kotlinx.coroutines.delay(150) // Small delay to ensure layout is ready
        
        try {
            focusRequesters.getOrNull(targetIndex)?.requestFocus()
        } catch (e: IllegalStateException) {
            // FocusRequester might not be ready yet, retry after delay
            kotlinx.coroutines.delay(100)
            try {
                focusRequesters.getOrNull(targetIndex)?.requestFocus()
            } catch (e2: IllegalStateException) {
                // Ignore if still not ready
            }
        }
    }
    
    // Scroll to focused episode
    LaunchedEffect(focusedEpisodeIndex.value) {
        if (focusedEpisodeIndex.value in 0 until episodes.size) {
            listState.animateScrollToItem(focusedEpisodeIndex.value)
        }
    }
    
    return EpisodeFocusState(
        focusedEpisodeIndex = focusedEpisodeIndex,
        listState = listState,
        focusRequesters = focusRequesters
    )
}

/**
 * Finds the episode index within a season based on target information
 */
private fun findEpisodeIndex(
    episodes: List<Episode>,
    targetEpisodeId: Int?,
    targetEpisodeNumber: Int?
): Int {
    return episodes.indexOfFirst { 
        it.id == targetEpisodeId || 
        (it.episodeNum ?: it.id) == targetEpisodeNumber 
    }
}

/**
 * Navigates to the target season and episode based on smart resume target
 */
private fun navigateToTargetSeason(
    sortedSeasons: List<Season>,
    targetSeasonNumber: Int,
    targetEpisodeId: Int?,
    targetEpisodeNumber: Int?,
    onSeasonSelected: (Int) -> Unit,
    onEpisodeIndexSet: (Int?) -> Unit,
    onSmartResumeSeasonIndexSet: (Int?) -> Unit
) {
    val targetSeasonIndex = sortedSeasons.indexOfFirst { it.seasonNumber == targetSeasonNumber }
    if (targetSeasonIndex < 0) return
    
    onSeasonSelected(targetSeasonIndex)
    onSmartResumeSeasonIndexSet(targetSeasonIndex)
    
    val targetSeason = sortedSeasons[targetSeasonIndex]
    val episodes = targetSeason.episodes ?: emptyList()
    val episodeIndex = findEpisodeIndex(
        episodes = episodes,
        targetEpisodeId = targetEpisodeId,
        targetEpisodeNumber = targetEpisodeNumber
    )
    
    if (episodeIndex >= 0) {
        onEpisodeIndexSet(episodeIndex)
    }
}

/**
 * Handles smart resume logic: finds and navigates to the target episode
 */
@Composable
private fun HandleSmartResume(
    displaySeries: Series,
    sortedSeasons: List<Season>,
    watchProgressService: WatchProgressService,
    onSeasonSelected: (Int) -> Unit,
    onEpisodeIndexSet: (Int?) -> Unit,
    onSmartResumeSeasonIndexSet: (Int?) -> Unit
) {
    LaunchedEffect(displaySeries.seasons) {
        if (displaySeries.seasons != null && sortedSeasons.isNotEmpty()) {
            val target = watchProgressService.getSmartResumeTarget(displaySeries.seriesId, displaySeries)
            
            if (target != null) {
                navigateToTargetSeason(
                    sortedSeasons = sortedSeasons,
                    targetSeasonNumber = target.seasonNumber,
                    targetEpisodeId = target.episodeId,
                    targetEpisodeNumber = target.episodeNumber,
                    onSeasonSelected = onSeasonSelected,
                    onEpisodeIndexSet = onEpisodeIndexSet,
                    onSmartResumeSeasonIndexSet = onSmartResumeSeasonIndexSet
                )
            }
        }
    }
}

/**
 * Renders the background image with overlay for the series screen
 */
@Composable
private fun SeriesBackground(backdropImageUrl: String?) {
    if (!backdropImageUrl.isNullOrEmpty()) {
        val painter = rememberAsyncImagePainter(model = backdropImageUrl)
        val imageState = painter.state
        
        if (imageState !is AsyncImagePainter.State.Error) {
            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.4f),
                contentScale = ContentScale.Crop
            )
        }
    } else {
        // Fallback background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        )
    }
    
    // Semi-transparent overlay for better readability
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.7f)
                    )
                )
            )
    )
}

@Composable
fun SeriesEpisodesScreen(
    series: Series,
    mainViewModel: com.flintr.tv.viewmodel.MainViewModel,
    watchProgressService: WatchProgressService,
    onBack: () -> Unit,
    onEpisodeClick: (Episode) -> Unit
) {
    // Load series info if seasons are not loaded
    LaunchedEffect(series.seriesId) {
        if (series.seasons == null) {
            mainViewModel.loadSeriesInfo(series.seriesId)
        }
    }
    
    val updatedSeries by mainViewModel.selectedSeries.collectAsState()
    val displaySeries = updatedSeries ?: series
    
    // Sort seasons by seasonNumber
    val sortedSeasons = displaySeries.seasons?.sortedBy { it.seasonNumber } ?: emptyList()
    
    var selectedSeasonIndex by remember { mutableStateOf(0) }
    val selectedSeason = sortedSeasons.getOrNull(selectedSeasonIndex)
    
    // Batch-load watched episode IDs for the series
    var watchedEpisodeIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    
    // Smart Resume: Get target episode for navigation
    var initialEpisodeIndex by remember { mutableStateOf<Int?>(null) }
    var smartResumeSeasonIndex by remember { mutableStateOf<Int?>(null) }
    
    LaunchedEffect(series.seriesId) {
        watchedEpisodeIds = watchProgressService.getWatchedEpisodeIds(series.seriesId)
    }
    
    // Load smart resume target when series info is available
    HandleSmartResume(
        displaySeries = displaySeries,
        sortedSeasons = sortedSeasons,
        watchProgressService = watchProgressService,
        onSeasonSelected = { selectedSeasonIndex = it },
        onEpisodeIndexSet = { initialEpisodeIndex = it },
        onSmartResumeSeasonIndexSet = { smartResumeSeasonIndex = it }
    )
    
    // Background image
    val backdropImageUrl = displaySeries.backdropPath?.firstOrNull()
    
    // Scroll state for auto-scrolling to selected season
    val scrollState = rememberScrollState()
    
    // Dynamic measurements for header and season heights
    var headerHeight by remember { mutableStateOf(0) }
    var seasonHeight by remember { mutableStateOf(0) }
    
    // Auto-scroll when season changes
    LaunchedEffect(selectedSeasonIndex, headerHeight, seasonHeight) {
        if (headerHeight > 0 && seasonHeight > 0) {
            // Scroll to show the selected season
            kotlinx.coroutines.delay(100)
            val targetPosition = headerHeight + (selectedSeasonIndex * seasonHeight)
            scrollState.animateScrollTo(targetPosition.coerceAtMost(scrollState.maxValue))
        }
    }
    
    // FocusRequester for header to enable navigation from season buttons
    val headerFocusRequester = remember { FocusRequester() }
    // Callback to navigate to selected season button
    var navigateToSeasonButton by remember { mutableStateOf<(() -> Unit)?>(null) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onBackButton(onBack)
    ) {
        SeriesBackground(backdropImageUrl = backdropImageUrl)
        
        // Content - Scrollable
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(24.dp)
        ) {
            // Series Header
            SeriesHeader(
                series = displaySeries,
                headerFocusRequester = headerFocusRequester,
                onNavigateDown = {
                    // Navigate to selected season button
                    navigateToSeasonButton?.invoke()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        headerHeight = coordinates.size.height
                    }
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Season Navigation
            SeasonNavigation(
                params = SeasonNavigationParams(
                    seasons = sortedSeasons,
                    selectedSeasonIndex = selectedSeasonIndex,
                    selectedSeason = selectedSeason,
                    series = displaySeries,
                    watchProgressService = watchProgressService,
                    watchedEpisodeIds = watchedEpisodeIds,
                    initialEpisodeIndex = initialEpisodeIndex,
                    smartResumeSeasonIndex = smartResumeSeasonIndex
                ),
                headerFocusRequester = headerFocusRequester,
                onNavigateToSeasonButton = { callback ->
                    navigateToSeasonButton = callback
                },
                onSeasonSelected = { index ->
                    selectedSeasonIndex = index
                    // Reset initialEpisodeIndex when season changes manually (not via smart resume)
                    if (index != smartResumeSeasonIndex) {
                        initialEpisodeIndex = null
                    }
                },
                onEpisodeClick = onEpisodeClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        if (seasonHeight == 0) {
                            // Only set once, use first season as reference
                            seasonHeight = coordinates.size.height
                        }
                    }
            )
        }
    }
}

@Composable
private fun SeriesHeader(
    series: Series,
    headerFocusRequester: FocusRequester,
    onNavigateDown: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rating = ContentParser.getRating(series)
    val year = series.releaseDate?.take(4) // Extract year from date
    val seasonCount = series.seasons?.size ?: 0
    val seasonText = if (seasonCount == 1) "1 Staffel" else "$seasonCount Staffeln"
    val colors = appColors()
    var isFocused by remember { mutableStateOf(false) }
    
    // Border color with animation for focus feedback
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) colors.accentColor else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "borderColor"
    )
    
    Column(
        modifier = modifier
            .background(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(8.dp)
            )
            .then(
                if (borderColor != Color.Transparent) {
                    Modifier.border(
                        width = 2.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(8.dp)
                    )
                } else {
                    Modifier
                }
            )
            .focusRequester(headerFocusRequester)
            .tvFocusable { isFocused = it }
            .onDpadDirection(
                onDown = {
                    onNavigateDown()
                    true
                }
            )
            .padding(16.dp)
    ) {
        // Title
        Text(
            text = series.name,
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Row 1: Rating, Year, Season Count, Categories
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rating
            if (rating != null) {
                Box(
                    modifier = Modifier
                        .background(
                            color = Color.Gray.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = rating,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // Year
            OptionalMetadataText(
                content = year,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
            
            // Season Count
            Text(
                text = seasonText,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
            
            // Categories/Genre
            OptionalMetadataText(
                content = series.genre,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
        }
        
        // Row 2: Cast
        OptionalMetadataText(
            content = series.cast,
            prefix = "Besetzung: ",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        // Row 3: Director
        OptionalMetadataText(
            content = series.director,
            prefix = "Regisseur: ",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        // Row 4+: Plot/Description
        OptionalMetadataText(
            content = series.plot,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.9f),
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun OptionalMetadataText(
    content: String?,
    prefix: String = "",
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    modifier: Modifier = Modifier
) {
    if (!content.isNullOrEmpty()) {
        Text(
            text = if (prefix.isNotEmpty()) "$prefix$content" else content,
            style = style,
            color = color,
            maxLines = maxLines,
            overflow = overflow,
            modifier = modifier
        )
    }
}

@Composable
private fun SeasonNavigation(
    params: SeasonNavigationParams,
    headerFocusRequester: FocusRequester,
    onNavigateToSeasonButton: ((() -> Unit)) -> Unit,
    onSeasonSelected: (Int) -> Unit,
    onEpisodeClick: (Episode) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    // Store FocusRequesters for season buttons to enable navigation from header
    val seasonButtonFocusRequesters = remember(params.seasons.size) {
        List(params.seasons.size) { FocusRequester() }
    }
    
    // Provide callback to navigate to selected season button
    LaunchedEffect(params.selectedSeasonIndex, seasonButtonFocusRequesters.size, params.seasons.size) {
        if (params.seasons.size > 1 && params.selectedSeasonIndex in 0 until seasonButtonFocusRequesters.size) {
            onNavigateToSeasonButton {
                coroutineScope.launch {
                    seasonButtonFocusRequesters[params.selectedSeasonIndex].safeRequestFocus(
                        "SeriesEpisodesScreen",
                        "SEASON_BUTTON_${params.selectedSeasonIndex}"
                    )
                }
            }
        } else {
            // If there's only one season or no season buttons, clear the callback
            onNavigateToSeasonButton { }
        }
    }
    
    Column(
        modifier = modifier
    ) {
        // Season Selector - Horizontal list of season buttons
        if (params.seasons.size > 1) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .onDpadDirection(
                        onUp = {
                            // Navigate to header when pressing up from season buttons
                            coroutineScope.launch {
                                headerFocusRequester.safeRequestFocus("SeriesEpisodesScreen", "HEADER")
                            }
                            true
                        }
                    )
            ) {
                itemsIndexed(
                    items = params.seasons,
                    key = { _, season -> season.seasonNumber }
                ) { seasonIndex, season ->
                    SeasonButton(
                        season = season,
                        isSelected = seasonIndex == params.selectedSeasonIndex,
                        focusRequester = seasonButtonFocusRequesters.getOrNull(seasonIndex),
                        onSelect = { onSeasonSelected(seasonIndex) }
                    )
                }
            }
        }
        
        // Season Title
        if (params.selectedSeason != null) {
            Text(
                text = "Staffel ${params.selectedSeason.seasonNumber}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
        
        // Episodes List for selected season
        params.selectedSeason?.episodes?.let { episodes ->
            EpisodesList(
                params = EpisodesListParams(
                    episodes = episodes,
                    season = params.selectedSeason,
                    series = params.series,
                    watchProgressService = params.watchProgressService,
                    watchedEpisodeIds = params.watchedEpisodeIds,
                    initialEpisodeIndex = if (params.selectedSeasonIndex == params.smartResumeSeasonIndex) params.initialEpisodeIndex else null
                ),
                onEpisodeClick = onEpisodeClick,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SeasonButton(
    season: Season,
    isSelected: Boolean,
    focusRequester: FocusRequester? = null,
    onSelect: () -> Unit
) {
    val colors = appColors()
    var isFocused by remember { mutableStateOf(false) }
    
    // Background color with animation (like CategoryRow)
    // Selected: completely filled with accentColor
    // Focused (but not selected): hover background
    val isHoveredOrFocused = isFocused && !isSelected
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSelected -> colors.accentColor
            isHoveredOrFocused -> colors.categoryHoverBg
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 200),
        label = "backgroundColor"
    )
    
    // Border color with animation
    // Selected OR focused: show border
    val borderColor by animateColorAsState(
        targetValue = if (isSelected || isFocused) colors.accentColor else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "borderColor"
    )
    
    // Text color: always white on dark background
    val textColor = Color.White
    
    val internalFocusRequester = remember { FocusRequester() }
    val actualFocusRequester = focusRequester ?: internalFocusRequester
    
    // Request focus when selected
    LaunchedEffect(isSelected) {
        if (isSelected) {
            kotlinx.coroutines.delay(50)
            try {
                actualFocusRequester.requestFocus()
            } catch (e: IllegalStateException) {
                // FocusRequester might not be ready yet
            }
        }
    }
    
    Box(
        modifier = Modifier
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(8.dp)
            )
            .then(
                if (borderColor != Color.Transparent) {
                    Modifier.border(
                        width = 2.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(8.dp)
                    )
                } else {
                    Modifier
                }
            )
            .focusRequester(actualFocusRequester)
            .tvFocusable { isFocused = it }
            .onClick(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "Staffel ${season.seasonNumber}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = textColor
        )
    }
}

@Composable
private fun EpisodesList(
    params: EpisodesListParams,
    onEpisodeClick: (Episode) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusState = rememberEpisodeFocus(
        episodes = params.episodes,
        initialEpisodeIndex = params.initialEpisodeIndex
    )
    
    val focusedEpisodeIndex by focusState.focusedEpisodeIndex
    
    Column(
        modifier = modifier
    ) {
        // Episode indicator (e.g., "1/8")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = "${focusedEpisodeIndex + 1}/${params.episodes.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
        
        // Episodes horizontal list
        LazyRow(
            state = focusState.listState,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(
                items = params.episodes,
                key = { _, episode -> episode.id }
            ) { episodeIndex, episode ->
                EpisodeDetailCard(
                    config = EpisodeCardConfig(
                        episode = episode,
                        season = params.season,
                        series = params.series,
                        watchProgressService = params.watchProgressService,
                        watchedEpisodeIds = params.watchedEpisodeIds
                    ),
                    callbacks = EpisodeCardCallbacks(
                        onClick = { onEpisodeClick(episode) },
                        onFocusChanged = { isFocused ->
                            if (isFocused) {
                                focusState.focusedEpisodeIndex.value = episodeIndex
                            }
                        },
                        onNavigateLeft = {
                            focusState.focusedEpisodeIndex.value = updateEpisodeIndex(
                                currentIndex = focusedEpisodeIndex,
                                maxIndex = params.episodes.size - 1,
                                direction = NavigationDirection.LEFT
                            )
                        },
                        onNavigateRight = {
                            focusState.focusedEpisodeIndex.value = updateEpisodeIndex(
                                currentIndex = focusedEpisodeIndex,
                                maxIndex = params.episodes.size - 1,
                                direction = NavigationDirection.RIGHT
                            )
                        }
                    ),
                    isFocused = episodeIndex == focusedEpisodeIndex,
                    focusRequester = focusState.focusRequesters.getOrNull(episodeIndex)
                )
            }
        }
    }
}

/**
 * Extension function to conditionally apply shadow modifier for focused episode cards
 */
private fun Modifier.episodeCardShadow(
    isFocused: Boolean,
    elevation: Float,
    accentColor: Color
): Modifier {
    return if (isFocused) {
        this.shadow(
            elevation = elevation.dp,
            shape = RoundedCornerShape(Dimensions.listItemHoverBorderRadius),
            spotColor = accentColor.copy(alpha = Dimensions.categoryShadowAlpha),
            ambientColor = accentColor.copy(alpha = Dimensions.categoryShadowAlpha)
        )
    } else {
        this
    }
}

/**
 * Extension function to conditionally apply border modifier for episode cards
 */
private fun Modifier.conditionalBorder(
    borderColor: Color,
    shape: RoundedCornerShape
): Modifier {
    return if (borderColor != Color.Transparent) {
        this.border(
            width = Dimensions.listItemHoverBorderWidth,
            color = borderColor,
            shape = shape
        )
    } else {
        this
    }
}

@Composable
private fun EpisodeDetailCard(
    config: EpisodeCardConfig,
    callbacks: EpisodeCardCallbacks,
    isFocused: Boolean,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    var isFocusedState by remember { mutableStateOf(false) }
    val colors = appColors()
    val cardFocusRequester = focusRequester ?: remember { FocusRequester() }
    
    val progressState = rememberEpisodeProgress(
        episode = config.episode,
        series = config.series,
        watchProgressService = config.watchProgressService,
        watchedEpisodeIds = config.watchedEpisodeIds
    )
    
    // Border color with animation
    val borderColor by animateColorAsState(
        targetValue = if (isFocused || isFocusedState) colors.accentColor else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "borderColor"
    )
    
    // Background color with animation
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isFocused -> colors.accentColorHoverBg
            isFocusedState -> colors.categoryHoverBg
            else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        },
        animationSpec = tween(durationMillis = 200),
        label = "backgroundColor"
    )
    
    // Shadow elevation for focused items
    val shadowElevation by animateFloatAsState(
        targetValue = if (isFocused) 8f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "shadowElevation"
    )
    
    // Request focus when isFocused changes
    LaunchedEffect(isFocused) {
        if (isFocused) {
            try {
                cardFocusRequester.requestFocus()
            } catch (e: IllegalStateException) {
                // FocusRequester might not be ready yet, retry after short delay
                kotlinx.coroutines.delay(50)
                try {
                    cardFocusRequester.requestFocus()
                } catch (e2: IllegalStateException) {
                    // Ignore if still not ready
                }
            }
        }
    }
    
    val cardShape = RoundedCornerShape(Dimensions.listItemHoverBorderRadius)
    
    Box(
        modifier = modifier
            .width(300.dp)
            .episodeCardShadow(
                isFocused = isFocused,
                elevation = shadowElevation,
                accentColor = colors.accentColor
            )
            .background(
                color = backgroundColor,
                shape = cardShape
            )
            .conditionalBorder(
                borderColor = borderColor,
                shape = cardShape
            )
            .focusRequester(cardFocusRequester)
            .onFocusChanged { focusState ->
                isFocusedState = focusState.isFocused
                callbacks.onFocusChanged(focusState.isFocused)
            }
            .focusable()
            .onDpadDirection(
                onLeft = {
                    callbacks.onNavigateLeft()
                    true
                },
                onRight = {
                    callbacks.onNavigateRight()
                    true
                }
            )
            .onClick(callbacks.onClick)
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Thumbnail with badge overlay
            EpisodeThumbnail(
                imageUrl = config.episode.info?.movieImage,
                showWatchedBadge = progressState.isWatched
            )
            
            // Episode number and title
            val episodeNum = config.episode.episodeNum ?: config.episode.id
            val episodeTitle = "S${config.season.seasonNumber}E$episodeNum - ${config.episode.title}"
            
            Text(
                text = episodeTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            
            // Description
            OptionalMetadataText(
                content = config.episode.info?.plot,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            
            // Duration
            config.episode.info?.duration?.let { durationStr ->
                Text(
                    text = formatDuration(durationStr),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            
            // Progress bar (badge is now on thumbnail)
            EpisodeProgressIndicator(
                progressState = progressState,
                colors = colors
            )
        }
    }
}

@Composable
private fun rememberEpisodeProgress(
    episode: Episode,
    series: Series,
    watchProgressService: WatchProgressService,
    watchedEpisodeIds: Set<Int>
): EpisodeCardState {
    var progress by remember { mutableStateOf<Long?>(null) }
    var duration by remember { mutableStateOf<Long?>(null) }
    
    LaunchedEffect(episode.id) {
        progress = watchProgressService.getProgress(
            contentType = "series",
            streamId = null,
            seriesId = series.seriesId,
            episodeId = episode.id
        )
        
        // Parse duration from episode.info?.duration
        episode.info?.duration?.let { durationStr ->
            // Duration is typically in seconds or "HH:MM:SS" format
            duration = try {
                durationStr.toLongOrNull()?.times(1000) // Convert seconds to milliseconds
                    ?: parseDurationString(durationStr)?.times(1000)
            } catch (e: Exception) {
                null
            }
        }
    }
    
    // Check if episode is watched (only from batch-loaded set of fully watched episodes)
    val isWatched = episode.id in watchedEpisodeIds
    
    // Calculate progress percentage
    val progressPercent = if (progress != null && duration != null && duration!! > 0) {
        (progress!!.toFloat() / duration!!.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    
    // If episode is watched, treat it as completed regardless of progress
    val isCompleted = isWatched || progressPercent >= 0.9f
    val remainingMinutes = if (progress != null && duration != null && !isCompleted) {
        ((duration!! - progress!!) / 60000).toInt()
    } else {
        null
    }
    
    return EpisodeCardState(
        progressPercent = progressPercent,
        isCompleted = isCompleted,
        remainingMinutes = remainingMinutes,
        isWatched = isWatched
    )
}

@Composable
private fun EpisodeThumbnail(
    imageUrl: String?,
    showWatchedBadge: Boolean = false
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .padding(bottom = 8.dp)
    ) {
        imageUrl?.let { url ->
            if (url.isNotEmpty()) {
                val painter = rememberAsyncImagePainter(model = url)
                val imageState = painter.state
                
                if (imageState !is AsyncImagePainter.State.Error) {
                    Image(
                        painter = painter,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
        
        // Watched badge overlay - centered on image
        if (showWatchedBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(8.dp)
            ) {
                WatchedBadge()
            }
        }
    }
}

@Composable
private fun EpisodeProgressIndicator(
    progressState: EpisodeCardState,
    colors: AppColors
) {
    // Only show progress bar for in-progress episodes (badge is now on thumbnail)
    if (progressState.progressPercent > 0f && !progressState.isCompleted) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            LinearProgressIndicator(
                progress = progressState.progressPercent,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = colors.accentColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            
            // Remaining time
            progressState.remainingMinutes?.let { minutes ->
                Text(
                    text = "${minutes}m verbleibend",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/**
 * Update episode index based on navigation direction
 */
private fun updateEpisodeIndex(
    currentIndex: Int,
    maxIndex: Int,
    direction: NavigationDirection
): Int {
    return when (direction) {
        NavigationDirection.LEFT -> (currentIndex - 1).coerceAtLeast(0)
        NavigationDirection.RIGHT -> (currentIndex + 1).coerceAtMost(maxIndex)
    }
}

/**
 * Parse duration string in "HH:MM:SS" format to seconds
 */
private fun parseDurationString(durationStr: String): Long? {
    return try {
        val parts = durationStr.split(":")
        when (parts.size) {
            3 -> { // HH:MM:SS
                val hours = parts[0].toLongOrNull() ?: return null
                val minutes = parts[1].toLongOrNull() ?: return null
                val seconds = parts[2].toLongOrNull() ?: return null
                hours * 3600 + minutes * 60 + seconds
            }
            2 -> { // MM:SS
                val minutes = parts[0].toLongOrNull() ?: return null
                val seconds = parts[1].toLongOrNull() ?: return null
                minutes * 60 + seconds
            }
            else -> durationStr.toLongOrNull()
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * Format duration string for display
 */
private fun formatDuration(durationStr: String): String {
    val seconds = parseDurationString(durationStr)
    return if (seconds != null) {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "${seconds}s"
        }
    } else {
        durationStr
    }
}
