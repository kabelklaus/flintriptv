package com.flintr.tv.ui.components.player

import android.content.Context
import android.os.Handler
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import com.flintr.tv.data.local.AudioDecoderMode

/** Audio MIME types eligible for the hardware→software fallback. */
internal val FALLBACK_AUDIO_MIMES = setOf(
    MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC, MimeTypes.AUDIO_AC3,
    MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_DTS_HD, MimeTypes.AUDIO_TRUEHD
)

/**
 * Float output stays off as a defensive measure: media3 1.3.1 only puts custom audio
 * processors into the pipeline for int PCM output (DefaultAudioSink.configure), so float
 * output would bypass the stereo downmix. This is not the fix for the live field failure
 * (AC3/E-AC3 passthrough over HDMI, see [buildDownmixAudioSink]) - it only guards against a
 * separate, unconfirmed float-output code path.
 */
internal const val AUDIO_SINK_FLOAT_OUTPUT = false

/** Outcome of a playback error with respect to the audio decoder fallback. */
internal sealed interface AudioFallbackDecision {
    data class BlockAndRetry(val mimeType: String) : AudioFallbackDecision
    data object PassThrough : AudioFallbackDecision
}

/**
 * Pure decision: block [failedMime] for hardware decoding and retry once, unless it is
 * not an audio fallback candidate, already blocked, or this stream already retried.
 *
 * In [AudioDecoderMode.HARDWARE], the fallback never fires: there is no software decoder to
 * fall back to, so blocking the MIME and retrying would just fail the same way again. The
 * normal error dialog takes over instead, without persisting a hardware blocklist entry.
 */
internal fun decideAudioFallback(
    errorCode: Int,
    failedMime: String?,
    blockedMimes: Set<String>,
    alreadyRetriedThisStream: Boolean,
    decoderMode: AudioDecoderMode
): AudioFallbackDecision = when {
    decoderMode == AudioDecoderMode.HARDWARE -> AudioFallbackDecision.PassThrough
    errorCode != PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> AudioFallbackDecision.PassThrough
    failedMime == null || failedMime !in FALLBACK_AUDIO_MIMES -> AudioFallbackDecision.PassThrough
    failedMime in blockedMimes || alreadyRetriedThisStream -> AudioFallbackDecision.PassThrough
    else -> AudioFallbackDecision.BlockAndRetry(failedMime)
}

/** Cap on cause-chain walks, so a circular [Throwable.cause] chain can't loop forever. */
private const val MAX_CAUSE_CHAIN_DEPTH = 10

/**
 * Walks the cause chain for a [MediaCodecRenderer.DecoderInitializationException] and returns
 * the MIME type the failing decoder was queried for. That is the codec's own MIME when known
 * (e.g. audio/eac3 for an eac3-joc track decoded by the eac3 fallback decoder), otherwise the
 * track's sample MIME type. (depth-limited against circular cause chains)
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun findFailedDecoderMime(error: Throwable?): String? {
    var current: Throwable? = error
    var depth = 0
    while (current != null && depth < MAX_CAUSE_CHAIN_DEPTH) {
        if (current is MediaCodecRenderer.DecoderInitializationException) {
            return current.codecInfo?.mimeType ?: current.mimeType
        }
        current = current.cause
        depth++
    }
    return null
}

/**
 * Clears any audio track override before a [AudioFallbackDecision.BlockAndRetry] retry. A
 * language pre-selection may have already pinned the now-blocked eac3/ac3 track as an
 * explicit override; without clearing it, the retry re-selects the same blocked track and
 * ExoPlayer surfaces an error dialog instead of falling back to another track/decoder.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun clearAudioOverridesForRetry(params: TrackSelectionParameters): TrackSelectionParameters =
    params.buildUpon().clearOverridesOfType(C.TRACK_TYPE_AUDIO).build()

/**
 * Whether a playback error on [errorMediaItem] (the player's current item when the error
 * fired) still belongs to [currentStreamUrl]. The audio fallback listener stays attached for
 * the whole ExoPlayer lifetime ([DisposableEffect][androidx.compose.runtime.DisposableEffect]
 * on the player, not on the stream), so without this guard a delayed error for a channel the
 * user already zapped away from can still block/retry against the new, unrelated stream.
 */
internal fun isFailureForCurrentStream(errorMediaItem: MediaItem?, currentStreamUrl: String): Boolean =
    errorMediaItem?.localConfiguration?.uri?.toString() == currentStreamUrl

/**
 * MediaCodecSelector that hides all decoders for MIME types currently in [blockedMimes].
 * Only ever blocks audio MIMEs ([MimeTypes.isAudio]), as defense-in-depth: [blockedMimes] is
 * meant to hold only [FALLBACK_AUDIO_MIMES], but a tampered prefs file could otherwise inject
 * a video MIME and silently disable video playback.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun blockingMediaCodecSelector(
    blockedMimes: () -> Set<String>,
    delegate: MediaCodecSelector = MediaCodecSelector.DEFAULT
): MediaCodecSelector =
    MediaCodecSelector { mime, secure, tunneling ->
        if (MimeTypes.isAudio(mime) && mime in blockedMimes()) {
            emptyList()
        } else {
            delegate.getDecoderInfos(mime, secure, tunneling)
        }
    }

/**
 * Whether the FFmpeg extension renderer can take over [mimeType]. False in HARDWARE mode
 * (extension renderers are off) and when the native library lacks the decoder.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun isSoftwareAudioDecoderAvailable(mode: AudioDecoderMode, mimeType: String): Boolean =
    mode != AudioDecoderMode.HARDWARE && FfmpegLibrary.supportsFormat(mimeType)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun extensionRendererModeFor(mode: AudioDecoderMode): Int = when (mode) {
    AudioDecoderMode.AUTO -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
    AudioDecoderMode.SOFTWARE -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
    AudioDecoderMode.HARDWARE -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
}

/**
 * Whether [mimeType] may be decoded by the bundled FFmpeg extension renderer. Restricts the
 * in-process, un-sandboxed native FFmpeg decoder (untrusted Xtream streams reach it directly)
 * to the codecs MediaCodec can't reliably handle on cheap TV boxes - never AAC/MP3/Opus/FLAC
 * etc., which already have a hardware/platform decoder and would otherwise also be routed
 * through FFmpeg in SOFTWARE mode (EXTENSION_RENDERER_MODE_PREFER tries extensions first).
 */
