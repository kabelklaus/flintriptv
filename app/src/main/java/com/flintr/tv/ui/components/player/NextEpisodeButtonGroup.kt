package com.flintr.tv.ui.components.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.flintr.tv.ui.common.PlayerConstants
import com.flintr.tv.ui.theme.FontSizes
import com.flintr.tv.ui.theme.SharedColors
import com.flintr.tv.ui.utils.onClick
import com.flintr.tv.ui.utils.onDpadCenter
import com.flintr.tv.ui.utils.onDpadDirection

/**
 * Button group containing "Watch Credits" and "Next Episode" buttons
 * 
 * @param autoPlayProgress Progress of auto-play countdown (0.0 to 1.0)
 * @param onWatchCredits Callback when user clicks "Watch Credits"
 * @param onNextEpisode Callback when user clicks "Next Episode"
 * @param modifier Modifier for the button group
 */
@Composable
fun NextEpisodeButtonGroup(
    autoPlayProgress: Float,
    onWatchCredits: () -> Unit,
    onNextEpisode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val watchCreditsFocusRequester = remember { FocusRequester() }
    val nextEpisodeFocusRequester = remember { FocusRequester() }
    var watchCreditsFocused by remember { mutableStateOf(false) }
    var nextEpisodeFocused by remember { mutableStateOf(false) }
    
    // Auto-focus on "Next Episode" button when group appears
    LaunchedEffect(Unit) {
        nextEpisodeFocusRequester.requestFocus()
    }
    
    // Animated progress for visual feedback
    val animatedProgress by animateFloatAsState(
        targetValue = autoPlayProgress,
        animationSpec = tween(durationMillis = PlayerConstants.NEXT_EPISODE_BUTTON_ANIMATION_DURATION_MS),
        label = "autoPlayProgress"
    )
    
    val buttonShape = RoundedCornerShape(8.dp)
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Watch Credits Button (left)
        PlayerActionButton(
            width = 160.dp,
            focusConfig = PlayerActionButtonFocusConfig(
                isFocused = watchCreditsFocused,
                focusRequester = watchCreditsFocusRequester,
                onFocusChange = { watchCreditsFocused = it }
            ),
            onClick = onWatchCredits,
            navigationConfig = PlayerActionButtonNavigationConfig(
                onNavigateRight = {
                    nextEpisodeFocusRequester.requestFocus()
                    true
                }
            ),
            buttonShape = buttonShape
        ) {
            // Text
            Text(
                text = "Watch Credits",
                fontSize = FontSizes.fontSizeMD,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .wrapContentSize(Alignment.Center)
            )
        }
        
        // Next Episode Button (right)
        PlayerActionButton(
            width = 200.dp,
            focusConfig = PlayerActionButtonFocusConfig(
                isFocused = nextEpisodeFocused,
                focusRequester = nextEpisodeFocusRequester,
                onFocusChange = { nextEpisodeFocused = it }
            ),
            onClick = onNextEpisode,
            navigationConfig = PlayerActionButtonNavigationConfig(
                onNavigateLeft = {
                    watchCreditsFocusRequester.requestFocus()
                    true
                }
            ),
            buttonShape = buttonShape
        ) {
            // Filling progress bar as background (from left to right)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedProgress)
                    .background(
                        color = Color.White,
                        shape = buttonShape
                    )
            )
            
            // Text overlay
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                // Play icon (triangle) - only visible on filled portion
                if (animatedProgress > PlayerConstants.PLAY_ICON_VISIBILITY_THRESHOLD) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier
                            .size(20.dp)
                            .padding(end = 8.dp)
                    )
                }
                
                // "Next Episode" text
                Text(
                    text = "Next Episode",
                    fontSize = FontSizes.fontSizeMD,
                    fontWeight = FontWeight.Medium,
                    color = if (animatedProgress > PlayerConstants.TEXT_COLOR_CHANGE_THRESHOLD) Color.Black else Color.White
                )
            }
        }
    }
}

/**
 * Configuration for player action button focus handling
 */
private data class PlayerActionButtonFocusConfig(
    val isFocused: Boolean,
    val focusRequester: FocusRequester,
    val onFocusChange: (Boolean) -> Unit
)

/**
 * Configuration for player action button navigation handling
 */
private data class PlayerActionButtonNavigationConfig(
    val onNavigateLeft: (() -> Boolean)? = null,
    val onNavigateRight: (() -> Boolean)? = null
)

/**
 * Reusable button composable for player action buttons
 * Encapsulates common button logic: focus handling, click handling, D-Pad navigation
 * 
 * @param width Button width
 * @param focusConfig Configuration for focus handling
 * @param onClick Callback when button is clicked
 * @param navigationConfig Configuration for D-Pad navigation
 * @param buttonShape Shape for the button
 * @param content Content to display inside the button
 */
@Composable
private fun PlayerActionButton(
    width: Dp,
    focusConfig: PlayerActionButtonFocusConfig,
    onClick: () -> Unit,
    navigationConfig: PlayerActionButtonNavigationConfig = PlayerActionButtonNavigationConfig(),
    buttonShape: RoundedCornerShape,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(48.dp)
            .focusRequester(focusConfig.focusRequester)
            .onFocusChanged { focusState ->
                focusConfig.onFocusChange(focusState.isFocused)
            }
            .focusable()
            .onClick(onClick = onClick)
            .onDpadCenter(onPress = onClick)
            .onDpadDirection(
                onLeft = navigationConfig.onNavigateLeft,
                onRight = navigationConfig.onNavigateRight
            )
            .clip(buttonShape)
            .background(
                color = Color.Black.copy(alpha = 0.8f),
                shape = buttonShape
            )
            .then(
                if (focusConfig.isFocused) {
                    Modifier.border(2.dp, SharedColors.accent, buttonShape)
                } else {
                    Modifier
                }
            ),
        content = content
    )
}
