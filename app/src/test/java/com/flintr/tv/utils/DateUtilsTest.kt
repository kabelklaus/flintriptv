package com.flintr.tv.utils

import androidx.compose.ui.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Ablaufdatum-Formatierung und Status-Farben.
 * Robolectric wegen android.util.Log in den Fehlerpfaden.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class DateUtilsTest {

    private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    private fun epochSecondsFor(date: LocalDate): String =
        date.atStartOfDay(ZoneId.systemDefault()).toEpochSecond().toString()

    @Test
    fun nullAndBlankInputReturnNull() {
        assertNull(formatExpirationDate(null))
        assertNull(formatExpirationDate(""))
        assertNull(formatExpirationDate("   "))
    }

    @Test
    fun unparseableInputReturnsNull() {
        assertNull(formatExpirationDate("kein-datum"))
    }

    @Test
    fun unixSecondsTimestampFormatsWithRemainingDays() {
        val target = LocalDate.now().plusDays(30)

        val result = formatExpirationDate(epochSecondsFor(target))

        assertEquals(
            "Account läuft ab: ${target.format(dateFormatter)} (noch 30 Tage)",
            result
        )
    }

    @Test
    fun singleRemainingDayUsesSingular() {
        val target = LocalDate.now().plusDays(1)

        val result = formatExpirationDate(epochSecondsFor(target))

        assertEquals(
            "Account läuft ab: ${target.format(dateFormatter)} (noch 1 Tag)",
            result
        )
    }

    @Test
    fun expiringTodayShowsTodayStatus() {
        val today = LocalDate.now()

        val result = formatExpirationDate(epochSecondsFor(today))

        assertEquals(
            "Account läuft ab: ${today.format(dateFormatter)} (läuft heute ab)",
            result
        )
    }

    @Test
    fun pastDateShowsExpiredStatus() {
        val past = LocalDate.now().minusDays(5)

        val result = formatExpirationDate(epochSecondsFor(past))

        assertEquals(
            "Account läuft ab: ${past.format(dateFormatter)} (abgelaufen)",
            result
        )
    }

    @Test
    fun millisecondTimestampIsRecognizedByLength() {
        val target = LocalDate.now().plusDays(10)
        val millis = target.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val result = formatExpirationDate(millis.toString())

        assertEquals(
            "Account läuft ab: ${target.format(dateFormatter)} (noch 10 Tage)",
            result
        )
    }

    @Test
    fun isoDateStringIsParsed() {
        val target = LocalDate.now().plusDays(3)

        val result = formatExpirationDate(target.format(DateTimeFormatter.ISO_LOCAL_DATE))

        assertEquals(
            "Account läuft ab: ${target.format(dateFormatter)} (noch 3 Tage)",
            result
        )
    }

    @Test
    fun germanDateStringIsParsed() {
        val target = LocalDate.now().plusDays(3)

        val result = formatExpirationDate(target.format(dateFormatter))

        assertEquals(
            "Account läuft ab: ${target.format(dateFormatter)} (noch 3 Tage)",
            result
        )
    }

    private val normal = Color.Green
    private val warning = Color.Yellow
    private val expired = Color.Red

    private fun colorFor(expDate: String?): Color =
        getExpirationDateColor(expDate, normal, warning, expired)

    @Test
    fun colorIsNormalForNullOrUnparseableDates() {
        assertEquals(normal, colorFor(null))
        assertEquals(normal, colorFor(""))
        assertEquals(normal, colorFor("kein-datum"))
    }

    @Test
    fun colorIsNormalForDatesFarInFuture() {
        assertEquals(normal, colorFor(epochSecondsFor(LocalDate.now().plusDays(30))))
    }

    @Test
    fun colorIsWarningWithinSevenDays() {
        assertEquals(warning, colorFor(epochSecondsFor(LocalDate.now().plusDays(7))))
        assertEquals(warning, colorFor(epochSecondsFor(LocalDate.now())))
    }

    @Test
    fun colorIsExpiredForPastDates() {
        assertEquals(expired, colorFor(epochSecondsFor(LocalDate.now().minusDays(1))))
    }
}
