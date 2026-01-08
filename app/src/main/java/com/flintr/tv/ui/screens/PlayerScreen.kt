package com.flintr.tv.ui.screens

import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.ui.PlayerView
import com.flintr.tv.ui.theme.black
import com.flintr.tv.viewmodel.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PlayerScreen(
    streamUrl: String,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isInitializing by remember { mutableStateOf(true) }
    
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

            override fun onPlaybackStateChanged(playbackState: Int) {
                val stateString = when (playbackState) {
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

    var showControls by remember { mutableStateOf(true) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    useController = true
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (showControls) {
            PlayerControls(
                onBack = onBack,
                onToggleControls = { showControls = !showControls }
            )
        }
        
        // Show loading indicator during initialization
        if (isInitializing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
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

@Composable
fun PlayerControls(
    onBack: () -> Unit,
    onToggleControls: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(onClick = onBack) {
                Text("Zurück")
            }
            Button(onClick = onToggleControls) {
                Text("Steuerung")
            }
        }
    }
}
