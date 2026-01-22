package com.flintr.tv

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.os.bundleOf
import com.google.firebase.analytics.FirebaseAnalytics
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FlintRTVApplication : Application() {
    // Auto-check for updates is handled in the Settings screen
    // This keeps the implementation simple and avoids Hilt entry point complexity
    
    override fun onCreate() {
        super.onCreate()
        
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
                Log.w(TAG, "Unable to get app version name", e)
                "unknown"
            }
            val bundle = bundleOf(PARAM_APP_VERSION to appVersion)
            firebaseAnalytics.logEvent(EVENT_APP_INITIALIZED, bundle)
            Log.d(TAG, "Firebase test event 'app_initialized' sent")
        } catch (e: Exception) {
            Log.e(TAG, "Firebase Analytics initialization failed", e)
        }
    }
    
    companion object {
        private const val TAG = "FlintRTVApplication"
        private const val EVENT_APP_INITIALIZED = "app_initialized"
        private const val PARAM_APP_VERSION = "app_version"
    }
}

