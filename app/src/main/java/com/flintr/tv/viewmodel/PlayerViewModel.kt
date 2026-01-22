package com.flintr.tv.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flintr.tv.data.local.CredentialsRepository
import com.flintr.tv.data.local.WatchProgressService
import com.flintr.tv.data.model.EpgProgram
import com.flintr.tv.data.model.Episode
import com.flintr.tv.data.model.Season
import com.flintr.tv.data.model.Series
import com.flintr.tv.data.repository.XtreamRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Information about the next episode to play
 */
data class NextEpisodeInfo(
    val episode: Episode,
    val season: Season,
    val displayTitle: String  // "S01E02 - Titel"
)

/**
 * State of the next episode button and auto-play
 */
sealed class NextEpisodeState {
    object Hidden : NextEpisodeState()
    data class Visible(val info: NextEpisodeInfo, val autoPlayProgress: Float) : NextEpisodeState()
    object Loading : NextEpisodeState()
    data class Error(val message: String) : NextEpisodeState()
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: XtreamRepository,
    private val credentialsRepository: CredentialsRepository,
    private val watchProgressService: WatchProgressService
) : ViewModel() {
    
    private val _epgPrograms = MutableStateFlow<List<EpgProgram>>(emptyList())
    val epgPrograms: StateFlow<List<EpgProgram>> = _epgPrograms.asStateFlow()
    
    private val _currentStreamUrl = MutableStateFlow<String?>(null)
    val currentStreamUrl: StateFlow<String?> = _currentStreamUrl.asStateFlow()
    
    private val _nextEpisodeState = MutableStateFlow<NextEpisodeState>(NextEpisodeState.Hidden)
    val nextEpisodeState: StateFlow<NextEpisodeState> = _nextEpisodeState.asStateFlow()
    
    private var currentSeries: Series? = null
    private var currentSeason: Season? = null
    private var currentEpisode: Episode? = null
    private var currentSeriesId: Int? = null
    private var currentEpisodeId: Int? = null
    private var autoPlayJob: kotlinx.coroutines.Job? = null
    private var _pendingNextEpisodeInfo: NextEpisodeInfo? = null
    
    /**
     * Gets the pending next episode info and clears it
     * Should be called when navigation is triggered
     */
    val pendingNextEpisodeInfo: NextEpisodeInfo?
        get() {
            val info = _pendingNextEpisodeInfo
            _pendingNextEpisodeInfo = null
            return info
        }
    
    fun setStreamUrl(url: String) {
        _currentStreamUrl.value = url
    }
    
    fun loadEpg(streamId: Int) {
        val credentials = credentialsRepository.getCredentials() ?: return
        viewModelScope.launch {
            repository.getEpg(credentials.username, credentials.password, streamId)
                .onSuccess { _epgPrograms.value = it }
                .onFailure { }
        }
    }
    
    fun clearEpg() {
        _epgPrograms.value = emptyList()
    }
    
    /**
     * Loads series context (Series, Season, Episode) for the current episode
     */
    fun loadSeriesContext(seriesId: Int, episodeId: Int, seriesData: com.flintr.tv.ui.screens.SeriesContext?) {
        // Store IDs for later use
        currentSeriesId = seriesId
        currentEpisodeId = episodeId
        
        // If seriesData is already provided, use it
        if (seriesData != null) {
            currentSeries = seriesData.series
            currentSeason = seriesData.currentSeason
            currentEpisode = seriesData.currentEpisode
            return
        }
        
        // Otherwise, load from repository
        viewModelScope.launch {
            val credentials = credentialsRepository.getCredentials() ?: return@launch
            repository.getSeriesInfo(credentials.username, credentials.password, seriesId)
                .onSuccess { series ->
                    currentSeries = series
                    // Find current season and episode
                    series.seasons?.forEach { season ->
                        season.episodes?.forEach { episode ->
                            if (episode.id == episodeId) {
                                currentSeason = season
                                currentEpisode = episode
                                return@onSuccess
                            }
                        }
                    }
                }
                .onFailure { }
        }
    }
    
    /**
     * Checks if series context is loaded
     */
    private fun isSeriesContextLoaded(): Boolean {
        return currentSeries != null && currentSeason != null && currentEpisode != null
    }
    
    /**
     * Finds the next episode in the series
     */
    fun findNextEpisode(): NextEpisodeInfo? {
        val series = currentSeries ?: return null
        val currentSeasonNum = currentSeason?.seasonNumber ?: return null
        val currentEpisodeNum = currentEpisode?.episodeNum ?: currentEpisode?.id ?: return null
        
        val sortedSeasons = series.seasons?.sortedBy { it.seasonNumber } ?: return null
        
        // Find current season
        val currentSeasonIndex = sortedSeasons.indexOfFirst { it.seasonNumber == currentSeasonNum }
        if (currentSeasonIndex < 0) return null
        
        val season = sortedSeasons[currentSeasonIndex]
        val episodes = season.episodes ?: return null
        
        // Try to find next episode in same season
        val currentEpisodeIndex = episodes.indexOfFirst { 
            (it.episodeNum ?: it.id) == currentEpisodeNum 
        }
        
        if (currentEpisodeIndex >= 0 && currentEpisodeIndex < episodes.size - 1) {
            // Next episode in same season
            val nextEpisode = episodes[currentEpisodeIndex + 1]
            val nextEpisodeNum = nextEpisode.episodeNum ?: nextEpisode.id
            val displayTitle = "S${season.seasonNumber}E$nextEpisodeNum - ${nextEpisode.title}"
            return NextEpisodeInfo(
                episode = nextEpisode,
                season = season,
                displayTitle = displayTitle
            )
        }
        
        // Check if there's a next season
        if (currentSeasonIndex < sortedSeasons.size - 1) {
            val nextSeason = sortedSeasons[currentSeasonIndex + 1]
            val nextSeasonEpisodes = nextSeason.episodes ?: return null
            
            if (nextSeasonEpisodes.isNotEmpty()) {
                // First episode of next season
                val firstEpisode = nextSeasonEpisodes[0]
                val firstEpisodeNum = firstEpisode.episodeNum ?: firstEpisode.id
                val displayTitle = "S${nextSeason.seasonNumber}E$firstEpisodeNum - ${firstEpisode.title}"
                return NextEpisodeInfo(
                    episode = firstEpisode,
                    season = nextSeason,
                    displayTitle = displayTitle
                )
            }
        }
        
        // No next episode found
        return null
    }
    
    /**
     * Shows the next episode button and starts auto-play countdown
     * If series context is not loaded yet, it will be loaded first
     */
    fun showNextEpisodeButton() {
        val currentState = _nextEpisodeState.value
        if (currentState is NextEpisodeState.Visible) {
            // Already visible and timer is running - don't restart
            return
        }
        
        // Check if series context is loaded
        if (!isSeriesContextLoaded()) {
            // Try to load series context if we have the IDs
            // This is a fallback - normally loadSeriesContext should be called earlier
            val seriesId = currentSeriesId
            val episodeId = currentEpisodeId
            if (seriesId != null && episodeId != null) {
                // Load context and try again after loading
                viewModelScope.launch {
                    val credentials = credentialsRepository.getCredentials() ?: return@launch
                    repository.getSeriesInfo(credentials.username, credentials.password, seriesId)
                        .onSuccess { series ->
                            currentSeries = series
                            // Find current season and episode
                            series.seasons?.forEach { season ->
                                season.episodes?.forEach { episode ->
                                    if (episode.id == episodeId) {
                                        currentSeason = season
                                        currentEpisode = episode
                                        // Now try to show button again
                                        tryShowNextEpisodeButton()
                                        return@onSuccess
                                    }
                                }
                            }
                        }
                }
            }
            return
        }
        
        tryShowNextEpisodeButton()
    }
    
    /**
     * Internal method to show next episode button (assumes context is loaded)
     */
    private fun tryShowNextEpisodeButton() {
        val nextEpisode = findNextEpisode()
        if (nextEpisode != null) {
            _nextEpisodeState.value = NextEpisodeState.Visible(nextEpisode, 0f)
            startAutoPlayCountdown()
        }
    }
    
    /**
     * Hides the next episode button and cancels auto-play
     */
    fun hideNextEpisodeButton() {
        autoPlayJob?.cancel()
        autoPlayJob = null
        _nextEpisodeState.value = NextEpisodeState.Hidden
    }
    
    /**
     * Starts the auto-play countdown (10 seconds)
     * Only starts if no timer is already running
     */
    private fun startAutoPlayCountdown() {
        // Don't start if timer is already running
        if (autoPlayJob != null && autoPlayJob!!.isActive) {
            return
        }
        
        autoPlayJob = viewModelScope.launch {
            repeat(100) {
                delay(100) // 100ms steps = 10 seconds total
                val currentState = _nextEpisodeState.value
                if (currentState is NextEpisodeState.Visible) {
                    val progress = (it + 1) / 100f
                    _nextEpisodeState.value = NextEpisodeState.Visible(currentState.info, progress)
                } else {
                    return@launch // Button was hidden, cancel countdown
                }
            }
            // Countdown finished, trigger auto-play
            triggerAutoPlayNext()
        }
    }
    
    /**
     * Updates the auto-play progress (for manual updates)
     */
    fun updateAutoPlayProgress(progress: Float) {
        val currentState = _nextEpisodeState.value
        if (currentState is NextEpisodeState.Visible) {
            _nextEpisodeState.value = NextEpisodeState.Visible(currentState.info, progress.coerceIn(0f, 1f))
        }
    }
    
    /**
     * Triggers navigation to next episode (called when auto-play countdown finishes or user clicks)
     */
    fun triggerAutoPlayNext() {
        val currentState = _nextEpisodeState.value
        if (currentState is NextEpisodeState.Visible) {
            // Store nextEpisodeInfo before changing state to Loading
            _pendingNextEpisodeInfo = currentState.info
            _nextEpisodeState.value = NextEpisodeState.Loading
        }
    }
    
    /**
     * Clears series context when leaving player
     */
    fun clearSeriesContext() {
        currentSeries = null
        currentSeason = null
        currentEpisode = null
        currentSeriesId = null
        currentEpisodeId = null
        _pendingNextEpisodeInfo = null
        hideNextEpisodeButton()
    }
}

