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
import androidx.compose.ui.unit.dp
import com.flintr.tv.data.model.Category
import com.flintr.tv.ui.screens.HomeDestination
import com.flintr.tv.ui.screens.HomeSidebar
import com.flintr.tv.ui.sidebars.CategoryRow

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
            onNavigateRight = if (showCategories) {
                { onNavigateToCategorySidebar() }
            } else {
                null // Favorites, EPG, Settings haben keine Kategorien
            },
            modifier = Modifier
                .fillMaxHeight()
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
                modifier = Modifier
                    .width(240.dp)
                    .fillMaxHeight()
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
        ) {
            content()
        }
    }
}

