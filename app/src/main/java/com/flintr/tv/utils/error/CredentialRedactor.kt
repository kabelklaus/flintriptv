package com.flintr.tv.utils.error

/**
 * Removes Xtream credentials from text before it leaves the device.
 *
 * Credentials appear in two shapes in this app:
 *  - API calls: `player_api.php?username=alice&password=s3cret&action=...`
 *  - Stream URLs: `<base>/series/alice/s3cret/12345.mkv` (also `live`, `movie`)
 *
 * Exception messages routinely carry the failing URL, so anything sent to a
 * crash reporter must pass through here first.
 */
object CredentialRedactor {

    private const val MASK = "***"
    private const val MAX_CAUSE_DEPTH = 10

    // Order matters: longer alternatives first so "username" wins over "user".
    private val queryParamRegex = Regex(
        "((?:username|password|pass|user)=)([^&\\s\"']*)",
        RegexOption.IGNORE_CASE
    )

    private val streamPathRegex = Regex(
        "(/(?:series|live|movie|hls)/)([^/\\s\"']+)/([^/\\s\"']+)",
        RegexOption.IGNORE_CASE
    )

    fun redact(text: String?): String? {
        if (text.isNullOrEmpty()) return text
        return text
            .replace(queryParamRegex) { "${it.groupValues[1]}$MASK" }
            .replace(streamPathRegex) { "${it.groupValues[1]}$MASK/$MASK" }
    }

    /**
     * Returns [throwable] unchanged when it carries no credentials, so Crashlytics
     * keeps the original exception type for grouping. Only when redaction actually
     * changes something is a sanitized stand-in returned; it keeps the original
     * stack trace and names the original type so grouping stays meaningful.
     */
    fun redact(throwable: Throwable): Throwable {
        if (!containsCredentials(throwable)) return throwable
        return sanitize(throwable, depth = 0)
    }

    private fun containsCredentials(throwable: Throwable): Boolean {
        var current: Throwable? = throwable
        // Identity-based set: guards against cyclic cause chains.
        val seen = mutableSetOf<Throwable>()
        while (current != null && seen.add(current)) {
            val message = current.message
            if (message != null && redact(message) != message) return true
            current = current.cause
        }
        return false
    }

    private fun sanitize(throwable: Throwable, depth: Int): Throwable {
        val cause = throwable.cause
        val sanitizedCause = if (depth < MAX_CAUSE_DEPTH && cause != null && cause !== throwable) {
            sanitize(cause, depth + 1)
        } else {
            null
        }
        return RedactedThrowable(
            originalType = throwable.javaClass.name,
            redactedMessage = redact(throwable.message),
            cause = sanitizedCause
        ).apply { stackTrace = throwable.stackTrace }
    }
}

/**
 * Stand-in for a throwable whose message contained credentials.
 * Names the original type so Crashlytics grouping stays meaningful.
 */
class RedactedThrowable(
    originalType: String,
    redactedMessage: String?,
    cause: Throwable?
) : Exception("$originalType: ${redactedMessage ?: "(no message)"}", cause)
