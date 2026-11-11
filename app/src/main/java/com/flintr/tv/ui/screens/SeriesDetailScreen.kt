package com.flintr.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.flintr.tv.data.model.DetailDisplayData
import com.flintr.tv.data.model.Episode
import com.flintr.tv.data.model.Series
import com.flintr.tv.ui.components.detail.DetailCastSection
import com.flintr.tv.ui.components.detail.DetailHeaderData
import com.flintr.tv.ui.components.detail.DetailHeaderSection
import com.flintr.tv.ui.components.detail.DetailHeaderSkeleton
import com.flintr.tv.ui.components.detail.DetailRecommendationsSection
import com.flintr.tv.ui.components.detail.focusBottomBuffer
import com.flintr.tv.ui.components.detail.focusWatchAndRevealHeader
import com.flintr.tv.ui.components.detail.formatSeasonLabel
import com.flintr.tv.ui.theme.CardTitleStyle
import com.flintr.tv.ui.theme.DarkColors
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.appColors
import com.flintr.tv.ui.theme.tvFocusRing
import com.flintr.tv.ui.utils.onBackButton
import com.flintr.tv.ui.utils.onClick
import com.flintr.tv.ui.utils.onDpadDirection
import com.flintr.tv.ui.utils.safeRequestFocus
import com.flintr.tv.utils.error.reportWarning
import com.flintr.tv.viewmodel.SeasonUiModel
import com.flintr.tv.viewmodel.SeriesDetailUiState
import com.flintr.tv.viewmodel.WatchTargetUiModel
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
 * visible block 25dp below the middle. Identical copy of the helper in MovieDetailScreen.kt -
 * the shared header itself lives in components/detail and renders 1:1 for both screens.
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
 * Series detail overview: header shared 1:1 with [MovieDetailScreen] via [DetailHeaderSection],
 * plus streamflix-style season tiles (poster + title + watched ribbon). A tile click reports its
 * season number to the Phase-4 route, which then opens [SeasonScreen]. The screen itself never
 * navigates.
 *
 * Genre/rating are rendered only when the ViewModel's TMDB meta lookup matched (rating and genres
 * come from TmdbMetaRepository); there is no Xtream fallback. Focus starts on the first action button (never on a Lazy item) and moving down scrolls
 * the season row to the first tile *before* requesting focus, so a D-Pad press can never land on a
 * not-yet-composed node. Moving back up out of the lowest focusable section (season tiles, or
 * recommendation cards when there are no seasons) always lands back on the header's watch button
 * and scrolls to the top so the poster is visible - never leaving the D-Pad "stuck" below the fold.
 */
@Composable
fun SeriesDetailScreen(
    uiState: SeriesDetailUiState,
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {},
    onToggleWatchlist: () -> Unit = {},
    onWatchClick: (Episode) -> Unit = {},
    onSeasonClick: (Int) -> Unit = {},
    onSeriesClick: (Int) -> Unit = {},
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
            uiState.isLoading -> SeriesDetailLoading(baseSeries = uiState.series)
            uiState.error != null -> SeriesDetailError(error = uiState.error, onRetry = onRetry)
            uiState.detail != null -> SeriesDetailContent(
                detail = uiState.detail,
                seasons = uiState.seasons,
                seasonsHint = uiState.seasonsHint,
                isInWatchlist = uiState.isInWatchlist,
                recommendations = uiState.recommendations,
                watchTarget = uiState.watchTarget,
                watchLabel = uiState.watchLabel,
                watchProgress = uiState.watchProgress,
                castPhotos = uiState.castPhotos,
                isMetaLoading = uiState.isMetaLoading,
                onToggleWatchlist = onToggleWatchlist,
                onWatchClick = onWatchClick,
                onSeasonClick = onSeasonClick,
                onSeriesClick = onSeriesClick
            )
        }
    }
}

