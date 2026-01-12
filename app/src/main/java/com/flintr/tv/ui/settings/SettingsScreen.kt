package com.flintr.tv.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import android.util.Log
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.FontSizes
import com.flintr.tv.ui.theme.appColors
import com.flintr.tv.ui.utils.onBackButton
import com.flintr.tv.ui.utils.onClick
import com.flintr.tv.ui.utils.onDpadDirection
import com.flintr.tv.data.local.CategoryVisibilityService
import com.flintr.tv.viewmodel.MainViewModel

sealed class SettingsDestination {
    object Main : SettingsDestination()
    object Connection : SettingsDestination()
    object Playback : SettingsDestination()
    object General : SettingsDestination()
    object Info : SettingsDestination()
    object HiddenCategories : SettingsDestination()
}

@Composable
fun SettingsScreen(
    focusRequester: FocusRequester,
    onNavigateLeft: () -> Unit,
    categoryVisibilityService: CategoryVisibilityService,
    mainViewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    var currentDestination by remember { mutableStateOf<SettingsDestination>(SettingsDestination.Main) }
    val colors = appColors()
    
    // Snackbar support
    val snackbarHostState = remember { SnackbarHostState() }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    
    // Show snackbar when message is set
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            snackbarMessage = null
        }
    }
    
    // Fade-in animation
    var isVisible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "fadeIn"
    )
    
    LaunchedEffect(Unit) {
        isVisible = true
    }
    
    // Handle Back button using BackHandler (works even when IME is visible)
    // Only active when in Main screen - sub-screens have their own BackHandler
    BackHandler(enabled = currentDestination is SettingsDestination.Main) {
        Log.i("SettingsNav", "🔙 BACK: SettingsMainScreen -> AppNavigation (Main Screen) (via BackHandler)")
        onNavigateLeft() // Exit settings, go back to main screen
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgPreview)
            .alpha(alpha)
    ) {
        when (currentDestination) {
            is SettingsDestination.Main -> {
                SettingsMainScreen(
                    onCategorySelected = { destination ->
                        currentDestination = destination
                    },
                    focusRequester = focusRequester,
                    onNavigateLeft = onNavigateLeft
                )
            }
            is SettingsDestination.Connection -> {
                SettingsConnection(
                    onBack = {
                        currentDestination = SettingsDestination.Main
                    },
                    onNavigateLeft = onNavigateLeft
                )
            }
            is SettingsDestination.Playback -> {
                SettingsPlayback(
                    onBack = {
                        currentDestination = SettingsDestination.Main
                    },
                    onNavigateLeft = onNavigateLeft
                )
            }
            is SettingsDestination.General -> {
                SettingsGeneral(
                    onBack = {
                        currentDestination = SettingsDestination.Main
                    },
                    onNavigateLeft = onNavigateLeft,
                    categoryVisibilityService = categoryVisibilityService,
                    onNavigateToHiddenCategories = {
                        currentDestination = SettingsDestination.HiddenCategories
                    }
                )
            }
            is SettingsDestination.Info -> {
                SettingsInfo(
                    onBack = {
                        currentDestination = SettingsDestination.Main
                    },
                    onNavigateLeft = onNavigateLeft
                )
            }
            is SettingsDestination.HiddenCategories -> {
                SubSettingsHiddenCategories(
                    onBack = {
                        currentDestination = SettingsDestination.Main
                    },
                    onNavigateLeft = onNavigateLeft,
                    categoryVisibilityService = categoryVisibilityService,
                    mainViewModel = mainViewModel,
                    onShowSnackbar = { message ->
                        snackbarMessage = message
                    }
                )
            }
        }
        
        // Snackbar for feedback messages
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun SettingsMainScreen(
    onCategorySelected: (SettingsDestination) -> Unit,
    focusRequester: FocusRequester,
    onNavigateLeft: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = appColors()
    
    // Set focus on first item when screen is first displayed
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(Dimensions.spacingXL),
        verticalArrangement = Arrangement.spacedBy(Dimensions.navbarItemSpacing, Alignment.Top),
        contentPadding = PaddingValues(vertical = Dimensions.spacingMD)
    ) {
        item {
                SettingsCategoryCard(
                    title = "Verbindung",
                    subtitle = "Server und Anmeldedaten verwalten",
                    icon = Icons.Default.Link,
                    onClick = {
                        Log.i("SettingsNav", "➡️ NAVIGATE: SettingsMainScreen -> SettingsConnection")
                        onCategorySelected(SettingsDestination.Connection)
                    },
                    colors = colors,
                    focusRequester = focusRequester,
                    onNavigateLeft = {
                        Log.i("SettingsNav", "🔙 BACK: SettingsMainScreen -> AppNavigation (Main Screen) via D-Pad Left")
                        onNavigateLeft()
                    }
                )
        }
        
        item {
                SettingsCategoryCard(
                    title = "Wiedergabe",
                    subtitle = "Videoqualität und Player-Einstellungen",
                    icon = Icons.Default.PlayArrow,
                    onClick = {
                        Log.i("SettingsNav", "➡️ NAVIGATE: SettingsMainScreen -> SettingsPlayback")
                        onCategorySelected(SettingsDestination.Playback)
                    },
                    colors = colors,
                    onNavigateLeft = {
                        Log.i("SettingsNav", "🔙 BACK: SettingsMainScreen -> AppNavigation (Main Screen) via D-Pad Left")
                        onNavigateLeft()
                    }
                )
        }
        
        item {
                SettingsCategoryCard(
                    title = "Allgemein",
                    subtitle = "Sprache, Theme und Cache",
                    icon = Icons.Default.Settings,
                    onClick = {
                        Log.i("SettingsNav", "➡️ NAVIGATE: SettingsMainScreen -> SettingsGeneral")
                        onCategorySelected(SettingsDestination.General)
                    },
                    colors = colors,
                    onNavigateLeft = {
                        Log.i("SettingsNav", "🔙 BACK: SettingsMainScreen -> AppNavigation (Main Screen) via D-Pad Left")
                        onNavigateLeft()
                    }
                )
        }
        
        item {
                SettingsCategoryCard(
                    title = "Info",
                    subtitle = "Version und Updates",
                    icon = Icons.Default.Info,
                    onClick = {
                        Log.i("SettingsNav", "➡️ NAVIGATE: SettingsMainScreen -> SettingsInfo")
                        onCategorySelected(SettingsDestination.Info)
                    },
                    colors = colors,
                    onNavigateLeft = {
                        Log.i("SettingsNav", "🔙 BACK: SettingsMainScreen -> AppNavigation (Main Screen) via D-Pad Left")
                        onNavigateLeft()
                    }
                )
        }
    }
}

@Composable
private fun SettingsCategoryCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    colors: com.flintr.tv.ui.theme.AppColors,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onNavigateLeft: (() -> Unit)? = null
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
    
    // Icon and text colors
    val iconColor = if (isFocused) colors.accentColor else colors.iconColor
    val titleColor = if (isFocused) colors.accentColor else colors.textPrimary
    val subtitleColor = if (isFocused) colors.textPrimary else colors.textSecondary
    
    Box(
        modifier = modifier
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
                onLeft = if (onNavigateLeft != null) {
                    {
                        onNavigateLeft()
                        true
                    }
                } else {
                    null
                }
            )
            .onBackButton {
                onNavigateLeft?.invoke()
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
                horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingLG),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier.size(28.dp),
                    tint = iconColor
                )
                
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Dimensions.spacingXXS)
                ) {
                    Text(
                        text = title,
                        fontSize = FontSizes.fontSizeXL,
                        fontWeight = FontWeight.W600,
                        color = titleColor
                    )
                    Text(
                        text = subtitle,
                        fontSize = FontSizes.fontSizeMD,
                        color = subtitleColor
                    )
                }
            }
        }
    }
}

