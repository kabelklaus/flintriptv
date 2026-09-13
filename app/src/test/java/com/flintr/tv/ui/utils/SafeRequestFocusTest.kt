package com.flintr.tv.ui.utils

import androidx.compose.ui.focus.FocusRequester
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Order 3: safeRequestFocus() was extended to return a Boolean success flag so callers (see
 * SeriesEpisodesScreen.rememberEpisodeFocus) can fall back to a different focus target instead
 * of leaving the screen with nothing focused when every retry fails.
 *
 * Robolectric is needed only because the retry-exhausted path logs via android.util.Log
 * (unmocked under plain JUnit); FocusRequester.requestFocus() itself throws
 * IllegalStateException purely from its own internal state (no attached focus target node),
 * independent of any Composition. Virtual time comes from kotlinx-coroutines-test's runTest.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SafeRequestFocusTest {

    @Test
    fun returnsFalseWhenRequesterNeverAttachesToAnyNode() = runTest {
        val neverAttached = FocusRequester()

        val result = neverAttached.safeRequestFocus(targetName = "never-attached")

        assertFalse(result)
    }
}
