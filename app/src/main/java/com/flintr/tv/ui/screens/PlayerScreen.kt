package com.flintr.tv.ui.screens

import android.app.Activity
import android.util.Log
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalDragOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.ui.PlayerView
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.common.Format
import androidx.media3.exoplayer.DecoderReuseEvaluation
import com.flintr.tv.BuildConfig
import com.flintr.tv.data.local.CredentialsRepository
import com.flintr.tv.data.local.PlaybackSettingsRepository
import com.flintr.tv.data.local.WatchProgressService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import com.flintr.tv.ui.components.player.*
import com.flintr.tv.ui.components.player.NextEpisodeButtonGroup
import com.flintr.tv.ui.components.player.ContentInfoOverlay
import com.flintr.tv.ui.common.PlayerConstants
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.SharedColors
import com.flintr.tv.ui.utils.FocusedButton
import com.flintr.tv.ui.utils.onBackButton
import com.flintr.tv.ui.utils.onDpadCenter
import com.flintr.tv.ui.utils.onDpadDirection
import com.flintr.tv.ui.utils.createVerticalSwipeGestureModifier
import com.flintr.tv.data.model.LiveStream
import com.flintr.tv.data.model.RatingEntry
import com.flintr.tv.data.model.Subtitle
import androidx.hilt.navigation.compose.hiltViewModel
import com.flintr.tv.viewmodel.PlayerViewModel
import com.flintr.tv.viewmodel.NextEpisodeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import android.content.Context
import android.util.Base64
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.lifecycle.Lifecycle
import java.time.Instant
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.flintr.tv.R
import com.flintr.tv.utils.error.reportError
import androidx.media3.session.MediaSession
import androidx.media3.common.MediaMetadata
import java.util.UUID

// Constants
private const val DECODER_INITIALIZING = "Initializing..."

/**
 * Data class containing series context for episode navigation
 */
data class SeriesContext(
    val series: com.flintr.tv.data.model.Series,
    val currentSeason: com.flintr.tv.data.model.Season,
    val currentEpisode: com.flintr.tv.data.model.Episode
)

/**
 * Data class containing stream information for playback
 */
data class StreamInfo(
    val streamUrl: String,
    val contentType: String,
    val streamId: Int? = null,
    val seriesId: Int? = null,
    val episodeId: Int? = null,
    val title: String? = null,
    val seriesData: SeriesContext? = null,
    val categoryId: String? = null,
    val categoryName: String? = null
)

// Swipe gesture constants
private const val SWIPE_EDGE_THRESHOLD_PX = 50f
private const val MIN_SWIPE_VELOCITY_PX_PER_S = 500f

// Player controls constants
private const val CONTROLS_VISIBILITY_DURATION_MS = 5000L

/**
 * Creates HTTP Basic Auth header value from username and password
 */
private fun createBasicAuthHeader(username: String, password: String): String {
    val credentials = "$username:$password"
    val encoded = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
    return "Basic $encoded"
}

/**
 * Creates and configures an ExoPlayer instance with optimized settings
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun createExoPlayer(
    context: Context,
    credentials: com.flintr.tv.data.local.Credentials?,
    audioDecoderMode: com.flintr.tv.data.local.AudioDecoderMode
): ExoPlayer {
    // Configure LoadControl with optimized buffer settings to prevent main thread blocking
    val allocator = DefaultAllocator(true, 16 * 1024)
    val loadControl: LoadControl = DefaultLoadControl.Builder()
        .setAllocator(allocator)
        .setBufferDurationsMs(
            5000,  // minBufferMs
            20000, // maxBufferMs
            1500,  // bufferForPlaybackMs
            5000   // bufferForPlaybackAfterRebufferMs
        )
        .setTargetBufferBytes(2 * 1024 * 1024) // 2MB target buffer
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()

    // Configure RenderersFactory based on audio decoder setting.
    // The audio sink downmixes multichannel PCM to stereo so devices without a
    // multichannel AudioTrack (e.g. 5.1 over HDMI to a stereo TV) don't fail.
    val renderersFactory = object : DefaultRenderersFactory(context) {
        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioTrackPlaybackParams: Boolean
        ): AudioSink = DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .setAudioProcessors(arrayOf(createStereoDownmixProcessor()))
            .build()
    }.apply {
        setEnableDecoderFallback(true)
        when (audioDecoderMode) {
            com.flintr.tv.data.local.AudioDecoderMode.SOFTWARE ->
                setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            com.flintr.tv.data.local.AudioDecoderMode.HARDWARE ->
                setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            com.flintr.tv.data.local.AudioDecoderMode.AUTO ->
                setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        }
    }
    
    // Configure HTTP DataSource with custom headers to avoid 513 errors
    // Use browser-like User-Agent
    val userAgent = "Mozilla/5.0 (Linux; Android 13; Pixel 6 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    
    // Create HttpDataSourceFactory with HTTP Basic Auth if credentials are available
    val httpDataSourceFactoryBuilder = DefaultHttpDataSource.Factory()
        .setUserAgent(userAgent)
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(30000)
        .setReadTimeoutMs(30000)
    
    // Add HTTP Basic Auth header if credentials are available
    credentials?.let { creds ->
        val authHeader = createBasicAuthHeader(creds.username, creds.password)
        httpDataSourceFactoryBuilder.setDefaultRequestProperties(
            mapOf("Authorization" to authHeader)
        )
        if (BuildConfig.DEBUG) Log.d("PlayerScreen", "HTTP Basic Auth configured.")
    }
    
    val httpDataSourceFactory: HttpDataSource.Factory = httpDataSourceFactoryBuilder
    
    val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
    
    return ExoPlayer.Builder(context)
        .setRenderersFactory(renderersFactory)
        .setLoadControl(loadControl)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(dataSourceFactory)
        )
        .build()
        .apply {
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_OFF
        }
}

/**
 * Finds InvalidResponseCodeException in exception chain
 */
private fun findHttpException(throwable: Throwable?): HttpDataSource.InvalidResponseCodeException? {
    var current: Throwable? = throwable
    while (current != null) {
        if (current is HttpDataSource.InvalidResponseCodeException) {
            return current
        }
        current = current.cause
    }
    return null
}

/**
 * Extracts error message from PlaybackException
 */
private fun getErrorMessage(context: Context, error: PlaybackException): String {
    // Check for HTTP status code errors first (most specific)
    val httpError = findHttpException(error)
    if (httpError != null) {
        if (BuildConfig.DEBUG) Log.d("PlayerScreen", "HTTP error detected: code=${httpError.responseCode}")
        return when (httpError.responseCode) {
            458 -> context.getString(R.string.http_error_458)
            403 -> context.getString(R.string.http_error_403)
            404 -> context.getString(R.string.http_error_404)
            500, 502, 503, 504 -> context.getString(R.string.http_error_server)
            else -> context.getString(R.string.http_error_generic, httpError.responseCode)
        }
    }
    
    // Check for HLS-specific errors (IndexOutOfBoundsException in playlist parsing)
    val cause = error.cause
    val isHlsPlaylistError = cause is IndexOutOfBoundsException || 
        (cause?.message?.contains("index") == true && 
         cause.message?.contains("size") == true)
    
    return when {
        isHlsPlaylistError -> 
            context.getString(R.string.hls_playlist_error)
        error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
        error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> 
            context.getString(R.string.network_error)
        error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
        error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> 
            context.getString(R.string.video_format_unsupported)
        error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
        error.errorCode == PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED ->
            context.getString(R.string.codec_error_try_software)
        else -> {
            val errorMessage = error.message ?: context.getString(R.string.playback_error_unknown)
            context.getString(R.string.playback_error_generic, errorMessage)
        }
    }
}

/**
 * Creates a Player.Listener with callbacks for state changes
 */
private fun createPlayerListener(
    context: Context,
    onError: (String) -> Unit,
    onPlaybackStateChanged: (Int) -> Unit,
    onIsPlayingChanged: (Boolean) -> Unit,
    onPlayWhenReadyChanged: (Boolean) -> Unit
): Player.Listener {
    return object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            reportError("PlayerScreen", "Player Error:", error)
            if (error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED) {
                Log.w("PlayerScreen", "Decoder error: code=${error.errorCode}, message=${error.message}")
            }
            onError(getErrorMessage(context, error))
        }

        override fun onPlaybackStateChanged(state: Int) {
            onPlaybackStateChanged(state)
            if (BuildConfig.DEBUG) {
                val stateString = when (state) {
                    Player.STATE_IDLE -> "STATE_IDLE"
                    Player.STATE_BUFFERING -> "STATE_BUFFERING"
                    Player.STATE_READY -> "STATE_READY"
                    Player.STATE_ENDED -> "STATE_ENDED"
                    else -> "UNKNOWN_STATE"
                }
                Log.d("PlayerScreen", "Playback state changed to $stateString")
            }
        }
        
        override fun onIsPlayingChanged(playing: Boolean) {
            onIsPlayingChanged(playing)
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            onPlayWhenReadyChanged(playWhenReady)
        }
    }
}

