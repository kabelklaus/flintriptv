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
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

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
    val onNextEpisode: (() -> Unit)? = null,
    val onSeek: (Float) -> Unit,
    val dragCallbacks: DragCallbacks = DragCallbacks(),
    val isDragging: Boolean = false
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
 * Data class containing button appearance configuration
 */
private data class ButtonAppearance(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val iconSize: androidx.compose.ui.unit.Dp,
    val contentDescription: String
)

/**
 * Data class containing button focus configuration
 */
private data class ButtonFocusConfig(
    val focusedButton: FocusedButton?,
    val targetButton: FocusedButton,
    val focusRequester: FocusRequester,
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

internal fun formatFinishTimeText(
    contentType: String,
    nowMillis: Long,
    currentPositionMillis: Long,
    durationMillis: Long,
    zoneId: ZoneId,
    locale: Locale
): String? {
    val isVodContent = contentType == "movie" || contentType == "series"
    if (!isVodContent || durationMillis <= 0L) return null

    val remainingMillis = (durationMillis - currentPositionMillis).coerceAtLeast(0L)
    val finishInstant = Instant.ofEpochMilli(nowMillis + remainingMillis)
    val finishTime = DateTimeFormatter.ofPattern("HH:mm", locale)
        .format(finishInstant.atZone(zoneId))

    return "Fertig um $finishTime"
}

/**
 * Custom player controls with progress bar, navigation buttons, time display, and utility buttons
 * @param state Player state information
 * @param callbacks Player control callbacks
 * @param utilityCallbacks Utility callbacks
 * @param contentType Current content type (live/movie/series)
 * @param focusedButton Currently focused button
 * @param modifier Modifier for the controls
 */
@Composable
fun CustomPlayerControls(
    state: PlayerState,
    callbacks: PlayerCallbacks,
    utilityCallbacks: PlayerUtilityCallbacks,
    contentType: String,
    focusedButton: FocusedButton?,
    modifier: Modifier = Modifier
) {
    val focusRequesters = remember {
        mapOf(
            FocusedButton.PREVIOUS to FocusRequester(),
            FocusedButton.PLAY_PAUSE to FocusRequester(),
            FocusedButton.NEXT to FocusRequester(),
            FocusedButton.NEXT_EPISODE to FocusRequester(),
            FocusedButton.DEBUG to FocusRequester(),
            FocusedButton.SETTINGS to FocusRequester()
        )
    }
    
    // Request focus when focusedButton changes
    LaunchedEffect(focusedButton) {
        focusRequesters[focusedButton]?.requestFocus()
    }

    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            val delayToNextMinute = 60_000L - (now % 60_000L)
            delay(delayToNextMinute)
            nowMillis = System.currentTimeMillis()
        }
    }

    val finishTimeText = remember(contentType, nowMillis, state.currentPosition, state.duration) {
        formatFinishTimeText(
            contentType = contentType,
            nowMillis = nowMillis,
            currentPositionMillis = state.currentPosition,
            durationMillis = state.duration,
            zoneId = ZoneId.systemDefault(),
            locale = Locale.getDefault()
        )
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
                isDragging = callbacks.isDragging,
                onSeek = callbacks.onSeek,
                dragCallbacks = callbacks.dragCallbacks,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(Dimensions.spacingMD))
            
            // Controls bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // All controls grouped on the left side
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingLG),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Navigation buttons
                    NavigationButtons(
                        state = state,
                        callbacks = callbacks,
                        utilityCallbacks = utilityCallbacks,
                        focusedButton = focusedButton,
                        focusRequesters = focusRequesters,
                        hasNextEpisode = callbacks.onNextEpisode != null
                    )

                    // Utility buttons
                    UtilityButtons(
                        utilityCallbacks = utilityCallbacks,
                        focusedButton = focusedButton,
                        focusRequesters = focusRequesters,
                        hasNextEpisode = callbacks.onNextEpisode != null
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Time block fixed at the right side
                Column(horizontalAlignment = Alignment.End) {
                    if (finishTimeText != null) {
                        Text(
                            text = finishTimeText,
                            fontSize = FontSizes.fontSizeMD,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }

                    Text(
                        text = "${formatTime(state.currentPosition)} / ${formatTime(state.duration)}",
                        fontSize = FontSizes.fontSizeLG,
                        color = Color.White
                    )
                }
            }
        }
    }
}

