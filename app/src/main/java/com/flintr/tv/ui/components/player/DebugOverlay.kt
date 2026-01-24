package com.flintr.tv.ui.components.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.app.ActivityManager
import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager
import java.io.BufferedReader
import java.io.FileReader
import java.util.Locale
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlin.OptIn
import com.flintr.tv.ui.theme.Dimensions
import com.flintr.tv.ui.theme.FontSizes
import com.flintr.tv.ui.utils.formatTime

/**
 * Data class for debug information entry
 */
data class DebugInfo(
    val label: String,
    val value: String
)

/**
 * Debug overlay showing player information
 * @param exoPlayer ExoPlayer instance to extract video information
 * @param playerState Current ExoPlayer state
 * @param currentPosition Current playback position in milliseconds
 * @param duration Total duration in milliseconds
 * @param bufferedPosition Buffered position in milliseconds
 * @param isPlaying Whether the player is currently playing
 * @param modifier Modifier for the overlay
 */
@OptIn(UnstableApi::class)
@Composable
fun DebugOverlay(
    exoPlayer: ExoPlayer?,
    playerState: Int,
    currentPosition: Long,
    duration: Long,
    bufferedPosition: Long,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val videoResolution = getVideoResolution(exoPlayer)
    val codecs = getVideoAudioCodecs(exoPlayer)
    val bitrate = getVideoAudioBitrate(exoPlayer)
    val decoderName = getVideoDecoderName(exoPlayer)
    val hardwareAccelerated = isHardwareAccelerated(exoPlayer)
    val transferFunction = getTransferFunction(exoPlayer)
    val colorSpace = getColorSpace(exoPlayer)
    val droppedRenderedFrames = getDroppedRenderedFrames(exoPlayer)
    val bufferSizeSeconds = getBufferSizeSeconds(bufferedPosition)
    val playerPaused = isPlayerPaused(isPlaying)
    val playbackState = getPlaybackState(playerState)
    val displayDPI = getDisplayDPI(context)
    val uiResolution = getUIResolution(context)
    val bootResolution = getBootResolution(context)
    val displayModeID = getDisplayModeID(context)
    val displayModesLength = getDisplayModesLength(context)
    val exoPlayerVersion = getExoPlayerVersion()
    val exoPlayerEngine = getExoPlayerEngine()
    val cronetVersion = getCronetVersion()
    val hlsVersion = getHLSVersion()
    val okHttpVersion = getOkHttpVersion()
    val flintrIPTVVersion = getFlintrIPTVVersion(context)
    val deviceName = getDeviceName()
    val androidSDK = getAndroidSDK()
    val diskCacheSize = getDiskCacheSize(exoPlayer)
    val maxHeapMemory = getMaxHeapMemory()
    val allocatedHeapMemory = getAllocatedHeapMemory()
    val potSupported = getPotSupported(context)
    val totalUsedRAM = getTotalUsedRAM(context)
    val ramUsedPercent = getRAMUsedPercent(context)
    val cpuCores = getCPUCores()
    val cpuUsage = getCPUUsage()
    
    val debugInfo = listOf(
        // Player Status (Live-Informationen)
        DebugInfo("State", getPlayerStateString(playerState)),
        DebugInfo("Player paused", playerPaused),
        DebugInfo("Payback state", playbackState),
        DebugInfo("Position", formatTime(currentPosition)),
        DebugInfo("Duration", formatTime(duration)),
        DebugInfo("Buffered", formatTime(bufferedPosition)),
        DebugInfo("Progress", if (duration > 0) "${((currentPosition.toFloat() / duration) * 100).toInt()}%" else "0%"),
        DebugInfo("Buffer %", if (duration > 0) "${((bufferedPosition.toFloat() / duration) * 100).toInt()}%" else "0%"),
        
        // Video/Media Informationen
        DebugInfo("Video Resolution", videoResolution),
        DebugInfo("Video/Audio codecs", codecs),
        DebugInfo("Video/Audio bitrate", bitrate),
        DebugInfo("Video decoder name", decoderName),
        DebugInfo("Hardware accelerated", hardwareAccelerated),
        DebugInfo("Transfer function", transferFunction),
        DebugInfo("Color space", colorSpace),
        DebugInfo("Dropped/Rendered frames", droppedRenderedFrames),
        DebugInfo("Buffer size (seconds)", bufferSizeSeconds),
        
        // System/Hardware
        DebugInfo("Device name", deviceName),
        DebugInfo("Adroid SDK", androidSDK),
        DebugInfo("Display DPI", displayDPI),
        DebugInfo("UI resolution", uiResolution),
        DebugInfo("Boot resolution", bootResolution),
        DebugInfo("Display mode ID", displayModeID),
        DebugInfo("Display modes length", displayModesLength),
        DebugInfo("CPU Cores", cpuCores),
        DebugInfo("CPU use", cpuUsage),
        DebugInfo("Pot supported", potSupported),
        
        // Memory
        DebugInfo("Max heap memory (MB)", maxHeapMemory),
        DebugInfo("Allocated heap memory (MB)", allocatedHeapMemory),
        DebugInfo("Total/used RAM", totalUsedRAM),
        DebugInfo("RAM used %", ramUsedPercent),
        DebugInfo("Disk cache size (MB)", diskCacheSize),
        
        // App-Versionen
        DebugInfo("Flintr iptv version", flintrIPTVVersion),
        DebugInfo("ExoPlayer version", exoPlayerVersion),
        DebugInfo("ExoPlayer engine", exoPlayerEngine),
        DebugInfo("HLS support", hlsVersion),
        DebugInfo("OkHttp version", okHttpVersion),
        DebugInfo("Cronet Version", cronetVersion)
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
 * Extract video decoder name from ExoPlayer
 * Uses reflection to access the decoder name from the VideoRenderer
 */
@UnstableApi
private fun getVideoDecoderName(exoPlayer: ExoPlayer?): String {
    if (exoPlayer == null) return "N/A"
    
    return try {
        val renderers = getRenderersFromExoPlayer(exoPlayer) ?: return "N/A"
        
        for (renderer in renderers) {
            if (renderer == null) continue
            if (!isVideoRenderer(renderer)) continue
            
            val decoderName = extractDecoderNameFromRenderer(renderer)
            if (decoderName != null) {
                return decoderName
            }
        }
        
        "N/A"
    } catch (e: Exception) {
        "N/A"
    }
}

/**
 * Get renderers array from ExoPlayer using reflection
 */
@UnstableApi
private fun getRenderersFromExoPlayer(exoPlayer: ExoPlayer): Array<*>? {
    return try {
        val renderersField = ExoPlayer::class.java.getDeclaredField("renderers")
        renderersField.isAccessible = true
        renderersField.get(exoPlayer) as? Array<*>
    } catch (e: Exception) {
        null
    }
}

/**
 * Check if renderer is a video renderer
 */
private fun isVideoRenderer(renderer: Any): Boolean {
    val className = renderer.javaClass.name
    return className.contains("VideoRenderer") || className.contains("MediaCodecVideoRenderer")
}

/**
 * Extract decoder name from renderer using reflection
 */
private fun extractDecoderNameFromRenderer(renderer: Any): String? {
    val rendererClass = renderer.javaClass
    
    // Try decoderName field first
    extractDecoderNameFromDecoderNameField(renderer, rendererClass)?.let { return it }
    
    // Try codecInfo field as fallback
    extractDecoderNameFromCodecInfo(renderer, rendererClass)?.let { return it }
    
    return null
}

/**
 * Try to extract decoder name from decoderName field
 */
private fun extractDecoderNameFromDecoderNameField(renderer: Any, rendererClass: Class<*>): String? {
    return try {
        val decoderNameField = rendererClass.getDeclaredField("decoderName")
        decoderNameField.isAccessible = true
        val decoderName = decoderNameField.get(renderer) as? String
        if (!decoderName.isNullOrEmpty()) decoderName else null
    } catch (e: NoSuchFieldException) {
        null
    } catch (e: Exception) {
        null
    }
}

/**
 * Try to extract decoder name from codecInfo field
 */
private fun extractDecoderNameFromCodecInfo(renderer: Any, rendererClass: Class<*>): String? {
    return try {
        val codecInfoField = rendererClass.getDeclaredField("codecInfo")
        codecInfoField.isAccessible = true
        val codecInfo = codecInfoField.get(renderer) ?: return null
        
        val codecInfoClass = codecInfo.javaClass
        val nameField = codecInfoClass.getDeclaredField("name")
        nameField.isAccessible = true
        val name = nameField.get(codecInfo) as? String
        if (!name.isNullOrEmpty()) name else null
    } catch (e: NoSuchFieldException) {
        null
    } catch (e: Exception) {
        null
    }
}

/**
 * Check if hardware acceleration is enabled based on decoder name
 */
@UnstableApi
private fun isHardwareAccelerated(exoPlayer: ExoPlayer?): String {
    if (exoPlayer == null) return "N/A"
    
    val decoderName = getVideoDecoderName(exoPlayer)
    
    if (decoderName == "N/A" || decoderName.isEmpty()) {
        return "N/A"
    }
    
    // Hardware decoders typically start with "OMX." (OpenMAX)
    // or contain "c2." (Codec2 hardware decoder)
    // Software decoders often have patterns like "c2.android." or "ffmpeg"
    return when {
        decoderName.startsWith("OMX.") -> "true"
        decoderName.contains("c2.") && !decoderName.contains("c2.android.") -> "true"
        decoderName.contains("c2.android.") -> "false"
        decoderName.contains("ffmpeg") -> "false"
        decoderName.contains("software") -> "false"
        else -> "false" // Default to false if pattern is unclear
    }
}

/**
 * Extract transfer function from video format
 */
@UnstableApi
private fun getTransferFunction(exoPlayer: ExoPlayer?): String {
    if (exoPlayer == null) return "N/A"
    
    val videoFormat = exoPlayer.videoFormat ?: return "N/A"
    val colorInfo = videoFormat.colorInfo ?: return "N/A"
    
    // Access transfer using reflection or direct property access
    val transfer = try {
        val transferField = colorInfo.javaClass.getDeclaredField("transfer")
        transferField.isAccessible = true
        transferField.getInt(colorInfo)
    } catch (e: Exception) {
        try {
            val transferMethod = colorInfo.javaClass.getMethod("getTransfer")
            transferMethod.invoke(colorInfo) as? Int ?: Format.NO_VALUE
        } catch (e2: Exception) {
            Format.NO_VALUE
        }
    }
    
    if (transfer == Format.NO_VALUE) {
        return "N/A"
    }
    
    return try {
        // Try to use constants if available
        when (transfer) {
            C.COLOR_TRANSFER_SDR -> "Gamma"
            C.COLOR_TRANSFER_ST2084 -> "SMPTE_ST2084"
            C.COLOR_TRANSFER_HLG -> "HLG"
            C.COLOR_TRANSFER_LINEAR -> "Linear"
            C.COLOR_TRANSFER_GAMMA_2_2 -> "Gamma_2.2"
            else -> "Unknown($transfer)"
        }
    } catch (e: Exception) {
        // Fallback to numeric values if constants don't exist
        when (transfer) {
            1 -> "Gamma"
            6 -> "SMPTE_ST2084"
            7 -> "HLG"
            3 -> "Linear"
            2 -> "Gamma_2.2"
            else -> "Unknown($transfer)"
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
        try {
            val colorSpaceMethod = colorInfo.javaClass.getMethod("getColorSpace")
            colorSpaceMethod.invoke(colorInfo) as? Int ?: Format.NO_VALUE
        } catch (e2: Exception) {
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
            try {
                val droppedMethod = videoDecoderCounters.javaClass.getMethod("getDroppedFrameCount")
                droppedMethod.invoke(videoDecoderCounters) as? Int ?: 0
            } catch (e2: Exception) {
                0
            }
        }
        
        val renderedFrames = try {
            val renderedField = videoDecoderCounters.javaClass.getDeclaredField("renderedFrameCount")
            renderedField.isAccessible = true
            renderedField.getInt(videoDecoderCounters)
        } catch (e: Exception) {
            try {
                val renderedMethod = videoDecoderCounters.javaClass.getMethod("getRenderedFrameCount")
                renderedMethod.invoke(videoDecoderCounters) as? Int ?: 0
            } catch (e2: Exception) {
                0
            }
        }
        
        "$droppedFrames/$renderedFrames"
    } catch (e: Exception) {
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
        val windowManager = context.getSystemService(android.content.Context.WINDOW_SERVICE) as? WindowManager
            ?: return "N/A"
        
        val displayMetrics = context.resources.displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels
        
        // Get refresh rate
        @Suppress("DEPRECATION")
        val display = windowManager.defaultDisplay ?: return "N/A"
        val refreshRate = display.mode.refreshRate
        
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
        "N/A"
    }
}

/**
 * Get boot resolution
 */
private fun getBootResolution(context: android.content.Context): String {
    return try {
        // Boot resolution is typically the same as current resolution on Android
        // or can be retrieved from system properties
        val displayMetrics = context.resources.displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels
        
        if (width > 0 && height > 0) {
            "$width x $height"
        } else {
            "N/A"
        }
    } catch (e: Exception) {
        "N/A"
    }
}

/**
 * Get display mode ID
 */
private fun getDisplayModeID(context: android.content.Context): String {
    return try {
        val windowManager = context.getSystemService(android.content.Context.WINDOW_SERVICE) as? WindowManager
            ?: return "N/A"
        @Suppress("DEPRECATION")
        val display = windowManager.defaultDisplay ?: return "N/A"
        val mode = display.mode
        val modeId = mode.modeId
        modeId.toString()
    } catch (e: Exception) {
        "N/A"
    }
}

/**
 * Get display modes length
 */
private fun getDisplayModesLength(context: android.content.Context): String {
    return try {
        val windowManager = context.getSystemService(android.content.Context.WINDOW_SERVICE) as? WindowManager
            ?: return "N/A"
        @Suppress("DEPRECATION")
        val display = windowManager.defaultDisplay ?: return "N/A"
        val supportedModes = display.supportedModes
        supportedModes.size.toString()
    } catch (e: Exception) {
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
        versionField.get(null) as? String ?: "N/A"
    } catch (e: ClassNotFoundException) {
        // Fallback: use Media3 version from gradle
        "1.3.1"
    } catch (e: Exception) {
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
        "N/A"
    } catch (e: Exception) {
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
        "N/A"
    } catch (e: Exception) {
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
        "N/A"
    } catch (e: Exception) {
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
        val cache = cacheField.get(exoPlayer)
        
        if (cache != null) {
            // Try to get cache size
            try {
                val cacheSizeMethod = cache.javaClass.getMethod("getCacheSize")
                val sizeBytes = cacheSizeMethod.invoke(cache) as? Long ?: 0L
                val sizeMB = sizeBytes / (1024 * 1024)
                "$sizeMB"
            } catch (e: Exception) {
                "N/A"
            }
        } else {
            "N/A"
        }
    } catch (e: Exception) {
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
        "N/A"
    }
}

/**
 * Get CPU usage percentage
 */
private fun getCPUUsage(): String {
    return try {
        val reader = BufferedReader(FileReader("/proc/stat"))
        val firstLine = reader.readLine() ?: return "N/A"
        reader.close()
        
        // Parse CPU line: cpu  user nice system idle iowait irq softirq
        val parts = firstLine.split("\\s+".toRegex())
        if (parts.size < 5) return "N/A"
        
        val user = parts[1].toLongOrNull() ?: return "N/A"
        val nice = parts[2].toLongOrNull() ?: return "N/A"
        val system = parts[3].toLongOrNull() ?: return "N/A"
        val idle = parts[4].toLongOrNull() ?: return "N/A"
        val iowait = if (parts.size > 5) parts[5].toLongOrNull() ?: 0L else 0L
        
        val total = user + nice + system + idle + iowait
        if (total == 0L) return "N/A"
        
        val usage = ((total - idle) * 100.0 / total).toInt()
        "${usage}%"
    } catch (e: Exception) {
        "N/A"
    }
}

/**
 * Get Picture-in-Picture (PiP) support status
 */
private fun getPotSupported(context: android.content.Context): String {
    return try {
        val packageManager = context.packageManager
        val pipSupported = packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        pipSupported.toString()
    } catch (e: Exception) {
        "N/A"
    }
}
