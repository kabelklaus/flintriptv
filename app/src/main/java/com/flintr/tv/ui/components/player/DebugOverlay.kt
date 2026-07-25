@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.flintr.tv.ui.components.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.app.ActivityManager
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.view.WindowManager
import com.flintr.tv.BuildConfig
import kotlinx.coroutines.delay
import java.io.BufferedReader
import java.io.FileReader
import java.util.Locale
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.FontSizes
import com.flintr.tv.ui.utils.formatTime
import com.flintr.tv.utils.error.reportControlFlow

// Constants for debug overlay
private const val DECODER_INITIALIZING = "Initializing..."
private const val HARDWARE_ACCELERATED_SOFTWARE = "false (SW)"

/**
 * Data class for debug information entry
 */
data class DebugInfo(
    val label: String,
    val value: String
)

/**
 * Data class for DebugOverlay parameters
 */
data class DebugOverlayParams(
    val exoPlayer: ExoPlayer?,
    val playerState: Int,
    val currentPosition: Long,
    val duration: Long,
    val bufferedPosition: Long,
    val isPlaying: Boolean,
    val measuredBitrate: Long = 0L,
    val videoDecoderName: String = "N/A",
    val audioDecoderName: String = "N/A"
)

/**
 * Data class for video/media debug parameters
 */
data class VideoMediaDebugParams(
    val videoResolution: String,
    val codecs: String,
    val bitrate: String,
    val decoderName: String,
    val audioDecoderName: String,
    val audioCodec: String,
    val audioSampleRate: String,
    val audioChannels: String,
    val hardwareAccelerated: String,
    val transferFunction: String,
    val colorSpace: String,
    val droppedRenderedFrames: String,
    val bufferSizeSeconds: String
)

/**
 * Data class for system/hardware debug parameters
 */
data class SystemHardwareDebugParams(
    val deviceName: String,
    val androidSDK: String,
    val displayDPI: String,
    val uiResolution: String,
    val displayModeID: String,
    val displayModesLength: String,
    val cpuCores: String,
    val cpuUsage: String,
    val pipSupported: String
)

/**
 * Formats decoder name, returning DECODER_INITIALIZING if decoder is unknown or initializing
 */
private fun formatDecoderName(decoderName: String): String {
    return if (decoderName != "unknown" && decoderName != DECODER_INITIALIZING) {
        decoderName
    } else {
        DECODER_INITIALIZING
    }
}

/**
 * Creates player status debug information
 */
private fun createPlayerStatusDebugInfo(
    playerState: Int,
    currentPosition: Long,
    duration: Long,
    bufferedPosition: Long,
    isPlaying: Boolean
): List<DebugInfo> {
    val playerPaused = isPlayerPaused(isPlaying)
    val playbackState = getPlaybackState(playerState)
    
    return listOf(
        DebugInfo("State", getPlayerStateString(playerState)),
        DebugInfo("Player paused", playerPaused),
        DebugInfo("Playback state", playbackState),
        DebugInfo("Position", formatTime(currentPosition)),
        DebugInfo("Duration", formatTime(duration)),
        DebugInfo("Buffered", formatTime(bufferedPosition)),
        DebugInfo("Progress", if (duration > 0) "${((currentPosition.toFloat() / duration) * 100).toInt()}%" else "0%"),
        DebugInfo("Buffer %", if (duration > 0) "${((bufferedPosition.toFloat() / duration) * 100).toInt()}%" else "0%")
    )
}

/**
 * Creates video/media debug information
 */
private fun createVideoMediaDebugInfo(
    params: VideoMediaDebugParams
): List<DebugInfo> {
    return listOf(
        DebugInfo("Video Resolution", params.videoResolution),
        DebugInfo("Video/Audio codecs", params.codecs),
        DebugInfo("Video/Audio bitrate", params.bitrate),
        DebugInfo("Video decoder name", params.decoderName),
        DebugInfo("Audio decoder name", formatDecoderName(params.audioDecoderName)),
        DebugInfo("Audio codec", params.audioCodec),
        DebugInfo("Audio sample rate", params.audioSampleRate),
        DebugInfo("Audio channels", params.audioChannels),
        DebugInfo("Hardware accelerated", params.hardwareAccelerated),
        DebugInfo("Transfer function", params.transferFunction),
        DebugInfo("Color space", params.colorSpace),
        DebugInfo("Dropped/Rendered frames", params.droppedRenderedFrames),
        DebugInfo("Buffer size (seconds)", params.bufferSizeSeconds)
    )
}

/**
 * Creates system/hardware debug information
 */
