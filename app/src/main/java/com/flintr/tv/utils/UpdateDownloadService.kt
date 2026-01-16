package com.flintr.tv.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
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
        
        deleteFileSafely(outputFile)
        
        try {
            val request = createDownloadRequest(downloadUrl)
            val response = okHttpClient.newCall(request).execute()
            
            val body = validateResponse(response) ?: return@flow
            
            val totalBytes = body.contentLength()
            
            body.byteStream().use { inputStream ->
                FileOutputStream(outputFile).use { outputStream ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var bytesDownloaded = 0L
                    
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        bytesDownloaded += bytesRead
                        emit(DownloadProgress(bytesDownloaded, totalBytes, false))
                    }
                }
            }
            
            if (!isValidApk(outputFile)) {
                deleteFileSafely(outputFile)
                emit(DownloadProgress(0, 0, false, Exception("Downloaded file is not a valid APK")))
                return@flow
            }
            
            emit(DownloadProgress(totalBytes, totalBytes, true))
            
        } catch (e: Exception) {
            deleteFileSafely(outputFile)
            emit(DownloadProgress(0, 0, false, e))
        }
    }.flowOn(Dispatchers.IO)
    
    private fun deleteFileSafely(file: File): Boolean {
        return if (file.exists()) {
            file.delete().also { deleted ->
                if (!deleted) {
                    Log.w("UpdateDownloadService", "Failed to delete file: ${file.name}")
                }
            }
        } else {
            true
        }
    }
    
    private fun createDownloadRequest(url: String): Request {
        return Request.Builder()
            .url(url)
            .header("Accept", "application/octet-stream")
            .build()
    }
    
    private suspend fun FlowCollector<DownloadProgress>.validateResponse(
        response: Response
    ): ResponseBody? {
        if (!response.isSuccessful) {
            emit(DownloadProgress(0, 0, false, Exception("HTTP ${response.code}: ${response.message}")))
            response.close()
            return null
        }
        
        val body = response.body
        if (body == null) {
            emit(DownloadProgress(0, 0, false, Exception("Response body is null")))
            response.close()
            return null
        }
        
        return body
    }
    
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
    
    fun deleteDownloadedApk(version: String): Boolean {
        val fileName = "app-v$version.apk"
        val cacheDir = context.cacheDir
        val file = File(cacheDir, fileName)
        return deleteFileSafely(file)
    }
}