@Composable
private fun SeriesDetailLoading(baseSeries: Series?) {
    val colors = appColors()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        val focused = focusRequester.safeRequestFocus("SeriesDetailScreen", "LOADING_ROOT")
        if (!focused) {
            focusRequester.safeRequestFocus("SeriesDetailScreen", "LOADING_ROOT_FALLBACK")
        }
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .testTag("series-detail-loading")
    ) {
        val backdropUrl = baseSeries?.backdropPath?.firstOrNull { it.isNotBlank() }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .background(colors.bgCard)
                .testTag("series-detail-backdrop")
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
                                // Same ~25% top scrim as the loaded header: the catalog
                                // artwork reads weaker and stays in place when details land.
                                colors.bgPreview.copy(alpha = 0.5f),
                                colors.bgPreview.copy(alpha = 0.85f),
                                colors.bgPreview
                            )
                        )
                    )
            )
        }

        // Same centered header geometry as SeriesDetailContent. Poster and title are rendered
        // as soon as a base series is available instead of leaving the whole header empty.
        CenteredHeaderBlock(viewportHeight = maxHeight) {
            DetailHeaderSkeleton(
                posterUrl = baseSeries?.cover,
                title = baseSeries?.name,
                meta = baseSeries?.releaseDate?.take(4)?.takeIf { it.isNotBlank() },
                testTagPrefix = "series-detail"
            )
        }
    }
}