private fun createSystemHardwareDebugInfo(
    params: SystemHardwareDebugParams
): List<DebugInfo> {
    return listOf(
        DebugInfo("Device name", params.deviceName),
        DebugInfo("Android SDK", params.androidSDK),
        DebugInfo("Display DPI", params.displayDPI),
        DebugInfo("UI resolution", params.uiResolution),
        DebugInfo("Display mode ID", params.displayModeID),
        DebugInfo("Display modes length", params.displayModesLength),
        DebugInfo("CPU Cores", params.cpuCores),
        DebugInfo("CPU use", params.cpuUsage),
        DebugInfo("PiP supported", params.pipSupported)
    )
}

/**
 * Creates memory debug information
 */
private fun createMemoryDebugInfo(
    maxHeapMemory: String,
    allocatedHeapMemory: String,
    totalUsedRAM: String,
    ramUsedPercent: String,
    diskCacheSize: String
): List<DebugInfo> {
    return listOf(
        DebugInfo("Max heap memory (MB)", maxHeapMemory),
        DebugInfo("Allocated heap memory (MB)", allocatedHeapMemory),
        DebugInfo("Total/used RAM", totalUsedRAM),
        DebugInfo("RAM used %", ramUsedPercent),
        DebugInfo("Disk cache size (MB)", diskCacheSize)
    )
}

/**
 * Creates app version debug information
 */
private fun createAppVersionDebugInfo(
    flintrIPTVVersion: String,
    exoPlayerVersion: String,
    exoPlayerEngine: String,
    hlsVersion: String,
    okHttpVersion: String,
    cronetVersion: String
): List<DebugInfo> {
    return listOf(
        DebugInfo("Flintr iptv version", flintrIPTVVersion),
        DebugInfo("ExoPlayer version", exoPlayerVersion),
        DebugInfo("ExoPlayer engine", exoPlayerEngine),
        DebugInfo("HLS support", hlsVersion),
        DebugInfo("OkHttp version", okHttpVersion),
        DebugInfo("Cronet Version", cronetVersion)
    )
}

