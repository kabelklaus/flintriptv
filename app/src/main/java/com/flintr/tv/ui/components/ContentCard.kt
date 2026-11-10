package com.flintr.tv.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import androidx.compose.foundation.Image
import android.util.Log
import com.flintr.tv.data.local.WatchlistRepository
import com.flintr.tv.data.local.WatchProgressService
import com.flintr.tv.data.model.WatchProgress
import com.flintr.tv.data.model.WatchlistEntry
import com.flintr.tv.data.model.LiveStream
import com.flintr.tv.data.model.Series
import com.flintr.tv.data.model.VodStream
import com.flintr.tv.ui.components.detail.formatRuntime
import com.flintr.tv.ui.theme.CardTitleStyle
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.SharedColors
import com.flintr.tv.ui.theme.appColors
import com.flintr.tv.ui.theme.tvFocusRing
import com.flintr.tv.ui.utils.onDpadDirection
import com.flintr.tv.ui.utils.onLongPress
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import com.flintr.tv.R
import kotlinx.coroutines.launch

// Content Card dimensions (from Flutter AppTheme)
// Using Dimensions object for single source of truth
private val ContentCardHeight = Dimensions.contentCardMaxCrossAxisExtent / Dimensions.contentCardAspectRatio
private val ContentCardBorderRadius = 8.dp // radiusMD

// Content Card Title Overlay (from Flutter AppTheme)
private val ContentCardTitleOverlayPadding = 1.dp
private val ContentCardTitleContainerPaddingH = 1.dp
private val ContentCardTitleContainerPaddingV = 1.dp
private val ContentCardTitleFontSize = CardTitleStyle.fontSize
private val ContentCardTitleMaxLines = 2
private val ContentCardTitleGradientAlpha = 0.7f
private val ContentCardTitleBgAlpha = 0.75f

// Friend avatar and watchlist badge share one shape so the top-left badge row reads as one
// family: both use the same radiusSM rounding as the rating chip on the opposite corner.
// The friend initial keeps its larger footprint/text for legibility; only the shape is unified.
private val ContentCardFriendAvatarSize = 24.dp
private val ContentCardFriendAvatarFontSize = 13.sp
private val ContentCardWatchlistBadgeSize = Dimensions.iconSizeMD
private val ContentCardWatchlistIconSize = 12.dp
private val ContentCardBadgeSpacing = 3.dp
private val ContentCardLogoPadding = 8.dp

// Rating-Badge-Zahl: bewusst alter Stil, bleibt wie vor dem Titel-Resizing (14sp/Bold).
private val ContentCardRatingFontSize = 14.sp
private val ContentCardRatingFontWeight = FontWeight.Bold

// Content Card Colors (from Flutter AppTheme)
private val ContentCardTextColor = Color.White
private val ContentCardBgColor = SharedColors.contentCardBgColor
private val ContentCardTextFontWeight = CardTitleStyle.fontWeight

// Content Card Title Line Height
private const val ContentCardTitleLineHeightFactor = 1.2f

// Watch Progress Display Threshold
private const val WATCH_PROGRESS_DISPLAY_THRESHOLD = 0.9f

data class CardConfig(
    val isSelected: Boolean = false,
    val onFocusChanged: ((Boolean) -> Unit)? = null,
    val onNavigateLeft: (() -> Unit)? = null,
    val focusRequester: androidx.compose.ui.focus.FocusRequester? = null
)

data class VODCardParams(
    val vod: VodStream? = null,
    val series: Series? = null,
    val onClick: () -> Unit,
    val watchlistRepository: WatchlistRepository,
    val watchProgressService: WatchProgressService? = null,
    val modifier: Modifier = Modifier,
    val isSelected: Boolean = false,
    val onFocusChanged: ((Boolean) -> Unit)? = null,
    val onNavigateLeft: (() -> Unit)? = null,
    val focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    val isWatched: Boolean = false,
    val sourceBadge: String? = null,
    val allowWatchlistActions: Boolean = true,
    /** Progress resolved from the grid's reactive map; avoids a per-card suspend lookup. */
    val watchProgress: WatchProgress? = null,
    val watchProgressResolved: Boolean = false
)

