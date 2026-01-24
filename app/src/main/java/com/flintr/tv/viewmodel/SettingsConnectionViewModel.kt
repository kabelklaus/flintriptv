package com.flintr.tv.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flintr.tv.data.local.CredentialsRepository
import com.flintr.tv.data.local.DataCacheService
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
    val error: String? = null,
    val isRefillingCache: Boolean = false,
    val cacheRefillMessage: String? = null,
    val cacheRefillSuccess: Boolean = false
)

@HiltViewModel
class SettingsConnectionViewModel @Inject constructor(
    private val repository: XtreamRepository,
    private val credentialsRepository: CredentialsRepository,
    private val cacheService: DataCacheService,
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
     * Reloads server info and cache data based on currently saved credentials.
     * Public method that can be called from UI to refresh server information and refill cache.
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
                // Reload server info
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
                
                // Reload and refill cache in background
                Log.d("SettingsConnectionViewModel", "Starting cache refill...")
                refillCache(credentials.username, credentials.password)
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
     * Refills the cache by loading all data from API and saving it.
     */
    private suspend fun refillCache(username: String, password: String) {
        try {
            _connectionTestState.value = _connectionTestState.value.copy(
                isRefillingCache = true,
                cacheRefillMessage = "Lade Kanäle...",
                cacheRefillSuccess = false
            )
            
            Log.d("SettingsConnectionViewModel", "Loading channels from API...")
            val liveStreamsResult = repository.loadLiveStreamsFromApi(username, password)
            val liveCategoriesResult = repository.getLiveCategories(username, password)
            
            val liveStreams = liveStreamsResult.getOrElse {
                Log.e("SettingsConnectionViewModel", "Failed to load channels", it)
                _connectionTestState.value = _connectionTestState.value.copy(
                    isRefillingCache = false,
                    cacheRefillMessage = "Fehler beim Laden der Kanäle: ${it.message}",
                    cacheRefillSuccess = false
                )
                return
            }
            val liveCategories = liveCategoriesResult.getOrNull() ?: emptyList()
            Log.d("SettingsConnectionViewModel", "Loaded ${liveStreams.size} channels, ${liveCategories.size} categories")
            
            _connectionTestState.value = _connectionTestState.value.copy(
                cacheRefillMessage = "Lade Filme... (${liveStreams.size} Kanäle geladen)"
            )
            
            Log.d("SettingsConnectionViewModel", "Loading movies from API...")
            val vodStreamsResult = repository.loadVodStreamsFromApi(username, password)
            val vodCategoriesResult = repository.getVodCategories(username, password)
            
            val vodStreams = vodStreamsResult.getOrElse {
                Log.e("SettingsConnectionViewModel", "Failed to load movies", it)
                _connectionTestState.value = _connectionTestState.value.copy(
                    isRefillingCache = false,
                    cacheRefillMessage = "Fehler beim Laden der Filme: ${it.message}",
                    cacheRefillSuccess = false
                )
                return
            }
            val vodCategories = vodCategoriesResult.getOrNull() ?: emptyList()
            Log.d("SettingsConnectionViewModel", "Loaded ${vodStreams.size} movies, ${vodCategories.size} categories")
            
            _connectionTestState.value = _connectionTestState.value.copy(
                cacheRefillMessage = "Lade Serien... (${liveStreams.size} Kanäle, ${vodStreams.size} Filme geladen)"
            )
            
            Log.d("SettingsConnectionViewModel", "Loading series from API...")
            val seriesResult = repository.loadSeriesFromApi(username, password)
            val seriesCategoriesResult = repository.getSeriesCategories(username, password)
            
            val series = seriesResult.getOrElse {
                Log.e("SettingsConnectionViewModel", "Failed to load series", it)
                _connectionTestState.value = _connectionTestState.value.copy(
                    isRefillingCache = false,
                    cacheRefillMessage = "Fehler beim Laden der Serien: ${it.message}",
                    cacheRefillSuccess = false
                )
                return
            }
            val seriesCategories = seriesCategoriesResult.getOrNull() ?: emptyList()
            Log.d("SettingsConnectionViewModel", "Loaded ${series.size} series, ${seriesCategories.size} categories")
            
            _connectionTestState.value = _connectionTestState.value.copy(
                cacheRefillMessage = "Speichere Cache... (${liveStreams.size} Kanäle, ${vodStreams.size} Filme, ${series.size} Serien)"
            )
            
            // Save to cache
            Log.d("SettingsConnectionViewModel", "Saving data to cache: Channels=${liveStreams.size}, Movies=${vodStreams.size}, Series=${series.size}")
            cacheService.saveCachedData(
                channels = liveStreams,
                movies = vodStreams,
                series = series,
                liveCategories = liveCategories,
                vodCategories = vodCategories,
                seriesCategories = seriesCategories
            )
            Log.d("SettingsConnectionViewModel", "Cache refilled successfully")
            
            _connectionTestState.value = _connectionTestState.value.copy(
                isRefillingCache = false,
                cacheRefillMessage = "Cache erfolgreich aktualisiert: ${liveStreams.size} Kanäle, ${vodStreams.size} Filme, ${series.size} Serien",
                cacheRefillSuccess = true
            )
        } catch (e: Exception) {
            Log.e("SettingsConnectionViewModel", "Error refilling cache", e)
            _connectionTestState.value = _connectionTestState.value.copy(
                isRefillingCache = false,
                cacheRefillMessage = "Fehler beim Cache-Update: ${e.message}",
                cacheRefillSuccess = false
            )
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
    
    fun clearCacheRefillStatus() {
        _connectionTestState.value = _connectionTestState.value.copy(
            cacheRefillMessage = null,
            cacheRefillSuccess = false
        )
    }
}
