package com.flintr.tv.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import com.flintr.tv.data.local.SmartResumeTarget
import com.flintr.tv.data.local.WatchProgressService
import com.flintr.tv.data.model.Episode
import com.flintr.tv.data.model.Season
import com.flintr.tv.data.model.Series
import com.flintr.tv.ui.components.PopupMenu
import com.flintr.tv.ui.components.WatchedBadge
import com.flintr.tv.ui.components.formatRemainingLabel
import com.flintr.tv.ui.components.detail.formatDate
import com.flintr.tv.ui.theme.CardTitleStyle
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.appColors
import com.flintr.tv.ui.theme.focusRingColor
import com.flintr.tv.ui.utils.onClick
import com.flintr.tv.ui.utils.onDpadDirection
import com.flintr.tv.ui.utils.onBackButton
import com.flintr.tv.ui.utils.onLongPress
import com.flintr.tv.ui.utils.InteractionUtils.tvFocusable
import com.flintr.tv.ui.utils.safeRequestFocus
import com.flintr.tv.utils.ContentParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import com.flintr.tv.utils.error.reportHandled

// Navigation direction enum
private enum class NavigationDirection {
    LEFT, RIGHT
}

// Streamflix-nahe, thumbnail-dominante Karte: ~3-4 Karten pro Screen-Breite statt der
// bisherigen 5 (300dp). 16:9 statt der alten 180dp-Fixhöhe, damit das Poster mitwächst.
private val EPISODE_CARD_WIDTH = 420.dp
private const val EPISODE_THUMBNAIL_ASPECT_RATIO = 16f / 9f

// Data classes for EpisodeDetailCard
internal data class EpisodeCardState(
    val progressPercent: Float,
    val isCompleted: Boolean,
    val remainingMinutes: Int?,
    val isWatched: Boolean
)

private data class EpisodeCardConfig(
    val episode: Episode,
    val season: Season,
    val series: Series,
    val watchProgressService: WatchProgressService,
    val watchedEpisodeIds: Set<Int>,
    val fallbackImageUrl: String? = null
)

private data class EpisodeCardCallbacks(
    val onClick: () -> Unit,
    val onFocusChanged: (Boolean) -> Unit,
    val onNavigateLeft: () -> Unit,
    val onNavigateRight: () -> Unit,
    val onWatchedStateChanged: () -> Unit
)

private data class SeasonNavigationParams(
    val seasons: List<Season>,
    val selectedSeasonIndex: Int,
    val selectedSeason: Season?,
    val series: Series,
    val watchProgressService: WatchProgressService,
    val watchedEpisodeIds: Set<Int>,
    val initialEpisodeIndex: Int?,
    val smartResumeSeasonIndex: Int?,
    val fallbackImageUrl: String? = null
)

internal data class EpisodesListParams(
    val episodes: List<Episode>,
    val season: Season,
    val series: Series,
    val watchProgressService: WatchProgressService,
    val watchedEpisodeIds: Set<Int>,
    val initialEpisodeIndex: Int?,
    val fallbackImageUrl: String? = null
)

// internal (not private): exercised directly by SeriesEpisodesFocusRecoveryTest, which needs
// EpisodeFocusState.focusRequesters to verify the Order 3 initial-focus-deadlock fix.
internal data class EpisodeFocusState(
    val focusedEpisodeIndex: androidx.compose.runtime.MutableState<Int>,
    val listState: androidx.compose.foundation.lazy.LazyListState,
    val focusRequesters: List<FocusRequester>
)

/**
 * Order 3 root-fix, extracted so it can be tested in isolation from the "scroll to currently
 * focused episode" LaunchedEffect below - that effect targets the same LazyListState and, given
 * enough time, can mask a regression here by scrolling the target into view on its own.
 *
 * Chris' non-fatal report ("FocusRequester is not initialized" at this call site) followed by a
 * fully unresponsive screen (no D-Pad, no Back) traced back to: a LazyRow only composes items
 * near the viewport, so requesting focus on a far-off initialEpisodeIndex (resuming deep into a
 * season) targeted a FocusRequester that was never attached, exhausting every safeRequestFocus()
 * retry and leaving the screen with NOTHING focused - which stops all key-event dispatch, since
 * key events route to the focused node and there wasn't one.
 *
 * scrollToItem (instant, not animate) guarantees the target is composed - and its FocusRequester
 * attached - before it's ever used; the fallback guarantees some element ends up focused even if
 * that somehow still fails.
 */
internal suspend fun requestInitialEpisodeFocus(
    listState: androidx.compose.foundation.lazy.LazyListState,
    focusRequesters: List<FocusRequester>,
    targetIndex: Int
) {
    listState.scrollToItem(targetIndex)

    // safeRequestFocus already retries internally until the target node attaches;
    // stacking another manual retry here would just double the wait on failure.
    val focused = focusRequesters.getOrNull(targetIndex)?.safeRequestFocus(
        "SeriesEpisodesScreen",
        "EPISODE_$targetIndex"
    ) ?: false

    // Fallback: never leave the screen with no focused element at all. If the target somehow
    // still couldn't be focused, fall back to the first episode - re-scrolling to it first
    // since scrollToItem(targetIndex) may have decomposed it in the meantime.
    if (!focused && targetIndex != 0) {
        listState.scrollToItem(0)
        focusRequesters.getOrNull(0)?.safeRequestFocus("SeriesEpisodesScreen", "EPISODE_0_FALLBACK")
    }
}

