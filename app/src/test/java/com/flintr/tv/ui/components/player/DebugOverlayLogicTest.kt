package com.flintr.tv.ui.components.player

import androidx.media3.common.Player
import com.flintr.tv.ui.utils.formatTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verhaltenstests für die reinen Datenaufbereitungs-/Formatierungs-Helfer aus
 * DebugOverlay.kt (ohne Compose-Rendering, ohne Android-Framework-Abhängigkeiten).
 */
class DebugOverlayLogicTest {

    // --- formatDecoderName ---

    @Test
    fun `formatDecoderName ersetzt unknown durch Initializing-Platzhalter`() {
        assertEquals("Initializing...", formatDecoderName("unknown"))
    }

    @Test
    fun `formatDecoderName laesst bereits gesetzten Initializing-Wert unveraendert`() {
        assertEquals("Initializing...", formatDecoderName("Initializing..."))
    }

    @Test
    fun `formatDecoderName gibt bekannten Decoder-Namen unveraendert zurueck`() {
        assertEquals("OMX.qcom.video.decoder.avc", formatDecoderName("OMX.qcom.video.decoder.avc"))
    }

    // --- isHardwareAccelerated ---

    @Test
    fun `isHardwareAccelerated erkennt Initializing- und Leer-Zustaende`() {
        assertEquals("Initializing...", isHardwareAccelerated("N/A"))
        assertEquals("Initializing...", isHardwareAccelerated(""))
        assertEquals("Initializing...", isHardwareAccelerated("Initializing..."))
    }

    @Test
    fun `isHardwareAccelerated erkennt Hersteller-spezifische Hardware-Decoder`() {
        assertEquals("true (HW)", isHardwareAccelerated("OMX.qcom.video.decoder.avc"))
        assertEquals("true (HW Samsung)", isHardwareAccelerated("c2.exynos.avc.decoder"))
        assertEquals("true (HW Qualcomm)", isHardwareAccelerated("c2.qti.avc.decoder"))
        assertEquals("true (HW MediaTek)", isHardwareAccelerated("c2.mtk.avc.decoder"))
        assertEquals("true (HW Google)", isHardwareAccelerated("c2.google.avc.decoder"))
    }

    @Test
    fun `isHardwareAccelerated erkennt Software- und Emulator-Decoder`() {
        assertEquals("false (SW)", isHardwareAccelerated("c2.android.avc.decoder"))
        assertEquals("Emulator (SW)", isHardwareAccelerated("something.goldfish.decoder"))
        assertEquals("false (SW)", isHardwareAccelerated("my.ffmpeg.decoder"))
        assertEquals("false (SW)", isHardwareAccelerated("my.software.decoder"))
    }

    @Test
    fun `isHardwareAccelerated faellt bei unbekanntem Decoder auf unknown zurueck`() {
        assertEquals("unknown", isHardwareAccelerated("totally.unknown.decoder"))
    }

    // --- formatMeasuredBitrate ---

    @Test
    fun `formatMeasuredBitrate formatiert positive Bitrate in Mbps mit zwei Nachkommastellen`() {
        assertEquals("5.00 Mbps", formatMeasuredBitrate(5_000_000L))
        assertEquals("1.50 Mbps", formatMeasuredBitrate(1_500_000L))
    }

    @Test
    fun `formatMeasuredBitrate zeigt Measuring an solange keine Schaetzung vorliegt`() {
        assertEquals("Measuring...", formatMeasuredBitrate(0L))
        assertEquals("Measuring...", formatMeasuredBitrate(-100L))
    }

    // --- getBufferSizeSeconds ---

    @Test
    fun `getBufferSizeSeconds rundet auf volle Sekunden ab`() {
        assertEquals("5", getBufferSizeSeconds(5_000L))
        assertEquals("5", getBufferSizeSeconds(5_999L))
    }

    @Test
    fun `getBufferSizeSeconds liefert 0 fuer nicht-positive Werte`() {
        assertEquals("0", getBufferSizeSeconds(0L))
        assertEquals("0", getBufferSizeSeconds(-50L))
    }

