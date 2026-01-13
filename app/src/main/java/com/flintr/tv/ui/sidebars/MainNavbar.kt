package com.flintr.tv.ui.screens

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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import android.util.Log
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.SharedColors
import com.flintr.tv.ui.theme.appColors
import com.flintr.tv.ui.utils.onClick
import com.flintr.tv.ui.utils.onDpadDirection
import com.flintr.tv.ui.utils.safeRequestFocus

sealed class HomeDestination {
    object LiveTV : HomeDestination()
    object VOD : HomeDestination()
    object Series : HomeDestination()
    object EPG : HomeDestination()
    object Favorites : HomeDestination()
    object Search : HomeDestination()
    object Settings : HomeDestination()
}

private data class NavItem(
    val title: String,
    val icon: ImageVector,
    val destination: HomeDestination
)

@Composable
fun HomeSidebar(
    selectedDestination: HomeDestination,
    onNavigate: (HomeDestination) -> Unit,
    focusRequester: FocusRequester,
    externalFocusRequested: Boolean = false,
    onNavigateRight: (() -> Unit)? = null,
    onSearchClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = appColors()
    
    val navItems = remember {
        listOf(
            NavItem("Suche", Icons.Default.Search, HomeDestination.Search),
            NavItem("TV", Icons.Default.Tv, HomeDestination.LiveTV),
            NavItem("Movies", Icons.Default.Movie, HomeDestination.VOD),
            NavItem("Series", Icons.Default.PlayArrow, HomeDestination.Series),
            NavItem("EPG", Icons.Default.Schedule, HomeDestination.EPG),
            NavItem("Favoriten", Icons.Default.Favorite, HomeDestination.Favorites),
            NavItem("Settings", Icons.Default.Settings, HomeDestination.Settings)
        )
    }
    
    val focusRequesters = remember(navItems.size) {
        List(navItems.size) { FocusRequester() }
    }
    
    var pendingFocusDestination by remember { mutableStateOf<HomeDestination?>(null) }
    
    LaunchedEffect(selectedDestination) {
        pendingFocusDestination = selectedDestination
    }
    
    Column(
        modifier = modifier
            .width(Dimensions.navbarWidth)
            .fillMaxHeight()
            .background(SharedColors.navSidebarBg)
    ) {
        NavbarLogo()
        
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(horizontal = Dimensions.navbarListHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(Dimensions.navbarItemSpacing, Alignment.Top)
        ) {
            item {
                Spacer(modifier = Modifier.height(Dimensions.spacingSM))
            }
            
            items(navItems.size) { index ->
                val navItem = navItems[index]
                HomeSidebarItem(
                    title = navItem.title,
                    icon = navItem.icon,
                    destination = navItem.destination,
                    selectedDestination = selectedDestination,
                    pendingFocusDestination = pendingFocusDestination,
                    onClick = {
                        if (navItem.destination == HomeDestination.Search) {
                            onSearchClick()
                        } else {
                            onNavigate(navItem.destination)
                        }
                    },
                    colors = colors,
                    focusRequester = focusRequesters[index],
                    externalFocusRequester = getExternalFocusRequester(
                        selectedDestination = selectedDestination,
                        destination = navItem.destination,
                        externalFocusRequested = externalFocusRequested,
                        focusRequester = focusRequester
                    ),
                    onNavigateRight = onNavigateRight
                )
            }
        }
    }
}

@Composable
private fun NavbarLogo() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                top = Dimensions.navbarLogoTopPadding,
                bottom = Dimensions.navbarLogoBottomPadding
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Tv,
            contentDescription = "Logo",
            modifier = Modifier.size(Dimensions.navbarLogoSize),
            tint = Color.White
        )
    }
}

private fun getExternalFocusRequester(
    selectedDestination: HomeDestination,
    destination: HomeDestination,
    externalFocusRequested: Boolean,
    focusRequester: FocusRequester
): FocusRequester? {
    return if (selectedDestination == destination && externalFocusRequested) {
        focusRequester
    } else {
        null
    }
}

