package com.flintr.tv.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.flintr.tv.data.model.LiveStream
import com.flintr.tv.data.model.Series
import com.flintr.tv.data.model.VodStream
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.SharedColors
import com.flintr.tv.ui.theme.appColors
import com.flintr.tv.ui.utils.onDpadCenter
import com.flintr.tv.ui.utils.onDpadDirection
import com.flintr.tv.utils.ContentParser

// Content Card dimensions (from Flutter AppTheme)
private val ContentCardMaxCrossAxisExtent = 133.dp
private val ContentCardAspectRatio = 0.67f // 2:3 format
private val ContentCardHeight = ContentCardMaxCrossAxisExtent / ContentCardAspectRatio
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

@Composable
fun VODCard(
    vod: VodStream,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    onNavigateLeft: (() -> Unit)? = null
) {
    ContentCardInternal(
        coverImageUrl = vod.streamIcon ?: vod.cover,
        title = vod.name,
        rating = ContentParser.getRating(vod),
        onClick = onClick,
        modifier = modifier,
        isSelected = isSelected,
        onFocusChanged = onFocusChanged,
        onNavigateLeft = onNavigateLeft
    )
}

@Composable
fun VODCard(
    series: Series,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    onNavigateLeft: (() -> Unit)? = null
) {
    ContentCardInternal(
        coverImageUrl = series.cover,
        title = series.name,
        rating = ContentParser.getRating(series),
        onClick = onClick,
        modifier = modifier,
        isSelected = isSelected,
        onFocusChanged = onFocusChanged,
        onNavigateLeft = onNavigateLeft
    )
}

@Composable
private fun ContentCardInternal(
    coverImageUrl: String?,
    title: String,
    rating: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    onNavigateLeft: (() -> Unit)? = null
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
            else -> colors.bgCard
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
    
    Card(
        modifier = modifier
            .size(width = ContentCardMaxCrossAxisExtent, height = ContentCardHeight)
            .then(
                if (isSelected) {
                    Modifier.shadow(
                        elevation = shadowElevation.dp,
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
                color = borderColor,
                shape = RoundedCornerShape(ContentCardBorderRadius)
            )
            .onFocusChanged { focusState ->
                val wasFocused = isFocused
                isFocused = focusState.isFocused
                if (isFocused != wasFocused) {
                    onFocusChanged?.invoke(isFocused)
                }
            }
            .focusable()
            .onDpadDirection(
                onLeft = if (onNavigateLeft != null) {
                    {
                        onNavigateLeft()
                        true
                    }
                } else {
                    null // Let Compose handle standard navigation
                },
                onRight = null, // Let Compose handle standard navigation
                onUp = null, // Let Compose handle standard navigation
                onDown = null // Let Compose handle standard navigation
            )
            .onDpadCenter(onClick)
            .clickable { onClick() },
        shape = RoundedCornerShape(ContentCardBorderRadius),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        )
    ) {
        CardContent(
            coverImageUrl = coverImageUrl,
            title = title,
            rating = rating,
            colors = colors
        )
    }
}

@Composable
fun LiveStreamCard(
    stream: LiveStream,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    onNavigateLeft: (() -> Unit)? = null,
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null
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
            else -> colors.bgCard
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
    
    Card(
        modifier = modifier
            .size(width = ContentCardMaxCrossAxisExtent, height = ContentCardHeight)
            .then(
                if (isSelected) {
                    Modifier.shadow(
                        elevation = shadowElevation.dp,
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
                color = borderColor,
                shape = RoundedCornerShape(ContentCardBorderRadius)
            )
            .then(
                if (focusRequester != null) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                }
            )
            .onFocusChanged { focusState ->
                val wasFocused = isFocused
                isFocused = focusState.isFocused
                if (isFocused != wasFocused) {
                    onFocusChanged?.invoke(isFocused)
                }
            }
            .focusable()
            .onDpadDirection(
                onLeft = if (onNavigateLeft != null) {
                    {
                        onNavigateLeft()
                        true
                    }
                } else {
                    null // Let Compose handle standard navigation
                },
                onRight = null, // Let Compose handle standard navigation
                onUp = null, // Let Compose handle standard navigation
                onDown = null // Let Compose handle standard navigation
            )
            .onDpadCenter(onClick)
            .clickable { onClick() },
        shape = RoundedCornerShape(ContentCardBorderRadius),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        )
    ) {
        CardContent(
            coverImageUrl = stream.streamIcon,
            title = stream.name,
            rating = null,
            colors = colors
        )
    }
}

@Composable
private fun CardContent(
    coverImageUrl: String?,
    title: String,
    rating: String?,
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
