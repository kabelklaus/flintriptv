package com.flintr.tv

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.test.core.app.ApplicationProvider
import com.flintr.tv.data.local.AudioDecoderMode
import com.flintr.tv.ui.components.player.AudioFallbackDecision
import com.flintr.tv.ui.components.player.FALLBACK_AUDIO_MIMES
import com.flintr.tv.ui.components.player.blockingMediaCodecSelector
import com.flintr.tv.ui.components.player.buildDownmixAudioSink
import com.flintr.tv.ui.components.player.clearAudioOverridesForRetry
import com.flintr.tv.ui.components.player.createStereoDownmixProcessor
import com.flintr.tv.ui.components.player.decideAudioFallback
import com.flintr.tv.ui.components.player.extensionRendererModeFor
import com.flintr.tv.ui.components.player.ffmpegInsertionIndex
import com.flintr.tv.ui.components.player.findFailedDecoderMime
import com.flintr.tv.ui.components.player.isFailureForCurrentStream
import com.flintr.tv.ui.components.player.isFfmpegAllowedMime
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AudioDecoderFallbackTest {

    private val initFailed = PlaybackException.ERROR_CODE_DECODER_INIT_FAILED

    private fun decide(
        mime: String?,
        errorCode: Int = initFailed,
        blocked: Set<String> = emptySet(),
        retried: Boolean = false
    ) = decideAudioFallback(errorCode, mime, blocked, retried)

    private fun decoderInitException(mime: String) = MediaCodecRenderer.DecoderInitializationException(
        Format.Builder().setSampleMimeType(mime).build(),
        null,
        false,
        -49999 // error code from the Crashlytics signature "Decoder init failed: [-49999]"
    )

    // --- decideAudioFallback ---

    @Test
    fun `eac3 init failure with empty blocklist blocks and retries`() {
        assertEquals(AudioFallbackDecision.BlockAndRetry(MimeTypes.AUDIO_E_AC3), decide(MimeTypes.AUDIO_E_AC3))
    }

    @Test
    fun `every fallback MIME blocks and retries`() {
        for (mime in FALLBACK_AUDIO_MIMES) {
            assertEquals(mime, AudioFallbackDecision.BlockAndRetry(mime), decide(mime))
        }
        assertTrue(FALLBACK_AUDIO_MIMES.containsAll(
            listOf(MimeTypes.AUDIO_E_AC3_JOC, MimeTypes.AUDIO_AC3, MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_TRUEHD)
        ))
    }

    @Test
    fun `video decoder failure is never blocked`() {
        assertEquals(AudioFallbackDecision.PassThrough, decide(MimeTypes.VIDEO_H265))
    }

    @Test
    fun `AAC decoder failure is not masked`() {
        assertEquals(AudioFallbackDecision.PassThrough, decide(MimeTypes.AUDIO_AAC))
    }

    @Test
    fun `already blocked MIME passes through so a failing software decoder cannot loop`() {
        assertEquals(
            AudioFallbackDecision.PassThrough,
            decide(MimeTypes.AUDIO_E_AC3, blocked = setOf(MimeTypes.AUDIO_E_AC3))
        )
    }

    @Test
    fun `second failure in the same stream passes through`() {
        assertEquals(AudioFallbackDecision.PassThrough, decide(MimeTypes.AUDIO_E_AC3, retried = true))
    }

    @Test
    fun `network error passes through`() {
        assertEquals(
            AudioFallbackDecision.PassThrough,
            decide(MimeTypes.AUDIO_E_AC3, errorCode = PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
        )
    }

    @Test
    fun `unknown failed MIME passes through`() {
        assertEquals(AudioFallbackDecision.PassThrough, decide(null))
    }

    // --- findFailedDecoderMime ---

    @Test
    fun `findFailedDecoderMime finds decoder exception two levels deep`() {
        val chain = RuntimeException(IllegalStateException(decoderInitException(MimeTypes.AUDIO_E_AC3)))
        assertEquals(MimeTypes.AUDIO_E_AC3, findFailedDecoderMime(chain))
    }

    @Test
    fun `findFailedDecoderMime returns null for unrelated chain`() {
        assertNull(findFailedDecoderMime(RuntimeException(IOException("timeout"))))
        assertNull(findFailedDecoderMime(null))
    }

    // --- blockingMediaCodecSelector ---

    @Test
    fun `selector hides blocked MIME and delegates others, reading the set on every call`() {
        val aacDecoders = listOf(mockk<MediaCodecInfo>())
        val delegate = mockk<MediaCodecSelector>()
        every { delegate.getDecoderInfos(any(), any(), any()) } returns aacDecoders
        val blocked = mutableSetOf(MimeTypes.AUDIO_E_AC3)
        val selector = blockingMediaCodecSelector({ blocked }, delegate)

        assertTrue(selector.getDecoderInfos(MimeTypes.AUDIO_E_AC3, false, false).isEmpty())
        verify(exactly = 0) { delegate.getDecoderInfos(MimeTypes.AUDIO_E_AC3, any(), any()) }

        assertSame(aacDecoders, selector.getDecoderInfos(MimeTypes.AUDIO_AAC, false, false))
        verify(exactly = 1) { delegate.getDecoderInfos(MimeTypes.AUDIO_AAC, false, false) }

        blocked.add(MimeTypes.AUDIO_AAC)
        assertTrue(selector.getDecoderInfos(MimeTypes.AUDIO_AAC, false, false).isEmpty())
    }

    // --- extensionRendererModeFor ---

    @Test
    fun `decoder mode maps to extension renderer mode`() {
        assertEquals(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON, extensionRendererModeFor(AudioDecoderMode.AUTO))
        assertEquals(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER, extensionRendererModeFor(AudioDecoderMode.SOFTWARE))
        assertEquals(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF, extensionRendererModeFor(AudioDecoderMode.HARDWARE))
    }

    // --- Audio sink (bug A: float output / passthrough) ---

    @Test
    fun `downmix processor rejects float PCM, so float output must stay off`() {
        val processor = createStereoDownmixProcessor()
        assertThrows(AudioProcessor.UnhandledAudioFormatException::class.java) {
            processor.configure(AudioFormat(48000, 6, C.ENCODING_PCM_FLOAT))
        }
    }

    @Test
    fun `sink transcodes float PCM to int, so the downmix pipeline runs`() {
        val sink = buildDownmixAudioSink(ApplicationProvider.getApplicationContext(), false, false)
        val float6ch = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setPcmEncoding(C.ENCODING_PCM_FLOAT)
            .setChannelCount(6)
            .setSampleRate(48000)
            .build()
        assertEquals(AudioSink.SINK_FORMAT_SUPPORTED_WITH_TRANSCODING, sink.getFormatSupport(float6ch))
    }

    @Test
    fun `sink without passthrough does not accept encoded AC3`() {
        val sink = buildDownmixAudioSink(ApplicationProvider.getApplicationContext(), false, false)
        val ac3 = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_AC3)
            .setChannelCount(6)
            .setSampleRate(48000)
            .build()
        assertFalse(sink.supportsFormat(ac3))
    }

    @Test
    fun `sink with passthrough enabled also transcodes float PCM to int, so the downmix pipeline still runs`() {
        // Mirrors the passthroughEnabled = false test above: Robolectric reports no real
        // HDMI/AVR passthrough capability either way, so encoded-AC3 support can't be asserted
        // deterministically here - but the downmix/float-transcode wiring (audio processors,
        // enableFloatOutput) must be identical regardless of passthroughEnabled, since AVR
        // users without passthrough-capable gear still fall through to decoded PCM.
        val sink = buildDownmixAudioSink(ApplicationProvider.getApplicationContext(), true, false)
        val float6ch = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setPcmEncoding(C.ENCODING_PCM_FLOAT)
            .setChannelCount(6)
            .setSampleRate(48000)
            .build()
        assertEquals(AudioSink.SINK_FORMAT_SUPPORTED_WITH_TRANSCODING, sink.getFormatSupport(float6ch))
    }

    // --- Classpath smoke test (FFmpeg dependency) ---

    @Test
    fun `FFmpeg audio renderer is on the classpath`() {
        Class.forName("androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer")
    }

    // --- clearAudioOverridesForRetry (F1/F10 regression) ---

    @Test
    fun `clearAudioOverridesForRetry removes the audio override but keeps other overrides`() {
        val audioGroup = TrackGroup(Format.Builder().setSampleMimeType(MimeTypes.AUDIO_E_AC3).build())
        val textGroup = TrackGroup(Format.Builder().setSampleMimeType(MimeTypes.TEXT_VTT).build())
        val params = TrackSelectionParameters.DEFAULT.buildUpon()
            .setOverrideForType(TrackSelectionOverride(audioGroup, 0))
            .setOverrideForType(TrackSelectionOverride(textGroup, 0))
            .build()

        val cleared = clearAudioOverridesForRetry(params)

        assertTrue(cleared.overrides.values.none { it.mediaTrackGroup == audioGroup })
        assertTrue(cleared.overrides.values.any { it.mediaTrackGroup == textGroup })
    }

    // --- isFailureForCurrentStream (F11: stale listener across channel switches) ---

    @Test
    fun `failure on the current stream's media item is not stale`() {
        val mediaItem = MediaItem.Builder().setUri("http://example.com/current.m3u8").build()
        assertTrue(isFailureForCurrentStream(mediaItem, "http://example.com/current.m3u8"))
    }

    @Test
    fun `failure on a different (already zapped-away-from) media item is stale`() {
        val mediaItem = MediaItem.Builder().setUri("http://example.com/old-channel.m3u8").build()
        assertFalse(isFailureForCurrentStream(mediaItem, "http://example.com/new-channel.m3u8"))
    }

    @Test
    fun `failure with no current media item is stale`() {
        assertFalse(isFailureForCurrentStream(null, "http://example.com/current.m3u8"))
    }

    // --- isFfmpegAllowedMime / ffmpegInsertionIndex (F5: restrict in-process FFmpeg) ---

    @Test
    fun `ffmpeg is allowed only for the fallback audio MIMEs, never AAC-MP3-Opus-Flac`() {
        for (mime in FALLBACK_AUDIO_MIMES) {
            assertTrue(mime, isFfmpegAllowedMime(mime))
        }
        assertFalse(isFfmpegAllowedMime(MimeTypes.AUDIO_AAC))
        assertFalse(isFfmpegAllowedMime(MimeTypes.AUDIO_MPEG))
        assertFalse(isFfmpegAllowedMime(MimeTypes.AUDIO_OPUS))
        assertFalse(isFfmpegAllowedMime(MimeTypes.AUDIO_FLAC))
        assertFalse(isFfmpegAllowedMime(null))
    }

    @Test
    fun `ffmpeg insertion index mirrors DefaultRenderersFactory placement`() {
        assertEquals(
            0,
            ffmpegInsertionIndex(baseRendererCount = 1, DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        )
        assertEquals(
            1,
            ffmpegInsertionIndex(baseRendererCount = 1, DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        )
    }

    @Test
    fun `ffmpeg insertion index clamps to 0 when the base renderer list is empty`() {
        assertEquals(
            0,
            ffmpegInsertionIndex(baseRendererCount = 0, DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        )
    }

    // --- blockingMediaCodecSelector only blocks audio (F7 hardening) ---

    @Test
    fun `selector never blocks a video MIME even if it ends up in the blocked set`() {
        val videoDecoders = listOf(mockk<MediaCodecInfo>())
        val delegate = mockk<MediaCodecSelector>()
        every { delegate.getDecoderInfos(any(), any(), any()) } returns videoDecoders
        val selector = blockingMediaCodecSelector({ setOf(MimeTypes.VIDEO_H264) }, delegate)

        assertSame(videoDecoders, selector.getDecoderInfos(MimeTypes.VIDEO_H264, false, false))
    }
}
