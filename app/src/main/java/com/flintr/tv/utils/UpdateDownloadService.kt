package com.flintr.tv.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class DownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val isComplete: Boolean,
    val error: Throwable? = null
) {
    val progress: Float
        get() = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes.toFloat() else 0f
}

@Singleton
class UpdateDownloadService @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val context: Context
) {
    
    suspend fun downloadApk(
        downloadUrl: String,
        version: String
    ): Flow<DownloadProgress> = flow {
        val fileName = "app-v$version.apk"
        val cacheDir = context.cacheDir
        val outputFile = File(cacheDir, fileName)
        
        // Delete existing file if present
        if (outputFile.exists()) {
            outputFile.delete()
        }
        
        try {
            // Token is already added by the GitHub OkHttpClient interceptor
            val request = Request.Builder()
                .url(downloadUrl)
                .header("Accept", "application/octet-stream")
                .build()
            
            val response = okHttpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                emit(DownloadProgress(0, 0, false, Exception("HTTP ${response.code}: ${response.message}")))
                return@flow
            }
            
            val body = response.body
            if (body == null) {
                emit(DownloadProgress(0, 0, false, Exception("Response body is null")))
                return@flow
            }
            
            val totalBytes = body.contentLength()
            var bytesDownloaded = 0L
            
            body.byteStream().use { inputStream ->
                FileOutputStream(outputFile).use { outputStream ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        bytesDownloaded += bytesRead
                        
                        // Emit progress
                        emit(DownloadProgress(bytesDownloaded, totalBytes, false))
                    }
                }
            }
            
            // Validate APK file
            if (!isValidApk(outputFile)) {
                outputFile.delete()
                emit(DownloadProgress(0, 0, false, Exception("Downloaded file is not a valid APK")))
                return@flow
            }
            
            emit(DownloadProgress(totalBytes, totalBytes, true))
            
        } catch (e: Exception) {
            outputFile.delete()
            emit(DownloadProgress(0, 0, false, e))
        }
    }.flowOn(Dispatchers.IO)
    
    fun getDownloadedApkFile(version: String): File? {
        val fileName = "app-v$version.apk"
        val cacheDir = context.cacheDir
        val file = File(cacheDir, fileName)
        return if (file.exists() && isValidApk(file)) file else null
    }
    
    private fun isValidApk(file: File): Boolean {
        if (!file.exists() || file.length() == 0L) {
            return false
        }
        
        // Check if file starts with APK magic bytes (ZIP file signature)
        return try {
            val bytes = ByteArray(4)
            file.inputStream().use { it.read(bytes) }
            bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() && 
            (bytes[2] == 0x03.toByte() || bytes[2] == 0x05.toByte() || bytes[2] == 0x07.toByte()) &&
            (bytes[3] == 0x04.toByte() || bytes[3] == 0x06.toByte() || bytes[3] == 0x08.toByte())
        } catch (e: Exception) {
            false
        }
    }
    
    fun deleteDownloadedApk(version: String) {
        val fileName = "app-v$version.apk"
        val cacheDir = context.cacheDir
        val file = File(cacheDir, fileName)
        if (file.exists()) {
            file.delete()
        }
    }
}
