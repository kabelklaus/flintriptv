package com.flintr.tv.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.flintr.tv.data.model.LiveStream
import com.flintr.tv.data.model.SearchScope
import com.flintr.tv.data.model.SearchState
import com.flintr.tv.data.model.Series
import com.flintr.tv.data.model.VodStream
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.FontSizes
import com.flintr.tv.ui.theme.SharedColors
import com.flintr.tv.ui.theme.FocusRing
import com.flintr.tv.ui.theme.appColors
import com.flintr.tv.ui.theme.focusRingColor
import com.flintr.tv.ui.utils.onClick
import com.flintr.tv.ui.utils.onLongPress
import com.flintr.tv.ui.utils.safeRequestFocus
import androidx.compose.ui.res.stringResource
import com.flintr.tv.R
import com.flintr.tv.data.local.WatchlistRepository
import com.flintr.tv.data.model.WatchlistEntry
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
    abstract fun toWatchlistEntry(): WatchlistEntry

    data class Live(val stream: LiveStream) : SearchContentType() {
        override fun toFavoriteId() = stream.toFavoriteId()
        override fun toWatchlistEntry() = stream.toWatchlistEntry()
    }

    data class Movie(val vod: VodStream) : SearchContentType() {
        override fun toFavoriteId() = vod.toFavoriteId()
        override fun toWatchlistEntry() = vod.toWatchlistEntry()
    }

    data class Series(val series: com.flintr.tv.data.model.Series) : SearchContentType() {
        override fun toFavoriteId() = series.toFavoriteId()
        override fun toWatchlistEntry() = series.toWatchlistEntry()
    }
}

private sealed class SearchOverlayFocusTarget {
    object SearchField : SearchOverlayFocusTarget()
    data class ScopeFilter(val index: Int) : SearchOverlayFocusTarget()
    object Results : SearchOverlayFocusTarget()
}

/**
 * Navigation callbacks shared by the scope filter row and its chips
 */
private data class ScopeRowNavigation(
    val onNavigateUp: () -> Boolean,
    val onNavigateDown: () -> Boolean,
    val hasResults: Boolean
)

/**
 * Focus handling configuration for a single search result row
 */
private data class SearchResultFocus(
    val focusRequester: FocusRequester?,
    val onNavigateUp: (() -> Unit)?,
    val isLastResult: Boolean,
    val onFocused: () -> Unit
)

/**
 * Handles key events for the search field
 */
private fun handleSearchFieldKeyEvent(
    event: androidx.compose.ui.input.key.KeyEvent,
    onNavigateDown: () -> Boolean
): Boolean {
    return if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
        onNavigateDown()
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
    onNavigateUpToScopes: () -> Unit,
    firstResultFocusRequester: FocusRequester,
    onResultFocused: () -> Unit,
    watchlistRepository: WatchlistRepository
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
        searchState.hasSearched &&
            effectiveQueryLength >= 2 &&
            !searchState.isSearching &&
            searchState.results.isEmpty -> {
            Text(
                text = "Keine Ergebnisse gefunden",
                fontSize = FontSizes.fontSizeMD,
                color = colors.textSecondary,
                modifier = Modifier.padding(horizontal = Dimensions.spacingMD)
            )
        }
        searchState.hasSearched && effectiveQueryLength >= 2 && searchState.results.totalCount > 0 -> {
            SearchResultsList(
                searchState = searchState,
                resultCallbacks = resultCallbacks,
                colors = colors,
                onNavigateUp = onNavigateUpToScopes,
                firstItemFocusRequester = firstResultFocusRequester,
                onResultFocused = onResultFocused,
                watchlistRepository = watchlistRepository
            )
        }
    }
}

@Composable
private fun SearchScopeFilters(
    selectedScopes: Set<SearchScope>,
    onScopeToggle: (SearchScope) -> Unit,
    colors: com.flintr.tv.ui.theme.AppColors,
    scopeFocusRequesters: List<FocusRequester>,
    navigation: ScopeRowNavigation,
    onScopeFocused: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingSM),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SearchScope.orderedScopes.forEachIndexed { index, scope ->
            SearchScopeFilterChip(
                scope = scope,
                selected = scope in selectedScopes,
                onClick = { onScopeToggle(scope) },
                navigation = navigation,
                modifier = Modifier
                    .focusRequester(scopeFocusRequesters[index]),
                onFocused = { onScopeFocused(index) },
                colors = colors
            )
        }
    }
}

