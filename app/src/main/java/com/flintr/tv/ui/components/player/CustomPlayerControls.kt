package com.flintr.tv.ui.components.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.FontSizes
import com.flintr.tv.ui.theme.SharedColors
import com.flintr.tv.ui.utils.FocusedButton
import com.flintr.tv.ui.utils.formatTime
import com.flintr.tv.ui.utils.onClick
import com.flintr.tv.ui.utils.onDpadDirection

/**
 * Data class containing player state information
 */
data class PlayerState(
    val isPlaying: Boolean,
    val currentPosition: Long,
    val duration: Long,
    val progress: Float,
    val bufferedPosition: Long = 0L
)

/**
 * Data class containing player control callbacks
 */
data class PlayerCallbacks(
    val onPrevious: () -> Unit,
    val onPlayPause: () -> Unit,
    val onNext: () -> Unit,
    val onSeek: (Float) -> Unit,
    val onDragStart: () -> Unit,
    val onDragEnd: () -> Unit
)

/**
 * Data class containing utility callbacks
 */
data class PlayerUtilityCallbacks(
    val onDebug: () -> Unit,
    val onSettings: () -> Unit,
    val onFocusChange: (FocusedButton?) -> Unit
)

/**
 * Data class containing button configuration
 */
data class ButtonConfig(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val iconSize: androidx.compose.ui.unit.Dp,
    val contentDescription: String,
    val isFocused: Boolean,
    val focusRequester: FocusRequester,
    val onFocusChange: (Boolean) -> Unit,
    val onClick: () -> Unit,
    val onNavigateLeft: () -> Unit,
    val onNavigateRight: () -> Unit
)

/**
 * Custom player controls with progress bar, navigation buttons, time display, and utility buttons
 * @param state Player state information
 * @param callbacks Player control callbacks
 * @param utilityCallbacks Utility callbacks
 * @param focusedButton Currently focused button
 * @param modifier Modifier for the controls
 */
@Composable
fun CustomPlayerControls(
    state: PlayerState,
    callbacks: PlayerCallbacks,
    utilityCallbacks: PlayerUtilityCallbacks,
    focusedButton: FocusedButton?,
    modifier: Modifier = Modifier
) {
    val focusRequesters = remember {
        mapOf(
            FocusedButton.PREVIOUS to FocusRequester(),
            FocusedButton.PLAY_PAUSE to FocusRequester(),
            FocusedButton.NEXT to FocusRequester(),
            FocusedButton.DEBUG to FocusRequester(),
            FocusedButton.SETTINGS to FocusRequester()
        )
    }
    
    // Request focus when focusedButton changes
    LaunchedEffect(focusedButton) {
        focusRequesters[focusedButton]?.requestFocus()
    }
    
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // Gradient overlay from bottom (black 80% to 0%) - covers entire screen
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0f),
                            Color.Black.copy(alpha = 0.8f)
                        )
                    )
                )
        )
        
        // Controls container
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = Dimensions.spacingMD, vertical = Dimensions.spacingSM)
        ) {
            // Progress bar
            VideoProgressBar(
                progress = state.progress,
                bufferedProgress = if (state.duration > 0) {
                    (state.bufferedPosition.toFloat() / state.duration).coerceIn(0f, 1f)
                } else {
                    0f
                },
                onSeek = callbacks.onSeek,
                onDragStart = callbacks.onDragStart,
                onDragEnd = callbacks.onDragEnd,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(Dimensions.spacingMD))
            
            // Controls bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Navigation buttons
                NavigationButtons(
                    state = state,
                    callbacks = callbacks,
                    utilityCallbacks = utilityCallbacks,
                    focusedButton = focusedButton,
                    focusRequesters = focusRequesters
                )
                
                // Time display
                Text(
                    text = "${formatTime(state.currentPosition)} / ${formatTime(state.duration)}",
                    fontSize = FontSizes.fontSizeLG,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = Dimensions.spacingMD)
                )
                
                // Utility buttons
                UtilityButtons(
                    utilityCallbacks = utilityCallbacks,
                    focusedButton = focusedButton,
                    focusRequesters = focusRequesters
                )
            }
        }
    }
}

/**
 * Navigation buttons group (Previous, Play/Pause, Next)
 */
