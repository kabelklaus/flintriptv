package com.flintr.tv.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flintr.tv.ui.theme.FlintRTVTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Verhalten des Popup-Menüs als lokale Unit-Tests (Robolectric),
 * damit die Abdeckung in den Jacoco-Report einfließt, den Sonar konsumiert.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w1280dp-h720dp-land")
class PopupMenuTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val options = listOf("Als gesehen markieren", "Zur Watchlist hinzufügen", "Abbrechen")

    private fun setMenu(
        isOpen: Boolean = true,
        onDismiss: () -> Unit = {},
        onOptionSelected: (Int) -> Unit = {}
    ) {
        composeTestRule.setContent {
            FlintRTVTheme {
                PopupMenu(
                    isOpen = isOpen,
                    menuOptions = options,
                    onDismiss = onDismiss,
                    onOptionSelected = onOptionSelected
                )
            }
        }
    }

    @Test
    fun offenesMenueZeigtTitelUndAlleOptionen() {
        setMenu(isOpen = true)

        composeTestRule.onNodeWithText("Menü").assertIsDisplayed()
        options.forEach { option ->
            composeTestRule.onNodeWithText(option).assertIsDisplayed()
        }
    }

    @Test
    fun geschlossenesMenueRendertNichts() {
        setMenu(isOpen = false)

        composeTestRule.onNodeWithText("Menü").assertDoesNotExist()
    }

    @Test
    fun optionsklickLiefertIndexUndSchliesstMenue() {
        var selectedIndex = -1
        var dismissed = false
        setMenu(
            onDismiss = { dismissed = true },
            onOptionSelected = { selectedIndex = it }
        )

        // Menü braucht kurz, bis es bereit ist (Frame-Delay + Grace Period)
        composeTestRule.mainClock.advanceTimeBy(600L)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Zur Watchlist hinzufügen").performClick()

        assertEquals(1, selectedIndex)
        assertTrue(dismissed)
    }
}
