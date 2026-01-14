package com.flintr.tv.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.material.icons.filled.Star
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import androidx.compose.foundation.Image
import android.util.Log
import com.flintr.tv.data.local.FavoritesRepository
import com.flintr.tv.data.model.Favorite
import com.flintr.tv.data.model.LiveStream
import com.flintr.tv.data.model.Series
import com.flintr.tv.data.model.VodStream
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.SharedColors
import com.flintr.tv.ui.theme.appColors
import com.flintr.tv.ui.utils.onDpadDirection
import com.flintr.tv.ui.utils.onLongPress
import com.flintr.tv.utils.ContentParser
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import com.flintr.tv.R
import kotlinx.coroutines.launch

// Content Card dimensions (from Flutter AppTheme)
// Using Dimensions object for single source of truth
private val ContentCardHeight = Dimensions.contentCardMaxCrossAxisExtent / Dimensions.contentCardAspectRatio
private val ContentCardBorderRadius = 8.dp // radiusMD
private val ContentCardBorderWidth = 2.dp // categoryBorderWidth

// Content Card Title Overlay (from Flutter AppTheme)
private val ContentCardTitleOverlayPadding = 1.dp
private val ContentCardTitleContainerPaddingH = 1.dp
private val ContentCardTitleContainerPaddingV = 1.dp
private val ContentCardTitleFontSize = 8.sp
private val ContentCardTitleMaxLines = 2
private val ContentCardTitleGradientAlpha = 0.7f
private val ContentCardTitleBgAlpha = 0.75f

// Content Card Colors (from Flutter AppTheme)
private val ContentCardTextColor = Color.White
private val ContentCardBgColor = Color.Black
private val ContentCardTextFontWeight = FontWeight.Bold

// Content Card Title Line Height
private const val ContentCardTitleLineHeightFactor = 1.2f

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
    val favoritesRepository: FavoritesRepository,
    val modifier: Modifier = Modifier,
    val isSelected: Boolean = false,
    val onFocusChanged: ((Boolean) -> Unit)? = null,
    val onNavigateLeft: (() -> Unit)? = null,
    val focusRequester: androidx.compose.ui.focus.FocusRequester? = null
)

private data class CardVisualState(
    val borderColor: Color,
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
    val rating: String?
)

private sealed class ContentType {
    abstract fun toFavoriteId(): String
    abstract fun toFavorite(metadata: Map<String, String?>? = null): Favorite
    
    data class Live(val stream: LiveStream) : ContentType() {
        override fun toFavoriteId(): String = stream.toFavoriteId()
        override fun toFavorite(metadata: Map<String, String?>?): Favorite = stream.toFavorite(metadata)
    }
    
    data class Movie(val vod: VodStream) : ContentType() {
        override fun toFavoriteId(): String = vod.toFavoriteId()
        override fun toFavorite(metadata: Map<String, String?>?): Favorite = vod.toFavorite(metadata)
    }
    
    data class Series(val seriesData: com.flintr.tv.data.model.Series) : ContentType() {
        override fun toFavoriteId(): String = seriesData.toFavoriteId()
        override fun toFavorite(metadata: Map<String, String?>?): Favorite = seriesData.toFavorite(metadata)
    }
}

@Composable
fun VODCard(
    vod: VodStream,
    onClick: () -> Unit,
    favoritesRepository: FavoritesRepository,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    onNavigateLeft: (() -> Unit)? = null,
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null
) {
    val params = VODCardParams(
        vod = vod,
        onClick = onClick,
        favoritesRepository = favoritesRepository,
        modifier = modifier,
        isSelected = isSelected,
        onFocusChanged = onFocusChanged,
        onNavigateLeft = onNavigateLeft,
        focusRequester = focusRequester
    )
    VODCard(params)
}

@Composable
fun VODCard(
    series: Series,
    onClick: () -> Unit,
    favoritesRepository: FavoritesRepository,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    onNavigateLeft: (() -> Unit)? = null,
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null
) {
    val params = VODCardParams(
        series = series,
        onClick = onClick,
        favoritesRepository = favoritesRepository,
        modifier = modifier,
        isSelected = isSelected,
        onFocusChanged = onFocusChanged,
        onNavigateLeft = onNavigateLeft,
        focusRequester = focusRequester
    )
    VODCard(params)
}