/**
 * Debug overlay showing player information
 * @param params DebugOverlayParams containing all player and system information
 * @param modifier Modifier for the overlay
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Composable
fun DebugOverlay(
    params: DebugOverlayParams,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val exoPlayer = params.exoPlayer
    val playerState = params.playerState
    val currentPosition = params.currentPosition
    val duration = params.duration
    val bufferedPosition = params.bufferedPosition
    val isPlaying = params.isPlaying
    val measuredBitrate = params.measuredBitrate
    val videoDecoderName = params.videoDecoderName
    val audioDecoderName = params.audioDecoderName
    
    // STATE for dynamic player values (updated live)
    var videoResolution by remember { mutableStateOf("") }
    var codecs by remember { mutableStateOf("") }
    var bitrate by remember { mutableStateOf("N/A") }
    var decoderName by remember { mutableStateOf("N/A") }
    var hardwareAccelerated by remember { mutableStateOf("N/A") }
    var transferFunction by remember { mutableStateOf("N/A") }
    var colorSpace by remember { mutableStateOf("N/A") }
    var droppedRenderedFrames by remember { mutableStateOf("") }
    var audioCodec by remember { mutableStateOf("N/A") }
    var audioSampleRate by remember { mutableStateOf("N/A") }
    var audioChannels by remember { mutableStateOf("N/A") }
    
    // STATE for dynamic system values
    var cpuUsage by remember { mutableStateOf("") }
    var allocatedHeapMemory by remember { mutableStateOf("") }
    var totalUsedRAM by remember { mutableStateOf("") }
    var ramUsedPercent by remember { mutableStateOf("") }
    
    // Statische Werte (nur 1x berechnet)
    val displayDPI = remember { getDisplayDPI(context) }
    val uiResolution = remember { getUIResolution(context) }
    val displayModeID = remember { getDisplayModeID(context) }
    val displayModesLength = remember { getDisplayModesLength(context) }
    val exoPlayerVersion = remember { getExoPlayerVersion() }
    val exoPlayerEngine = remember { getExoPlayerEngine() }
    val cronetVersion = remember { getCronetVersion() }
    val hlsVersion = remember { getHLSVersion() }
    val okHttpVersion = remember { getOkHttpVersion() }
    val flintrIPTVVersion = remember { getFlintrIPTVVersion(context) }
    val deviceName = remember { getDeviceName() }
    val androidSDK = remember { getAndroidSDK() }
    val diskCacheSize = remember { getDiskCacheSize(exoPlayer) }
    val maxHeapMemory = remember { getMaxHeapMemory() }
    val pipSupported = remember { getPiPSupported(context) }
    val cpuCores = remember { getCPUCores() }
    
    // Use rememberUpdatedState to access latest values without restarting LaunchedEffect
    val currentMeasuredBitrate by rememberUpdatedState(measuredBitrate)
    val currentVideoDecoderName by rememberUpdatedState(videoDecoderName)
    
    // LIVE-UPDATE for player values (every 500ms)
    LaunchedEffect(exoPlayer) {
        if (exoPlayer == null) return@LaunchedEffect
        
        while (true) {
            // Debug-Logging (nur in DEBUG-Builds)
            if (BuildConfig.DEBUG) {
                Log.d("DebugOverlay", "videoFormat: ${exoPlayer.videoFormat}")
                Log.d("DebugOverlay", "audioFormat: ${exoPlayer.audioFormat}")
            }
            
            // Update player values
            videoResolution = getVideoResolution(exoPlayer)
            codecs = getVideoAudioCodecs(exoPlayer)
            droppedRenderedFrames = getDroppedRenderedFrames(exoPlayer)
            // Use measured bitrate from AnalyticsListener instead of reflection
            bitrate = formatMeasuredBitrate(currentMeasuredBitrate)
            decoderName = formatDecoderName(currentVideoDecoderName)
            hardwareAccelerated = isHardwareAccelerated(currentVideoDecoderName)
            transferFunction = getTransferFunction(exoPlayer)
            colorSpace = getColorSpace(exoPlayer)
            audioCodec = getAudioCodec(exoPlayer)
            audioSampleRate = getAudioSampleRate(exoPlayer)
            audioChannels = getAudioChannels(exoPlayer)

            delay(500)
        }
    }
    
    // LIVE-UPDATE for system values (every 2 seconds)
    LaunchedEffect(Unit) {
        while (true) {
            cpuUsage = getCPUUsage()
            allocatedHeapMemory = getAllocatedHeapMemory()
            totalUsedRAM = getTotalUsedRAM(context)
            ramUsedPercent = getRAMUsedPercent(context)
            delay(2000)
        }
    }
    
    // Berechnete Werte aus Parametern
    val bufferSizeSeconds = getBufferSizeSeconds(bufferedPosition)
    
    val debugInfo = createPlayerStatusDebugInfo(
        playerState = playerState,
        currentPosition = currentPosition,
        duration = duration,
        bufferedPosition = bufferedPosition,
        isPlaying = isPlaying
    ) + createVideoMediaDebugInfo(
        VideoMediaDebugParams(
            videoResolution = videoResolution,
            codecs = codecs,
            bitrate = bitrate,
            decoderName = decoderName,
            audioDecoderName = audioDecoderName,
            audioCodec = audioCodec,
            audioSampleRate = audioSampleRate,
            audioChannels = audioChannels,
            hardwareAccelerated = hardwareAccelerated,
            transferFunction = transferFunction,
            colorSpace = colorSpace,
            droppedRenderedFrames = droppedRenderedFrames,
            bufferSizeSeconds = bufferSizeSeconds
        )
    ) + createSystemHardwareDebugInfo(
        SystemHardwareDebugParams(
            deviceName = deviceName,
            androidSDK = androidSDK,
            displayDPI = displayDPI,
            uiResolution = uiResolution,
            displayModeID = displayModeID,
            displayModesLength = displayModesLength,
            cpuCores = cpuCores,
            cpuUsage = cpuUsage,
            pipSupported = pipSupported
        )
    ) + createMemoryDebugInfo(
        maxHeapMemory = maxHeapMemory,
        allocatedHeapMemory = allocatedHeapMemory,
        totalUsedRAM = totalUsedRAM,
        ramUsedPercent = ramUsedPercent,
        diskCacheSize = diskCacheSize
    ) + createAppVersionDebugInfo(
        flintrIPTVVersion = flintrIPTVVersion,
        exoPlayerVersion = exoPlayerVersion,
        exoPlayerEngine = exoPlayerEngine,
        hlsVersion = hlsVersion,
        okHttpVersion = okHttpVersion,
        cronetVersion = cronetVersion
    )
    
    Box(
        modifier = modifier
            .fillMaxHeight()
            .widthIn(min = 350.dp, max = 450.dp)
            .fillMaxWidth(0.3f)
            .background(
                Color.Black.copy(alpha = 0.8f),
                RoundedCornerShape(Dimensions.radiusMD)
            )
            .padding(Dimensions.spacingSM)
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(debugInfo) { info ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = info.label,
                        fontSize = FontSizes.fontSizeXXS,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.End
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = info.value,
                        fontSize = FontSizes.fontSizeXXS,
                        color = Color.White,
                        fontWeight = FontWeight.W500,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Start
                    )
                }
            }
        }
    }
}

/**
 * Get readable string for player state
 */