data class LiveStreamCardParams(
    val stream: LiveStream,
    val onClick: () -> Unit,
    val watchlistRepository: WatchlistRepository,
    val modifier: Modifier = Modifier,
    val config: CardConfig = CardConfig(),
    val sourceBadge: String? = null,
    val allowWatchlistActions: Boolean = true
)

private data class CardVisualState(
    val shadowElevation: Float,
    val backgroundColor: Color,
    val isFocused: Boolean
)

private data class CardInteractionHandlers(
    val onFocusChanged: (androidx.compose.ui.focus.FocusState) -> Unit,
    val onLongPress: () -> Unit,
    val onNormalClick: () -> Unit,
    val performClick: () -> Unit
)

private data class ContentInfo(
    val coverImageUrl: String?,
    val title: String,
    val rating: String?,
    /** Live channels render a sender logo, not a poster: fit it instead of cropping. */
    val isLogo: Boolean = false
)

private data class WatchState(
    val isWatched: Boolean = false,
    val progressPercent: Float = 0f,
    val remainingMinutes: Int? = null
)

private data class CardOverlayParams(
    val rating: String?,
    val isInWatchlist: Boolean,
    val sourceBadge: String?,
    val watchState: WatchState,
    val colors: com.flintr.tv.ui.theme.AppColors
)

private data class ContentCardData(
    val contentInfo: ContentInfo,
    val contentType: ContentType? = null,
    val watchState: WatchState = WatchState(),
    val sourceBadge: String? = null,
    val allowWatchlistActions: Boolean = true
)

private sealed class ContentType {
    abstract fun toWatchlistEntryId(): String
    abstract fun toWatchlistEntry(metadata: Map<String, String?>? = null): WatchlistEntry

    data class Live(val stream: LiveStream) : ContentType() {
        override fun toWatchlistEntryId(): String = stream.toWatchlistEntryId()
        override fun toWatchlistEntry(metadata: Map<String, String?>?): WatchlistEntry = stream.toWatchlistEntry(metadata)
    }

    data class Movie(val vod: VodStream) : ContentType() {
        override fun toWatchlistEntryId(): String = vod.toWatchlistEntryId()
        override fun toWatchlistEntry(metadata: Map<String, String?>?): WatchlistEntry = vod.toWatchlistEntry(metadata)
    }

    data class Series(val seriesData: com.flintr.tv.data.model.Series) : ContentType() {
        override fun toWatchlistEntryId(): String = seriesData.toWatchlistEntryId()
        override fun toWatchlistEntry(metadata: Map<String, String?>?): WatchlistEntry = seriesData.toWatchlistEntry(metadata)
    }
}

