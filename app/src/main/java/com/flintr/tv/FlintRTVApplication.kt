package com.flintr.tv

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FlintRTVApplication : Application() {
    // Auto-check for updates is handled in the Settings screen
    // This keeps the implementation simple and avoids Hilt entry point complexity
}