/**
 * Restores watch progress from saved state
 */
private suspend fun restoreWatchProgress(
    exoPlayer: ExoPlayer,
    contentType: String,
    streamId: Int?,
    seriesId: Int?,
    episodeId: Int?,
    watchProgressService: WatchProgressService
): Boolean {
    if (contentType == "live") return false
    if (contentType == "movie" && streamId == null) return false
    if (contentType == "series" && (seriesId == null || episodeId == null)) return false
    
    val savedPosition = watchProgressService.getProgress(
        contentType = contentType,
        streamId = streamId,
        seriesId = seriesId,
        episodeId = episodeId
    ) ?: return false
    
    val duration = exoPlayer.duration
    if (duration <= 0 || savedPosition >= duration) {
        if (BuildConfig.DEBUG) Log.d("PlayerScreen", "Skipping progress restore: savedPosition ($savedPosition) >= duration ($duration) or duration invalid")
        return false
    }
    
    if (BuildConfig.DEBUG) Log.d("PlayerScreen", "Restoring watch progress: position=${savedPosition}ms (${savedPosition / 1000}s), duration=${duration}ms (${duration / 1000}s)")
    delay(500) // Wait a bit for player to be fully ready
    exoPlayer.seekTo(savedPosition)
    if (BuildConfig.DEBUG) Log.d("PlayerScreen", "Watch progress restored successfully")
    return true
}

/**
 * Saves watch progress periodically
 */
private suspend fun saveWatchProgress(
    exoPlayer: ExoPlayer,
    contentType: String,
    streamId: Int?,
    seriesId: Int?,
    episodeId: Int?,
    title: String?,
    watchProgressService: WatchProgressService,
    playbackSettingsRepository: PlaybackSettingsRepository
) {
    if (!playbackSettingsRepository.isRememberPositionEnabled()) return
    if (contentType == "live") return
    if (contentType == "movie" && streamId == null) return
    if (contentType == "series" && (seriesId == null || episodeId == null)) return

    val position = exoPlayer.currentPosition
    val duration = exoPlayer.duration
    
    if (position > 0 && duration > 0) {
        val titleToSave = title ?: "Unknown"
        watchProgressService.saveProgress(
            contentType = contentType,
            streamId = streamId,
            seriesId = seriesId,
            episodeId = episodeId,
            position = position,
            duration = duration,
            title = titleToSave
        )
    }
}

/**
 * Creates a modifier for tap gesture detection
 */
private fun Modifier.createTapGestureModifier(onTap: () -> Unit): Modifier {
    return this.pointerInput(Unit) {
        detectTapGestures(onTap = { onTap() })
    }
}

/**
 * Calculates swipe velocity in pixels per second
 * @return Velocity in px/s, or null if calculation is invalid
 */
private fun calculateSwipeVelocity(
    startX: Float,
    startTime: Long,
    currentX: Float,
    currentTime: Long
): Float? {
    val deltaX = currentX - startX
    val deltaTime = currentTime - startTime
    
    return if (deltaX > 0 && deltaTime > 0) {
        deltaX / deltaTime * 1000 // px/s
    } else {
        null
    }
}

/**
 * Checks if swipe starts from the left edge
 */
private fun isSwipeFromLeftEdge(startX: Float): Boolean {
    return startX < SWIPE_EDGE_THRESHOLD_PX
}

/**
 * Processes swipe drag events and detects if swipe velocity threshold is reached
 * @return true if swipe was detected and onBack was called, false otherwise
 */
private suspend fun AwaitPointerEventScope.processSwipeDrag(
    initialDrag: PointerInputChange,
    startX: Float,
    startTime: Long,
    onBack: () -> Unit
): Boolean {
    var lastDrag: PointerInputChange = initialDrag
    
    while (true) {
        val currentX = lastDrag.position.x
        val currentTime = System.currentTimeMillis()
        
        val velocity = calculateSwipeVelocity(startX, startTime, currentX, currentTime)
        if (velocity != null && velocity > MIN_SWIPE_VELOCITY_PX_PER_S) {
            onBack()
            return true
        }
        
        val dragEvent = awaitHorizontalDragOrCancellation(lastDrag.id)
        if (dragEvent == null) {
            return false
        }
        lastDrag = dragEvent
    }
}

/**
 * Creates a modifier for swipe gesture detection (from left edge)
 */
@Suppress("ReturnFromAwaitPointerEventScope")
private fun Modifier.createSwipeGestureModifier(onBack: () -> Unit): Modifier {
    return this.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val down = awaitFirstDown(requireUnconsumed = false)
                val swipeStartX = down.position.x
                val swipeStartTime = System.currentTimeMillis()
                
                if (!isSwipeFromLeftEdge(swipeStartX)) {
                    continue
                }
                
                val drag = awaitHorizontalDragOrCancellation(down.id)
                if (drag != null) {
                    processSwipeDrag(drag, swipeStartX, swipeStartTime, onBack)
                }
            }
        }
    }
}

/**
 * Manages all player-related state
 */
@Composable
private fun rememberPlayerState() = remember {
    object {
        var showControls by mutableStateOf(true)
        var showDebugOverlay by mutableStateOf(false)
        var showSettingsDialog by mutableStateOf(false)
        var showRatingDialog by mutableStateOf(false)
        var focusedButton by mutableStateOf<FocusedButton?>(null)
        var isDraggingProgress by mutableStateOf(false)
        var playbackState by mutableStateOf(Player.STATE_IDLE)
        var currentPosition by mutableStateOf(0L)
        var duration by mutableStateOf(0L)
        var bufferedPosition by mutableStateOf(0L)
        var isPlaying by mutableStateOf(false)
        var playWhenReady by mutableStateOf(false)
        var isPreparingStream by mutableStateOf(true)
        var errorMessage by mutableStateOf<String?>(null)
        var hasRestoredProgress by mutableStateOf(false)
        var lastInteractionTime by mutableStateOf(System.currentTimeMillis())
        var hasTriggeredCompletion by mutableStateOf(false)
        var seekFeedbackText by mutableStateOf<String?>(null)
        var lastSeekTime = 0L
        var hasShownAudioWarning by mutableStateOf(false)
        var ratedThumb by mutableStateOf<Int?>(null)
    }
}

/**
 * Entry point for Hilt to access CredentialsRepository in Composable
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
fun interface CredentialsRepositoryEntryPoint {
    fun credentialsRepository(): CredentialsRepository
}

@EntryPoint
@InstallIn(SingletonComponent::class)
fun interface PlaybackSettingsRepositoryEntryPoint {
    fun playbackSettingsRepository(): PlaybackSettingsRepository
}

/**
 * Suspends until the player reaches STATE_IDLE state.
 * Uses a temporary listener to wait for the state change.
 */
private suspend fun waitForPlayerIdle(player: ExoPlayer) {
    // If already idle, return immediately
    if (player.playbackState == Player.STATE_IDLE) {
        return
    }
    
    suspendCancellableCoroutine<Unit> { continuation ->
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_IDLE) {
                    player.removeListener(this)
                    continuation.resume(Unit)
                }
            }
        }
        
        player.addListener(listener)
        
        // Cleanup if coroutine is cancelled
        continuation.invokeOnCancellation {
            player.removeListener(listener)
        }
    }
}

/**
 * Gets credentials from repository
 */
@Composable
private fun rememberCredentials(context: Context): com.flintr.tv.data.local.Credentials? {
    val credentialsRepository = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            CredentialsRepositoryEntryPoint::class.java
        ).credentialsRepository()
    }
    return credentialsRepository.credentialsFlow
        .collectAsState(initial = credentialsRepository.getCredentials()).value
}

/**
 * Gets the playback settings repository (Hilt singleton; same instance as the one
 * resolved inside [rememberPlayerSetup]) so [VideoSettingsDialog] can persist/read it.
 */
@Composable
private fun rememberPlaybackSettingsRepository(context: Context): PlaybackSettingsRepository {
    return remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            PlaybackSettingsRepositoryEntryPoint::class.java
        ).playbackSettingsRepository()
    }
}

/**
 * Prepares a new media item for playback
 */
