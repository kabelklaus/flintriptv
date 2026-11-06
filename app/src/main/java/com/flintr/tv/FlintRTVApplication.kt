package com.flintr.tv

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.os.bundleOf
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.flintr.tv.utils.error.AppErrorReporter
import com.flintr.tv.utils.error.ErrorReporter
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.flintr.tv.utils.error.reportError
import com.flintr.tv.utils.error.reportWarning

@HiltAndroidApp
class FlintRTVApplication : Application(), ImageLoaderFactory {
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
    
    private var imageLoader: ImageLoader? = null

    override fun newImageLoader(): ImageLoader =
        imageLoader ?: buildFlintrImageLoader(this).also { imageLoader = it }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level == ComponentCallbacks2.TRIM_MEMORY_COMPLETE
        ) {
            imageLoader?.memoryCache?.clear()
        }
    }

    companion object {
        private const val TAG = "FlintRTVApplication"
        private const val EVENT_APP_INITIALIZED = "app_initialized"
        private const val PARAM_APP_VERSION = "app_version"
    }
}

/** Max concurrent bitmap decodes; leaves the other cores of a 4-core box to the UI/render threads. */
internal const val IMAGE_DECODE_PARALLELISM = 2

/**
 * Coil loader tuned for scrolling large poster grids on weak TV boxes (ANR at load ~5 on 4 cores):
 * - decoding is capped at [IMAGE_DECODE_PARALLELISM] so a fast scroll can't saturate every core
 *   and starve the main thread's input dispatch,
 * - RGB_565 halves bitmap memory/bandwidth for opaque posters (fewer GCs while recycling),
 * - no crossfade: every visible tile would otherwise animate for its first frames after a scroll,
 * - cache headers are ignored so posters come from the disk cache instead of being re-fetched.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun buildFlintrImageLoader(context: Context): ImageLoader =
    ImageLoader.Builder(context)
        .memoryCache { MemoryCache.Builder(context).maxSizePercent(0.15).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve("images"))
                .maxSizeBytes(250L * 1024 * 1024)
                .build()
        }
        .decoderDispatcher(Dispatchers.IO.limitedParallelism(IMAGE_DECODE_PARALLELISM))
        .allowRgb565(true)
        .crossfade(false)
        .respectCacheHeaders(false)
        .build()

