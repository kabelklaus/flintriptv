package com.flintr.tv

import android.app.Application
import android.util.Log
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
            val bundle = android.os.Bundle().apply {
                putString("app_version", try {
                    packageManager.getPackageInfo(packageName, 0).versionName
                } catch (e: Exception) {
                    "unknown"
                })
            }
            firebaseAnalytics.logEvent("app_initialized", bundle)
            Log.d(TAG, "Firebase test event 'app_initialized' sent")
        } catch (e: Exception) {
            Log.e(TAG, "Firebase Analytics initialization failed", e)
        }
    }
    
    companion object {
        private const val TAG = "FlintRTVApplication"
    }
}