private fun getPlayerStateString(state: Int): String {
    return when (state) {
        Player.STATE_IDLE -> "IDLE"
        Player.STATE_BUFFERING -> "BUFFERING"
        Player.STATE_READY -> "READY"
        Player.STATE_ENDED -> "ENDED"
        else -> "UNKNOWN"
    }
}

/**
 * Extract video resolution from ExoPlayer in format "width x height @ framerate"
 */
@UnstableApi
private fun getVideoResolution(exoPlayer: ExoPlayer?): String {
    if (exoPlayer == null) return ""
    
    val videoFormat = exoPlayer.videoFormat ?: return ""
    val width = videoFormat.width
    val height = videoFormat.height
    val frameRate = videoFormat.frameRate
    
    if (width == Format.NO_VALUE || height == Format.NO_VALUE) {
        return ""
    }
    
    val frameRateStr = if (frameRate > 0f) {
        "@${frameRate.toInt()}"
    } else {
        ""
    }
    
    return "$width x $height$frameRateStr"
}

/**
 * Extract video and audio codecs from ExoPlayer in format "video_codec(video_id)/audio_codec(audio_id)"
 */
@UnstableApi
private fun getVideoAudioCodecs(exoPlayer: ExoPlayer?): String {
    if (exoPlayer == null) return ""
    
    val videoFormat = exoPlayer.videoFormat
    val audioFormat = exoPlayer.audioFormat
    
    val videoCodec = videoFormat?.codecs ?: ""
    val videoId = videoFormat?.id ?: ""
    val audioCodec = audioFormat?.codecs ?: ""
    val audioId = audioFormat?.id ?: ""
    
    val videoPart = if (videoCodec.isNotEmpty() && videoId.isNotEmpty()) {
        "$videoCodec($videoId)"
    } else if (videoCodec.isNotEmpty()) {
        videoCodec
    } else {
        ""
    }
    
    val audioPart = if (audioCodec.isNotEmpty() && audioId.isNotEmpty()) {
        "$audioCodec($audioId)"
    } else if (audioCodec.isNotEmpty()) {
        audioCodec
    } else {
        ""
    }
    
    return when {
        videoPart.isNotEmpty() && audioPart.isNotEmpty() -> "$videoPart/$audioPart"
        videoPart.isNotEmpty() -> videoPart
        audioPart.isNotEmpty() -> audioPart
        else -> ""
    }
}

/**
 * Extract audio codec from ExoPlayer audio format.
 */
@UnstableApi
private fun getAudioCodec(exoPlayer: ExoPlayer?): String {
    if (exoPlayer == null) return "N/A"
    val format = exoPlayer.audioFormat ?: return "N/A"
    return format.codecs?.takeIf { it.isNotEmpty() } ?: "N/A"
}

/**
 * Extract audio sample rate from ExoPlayer audio format.
 */
@UnstableApi
private fun getAudioSampleRate(exoPlayer: ExoPlayer?): String {
    if (exoPlayer == null) return "N/A"
    val format = exoPlayer.audioFormat ?: return "N/A"
    val rate = format.sampleRate
    return if (rate != Format.NO_VALUE && rate > 0) "${rate}Hz" else "N/A"
}

/**
 * Extract audio channel count from ExoPlayer audio format.
 */
@UnstableApi
private fun getAudioChannels(exoPlayer: ExoPlayer?): String {
    if (exoPlayer == null) return "N/A"
    val format = exoPlayer.audioFormat ?: return "N/A"
    val channels = format.channelCount
    return if (channels != Format.NO_VALUE && channels > 0) channels.toString() else "N/A"
}

/**
 * Extract video and audio bitrate from ExoPlayer in format "video_bitrateMbps/audio_bitrateMbps"
 */
@UnstableApi
private fun getVideoAudioBitrate(exoPlayer: ExoPlayer?): String {
    if (exoPlayer == null) return "N/A"
    
    val videoFormat = exoPlayer.videoFormat
    val audioFormat = exoPlayer.audioFormat
    
    if (videoFormat == null && audioFormat == null) {
        return "N/A"
    }
    
    val videoBitrateBps = videoFormat?.bitrate ?: Format.NO_VALUE
    val audioBitrateBps = audioFormat?.bitrate ?: Format.NO_VALUE
    
    val videoBitrateMbps = if (videoBitrateBps != Format.NO_VALUE && videoBitrateBps > 0) {
        String.format(Locale.US, "%.2f", videoBitrateBps / 1_000_000.0)
    } else {
        null
    }
    
    val audioBitrateMbps = if (audioBitrateBps != Format.NO_VALUE && audioBitrateBps > 0) {
        String.format(Locale.US, "%.2f", audioBitrateBps / 1_000_000.0)
    } else {
        null
    }
    
    return when {
        videoBitrateMbps != null && audioBitrateMbps != null -> "${videoBitrateMbps}Mbps/${audioBitrateMbps}Mbps"
        videoBitrateMbps != null -> "${videoBitrateMbps}Mbps/N/A"
        audioBitrateMbps != null -> "N/A/${audioBitrateMbps}Mbps"
        else -> "N/A"
    }
}

