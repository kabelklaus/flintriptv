package com.flintr.tv.ui.screens

import android.app.Activity
import android.util.Log
import android.view.ViewGroup
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.DefaultLoadControl
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
import com.flintr.tv.data.local.WatchProgressService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import com.flintr.tv.ui.components.player.*
import com.flintr.tv.ui.components.player.NextEpisodeButtonGroup
import com.flintr.tv.ui.components.player.ContentInfoOverlay
import com.flintr.tv.ui.common.PlayerConstants
import com.flintr.tv.ui.theme.SharedColors
import com.flintr.tv.ui.utils.FocusedButton
import com.flintr.tv.ui.utils.onBackButton
import com.flintr.tv.ui.utils.onDpadCenter
import androidx.hilt.navigation.compose.hiltViewModel
import com.flintr.tv.viewmodel.PlayerViewModel
import com.flintr.tv.viewmodel.NextEpisodeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Context
import android.util.Base64
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import com.flintr.tv.R

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
    val seriesData: SeriesContext? = null
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
private fun createExoPlayer(context: Context, credentials: com.flintr.tv.data.local.Credentials?): ExoPlayer {
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
    
    // Configure RenderersFactory to allow software decoders as fallback
    val renderersFactory = DefaultRenderersFactory(context).apply {
        setEnableDecoderFallback(true)
        setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
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
        Log.d("PlayerScreen", "HTTP Basic Auth configured.")
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
        Log.d("PlayerScreen", "HTTP error detected: code=${httpError.responseCode}")
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
            context.getString(R.string.codec_error)
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
    onIsPlayingChanged: (Boolean) -> Unit
): Player.Listener {
    return object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            Log.e("PlayerScreen", "Player Error:", error)
            onError(getErrorMessage(context, error))
        }

        override fun onPlaybackStateChanged(state: Int) {
            onPlaybackStateChanged(state)
            val stateString = when (state) {
                Player.STATE_IDLE -> "STATE_IDLE"
                Player.STATE_BUFFERING -> "STATE_BUFFERING"
                Player.STATE_READY -> "STATE_READY"
                Player.STATE_ENDED -> "STATE_ENDED"
                else -> "UNKNOWN_STATE"
            }
            Log.d("PlayerScreen", "Playback state changed to $stateString")
        }
        
        override fun onIsPlayingChanged(playing: Boolean) {
            onIsPlayingChanged(playing)
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
        Log.d("PlayerScreen", "Skipping progress restore: savedPosition ($savedPosition) >= duration ($duration) or duration invalid")
        return false
    }
    
    Log.d("PlayerScreen", "Restoring watch progress: position=${savedPosition}ms (${savedPosition / 1000}s), duration=${duration}ms (${duration / 1000}s)")
    delay(500) // Wait a bit for player to be fully ready
    exoPlayer.seekTo(savedPosition)
    Log.d("PlayerScreen", "Watch progress restored successfully")
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
    watchProgressService: WatchProgressService
) {
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
        var focusedButton by mutableStateOf<FocusedButton?>(null)
        var isDraggingProgress by mutableStateOf(false)
        var playbackState by mutableStateOf(Player.STATE_IDLE)
        var currentPosition by mutableStateOf(0L)
        var duration by mutableStateOf(0L)
        var bufferedPosition by mutableStateOf(0L)
        var isPlaying by mutableStateOf(false)
        var errorMessage by mutableStateOf<String?>(null)
        var hasRestoredProgress by mutableStateOf(false)
        var lastInteractionTime by mutableStateOf(System.currentTimeMillis())
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

/**
 * Sets up ExoPlayer and manages its lifecycle
 */
@Suppress("InjectDispatcher")
@Composable
private fun rememberPlayerSetup(
    context: Context,
    streamUrl: String,
    onError: (String) -> Unit,
    onPlaybackStateChanged: (Int) -> Unit,
    onIsPlayingChanged: (Boolean) -> Unit,
    coroutineScope: CoroutineScope
): ExoPlayer {
    // Get credentials from repository
    val credentialsRepository = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            CredentialsRepositoryEntryPoint::class.java
        ).credentialsRepository()
    }
    val credentials by credentialsRepository.getCredentialsFlow()
        .collectAsState(initial = credentialsRepository.getCredentials())
    
    val exoPlayer = remember(credentials) {
        createExoPlayer(context, credentials)
    }
    
    DisposableEffect(streamUrl) {
        val listener = createPlayerListener(
            context = context,
            onError = onError,
            onPlaybackStateChanged = { state ->
                onPlaybackStateChanged(state)
            },
            onIsPlayingChanged = onIsPlayingChanged
        )
        
        exoPlayer.addListener(listener)

        val prepareJob = coroutineScope.launch(Dispatchers.IO) { // NOSONAR - UI component, hardcoded dispatcher acceptable
            try {
                val mediaItem = MediaItem.fromUri(streamUrl)
                withContext(Dispatchers.Main) { // NOSONAR - UI thread required for ExoPlayer operations
                    exoPlayer.setMediaItem(mediaItem)
                    exoPlayer.prepare()
                }
            } catch (e: Exception) {
                Log.e("PlayerScreen", "Error preparing media item", e)
                withContext(Dispatchers.Main) { // NOSONAR - UI thread required for error callback
                    onError("Fehler beim Laden des Streams: ${e.message}")
                }
            }
        }

        onDispose {
            prepareJob.cancel()
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }
    
    return exoPlayer
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
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
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
    val coroutineScope: CoroutineScope
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
                if (!restored) {
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
        while (true) {
            delay(10000) // 10 seconds
            params.coroutineScope.launch {
                saveWatchProgress(
                    exoPlayer = params.exoPlayer,
                    contentType = params.contentType,
                    streamId = params.streamId,
                    seriesId = params.seriesId,
                    episodeId = params.episodeId,
                    title = params.title,
                    watchProgressService = params.watchProgressService
                )
            }
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
@Composable
fun PlayerScreen( // NOSONAR - Cognitive Complexity acceptable for UI composition with multiple state callbacks
    streamInfo: StreamInfo,
    watchProgressService: WatchProgressService,
    onBack: () -> Unit,
    onNextEpisode: ((com.flintr.tv.viewmodel.NextEpisodeInfo) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val streamUrl = streamInfo.streamUrl
    val contentType = streamInfo.contentType
    val streamId = streamInfo.streamId
    val seriesId = streamInfo.seriesId
    val episodeId = streamInfo.episodeId
    val title = streamInfo.title
    val seriesData = streamInfo.seriesData
    
    val context = LocalContext.current
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    val state = rememberPlayerState()
    val viewModel: PlayerViewModel = hiltViewModel()
    
    // Load series context if this is a series episode
    LaunchedEffect(seriesId, episodeId, seriesData) {
        if (contentType == "series" && seriesId != null && episodeId != null) {
            viewModel.loadSeriesContext(seriesId, episodeId, seriesData)
        } else {
            viewModel.clearSeriesContext()
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
    
    // State for measured bitrate and decoder info
    var measuredBitrate by remember { mutableStateOf(0L) }
    var videoDecoderName by remember { mutableStateOf(DECODER_INITIALIZING) }
    var audioDecoderName by remember { mutableStateOf(DECODER_INITIALIZING) }
    
    // Setup player with callbacks
    val exoPlayer = rememberPlayerSetup(
        context = context,
        streamUrl = streamUrl,
        onError = { message -> state.errorMessage = message },
        onPlaybackStateChanged = { playbackStateValue ->
            state.playbackState = playbackStateValue
            if (playbackStateValue == Player.STATE_READY) {
                state.errorMessage = null
            }
        },
        onIsPlayingChanged = { playing -> state.isPlaying = playing },
        coroutineScope = coroutineScope
    )
    
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
                Log.d("PlayerAnalytics", "Bitrate: ${bitrateEstimate / 1_000_000.0} Mbps " +
                    "(${totalBytesLoaded / 1024}KB in ${totalLoadTimeMs}ms)")
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
    
    // Update position and duration
    PlayerPositionUpdateEffect(
        exoPlayer = exoPlayer,
        isPlaying = state.isPlaying,
        onPositionUpdate = { position, durationValue, buffered ->
            state.currentPosition = position
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
            coroutineScope = coroutineScope
        )
    )
    
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
    LaunchedEffect(progress, contentType, seriesContextLoaded, watchCreditsMode) {
        if (contentType == "series" && seriesContextLoaded) {
            // Don't show button if watch credits mode is active
            if (watchCreditsMode) {
                Log.d("PlayerScreen", "Button not shown: watchCreditsMode is active")
                return@LaunchedEffect
            }
            
            val currentState = viewModel.nextEpisodeState.value
            val isButtonVisible = currentState is NextEpisodeState.Visible
            
            Log.d("PlayerScreen", "Progress check: progress=$progress (${(progress * 100).toInt()}%), seriesContextLoaded=$seriesContextLoaded, isButtonVisible=$isButtonVisible")
            
            if (progress >= PlayerConstants.NEXT_EPISODE_BUTTON_THRESHOLD && !isButtonVisible) {
                // Show button only if not already visible (prevents timer restart)
                // Only show if series context is loaded
                Log.d("PlayerScreen", "Calling showNextEpisodeButton() - progress >= 98.5%")
                viewModel.showNextEpisodeButton()
            } else if (progress < PlayerConstants.NEXT_EPISODE_BUTTON_THRESHOLD && isButtonVisible) {
                // User rewound, hide button
                Log.d("PlayerScreen", "Hiding button - progress < 98.5%")
                viewModel.hideNextEpisodeButton()
            }
        } else {
            if (contentType == "series") {
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
                // Standard auto-play at 98.5%
                val currentState = viewModel.nextEpisodeState.value
                if (currentState is NextEpisodeState.Visible) {
                    viewModel.triggerAutoPlayNext()
                }
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
                        watchProgressService = watchProgressService
                    )
                }
                // Navigate to next episode
                onNextEpisode(nextEpisodeInfo)
            }
        }
    }
    
    // Reset controls timer function
    val resetControlsTimer = {
        state.showControls = true
        state.lastInteractionTime = System.currentTimeMillis()
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
                onBack()
            })
            .onBackButton(onBack = {
                // Cancel next episode auto-play if visible
                if (nextEpisodeState is NextEpisodeState.Visible) {
                    viewModel.hideNextEpisodeButton()
                }
                onBack()
            })
            .onDpadCenter {
                if (!state.showControls) {
                    resetControlsTimer()
                    state.focusedButton = FocusedButton.PLAY_PAUSE
                    onPlayPause()
                }
            }
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
                    keepScreenOn = true
                }
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
                title = title,
                seriesData = contentInfoSeriesData,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
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
                    onDragStart = {
                        state.isDraggingProgress = true
                    },
                    onDragEnd = {
                        state.isDraggingProgress = false
                        resetControlsTimer()
                    }
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
                focusedButton = state.focusedButton,
                modifier = Modifier.fillMaxSize()
            )
            
            // Central play/pause button
            CentralPlayPauseButton(
                isPlaying = state.isPlaying,
                onClick = onPlayPause,
                modifier = Modifier.align(Alignment.Center)
            )
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
                onDismiss = { state.showSettingsDialog = false }
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
        
        // Next episode button group (shown when progress >= 98.5% or auto-play is active)
        // Positioned directly above the timeline (progress bar), right-aligned
        // CustomPlayerControls are at BottomCenter with padding
        // Progress bar is the first element, so button should be just above it
        // Height calculation: ProgressBar (~4dp) + Spacer (12dp) + Controls (~48dp) + Padding vertical (12dp) = ~76dp
        // Position button directly above the progress bar
        when (val nextState = nextEpisodeState) {
            is NextEpisodeState.Visible -> {
                NextEpisodeButtonGroup(
                    autoPlayProgress = nextState.autoPlayProgress,
                    onWatchCredits = {
                        viewModel.enableWatchCreditsMode()
                    },
                    onNextEpisode = {
                        viewModel.triggerAutoPlayNext()
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 100.dp, end = 16.dp) // Position above timeline with more spacing
                )
            }
            is NextEpisodeState.Error -> {
                // Show error message for next episode loading
                Text(
                    text = nextState.message,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 100.dp, end = 16.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(16.dp)
                )
            }
            is NextEpisodeState.Loading -> {
                // Show loading indicator
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 100.dp, end = 16.dp)
                        .size(48.dp)
                )
            }
            is NextEpisodeState.Hidden -> {
                // Nothing to show
            }
        }
    }
}