@Composable
private fun VODCard(params: VODCardParams) {
    val contentInfo = when {
        params.vod != null -> ContentInfo(
            coverImageUrl = params.vod.streamIcon ?: params.vod.cover,
            title = params.vod.name,
            rating = ContentParser.getRating(params.vod)
        )
        params.series != null -> ContentInfo(
            coverImageUrl = params.series.cover,
            title = params.series.name,
            rating = ContentParser.getRating(params.series)
        )
        else -> throw IllegalArgumentException("Either vod or series must be provided")
    }
    
    val contentType = when {
        params.vod != null -> ContentType.Movie(params.vod)
        params.series != null -> ContentType.Series(params.series)
        else -> throw IllegalArgumentException("Either vod or series must be provided")
    }
    
    ContentCardInternal(
        contentInfo = contentInfo,
        onClick = params.onClick,
        modifier = params.modifier,
        config = CardConfig(
            isSelected = params.isSelected,
            onFocusChanged = params.onFocusChanged,
            onNavigateLeft = params.onNavigateLeft,
            focusRequester = params.focusRequester
        ),
        contentType = contentType,
        favoritesRepository = params.favoritesRepository
    )
}

@Composable
private fun buildCardModifier(
    baseModifier: Modifier,
    config: CardConfig,
    colors: com.flintr.tv.ui.theme.AppColors,
    visualState: CardVisualState,
    handlers: CardInteractionHandlers
): Modifier {
    return baseModifier
        .width(Dimensions.contentCardMaxCrossAxisExtent)
        .aspectRatio(Dimensions.contentCardAspectRatio)
        .then(
            if (config.isSelected) {
                Modifier.shadow(
                    elevation = visualState.shadowElevation.dp,
                    shape = RoundedCornerShape(ContentCardBorderRadius),
                    spotColor = colors.accentColor.copy(alpha = Dimensions.categoryShadowAlpha),
                    ambientColor = colors.accentColor.copy(alpha = Dimensions.categoryShadowAlpha)
                )
            } else {
                Modifier
            }
        )
        .border(
            width = ContentCardBorderWidth,
            color = visualState.borderColor,
            shape = RoundedCornerShape(ContentCardBorderRadius)
        )
        .then(
            if (config.focusRequester != null) {
                Modifier.focusRequester(config.focusRequester)
            } else {
                Modifier
            }
        )
        .onFocusChanged(handlers.onFocusChanged)
        .focusable()
        .onDpadDirection(
            onLeft = if (config.onNavigateLeft != null) {
                {
                    config.onNavigateLeft()
                    true
                }
            } else {
                null
            },
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
    contentInfo: ContentInfo,
    onClick: () -> Unit,
    favoritesRepository: FavoritesRepository,
    modifier: Modifier = Modifier,
    config: CardConfig = CardConfig(),
    contentType: ContentType? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var longPressDetected by remember { mutableStateOf(false) }
    val colors = appColors()
    val coroutineScope = rememberCoroutineScope()
    
    val favoriteId = remember(contentType) {
        contentType?.toFavoriteId()
    }
    
    val (isFavorite, setIsFavorite) = useFavoriteState(
        favoriteId = favoriteId,
        showMenu = showMenu,
        favoritesRepository = favoritesRepository
    )
    useLongPressState(longPressDetected, contentInfo.title) { longPressDetected = it }
    useMenuStateLogging(showMenu, contentInfo.title)
    
    val performClick = createPerformClickHandler(
        showMenu = showMenu,
        longPressDetected = longPressDetected,
        title = contentInfo.title,
        onClick = onClick
    )
    
    val visualState = useCardVisualState(config, isFocused, colors)
    
    val cardModifier = buildCardModifier(
        baseModifier = modifier,
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
            rating = contentInfo.rating,
            isFavorite = isFavorite,
            colors = colors
        )
    }
    
    val menuOptions = createMenuOptions(contentType, isFavorite)
    
    PopupMenu(
        isOpen = showMenu && menuOptions.isNotEmpty(),
        menuOptions = menuOptions,
        onDismiss = createMenuDismissHandler(
            title = contentInfo.title,
            setShowMenu = { showMenu = it }
        ),
        onOptionSelected = createMenuOptionSelectedHandler(
            contentType = contentType,
            favoriteId = favoriteId,
            isFavorite = isFavorite,
            title = contentInfo.title,
            favoritesRepository = favoritesRepository,
            coroutineScope = coroutineScope,
            setIsFavorite = setIsFavorite
        )
    )
}