@Composable
private fun NavigationButtons(
    state: PlayerState,
    callbacks: PlayerCallbacks,
    utilityCallbacks: PlayerUtilityCallbacks,
    focusedButton: FocusedButton?,
    focusRequesters: Map<FocusedButton, FocusRequester>
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingMD),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Previous button (10s back)
        PlayerControlButton(
            config = ButtonConfig(
                icon = Icons.Default.Replay10,
                iconSize = 28.dp,
                contentDescription = "10 Sekunden zurück",
                isFocused = focusedButton == FocusedButton.PREVIOUS,
                focusRequester = focusRequesters[FocusedButton.PREVIOUS]!!,
                onFocusChange = { utilityCallbacks.onFocusChange(if (it) FocusedButton.PREVIOUS else null) },
                onClick = callbacks.onPrevious,
                onNavigateLeft = { utilityCallbacks.onFocusChange(null) },
                onNavigateRight = { utilityCallbacks.onFocusChange(FocusedButton.PLAY_PAUSE) }
            )
        )
        
        // Play/Pause button
        PlayerControlButton(
            config = ButtonConfig(
                icon = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                iconSize = 32.dp,
                contentDescription = if (state.isPlaying) "Pause" else "Play",
                isFocused = focusedButton == FocusedButton.PLAY_PAUSE,
                focusRequester = focusRequesters[FocusedButton.PLAY_PAUSE]!!,
                onFocusChange = { utilityCallbacks.onFocusChange(if (it) FocusedButton.PLAY_PAUSE else null) },
                onClick = callbacks.onPlayPause,
                onNavigateLeft = { utilityCallbacks.onFocusChange(FocusedButton.PREVIOUS) },
                onNavigateRight = { utilityCallbacks.onFocusChange(FocusedButton.NEXT) }
            )
        )
        
        // Next button (10s forward)
        PlayerControlButton(
            config = ButtonConfig(
                icon = Icons.Default.Forward10,
                iconSize = 28.dp,
                contentDescription = "10 Sekunden vor",
                isFocused = focusedButton == FocusedButton.NEXT,
                focusRequester = focusRequesters[FocusedButton.NEXT]!!,
                onFocusChange = { utilityCallbacks.onFocusChange(if (it) FocusedButton.NEXT else null) },
                onClick = callbacks.onNext,
                onNavigateLeft = { utilityCallbacks.onFocusChange(FocusedButton.PLAY_PAUSE) },
                onNavigateRight = { utilityCallbacks.onFocusChange(FocusedButton.DEBUG) }
            )
        )
    }
}

/**
 * Utility buttons group (Debug, Settings)
 */
@Composable
private fun UtilityButtons(
    utilityCallbacks: PlayerUtilityCallbacks,
    focusedButton: FocusedButton?,
    focusRequesters: Map<FocusedButton, FocusRequester>
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingMD),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Debug button
        PlayerControlButton(
            config = ButtonConfig(
                icon = Icons.Default.BugReport,
                iconSize = 24.dp,
                contentDescription = "Debug",
                isFocused = focusedButton == FocusedButton.DEBUG,
                focusRequester = focusRequesters[FocusedButton.DEBUG]!!,
                onFocusChange = { utilityCallbacks.onFocusChange(if (it) FocusedButton.DEBUG else null) },
                onClick = utilityCallbacks.onDebug,
                onNavigateLeft = { utilityCallbacks.onFocusChange(FocusedButton.NEXT) },
                onNavigateRight = { utilityCallbacks.onFocusChange(FocusedButton.SETTINGS) }
            )
        )
        
        // Settings button
        PlayerControlButton(
            config = ButtonConfig(
                icon = Icons.Default.Settings,
                iconSize = 24.dp,
                contentDescription = "Einstellungen",
                isFocused = focusedButton == FocusedButton.SETTINGS,
                focusRequester = focusRequesters[FocusedButton.SETTINGS]!!,
                onFocusChange = { utilityCallbacks.onFocusChange(if (it) FocusedButton.SETTINGS else null) },
                onClick = utilityCallbacks.onSettings,
                onNavigateLeft = { utilityCallbacks.onFocusChange(FocusedButton.DEBUG) },
                onNavigateRight = { utilityCallbacks.onFocusChange(null) }
            )
        )
    }
}

/**
 * Individual player control button with focus support
 */
@Composable
private fun PlayerControlButton(
    config: ButtonConfig,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .focusRequester(config.focusRequester)
            .onFocusChanged { focusState ->
                config.onFocusChange(focusState.isFocused)
            }
            .focusable()
            .onClick(onClick = config.onClick)
            .onDpadDirection(
                onLeft = {
                    config.onNavigateLeft()
                    true
                },
                onRight = {
                    config.onNavigateRight()
                    true
                }
            )
            .then(
                if (config.isFocused) {
                    Modifier.border(2.dp, SharedColors.red, RoundedCornerShape(Dimensions.radiusSM))
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = config.icon,
            contentDescription = config.contentDescription,
            modifier = Modifier.size(config.iconSize),
            tint = if (config.isFocused) SharedColors.red else Color.White
        )
    }
}
