package com.flintr.tv.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateInstaller @Inject constructor(
    private val context: Context
) {
    
    fun installApk(apkFile: File): Boolean {
        if (!apkFile.exists()) {
            return false
        }
        
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Use PackageInstaller for Android 8.0+
                installWithPackageInstaller(apkFile)
            } else {
                // Use Intent for older versions
                installWithIntent(apkFile)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    private fun installWithIntent(apkFile: File): Boolean {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile
                )
            } else {
                Uri.fromFile(apkFile)
            }
            
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    @Suppress("DEPRECATION")
    private fun installWithPackageInstaller(apkFile: File): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return installWithIntent(apkFile)
        }
        
        return try {
            val packageInstaller = context.packageManager.packageInstaller
            val sessionParams = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            )
            
            val sessionId = packageInstaller.createSession(sessionParams)
            val session = packageInstaller.openSession(sessionId)
            
            val inputStream = FileInputStream(apkFile)
            val outputStream = session.openWrite("package", 0, -1)
            
            val buffer = ByteArray(65536)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
            
            session.fsync(outputStream)
            inputStream.close()
            outputStream.close()
            
            // Create install intent (optional - installation works without receiver)
            val intent = Intent("com.flintr.tv.INSTALL_COMPLETE").apply {
                setPackage(context.packageName)
            }
            val pendingIntent = android.app.PendingIntent.getBroadcast(
                context,
                0,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            
            session.commit(pendingIntent.intentSender)
            session.close()
            
            true
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to Intent method
            installWithIntent(apkFile)
        }
    }
}
