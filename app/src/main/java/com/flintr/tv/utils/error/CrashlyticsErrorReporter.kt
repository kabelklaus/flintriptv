package com.flintr.tv.utils.error

import java.util.concurrent.ConcurrentHashMap

/**
 * Reports every handled error, deduplicated.
 *
 * No filtering by exception type: network errors, parse errors and reflection
 * fallbacks all get reported. What is suppressed is *repetition* — the same
 * error signature is sent once per [dedupWindowMs]. Without that, a per-frame
 * code path would exhaust the per-session non-fatal budget and crowd out the
 * errors that matter.
 */
class CrashlyticsErrorReporter(
    private val sink: CrashlyticsSink,
    private val clock: () -> Long = System::currentTimeMillis,
    private val dedupWindowMs: Long = DEFAULT_DEDUP_WINDOW_MS,
    private val maxDistinctPerSession: Int = DEFAULT_MAX_DISTINCT_PER_SESSION
) : ErrorReporter {

    private val lastReportedAt = ConcurrentHashMap<String, Long>()

    override fun report(throwable: Throwable, keys: Map<String, String>) {
        // Telemetry must never take the app down.
        try {
            if (!shouldReport(throwable)) return

            keys.forEach { (key, value) ->
                CredentialRedactor.redact(value)?.let { sink.setCustomKey(key, it) }
            }
            sink.recordException(CredentialRedactor.redact(throwable))
        } catch (_: Exception) {
            // Swallowing here is deliberate: a broken reporter must stay invisible.
        }
    }

    private fun shouldReport(throwable: Throwable): Boolean {
        val signature = signatureOf(throwable)
        val now = clock()
        synchronized(lastReportedAt) {
            val previous = lastReportedAt[signature]
            if (previous != null && now - previous < dedupWindowMs) return false
            // Cap only applies to signatures we have not seen before.
            if (previous == null && lastReportedAt.size >= maxDistinctPerSession) return false
            lastReportedAt[signature] = now
            return true
        }
    }

    private fun signatureOf(throwable: Throwable): String {
        val topFrame = throwable.stackTrace.firstOrNull()
            ?.let { "${it.className}.${it.methodName}:${it.lineNumber}" }
            ?: "unknown"
        return "${throwable.javaClass.name}|${throwable.message}|$topFrame"
    }

    companion object {
        const val DEFAULT_DEDUP_WINDOW_MS = 5 * 60 * 1000L
        const val DEFAULT_MAX_DISTINCT_PER_SESSION = 200
    }
}
