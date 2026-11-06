package com.flintr.tv.utils.error

import com.google.firebase.crashlytics.FirebaseCrashlytics

/** Real sink backed by Firebase Crashlytics. */
class FirebaseCrashlyticsSink : CrashlyticsSink {

    private val crashlytics: FirebaseCrashlytics
        get() = FirebaseCrashlytics.getInstance()

    override fun recordException(throwable: Throwable) {
        crashlytics.recordException(throwable)
    }

    override fun setCustomKey(key: String, value: String) {
        crashlytics.setCustomKey(key, value)
    }
}
