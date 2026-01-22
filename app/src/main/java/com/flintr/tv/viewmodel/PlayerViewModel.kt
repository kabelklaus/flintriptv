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
import com.flintr.tv.ui.common.PlayerConstants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.channels.Channel
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
    
    private val _seriesContextLoaded = MutableStateFlow<Boolean>(false)
    val seriesContextLoaded: StateFlow<Boolean> = _seriesContextLoaded.asStateFlow()
    
    private val _hasNextEpisodeAvailable = MutableStateFlow<Boolean>(false)
    val hasNextEpisodeAvailable: StateFlow<Boolean> = _hasNextEpisodeAvailable.asStateFlow()
    
    private var currentSeries: Series? = null
    private var currentSeason: Season? = null
    private var currentEpisode: Episode? = null
    private var currentSeriesId: Int? = null
    private var currentEpisodeId: Int? = null
    private var autoPlayJob: kotlinx.coroutines.Job? = null
    private var _pendingNextEpisodeInfo: Channel<NextEpisodeInfo>? = null
    
    private fun getPendingNextEpisodeInfoChannel(): Channel<NextEpisodeInfo> {
        if (_pendingNextEpisodeInfo == null || _pendingNextEpisodeInfo!!.isClosedForSend) {
            _pendingNextEpisodeInfo = Channel(Channel.CONFLATED)
        }
        return _pendingNextEpisodeInfo!!
    }
    
    /**
     * Consumes the pending next episode info from the channel
     * Should be called when navigation is triggered
     * Returns null if no info is available
     */
    fun consumePendingNextEpisodeInfo(): NextEpisodeInfo? {
        val channel = _pendingNextEpisodeInfo ?: return null
        return channel.tryReceive().getOrNull()
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
            _seriesContextLoaded.value = true
            _hasNextEpisodeAvailable.value = findNextEpisode() != null
            return
        }
        
        // Otherwise, load from repository
        _seriesContextLoaded.value = false
        _hasNextEpisodeAvailable.value = false
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
                                _seriesContextLoaded.value = true
                                _hasNextEpisodeAvailable.value = findNextEpisode() != null
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
     * Checks if a next episode exists in the series
     * @return true if a next episode is available, false otherwise
     */
    fun hasNextEpisode(): Boolean {
        return findNextEpisode() != null
    }
    
    /**
     * Finds the next episode in the series
     * 
     * Note: Uses `episodeNum` for ordering if available, otherwise falls back to `episode.id`.
     * This fallback may lead to incorrect ordering if `episodeNum` is truly missing, as `id`
     * is a unique identifier and may not represent sequential order within a season.
     * In such cases, the episode sequence might not be correctly determined.
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
            repeat(PlayerConstants.AUTO_PLAY_COUNTDOWN_STEPS) {
                delay(PlayerConstants.AUTO_PLAY_UPDATE_INTERVAL_MS)
                val currentState = _nextEpisodeState.value
                if (currentState is NextEpisodeState.Visible) {
                    val progress = (it + 1) / PlayerConstants.AUTO_PLAY_COUNTDOWN_STEPS.toFloat()
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
        val nextEpisodeInfo = if (currentState is NextEpisodeState.Visible) {
            // Use info from visible state
            currentState.info
        } else {
            // State might be Hidden, but button is still visible - find next episode directly
            findNextEpisode()
        }
        
        if (nextEpisodeInfo != null) {
            // Store nextEpisodeInfo before changing state to Loading
            getPendingNextEpisodeInfoChannel().trySend(nextEpisodeInfo)
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
        _pendingNextEpisodeInfo?.close()
        _pendingNextEpisodeInfo = null
        _seriesContextLoaded.value = false
        _hasNextEpisodeAvailable.value = false
        hideNextEpisodeButton()
    }
}