/**
 * Creates navigation handlers for a button based on its position and context
 */
private fun createNavigationHandler(
    button: FocusedButton,
    hasNextEpisode: Boolean,
    onFocusChange: (FocusedButton?) -> Unit
): Pair<() -> Unit, () -> Unit> {
    val onNavigateLeft: () -> Unit = when (button) {
        FocusedButton.PREVIOUS -> { -> onFocusChange(null) }
        FocusedButton.PLAY_PAUSE -> { -> onFocusChange(FocusedButton.PREVIOUS) }
        FocusedButton.NEXT -> { -> onFocusChange(FocusedButton.PLAY_PAUSE) }
        FocusedButton.NEXT_EPISODE -> { -> onFocusChange(FocusedButton.NEXT) }
        FocusedButton.DEBUG -> { -> 
            onFocusChange(if (hasNextEpisode) FocusedButton.NEXT_EPISODE else FocusedButton.NEXT)
        }
        FocusedButton.SETTINGS -> { -> onFocusChange(FocusedButton.DEBUG) }
    }
    
    val onNavigateRight: () -> Unit = when (button) {
        FocusedButton.PREVIOUS -> { -> onFocusChange(FocusedButton.PLAY_PAUSE) }
        FocusedButton.PLAY_PAUSE -> { -> onFocusChange(FocusedButton.NEXT) }
        FocusedButton.NEXT -> { -> 
            onFocusChange(if (hasNextEpisode) FocusedButton.NEXT_EPISODE else FocusedButton.DEBUG)
        }
        FocusedButton.NEXT_EPISODE -> { -> onFocusChange(FocusedButton.DEBUG) }
        FocusedButton.DEBUG -> { -> onFocusChange(FocusedButton.SETTINGS) }
        FocusedButton.SETTINGS -> { -> onFocusChange(null) }
    }
    
    return Pair(onNavigateLeft, onNavigateRight)
}

/**
 * Creates a ButtonConfig with standardized structure
 */
