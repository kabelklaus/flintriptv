package com.flintr.tv.ui.components.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.flintr.tv.ui.theme.DarkColors
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.appColors
import com.flintr.tv.ui.theme.tvFocusRing
import com.flintr.tv.ui.utils.onClick
import com.flintr.tv.ui.utils.onDpadDirection

/**
 * Shared cast row (Movie/Series detail) - identical layout, only the root testTag differs;
 * member testTags derive from it as `$testTag-member-$index`.
 */
@Composable
fun DetailCastSection(cast: List<String>, testTag: String) {
    val colors = appColors()
    Column(
        modifier = Modifier
            .padding(top = Dimensions.spacingLG, start = Dimensions.spacingXL, end = Dimensions.spacingXL)
            .testTag(testTag)
    ) {
        Text(
            text = "Besetzung",
            color = DarkColors.textPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = Dimensions.spacingSM)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingSM)) {
            itemsIndexed(items = cast) { index, name ->
                Column(
                    modifier = Modifier
                        .width(120.dp)
                        .testTag("$testTag-member-$index"),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(Dimensions.radiusRound))
                            .background(colors.bgCard),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = name.trim().take(1).uppercase(),
                            color = colors.accentBright,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = name,
                        color = DarkColors.textPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = Dimensions.spacingXS)
                    )
                }
            }
        }
    }
}

/**
 * Shared "Das konnte dir gefallen" row (Movie/Series detail) - identical layout; callers supply
 * the per-item poster/title/testTag/click since movie and series recommendations are different
 * model types.
 */
@Composable
fun <T> DetailRecommendationsSection(
    testTag: String,
    recommendations: List<T>,
    listState: LazyListState,
    focusRequesters: List<FocusRequester>,
    onUp: () -> Unit,
    key: (T) -> Any,
    posterUrl: (T) -> String?,
    title: (T) -> String,
    cardTestTag: (T) -> String,
    onItemClick: (T) -> Unit
) {
    Column(
        modifier = Modifier
            .padding(top = Dimensions.spacingLG, start = Dimensions.spacingXL, end = Dimensions.spacingXL)
            .testTag(testTag)
    ) {
        Text(
            text = "Das könnte dir gefallen",
            color = DarkColors.textPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = Dimensions.spacingSM)
        )
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingSM)
        ) {
            itemsIndexed(items = recommendations, key = { _, item -> key(item) }) { index, item ->
                DetailRecommendationCard(
                    posterUrl = posterUrl(item),
                    title = title(item),
                    testTag = cardTestTag(item),
                    focusRequester = focusRequesters.getOrNull(index),
                    onUp = onUp,
                    onClick = { onItemClick(item) }
                )
            }
        }
    }
}

@Composable
private fun DetailRecommendationCard(
    posterUrl: String?,
    title: String,
    testTag: String,
    focusRequester: FocusRequester?,
    onUp: () -> Unit,
    onClick: () -> Unit
) {
    val colors = appColors()
    var isFocused by remember { mutableStateOf(false) }
    val internalFocusRequester = remember { FocusRequester() }
    val actualFocusRequester = focusRequester ?: internalFocusRequester

    Column(
        modifier = Modifier
            .width(140.dp)
            .focusRequester(actualFocusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onDpadDirection(onUp = { onUp(); true })
            .onClick(onClick = onClick)
            .tvFocusRing(
                isFocused = isFocused,
                onArtwork = true,
                shape = RoundedCornerShape(Dimensions.radiusMD)
            )
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(Dimensions.radiusMD))
                .background(colors.bgCard)
        ) {
            if (posterUrl != null) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Text(
            text = title,
            color = DarkColors.textPrimary,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Dimensions.spacingXS)
        )
    }
}