    // --- getPlayerStateString ---

    @Test
    fun `getPlayerStateString mappt alle bekannten Player-Zustaende`() {
        assertEquals("IDLE", getPlayerStateString(Player.STATE_IDLE))
        assertEquals("BUFFERING", getPlayerStateString(Player.STATE_BUFFERING))
        assertEquals("READY", getPlayerStateString(Player.STATE_READY))
        assertEquals("ENDED", getPlayerStateString(Player.STATE_ENDED))
    }

    @Test
    fun `getPlayerStateString faellt bei unbekanntem Zustand auf UNKNOWN zurueck`() {
        assertEquals("UNKNOWN", getPlayerStateString(9999))
    }

    // --- createPlayerStatusDebugInfo ---

    @Test
    fun `createPlayerStatusDebugInfo berechnet Fortschritt und Puffer-Prozentsatz korrekt`() {
        val info = createPlayerStatusDebugInfo(
            playerState = Player.STATE_READY,
            currentPosition = 30_000L,
            duration = 120_000L,
            bufferedPosition = 60_000L,
            isPlaying = true
        )

        val expected = listOf(
            DebugInfo("State", "READY"),
            DebugInfo("Player paused", "false"),
            DebugInfo("Playback state", "ready"),
            DebugInfo("Position", formatTime(30_000L)),
            DebugInfo("Duration", formatTime(120_000L)),
            DebugInfo("Buffered", formatTime(60_000L)),
            DebugInfo("Progress", "25%"),
            DebugInfo("Buffer %", "50%")
        )
        assertEquals(expected, info)
    }

    @Test
    fun `createPlayerStatusDebugInfo vermeidet Division durch Null bei fehlender Dauer`() {
        val info = createPlayerStatusDebugInfo(
            playerState = Player.STATE_IDLE,
            currentPosition = 0L,
            duration = 0L,
            bufferedPosition = 0L,
            isPlaying = false
        )

        val expected = listOf(
            DebugInfo("State", "IDLE"),
            DebugInfo("Player paused", "true"),
            DebugInfo("Playback state", "idle"),
            DebugInfo("Position", formatTime(0L)),
            DebugInfo("Duration", formatTime(0L)),
            DebugInfo("Buffered", formatTime(0L)),
            DebugInfo("Progress", "0%"),
            DebugInfo("Buffer %", "0%")
        )
        assertEquals(expected, info)
    }

    // --- createVideoMediaDebugInfo ---

    @Test
    fun `createVideoMediaDebugInfo formatiert nur den Audio-Decoder-Namen, nicht den Video-Decoder-Namen`() {
        val params = VideoMediaDebugParams(
            videoResolution = "1920 x 1080@60",
            codecs = "avc(v1)/aac(a1)",
            bitrate = "5.00Mbps/0.13Mbps",
            decoderName = "unknown",
            audioDecoderName = "unknown",
            audioCodec = "aac",
            audioSampleRate = "48000Hz",
            audioChannels = "2",
            hardwareAccelerated = "true (HW)",
            transferFunction = "SDR",
            colorSpace = "BT.709",
            droppedRenderedFrames = "0/1000",
            bufferSizeSeconds = "12"
        )

        val info = createVideoMediaDebugInfo(params)

        val expected = listOf(
            DebugInfo("Video Resolution", "1920 x 1080@60"),
            DebugInfo("Video/Audio codecs", "avc(v1)/aac(a1)"),
            DebugInfo("Video/Audio bitrate", "5.00Mbps/0.13Mbps"),
            DebugInfo("Video decoder name", "unknown"),
            DebugInfo("Audio decoder name", "Initializing..."),
            DebugInfo("Audio codec", "aac"),
            DebugInfo("Audio sample rate", "48000Hz"),
            DebugInfo("Audio channels", "2"),
            DebugInfo("Hardware accelerated", "true (HW)"),
            DebugInfo("Transfer function", "SDR"),
            DebugInfo("Color space", "BT.709"),
            DebugInfo("Dropped/Rendered frames", "0/1000"),
            DebugInfo("Buffer size (seconds)", "12")
        )
        assertEquals(expected, info)
    }