private fun handleSearchScopeKeyEvent(
    event: androidx.compose.ui.input.key.KeyEvent,
    navigation: ScopeRowNavigation
): Boolean {
    return when {
        event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp -> navigation.onNavigateUp()
        event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown && navigation.hasResults -> navigation.onNavigateDown()
        event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown -> true
        else -> false
    }
}

private fun handleOverlayDownKey(
    currentFocusTarget: SearchOverlayFocusTarget?,
    hasResults: Boolean,
    onFocusScope: (Int) -> Boolean,
    onFocusResults: () -> Boolean
): Boolean = when (currentFocusTarget) {
    SearchOverlayFocusTarget.SearchField -> onFocusScope(0)
    is SearchOverlayFocusTarget.ScopeFilter -> !hasResults || onFocusResults()
    else -> false
}

private fun handleOverlayUpKey(
    currentFocusTarget: SearchOverlayFocusTarget?,
    onFocusSearchField: () -> Boolean
): Boolean = when (currentFocusTarget) {
    SearchOverlayFocusTarget.SearchField -> true
    is SearchOverlayFocusTarget.ScopeFilter -> onFocusSearchField()
    else -> false
}

private fun handleOverlayHorizontalKey(
    currentFocusTarget: SearchOverlayFocusTarget?,
    delta: Int,
    scopeCount: Int,
    onFocusScope: (Int) -> Boolean
): Boolean {
    if (currentFocusTarget !is SearchOverlayFocusTarget.ScopeFilter) {
        return false
    }
    val targetIndex = currentFocusTarget.index + delta
    return if (targetIndex in 0 until scopeCount) {
        onFocusScope(targetIndex)
    } else {
        true
    }
}

private fun handleSearchOverlayPreviewKeyEvent(
    event: androidx.compose.ui.input.key.KeyEvent,
    currentFocusTarget: SearchOverlayFocusTarget?,
    scopeCount: Int,
    hasResults: Boolean,
    onFocusSearchField: () -> Boolean,
    onFocusScope: (Int) -> Boolean,
    onFocusResults: () -> Boolean
): Boolean {
    if (event.type != KeyEventType.KeyDown) {
        return false
    }

    return when (event.key) {
        Key.DirectionDown -> handleOverlayDownKey(currentFocusTarget, hasResults, onFocusScope, onFocusResults)
        Key.DirectionUp -> handleOverlayUpKey(currentFocusTarget, onFocusSearchField)
        Key.DirectionLeft -> handleOverlayHorizontalKey(currentFocusTarget, -1, scopeCount, onFocusScope)
        Key.DirectionRight -> handleOverlayHorizontalKey(currentFocusTarget, 1, scopeCount, onFocusScope)
        else -> false
    }
}

@Composable
private fun SearchScopeFilterChip(
    scope: SearchScope,
    selected: Boolean,
    onClick: () -> Unit,
    navigation: ScopeRowNavigation,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    colors: com.flintr.tv.ui.theme.AppColors
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(Dimensions.radiusSM)
    val backgroundColor = when {
        selected -> colors.selectionFill
        isFocused -> colors.accentColorHoverBg
        else -> Color.Transparent
    }
    // Der Fokusrahmen ersetzt die dekorative Umrandung, solange der Fokus hier
    // steht. Die Selektion allein zeichnet keinen Rahmen.
    val focusRing = focusRingColor(isFocused = isFocused, onSelectionFill = selected)
    val borderColor = if (focusRing == Color.Transparent) colors.cardBorder else focusRing
    val borderWidth = if (isFocused) FocusRing.width else 1.dp
    val textColor = if (selected) Color.White else colors.textSecondary

    Box(
        modifier = modifier
            .background(backgroundColor, shape)
            .border(width = borderWidth, color = borderColor, shape = shape)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) {
                    onFocused()
                }
            }
            .focusable()
            .onKeyEvent {
                handleSearchScopeKeyEvent(
                    event = it,
                    navigation = navigation
                )
            }
            .onClick(onClick)
            .padding(
                horizontal = Dimensions.spacingMD,
                vertical = Dimensions.spacingXS
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = scope.label,
            fontSize = FontSizes.fontSizeMD,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
            color = textColor
        )
    }
}

