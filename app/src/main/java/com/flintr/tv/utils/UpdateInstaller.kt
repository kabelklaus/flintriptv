package com.flintr.tv.utils

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import com.flintr.tv.utils.error.reportError

@Singleton
class UpdateInstaller @Inject constructor(
    private val context: Context
) {
    
    fun installApk(apkFile: File): Boolean {
        if (!apkFile.exists()) {
            return false
        }
        
        // Always use Intent-based installation to show system installation dialog
        // PackageInstaller is for silent installations (requires special permissions)
        return installWithIntent(apkFile)
    }
    
    private fun installWithIntent(apkFile: File): Boolean {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            reportError("UpdateInstaller", "Failed to install APK", e)
            false
        }
    }
}
