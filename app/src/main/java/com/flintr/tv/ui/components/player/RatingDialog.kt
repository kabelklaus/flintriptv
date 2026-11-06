package com.flintr.tv.ui.components.player

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import com.flintr.tv.data.model.RatingEntry
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.appColors
import com.flintr.tv.ui.theme.tvFocusRing
import com.flintr.tv.ui.utils.onBackButton
import com.flintr.tv.ui.utils.onClick
import com.flintr.tv.ui.utils.onDpadDirection
import com.flintr.tv.ui.utils.safeRequestFocus
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Thumbs up/down rating dialog shown after a VOD or the last episode of a series ends.
 * Content-agnostic: [onRated] receives a [RatingEntry] carrying only [title] and the
 * chosen rating (1 = up, 0 = down); the caller fills in contentType/contentId before
 * persisting via UserRepository.saveRating().
 *
 * @param title Title of the rated content, shown in the question text
 * @param onRated Callback with the rating entry when a thumb button is clicked
 * @param onDismiss Callback when the dialog is dismissed without rating (back/outside tap)
 */
@Composable
fun RatingDialog(
    title: String,
    onRated: (RatingEntry) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = appColors()
    val coroutineScope = rememberCoroutineScope()
    val thumbsUpFocusRequester = remember { FocusRequester() }
    val thumbsDownFocusRequester = remember { FocusRequester() }
    var thumbsUpFocused by remember { mutableStateOf(false) }
    var thumbsDownFocused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        thumbsUpFocusRequester.safeRequestFocus(targetName = "RATING_DIALOG_THUMBS_UP")
    }

    fun rate(rating: Int) {
        onRated(
            RatingEntry(
                contentType = "",
                contentId = 0,
                title = title,
                rating = rating,
                ratedAt = Instant.now().toString()
            )
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .testTag("rating-dialog")
                .onBackButton(onBack = onDismiss),
            shape = RoundedCornerShape(Dimensions.radiusMD),
            colors = CardDefaults.cardColors(containerColor = colors.bgCard)
        ) {
            Column(
                modifier = Modifier.padding(Dimensions.spacingXL),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Wie fandst du „$title\"?",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(Dimensions.spacingLG))
                Row(horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingLG)) {
                    Icon(
                        imageVector = Icons.Default.ThumbUp,
                        contentDescription = null,
                        tint = colors.iconColor,
                        modifier = Modifier
                            .size(Dimensions.iconSizeXL)
                            .padding(Dimensions.spacingXS)
                            .focusRequester(thumbsUpFocusRequester)
                            .onFocusChanged { thumbsUpFocused = it.isFocused }
                            .tvFocusRing(
                                isFocused = thumbsUpFocused,
                                shape = RoundedCornerShape(Dimensions.radiusSM)
                            )
                            .focusable()
                            .onClick(onClick = { rate(1) })
                            .onDpadDirection(
                                onRight = {
                                    coroutineScope.launch {
                                        thumbsDownFocusRequester.safeRequestFocus(targetName = "RATING_DIALOG_THUMBS_DOWN", initialDelayMs = 0)
                                    }
                                    true
                                }
                            )
                            .semantics { contentDescription = "Daumen hoch" }
                    )
                    Icon(
                        imageVector = Icons.Default.ThumbDown,
                        contentDescription = null,
                        tint = colors.iconColor,
                        modifier = Modifier
                            .size(Dimensions.iconSizeXL)
                            .padding(Dimensions.spacingXS)
                            .focusRequester(thumbsDownFocusRequester)
                            .onFocusChanged { thumbsDownFocused = it.isFocused }
                            .tvFocusRing(
                                isFocused = thumbsDownFocused,
                                shape = RoundedCornerShape(Dimensions.radiusSM)
                            )
                            .focusable()
                            .onClick(onClick = { rate(0) })
                            .onDpadDirection(
                                onLeft = {
                                    coroutineScope.launch {
                                        thumbsUpFocusRequester.safeRequestFocus(targetName = "RATING_DIALOG_THUMBS_UP", initialDelayMs = 0)
                                    }
                                    true
                                }
                            )
                            .semantics { contentDescription = "Daumen runter" }
                    )
                }
            }
        }
    }
}