/**
 * Format measured bitrate from bandwidth estimate
 */
private fun formatMeasuredBitrate(bitrateEstimate: Long): String {
    return if (bitrateEstimate > 0) {
        String.format(Locale.US, "%.2f Mbps", bitrateEstimate / 1_000_000.0)
    } else {
        "Measuring..."
    }
}

/**
 * Check if hardware acceleration is enabled based on decoder name
 */
private fun isHardwareAccelerated(decoderName: String): String {
    if (decoderName == "N/A" || decoderName.isEmpty() || decoderName == DECODER_INITIALIZING) {
        return DECODER_INITIALIZING
    }
    
    return when {
        // Hardware decoders (real devices)
        decoderName.startsWith("OMX.") -> "true (HW)"
        decoderName.startsWith("c2.exynos") -> "true (HW Samsung)"  // Samsung Exynos
        decoderName.startsWith("c2.qti") -> "true (HW Qualcomm)"     // Qualcomm
        decoderName.startsWith("c2.mtk") -> "true (HW MediaTek)"     // MediaTek
        decoderName.startsWith("c2.google") -> "true (HW Google)"  // Google Tensor
        
        // Software-Decoder
        decoderName.contains("c2.android.") -> HARDWARE_ACCELERATED_SOFTWARE // Software
        
        // Emulator
        decoderName.contains("goldfish") -> "Emulator (SW)"
        
        // Additional software decoders
        decoderName.contains("ffmpeg") -> HARDWARE_ACCELERATED_SOFTWARE
        decoderName.contains("software") -> HARDWARE_ACCELERATED_SOFTWARE
        else -> "unknown"
    }
}

/**
 * Extract transfer function from video format
 * Parses from ColorInfo.toString() since reflection fails with Media3 structure changes
 */
@UnstableApi
private fun getTransferFunction(exoPlayer: ExoPlayer?): String {
    if (exoPlayer == null) return "N/A"
    
    val videoFormat = exoPlayer.videoFormat ?: return "N/A"
    val colorInfo = videoFormat.colorInfo ?: return "N/A"
    
    // Parse directly from toString() - format: "BT709, Limited range, SDR SMPTE 170M"
    val colorInfoStr = colorInfo.toString()
    
    if (BuildConfig.DEBUG) {
        Log.d("DebugOverlay", "ColorInfo parsed: $colorInfoStr")
    }
    
    return when {
        colorInfoStr.contains("SDR") -> "SDR"
        colorInfoStr.contains("ST2084") || colorInfoStr.contains("PQ") -> "HDR10 (PQ)"
        colorInfoStr.contains("HLG") -> "HLG (HDR)"
        colorInfoStr.contains("Linear") -> "Linear"
        else -> {
            // Fallback: try reflection if toString parsing fails
            try {
                val transferField = colorInfo.javaClass.getDeclaredField("transfer")
                transferField.isAccessible = true
                val transfer = transferField.getInt(colorInfo)
                when (transfer) {
                    C.COLOR_TRANSFER_SDR -> "SDR"
                    C.COLOR_TRANSFER_ST2084 -> "HDR10 (PQ)"
                    C.COLOR_TRANSFER_HLG -> "HLG (HDR)"
                    else -> "Unknown"
                }
            } catch (e: Exception) {
                reportControlFlow("DebugOverlay.getTransferFunction", e)
                "Unknown"
            }
        }
    }
}

/**
 * Extract color space from video format
 */