private fun createButtonConfig(
    appearance: ButtonAppearance,
    focusConfig: ButtonFocusConfig,
    onClick: () -> Unit,
    navigationHandler: Pair<() -> Unit, () -> Unit>
): ButtonConfig {
    return ButtonConfig(
        icon = appearance.icon,
        iconSize = appearance.iconSize,
        contentDescription = appearance.contentDescription,
        isFocused = focusConfig.focusedButton == focusConfig.targetButton,
        focusRequester = focusConfig.focusRequester,
        onFocusChange = { isFocused -> 
            focusConfig.onFocusChange(if (isFocused) focusConfig.targetButton else null) 
        },
        onClick = onClick,
        onNavigateLeft = navigationHandler.first,
        onNavigateRight = navigationHandler.second
    )
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
    focusRequesters: Map<FocusedButton, FocusRequester>,
    hasNextEpisode: Boolean
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingMD),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Previous button (10s back)
        PlayerControlButton(
            config = createButtonConfig(
                appearance = ButtonAppearance(
                    icon = Icons.Default.Replay10,
                    iconSize = 28.dp,
                    contentDescription = "10 Sekunden zurück"
                ),
                focusConfig = ButtonFocusConfig(
                    focusedButton = focusedButton,
                    targetButton = FocusedButton.PREVIOUS,
                    focusRequester = focusRequesters[FocusedButton.PREVIOUS]!!,
                    onFocusChange = utilityCallbacks.onFocusChange
                ),
                onClick = callbacks.onPrevious,
                navigationHandler = createNavigationHandler(
                    FocusedButton.PREVIOUS,
                    hasNextEpisode,
                    utilityCallbacks.onFocusChange
                )
            )
        )
        
        // Play/Pause button
        PlayerControlButton(
            config = createButtonConfig(
                appearance = ButtonAppearance(
                    icon = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    iconSize = 32.dp,
                    contentDescription = if (state.isPlaying) "Pause" else "Play"
                ),
                focusConfig = ButtonFocusConfig(
                    focusedButton = focusedButton,
                    targetButton = FocusedButton.PLAY_PAUSE,
                    focusRequester = focusRequesters[FocusedButton.PLAY_PAUSE]!!,
                    onFocusChange = utilityCallbacks.onFocusChange
                ),
                onClick = callbacks.onPlayPause,
                navigationHandler = createNavigationHandler(
                    FocusedButton.PLAY_PAUSE,
                    hasNextEpisode,
                    utilityCallbacks.onFocusChange
                )
            )
        )
        
        // Next button (10s forward)
        PlayerControlButton(
            config = createButtonConfig(
                appearance = ButtonAppearance(
                    icon = Icons.Default.Forward10,
                    iconSize = 28.dp,
                    contentDescription = "10 Sekunden vor"
                ),
                focusConfig = ButtonFocusConfig(
                    focusedButton = focusedButton,
                    targetButton = FocusedButton.NEXT,
                    focusRequester = focusRequesters[FocusedButton.NEXT]!!,
                    onFocusChange = utilityCallbacks.onFocusChange
                ),
                onClick = callbacks.onNext,
                navigationHandler = createNavigationHandler(
                    FocusedButton.NEXT,
                    hasNextEpisode,
                    utilityCallbacks.onFocusChange
                )
            )
        )
        
        // Next Episode button (only shown if next episode is available)
        if (hasNextEpisode && callbacks.onNextEpisode != null) {
            PlayerControlButton(
                config = createButtonConfig(
                    appearance = ButtonAppearance(
                        icon = Icons.Default.SkipNext,
                        iconSize = 28.dp,
                        contentDescription = "Nächste Episode"
                    ),
                    focusConfig = ButtonFocusConfig(
                        focusedButton = focusedButton,
                        targetButton = FocusedButton.NEXT_EPISODE,
                        focusRequester = focusRequesters[FocusedButton.NEXT_EPISODE]!!,
                        onFocusChange = utilityCallbacks.onFocusChange
                    ),
                    onClick = { callbacks.onNextEpisode.invoke() },
                    navigationHandler = createNavigationHandler(
                        FocusedButton.NEXT_EPISODE,
                        hasNextEpisode,
                        utilityCallbacks.onFocusChange
                    )
                )
            )
        }
    }
}

/**
 * Utility buttons group (Debug, Settings)
 */
@Composable
private fun UtilityButtons(
    utilityCallbacks: PlayerUtilityCallbacks,
    focusedButton: FocusedButton?,
    focusRequesters: Map<FocusedButton, FocusRequester>,
    hasNextEpisode: Boolean
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingMD),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Debug button
        PlayerControlButton(
            config = createButtonConfig(
                appearance = ButtonAppearance(
                    icon = Icons.Default.BugReport,
                    iconSize = 24.dp,
                    contentDescription = "Debug"
                ),
                focusConfig = ButtonFocusConfig(
                    focusedButton = focusedButton,
                    targetButton = FocusedButton.DEBUG,
                    focusRequester = focusRequesters[FocusedButton.DEBUG]!!,
                    onFocusChange = utilityCallbacks.onFocusChange
                ),
                onClick = utilityCallbacks.onDebug,
                navigationHandler = createNavigationHandler(
                    FocusedButton.DEBUG,
                    hasNextEpisode,
                    utilityCallbacks.onFocusChange
                )
            )
        )
        
        // Settings button
        PlayerControlButton(
            config = createButtonConfig(
                appearance = ButtonAppearance(
                    icon = Icons.Default.Settings,
                    iconSize = 24.dp,
                    contentDescription = "Einstellungen"
                ),
                focusConfig = ButtonFocusConfig(
                    focusedButton = focusedButton,
                    targetButton = FocusedButton.SETTINGS,
                    focusRequester = focusRequesters[FocusedButton.SETTINGS]!!,
                    onFocusChange = utilityCallbacks.onFocusChange
                ),
                onClick = utilityCallbacks.onSettings,
                navigationHandler = createNavigationHandler(
                    FocusedButton.SETTINGS,
                    hasNextEpisode,
                    utilityCallbacks.onFocusChange
                )
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
