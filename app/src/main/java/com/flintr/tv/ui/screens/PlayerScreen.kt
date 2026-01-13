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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.flintr.tv.data.local.WatchProgressService
import com.flintr.tv.ui.components.player.*
import com.flintr.tv.ui.theme.SharedColors
import com.flintr.tv.ui.utils.FocusedButton
import com.flintr.tv.ui.utils.onBackButton
import com.flintr.tv.ui.utils.onDpadCenter
import com.flintr.tv.viewmodel.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Context
import androidx.compose.ui.input.pointer.PointerInputChange

/**
 * Data class containing stream information for playback
 */
data class StreamInfo(
    val streamUrl: String,
    val contentType: String,
    val streamId: Int? = null,
    val seriesId: Int? = null,
    val episodeId: Int? = null,
    val title: String? = null
)

/**
 * Creates and configures an ExoPlayer instance with optimized settings
 */
private fun createExoPlayer(context: Context): ExoPlayer {
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
    
    // Create HttpDataSourceFactory
    val httpDataSourceFactory: HttpDataSource.Factory = DefaultHttpDataSource.Factory()
        .setUserAgent(userAgent)
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(30000)
        .setReadTimeoutMs(30000)
    
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
 * Extracts error message from PlaybackException
 */
private fun getErrorMessage(error: PlaybackException): String {
    // Check for HLS-specific errors (IndexOutOfBoundsException in playlist parsing)
    val cause = error.cause
    val isHlsPlaylistError = cause is IndexOutOfBoundsException || 
        (cause?.message?.contains("index") == true && 
         cause.message?.contains("size") == true)
    
    return when {
        isHlsPlaylistError -> 
            "HLS-Playlist ist leer oder ungültig. Der Stream ist möglicherweise nicht verfügbar."
        error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
        error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> 
            "Netzwerkfehler. Bitte Verbindung prüfen."
        error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
        error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> 
            "Videoformat wird nicht unterstützt."
        error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
        error.errorCode == PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED -> 
            "Codec-Fehler. Videoformat wird möglicherweise nicht unterstützt."
        else -> "Wiedergabefehler: ${error.message ?: "Unbekannter Fehler"}"
    }
}

/**
 * Creates a Player.Listener with callbacks for state changes
 */
private fun createPlayerListener(
    onError: (String) -> Unit,
    onPlaybackStateChanged: (Int) -> Unit,
    onIsPlayingChanged: (Boolean) -> Unit
): Player.Listener {
    return object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            Log.e("PlayerScreen", "Player Error:", error)
            onError(getErrorMessage(error))
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
 * Creates a modifier for swipe gesture detection (from left edge)
 */
private fun Modifier.createSwipeGestureModifier(onBack: () -> Unit): Modifier {
    return this.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val down = awaitFirstDown(requireUnconsumed = false)
                val swipeStartX = down.position.x
                val swipeStartTime = System.currentTimeMillis()
                
                // Only detect swipe from left edge (< 50px)
                if (swipeStartX < 50f) {
                    val drag = awaitHorizontalDragOrCancellation(down.id)
                    if (drag != null) {
                        var lastDrag: PointerInputChange = drag
                        while (true) {
                            val currentX = lastDrag.position.x
                            val currentTime = System.currentTimeMillis()
                            val deltaX = currentX - swipeStartX
                            val deltaTime = currentTime - swipeStartTime
                            
                            // Check if swipe is fast enough (> 500px/s)
                            if (deltaX > 0 && deltaTime > 0) {
                                val velocity = deltaX / deltaTime * 1000 // px/s
                                if (velocity > 500) {
                                    onBack()
                                    break
                                }
                            }
                            
                            val dragEvent = awaitHorizontalDragOrCancellation(lastDrag.id)
                            if (dragEvent == null) {
                                break
                            } else {
                                lastDrag = dragEvent
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlayerScreen(
    streamInfo: StreamInfo,
    playerViewModel: PlayerViewModel,
    watchProgressService: WatchProgressService,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val streamUrl = streamInfo.streamUrl
    val contentType = streamInfo.contentType
    val streamId = streamInfo.streamId
    val seriesId = streamInfo.seriesId
    val episodeId = streamInfo.episodeId
    val title = streamInfo.title
    
    val context = LocalContext.current
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var hasRestoredProgress by remember { mutableStateOf(false) }
    
    // Player UI State
    var showControls by remember { mutableStateOf(true) }
    var showDebugOverlay by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var focusedButton by remember { mutableStateOf<FocusedButton?>(null) }
    var isDraggingProgress by remember { mutableStateOf(false) }
    var playbackState by remember { mutableStateOf(Player.STATE_IDLE) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var bufferedPosition by remember { mutableStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    
    // Hide System UI in fullscreen mode
    DisposableEffect(Unit) {
        val window = (view.context as Activity).window
        val windowInsetsController = WindowCompat.getInsetsController(window, view)
        
        // Hide system bars
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        windowInsetsController.systemBarsBehavior = 
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        
        onDispose {
            // Show system bars when leaving player
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    
    val exoPlayer = remember {
        createExoPlayer(context)
    }

    DisposableEffect(streamUrl) {
        val listener = createPlayerListener(
            onError = { message -> errorMessage = message },
            onPlaybackStateChanged = { state ->
                playbackState = state
                if (state == Player.STATE_READY) {
                    errorMessage = null // Clear error on successful playback
                }
            },
            onIsPlayingChanged = { playing -> isPlaying = playing }
        )
        
        exoPlayer.addListener(listener)

        // Prepare player asynchronously to avoid blocking main thread
        val prepareJob = coroutineScope.launch(Dispatchers.IO) {
            try {
                val mediaItem = MediaItem.fromUri(streamUrl)
                withContext(Dispatchers.Main) {
                    exoPlayer.setMediaItem(mediaItem)
                    exoPlayer.prepare()
                }
            } catch (e: Exception) {
                Log.e("PlayerScreen", "Error preparing media item", e)
                withContext(Dispatchers.Main) {
                    errorMessage = "Fehler beim Laden des Streams: ${e.message}"
                }
            }
        }

        onDispose {
            prepareJob.cancel()
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }
    
    // Update position and duration continuously
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = exoPlayer.currentPosition
            duration = exoPlayer.duration
            bufferedPosition = exoPlayer.bufferedPosition
            delay(250) // Update every 250ms
        }
    }
    
    // Restore watch progress when player is ready
    LaunchedEffect(exoPlayer.playbackState) {
        if (exoPlayer.playbackState == Player.STATE_READY && !hasRestoredProgress) {
            coroutineScope.launch {
                val restored = restoreWatchProgress(
                    exoPlayer = exoPlayer,
                    contentType = contentType,
                    streamId = streamId,
                    seriesId = seriesId,
                    episodeId = episodeId,
                    watchProgressService = watchProgressService
                )
                hasRestoredProgress = true
                if (!restored) {
                    Log.d("PlayerScreen", "No saved progress found for contentType: $contentType, streamId: $streamId, seriesId: $seriesId, episodeId: $episodeId")
                }
            }
        }
    }
    
    // Auto-save progress every 10 seconds
    LaunchedEffect(contentType, streamId, seriesId, episodeId, title) {
        while (true) {
            delay(10000) // 10 seconds
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
        }
    }
    
    // Auto-hide controls after 3 seconds of inactivity
    LaunchedEffect(showControls, isDraggingProgress) {
        if (showControls && !isDraggingProgress) {
            delay(3000)
            if (!isDraggingProgress) {
                showControls = false
                focusedButton = null
            }
        }
    }
    
    // Calculate progress
    val progress = if (duration > 0) {
        (currentPosition.toFloat() / duration).coerceIn(0f, 1f)
    } else {
        0f
    }
    
    // Handle seek
    val onSeek = { seekProgress: Float ->
        if (duration > 0) {
            val seekPosition = (seekProgress * duration).toLong()
            exoPlayer.seekTo(seekPosition)
        }
    }
    
    // Handle previous (10s back)
    val onPrevious = {
        val newPosition = (exoPlayer.currentPosition - 10000).coerceAtLeast(0L)
        exoPlayer.seekTo(newPosition)
        showControls = true
    }
    
    // Handle next (10s forward)
    val onNext = {
        val newPosition = (exoPlayer.currentPosition + 10000).coerceAtMost(duration)
        exoPlayer.seekTo(newPosition)
        showControls = true
    }
    
    // Handle play/pause
    val onPlayPause = {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
        showControls = true
    }
    
    // Handle tap on video to toggle controls
    val onVideoTap = {
        showControls = !showControls
        if (showControls) {
            focusedButton = FocusedButton.PREVIOUS
        }
    }
    
    // Focus requester to ensure Box receives key events
    val playerFocusRequester = remember { FocusRequester() }
    
    // Request focus when player screen is shown
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
            .createSwipeGestureModifier(onBack = onBack)
            .onBackButton(onBack = onBack)
            .onDpadCenter {
                if (!showControls) {
                    showControls = true
                    focusedButton = FocusedButton.PLAY_PAUSE
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
            isBuffering = playbackState == Player.STATE_BUFFERING,
            modifier = Modifier.fillMaxSize()
        )
        
        // Custom player controls
        if (showControls) {
            CustomPlayerControls(
                state = PlayerState(
                    isPlaying = isPlaying,
                    currentPosition = currentPosition,
                    duration = duration,
                    progress = progress,
                    bufferedPosition = bufferedPosition
                ),
                callbacks = PlayerCallbacks(
                    onPrevious = onPrevious,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    onSeek = onSeek,
                    onDragStart = {
                        isDraggingProgress = true
                    },
                    onDragEnd = {
                        isDraggingProgress = false
                    }
                ),
                utilityCallbacks = PlayerUtilityCallbacks(
                    onDebug = {
                        showDebugOverlay = !showDebugOverlay
                    },
                    onSettings = {
                        showSettingsDialog = true
                    },
                    onFocusChange = { button ->
                        focusedButton = button
                        if (button != null) {
                            showControls = true
                        }
                    }
                ),
                focusedButton = focusedButton,
                modifier = Modifier.fillMaxSize()
            )
            
            // Central play/pause button
            CentralPlayPauseButton(
                isPlaying = isPlaying,
                onClick = onPlayPause,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        
        // Debug overlay
        if (showDebugOverlay) {
            DebugOverlay(
                playerState = playbackState,
                currentPosition = currentPosition,
                duration = duration,
                bufferedPosition = bufferedPosition,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(8.dp)
            )
        }
        
        // Settings dialog
        if (showSettingsDialog) {
            VideoSettingsDialog(
                onDismiss = { showSettingsDialog = false }
            )
        }
        
        // Show error message if playback failed
        PlayerErrorDialog(
            errorMessage = errorMessage,
            onRetry = {
                errorMessage = null
                exoPlayer.prepare()
            },
            onDismiss = {
                errorMessage = null
                onBack()
            },
            modifier = Modifier.align(Alignment.Center)
        )
    }
}