@Composable
fun VODCard(params: VODCardParams) {
    val contentInfo = when {
        params.vod != null -> ContentInfo(
            coverImageUrl = params.vod.streamIcon ?: params.vod.cover,
            title = params.vod.name,
            rating = rememberTmdbRating("movie", params.vod.tmdbId),
            isLogo = false
        )
        params.series != null -> ContentInfo(
            coverImageUrl = params.series.cover,
            title = params.series.name,
            rating = rememberTmdbRating("tv", params.series.tmdbId),
            isLogo = false
        )
        else -> throw IllegalArgumentException("Either vod or series must be provided")
    }

    val contentType = when {
        params.vod != null -> ContentType.Movie(params.vod)
        params.series != null -> ContentType.Series(params.series)
        else -> throw IllegalArgumentException("Either vod or series must be provided")
    }

    // Grids can resolve progress reactively and hand it down; otherwise fall back to one suspend
    // lookup per card (existing behaviour for call sites outside the VOD grid).
    val resolvedWatchState = remember(
        params.watchProgress,
        params.isWatched,
        params.watchProgressResolved
    ) {
        if (params.watchProgressResolved) {
            params.watchProgress.toWatchState(params.isWatched)
        } else {
            WatchState(isWatched = params.isWatched)
        }
    }
    var loadedWatchState by remember { mutableStateOf(resolvedWatchState) }

    LaunchedEffect(params.vod?.streamId, params.isWatched, params.watchProgressResolved) {
        if (!params.watchProgressResolved &&
            params.vod != null &&
            params.watchProgressService != null &&
            !params.isWatched
        ) {
            loadedWatchState = params.watchProgressService.getWatchProgress(
                contentType = "movie",
                streamId = params.vod.streamId,
                seriesId = null,
                episodeId = null
            ).toWatchState(isWatched = false)
        } else {
            loadedWatchState = WatchState(isWatched = params.isWatched)
        }
    }

    val watchState = if (params.watchProgressResolved) resolvedWatchState else loadedWatchState

    ContentCardInternal(
        cardData = ContentCardData(
            contentInfo = contentInfo,
            contentType = contentType,
            watchState = watchState,
            sourceBadge = params.sourceBadge,
            allowWatchlistActions = params.allowWatchlistActions
        ),
        onClick = params.onClick,
        modifier = params.modifier,
        config = CardConfig(
            isSelected = params.isSelected,
            onFocusChanged = params.onFocusChanged,
            onNavigateLeft = params.onNavigateLeft,
            focusRequester = params.focusRequester
        ),
        watchlistRepository = params.watchlistRepository
    )
}

private fun WatchProgress?.toWatchState(isWatched: Boolean): WatchState {
    if (this == null || duration <= 0L) return WatchState(isWatched = isWatched)
    if (isWatched) return WatchState(isWatched = true)
    val percent = position.toFloat() / duration.toFloat()
    return if (percent > 0f && percent < WATCH_PROGRESS_DISPLAY_THRESHOLD) {
        WatchState(
            isWatched = false,
            progressPercent = percent,
            remainingMinutes = ((duration - position) / 60000).toInt()
        )
    } else {
        WatchState(isWatched = false)
    }
}

private fun Modifier.shadowModifier(
    isSelected: Boolean,
    colors: com.flintr.tv.ui.theme.AppColors,
    shadowElevation: Float
): Modifier {
    return if (isSelected) {
        this.shadow(
            elevation = shadowElevation.dp,
            shape = RoundedCornerShape(ContentCardBorderRadius),
            spotColor = colors.accentColor.copy(alpha = Dimensions.categoryShadowAlpha),
            ambientColor = colors.accentColor.copy(alpha = Dimensions.categoryShadowAlpha)
        )
    } else {
        this
    }
}

private fun Modifier.focusRequesterModifier(
    focusRequester: androidx.compose.ui.focus.FocusRequester?
): Modifier {
    return if (focusRequester != null) {
        this.focusRequester(focusRequester)
    } else {
        this
    }
}

private fun buildDpadHandler(
    onNavigateLeft: (() -> Unit)?
): (() -> Boolean)? {
    return if (onNavigateLeft != null) {
        {
            onNavigateLeft()
            true
        }
    } else {
        null
    }
}

