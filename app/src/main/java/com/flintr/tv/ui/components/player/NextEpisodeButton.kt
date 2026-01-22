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
import androidx.compose.ui.unit.dp
import com.flintr.tv.ui.theme.FontSizes
import com.flintr.tv.ui.theme.SharedColors
import com.flintr.tv.ui.utils.onClick
import com.flintr.tv.ui.utils.onDpadCenter

/**
 * Button for navigating to the next episode with auto-play countdown
 * Simplified design showing only "Next Episode" text with filling progress bar
 * 
 * @param autoPlayProgress Progress of auto-play countdown (0.0 to 1.0)
 * @param onNextEpisode Callback when user clicks or auto-play triggers
 * @param modifier Modifier for the button
 */
@Composable
fun NextEpisodeButton(
    autoPlayProgress: Float,
    onNextEpisode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }
    
    // Auto-focus when button appears
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    
    // Animated progress for visual feedback
    val animatedProgress by animateFloatAsState(
        targetValue = autoPlayProgress,
        animationSpec = tween(durationMillis = 100),
        label = "autoPlayProgress"
    )
    
    val buttonShape = RoundedCornerShape(8.dp)
    
    Box(
        modifier = modifier
            .width(200.dp)
            .height(48.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            }
            .focusable()
            .onClick(onClick = onNextEpisode)
            .onDpadCenter(onPress = onNextEpisode)
            .clip(buttonShape)
            .background(
                color = Color.Black.copy(alpha = 0.8f),
                shape = buttonShape
            )
            .then(
                if (isFocused) {
                    Modifier.border(2.dp, SharedColors.red, buttonShape)
                } else {
                    Modifier
                }
            )
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
            if (animatedProgress > 0.1f) {
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
                color = if (animatedProgress > 0.5f) Color.Black else Color.White
            )
        }
    }
}
