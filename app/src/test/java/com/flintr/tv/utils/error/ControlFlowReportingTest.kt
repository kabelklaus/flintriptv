package com.flintr.tv.utils.error

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Erwartete Exceptions, die als Kontrollfluss benutzt werden (FocusRequester
 * noch nicht attached, Gesten-Timeout, Reflection-Fallbacks). Werden gemeldet,
 * aber separat markiert und per Schalter abschaltbar.
 */
class ControlFlowReportingTest {

    private class RecordingReporter : ErrorReporter {
        val calls = mutableListOf<Pair<Throwable, Map<String, String>>>()
        override fun report(throwable: Throwable, keys: Map<String, String>) {
            calls += throwable to keys
        }
    }

    private val recording = RecordingReporter()

    init {
        AppErrorReporter.install(recording)
    }

    @After
    fun tearDown() {
        AppErrorReporter.controlFlowReportingEnabled = true
        AppErrorReporter.reset()
    }

    @Test
    fun isEnabledByDefault() {
        assertTrue(AppErrorReporter.controlFlowReportingEnabled)
    }

    @Test
    fun reportsAndTagsAsControlFlow() {
        reportControlFlow("VideoProgressBar.gestureTimeout", IllegalStateException("timeout"))

        val keys = recording.calls.single().second
        assertEquals("control-flow", keys["kind"])
        assertEquals("VideoProgressBar.gestureTimeout", keys["context"])
    }

    @Test
    fun canBeMutedWithoutAffectingRealErrors() {
        AppErrorReporter.controlFlowReportingEnabled = false

        reportControlFlow("VideoProgressBar.gestureTimeout", IllegalStateException("timeout"))
        assertEquals(0, recording.calls.size)

        // Echte Fehler bleiben unberührt
        reportHandled("XtreamRepository.getSeriesInfo", IllegalStateException("kaputt"))
        assertEquals(1, recording.calls.size)
    }
}
