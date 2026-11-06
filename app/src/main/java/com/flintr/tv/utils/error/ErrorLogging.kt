package com.flintr.tv.utils.error

import android.util.Log

/**
 * Log *and* report. Replaces the `Log.e(TAG, msg, e)` dead ends where a handled
 * exception used to reach nothing but logcat — invisible once the device
 * restarts, which is why a JSON parse failure could only be reported by photo.
 *
 * Logging behaviour is unchanged; the reporting is additive.
 */

private const val KEY_CONTEXT = "context"
private const val KEY_TAG = "tag"
private const val KEY_KIND = "kind"
private const val KIND_CONTROL_FLOW = "control-flow"

private fun keysWith(
    context: String,
    tag: String?,
    keys: Map<String, String>
): Map<String, String> = buildMap {
    putAll(keys)
    put(KEY_CONTEXT, context)
    tag?.let { put(KEY_TAG, it) }
}

/** Logs at error level and reports. */
fun reportError(
    tag: String,
    message: String,
    throwable: Throwable,
    keys: Map<String, String> = emptyMap()
) {
    Log.e(tag, message, throwable)
    AppErrorReporter.report(throwable, keysWith(message, tag, keys))
}

/** Logs at warning level and reports. */
fun reportWarning(
    tag: String,
    message: String,
    throwable: Throwable,
    keys: Map<String, String> = emptyMap()
) {
    Log.w(tag, message, throwable)
    AppErrorReporter.report(throwable, keysWith(message, tag, keys))
}

/**
 * Reports without logging — for catch blocks that intentionally stay quiet
 * (e.g. reflection fallbacks used as control flow). Deduplication in the
 * reporter keeps these from flooding, so a per-frame path costs one report.
 */
fun reportHandled(
    context: String,
    throwable: Throwable,
    keys: Map<String, String> = emptyMap()
) {
    AppErrorReporter.report(throwable, keysWith(context, null, keys))
}

/**
 * For sites where an expected exception is used as control flow — a
 * FocusRequester not yet attached, a gesture timeout, a reflection fallback.
 * These are reported too, but tagged `kind=control-flow` so they stay
 * separable in the console, and they can be muted as a group via
 * [AppErrorReporter.controlFlowReportingEnabled] without affecting real errors.
 */
fun reportControlFlow(
    context: String,
    throwable: Throwable,
    keys: Map<String, String> = emptyMap()
) {
    if (!AppErrorReporter.controlFlowReportingEnabled) return
    AppErrorReporter.report(throwable, keysWith(context, null, keys) + (KEY_KIND to KIND_CONTROL_FLOW))
}
