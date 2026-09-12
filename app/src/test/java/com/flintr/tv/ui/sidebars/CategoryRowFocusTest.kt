package com.flintr.tv.ui.sidebars

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flintr.tv.data.model.Category
import com.flintr.tv.ui.theme.FlintRTVTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Order 2 / Bug 2 regression: Tap auf einen nicht-selektierten Chip darf ihn im selben Tap
 * fokussieren UND selektieren (keine stale FocusRequester-Referenz nach Selektionswechsel),
 * und der geteilte Navbar-Rückkehr-Requester muss weiterhin den aktuell selektierten Chip
 * fokussieren koennen.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w1280dp-h720dp-land")
class CategoryRowFocusTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val categoryA = Category(categoryId = "A", categoryName = "Alpha")
    private val categoryB = Category(categoryId = "B", categoryName = "Beta")
    private val chipTagA = "category-chip-${categoryA.categoryId}"
    private val chipTagB = "category-chip-${categoryB.categoryId}"

    private fun setRowContent(
        initialSelectedCategoryId: String?,
        navbarReturnFocusRequester: FocusRequester = FocusRequester(),
        onCategorySelected: (String?) -> Unit = {}
    ) {
        composeTestRule.setContent {
            var selectedCategoryId by remember { mutableStateOf(initialSelectedCategoryId) }
            FlintRTVTheme {
                CategoryRow(
                    params = CategoryRowParams(
                        categories = listOf(categoryA, categoryB),
                        selectedCategoryId = selectedCategoryId,
                        onCategorySelected = {
                            selectedCategoryId = it
                            onCategorySelected(it)
                        },
                        focusRequester = navbarReturnFocusRequester,
                        onNavigateLeft = {},
                        onNavigateRight = {},
                        onCategoryHidden = {},
                        listState = remember { LazyListState() }
                    ),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    @Test
    fun tapOnUnselectedChipSelectsAndFocusesItInOneTap() {
        val selections = mutableListOf<String?>()
        setRowContent(
            initialSelectedCategoryId = categoryA.categoryId,
            onCategorySelected = { selections.add(it) }
        )

        composeTestRule.onNodeWithTag(chipTagB).performClick()
        // safeRequestFocus() delays 50ms before its first attempt; the shared main clock
        // (see PopupMenuTest) drives that delay, so advance past it before asserting.
        composeTestRule.mainClock.advanceTimeBy(500L)
        composeTestRule.waitForIdle()

        assertEquals(listOf(categoryB.categoryId), selections)
        composeTestRule.onNodeWithTag(chipTagB).assertIsFocused()
    }

    @Test
    fun navbarReturnFocusRequesterFocusesSelectedChip() {
        val sharedFocusRequester = FocusRequester()
        setRowContent(
            initialSelectedCategoryId = categoryB.categoryId,
            navbarReturnFocusRequester = sharedFocusRequester
        )

        composeTestRule.runOnIdle {
            sharedFocusRequester.requestFocus()
        }

        composeTestRule.onNodeWithTag(chipTagB).assertIsFocused()
    }

    @Test
    fun tappingUnselectedChipTwiceInARowSelectsAndFocusesBothTimes() {
        // Regression for the stale-requester race: tap B (was unselected), then tap A
        // (now unselected) - each tap must land its own chip's focus, never the other's.
        val selections = mutableListOf<String?>()
        setRowContent(
            initialSelectedCategoryId = categoryA.categoryId,
            onCategorySelected = { selections.add(it) }
        )

        composeTestRule.onNodeWithTag(chipTagB).performClick()
        composeTestRule.mainClock.advanceTimeBy(500L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(chipTagB).assertIsFocused()

        composeTestRule.onNodeWithTag(chipTagA).performClick()
        composeTestRule.mainClock.advanceTimeBy(500L)
        composeTestRule.waitForIdle()

        assertEquals(listOf(categoryB.categoryId, categoryA.categoryId), selections)
        composeTestRule.onNodeWithTag(chipTagA).assertIsFocused()
    }
}
