package com.flintr.tv.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.flintr.tv.data.model.DetailDisplayData
import com.flintr.tv.ui.components.detail.DetailCastSection
import com.flintr.tv.ui.components.detail.DetailHeaderData
import com.flintr.tv.ui.components.detail.DetailHeaderSection
import com.flintr.tv.ui.components.detail.DetailHeaderSkeleton
import com.flintr.tv.ui.components.detail.DetailRecommendationsSection
import com.flintr.tv.ui.components.detail.focusWatchAndRevealHeader
import com.flintr.tv.ui.theme.DarkColors
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.appColors
import com.flintr.tv.ui.utils.onBackButton
import com.flintr.tv.ui.utils.onClick
import com.flintr.tv.ui.utils.safeRequestFocus
import com.flintr.tv.utils.error.reportWarning
import com.flintr.tv.viewmodel.MovieDetailUiState
import kotlinx.coroutines.launch

/**
 * DetailHeaderSection's internal row top padding (streamflix 1:1): the shared header pads its
 * row 50dp from the top, so the visible block (poster + text) starts BELOW that padding.
 */
private val HeaderRowTopPadding = 50.dp

/**
 * Vertically centers the visible header block (poster + title/meta/overview/buttons) in the
 * viewport: on entering the detail screen the remaining space above and below the block is
 * equal. The container is at least one viewport high, so the sections below start at the fold
 * and the scroll/D-Pad chains behave exactly as before. The inner bottom padding cancels the
 * header row's own [HeaderRowTopPadding]: centering the padded row directly would sit the
 * visible block 25dp below the middle.
 */
@Composable
private fun CenteredHeaderBlock(viewportHeight: Dp, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = viewportHeight),
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier.padding(bottom = HeaderRowTopPadding)) {
            content()
        }
    }
}

/**
 * Movie detail overview (streamflix section structure, flintrTV design).
 *
 * The composable is intentionally stateless regarding navigation: Phase 4 wires the route and
 * supplies the callbacks. All interactive surfaces use [onClick] (touch + D-Pad center) and the
 * screen guarantees a focused element through [safeRequestFocus] plus a first-element fallback.
 *
 * The header (poster/title/meta/genres/overview/buttons) is [DetailHeaderSection], shared 1:1
 * with SeriesDetailScreen - see that file for the series variant.
 */
@Composable
fun MovieDetailScreen(
    uiState: MovieDetailUiState,
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {},
    onWatchClick: () -> Unit = {},
    onToggleWatchlist: () -> Unit = {},
    onMovieClick: (Int) -> Unit = {},
    onRetry: () -> Unit = {}
) {
    val colors = appColors()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgPreview)
            .onBackButton(onBackClick)
    ) {
        when {
            uiState.isLoading -> MovieDetailLoading(baseStream = uiState.baseStream)
            uiState.error != null -> MovieDetailError(error = uiState.error, onRetry = onRetry)
            uiState.detail != null -> MovieDetailContent(
                detail = uiState.detail,
                isInWatchlist = uiState.isInWatchlist,
                watchProgress = uiState.watchProgress,
                recommendations = uiState.recommendations,
                castPhotos = uiState.castPhotos,
                onWatchClick = onWatchClick,
                onToggleWatchlist = onToggleWatchlist,
                onMovieClick = onMovieClick
            )
        }
    }
}

@Composable
private fun MovieDetailLoading(baseStream: com.flintr.tv.data.model.VodStream?) {
    val colors = appColors()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        val focused = focusRequester.safeRequestFocus("MovieDetailScreen", "LOADING_ROOT")
        if (!focused) {
            focusRequester.safeRequestFocus("MovieDetailScreen", "LOADING_ROOT_FALLBACK")
        }
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .testTag("movie-detail-loading")
    ) {
        val backdropUrl = baseStream?.backdropPath?.firstOrNull { it.isNotBlank() }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .background(colors.bgCard)
                .testTag("movie-detail-backdrop")
        ) {
            if (backdropUrl != null) {
                AsyncImage(
                    model = backdropUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                // ~25% scrim at the top: the backdrop wallpaper reads ~25%
                                // weaker while white header text gains contrast; fades to solid.
                                colors.bgPreview.copy(alpha = 0.5f),
                                colors.bgPreview.copy(alpha = 0.85f),
                                colors.bgPreview
                            )
                        )
                    )
            )
        }

        // Same centered header geometry as MovieDetailContent, so the switch from loading to
        // loaded keeps poster and title in place instead of rendering a half-built layout.
        CenteredHeaderBlock(viewportHeight = maxHeight) {
            DetailHeaderSkeleton(
                posterUrl = baseStream?.let { it.streamIcon ?: it.cover },
                title = baseStream?.name,
                meta = baseStream?.let {
                    listOfNotNull(
                        it.duration?.takeIf { value -> value.isNotBlank() },
                        it.releaseDate?.takeIf { value -> value.isNotBlank() }
                    ).joinToString(" • ")
                },
                testTagPrefix = "movie-detail"
            )
        }
    }
}

