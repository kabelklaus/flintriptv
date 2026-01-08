package com.flintr.tv.ui.sidebars

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.util.Log
import com.flintr.tv.data.model.Category
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.FontSizes
import com.flintr.tv.ui.theme.SharedColors
import com.flintr.tv.ui.theme.appColors
import com.flintr.tv.ui.utils.onDpadCenter
import com.flintr.tv.ui.utils.onDpadDirection
import androidx.compose.ui.focus.focusRequester

@Composable
fun CategoryRow(
    categories: List<Category>,
    selectedCategoryId: String? = null,
    onCategorySelected: (String?) -> Unit,
    itemCounts: Map<String, Int>? = null,
    itemLabelBuilder: (Int) -> String = { count -> "Kanäle" },
    focusRequester: androidx.compose.ui.focus.FocusRequester,
    onNavigateLeft: () -> Unit,
    onNavigateRight: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sortedCategories = categories.sortedBy { it.categoryName }
    val colors = appColors()
    val isDark = isSystemInDarkTheme()
    
    LazyColumn(
        modifier = modifier
            .fillMaxHeight()
            .width(Dimensions.categorySidebarWidth)
            .background(
                if (isDark) SharedColors.sidebarCard else colors.bgList
            )
            .focusRequester(focusRequester),
        contentPadding = PaddingValues(all = Dimensions.spacingMD),
        verticalArrangement = Arrangement.spacedBy(Dimensions.spacingXS)
    ) {
        if (sortedCategories.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Dimensions.spacingLG),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Keine Kategorien",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = colors.textSecondary
                        )
                    )
                }
            }
        } else {
            items(sortedCategories) { category ->
                CategoryChip(
                    name = category.categoryName,
                    itemCount = itemCounts?.get(category.categoryId) ?: 0,
                    itemLabelBuilder = itemLabelBuilder,
                    isSelected = selectedCategoryId == category.categoryId,
                    onClick = { onCategorySelected(category.categoryId) },
                    onNavigateLeft = onNavigateLeft,
                    onNavigateRight = onNavigateRight
                )
            }
        }
    }
}

@Composable
fun CategoryChip(
    name: String,
    itemCount: Int = 0,
    itemLabelBuilder: (Int) -> String = { count -> "Kanäle" },
    isSelected: Boolean,
    onClick: () -> Unit,
    onNavigateLeft: (() -> Unit)? = null,
    onNavigateRight: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var hasFocus by remember { mutableStateOf(false) }
    val colors = appColors()
    
    val isHoveredOrFocused = hasFocus && !isSelected
    
    // Background color with animation
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSelected -> colors.accentColor
            isHoveredOrFocused -> colors.categoryHoverBg
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 200),
        label = "backgroundColor"
    )
    
    // Text colors
    val titleColor = if (isSelected || isHoveredOrFocused) {
        Color.White
    } else {
        colors.categoryDefaultText
    }
    
    val countColor = if (isSelected || isHoveredOrFocused) {
        Color.White
    } else {
        colors.categoryChannelCountText
    }
    
    // Border
    val borderColor = if (isSelected || hasFocus) {
        colors.accentColor
    } else {
        Color.Transparent
    }
    
    // Shadow for selected items
    val shadowElevation = if (isSelected) {
        4.dp
    } else {
        0.dp
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isSelected) {
                    Modifier.shadow(
                        elevation = shadowElevation,
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
                val wasFocused = hasFocus
                hasFocus = focusState.hasFocus
                if (hasFocus != wasFocused) {
                    if (hasFocus) {
                        Log.i("TVFocus", "[CategoryRow] ✅ FOCUSED: Category '$name' (Selected: $isSelected, Count: $itemCount)")
                    } else {
                        Log.i("TVFocus", "[CategoryRow] ❌ UNFOCUSED: Category '$name'")
                    }
                }
            }
            .focusable()
            .onDpadDirection(
                onLeft = if (onNavigateLeft != null) {
                    {
                        Log.i("TVFocus", "[CategoryRow] ⬅️ D-Pad Left: Category '$name' -> Navigating to Navbar")
                        onNavigateLeft()
                        true
                    }
                } else {
                    null // Let Compose handle standard navigation
                },
                onRight = if (onNavigateRight != null) {
                    {
                        Log.i("TVFocus", "[CategoryRow] ➡️ D-Pad Right: Category '$name' -> Navigating to Content")
                        onNavigateRight()
                        true
                    }
                } else {
                    null // Let Compose handle standard navigation
                }
            )
            .onDpadCenter(onClick)
            .clickable { onClick() }
            .padding(all = Dimensions.listItemHoverPadding),
        contentAlignment = Alignment.CenterStart
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Dimensions.categoryTextSpacing)
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = titleColor
                )
            )
            if (itemCount > 0) {
                Text(
                    text = "$itemCount ${itemLabelBuilder(itemCount)}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = FontSizes.fontSizeXS,
                        color = countColor
                    )
                )
            }
        }
    }
}

