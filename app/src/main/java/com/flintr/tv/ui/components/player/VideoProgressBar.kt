package com.flintr.tv.ui.components.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalDragOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.FontSizes
import com.flintr.tv.ui.theme.SharedColors
import kotlinx.coroutines.withTimeout
import com.flintr.tv.utils.error.reportControlFlow

/**
 * Data class containing drag gesture callbacks
 */
data class DragCallbacks(
    val onDragStart: () -> Unit = {},
    val onDragProgress: (Float) -> Unit = {},
    val onDragEnd: (Float) -> Unit = {}
)

/**
 * Calculates progress value from x position
 */
private fun calculateProgress(xPosition: Float, containerWidthPx: Float): Float {
    return (xPosition / containerWidthPx).coerceIn(0f, 1f)
}

/**
 * Handles tap gesture (tap without drag)
 */
private fun handleTapSeek(
    downEvent: PointerInputChange,
    containerWidthPx: Float,
    onSeek: (Float) -> Unit
) {
    if (containerWidthPx > 0f) {
        val newProgress = calculateProgress(downEvent.position.x, containerWidthPx)
        onSeek(newProgress)
    }
}

/**
 * Processes continuous drag events and updates drag progress (visual only)
 */
private suspend fun AwaitPointerEventScope.processDragEvents(
    initialDrag: PointerInputChange,
    containerWidthPx: Float,
    onDragProgress: (Float) -> Unit
) {
    var currentDrag = initialDrag
    onDragProgress(calculateProgress(currentDrag.position.x, containerWidthPx))

    while (true) {
        val dragEvent = awaitHorizontalDragOrCancellation(currentDrag.id) ?: break
        currentDrag = dragEvent
        onDragProgress(calculateProgress(currentDrag.position.x, containerWidthPx))
    }
}

/**
 * Waits for drag gesture within timeout, returns null if timeout or cancelled
 */
private suspend fun AwaitPointerEventScope.awaitDragGestureOrNull(
    downEvent: PointerInputChange,
    timeoutMillis: Long
): PointerInputChange? {
    return try {
        withTimeout(timeoutMillis) {
            awaitHorizontalDragOrCancellation(downEvent.id)
        }
    } catch (e: PointerEventTimeoutCancellationException) {
        reportControlFlow("VideoProgressBar.gestureTimeout", e)
        null
    }
}

/**
 * Handles complete drag gesture flow
 * @return true if drag was handled, false if it should be treated as tap
 */
private suspend fun AwaitPointerEventScope.handleDragGesture(
    downEvent: PointerInputChange,
    containerWidthPx: Float,
    timeoutMillis: Long,
    onDragStart: () -> Unit,
    onDragProgress: (Float) -> Unit,
    onDragEnd: (Float) -> Unit
): Boolean {
    val initialDrag = awaitDragGestureOrNull(downEvent, timeoutMillis) ?: return false

    onDragStart()
    var lastProgress = calculateProgress(initialDrag.position.x, containerWidthPx)
    onDragProgress(lastProgress)

    var currentDrag = initialDrag
    while (true) {
        val dragEvent = awaitHorizontalDragOrCancellation(currentDrag.id) ?: break
        currentDrag = dragEvent
        lastProgress = calculateProgress(currentDrag.position.x, containerWidthPx)
        onDragProgress(lastProgress)
    }

    onDragEnd(lastProgress)
    return true
}


/**
 * Custom video progress bar with drag interaction and scrubber dot
 * @param progress Progress value between 0.0 and 1.0 (current position - accent blue)
 * @param onSeek Callback when user taps to seek to a new position (0.0 to 1.0)
 * @param modifier Modifier for the progress bar
 * @param bufferedProgress Buffered progress value between 0.0 and 1.0 (accent blue, 50% alpha)
 * @param isDragging Whether the user is currently dragging the scrubber
 * @param dragCallbacks Callbacks for drag gestures (start, progress, end)
 * @param isLive Live streams have no meaningful position/duration window, so the bar,
 *   its scrubber and all seek gestures are omitted.
 */
// Lint false positive: awaitPointerEventScope is entered exactly once and loops
// forever (while(true) inside the scope), so no pointer events can be dropped
// between scope invocations.
@Suppress("ReturnFromAwaitPointerEventScope")
@Composable
fun VideoProgressBar(
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    bufferedProgress: Float = 0f,
    isDragging: Boolean = false,
    dragCallbacks: DragCallbacks = DragCallbacks(),
    isLive: Boolean = false
) {
    // Live TV only exposes a buffered glimpse of the stream; a progress bar would suggest a
    // seekable 1-minute clip. The caller renders [LiveBadge] instead.
    if (isLive) return
    val progressHeight = 4.dp
    val scrubberSize by animateDpAsState(
        targetValue = if (isDragging) 18.dp else 12.dp,
        label = "scrubberSize"
    )
    var containerWidthPx by remember { mutableStateOf(0f) }
    val viewConfiguration = LocalViewConfiguration.current
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp) // Large touch target
            .onSizeChanged { size ->
                containerWidthPx = size.width.toFloat()
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown()

                        val wasDragged = handleDragGesture(
                            downEvent = down,
                            containerWidthPx = containerWidthPx,
                            timeoutMillis = viewConfiguration.longPressTimeoutMillis,
                            onDragStart = dragCallbacks.onDragStart,
                            onDragProgress = dragCallbacks.onDragProgress,
                            onDragEnd = dragCallbacks.onDragEnd
                        )

                        if (!wasDragged) {
                            handleTapSeek(down, containerWidthPx, onSeek)
                        }
                    }
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // Progress bar layers (centered vertically)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(progressHeight)
                .align(Alignment.Center)
        ) {
            // Background progress bar (white 30% opacity)
            LinearProgressIndicator(
                progress = 1f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(progressHeight),
                color = Color.White.copy(alpha = 0.3f),
                trackColor = Color.White.copy(alpha = 0.3f)
            )

            // Buffered progress indicator (accent blue, 50% alpha)
            LinearProgressIndicator(
                progress = bufferedProgress.coerceIn(0f, 1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(progressHeight),
                color = SharedColors.accent.copy(alpha = 0.5f),
                trackColor = Color.Transparent
            )

            // Current position indicator (accent blue)
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(progressHeight),
                color = SharedColors.accent,
                trackColor = Color.Transparent
            )
        }

        // Scrubber dot
        val scrubberOffsetDp = with(density) {
            (progress * containerWidthPx).toDp()
        }

        Box(
            modifier = Modifier
                .offset(x = scrubberOffsetDp - scrubberSize / 2)
                .align(Alignment.CenterStart)
                .size(scrubberSize)
                .then(
                    if (isDragging) {
                        Modifier.shadow(4.dp, CircleShape)
                    } else {
                        Modifier
                    }
                )
                .background(SharedColors.accent, CircleShape)
        )
    }
}

/**
 * Live indicator shown in place of the elapsed/total time. Uses the existing player label
 * typography; the dot carries the live state, the text stays readable over the scrim.
 */
@Composable
internal fun LiveBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .testTag("player-live-badge"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(SharedColors.red, CircleShape)
        )
        Spacer(modifier = Modifier.width(Dimensions.spacingXS))
        // Inline label: strings.xml is outside the T8 file scope. Keep it identical to the
        // badge wording ("LIVE") instead of inventing a localised variant here.
        Text(
            text = "LIVE",
            fontSize = FontSizes.fontSizeLG,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
    }
}
