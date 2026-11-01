package com.flintr.tv.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flintr.tv.data.local.CredentialsRepository
import com.flintr.tv.data.local.PlaybackSettingsRepository
import com.flintr.tv.data.local.WatchProgressService
import com.flintr.tv.data.model.EpgProgram
import com.flintr.tv.data.model.Episode
import com.flintr.tv.data.model.LiveStream
import com.flintr.tv.data.model.RatingEntry
import com.flintr.tv.data.model.Season
import com.flintr.tv.data.model.Series
import com.flintr.tv.data.repository.UserRepository
import com.flintr.tv.data.repository.XtreamRepository
import com.flintr.tv.BuildConfig
import com.flintr.tv.ui.common.PlayerConstants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.flintr.tv.utils.error.reportError

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
    private val watchProgressService: WatchProgressService,
    private val userRepository: UserRepository,
    private val playbackSettingsRepository: PlaybackSettingsRepository
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
    
    private val _watchCreditsMode = MutableStateFlow<Boolean>(false)
    val watchCreditsMode: StateFlow<Boolean> = _watchCreditsMode.asStateFlow()
    
    private val _currentCategoryChannels = MutableStateFlow<List<LiveStream>>(emptyList())
    val currentCategoryChannels: StateFlow<List<LiveStream>> = _currentCategoryChannels.asStateFlow()
    
    private val _currentChannelIndex = MutableStateFlow<Int>(-1)
    val currentChannelIndex: StateFlow<Int> = _currentChannelIndex.asStateFlow()
    
    private var currentSeries: Series? = null
    private var currentSeason: Season? = null
    private var currentEpisode: Episode? = null
    private var currentSeriesId: Int? = null
    private var currentEpisodeId: Int? = null
    private var autoPlayJob: kotlinx.coroutines.Job? = null
    private var _pendingNextEpisodeInfo: Channel<NextEpisodeInfo>? = null
    private var ratingPromptShown = false
    private val ratedContentKeys = mutableSetOf<String>()
    
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
     * Gets the current series context for content info display
     * Returns null if context is not loaded
     */
    fun getSeriesContext(): com.flintr.tv.ui.screens.SeriesContext? {
        val series = currentSeries ?: return null
        val season = currentSeason ?: return null
        val episode = currentEpisode ?: return null
        return com.flintr.tv.ui.screens.SeriesContext(
            series = series,
            currentSeason = season,
            currentEpisode = episode
        )
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
        
        // Don't show button if watch credits mode is active
        if (_watchCreditsMode.value) {
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
        val currentState = _nextEpisodeState.value
        val nextEpisode = findNextEpisode()
        
        if (BuildConfig.DEBUG) android.util.Log.d("PlayerViewModel", "tryShowNextEpisodeButton: nextEpisode=${nextEpisode?.displayTitle ?: "null"}")
        
        if (nextEpisode != null) {
            // If button is already visible, preserve current progress
            val initialProgress = if (currentState is NextEpisodeState.Visible) {
                currentState.autoPlayProgress
            } else {
                0f
            }
            
            _nextEpisodeState.value = NextEpisodeState.Visible(nextEpisode, initialProgress)
            if (BuildConfig.DEBUG) android.util.Log.d("PlayerViewModel", "Button state set to Visible")
            
            // Only start countdown if it's not already running
            if (autoPlayJob == null || !autoPlayJob!!.isActive) {
                startAutoPlayCountdown()
            }
        } else {
            if (BuildConfig.DEBUG) android.util.Log.d("PlayerViewModel", "Button not shown: no next episode found")
        }
    }
    
    /**
     * Hides the next episode button and cancels auto-play
     */
    fun hideNextEpisodeButton() {
        autoPlayJob?.cancel()
        autoPlayJob = null
        _nextEpisodeState.value = NextEpisodeState.Hidden
        _watchCreditsMode.value = false
    }
    
    /**
     * Enables watch credits mode - cancels auto-play countdown and hides button
     * Episode will play until 100% before next episode starts
     */
    fun enableWatchCreditsMode() {
        autoPlayJob?.cancel()
        autoPlayJob = null
        _watchCreditsMode.value = true
        _nextEpisodeState.value = NextEpisodeState.Hidden
    }
    
    /**
     * Starts the auto-play countdown (10 seconds)
     * Only starts if no timer is already running and the AutoPlay setting is enabled -
     * when disabled, the next-episode button still shows (via showNextEpisodeButton), it
     * just never advances on its own; the user can still click it manually.
     */
    private fun startAutoPlayCountdown() {
        if (!playbackSettingsRepository.isAutoPlayNextEnabled()) {
            return
        }
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
        _watchCreditsMode.value = false
        hideNextEpisodeButton()
    }
    
    /**
     * Loads all channels for a given category
     */
    fun loadChannelsForCategory(categoryId: String) {
        if (categoryId.isEmpty()) {
            _currentCategoryChannels.value = emptyList()
            _currentChannelIndex.value = -1
            return
        }
        
        viewModelScope.launch {
            val credentials = credentialsRepository.getCredentials() ?: return@launch
            repository.getLiveStreams(credentials.username, credentials.password, categoryId)
                .onSuccess { channels ->
                    _currentCategoryChannels.value = channels
                    if (BuildConfig.DEBUG) android.util.Log.d("PlayerViewModel", "Loaded ${channels.size} channels for category $categoryId")
                    // Note: findCurrentChannelIndex should be called from PlayerScreen after channels are loaded
                }
                .onFailure { error ->
                    reportError("PlayerViewModel", "Failed to load channels for category $categoryId", error)
                    _currentCategoryChannels.value = emptyList()
                    _currentChannelIndex.value = -1
                }
        }
    }
    
    /**
     * Finds the current channel index in the category channels list
     */
    fun findCurrentChannelIndex(streamId: Int) {
        val channels = _currentCategoryChannels.value
        val index = channels.indexOfFirst { it.streamId == streamId }
        if (index >= 0) {
            _currentChannelIndex.value = index
            if (BuildConfig.DEBUG) android.util.Log.d("PlayerViewModel", "Found channel index $index for streamId $streamId")
        } else {
            _currentChannelIndex.value = -1
            android.util.Log.w("PlayerViewModel", "Channel with streamId $streamId not found in category channels")
        }
    }
    
    /**
     * Gets the next channel in the category (no wrap-around)
     * Returns null if already at the last channel
     */
    fun getNextChannel(): LiveStream? {
        val channels = _currentCategoryChannels.value
        val currentIndex = _currentChannelIndex.value
        
        if (BuildConfig.DEBUG) android.util.Log.d("PlayerViewModel", "getNextChannel: channels=${channels.size}, currentIndex=$currentIndex")
        
        if (channels.isEmpty() || currentIndex < 0) {
            if (BuildConfig.DEBUG) android.util.Log.d("PlayerViewModel", "getNextChannel: No channels or invalid index")
            return null
        }
        
        if (currentIndex >= channels.size - 1) {
            // Already at last channel, no wrap-around
            if (BuildConfig.DEBUG) android.util.Log.d("PlayerViewModel", "getNextChannel: Already at last channel (index $currentIndex of ${channels.size - 1})")
            return null
        }
        
        val nextChannel = channels[currentIndex + 1]
        _currentChannelIndex.value = currentIndex + 1
        if (BuildConfig.DEBUG) android.util.Log.d("PlayerViewModel", "getNextChannel: Next channel: ${nextChannel.name} (streamId: ${nextChannel.streamId}, index ${currentIndex + 1})")
        return nextChannel
    }
    
    /**
     * Gets the previous channel in the category (no wrap-around)
     * Returns null if already at the first channel
     */
    fun getPreviousChannel(): LiveStream? {
        val channels = _currentCategoryChannels.value
        val currentIndex = _currentChannelIndex.value
        
        if (BuildConfig.DEBUG) android.util.Log.d("PlayerViewModel", "getPreviousChannel: channels=${channels.size}, currentIndex=$currentIndex")
        
        if (channels.isEmpty() || currentIndex < 0) {
            if (BuildConfig.DEBUG) android.util.Log.d("PlayerViewModel", "getPreviousChannel: No channels or invalid index")
            return null
        }
        
        if (currentIndex <= 0) {
            // Already at first channel, no wrap-around
            if (BuildConfig.DEBUG) android.util.Log.d("PlayerViewModel", "getPreviousChannel: Already at first channel (index $currentIndex)")
            return null
        }
        
        val previousChannel = channels[currentIndex - 1]
        _currentChannelIndex.value = currentIndex - 1
        if (BuildConfig.DEBUG) android.util.Log.d("PlayerViewModel", "getPreviousChannel: Previous channel: ${previousChannel.name} (streamId: ${previousChannel.streamId}, index ${currentIndex - 1})")
        return previousChannel
    }
    
    /**
     * Clears channel navigation state when leaving player
     */
    fun clearChannelNavigation() {
        _currentCategoryChannels.value = emptyList()
        _currentChannelIndex.value = -1
    }

    /**
     * Decides whether the thumbs up/down rating prompt should be shown for the current
     * exit (VOD/series ended or user backed out). VOD always prompts; a series only
     * prompts on its last episode, and only once per player session (guards against the
     * swipe-exit/back-button/STATE_ENDED paths all firing for the same exit).
     *
     * Also guards against auto-play already engaging (next-episode button visible/loading
     * or watch-credits mode) so the dialog never interrupts a binge.
     *
     * @param contentType "movie" or "series"
     * @param hasNextEpisode whether another episode follows (irrelevant for movies)
     */
    fun maybeShowRatingPrompt(contentType: String, hasNextEpisode: Boolean): Boolean {
        if (ratingPromptShown) return false

        val autoNextEngaging = _nextEpisodeState.value !is NextEpisodeState.Hidden || _watchCreditsMode.value
        val shouldShow = when (contentType) {
            "movie" -> true
            "series" -> !hasNextEpisode && !autoNextEngaging
            else -> false
        }

        if (shouldShow) {
            ratingPromptShown = true
        }
        return shouldShow
    }

    /**
     * Marks [contentType]/[contentId] as rated in this player session (via the controls
     * thumbs buttons or the soft rating overlay), so callers can suppress a later
     * end-of-content rating dialog for the same content.
     */
    fun markRatedInSession(contentType: String, contentId: Int) {
        ratedContentKeys.add("$contentType:$contentId")
    }

    /**
     * Whether [contentType]/[contentId] has already been rated in this player session.
     */
    fun hasRatedInSession(contentType: String, contentId: Int): Boolean {
        return ratedContentKeys.contains("$contentType:$contentId")
    }

    /**
     * Persists a rating entry via UserRepository. Fire-and-forget from the caller's
     * perspective (used right before navigating back out of the player).
     */
    fun saveRating(entry: RatingEntry) {
        val username = credentialsRepository.getCredentials()?.username ?: return
        viewModelScope.launch {
            userRepository.saveRating(username, entry)
                .onFailure { reportError("PlayerViewModel", "Failed to save rating", it) }
        }
    }

    /**
     * Loads the persisted rating (1 = up, 0 = down, null = none/unavailable) for
     * [contentType]/[contentId], so the player can restore the "already rated" indicator
     * when the same content is opened again.
     */
    @Suppress("S6313") // called directly from a LaunchedEffect keyed on (contentType, contentId) in PlayerScreen,
    // so cancellation follows that content-identity key; moving this to a viewModelScope-owned
    // StateFlow would be a larger behavioral change than this fix pass should risk
    suspend fun loadRating(contentType: String, contentId: Int): Int? {
        val username = credentialsRepository.getCredentials()?.username ?: return null
        val contentKey = "${contentType}_$contentId"
        return userRepository.getRating(username, contentKey)
            .onFailure { reportError("PlayerViewModel", "Failed to load rating", it) }
            .getOrNull()
            ?.rating
    }
}

