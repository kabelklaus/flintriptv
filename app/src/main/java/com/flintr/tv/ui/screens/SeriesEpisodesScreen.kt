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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.flintr.tv.utils.ContentParser

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
    val watchedEpisodeIds: Set<Int>
)

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
    
    LaunchedEffect(series.seriesId) {
        watchedEpisodeIds = watchProgressService.getWatchedEpisodeIds(series.seriesId)
    }
    
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
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onBackButton(onBack)
    ) {
        // Background image
        if (!backdropImageUrl.isNullOrEmpty()) {
            val painter = rememberAsyncImagePainter(model = backdropImageUrl)
            val imageState = painter.state
            
            if (imageState !is AsyncImagePainter.State.Error) {
                Image(
                    painter = painter,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
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
                    watchedEpisodeIds = watchedEpisodeIds
                ),
                onSeasonSelected = { index ->
                    selectedSeasonIndex = index
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
    modifier: Modifier = Modifier
) {
    val rating = ContentParser.getRating(series)
    val year = series.releaseDate?.take(4) // Extract year from date
    val seasonCount = series.seasons?.size ?: 0
    val seasonText = if (seasonCount == 1) "1 Staffel" else "$seasonCount Staffeln"
    
    Column(
        modifier = modifier
            .background(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(8.dp)
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
    onSeasonSelected: (Int) -> Unit,
    onEpisodeClick: (Episode) -> Unit,
    modifier: Modifier = Modifier
) {
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
            ) {
                items(
                    items = params.seasons,
                    key = { it.seasonNumber }
                ) { season ->
                    val seasonIndex = params.seasons.indexOf(season)
                    SeasonButton(
                        season = season,
                        isSelected = seasonIndex == params.selectedSeasonIndex,
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
                episodes = episodes,
                season = params.selectedSeason,
                series = params.series,
                watchProgressService = params.watchProgressService,
                watchedEpisodeIds = params.watchedEpisodeIds,
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
    onSelect: () -> Unit
) {
    val colors = appColors()
    
    // Border color with animation
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) colors.accentColor else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "borderColor"
    )
    
    // Background color with animation
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSelected -> colors.accentColorHoverBg
            else -> Color.White.copy(alpha = 0.2f)
        },
        animationSpec = tween(durationMillis = 200),
        label = "backgroundColor"
    )
    
    val focusRequester = remember { FocusRequester() }
    
    // Request focus when selected
    LaunchedEffect(isSelected) {
        if (isSelected) {
            kotlinx.coroutines.delay(50)
            try {
                focusRequester.requestFocus()
            } catch (e: Exception) {
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
            .focusRequester(focusRequester)
            .focusable()
            .onClick(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "Staffel ${season.seasonNumber}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = Color.White
        )
    }
}

@Composable
private fun EpisodesList(
    episodes: List<Episode>,
    season: Season,
    series: Series,
    watchProgressService: WatchProgressService,
    watchedEpisodeIds: Set<Int>,
    onEpisodeClick: (Episode) -> Unit,
    modifier: Modifier = Modifier
) {
    var focusedEpisodeIndex by remember { mutableStateOf(0) }
    val listState = rememberLazyListState()
    val firstEpisodeFocusRequester = remember { FocusRequester() }
    
    // Set initial focus on first episode when list is displayed
    LaunchedEffect(episodes.size) {
        if (episodes.isEmpty()) return@LaunchedEffect
        
        kotlinx.coroutines.delay(100) // Small delay to ensure layout is ready
        try {
            firstEpisodeFocusRequester.requestFocus()
        } catch (e: Exception) {
            // FocusRequester might not be ready yet
        }
    }
    
    // Scroll to focused episode
    LaunchedEffect(focusedEpisodeIndex) {
        if (focusedEpisodeIndex in 0 until episodes.size) {
            listState.animateScrollToItem(focusedEpisodeIndex)
        }
    }
    
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
                text = "${focusedEpisodeIndex + 1}/${episodes.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
        
        // Episodes horizontal list
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(
                items = episodes,
                key = { it.id }
            ) { episode ->
                val episodeIndex = episodes.indexOf(episode)
                EpisodeDetailCard(
                    config = EpisodeCardConfig(
                        episode = episode,
                        season = season,
                        series = series,
                        watchProgressService = watchProgressService,
                        watchedEpisodeIds = watchedEpisodeIds
                    ),
                    callbacks = EpisodeCardCallbacks(
                        onClick = { onEpisodeClick(episode) },
                        onFocusChanged = { isFocused ->
                            if (isFocused) {
                                focusedEpisodeIndex = episodeIndex
                            }
                        },
                        onNavigateLeft = {
                            focusedEpisodeIndex = updateEpisodeIndex(
                                currentIndex = focusedEpisodeIndex,
                                maxIndex = episodes.size - 1,
                                direction = NavigationDirection.LEFT
                            )
                        },
                        onNavigateRight = {
                            focusedEpisodeIndex = updateEpisodeIndex(
                                currentIndex = focusedEpisodeIndex,
                                maxIndex = episodes.size - 1,
                                direction = NavigationDirection.RIGHT
                            )
                        }
                    ),
                    isFocused = episodeIndex == focusedEpisodeIndex,
                    focusRequester = if (episodeIndex == 0) firstEpisodeFocusRequester else null
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
            } catch (e: Exception) {
                // FocusRequester might not be ready yet, retry after short delay
                kotlinx.coroutines.delay(50)
                try {
                    cardFocusRequester.requestFocus()
                } catch (e2: Exception) {
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
            // Thumbnail
            EpisodeThumbnail(
                imageUrl = config.episode.info?.movieImage
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
            
            // Progress bar or checkmark
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
    
    // Check if episode is watched (from batch-loaded set)
    val isWatched = episode.id in watchedEpisodeIds
    
    // Calculate progress percentage
    val progressPercent = if (progress != null && duration != null && duration!! > 0) {
        (progress!!.toFloat() / duration!!.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    
    val isCompleted = progressPercent >= 0.9f
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
    imageUrl: String?
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
                        .fillMaxWidth()
                        .height(180.dp)
                        .padding(bottom = 8.dp),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

@Composable
private fun EpisodeProgressIndicator(
    progressState: EpisodeCardState,
    colors: AppColors
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (progressState.isWatched) {
            // Watched badge for fully watched episodes
            WatchedBadge()
        } else if (progressState.progressPercent > 0f) {
            // Progress bar for in-progress episodes
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
