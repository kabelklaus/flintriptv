package com.flintr.tv.ui.components.player

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.appColors
import com.flintr.tv.ui.utils.onClick

/**
 * Window (in ms remaining before content ends) during which the soft rating overlay
 * may be shown.
 */
const val SOFT_RATING_OVERLAY_WINDOW_MS = 60_000L

/**
 * Decides whether the soft, non-blocking rating overlay should be shown: in the last
 * [SOFT_RATING_OVERLAY_WINDOW_MS] of a VOD, or of a series' last episode (more episodes
 * pending means auto-play should take over instead), unless the content was already
 * rated this session or isn't ratable (live).
 */
internal fun shouldShowSoftRatingOverlay(
    contentType: String,
    remainingMillis: Long,
    hasNextEpisode: Boolean,
    hasRatedInSession: Boolean
): Boolean {
    if (hasRatedInSession) return false
    if (remainingMillis <= 0L || remainingMillis > SOFT_RATING_OVERLAY_WINDOW_MS) return false
    return when (contentType) {
        "movie" -> true
        "series" -> !hasNextEpisode
        else -> false
    }
}

/**
 * Small, non-blocking thumbs up/down overlay shown near the end of VOD content.
 * Never grabs focus on appearance - the buttons are focusable but only reached via
 * explicit navigation, so the "screen is never without focus" invariant holds.
 */
@Composable
fun SoftRatingOverlay(
    onThumbsUp: () -> Unit,
    onThumbsDown: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = appColors()
    Column(
        modifier = modifier
            .background(
                color = colors.bgCard.copy(alpha = 0.85f),
                shape = RoundedCornerShape(Dimensions.radiusMD)
            )
            .padding(Dimensions.spacingSM),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Gefällt dir?", color = colors.textPrimary)
        Spacer(modifier = Modifier.height(Dimensions.spacingXXS))
        Row(horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingSM)) {
            Icon(
                imageVector = Icons.Default.ThumbUp,
                contentDescription = "Gefällt mir",
                tint = colors.iconColor,
                modifier = Modifier
                    .size(32.dp)
                    .focusable()
                    .onClick(onClick = onThumbsUp)
            )
            Icon(
                imageVector = Icons.Default.ThumbDown,
                contentDescription = "Gefällt mir nicht",
                tint = colors.iconColor,
                modifier = Modifier
                    .size(32.dp)
                    .focusable()
                    .onClick(onClick = onThumbsDown)
            )
        }
    }
}
