package com.flintr.tv.utils.error

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Ersetzt die bisherigen "Log.e(TAG, msg, e)"-Sackgassen: loggt weiterhin,
 * meldet den Fehler aber zusätzlich nach außen.
 */
@RunWith(RobolectricTestRunner::class)
class ErrorLoggingTest {

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
    fun tearDown() = AppErrorReporter.reset()

    @Test
    fun reportErrorForwardsThrowableAndContext() {
        val error = IllegalStateException("kaputt")

        reportError("MyTag", "Laden fehlgeschlagen", error)

        val (reported, keys) = recording.calls.single()
        assertSame(error, reported)
        assertEquals("Laden fehlgeschlagen", keys["context"])
        assertEquals("MyTag", keys["tag"])
    }

    @Test
    fun reportWarningForwardsThrowableAndContext() {
        val error = IllegalStateException("kaputt")

        reportWarning("MyTag", "unkritisch", error)

        val (reported, keys) = recording.calls.single()
        assertSame(error, reported)
        assertEquals("unkritisch", keys["context"])
    }

    @Test
    fun callerSuppliedKeysArePreserved() {
        reportError("MyTag", "Serie kaputt", IllegalStateException("x"), mapOf("seriesId" to "42"))

        val keys = recording.calls.single().second
        assertEquals("42", keys["seriesId"])
        assertEquals("Serie kaputt", keys["context"])
    }

    @Test
    fun reportHandledForwardsWithoutLogging() {
        val error = IllegalStateException("stiller Fallback")

        reportHandled("Reflection-Fallback", error)

        val (reported, keys) = recording.calls.single()
        assertSame(error, reported)
        assertEquals("Reflection-Fallback", keys["context"])
    }
}
