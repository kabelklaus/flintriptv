package com.flintr.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.flintr.tv.R
import com.flintr.tv.data.model.RatingEntry
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.SharedColors

@Composable
fun WatchedBadge(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .background(
                    color = SharedColors.contentCardBgColor.copy(alpha = Dimensions.contentCardRatingBgAlpha),
                    shape = RoundedCornerShape(Dimensions.radiusMD)
                )
                .padding(
                    horizontal = Dimensions.spacingMD,
                    vertical = Dimensions.spacingSM
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = stringResource(R.string.watched_badge_description),
                modifier = Modifier.size(Dimensions.iconSizeMD),
                tint = SharedColors.successGreen
            )
        }
    }
}

/**
 * Badge shown when a piece of content has been thumbs-up rated ([RatingEntry.rating] == 1).
 * Self-gating: renders nothing for a thumbs-down rating or when there's no rating yet, so
 * callers can pass the result of `UserRepository.getRating()` straight through.
 */
@Composable
fun RatingUpBadge(
    rating: RatingEntry?,
    modifier: Modifier = Modifier
) {
    if (rating?.rating != 1) return

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .background(
                    color = SharedColors.contentCardBgColor.copy(alpha = Dimensions.contentCardRatingBgAlpha),
                    shape = RoundedCornerShape(Dimensions.radiusMD)
                )
                .padding(
                    horizontal = Dimensions.spacingMD,
                    vertical = Dimensions.spacingSM
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ThumbUp,
                contentDescription = stringResource(R.string.rating_up_badge_description),
                modifier = Modifier.size(Dimensions.iconSizeMD),
                tint = SharedColors.successGreen
            )
        }
    }
}
