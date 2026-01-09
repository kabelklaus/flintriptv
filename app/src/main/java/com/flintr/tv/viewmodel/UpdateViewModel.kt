package com.flintr.tv.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flintr.tv.data.model.UpdateInfo
import com.flintr.tv.data.repository.UpdateRepository
import com.flintr.tv.utils.DownloadProgress
import com.flintr.tv.utils.UpdateDownloadService
import com.flintr.tv.utils.UpdateInstaller
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(
    val updateRepository: UpdateRepository,
    val downloadService: UpdateDownloadService,
    val installer: UpdateInstaller
) : ViewModel() {
    
    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()
    
    private val _isCheckingUpdates = MutableStateFlow(false)
    val isCheckingUpdates: StateFlow<Boolean> = _isCheckingUpdates.asStateFlow()
    
    private val _downloadProgress = MutableStateFlow<DownloadProgress?>(null)
    val downloadProgress: StateFlow<DownloadProgress?> = _downloadProgress.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    fun checkForUpdates(currentVersion: String) {
        viewModelScope.launch {
            _isCheckingUpdates.value = true
            _errorMessage.value = null
            
            try {
                val info = updateRepository.checkForUpdates(currentVersion)
                _updateInfo.value = info
            } catch (e: Exception) {
                _errorMessage.value = "Fehler beim Prüfen auf Updates: ${e.message}"
            } finally {
                _isCheckingUpdates.value = false
            }
        }
    }
    
    fun downloadUpdate(downloadUrl: String, version: String) {
        viewModelScope.launch {
            _errorMessage.value = null
            _downloadProgress.value = DownloadProgress(0, 0, false)
            
            downloadService.downloadApk(downloadUrl, version).collect { progress ->
                _downloadProgress.value = progress
                
                if (progress.isComplete) {
                    // Install automatically after download
                    installUpdate(version)
                } else if (progress.error != null) {
                    _errorMessage.value = "Download-Fehler: ${progress.error.message}"
                }
            }
        }
    }
    
    private fun installUpdate(version: String) {
        viewModelScope.launch {
            val apkFile = downloadService.getDownloadedApkFile(version)
            if (apkFile != null) {
                val success = installer.installApk(apkFile)
                if (!success) {
                    _errorMessage.value = "Installation fehlgeschlagen"
                }
            } else {
                _errorMessage.value = "APK-Datei nicht gefunden"
            }
        }
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
}
