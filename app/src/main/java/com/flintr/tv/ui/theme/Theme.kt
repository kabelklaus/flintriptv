package com.flintr.tv.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Light theme color scheme
 */
private val LightColorScheme = lightColorScheme(
    primary = LightColors.accentColor,
    secondary = LightColors.accentColor,
    surface = LightColors.bgList,
    error = LightColors.dangerText,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    onSurface = LightColors.textPrimary,
    onError = androidx.compose.ui.graphics.Color.White,
    background = LightColors.bgPreview,
    surfaceVariant = LightColors.bgCard
)

/**
 * Dark theme color scheme
 */
private val DarkColorScheme = darkColorScheme(
    primary = DarkColors.accentColor,
    secondary = DarkColors.accentColor,
    surface = DarkColors.bgList,
    error = DarkColors.dangerText,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    onSurface = DarkColors.textPrimary,
    onError = androidx.compose.ui.graphics.Color.White,
    background = DarkColors.bgPreview,
    surfaceVariant = DarkColors.bgCard
)

/**
 * Main theme composable
 * Supports light/dark mode and dynamic colors (Android 12+)
 */
@Composable
fun FlintRTVTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false, // Disabled by default to use custom colors
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

/**
 * Helper to get custom colors based on current theme
 * Similar to AppTheme.colorsOf(context) in Flutter
 */
@Composable
fun appColors(): AppColors {
    val colorScheme = MaterialTheme.colorScheme
    // Dark theme has dark background - check if background color is dark
    // Calculate relative luminance: 0.2126*R + 0.7152*G + 0.0722*B (normalized to 0-1)
    val bg = colorScheme.background
    val luminance = (0.2126f * bg.red + 0.7152f * bg.green + 0.0722f * bg.blue)
    val isDark = luminance < 0.5f
    return if (isDark) {
        AppColors(
            bgSidebar = DarkColors.bgSidebar,
            bgSidebarCard = DarkColors.bgSidebarCard,
            bgList = DarkColors.bgList,
            bgPreview = DarkColors.bgPreview,
            bgSelected = DarkColors.bgSelected,
            bgHover = DarkColors.bgHover,
            bgCard = DarkColors.bgCard,
            cardBorder = DarkColors.cardBorder,
            textPrimary = DarkColors.textPrimary,
            textSecondary = DarkColors.textSecondary,
            accentColor = DarkColors.accentColor,
            accentColorHoverBg = DarkColors.accentColorHoverBg,
            accentColorBorderLight = DarkColors.accentColorBorderLight,
            navActiveBg = DarkColors.navActiveBg,
            navActiveText = DarkColors.navActiveText,
            badgeBg = DarkColors.badgeBg,
            badgeText = DarkColors.badgeText,
            dangerBg = DarkColors.dangerBg,
            dangerBorder = DarkColors.dangerBorder,
            dangerText = DarkColors.dangerText,
            successColor = DarkColors.successColor,
            errorColor = DarkColors.errorColor,
            successGreen = DarkColors.successGreen,
            buttonPrimary = DarkColors.buttonPrimary,
            buttonPrimaryHover = DarkColors.buttonPrimaryHover,
            proBadgeBg = DarkColors.proBadgeBg,
            iconColor = DarkColors.iconColor,
            iconColorHover = DarkColors.iconColorHover,
            colorBlocked = DarkColors.colorBlocked,
            avatarPlaceholder = DarkColors.avatarPlaceholder,
            avatarAccentBg = DarkColors.avatarAccentBg,
            avatarAccentText = DarkColors.avatarAccentText,
            gradientIndigoStart = DarkColors.gradientIndigoStart,
            gradientIndigoEnd = DarkColors.gradientIndigoEnd,
            gradientPinkStart = DarkColors.gradientPinkStart,
            gradientPinkEnd = DarkColors.gradientPinkEnd,
            gradientTealStart = DarkColors.gradientTealStart,
            gradientTealEnd = DarkColors.gradientTealEnd,
            gradientOrangeStart = DarkColors.gradientOrangeStart,
            gradientRedEnd = DarkColors.gradientRedEnd,
            videoPlayerBg = DarkColors.videoPlayerBg,
            favoriteLive = DarkColors.favoriteLive,
            favoriteMovie = DarkColors.favoriteMovie,
            favoriteSeries = DarkColors.favoriteSeries,
            overlayBlack = DarkColors.overlayBlack,
            overlayWhite = DarkColors.overlayWhite,
            categoryHoverBg = DarkColors.categoryHoverBg,
            categoryDefaultText = DarkColors.categoryDefaultText,
            categoryChannelCountText = DarkColors.categoryChannelCountText
        )
    } else {
        AppColors(
            bgSidebar = LightColors.bgSidebar,
            bgSidebarCard = LightColors.bgSidebarCard,
            bgList = LightColors.bgList,
            bgPreview = LightColors.bgPreview,
            bgSelected = LightColors.bgSelected,
            bgHover = LightColors.bgHover,
            bgCard = LightColors.bgCard,
            cardBorder = LightColors.cardBorder,
            textPrimary = LightColors.textPrimary,
            textSecondary = LightColors.textSecondary,
            accentColor = LightColors.accentColor,
            accentColorHoverBg = LightColors.accentColorHoverBg,
            accentColorBorderLight = LightColors.accentColorBorderLight,
            navActiveBg = LightColors.navActiveBg,
            navActiveText = LightColors.navActiveText,
            badgeBg = LightColors.badgeBg,
            badgeText = LightColors.badgeText,
            dangerBg = LightColors.dangerBg,
            dangerBorder = LightColors.dangerBorder,
            dangerText = LightColors.dangerText,
            successColor = LightColors.successColor,
            errorColor = LightColors.errorColor,
            successGreen = LightColors.successGreen,
            buttonPrimary = LightColors.buttonPrimary,
            buttonPrimaryHover = LightColors.buttonPrimaryHover,
            proBadgeBg = LightColors.proBadgeBg,
            iconColor = LightColors.iconColor,
            iconColorHover = LightColors.iconColorHover,
            colorBlocked = LightColors.colorBlocked,
            avatarPlaceholder = LightColors.avatarPlaceholder,
            avatarAccentBg = LightColors.avatarAccentBg,
            avatarAccentText = LightColors.avatarAccentText,
            gradientIndigoStart = LightColors.gradientIndigoStart,
            gradientIndigoEnd = LightColors.gradientIndigoEnd,
            gradientPinkStart = LightColors.gradientPinkStart,
            gradientPinkEnd = LightColors.gradientPinkEnd,
            gradientTealStart = LightColors.gradientTealStart,
            gradientTealEnd = LightColors.gradientTealEnd,
            gradientOrangeStart = LightColors.gradientOrangeStart,
            gradientRedEnd = LightColors.gradientRedEnd,
            videoPlayerBg = LightColors.videoPlayerBg,
            favoriteLive = LightColors.favoriteLive,
            favoriteMovie = LightColors.favoriteMovie,
            favoriteSeries = LightColors.favoriteSeries,
            overlayBlack = LightColors.overlayBlack,
            overlayWhite = LightColors.overlayWhite,
            categoryHoverBg = LightColors.categoryHoverBg,
            categoryDefaultText = LightColors.categoryDefaultText,
            categoryChannelCountText = LightColors.categoryChannelCountText
        )
    }
}

