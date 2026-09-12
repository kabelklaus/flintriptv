package com.flintr.tv.ui.utils

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Isolated diagnostic for Order 2 / Bug 2's preferred fix: can a single focus target node be
 * attached to TWO different FocusRequesters at once (`Modifier.focusRequester(a).focusRequester(b)`),
 * such that requestFocus() on EITHER one focuses the node?
 *
 * Result: YES. A FocusRequester tracks a set of attached focus-target nodes rather than a node
 * tracking a single owning requester, so chaining two `.focusRequester(...)` calls on the same
 * node registers it with both - `requestFocus()` on either one successfully focuses it. This is
 * what CategoryRow.kt relies on: every chip keeps its own stable requester, and the selected
 * chip additionally gets the shared Navbar-return requester chained after it.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class FocusRequesterDoubleAttachTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setDoubleAttachedTarget(): Pair<FocusRequester, FocusRequester> {
        val first = FocusRequester()
        val second = FocusRequester()
        composeTestRule.setContent {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .testTag("target")
                    .focusRequester(first)
                    .focusRequester(second)
                    .focusable()
            )
        }
        return first to second
    }

    @Test
    fun firstAttachedRequesterFocusesTheNode() {
        val (first, _) = setDoubleAttachedTarget()

        composeTestRule.runOnIdle {
            first.requestFocus()
        }

        composeTestRule.onNodeWithTag("target").assertIsFocused()
    }

    @Test
    fun secondAttachedRequesterAlsoFocusesTheNode() {
        val (_, second) = setDoubleAttachedTarget()

        composeTestRule.runOnIdle {
            second.requestFocus()
        }

        composeTestRule.onNodeWithTag("target").assertIsFocused()
    }
}
