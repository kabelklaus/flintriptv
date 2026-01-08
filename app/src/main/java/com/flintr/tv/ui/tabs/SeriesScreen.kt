package com.flintr.tv.ui.tabs

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.flintr.tv.data.model.Episode
import com.flintr.tv.data.model.Season
import com.flintr.tv.data.model.Series
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.appColors
import com.flintr.tv.ui.utils.onDpadCenter
import com.flintr.tv.ui.utils.onDpadDirection
import com.flintr.tv.viewmodel.MainViewModel

@Composable
fun SeriesContent(
    viewModel: MainViewModel,
    selectedCategoryId: String?,
    focusRequester: FocusRequester,
    onNavigateLeft: () -> Unit,
    onEpisodeClick: (Episode, Series) -> Unit,
    modifier: Modifier = Modifier
) {
    val series by viewModel.series.collectAsState()
    val selectedSeries by viewModel.selectedSeries.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    
    var expandedSeriesId by remember { mutableStateOf<Int?>(null) }
    
    LaunchedEffect(selectedCategoryId) {
        if (selectedCategoryId != null) {
            viewModel.loadSeries(selectedCategoryId)
        }
    }
    
    LaunchedEffect(expandedSeriesId) {
        expandedSeriesId?.let { viewModel.loadSeriesInfo(it) }
    }
    
    // Don't automatically request focus when series are loaded
    // Focus will be set manually when navigating from CategorySidebar
    // This keeps the focus on the Navbar when the app starts
    
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .focusable()
                    .onDpadDirection(
                        onLeft = {
                            onNavigateLeft()
                            true
                        }
                    )
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(series) { seriesItem ->
                        SeriesItem(
                            series = seriesItem,
                            isExpanded = expandedSeriesId == seriesItem.seriesId,
                            onExpand = { 
                                expandedSeriesId = if (expandedSeriesId == seriesItem.seriesId) null else seriesItem.seriesId
                            },
                            onEpisodeClick = { episode ->
                                onEpisodeClick(episode, seriesItem)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SeriesItem(
    series: Series,
    isExpanded: Boolean,
    onExpand: () -> Unit,
    onEpisodeClick: (Episode) -> Unit,
    isSelected: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }
    val colors = appColors()
    
    // Border color with animation
    val borderColor by animateColorAsState(
        targetValue = if (isSelected || isFocused) colors.accentColor else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "borderColor"
    )
    
    // Background color with animation
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSelected -> colors.accentColorHoverBg
            isFocused -> colors.categoryHoverBg
            else -> MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(durationMillis = 200),
        label = "backgroundColor"
    )
    
    // Shadow elevation for selected items
    val shadowElevation by animateFloatAsState(
        targetValue = if (isSelected) 4f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "shadowElevation"
    )
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelected) {
                    Modifier.shadow(
                        elevation = shadowElevation.dp,
                        shape = RoundedCornerShape(Dimensions.listItemHoverBorderRadius),
                        spotColor = colors.accentColor.copy(alpha = Dimensions.categoryShadowAlpha),
                        ambientColor = colors.accentColor.copy(alpha = Dimensions.categoryShadowAlpha)
                    )
                } else {
                    Modifier
                }
            )
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(Dimensions.listItemHoverBorderRadius)
            )
            .then(
                if (borderColor != Color.Transparent) {
                    Modifier.border(
                        width = Dimensions.listItemHoverBorderWidth,
                        color = borderColor,
                        shape = RoundedCornerShape(Dimensions.listItemHoverBorderRadius)
                    )
                } else {
                    Modifier
                }
            )
            // Correct order: onFocusChanged -> focusable -> key handlers
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            }
            .focusable()
            .onDpadCenter(onExpand)
            .clickable { onExpand() }
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AsyncImage(
                model = series.cover,
                contentDescription = series.name,
                modifier = Modifier.size(120.dp),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = series.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                series.plot?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 3
                    )
                }
            }
        }
        
        if (isExpanded && series.seasons != null) {
            series.seasons.forEach { season ->
                SeasonSection(
                    season = season,
                    seriesId = series.seriesId,
                    onEpisodeClick = onEpisodeClick
                )
            }
        }
    }
}

@Composable
fun SeasonSection(
    season: Season,
    seriesId: Int,
    onEpisodeClick: (Episode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
    ) {
        Text(
            text = season.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        season.episodes?.let { episodes ->
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(episodes) { episode ->
                    EpisodeCard(
                        episode = episode,
                        seriesId = seriesId,
                        onClick = { onEpisodeClick(episode) }
                    )
                }
            }
        }
    }
}

@Composable
fun EpisodeCard(
    episode: Episode,
    seriesId: Int,
    onClick: () -> Unit,
    isSelected: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }
    val colors = appColors()
    
    // Border color with animation
    val borderColor by animateColorAsState(
        targetValue = if (isSelected || isFocused) colors.accentColor else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "borderColor"
    )
    
    // Background color with animation
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSelected -> colors.accentColorHoverBg
            isFocused -> colors.categoryHoverBg
            else -> MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(durationMillis = 200),
        label = "backgroundColor"
    )
    
    // Shadow elevation for selected items
    val shadowElevation by animateFloatAsState(
        targetValue = if (isSelected) 4f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "shadowElevation"
    )
    
    Box(
        modifier = Modifier
            .width(120.dp)
            .height(80.dp)
            .then(
                if (isSelected) {
                    Modifier.shadow(
                        elevation = shadowElevation.dp,
                        shape = RoundedCornerShape(Dimensions.listItemHoverBorderRadius),
                        spotColor = colors.accentColor.copy(alpha = Dimensions.categoryShadowAlpha),
                        ambientColor = colors.accentColor.copy(alpha = Dimensions.categoryShadowAlpha)
                    )
                } else {
                    Modifier
                }
            )
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(Dimensions.listItemHoverBorderRadius)
            )
            .then(
                if (borderColor != Color.Transparent) {
                    Modifier.border(
                        width = Dimensions.listItemHoverBorderWidth,
                        color = borderColor,
                        shape = RoundedCornerShape(Dimensions.listItemHoverBorderRadius)
                    )
                } else {
                    Modifier
                }
            )
            // Correct order: onFocusChanged -> focusable -> key handlers
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            }
            .focusable()
            .onDpadCenter(onClick)
            .clickable { onClick() }
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = episode.title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