// Sonar/Lint verlangt hier eine Non-Composable Modifier-Factory. buildCardModifier
// baut auf tvFocusRing auf (siehe dortige Begründung) und ist Teil desselben, gerade
// stabilisierten TV-Fokus-Pfads; ein Refactor beider Stellen zusammen birgt unnötiges
// Regressionsrisiko. Bewusst zurückgestellt.
@Suppress("ComposableModifierFactory")
@Composable
private fun Modifier.buildCardModifier(
    config: CardConfig,
    colors: com.flintr.tv.ui.theme.AppColors,
    visualState: CardVisualState,
    handlers: CardInteractionHandlers
): Modifier {
    return this
        .width(Dimensions.contentCardMaxCrossAxisExtent)
        .aspectRatio(Dimensions.contentCardAspectRatio)
        .shadowModifier(config.isSelected, colors, visualState.shadowElevation)
        .tvFocusRing(
            isFocused = visualState.isFocused,
            onSelectionFill = config.isSelected,
            shape = RoundedCornerShape(ContentCardBorderRadius)
        )
        .focusRequesterModifier(config.focusRequester)
        .onFocusChanged(handlers.onFocusChanged)
        .focusable()
        .onDpadDirection(
            onLeft = buildDpadHandler(config.onNavigateLeft),
            onRight = null,
            onUp = null,
            onDown = null
        )
        .onLongPress(
            onLongPress = handlers.onLongPress,
            onNormalClick = handlers.onNormalClick
        )
}

@Composable
private fun ContentCardInternal(
    cardData: ContentCardData,
    onClick: () -> Unit,
    watchlistRepository: WatchlistRepository,
    modifier: Modifier = Modifier,
    config: CardConfig = CardConfig()
) {
    var isFocused by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var longPressDetected by remember { mutableStateOf(false) }
    val colors = appColors()
    val coroutineScope = rememberCoroutineScope()
    val contentInfo = cardData.contentInfo
    
    val effectiveContentType = remember(cardData.contentType, cardData.allowWatchlistActions) {
        if (cardData.allowWatchlistActions) cardData.contentType else null
    }

    val entryId = remember(cardData.contentType) {
        // Watchlist status is the viewer's own, even on friend-sourced cards; only the
        // add/remove actions are gated by allowWatchlistActions (effectiveContentType).
        cardData.contentType?.toWatchlistEntryId()
    }
    
    val (isInWatchlist, setIsInWatchlist) = useWatchlistState(
        entryId = entryId,
        showMenu = showMenu,
        watchlistRepository = watchlistRepository
    )
    UseLongPressState(longPressDetected, contentInfo.title) { longPressDetected = it }
    UseMenuStateLogging(showMenu, contentInfo.title)
    
    val performClick = createPerformClickHandler(
        showMenu = showMenu,
        longPressDetected = longPressDetected,
        title = contentInfo.title,
        onClick = onClick
    )
    
    val visualState = useCardVisualState(config, isFocused, colors)
    
    val cardModifier = modifier.buildCardModifier(
        config = config,
        colors = colors,
        visualState = visualState,
        handlers = CardInteractionHandlers(
            onFocusChanged = createFocusChangedHandler(
                isFocused = isFocused,
                onFocusChanged = config.onFocusChanged,
                setIsFocused = { isFocused = it }
            ),
            onLongPress = createLongPressHandler(
                title = contentInfo.title,
                setLongPressDetected = { longPressDetected = it },
                setShowMenu = { showMenu = it }
            ),
            onNormalClick = createNormalClickHandler(
                showMenu = showMenu,
                title = contentInfo.title,
                performClick = performClick
            ),
            performClick = performClick
        )
    )
    
    Card(
        modifier = cardModifier,
        shape = RoundedCornerShape(ContentCardBorderRadius),
        colors = CardDefaults.cardColors(
            containerColor = visualState.backgroundColor
        )
    ) {
        CardContent(
            coverImageUrl = contentInfo.coverImageUrl,
            title = contentInfo.title,
            isLogo = contentInfo.isLogo,
            overlayParams = CardOverlayParams(
                rating = contentInfo.rating,
                isInWatchlist = isInWatchlist,
                sourceBadge = cardData.sourceBadge,
                watchState = cardData.watchState,
                colors = colors
            )
        )
    }
    
    val menuOptions = createMenuOptions(effectiveContentType, isInWatchlist)
    
    PopupMenu(
        isOpen = showMenu && menuOptions.isNotEmpty(),
        menuOptions = menuOptions,
        onDismiss = createMenuDismissHandler(
            title = contentInfo.title,
            setShowMenu = { showMenu = it }
        ),
        onOptionSelected = createMenuOptionSelectedHandler(
            contentType = effectiveContentType,
            entryId = entryId,
            isInWatchlist = isInWatchlist,
            title = contentInfo.title,
            watchlistRepository = watchlistRepository,
            coroutineScope = coroutineScope,
            setIsInWatchlist = setIsInWatchlist
        )
    )
}

