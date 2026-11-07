package com.flintr.tv.ui.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClickDebouncerTest {

    private var now = 1_000L
    private val debouncer = ClickDebouncer(clock = { now })

    @Test
    fun ersterKlickWirdAkzeptiert() {
        assertTrue(debouncer.tryAccept())
    }

    @Test
    fun zweiterKlickInnerhalbDesFenstersWirdVerworfen() {
        assertTrue(debouncer.tryAccept())

        now += 299L

        assertFalse(debouncer.tryAccept())
    }

    @Test
    fun klickExaktAufDerGrenzeWirdAkzeptiert() {
        assertTrue(debouncer.tryAccept())

        // now - last == debounceMillis: the window is exclusive, so this must be accepted.
        now += 300L

        assertTrue(debouncer.tryAccept())
    }

    @Test
    fun resetGibtDenNaechstenKlickFrei() {
        assertTrue(debouncer.tryAccept())
        now += 1L
        assertFalse(debouncer.tryAccept())

        debouncer.reset()

        assertTrue(debouncer.tryAccept())
    }
}
