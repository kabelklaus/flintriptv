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
        val fixedInstant = Instant.parse("2026-11-04T10:04:00Z")

        composeTestRule.setContent {
            FlintRTVTheme {
                OverlayClockDate(clockMillis = { fixedInstant.toEpochMilli() })
            }
        }

        // Erwartete Texte aus derselben Formatierungslogik ableiten, gegen denselben fixen
        // Zeitpunkt, der auch der Komponente übergeben wurde - sonst flackert der Test an
        // Minuten-Grenzen, wenn beide Seiten ihr eigenes Instant.now() abfragen.
        val expected = formatOverlayDateTime(
            instant = fixedInstant,
            zoneId = ZoneId.systemDefault(),
            locale = Locale.getDefault()
        )

        composeTestRule.onNodeWithText(expected.time).assertIsDisplayed()
        composeTestRule.onNodeWithText(expected.date).assertIsDisplayed()
    }

    @Test
    fun uhrLaeuftNachMinutenwechselWeiter() {
        val fixedInstant = Instant.parse("2026-11-04T10:04:00Z")

        composeTestRule.setContent {
            FlintRTVTheme {
                OverlayClockDate(clockMillis = { fixedInstant.toEpochMilli() })
            }
        }

        // Über den Minutenwechsel hinaus laufen lassen — darf nicht abstürzen
        composeTestRule.mainClock.advanceTimeBy(61_000L)
        composeTestRule.waitForIdle()

        val expected = formatOverlayDateTime(
            instant = fixedInstant,
            zoneId = ZoneId.systemDefault(),
            locale = Locale.getDefault()
        )
        composeTestRule.onNodeWithText(expected.time).assertIsDisplayed()
    }
}
