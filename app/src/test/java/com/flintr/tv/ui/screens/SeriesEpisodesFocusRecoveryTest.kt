package com.flintr.tv.ui.screens

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.LaunchedEffect
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
import org.junit.Assert.assertFalse
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
    private fun EpisodeCard(index: Int, focusRequester: FocusRequester) {
        Box(
            modifier = Modifier
                .testTag(episodeTag(index))
                .width(300.dp)
                .height(180.dp)
                .focusRequester(focusRequester)
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
}
