package com.flintr.tv.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flintr.tv.ui.theme.FlintRTVTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Klickverhalten der HomeSidebar als lokale Unit-Tests (Robolectric),
 * damit die Abdeckung in den Jacoco-Report einfließt, den Sonar konsumiert.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w1280dp-h720dp-land")
class HomeSidebarClickTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setSidebarContent(
        selectedDestination: HomeDestination = HomeDestination.LiveTV,
        onNavigate: (HomeDestination) -> Unit = {},
        focusRequester: FocusRequester = FocusRequester(),
        onNavigateRight: (() -> Unit)? = null,
        onSearchClick: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            FlintRTVTheme {
                HomeSidebar(
                    selectedDestination = selectedDestination,
                    onNavigate = onNavigate,
                    focusRequester = focusRequester,
                    onNavigateRight = onNavigateRight,
                    onSearchClick = onSearchClick,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    @Test
    fun clickOnNavItemNavigatesToItsDestination() {
        val navigated = mutableListOf<HomeDestination>()
        setSidebarContent(onNavigate = { navigated.add(it) })

        composeTestRule.onNodeWithTag("navbar-item-TV").performClick()
        composeTestRule.onNodeWithTag("navbar-item-Movies").performClick()
        composeTestRule.onNodeWithTag("navbar-item-Series").performClick()
        composeTestRule.onNodeWithTag("navbar-item-Settings").performClick()

        assertEquals(
            listOf<HomeDestination>(
                HomeDestination.LiveTV,
                HomeDestination.VOD,
                HomeDestination.Series,
                HomeDestination.Settings
            ),
            navigated
        )
    }

    @Test
    fun clickOnSearchItemTriggersSearchInsteadOfNavigation() {
        val navigated = mutableListOf<HomeDestination>()
        var searchClicks = 0
        setSidebarContent(
            onNavigate = { navigated.add(it) },
            onSearchClick = { searchClicks++ }
        )

        composeTestRule.onNodeWithTag("navbar-item-Suche").performClick()

        assertEquals(1, searchClicks)
        assertTrue("Suche darf nicht über onNavigate laufen", navigated.isEmpty())
    }

    @Test
    fun sharedFocusRequesterFocusesSelectedNavbarItem() {
        val sharedFocusRequester = FocusRequester()
        setSidebarContent(
            selectedDestination = HomeDestination.Series,
            focusRequester = sharedFocusRequester
        )

        composeTestRule.runOnIdle {
            sharedFocusRequester.requestFocus()
        }

        composeTestRule
            .onNodeWithTag("navbar-item-Series")
            .assertIsFocused()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun dpadRightOnFocusedItemOpensCategorySidebar() {
        val sharedFocusRequester = FocusRequester()
        var navigateRightCalls = 0
        setSidebarContent(
            selectedDestination = HomeDestination.LiveTV,
            focusRequester = sharedFocusRequester,
            onNavigateRight = { navigateRightCalls++ }
        )

        composeTestRule.runOnIdle {
            sharedFocusRequester.requestFocus()
        }
        composeTestRule.onNodeWithTag("navbar-item-TV").assertIsFocused()

        composeTestRule.onNodeWithTag("navbar-item-TV").performKeyInput {
            pressKey(Key.DirectionRight)
        }

        assertEquals(1, navigateRightCalls)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun dpadCenterOnFocusedItemNavigates() {
        val sharedFocusRequester = FocusRequester()
        val navigated = mutableListOf<HomeDestination>()
        setSidebarContent(
            selectedDestination = HomeDestination.Settings,
            focusRequester = sharedFocusRequester,
            onNavigate = { navigated.add(it) }
        )

        composeTestRule.runOnIdle {
            sharedFocusRequester.requestFocus()
        }
        composeTestRule.onNodeWithTag("navbar-item-Settings").assertIsFocused()

        composeTestRule.onNodeWithTag("navbar-item-Settings").performKeyInput {
            pressKey(Key.DirectionCenter)
        }

        assertEquals(listOf<HomeDestination>(HomeDestination.Settings), navigated)
    }
}