    // --- createSystemHardwareDebugInfo ---

    @Test
    fun `createSystemHardwareDebugInfo mappt alle Felder auf die korrekten Labels`() {
        val params = SystemHardwareDebugParams(
            deviceName = "Pixel 8",
            androidSDK = "35",
            displayDPI = "420",
            uiResolution = "1920 x 1080@60.0",
            displayModeID = "1",
            displayModesLength = "3",
            cpuCores = "8",
            cpuUsage = "42%",
            pipSupported = "true"
        )

        val info = createSystemHardwareDebugInfo(params)

        val expected = listOf(
            DebugInfo("Device name", "Pixel 8"),
            DebugInfo("Android SDK", "35"),
            DebugInfo("Display DPI", "420"),
            DebugInfo("UI resolution", "1920 x 1080@60.0"),
            DebugInfo("Display mode ID", "1"),
            DebugInfo("Display modes length", "3"),
            DebugInfo("CPU Cores", "8"),
            DebugInfo("CPU use", "42%"),
            DebugInfo("PiP supported", "true")
        )
        assertEquals(expected, info)
    }

    // --- createMemoryDebugInfo ---

    @Test
    fun `createMemoryDebugInfo mappt alle Felder auf die korrekten Labels`() {
        val info = createMemoryDebugInfo(
            maxHeapMemory = "512",
            allocatedHeapMemory = "256",
            totalUsedRAM = "4096/2048",
            ramUsedPercent = "50%",
            diskCacheSize = "128"
        )

        val expected = listOf(
            DebugInfo("Max heap memory (MB)", "512"),
            DebugInfo("Allocated heap memory (MB)", "256"),
            DebugInfo("Total/used RAM", "4096/2048"),
            DebugInfo("RAM used %", "50%"),
            DebugInfo("Disk cache size (MB)", "128")
        )
        assertEquals(expected, info)
    }

    // --- createAppVersionDebugInfo ---

    @Test
    fun `createAppVersionDebugInfo mappt alle Felder auf die korrekten Labels`() {
        val info = createAppVersionDebugInfo(
            flintrIPTVVersion = "2.1.0",
            exoPlayerVersion = "1.3.1",
            exoPlayerEngine = "Default",
            hlsVersion = "Available",
            okHttpVersion = "4.12.0",
            cronetVersion = "N/A"
        )

        val expected = listOf(
            DebugInfo("Flintr iptv version", "2.1.0"),
            DebugInfo("ExoPlayer version", "1.3.1"),
            DebugInfo("ExoPlayer engine", "Default"),
            DebugInfo("HLS support", "Available"),
            DebugInfo("OkHttp version", "4.12.0"),
            DebugInfo("Cronet Version", "N/A")
        )
        assertEquals(expected, info)
    }

    // --- getExoPlayerEngine / Runtime-basierte Systemwerte ---

    @Test
    fun `getExoPlayerEngine liefert immer Default`() {
        assertEquals("Default", getExoPlayerEngine())
    }

    @Test
    fun `getMaxHeapMemory liefert die JVM-Maximalspeichergrenze in MB als Zahl`() {
        val expectedMB = Runtime.getRuntime().maxMemory() / (1024 * 1024)
        assertEquals(expectedMB.toString(), getMaxHeapMemory())
    }

    @Test
    fun `getAllocatedHeapMemory liefert den aktuell allozierten Speicher in MB als Zahl`() {
        val result = getAllocatedHeapMemory()
        assertTrue(result.toLongOrNull() != null)
    }

    @Test
    fun `getCPUCores liefert die Anzahl verfuegbarer Prozessoren`() {
        assertEquals(Runtime.getRuntime().availableProcessors().toString(), getCPUCores())
    }
}
