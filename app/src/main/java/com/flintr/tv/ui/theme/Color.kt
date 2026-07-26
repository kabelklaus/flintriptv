package com.flintr.tv.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Shared colors used across both light and dark themes
 */
object SharedColors {
    // Brand/Accent colors
    val primary = Color(0xFF1152D4)
    val primaryHover = Color(0xFF0E42A8)
    val primaryHoverBg = Color(0x1A1152D4)
    val primaryBorderLight = Color(0x331152D4)
    
    // Semantic colors
    val blocked = Color(0xFFEF4444)
    val error = Color(0xFFEF4444)
    val proBadge = Color(0xFF9334E6)
    val success = Color(0xFF4CAF50)
    val successGreen = Color(0xFF10B981)
    
    // Avatar colors
    val avatarPlaceholder = Color(0xFF9AA0A6)
    val avatarAccentBg = Color(0xFFC2410C)
    val avatarAccentText = Color(0xFFFFFFFF)
    
    // Gradient colors
    val indigo = Color(0xFF6366F1)
    val indigoEnd = Color(0xFF8B5CF6)
    val pink = Color(0xFFEC4899)
    val pinkEnd = Color(0xFFF43F5E)
    val teal = Color(0xFF14B8A6)
    val tealEnd = Color(0xFF06B6D4)
    val orange = Color(0xFFF59E0B)
    val red = Color(0xFFEF4444)
    
    // Video/Surface colors
    val videoPlayerBg = Color(0xFF000000)
    val sidebarCard = Color(0xFF1A2230)
    val navSidebarBg = Color(0xFF151B26)
    
    // Overlay colors
    val overlayBlack = Color(0xFF000000)
    val overlayWhite = Color(0xFFFFFFFF)
    
    // Slate colors
    val slate400 = Color(0xFF94A3B8)
    
    // Content Card colors
    val contentCardRatingIconColor = Color(0xFFFFC107)
    val contentCardBgColor = Color(0xFF000000) // Black
}

/**
 * Dark mode color definitions
 */
object DarkColors {
    // Background colors
    val bgSidebar = Color(0xFF101722)
    val bgSidebarCard = SharedColors.sidebarCard
    val bgList = Color(0xFF0B111A)
    val bgPreview = Color(0xFF0B111A)
    val bgSelected = Color(0xFF414B5C)
    val bgHover = Color(0xFF2D2E30)
    val bgCard = Color(0xFF1A2230)
    
    // Border colors
    val cardBorder = Color(0xFF3C4043)
    
    // Text colors
    val textPrimary = Color(0xFFE8EAED)
    val textSecondary = Color(0xFF9AA0A6)
    
    // Accent colors
    val accentColor = SharedColors.primary
    val accentColorHoverBg = SharedColors.primaryHoverBg
    val accentColorBorderLight = SharedColors.primaryBorderLight
    
    // Navigation colors
    val navActiveBg = Color(0xFF004A77)
    val navActiveText = Color(0xFFC2E7FF)
    
    // Badge colors
    val badgeBg = Color(0xFFA8C7FA)
    val badgeText = Color(0xFF0B57D0)
    
    // Semantic colors
    val dangerBg = Color(0xFF3B0A0A)
    val dangerBorder = Color(0x73F87171)
    val dangerText = Color(0xFFFECACA)
    val successColor = SharedColors.success
    val errorColor = SharedColors.error
    val successGreen = SharedColors.successGreen
    
    // Button colors
    val buttonPrimary = SharedColors.primary
    val buttonPrimaryHover = SharedColors.primaryHover
    
    // Badge colors
    val proBadgeBg = SharedColors.proBadge
    
    // Icon colors
    val iconColor = Color(0xFFBDC1C6)
    val iconColorHover = textPrimary
    
    // Category Sidebar colors
    val categoryHoverBg = Color(0x0DFFFFFF)
    val categoryDefaultText = Color(0xFFCBD5E1)
}

// Legacy compatibility - keep for existing code
val Primary = SharedColors.primary
val Secondary = SharedColors.primary
val Background = DarkColors.bgPreview
val Surface = DarkColors.bgCard
val Error = DarkColors.dangerText
val OnPrimary = Color(0xFFFFFFFF)
val OnSecondary = Color(0xFFFFFFFF)
val OnBackground = DarkColors.textPrimary
val OnSurface = DarkColors.textPrimary
val OnError = Color(0xFFFFFFFF)

// TV Focus Colors (legacy compatibility)
val FocusHighlight = Color(0xFF03DAC6)
val FocusBackground = Color(0xFF2A2A2A)
val black = Color(0xFF000000)
