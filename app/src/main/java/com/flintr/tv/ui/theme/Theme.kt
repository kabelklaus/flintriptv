package com.flintr.tv.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Farbschema der App. Die App läuft ausschließlich dunkel — ein Light-Theme
 * gab es nie erreichbar und wurde entfernt.
 */
private val DarkColorScheme = darkColorScheme(
    primary = DarkColors.accentColor,
    secondary = DarkColors.accentColor,
    surface = DarkColors.bgList,
    error = DarkColors.dangerText,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onSurface = DarkColors.textPrimary,
    onError = Color.White,
    background = DarkColors.bgPreview,
    surfaceVariant = DarkColors.bgCard
)

@Composable
fun FlintRTVTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}

/**
 * App-spezifische Farben. Einmal aufgebaut statt bei jeder Recomposition,
 * da es nur eine Palette gibt.
 */
private val AppColorsDark = AppColors(
    bgSidebar = DarkColors.bgSidebar,
    bgSidebarCard = DarkColors.bgSidebarCard,
    bgList = DarkColors.bgList,
    bgPreview = DarkColors.bgPreview,
    bgHover = DarkColors.bgHover,
    bgCard = DarkColors.bgCard,
    cardBorder = DarkColors.cardBorder,
    textPrimary = DarkColors.textPrimary,
    textSecondary = DarkColors.textSecondary,
    accentColor = DarkColors.accentColor,
    accentColorHoverBg = DarkColors.accentColorHoverBg,
    selectionFill = DarkColors.selectionFill,
    accentBright = DarkColors.accentBright,
    dangerBg = DarkColors.dangerBg,
    dangerText = DarkColors.dangerText,
    successColor = DarkColors.successColor,
    errorColor = DarkColors.errorColor,
    successGreen = DarkColors.successGreen,
    buttonPrimary = DarkColors.buttonPrimary,
    buttonPrimaryHover = DarkColors.buttonPrimaryHover,
    iconColor = DarkColors.iconColor,
    iconColorHover = DarkColors.iconColorHover,
    categoryHoverBg = DarkColors.categoryHoverBg,
    categoryDefaultText = DarkColors.categoryDefaultText,
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
    watchlistLive = SharedColors.indigo,
    watchlistMovie = SharedColors.pink,
    watchlistSeries = SharedColors.teal,
    overlayBlack = SharedColors.overlayBlack,
    overlayWhite = SharedColors.overlayWhite,
    categoryChannelCountText = SharedColors.slate400
)

@Composable
fun appColors(): AppColors = AppColorsDark

/**
 * Die Palette ohne Compose-Kontext, für Unit-Tests reiner Farbfunktionen.
 */
fun appColorsForTest(): AppColors = AppColorsDark

/**
 * Data class für app-spezifische Farben.
 */
data class AppColors(
    val bgSidebar: Color,
    val bgSidebarCard: Color,
    val bgList: Color,
    val bgPreview: Color,
    val bgHover: Color,
    val bgCard: Color,
    val cardBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accentColor: Color,
    val accentColorHoverBg: Color,
    val selectionFill: Color,
    val accentBright: Color,
    val dangerBg: Color,
    val dangerText: Color,
    val successColor: Color,
    val errorColor: Color,
    val successGreen: Color,
    val buttonPrimary: Color,
    val buttonPrimaryHover: Color,
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
    val watchlistLive: Color,
    val watchlistMovie: Color,
    val watchlistSeries: Color,
    val overlayBlack: Color,
    val overlayWhite: Color,
    val categoryHoverBg: Color,
    val categoryDefaultText: Color,
    val categoryChannelCountText: Color
)
