package com.flintr.tv.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flintr.tv.data.local.CredentialsRepository
import com.flintr.tv.data.model.ServerInfo
import com.flintr.tv.data.repository.XtreamRepository
import com.flintr.tv.di.AppModule
import com.flintr.tv.data.api.XtreamApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import com.squareup.moshi.Moshi
import javax.inject.Inject

data class ConnectionTestState(
    val isLoading: Boolean = false,
    val serverInfo: ServerInfo? = null,
    val error: String? = null
)

@HiltViewModel
class SettingsConnectionViewModel @Inject constructor(
    private val repository: XtreamRepository,
    private val credentialsRepository: CredentialsRepository,
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi
) : ViewModel() {

    private val _connectionTestState = MutableStateFlow(ConnectionTestState())
    val connectionTestState: StateFlow<ConnectionTestState> = _connectionTestState.asStateFlow()

    init {
        // Load server info automatically if credentials are available
        loadServerInfoIfAvailable()
    }

    /**
     * Loads server info automatically if credentials are already saved.
     */
    private fun loadServerInfoIfAvailable() {
        val credentials = credentialsRepository.getCredentials() ?: return
        
        viewModelScope.launch {
            try {
                repository.getServerInfo(credentials.username, credentials.password)
                    .onSuccess { serverInfo ->
                        _connectionTestState.value = ConnectionTestState(
                            isLoading = false,
                            serverInfo = serverInfo
                        )
                        Log.d("SettingsConnectionViewModel", "Server info loaded automatically")
                    }
                    .onFailure { error ->
                        Log.w("SettingsConnectionViewModel", "Failed to load server info automatically", error)
                        // Don't set error state here, just log it - user can test connection manually
                    }
            } catch (e: Exception) {
                Log.w("SettingsConnectionViewModel", "Error loading server info automatically", e)
            }
        }
    }

    /**
     * Reloads server info based on currently saved credentials.
     * Public method that can be called from UI to refresh server information.
     */
    fun reloadServerInfo() {
        val credentials = credentialsRepository.getCredentials() ?: run {
            _connectionTestState.value = ConnectionTestState(
                error = "Keine gespeicherten Anmeldedaten gefunden"
            )
            return
        }
        
        viewModelScope.launch {
            _connectionTestState.value = ConnectionTestState(isLoading = true, error = null)
            
            try {
                repository.getServerInfo(credentials.username, credentials.password)
                    .onSuccess { serverInfo ->
                        _connectionTestState.value = ConnectionTestState(
                            isLoading = false,
                            serverInfo = serverInfo
                        )
                        Log.d("SettingsConnectionViewModel", "Server info reloaded successfully")
                    }
                    .onFailure { error ->
                        _connectionTestState.value = ConnectionTestState(
                            isLoading = false,
                            error = "Fehler beim Neuladen: ${error.message}"
                        )
                        Log.w("SettingsConnectionViewModel", "Failed to reload server info", error)
                    }
            } catch (e: Exception) {
                _connectionTestState.value = ConnectionTestState(
                    isLoading = false,
                    error = "Fehler beim Neuladen: ${e.message}"
                )
                Log.e("SettingsConnectionViewModel", "Error reloading server info", e)
            }
        }
    }

    /**
     * Tests connection with provided credentials and loads server info.
     */
    fun testConnection(serverUrl: String, username: String, password: String) {
        if (serverUrl.isBlank() || username.isBlank() || password.isBlank()) {
            _connectionTestState.value = ConnectionTestState(
                error = "Bitte füllen Sie alle Felder aus"
            )
            return
        }

        viewModelScope.launch {
            _connectionTestState.value = ConnectionTestState(isLoading = true, error = null)
            
            try {
                // Create temporary Retrofit instance for validation
                val retrofit = AppModule.createRetrofitForUrl(serverUrl, okHttpClient, moshi)
                val apiService = retrofit.create(XtreamApiService::class.java)
                
                // Test connection by calling getServerInfo
                val response = apiService.getServerInfo(username, password)
                
                if (response.isSuccessful && response.body() != null) {
                    val serverInfo = response.body()!!
                    _connectionTestState.value = ConnectionTestState(
                        isLoading = false,
                        serverInfo = serverInfo
                    )
                    Log.d("SettingsConnectionViewModel", "Connection test successful")
                } else {
                    _connectionTestState.value = ConnectionTestState(
                        isLoading = false,
                        error = "Ungültige Anmeldedaten (Code: ${response.code()})"
                    )
                    Log.e("SettingsConnectionViewModel", "Connection test failed: ${response.code()}")
                }
            } catch (e: Exception) {
                _connectionTestState.value = ConnectionTestState(
                    isLoading = false,
                    error = "Verbindungsfehler: ${e.message}"
                )
                Log.e("SettingsConnectionViewModel", "Connection test error", e)
            }
        }
    }

    fun clearError() {
        _connectionTestState.value = _connectionTestState.value.copy(error = null)
    }

    fun clearServerInfo() {
        _connectionTestState.value = _connectionTestState.value.copy(serverInfo = null)
    }
}
