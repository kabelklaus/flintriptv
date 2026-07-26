package com.flintr.tv.utils.error

/**
 * Process-wide entry point for call sites that cannot receive an injected
 * [ErrorReporter] — Composables and top-level utility functions.
 *
 * Classes built by Hilt should inject [ErrorReporter] instead; both routes end
 * up at the same instance, which [install] wires up during app startup. Until
 * then this is a no-op, so unit tests need no Firebase.
 */
object AppErrorReporter : ErrorReporter {

    @Volatile
    private var delegate: ErrorReporter? = null

    /**
     * Whether expected-exception-as-control-flow sites are reported
     * (see [reportControlFlow]). Set to false to mute that category without
     * touching real error reporting.
     */
    @Volatile
    var controlFlowReportingEnabled: Boolean = true

    fun install(reporter: ErrorReporter) {
        delegate = reporter
    }

    /** Visible for tests: drops the installed delegate. */
    fun reset() {
        delegate = null
    }

    override fun report(throwable: Throwable, keys: Map<String, String>) {
        delegate?.report(throwable, keys)
    }
}
