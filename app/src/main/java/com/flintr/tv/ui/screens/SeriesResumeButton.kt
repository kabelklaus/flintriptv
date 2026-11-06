package com.flintr.tv.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flintr.tv.ui.utils.InteractionUtils.tvFocusable
import com.flintr.tv.ui.utils.onClick
import com.flintr.tv.ui.utils.onDpadDirection
import com.flintr.tv.ui.theme.appColors

/**
 * UI-Zustand für den Weiterschauen-Button in der Serien-Detailansicht.
 */
data class ResumeButtonUiState(
    val label: String,
    val progressPercent: Float
)

@Composable
internal fun SeriesResumeButton(
    state: ResumeButtonUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onNavigateUp: (() -> Boolean)? = null,
    onNavigateDown: (() -> Boolean)? = null
) {
    val colors = appColors()
    var isFocused by remember { mutableStateOf(false) }

    val backgroundColor by animateColorAsState(
        targetValue = if (isFocused) colors.accentColor else Color.White.copy(alpha = 0.15f),
        animationSpec = tween(durationMillis = 200),
        label = "resumeButtonBackground"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) colors.accentColor else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "resumeButtonBorder"
    )

    val shape = RoundedCornerShape(8.dp)
    val internalFocusRequester = remember { FocusRequester() }

    Box(
        modifier = modifier
            .background(color = backgroundColor, shape = shape)
            .then(
                if (borderColor != Color.Transparent) {
                    Modifier.border(width = 2.dp, color = borderColor, shape = shape)
                } else {
                    Modifier
                }
            )
            .focusRequester(focusRequester ?: internalFocusRequester)
            .tvFocusable { isFocused = it }
            .onDpadDirection(
                onUp = onNavigateUp,
                onDown = onNavigateDown
            )
            .onClick(onClick = onClick)
            .testTag("series-resume-button")
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        // IntrinsicSize.Max: Fortschrittsbalken nur so breit wie Icon + Label,
        // damit der Button nicht auf volle Bildschirmbreite aufgezogen wird
        Column(modifier = Modifier.width(IntrinsicSize.Max)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = state.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            if (state.progressPercent > 0f) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .testTag("series-resume-button-progress")
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.3f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(state.progressPercent.coerceIn(0f, 1f))
                            .height(3.dp)
                            .background(if (isFocused) Color.White else colors.accentColor)
                    )
                }
            }
        }
    }
}