@UnstableApi
private fun getColorSpace(exoPlayer: ExoPlayer?): String {
    if (exoPlayer == null) return "N/A"
    
    val videoFormat = exoPlayer.videoFormat ?: return "N/A"
    val colorInfo = videoFormat.colorInfo ?: return "N/A"
    
    // Access colorSpace using reflection or direct property access
    val colorSpace = try {
        val colorSpaceField = colorInfo.javaClass.getDeclaredField("colorSpace")
        colorSpaceField.isAccessible = true
        colorSpaceField.getInt(colorInfo)
    } catch (e: Exception) {
        reportControlFlow("DebugOverlay.getColorSpace", e)
        try {
            val colorSpaceMethod = colorInfo.javaClass.getMethod("getColorSpace")
            colorSpaceMethod.invoke(colorInfo) as? Int ?: Format.NO_VALUE
        } catch (e2: Exception) {
            reportControlFlow("DebugOverlay.getColorSpace", e2)
            Format.NO_VALUE
        }
    }
    
    if (colorSpace == Format.NO_VALUE) {
        return "N/A"
    }
    
    return try {
        when (colorSpace) {
            C.COLOR_SPACE_BT709 -> "BT.709"
            C.COLOR_SPACE_BT601 -> "BT.601"
            C.COLOR_SPACE_BT2020 -> "BT.2020"
            else -> "Unknown($colorSpace)"
        }
    } catch (e: Exception) {
        reportControlFlow("DebugOverlay.getColorSpace", e)
        // Fallback to numeric values if constants don't exist
        when (colorSpace) {
            1 -> "BT.709"
            2 -> "BT.601"
            6 -> "BT.2020"
            else -> "Unknown($colorSpace)"
        }
    }
}

/**
 * Extract dropped and rendered frames from video decoder counters
 */
@UnstableApi
private fun getDroppedRenderedFrames(exoPlayer: ExoPlayer?): String {
    if (exoPlayer == null) return "N/A"
    
    return try {
        val videoDecoderCounters = exoPlayer.videoDecoderCounters
        if (videoDecoderCounters == null) {
            return "N/A"
        }
        
        // Access fields using reflection
        val droppedFrames = try {
            val droppedField = videoDecoderCounters.javaClass.getDeclaredField("droppedFrameCount")
            droppedField.isAccessible = true
            droppedField.getInt(videoDecoderCounters)
        } catch (e: Exception) {
            reportControlFlow("DebugOverlay.getDroppedRenderedFrames", e)
            try {
                val droppedMethod = videoDecoderCounters.javaClass.getMethod("getDroppedFrameCount")
                droppedMethod.invoke(videoDecoderCounters) as? Int ?: 0
            } catch (e2: Exception) {
                reportControlFlow("DebugOverlay.getDroppedRenderedFrames", e2)
                0
            }
        }
        
        val renderedFrames = try {
            val renderedField = videoDecoderCounters.javaClass.getDeclaredField("renderedFrameCount")
            renderedField.isAccessible = true
            renderedField.getInt(videoDecoderCounters)
        } catch (e: Exception) {
            reportControlFlow("DebugOverlay.getDroppedRenderedFrames", e)
            try {
                val renderedMethod = videoDecoderCounters.javaClass.getMethod("getRenderedFrameCount")
                renderedMethod.invoke(videoDecoderCounters) as? Int ?: 0
            } catch (e2: Exception) {
                reportControlFlow("DebugOverlay.getDroppedRenderedFrames", e2)
                0
            }
        }
        
        "$droppedFrames/$renderedFrames"
    } catch (e: Exception) {
        reportControlFlow("DebugOverlay.getDroppedRenderedFrames", e)
        "N/A"
    }
}

/**
 * Calculate buffer size in seconds
 */
private fun getBufferSizeSeconds(bufferedPosition: Long): String {
    if (bufferedPosition <= 0) return "0"
    
    val seconds = bufferedPosition / 1000
    return seconds.toString()
}

/**
 * Check if player is paused
 */
private fun isPlayerPaused(isPlaying: Boolean): String {
    return (!isPlaying).toString()
}

/**
 * Get playback state as readable string
 */
private fun getPlaybackState(playerState: Int): String {
    return getPlayerStateString(playerState).lowercase()
}

/**
 * Get display DPI from context
 */
private fun getDisplayDPI(context: android.content.Context): String {
    val displayMetrics = context.resources.displayMetrics
    return displayMetrics.densityDpi.toString()
}

/**
 * Get UI resolution (actual display resolution with refresh rate)
 */
private fun getUIResolution(context: android.content.Context): String {
    return try {
        val displayMetrics = context.resources.displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels
        
        // Get refresh rate - use context.display for API 30+, fallback to deprecated API
        val refreshRate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display.mode?.refreshRate ?: 0f
        } else {
            @Suppress("DEPRECATION")
            val windowManager = context.getSystemService(android.content.Context.WINDOW_SERVICE) as? WindowManager
            @Suppress("DEPRECATION")
            val display = windowManager?.defaultDisplay
            display?.mode?.refreshRate ?: 0f
        }
        
        if (width > 0 && height > 0) {
            if (refreshRate > 0f) {
                "$width x $height@$refreshRate"
            } else {
                "$width x $height"
            }
        } else {
            "N/A"
        }
    } catch (e: Exception) {
        reportControlFlow("DebugOverlay.getUIResolution", e)
        "N/A"
    }
}

