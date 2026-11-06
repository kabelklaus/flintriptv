package com.flintr.tv.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.flintr.tv.data.local.WatchProgressService
import com.flintr.tv.data.model.Episode
import com.flintr.tv.data.model.Series
import com.flintr.tv.ui.components.detail.formatSeasonLabel
import com.flintr.tv.ui.theme.DarkColors
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.utils.onBackButton
import com.flintr.tv.ui.utils.safeRequestFocus
import com.flintr.tv.utils.ContentParser

/**
 * Season screen (Task 13, from the former SeriesEpisodesScreen): episodes of exactly one season.
 * Season selection now lives on [SeriesDetailScreen]'s season tiles, so this screen no longer
 * renders the flat season row.
 *
 * Everything that made the old screen work is preserved and reused unchanged:
 *  - watched-state batch loading + long-press menu refresh,
 *  - smart resume, resolved per season ([WatchProgressService.getSmartResumeTarget]),
 *  - [EpisodesList]/`rememberEpisodeFocus` with the proven scroll-to-item-then-focus recovery and
 *    its fallback (see [requestInitialEpisodeFocus]).
 *
 * The screen is navigation-free: [onEpisodeClick] and [onBack] are supplied by Phase 4.
 */
@Composable
fun SeasonScreen(
    series: Series,
    seasonNumber: Int,
    watchProgressService: WatchProgressService,
    onBack: () -> Unit,
    onEpisodeClick: (Episode) -> Unit,
    onLoadSeriesInfo: (Int) -> Unit = {}
) {
    val sortedSeasons = series.seasons?.sortedBy { it.seasonNumber }.orEmpty()
    val season = sortedSeasons.firstOrNull { it.seasonNumber == seasonNumber }

    LaunchedEffect(series.seriesId) {
        if (series.seasons == null) {
            onLoadSeriesInfo(series.seriesId)
        }
    }

    var watchedEpisodeIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var watchedRefreshTrigger by remember { mutableStateOf(0) }
    var initialEpisodeIndex by remember { mutableStateOf<Int?>(null) }

    // EpisodesList always auto-focuses a card on open (pre-selection by design), and that card's
    // own OK handler plays immediately - so an OK echo from the season-tile click that opened this
    // screen (double-OK repro, Chris 03.11.2026) started playback nobody asked for. Swallowing
    // OK/Enter here until an explicit D-Pad press is seen keeps that pre-selection (highlight) but
    // makes it non-reactive until the user actually navigates - no timer, no focus change needed.
    var hasExplicitNavigation by remember(seasonNumber) { mutableStateOf(false) }

    LaunchedEffect(series.seriesId, watchedRefreshTrigger) {
        watchedEpisodeIds = watchProgressService.getWatchedEpisodeIds(series.seriesId)
    }

    // Smart resume is season-scoped: only a target inside THIS season moves the initial focus.
    // A target in another season is ignored here; SeriesDetail's season tile decides which season
    // opens (the tile itself already carries the watched count).
    LaunchedEffect(series.seriesId, seasonNumber, series.seasons) {
        if (season == null) {
            initialEpisodeIndex = null
            return@LaunchedEffect
        }
        val target = watchProgressService.getSmartResumeTarget(series.seriesId, series)
        initialEpisodeIndex = if (target != null && target.seasonNumber == seasonNumber) {
            val episodes = season.episodes.orEmpty()
            episodes.indexOfFirst {
                it.id == target.episodeId ||
                    (it.episodeNum ?: it.id) == target.episodeNumber
            }.takeIf { it >= 0 }
        } else {
            null
        }
    }

    val backdropCandidates = ContentParser.seriesBackdropCandidates(series)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onBackButton(onBack)
            .testTag("season-screen")
    ) {
        val backdropPainter = rememberAsyncImagePainter(model = backdropCandidates.firstOrNull())
        Image(
            painter = backdropPainter,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.35f)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.7f),
                            Color.Black.copy(alpha = 0.95f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Text(
                text = series.name,
                color = DarkColors.textPrimary,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("season-screen-series-title")
            )
            Text(
                text = season?.name?.takeIf { it.isNotBlank() }?.let { formatSeasonLabel(it) }
                    ?: "Staffel $seasonNumber",
                color = DarkColors.textSecondary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .padding(top = Dimensions.spacingXS, bottom = Dimensions.spacingLG)
                    .testTag("season-screen-title")
            )

            val episodes = season?.episodes.orEmpty()
            if (episodes.isEmpty()) {
                val emptyFocusRequester = remember { FocusRequester() }
                LaunchedEffect(seasonNumber) {
                    val focused = emptyFocusRequester.safeRequestFocus(
                        "SeasonScreen",
                        "EMPTY_STATE"
                    )
                    if (!focused) {
                        emptyFocusRequester.safeRequestFocus("SeasonScreen", "EMPTY_STATE_FALLBACK")
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(emptyFocusRequester)
                        .focusable(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Für diese Staffel sind derzeit keine Episoden verfügbar.",
                        color = DarkColors.textSecondary,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.testTag("season-screen-empty")
                    )
                }
            } else {
                EpisodesList(
                    params = EpisodesListParams(
                        episodes = episodes,
                        season = season!!,
                        series = series,
                        watchProgressService = watchProgressService,
                        watchedEpisodeIds = watchedEpisodeIds,
                        initialEpisodeIndex = initialEpisodeIndex,
                        fallbackImageUrl = backdropCandidates.firstOrNull()
                    ),
                    onEpisodeClick = onEpisodeClick,
                    onWatchedStateChanged = { watchedRefreshTrigger++ },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onPreviewKeyEvent { event ->
                            when (event.key) {
                                Key.DirectionCenter, Key.Enter -> !hasExplicitNavigation
                                Key.DirectionLeft, Key.DirectionRight,
                                Key.DirectionUp, Key.DirectionDown -> {
                                    if (event.type == KeyEventType.KeyDown) {
                                        hasExplicitNavigation = true
                                    }
                                    false
                                }
                                else -> false
                            }
                        }
                )
            }

            Spacer(modifier = Modifier.height(Dimensions.spacingXL))
        }
    }
}