/**
 * Data class for app-specific colors
 * Similar to AppColors in Flutter
 */
data class AppColors(
    val bgSidebar: Color,
    val bgSidebarCard: Color,
    val bgList: Color,
    val bgPreview: Color,
    val bgSelected: Color,
    val bgHover: Color,
    val bgCard: Color,
    val cardBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accentColor: Color,
    val accentColorHoverBg: Color,
    val accentColorBorderLight: Color,
    val navActiveBg: Color,
    val navActiveText: Color,
    val badgeBg: Color,
    val badgeText: Color,
    val dangerBg: Color,
    val dangerBorder: Color,
    val dangerText: Color,
    val successColor: Color,
    val errorColor: Color,
    val successGreen: Color,
    val buttonPrimary: Color,
    val buttonPrimaryHover: Color,
    val proBadgeBg: Color,
    val iconColor: Color,
    val iconColorHover: Color,
    val colorBlocked: Color,
    val avatarPlaceholder: Color,
    val avatarAccentBg: Color,
    val avatarAccentText: Color,
    val gradientIndigoStart: Color,
    val gradientIndigoEnd: Color,
    val gradientPinkStart: Color,
    val gradientPinkEnd: Color,
    val gradientTealStart: Color,
    val gradientTealEnd: Color,
    val gradientOrangeStart: Color,
    val gradientRedEnd: Color,
    val videoPlayerBg: Color,
    val favoriteLive: Color,
    val favoriteMovie: Color,
    val favoriteSeries: Color,
    val overlayBlack: Color,
    val overlayWhite: Color,
    val categoryHoverBg: Color,
    val categoryDefaultText: Color,
    val categoryChannelCountText: Color
)