@Composable
fun LiveStreamCard(
    stream: LiveStream,
    onClick: () -> Unit,
    favoritesRepository: FavoritesRepository,
    modifier: Modifier = Modifier,
    config: CardConfig = CardConfig(),
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null
) {
    ContentCardInternal(
        contentInfo = ContentInfo(
            coverImageUrl = stream.streamIcon,
            title = stream.name,
            rating = null
        ),
        onClick = onClick,
        favoritesRepository = favoritesRepository,
        modifier = modifier,
        config = config.copy(focusRequester = focusRequester ?: config.focusRequester),
        contentType = ContentType.Live(stream)
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
    isFavorite: Boolean
): List<String> {
    val removeFromFavoritesText = stringResource(R.string.remove_from_favorites)
    val addToFavoritesText = stringResource(R.string.add_to_favorites)
    
    return remember(contentType, isFavorite, removeFromFavoritesText, addToFavoritesText) {
        if (contentType != null) {
            if (isFavorite) {
                listOf(removeFromFavoritesText)
            } else {
                listOf(addToFavoritesText)
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
    favoriteId: String?,
    isFavorite: Boolean,
    title: String,
    favoritesRepository: FavoritesRepository,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    setIsFavorite: (Boolean) -> Unit
): (Int) -> Unit {
    return { index ->
        Log.d("ContentCard", "[ContentCard] Card '$title' - Option $index wurde ausgewählt")
        if (contentType != null && favoriteId != null) {
            coroutineScope.launch {
                if (isFavorite) {
                    favoritesRepository.removeFavorite(favoriteId)
                    setIsFavorite(false)
                    Log.d("ContentCard", "[ContentCard] Favorit entfernt: $favoriteId")
                } else {
                    val favorite = contentType.toFavorite()
                    favoritesRepository.addFavorite(favorite)
                    setIsFavorite(true)
                    Log.d("ContentCard", "[ContentCard] Favorit hinzugefügt: $favoriteId")
                }
            }
        }
    }
}

// State Management Functions
@Composable
private fun useFavoriteState(
    favoriteId: String?,
    showMenu: Boolean,
    favoritesRepository: FavoritesRepository
): Pair<Boolean, (Boolean) -> Unit> {
    var isFavorite by remember { mutableStateOf(false) }
    
    LaunchedEffect(favoriteId, showMenu) {
        if (favoriteId != null) {
            // Re-fetch when card changes or when menu is about to be shown
            isFavorite = favoritesRepository.isFavorite(favoriteId)
        }
    }
    
    return Pair(isFavorite) { newValue ->
        isFavorite = newValue
    }
}

@Composable
private fun useLongPressState(
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
private fun useMenuStateLogging(
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
    val borderColor by animateColorAsState(
        targetValue = if (config.isSelected || isFocused) colors.accentColor else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "borderColor"
    )
    
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
        borderColor = borderColor,
        shadowElevation = shadowElevation,
        backgroundColor = backgroundColor,
        isFocused = isFocused
    )
}

@Composable
private fun CardContent(
    coverImageUrl: String?,
    title: String,
    rating: String?,
    isFavorite: Boolean,
    colors: com.flintr.tv.ui.theme.AppColors
) {
    if (!coverImageUrl.isNullOrEmpty()) {
        // Card with cover image
        val painter = rememberAsyncImagePainter(model = coverImageUrl)
        val imageState = painter.state
        
        if (imageState is AsyncImagePainter.State.Error) {
            // Fallback to text if image fails
            buildTextFallback(title, colors)
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    painter = painter,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                
                // Favorite badge at top left
                if (isFavorite) {
                    FavoriteBadge(
                        modifier = Modifier.align(Alignment.TopStart)
                    )
                }
                
                // Rating badge at top right
                if (rating != null) {
                    RatingBadge(
                        rating = rating,
                        modifier = Modifier.align(Alignment.TopEnd)
                    )
                }
                
                // Title overlay at bottom
                TitleOverlay(
                    title = title,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    } else {
        // Fallback to text-only card
        buildTextFallback(title, colors)
    }
}

@Composable
private fun FavoriteBadge(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(
                top = Dimensions.contentCardRatingBadgeTop,
                start = Dimensions.contentCardRatingBadgeRight
            )
    ) {
        Box(
            modifier = Modifier
                .background(
                    color = ContentCardBgColor.copy(alpha = Dimensions.contentCardRatingBgAlpha),
                    shape = RoundedCornerShape(Dimensions.radiusSM)
                )
                .padding(
                    horizontal = Dimensions.contentCardRatingBadgePaddingH,
                    vertical = Dimensions.contentCardRatingBadgePaddingV
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Favorit",
                modifier = Modifier.size(Dimensions.contentCardRatingIconSize),
                tint = SharedColors.contentCardRatingIconColor
            )
        }
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
                fontSize = ContentCardTitleFontSize,
                color = ContentCardTextColor,
                fontWeight = ContentCardTextFontWeight
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
private fun buildTextFallback(
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
