package com.flintr.tv.ui.sidebars

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
    val categoryFocusRequesterMap: MutableMap<String, FocusRequester>,
    val onNavigateLeft: () -> Unit,
    val onNavigateRight: () -> Unit,
    val onCategoryHidden: (Category) -> Unit,
    val listState: LazyListState
)

data class CategoryChipParams(
    val category: Category,
    val itemCount: Int = 0,
    val itemLabelBuilder: (Int) -> String = { _ -> "Kanäle" },
    val isSelected: Boolean,
    val onClick: () -> Unit,
    val categoryFocusRequesterMap: MutableMap<String, FocusRequester>,
    val onNavigateLeft: (() -> Unit)? = null,
    val onNavigateRight: (() -> Unit)? = null,
    val onCategoryHidden: ((Category) -> Unit)? = null
)

/**
 * Data class holding computed colors for CategoryChip
 */
private data class CategoryChipColors(
    val background: Color,
    val title: Color,
    val count: Color,
    val border: Color,
    val shadowElevation: androidx.compose.ui.unit.Dp
)

/**
 * Calculate colors based on selection and focus state
 */
private fun calculateChipColors(
    isSelected: Boolean,
    hasFocus: Boolean,
    colors: com.flintr.tv.ui.theme.AppColors
): CategoryChipColors {
    val isHoveredOrFocused = hasFocus && !isSelected

    val background = when {
        isSelected -> colors.accentColor
        isHoveredOrFocused -> colors.categoryHoverBg
        else -> Color.Transparent
    }

    val title = if (isSelected || isHoveredOrFocused) Color.White else colors.categoryDefaultText
    val count = if (isSelected || isHoveredOrFocused) Color.White else colors.categoryChannelCountText
    val border = if (isSelected || hasFocus) colors.accentColor else Color.Transparent
    val shadowElevation = if (isSelected) 4.dp else 0.dp

    return CategoryChipColors(background, title, count, border, shadowElevation)
}

/**
 * Creates D-Pad navigation handler for left direction
 */
private fun createLeftNavigationHandler(
    onNavigateLeft: (() -> Unit)?,
    categoryName: String
): (() -> Boolean)? {
    return if (onNavigateLeft != null) {
        {
            Log.i("TVFocus", "[CategoryRow] ⬅️ D-Pad Left: Category '$categoryName' -> Navigating to Navbar")
            onNavigateLeft()
            true
        }
    } else {
        null
    }
}

/**
 * Creates D-Pad navigation handler for right direction
 */
private fun createRightNavigationHandler(
    onNavigateRight: (() -> Unit)?,
    categoryName: String
): (() -> Boolean)? {
    return if (onNavigateRight != null) {
        {
            Log.i("TVFocus", "[CategoryRow] ➡️ D-Pad Right: Category '$categoryName' -> Navigating to Content")
            onNavigateRight()
            true
        }
    } else {
        null
    }
}

/**
 * Text content for CategoryChip
 */
@Composable
private fun CategoryChipContent(
    name: String,
    itemCount: Int,
    itemLabelBuilder: (Int) -> String,
    isSelected: Boolean,
    titleColor: Color,
    countColor: Color
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

/**
 * PopupMenu for category actions (hide)
 */
@Composable
private fun CategoryChipMenu(
    category: Category,
    showMenu: Boolean,
    onDismiss: () -> Unit,
    onCategoryHidden: (Category) -> Unit
) {
    val name = category.categoryName
    Log.d("CategoryChip", "[MenuRender] Rendering PopupMenu für Kategorie '$name': showMenu = $showMenu")
    PopupMenu(
        isOpen = showMenu,
        menuOptions = listOf("Ausblenden"),
        onDismiss = {
            Log.d("CategoryChip", "[MenuDismiss] Menü wird geschlossen für Kategorie '$name'")
            onDismiss()
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
}

/**
 * Creates modifier chain for shadow and border styling
 */
private fun Modifier.categoryChipStyling(
    isSelected: Boolean,
    chipColors: CategoryChipColors,
    animatedBackgroundColor: Color,
    accentColor: Color
): Modifier {
    return this
        .then(
            if (isSelected) {
                Modifier.shadow(
                    elevation = chipColors.shadowElevation,
                    shape = RoundedCornerShape(Dimensions.listItemHoverBorderRadius),
                    spotColor = accentColor.copy(alpha = Dimensions.categoryShadowAlpha),
                    ambientColor = accentColor.copy(alpha = Dimensions.categoryShadowAlpha)
                )
            } else {
                Modifier
            }
        )
        .background(
            color = animatedBackgroundColor,
            shape = RoundedCornerShape(Dimensions.listItemHoverBorderRadius)
        )
        .then(
            if (chipColors.border != Color.Transparent) {
                Modifier.border(
                    width = Dimensions.listItemHoverBorderWidth,
                    color = chipColors.border,
                    shape = RoundedCornerShape(Dimensions.listItemHoverBorderRadius)
                )
            } else {
                Modifier
            }
        )
}

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
        state = params.listState,
        modifier = modifier
            .fillMaxHeight()
            .width(Dimensions.categorySidebarWidth)
            .background(
                if (isDark) SharedColors.sidebarCard else colors.bgList
            ),
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
                        categoryFocusRequesterMap = params.categoryFocusRequesterMap,
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
    val categoryId = params.category.categoryId

    // Create individual FocusRequester for this category
    val categoryFocusRequester = remember { FocusRequester() }
    
    // Store FocusRequester in map
    LaunchedEffect(categoryId) {
        params.categoryFocusRequesterMap[categoryId] = categoryFocusRequester
    }

    // Debug: Log menu state changes
    LaunchedEffect(showMenu) {
        Log.d("CategoryChip", "[MenuState] Category '$name': showMenu = $showMenu, onCategoryHidden = ${params.onCategoryHidden != null}")
    }

    val chipColors = calculateChipColors(params.isSelected, hasFocus, colors)

    // Background color with animation
    val animatedBackgroundColor by animateColorAsState(
        targetValue = chipColors.background,
        animationSpec = tween(durationMillis = 200),
        label = "backgroundColor"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(categoryFocusRequester)
            .categoryChipStyling(params.isSelected, chipColors, animatedBackgroundColor, colors.accentColor)
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
                onLeft = createLeftNavigationHandler(params.onNavigateLeft, name),
                onRight = createRightNavigationHandler(params.onNavigateRight, name)
            )
            .onLongPress(
                onLongPress = {
                    Log.d("CategoryChip", "[LongPress] ✅ Long-Press erkannt für Kategorie '$name'")
                    if (params.onCategoryHidden != null) {
                        showMenu = true
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
        CategoryChipContent(
            name = name,
            itemCount = params.itemCount,
            itemLabelBuilder = params.itemLabelBuilder,
            isSelected = params.isSelected,
            titleColor = chipColors.title,
            countColor = chipColors.count
        )
    }

    // Category menu dialog - only render when menu should be shown
    if (params.onCategoryHidden != null && showMenu) {
        CategoryChipMenu(
            category = params.category,
            showMenu = showMenu,
            onDismiss = { showMenu = false },
            onCategoryHidden = params.onCategoryHidden
        )
    }
}
