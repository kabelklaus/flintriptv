package com.flintr.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyListState
import com.flintr.tv.data.model.Category
import com.flintr.tv.ui.screens.HomeDestination
import com.flintr.tv.ui.screens.HomeSidebar
import com.flintr.tv.ui.sidebars.CategoryRow
import com.flintr.tv.ui.sidebars.CategoryRowParams

/**
 * Creates a callback for onGloballyPositioned that invokes the ready callback
 * when the component has valid dimensions.
 */
private fun createPositionedCallback(
    onReady: (() -> Unit)?
): (LayoutCoordinates) -> Unit {
    return { coordinates ->
        val size = coordinates.size
        if (size.width > 0 && size.height > 0) {
            onReady?.invoke()
        }
    }
}

/**
 * Creates the onNavigateRight callback for HomeSidebar based on showCategories flag.
 */
@Composable
private fun createNavbarOnNavigateRight(
    showCategories: Boolean,
    onNavigateToCategorySidebar: () -> Unit
): (() -> Unit)? {
    return if (showCategories) {
        { onNavigateToCategorySidebar() }
    } else {
        null // Watchlist, EPG, Settings haben keine Kategorien
    }
}

/**
 * Creates the modifier for the navbar with positioning callback.
 */
private fun Modifier.createNavbarModifier(
    onNavbarReady: (() -> Unit)?
): Modifier {
    return this
        .fillMaxHeight()
        .onGloballyPositioned(createPositionedCallback(onNavbarReady))
}

/**
 * Creates the modifier for the category sidebar with positioning callback.
 */
private fun Modifier.createCategorySidebarModifier(
    onCategorySidebarReady: (() -> Unit)?
): Modifier {
    return this
        .width(240.dp)
        .fillMaxHeight()
        .onGloballyPositioned(createPositionedCallback(onCategorySidebarReady))
}

/**
 * Parameters for MainLayout component.
 */
data class MainLayoutParams(
    // Navigation
    val selectedDestination: HomeDestination,
    val onDestinationChange: (HomeDestination) -> Unit,
    
    // Categories
    val showCategories: Boolean,
    val categories: List<Category>,
    val selectedCategoryId: String?,
    val onCategorySelected: (String?) -> Unit,
    val onCategoryHidden: (Category) -> Unit,
    val itemCounts: Map<String, Int>? = null,
    
    // Focus Management
    val navbarFocusRequester: FocusRequester,
    val categorySidebarFocusRequester: FocusRequester,
    val categoryFocusRequesterMap: MutableMap<String, FocusRequester>,
    val onNavigateToNavbar: () -> Unit,
    val onNavigateToCategorySidebar: () -> Unit,
    val onNavigateToContentList: () -> Unit,
    
    // Callbacks
    val onSearchClick: () -> Unit = {},
    val onNavbarReady: (() -> Unit)? = null,
    val onCategorySidebarReady: (() -> Unit)? = null,
    val onContentListReady: (() -> Unit)? = null,
    
    // State
    val categorySidebarListState: LazyListState
)

@Composable
fun MainLayout(
    params: MainLayoutParams,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Left Sidebar: Navigation
        HomeSidebar(
            selectedDestination = params.selectedDestination,
            onNavigate = params.onDestinationChange,
            focusRequester = params.navbarFocusRequester,
            onNavigateRight = createNavbarOnNavigateRight(params.showCategories, params.onNavigateToCategorySidebar),
            onSearchClick = params.onSearchClick,
            modifier = Modifier.createNavbarModifier(params.onNavbarReady)
        )
        
        // Middle Column: Categories (if applicable)
        if (params.showCategories) {
            CategoryRow(
                params = CategoryRowParams(
                    categories = params.categories,
                    selectedCategoryId = params.selectedCategoryId,
                    onCategorySelected = params.onCategorySelected,
                    itemCounts = params.itemCounts,
                    focusRequester = params.categorySidebarFocusRequester,
                    onNavigateLeft = params.onNavigateToNavbar,
                    onNavigateRight = { params.onNavigateToContentList() },
                    onCategoryHidden = { category ->
                        // Don't allow hiding watchlist category
                        if (category.categoryId != "__watchlist__") {
                            params.onCategoryHidden(category)
                        }
                    },
                    listState = params.categorySidebarListState
                ),
                modifier = Modifier.createCategorySidebarModifier(params.onCategorySidebarReady)
            )
        } else {
            // Empty space for EPG or other screens without categories
            Spacer(
                modifier = Modifier
                    .width(240.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface)
            )
        }
        
        // Right Column: Content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .onGloballyPositioned(createPositionedCallback(params.onContentListReady))
        ) {
            content()
        }
    }
}
