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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.flintr.tv.data.model.VodStream
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.appColors
import com.flintr.tv.ui.utils.onDpadCenter
import com.flintr.tv.ui.utils.onDpadDirection

@Composable
fun VODCard(
    vod: VodStream,
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
        modifier = modifier
            .width(150.dp)
            .height(250.dp)
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
            .border(
                width = Dimensions.listItemHoverBorderWidth,
                color = borderColor,
                shape = RoundedCornerShape(Dimensions.listItemHoverBorderRadius)
            )
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(Dimensions.listItemHoverBorderRadius)
            )
            // Correct order: onFocusChanged -> focusable -> key handlers
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
            .clickable { onClick() }
    ) {
        AsyncImage(
            model = vod.streamIcon ?: vod.cover,
            contentDescription = vod.name,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentScale = ContentScale.Crop
        )
        Text(
            text = vod.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

