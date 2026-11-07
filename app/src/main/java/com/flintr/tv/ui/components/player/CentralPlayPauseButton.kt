package com.flintr.tv.ui.components.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.flintr.tv.ui.utils.onClick
import kotlinx.coroutines.delay

private const val PAUSE_GLYPH_VISIBLE_MS = 600L
private const val PAUSE_GLYPH_FADE_MS = 200

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
    val iconSize = 80.dp

    // The central glyph is feedback for a state change, not a persistent status indicator:
    // show it briefly after each play/pause toggle and fade it out again. The first
    // composition is not a state change, so it must not flash the glyph.
    var glyphVisible by remember { mutableStateOf(false) }
    var previousIsPlaying by remember { mutableStateOf(isPlaying) }
    LaunchedEffect(isPlaying) {
        if (isPlaying != previousIsPlaying) {
            previousIsPlaying = isPlaying
            glyphVisible = true
            delay(PAUSE_GLYPH_VISIBLE_MS)
            glyphVisible = false
        }
    }
    val glyphAlpha by animateFloatAsState(
        targetValue = if (glyphVisible) 1f else 0f,
        animationSpec = tween(durationMillis = PAUSE_GLYPH_FADE_MS),
        label = "centralPlayPauseGlyphAlpha"
    )

    Box(
        modifier = modifier
            .size(iconSize)
            .onClick(onClick = onClick)
            // The button, not the transient glyph, owns the accessible label. The glyph's
            // own contentDescription stays null so a faded-out icon adds no a11y noise.
            .semantics { contentDescription = if (isPlaying) "Pause" else "Play" },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = null,
            modifier = Modifier
                .size(iconSize)
                .alpha(glyphAlpha),
            tint = Color.White
        )
    }
}
