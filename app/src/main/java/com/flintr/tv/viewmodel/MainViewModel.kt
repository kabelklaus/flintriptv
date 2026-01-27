package com.flintr.tv.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flintr.tv.data.local.CategoryVisibilityService
import com.flintr.tv.data.local.Credentials
import com.flintr.tv.data.local.CredentialsRepository
import com.flintr.tv.data.local.CachedData
import com.flintr.tv.data.local.DataCacheService
import com.flintr.tv.data.model.*
import com.flintr.tv.data.repository.UserRepository
import com.flintr.tv.data.repository.XtreamRepository
import com.flintr.tv.ui.common.ContentFilterUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: XtreamRepository,
    private val credentialsRepository: CredentialsRepository,
    private val userRepository: UserRepository,
    private val cacheService: DataCacheService,
    private val categoryVisibilityService: CategoryVisibilityService
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

    private val _searchState = MutableStateFlow(SearchState())
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    private val _pendingNavigationRestore = MutableStateFlow<NavigationStateBeforePlayer?>(null)
    val pendingNavigationRestore: StateFlow<NavigationStateBeforePlayer?> = _pendingNavigationRestore.asStateFlow()

    private var searchJob: Job? = null

    companion object {
        private const val MIN_QUERY_LENGTH = 2
        private const val MAX_RESULTS_PER_CATEGORY = 50
        private const val KEY_NAVIGATION_STATE = "navigation_state_before_player"
        private const val KEY_USERNAME_WRITTEN_THIS_SESSION = "username_written_this_session"
    }

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

        saveUsernameIfNeeded(credentials)

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            Log.d("MainViewModel", "Loading initial data...")

            val cachedData = loadFromCacheIfValid()

            loadServerInfo(credentials)

            if (cachedData == null) {
                loadCategoriesFromRepository(credentials)
            }

            _uiState.value = _uiState.value.copy(isLoading = false)
            Log.d("MainViewModel", "Initial data loading finished.")
        }
    }

    private fun saveUsernameIfNeeded(credentials: Credentials) {
        val usernameWrittenThisSession = savedStateHandle[KEY_USERNAME_WRITTEN_THIS_SESSION] ?: false
        if (!usernameWrittenThisSession) {
            viewModelScope.launch {
                userRepository.saveUsername(credentials.username).fold(
                    onSuccess = {
                        savedStateHandle[KEY_USERNAME_WRITTEN_THIS_SESSION] = true
                        Log.d("MainViewModel", "Username saved to Firestore on app start: ${credentials.username}")
                    },
                    onFailure = { exception ->
                        Log.w("MainViewModel", "Failed to save username to Firestore on app start: ${credentials.username}", exception)
                    }
                )
            }
        }
    }

    private suspend fun loadFromCacheIfValid(): CachedData? {
        if (!cacheService.isCacheValid()) {
            Log.d("MainViewModel", "Cache not available, loading from repository")
            return null
        }

        val cachedData = cacheService.loadCachedData() ?: run {
            Log.d("MainViewModel", "Cache not available, loading from repository")
            return null
        }

        Log.d("MainViewModel", "Loading data from cache")
        _liveCategories.value = cachedData.liveCategories
        _vodCategories.value = cachedData.vodCategories
        _seriesCategories.value = cachedData.seriesCategories
        _uiState.value = _uiState.value.copy(isLoading = false)
        Log.d("MainViewModel", "Categories loaded from cache successfully")
        return cachedData
    }

    private suspend fun loadServerInfo(credentials: Credentials) {
        repository.getServerInfo(credentials.username, credentials.password)
            .onSuccess { serverInfo ->
                _uiState.value = _uiState.value.copy(serverInfo = serverInfo)
                Log.d("MainViewModel", "Server info loaded successfully: $serverInfo")
            }
            .onFailure { error ->
                _uiState.value = _uiState.value.copy(error = error.message)
                Log.e("MainViewModel", "Failed to load server info", error)
            }
    }

    private suspend fun loadCategoriesFromRepository(credentials: Credentials) {
        var categoryLoadError: String? = null

        repository.getLiveCategories(credentials.username, credentials.password)
            .onSuccess {
                _liveCategories.value = it
                Log.d("MainViewModel", "Live categories loaded successfully: ${it.size} categories")
            }
            .onFailure {
                Log.e("MainViewModel", "Failed to load live categories", it)
                if (categoryLoadError == null) categoryLoadError = it.message
            }

        repository.getVodCategories(credentials.username, credentials.password)
            .onSuccess {
                _vodCategories.value = it
                Log.d("MainViewModel", "VOD categories loaded successfully: ${it.size} categories")
            }
            .onFailure {
                Log.e("MainViewModel", "Failed to load VOD categories", it)
                if (categoryLoadError == null) categoryLoadError = it.message
            }

        repository.getSeriesCategories(credentials.username, credentials.password)
            .onSuccess {
                _seriesCategories.value = it
                Log.d("MainViewModel", "Series categories loaded successfully: ${it.size} categories")
            }
            .onFailure {
                Log.e("MainViewModel", "Failed to load series categories", it)
                if (categoryLoadError == null) categoryLoadError = it.message
            }

        if (_liveCategories.value.isEmpty() && _vodCategories.value.isEmpty() && _seriesCategories.value.isEmpty() && categoryLoadError != null) {
            _uiState.value = _uiState.value.copy(error = categoryLoadError)
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
        
        if (seriesCategory == null) {
            Log.w("MainViewModel", "Category not found for series: ${series.name}, categoryId: ${series.categoryId}")
            return null
        }
        
        // Load series for this category
        loadSeries(seriesCategory.categoryId)
        Log.d("MainViewModel", "Showing series from favorites: ${series.name}, category: ${seriesCategory.categoryName}")
        return seriesCategory.categoryId
    }

    /**
     * Updates the search query state without performing a search.
     * Search is only performed when user confirms with Enter key.
     */
    fun searchContent(query: String) {
        // Store original query for TextField display, use trimmed for search logic
        val trimmedQuery = query.trim()
        _searchState.value = _searchState.value.copy(
            query = query, // Keep original for TextField display
            showMinLengthHint = trimmedQuery.isNotEmpty() && trimmedQuery.length < MIN_QUERY_LENGTH
        )
        
        // Clear results if query is too short
        if (trimmedQuery.length < MIN_QUERY_LENGTH) {
            _searchState.value = _searchState.value.copy(
                results = SearchResults(),
                isSearching = false
            )
        }
    }
    
    /**
     * Performs search immediately (triggered by Enter key)
     */
    fun searchContentImmediate() {
        searchJob?.cancel()
        val trimmedQuery = _searchState.value.query.trim()
        
        if (trimmedQuery.length < MIN_QUERY_LENGTH) {
            _searchState.value = _searchState.value.copy(
                results = SearchResults(),
                isSearching = false
            )
            return
        }
        
        searchJob = viewModelScope.launch {
            performSearch(trimmedQuery)
        }
    }
    
    /**
     * Performs the actual search operation
     * Note: cachedData is loaded locally and not stored in ViewModel to prevent OutOfMemoryError
     */
    private suspend fun performSearch(trimmedQuery: String) {
        _searchState.value = _searchState.value.copy(isSearching = true)
        
        // Load cached data temporarily (not stored in ViewModel)
        val cachedData = cacheService.loadCachedData()
        if (cachedData == null) {
            Log.w("MainViewModel", "No cached data available for search")
            _searchState.value = _searchState.value.copy(
                results = SearchResults(),
                isSearching = false
            )
            return
        }
        
        // Perform search (case-insensitive substring match)
        val queryLower = trimmedQuery.lowercase()
        
        // Get hidden categories for filtering
        val hiddenLiveCategories = categoryVisibilityService.getHiddenCategories(CategoryType.LIVE_TV)
        val hiddenVodCategories = categoryVisibilityService.getHiddenCategories(CategoryType.VOD)
        val hiddenSeriesCategories = categoryVisibilityService.getHiddenCategories(CategoryType.SERIES)
        
        val liveTVResults = ContentFilterUtils.filterLiveStreamsByHiddenCategories(
            cachedData.channels,
            hiddenLiveCategories
        )
            .filter { it.name.lowercase().contains(queryLower) }
            .take(MAX_RESULTS_PER_CATEGORY)
        
        val vodResults = ContentFilterUtils.filterVodStreamsByHiddenCategories(
            cachedData.movies,
            hiddenVodCategories
        )
            .filter { it.name.lowercase().contains(queryLower) }
            .take(MAX_RESULTS_PER_CATEGORY)
        
        val seriesResults = ContentFilterUtils.filterSeriesByHiddenCategories(
            cachedData.series,
            hiddenSeriesCategories
        )
            .filter { it.name.lowercase().contains(queryLower) }
            .take(MAX_RESULTS_PER_CATEGORY)
        
        val results = SearchResults(
            liveTV = liveTVResults,
            vod = vodResults,
            series = seriesResults
        )
        
        // cachedData is no longer referenced after this point and will be garbage collected
        // Results are stored in searchState, not the full cachedData
        
        Log.d("MainViewModel", "Search completed: query='$trimmedQuery', results=${results.totalCount} (LiveTV: ${results.liveTV.size}, VOD: ${results.vod.size}, Series: ${results.series.size})")
        
        _searchState.value = _searchState.value.copy(
            results = results,
            isSearching = false
        )
    }

    /**
     * Clear search state
     */
    fun clearSearch() {
        searchJob?.cancel()
        _searchState.value = SearchState()
    }

    /**
     * Save navigation state before navigating to Player.
     * This state will be used to restore the navigation position when user returns from Player.
     */
    fun saveNavigationStateBeforePlayer(state: NavigationStateBeforePlayer) {
        savedStateHandle[KEY_NAVIGATION_STATE] = state
        Log.d("MainViewModel", "Saved navigation state: destinationType=${state.destinationType}, categoryId=${state.categoryId}, contentId=${state.contentId}, scrollIndex=${state.scrollIndex}, scrollOffset=${state.scrollOffset}")
    }

    /**
     * Consume navigation state from SavedStateHandle and emit it via StateFlow.
     * This is called when returning from Player to trigger restoration.
     * Returns the state if available, null otherwise.
     */
    fun consumeNavigationState() {
        val state = savedStateHandle.get<NavigationStateBeforePlayer>(KEY_NAVIGATION_STATE)
        if (state != null) {
            savedStateHandle.remove<NavigationStateBeforePlayer>(KEY_NAVIGATION_STATE)
            _pendingNavigationRestore.value = state
            Log.d("MainViewModel", "Consumed navigation state for restoration: $state")
        }
    }

    /**
     * Clear the pending navigation restore state.
     * Called after successful restoration to prevent re-triggering.
     */
    fun clearPendingNavigationRestore() {
        _pendingNavigationRestore.value = null
        Log.d("MainViewModel", "Cleared pending navigation restore state")
    }
}

data class MainUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val serverInfo: ServerInfo? = null
)
