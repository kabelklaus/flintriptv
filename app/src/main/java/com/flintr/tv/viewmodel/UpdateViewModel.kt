package com.flintr.tv.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flintr.tv.R
import com.flintr.tv.data.model.UpdateInfo
import com.flintr.tv.data.repository.UpdateRepository
import com.flintr.tv.di.ResourcesProvider
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
    val installer: UpdateInstaller,
    private val resourcesProvider: ResourcesProvider
) : ViewModel() {
    
    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()
    
    private val _isCheckingUpdates = MutableStateFlow(false)
    val isCheckingUpdates: StateFlow<Boolean> = _isCheckingUpdates.asStateFlow()
    
    private val _downloadProgress = MutableStateFlow<DownloadProgress?>(null)
    val downloadProgress: StateFlow<DownloadProgress?> = _downloadProgress.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val _currentVersion = MutableStateFlow<String?>(null)
    val currentVersion: StateFlow<String?> = _currentVersion.asStateFlow()
    
    fun checkForUpdates(currentVersion: String) {
        viewModelScope.launch {
            _isCheckingUpdates.value = true
            _errorMessage.value = null
            _currentVersion.value = currentVersion
            
            try {
                val info = updateRepository.checkForUpdates(currentVersion)
                _updateInfo.value = info
            } catch (e: Exception) {
                _errorMessage.value = resourcesProvider.getString(
                    R.string.error_checking_updates,
                    e.message ?: "Unknown error"
                )
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
                    _errorMessage.value = resourcesProvider.getString(
                        R.string.download_error,
                        progress.error.message ?: "Unknown error"
                    )
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
                    _errorMessage.value = resourcesProvider.getString(R.string.installation_failed)
                }
            } else {
                _errorMessage.value = resourcesProvider.getString(R.string.apk_not_found)
            }
        }
    }
    
    fun clearError() {
        _errorMessage.value = null
    }
}
