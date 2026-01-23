package com.flintr.tv.ui.sidebars

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.util.Log
import com.flintr.tv.data.model.Category
import com.flintr.tv.ui.components.PopupMenu
import com.flintr.tv.ui.components.MenuType
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.FontSizes
import com.flintr.tv.ui.theme.SharedColors
import com.flintr.tv.ui.theme.appColors
import com.flintr.tv.ui.theme.isDark
import com.flintr.tv.ui.utils.onDpadDirection
import com.flintr.tv.ui.utils.onLongPress
import androidx.compose.ui.focus.focusRequester

data class CategoryRowParams(
    val categories: List<Category>,
    val selectedCategoryId: String? = null,
    val onCategorySelected: (String?) -> Unit,
    val itemCounts: Map<String, Int>? = null,
    val itemLabelBuilder: (Int) -> String = { _ -> "Kanäle" },
    val focusRequester: FocusRequester,
    val onNavigateLeft: () -> Unit,
    val onNavigateRight: () -> Unit,
    val onCategoryHidden: (Category) -> Unit
)

data class CategoryChipParams(
    val category: Category,
    val itemCount: Int = 0,
    val itemLabelBuilder: (Int) -> String = { _ -> "Kanäle" },
    val isSelected: Boolean,
    val onClick: () -> Unit,
    val onNavigateLeft: (() -> Unit)? = null,
    val onNavigateRight: (() -> Unit)? = null,
    val onCategoryHidden: ((Category) -> Unit)? = null
)

@Composable
fun CategoryRow(
    params: CategoryRowParams,
    modifier: Modifier = Modifier
) {
    // Categories are already filtered and sorted in AppNavigation.kt
    val colors = appColors()
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.isDark
    
    LazyColumn(
        modifier = modifier
            .fillMaxHeight()
            .width(Dimensions.categorySidebarWidth)
            .background(
                if (isDark) SharedColors.sidebarCard else colors.bgList
            )
            .focusRequester(params.focusRequester),
        contentPadding = PaddingValues(all = Dimensions.spacingMD),
        verticalArrangement = Arrangement.spacedBy(Dimensions.spacingXS)
    ) {
        if (params.categories.isEmpty()) {
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
            items(
                items = params.categories,
                key = { it.categoryId }
            ) { category ->
                CategoryChip(
                    params = CategoryChipParams(
                        category = category,
                        itemCount = params.itemCounts?.get(category.categoryId) ?: 0,
                        itemLabelBuilder = params.itemLabelBuilder,
                        isSelected = params.selectedCategoryId == category.categoryId,
                        onClick = { params.onCategorySelected(category.categoryId) },
                        onNavigateLeft = params.onNavigateLeft,
                        onNavigateRight = params.onNavigateRight,
                        onCategoryHidden = params.onCategoryHidden
                    )
                )
            }
        }
    }
}

@Composable
fun CategoryChip(
    params: CategoryChipParams,
    modifier: Modifier = Modifier
) {
    var hasFocus by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val colors = appColors()
    val name = params.category.categoryName
    
    // Debug: Log menu state changes
    LaunchedEffect(showMenu) {
        Log.d("CategoryChip", "[MenuState] Category '$name': showMenu = $showMenu, onCategoryHidden = ${params.onCategoryHidden != null}")
    }
    
    val isHoveredOrFocused = hasFocus && !params.isSelected
    
    // Background color with animation
    val backgroundColor by animateColorAsState(
        targetValue = when {
            params.isSelected -> colors.accentColor
            isHoveredOrFocused -> colors.categoryHoverBg
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 200),
        label = "backgroundColor"
    )
    
    // Text colors
    val titleColor = if (params.isSelected || isHoveredOrFocused) {
        Color.White
    } else {
        colors.categoryDefaultText
    }
    
    val countColor = if (params.isSelected || isHoveredOrFocused) {
        Color.White
    } else {
        colors.categoryChannelCountText
    }
    
    // Border
    val borderColor = if (params.isSelected || hasFocus) {
        colors.accentColor
    } else {
        Color.Transparent
    }
    
    // Shadow for selected items
    val shadowElevation = if (params.isSelected) {
        4.dp
    } else {
        0.dp
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (params.isSelected) {
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
                        Log.i("TVFocus", "[CategoryRow] ✅ FOCUSED: Category '$name' (Selected: ${params.isSelected}, Count: ${params.itemCount})")
                    } else {
                        Log.i("TVFocus", "[CategoryRow] ❌ UNFOCUSED: Category '$name'")
                    }
                }
            }
            .focusable()
            .onDpadDirection(
                onLeft = if (params.onNavigateLeft != null) {
                    {
                        Log.i("TVFocus", "[CategoryRow] ⬅️ D-Pad Left: Category '$name' -> Navigating to Navbar")
                        params.onNavigateLeft()
                        true
                    }
                } else {
                    null // Let Compose handle standard navigation
                },
                onRight = if (params.onNavigateRight != null) {
                    {
                        Log.i("TVFocus", "[CategoryRow] ➡️ D-Pad Right: Category '$name' -> Navigating to Content")
                        params.onNavigateRight()
                        true
                    }
                } else {
                    null // Let Compose handle standard navigation
                }
            )
            .onLongPress(
                onLongPress = {
                    Log.d("CategoryChip", "[LongPress] ✅ Long-Press erkannt für Kategorie '$name'")
                    Log.d("CategoryChip", "[LongPress] onCategoryHidden != null: ${params.onCategoryHidden != null}")
                    if (params.onCategoryHidden != null) {
                        Log.d("CategoryChip", "[LongPress] Setze showMenu = true für Kategorie '$name'")
                        showMenu = true
                    } else {
                        Log.w("CategoryChip", "[LongPress] ⚠️ onCategoryHidden ist null - Menü kann nicht geöffnet werden")
                    }
                },
                onNormalClick = {
                    Log.d("CategoryChip", "[Click] 🔵 Single-Click erkannt für Kategorie '$name' (via onNormalClick)")
                    params.onClick()
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
                    fontWeight = if (params.isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = titleColor
                )
            )
            if (params.itemCount > 0) {
                Text(
                    text = "${params.itemCount} ${params.itemLabelBuilder(params.itemCount)}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = FontSizes.fontSizeXS,
                        color = countColor
                    )
                )
            }
        }
    }
    
    // Category menu dialog - only render when menu should be shown
    if (params.onCategoryHidden != null && showMenu) {
        Log.d("CategoryChip", "[MenuRender] Rendering PopupMenu für Kategorie '$name': showMenu = $showMenu")
        PopupMenu(
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
                    params.onCategoryHidden(params.category)
                }
            },
            menuType = MenuType.CATEGORY
        )
    }
}