@Composable
fun LiveStreamCard(
    params: LiveStreamCardParams
) {
    ContentCardInternal(
        cardData = ContentCardData(
            contentInfo = ContentInfo(
                coverImageUrl = params.stream.streamIcon,
                title = params.stream.name,
                rating = null,
                isLogo = true
            ),
            contentType = ContentType.Live(params.stream),
            sourceBadge = params.sourceBadge,
            allowWatchlistActions = params.allowWatchlistActions
        ),
        onClick = params.onClick,
        watchlistRepository = params.watchlistRepository,
        modifier = params.modifier,
        config = params.config
    )
}

// Event Handler Functions
private fun createPerformClickHandler(
    showMenu: Boolean,
    longPressDetected: Boolean,
    title: String,
    onClick: () -> Unit
): () -> Unit {
    return {
        if (!showMenu && !longPressDetected) {
            Log.d("ContentCard", "[ContentCard] Card '$title' - clickable() called (normal click)")
            onClick()
        } else {
            if (showMenu) {
                Log.d("ContentCard", "[ContentCard] Card '$title' - clickable() blocked - menu is open")
            } else {
                Log.d("ContentCard", "[ContentCard] Card '$title' - clickable() prevented (long press detected)")
            }
        }
    }
}

private fun createFocusChangedHandler(
    isFocused: Boolean,
    onFocusChanged: ((Boolean) -> Unit)?,
    setIsFocused: (Boolean) -> Unit
): (androidx.compose.ui.focus.FocusState) -> Unit {
    return { focusState ->
        val wasFocused = isFocused
        setIsFocused(focusState.isFocused)
        if (focusState.isFocused != wasFocused) {
            onFocusChanged?.invoke(focusState.isFocused)
        }
    }
}

private fun createLongPressHandler(
    title: String,
    setLongPressDetected: (Boolean) -> Unit,
    setShowMenu: (Boolean) -> Unit
): () -> Unit {
    return {
        Log.d("ContentCard", "[ContentCard] Card '$title' - Long press callback received!")
        Log.d("ContentCard", "[ContentCard] Setting longPressDetected = true")
        setLongPressDetected(true)
        Log.d("ContentCard", "[ContentCard] Setting showMenu = true")
        setShowMenu(true)
    }
}

private fun createNormalClickHandler(
    showMenu: Boolean,
    title: String,
    performClick: () -> Unit
): () -> Unit {
    return {
        if (showMenu) {
            Log.d("ContentCard", "[ContentCard] Card '$title' - onNormalClick() blocked - menu is open")
        } else {
            Log.d("ContentCard", "[ContentCard] Card '$title' - onNormalClick() called (timer cancelled)")
            performClick()
        }
    }
}

// Menu Logic Functions
@Composable
private fun createMenuOptions(
    contentType: ContentType?,
    isInWatchlist: Boolean
): List<String> {
    val removeFromWatchlistText = stringResource(R.string.remove_from_watchlist)
    val addToWatchlistText = stringResource(R.string.add_to_watchlist)

    return remember(contentType, isInWatchlist, removeFromWatchlistText, addToWatchlistText) {
        if (contentType != null) {
            if (isInWatchlist) {
                listOf(removeFromWatchlistText)
            } else {
                listOf(addToWatchlistText)
            }
        } else {
            emptyList()
        }
    }
}

private fun createMenuDismissHandler(
    title: String,
    setShowMenu: (Boolean) -> Unit
): () -> Unit {
    return {
        Log.d("ContentCard", "[ContentCard] Card '$title' - Menu onDismiss() called")
        Log.d("ContentCard", "[ContentCard] Setting showMenu = false")
        setShowMenu(false)
    }
}

