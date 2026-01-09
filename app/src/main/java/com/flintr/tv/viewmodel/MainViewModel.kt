package com.flintr.tv.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flintr.tv.data.local.CredentialsRepository
import com.flintr.tv.data.local.DataCacheService
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
    private val credentialsRepository: CredentialsRepository,
    private val cacheService: DataCacheService
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

    /**
     * Refresh data from cache
     * Called after DataLoadingScreen successfully loads and caches data
     */
    fun refreshData() {
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

            // Try to load from cache first
            val cachedData = if (cacheService.isCacheValid()) {
                cacheService.loadCachedData()
            } else {
                null
            }

            if (cachedData != null) {
                Log.d("MainViewModel", "Loading data from cache")
                _liveCategories.value = cachedData.liveCategories
                _vodCategories.value = cachedData.vodCategories
                _seriesCategories.value = cachedData.seriesCategories
                // Don't load streams here - they will be loaded when a category is selected
                // Streams are category-specific and should be loaded on demand
                _uiState.value = _uiState.value.copy(isLoading = false)
                Log.d("MainViewModel", "Categories loaded from cache successfully")
            } else {
                Log.d("MainViewModel", "Cache not available, loading from repository")
            }

            // Always load server info from repository (not cached)
            repository.getServerInfo(credentials.username, credentials.password)
                .onSuccess { serverInfo ->
                    _uiState.value = _uiState.value.copy(serverInfo = serverInfo)
                    Log.d("MainViewModel", "Server info loaded successfully: $serverInfo")
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(error = error.message)
                    Log.e("MainViewModel", "Failed to load server info", error)
                }

            // If cache was not available, load categories from repository
            if (cachedData == null) {
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
            }

            _uiState.value = _uiState.value.copy(isLoading = false)
            Log.d("MainViewModel", "Initial data loading finished.")
        }
    }

    private fun <T> loadData(
        dataFlow: MutableStateFlow<List<T>>,
        logName: String,
        fetchCall: suspend (username: String, password: String) -> Result<List<T>>
    ) {
        val credentials = credentialsRepository.getCredentials() ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            dataFlow.value = emptyList() // Clear old content while loading
            Log.d("MainViewModel", "Loading $logName")
            
            fetchCall(credentials.username, credentials.password)
                .onSuccess { data ->
                    dataFlow.value = data
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    Log.d("MainViewModel", "$logName loaded successfully: ${data.size} items")
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = error.message)
                    Log.e("MainViewModel", "Failed to load $logName", error)
                }
        }
    }

    fun loadLiveStreams(categoryId: String? = null) {
        loadData(
            dataFlow = _liveStreams,
            logName = "live streams for category: $categoryId",
            fetchCall = { username, password -> repository.getLiveStreams(username, password, categoryId) }
        )
    }

    fun loadVodStreams(categoryId: String? = null) {
        loadData(
            dataFlow = _vodStreams,
            logName = "VOD streams for category: $categoryId",
            fetchCall = { username, password -> repository.getVodStreams(username, password, categoryId) }
        )
    }

    fun loadSeries(categoryId: String? = null) {
        loadData(
            dataFlow = _series,
            logName = "series for category: $categoryId",
            fetchCall = { username, password -> repository.getSeries(username, password, categoryId) }
        )
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

    fun clearLiveStreams() {
        _liveStreams.value = emptyList()
    }

    fun clearVodStreams() {
        _vodStreams.value = emptyList()
    }

    fun clearSeries() {
        _series.value = emptyList()
        _selectedSeries.value = null
    }

    /**
     * Shows a series from favorites by loading its info and the series list for its category.
     * Returns the categoryId that should be selected in the UI, or null if the category was not found.
     */
    fun showSeriesFromFavorites(series: Series): String? {
        // Load series info to show episodes
        loadSeriesInfo(series.seriesId)
        
        // Find the category for this series
        val seriesCategory = _seriesCategories.value.find { 
            it.categoryId == series.categoryId 
        }
        
        if (seriesCategory != null) {
            // Load series for this category
            loadSeries(seriesCategory.categoryId)
            Log.d("MainViewModel", "Showing series from favorites: ${series.name}, category: ${seriesCategory.categoryName}")
            return seriesCategory.categoryId
        } else {
            Log.w("MainViewModel", "Category not found for series: ${series.name}, categoryId: ${series.categoryId}")
            return null
        }
    }
}

data class MainUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val serverInfo: ServerInfo? = null
)