@Composable
private fun SeriesDetailError(error: String, onRetry: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val focused = focusRequester.safeRequestFocus("SeriesDetailScreen", "RETRY_BUTTON")
        if (!focused) {
            focusRequester.safeRequestFocus("SeriesDetailScreen", "RETRY_BUTTON_FALLBACK")
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
                .testTag("series-detail-retry")
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
private fun SeriesDetailContent(
    detail: DetailDisplayData,
    seasons: List<SeasonUiModel>,
    seasonsHint: String?,
    isInWatchlist: Boolean,
    recommendations: List<Series>,
    watchTarget: WatchTargetUiModel?,
    watchLabel: String,
    watchProgress: Float?,
    castPhotos: Map<String, String>,
    isMetaLoading: Boolean,
    onToggleWatchlist: () -> Unit,
    onWatchClick: (Episode) -> Unit,
    onSeasonClick: (Int) -> Unit,
    onSeriesClick: (Int) -> Unit
) {
    val colors = appColors()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val backdropUrl = detail.backdropPath?.firstOrNull { it.isNotBlank() }

    val watchFocusRequester = remember { FocusRequester() }
    val trailerFocusRequester = remember { FocusRequester() }
    val watchlistFocusRequester = remember { FocusRequester() }
    val seasonListState = rememberLazyListState()
    val seasonFocusRequesters = remember(seasons.size) {
        List(seasons.size) { FocusRequester() }
    }
    val recommendationListState = rememberLazyListState()
    val recommendationFocusRequesters = remember(recommendations.size) {
        List(recommendations.size) { FocusRequester() }
    }

    val scrollState = rememberScrollState()

    fun focusWatchButton() {
        coroutineScope.launch {
            focusWatchAndRevealHeader(watchFocusRequester, scrollState, "SeriesDetailScreen", "BODY_UP_TO_WATCH")
        }
    }

    fun focusFirstSeason(): Boolean {
        if (seasons.isEmpty()) return false
        coroutineScope.launch {
            // Scroll first, focus second: a not-yet-composed Lazy item would never accept focus
            // and would leave the screen without a focused node (D-Pad/Back dead).
            seasonListState.scrollToItem(0)
            seasonFocusRequesters.getOrNull(0)?.safeRequestFocus(
                "SeriesDetailScreen",
                "SEASON_TILE_0"
            )
        }
        return true
    }

    fun focusFirstRecommendation(): Boolean {
        if (recommendations.isEmpty()) return false
        coroutineScope.launch {
            recommendationListState.scrollToItem(0)
            recommendationFocusRequesters.getOrNull(0)?.safeRequestFocus(
                "SeriesDetailScreen",
                "RECOMMENDATION_TILE_0"
            )
        }
        return true
    }

    fun focusFirstBodySection(): Boolean = focusFirstSeason() || focusFirstRecommendation()

    LaunchedEffect(Unit) {
        val focused = watchFocusRequester.safeRequestFocus("SeriesDetailScreen", "INITIAL_ACTION_BUTTON")
        if (!focused) {
            watchFocusRequester.safeRequestFocus("SeriesDetailScreen", "INITIAL_ACTION_BUTTON_FALLBACK")
        }
    }

    val year = detail.releaseDate?.take(4)?.takeIf { it.isNotBlank() }
    val seasonCountLabel = seasons.size.takeIf { it > 0 }?.let { count ->
        if (count == 1) "1 Staffel" else "$count Staffeln"
    }
    val meta = listOfNotNull(year, seasonCountLabel).joinToString(" • ").takeIf { it.isNotEmpty() }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .background(colors.bgCard)
                .testTag("series-detail-backdrop")
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
                // Padding NACH verticalScroll = Content-Padding: vergrößert den
                // Scrollbereich, damit die letzte Reihe samt Fokus-Ring nicht am
                // Viewport-Unterrand klebt.
                .verticalScroll(scrollState)
                .padding(bottom = Dimensions.spacingXL)
        ) {
            CenteredHeaderBlock(viewportHeight = viewportHeight) {
                DetailHeaderSection(
                    data = DetailHeaderData(
                        posterUrl = detail.posterUrl,
                        title = detail.title ?: "Unbekannte Serie",
                        rating = detail.rating,
                        meta = meta,
                        genres = detail.genres,
                        overview = detail.plot,
                        isInWatchlist = isInWatchlist,
                        trailerAvailable = !detail.youtubeTrailer.isNullOrBlank(),
                        watchLabel = watchLabel,
                        watchProgress = watchProgress,
                        isMetaLoading = isMetaLoading,
                        onWatch = { watchTarget?.episode?.let(onWatchClick) },
                        onTrailer = { openSeriesYouTubeTrailer(context, detail.youtubeTrailer) },
                        onToggleWatchlist = onToggleWatchlist
                    ),
                    testTagPrefix = "series-detail",
                    watchFocusRequester = watchFocusRequester,
                    trailerFocusRequester = trailerFocusRequester,
                    watchlistFocusRequester = watchlistFocusRequester,
                    onButtonRowDown = { focusFirstBodySection() },
                    scrollState = scrollState,
                    modifier = Modifier.testTag("series-detail-header-row")
                )
            }

            Column(
                modifier = Modifier
                    .padding(top = Dimensions.spacingXL, start = Dimensions.spacingXL, end = Dimensions.spacingXL)
                    .testTag("series-detail-seasons")
            ) {
                Text(
                    text = "Staffeln",
                    color = DarkColors.textPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = Dimensions.spacingSM)
                )

                when {
                    seasons.isNotEmpty() -> LazyRow(
                        state = seasonListState,
                        horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingMD),
                        // Analog zur Besetzungsreihe (DetailCastSection): ohne contentPadding
                        // klebt die fokussierte Randkachel am eigenen Clip-Rand der LazyRow und
                        // ihr Fokus-Ring/-Scale wird dort beschnitten. Vertikal schafft das
                        // Padding Raum fuer den Scale-UEberhang des Fokus-Rings (1.04), damit
                        // der untere Rand nicht von der naechsten Sektion abgeschnitten wird.
                        contentPadding = PaddingValues(
                            start = Dimensions.spacingXS,
                            end = Dimensions.spacingXS,
                            top = Dimensions.spacingSM,
                            bottom = Dimensions.spacingSM
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("series-detail-season-row")
                    ) {
                        itemsIndexed(
                            items = seasons,
                            key = { _, season -> season.seasonNumber }
                        ) { index, season ->
                            SeasonTile(
                                season = season,
                                focusRequester = seasonFocusRequesters.getOrNull(index),
                                onUp = { focusWatchButton() },
                                onDown = { focusFirstRecommendation() },
                                onClick = { onSeasonClick(season.seasonNumber) }
                            )
                        }
                    }

                    seasonsHint != null -> Text(
                        text = seasonsHint,
                        color = DarkColors.textSecondary,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.testTag("series-detail-no-seasons")
                    )
                }
            }

            if (detail.cast.isNotEmpty()) {
                DetailCastSection(cast = detail.cast, testTag = "series-detail-cast", photos = castPhotos)
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
                        modifier = Modifier.testTag("series-detail-director")
                    )
                }
            }

            if (recommendations.isNotEmpty()) {
                DetailRecommendationsSection(
                    testTag = "series-detail-recommendations",
                    recommendations = recommendations,
                    listState = recommendationListState,
                    focusRequesters = recommendationFocusRequesters,
                    onUp = {
                        if (!focusFirstSeason()) {
                            focusWatchButton()
                        }
                    },
                    key = { series -> series.seriesId },
                    posterUrl = { series -> series.cover },
                    title = { series -> series.name },
                    cardTestTag = { series -> "series-recommendation-${series.seriesId}" },
                    onItemClick = { series -> onSeriesClick(series.seriesId) }
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun SeasonTile(
    season: SeasonUiModel,
    focusRequester: FocusRequester?,
    onUp: () -> Unit,
    onDown: () -> Boolean,
    onClick: () -> Unit
) {
    val colors = appColors()
    var isFocused by remember { mutableStateOf(false) }
    val internalFocusRequester = remember { FocusRequester() }
    val actualFocusRequester = focusRequester ?: internalFocusRequester
    val shape = RoundedCornerShape(Dimensions.radiusMD)
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var cardSize by remember { mutableStateOf(IntSize.Zero) }

    Column(
        modifier = Modifier
            .width(160.dp)
            .onSizeChanged { cardSize = it }
            .bringIntoViewRequester(bringIntoViewRequester)
            .focusRequester(actualFocusRequester)
            .onFocusChanged {
                isFocused = it.isFocused
                // Fokus-Ring skaliert 1.04 und ragt unterhalb der Layout-Bounds —
                // bringIntoView mit erweitertem Rect, damit der untere Rand nicht
                // am Screen-Unterrand abgeschnitten wird.
                if (it.isFocused && cardSize != IntSize.Zero) {
                    val extra = with(density) { Dimensions.spacingLG.toPx() }
                    scope.launch {
                        bringIntoViewRequester.bringIntoView(
                            Rect(0f, 0f, cardSize.width.toFloat(), cardSize.height + extra)
                        )
                    }
                }
            }
            .focusable()
            .onDpadDirection(onUp = { onUp(); true }, onDown = onDown)
            .onClick(onClick = onClick)
            .tvFocusRing(isFocused = isFocused, onArtwork = true, shape = shape)
            .focusBottomBuffer()
            .testTag("season-tile-${season.seasonNumber}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .clip(shape)
                .background(Color.Transparent)
                .testTag("season-poster-${season.seasonNumber}")
        ) {
            if (season.posterUrl != null) {
                AsyncImage(
                    model = season.posterUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (season.isFullyWatched) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Dimensions.spacingXS)
                        .background(
                            color = colors.accentColor,
                            shape = RoundedCornerShape(Dimensions.radiusSM)
                        )
                        .padding(horizontal = Dimensions.spacingXS, vertical = 2.dp)
                        .testTag("season-watched-ribbon-${season.seasonNumber}")
                ) {
                    Text(
                        text = "Gesehen",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (season.episodeCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(Dimensions.spacingXS)
                        .background(
                            color = Color.Black.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(Dimensions.radiusSM)
                        )
                        .padding(horizontal = Dimensions.spacingXS, vertical = 2.dp)
                ) {
                    Text(
                        text = "${season.watchedCount}/${season.episodeCount}",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Text(
            text = formatSeasonLabel(season.title),
            color = DarkColors.textPrimary,
            fontSize = CardTitleStyle.fontSize,
            fontWeight = CardTitleStyle.fontWeight,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(top = Dimensions.spacingXS)
                .testTag("season-title-${season.seasonNumber}")
        )
    }
}

private fun openSeriesYouTubeTrailer(context: android.content.Context, raw: String?) {
    val intent = buildYouTubeTrailerIntent(raw) ?: return
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        reportWarning("SeriesDetailScreen", "Failed to open YouTube trailer", e)
    }
}
