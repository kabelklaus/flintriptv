package com.flintr.tv.utils

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.flintr.tv.utils.error.reportError

/**
 * Data class for package information
 */
data class PackageInfoData(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Int
)

/**
 * Load package information from PackageManager
 * Returns null if loading fails
 */
suspend fun loadPackageInfo(
    context: Context,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
): PackageInfoData? = withContext(dispatcher) {
    try {
        val packageManager = context.packageManager
        val packageName = context.packageName
        val packageInfo: PackageInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
        
        val appName = packageManager.getApplicationLabel(
            packageManager.getApplicationInfo(packageName, 0)
        ).toString()
        
        PackageInfoData(
            appName = appName,
            packageName = packageName,
            versionName = packageInfo.versionName ?: "Unknown",
            versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
        )
    } catch (e: Exception) {
        reportError("PackageInfoUtils", "Failed to load package info", e)
        null
    }
}
