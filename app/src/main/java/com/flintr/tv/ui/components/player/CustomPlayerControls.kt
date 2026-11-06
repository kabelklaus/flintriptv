package com.flintr.tv.ui.components.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flintr.tv.BuildConfig
import com.flintr.tv.R
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.FontSizes
import com.flintr.tv.ui.theme.SharedColors
import com.flintr.tv.ui.utils.FocusedButton
import com.flintr.tv.ui.utils.formatTime
import com.flintr.tv.ui.utils.onClick
import com.flintr.tv.ui.utils.onDpadDirection
import com.flintr.tv.ui.utils.safeRequestFocus
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FINISH_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

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
    val onThumbsUp: () -> Unit = {},
    val onThumbsDown: () -> Unit = {},
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
 * Data class bundling the rating-buttons' display state (grouped to keep
 * [CustomPlayerControls]'s parameter count within the S107 limit)
 */
data class RatingUiState(
    val ratedThumb: Int? = null,
    val showRatingButtons: Boolean = true
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
    val isHighlighted: Boolean = false,
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
    val finishTime = FINISH_TIME_FORMATTER.withLocale(locale)
        .format(finishInstant.atZone(zoneId))

    return finishTime
}

/**
 * Custom player controls with progress bar, navigation buttons, time display, and utility buttons
 * @param state Player state information
 * @param callbacks Player control callbacks
 * @param utilityCallbacks Utility callbacks
 * @param contentType Current content type (live/movie/series)
 * @param focusedButton Currently focused button
 * @param modifier Modifier for the controls
 * @param ratingUiState The rating-buttons' display state: [RatingUiState.ratedThumb] is the
 *   persisted rating for the current content (1 = up, 0 = down, null = none), highlighted
 *   independent of focus for as long as the content stays rated; [RatingUiState.showRatingButtons]
 *   controls whether the thumbs up/down buttons are rendered and focusable at all
 */
