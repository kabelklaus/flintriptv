package com.flintr.tv.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.activity.compose.BackHandler
import android.util.Log
import com.flintr.tv.data.local.CategoryVisibilityService
import com.flintr.tv.data.model.CategoryType
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.FontSizes
import com.flintr.tv.ui.theme.appColors
import com.flintr.tv.ui.utils.onBackButton
import com.flintr.tv.ui.utils.onClick
import com.flintr.tv.ui.utils.onDpadDirection
import com.flintr.tv.viewmodel.MainViewModel

/**
 * Data class representing a hidden category with resolved name
 */
private data class HiddenCategoryItem(
    val categoryId: String,
    val categoryName: String,
    val categoryType: CategoryType,
    val isAvailable: Boolean // true if category still exists in current categories
)

/**
 * Settings screen for managing hidden categories
 */
@Composable
fun SubSettingsHiddenCategories(
    onBack: () -> Unit,
    onNavigateLeft: () -> Unit,
    categoryVisibilityService: CategoryVisibilityService,
    mainViewModel: MainViewModel,
    onShowSnackbar: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = appColors()
    
    // Collect hidden categories from StateFlow
    val hiddenCategories by categoryVisibilityService.hiddenCategories.collectAsState()
    
    // Collect all categories from MainViewModel for name resolution
    val liveCategories by mainViewModel.liveCategories.collectAsState()
    val vodCategories by mainViewModel.vodCategories.collectAsState()
    val seriesCategories by mainViewModel.seriesCategories.collectAsState()
    
    // Resolve category names
    val hiddenCategoryItems = remember(hiddenCategories, liveCategories, vodCategories, seriesCategories) {
        val liveCategoriesMap = liveCategories.associateBy { it.categoryId }
        val vodCategoriesMap = vodCategories.associateBy { it.categoryId }
        val seriesCategoriesMap = seriesCategories.associateBy { it.categoryId }
        
        buildList {
            hiddenCategories.forEach { (categoryType, categoryIds) ->
                val allCategoriesMap = when (categoryType) {
                    CategoryType.LIVE_TV -> liveCategoriesMap
                    CategoryType.VOD -> vodCategoriesMap
                    CategoryType.SERIES -> seriesCategoriesMap
                }
                
                categoryIds.forEach { categoryId ->
                    val category = allCategoriesMap[categoryId]
                    val categoryName = category?.categoryName ?: categoryId
                    val isAvailable = category != null
                    
                    add(
                        HiddenCategoryItem(
                            categoryId = categoryId,
                            categoryName = categoryName,
                            categoryType = categoryType,
                            isAvailable = isAvailable
                        )
                    )
                }
            }
        }
    }
    
    // Group by category type
    val groupedItems = remember(hiddenCategoryItems) {
        hiddenCategoryItems.groupBy { it.categoryType }
    }
    
    // Dialog state
    var showDialog by remember { mutableStateOf<HiddenCategoryItem?>(null) }
    
    // Focus requester for first item
    val firstItemFocusRequester = remember { FocusRequester() }
    
    // Set initial focus
    LaunchedEffect(Unit) {
        if (hiddenCategoryItems.isNotEmpty()) {
            firstItemFocusRequester.requestFocus()
        }
    }
    
    // Handle Back button
    BackHandler(enabled = true) {
        Log.i("SettingsNav", "🔙 BACK: SubSettingsHiddenCategories -> SettingsMainScreen (via BackHandler)")
        onBack()
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgPreview)
    ) {
        if (hiddenCategoryItems.isEmpty()) {
            // Empty state
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Dimensions.spacingXL),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Keine versteckten Kategorien",
                    fontSize = FontSizes.fontSizeXL,
                    color = colors.textSecondary,
                    fontWeight = FontWeight.W500
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Dimensions.spacingXL),
                verticalArrangement = Arrangement.spacedBy(Dimensions.spacingMD),
                contentPadding = PaddingValues(vertical = Dimensions.spacingMD)
            ) {
                val categoryOrder = listOf(
                    CategoryType.LIVE_TV to "Live TV",
                    CategoryType.VOD to "VOD",
                    CategoryType.SERIES to "Serien"
                )
                var isFirstItem = true

                categoryOrder.forEach { (categoryType, title) ->
                    groupedItems[categoryType]?.let { items ->
                        item {
                            CategoryTypeHeader(title, colors)
                        }
                        items(items) { item ->
                            val requester = if (isFirstItem) {
                                isFirstItem = false
                                firstItemFocusRequester
                            } else null

                            HiddenCategoryItemCard(
                                item = item,
                                colors = colors,
                                focusRequester = requester,
                                onClick = { showDialog = item },
                                onNavigateLeft = onNavigateLeft,
                                onBack = onBack
                            )
                        }
                    }
                }
            }
        }
        
        // Dialog
        showDialog?.let { item ->
            ConfirmUnhideCategoryDialog(
                categoryName = item.categoryName,
                onConfirm = {
                    categoryVisibilityService.showCategory(item.categoryId, item.categoryType)
                    onShowSnackbar("Kategorie wurde wieder eingeblendet")
                    showDialog = null
                },
                onDismiss = { showDialog = null }
            )
        }
    }
}

