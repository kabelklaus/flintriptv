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
    object Search : HomeDestination()
    object Settings : HomeDestination()
}

internal data class NavItem(
    val title: String,
    val icon: ImageVector,
    val destination: HomeDestination
)

internal data class NavItemState(
    val selectedDestination: HomeDestination,
    val pendingFocusDestination: HomeDestination?
)

internal data class NavItemFocusConfig(
    val focusRequester: FocusRequester? = null,
    val externalFocusRequester: FocusRequester? = null
)

internal data class NavItemCallbacks(
    val onClick: () -> Unit,
    val onNavigateRight: (() -> Unit)? = null
)

internal data class NavItemConfig(
    val title: String,
    val isSelected: Boolean
)

private data class ItemColors(
    val backgroundColor: Color,
    val borderColor: Color,
    val iconColor: Color,
    val textColor: Color
)

@Composable
private fun calculateItemColors(
    isSelected: Boolean,
    isFocused: Boolean,
    colors: com.flintr.tv.ui.theme.AppColors
): ItemColors {
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
    
    val borderColor by animateColorAsState(
        targetValue = if (isSelected || isFocused) colors.accentColor else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "borderColor"
    )
    
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
    
    return ItemColors(backgroundColor, borderColor, iconColor, textColor)
}

private fun Modifier.createItemModifier(
    focusConfig: NavItemFocusConfig,
    pendingFocusDestination: HomeDestination?,
    destination: HomeDestination,
    itemConfig: NavItemConfig,
    callbacks: NavItemCallbacks,
    onPositioned: () -> Unit,
    onFocusChanged: (Boolean) -> Unit
): Modifier {
    return this
        .fillMaxWidth()
        .then(
            if (focusConfig.externalFocusRequester != null) {
                Modifier.focusRequester(focusConfig.externalFocusRequester)
            } else if (focusConfig.focusRequester != null) {
                Modifier.focusRequester(focusConfig.focusRequester)
            } else {
                Modifier
            }
        )
        .onGloballyPositioned {
            if (focusConfig.focusRequester != null && 
                pendingFocusDestination == destination) {
                onPositioned()
            }
        }
        .onFocusChanged { focusState ->
            onFocusChanged(focusState.isFocused)
        }
        .focusable()
        .onDpadDirection(
            onRight = if (callbacks.onNavigateRight != null) {
                {
                    Log.i("TVFocus", "[MainNavbar] ➡️ D-Pad Right: '${itemConfig.title}' -> Navigating to CategorySidebar")
                    callbacks.onNavigateRight()
                    true
                }
            } else {
                null
            }
        )
        .onClick(callbacks.onClick)
}

@Composable
private fun NavItemBackground(
    colors: ItemColors,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = colors.backgroundColor,
                shape = RoundedCornerShape(Dimensions.listItemHoverBorderRadius)
            )
            .then(
                if (colors.borderColor != Color.Transparent) {
                    Modifier.border(
                        width = Dimensions.listItemHoverBorderWidth,
                        color = colors.borderColor,
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
        content()
    }
}

@Composable
private fun NavItemContent(
    title: String,
    icon: ImageVector,
    iconColor: Color,
    textColor: Color
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
                    navItem = navItem,
                    state = NavItemState(
                        selectedDestination = selectedDestination,
                        pendingFocusDestination = pendingFocusDestination
                    ),
                    focusConfig = NavItemFocusConfig(
                        focusRequester = focusRequesters[index],
                        externalFocusRequester = getExternalFocusRequester(
                            selectedDestination = selectedDestination,
                            destination = navItem.destination,
                            externalFocusRequested = externalFocusRequested,
                            focusRequester = focusRequester
                        )
                    ),
                    callbacks = NavItemCallbacks(
                        onClick = {
                            if (navItem.destination == HomeDestination.Search) {
                                onSearchClick()
                            } else {
                                onNavigate(navItem.destination)
                            }
                        },
                        onNavigateRight = onNavigateRight
                    ),
                    colors = colors
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
internal fun HomeSidebarItem(
    navItem: NavItem,
    state: NavItemState,
    focusConfig: NavItemFocusConfig,
    callbacks: NavItemCallbacks,
    colors: com.flintr.tv.ui.theme.AppColors,
    modifier: Modifier = Modifier
) {
    val isSelected = navItem.destination == state.selectedDestination
    var isFocused by remember { mutableStateOf(false) }
    var hasRequestedFocus by remember { mutableStateOf(false) }
    var isPositioned by remember { mutableStateOf(false) }
    
    // Request focus when item is positioned and should receive focus
    LaunchedEffect(state.pendingFocusDestination) {
        hasRequestedFocus = false
        isPositioned = false
    }
    
    // Request focus with delay after positioning to ensure FocusRequester is ready
    LaunchedEffect(isPositioned, state.pendingFocusDestination) {
        if (isPositioned && 
            focusConfig.focusRequester != null && 
            state.pendingFocusDestination == navItem.destination && 
            !hasRequestedFocus) {
            hasRequestedFocus = true
            focusConfig.focusRequester.safeRequestFocus("[MainNavbar]", navItem.destination.toString())
        }
    }
    
    val itemColors = calculateItemColors(isSelected, isFocused, colors)
    
    val handleFocusChanged = { focused: Boolean ->
        val wasFocused = isFocused
        isFocused = focused
        if (isFocused != wasFocused) {
            if (isFocused) {
                Log.i("TVFocus", "[MainNavbar] ✅ FOCUSED: '${navItem.title}' (Selected: $isSelected)")
            } else {
                Log.i("TVFocus", "[MainNavbar] ❌ UNFOCUSED: '${navItem.title}'")
            }
        }
    }
    
    val handlePositioned = {
        if (!isPositioned) {
            isPositioned = true
        }
    }
    
    Box(
        modifier = modifier.createItemModifier(
            focusConfig = focusConfig,
            pendingFocusDestination = state.pendingFocusDestination,
            destination = navItem.destination,
            itemConfig = NavItemConfig(
                title = navItem.title,
                isSelected = isSelected
            ),
            callbacks = callbacks,
            onPositioned = handlePositioned,
            onFocusChanged = handleFocusChanged
        )
    ) {
        NavItemBackground(colors = itemColors) {
            NavItemContent(
                title = navItem.title,
                icon = navItem.icon,
                iconColor = itemColors.iconColor,
                textColor = itemColors.textColor
            )
        }
    }
}

