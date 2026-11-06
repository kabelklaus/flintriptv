package com.flintr.tv

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import com.flintr.tv.ui.components.player.DOWNMIX_INPUT_CHANNEL_COUNTS
import com.flintr.tv.ui.components.player.createStereoDownmixProcessor
import com.flintr.tv.ui.components.player.stereoDownmixCoefficients
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class StereoDownmixTest {

    @Test
    fun `6 channel coefficients are row-major FL FR FC LFE BL BR to L R`() {
        val expected = floatArrayOf(
            1f, 0f,         // FL
            0f, 1f,         // FR
            0.707f, 0.707f, // FC
            0f, 0f,         // LFE
            0.707f, 0f,     // BL
            0f, 0.707f      // BR
        )
        assertArrayEquals(expected, stereoDownmixCoefficients(6), 0f)
    }

    @Test
    fun `7ch Back-Center mixes symmetrically`() {
        val coefficients = stereoDownmixCoefficients(7)
        assertEquals(0.707f, coefficients[12], 0.001f)
        assertEquals(0.707f, coefficients[13], 0.001f)
    }

    @Test
    fun `coefficient array length matches input times two and is non-negative`() {
        for (n in DOWNMIX_INPUT_CHANNEL_COUNTS) {
            val coefficients = stereoDownmixCoefficients(n)
            assertEquals(n * 2, coefficients.size)
            assertTrue(coefficients.all { it >= 0f })
        }
    }

    @Test
    fun `stereo input is passed through unchanged`() {
        val processor = createStereoDownmixProcessor()
        val output = processor.configure(AudioFormat(48000, 2, C.ENCODING_PCM_16BIT))
        assertEquals(AudioFormat.NOT_SET, output)
    }

    @Test
    fun `mono input is passed through unchanged`() {
        val processor = createStereoDownmixProcessor()
        val output = processor.configure(AudioFormat(48000, 1, C.ENCODING_PCM_16BIT))
        assertEquals(AudioFormat.NOT_SET, output)
    }

    @Test
    fun `6 channel input is downmixed to stereo at same sample rate`() {
        val processor = createStereoDownmixProcessor()
        val output = processor.configure(AudioFormat(48000, 6, C.ENCODING_PCM_16BIT))
        assertEquals(AudioFormat(48000, 2, C.ENCODING_PCM_16BIT), output)
    }

    @Test
    fun `every channel count from 1 to 8 configures without throwing`() {
        for (n in 1..8) {
            val processor = createStereoDownmixProcessor()
            val output = processor.configure(AudioFormat(44100, n, C.ENCODING_PCM_16BIT))
            if (n > 2) assertEquals(2, output.channelCount)
        }
    }
}
