package com.flintr.tv.ui.utils

import java.util.Locale

/**
 * Utility functions for video player
 */

/**
 * Enum for focused buttons in player controls
 */
enum class FocusedButton {
    PREVIOUS,
    PLAY_PAUSE,
    NEXT,
    NEXT_EPISODE,
    DEBUG,
    SETTINGS
}

/**
 * Format time in milliseconds to MM:SS or HH:MM:SS format
 * @param millis Time in milliseconds
 * @return Formatted time string (MM:SS if < 1 hour, else HH:MM:SS)
 */
fun formatTime(millis: Long): String {
    val totalSeconds = (millis / 1000).toInt()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}
