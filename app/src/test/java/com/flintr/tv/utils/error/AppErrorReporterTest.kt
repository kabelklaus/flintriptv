package com.flintr.tv.utils.error

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fassade für Call-Sites ohne Hilt (Composables, Top-Level-Funktionen).
 * Muss vor der Installation still sein, damit Unit-Tests kein Firebase brauchen.
 */
class AppErrorReporterTest {

    private class RecordingReporter : ErrorReporter {
        val calls = mutableListOf<Pair<Throwable, Map<String, String>>>()
        override fun report(throwable: Throwable, keys: Map<String, String>) {
            calls += throwable to keys
        }
    }

    @After
    fun tearDown() = AppErrorReporter.reset()

    @Test
    fun isSilentBeforeInstall() {
        AppErrorReporter.reset()

        AppErrorReporter.report(IllegalStateException("kaputt"))
        // kein Throw, kein Firebase-Zugriff
        assertTrue(true)
    }

    @Test
    fun forwardsToInstalledDelegate() {
        val recording = RecordingReporter()
        AppErrorReporter.install(recording)

        val error = IllegalStateException("kaputt")
        AppErrorReporter.report(error, mapOf("k" to "v"))

        assertEquals(1, recording.calls.size)
        assertEquals(error, recording.calls.single().first)
        assertEquals(mapOf("k" to "v"), recording.calls.single().second)
    }

    @Test
    fun installReplacesPreviousDelegate() {
        val first = RecordingReporter()
        val second = RecordingReporter()
        AppErrorReporter.install(first)
        AppErrorReporter.install(second)

        AppErrorReporter.report(IllegalStateException("kaputt"))

        assertEquals(0, first.calls.size)
        assertEquals(1, second.calls.size)
    }

    @Test
    fun resetStopsForwarding() {
        val recording = RecordingReporter()
        AppErrorReporter.install(recording)
        AppErrorReporter.reset()

        AppErrorReporter.report(IllegalStateException("kaputt"))

        assertEquals(0, recording.calls.size)
    }
}
