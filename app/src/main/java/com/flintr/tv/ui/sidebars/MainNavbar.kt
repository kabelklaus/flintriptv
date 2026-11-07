package com.flintr.tv.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import android.util.Log
import com.flintr.tv.R
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.SharedColors
import com.flintr.tv.ui.theme.appColors
import com.flintr.tv.ui.theme.tvFocusRing
import com.flintr.tv.ui.utils.onClick
import com.flintr.tv.ui.utils.onDpadDirection
import com.flintr.tv.ui.utils.safeRequestFocus
import kotlinx.coroutines.launch

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
    val pendingFocusDestination: HomeDestination?,
    val isExpanded: Boolean = false
)

/**
 * Hält den fokussierten Eintrag fest, bis der Fokus tatsächlich woanders landet.
 */
private fun resolveFocusedDestination(
    current: HomeDestination?,
    destination: HomeDestination,
    isFocused: Boolean
): HomeDestination? = when {
    isFocused -> destination
    current == destination -> null
    else -> current
}

/**
 * Suche öffnet das Overlay statt zu navigieren.
 */
private fun dispatchNavItemClick(
    destination: HomeDestination,
    onNavigate: (HomeDestination) -> Unit,
    onSearchClick: () -> Unit
) {
    if (destination == HomeDestination.Search) {
        onSearchClick()
    } else {
        onNavigate(destination)
    }
}

internal data class NavItemFocusConfig(
    val focusRequester: FocusRequester? = null
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
            isSelected -> colors.selectionFill
            isFocused -> colors.accentColorHoverBg
            else -> Color.Transparent
        },
        animationSpec = tween<Color>(
            durationMillis = 200,
            easing = FastOutSlowInEasing
        ),
        label = "backgroundColor"
    )

    val iconColor = when {
        isSelected -> Color.White
        isFocused -> colors.accentColor
        else -> colors.iconColor
    }

    val textColor = when {
        isSelected -> Color.White
        isFocused -> colors.textPrimary
        else -> colors.textSecondary
    }

    return ItemColors(backgroundColor, iconColor, textColor)
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
            if (focusConfig.focusRequester != null) {
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
private fun rememberNavbarWidth(isExpanded: Boolean) = animateDpAsState(
    targetValue = if (isExpanded) Dimensions.navbarExpandedWidth else Dimensions.navbarWidth,
    animationSpec = tween(
        durationMillis = 180,
        easing = FastOutSlowInEasing
    ),
    label = "navbarWidth"
)

@Composable
private fun NavItemBackground(
    colors: ItemColors,
    isFocused: Boolean,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(Dimensions.listItemHoverBorderRadius)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimensions.navbarItemHeight)
            // Fokus-Rahmen zuerst, damit die Skalierung Hintergrund und Inhalt
            // gemeinsam erfasst (wie in ContentCard.kt) statt nur den Rahmen.
            .tvFocusRing(
                isFocused = isFocused,
                onSelectionFill = isSelected,
                shape = shape
            )
            .background(
                color = colors.backgroundColor,
                shape = shape
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
    textColor: Color,
    isExpanded: Boolean
) {
    if (isExpanded) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimensions.navbarLabelSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(Dimensions.navbarIconSize),
                tint = iconColor
            )
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                fontSize = Dimensions.navbarTextSize.value.sp,
                fontWeight = FontWeight.W500,
                color = textColor,
                textAlign = TextAlign.Start,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        return
    }

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
    }
}

