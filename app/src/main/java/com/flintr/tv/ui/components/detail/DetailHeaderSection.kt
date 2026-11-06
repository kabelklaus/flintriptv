package com.flintr.tv.ui.components.detail

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.appColors
import com.flintr.tv.ui.theme.tvFocusRing
import com.flintr.tv.ui.utils.onClick
import com.flintr.tv.ui.utils.onDpadDirection
import com.flintr.tv.ui.utils.safeRequestFocus
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Pure field holder for [DetailHeaderSection] - no business logic, no formatting decisions.
 * Callers (MovieDetailScreen/SeriesDetailScreen) compute [meta]/[genres]/[overview] from their
 * own IMDb/Xtream data before constructing this.
 */
data class DetailHeaderData(
    val posterUrl: String?,
    val title: String,
    val rating: Double?,
    val meta: String?,
    val genres: List<String>?,
    val overview: String?,
    val isInWatchlist: Boolean,
    val trailerAvailable: Boolean,
    val watchLabel: String = "Ansehen",
    /** Playback progress (0-100) of the concrete watch target; null/0 hides the in-button bar. */
    val watchProgress: Float? = null,
    val onWatch: () -> Unit,
    val onTrailer: () -> Unit,
    val onToggleWatchlist: () -> Unit
)

/**
 * Shared Film+Serie detail header: poster left, title/meta/genres/overview/buttons right - used
 * identically by MovieDetailScreen and SeriesDetailScreen (streamflix `content_*_tv.xml` 1:1
 * dp/sp values). The three button FocusRequesters are owned by the caller so the caller's lower
 * sections (season tiles, recommendation cards) can request focus back onto the watch button
 * when the user presses D-Pad Up from the bottom of the screen.
 *
 * D-Pad Up inside the header never dead-ends: the buttons go up to the "Mehr anzeigen" toggle (if
 * any), and the toggle - the topmost focusable - jumps back to the watch button and scrolls
 * [scrollState] to 0 so the poster is fully visible again.
 */
@Composable
fun DetailHeaderSection(
    data: DetailHeaderData,
    testTagPrefix: String,
    watchFocusRequester: FocusRequester,
    trailerFocusRequester: FocusRequester,
    watchlistFocusRequester: FocusRequester,
    onButtonRowDown: (() -> Boolean)? = null,
    scrollState: ScrollState? = null,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val overviewToggleFocusRequester = remember { FocusRequester() }
    val hasOverviewToggle = isOverviewExpandable(data.overview)

    fun returnToTop() {
        coroutineScope.launch {
            focusWatchAndRevealHeader(watchFocusRequester, scrollState, "DetailHeaderSection", "HEADER_UP_TO_WATCH")
        }
    }

    // Buttons -> toggle above them; without a toggle the buttons are the top, so just reveal the poster.
    val onButtonUp: () -> Boolean = {
        coroutineScope.launch {
            if (hasOverviewToggle) {
                overviewToggleFocusRequester.safeRequestFocus("DetailHeaderSection", "OVERVIEW_TOGGLE")
            } else {
                scrollState?.revealTop()
            }
        }
        true
    }
    val focusOrder = remember(data.trailerAvailable) {
        buildList {
            add(watchFocusRequester)
            if (data.trailerAvailable) add(trailerFocusRequester)
            add(watchlistFocusRequester)
        }
    }

    fun requestButtonFocus(index: Int) {
        focusOrder.getOrNull(index)?.let { requester ->
            coroutineScope.launch {
                requester.safeRequestFocus("DetailHeaderSection", "ACTION_BUTTON_$index")
            }
        }
    }

    Row(
        modifier = modifier.padding(start = 70.dp, top = 50.dp, end = 70.dp),
        verticalAlignment = Alignment.Top
    ) {
        DetailHeaderPoster(posterUrl = data.posterUrl, testTag = "$testTagPrefix-poster")

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 40.dp)
        ) {
            Text(
                text = data.title,
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("$testTagPrefix-title")
            )

            val formattedMeta = formatMetaLine(data.meta)?.takeIf { it.isNotBlank() }
            if (data.rating != null || formattedMeta != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 15.dp)
                ) {
                    data.rating?.let { rating ->
                        DetailImdbRating(rating = rating, testTag = "$testTagPrefix-rating")
                    }
                    formattedMeta?.let { meta ->
                        if (data.rating != null) {
                            Box(modifier = Modifier.width(25.dp))
                        }
                        Text(
                            text = meta,
                            color = Color.White,
                            fontSize = 14.sp,
                            modifier = Modifier.testTag("$testTagPrefix-meta")
                        )
                    }
                }
            }

            if (!data.genres.isNullOrEmpty()) {
                Text(
                    text = data.genres.joinToString(", "),
                    color = Color.White,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = 5.dp)
                        .testTag("$testTagPrefix-genres")
                )
            }

            data.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                DetailOverviewText(
                    overview = overview,
                    testTagPrefix = testTagPrefix,
                    toggleFocusRequester = overviewToggleFocusRequester,
                    onUp = {
                        returnToTop()
                        true
                    },
                    modifier = Modifier.padding(top = 30.dp)
                )
            }

            DetailActionButtonRow(
                data = data,
                testTagPrefix = testTagPrefix,
                watchFocusRequester = watchFocusRequester,
                trailerFocusRequester = trailerFocusRequester,
                watchlistFocusRequester = watchlistFocusRequester,
                watchIndex = focusOrder.indexOf(watchFocusRequester),
                trailerIndex = focusOrder.indexOf(trailerFocusRequester),
                watchlistIndex = focusOrder.indexOf(watchlistFocusRequester),
                onButtonRowDown = onButtonRowDown,
                onButtonUp = onButtonUp,
                requestButtonFocus = ::requestButtonFocus,
                modifier = Modifier.padding(top = 30.dp)
            )
        }
    }
}

