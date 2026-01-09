package com.flintr.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.flintr.tv.data.model.Category
import com.flintr.tv.ui.screens.HomeDestination
import com.flintr.tv.ui.screens.HomeSidebar
import com.flintr.tv.ui.sidebars.CategoryRow

/**
 * Creates a callback for onGloballyPositioned that invokes the ready callback
 * when the component has valid dimensions.
 */
@Composable
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
        null // Favorites, EPG, Settings haben keine Kategorien
    }
}

/**
 * Creates the modifier for the navbar with positioning callback.
 */
@Composable
private fun createNavbarModifier(
    onNavbarReady: (() -> Unit)?
): Modifier {
    return Modifier
        .fillMaxHeight()
        .onGloballyPositioned(createPositionedCallback(onNavbarReady))
}

/**
 * Creates the modifier for the category sidebar with positioning callback.
 */
@Composable
private fun createCategorySidebarModifier(
    onCategorySidebarReady: (() -> Unit)?
): Modifier {
    return Modifier
        .width(240.dp)
        .fillMaxHeight()
        .onGloballyPositioned(createPositionedCallback(onCategorySidebarReady))
}


@Composable
fun MainLayout(
    selectedDestination: HomeDestination,
    onDestinationChange: (HomeDestination) -> Unit,
    showCategories: Boolean,
    categories: List<Category>,
    selectedCategoryId: String?,
    onCategorySelected: (String?) -> Unit,
    navbarFocusRequester: FocusRequester,
    categorySidebarFocusRequester: FocusRequester,
    contentListFocusRequester: FocusRequester,
    onNavigateToNavbar: () -> Unit,
    onNavigateToCategorySidebar: () -> Unit,
    onNavigateToContentList: () -> Unit,
    onNavbarReady: (() -> Unit)? = null,
    onCategorySidebarReady: (() -> Unit)? = null,
    onContentListReady: (() -> Unit)? = null,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    // Track if external focus was requested (from CategoryRow back to Navbar)
    var externalFocusRequested by remember { mutableStateOf(false) }
    
    // Wrap onNavigateToNavbar to set externalFocusRequested flag
    val wrappedOnNavigateToNavbar: () -> Unit = {
        externalFocusRequested = true
        onNavigateToNavbar()
    }
    
    Row(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Left Sidebar: Navigation
        HomeSidebar(
            selectedDestination = selectedDestination,
            onNavigate = { destination ->
                externalFocusRequested = false // Reset when destination changes via click
                onDestinationChange(destination)
            },
            focusRequester = navbarFocusRequester,
            externalFocusRequested = externalFocusRequested,
            onNavigateRight = createNavbarOnNavigateRight(showCategories, onNavigateToCategorySidebar),
            modifier = createNavbarModifier(onNavbarReady)
        )
        
        // Middle Column: Categories (if applicable)
        if (showCategories) {
            CategoryRow(
                categories = categories,
                selectedCategoryId = selectedCategoryId,
                onCategorySelected = onCategorySelected,
                focusRequester = categorySidebarFocusRequester,
                onNavigateLeft = { wrappedOnNavigateToNavbar() },
                onNavigateRight = { onNavigateToContentList() },
                modifier = createCategorySidebarModifier(onCategorySidebarReady)
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
                .onGloballyPositioned(createPositionedCallback(onContentListReady))
        ) {
            content()
        }
    }
}