// Order GF6: fast D-Pad bursts used to restart the follow animation on every index change, so the
// MutatePriority mutex cancelled the previous animateScrollToItem before it reached anything and
// the viewport stayed at the first visible card while focusedEpisodeIndex raced ahead (15x RIGHT
// -> counter "50/64", viewport still at episode 4/5). Bundle the burst and jump once when it ends.
internal const val EPISODE_FOCUS_SCROLL_DEBOUNCE_MS = 120L

/**
 * Collects navigation-driven episode index changes with trailing-edge debounce: every new index
 * cancels the pending delay via collectLatest, so only the last index after a short input pause is
 * acted on. This is deliberately separate from [requestInitialEpisodeFocus]: the initial focus
 * path still scrolls immediately and is never delayed by this effect.
 *
 * The callbacks are injectable so the burst/strategy contract can be tested without mocking
 * LazyListState.
 */
internal suspend fun followEpisodeFocusBurst(
    indices: kotlinx.coroutines.flow.Flow<Int>,
    debounceMillis: Long = EPISODE_FOCUS_SCROLL_DEBOUNCE_MS,
    isVisible: (Int) -> Boolean,
    scrollTo: suspend (Int) -> Unit,
    animateScrollTo: suspend (Int) -> Unit
) {
    indices.collectLatest { index ->
        delay(debounceMillis)
        // Off-screen target: the item is not composed yet, so an animation would have nothing to
        // animate towards. scrollToItem composes it immediately; EpisodeCard's own
        // LaunchedEffect(isFocused) then requests focus (no duplicate request here).
        // Visible target: a single tap close by can move smoothly.
        if (isVisible(index)) {
            animateScrollTo(index)
        } else {
            scrollTo(index)
        }
    }
}

/**
 * Manages episode focus state and handles initial focus setup
 */
@Composable
internal fun rememberEpisodeFocus(
    episodes: List<Episode>,
    initialEpisodeIndex: Int?
): EpisodeFocusState {
    val focusedEpisodeIndex = remember(episodes) { 
        mutableStateOf(initialEpisodeIndex?.coerceIn(0, episodes.size - 1) ?: 0) 
    }
    val listState = rememberLazyListState()
    // Keyed by episodes (like focusedEpisodeIndex above): without this key, a season switch that
    // reuses this same composable slot (EpisodesList's call site in SeasonNavigation is not keyed
    // by season, see SeriesEpisodesScreen.kt) would keep the previous season's stale, wrong-sized
    // FocusRequester list - see c2-report.md.
    val focusRequesters = remember(episodes) { episodes.map { FocusRequester() } }
    
    // Update focusedEpisodeIndex when initialEpisodeIndex changes
    LaunchedEffect(initialEpisodeIndex) {
        if (initialEpisodeIndex != null && initialEpisodeIndex in 0 until episodes.size) {
            focusedEpisodeIndex.value = initialEpisodeIndex
        }
    }
    
    // Set initial focus on target episode when list is displayed
    LaunchedEffect(episodes.size, initialEpisodeIndex) {
        if (episodes.isEmpty()) return@LaunchedEffect

        val targetIndex = initialEpisodeIndex?.coerceIn(0, episodes.size - 1) ?: 0
        requestInitialEpisodeFocus(listState, focusRequesters, targetIndex)
    }

    // Navigation follow only - initial focus belongs exclusively to requestInitialEpisodeFocus
    // above. Keyed the same as that effect and drop(1)-ing the first emission on every restart, so
    // this only reacts to LATER, genuine navigation-driven focusedEpisodeIndex changes (e.g. D-Pad
    // left/right updating focusState.focusedEpisodeIndex.value directly).
    //
    // Without the drop(1): on a season switch (or any initialEpisodeIndex change), focusedEpisodeIndex
    // is freshly remembered already holding the resume target, so this effect's very first emission
    // duplicates requestInitialEpisodeFocus's own scrollToItem(targetIndex) call above - both then
    // race scroll(MutatePriority.Default) on the SAME listState. The second one to acquire the
    // mutex cancels the other mid-flight; if that's requestInitialEpisodeFocus, its scrollToItem
    // throws CancellationException before it ever reaches focusRequesters.getOrNull(targetIndex)
    // ?.safeRequestFocus(...) - so nothing ends up focused. Reproduced deterministically via
    // SeriesEpisodesFocusRecoveryTest.seasonSwitchAfterInitialCompositionLeavesFocusRequestersStaleForTheNewSeason;
    // see fix2-report.md.
    //
    // Order GF6: the follow is trailing-edge debounced (120ms). Rapid D-Pad input therefore produces
    // exactly ONE action after the burst instead of one cancelled animation per index. A target
    // outside the composed viewport is jumped to instantly (scrollToItem, which composes it and
    // lets EpisodeCard's LaunchedEffect(isFocused) attach focus); a target already in view is
    // animated for a smooth single step. The debounce only delays this navigation follow: the
    // initial requestInitialEpisodeFocus path above remains un-delayed and unchanged.
    LaunchedEffect(episodes.size, initialEpisodeIndex) {
        followEpisodeFocusBurst(
            indices = snapshotFlow { focusedEpisodeIndex.value }
                .drop(1)
                .filter { it in 0 until episodes.size },
            isVisible = { index ->
                listState.layoutInfo.visibleItemsInfo.any { it.index == index }
            },
            scrollTo = { index -> listState.scrollToItem(index) },
            animateScrollTo = { index -> listState.animateScrollToItem(index) }
        )
    }
    
    return EpisodeFocusState(
        focusedEpisodeIndex = focusedEpisodeIndex,
        listState = listState,
        focusRequesters = focusRequesters
    )
}

