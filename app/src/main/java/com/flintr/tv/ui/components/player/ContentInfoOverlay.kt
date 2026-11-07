package com.flintr.tv.ui.components.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flintr.tv.ui.screens.SeriesContext
import com.flintr.tv.ui.theme.FontSizes

/**
 * Maximum length for content titles before truncation
 */
private const val MAX_TITLE_LENGTH = 50

/**
 * Formats content info text based on content type
 */
private fun formatContentInfo(
    contentType: String,
    title: String?,
    seriesData: SeriesContext?
): String? {
    return when (contentType) {
        "series" -> {
            val season = seriesData?.currentSeason?.seasonNumber
            val episodeNum = seriesData?.currentEpisode?.episodeNum ?: seriesData?.currentEpisode?.id
            val episodeName = seriesData?.currentEpisode?.title?.takeIf { it.isNotBlank() }
            
            when {
                season != null && episodeNum != null && episodeName != null -> {
                    "S${season} E${episodeNum} $episodeName"
                }
                season != null && episodeNum != null -> {
                    // Episode name is empty, show only season and episode number
                    "S${season} E${episodeNum}"
                }
                else -> null
            }
        }
        "movie", "live" -> {
            title?.takeIf { it.isNotEmpty() }
        }
        else -> null
    }
}

/**
 * Truncates text if it exceeds maximum length
 */
private fun truncateText(text: String, maxLength: Int = MAX_TITLE_LENGTH): String {
    if (text.length <= maxLength) {
        return text
    }

    val truncateLength = maxLength - 3
    return if (truncateLength > 0) {
        text.take(truncateLength) + "..."
    } else {
        text.take(maxLength)
    }
}

/**
 * Content info overlay displayed at the top center of the player overlay
 * Shows series episode info, movie title, or live TV channel name
 * 
 * @param contentType Content type ("series", "movie", "live")
 * @param title Title for movies/live TV, or episode title for series
 * @param seriesData Series context data for series episodes
 * @param modifier Modifier for the overlay
 */
@Composable
fun ContentInfoOverlay(
    contentType: String,
    title: String?,
    seriesData: SeriesContext?,
    modifier: Modifier = Modifier
) {
    val contentText = formatContentInfo(contentType, title, seriesData)
    
    if (contentText != null) {
        val truncatedText = truncateText(contentText)
        
        Text(
            text = truncatedText,
            color = Color.White,
            fontSize = FontSizes.fontSizeXL,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier
                .background(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}