@Suppress("InjectDispatcher")
private suspend fun prepareMediaItem(
    exoPlayer: ExoPlayer,
    streamUrl: String,
    subtitles: List<Subtitle>?,
    onError: (String) -> Unit
) {
    try {
        val mediaItem = MediaItem.Builder()
            .setUri(streamUrl)
            .setSubtitleConfigurations(subtitles?.let { buildSubtitleConfigurations(it) } ?: emptyList())
            .build()
        withContext(Dispatchers.Main) { // NOSONAR - UI thread required for ExoPlayer operations
            if (BuildConfig.DEBUG) Log.d("PlayerScreen", "Setting new media item: $streamUrl")
            // Stop current playback
            exoPlayer.stop()
            // Clear old media items
            exoPlayer.clearMediaItems()
            // Wait for player to reach STATE_IDLE before setting new media item
            waitForPlayerIdle(exoPlayer)
            // Set new media item
            exoPlayer.setMediaItem(mediaItem)
            // Prepare the new stream
            exoPlayer.prepare()
            // Seek to start to ensure we're at the beginning of the new stream
            exoPlayer.seekTo(0)
            // Ensure playback starts
            exoPlayer.playWhenReady = true
            if (BuildConfig.DEBUG) Log.d("PlayerScreen", "New media item prepared and playback started")
        }
    } catch (e: Exception) {
        reportError("PlayerScreen", "Error preparing media item", e)
        withContext(Dispatchers.Main) { // NOSONAR - UI thread required for error callback
            onError("Fehler beim Laden des Streams: ${e.message}")
        }
    }
}

/**
 * Callbacks for player setup state changes
 */
private data class PlayerSetupCallbacks(
    val onError: (String) -> Unit,
    val onStreamPreparingChanged: (Boolean) -> Unit,
    val onPlaybackStateChanged: (Int) -> Unit,
    val onIsPlayingChanged: (Boolean) -> Unit,
    val onPlayWhenReadyChanged: (Boolean) -> Unit
)

/**
 * Result of [rememberPlayerSetup]: the player plus a flag tracking whether the user has
 * manually overridden the audio track for the current stream, so the preferred-audio-language
 * auto-selection in the track-change listener below stops re-asserting itself once the user
 * has made an explicit choice.
 */
private data class PlayerSetup(
    val exoPlayer: ExoPlayer,
    val userOverrodeAudioTrack: MutableState<Boolean>
)

/**
 * Sets up ExoPlayer and manages its lifecycle
 */
@Suppress("InjectDispatcher")
@Composable
private fun rememberPlayerSetup(
    context: Context,
    streamUrl: String,
    subtitles: List<Subtitle>?,
    callbacks: PlayerSetupCallbacks,
    coroutineScope: CoroutineScope
): PlayerSetup {
    val credentials = rememberCredentials(context)
    val playbackSettingsRepository = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            PlaybackSettingsRepositoryEntryPoint::class.java
        ).playbackSettingsRepository()
    }
    val audioDecoderMode = playbackSettingsRepository.getAudioDecoderMode()

    val exoPlayer = remember(credentials, audioDecoderMode) {
        createExoPlayer(context, credentials, audioDecoderMode)
    }
    // Explicit unique session ID: without it, Builder defaults to "". During the
    // next-episode navigation transition, old and new PlayerScreen compositions
    // briefly coexist (AnimatedContent crossfade), so two MediaSessions with the
    // same default "" ID exist at once and media3 throws IllegalStateException.
    val mediaSession = remember(exoPlayer) {
        MediaSession.Builder(context, exoPlayer)
            .setId(UUID.randomUUID().toString())
            .build()
    }

    // Add listener once when ExoPlayer is created
    DisposableEffect(exoPlayer) {
        val listener = createPlayerListener(
            context = context,
            onError = callbacks.onError,
            onPlaybackStateChanged = callbacks.onPlaybackStateChanged,
            onIsPlayingChanged = callbacks.onIsPlayingChanged,
            onPlayWhenReadyChanged = callbacks.onPlayWhenReadyChanged
        )

        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            if (BuildConfig.DEBUG) Log.d("PlayerScreen", "Releasing ExoPlayer")
            exoPlayer.release()
        }
    }
    // Registered after the player's DisposableEffect so Compose tears it down
    // in reverse order: session releases before the player is released.
    DisposableEffect(mediaSession) {
        onDispose {
            mediaSession.release()
        }
    }

    val userOverrodeAudioTrack = remember { mutableStateOf(false) }

    // Handle stream URL changes (for channel switching)
    DisposableEffect(exoPlayer, streamUrl) {
        if (BuildConfig.DEBUG) Log.d("PlayerScreen", "DisposableEffect triggered for streamUrl: $streamUrl")
        callbacks.onStreamPreparingChanged(true)
        userOverrodeAudioTrack.value = false

        // Applies the persisted subtitles/audio-language/max-resolution settings once the
        // new stream's tracks are known (can't select a language-matching track or judge
        // track-count before then).
        val playbackSettingsListener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                val enabled = playbackSettingsRepository.getSubtitlesEnabled()
                applySubtitlesEnabled(exoPlayer, enabled)
                if (enabled) {
                    val availableTracks = getSubtitleTracksFromPlayer(exoPlayer)
                    val preferredLanguage = playbackSettingsRepository.getPreferredSubtitleLanguage()
                    pickSubtitleTrackByLanguage(availableTracks, preferredLanguage)?.let { track ->
                        selectSubtitleTrack(exoPlayer, track)
                    }
                }

                applyMaxVideoSize(exoPlayer, playbackSettingsRepository.getVideoQuality())

                if (!userOverrodeAudioTrack.value) {
                    val availableAudioTracks = getAudioTracksFromPlayer(exoPlayer)
                    val preferredAudioLanguage = playbackSettingsRepository.getPreferredAudioLanguage()
                    pickAudioTrackByLanguage(availableAudioTracks, preferredAudioLanguage)?.let { track ->
                        selectAudioTrack(exoPlayer, track)
                    }
                }
            }
        }
        exoPlayer.addListener(playbackSettingsListener)

        val prepareJob = coroutineScope.launch(Dispatchers.IO) { // NOSONAR - UI component, hardcoded dispatcher acceptable
            try {
                prepareMediaItem(exoPlayer, streamUrl, subtitles, callbacks.onError)
            } finally {
                withContext(Dispatchers.Main) { // NOSONAR - Compose state update must run on main
                    callbacks.onStreamPreparingChanged(false)
                }
            }
        }

        onDispose {
            exoPlayer.removeListener(playbackSettingsListener)
            prepareJob.cancel()
            callbacks.onStreamPreparingChanged(false)
        }
    }

    return PlayerSetup(exoPlayer, userOverrodeAudioTrack)
}

/**
 * Hides system UI in fullscreen mode
 */
@Composable
private fun HideSystemUIEffect(view: android.view.View) {
    DisposableEffect(Unit) {
        val window = (view.context as Activity).window
        val windowInsetsController = WindowCompat.getInsetsController(window, view)
        
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        windowInsetsController.systemBarsBehavior = 
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        
        onDispose {
            // System bars stay hidden app-wide.
        }
    }
}

/**
 * Keeps screen on while playback is active
 */