/**
 * Finds the episode index within a season based on target information
 */
private fun findEpisodeIndex(
    episodes: List<Episode>,
    targetEpisodeId: Int?,
    targetEpisodeNumber: Int?
): Int {
    return episodes.indexOfFirst { 
        it.id == targetEpisodeId || 
        (it.episodeNum ?: it.id) == targetEpisodeNumber 
    }
}

/**
 * Navigates to the target season and episode based on smart resume target
 */
private fun navigateToTargetSeason(
    sortedSeasons: List<Season>,
    targetSeasonNumber: Int,
    targetEpisodeId: Int?,
    targetEpisodeNumber: Int?,
    onSeasonSelected: (Int) -> Unit,
    onEpisodeIndexSet: (Int?) -> Unit,
    onSmartResumeSeasonIndexSet: (Int?) -> Unit
) {
    val targetSeasonIndex = sortedSeasons.indexOfFirst { it.seasonNumber == targetSeasonNumber }
    if (targetSeasonIndex < 0) return
    
    onSeasonSelected(targetSeasonIndex)
    onSmartResumeSeasonIndexSet(targetSeasonIndex)
    
    val targetSeason = sortedSeasons[targetSeasonIndex]
    val episodes = targetSeason.episodes ?: emptyList()
    val episodeIndex = findEpisodeIndex(
        episodes = episodes,
        targetEpisodeId = targetEpisodeId,
        targetEpisodeNumber = targetEpisodeNumber
    )
    
    if (episodeIndex >= 0) {
        onEpisodeIndexSet(episodeIndex)
    }
}

/**
 * Handles smart resume logic: finds and navigates to the target episode
 */
@Composable
private fun HandleSmartResume(
    displaySeries: Series,
    sortedSeasons: List<Season>,
    watchProgressService: WatchProgressService,
    onSeasonSelected: (Int) -> Unit,
    onEpisodeIndexSet: (Int?) -> Unit,
    onSmartResumeSeasonIndexSet: (Int?) -> Unit,
    onResumeTargetLoaded: (SmartResumeTarget?) -> Unit = {}
) {
    LaunchedEffect(displaySeries.seasons) {
        if (displaySeries.seasons != null && sortedSeasons.isNotEmpty()) {
            val target = watchProgressService.getSmartResumeTarget(displaySeries.seriesId, displaySeries)
            onResumeTargetLoaded(target)

            if (target != null) {
                navigateToTargetSeason(
                    sortedSeasons = sortedSeasons,
                    targetSeasonNumber = target.seasonNumber,
                    targetEpisodeId = target.episodeId,
                    targetEpisodeNumber = target.episodeNumber,
                    onSeasonSelected = onSeasonSelected,
                    onEpisodeIndexSet = onEpisodeIndexSet,
                    onSmartResumeSeasonIndexSet = onSmartResumeSeasonIndexSet
                )
            }
        }
    }
}

/**
 * Merkt sich den aktuell ladbaren Bild-Kandidaten und schaltet bei
 * Ladefehlern auf den nächsten weiter.
 */
@Composable
private fun rememberImageCandidatePainter(
    imageCandidates: List<String>
): coil.compose.AsyncImagePainter? {
    var candidateIndex by remember(imageCandidates) { mutableStateOf(0) }
    val currentUrl = imageCandidates.getOrNull(candidateIndex) ?: return null

    val painter = rememberAsyncImagePainter(model = currentUrl)
    val imageState = painter.state

    LaunchedEffect(imageState) {
        if (imageState is AsyncImagePainter.State.Error && candidateIndex < imageCandidates.size - 1) {
            candidateIndex++
        }
    }

    return painter
}

/**
 * Renders the background image with overlay for the series screen
 */
@Composable
private fun SeriesBackground(imageCandidates: List<String>) {
    val painter = rememberImageCandidatePainter(imageCandidates)

    if (painter != null && painter.state !is AsyncImagePainter.State.Error) {
        Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.4f),
            contentScale = ContentScale.Crop
        )
    } else {
        // Fallback background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        )
    }

    // Semi-transparent overlay for better readability
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.7f)
                    )
                )
            )
    )
}

/**
 * Scrollt die Ansicht automatisch zur ausgewählten Staffel, sobald die Maße bekannt sind.
 */
@Composable
private fun AutoScrollToSeason(
    scrollState: androidx.compose.foundation.ScrollState,
    selectedSeasonIndex: Int,
    headerHeight: Int,
    seasonHeight: Int
) {
    LaunchedEffect(selectedSeasonIndex, headerHeight, seasonHeight) {
        if (headerHeight > 0 && seasonHeight > 0) {
            // Scroll to show the selected season
            kotlinx.coroutines.delay(100)
            val targetPosition = headerHeight + (selectedSeasonIndex * seasonHeight)
            scrollState.animateScrollTo(targetPosition.coerceAtMost(scrollState.maxValue))
        }
    }
}

/**
 * Serien-Header mit Fokus-Navigation nach unten (Staffel-Leiste).
 */
@Composable
private fun SeriesHeaderWithNav(
    series: Series,
    headerFocusRequester: FocusRequester,
    onNavigateToSeasonButton: () -> Unit,
    onHeaderHeightChanged: (Int) -> Unit
) {
    SeriesHeader(
        series = series,
        headerFocusRequester = headerFocusRequester,
        onNavigateDown = onNavigateToSeasonButton,
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                onHeaderHeightChanged(coordinates.size.height)
            }
    )
}