/**
 * Resolves a pending focus request. Returns true when the request was handled
 * so the caller can clear it; a Results request stays pending until results exist.
 */
private suspend fun resolvePendingFocus(
    target: SearchOverlayFocusTarget,
    searchFieldFocusRequester: FocusRequester,
    scopeFocusRequesters: List<FocusRequester>,
    resultsListFocusRequester: FocusRequester,
    resultCount: Int
): Boolean = when (target) {
    SearchOverlayFocusTarget.SearchField -> {
        searchFieldFocusRequester.safeRequestFocus("SearchOverlay", "SearchField")
        true
    }
    is SearchOverlayFocusTarget.ScopeFilter -> {
        scopeFocusRequesters
            .getOrNull(target.index)
            ?.safeRequestFocus("SearchOverlay", "SearchScope ${target.index}")
        true
    }
    SearchOverlayFocusTarget.Results -> {
        if (resultCount > 0) {
            resultsListFocusRequester.safeRequestFocus("SearchOverlay", "SearchResults")
            true
        } else {
            false
        }
    }
}

/**
 * Handles key events for the search input field (confirm keys trigger immediate search)
 */
private fun handleSearchFieldInputKeyEvent(
    event: androidx.compose.ui.input.key.KeyEvent,
    onSearchImmediate: () -> Unit,
    onNavigateDown: () -> Boolean
): Boolean {
    val isConfirmKey = event.key == Key.Enter || event.key == Key.DirectionCenter
    if (event.type == KeyEventType.KeyDown && isConfirmKey) {
        onSearchImmediate()
        return true
    }
    return handleSearchFieldKeyEvent(event = event, onNavigateDown = onNavigateDown)
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
    onSearchScopeToggle: (SearchScope) -> Unit,
    onDismiss: () -> Unit,
    resultCallbacks: SearchResultCallbacks,
    watchlistRepository: WatchlistRepository
) {
    val colors = appColors()
    val searchFieldFocusRequester = remember { FocusRequester() }
    val scopeFocusRequesters = remember {
        List(SearchScope.orderedScopes.size) { FocusRequester() }
    }
    val resultsListFocusRequester = remember { FocusRequester() }
    var currentFocusTarget by remember {
        mutableStateOf<SearchOverlayFocusTarget?>(SearchOverlayFocusTarget.SearchField)
    }
    var pendingFocusTarget by remember { mutableStateOf<SearchOverlayFocusTarget?>(null) }

    fun requestOverlayFocus(target: SearchOverlayFocusTarget): Boolean {
        pendingFocusTarget = target
        return true
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

    LaunchedEffect(pendingFocusTarget, searchState.results.totalCount) {
        val target = pendingFocusTarget ?: return@LaunchedEffect
        val handled = resolvePendingFocus(
            target = target,
            searchFieldFocusRequester = searchFieldFocusRequester,
            scopeFocusRequesters = scopeFocusRequesters,
            resultsListFocusRequester = resultsListFocusRequester,
            resultCount = searchState.results.totalCount
        )
        if (handled) {
            pendingFocusTarget = null
        }
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
            .onPreviewKeyEvent {
                handleSearchOverlayPreviewKeyEvent(
                    event = it,
                    currentFocusTarget = currentFocusTarget,
                    scopeCount = scopeFocusRequesters.size,
                    hasResults = searchState.results.totalCount > 0,
                    onFocusSearchField = { requestOverlayFocus(SearchOverlayFocusTarget.SearchField) },
                    onFocusScope = { requestOverlayFocus(SearchOverlayFocusTarget.ScopeFilter(it)) },
                    onFocusResults = { requestOverlayFocus(SearchOverlayFocusTarget.Results) }
                )
            }
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
                    .onFocusChanged {
                        if (it.isFocused) {
                            currentFocusTarget = SearchOverlayFocusTarget.SearchField
                        }
                    }
                    .onKeyEvent { event ->
                        handleSearchFieldInputKeyEvent(
                            event = event,
                            onSearchImmediate = onSearchImmediate,
                            onNavigateDown = {
                                requestOverlayFocus(SearchOverlayFocusTarget.ScopeFilter(0))
                            }
                        )
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

            SearchScopeFilters(
                selectedScopes = searchState.selectedScopes,
                onScopeToggle = onSearchScopeToggle,
                colors = colors,
                scopeFocusRequesters = scopeFocusRequesters,
                navigation = ScopeRowNavigation(
                    onNavigateUp = {
                        requestOverlayFocus(SearchOverlayFocusTarget.SearchField)
                    },
                    onNavigateDown = {
                        requestOverlayFocus(SearchOverlayFocusTarget.Results)
                    },
                    hasResults = searchState.results.totalCount > 0
                ),
                onScopeFocused = {
                    currentFocusTarget = SearchOverlayFocusTarget.ScopeFilter(it)
                }
            )

            Spacer(modifier = Modifier.height(Dimensions.spacingMD))
            
            // Search results or hints
            SearchContent(
                searchState = searchState,
                resultCallbacks = resultCallbacks,
                colors = colors,
                onNavigateUpToScopes = {
                    requestOverlayFocus(SearchOverlayFocusTarget.ScopeFilter(0))
                },
                firstResultFocusRequester = resultsListFocusRequester,
                onResultFocused = {
                    currentFocusTarget = SearchOverlayFocusTarget.Results
                },
                watchlistRepository = watchlistRepository
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
    val firstItemFocusRequester: FocusRequester,
    val onNavigateUp: () -> Unit,
    val onResultFocused: () -> Unit
)

@Composable
private fun SearchResultsList(
    searchState: SearchState,
    resultCallbacks: SearchResultCallbacks,
    colors: com.flintr.tv.ui.theme.AppColors,
    onNavigateUp: () -> Unit,
    firstItemFocusRequester: FocusRequester,
    onResultFocused: () -> Unit,
    watchlistRepository: WatchlistRepository
) {
    // Build sections list
    val sections = buildList {
        if (searchState.results.liveTV.isNotEmpty()) {
            add(SearchSection(
                title = "TV",
                items = searchState.results.liveTV,
                getName = { it.name },
                onClick = { resultCallbacks.onLiveStreamClick(it) },
                categoryId = "liveTV",
                toContentType = { SearchContentType.Live(it) }
            ))
        }
        if (searchState.results.vod.isNotEmpty()) {
            add(SearchSection(
                title = "Filme",
                items = searchState.results.vod,
                getName = { it.name },
                onClick = { resultCallbacks.onVodClick(it) },
                categoryId = "vod",
                toContentType = { SearchContentType.Movie(it) }
            ))
        }
        if (searchState.results.series.isNotEmpty()) {
            add(SearchSection(
                title = "Serien",
                items = searchState.results.series,
                getName = { it.name },
                onClick = { resultCallbacks.onSeriesClick(it) },
                categoryId = "series",
                toContentType = { SearchContentType.Series(it) }
            ))
        }
    }
    
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 600.dp),
        verticalArrangement = Arrangement.spacedBy(Dimensions.spacingMD),
        contentPadding = PaddingValues(horizontal = Dimensions.spacingMD)
    ) {
        val focusConfig = SearchSectionFocusConfig(
            firstItemFocusRequester = firstItemFocusRequester,
            onNavigateUp = onNavigateUp,
            onResultFocused = onResultFocused
        )
        var startIndex = 0
        sections.forEach { section ->
            searchSection(
                section = section,
                focusConfig = focusConfig,
                startIndex = startIndex,
                totalItems = searchState.results.totalCount,
                colors = colors,
                watchlistRepository = watchlistRepository
            )
            startIndex += section.items.size
        }
    }
}

/**
 * Helper function to render a search section in LazyColumn
 */
private fun <T> LazyListScope.searchSection(
    section: SearchSection<T>,
    focusConfig: SearchSectionFocusConfig,
    startIndex: Int,
    totalItems: Int,
    colors: com.flintr.tv.ui.theme.AppColors,
    watchlistRepository: WatchlistRepository
) {
    item {
        SearchSectionHeader("${section.title} (${section.items.size})", colors)
    }
    itemsIndexed(section.items) { index, item ->
        val resultIndex = startIndex + index
        val isFirstItem = resultIndex == 0
        SearchResultItem(
            title = section.getName(item),
            contentType = section.toContentType(item),
            onClick = { section.onClick(item) },
            colors = colors,
            focus = SearchResultFocus(
                focusRequester = if (isFirstItem) focusConfig.firstItemFocusRequester else null,
                onNavigateUp = if (isFirstItem) focusConfig.onNavigateUp else null,
                isLastResult = resultIndex == totalItems - 1,
                onFocused = focusConfig.onResultFocused
            ),
            watchlistRepository = watchlistRepository
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
    onNavigateUp: (() -> Unit)?,
    isLastResult: Boolean
): Boolean {
    if (event.type != KeyEventType.KeyDown) {
        return false
    }

    return when (event.key) {
        Key.DirectionUp -> {
            if (onNavigateUp != null) {
                onNavigateUp()
                true
            } else {
                false
            }
        }
        Key.DirectionDown -> isLastResult
        Key.DirectionLeft,
        Key.DirectionRight -> true
        else -> false
    }
}

/**
 * Handles favorite toggle action
 */
private suspend fun handleFavoriteToggle(
    isFavorite: Boolean,
    favoriteId: String,
    contentType: SearchContentType,
    watchlistRepository: WatchlistRepository,
    onFavoriteChanged: (Boolean) -> Unit
) {
    if (isFavorite) {
        watchlistRepository.removeFromWatchlist(favoriteId)
        onFavoriteChanged(false)
    } else {
        val favorite = contentType.toWatchlistEntry()
        watchlistRepository.addToWatchlist(favorite)
        onFavoriteChanged(true)
    }
}

@Composable
private fun SearchResultItem(
    title: String,
    contentType: SearchContentType,
    onClick: () -> Unit,
    colors: com.flintr.tv.ui.theme.AppColors,
    focus: SearchResultFocus,
    watchlistRepository: WatchlistRepository
) {
    var isFocused by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val favoriteId = remember(contentType) { contentType.toFavoriteId() }
    var isInWatchlist by remember { mutableStateOf(false) }

    LaunchedEffect(favoriteId, showMenu) {
        isInWatchlist = watchlistRepository.isInWatchlist(favoriteId)
    }

    LaunchedEffect(focus.focusRequester) {
        focus.focusRequester?.safeRequestFocus("SearchOverlay", "SearchResultItem: $title")
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

    val removeFromFavoritesText = stringResource(R.string.remove_from_watchlist)
    val addToFavoritesText = stringResource(R.string.add_to_watchlist)
    val menuOptions = remember(isInWatchlist, removeFromFavoritesText, addToFavoritesText) {
        if (isInWatchlist) listOf(removeFromFavoritesText) else listOf(addToFavoritesText)
    }

    val itemShape = RoundedCornerShape(Dimensions.radiusSM)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = backgroundColor, shape = itemShape)
            .conditionalBorder(borderColor, itemShape)
            .conditionalFocusRequester(focus.focusRequester)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) {
                    focus.onFocused()
                }
            }
            .focusable()
            .onKeyEvent { handleSearchResultKeyEvent(it, focus.onNavigateUp, focus.isLastResult) }
            .onLongPress(
                onLongPress = { showMenu = true },
                onNormalClick = onClick
            )
            .padding(horizontal = Dimensions.spacingMD, vertical = Dimensions.spacingSM)
    ) {
        SearchResultItemContent(title, isInWatchlist, isFocused, colors)
    }

    PopupMenu(
        isOpen = showMenu,
        menuOptions = menuOptions,
        onDismiss = { showMenu = false },
        onOptionSelected = {
            coroutineScope.launch {
                handleFavoriteToggle(isInWatchlist, favoriteId, contentType, watchlistRepository) {
                    isInWatchlist = it
                }
            }
        }
    )
}

@Composable
private fun SearchResultItemContent(
    title: String,
    isInWatchlist: Boolean,
    isFocused: Boolean,
    colors: com.flintr.tv.ui.theme.AppColors
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingSM)
    ) {
        if (isInWatchlist) {
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