/**
 * Get display mode ID
 */
private fun getDisplayModeID(context: android.content.Context): String {
    return try {
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display.mode
        } else {
            @Suppress("DEPRECATION")
            val windowManager = context.getSystemService(android.content.Context.WINDOW_SERVICE) as? WindowManager
            @Suppress("DEPRECATION")
            windowManager?.defaultDisplay?.mode
        }
        
        val modeId = mode?.modeId ?: return "N/A"
        modeId.toString()
    } catch (e: Exception) {
        reportControlFlow("DebugOverlay.getDisplayModeID", e)
        "N/A"
    }
}

/**
 * Get display modes length
 */
private fun getDisplayModesLength(context: android.content.Context): String {
    return try {
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display
        } else {
            @Suppress("DEPRECATION")
            val windowManager = context.getSystemService(android.content.Context.WINDOW_SERVICE) as? WindowManager
            @Suppress("DEPRECATION")
            windowManager?.defaultDisplay
        }
        
        val supportedModes = display?.supportedModes ?: return "N/A"
        supportedModes.size.toString()
    } catch (e: Exception) {
        reportControlFlow("DebugOverlay.getDisplayModesLength", e)
        "N/A"
    }
}

/**
 * Get ExoPlayer version
 */
private fun getExoPlayerVersion(): String {
    return try {
        // Try to get version from library info class
        val libraryInfoClass = Class.forName("androidx.media3.common.util.LibraryInfo")
        val versionField = libraryInfoClass.getDeclaredField("VERSION")
        versionField.isAccessible = true
        versionField[null] as? String ?: "N/A"
    } catch (e: ClassNotFoundException) {
        reportControlFlow("DebugOverlay.getExoPlayerVersion", e)
        // Fallback: use Media3 version from gradle
        "1.3.1"
    } catch (e: Exception) {
        reportControlFlow("DebugOverlay.getExoPlayerVersion", e)
        "N/A"
    }
}

/**
 * Get ExoPlayer engine
 */
private fun getExoPlayerEngine(): String {
    return "Default"
}

/**
 * Get Cronet version (if used)
 */
private fun getCronetVersion(): String {
    return try {
        // Check if Cronet is available
        Class.forName("org.chromium.net.CronetEngine")
        // If class exists, try to get version
        // Cronet version is typically not directly accessible
        "Available"
    } catch (e: ClassNotFoundException) {
        reportControlFlow("DebugOverlay.getCronetVersion", e)
        "N/A"
    } catch (e: Exception) {
        reportControlFlow("DebugOverlay.getCronetVersion", e)
        "N/A"
    }
}

/**
 * Get HLS support status
 */
private fun getHLSVersion(): String {
    return try {
        // Check if Media3 HLS is available
        Class.forName("androidx.media3.exoplayer.hls.HlsMediaSource")
        // If class exists, HLS is supported
        "Available"
    } catch (e: ClassNotFoundException) {
        reportControlFlow("DebugOverlay.getHLSVersion", e)
        "N/A"
    } catch (e: Exception) {
        reportControlFlow("DebugOverlay.getHLSVersion", e)
        "N/A"
    }
}

/**
 * Get OkHttp version
 */
private fun getOkHttpVersion(): String {
    return try {
        // Try to get version from OkHttpClient package
        val okHttpClass = Class.forName("okhttp3.OkHttpClient")
        val packageInfo = okHttpClass.`package`
        val version = packageInfo?.implementationVersion
        
        if (!version.isNullOrEmpty()) {
            version
        } else {
            // Fallback to known version from libs.versions.toml
            "4.12.0"
        }
    } catch (e: ClassNotFoundException) {
        reportControlFlow("DebugOverlay.getOkHttpVersion", e)
        "N/A"
    } catch (e: Exception) {
        reportControlFlow("DebugOverlay.getOkHttpVersion", e)
        "N/A"
    }
}

/**
 * Get Flintr IPTV app version
 */
private fun getFlintrIPTVVersion(context: android.content.Context): String {
    return try {
        val packageManager = context.packageManager
        val packageName = context.packageName
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
        packageInfo.versionName ?: "N/A"
    } catch (e: Exception) {
        reportControlFlow("DebugOverlay.getFlintrIPTVVersion", e)
        "N/A"
    }
}

/**
 * Get device name
 */
private fun getDeviceName(): String {
    return Build.MODEL
}

/**
 * Get Android SDK version
 */
private fun getAndroidSDK(): String {
    return Build.VERSION.SDK_INT.toString()
}

/**
 * Get disk cache size from ExoPlayer
 */
