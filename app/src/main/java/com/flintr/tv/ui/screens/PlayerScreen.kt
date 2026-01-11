package com.flintr.tv.ui.screens

import android.app.Activity
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalDragOrCancellation
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
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
import kotlinx.coroutines.withTimeout

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
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isInitializing by remember { mutableStateOf(true) }
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
        
        ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setLoadControl(loadControl)
            .build()
            .apply {
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_OFF
            }
    }

    DisposableEffect(streamUrl) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.e("PlayerScreen", "Player Error:", error)
                
                // Check for HLS-specific errors (IndexOutOfBoundsException in playlist parsing)
                val cause = error.cause
                val isHlsPlaylistError = cause is IndexOutOfBoundsException || 
                    (cause?.message?.contains("index") == true && 
                     cause.message?.contains("size") == true)
                
                val message = when {
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
                errorMessage = message
                isInitializing = false
            }

            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
                val stateString = when (state) {
                    Player.STATE_IDLE -> {
                        isInitializing = false
                        "STATE_IDLE"
                    }
                    Player.STATE_BUFFERING -> "STATE_BUFFERING"
                    Player.STATE_READY -> {
                        errorMessage = null // Clear error on successful playback
                        isInitializing = false
                        "STATE_READY"
                    }
                    Player.STATE_ENDED -> {
                        isInitializing = false
                        "STATE_ENDED"
                    }
                    else -> "UNKNOWN_STATE"
                }
                Log.d("PlayerScreen", "Playback state changed to $stateString")
            }
            
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        
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
                    isInitializing = false
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
        if (exoPlayer.playbackState == Player.STATE_READY && 
            !hasRestoredProgress &&
            contentType != "live" &&
            ((contentType == "movie" && streamId != null) ||
             (contentType == "series" && seriesId != null && episodeId != null))) {
            
            coroutineScope.launch {
                val savedPosition = watchProgressService.getProgress(
                    contentType = contentType,
                    streamId = streamId,
                    seriesId = seriesId,
                    episodeId = episodeId
                )
                
                if (savedPosition != null) {
                    val duration = exoPlayer.duration
                    if (duration > 0 && savedPosition < duration) {
                        Log.d("PlayerScreen", "Restoring watch progress: position=${savedPosition}ms (${savedPosition / 1000}s), duration=${duration}ms (${duration / 1000}s)")
                        // Wait a bit for player to be fully ready
                        delay(500)
                        exoPlayer.seekTo(savedPosition)
                        hasRestoredProgress = true
                        Log.d("PlayerScreen", "Watch progress restored successfully")
                    } else {
                        Log.d("PlayerScreen", "Skipping progress restore: savedPosition ($savedPosition) >= duration ($duration) or duration invalid")
                        hasRestoredProgress = true
                    }
                } else {
                    Log.d("PlayerScreen", "No saved progress found for contentType: $contentType, streamId: $streamId, seriesId: $seriesId, episodeId: $episodeId")
                    hasRestoredProgress = true
                }
            }
        }
    }
    
    // Auto-save progress every 10 seconds
    LaunchedEffect(contentType, streamId, seriesId, episodeId, title) {
        if (contentType == "live") {
            return@LaunchedEffect // Skip live streams
        }
        
        if (contentType == "movie" && streamId == null) {
            return@LaunchedEffect // Skip if required IDs are missing
        }
        
        if (contentType == "series" && (seriesId == null || episodeId == null)) {
            return@LaunchedEffect // Skip if required IDs are missing
        }
        
        while (true) {
            delay(10000) // 10 seconds
            
            val position = exoPlayer.currentPosition
            val duration = exoPlayer.duration
            
            if (position > 0 && duration > 0) {
                val titleToSave = title ?: "Unknown"
                coroutineScope.launch {
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
    
    // Swipe gesture for back navigation
    var swipeStartX by remember { mutableStateOf(0f) }
    var swipeStartTime by remember { mutableStateOf(0L) }
    
    // Focus requester to ensure Box receives key events
    val playerFocusRequester = remember { FocusRequester() }
    val viewConfiguration = LocalViewConfiguration.current
    
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
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown()
                        var isTap = true
                        swipeStartX = down.position.x
                        swipeStartTime = System.currentTimeMillis()
                        
                        try {
                            withTimeout(viewConfiguration.longPressTimeoutMillis) {
                                var dragOccurred = false
                                val drag = awaitHorizontalDragOrCancellation(down.id)
                                if (drag != null) {
                                    dragOccurred = true
                                    isTap = false
                                    
                                    var lastDrag: androidx.compose.ui.input.pointer.PointerInputChange = drag
                                    while (true) {
                                        val currentX = lastDrag.position.x
                                        val currentTime = System.currentTimeMillis()
                                        val deltaX = currentX - swipeStartX
                                        val deltaTime = currentTime - swipeStartTime
                                        
                                        // Check if swipe is from left edge (< 50px) and fast enough (> 500px/s)
                                        if (swipeStartX < 50f && deltaX > 0 && deltaTime > 0) {
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
                                if (!dragOccurred) {
                                    waitForUpOrCancellation()
                                }
                            }
                        } catch (_: PointerEventTimeoutCancellationException) {
                            isTap = false
                        }
                        
                        if (isTap) {
                            onVideoTap()
                        }
                    }
                }
            }
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
        if (playbackState == Player.STATE_BUFFERING) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(SharedColors.videoPlayerBg.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = androidx.compose.ui.graphics.Color.White)
            }
        }
        
        // Custom player controls
        if (showControls) {
            CustomPlayerControls(
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                duration = duration,
                progress = progress,
                focusedButton = focusedButton,
                onPrevious = onPrevious,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onSeek = onSeek,
                onDragStart = {
                    isDraggingProgress = true
                },
                onDragEnd = {
                    isDraggingProgress = false
                },
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
                },
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
        errorMessage?.let { message ->
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Wiedergabefehler",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(onClick = { 
                            errorMessage = null
                            isInitializing = true
                            exoPlayer.prepare()
                        }) {
                            Text("Erneut versuchen")
                        }
                        Button(onClick = { 
                            errorMessage = null
                            onBack()
                        }) {
                            Text("Zurück")
                        }
                    }
                }
            }
        }
    }
}
