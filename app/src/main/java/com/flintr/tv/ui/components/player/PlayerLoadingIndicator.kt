package com.flintr.tv.ui.components.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.flintr.tv.ui.theme.SharedColors

/**
 * Loading indicator shown during player buffering
 * @param isBuffering Whether the player is currently buffering
 */
@Composable
fun PlayerLoadingIndicator(
    isBuffering: Boolean,
    modifier: Modifier = Modifier
) {
    if (isBuffering) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(SharedColors.videoPlayerBg.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color.White)
        }
    }
}
