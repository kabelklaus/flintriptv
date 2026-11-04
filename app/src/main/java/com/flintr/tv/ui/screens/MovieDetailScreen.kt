package com.flintr.tv.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.flintr.tv.data.model.DetailDisplayData
import com.flintr.tv.ui.components.detail.DetailHeaderData
import com.flintr.tv.ui.components.detail.DetailHeaderSection
import com.flintr.tv.ui.components.detail.DetailHeaderSkeleton
import com.flintr.tv.ui.components.detail.focusWatchAndRevealHeader
import com.flintr.tv.ui.theme.DarkColors
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.appColors
import com.flintr.tv.ui.theme.tvFocusRing
import com.flintr.tv.ui.utils.onBackButton
import com.flintr.tv.ui.utils.onClick
import com.flintr.tv.ui.utils.onDpadDirection
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
    onToggleFavorite: () -> Unit = {},
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
                isFavorite = uiState.isFavorite,
                watchProgress = uiState.watchProgress,
                recommendations = uiState.recommendations,
                onWatchClick = onWatchClick,
                onToggleFavorite = onToggleFavorite,
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
    isFavorite: Boolean,
    watchProgress: com.flintr.tv.data.model.WatchProgress?,
    recommendations: List<com.flintr.tv.data.model.VodStream>,
    onWatchClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMovieClick: (Int) -> Unit
) {
    val colors = appColors()
    val context = LocalContext.current
    val backdropUrl = detail.backdropPath?.firstOrNull { it.isNotBlank() }
    val watchFocusRequester = remember { FocusRequester() }
    val trailerFocusRequester = remember { FocusRequester() }
    val favoriteFocusRequester = remember { FocusRequester() }
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
                        isFavorite = isFavorite,
                        trailerAvailable = !detail.youtubeTrailer.isNullOrBlank(),
                        watchProgress = watchProgressPercent,
                        onWatch = onWatchClick,
                        onTrailer = { openYouTubeTrailer(context, detail.youtubeTrailer) },
                        onFavorite = onToggleFavorite
                    ),
                    testTagPrefix = "movie-detail",
                    watchFocusRequester = watchFocusRequester,
                    trailerFocusRequester = trailerFocusRequester,
                    favoriteFocusRequester = favoriteFocusRequester,
                    onButtonRowDown = { focusFirstRecommendation() },
                    scrollState = scrollState,
                    modifier = Modifier.testTag("movie-detail-header-row")
                )
            }

            if (detail.cast.isNotEmpty()) {
                MovieDetailCastSection(cast = detail.cast)
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
                MovieDetailRecommendationsSection(
                    recommendations = recommendations,
                    listState = recommendationListState,
                    focusRequesters = recommendationFocusRequesters,
                    onMovieClick = onMovieClick,
                    onUp = { focusWatchButton() }
                )
            }

            Spacer(modifier = Modifier.height(Dimensions.spacingXL))
        }
    }
}

@Composable
private fun MovieDetailRecommendationsSection(
    recommendations: List<com.flintr.tv.data.model.VodStream>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    focusRequesters: List<FocusRequester>,
    onMovieClick: (Int) -> Unit,
    onUp: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(top = Dimensions.spacingLG, start = Dimensions.spacingXL, end = Dimensions.spacingXL)
            .testTag("movie-detail-recommendations")
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
            itemsIndexed(
                items = recommendations,
                key = { _, movie -> movie.streamId }
            ) { index, movie ->
                RecommendationCard(
                    movie = movie,
                    focusRequester = focusRequesters.getOrNull(index),
                    onUp = onUp,
                    onClick = { onMovieClick(movie.streamId) }
                )
            }
        }
    }
}

@Composable
private fun RecommendationCard(
    movie: com.flintr.tv.data.model.VodStream,
    focusRequester: FocusRequester?,
    onUp: () -> Unit,
    onClick: () -> Unit
) {
    val colors = appColors()
    var isFocused by remember { mutableStateOf(false) }
    val internalFocusRequester = remember { FocusRequester() }
    val actualFocusRequester = focusRequester ?: internalFocusRequester
    val poster = movie.streamIcon ?: movie.cover

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
            .testTag("recommendation-${movie.streamId}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(Dimensions.radiusMD))
                .background(colors.bgCard)
        ) {
            if (poster != null) {
                AsyncImage(
                    model = poster,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Text(
            text = movie.name,
            color = DarkColors.textPrimary,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Dimensions.spacingXS)
        )
    }
}

@Composable
private fun MovieDetailCastSection(cast: List<String>) {
    val colors = appColors()
    Column(
        modifier = Modifier
            .padding(top = Dimensions.spacingLG, start = Dimensions.spacingXL, end = Dimensions.spacingXL)
            .testTag("movie-detail-cast")
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
                        .testTag("movie-detail-cast-member-$index"),
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

private val YOUTUBE_ID_PATH_PREFIXES = setOf("embed", "shorts", "v")

/**
 * Extracts a video id from raw `youtube_trailer` values (id, watch URL, youtu.be link or
 * `/embed/`, `/shorts/`, `/v/` paths on any youtube.com host incl. m.youtube.com).
 */
internal fun youtubeTrailerVideoId(raw: String?): String? {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    if (!trimmed.contains("://")) return trimmed

    val uri = Uri.parse(trimmed)
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
        Uri.parse("https://www.youtube.com/watch?v=$videoId")
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
