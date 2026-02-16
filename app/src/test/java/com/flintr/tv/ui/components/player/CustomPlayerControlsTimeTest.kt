package com.flintr.tv.ui.components.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class CustomPlayerControlsTimeTest {

    @Test
    fun formatFinishTimeText_movie_returnsExpectedFinishTime() {
        val result = formatFinishTimeText(
            contentType = "movie",
            nowMillis = Instant.parse("2026-02-16T20:00:00Z").toEpochMilli(),
            currentPositionMillis = 10 * 60 * 1000L,
            durationMillis = 40 * 60 * 1000L,
            zoneId = ZoneId.of("UTC"),
            locale = Locale.GERMANY
        )

        assertEquals("Fertig um 20:30", result)
    }

    @Test
    fun formatFinishTimeText_series_returnsExpectedFinishTime() {
        val result = formatFinishTimeText(
            contentType = "series",
            nowMillis = Instant.parse("2026-02-16T08:15:00Z").toEpochMilli(),
            currentPositionMillis = 15 * 60 * 1000L,
            durationMillis = 75 * 60 * 1000L,
            zoneId = ZoneId.of("UTC"),
            locale = Locale.GERMANY
        )

        assertEquals("Fertig um 09:15", result)
    }

    @Test
    fun formatFinishTimeText_live_returnsNull() {
        val result = formatFinishTimeText(
            contentType = "live",
            nowMillis = Instant.parse("2026-02-16T10:00:00Z").toEpochMilli(),
            currentPositionMillis = 10 * 60 * 1000L,
            durationMillis = 40 * 60 * 1000L,
            zoneId = ZoneId.of("UTC"),
            locale = Locale.GERMANY
        )

        assertNull(result)
    }

    @Test
    fun formatFinishTimeText_invalidDuration_returnsNull() {
        val result = formatFinishTimeText(
            contentType = "movie",
            nowMillis = Instant.parse("2026-02-16T10:00:00Z").toEpochMilli(),
            currentPositionMillis = 5 * 60 * 1000L,
            durationMillis = 0L,
            zoneId = ZoneId.of("UTC"),
            locale = Locale.GERMANY
        )

        assertNull(result)
    }

    @Test
    fun formatFinishTimeText_currentPositionBeyondDuration_usesNowAsFinishTime() {
        val result = formatFinishTimeText(
            contentType = "movie",
            nowMillis = Instant.parse("2026-02-16T14:42:00Z").toEpochMilli(),
            currentPositionMillis = 120 * 60 * 1000L,
            durationMillis = 90 * 60 * 1000L,
            zoneId = ZoneId.of("UTC"),
            locale = Locale.GERMANY
        )

        assertEquals("Fertig um 14:42", result)
    }

    @Test
    fun formatFinishTimeText_pauseScenario_laterNowShiftsFinishTimeLater() {
        val pausedPosition = 10 * 60 * 1000L
        val duration = 30 * 60 * 1000L

        val firstResult = formatFinishTimeText(
            contentType = "movie",
            nowMillis = Instant.parse("2026-02-16T10:00:00Z").toEpochMilli(),
            currentPositionMillis = pausedPosition,
            durationMillis = duration,
            zoneId = ZoneId.of("UTC"),
            locale = Locale.GERMANY
        )
        val secondResult = formatFinishTimeText(
            contentType = "movie",
            nowMillis = Instant.parse("2026-02-16T10:01:00Z").toEpochMilli(),
            currentPositionMillis = pausedPosition,
            durationMillis = duration,
            zoneId = ZoneId.of("UTC"),
            locale = Locale.GERMANY
        )

        assertEquals("Fertig um 10:20", firstResult)
        assertEquals("Fertig um 10:21", secondResult)
    }

    @Test
    fun formatFinishTimeText_crossesMidnight_stillShowsOnlyClockTime() {
        val result = formatFinishTimeText(
            contentType = "movie",
            nowMillis = Instant.parse("2026-02-16T23:50:00Z").toEpochMilli(),
            currentPositionMillis = 10 * 60 * 1000L,
            durationMillis = 30 * 60 * 1000L,
            zoneId = ZoneId.of("UTC"),
            locale = Locale.GERMANY
        )

        assertEquals("Fertig um 00:10", result)
    }
}
