package com.flintr.tv.ui.components.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class OverlayClockDateTest {

    @Test
    fun formatOverlayDateTime_berlinGerman_formatsExpectedTimeAndDate() {
        val result = formatOverlayDateTime(
            instant = Instant.parse("2026-02-16T15:19:00Z"),
            zoneId = ZoneId.of("Europe/Berlin"),
            locale = Locale.GERMANY
        )

        assertEquals("16:19", result.time)
        assertEquals("16. Februar 2026", result.date)
    }

    @Test
    fun formatOverlayDateTime_londonGerman_formatsExpectedTimeAndDate() {
        val result = formatOverlayDateTime(
            instant = Instant.parse("2026-02-16T15:19:00Z"),
            zoneId = ZoneId.of("Europe/London"),
            locale = Locale.GERMANY
        )

        assertEquals("15:19", result.time)
        assertEquals("16. Februar 2026", result.date)
    }

    @Test
    fun formatOverlayDateTime_alwaysUses24HourFormat_withoutAmPm() {
        val result = formatOverlayDateTime(
            instant = Instant.parse("2026-02-16T21:05:00Z"),
            zoneId = ZoneId.of("America/New_York"),
            locale = Locale.US
        )

        assertEquals("16:05", result.time)
        assertFalse(result.time.contains("AM"))
        assertFalse(result.time.contains("PM"))
    }
}