/**
 * Loading state of [DetailHeaderSection] with the identical geometry (padding, 250x375 poster,
 * 30sp title, button row height), so the switch to the loaded header does not jump. Whatever the
 * catalog already knows (poster/title/meta) is shown for real; everything else is a static
 * placeholder block - no shimmer animation, the box is already under load while the API answers.
 * Nothing in here is focusable; the caller keeps focus on its loading root.
 */
@Composable
fun DetailHeaderSkeleton(
    posterUrl: String?,
    title: String?,
    meta: String?,
    testTagPrefix: String,
    modifier: Modifier = Modifier
) {
    val colors = appColors()
    Row(
        modifier = modifier
            .padding(start = 70.dp, top = 50.dp, end = 70.dp)
            .testTag("$testTagPrefix-skeleton"),
        verticalAlignment = Alignment.Top
    ) {
        DetailHeaderPoster(posterUrl = posterUrl, testTag = "$testTagPrefix-poster")

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 40.dp)
        ) {
            if (title != null) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("$testTagPrefix-title")
                )
            } else {
                SkeletonBlock(width = 420.dp, height = 36.dp, color = colors.bgCard)
            }

            val formattedMeta = formatMetaLine(meta)?.takeIf { it.isNotBlank() }
            if (formattedMeta != null) {
                Text(
                    text = formattedMeta,
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .padding(top = 15.dp)
                        .testTag("$testTagPrefix-meta")
                )
            } else {
                SkeletonBlock(width = 220.dp, height = 22.dp, color = colors.bgCard, topPadding = 15.dp)
            }

            // Overview placeholder: a few full-width lines and a shorter last line.
            SkeletonBlock(width = null, height = 18.dp, color = colors.bgCard, topPadding = 30.dp)
            SkeletonBlock(width = null, height = 18.dp, color = colors.bgCard, topPadding = 10.dp)
            SkeletonBlock(width = 360.dp, height = 18.dp, color = colors.bgCard, topPadding = 10.dp)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(top = 30.dp)
                    .testTag("$testTagPrefix-loading-indicator")
            ) {
                SkeletonBlock(width = 160.dp, height = 44.dp, color = colors.bgCard)
                Box(modifier = Modifier.width(25.dp))
                CircularProgressIndicator(
                    color = colors.accentColor,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Details werden geladen…",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 16.sp,
                    modifier = Modifier.padding(start = Dimensions.spacingSM)
                )
            }
        }
    }
}