@Composable
private fun KeepScreenOnEffect(view: android.view.View, keepScreenOn: Boolean) {
    DisposableEffect(view, keepScreenOn) {
        val activity = view.context as? Activity
        view.keepScreenOn = keepScreenOn
        if (keepScreenOn) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        onDispose {
            view.keepScreenOn = false
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

/**
 * Creates seek callback
 */
private fun createSeekCallback(
    exoPlayer: ExoPlayer,
    duration: Long
): (Float) -> Unit {
    return { seekProgress: Float ->
        if (duration > 0) {
            val seekPosition = (seekProgress * duration).toLong()
            exoPlayer.seekTo(seekPosition)
        }
    }
}

/**
 * Creates previous (10s back) callback
 */
private fun createPreviousCallback(
    exoPlayer: ExoPlayer,
    onShowControls: () -> Unit,
    onPositionUpdate: (Long) -> Unit
): () -> Unit {
    return {
        val newPosition = (exoPlayer.currentPosition - 10000).coerceAtLeast(0L)
        exoPlayer.seekTo(newPosition)
        onPositionUpdate(newPosition)
        onShowControls()
    }
}

/**
 * Creates next (10s forward) callback
 */
private fun createNextCallback(
    exoPlayer: ExoPlayer,
    duration: Long,
    onShowControls: () -> Unit,
    onPositionUpdate: (Long) -> Unit
): () -> Unit {
    return {
        val newPosition = (exoPlayer.currentPosition + 10000).coerceAtMost(duration)
        exoPlayer.seekTo(newPosition)
        onPositionUpdate(newPosition)
        onShowControls()
    }
}

/**
 * Creates play/pause callback
 */
private fun createPlayPauseCallback(
    exoPlayer: ExoPlayer,
    onShowControls: () -> Unit
): () -> Unit {
    return {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
        onShowControls()
    }
}

/**
 * Data class grouping parameters for PlayerProgressRestoreEffect
 */
private data class PlayerProgressRestoreParams(
    val exoPlayer: ExoPlayer,
    val contentType: String,
    val streamId: Int?,
    val seriesId: Int?,
    val episodeId: Int?,
    val watchProgressService: WatchProgressService,
    val hasRestoredProgress: Boolean,
    val onRestored: () -> Unit,
    val coroutineScope: CoroutineScope
)

/**
 * Data class grouping parameters for PlayerProgressSaveEffect
 */
private data class PlayerProgressSaveParams(
    val exoPlayer: ExoPlayer,
    val contentType: String,
    val streamId: Int?,
    val seriesId: Int?,
    val episodeId: Int?,
    val title: String?,
    val watchProgressService: WatchProgressService,
    val playbackSettingsRepository: PlaybackSettingsRepository
)

/**
 * Effect to restore watch progress when player is ready
 */
@Composable
private fun PlayerProgressRestoreEffect(
    params: PlayerProgressRestoreParams
) {
    LaunchedEffect(params.exoPlayer.playbackState) {
        if (params.exoPlayer.playbackState == Player.STATE_READY && !params.hasRestoredProgress) {
            params.coroutineScope.launch {
                val restored = restoreWatchProgress(
                    exoPlayer = params.exoPlayer,
                    contentType = params.contentType,
                    streamId = params.streamId,
                    seriesId = params.seriesId,
                    episodeId = params.episodeId,
                    watchProgressService = params.watchProgressService
                )
                params.onRestored()
                if (!restored && BuildConfig.DEBUG) {
                    Log.d("PlayerScreen", "No saved progress found for contentType: ${params.contentType}, streamId: ${params.streamId}, seriesId: ${params.seriesId}, episodeId: ${params.episodeId}")
                }
            }
        }
    }
}

/**
 * Effect to auto-save watch progress periodically
 */
@Composable
private fun PlayerProgressSaveEffect(
    params: PlayerProgressSaveParams
) {
    LaunchedEffect(params.contentType, params.streamId, params.seriesId, params.episodeId, params.title) {
        // media3 1.3.1's Player has no direct setMediaMetadata(); metadata lives on the
        // MediaItem, so replace the current item with one carrying the updated metadata.
        params.exoPlayer.currentMediaItem?.let { currentItem ->
            val updatedItem = currentItem.buildUpon()
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(params.title)
                        .setArtist(if (params.contentType == "live") "flintrTV Live" else "flintrTV")
                        .build()
                )
                .build()
            params.exoPlayer.replaceMediaItem(params.exoPlayer.currentMediaItemIndex, updatedItem)
        }
        while (true) {
            delay(10000) // 10 seconds
            saveWatchProgress(
                exoPlayer = params.exoPlayer,
                contentType = params.contentType,
                streamId = params.streamId,
                seriesId = params.seriesId,
                episodeId = params.episodeId,
                title = params.title,
                watchProgressService = params.watchProgressService,
                playbackSettingsRepository = params.playbackSettingsRepository
            )
        }
    }
}

/**
 * Effect to update player position and duration continuously
 */
@Composable
private fun PlayerPositionUpdateEffect(
    exoPlayer: ExoPlayer,
    isPlaying: Boolean,
    onPositionUpdate: (Long, Long, Long) -> Unit
) {
    LaunchedEffect(exoPlayer, isPlaying) {
        while (isPlaying) {
            onPositionUpdate(
                exoPlayer.currentPosition,
                exoPlayer.duration,
                exoPlayer.bufferedPosition
            )
            delay(250) // Update every 250ms
        }
    }
}

/**
 * Effect to detect completion (>= 90%) and trigger immediate save.
 * This ensures completion is registered even if player crashes or is killed.
 */
@Composable
private fun CompletionDetectionEffect(
    position: Long,
    duration: Long,
    contentType: String,
    hasTriggeredCompletion: Boolean,
    isDragging: Boolean,
    onCompletionTriggered: () -> Unit,
    saveProgress: suspend () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(position, duration, hasTriggeredCompletion, isDragging) {
        // Skip during drag to prevent false completion triggers from visual-only position updates
        if (isDragging) return@LaunchedEffect
        // Skip if already triggered or invalid duration or live content
        if (hasTriggeredCompletion) return@LaunchedEffect
        if (duration <= 0) return@LaunchedEffect
        if (contentType == "live") return@LaunchedEffect

        val progressPercent = position.toDouble() / duration.toDouble()
        if (progressPercent >= 0.9) {
            // Immediately save - the completion logic in WatchProgressService
            // will delete the progress and mark as watched
            coroutineScope.launch {
                saveProgress()
            }
            onCompletionTriggered()
            if (BuildConfig.DEBUG) {
                Log.d("PlayerScreen", "Completion detected at ${(progressPercent * 100).toInt()}% - triggering save")
            }
        }
    }
}


/**
 * Effect to auto-hide controls after inactivity (5 seconds minimum display duration)
 */
@Composable
private fun AutoHideControlsEffect(
    showControls: Boolean,
    isDraggingProgress: Boolean,
    lastInteractionTime: Long,
    onHideControls: () -> Unit
) {
    LaunchedEffect(showControls, isDraggingProgress, lastInteractionTime) {
        if (!showControls || isDraggingProgress) return@LaunchedEffect
        
        while (true) {
            val timeSinceInteraction = System.currentTimeMillis() - lastInteractionTime
            val remainingTime = CONTROLS_VISIBILITY_DURATION_MS - timeSinceInteraction
            
            if (remainingTime <= 0) {
                if (!isDraggingProgress && showControls) {
                    onHideControls()
                }
                break
            }
            
            delay(remainingTime.coerceAtMost(1000L)) // Max 1s steps for responsiveness
        }
    }
}

@Suppress("CyclomaticComplexMethod") // UI composition with multiple state callbacks
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PlayerScreen( // NOSONAR - Cognitive Complexity acceptable for UI composition with multiple state callbacks
    streamInfo: StreamInfo,
    watchProgressService: WatchProgressService,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onNextEpisode: ((com.flintr.tv.viewmodel.NextEpisodeInfo) -> Unit)? = null
) {
    val streamUrl = streamInfo.streamUrl
    val contentType = streamInfo.contentType
    val streamId = streamInfo.streamId
    val seriesId = streamInfo.seriesId
    val episodeId = streamInfo.episodeId
    val title = streamInfo.title
    val seriesData = streamInfo.seriesData
    val categoryId = streamInfo.categoryId
    
    val context = LocalContext.current
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    val state = rememberPlayerState()
    val snackbarHostState = remember { SnackbarHostState() }
    val viewModel: PlayerViewModel = hiltViewModel()
    val keepScreenOn = state.isPlaying ||
        state.playWhenReady ||
        state.playbackState == Player.STATE_BUFFERING ||
        state.isPreparingStream
    
    // Observe category channels (must be defined before LaunchedEffects that use it)
    val categoryChannels by viewModel.currentCategoryChannels.collectAsState()
    val currentChannelIndex by viewModel.currentChannelIndex.collectAsState()
    
    // Load category channels if this is a live TV stream
    LaunchedEffect(categoryId, contentType) {
        if (contentType == "live" && categoryId != null) {
            if (BuildConfig.DEBUG) Log.d("PlayerScreen", "Loading channels for category: $categoryId")
            viewModel.loadChannelsForCategory(categoryId)
        } else {
            viewModel.clearChannelNavigation()
        }
    }
    
    // Find current channel index when channels are loaded or streamId changes
    LaunchedEffect(streamId, contentType, categoryChannels) {
        if (contentType == "live" && streamId != null && categoryChannels.isNotEmpty()) {
            if (BuildConfig.DEBUG) Log.d("PlayerScreen", "Finding channel index for streamId: $streamId in ${categoryChannels.size} channels")
            // Log first few channel IDs for debugging
            if (BuildConfig.DEBUG && categoryChannels.isNotEmpty()) {
                val firstFewIds = categoryChannels.take(5).map { it.streamId }.joinToString(", ")
                Log.d("PlayerScreen", "First few channel IDs: $firstFewIds")
            }
            viewModel.findCurrentChannelIndex(streamId)
            // Log result
            if (BuildConfig.DEBUG) {
                val foundIndex = viewModel.currentChannelIndex.value
                Log.d("PlayerScreen", "Channel index search result: $foundIndex (streamId: $streamId)")
            }
        } else {
            if (contentType == "live" && BuildConfig.DEBUG) {
                Log.d("PlayerScreen", "Cannot find channel index - streamId=$streamId, channels=${categoryChannels.size}")
            }
        }
    }
    
    // Load series context if this is a series episode
    LaunchedEffect(seriesId, episodeId, seriesData) {
        if (contentType == "series" && seriesId != null && episodeId != null) {
            viewModel.loadSeriesContext(seriesId, episodeId, seriesData)
        } else {
            viewModel.clearSeriesContext()
        }
    }
    
    // Get credentials for stream URL generation
    val credentialsRepository = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            CredentialsRepositoryEntryPoint::class.java
        ).credentialsRepository()
    }
    val credentials by credentialsRepository.credentialsFlow
        .collectAsState(initial = credentialsRepository.getCredentials())
    val playbackSettingsRepository = rememberPlaybackSettingsRepository(context)

    // State for current stream URL (can be changed when switching channels)
    var currentStreamUrl by remember { mutableStateOf(streamUrl) }
    var currentTitle by remember { mutableStateOf(title) }
    
    // Update current stream URL when streamInfo changes
    LaunchedEffect(streamUrl, title) {
        currentStreamUrl = streamUrl
        currentTitle = title
    }
    
    // Reset controls timer function (defined early so it can be used in handlers)
    val resetControlsTimer = {
        state.showControls = true
        state.lastInteractionTime = System.currentTimeMillis()
    }

    // Seek commit job - delays the actual exoPlayer.seekTo until user stops pressing
    var seekCommitJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    // Pending seek position (visual only, not yet committed to ExoPlayer)
    var pendingSeekPosition by remember { mutableStateOf<Long?>(null) }
    // Start position when beginning a seek sequence (for accumulated offset calculation)
    var seekStartPosition by remember { mutableStateOf<Long?>(null) }

    // Function to switch to a new channel
    val switchToChannel: (LiveStream) -> Unit = { newStream ->
        credentials?.let { creds ->
            val newStreamUrl = newStream.getStreamUrl(
                creds.serverUrl,
                creds.username,
                creds.password
            )
            if (BuildConfig.DEBUG) Log.d("PlayerScreen", "Switching to channel: ${newStream.name} (streamId: ${newStream.streamId}) - oldUrl=$currentStreamUrl, newUrl=$newStreamUrl")
            currentStreamUrl = newStreamUrl
            currentTitle = newStream.name
            if (BuildConfig.DEBUG) Log.d("PlayerScreen", "Channel switched - currentStreamUrl updated to: $currentStreamUrl")
        } ?: run {
            Log.w("PlayerScreen", "Cannot switch channel: credentials not available")
        }
    }
    
    // Handle channel navigation (D-Pad or Swipe)
    // Edge cases: Only allow navigation if:
    // - contentType is "live"
    // - categoryId is not null
    // - categoryChannels is not empty
    // - currentChannelIndex is valid (>= 0)
    val handleChannelUp = {
        if (BuildConfig.DEBUG) Log.d("PlayerScreen", "handleChannelUp called - contentType=$contentType, categoryId=$categoryId, channels=${categoryChannels.size}, index=$currentChannelIndex")
        if (contentType == "live" && 
            categoryId != null && 
            categoryChannels.isNotEmpty() && 
            currentChannelIndex >= 0) {
            val previousChannel = viewModel.getPreviousChannel()
            if (previousChannel != null) {
                if (BuildConfig.DEBUG) Log.d("PlayerScreen", "Switching to previous channel: ${previousChannel.name}")
                switchToChannel(previousChannel)
                resetControlsTimer()
                true
            } else {
                // Already at first channel, no wrap-around
                if (BuildConfig.DEBUG) Log.d("PlayerScreen", "Already at first channel, no previous channel available")
                false
            }
        } else {
            if (BuildConfig.DEBUG) Log.d("PlayerScreen", "Channel navigation not allowed - conditions not met")
            false
        }
    }
    
    val handleChannelDown = {
        if (BuildConfig.DEBUG) Log.d("PlayerScreen", "handleChannelDown called - contentType=$contentType, categoryId=$categoryId, channels=${categoryChannels.size}, index=$currentChannelIndex")
        if (contentType == "live" && 
            categoryId != null && 
            categoryChannels.isNotEmpty() && 
            currentChannelIndex >= 0) {
            val nextChannel = viewModel.getNextChannel()
            if (nextChannel != null) {
                if (BuildConfig.DEBUG) Log.d("PlayerScreen", "Switching to next channel: ${nextChannel.name}")
                switchToChannel(nextChannel)
                resetControlsTimer()
                true
            } else {
                // Already at last channel, no wrap-around
                if (BuildConfig.DEBUG) Log.d("PlayerScreen", "Already at last channel, no next channel available")
                false
            }
        } else {
            if (BuildConfig.DEBUG) Log.d("PlayerScreen", "Channel navigation not allowed - conditions not met")
            false
        }
    }
    
    // Observe series context loaded state from ViewModel
    val seriesContextLoaded by viewModel.seriesContextLoaded.collectAsState()
    
    // Observe next episode state
    val nextEpisodeState by viewModel.nextEpisodeState.collectAsState()
    
    // Observe if next episode is available (for always-visible button)
    val hasNextEpisodeAvailable by viewModel.hasNextEpisodeAvailable.collectAsState()
    
    // Observe watch credits mode
    val watchCreditsMode by viewModel.watchCreditsMode.collectAsState()
    
    // Get series context for content info (use seriesData if available, otherwise from ViewModel)
    val contentInfoSeriesData = remember(seriesData, seriesContextLoaded) {
        seriesData ?: if (seriesContextLoaded) viewModel.getSeriesContext() else null
    }

    // Title shown in the rating dialog - the series name (not the episode title) for series content
    val ratingDialogTitle = if (contentType == "series") {
        contentInfoSeriesData?.series?.name ?: currentTitle ?: ""
    } else {
        currentTitle ?: ""
    }

    // Identifies the currently playing content for ratedInSession lookups (seriesId for
    // series, streamId for movie/live).
    val currentContentId = (if (contentType == "series") seriesId else streamId) ?: 0

    // Attempts to leave the player: offers the rating prompt first (VOD always, series only on
    // its last episode with no auto-play pending), otherwise exits immediately.
    // Only 1x triggered per player session, see PlayerViewModel.maybeShowRatingPrompt. Skipped
    // entirely if the content was already rated this session (controls thumbs / soft overlay).
    val attemptExit: () -> Unit = {
        if ((contentType == "movie" || contentType == "series") &&
            !viewModel.hasRatedInSession(contentType, currentContentId) &&
            viewModel.maybeShowRatingPrompt(contentType, hasNextEpisodeAvailable)
        ) {
            state.showRatingDialog = true
        } else {
            onBack()
        }
    }

    // Direct thumbs up/down rating from the controls bar or the soft rating overlay -
    // persists immediately and suppresses later end-of-content rating dialogs.
    val onThumbsUp: () -> Unit = {
        viewModel.saveRating(
            RatingEntry(
                contentType = contentType,
                contentId = currentContentId,
                title = ratingDialogTitle,
                rating = 1,
                ratedAt = Instant.now().toString()
            )
        )
        viewModel.markRatedInSession(contentType, currentContentId)
        state.ratedThumb = 1
    }
    val onThumbsDown: () -> Unit = {
        viewModel.saveRating(
            RatingEntry(
                contentType = contentType,
                contentId = currentContentId,
                title = ratingDialogTitle,
                rating = 0,
                ratedAt = Instant.now().toString()
            )
        )
        viewModel.markRatedInSession(contentType, currentContentId)
        state.ratedThumb = 0
    }

    // Restores the persisted rating for the current content (movie/series) so the
    // matching thumb stays highlighted across player sessions. Reloads - and resets to
    // null while loading - whenever the content changes, so a rating from the previous
    // content never lingers on a newly opened one.
    LaunchedEffect(contentType, currentContentId) {
        state.ratedThumb = if (contentType == "movie" || contentType == "series") {
            viewModel.loadRating(contentType, currentContentId)
        } else {
            null
        }
    }

    var wasPlayingBeforeDrag by remember { mutableStateOf(false) }
    
    // State for measured bitrate and decoder info
    var measuredBitrate by remember { mutableStateOf(0L) }
    var videoDecoderName by remember { mutableStateOf(DECODER_INITIALIZING) }
    var audioDecoderName by remember { mutableStateOf(DECODER_INITIALIZING) }
    
    // Setup player with callbacks (use currentStreamUrl to support channel switching)
    val (exoPlayer, userOverrodeAudioTrack) = rememberPlayerSetup(
        context = context,
        streamUrl = currentStreamUrl,
        subtitles = contentInfoSeriesData?.currentEpisode?.subtitles,
        callbacks = PlayerSetupCallbacks(
            onError = { message -> state.errorMessage = message },
            onStreamPreparingChanged = { preparing -> state.isPreparingStream = preparing },
            onPlaybackStateChanged = { playbackStateValue ->
                state.playbackState = playbackStateValue
                if (playbackStateValue == Player.STATE_READY) {
                    state.errorMessage = null
                }
            },
            onIsPlayingChanged = { playing -> state.isPlaying = playing },
            onPlayWhenReadyChanged = { ready -> state.playWhenReady = ready }
        ),
        coroutineScope = coroutineScope
    )

    // Lifecycle observer: pause player when app goes to background.
    // isMinimizerActive stays false until a picture-in-picture / minimizer mode exists;
    // once such a mode sets it to true, the player keeps playing in the background
    // instead of pausing.
    var isMinimizerActive by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    // Reference to the live PlayerView, set once by the AndroidView factory below; read
    // lazily by playerSurfaceObserver, so declaration order here doesn't matter.
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    // Applies the persisted resize mode once the PlayerView is attached - resize mode
    // doesn't depend on track info, so it doesn't need to wait for onTracksChanged.
    LaunchedEffect(playerViewRef) {
        playerViewRef?.let { applyResizeMode(it, playbackSettingsRepository.getVideoResizeMode()) }
    }

    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && !isMinimizerActive) {
                exoPlayer.playWhenReady = false
                exoPlayer.pause()
                // Save watch progress for non-live content
                coroutineScope.launch {
                    saveWatchProgress(
                        exoPlayer = exoPlayer,
                        contentType = contentType,
                        streamId = streamId,
                        seriesId = seriesId,
                        episodeId = episodeId,
                        title = currentTitle,
                        watchProgressService = watchProgressService,
                        playbackSettingsRepository = playbackSettingsRepository
                    )
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Surface-Handling: releases the video surface on ON_STOP and reattaches it (resuming
    // playback if it was playing before) on ON_START. See PlayerSurfaceLifecycleObserver.
    val playerSurfaceObserver = remember(exoPlayer) {
        PlayerSurfaceLifecycleObserver(
            player = { exoPlayer },
            playerView = { playerViewRef }
        )
    }
    DisposableEffect(lifecycleOwner, playerSurfaceObserver) {
        lifecycleOwner.lifecycle.addObserver(playerSurfaceObserver)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(playerSurfaceObserver)
        }
    }

    // AnalyticsListener for debug information
    DisposableEffect(exoPlayer) {
        if (!BuildConfig.DEBUG) {
            onDispose { }
        } else {
            val analyticsListener = object : AnalyticsListener {
            override fun onBandwidthEstimate(
                eventTime: AnalyticsListener.EventTime,
                totalLoadTimeMs: Int,
                totalBytesLoaded: Long,
                bitrateEstimate: Long
            ) {
                measuredBitrate = bitrateEstimate
                if (BuildConfig.DEBUG) {
                    Log.d("PlayerAnalytics", "Bitrate: ${bitrateEstimate / 1_000_000.0} Mbps " +
                        "(${totalBytesLoaded / 1024}KB in ${totalLoadTimeMs}ms)")
                }
            }
            
            override fun onVideoInputFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                format: Format,
                decoderReuseEvaluation: DecoderReuseEvaluation?
            ) {
                // Only update if not already set by onVideoDecoderInitialized
                if (videoDecoderName == DECODER_INITIALIZING) {
                    videoDecoderName = decoderReuseEvaluation?.decoderName ?: "unknown"
                }
                
                if (BuildConfig.DEBUG) {
                    Log.d("PlayerAnalytics", "Video: ${format.codecs}, ${format.width}x${format.height}, " +
                        "Decoder: ${decoderReuseEvaluation?.decoderName ?: "unknown"}")
                }
            }
            
            override fun onAudioInputFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                format: Format,
                decoderReuseEvaluation: DecoderReuseEvaluation?
            ) {
                // Only update if not already set by onAudioDecoderInitialized
                if (audioDecoderName == DECODER_INITIALIZING) {
                    audioDecoderName = decoderReuseEvaluation?.decoderName ?: "unknown"
                }
                
                if (BuildConfig.DEBUG) {
                    Log.d("PlayerAnalytics", "Audio: ${format.codecs}, ${format.sampleRate}Hz, " +
                        "Channels: ${format.channelCount}, Decoder: ${decoderReuseEvaluation?.decoderName ?: "unknown"}")
                }
            }
            
            override fun onVideoDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long
            ) {
                videoDecoderName = decoderName
                if (BuildConfig.DEBUG) {
                    Log.d("PlayerAnalytics", "Video Decoder initialized: $decoderName " +
                        "(took ${initializationDurationMs}ms)")
                }
            }
            
            override fun onAudioDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long
            ) {
                audioDecoderName = decoderName
                if (BuildConfig.DEBUG) {
                    Log.d("PlayerAnalytics", "Audio Decoder initialized: $decoderName " +
                        "(took ${initializationDurationMs}ms)")
                }
            }
        }
        
            exoPlayer.addAnalyticsListener(analyticsListener)
            
            onDispose {
                exoPlayer.removeAnalyticsListener(analyticsListener)
            }
        }
    }
    
    // Hide system UI
    HideSystemUIEffect(view)
    KeepScreenOnEffect(view, keepScreenOn)
    
    // Update position and duration
    PlayerPositionUpdateEffect(
        exoPlayer = exoPlayer,
        isPlaying = state.isPlaying,
        onPositionUpdate = { position, durationValue, buffered ->
            if (pendingSeekPosition == null) {
                state.currentPosition = position
            }
            state.duration = durationValue
            state.bufferedPosition = buffered
        }
    )
    
    // Restore watch progress
    PlayerProgressRestoreEffect(
        params = PlayerProgressRestoreParams(
            exoPlayer = exoPlayer,
            contentType = contentType,
            streamId = streamId,
            seriesId = seriesId,
            episodeId = episodeId,
            watchProgressService = watchProgressService,
            hasRestoredProgress = state.hasRestoredProgress,
            onRestored = { state.hasRestoredProgress = true },
            coroutineScope = coroutineScope
        )
    )
    
    // Auto-save progress
    PlayerProgressSaveEffect(
        params = PlayerProgressSaveParams(
            exoPlayer = exoPlayer,
            contentType = contentType,
            streamId = streamId,
            seriesId = seriesId,
            episodeId = episodeId,
            title = title,
            watchProgressService = watchProgressService,
            playbackSettingsRepository = playbackSettingsRepository
        )
    )

    // Detect completion (>= 90%) and trigger immediate save
    CompletionDetectionEffect(
        position = state.currentPosition,
        duration = state.duration,
        contentType = contentType,
        hasTriggeredCompletion = state.hasTriggeredCompletion,
        isDragging = state.isDraggingProgress,
        onCompletionTriggered = { state.hasTriggeredCompletion = true },
        saveProgress = {
            saveWatchProgress(
                exoPlayer = exoPlayer,
                contentType = contentType,
                streamId = streamId,
                seriesId = seriesId,
                episodeId = episodeId,
                title = title,
                watchProgressService = watchProgressService,
                playbackSettingsRepository = playbackSettingsRepository
            )
        }
    )
    

    // No-audio warning: after 3s when video is playing but no audio detected
    LaunchedEffect(state.playbackState, state.hasShownAudioWarning) {
        if (state.playbackState != Player.STATE_READY || state.hasShownAudioWarning) return@LaunchedEffect
        delay(3000)
        val hasVideo = exoPlayer.videoFormat != null
        val hasAudio = exoPlayer.audioFormat != null ||
            exoPlayer.currentTracks.groups.any { it.type == C.TRACK_TYPE_AUDIO }
        if (hasVideo && !hasAudio) {
            state.hasShownAudioWarning = true
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.no_audio_detected),
                actionLabel = context.getString(R.string.open_audio_settings),
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                state.showSettingsDialog = true
            }
        }
    }
    
    // Auto-hide controls
    AutoHideControlsEffect(
        showControls = state.showControls,
        isDraggingProgress = state.isDraggingProgress,
        lastInteractionTime = state.lastInteractionTime,
        onHideControls = {
            state.showControls = false
            state.focusedButton = null
        }
    )
    
    // Calculate progress
    val progress = if (state.duration > 0) {
        (state.currentPosition.toFloat() / state.duration).coerceIn(0f, 1f)
    } else {
        0f
    }
    
    // Show/hide next episode button based on progress (98.5% threshold)
    // Skip during drag to prevent false triggers from visual-only position updates
    LaunchedEffect(progress, contentType, seriesContextLoaded, watchCreditsMode, state.isDraggingProgress) {
        if (state.isDraggingProgress) return@LaunchedEffect
        if (contentType == "series" && seriesContextLoaded) {
            // Don't show button if watch credits mode is active
            if (watchCreditsMode) {
                if (BuildConfig.DEBUG) Log.d("PlayerScreen", "Button not shown: watchCreditsMode is active")
                return@LaunchedEffect
            }
            
            val currentState = viewModel.nextEpisodeState.value
            val isButtonVisible = currentState is NextEpisodeState.Visible
            
            if (BuildConfig.DEBUG) Log.d("PlayerScreen", "Progress check: progress=$progress (${(progress * 100).toInt()}%), seriesContextLoaded=$seriesContextLoaded, isButtonVisible=$isButtonVisible")
            
            if (progress >= PlayerConstants.NEXT_EPISODE_BUTTON_THRESHOLD && !isButtonVisible) {
                // Show button only if not already visible (prevents timer restart)
                // Only show if series context is loaded
                if (BuildConfig.DEBUG) Log.d("PlayerScreen", "Calling showNextEpisodeButton() - progress >= 98.5%")
                viewModel.showNextEpisodeButton()
            } else if (progress < PlayerConstants.NEXT_EPISODE_BUTTON_THRESHOLD && isButtonVisible) {
                // User rewound, hide button
                if (BuildConfig.DEBUG) Log.d("PlayerScreen", "Hiding button - progress < 98.5%")
                viewModel.hideNextEpisodeButton()
            }
        } else {
            if (contentType == "series" && BuildConfig.DEBUG) {
                Log.d("PlayerScreen", "Button not shown: contentType=series but seriesContextLoaded=$seriesContextLoaded")
            }
        }
    }
    
    // Handle STATE_ENDED - trigger auto-play if button was visible or watch credits mode is active
    LaunchedEffect(state.playbackState, contentType) {
        if (state.playbackState == Player.STATE_ENDED && contentType == "series") {
            val watchCredits = viewModel.watchCreditsMode.value
            if (watchCredits) {
                // Episode was watched to 100% - now start next episode
                viewModel.triggerAutoPlayNext()
            } else {
                // Standard auto-play at 98.5% - only if the video itself ended before the
                // countdown finished; gated on the AutoPlay setting like the countdown
                // itself (PlayerViewModel.startAutoPlayCountdown). If disabled, the button
                // stays Visible so the user can still advance manually.
                val currentState = viewModel.nextEpisodeState.value
                if (currentState is NextEpisodeState.Visible) {
                    if (playbackSettingsRepository.isAutoPlayNextEnabled()) {
                        viewModel.triggerAutoPlayNext()
                    }
                } else {
                    // Series complete: last episode ended, no auto-play pending -
                    // offer the rating prompt instead of leaving the player idle
                    if (!viewModel.hasRatedInSession(contentType, currentContentId) &&
                        viewModel.maybeShowRatingPrompt(contentType, viewModel.hasNextEpisode())
                    ) {
                        state.showRatingDialog = true
                    }
                }
            }
        } else if (state.playbackState == Player.STATE_ENDED && contentType == "movie") {
            // VOD complete: same flow as a series' last episode (Task F3 parity fix)
            if (!viewModel.hasRatedInSession(contentType, currentContentId) &&
                viewModel.maybeShowRatingPrompt(contentType, hasNextEpisode = false)
            ) {
                state.showRatingDialog = true
            }
        }
    }
    
    // Handle next episode navigation
    LaunchedEffect(nextEpisodeState) {
        if (nextEpisodeState is NextEpisodeState.Loading) {
            // Get nextEpisodeInfo from ViewModel (stored before state changed to Loading)
            val nextEpisodeInfo = viewModel.consumePendingNextEpisodeInfo()
            
            if (nextEpisodeInfo != null && onNextEpisode != null) {
                // Save current progress before navigating
                coroutineScope.launch {
                    saveWatchProgress(
                        exoPlayer = exoPlayer,
                        contentType = contentType,
                        streamId = streamId,
                        seriesId = seriesId,
                        episodeId = episodeId,
                        title = title,
                        watchProgressService = watchProgressService,
                        playbackSettingsRepository = playbackSettingsRepository
                    )
                }
                // Navigate to next episode
                onNextEpisode(nextEpisodeInfo)
            }
        }
    }
    
    // D-Pad seek handler (left/right during playback)
    // Accumulates seek offset visually; only commits exoPlayer.seekTo after user stops pressing.
    // Shows the standard controls overlay with the red progress bar.
    val handleDpadSeek: (Boolean) -> Boolean = { isForward ->
        if (state.isPlaying) {
            val now = System.currentTimeMillis()
            if (now - state.lastSeekTime < PlayerConstants.SEEK_DEBOUNCE_MS) {
                true // consumed but debounced
            } else {
                state.lastSeekTime = now
                val duration = exoPlayer.duration
                if (duration != androidx.media3.common.C.TIME_UNSET && duration > 0) {
                    // Use pending position if we're in a seek sequence, otherwise use current player position
                    val basePosition = pendingSeekPosition ?: exoPlayer.currentPosition
                    
                    // Store start position when beginning a new seek sequence
                    if (pendingSeekPosition == null) {
                        seekStartPosition = exoPlayer.currentPosition
                    }
                    
                    val newPos = if (isForward) {
                        (basePosition + PlayerConstants.DPAD_SEEK_FORWARD_MS).coerceAtMost(duration)
                    } else {
                        (basePosition - PlayerConstants.DPAD_SEEK_BACK_MS).coerceAtLeast(0L)
                    }

                    // Update visual position immediately (no exoPlayer.seekTo yet)
                    pendingSeekPosition = newPos
                    state.currentPosition = newPos

                    // Show accumulated seek feedback relative to start position
                    val startPos = seekStartPosition ?: exoPlayer.currentPosition
                    val totalOffset = newPos - startPos
                    val offsetSeconds = totalOffset / 1000
                    state.seekFeedbackText = if (offsetSeconds >= 0) "+${offsetSeconds}s" else "${offsetSeconds}s"
                    resetControlsTimer()

                    // Cancel previous commit job and schedule a new one
                    seekCommitJob?.cancel()
                    seekCommitJob = coroutineScope.launch {
                        delay(PlayerConstants.SEEK_COMMIT_DELAY_MS)
                        val commitPos = pendingSeekPosition
                        if (commitPos != null) {
                            pendingSeekPosition = null
                            seekStartPosition = null
                            exoPlayer.seekTo(commitPos)
                            state.seekFeedbackText = null
                            if (BuildConfig.DEBUG) Log.d("PlayerScreen", "Seek committed to ${commitPos}ms")
                        }
                    }
                }
                true
            }
        } else {
            false
        }
    }

    // Create callbacks
    val onSeek = { seekProgress: Float ->
        createSeekCallback(exoPlayer, state.duration)(seekProgress)
        // Only reset timer if not dragging (i.e., tap seek)
        if (!state.isDraggingProgress) {
            resetControlsTimer()
        }
    }
    val onPrevious = remember(exoPlayer) {
        createPreviousCallback(
            exoPlayer,
            resetControlsTimer,
            { newPosition -> state.currentPosition = newPosition }
        )
    }
    val onNext = remember(exoPlayer, state.duration) {
        createNextCallback(
            exoPlayer,
            state.duration,
            resetControlsTimer,
            { newPosition -> state.currentPosition = newPosition }
        )
    }
    val onPlayPause = remember(exoPlayer) {
        createPlayPauseCallback(exoPlayer, resetControlsTimer)
    }
    val onVideoTap = {
        state.showControls = !state.showControls
        if (state.showControls) {
            state.focusedButton = FocusedButton.PREVIOUS
            resetControlsTimer()
        } else {
            state.focusedButton = null
        }
    }
    
    // Focus requester
    val playerFocusRequester = remember { FocusRequester() }
    
    LaunchedEffect(Unit) {
        playerFocusRequester.requestFocus()
    }

    val isControlsFocusActive = state.showControls && state.focusedButton != null
    val isOverlayWithoutControlFocus = state.showControls && state.focusedButton == null
    val exitControlsFocus: () -> Boolean = {
        state.focusedButton = null
        playerFocusRequester.requestFocus()
        resetControlsTimer()
        true
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SharedColors.videoPlayerBg)
            .focusRequester(playerFocusRequester)
            .focusable() // Make Box focusable to receive key events
            .createTapGestureModifier(onTap = onVideoTap)
            .createSwipeGestureModifier(onBack = {
                // Cancel next episode auto-play if visible
                if (nextEpisodeState is NextEpisodeState.Visible) {
                    viewModel.hideNextEpisodeButton()
                }
                attemptExit()
            })
            .onBackButton(onBack = {
                // Cancel next episode auto-play if visible
                if (nextEpisodeState is NextEpisodeState.Visible) {
                    viewModel.hideNextEpisodeButton()
                }
                attemptExit()
            })
            .onDpadCenter {
                if (!state.showControls) {
                    // First Enter/OK: only reveal the controls overlay, focus Play/Pause.
                    // Second Enter then hits the focused button and toggles playback.
                    resetControlsTimer()
                    state.focusedButton = FocusedButton.PLAY_PAUSE
                }
            }
            .onDpadDirection(
                onLeft = {
                    if (isControlsFocusActive) {
                        false
                    } else {
                        handleDpadSeek(false)
                    }
                },
                onRight = {
                    if (isControlsFocusActive) {
                        false
                    } else {
                        handleDpadSeek(true)
                    }
                },
                onUp = {
                    if (isControlsFocusActive) {
                        exitControlsFocus()
                    } else {
                        handleChannelUp()
                    }
                },
                onDown = {
                    if (isControlsFocusActive) {
                        exitControlsFocus()
                    } else if (isOverlayWithoutControlFocus) {
                        state.focusedButton = FocusedButton.PLAY_PAUSE
                        resetControlsTimer()
                        true
                    } else {
                        handleChannelDown()
                    }
                }
            )
            .createVerticalSwipeGestureModifier(
                onSwipeUp = { handleChannelUp() },
                onSwipeDown = { handleChannelDown() }
            )
    ) {
        // ExoPlayer view (without default controls)
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    useController = false // Disable default controls
                    playerViewRef = this
                }
            },
            update = { view ->
                view.player = exoPlayer
                view.keepScreenOn = keepScreenOn
            },
            modifier = Modifier.fillMaxSize()
        )

        // Loading indicator during buffering
        PlayerLoadingIndicator(
            isBuffering = state.playbackState == Player.STATE_BUFFERING,
            modifier = Modifier.fillMaxSize()
        )

        // Content info overlay (top center)
        if (state.showControls) {
            ContentInfoOverlay(
                contentType = contentType,
                title = currentTitle ?: title,
                seriesData = contentInfoSeriesData,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
            )
        }

        // Clock/date overlay (top right)
        if (state.showControls) {
            OverlayClockDate(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = Dimensions.spacingMD, end = Dimensions.spacingMD)
            )
        }
        
        // Custom player controls
        if (state.showControls) {
            CustomPlayerControls(
                state = PlayerState(
                    isPlaying = state.isPlaying,
                    currentPosition = state.currentPosition,
                    duration = state.duration,
                    progress = progress,
                    bufferedPosition = state.bufferedPosition
                ),
                callbacks = PlayerCallbacks(
                    onPrevious = onPrevious,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    onNextEpisode = if (contentType == "series" && hasNextEpisodeAvailable) {
                        { viewModel.triggerAutoPlayNext() }
                    } else {
                        null
                    },
                    onSeek = onSeek,
                    onThumbsUp = onThumbsUp,
                    onThumbsDown = onThumbsDown,
                    dragCallbacks = DragCallbacks(
                        onDragStart = {
                            wasPlayingBeforeDrag = exoPlayer.isPlaying
                            exoPlayer.pause()
                            state.isDraggingProgress = true
                        },
                        onDragProgress = { dragProg ->
                            if (state.duration > 0) {
                                state.currentPosition = (dragProg * state.duration).toLong()
                            }
                        },
                        onDragEnd = { finalProgress ->
                            if (state.duration > 0) {
                                val seekPosition = (finalProgress * state.duration).toLong()
                                exoPlayer.seekTo(seekPosition)
                                state.currentPosition = seekPosition
                            }
                            if (wasPlayingBeforeDrag) {
                                exoPlayer.play()
                            }
                            state.isDraggingProgress = false
                            resetControlsTimer()
                        }
                    ),
                    isDragging = state.isDraggingProgress
                ),
                utilityCallbacks = PlayerUtilityCallbacks(
                    onDebug = {
                        state.showDebugOverlay = !state.showDebugOverlay
                    },
                    onSettings = {
                        state.showSettingsDialog = true
                    },
                    onFocusChange = { button ->
                        state.focusedButton = button
                        if (button != null) {
                            resetControlsTimer()
                        }
                    }
                ),
                contentType = contentType,
                focusedButton = state.focusedButton,
                modifier = Modifier.fillMaxSize(),
                ratingUiState = RatingUiState(
                    ratedThumb = state.ratedThumb,
                    showRatingButtons = contentType != "live"
                )
            )

            // Central play/pause button with seek feedback
            val seekFeedback = state.seekFeedbackText
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Left spacer (for backward seek feedback)
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    if (seekFeedback != null && seekFeedback.startsWith("-")) {
                        Text(
                            text = seekFeedback,
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 16.dp)
                        )
                    }
                }
                
                // Central button (always at same position)
                CentralPlayPauseButton(
                    isPlaying = state.isPlaying,
                    onClick = onPlayPause
                )
                
                // Right spacer (for forward seek feedback)
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (seekFeedback != null && seekFeedback.startsWith("+")) {
                        Text(
                            text = seekFeedback,
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }
        }
        
        // Debug overlay
        if (state.showDebugOverlay) {
            DebugOverlay(
                params = DebugOverlayParams(
                    exoPlayer = exoPlayer,
                    playerState = state.playbackState,
                    currentPosition = state.currentPosition,
                    duration = state.duration,
                    bufferedPosition = state.bufferedPosition,
                    isPlaying = state.isPlaying,
                    measuredBitrate = measuredBitrate,
                    videoDecoderName = videoDecoderName,
                    audioDecoderName = audioDecoderName
                ),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(8.dp)
            )
        }
        
        // Settings dialog
        if (state.showSettingsDialog) {
            VideoSettingsDialog(
                onDismiss = { state.showSettingsDialog = false },
                exoPlayer = exoPlayer,
                playerView = playerViewRef,
                playbackSettingsRepository = playbackSettingsRepository,
                onAudioTrackOverridden = { userOverrodeAudioTrack.value = true }
            )
        }

        // Rating dialog - shown on player exit (VOD always, series only on its last episode)
        if (state.showRatingDialog) {
            RatingDialog(
                title = ratingDialogTitle,
                onRated = { entry ->
                    viewModel.saveRating(
                        entry.copy(
                            contentType = contentType,
                            contentId = currentContentId,
                            title = ratingDialogTitle
                        )
                    )
                    viewModel.markRatedInSession(contentType, currentContentId)
                    state.showRatingDialog = false
                    onBack()
                },
                onDismiss = {
                    state.showRatingDialog = false
                    onBack()
                }
            )
        }

        // Show error message if playback failed
        PlayerErrorDialog(
            errorMessage = state.errorMessage,
            onRetry = {
                state.errorMessage = null
                exoPlayer.prepare()
            },
            onDismiss = {
                state.errorMessage = null
                onBack()
            },
            modifier = Modifier.align(Alignment.Center)
        )
        
        // Discreet, non-blocking next-episode overlay (no scrim, no dialog): only a small
        // bottom-right group above the timeline. Hidden while playback is far from the end.
        NextEpisodeOverlay(
            state = nextEpisodeState,
            onWatchCredits = { viewModel.enableWatchCreditsMode() },
            onNextEpisode = { viewModel.triggerAutoPlayNext() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 100.dp, end = 16.dp)
        )

        // Snackbar for no-audio warning
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }
}


