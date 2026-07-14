package com.flintr.tv.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Design dimensions and spacing constants
 * Translated from Flutter app_theme.dart
 */
object Dimensions {
    // Base rem to px conversion (1rem = 16px)
    private const val remBase = 16.0
    
    // Base primitive values
    private const val size2 = 2.0
    private const val size4 = 4.0
    private const val size8 = 8.0
    private const val size12 = 12.0
    private const val size16 = 16.0
    
    // Spacing (converted from rem)
    val spacingXXS = size2.dp // 2px
    val spacingXS = size8.dp // 8px (0.5 * remBase)
    val spacingSM = size12.dp // 12px (0.75 * remBase)
    val spacingMD = size16.dp // 16px (1.0 * remBase)
    val spacingLG = (1.5 * remBase).dp // 24px
    val spacingXL = (2.0 * remBase).dp // 32px
    
    // Border radius
    val radiusXXS = size2.dp
    val radiusSM = size4.dp // 0.25rem
    val radiusMD = size8.dp // 0.5rem (lg)
    val radiusLG = size12.dp // 0.75rem (xl)
    val radiusXL = size16.dp
    val radiusPill = 24.0.dp
    val radiusRound = 9999.0.dp // full
    
    // Icon sizes
    val iconSizeXS = size12.dp
    val iconSizeSM = size16.dp
    val iconSize = 18.0.dp
    val iconSizeMD = 20.0.dp
    val iconSizeLG = 20.0.dp
    val iconSizeXL = 64.0.dp
    
    // Layout dimensions
    val sidebarWidth = 230.0.dp
    val listWidth = 360.0.dp
    val headerHeight = 50.0.dp
    val mobileBreakpoint = 768.0.dp
    val categorySidebarWidth = 256.0.dp
    
    // Navbar dimensions
    val navbarWidth = 76.0.dp
    val navbarExpandedWidth = 210.0.dp
    val navbarLogoSize = 40.0.dp
    val navbarIconSize = iconSizeMD
    val navbarTextSize = 16.0.dp
    val navbarItemHeight = 48.0.dp
    val navbarLabelSpacing = size4.dp
    val navbarItemSpacing = spacingSM
    val navbarVerticalBarWidth = size4.dp
    val navbarVerticalBarHeight = 32.0.dp
    val navbarVerticalBarRadius = size2.dp
    val navbarItemPadding = spacingXS
    val navbarItemVerticalPadding = spacingXS
    val navbarLogoTopPadding = spacingLG
    val navbarLogoBottomPadding = spacingSM
    val navbarListHorizontalPadding = spacingXS
    
    // Category Sidebar dimensions
    val categoryItemHeight = 72.0.dp
    val categoryScrollOffset = 100.0.dp
    val categoryTextSpacing = size2.dp
    val categoryBorderWidth = size2.dp
    val categoryShadowAlpha = 0.3f
    val categoryShadowBlurRadius = size12.dp
    val categoryShadowOffsetY = size4.dp
    
    // List Item Hover Style
    val listItemHoverPadding = spacingSM
    val listItemHoverBorderRadius = radiusMD
    val listItemHoverBorderWidth = categoryBorderWidth
    
    // Content Card dimensions
    val contentCardMaxCrossAxisExtent = 133.0.dp
    val contentCardCrossAxisSpacing = size8.dp
    val contentCardMainAxisSpacing = size8.dp
    val contentCardAspectRatio = 0.67f
    val contentCardGridPadding = size8.dp
    
    // Content Card Rating Badge
    val contentCardRatingBadgeTop = 3.0.dp
    val contentCardRatingBadgeRight = 3.0.dp
    val contentCardRatingBadgePaddingH = size4.dp
    val contentCardRatingBadgePaddingV = size2.dp
    val contentCardRatingIconSize = size8.dp
    val contentCardRatingSpacing = 1.0.dp
    val contentCardRatingFontSize = size8.dp
    val contentCardRatingBgAlpha = 0.75f
    
    // Content Card Title Overlay
    val contentCardTitleOverlayPadding = 1.0.dp
    val contentCardTitleContainerPaddingH = 1.0.dp
    val contentCardTitleContainerPaddingV = 1.0.dp
    val contentCardTitleFontSize = size8.dp
    val contentCardTitleMaxLines = 2
    val contentCardTitleGradientAlpha = 0.7f
    val contentCardTitleBgAlpha = 0.75f
    
    // Content Card Text Fallback
    val contentCardTextFallbackPadding = 3.0.dp
    val contentCardTextFallbackFontSize = size8.dp
    val contentCardTextFallbackMaxLines = 4
}