@UnstableApi
private fun getDiskCacheSize(exoPlayer: ExoPlayer?): String {
    if (exoPlayer == null) return "N/A"
    
    return try {
        // Try to access cache through reflection
        val cacheField = ExoPlayer::class.java.getDeclaredField("cache")
        cacheField.isAccessible = true
        val cache = cacheField[exoPlayer]
        
        if (cache != null) {
            // Try to get cache size
            try {
                val cacheSizeMethod = cache.javaClass.getMethod("getCacheSize")
                val sizeBytes = cacheSizeMethod.invoke(cache) as? Long ?: 0L
                val sizeMB = sizeBytes / (1024 * 1024)
                "$sizeMB"
            } catch (e: Exception) {
                reportControlFlow("DebugOverlay.getDiskCacheSize", e)
                "N/A"
            }
        } else {
            "N/A"
        }
    } catch (e: Exception) {
        reportControlFlow("DebugOverlay.getDiskCacheSize", e)
        "N/A"
    }
}

/**
 * Get max heap memory in MB
 */
private fun getMaxHeapMemory(): String {
    return try {
        val maxMemory = Runtime.getRuntime().maxMemory()
        val maxMemoryMB = maxMemory / (1024 * 1024)
        "$maxMemoryMB"
    } catch (e: Exception) {
        reportControlFlow("DebugOverlay.getMaxHeapMemory", e)
        "N/A"
    }
}

/**
 * Get allocated heap memory in MB
 */
private fun getAllocatedHeapMemory(): String {
    return try {
        val totalMemory = Runtime.getRuntime().totalMemory()
        val allocatedMemoryMB = totalMemory / (1024 * 1024)
        "$allocatedMemoryMB"
    } catch (e: Exception) {
        reportControlFlow("DebugOverlay.getAllocatedHeapMemory", e)
        "N/A"
    }
}

/**
 * Get total and used RAM in MB
 */
private fun getTotalUsedRAM(context: android.content.Context): String {
    return try {
        val activityManager = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return "N/A"
        
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        val totalMemMB = memoryInfo.totalMem / (1024 * 1024)
        val availMemMB = memoryInfo.availMem / (1024 * 1024)
        val usedMemMB = totalMemMB - availMemMB
        
        "$totalMemMB/$usedMemMB"
    } catch (e: Exception) {
        reportControlFlow("DebugOverlay.getTotalUsedRAM", e)
        "N/A"
    }
}

/**
 * Get RAM used percentage
 */
private fun getRAMUsedPercent(context: android.content.Context): String {
    return try {
        val activityManager = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return "N/A"
        
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        val totalMem = memoryInfo.totalMem
        val availMem = memoryInfo.availMem
        val usedMem = totalMem - availMem
        
        if (totalMem == 0L) return "N/A"
        
        val usedPercent = (usedMem * 100.0 / totalMem).toInt()
        "${usedPercent}%"
    } catch (e: Exception) {
        reportControlFlow("DebugOverlay.getRAMUsedPercent", e)
        "N/A"
    }
}

/**
 * Get number of CPU cores
 */
private fun getCPUCores(): String {
    return try {
        val cores = Runtime.getRuntime().availableProcessors()
        cores.toString()
    } catch (e: Exception) {
        reportControlFlow("DebugOverlay.getCPUCores", e)
        "N/A"
    }
}

/**
 * Get CPU load average (1-minute load average as percentage)
 * Uses /proc/loadavg which provides load averages over 1, 5, and 15 minutes
 */
private fun getCPUUsage(): String {
    return try {
        BufferedReader(FileReader("/proc/loadavg")).use { reader ->
            val line = reader.readLine() ?: return "N/A"
            
            // Parse loadavg line: "1min 5min 15min running/total last_pid"
            val parts = line.split("\\s+".toRegex())
            if (parts.isEmpty()) return "N/A"
            
            val load1min = parts[0].toDoubleOrNull() ?: return "N/A"
            val cpuCores = Runtime.getRuntime().availableProcessors()
            
            // Convert load average to percentage (load / cores * 100)
            // Clamp to 0-100% range
            val usagePercent = ((load1min / cpuCores) * 100.0).coerceIn(0.0, 100.0).toInt()
            "${usagePercent}%"
        }
    } catch (e: Exception) {
        reportControlFlow("DebugOverlay.getCPUUsage", e)
        "N/A"
    }
}

/**
 * Get Picture-in-Picture (PiP) support status
 */
private fun getPiPSupported(context: android.content.Context): String {
    return try {
        val packageManager = context.packageManager
        val pipSupported = packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        pipSupported.toString()
    } catch (e: Exception) {
        reportControlFlow("DebugOverlay.getPiPSupported", e)
        "N/A"
    }
}