@Composable
private fun CategoryTypeHeader(
    title: String,
    colors: com.flintr.tv.ui.theme.AppColors
) {
    Text(
        text = title,
        fontSize = FontSizes.fontSizeXL,
        fontWeight = FontWeight.W600,
        color = colors.accentColor,
        modifier = Modifier.padding(
            vertical = Dimensions.spacingSM,
            horizontal = Dimensions.spacingMD
        )
    )
}

@Composable
private fun HiddenCategoryItemCard(
    item: HiddenCategoryItem,
    colors: com.flintr.tv.ui.theme.AppColors,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
    onNavigateLeft: () -> Unit,
    onBack: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    
    // Animation for background color
    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) colors.categoryHoverBg else Color.Transparent,
        animationSpec = tween<Color>(
            durationMillis = 200,
            easing = FastOutSlowInEasing
        ),
        label = "backgroundColor"
    )
    
    // Border color
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) colors.accentColor else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "borderColor"
    )
    
    // Text colors
    val titleColor = if (isFocused) colors.accentColor else colors.textPrimary
    val subtitleColor = if (isFocused) colors.textPrimary else colors.textSecondary
    
    val typeLabel = when (item.categoryType) {
        CategoryType.LIVE_TV -> "Live TV"
        CategoryType.VOD -> "VOD"
        CategoryType.SERIES -> "Serien"
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (focusRequester != null) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                }
            )
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            }
            .focusable()
            .onDpadDirection(
                onLeft = {
                    onNavigateLeft()
                    true
                }
            )
            .onBackButton {
                onBack()
            }
            .onClick(onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
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
                .padding(Dimensions.listItemHoverPadding)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Dimensions.spacingXXS)
                ) {
                    Text(
                        text = item.categoryName,
                        fontSize = FontSizes.fontSizeXL,
                        fontWeight = FontWeight.W600,
                        color = titleColor
                    )
                    Text(
                        text = typeLabel,
                        fontSize = FontSizes.fontSizeMD,
                        color = subtitleColor
                    )
                }
                
                if (!item.isAvailable) {
                    Text(
                        text = "(Nicht mehr verfügbar)",
                        fontSize = FontSizes.fontSizeSM,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(start = Dimensions.spacingMD)
                    )
                }
            }
        }
    }
}

/**
 * Confirmation dialog for unhiding a category
 */
@Composable
private fun ConfirmUnhideCategoryDialog(
    categoryName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = appColors()
    val confirmButtonFocusRequester = remember { FocusRequester() }
    val cancelButtonFocusRequester = remember { FocusRequester() }
    
    // Set initial focus on confirm button
    LaunchedEffect(Unit) {
        confirmButtonFocusRequester.requestFocus()
    }
    
    BackHandler(enabled = true) {
        onDismiss()
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp)
                .padding(Dimensions.spacingXL),
            shape = RoundedCornerShape(Dimensions.radiusLG),
            colors = CardDefaults.cardColors(
                containerColor = colors.bgCard
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimensions.spacingXL),
                verticalArrangement = Arrangement.spacedBy(Dimensions.spacingLG)
            ) {
                // Title
                Text(
                    text = "Kategorie einblenden",
                    fontSize = FontSizes.fontSizeXL,
                    fontWeight = FontWeight.W600,
                    color = colors.textPrimary
                )
                
                // Message
                Text(
                    text = "Möchtest du die Kategorie '$categoryName' wieder einblenden?",
                    fontSize = FontSizes.fontSizeXL,
                    color = colors.textPrimary
                )
                
                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingMD),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Cancel button
                    var isCancelFocused by remember { mutableStateOf(false) }
                    
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(cancelButtonFocusRequester)
                            .onFocusChanged { focusState ->
                                isCancelFocused = focusState.isFocused
                            },
                        shape = RoundedCornerShape(Dimensions.radiusMD),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isCancelFocused) colors.accentColor else colors.textPrimary
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            width = if (isCancelFocused) 2.dp else 1.dp
                        ),
                        contentPadding = PaddingValues(vertical = Dimensions.spacingMD)
                    ) {
                        Text(
                            text = "Abbrechen",
                            fontSize = FontSizes.fontSizeXL,
                            fontWeight = if (isCancelFocused) FontWeight.W600 else FontWeight.Normal
                        )
                    }
                    
                    // Confirm button
                    var isConfirmFocused by remember { mutableStateOf(false) }
                    
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(confirmButtonFocusRequester)
                            .onFocusChanged { focusState ->
                                isConfirmFocused = focusState.isFocused
                            },
                        shape = RoundedCornerShape(Dimensions.radiusMD),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentColor,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(vertical = Dimensions.spacingMD)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(Dimensions.spacingXS))
                        Text(
                            text = "Einblenden",
                            fontSize = FontSizes.fontSizeXL,
                            fontWeight = if (isConfirmFocused) FontWeight.W600 else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}