internal fun isFfmpegAllowedMime(mimeType: String?): Boolean = mimeType != null && mimeType in FALLBACK_AUDIO_MIMES

/**
 * [FfmpegAudioRenderer] restricted to [isFfmpegAllowedMime]. The stock renderer reports
 * support for every MIME FFmpeg was built with (aac, mp3, opus, flac, ... - see
 * [isFfmpegAllowedMime] KDoc), so without this override it would also decode those even
 * though MediaCodec already handles them. [FfmpegAudioRenderer] is `final`, so this delegates
 * `Renderer` to an instance instead of extending it, overriding only [getCapabilities] to
 * enforce the restriction - every other call passes through to [delegate] unchanged.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class RestrictedFfmpegAudioRenderer(
    eventHandler: Handler?,
    eventListener: AudioRendererEventListener?,
    audioSink: AudioSink,
    private val delegate: FfmpegAudioRenderer = FfmpegAudioRenderer(eventHandler, eventListener, audioSink)
) : Renderer by delegate {

    private val capabilities = object : RendererCapabilities {
        override fun getName(): String = delegate.name
        override fun getTrackType(): Int = delegate.trackType
        override fun supportsFormat(format: Format): Int =
            if (isFfmpegAllowedMime(format.sampleMimeType)) {
                delegate.capabilities.supportsFormat(format)
            } else {
                RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE)
            }
        override fun supportsMixedMimeTypeAdaptation(): Int = delegate.capabilities.supportsMixedMimeTypeAdaptation()
    }

    override fun getCapabilities(): RendererCapabilities = capabilities
}

/**
 * Where to insert the FFmpeg extension renderer into the audio renderer list, mirroring
 * [DefaultRenderersFactory.buildAudioRenderers]'s own placement: PREFER puts it before the
 * first MediaCodec *audio* renderer (tried first among audio renderers), ON/AUTO appends it
 * at [listSize] (tried only once MediaCodec can't handle the track). Pure so it's
 * unit-testable without instantiating a real renderer list.
 *
 * [firstAudioRendererIndex] must be read from the renderer list *before* the base class's
 * `buildAudioRenderers` call that produces [listSize]: that call only appends audio renderers,
 * so indices already in the list (including any video renderers placed earlier by
 * `createRenderers`) don't shift. If there is no MediaCodec audio renderer
 * ([firstAudioRendererIndex] is -1, e.g. decoder blocklisting removed it), FFmpeg has nothing
 * to be tried "before", so it's appended at [listSize] instead - it is then the only audio
 * renderer either way.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun decideFfmpegInsertionIndex(
    firstAudioRendererIndex: Int,
    listSize: Int,
    extensionRendererMode: Int
): Int =
    if (extensionRendererMode == DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER && firstAudioRendererIndex >= 0) {
        firstAudioRendererIndex
    } else {
        listSize
    }

/**
 * Audio sink that downmixes multichannel PCM to stereo. Encoded passthrough is only used
 * when [passthroughEnabled]; otherwise the sink ignores the HDMI capabilities, so AC3/E-AC3
 * is always decoded to PCM and runs through the downmix.
 *
 * This is the actual fix for the confirmed live failure: a MiBox reports AC3 passthrough
 * support over HDMI and ExoPlayer honors it (AudioTrack `format 0x9000000` = AUDIO_FORMAT_AC3),
 * but media3's DefaultAudioSink never runs audio processors - including the stereo downmix -
 * in offload or passthrough mode ("Audio processing is not supported in offload or passthrough
 * mode"). So as long as passthrough is selected, the downmix processor added below never
 * applies, and the device's stereo-only output fails to open the encoded AudioTrack. Forcing
 * decode (passthrough off) is what makes the downmix actually run.
 */
@Suppress("DEPRECATION")
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun buildDownmixAudioSink(
    context: Context,
    passthroughEnabled: Boolean,
    enableAudioTrackPlaybackParams: Boolean
): AudioSink {
    // Builder(context) always reads live HDMI capabilities; the context-free builder with
    // default capabilities is the only way in media3 1.3.1 to switch passthrough off.
    val builder = if (passthroughEnabled) {
        DefaultAudioSink.Builder(context)
    } else {
        DefaultAudioSink.Builder().setAudioCapabilities(AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES)
    }
    return builder
        .setEnableFloatOutput(AUDIO_SINK_FLOAT_OUTPUT)
        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
        .setAudioProcessors(arrayOf(createStereoDownmixProcessor()))
        .build()
}