@Composable
private fun SkeletonBlock(
    width: Dp?,
    height: Dp,
    color: Color,
    topPadding: Dp = 0.dp
) {
    Box(
        modifier = Modifier
            .padding(top = topPadding)
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .height(height)
            .clip(RoundedCornerShape(Dimensions.radiusSM))
            .background(color)
    )
}

/**
 * Watch/Trailer/Watchlist row, content-width, single line - streamflix `content_*_tv.xml` 1:1.
 */
@Composable
private fun DetailActionButtonRow(
    data: DetailHeaderData,
    testTagPrefix: String,
    watchFocusRequester: FocusRequester,
    trailerFocusRequester: FocusRequester,
    watchlistFocusRequester: FocusRequester,
    watchIndex: Int,
    trailerIndex: Int,
    watchlistIndex: Int,
    onButtonRowDown: (() -> Boolean)?,
    onButtonUp: () -> Boolean,
    requestButtonFocus: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        DetailActionButton(
            label = data.watchLabel,
            testTag = "$testTagPrefix-watch",
            primary = true,
            icon = Icons.Filled.PlayArrow,
            focusRequester = watchFocusRequester,
            watchProgress = data.watchProgress,
            onRight = {
                requestButtonFocus(watchIndex + 1)
                true
            },
            onUp = onButtonUp,
            onDown = onButtonRowDown,
            onClick = data.onWatch
        )

        Box(modifier = Modifier.width(25.dp))

        if (data.trailerAvailable) {
            DetailActionButton(
                label = "Trailer",
                testTag = "$testTagPrefix-trailer",
                primary = false,
                icon = Icons.Filled.Movie,
                focusRequester = trailerFocusRequester,
                onLeft = {
                    requestButtonFocus(trailerIndex - 1)
                    true
                },
                onRight = {
                    requestButtonFocus(trailerIndex + 1)
                    true
                },
                onUp = onButtonUp,
                onDown = onButtonRowDown,
                onClick = data.onTrailer
            )
            Box(modifier = Modifier.width(20.dp))
        }

        DetailWatchlistButton(
            isInWatchlist = data.isInWatchlist,
            testTag = "$testTagPrefix-watchlist",
            focusRequester = watchlistFocusRequester,
            onLeft = {
                requestButtonFocus(watchlistIndex - 1)
                true
            },
            onUp = onButtonUp,
            onDown = onButtonRowDown,
            onClick = data.onToggleWatchlist
        )
    }
}

@Composable
private fun DetailHeaderPoster(posterUrl: String?, testTag: String) {
    val colors = appColors()
    Box(
        modifier = Modifier
            .width(250.dp)
            .height(375.dp)
            .clip(RoundedCornerShape(Dimensions.radiusMD))
            .background(colors.bgCard)
            .testTag(testTag)
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
}

@Composable
private fun DetailImdbRating(rating: Double, testTag: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .background(
                    color = Color(0xFFFFC107),
                    shape = RoundedCornerShape(Dimensions.radiusSM)
                )
                .padding(horizontal = Dimensions.spacingXS, vertical = 2.dp)
        ) {
            Text(
                text = "IMDb",
                color = Color.Black,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = String.format(Locale.US, "%.1f", rating),
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = Dimensions.spacingXS)
        )
    }
}

private const val OVERVIEW_TOGGLE_MIN_LENGTH = 240

private fun isOverviewExpandable(overview: String?): Boolean =
    !overview.isNullOrBlank() && overview.length > OVERVIEW_TOGGLE_MIN_LENGTH

/**
 * Focuses the watch button, then scrolls the detail column back to 0 so the poster is fully
 * visible. Shared by every "D-Pad Up back to the top" path (header toggle and body sections).
 */
suspend fun focusWatchAndRevealHeader(
    watchFocusRequester: FocusRequester,
    scrollState: ScrollState?,
    logTag: String,
    targetName: String
) {
    watchFocusRequester.safeRequestFocus(logTag, targetName)
    scrollState?.revealTop()
}

