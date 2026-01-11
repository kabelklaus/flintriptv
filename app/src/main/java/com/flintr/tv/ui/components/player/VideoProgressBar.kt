package com.flintr.tv.ui.components.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalDragOrCancellation
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp
import com.flintr.tv.ui.theme.SharedColors
import kotlinx.coroutines.withTimeout

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
    val viewConfiguration = LocalViewConfiguration.current
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(progressHeight + handleRadius * 2)
            .onSizeChanged { size ->
                containerWidthPx = size.width.toFloat()
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown()
                        var isTap = true
                        
                        try {
                            withTimeout(viewConfiguration.longPressTimeoutMillis) {
                                var dragOccurred = false
                                val drag = awaitHorizontalDragOrCancellation(down.id)
                                if (drag != null) {
                                    dragOccurred = true
                                    isTap = false
                                    onDragStart()
                                    val newProgress = (drag.position.x / containerWidthPx).coerceIn(0f, 1f)
                                    onSeek(newProgress)
                                    
                                    while (true) {
                                        val dragEvent = awaitHorizontalDragOrCancellation(drag.id)
                                        if (dragEvent == null) {
                                            break
                                        } else {
                                            val newProgress = (dragEvent.position.x / containerWidthPx).coerceIn(0f, 1f)
                                            onSeek(newProgress)
                                        }
                                    }
                                }
                                if (!dragOccurred) {
                                    waitForUpOrCancellation()
                                }
                            }
                        } catch (_: PointerEventTimeoutCancellationException) {
                            isTap = false
                        }
                        
                        if (isTap && containerWidthPx > 0f) {
                            val newProgress = (down.position.x / containerWidthPx).coerceIn(0f, 1f)
                            onSeek(newProgress)
                        } else {
                            onDragEnd()
                        }
                    }
                }
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
