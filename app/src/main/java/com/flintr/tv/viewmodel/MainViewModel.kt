package com.flintr.tv.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flintr.tv.data.local.CredentialsRepository
import com.flintr.tv.data.model.*
import com.flintr.tv.data.repository.XtreamRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: XtreamRepository,
    private val credentialsRepository: CredentialsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _liveCategories = MutableStateFlow<List<Category>>(emptyList())
    val liveCategories: StateFlow<List<Category>> = _liveCategories.asStateFlow()

    private val _liveStreams = MutableStateFlow<List<LiveStream>>(emptyList())
    val liveStreams: StateFlow<List<LiveStream>> = _liveStreams.asStateFlow()

    private val _vodCategories = MutableStateFlow<List<Category>>(emptyList())
    val vodCategories: StateFlow<List<Category>> = _vodCategories.asStateFlow()

    private val _vodStreams = MutableStateFlow<List<VodStream>>(emptyList())
    val vodStreams: StateFlow<List<VodStream>> = _vodStreams.asStateFlow()

    private val _seriesCategories = MutableStateFlow<List<Category>>(emptyList())
    val seriesCategories: StateFlow<List<Category>> = _seriesCategories.asStateFlow()

    private val _series = MutableStateFlow<List<Series>>(emptyList())
    val series: StateFlow<List<Series>> = _series.asStateFlow()

    private val _selectedSeries = MutableStateFlow<Series?>(null)
    val selectedSeries: StateFlow<Series?> = _selectedSeries.asStateFlow()

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        val credentials = credentialsRepository.getCredentials()
        if (credentials == null) {
            Log.w("MainViewModel", "No credentials available, skipping initial data load")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            Log.d("MainViewModel", "Loading initial data...")

            // Load server info
            repository.getServerInfo(credentials.username, credentials.password)
                .onSuccess { serverInfo ->
                    _uiState.value = _uiState.value.copy(serverInfo = serverInfo)
                    Log.d("MainViewModel", "Server info loaded successfully: $serverInfo")
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(error = error.message)
                    Log.e("MainViewModel", "Failed to load server info", error)
                }

            // Load categories
            repository.getLiveCategories(credentials.username, credentials.password)
                .onSuccess { 
                    _liveCategories.value = it
                    Log.d("MainViewModel", "Live categories loaded successfully: ${it.size} categories")
                 }
                .onFailure { 
                    Log.e("MainViewModel", "Failed to load live categories", it)
                }

            repository.getVodCategories(credentials.username, credentials.password)
                .onSuccess { 
                    _vodCategories.value = it
                    Log.d("MainViewModel", "VOD categories loaded successfully: ${it.size} categories")
                }
                .onFailure { 
                    Log.e("MainViewModel", "Failed to load VOD categories", it)
                }

            repository.getSeriesCategories(credentials.username, credentials.password)
                .onSuccess { 
                    _seriesCategories.value = it
                    Log.d("MainViewModel", "Series categories loaded successfully: ${it.size} categories")
                }
                .onFailure { 
                    Log.e("MainViewModel", "Failed to load series categories", it)
                }

            _uiState.value = _uiState.value.copy(isLoading = false)
            Log.d("MainViewModel", "Initial data loading finished.")
        }
    }

    fun loadLiveStreams(categoryId: String? = null) {
        val credentials = credentialsRepository.getCredentials() ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            Log.d("MainViewModel", "Loading live streams for category: $categoryId")
            repository.getLiveStreams(credentials.username, credentials.password, categoryId)
                .onSuccess { 
                    _liveStreams.value = it
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    Log.d("MainViewModel", "Live streams loaded successfully: ${it.size} streams")
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = error.message)
                    Log.e("MainViewModel", "Failed to load live streams", error)
                }
        }
    }

    fun loadVodStreams(categoryId: String? = null) {
        val credentials = credentialsRepository.getCredentials() ?: return
        viewModelScope.launch {
            Log.d("MainViewModel", "Loading VOD streams for category: $categoryId")
            repository.getVodStreams(credentials.username, credentials.password, categoryId)
                .onSuccess { 
                    _vodStreams.value = it 
                    Log.d("MainViewModel", "VOD streams loaded successfully: ${it.size} streams")
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(error = error.message)
                    Log.e("MainViewModel", "Failed to load VOD streams", error)
                }
        }
    }

    fun loadSeries(categoryId: String? = null) {
        val credentials = credentialsRepository.getCredentials() ?: return
        viewModelScope.launch {
            Log.d("MainViewModel", "Loading series for category: $categoryId")
            repository.getSeries(credentials.username, credentials.password, categoryId)
                .onSuccess { 
                    _series.value = it 
                    Log.d("MainViewModel", "Series loaded successfully: ${it.size} series")
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(error = error.message)
                    Log.e("MainViewModel", "Failed to load series", error)
                }
        }
    }

    fun loadSeriesInfo(seriesId: Int) {
        val credentials = credentialsRepository.getCredentials() ?: return
        viewModelScope.launch {
            Log.d("MainViewModel", "Loading series info for series ID: $seriesId")
            repository.getSeriesInfo(credentials.username, credentials.password, seriesId)
                .onSuccess { 
                    _selectedSeries.value = it 
                    Log.d("MainViewModel", "Series info loaded successfully for series ID: $seriesId")
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(error = error.message)
                    Log.e("MainViewModel", "Failed to load series info for series ID: $seriesId", error)
                }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

data class MainUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val serverInfo: ServerInfo? = null
)
