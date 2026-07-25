package com.flintr.tv.utils.error

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Credentials dürfen das Gerät nie verlassen. Xtream trägt Username und Passwort
 * sowohl als Query-Parameter (player_api.php) als auch im Stream-Pfad.
 */
class CredentialRedactorTest {

    @Test
    fun masksQueryParameterCredentials() {
        val input = "HTTP 404 http://srv.example/player_api.php" +
            "?username=alice&password=s3cret&action=get_series_info&series_id=7"

        val result = CredentialRedactor.redact(input)

        assertFalse("Username geleakt: $result", result!!.contains("alice"))
        assertFalse("Passwort geleakt: $result", result.contains("s3cret"))
        // Nicht-sensible Parameter bleiben zur Diagnose erhalten
        assertTrue(result.contains("get_series_info"))
        assertTrue(result.contains("series_id=7"))
        assertTrue(result.contains("srv.example"))
    }

    @Test
    fun masksStreamPathCredentials() {
        val input = "Fehler beim Laden: http://srv.example/series/alice/s3cret/12345.mkv"

        val result = CredentialRedactor.redact(input)

        assertFalse("Username geleakt: $result", result!!.contains("alice"))
        assertFalse("Passwort geleakt: $result", result.contains("s3cret"))
        // Stream-ID und Endung bleiben erhalten
        assertTrue(result.contains("12345.mkv"))
    }

    @Test
    fun masksLiveAndMoviePaths() {
        val live = CredentialRedactor.redact("http://s/live/bob/pw123/9.ts")
        val movie = CredentialRedactor.redact("http://s/movie/bob/pw123/9.mp4")

        assertFalse(live!!.contains("bob"))
        assertFalse(live.contains("pw123"))
        assertFalse(movie!!.contains("bob"))
        assertFalse(movie.contains("pw123"))
    }

    @Test
    fun leavesHarmlessTextUntouched() {
        val input = "Expected BEGIN_OBJECT but was BEGIN_ARRAY at path \$.episodes.21[133].info"

        assertEquals(input, CredentialRedactor.redact(input))
    }

    @Test
    fun handlesNullAndEmpty() {
        assertNull(CredentialRedactor.redact(null as String?))
        assertEquals("", CredentialRedactor.redact(""))
    }

    @Test
    fun throwableWithoutCredentialsIsReturnedUnchanged() {
        // Wichtig für Crashlytics-Gruppierung: Originaltyp muss erhalten bleiben.
        val original = IllegalStateException("harmloser Fehler")

        assertSame(original, CredentialRedactor.redact(original))
    }

    @Test
    fun throwableWithCredentialsIsSanitizedKeepingTypeAndStack() {
        val original = IllegalStateException("GET http://s/player_api.php?username=alice&password=s3cret")

        val result = CredentialRedactor.redact(original)

        assertTrue("Sollte ersetzt werden", result !== original)
        assertFalse("Username geleakt: ${result.message}", result.message!!.contains("alice"))
        assertFalse("Passwort geleakt: ${result.message}", result.message!!.contains("s3cret"))
        // Originaltyp im Text, damit die Gruppierung in der Console brauchbar bleibt
        assertTrue(result.message!!.contains("IllegalStateException"))
        // Stacktrace muss erhalten bleiben, sonst ist der Report wertlos
        assertArrayEqualsStack(original.stackTrace, result.stackTrace)
    }

    @Test
    fun sanitizesCredentialsInCauseChain() {
        val cause = IllegalStateException("http://s/live/alice/s3cret/1.ts")
        val outer = RuntimeException("Laden fehlgeschlagen", cause)

        val result = CredentialRedactor.redact(outer)

        val flattened = generateSequence(result) { it.cause }
            .joinToString(" | ") { it.message ?: "" }
        assertFalse("Username geleakt: $flattened", flattened.contains("alice"))
        assertFalse("Passwort geleakt: $flattened", flattened.contains("s3cret"))
        assertNotNull(result.cause)
    }

    @Test
    fun survivesSelfReferencingCause() {
        // Defensiv: zyklische cause-Ketten dürfen nicht in eine Endlosschleife laufen.
        val selfReferencing = object : RuntimeException("harmlos") {
            override val cause: Throwable get() = this
        }

        assertSame(selfReferencing, CredentialRedactor.redact(selfReferencing))
    }

    private fun assertArrayEqualsStack(
        expected: Array<StackTraceElement>,
        actual: Array<StackTraceElement>
    ) {
        assertEquals(expected.size, actual.size)
        expected.indices.forEach { assertEquals(expected[it], actual[it]) }
    }
}
