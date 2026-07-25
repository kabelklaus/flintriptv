package com.flintr.tv

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.os.bundleOf
import com.flintr.tv.utils.error.AppErrorReporter
import com.flintr.tv.utils.error.ErrorReporter
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import com.flintr.tv.utils.error.reportError
import com.flintr.tv.utils.error.reportWarning

@HiltAndroidApp
class FlintRTVApplication : Application() {
    // Auto-check for updates is handled in the Settings screen
    // This keeps the implementation simple and avoids Hilt entry point complexity

    @Inject
    lateinit var errorReporter: ErrorReporter

    override fun onCreate() {
        super.onCreate()

        // Wire the reporter before anything else can fail, so early errors are
        // not lost. Composables and top-level helpers report through this facade;
        // Hilt-built classes inject ErrorReporter and reach the same instance.
        try {
            FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = true
            AppErrorReporter.install(errorReporter)
            Log.d(TAG, "Crashlytics error reporting installed")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install error reporting", e)
        }

        // Initialize Firebase Analytics and test connection
        try {
            val firebaseAnalytics = FirebaseAnalytics.getInstance(this)
            Log.d(TAG, "Firebase Analytics initialized successfully")
            
            // Send test event to verify connection
            val appVersion = try {
                val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getPackageInfo(packageName, 0)
                }
                packageInfo.versionName
            } catch (e: PackageManager.NameNotFoundException) {
                reportWarning(TAG, "Unable to get app version name", e)
                "unknown"
            }
            val bundle = bundleOf(PARAM_APP_VERSION to appVersion)
            firebaseAnalytics.logEvent(EVENT_APP_INITIALIZED, bundle)
            Log.d(TAG, "Firebase test event 'app_initialized' sent")
        } catch (e: Exception) {
            reportError(TAG, "Firebase Analytics initialization failed", e)
        }
    }
    
    companion object {
        private const val TAG = "FlintRTVApplication"
        private const val EVENT_APP_INITIALIZED = "app_initialized"
        private const val PARAM_APP_VERSION = "app_version"
    }
}

