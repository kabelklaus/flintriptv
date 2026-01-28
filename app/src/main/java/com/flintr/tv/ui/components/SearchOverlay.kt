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
import androidx.compose.material.icons.filled.Star
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
import com.flintr.tv.ui.utils.onLongPress
import com.flintr.tv.ui.utils.safeRequestFocus
import androidx.compose.ui.res.stringResource
import com.flintr.tv.R
import com.flintr.tv.data.local.FavoritesRepository
import com.flintr.tv.data.model.Favorite
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Callbacks for search result content clicks
 */
data class SearchResultCallbacks(
    val onLiveStreamClick: (LiveStream) -> Unit,
    val onVodClick: (VodStream) -> Unit,
    val onSeriesClick: (Series) -> Unit
)

/**
 * Sealed class for type-safe search content handling with favorite support
 */
private sealed class SearchContentType {
    abstract fun toFavoriteId(): String
    abstract fun toFavorite(): Favorite

    data class Live(val stream: LiveStream) : SearchContentType() {
        override fun toFavoriteId() = stream.toFavoriteId()
        override fun toFavorite() = stream.toFavorite()
    }

    data class Movie(val vod: VodStream) : SearchContentType() {
        override fun toFavoriteId() = vod.toFavoriteId()
        override fun toFavorite() = vod.toFavorite()
    }

    data class Series(val series: com.flintr.tv.data.model.Series) : SearchContentType() {
        override fun toFavoriteId() = series.toFavoriteId()
        override fun toFavorite() = series.toFavorite()
    }
}

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
    resultCallbacks: SearchResultCallbacks,
    colors: com.flintr.tv.ui.theme.AppColors,
    onNavigateUpToSearchField: () -> Unit,
    favoritesRepository: FavoritesRepository
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
                resultCallbacks = resultCallbacks,
                colors = colors,
                onNavigateUp = onNavigateUpToSearchField,
                favoritesRepository = favoritesRepository
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
    resultCallbacks: SearchResultCallbacks,
    favoritesRepository: FavoritesRepository
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
                resultCallbacks = resultCallbacks,
                colors = colors,
                onNavigateUpToSearchField = {
                    searchFieldFocusRequester.requestFocus()
                },
                favoritesRepository = favoritesRepository
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
    val categoryId: String,
    val toContentType: (T) -> SearchContentType
)

/**
 * Configuration for focus management in search sections
 */
private data class SearchSectionFocusConfig(
    val firstCategory: String?,
    val firstItemRendered: Boolean,
    val onFirstItemRendered: () -> Unit,
    val firstItemFocusRequester: FocusRequester,
    val onNavigateUp: () -> Unit
)