@Composable
fun HomeSidebar(
    selectedDestination: HomeDestination,
    onNavigate: (HomeDestination) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    onNavigateRight: (() -> Unit)? = null,
    onSearchClick: () -> Unit = {}
) {
    val colors = appColors()
    val searchLabel = stringResource(R.string.nav_search)
    val tvLabel = stringResource(R.string.nav_tv)
    val moviesLabel = stringResource(R.string.nav_movies)
    val seriesLabel = stringResource(R.string.nav_series)
    val settingsLabel = stringResource(R.string.nav_settings)

    val navItems = remember(searchLabel, tvLabel, moviesLabel, seriesLabel, settingsLabel) {
        listOf(
            NavItem(searchLabel, Icons.Default.Search, HomeDestination.Search),
            NavItem(tvLabel, Icons.Default.Tv, HomeDestination.LiveTV),
            NavItem(moviesLabel, Icons.Default.Movie, HomeDestination.VOD),
            NavItem(seriesLabel, Icons.Default.PlayArrow, HomeDestination.Series),
            NavItem(settingsLabel, Icons.Default.Settings, HomeDestination.Settings)
        )
    }
    
    val focusRequesters = remember(navItems.size) {
        List(navItems.size) { FocusRequester() }
    }
    
    var pendingFocusDestination by remember { mutableStateOf<HomeDestination?>(null) }
    var focusedDestination by remember { mutableStateOf<HomeDestination?>(null) }
    val isExpanded = focusedDestination != null
    val navbarWidth by rememberNavbarWidth(isExpanded)
    
    LaunchedEffect(selectedDestination) {
        pendingFocusDestination = selectedDestination
    }
    
    Column(
        modifier = modifier
            .width(navbarWidth)
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
                        pendingFocusDestination = pendingFocusDestination,
                        isExpanded = isExpanded
                    ),
                    focusConfig = NavItemFocusConfig(
                        // IMPORTANT: This shared requester is the only return path from the
                        // category sidebar to the active navbar item. Do not replace it with
                        // the local requester without updating the focus-navigation test.
                        focusRequester = if (navItem.destination == selectedDestination) focusRequester else focusRequesters[index]
                    ),
                    callbacks = NavItemCallbacks(
                        onClick = {
                            dispatchNavItemClick(navItem.destination, onNavigate, onSearchClick)
                        },
                        onNavigateRight = onNavigateRight
                    ),
                    colors = colors,
                    onFocusChanged = { destination, isFocused ->
                        focusedDestination = resolveFocusedDestination(focusedDestination, destination, isFocused)
                    }
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
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = "Flintr TV Logo",
            modifier = Modifier.size(Dimensions.iconSizeXL)
        )
    }
}

// Stable tag names: KClass.simpleName would change under R8 obfuscation in release builds.
private fun HomeDestination.testTagName(): String = when (this) {
    HomeDestination.LiveTV -> "LiveTV"
    HomeDestination.VOD -> "VOD"
    HomeDestination.Series -> "Series"
    HomeDestination.EPG -> "EPG"
    HomeDestination.Search -> "Search"
    HomeDestination.Settings -> "Settings"
}

@Composable
internal fun HomeSidebarItem(
    navItem: NavItem,
    state: NavItemState,
    focusConfig: NavItemFocusConfig,
    callbacks: NavItemCallbacks,
    colors: com.flintr.tv.ui.theme.AppColors,
    onFocusChanged: (HomeDestination, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val isSelected = navItem.destination == state.selectedDestination
    var isFocused by remember { mutableStateOf(false) }
    var hasRequestedFocus by remember { mutableStateOf(false) }
    var isPositioned by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

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
        onFocusChanged(navItem.destination, focused)
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

    // Touch tap on an already-selected item never changes selectedDestination, so the
    // LaunchedEffect(selectedDestination) focus-request above never fires. Request focus
    // on click too - a no-op if the item already has focus (D-Pad case).
    val handleClick = {
        focusConfig.focusRequester?.let { requester ->
            coroutineScope.launch {
                requester.safeRequestFocus("[MainNavbar]", navItem.destination.toString())
            }
        }
        callbacks.onClick()
    }

    Box(
        modifier = modifier
            .testTag("navbar-item-${navItem.destination.testTagName()}")
            .createItemModifier(
            focusConfig = focusConfig,
            pendingFocusDestination = state.pendingFocusDestination,
            destination = navItem.destination,
            itemConfig = NavItemConfig(
                title = navItem.title,
                isSelected = isSelected
            ),
            callbacks = callbacks.copy(onClick = handleClick),
            onPositioned = handlePositioned,
            onFocusChanged = handleFocusChanged
        )
    ) {
        NavItemBackground(colors = itemColors, isFocused = isFocused, isSelected = isSelected) {
            NavItemContent(
                title = navItem.title,
                icon = navItem.icon,
                iconColor = itemColors.iconColor,
                textColor = itemColors.textColor,
                isExpanded = state.isExpanded
            )
        }
    }
}
