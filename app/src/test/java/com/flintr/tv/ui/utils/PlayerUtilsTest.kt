package com.flintr.tv.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerUtilsTest {

    @Test
    fun `formatTime formatiert unter einer Stunde als M_SS`() {
        assertEquals("0:00", formatTime(0L))
        assertEquals("0:59", formatTime(59_000L))
        assertEquals("1:00", formatTime(60_000L))
        assertEquals("59:59", formatTime(3_599_000L))
    }

    @Test
    fun `formatTime formatiert ab einer Stunde als H_MM_SS`() {
        assertEquals("1:00:00", formatTime(3_600_000L))
        assertEquals("1:01:05", formatTime(3_665_000L))
        assertEquals("12:34:56", formatTime(45_296_000L))
    }

    @Test
    fun `formatTime schneidet Millisekunden ab`() {
        assertEquals("0:01", formatTime(1_999L))
    }
}
