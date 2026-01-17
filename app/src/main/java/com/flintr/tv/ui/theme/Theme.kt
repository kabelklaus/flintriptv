package com.flintr.tv.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.ColorScheme
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
 * Extension property to determine if a ColorScheme represents a dark theme
 * Uses relative luminance calculation: 0.2126*R + 0.7152*G + 0.0722*B
 */
val ColorScheme.isDark: Boolean
    get() {
        val luminance = (0.2126f * background.red + 0.7152f * background.green + 0.0722f * background.blue)
        return luminance < 0.5f
    }

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
 * Data class containing all theme-specific color properties
 * Used to reduce parameter count in conversion functions
 */
private data class ThemeSpecificColors(
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
    val categoryHoverBg: Color,
    val categoryDefaultText: Color
)

/**
 * Extension function to convert LightColors to ThemeSpecificColors
 */
private fun LightColors.toThemeSpecificColors(): ThemeSpecificColors {
    return ThemeSpecificColors(
        bgSidebar, bgSidebarCard, bgList, bgPreview, bgSelected, bgHover,
        bgCard, cardBorder, textPrimary, textSecondary, accentColor,
        accentColorHoverBg, accentColorBorderLight, navActiveBg, navActiveText,
        badgeBg, badgeText, dangerBg, dangerBorder, dangerText,
        successColor, errorColor, successGreen, buttonPrimary, buttonPrimaryHover,
        proBadgeBg, iconColor, iconColorHover, categoryHoverBg, categoryDefaultText
    )
}

/**
 * Extension function to convert DarkColors to ThemeSpecificColors
 */
private fun DarkColors.toThemeSpecificColors(): ThemeSpecificColors {
    return ThemeSpecificColors(
        bgSidebar, bgSidebarCard, bgList, bgPreview, bgSelected, bgHover,
        bgCard, cardBorder, textPrimary, textSecondary, accentColor,
        accentColorHoverBg, accentColorBorderLight, navActiveBg, navActiveText,
        badgeBg, badgeText, dangerBg, dangerBorder, dangerText,
        successColor, errorColor, successGreen, buttonPrimary, buttonPrimaryHover,
        proBadgeBg, iconColor, iconColorHover, categoryHoverBg, categoryDefaultText
    )
}

/**
 * Extension function to convert ThemeSpecificColors to AppColors
 * Copies all theme-specific properties and adds shared colors from SharedColors
 * 
 * Note: The first 30 properties of AppColors are identical to ThemeSpecificColors.
 * This duplication is structurally necessary because:
 * 1. AppColors is a data class used throughout the codebase with direct property access
 * 2. ThemeSpecificColors serves as an intermediate type to reduce parameter count
 * 3. Both need to be data classes for proper destructuring and equality checks
 * The explicit property copying ensures type safety and maintains API compatibility.
 */
private fun ThemeSpecificColors.toAppColors(): AppColors {
    return AppColors(
        bgSidebar = bgSidebar,
        bgSidebarCard = bgSidebarCard,
        bgList = bgList,
        bgPreview = bgPreview,
        bgSelected = bgSelected,
        bgHover = bgHover,
        bgCard = bgCard,
        cardBorder = cardBorder,
        textPrimary = textPrimary,
        textSecondary = textSecondary,
        accentColor = accentColor,
        accentColorHoverBg = accentColorHoverBg,
        accentColorBorderLight = accentColorBorderLight,
        navActiveBg = navActiveBg,
        navActiveText = navActiveText,
        badgeBg = badgeBg,
        badgeText = badgeText,
        dangerBg = dangerBg,
        dangerBorder = dangerBorder,
        dangerText = dangerText,
        successColor = successColor,
        errorColor = errorColor,
        successGreen = successGreen,
        buttonPrimary = buttonPrimary,
        buttonPrimaryHover = buttonPrimaryHover,
        proBadgeBg = proBadgeBg,
        iconColor = iconColor,
        iconColorHover = iconColorHover,
        categoryHoverBg = categoryHoverBg,
        categoryDefaultText = categoryDefaultText,
        // Shared colors
        colorBlocked = SharedColors.blocked,
        avatarPlaceholder = SharedColors.avatarPlaceholder,
        avatarAccentBg = SharedColors.avatarAccentBg,
        avatarAccentText = SharedColors.avatarAccentText,
        gradientIndigoStart = SharedColors.indigo,
        gradientIndigoEnd = SharedColors.indigoEnd,
        gradientPinkStart = SharedColors.pink,
        gradientPinkEnd = SharedColors.pinkEnd,
        gradientTealStart = SharedColors.teal,
        gradientTealEnd = SharedColors.tealEnd,
        gradientOrangeStart = SharedColors.orange,
        gradientRedEnd = SharedColors.red,
        videoPlayerBg = SharedColors.videoPlayerBg,
        favoriteLive = SharedColors.indigo,
        favoriteMovie = SharedColors.pink,
        favoriteSeries = SharedColors.teal,
        overlayBlack = SharedColors.overlayBlack,
        overlayWhite = SharedColors.overlayWhite,
        categoryChannelCountText = SharedColors.slate400
    )
}

/**
 * Helper to get custom colors based on current theme
 * Similar to AppTheme.colorsOf(context) in Flutter
 */
@Composable
fun appColors(): AppColors {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.isDark
    return if (isDark) {
        DarkColors.toThemeSpecificColors().toAppColors()
    } else {
        LightColors.toThemeSpecificColors().toAppColors()
    }
}

/**
 * Data class for app-specific colors
 * Similar to AppColors in Flutter
 * 
 * Note: The first 30 properties (bgSidebar through categoryDefaultText) are
 * structurally identical to ThemeSpecificColors. This duplication is necessary
 * because AppColors is used throughout the codebase with direct property access
 * (e.g., colors.bgSidebar), and changing this would require a breaking change.
 * ThemeSpecificColors serves as an intermediate type to reduce parameter count
 * in helper functions, while AppColors provides the public API.
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

