package com.flintr.tv.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Farben, die unabhängig vom Flächenkontext gelten.
 */
object SharedColors {
    // Brand/Accent colors
    //
    // accent trägt die Marke und den Fokus: 8,7:1 gegen den Hintergrund.
    // selectionFill füllt ausgewählte Elemente — dort liegt weißer Text
    // darauf, weshalb der schwächere Kontrast gegen Schwarz keine Rolle
    // spielt. Als Akzentfarbe käme selectionFill nur auf 4,9:1.
    val accent = Color(0xFF1B84FF)
    val accentBright = Color(0xFF38BDF8)
    val selectionFill = Color(0xFF1152D4)
    val primaryHover = Color(0xFF0E42A8)
    val accentHoverBg = Color(0x241B84FF)
    
    // Semantic colors
    val blocked = Color(0xFFEF4444)
    val error = Color(0xFFEF4444)
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
    val sidebarCard = Color(0xFF1C1D1F)
    val navSidebarBg = Color(0xFF141518)
    
    // Overlay colors
    val overlayBlack = Color(0xFF000000)
    val overlayWhite = Color(0xFFFFFFFF)
    
    // Slate colors
    val slate400 = Color(0xFF94A3B8)
    
    // Content Card colors
    val contentCardRatingIconColor = Color(0xFFFFC107)
    // Karten liegen als Fläche über dem Hintergrund, nicht auf reinem Schwarz —
    // sonst verschwinden sie, solange kein Artwork geladen ist.
    val contentCardBgColor = Color(0xFF1C1D1F)
}

/**
 * Dark mode color definitions
 */
object DarkColors {
    // Background colors
    val bgSidebar = Color(0xFF141518)
    val bgSidebarCard = SharedColors.sidebarCard
    val bgList = Color(0xFF0D0D0F)
    val bgPreview = Color(0xFF0D0D0F)
    val bgHover = Color(0xFF2A2C2F)
    val bgCard = Color(0xFF1C1D1F)

    // Border colors
    val cardBorder = Color(0xFF2E3033)

    // Text colors
    val textPrimary = Color(0xFFF2F3F5)
    val textSecondary = Color(0xFF8B9096)

    // Accent colors
    val accentColor = SharedColors.accent
    val accentColorHoverBg = SharedColors.accentHoverBg
    val selectionFill = SharedColors.selectionFill
    val accentBright = SharedColors.accentBright
    
    // Semantic colors
    val dangerBg = Color(0xFF3B0A0A)
    val dangerText = Color(0xFFFECACA)
    val successColor = SharedColors.success
    val errorColor = SharedColors.error
    val successGreen = SharedColors.successGreen
    
    // Button colors
    val buttonPrimary = SharedColors.selectionFill
    val buttonPrimaryHover = SharedColors.primaryHover
    
    // Icon colors
    val iconColor = Color(0xFFBDC1C6)
    val iconColorHover = textPrimary
    
    // Category Sidebar colors
    val categoryHoverBg = Color(0x0DFFFFFF)
    val categoryDefaultText = Color(0xFFCBD5E1)
}