@Composable
private fun MovieDetailError(error: String, onRetry: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val focused = focusRequester.safeRequestFocus("MovieDetailScreen", "RETRY_BUTTON")
        if (!focused) {
            focusRequester.safeRequestFocus("MovieDetailScreen", "RETRY_BUTTON_FALLBACK")
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimensions.spacingXL),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = error,
            color = DarkColors.textPrimary,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = Dimensions.spacingLG)
        )
        Box(
            modifier = Modifier
                .testTag("movie-detail-retry")
                .focusRequester(focusRequester)
                .onFocusChanged { isFocused = it.isFocused }
                .focusable()
                .onClick(onRetry)
                .background(
                    color = if (isFocused) appColors().buttonPrimaryHover else appColors().selectionFill,
                    shape = RoundedCornerShape(Dimensions.radiusMD)
                )
                .padding(horizontal = Dimensions.spacingLG, vertical = Dimensions.spacingSM)
        ) {
            Text(
                text = "Erneut versuchen",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun MovieDetailContent(
    detail: DetailDisplayData,
    isInWatchlist: Boolean,
    watchProgress: com.flintr.tv.data.model.WatchProgress?,
    recommendations: List<com.flintr.tv.data.model.VodStream>,
    castPhotos: Map<String, String>,
    onWatchClick: () -> Unit,
    onToggleWatchlist: () -> Unit,
    onMovieClick: (Int) -> Unit
) {
    val colors = appColors()
    val context = LocalContext.current
    val backdropUrl = detail.backdropPath?.firstOrNull { it.isNotBlank() }
    val watchFocusRequester = remember { FocusRequester() }
    val trailerFocusRequester = remember { FocusRequester() }
    val watchlistFocusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()
    val recommendationListState = rememberLazyListState()
    val recommendationFocusRequesters = remember(recommendations.size) {
        List(recommendations.size) { FocusRequester() }
    }

    fun focusFirstRecommendation(): Boolean {
        if (recommendations.isEmpty()) return false
        coroutineScope.launch {
            // Scroll first, focus second: a not-yet-composed Lazy item would never accept focus
            // and would leave the screen without a focused node (D-Pad/Back dead).
            recommendationListState.scrollToItem(0)
            recommendationFocusRequesters.getOrNull(0)?.safeRequestFocus(
                "MovieDetailScreen",
                "RECOMMENDATION_TILE_0"
            )
        }
        return true
    }

    val scrollState = rememberScrollState()

    fun focusWatchButton() {
        coroutineScope.launch {
            focusWatchAndRevealHeader(
                watchFocusRequester,
                scrollState,
                "MovieDetailScreen",
                "RECOMMENDATION_UP_TO_WATCH"
            )
        }
    }

    LaunchedEffect(Unit) {
        val focused = watchFocusRequester.safeRequestFocus("MovieDetailScreen", "WATCH_BUTTON")
        if (!focused) {
            // Fallback to the screen's first interactive element - never leave the screen
            // without a focused node, otherwise D-Pad/Back key dispatch stops entirely.
            watchFocusRequester.safeRequestFocus("MovieDetailScreen", "WATCH_BUTTON_FALLBACK")
        }
    }

    val meta = listOfNotNull(
        detail.runtime?.takeIf { it.isNotBlank() },
        detail.releaseDate?.takeIf { it.isNotBlank() }
    ).joinToString(" • ").takeIf { it.isNotEmpty() }

    val watchProgressPercent = watchProgress?.takeIf { it.duration > 0L }?.let {
        (it.position.toDouble() / it.duration.toDouble() * 100).coerceIn(0.0, 100.0).toFloat()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .background(colors.bgCard)
                .testTag("movie-detail-backdrop")
        ) {
            if (backdropUrl != null) {
                AsyncImage(
                    model = backdropUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                // ~25% scrim at the top: the backdrop wallpaper reads ~25%
                                // weaker while white header text gains contrast; fades to solid.
                                colors.bgPreview.copy(alpha = 0.5f),
                                colors.bgPreview.copy(alpha = 0.85f),
                                colors.bgPreview
                            )
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            CenteredHeaderBlock(viewportHeight = viewportHeight) {
                DetailHeaderSection(
                    data = DetailHeaderData(
                        posterUrl = detail.posterUrl,
                        title = detail.title ?: "Unbekannter Film",
                        rating = detail.rating,
                        meta = meta,
                        genres = detail.genres,
                        overview = detail.plot,
                        isInWatchlist = isInWatchlist,
                        trailerAvailable = !detail.youtubeTrailer.isNullOrBlank(),
                        watchProgress = watchProgressPercent,
                        onWatch = onWatchClick,
                        onTrailer = { openYouTubeTrailer(context, detail.youtubeTrailer) },
                        onToggleWatchlist = onToggleWatchlist
                    ),
                    testTagPrefix = "movie-detail",
                    watchFocusRequester = watchFocusRequester,
                    trailerFocusRequester = trailerFocusRequester,
                    watchlistFocusRequester = watchlistFocusRequester,
                    onButtonRowDown = { focusFirstRecommendation() },
                    scrollState = scrollState,
                    modifier = Modifier.testTag("movie-detail-header-row")
                )
            }

            if (detail.cast.isNotEmpty()) {
                DetailCastSection(cast = detail.cast, testTag = "movie-detail-cast", photos = castPhotos)
            }

            detail.director?.takeIf { it.isNotBlank() }?.let { director ->
                Column(
                    modifier = Modifier.padding(
                        top = Dimensions.spacingLG,
                        start = Dimensions.spacingXL,
                        end = Dimensions.spacingXL
                    )
                ) {
                    Text(
                        text = "Regie",
                        color = DarkColors.textPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = Dimensions.spacingXS)
                    )
                    Text(
                        text = director,
                        color = DarkColors.textSecondary,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.testTag("movie-detail-director")
                    )
                }
            }

            if (recommendations.isNotEmpty()) {
                DetailRecommendationsSection(
                    testTag = "movie-detail-recommendations",
                    recommendations = recommendations,
                    listState = recommendationListState,
                    focusRequesters = recommendationFocusRequesters,
                    onUp = { focusWatchButton() },
                    key = { movie -> movie.streamId },
                    posterUrl = { movie -> movie.streamIcon ?: movie.cover },
                    title = { movie -> movie.name },
                    cardTestTag = { movie -> "recommendation-${movie.streamId}" },
                    onItemClick = { movie -> onMovieClick(movie.streamId) }
                )
            }

            Spacer(modifier = Modifier.height(Dimensions.spacingXL))
        }
    }
}

private val YOUTUBE_ID_PATH_PREFIXES = setOf("embed", "shorts", "v")

/**
 * Extracts a video id from raw `youtube_trailer` values (id, watch URL, youtu.be link or
 * `/embed/`, `/shorts/`, `/v/` paths on any youtube.com host incl. m.youtube.com).
 */
internal fun youtubeTrailerVideoId(raw: String?): String? {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    if (!trimmed.contains("://")) return trimmed

    val uri = trimmed.toUri()
    val fromQuery = uri.getQueryParameter("v")?.trim()
    if (!fromQuery.isNullOrEmpty()) return fromQuery

    val host = uri.host.orEmpty()
    if (host.contains("youtu.be")) {
        return uri.lastPathSegment?.trim()?.takeIf { it.isNotEmpty() }
    }
    if (host.contains("youtube.com")) {
        val segments = uri.pathSegments.orEmpty()
        if (segments.size >= 2 && segments[0] in YOUTUBE_ID_PATH_PREFIXES) {
            return segments[1].trim().takeIf { it.isNotEmpty() }
        }
    }
    return null
}

internal fun buildYouTubeTrailerIntent(raw: String?): Intent? {
    val videoId = youtubeTrailerVideoId(raw) ?: return null
    return Intent(
        Intent.ACTION_VIEW,
        "https://www.youtube.com/watch?v=$videoId".toUri()
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

private fun openYouTubeTrailer(context: Context, raw: String?) {
    val intent = buildYouTubeTrailerIntent(raw) ?: return
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        reportWarning("MovieDetailScreen", "Failed to open YouTube trailer", e)
    }
}
