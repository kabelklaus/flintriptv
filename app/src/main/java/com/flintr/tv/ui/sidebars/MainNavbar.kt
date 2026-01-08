package com.flintr.tv.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Log
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.SharedColors
import com.flintr.tv.ui.theme.appColors
import com.flintr.tv.ui.utils.onDpadCenter
import com.flintr.tv.ui.utils.onDpadDirection

sealed class HomeDestination {
    object LiveTV : HomeDestination()
    object VOD : HomeDestination()
    object Series : HomeDestination()
    object EPG : HomeDestination()
    object Settings : HomeDestination()
}

@Composable
fun HomeSidebar(
    selectedDestination: HomeDestination,
    onNavigate: (HomeDestination) -> Unit,
    focusRequester: FocusRequester,
    onNavigateRight: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = appColors()
    
    // Set focus on first item when sidebar is first displayed or when focus requester is triggered
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    
    Column(
        modifier = modifier
            .width(Dimensions.navbarWidth)
            .fillMaxHeight()
            .background(SharedColors.navSidebarBg)
    ) {
        // Logo at top
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = Dimensions.navbarLogoTopPadding,
                    bottom = Dimensions.navbarLogoBottomPadding
                ),
            contentAlignment = Alignment.Center
        ) {
            // Logo placeholder - replace with actual logo when available
            Icon(
                imageVector = Icons.Default.Tv,
                contentDescription = "Logo",
                modifier = Modifier.size(Dimensions.navbarLogoSize),
                tint = Color.White
            )
        }
        
        // Main navigation sections
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
            
            item {
                HomeSidebarItem(
                    title = "TV",
                    icon = Icons.Default.Tv,
                    destination = HomeDestination.LiveTV,
                    selectedDestination = selectedDestination,
                    onClick = { onNavigate(HomeDestination.LiveTV) },
                    colors = colors,
                    focusRequester = focusRequester,
                    onNavigateRight = onNavigateRight
                )
            }
            
            item {
                HomeSidebarItem(
                    title = "Movies",
                    icon = Icons.Default.Movie,
                    destination = HomeDestination.VOD,
                    selectedDestination = selectedDestination,
                    onClick = { onNavigate(HomeDestination.VOD) },
                    colors = colors,
                    onNavigateRight = onNavigateRight
                )
            }
            
            item {
                HomeSidebarItem(
                    title = "Series",
                    icon = Icons.Default.PlayArrow,
                    destination = HomeDestination.Series,
                    selectedDestination = selectedDestination,
                    onClick = { onNavigate(HomeDestination.Series) },
                    colors = colors,
                    onNavigateRight = onNavigateRight
                )
            }
            
            item {
                HomeSidebarItem(
                    title = "EPG",
                    icon = Icons.Default.Schedule,
                    destination = HomeDestination.EPG,
                    selectedDestination = selectedDestination,
                    onClick = { onNavigate(HomeDestination.EPG) },
                    colors = colors,
                    onNavigateRight = onNavigateRight
                )
            }
            
            item {
                HomeSidebarItem(
                    title = "Settings",
                    icon = Icons.Default.Settings,
                    destination = HomeDestination.Settings,
                    selectedDestination = selectedDestination,
                    onClick = { onNavigate(HomeDestination.Settings) },
                    colors = colors,
                    onNavigateRight = onNavigateRight
                )
            }
        }
    }
}

@Composable
fun HomeSidebarItem(
    title: String,
    icon: ImageVector,
    destination: HomeDestination,
    selectedDestination: HomeDestination,
    onClick: () -> Unit,
    colors: com.flintr.tv.ui.theme.AppColors,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onNavigateRight: (() -> Unit)? = null
) {
    val isSelected = destination == selectedDestination
    var isFocused by remember { mutableStateOf(false) }
    
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
            // Correct order: focusRequester -> onFocusChanged -> focusable -> key handlers
            .then(
                if (focusRequester != null) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                }
            )
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
            .onDpadCenter(onClick)
            .clickable { onClick() }
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