@Composable
fun CustomPlayerControls(
    state: PlayerState,
    callbacks: PlayerCallbacks,
    utilityCallbacks: PlayerUtilityCallbacks,
    contentType: String,
    focusedButton: FocusedButton?,
    modifier: Modifier = Modifier,
    ratingUiState: RatingUiState = RatingUiState()
) {
    val isLive = contentType == "live"
    val focusRequesters = remember {
        buildMap {
            put(FocusedButton.PREVIOUS, FocusRequester())
            put(FocusedButton.PLAY_PAUSE, FocusRequester())
            put(FocusedButton.NEXT, FocusRequester())
            put(FocusedButton.NEXT_EPISODE, FocusRequester())
            put(FocusedButton.THUMBS_UP, FocusRequester())
            put(FocusedButton.THUMBS_DOWN, FocusRequester())
            // Release builds have no Debug button, so they must not know its requester either.
            if (BuildConfig.DEBUG) put(FocusedButton.DEBUG, FocusRequester())
            put(FocusedButton.SETTINGS, FocusRequester())
        }
    }

    // Live controls omit the ±10s seek buttons, but PlayerScreen still seeds PREVIOUS when the
    // overlay opens. Redirect that seed to the first visible control instead of losing focus.
    val focusTarget = resolveEffectiveFocusedButton(focusedButton, isLive)

    // Request focus when the effective target changes
    LaunchedEffect(focusTarget) {
        focusRequesters[focusTarget]?.safeRequestFocus(
            targetName = "PlayerControl_${focusTarget?.name ?: "none"}",
            initialDelayMs = 0
        )
    }

    var minuteTickMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            val delayToNextMinute = 60_000L - (now % 60_000L)
            delay(delayToNextMinute)
            minuteTickMillis = System.currentTimeMillis()
        }
    }

    val sampledNowMillis = if (state.isPlaying) System.currentTimeMillis() else minuteTickMillis
    val finishTime = remember(contentType, sampledNowMillis, state.currentPosition, state.duration) {
        formatFinishTimeText(
            contentType = contentType,
            nowMillis = sampledNowMillis,
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
            // Progress bar - omitted for live TV: the buffered window is not a real duration.
            if (!isLive) {
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
            }
            
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
                        hasNextEpisode = callbacks.onNextEpisode != null,
                        showRatingButtons = ratingUiState.showRatingButtons,
                        isLive = isLive
                    )

                    // Rating buttons (thumbs up/down)
                    if (ratingUiState.showRatingButtons) {
                        RatingButtons(
                            callbacks = callbacks,
                            utilityCallbacks = utilityCallbacks,
                            focusedButton = focusedButton,
                            focusRequesters = focusRequesters,
                            hasNextEpisode = callbacks.onNextEpisode != null,
                            ratedThumb = ratingUiState.ratedThumb,
                            isLive = isLive
                        )
                    }

                    // Utility buttons
                    UtilityButtons(
                        utilityCallbacks = utilityCallbacks,
                        focusedButton = focusedButton,
                        focusRequesters = focusRequesters,
                        hasNextEpisode = callbacks.onNextEpisode != null,
                        showRatingButtons = ratingUiState.showRatingButtons,
                        isLive = isLive
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Time block fixed at the right side
                Column(horizontalAlignment = Alignment.End) {
                    if (isLive) {
                        LiveBadge(isPlaying = state.isPlaying)
                    } else {
                        if (finishTime != null) {
                            Text(
                                text = stringResource(R.string.finish_time_at, finishTime),
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
}

/**
 * The visible D-Pad order of the control bar. Live TV drops the ±10s seek buttons and the
 * progress bar; release builds drop the Debug button. Navigation and rendering both derive
 * from this list, so the focus loop can never target an invisible control.
 */
internal fun playerControlFocusOrder(
    hasNextEpisode: Boolean,
    showRatingButtons: Boolean,
    isLive: Boolean = false,
    debugEnabled: Boolean = BuildConfig.DEBUG
): List<FocusedButton> = buildList {
    if (!isLive) {
        add(FocusedButton.PREVIOUS)
        add(FocusedButton.PLAY_PAUSE)
        add(FocusedButton.NEXT)
    } else {
        add(FocusedButton.PLAY_PAUSE)
    }
    if (hasNextEpisode) add(FocusedButton.NEXT_EPISODE)
    if (showRatingButtons) {
        add(FocusedButton.THUMBS_UP)
        add(FocusedButton.THUMBS_DOWN)
    }
    if (debugEnabled) add(FocusedButton.DEBUG)
    add(FocusedButton.SETTINGS)
}

/**
 * Live TV has no seek buttons, but the player seeds [FocusedButton.PREVIOUS] when the overlay
 * opens. Map that seed onto the first visible control.
 */
internal fun resolveEffectiveFocusedButton(
    focusedButton: FocusedButton?,
    isLive: Boolean
): FocusedButton? = when {
    isLive && focusedButton in setOf(FocusedButton.PREVIOUS, FocusedButton.NEXT) ->
        FocusedButton.PLAY_PAUSE
    else -> focusedButton
}

/**
 * Live indicator shown in place of the elapsed/total time. Uses the existing player label
 * typography; the dot carries the live state, the text stays readable over the scrim.
 *
 * [isPlaying] is the live-stream play state: while a live stream is paused the dot turns grey
 * instead of red, so the badge does not claim an active live feed. The label stays "LIVE" to
 * avoid touching the out-of-scope string resources.
 */
@Composable
internal fun LiveBadge(
    isPlaying: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .testTag("player-live-badge"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = if (isPlaying) SharedColors.red else Color.Gray,
                    shape = CircleShape
                )
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

private fun navigateToNeighbour(
    button: FocusedButton,
    offset: Int,
    hasNextEpisode: Boolean,
    onFocusChange: (FocusedButton?) -> Unit,
    showRatingButtons: Boolean,
    isLive: Boolean
): () -> Unit = {
    val order = playerControlFocusOrder(hasNextEpisode, showRatingButtons, isLive)
    val index = order.indexOf(button)
    onFocusChange(if (index >= 0) order.getOrNull(index + offset) else null)
}

/**
 * Left-navigation target for a button based on its position and context
 */
private fun navigateLeftHandler(
    button: FocusedButton,
    hasNextEpisode: Boolean,
    onFocusChange: (FocusedButton?) -> Unit,
    showRatingButtons: Boolean = true,
    isLive: Boolean = false
): () -> Unit = navigateToNeighbour(
    button, -1, hasNextEpisode, onFocusChange, showRatingButtons, isLive
)

/**
 * Right-navigation target for a button based on its position and context
 */
private fun navigateRightHandler(
    button: FocusedButton,
    hasNextEpisode: Boolean,
    onFocusChange: (FocusedButton?) -> Unit,
    showRatingButtons: Boolean = true,
    isLive: Boolean = false
): () -> Unit = navigateToNeighbour(
    button, 1, hasNextEpisode, onFocusChange, showRatingButtons, isLive
)

/**
 * Creates the (left, right) navigation handler pair for a button based on its position and context
 */
private fun createNavigationHandler(
    button: FocusedButton,
    hasNextEpisode: Boolean,
    onFocusChange: (FocusedButton?) -> Unit,
    showRatingButtons: Boolean = true,
    isLive: Boolean = false
): Pair<() -> Unit, () -> Unit> = Pair(
    navigateLeftHandler(button, hasNextEpisode, onFocusChange, showRatingButtons, isLive),
    navigateRightHandler(button, hasNextEpisode, onFocusChange, showRatingButtons, isLive)
)

/**
 * Creates a ButtonConfig with standardized structure
 */
private fun createButtonConfig(
    appearance: ButtonAppearance,
    focusConfig: ButtonFocusConfig,
    onClick: () -> Unit,
    navigationHandler: Pair<() -> Unit, () -> Unit>,
    isHighlighted: Boolean = false
): ButtonConfig {
    return ButtonConfig(
        icon = appearance.icon,
        iconSize = appearance.iconSize,
        contentDescription = appearance.contentDescription,
        isFocused = focusConfig.focusedButton == focusConfig.targetButton,
        isHighlighted = isHighlighted,
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
    hasNextEpisode: Boolean,
    showRatingButtons: Boolean = true,
    isLive: Boolean = false
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingMD),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Previous button (10s back) - live TV is not seekable
        if (!isLive) {
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
                        utilityCallbacks.onFocusChange,
                        showRatingButtons,
                        isLive
                    )
                )
            )
        }
        
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
                    utilityCallbacks.onFocusChange,
                    showRatingButtons,
                    isLive
                )
            )
        )
        
        // Next button (10s forward) - live TV is not seekable
        if (!isLive) {
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
                        utilityCallbacks.onFocusChange,
                        showRatingButtons,
                        isLive
                    )
                )
            )
        }

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
                        utilityCallbacks.onFocusChange,
                        showRatingButtons,
                        isLive
                    )
                )
            )
        }
    }
}

