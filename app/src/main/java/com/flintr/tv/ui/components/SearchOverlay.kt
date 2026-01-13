package com.flintr.tv.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flintr.tv.data.local.FavoritesRepository
import com.flintr.tv.data.model.LiveStream
import com.flintr.tv.data.model.SearchState
import com.flintr.tv.data.model.Series
import com.flintr.tv.data.model.VodStream
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.FontSizes
import com.flintr.tv.ui.theme.SharedColors
import com.flintr.tv.ui.theme.appColors
import com.flintr.tv.ui.utils.onClick
import com.flintr.tv.ui.utils.safeRequestFocus
import kotlinx.coroutines.delay

/**
 * Fullscreen search overlay optimized for TV navigation
 * Opens when search button is activated and provides a text input field with search results
 */
@Composable
fun SearchOverlay(
    searchState: SearchState,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onLiveStreamClick: (LiveStream) -> Unit,
    onVodClick: (VodStream) -> Unit,
    onSeriesClick: (Series) -> Unit,
    favoritesRepository: FavoritesRepository
) {
    val colors = appColors()
    val searchFieldFocusRequester = remember { FocusRequester() }
    val resultsListFocusRequester = remember { FocusRequester() }
    var searchQuery by remember { mutableStateOf("") }
    
    // Sync searchQuery with searchState.query
    LaunchedEffect(searchState.query) {
        searchQuery = searchState.query
    }
    
    // Handle Back button
    BackHandler(enabled = true) {
        onDismiss()
    }
    
    // Request focus on search field when overlay opens
    LaunchedEffect(Unit) {
        delay(16) // Small delay to ensure component is ready
        searchFieldFocusRequester.requestFocus()
    }
    
    // Auto-focus first result when results become available
    LaunchedEffect(searchState.results.totalCount) {
        if (searchState.results.totalCount > 0) {
            // Small delay to ensure LazyColumn is ready
            delay(50)
            // Focus will be set on first item via FocusRequester in SearchResultsList
        }
    }
    
    // Fullscreen overlay with dark background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SharedColors.overlayBlack.copy(alpha = 0.8f))
    ) {
        // Search field container at top center
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 1200.dp)
                .fillMaxWidth()
                .padding(
                    top = Dimensions.spacingXL * 2,
                    start = Dimensions.spacingXL,
                    end = Dimensions.spacingXL,
                    bottom = Dimensions.spacingXL
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Search input field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { newQuery ->
                    searchQuery = newQuery
                    onQueryChange(newQuery)
                },
                placeholder = { 
                    Text(
                        text = "Suchen...",
                        fontSize = FontSizes.fontSizeLG,
                        color = colors.textSecondary
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = colors.iconColor
                    )
                },
                trailingIcon = {
                    if (searchState.isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = colors.accentColor
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(searchFieldFocusRequester)
                    .onKeyEvent { event ->
                        // Navigate to results list when Down is pressed
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                            if (searchState.results.totalCount > 0) {
                                resultsListFocusRequester.requestFocus()
                                true
                            } else {
                                false
                            }
                        } else {
                            false
                        }
                    },
                singleLine = true,
                shape = RoundedCornerShape(Dimensions.radiusMD),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accentColor,
                    unfocusedBorderColor = colors.cardBorder,
                    focusedLabelColor = colors.accentColor,
                    unfocusedLabelColor = colors.textSecondary,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    cursorColor = colors.accentColor,
                    focusedPlaceholderColor = colors.textSecondary,
                    unfocusedPlaceholderColor = colors.textSecondary,
                    focusedLeadingIconColor = colors.accentColor,
                    unfocusedLeadingIconColor = colors.iconColor
                ),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = FontSizes.fontSizeXL
                )
            )
            
            Spacer(modifier = Modifier.height(Dimensions.spacingMD))
            
            // Search results or hints
            when {
                searchState.showMinLengthHint -> {
                    Text(
                        text = "Mindestens 2 Zeichen eingeben",
                        fontSize = FontSizes.fontSizeMD,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(horizontal = Dimensions.spacingMD)
                    )
                }
                searchState.query.length >= 2 && !searchState.isSearching && searchState.results.isEmpty -> {
                    Text(
                        text = "Keine Ergebnisse gefunden",
                        fontSize = FontSizes.fontSizeMD,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(horizontal = Dimensions.spacingMD)
                    )
                }
                searchState.query.length >= 2 && searchState.results.totalCount > 0 -> {
                    SearchResultsList(
                        searchState = searchState,
                        onLiveStreamClick = onLiveStreamClick,
                        onVodClick = onVodClick,
                        onSeriesClick = onSeriesClick,
                        favoritesRepository = favoritesRepository,
                        colors = colors,
                        focusRequester = resultsListFocusRequester,
                        onNavigateUp = {
                            searchFieldFocusRequester.requestFocus()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultsList(
    searchState: SearchState,
    onLiveStreamClick: (LiveStream) -> Unit,
    onVodClick: (VodStream) -> Unit,
    onSeriesClick: (Series) -> Unit,
    favoritesRepository: FavoritesRepository,
    colors: com.flintr.tv.ui.theme.AppColors,
    focusRequester: FocusRequester,
    onNavigateUp: () -> Unit
) {
    // Track if we have any results to determine first item
    val hasResults = searchState.results.totalCount > 0
    val firstItemFocusRequester = remember { FocusRequester() }
    var firstItemRendered by remember { mutableStateOf(false) }
    
    // Determine which category comes first
    val firstCategory = when {
        searchState.results.liveTV.isNotEmpty() -> "liveTV"
        searchState.results.vod.isNotEmpty() -> "vod"
        searchState.results.series.isNotEmpty() -> "series"
        else -> null
    }
    
    // Reset firstItemRendered when results change
    LaunchedEffect(searchState.results.totalCount) {
        firstItemRendered = false
    }
    
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 600.dp),
        verticalArrangement = Arrangement.spacedBy(Dimensions.spacingMD),
        contentPadding = PaddingValues(horizontal = Dimensions.spacingMD)
    ) {
        // LiveTV results
        if (searchState.results.liveTV.isNotEmpty()) {
            item {
                SearchSectionHeader("LiveTV (${searchState.results.liveTV.size})", colors)
            }
            items(searchState.results.liveTV) { stream ->
                val isFirstItem = firstCategory == "liveTV" && !firstItemRendered
                if (isFirstItem) {
                    firstItemRendered = true
                }
                SearchResultItem(
                    title = stream.name,
                    onClick = { onLiveStreamClick(stream) },
                    colors = colors,
                    focusRequester = if (isFirstItem) firstItemFocusRequester else null,
                    onNavigateUp = if (isFirstItem) onNavigateUp else null
                )
            }
        }
        
        // VOD results
        if (searchState.results.vod.isNotEmpty()) {
            item {
                SearchSectionHeader("Movies (${searchState.results.vod.size})", colors)
            }
            items(searchState.results.vod) { vod ->
                val isFirstItem = firstCategory == "vod" && !firstItemRendered
                if (isFirstItem) {
                    firstItemRendered = true
                }
                SearchResultItem(
                    title = vod.name,
                    onClick = { onVodClick(vod) },
                    colors = colors,
                    focusRequester = if (isFirstItem) firstItemFocusRequester else null,
                    onNavigateUp = if (isFirstItem) onNavigateUp else null
                )
            }
        }
        
        // Series results
        if (searchState.results.series.isNotEmpty()) {
            item {
                SearchSectionHeader("Series (${searchState.results.series.size})", colors)
            }
            items(searchState.results.series) { series ->
                val isFirstItem = firstCategory == "series" && !firstItemRendered
                if (isFirstItem) {
                    firstItemRendered = true
                }
                SearchResultItem(
                    title = series.name,
                    onClick = { onSeriesClick(series) },
                    colors = colors,
                    focusRequester = if (isFirstItem) firstItemFocusRequester else null,
                    onNavigateUp = if (isFirstItem) onNavigateUp else null
                )
            }
        }
    }
}

@Composable
private fun SearchSectionHeader(
    title: String,
    colors: com.flintr.tv.ui.theme.AppColors
) {
    Text(
        text = title,
        fontSize = FontSizes.fontSizeLG,
        fontWeight = FontWeight.Bold,
        color = colors.accentColor,
        modifier = Modifier.padding(
            top = Dimensions.spacingMD,
            bottom = Dimensions.spacingXS
        )
    )
}

@Composable
private fun SearchResultItem(
    title: String,
    onClick: () -> Unit,
    colors: com.flintr.tv.ui.theme.AppColors,
    focusRequester: FocusRequester? = null,
    onNavigateUp: (() -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    
    // Request focus when this is the first item and focusRequester is provided
    LaunchedEffect(focusRequester) {
        if (focusRequester != null) {
            focusRequester.safeRequestFocus("SearchOverlay", "SearchResultItem: $title")
        }
    }
    
    val backgroundColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isFocused) colors.bgHover else Color.Transparent,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
        label = "backgroundColor"
    )
    
    val borderColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isFocused) colors.accentColor else Color.Transparent,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
        label = "borderColor"
    )
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(Dimensions.radiusSM)
            )
            .then(
                if (borderColor != Color.Transparent) {
                    Modifier.border(
                        width = 2.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(Dimensions.radiusSM)
                    )
                } else {
                    Modifier
                }
            )
            .then(
                if (focusRequester != null) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                }
            )
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            }
            .focusable()
            .onKeyEvent { event ->
                // Navigate back to search field when Up is pressed on first item
                if (event.type == KeyEventType.KeyDown && 
                    event.key == Key.DirectionUp && 
                    onNavigateUp != null) {
                    onNavigateUp()
                    true
                } else {
                    false
                }
            }
            .onClick(onClick)
            .padding(
                horizontal = Dimensions.spacingMD,
                vertical = Dimensions.spacingSM
            )
    ) {
        Text(
            text = title,
            fontSize = FontSizes.fontSizeMD,
            color = if (isFocused) colors.textPrimary else colors.textSecondary,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
