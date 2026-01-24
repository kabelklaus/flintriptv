package com.flintr.tv.ui.common

/**
 * Constants for player-related functionality
 */
object PlayerConstants {
    // Auto-play countdown configuration
    const val AUTO_PLAY_COUNTDOWN_SECONDS = 10
    const val AUTO_PLAY_UPDATE_INTERVAL_MS = 100L
    const val AUTO_PLAY_COUNTDOWN_STEPS = 100
    
    // Next episode button animation
    const val NEXT_EPISODE_BUTTON_ANIMATION_DURATION_MS = 100
    
    // Next episode button visual thresholds
    const val PLAY_ICON_VISIBILITY_THRESHOLD = 0.1f
    const val TEXT_COLOR_CHANGE_THRESHOLD = 0.5f
    
    // Next episode button display threshold (as percentage of episode duration)
    const val NEXT_EPISODE_BUTTON_THRESHOLD = 0.985f
}
