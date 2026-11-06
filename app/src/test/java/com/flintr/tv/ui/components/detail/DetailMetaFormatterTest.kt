package com.flintr.tv.ui.components.detail

import org.junit.Assert.assertEquals
import org.junit.Test

class DetailMetaFormatterTest {

    // ---- formatDate: every source format -> TT.MM.JJJJ ---------------------------------

    @Test
    fun `iso date becomes german`() {
        assertEquals("25.12.2013", formatDate("2013-12-25"))
    }

    @Test
    fun `iso date with slashes becomes german`() {
        assertEquals("25.12.2013", formatDate("2013/12/25"))
    }

    @Test
    fun `german date stays german`() {
        assertEquals("25.12.2013", formatDate("25.12.2013"))
    }

    @Test
    fun `dashed day-first date becomes german`() {
        assertEquals("25.12.2013", formatDate("25-12-2013"))
    }

    @Test
    fun `us slash date becomes german`() {
        assertEquals("25.12.2013", formatDate("12/25/2013"))
    }

    @Test
    fun `date with trailing time becomes german`() {
        assertEquals("25.12.2013", formatDate("2013-12-25 00:00:00"))
        assertEquals("25.12.2013", formatDate("2013-12-25T00:00:00"))
        assertEquals("25.12.2013", formatDate("25.12.2013 00:00:00"))
    }

    @Test
    fun `single digit parts become german`() {
        assertEquals("05.01.2013", formatDate("2013-1-5"))
    }

    @Test
    fun `epoch seconds become german`() {
        assertEquals("25.12.2013", formatDate("1387929600"))
    }

    @Test
    fun `epoch milliseconds become german`() {
        assertEquals("25.12.2013", formatDate("1387929600000"))
    }

    @Test
    fun `garbage stays raw`() {
        assertEquals("kein Datum", formatDate("kein Datum"))
        assertEquals("N/A", formatDate("N/A"))
    }

    @Test
    fun `invalid calendar date falls back to contained year`() {
        assertEquals("2013", formatDate("2013-02-30"))
    }

    @Test
    fun `garbage with year shows only the year`() {
        assertEquals("2013", formatDate("Release 2013 (Director's Cut)"))
    }

    @Test
    fun `empty and null stay empty`() {
        assertEquals("", formatDate(""))
        assertEquals("", formatDate("   "))
        assertEquals("", formatDate(null))
    }

    // ---- formatRuntime: bare number -> "N Min" ------------------------------------------

    @Test
    fun `bare number gets min suffix`() {
        assertEquals("155 Min", formatRuntime("155"))
    }

    @Test
    fun `value with unit stays unchanged`() {
        assertEquals("2h 35min", formatRuntime("2h 35min"))
        assertEquals("155 min", formatRuntime("155 min"))
        assertEquals("2:35", formatRuntime("2:35"))
    }

    @Test
    fun `whitespace is normalized`() {
        assertEquals("155 Min", formatRuntime("  155  "))
        assertEquals("2h 35min", formatRuntime("2h  35min"))
    }

    @Test
    fun `empty and null stay empty without min suffix`() {
        assertEquals("", formatRuntime(""))
        assertEquals("", formatRuntime("   "))
        assertEquals("", formatRuntime(null))
    }

    @Test
    fun `unparsable runtime stays empty instead of zero min`() {
        assertEquals("", formatRuntime("N/A"))
    }

    @Test
    fun `bare year-like value stays a year not a runtime`() {
        assertEquals("2013", formatRuntime("2013"))
    }

    // ---- formatMetaLine: whole line, separators and order preserved ---------------------

    @Test
    fun `meta line gets min suffix and german date`() {
        assertEquals("155 Min • 25.12.2013", formatMetaLine("155 • 2013-12-25"))
    }

    @Test
    fun `meta line with unit runtime keeps runtime`() {
        assertEquals("2h 35min • 25.12.2013", formatMetaLine("2h 35min • 2013-12-25"))
    }

    @Test
    fun `series meta line with year and staffeln stays untouched`() {
        assertEquals("2013 • 2 Staffeln", formatMetaLine("2013 • 2 Staffeln"))
    }

    @Test
    fun `single runtime segment gets min suffix`() {
        assertEquals("155 Min", formatMetaLine("155"))
    }

    @Test
    fun `single date segment becomes german`() {
        assertEquals("25.12.2013", formatMetaLine("2013-12-25"))
    }

    @Test
    fun `single year segment stays a year`() {
        assertEquals("2013", formatMetaLine("2013"))
    }

    @Test
    fun `unparsable runtime segment is dropped without dangling separator`() {
        assertEquals("25.12.2013", formatMetaLine("N/A • 2013-12-25"))
    }

    @Test
    fun `unparsable date segment stays raw`() {
        assertEquals("155 Min • unbekannt", formatMetaLine("155 • unbekannt"))
    }

    @Test
    fun `pipe separated line keeps its separator`() {
        assertEquals("IMDb 7.2 | 155 Min • 25.12.2013", formatMetaLine("IMDb 7.2 | 155 • 2013-12-25"))
    }

    @Test
    fun `null and blank meta stay as delivered`() {
        assertEquals(null, formatMetaLine(null))
        assertEquals("", formatMetaLine(""))
    }

    // ---- formatSeasonLabel: "Season N" -> "Staffel N" -----------------------------------

    @Test
    fun `season label becomes staffel`() {
        assertEquals("Staffel 3", formatSeasonLabel("Season 3"))
        assertEquals("Staffel 10", formatSeasonLabel("Season 10"))
    }

    @Test
    fun `staffel label stays untouched`() {
        assertEquals("Staffel 3", formatSeasonLabel("Staffel 3"))
    }

    @Test
    fun `season label variants are normalized`() {
        assertEquals("Staffel 1", formatSeasonLabel("season 1"))
        assertEquals("Staffel 1", formatSeasonLabel("SEASON  1"))
        assertEquals("Staffel 2", formatSeasonLabel("Season. 2"))
        assertEquals("Staffel 2", formatSeasonLabel("Season: 2"))
        assertEquals("Staffel 2", formatSeasonLabel("Season02"))
        assertEquals("Staffel 0", formatSeasonLabel("Season 00"))
    }

    @Test
    fun `labels without season number stay as delivered`() {
        assertEquals("Specials", formatSeasonLabel("Specials"))
        assertEquals("Seasonal Hits", formatSeasonLabel("Seasonal Hits"))
    }
}
