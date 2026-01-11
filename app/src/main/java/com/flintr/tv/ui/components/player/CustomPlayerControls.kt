package com.flintr.tv.ui.components.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
 * Custom player controls with progress bar, navigation buttons, time display, and utility buttons
 * @param isPlaying Whether video is currently playing
 * @param currentPosition Current playback position in milliseconds
 * @param duration Total duration in milliseconds
 * @param progress Progress value between 0.0 and 1.0
 * @param focusedButton Currently focused button
 * @param onPrevious Callback for previous button (10s back)
 * @param onPlayPause Callback for play/pause button
 * @param onNext Callback for next button (10s forward)
 * @param onSeek Callback when user seeks to a new position
 * @param onDragStart Callback when drag starts on progress bar
 * @param onDragEnd Callback when drag ends on progress bar
 * @param onDebug Callback for debug button
 * @param onSettings Callback for settings button
 * @param onFocusChange Callback when focus changes
 * @param modifier Modifier for the controls
 */
@Composable
fun CustomPlayerControls(
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    progress: Float,
    focusedButton: FocusedButton?,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Float) -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onDebug: () -> Unit,
    onSettings: () -> Unit,
    onFocusChange: (FocusedButton?) -> Unit,
    modifier: Modifier = Modifier
) {
    val previousFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }
    val nextFocusRequester = remember { FocusRequester() }
    val debugFocusRequester = remember { FocusRequester() }
    val settingsFocusRequester = remember { FocusRequester() }
    
    // Request focus when focusedButton changes
    LaunchedEffect(focusedButton) {
        when (focusedButton) {
            FocusedButton.PREVIOUS -> previousFocusRequester.requestFocus()
            FocusedButton.PLAY_PAUSE -> playPauseFocusRequester.requestFocus()
            FocusedButton.NEXT -> nextFocusRequester.requestFocus()
            FocusedButton.DEBUG -> debugFocusRequester.requestFocus()
            FocusedButton.SETTINGS -> settingsFocusRequester.requestFocus()
            null -> {}
        }
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
                progress = progress,
                onSeek = onSeek,
                onDragStart = onDragStart,
                onDragEnd = onDragEnd,
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingMD),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous button (10s back)
                    PlayerControlButton(
                        icon = Icons.Default.Replay10,
                        iconSize = 28.dp,
                        contentDescription = "10 Sekunden zurück",
                        isFocused = focusedButton == FocusedButton.PREVIOUS,
                        focusRequester = previousFocusRequester,
                        onFocusChange = { onFocusChange(if (it) FocusedButton.PREVIOUS else null) },
                        onClick = onPrevious,
                        onNavigateLeft = { onFocusChange(null) },
                        onNavigateRight = { onFocusChange(FocusedButton.PLAY_PAUSE) }
                    )
                    
                    // Play/Pause button
                    PlayerControlButton(
                        icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        iconSize = 32.dp,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        isFocused = focusedButton == FocusedButton.PLAY_PAUSE,
                        focusRequester = playPauseFocusRequester,
                        onFocusChange = { onFocusChange(if (it) FocusedButton.PLAY_PAUSE else null) },
                        onClick = onPlayPause,
                        onNavigateLeft = { onFocusChange(FocusedButton.PREVIOUS) },
                        onNavigateRight = { onFocusChange(FocusedButton.NEXT) }
                    )
                    
                    // Next button (10s forward)
                    PlayerControlButton(
                        icon = Icons.Default.Forward10,
                        iconSize = 28.dp,
                        contentDescription = "10 Sekunden vor",
                        isFocused = focusedButton == FocusedButton.NEXT,
                        focusRequester = nextFocusRequester,
                        onFocusChange = { onFocusChange(if (it) FocusedButton.NEXT else null) },
                        onClick = onNext,
                        onNavigateLeft = { onFocusChange(FocusedButton.PLAY_PAUSE) },
                        onNavigateRight = { onFocusChange(FocusedButton.DEBUG) }
                    )
                }
                
                // Time display
                Text(
                    text = "${formatTime(currentPosition)} / ${formatTime(duration)}",
                    fontSize = FontSizes.fontSizeLG,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = Dimensions.spacingMD)
                )
                
                // Utility buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingMD),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Debug button
                    PlayerControlButton(
                        icon = Icons.Default.BugReport,
                        iconSize = 24.dp,
                        contentDescription = "Debug",
                        isFocused = focusedButton == FocusedButton.DEBUG,
                        focusRequester = debugFocusRequester,
                        onFocusChange = { onFocusChange(if (it) FocusedButton.DEBUG else null) },
                        onClick = onDebug,
                        onNavigateLeft = { onFocusChange(FocusedButton.NEXT) },
                        onNavigateRight = { onFocusChange(FocusedButton.SETTINGS) }
                    )
                    
                    // Settings button
                    PlayerControlButton(
                        icon = Icons.Default.Settings,
                        iconSize = 24.dp,
                        contentDescription = "Einstellungen",
                        isFocused = focusedButton == FocusedButton.SETTINGS,
                        focusRequester = settingsFocusRequester,
                        onFocusChange = { onFocusChange(if (it) FocusedButton.SETTINGS else null) },
                        onClick = onSettings,
                        onNavigateLeft = { onFocusChange(FocusedButton.DEBUG) },
                        onNavigateRight = { onFocusChange(null) }
                    )
                }
            }
        }
    }
}

/**
 * Individual player control button with focus support
 */
@Composable
private fun PlayerControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconSize: androidx.compose.ui.unit.Dp,
    contentDescription: String,
    isFocused: Boolean,
    focusRequester: FocusRequester,
    onFocusChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    onNavigateLeft: () -> Unit,
    onNavigateRight: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                onFocusChange(focusState.isFocused)
            }
            .focusable()
            .onDpadDirection(
                onLeft = {
                    onNavigateLeft()
                    true
                },
                onRight = {
                    onNavigateRight()
                    true
                }
            )
            .then(
                if (isFocused) {
                    Modifier.border(2.dp, SharedColors.red, RoundedCornerShape(Dimensions.radiusSM))
                } else {
                    Modifier
                }
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = if (isFocused) SharedColors.red else Color.White
        )
    }
}