@Composable
fun HomeSidebarItem(
    title: String,
    icon: ImageVector,
    destination: HomeDestination,
    selectedDestination: HomeDestination,
    pendingFocusDestination: HomeDestination?,
    onClick: () -> Unit,
    colors: com.flintr.tv.ui.theme.AppColors,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    externalFocusRequester: FocusRequester? = null,
    onNavigateRight: (() -> Unit)? = null
) {
    val isSelected = destination == selectedDestination
    var isFocused by remember { mutableStateOf(false) }
    var hasRequestedFocus by remember { mutableStateOf(false) }
    var isPositioned by remember { mutableStateOf(false) }
    
    // Request focus when item is positioned and should receive focus
    LaunchedEffect(pendingFocusDestination) {
        hasRequestedFocus = false
        isPositioned = false
    }
    
    // Request focus with delay after positioning to ensure FocusRequester is ready
    LaunchedEffect(isPositioned, pendingFocusDestination) {
        if (isPositioned && 
            focusRequester != null && 
            pendingFocusDestination == destination && 
            !hasRequestedFocus) {
            hasRequestedFocus = true
            focusRequester.safeRequestFocus("[MainNavbar]", destination.toString())
        }
    }
    
    // Animation for background color
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSelected -> colors.accentColorHoverBg
            isFocused -> colors.bgHover
            else -> Color.Transparent
        },
        animationSpec = tween<Color>(
            durationMillis = 200,
            easing = FastOutSlowInEasing
        ),
        label = "backgroundColor"
    )
    
    // Border color
    val borderColor by animateColorAsState(
        targetValue = if (isSelected || isFocused) colors.accentColor else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "borderColor"
    )
    
    // Icon and text colors
    val iconColor = when {
        isSelected -> colors.accentColor
        isFocused -> colors.accentColor
        else -> colors.iconColor
    }
    
    val textColor = when {
        isSelected -> colors.accentColor
        isFocused -> colors.textPrimary
        else -> colors.textSecondary
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            // Correct order: focusRequester -> onGloballyPositioned -> onFocusChanged -> focusable -> key handlers
            // Apply external focusRequester if provided (for selected item), otherwise use internal one
            .then(
                if (externalFocusRequester != null) {
                    Modifier.focusRequester(externalFocusRequester)
                } else if (focusRequester != null) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                }
            )
            .onGloballyPositioned {
                // Signal that item is positioned - focus will be requested via LaunchedEffect
                if (focusRequester != null && 
                    pendingFocusDestination == destination && 
                    !isPositioned) {
                    isPositioned = true
                }
            }
            .onFocusChanged { focusState ->
                val wasFocused = isFocused
                isFocused = focusState.isFocused
                if (isFocused != wasFocused) {
                    if (isFocused) {
                        Log.i("TVFocus", "[MainNavbar] ✅ FOCUSED: '$title' (Selected: $isSelected)")
                    } else {
                        Log.i("TVFocus", "[MainNavbar] ❌ UNFOCUSED: '$title'")
                    }
                }
            }
            .focusable()
            .onDpadDirection(
                onRight = if (onNavigateRight != null) {
                    {
                        Log.i("TVFocus", "[MainNavbar] ➡️ D-Pad Right: '$title' -> Navigating to CategorySidebar")
                        onNavigateRight()
                        true
                    }
                } else {
                    null // Let Compose handle standard navigation
                }
            )
            .onClick(onClick)
    ) {
        // Background container with animation
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
                .padding(
                    horizontal = Dimensions.navbarItemPadding,
                    vertical = Dimensions.navbarItemVerticalPadding
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimensions.navbarLabelSpacing)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier.size(Dimensions.navbarIconSize),
                    tint = iconColor
                )
                Text(
                    text = title,
                    fontSize = Dimensions.navbarTextSize.value.sp,
                    fontWeight = FontWeight.W500,
                    color = textColor,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

