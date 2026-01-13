package com.flintr.tv.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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
 * Handles key events for the search field
 */
private fun handleSearchFieldKeyEvent(
    event: androidx.compose.ui.input.key.KeyEvent,
    hasResults: Boolean,
    onNavigateToResults: () -> Unit
): Boolean {
    return if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
        if (hasResults) {
            onNavigateToResults()
            true
        } else {
            false
        }
    } else {
        false
    }
}

/**
 * Renders search content (hints or results)
 */
@Composable
private fun SearchContent(
    searchState: SearchState,
    onLiveStreamClick: (LiveStream) -> Unit,
    onVodClick: (VodStream) -> Unit,
    onSeriesClick: (Series) -> Unit,
    colors: com.flintr.tv.ui.theme.AppColors,
    resultsListFocusRequester: FocusRequester,
    onNavigateUpToSearchField: () -> Unit
) {
    // Use trimmed length for display logic (original query kept for TextField)
    val effectiveQueryLength = searchState.query.trim().length
    
    when {
        searchState.showMinLengthHint -> {
            Text(
                text = "Mindestens 2 Zeichen eingeben",
                fontSize = FontSizes.fontSizeMD,
                color = colors.textSecondary,
                modifier = Modifier.padding(horizontal = Dimensions.spacingMD)
            )
        }
        effectiveQueryLength >= 2 && !searchState.isSearching && searchState.results.isEmpty -> {
            Text(
                text = "Keine Ergebnisse gefunden",
                fontSize = FontSizes.fontSizeMD,
                color = colors.textSecondary,
                modifier = Modifier.padding(horizontal = Dimensions.spacingMD)
            )
        }
        effectiveQueryLength >= 2 && searchState.results.totalCount > 0 -> {
            SearchResultsList(
                searchState = searchState,
                onLiveStreamClick = onLiveStreamClick,
                onVodClick = onVodClick,
                onSeriesClick = onSeriesClick,
                colors = colors,
                onNavigateUp = onNavigateUpToSearchField
            )
        }
    }
}

/**
 * Fullscreen search overlay optimized for TV navigation
 * Opens when search button is activated and provides a text input field with search results
 */
@Composable
fun SearchOverlay(
    searchState: SearchState,
    onQueryChange: (String) -> Unit,
    onSearchImmediate: () -> Unit,
    onDismiss: () -> Unit,
    onLiveStreamClick: (LiveStream) -> Unit,
    onVodClick: (VodStream) -> Unit,
    onSeriesClick: (Series) -> Unit
) {
    val colors = appColors()
    val searchFieldFocusRequester = remember { FocusRequester() }
    val resultsListFocusRequester = remember { FocusRequester() }
    
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
            .background(SharedColors.overlayBlack.copy(alpha = 0.95f))
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
                value = searchState.query,
                onValueChange = onQueryChange,
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
                        when {
                            event.type == KeyEventType.KeyDown && 
                            (event.key == Key.Enter || event.key == Key.DirectionCenter) -> {
                                onSearchImmediate()
                                true
                            }
                            else -> handleSearchFieldKeyEvent(
                                event = event,
                                hasResults = searchState.results.totalCount > 0,
                                onNavigateToResults = { resultsListFocusRequester.requestFocus() }
                            )
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
                ),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = ImeAction.Search,
                    keyboardType = KeyboardType.Text
                ),
                keyboardActions = KeyboardActions(
                    onSearch = { onSearchImmediate() },
                    onDone = { onSearchImmediate() }
                )
            )
            
            Spacer(modifier = Modifier.height(Dimensions.spacingMD))
            
            // Search results or hints
            SearchContent(
                searchState = searchState,
                onLiveStreamClick = onLiveStreamClick,
                onVodClick = onVodClick,
                onSeriesClick = onSeriesClick,
                colors = colors,
                resultsListFocusRequester = resultsListFocusRequester,
                onNavigateUpToSearchField = {
                    searchFieldFocusRequester.requestFocus()
                }
            )
        }
    }
}

/**
 * Data class for type-safe search section definitions
 */
private data class SearchSection<T>(
    val title: String,
    val items: List<T>,
    val getName: (T) -> String,
    val onClick: (T) -> Unit,
    val categoryId: String
)

@Composable
private fun SearchResultsList(
    searchState: SearchState,
    onLiveStreamClick: (LiveStream) -> Unit,
    onVodClick: (VodStream) -> Unit,
    onSeriesClick: (Series) -> Unit,
    colors: com.flintr.tv.ui.theme.AppColors,
    onNavigateUp: () -> Unit
) {
    val firstItemFocusRequester = remember { FocusRequester() }
    var firstItemRendered by remember { mutableStateOf(false) }
    
    // Build sections list
    val sections = buildList {
        if (searchState.results.liveTV.isNotEmpty()) {
            add(SearchSection(
                title = "LiveTV",
                items = searchState.results.liveTV,
                getName = { it.name },
                onClick = { onLiveStreamClick(it) },
                categoryId = "liveTV"
            ))
        }
        if (searchState.results.vod.isNotEmpty()) {
            add(SearchSection(
                title = "Movies",
                items = searchState.results.vod,
                getName = { it.name },
                onClick = { onVodClick(it) },
                categoryId = "vod"
            ))
        }
        if (searchState.results.series.isNotEmpty()) {
            add(SearchSection(
                title = "Series",
                items = searchState.results.series,
                getName = { it.name },
                onClick = { onSeriesClick(it) },
                categoryId = "series"
            ))
        }
    }
    
    val firstCategory = sections.firstOrNull()?.categoryId
    
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
        sections.forEach { section ->
            searchSection(
                section = section,
                firstCategory = firstCategory,
                firstItemRendered = firstItemRendered,
                onFirstItemRendered = { firstItemRendered = true },
                colors = colors,
                firstItemFocusRequester = firstItemFocusRequester,
                onNavigateUp = onNavigateUp
            )
        }
    }
}

/**
 * Helper function to render a search section in LazyColumn
 */
private fun <T> LazyListScope.searchSection(
    section: SearchSection<T>,
    firstCategory: String?,
    firstItemRendered: Boolean,
    onFirstItemRendered: () -> Unit,
    colors: com.flintr.tv.ui.theme.AppColors,
    firstItemFocusRequester: FocusRequester,
    onNavigateUp: () -> Unit
) {
    item {
        SearchSectionHeader("${section.title} (${section.items.size})", colors)
    }
    items(section.items) { item ->
        val isFirstItem = section.categoryId == firstCategory && !firstItemRendered
        if (isFirstItem) {
            onFirstItemRendered()
        }
        SearchResultItem(
            title = section.getName(item),
            onClick = { section.onClick(item) },
            colors = colors,
            focusRequester = if (isFirstItem) firstItemFocusRequester else null,
            onNavigateUp = if (isFirstItem) onNavigateUp else null
        )
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
