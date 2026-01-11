package com.flintr.tv.ui.components.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.FontSizes
import com.flintr.tv.ui.utils.formatTime

/**
 * Data class for debug information entry
 */
data class DebugInfo(
    val label: String,
    val value: String
)

/**
 * Debug overlay showing player information
 * @param playerState Current ExoPlayer state
 * @param currentPosition Current playback position in milliseconds
 * @param duration Total duration in milliseconds
 * @param bufferedPosition Buffered position in milliseconds
 * @param modifier Modifier for the overlay
 */
@Composable
fun DebugOverlay(
    playerState: Int,
    currentPosition: Long,
    duration: Long,
    bufferedPosition: Long,
    modifier: Modifier = Modifier
) {
    val debugInfo = listOf(
        DebugInfo("State", getPlayerStateString(playerState)),
        DebugInfo("Position", formatTime(currentPosition)),
        DebugInfo("Duration", formatTime(duration)),
        DebugInfo("Buffered", formatTime(bufferedPosition)),
        DebugInfo("Progress", if (duration > 0) "${((currentPosition.toFloat() / duration) * 100).toInt()}%" else "0%"),
        DebugInfo("Buffer %", if (duration > 0) "${((bufferedPosition.toFloat() / duration) * 100).toInt()}%" else "0%")
    )
    
    Box(
        modifier = modifier
            .fillMaxHeight()
            .widthIn(min = 350.dp, max = 450.dp)
            .fillMaxWidth(0.3f)
            .background(
                Color.Black.copy(alpha = 0.8f),
                RoundedCornerShape(Dimensions.radiusMD)
            )
            .padding(Dimensions.spacingSM)
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(debugInfo) { info ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingSM)
                ) {
                    Text(
                        text = info.label,
                        fontSize = FontSizes.fontSizeXXS,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.width(140.dp)
                    )
                    Text(
                        text = info.value,
                        fontSize = FontSizes.fontSizeXXS,
                        color = Color.White,
                        fontWeight = FontWeight.W500
                    )
                }
            }
        }
    }
}

/**
 * Get readable string for player state
 */
private fun getPlayerStateString(state: Int): String {
    return when (state) {
        Player.STATE_IDLE -> "IDLE"
        Player.STATE_BUFFERING -> "BUFFERING"
        Player.STATE_READY -> "READY"
        Player.STATE_ENDED -> "ENDED"
        else -> "UNKNOWN"
    }
}
