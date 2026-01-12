package com.flintr.tv.ui.sidebars

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.flintr.tv.data.local.CategoryVisibilityService
import com.flintr.tv.data.model.Category
import com.flintr.tv.data.model.CategoryType
import com.flintr.tv.ui.components.ContentCardMenu
import com.flintr.tv.ui.components.MenuType
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.FontSizes
import com.flintr.tv.ui.theme.SharedColors
import com.flintr.tv.ui.theme.appColors
import com.flintr.tv.ui.utils.onClick
import com.flintr.tv.ui.utils.onDpadDirection
import com.flintr.tv.ui.utils.onLongPress
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
    categoryVisibilityService: CategoryVisibilityService,
    categoryType: CategoryType,
    onCategoryHidden: (Category) -> Unit,
    modifier: Modifier = Modifier
) {
    // Filter out hidden categories
    val filteredCategories = categories.filter { 
        !categoryVisibilityService.isCategoryHidden(it.categoryId, categoryType) 
    }
    val sortedCategories = filteredCategories.sortedBy { it.categoryName }
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
                    category = category,
                    itemCount = itemCounts?.get(category.categoryId) ?: 0,
                    itemLabelBuilder = itemLabelBuilder,
                    isSelected = selectedCategoryId == category.categoryId,
                    onClick = { onCategorySelected(category.categoryId) },
                    onNavigateLeft = onNavigateLeft,
                    onNavigateRight = onNavigateRight,
                    onCategoryHidden = onCategoryHidden
                )
            }
        }
    }
}

@Composable
fun CategoryChip(
    category: Category,
    itemCount: Int = 0,
    itemLabelBuilder: (Int) -> String = { count -> "Kanäle" },
    isSelected: Boolean,
    onClick: () -> Unit,
    onNavigateLeft: (() -> Unit)? = null,
    onNavigateRight: (() -> Unit)? = null,
    onCategoryHidden: ((Category) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var hasFocus by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val colors = appColors()
    val name = category.categoryName
    
    // Debug: Log menu state changes
    LaunchedEffect(showMenu) {
        Log.d("CategoryChip", "[MenuState] Category '$name': showMenu = $showMenu, onCategoryHidden = ${onCategoryHidden != null}")
    }
    
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
            .onLongPress(
                onLongPress = {
                    Log.d("CategoryChip", "[LongPress] ✅ Long-Press erkannt für Kategorie '$name'")
                    Log.d("CategoryChip", "[LongPress] onCategoryHidden != null: ${onCategoryHidden != null}")
                    if (onCategoryHidden != null) {
                        Log.d("CategoryChip", "[LongPress] Setze showMenu = true für Kategorie '$name'")
                        showMenu = true
                    } else {
                        Log.w("CategoryChip", "[LongPress] ⚠️ onCategoryHidden ist null - Menü kann nicht geöffnet werden")
                    }
                },
                onNormalClick = {
                    Log.d("CategoryChip", "[Click] 🔵 Single-Click erkannt für Kategorie '$name' (via onNormalClick)")
                    onClick()
                },
                durationMillis = 300L
            )
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
    
    // Category menu dialog
    if (onCategoryHidden != null) {
        Log.d("CategoryChip", "[MenuRender] Rendering ContentCardMenu für Kategorie '$name': showMenu = $showMenu")
        ContentCardMenu(
            isOpen = showMenu,
            menuOptions = listOf("Ausblenden"),
            onDismiss = { 
                Log.d("CategoryChip", "[MenuDismiss] Menü wird geschlossen für Kategorie '$name'")
                showMenu = false 
            },
            onOptionSelected = { index ->
                Log.d("CategoryChip", "[MenuOption] Option $index ausgewählt für Kategorie '$name'")
                if (index == 0) {
                    Log.d("CategoryChip", "[MenuOption] Rufe onCategoryHidden() auf für Kategorie '$name'")
                    onCategoryHidden(category)
                }
            },
            menuType = MenuType.CATEGORY
        )
    } else {
        Log.d("CategoryChip", "[MenuRender] onCategoryHidden ist null - Menü wird nicht gerendert für Kategorie '$name'")
    }
}

