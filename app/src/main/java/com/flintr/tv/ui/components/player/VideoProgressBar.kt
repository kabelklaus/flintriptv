package com.flintr.tv.ui.components.player

import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalDragOrCancellation
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp
import com.flintr.tv.ui.theme.SharedColors
import kotlinx.coroutines.withTimeout

/**
 * Calculates progress value from x position
 */
private fun calculateProgress(xPosition: Float, containerWidthPx: Float): Float {
    return (xPosition / containerWidthPx).coerceIn(0f, 1f)
}


/**
 * Custom video progress bar with drag interaction
 * @param progress Progress value between 0.0 and 1.0 (current position - dark red)
 * @param bufferedProgress Buffered progress value between 0.0 and 1.0 (light red)
 * @param onSeek Callback when user seeks to a new position (0.0 to 1.0)
 * @param onDragStart Callback when drag starts
 * @param onDragEnd Callback when drag ends
 * @param modifier Modifier for the progress bar
 */
@Composable
fun VideoProgressBar(
    progress: Float,
    bufferedProgress: Float = 0f,
    onSeek: (Float) -> Unit,
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val progressHeight = 4.dp
    var containerWidthPx by remember { mutableStateOf(0f) }
    val viewConfiguration = LocalViewConfiguration.current
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(progressHeight)
            .onSizeChanged { size ->
                containerWidthPx = size.width.toFloat()
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown()
                        val isDrag = try {
                            withTimeout(viewConfiguration.longPressTimeoutMillis) {
                                val drag = awaitHorizontalDragOrCancellation(down.id)
                                if (drag == null) {
                                    waitForUpOrCancellation()
                                    false
                                } else {
                                    // Process drag gesture
                                    onDragStart()
                                    var currentDrag: PointerInputChange = drag
                                    var newProgress = calculateProgress(currentDrag.position.x, containerWidthPx)
                                    onSeek(newProgress)
                                    
                                    // Process continuous drag events
                                    while (true) {
                                        val dragEvent = awaitHorizontalDragOrCancellation(currentDrag.id)
                                        if (dragEvent == null) break
                                        
                                        currentDrag = dragEvent
                                        newProgress = calculateProgress(currentDrag.position.x, containerWidthPx)
                                        onSeek(newProgress)
                                    }
                                    true
                                }
                            }
                        } catch (_: PointerEventTimeoutCancellationException) {
                            false
                        }
                        
                        if (isDrag) {
                            onDragEnd()
                        } else if (containerWidthPx > 0f) {
                            val newProgress = calculateProgress(down.position.x, containerWidthPx)
                            onSeek(newProgress)
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
        
        // Buffered progress indicator (light red)
        LinearProgressIndicator(
            progress = bufferedProgress.coerceIn(0f, 1f),
            modifier = Modifier
                .fillMaxWidth()
                .height(progressHeight)
                .align(Alignment.Center),
            color = SharedColors.red.copy(alpha = 0.5f),
            trackColor = Color.Transparent
        )
        
        // Current position indicator (dark red)
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier
                .fillMaxWidth()
                .height(progressHeight)
                .align(Alignment.Center),
            color = SharedColors.red,
            trackColor = Color.Transparent
        )
    }
}
