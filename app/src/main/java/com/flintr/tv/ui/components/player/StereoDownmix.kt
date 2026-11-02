package com.flintr.tv.ui.components.player

import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix

private const val DOWNMIX_GAIN = 0.707f
private const val STEREO_CHANNEL_COUNT = 2

/** Input channel counts that get a n→2 downmix matrix. */
internal val DOWNMIX_INPUT_CHANNEL_COUNTS = 3..8

/**
 * Row-major n→2 downmix coefficients (row = input channel, columns = L, R),
 * Android channel order FL FR FC LFE BL BR SL SR.
 *
 * FL/FR pass at 1.0, centre goes to both sides at 0.707, rear/side channels go to
 * their side at 0.707. LFE is dropped. Coefficients are not normalised, so
 * simultaneous peaks on several channels can saturate the 16-bit output — accepted
 * in favour of standard downmix levels.
 */
internal fun stereoDownmixCoefficients(inputChannelCount: Int): FloatArray {
    val g = DOWNMIX_GAIN
    return when (inputChannelCount) {
        // FL FR FC
        3 -> floatArrayOf(1f, 0f, 0f, 1f, g, g)
        // FL FR BL BR
        4 -> floatArrayOf(1f, 0f, 0f, 1f, g, 0f, 0f, g)
        // FL FR FC BL BR
        5 -> floatArrayOf(1f, 0f, 0f, 1f, g, g, g, 0f, 0f, g)
        // FL FR FC LFE BL BR
        6 -> floatArrayOf(1f, 0f, 0f, 1f, g, g, 0f, 0f, g, 0f, 0f, g)
        // FL FR FC LFE BL BR SR
        7 -> floatArrayOf(1f, 0f, 0f, 1f, g, g, 0f, 0f, g, 0f, 0f, g, 0f, g)
        // FL FR FC LFE BL BR SL SR
        8 -> floatArrayOf(1f, 0f, 0f, 1f, g, g, 0f, 0f, g, 0f, 0f, g, g, 0f, 0f, g)
        else -> throw IllegalArgumentException("No stereo downmix for $inputChannelCount channels")
    }
}

/**
 * Audio processor that downmixes 3–8 channel PCM to stereo so playback works on
 * devices that cannot open a multichannel PCM AudioTrack. Mono and stereo are
 * registered as identity matrices, which leaves the processor inactive for them.
 * Only runs in the PCM decode path; passthrough/offload are unaffected.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
fun createStereoDownmixProcessor(): ChannelMixingAudioProcessor =
    ChannelMixingAudioProcessor().apply {
        putChannelMixingMatrix(ChannelMixingMatrix.create(1, 1))
        putChannelMixingMatrix(ChannelMixingMatrix.create(2, 2))
        for (n in DOWNMIX_INPUT_CHANNEL_COUNTS) {
            putChannelMixingMatrix(
                ChannelMixingMatrix(n, STEREO_CHANNEL_COUNT, stereoDownmixCoefficients(n))
            )
        }
    }