/**
 * Rating buttons group (thumbs up/down) - direct rating without the RatingDialog.
 * [ratedThumb] persistently highlights the currently rated thumb (1 = up, 0 = down),
 * for as long as this content stays rated.
 */
@Composable
private fun RatingButtons(
    callbacks: PlayerCallbacks,
    utilityCallbacks: PlayerUtilityCallbacks,
    focusedButton: FocusedButton?,
    focusRequesters: Map<FocusedButton, FocusRequester>,
    hasNextEpisode: Boolean,
    ratedThumb: Int?,
    isLive: Boolean = false
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingMD),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbs up
        PlayerControlButton(
            config = createButtonConfig(
                appearance = ButtonAppearance(
                    icon = Icons.Default.ThumbUp,
                    iconSize = 24.dp,
                    contentDescription = "Gefällt mir"
                ),
                focusConfig = ButtonFocusConfig(
                    focusedButton = focusedButton,
                    targetButton = FocusedButton.THUMBS_UP,
                    focusRequester = focusRequesters[FocusedButton.THUMBS_UP]!!,
                    onFocusChange = utilityCallbacks.onFocusChange
                ),
                onClick = callbacks.onThumbsUp,
                navigationHandler = createNavigationHandler(
                    FocusedButton.THUMBS_UP,
                    hasNextEpisode,
                    utilityCallbacks.onFocusChange,
                    showRatingButtons = true,
                    isLive = isLive
                ),
                isHighlighted = ratedThumb == 1
            )
        )

        // Thumbs down
        PlayerControlButton(
            config = createButtonConfig(
                appearance = ButtonAppearance(
                    icon = Icons.Default.ThumbDown,
                    iconSize = 24.dp,
                    contentDescription = "Gefällt mir nicht"
                ),
                focusConfig = ButtonFocusConfig(
                    focusedButton = focusedButton,
                    targetButton = FocusedButton.THUMBS_DOWN,
                    focusRequester = focusRequesters[FocusedButton.THUMBS_DOWN]!!,
                    onFocusChange = utilityCallbacks.onFocusChange
                ),
                onClick = callbacks.onThumbsDown,
                navigationHandler = createNavigationHandler(
                    FocusedButton.THUMBS_DOWN,
                    hasNextEpisode,
                    utilityCallbacks.onFocusChange,
                    showRatingButtons = true,
                    isLive = isLive
                ),
                isHighlighted = ratedThumb == 0
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
    focusRequesters: Map<FocusedButton, FocusRequester>,
    hasNextEpisode: Boolean,
    showRatingButtons: Boolean = true,
    isLive: Boolean = false
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingMD),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Debug button - debug builds only; release builds must not render or focus it.
        if (BuildConfig.DEBUG) {
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
                        utilityCallbacks.onFocusChange,
                        showRatingButtons,
                        isLive
                    )
                )
            )
        }
        
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
                    utilityCallbacks.onFocusChange,
                    showRatingButtons,
                    isLive
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
                    Modifier.border(2.dp, SharedColors.accent, RoundedCornerShape(Dimensions.radiusSM))
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
            tint = if (config.isFocused || config.isHighlighted) SharedColors.accent else Color.White
        )
    }
}