private fun createMenuOptionSelectedHandler(
    contentType: ContentType?,
    entryId: String?,
    isInWatchlist: Boolean,
    title: String,
    watchlistRepository: WatchlistRepository,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    setIsInWatchlist: (Boolean) -> Unit
): (Int) -> Unit {
    return { index ->
        Log.d("ContentCard", "[ContentCard] Card '$title' - Option $index wurde ausgewählt")
        if (contentType != null && entryId != null) {
            coroutineScope.launch {
                if (isInWatchlist) {
                    watchlistRepository.removeFromWatchlist(entryId)
                    setIsInWatchlist(false)
                    Log.d("ContentCard", "[ContentCard] Favorit entfernt: $entryId")
                } else {
                    val entry = contentType.toWatchlistEntry()
                    watchlistRepository.addToWatchlist(entry)
                    setIsInWatchlist(true)
                    Log.d("ContentCard", "[ContentCard] Favorit hinzugefügt: $entryId")
                }
            }
        }
    }
}

// State Management Functions
@Composable
private fun useWatchlistState(
    entryId: String?,
    showMenu: Boolean,
    watchlistRepository: WatchlistRepository
): Pair<Boolean, (Boolean) -> Unit> {
    var isInWatchlist by remember { mutableStateOf(false) }

    LaunchedEffect(entryId, showMenu) {
        if (entryId != null) {
            // Re-fetch when card changes or when menu is about to be shown
            isInWatchlist = watchlistRepository.isInWatchlist(entryId)
        }
    }

    return Pair(isInWatchlist) { newValue ->
        isInWatchlist = newValue
    }
}

@Composable
private fun UseLongPressState(
    longPressDetected: Boolean,
    title: String,
    setLongPressDetected: (Boolean) -> Unit
) {
    LaunchedEffect(longPressDetected) {
        if (longPressDetected) {
            kotlinx.coroutines.delay(100)
            Log.d("ContentCard", "[ContentCard] Card '$title' - Resetting longPressDetected flag")
            setLongPressDetected(false)
        }
    }
}

@Composable
private fun UseMenuStateLogging(
    showMenu: Boolean,
    title: String
) {
    LaunchedEffect(showMenu) {
        Log.d("ContentCard", "[ContentCard] Card '$title' - showMenu state changed: $showMenu")
    }
}

// Visual State Functions
@Composable
private fun useCardVisualState(
    config: CardConfig,
    isFocused: Boolean,
    colors: com.flintr.tv.ui.theme.AppColors
): CardVisualState {
    val backgroundColor by animateColorAsState(
        targetValue = when {
            config.isSelected -> colors.accentColorHoverBg
            isFocused -> colors.categoryHoverBg
            else -> colors.bgCard
        },
        animationSpec = tween(durationMillis = 200),
        label = "backgroundColor"
    )
    
    val shadowElevation by animateFloatAsState(
        targetValue = if (config.isSelected) 4f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "shadowElevation"
    )
    
    return CardVisualState(
        shadowElevation = shadowElevation,
        backgroundColor = backgroundColor,
        isFocused = isFocused
    )
}

@Composable
private fun CardContent(
    coverImageUrl: String?,
    title: String,
    isLogo: Boolean,
    overlayParams: CardOverlayParams
) {
    if (!coverImageUrl.isNullOrEmpty()) {
        CardImageWithOverlays(
            coverImageUrl = coverImageUrl,
            title = title,
            isLogo = isLogo,
            overlayParams = overlayParams
        )
    } else {
        CardFallbackWithOverlays(
            title = title,
            overlayParams = overlayParams
        )
    }
}