/**
 * Task 14: minimal next-episode overlay.
 *
 * Deliberately NOT a dialog and NOT a full-screen scrim - it sits above the timeline at the
 * bottom right, leaves playback visible and lets the user keep using the normal player controls.
 * "Watch Credits" dismisses the auto-play countdown, "Next Episode" advances immediately; both
 * are the streamflix overlay's dismiss/play actions. [NextEpisodeState.Hidden] renders nothing.
 */
@Composable
internal fun NextEpisodeOverlay(
    state: NextEpisodeState,
    onWatchCredits: () -> Unit,
    onNextEpisode: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (state) {
        is NextEpisodeState.Visible -> NextEpisodeButtonGroup(
            autoPlayProgress = state.autoPlayProgress,
            onWatchCredits = onWatchCredits,
            onNextEpisode = onNextEpisode,
            modifier = modifier.testTag("next-episode-overlay")
        )

        is NextEpisodeState.Error -> Text(
            text = state.message,
            color = Color.White,
            modifier = modifier
                .testTag("next-episode-overlay-error")
                .background(
                    color = Color.Black.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(16.dp)
        )

        is NextEpisodeState.Loading -> CircularProgressIndicator(
            modifier = modifier
                .testTag("next-episode-overlay-loading")
                .size(48.dp)
        )

        is NextEpisodeState.Hidden -> Unit
    }
}
