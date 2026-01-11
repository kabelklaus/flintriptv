package com.flintr.tv.ui.components.player

import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.flintr.tv.ui.theme.SharedColors
import com.flintr.tv.ui.utils.onClick

/**
 * Central play/pause button (80px icon)
 * @param isPlaying Whether video is currently playing
 * @param onClick Callback when button is clicked
 * @param modifier Modifier for the button
 */
@Composable
fun CentralPlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val iconSize = 80.dp
    val iconColor = if (isFocused) SharedColors.red else Color.White
    
    Box(
        modifier = modifier
            .size(iconSize)
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            }
            .focusable()
            .onClick(onClick = onClick)
            .then(
                if (isFocused) {
                    Modifier.border(2.dp, SharedColors.red, CircleShape)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            modifier = Modifier.size(iconSize),
            tint = iconColor
        )
    }
}
