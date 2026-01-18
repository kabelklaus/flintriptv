package com.flintr.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.SharedColors

// Content Card Colors (from ContentCard.kt)
private val ContentCardBgColor = Color.Black

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
                    color = ContentCardBgColor.copy(alpha = Dimensions.contentCardRatingBgAlpha),
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
                contentDescription = "Geschaut",
                modifier = Modifier.size(Dimensions.iconSizeMD),
                tint = SharedColors.successGreen
            )
        }
    }
}
