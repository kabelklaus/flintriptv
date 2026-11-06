package com.flintr.tv.utils.error

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Alle Fehler werden gemeldet — aber derselbe Fehler nicht endlos oft.
 * Ohne Dedup würde ein Per-Frame-Pfad (DebugOverlay) die Session-Quote
 * verbrauchen und die echten Fehler verdrängen.
 */
class CrashlyticsErrorReporterTest {

    private class FakeSink : CrashlyticsSink {
        val recorded = mutableListOf<Throwable>()
        val keys = mutableMapOf<String, String>()

        override fun recordException(throwable: Throwable) {
            recorded += throwable
        }

        override fun setCustomKey(key: String, value: String) {
            keys[key] = value
        }
    }

    private var now = 1_000L
    private val sink = FakeSink()
    private val reporter = CrashlyticsErrorReporter(
        sink = sink,
        clock = { now },
        dedupWindowMs = 60_000L,
        maxDistinctPerSession = 3
    )

    @Test
    fun reportsThrowableToSink() {
        val error = IllegalStateException("kaputt")

        reporter.report(error)

        assertEquals(1, sink.recorded.size)
        assertEquals("kaputt", sink.recorded.first().message)
    }

    @Test
    fun identicalErrorWithinWindowIsReportedOnce() {
        repeat(50) { reporter.report(sameError()) }

        assertEquals(1, sink.recorded.size)
    }

    @Test
    fun identicalErrorIsReportedAgainAfterWindowExpires() {
        reporter.report(sameError())
        now += 60_001L
        reporter.report(sameError())

        assertEquals(2, sink.recorded.size)
    }

    @Test
    fun distinctErrorsAreAllReported() {
        reporter.report(IllegalStateException("erster"))
        reporter.report(IllegalArgumentException("zweiter"))

        assertEquals(2, sink.recorded.size)
    }

    @Test
    fun networkErrorsAreReportedToo() {
        // Ausdrücklicher Wunsch: keine Filterung nach Fehlertyp.
        reporter.report(java.net.SocketTimeoutException("timeout"))
        reporter.report(java.io.IOException("offline"))

        assertEquals(2, sink.recorded.size)
    }

    @Test
    fun distinctErrorCountIsCappedPerSession() {
        reporter.report(IllegalStateException("a"))
        reporter.report(IllegalStateException("b"))
        reporter.report(IllegalStateException("c"))
        reporter.report(IllegalStateException("d"))

        assertEquals("Cap von 3 muss greifen", 3, sink.recorded.size)
    }

    @Test
    fun customKeysAreForwarded() {
        reporter.report(IllegalStateException("kaputt"), mapOf("seriesId" to "42"))

        assertEquals("42", sink.keys["seriesId"])
    }

    @Test
    fun credentialsAreStrippedFromReportedThrowable() {
        reporter.report(
            IllegalStateException("GET http://s/player_api.php?username=alice&password=s3cret")
        )

        val message = sink.recorded.single().message!!
        assertFalse("Username geleakt: $message", message.contains("alice"))
        assertFalse("Passwort geleakt: $message", message.contains("s3cret"))
    }

    @Test
    fun credentialsAreStrippedFromCustomKeys() {
        reporter.report(
            IllegalStateException("kaputt"),
            mapOf("url" to "http://s/series/alice/s3cret/1.mkv")
        )

        val value = sink.keys.getValue("url")
        assertFalse("Username geleakt: $value", value.contains("alice"))
        assertFalse("Passwort geleakt: $value", value.contains("s3cret"))
    }

    @Test
    fun reporterNeverThrowsWhenSinkFails() {
        // Telemetrie darf die App niemals crashen lassen.
        val failing = object : CrashlyticsSink {
            override fun recordException(throwable: Throwable) = error("Firebase nicht verfügbar")
            override fun setCustomKey(key: String, value: String) = error("Firebase nicht verfügbar")
        }
        val resilient = CrashlyticsErrorReporter(failing, { now })

        resilient.report(IllegalStateException("kaputt"), mapOf("k" to "v"))
        // kein Throw = Test bestanden
        assertTrue(true)
    }

    private fun sameError(): Throwable {
        val error = IllegalStateException("immer derselbe")
        error.stackTrace = arrayOf(
            StackTraceElement("com.flintr.tv.Foo", "bar", "Foo.kt", 42)
        )
        return error
    }
}