/**
 * Renders the card artwork. Posters keep `ContentScale.Crop`; the existing RGB_565 decoding
 * trade-off for posters on low-end boxes is untouched here. Sender logos instead use
 * `ContentScale.Fit` with inner padding, because cropping/upscaling a transparent channel logo
 * cuts off wordmarks (ZDF case) and stretches the aspect ratio.
 */
@Composable
private fun CardImageWithOverlays(
    coverImageUrl: String,
    title: String,
    isLogo: Boolean,
    overlayParams: CardOverlayParams
) {
    val painter = rememberAsyncImagePainter(model = coverImageUrl)
    val imageState = painter.state

    if (imageState is AsyncImagePainter.State.Error) {
        CardFallbackWithOverlays(
            title = title,
            overlayParams = overlayParams
        )
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painter,
                contentDescription = title,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (isLogo) Modifier.padding(ContentCardLogoPadding) else Modifier
                    ),
                contentScale = if (isLogo) ContentScale.Fit else ContentScale.Crop
            )

            CardBadges(
                isInWatchlist = overlayParams.isInWatchlist,
                sourceBadge = overlayParams.sourceBadge,
                rating = overlayParams.rating,
                isWatched = overlayParams.watchState.isWatched
            )

            Column(
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                if (overlayParams.watchState.progressPercent > 0f && !overlayParams.watchState.isWatched) {
                    ProgressWithRemainingLabel(
                        progressPercent = overlayParams.watchState.progressPercent,
                        remainingMinutes = overlayParams.watchState.remainingMinutes,
                        colors = overlayParams.colors
                    )
                }
                TitleOverlay(title = title)
            }
        }
    }
}

@Composable
private fun ProgressWithRemainingLabel(
    progressPercent: Float,
    remainingMinutes: Int?,
    colors: com.flintr.tv.ui.theme.AppColors
) {
    Box(modifier = Modifier.padding(horizontal = 4.dp)) {
        Column {
            InProgressIndicator(
                progressPercent = progressPercent,
                remainingMinutes = null,
                colors = colors
            )
            remainingMinutes?.let { minutes ->
                CardRemainingTimeLabel(minutes = minutes)
            }
        }
    }
}

