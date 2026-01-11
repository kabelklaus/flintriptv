package com.flintr.tv.ui.components.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.flintr.tv.ui.theme.SharedColors

/**
 * Custom video progress bar with drag interaction
 * @param progress Progress value between 0.0 and 1.0
 * @param onSeek Callback when user seeks to a new position (0.0 to 1.0)
 * @param onDragStart Callback when drag starts
 * @param onDragEnd Callback when drag ends
 * @param modifier Modifier for the progress bar
 */
@Composable
fun VideoProgressBar(
    progress: Float,
    onSeek: (Float) -> Unit,
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val progressHeight = 4.dp
    val handleRadius = 6.dp
    val showHandle = progress > 0f && progress < 1f
    var containerWidthPx by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(progressHeight + handleRadius * 2)
            .onSizeChanged { size ->
                containerWidthPx = size.width.toFloat()
            }
            .pointerInput(Unit) {
                detectTapGestures { tapOffset ->
                    val newProgress = (tapOffset.x / size.width).coerceIn(0f, 1f)
                    onSeek(newProgress)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { _ ->
                        onDragStart()
                    },
                    onDrag = { change, _ ->
                        val newProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                        onSeek(newProgress)
                    },
                    onDragEnd = {
                        onDragEnd()
                    }
                )
            }
    ) {
        // Background progress bar (white 30% opacity)
        LinearProgressIndicator(
            progress = 1f,
            modifier = Modifier
                .fillMaxWidth()
                .height(progressHeight)
                .align(Alignment.Center),
            color = Color.White.copy(alpha = 0.3f),
            trackColor = Color.White.copy(alpha = 0.3f)
        )
        
        // Progress indicator (red)
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier
                .fillMaxWidth()
                .height(progressHeight)
                .align(Alignment.Center),
            color = SharedColors.red,
            trackColor = Color.Transparent
        )
        
        // Handle circle (white, 6px radius)
        if (showHandle && containerWidthPx > 0f) {
            val handleOffsetXPx = progress * containerWidthPx
            val handleOffsetX = with(density) { handleOffsetXPx.toDp() - handleRadius }
            
            Box(
                modifier = Modifier
                    .size(handleRadius * 2)
                    .background(Color.White, CircleShape)
                    .offset(x = handleOffsetX)
                    .align(Alignment.CenterStart)
            )
        }
    }
}
