package com.flintr.tv.ui.components.player

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.flintr.tv.ui.theme.FlintRTVTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * Render-Verhalten der Overlay-Uhr als lokale Unit-Tests (Robolectric),
 * damit die Abdeckung in den Jacoco-Report einfließt, den Sonar konsumiert.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w1280dp-h720dp-land")
class OverlayClockDateRenderTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun zeigtAktuelleUhrzeitUndDatum() {
        composeTestRule.setContent {
            FlintRTVTheme {
                OverlayClockDate()
            }
        }

        // Erwartete Texte aus derselben Formatierungslogik ableiten
        val expected = formatOverlayDateTime(
            instant = Instant.now(),
            zoneId = ZoneId.systemDefault(),
            locale = Locale.getDefault()
        )

        composeTestRule.onNodeWithText(expected.time).assertIsDisplayed()
        composeTestRule.onNodeWithText(expected.date).assertIsDisplayed()
    }

    @Test
    fun uhrLaeuftNachMinutenwechselWeiter() {
        composeTestRule.setContent {
            FlintRTVTheme {
                OverlayClockDate()
            }
        }

        // Über den Minutenwechsel hinaus laufen lassen — darf nicht abstürzen
        composeTestRule.mainClock.advanceTimeBy(61_000L)
        composeTestRule.waitForIdle()

        val expected = formatOverlayDateTime(
            instant = Instant.now(),
            zoneId = ZoneId.systemDefault(),
            locale = Locale.getDefault()
        )
        composeTestRule.onNodeWithText(expected.time).assertIsDisplayed()
    }
}