@Composable
private fun BoxScope.CardBadges(
    isInWatchlist: Boolean,
    sourceBadge: String?,
    rating: String?,
    isWatched: Boolean
) {
    if (!sourceBadge.isNullOrBlank() || isInWatchlist) {
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(
                    top = Dimensions.contentCardRatingBadgeTop,
                    start = Dimensions.contentCardRatingBadgeRight
                ),
            horizontalArrangement = Arrangement.spacedBy(ContentCardBadgeSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!sourceBadge.isNullOrBlank()) {
                SourceBadge(badge = sourceBadge)
            }
            if (isInWatchlist) {
                WatchlistBadge()
            }
        }
    }
    
    if (rating != null) {
        RatingBadge(
            rating = rating,
            modifier = Modifier.align(Alignment.TopEnd)
        )
    }
    
    if (isWatched) {
        WatchedBadge(
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
private fun SourceBadge(
    badge: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(ContentCardFriendAvatarSize)
            .background(
                color = SharedColors.avatarAccentBg.copy(alpha = Dimensions.contentCardRatingBgAlpha),
                shape = RoundedCornerShape(Dimensions.radiusSM)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = badge,
            fontSize = ContentCardFriendAvatarFontSize,
            color = SharedColors.avatarAccentText,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun WatchlistBadge(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(ContentCardWatchlistBadgeSize)
            .background(
                color = ContentCardBgColor.copy(alpha = Dimensions.contentCardRatingBgAlpha),
                shape = RoundedCornerShape(Dimensions.radiusSM)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Favorite,
            contentDescription = "Watchlist",
            modifier = Modifier.size(ContentCardWatchlistIconSize),
            tint = ContentCardTextColor
        )
    }
}

@Composable
private fun RatingBadge(
    rating: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(
                top = Dimensions.contentCardRatingBadgeTop,
                end = Dimensions.contentCardRatingBadgeRight
            )
    ) {
        Row(
            modifier = Modifier
                .background(
                    color = ContentCardBgColor.copy(alpha = Dimensions.contentCardRatingBgAlpha),
                    shape = RoundedCornerShape(Dimensions.radiusSM)
                )
                .padding(
                    horizontal = Dimensions.contentCardRatingBadgePaddingH,
                    vertical = Dimensions.contentCardRatingBadgePaddingV
                ),
            horizontalArrangement = Arrangement.spacedBy(Dimensions.contentCardRatingSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                modifier = Modifier.size(Dimensions.contentCardRatingIconSize),
                tint = SharedColors.contentCardRatingIconColor
            )
            Text(
                text = rating,
                fontSize = ContentCardRatingFontSize,
                color = ContentCardTextColor,
                fontWeight = ContentCardRatingFontWeight
            )
        }
    }
}

@Composable
private fun TitleOverlay(
    title: String,
    modifier: Modifier = Modifier
) {
    // Outer container: positioned at bottom with padding and gradient
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(ContentCardTitleOverlayPadding)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        ContentCardBgColor.copy(alpha = ContentCardTitleGradientAlpha)
                    )
                )
            )
    ) {
        // Inner container: text background with padding
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = Dimensions.contentCardTitleContainerPaddingH,
                    vertical = Dimensions.contentCardTitleContainerPaddingV
                )
                .background(
                    color = ContentCardBgColor.copy(alpha = ContentCardTitleBgAlpha),
                    shape = RoundedCornerShape(Dimensions.radiusXXS)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                textAlign = TextAlign.Center,
                maxLines = ContentCardTitleMaxLines,
                overflow = TextOverflow.Ellipsis,
                fontSize = ContentCardTitleFontSize,
                lineHeight = ContentCardTitleFontSize * ContentCardTitleLineHeightFactor,
                color = ContentCardTextColor,
                fontWeight = ContentCardTextFontWeight
            )
        }
    }
}

@Composable
private fun BuildTextFallback(
    title: String,
    colors: com.flintr.tv.ui.theme.AppColors
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimensions.contentCardTextFallbackPadding),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            textAlign = TextAlign.Center,
            maxLines = Dimensions.contentCardTextFallbackMaxLines,
            overflow = TextOverflow.Ellipsis,
            fontSize = ContentCardTitleFontSize,
            color = colors.textPrimary
        )
    }
}

@Composable
private fun CardRemainingTimeLabel(minutes: Int) {
    val label = remember(minutes) { formatRemainingLabel(minutes) }
    if (label.isNotBlank()) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(Dimensions.radiusSM)
                )
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.9f)
            )
        }
    }
}

/** One unit spelling for card and detail: [formatRuntime] plus the card's "übrig" suffix. */
internal fun formatRemainingLabel(minutes: Int): String {
    val runtime = formatRuntime(minutes.toString())
    return if (runtime.isBlank()) "" else "$runtime übrig"
}

@Composable
private fun CardFallbackWithOverlays(
    title: String,
    overlayParams: CardOverlayParams
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Text-Fallback als Hintergrund
        BuildTextFallback(title, overlayParams.colors)

        // Badges (Freund-Avatar, Watchlist-Herz, Rating, Watched)
        CardBadges(
            isInWatchlist = overlayParams.isInWatchlist,
            sourceBadge = overlayParams.sourceBadge,
            rating = overlayParams.rating,
            isWatched = overlayParams.watchState.isWatched
        )

        // Progress-Indicator und Titel-Overlay unten
        Column(
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            if (overlayParams.watchState.progressPercent > 0f && !overlayParams.watchState.isWatched) {
                ProgressWithRemainingLabel(
                    progressPercent = overlayParams.watchState.progressPercent,
                    remainingMinutes = overlayParams.watchState.remainingMinutes,
                    colors = overlayParams.colors
                )
            }
            TitleOverlay(title = title)
        }
    }
}
