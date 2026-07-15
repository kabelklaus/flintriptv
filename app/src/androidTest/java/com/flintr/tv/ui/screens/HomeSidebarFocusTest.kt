package com.flintr.tv.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.flintr.tv.ui.theme.FlintRTVTheme
import org.junit.Rule
import org.junit.Test

class HomeSidebarFocusTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun sharedFocusRequesterFocusesSelectedNavbarItem() {
        val sharedFocusRequester = FocusRequester()

        composeTestRule.setContent {
            FlintRTVTheme {
                HomeSidebar(
                    selectedDestination = HomeDestination.Series,
                    onNavigate = {},
                    focusRequester = sharedFocusRequester,
                    onNavigateRight = {},
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        composeTestRule.runOnIdle {
            sharedFocusRequester.requestFocus()
        }

        composeTestRule
            .onNodeWithTag("navbar-item-Series")
            .assertIsFocused()
    }
}
