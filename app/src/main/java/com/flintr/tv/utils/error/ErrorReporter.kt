package com.flintr.tv.utils.error

/**
 * Reports handled errors so they become visible outside the device.
 * Uncaught exceptions are captured by Crashlytics automatically; this covers
 * everything the app catches and would otherwise only write to logcat.
 */
interface ErrorReporter {
    fun report(throwable: Throwable, keys: Map<String, String> = emptyMap())
}

/** Thin seam over FirebaseCrashlytics so the reporter stays unit-testable. */
interface CrashlyticsSink {
    fun recordException(throwable: Throwable)
    fun setCustomKey(key: String, value: String)
}
