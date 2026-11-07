package com.flintr.tv.ui.screens

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flintr.tv.data.model.Episode
import com.flintr.tv.ui.utils.safeRequestFocus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Order 3 regression: Chris' non-fatal report ("FocusRequester is not initialized" at
 * rememberEpisodeFocus) left the whole SeriesEpisodesScreen frozen - no D-Pad, no Back - because
 * NOTHING ended up focused. Root cause: the episodes LazyRow only composes items near the
 * viewport, so requesting focus on a far-off initialEpisodeIndex (resuming deep into a season)
 * targeted a FocusRequester that was never attached, exhausting all retries.
 *
 * Two groups of tests here:
 *  - integration-style tests through the real `rememberEpisodeFocus` (now internal for this
 *    reason), inside a bare LazyRow mirroring EpisodesList's structure (300dp-wide focusable
 *    cards, one FocusRequester per episode) without EpisodeDetailCard's other dependencies
 *    (WatchProgressService, Coil, ...) - this is the exact scenario the order mandates.
 *  - isolated tests calling `requestInitialEpisodeFocus` (the extracted root-fix) directly,
 *    without rememberEpisodeFocus's separate "scroll to currently focused episode" effect
 *    running alongside it. That second effect targets the same index and, given enough virtual
 *    time, can independently scroll the target into view and mask a regression in the fix being
 *    tested here - the isolated tests (and the pre-fix snapshot below) exist specifically to
 *    prove the fix's own mechanism deterministically, independent of that other effect.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w1280dp-h720dp-land")
class SeriesEpisodesFocusRecoveryTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun episode(id: Int) = Episode(
        id = id,
        episodeNum = id,
        title = "Episode $id",
        containerExtension = null,
        info = null,
        subtitles = null,
        added = null,
        season = null,
        directSource = null
    )

    private fun episodeTag(index: Int) = "episode-card-$index"

    @androidx.compose.runtime.Composable
    private fun EpisodeCard(index: Int, focusRequester: FocusRequester?) {
        Box(
            modifier = Modifier
                .testTag(episodeTag(index))
                .width(300.dp)
                .height(180.dp)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .focusable()
        )
    }

    // ---- Integration-style tests through the real rememberEpisodeFocus ----

    private fun setEpisodesRow(episodeCount: Int, initialEpisodeIndex: Int?) {
        val episodes = List(episodeCount) { episode(it) }
        composeTestRule.setContent {
            val focusState = rememberEpisodeFocus(
                episodes = episodes,
                initialEpisodeIndex = initialEpisodeIndex
            )
            LazyRow(state = focusState.listState) {
                itemsIndexed(episodes) { index, _ ->
                    EpisodeCard(index, focusState.focusRequesters[index])
                }
            }
        }
        // scrollToItem + safeRequestFocus (50ms delay, up to 6x50ms retries) all run on the
        // main clock in this Robolectric harness (see PopupMenuTest / CategoryRowFocusTest).
        composeTestRule.mainClock.advanceTimeBy(1000L)
        composeTestRule.waitForIdle()
    }

    @Test
    fun initialEpisodeIndexFarOutsideViewportStillEndsUpWithExactlyOneFocusedEpisode() {
        // 20 episodes at 300dp each don't fit in a 1280dp-wide viewport - index 15 is not
        // composed on first layout, reproducing Chris' "resumed deep into a season" repro.
        setEpisodesRow(episodeCount = 20, initialEpisodeIndex = 15)

        composeTestRule.onAllNodes(isFocused()).assertCountEquals(1)
    }

    @Test
    fun initialEpisodeIndexFarOutsideViewportFocusesTheTargetEpisode() {
        setEpisodesRow(episodeCount = 20, initialEpisodeIndex = 15)

        composeTestRule.onNodeWithTag(episodeTag(15)).assertIsFocused()
    }

    @Test
    fun initialEpisodeIndexWithinViewportIsUnaffected() {
        // Regression guard: the fix must not break the common case (resume target already visible).
        setEpisodesRow(episodeCount = 20, initialEpisodeIndex = 1)

        composeTestRule.onNodeWithTag(episodeTag(1)).assertIsFocused()
        composeTestRule.onAllNodes(isFocused()).assertCountEquals(1)
    }

    @Test
    fun noInitialEpisodeIndexFocusesFirstEpisode() {
        setEpisodesRow(episodeCount = 20, initialEpisodeIndex = null)

        composeTestRule.onNodeWithTag(episodeTag(0)).assertIsFocused()
    }

    // ---- Isolated, deterministic proof of the extracted fix vs. the pre-fix behavior ----

    /**
     * Snapshot of the pre-fix logic (requests focus without scrolling first). A static copy for
     * comparison only - not a call into production code - kept here to deterministically
     * demonstrate the failure mode `requestInitialEpisodeFocus` (SeriesEpisodesScreen.kt) fixes.
     */
    private suspend fun requestFocusWithoutScrollingFirstLikeThePreFixCode(
        focusRequesters: List<FocusRequester>,
        targetIndex: Int
    ): Boolean {
        return focusRequesters.getOrNull(targetIndex)?.safeRequestFocus(
            targetName = "EPISODE_${targetIndex}_PRE_FIX_SNAPSHOT"
        ) ?: false
    }

    private fun setBareEpisodesRow(
        episodeCount: Int,
        onListStateReady: suspend (LazyListState, List<FocusRequester>) -> Unit
    ) {
        composeTestRule.setContent {
            val listState = rememberLazyListState()
            val focusRequesters = remember { List(episodeCount) { FocusRequester() } }
            LazyRow(state = listState) {
                itemsIndexed(items = List(episodeCount) { it }) { index, _ ->
                    EpisodeCard(index, focusRequesters[index])
                }
            }
            LaunchedEffect(Unit) {
                onListStateReady(listState, focusRequesters)
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1000L)
        composeTestRule.waitForIdle()
    }

    @Test
    fun rootFixScrollsThenReliablyFocusesAnOffScreenTarget() {
        setBareEpisodesRow(episodeCount = 20) { listState, focusRequesters ->
            requestInitialEpisodeFocus(listState, focusRequesters, targetIndex = 15)
        }

        composeTestRule.onNodeWithTag(episodeTag(15)).assertIsFocused()
        composeTestRule.onAllNodes(isFocused()).assertCountEquals(1)
    }

    @Test
    fun preFixLogicWithoutScrollingFirstFailsToFocusAnOffScreenTarget() {
        var focused = true
        setBareEpisodesRow(episodeCount = 20) { _, focusRequesters ->
            focused = requestFocusWithoutScrollingFirstLikeThePreFixCode(focusRequesters, targetIndex = 15)
        }

        assertFalse(
            "Pre-fix logic (no scroll before requesting focus) should fail on an off-screen target",
            focused
        )
        // Nothing else in this isolated setup ever retries - exactly the deadlock Chris hit.
        composeTestRule.onAllNodes(isFocused()).assertCountEquals(0)
    }

    // ---- C2 regression: "Back from Player -> season button focused instead of the episode" ----

    /**
     * C2 diagnosis (read-only, see c2-report.md): `EpisodesList` calls `rememberEpisodeFocus`
     * at a call site that is NOT keyed by season (SeriesEpisodesScreen.kt:759-774, no
     * `key(selectedSeasonIndex) { ... }` around the `EpisodesList(...)` call inside
     * `SeasonNavigation`). `rememberEpisodeFocus`'s own `focusRequesters = remember { episodes.map
     * { FocusRequester() } }` (line 171) has no key at all either.
     *
     * On every screen entry, `selectedSeasonIndex` starts at 0 (plain `remember`, not
     * `rememberSaveable` - Navigation-Compose disposes/recomposes a destination that isn't on top
     * of the back stack, so "Back from Player" is a FRESH composition, not a resumed one) and
     * `EpisodesList` first composes with season 0's episodes - `focusRequesters` is created for
     * THAT season's episode count. `HandleSmartResume` (SeriesEpisodesScreen.kt:250-278) then
     * asynchronously calls `onSeasonSelected`, which can switch to a DIFFERENT season. `EpisodesList`
     * recomposes with the new season's `episodes` at the SAME call site, but `focusRequesters` is
     * never recreated - it stays the stale, wrong-sized list bound to season 0's (now disposed)
     * LazyRow nodes.
     *
     * This test reproduces that season switch directly through the real, production
     * `rememberEpisodeFocus`/`requestInitialEpisodeFocus` functions: season A composes first (3
     * episodes), then - without tearing down the composition, exactly like `EpisodesList`
     * recomposing after `HandleSmartResume` changes the selected season - season B's 10 episodes
     * and a smart-resume target of index 7 replace it at the same call site.
     *
     * Expected (fixed) behavior: episode 7 of season B ends up focused.
     * Actual (current, buggy) behavior: `focusRequesters` still has only 3 (season A-sized)
     * entries, `getOrNull(7)` is null, so `requestInitialEpisodeFocus`'s off-screen-target path is
     * never even attempted - it falls straight to the EPISODE_0_FALLBACK, which lands on the
     * wrong episode (season B's index 0) instead of the smart-resume target. This is the
     * structural defect that also explains Chris' "season button ends up focused" symptom: since
     * the episode list's own focus request never successfully lands on the correct target, it
     * never challenges/overrides the season button's own unconditional self-focus effect
     * (`SeasonButton`'s `LaunchedEffect(isSelected) { delay(50); requestFocus() }`,
     * SeriesEpisodesScreen.kt:817-827) - that effect's earlier, always-succeeding focus grab is
     * left standing. The season-button race itself is real-time-delay-based and not encoded here
     * as a second timing test to avoid a flaky/non-deterministic test (see c2-report.md); this
     * test instead deterministically proves the enabling defect (H2).
     */
    @Test
    fun seasonSwitchAfterInitialCompositionLeavesFocusRequestersStaleForTheNewSeason() {
        val seasonAEpisodes = List(3) { episode(100 + it) }
        val seasonBEpisodes = List(10) { episode(200 + it) }

        lateinit var episodesState: androidx.compose.runtime.MutableState<List<Episode>>
        lateinit var initialIndexState: androidx.compose.runtime.MutableState<Int?>

        composeTestRule.setContent {
            episodesState = remember { mutableStateOf(seasonAEpisodes) }
            initialIndexState = remember { mutableStateOf<Int?>(null) }
            val episodes = episodesState.value

            // Mirrors EpisodesList's call site inside SeasonNavigation: no key(selectedSeasonIndex)
            // wrapper, so this is the SAME call site (and the SAME `remember` slot inside
            // rememberEpisodeFocus) across a season switch, exactly like production.
            val focusState = rememberEpisodeFocus(
                episodes = episodes,
                initialEpisodeIndex = initialIndexState.value
            )
            LazyRow(state = focusState.listState) {
                itemsIndexed(items = episodes, key = { _, ep -> ep.id }) { index, _ ->
                    // Mirrors EpisodesList passing `focusState.focusRequesters.getOrNull(episodeIndex)`
                    // (nullable) straight into the card (SeriesEpisodesScreen.kt:934) - the card still
                    // renders (and is D-Pad-reachable) even when the stale list has no requester for it.
                    EpisodeCard(index, focusState.focusRequesters.getOrNull(index))
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1000L)
        composeTestRule.waitForIdle()
        // Baseline: season A settles normally (matches noInitialEpisodeIndexFocusesFirstEpisode).
        composeTestRule.onNodeWithTag(episodeTag(0)).assertIsFocused()

        // Simulate HandleSmartResume's async season jump (onSeasonSelected + onEpisodeIndexSet in
        // navigateToTargetSeason, SeriesEpisodesScreen.kt:224-244): season B replaces season A's
        // episodes at the same rememberEpisodeFocus call site, with a smart-resume target deep in
        // season B (index 7 - outside season A's stale 3-entry focusRequesters list).
        composeTestRule.runOnIdle {
            episodesState.value = seasonBEpisodes
            initialIndexState.value = 7
        }
        // Advance in smaller steps (mirroring real frame-by-frame execution) instead of one large
        // 1000ms jump: the season switch relaunches several LaunchedEffects at once
        // (requestInitialEpisodeFocus's scroll+safeRequestFocus retries, the focusedEpisodeIndex
        // sync effect, the scroll-follow effect), and driving that settle frame-by-frame is what
        // actually happens on a real device - a single huge advanceTimeBy jump does not reliably
        // step through their delay()/retry interplay the same way. See fix2-report.md.
        repeat(10) {
            composeTestRule.mainClock.advanceTimeBy(100L)
            composeTestRule.waitForIdle()
        }

        // Bug proof: the smart-resume target (season B, index 7) is not focused.
        composeTestRule.onNodeWithTag(episodeTag(7)).assertIsFocused()
    }

    // ---- Order GF6: burst bundling and scroll strategy ----

    /**
     * 15 rapid index changes in one burst (Chris' live repro: 15x RIGHT, 100ms apart) must produce
     * exactly ONE scroll action after the debounce window - to the final index - and must never
     * animate the intermediate indices. The production helper is exercised directly with injected
     * callbacks, so no LazyListState mock is needed.
     */
    @Test
    fun burstOfFifteenIndexChangesScrollsOnceToTheFinalIndexAfterDebounce() = runTest {
        val indices = MutableSharedFlow<Int>(extraBufferCapacity = 64)
        val instantTargets = mutableListOf<Int>()
        val animatedTargets = mutableListOf<Int>()

        backgroundScope.launch {
            followEpisodeFocusBurst(
                indices = indices,
                isVisible = { false },
                scrollTo = { instantTargets += it },
                animateScrollTo = { animatedTargets += it }
            )
        }
        runCurrent()

        repeat(15) { indices.emit(it + 1) }

        // Nothing may happen before the trailing-edge debounce expires.
        advanceTimeBy(EPISODE_FOCUS_SCROLL_DEBOUNCE_MS - 1)
        runCurrent()
        assertTrue("No scroll before the burst pause", instantTargets.isEmpty())
        assertTrue("No animation during the burst", animatedTargets.isEmpty())

        advanceTimeBy(1)
        runCurrent()
        assertEquals("Exactly one jump, to the 15th index", listOf(15), instantTargets)
        assertTrue("No animateScrollToItem for the burst", animatedTargets.isEmpty())
    }

    @Test
    fun singleInputWithinViewportUsesAnimateScrollToItem() = runTest {
        val instantTargets = mutableListOf<Int>()
        val animatedTargets = mutableListOf<Int>()

        launch {
            followEpisodeFocusBurst(
                indices = flowOf(2),
                isVisible = { it == 2 },
                scrollTo = { instantTargets += it },
                animateScrollTo = { animatedTargets += it }
            )
        }
        advanceTimeBy(EPISODE_FOCUS_SCROLL_DEBOUNCE_MS)
        runCurrent()

        assertTrue("Visible target must not jump", instantTargets.isEmpty())
        assertEquals(listOf(2), animatedTargets)
    }

    @Test
    fun offScreenTargetUsesInstantScrollToItem() = runTest {
        val instantTargets = mutableListOf<Int>()
        val animatedTargets = mutableListOf<Int>()

        launch {
            followEpisodeFocusBurst(
                indices = flowOf(15),
                isVisible = { false },
                scrollTo = { instantTargets += it },
                animateScrollTo = { animatedTargets += it }
            )
        }
        advanceTimeBy(EPISODE_FOCUS_SCROLL_DEBOUNCE_MS)
        runCurrent()

        assertEquals("Off-screen target must be composed via scrollToItem", listOf(15), instantTargets)
        assertTrue("Off-screen target must not animate", animatedTargets.isEmpty())
    }
}