@Composable
private fun SearchResultsList(
    searchState: SearchState,
    resultCallbacks: SearchResultCallbacks,
    colors: com.flintr.tv.ui.theme.AppColors,
    onNavigateUp: () -> Unit,
    favoritesRepository: FavoritesRepository
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
                onClick = { resultCallbacks.onLiveStreamClick(it) },
                categoryId = "liveTV",
                toContentType = { SearchContentType.Live(it) }
            ))
        }
        if (searchState.results.vod.isNotEmpty()) {
            add(SearchSection(
                title = "Movies",
                items = searchState.results.vod,
                getName = { it.name },
                onClick = { resultCallbacks.onVodClick(it) },
                categoryId = "vod",
                toContentType = { SearchContentType.Movie(it) }
            ))
        }
        if (searchState.results.series.isNotEmpty()) {
            add(SearchSection(
                title = "Series",
                items = searchState.results.series,
                getName = { it.name },
                onClick = { resultCallbacks.onSeriesClick(it) },
                categoryId = "series",
                toContentType = { SearchContentType.Series(it) }
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
        val focusConfig = SearchSectionFocusConfig(
            firstCategory = firstCategory,
            firstItemRendered = firstItemRendered,
            onFirstItemRendered = { firstItemRendered = true },
            firstItemFocusRequester = firstItemFocusRequester,
            onNavigateUp = onNavigateUp
        )
        sections.forEach { section ->
            searchSection(
                section = section,
                focusConfig = focusConfig,
                colors = colors,
                favoritesRepository = favoritesRepository
            )
        }
    }
}

/**
 * Helper function to render a search section in LazyColumn
 */
private fun <T> LazyListScope.searchSection(
    section: SearchSection<T>,
    focusConfig: SearchSectionFocusConfig,
    colors: com.flintr.tv.ui.theme.AppColors,
    favoritesRepository: FavoritesRepository
) {
    item {
        SearchSectionHeader("${section.title} (${section.items.size})", colors)
    }
    items(section.items) { item ->
        val isFirstItem = section.categoryId == focusConfig.firstCategory && !focusConfig.firstItemRendered
        if (isFirstItem) {
            focusConfig.onFirstItemRendered()
        }
        SearchResultItem(
            title = section.getName(item),
            contentType = section.toContentType(item),
            onClick = { section.onClick(item) },
            colors = colors,
            focusRequester = if (isFirstItem) focusConfig.firstItemFocusRequester else null,
            onNavigateUp = if (isFirstItem) focusConfig.onNavigateUp else null,
            favoritesRepository = favoritesRepository
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

/**
 * Creates a conditional border modifier
 */
private fun Modifier.conditionalBorder(
    borderColor: Color,
    shape: RoundedCornerShape
): Modifier = if (borderColor != Color.Transparent) {
    this.border(width = 2.dp, color = borderColor, shape = shape)
} else {
    this
}

/**
 * Creates a conditional focus requester modifier
 */
private fun Modifier.conditionalFocusRequester(
    focusRequester: FocusRequester?
): Modifier = if (focusRequester != null) {
    this.focusRequester(focusRequester)
} else {
    this
}

/**
 * Handles key events for search result navigation
 */
private fun handleSearchResultKeyEvent(
    event: androidx.compose.ui.input.key.KeyEvent,
    onNavigateUp: (() -> Unit)?
): Boolean {
    val isUpPressed = event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp
    return if (isUpPressed && onNavigateUp != null) {
        onNavigateUp()
        true
    } else {
        false
    }
}

/**
 * Handles favorite toggle action
 */
private suspend fun handleFavoriteToggle(
    isFavorite: Boolean,
    favoriteId: String,
    contentType: SearchContentType,
    favoritesRepository: FavoritesRepository,
    onFavoriteChanged: (Boolean) -> Unit
) {
    if (isFavorite) {
        favoritesRepository.removeFavorite(favoriteId)
        onFavoriteChanged(false)
    } else {
        val favorite = contentType.toFavorite()
        favoritesRepository.addFavorite(favorite)
        onFavoriteChanged(true)
    }
}

@Composable
private fun SearchResultItem(
    title: String,
    contentType: SearchContentType,
    onClick: () -> Unit,
    colors: com.flintr.tv.ui.theme.AppColors,
    focusRequester: FocusRequester? = null,
    onNavigateUp: (() -> Unit)? = null,
    favoritesRepository: FavoritesRepository
) {
    var isFocused by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var longPressDetected by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val favoriteId = remember(contentType) { contentType.toFavoriteId() }
    var isFavorite by remember { mutableStateOf(false) }

    LaunchedEffect(favoriteId, showMenu) {
        isFavorite = favoritesRepository.isFavorite(favoriteId)
    }

    LaunchedEffect(longPressDetected) {
        if (!longPressDetected) return@LaunchedEffect
        delay(100)
        longPressDetected = false
    }

    LaunchedEffect(focusRequester) {
        focusRequester?.safeRequestFocus("SearchOverlay", "SearchResultItem: $title")
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

    val removeFromFavoritesText = stringResource(R.string.remove_from_favorites)
    val addToFavoritesText = stringResource(R.string.add_to_favorites)
    val menuOptions = remember(isFavorite, removeFromFavoritesText, addToFavoritesText) {
        if (isFavorite) listOf(removeFromFavoritesText) else listOf(addToFavoritesText)
    }

    val itemShape = RoundedCornerShape(Dimensions.radiusSM)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = backgroundColor, shape = itemShape)
            .conditionalBorder(borderColor, itemShape)
            .conditionalFocusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onKeyEvent { handleSearchResultKeyEvent(it, onNavigateUp) }
            .onLongPress(
                onLongPress = {
                    longPressDetected = true
                    showMenu = true
                },
                onNormalClick = {
                    if (!showMenu && !longPressDetected) onClick()
                }
            )
            .padding(horizontal = Dimensions.spacingMD, vertical = Dimensions.spacingSM)
    ) {
        SearchResultItemContent(title, isFavorite, isFocused, colors)
    }

    PopupMenu(
        isOpen = showMenu,
        menuOptions = menuOptions,
        onDismiss = { showMenu = false },
        onOptionSelected = {
            coroutineScope.launch {
                handleFavoriteToggle(isFavorite, favoriteId, contentType, favoritesRepository) {
                    isFavorite = it
                }
            }
        }
    )
}

@Composable
private fun SearchResultItemContent(
    title: String,
    isFavorite: Boolean,
    isFocused: Boolean,
    colors: com.flintr.tv.ui.theme.AppColors
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingSM)
    ) {
        if (isFavorite) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = colors.accentColor,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = title,
            fontSize = FontSizes.fontSizeMD,
            color = if (isFocused) colors.textPrimary else colors.textSecondary,
            modifier = Modifier.weight(1f)
        )
    }
}