@Composable
fun SeriesEpisodesScreen(
    series: Series,
    mainViewModel: com.flintr.tv.viewmodel.MainViewModel,
    watchProgressService: WatchProgressService,
    onBack: () -> Unit,
    onEpisodeClick: (Episode) -> Unit
) {
    // Load series info if seasons are not loaded
    LaunchedEffect(series.seriesId) {
        if (series.seasons == null) {
            mainViewModel.loadSeriesInfo(series.seriesId)
        }
    }
    
    val updatedSeries by mainViewModel.selectedSeries.collectAsState()
    val displaySeries = updatedSeries ?: series
    
    // Sort seasons by seasonNumber
    val sortedSeasons = displaySeries.seasons?.sortedBy { it.seasonNumber } ?: emptyList()
    
    var selectedSeasonIndex by remember { mutableStateOf(0) }
    val selectedSeason = sortedSeasons.getOrNull(selectedSeasonIndex)
    
    // Batch-load watched episode IDs for the series
    var watchedEpisodeIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    
    // Smart Resume: Get target episode for navigation
    var initialEpisodeIndex by remember { mutableStateOf<Int?>(null) }
    var smartResumeSeasonIndex by remember { mutableStateOf<Int?>(null) }

    // Increments after long-press menu actions to reload watched state
    var watchedRefreshTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(series.seriesId, watchedRefreshTrigger) {
        watchedEpisodeIds = watchProgressService.getWatchedEpisodeIds(series.seriesId)
    }

    // Load smart resume target when series info is available
    HandleSmartResume(
        displaySeries = displaySeries,
        sortedSeasons = sortedSeasons,
        watchProgressService = watchProgressService,
        onSeasonSelected = { selectedSeasonIndex = it },
        onEpisodeIndexSet = { initialEpisodeIndex = it },
        onSmartResumeSeasonIndexSet = { smartResumeSeasonIndex = it }
    )

    // Background image candidates: backdrops -> first episode image -> cover
    val backdropCandidates = ContentParser.seriesBackdropCandidates(displaySeries)
    val backdropImageUrl = backdropCandidates.firstOrNull()
    
    // Scroll state for auto-scrolling to selected season
    val scrollState = rememberScrollState()

    // Dynamic measurements for header and season heights
    var headerHeight by remember { mutableStateOf(0) }
    var seasonHeight by remember { mutableStateOf(0) }

    // Auto-scroll when season changes
    AutoScrollToSeason(
        scrollState = scrollState,
        selectedSeasonIndex = selectedSeasonIndex,
        headerHeight = headerHeight,
        seasonHeight = seasonHeight
    )

    // FocusRequester for header to enable navigation from season buttons
    val headerFocusRequester = remember { FocusRequester() }
    // Callback to navigate to selected season button
    var navigateToSeasonButton by remember { mutableStateOf<(() -> Unit)?>(null) }

    val handleSeasonSelected = { index: Int ->
        selectedSeasonIndex = index
        // Reset initialEpisodeIndex when season changes manually (not via smart resume)
        if (index != smartResumeSeasonIndex) {
            initialEpisodeIndex = null
        }
    }
    val handleSeasonPositioned = { coordinates: androidx.compose.ui.layout.LayoutCoordinates ->
        if (seasonHeight == 0) {
            // Only set once, use first season as reference
            seasonHeight = coordinates.size.height
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onBackButton(onBack)
    ) {
        SeriesBackground(imageCandidates = backdropCandidates)

        // Content - Scrollable
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(24.dp)
        ) {
            // Series Header
            SeriesHeaderWithNav(
                series = displaySeries,
                headerFocusRequester = headerFocusRequester,
                onNavigateToSeasonButton = { navigateToSeasonButton?.invoke() },
                onHeaderHeightChanged = { headerHeight = it }
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Season Navigation
            SeasonNavigation(
                params = SeasonNavigationParams(
                    seasons = sortedSeasons,
                    selectedSeasonIndex = selectedSeasonIndex,
                    selectedSeason = selectedSeason,
                    series = displaySeries,
                    watchProgressService = watchProgressService,
                    watchedEpisodeIds = watchedEpisodeIds,
                    initialEpisodeIndex = initialEpisodeIndex,
                    smartResumeSeasonIndex = smartResumeSeasonIndex,
                    fallbackImageUrl = backdropImageUrl
                ),
                upFocusRequester = headerFocusRequester,
                onNavigateToSeasonButton = { callback ->
                    navigateToSeasonButton = callback
                },
                onSeasonSelected = handleSeasonSelected,
                onEpisodeClick = onEpisodeClick,
                onWatchedStateChanged = { watchedRefreshTrigger++ },
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned(handleSeasonPositioned)
            )
        }
    }
}

@Composable
private fun SeriesHeader(
    series: Series,
    headerFocusRequester: FocusRequester,
    onNavigateDown: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rating = ContentParser.getRating(series)
    val year = series.releaseDate?.take(4) // Extract year from date
    val seasonCount = series.seasons?.size ?: 0
    val seasonText = if (seasonCount == 1) "1 Staffel" else "$seasonCount Staffeln"
    val colors = appColors()
    var isFocused by remember { mutableStateOf(false) }
    
    // Border color with animation for focus feedback
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) colors.accentColor else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "borderColor"
    )
    
    Column(
        modifier = modifier
            .background(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(8.dp)
            )
            .then(
                if (borderColor != Color.Transparent) {
                    Modifier.border(
                        width = 2.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(8.dp)
                    )
                } else {
                    Modifier
                }
            )
            .focusRequester(headerFocusRequester)
            .tvFocusable { isFocused = it }
            .onDpadDirection(
                onDown = {
                    onNavigateDown()
                    true
                }
            )
            .padding(16.dp)
    ) {
        // Title
        Text(
            text = series.name,
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Row 1: Rating, Year, Season Count, Categories
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rating
            if (rating != null) {
                Box(
                    modifier = Modifier
                        .background(
                            color = Color.Gray.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = rating,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // Year
            OptionalMetadataText(
                content = year,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
            
            // Season Count
            Text(
                text = seasonText,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
            
            // Categories/Genre
            OptionalMetadataText(
                content = series.genre,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
        }
        
        // Row 2: Cast
        OptionalMetadataText(
            content = series.cast,
            prefix = "Besetzung: ",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        // Row 3: Director
        OptionalMetadataText(
            content = series.director,
            prefix = "Regisseur: ",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        // Row 4+: Plot/Description
        OptionalMetadataText(
            content = series.plot,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.9f),
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun OptionalMetadataText(
    content: String?,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    prefix: String = "",
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    if (!content.isNullOrEmpty()) {
        Text(
            text = if (prefix.isNotEmpty()) "$prefix$content" else content,
            style = style,
            color = color,
            maxLines = maxLines,
            overflow = overflow,
            modifier = modifier
        )
    }
}

@Composable
private fun SeasonNavigation(
    params: SeasonNavigationParams,
    upFocusRequester: FocusRequester,
    onNavigateToSeasonButton: ((() -> Unit)) -> Unit,
    onSeasonSelected: (Int) -> Unit,
    onEpisodeClick: (Episode) -> Unit,
    onWatchedStateChanged: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    // Store FocusRequesters for season buttons to enable navigation from header
    val seasonButtonFocusRequesters = remember(params.seasons.size) {
        List(params.seasons.size) { FocusRequester() }
    }
    
    // Provide callback to navigate to selected season button
    LaunchedEffect(params.selectedSeasonIndex, seasonButtonFocusRequesters.size, params.seasons.size) {
        if (params.seasons.size > 1 && params.selectedSeasonIndex in 0 until seasonButtonFocusRequesters.size) {
            onNavigateToSeasonButton {
                coroutineScope.launch {
                    seasonButtonFocusRequesters[params.selectedSeasonIndex].safeRequestFocus(
                        "SeriesEpisodesScreen",
                        "SEASON_BUTTON_${params.selectedSeasonIndex}",
                        initialDelayMs = 0
                    )
                }
            }
        } else {
            // If there's only one season or no season buttons, clear the callback
            onNavigateToSeasonButton { }
        }
    }
    
    Column(
        modifier = modifier
    ) {
        // Season Selector - Horizontal list of season buttons
        if (params.seasons.size > 1) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .onDpadDirection(
                        onUp = {
                            // Navigate to resume button or header when pressing up from season buttons
                            coroutineScope.launch {
                                upFocusRequester.safeRequestFocus("SeriesEpisodesScreen", "ABOVE_SEASONS", initialDelayMs = 0)
                            }
                            true
                        }
                    )
            ) {
                itemsIndexed(
                    items = params.seasons,
                    key = { _, season -> season.seasonNumber }
                ) { seasonIndex, season ->
                    SeasonButton(
                        season = season,
                        isSelected = seasonIndex == params.selectedSeasonIndex,
                        focusRequester = seasonButtonFocusRequesters.getOrNull(seasonIndex),
                        onSelect = { onSeasonSelected(seasonIndex) },
                        // A smart-resume episode target is pending for this season - let
                        // EpisodesList/rememberEpisodeFocus own the focus instead of racing it
                        // (see c2-report.md H1); with no pending target, self-focus is unaffected.
                        suppressAutoFocus = seasonIndex == params.smartResumeSeasonIndex &&
                            params.initialEpisodeIndex != null
                    )
                }
            }
        }
        
        // Season Title
        if (params.selectedSeason != null) {
            Text(
                text = "Staffel ${params.selectedSeason.seasonNumber}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
        
        // Episodes List for selected season
        params.selectedSeason?.episodes?.let { episodes ->
            EpisodesList(
                params = EpisodesListParams(
                    episodes = episodes,
                    season = params.selectedSeason,
                    series = params.series,
                    watchProgressService = params.watchProgressService,
                    watchedEpisodeIds = params.watchedEpisodeIds,
                    initialEpisodeIndex = if (params.selectedSeasonIndex == params.smartResumeSeasonIndex) params.initialEpisodeIndex else null,
                    fallbackImageUrl = params.fallbackImageUrl
                ),
                onEpisodeClick = onEpisodeClick,
                onWatchedStateChanged = onWatchedStateChanged,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SeasonButton(
    season: Season,
    isSelected: Boolean,
    focusRequester: FocusRequester? = null,
    onSelect: () -> Unit,
    suppressAutoFocus: Boolean = false
) {
    val colors = appColors()
    var isFocused by remember { mutableStateOf(false) }
    
    // Background color with animation (like CategoryRow)
    // Selected: completely filled with selectionFill
    // Focused (but not selected): hover background
    val isHoveredOrFocused = isFocused && !isSelected
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSelected -> colors.selectionFill
            isHoveredOrFocused -> colors.categoryHoverBg
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 200),
        label = "backgroundColor"
    )

    // Border color with animation
    // Der Rahmen markiert ausschließlich den Fokus, nie die Selektion.
    val borderColor by animateColorAsState(
        targetValue = focusRingColor(isFocused = isFocused, onSelectionFill = isSelected),
        animationSpec = tween(durationMillis = 200),
        label = "borderColor"
    )
    
    // Text color: always white on dark background
    val textColor = Color.White
    
    val internalFocusRequester = remember { FocusRequester() }
    val actualFocusRequester = focusRequester ?: internalFocusRequester
    
    // Request focus when selected - unless a smart-resume episode focus is pending for this
    // season, in which case EpisodesList/rememberEpisodeFocus owns the focus (see c2-report.md
    // H1: this effect previously always won that race regardless of the episode list's own,
    // slower/retrying focus request).
    LaunchedEffect(isSelected, suppressAutoFocus) {
        if (isSelected && !suppressAutoFocus) {
            kotlinx.coroutines.delay(50)
            actualFocusRequester.safeRequestFocus(targetName = "SeasonButton", initialDelayMs = 0)
        }
    }
    
    Box(
        modifier = Modifier
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(8.dp)
            )
            .then(
                if (borderColor != Color.Transparent) {
                    Modifier.border(
                        width = 2.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(8.dp)
                    )
                } else {
                    Modifier
                }
            )
            .focusRequester(actualFocusRequester)
            .tvFocusable { isFocused = it }
            .onClick(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "Staffel ${season.seasonNumber}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = textColor
        )
    }
}

@Composable
internal fun EpisodesList(
    params: EpisodesListParams,
    onEpisodeClick: (Episode) -> Unit,
    onWatchedStateChanged: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusState = rememberEpisodeFocus(
        episodes = params.episodes,
        initialEpisodeIndex = params.initialEpisodeIndex
    )
    
    val focusedEpisodeIndex by focusState.focusedEpisodeIndex
    
    Column(
        modifier = modifier
    ) {
        // Episode indicator (e.g., "1/8")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = "${focusedEpisodeIndex + 1}/${params.episodes.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
        
        // Episodes horizontal list
        LazyRow(
            state = focusState.listState,
            horizontalArrangement = Arrangement.spacedBy(Dimensions.spacingLG),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(
                items = params.episodes,
                key = { _, episode -> episode.id }
            ) { episodeIndex, episode ->
                EpisodeDetailCard(
                    config = EpisodeCardConfig(
                        episode = episode,
                        season = params.season,
                        series = params.series,
                        watchProgressService = params.watchProgressService,
                        watchedEpisodeIds = params.watchedEpisodeIds,
                        fallbackImageUrl = params.fallbackImageUrl
                    ),
                    callbacks = EpisodeCardCallbacks(
                        onClick = { onEpisodeClick(episode) },
                        onFocusChanged = { isFocused ->
                            if (isFocused) {
                                focusState.focusedEpisodeIndex.value = episodeIndex
                            }
                        },
                        onNavigateLeft = {
                            focusState.focusedEpisodeIndex.value = updateEpisodeIndex(
                                currentIndex = focusedEpisodeIndex,
                                maxIndex = params.episodes.size - 1,
                                direction = NavigationDirection.LEFT
                            )
                        },
                        onNavigateRight = {
                            focusState.focusedEpisodeIndex.value = updateEpisodeIndex(
                                currentIndex = focusedEpisodeIndex,
                                maxIndex = params.episodes.size - 1,
                                direction = NavigationDirection.RIGHT
                            )
                        },
                        onWatchedStateChanged = onWatchedStateChanged
                    ),
                    isFocused = episodeIndex == focusedEpisodeIndex,
                    focusRequester = focusState.focusRequesters.getOrNull(episodeIndex)
                )
            }
        }
    }
}

/**
 * Extension function to conditionally apply shadow modifier for focused episode cards
 */
private fun Modifier.episodeCardShadow(
    isFocused: Boolean,
    elevation: Float,
    accentColor: Color
): Modifier {
    return if (isFocused) {
        this.shadow(
            elevation = elevation.dp,
            shape = RoundedCornerShape(Dimensions.listItemHoverBorderRadius),
            spotColor = accentColor.copy(alpha = Dimensions.categoryShadowAlpha),
            ambientColor = accentColor.copy(alpha = Dimensions.categoryShadowAlpha)
        )
    } else {
        this
    }
}

/**
 * Extension function to conditionally apply border modifier for episode cards
 */
private fun Modifier.conditionalBorder(
    borderColor: Color,
    shape: RoundedCornerShape
): Modifier {
    return if (borderColor != Color.Transparent) {
        this.border(
            width = Dimensions.listItemHoverBorderWidth,
            color = borderColor,
            shape = shape
        )
    } else {
        this
    }
}

@Composable
private fun EpisodeDetailCard(
    config: EpisodeCardConfig,
    callbacks: EpisodeCardCallbacks,
    isFocused: Boolean,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    var isFocusedState by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val menuCoroutineScope = rememberCoroutineScope()
    val colors = appColors()
    val cardFocusRequester = focusRequester ?: remember { FocusRequester() }

    val progressState = rememberEpisodeProgress(
        episode = config.episode,
        series = config.series,
        watchProgressService = config.watchProgressService,
        watchedEpisodeIds = config.watchedEpisodeIds
    )
    
    // Border color with animation
    // isFocused und isFocusedState sind zwei Fokusquellen desselben Elements:
    // die vom Aufrufer durchgereichte und die lokal beobachtete. Die Verodung
    // ist deshalb korrekt und zeichnet keine zwei Rahmen.
    val borderColor by animateColorAsState(
        targetValue = focusRingColor(
            isFocused = isFocused || isFocusedState,
            onSelectionFill = false
        ),
        animationSpec = tween(durationMillis = 200),
        label = "borderColor"
    )
    
    // Background color with animation
    val backgroundColor by animateColorAsState(
        targetValue = when {
            isFocused -> colors.accentColorHoverBg
            isFocusedState -> colors.categoryHoverBg
            else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        },
        animationSpec = tween(durationMillis = 200),
        label = "backgroundColor"
    )
    
    // Shadow elevation for focused items
    val shadowElevation by animateFloatAsState(
        targetValue = if (isFocused) 8f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "shadowElevation"
    )
    
    // Request focus when isFocused changes. Order GF6: no initial delay here - isFocused only turns
    // true after the handler updated focusedEpisodeIndex, and by then the item is composed (the
    // follow effect scrolls instantly when needed). The old default 50ms delay was pure lag.
    LaunchedEffect(isFocused) {
        if (isFocused) {
            cardFocusRequester.safeRequestFocus(targetName = "EpisodeCard", initialDelayMs = 0)
        }
    }
    
    val cardShape = RoundedCornerShape(Dimensions.listItemHoverBorderRadius)
    
    Box(
        modifier = modifier
            .width(EPISODE_CARD_WIDTH)
            .testTag("episode-card")
            .episodeCardShadow(
                isFocused = isFocused,
                elevation = shadowElevation,
                accentColor = colors.accentColor
            )
            .background(
                color = backgroundColor,
                shape = cardShape
            )
            .conditionalBorder(
                borderColor = borderColor,
                shape = cardShape
            )
            .focusRequester(cardFocusRequester)
            .onFocusChanged { focusState ->
                isFocusedState = focusState.isFocused
                callbacks.onFocusChanged(focusState.isFocused)
            }
            .focusable()
            .onDpadDirection(
                onLeft = {
                    callbacks.onNavigateLeft()
                    true
                },
                onRight = {
                    callbacks.onNavigateRight()
                    true
                }
            )
            .onLongPress(
                onLongPress = { showMenu = true },
                onNormalClick = callbacks.onClick
            )
            .padding(12.dp)
    ) {
        EpisodeCardContent(
            episode = config.episode,
            seasonNumber = config.season.seasonNumber,
            seriesName = config.series.name,
            progressState = progressState,
            fallbackImageUrl = config.fallbackImageUrl
        )
    }

    PopupMenu(
        isOpen = showMenu,
        menuOptions = SeriesEpisodeMenuLogic.buildEpisodeMenuOptions(progressState.isWatched),
        onDismiss = { showMenu = false },
        onOptionSelected = { index ->
            val action = SeriesEpisodeMenuLogic.menuActionForOption(index, progressState.isWatched)
            if (action != null) {
                menuCoroutineScope.launch {
                    executeEpisodeMenuAction(action, config)
                    callbacks.onWatchedStateChanged()
                }
            }
        }
    )
}


/**
 * Streamflix-nahe, thumbnail-dominante Karte (`item_episode_tv`): großes 16:9-Poster, watched
 * ribbon, progress bar, darunter zentriert Titel + eine Meta-Zeile "SxE • Dauer • Datum".
 * Deliberately WITHOUT an overview: Xtream's get_series_info does not deliver an episode plot
 * (FINDINGS_DETAIL_SCREENS.tmp.md), so there is nothing to show and no invented fallback text -
 * the title's existing 2-line clamp absorbs the space instead.
 *
 * Extracted from [EpisodeDetailCard] so the card rendering is testable without focus, menu and
 * watch-progress side effects.
 */
@Composable
internal fun EpisodeCardContent(
    episode: Episode,
    seasonNumber: Int,
    seriesName: String,
    progressState: EpisodeCardState,
    fallbackImageUrl: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        EpisodeThumbnail(
            imageCandidates = ContentParser.imageCandidates(
                episode.info?.movieImage,
                fallbackImageUrl
            ),
            showWatchedBadge = progressState.isWatched,
            progressPercent = if (progressState.isCompleted) 0f else progressState.progressPercent,
            remainingMinutes = progressState.remainingMinutes
        )

        val episodeNum = episode.episodeNum ?: episode.id
        val episodeCode = "S${seasonNumber}E$episodeNum"
        val episodeName = ContentParser.extractEpisodeName(episode.title, seriesName)

        Text(
            text = episodeName ?: "Episode $episodeNum",
            // Einheitlich mit Movie/Series/Recommendation-Karten (siehe CardTitleStyle).
            fontSize = CardTitleStyle.fontSize,
            fontWeight = CardTitleStyle.fontWeight,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(bottom = 2.dp)
                .testTag("episode-card-title")
        )

        val duration = episode.info?.duration?.let { formatDuration(it) }
        val airDate = (episode.info?.airDate ?: episode.info?.releaseDate)
            ?.takeIf { it.isNotBlank() }
            ?.let { formatDate(it) }
        val meta = listOfNotNull(episodeCode, duration, airDate).joinToString(" • ")
        Text(
            text = meta,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("episode-card-info")
        )
    }
}

/**
 * Führt eine Aktion des Episoden-Long-Press-Menüs aus.
 */
private suspend fun executeEpisodeMenuAction(
    action: EpisodeMenuAction,
    config: EpisodeCardConfig
) {
    when (action) {
        EpisodeMenuAction.MARK_WATCHED -> config.watchProgressService.setSeriesEpisodeWatched(
            seriesId = config.series.seriesId,
            episodeId = config.episode.id,
            watched = true
        )
        EpisodeMenuAction.MARK_UNWATCHED -> config.watchProgressService.setSeriesEpisodeWatched(
            seriesId = config.series.seriesId,
            episodeId = config.episode.id,
            watched = false
        )
        EpisodeMenuAction.MARK_PREVIOUS_WATCHED -> {
            val previousIds = SeriesEpisodeMenuLogic.previousEpisodeIds(
                seasons = config.series.seasons ?: emptyList(),
                targetSeasonNumber = config.season.seasonNumber,
                targetEpisodeId = config.episode.id,
                alreadyWatchedIds = config.watchedEpisodeIds
            )
            config.watchProgressService.markSeriesEpisodesAsWatched(
                seriesId = config.series.seriesId,
                episodeIds = previousIds
            )
        }
    }
}

@Composable
private fun rememberEpisodeProgress(
    episode: Episode,
    series: Series,
    watchProgressService: WatchProgressService,
    watchedEpisodeIds: Set<Int>
): EpisodeCardState {
    var progress by remember { mutableStateOf<Long?>(null) }
    var duration by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(episode.id, watchedEpisodeIds) {
        progress = watchProgressService.getProgress(
            contentType = "series",
            streamId = null,
            seriesId = series.seriesId,
            episodeId = episode.id
        )
        
        // Parse duration from episode.info?.duration
        episode.info?.duration?.let { durationStr ->
            // Duration is typically in seconds or "HH:MM:SS" format
            duration = try {
                durationStr.toLongOrNull()?.times(1000) // Convert seconds to milliseconds
                    ?: parseDurationString(durationStr)?.times(1000)
            } catch (e: Exception) {
                reportHandled("SeriesEpisodesScreen.rememberEpisodeProgress", e)
                null
            }
        }
    }
    
    // Check if episode is watched (only from batch-loaded set of fully watched episodes)
    val isWatched = episode.id in watchedEpisodeIds
    
    // Calculate progress percentage
    val progressPercent = if (progress != null && duration != null && duration!! > 0) {
        (progress!!.toFloat() / duration!!.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    
    // If episode is watched, treat it as completed regardless of progress
    val isCompleted = isWatched || progressPercent >= 0.9f
    val remainingMinutes = if (progress != null && duration != null && !isCompleted) {
        ((duration!! - progress!!) / 60000).toInt()
    } else {
        null
    }
    
    return EpisodeCardState(
        progressPercent = progressPercent,
        isCompleted = isCompleted,
        remainingMinutes = remainingMinutes,
        isWatched = isWatched
    )
}

@Composable
internal fun EpisodeThumbnail(
    imageCandidates: List<String>,
    showWatchedBadge: Boolean = false,
    progressPercent: Float = 0f,
    remainingMinutes: Int? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(EPISODE_THUMBNAIL_ASPECT_RATIO)
            .padding(bottom = 8.dp)
    ) {
        val painter = rememberImageCandidatePainter(imageCandidates)
        if (painter != null && painter.state !is AsyncImagePainter.State.Error) {
            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        
        // Watched badge overlay - centered on image
        if (showWatchedBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(8.dp)
                    .testTag("episode-watched-ribbon")
            ) {
                WatchedBadge()
            }
        }

        // Progress overlay at the bottom of the image (Netflix-style)
        if (progressPercent > 0f) {
            val colors = appColors()
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 8.dp)
                    .testTag("episode-thumbnail-progress"),
                horizontalAlignment = Alignment.End
            ) {
                remainingMinutes?.let { minutes ->
                    Box(
                        modifier = Modifier
                            .testTag("episode-thumbnail-remaining")
                            .padding(bottom = 4.dp)
                            .background(
                                color = Color.Black.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = formatRemainingLabel(minutes),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.3f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressPercent.coerceIn(0f, 1f))
                            .height(4.dp)
                            .background(colors.accentColor)
                    )
                }
            }
        }
    }
}

/**
 * Update episode index based on navigation direction
 */
private fun updateEpisodeIndex(
    currentIndex: Int,
    maxIndex: Int,
    direction: NavigationDirection
): Int {
    return when (direction) {
        NavigationDirection.LEFT -> (currentIndex - 1).coerceAtLeast(0)
        NavigationDirection.RIGHT -> (currentIndex + 1).coerceAtMost(maxIndex)
    }
}

/**
 * Parse duration string in "HH:MM:SS" format to seconds
 */
private fun parseDurationString(durationStr: String): Long? {
    return try {
        val parts = durationStr.split(":")
        when (parts.size) {
            3 -> { // HH:MM:SS
                val hours = parts[0].toLongOrNull() ?: return null
                val minutes = parts[1].toLongOrNull() ?: return null
                val seconds = parts[2].toLongOrNull() ?: return null
                hours * 3600 + minutes * 60 + seconds
            }
            2 -> { // MM:SS
                val minutes = parts[0].toLongOrNull() ?: return null
                val seconds = parts[1].toLongOrNull() ?: return null
                minutes * 60 + seconds
            }
            else -> durationStr.toLongOrNull()
        }
    } catch (e: Exception) {
        reportHandled("SeriesEpisodesScreen.parseDurationString", e)
        null
    }
}

/**
 * Format duration string for display
 */
private fun formatDuration(durationStr: String): String {
    val seconds = parseDurationString(durationStr)
    return if (seconds != null) {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "${seconds}s"
        }
    } else {
        durationStr
    }
}