private suspend fun ScrollState.revealTop() {
    // One frame so the focus change's bring-into-view has started; the higher priority below
    // then cancels it instead of being undone by it.
    withFrameNanos { }
    scroll(MutatePriority.PreventUserInput) { scrollBy(-value.toFloat()) }
}

@Composable
private fun DetailOverviewText(
    overview: String,
    testTagPrefix: String,
    toggleFocusRequester: FocusRequester,
    onUp: () -> Boolean,
    modifier: Modifier = Modifier
) {
    val colors = appColors()
    var expanded by remember(overview) { mutableStateOf(false) }
    val isLong = isOverviewExpandable(overview)

    Column(modifier = modifier) {
        Text(
            text = overview,
            color = colors.textSecondary,
            fontSize = 14.sp,
            maxLines = if (expanded || !isLong) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("$testTagPrefix-overview")
        )
        if (isLong) {
            var isFocused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .padding(top = Dimensions.spacingXS)
                    .focusRequester(toggleFocusRequester)
                    .onFocusChanged { isFocused = it.isFocused }
                    .focusable()
                    .onDpadDirection(onUp = onUp)
                    .onClick(onClick = { expanded = !expanded })
                    .tvFocusRing(
                        isFocused = isFocused,
                        shape = RoundedCornerShape(Dimensions.radiusSM)
                    )
                    .padding(horizontal = Dimensions.spacingXS, vertical = 2.dp)
                    .testTag("$testTagPrefix-overview-toggle")
            ) {
                Text(
                    text = if (expanded) "Weniger anzeigen" else "Mehr anzeigen",
                    color = colors.accentBright,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun DetailActionButton(
    label: String,
    testTag: String,
    primary: Boolean,
    icon: ImageVector,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    watchProgress: Float? = null,
    onLeft: (() -> Boolean)? = null,
    onRight: (() -> Boolean)? = null,
    onUp: (() -> Boolean)? = null,
    onDown: (() -> Boolean)? = null,
    onClick: () -> Unit
) {
    val colors = appColors()
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .height(44.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onDpadDirection(onLeft = onLeft, onRight = onRight, onUp = onUp, onDown = onDown)
            .onClick(onClick = onClick)
            .background(
                color = when {
                    primary && isFocused -> colors.buttonPrimaryHover
                    primary -> colors.selectionFill
                    isFocused -> colors.bgHover
                    else -> colors.bgCard
                },
                shape = RoundedCornerShape(Dimensions.radiusMD)
            )
            .tvFocusRing(
                isFocused = isFocused,
                onSelectionFill = primary,
                shape = RoundedCornerShape(Dimensions.radiusMD)
            )
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        DetailActionButtonContent(icon = icon, label = label)

        watchProgress?.takeIf { it > 0f && it < 100f }?.let { progress ->
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 6.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.25f))
                        .testTag("$testTag-progress")
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((progress / 100f).coerceIn(0f, 1f))
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.9f))
                            .testTag("$testTag-progress-fill")
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailActionButtonContent(icon: ImageVector, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(Dimensions.iconSizeMD)
        )
        Text(
            text = label,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = Dimensions.spacingXS)
        )
    }
}

@Composable
private fun DetailWatchlistButton(
    isInWatchlist: Boolean,
    testTag: String,
    focusRequester: FocusRequester,
    onLeft: (() -> Boolean)? = null,
    onUp: (() -> Boolean)? = null,
    onDown: (() -> Boolean)? = null,
    onClick: () -> Unit
) {
    val colors = appColors()
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(Dimensions.radiusMD)

    Box(
        modifier = Modifier
            .size(44.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onDpadDirection(onLeft = onLeft, onUp = onUp, onDown = onDown)
            .onClick(onClick = onClick)
            .background(
                color = if (isFocused) colors.bgHover else colors.bgCard,
                shape = shape
            )
            .tvFocusRing(isFocused = isFocused, shape = shape)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isInWatchlist) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = if (isInWatchlist) "Watchlist" else "Zur Watchlist",
            tint = Color.White,
            modifier = Modifier.size(Dimensions.iconSizeMD)
        )
    }
}
