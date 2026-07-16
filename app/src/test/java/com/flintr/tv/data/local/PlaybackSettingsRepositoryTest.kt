package com.flintr.tv.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaybackSettingsRepositoryTest {

    private lateinit var repository: PlaybackSettingsRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        repository = PlaybackSettingsRepository(context)
    }

    @Test
    fun `Standardwerte sind Auto und Passthrough aus`() {
        assertEquals(AudioDecoderMode.AUTO, repository.getAudioDecoderMode())
        assertEquals(PreferredAudioCodec.AUTO, repository.getPreferredAudioCodec())
        assertFalse(repository.isAudioPassthroughEnabled())
    }

    @Test
    fun `AudioDecoderMode wird persistiert und wieder gelesen`() {
        repository.setAudioDecoderMode(AudioDecoderMode.SOFTWARE)
        assertEquals(AudioDecoderMode.SOFTWARE, repository.getAudioDecoderMode())

        repository.setAudioDecoderMode(AudioDecoderMode.HARDWARE)
        assertEquals(AudioDecoderMode.HARDWARE, repository.getAudioDecoderMode())
    }

    @Test
    fun `Passthrough wird persistiert und wieder gelesen`() {
        repository.setAudioPassthroughEnabled(true)
        assertTrue(repository.isAudioPassthroughEnabled())

        repository.setAudioPassthroughEnabled(false)
        assertFalse(repository.isAudioPassthroughEnabled())
    }

    @Test
    fun `PreferredAudioCodec wird persistiert und wieder gelesen`() {
        repository.setPreferredAudioCodec(PreferredAudioCodec.AAC)
        assertEquals(PreferredAudioCodec.AAC, repository.getPreferredAudioCodec())

        repository.setPreferredAudioCodec(PreferredAudioCodec.STEREO_FALLBACK)
        assertEquals(PreferredAudioCodec.STEREO_FALLBACK, repository.getPreferredAudioCodec())
    }

    @Test
    fun `fromValue faellt bei unbekannten Werten auf AUTO zurueck`() {
        assertEquals(AudioDecoderMode.AUTO, AudioDecoderMode.fromValue("unbekannt"))
        assertEquals(PreferredAudioCodec.AUTO, PreferredAudioCodec.fromValue("unbekannt"))
        assertEquals(AudioDecoderMode.SOFTWARE, AudioDecoderMode.fromValue("software"))
        assertEquals(PreferredAudioCodec.STEREO_FALLBACK, PreferredAudioCodec.fromValue("stereo_fallback"))
    }

    @Test
    fun `Enums liefern lesbare Anzeigenamen`() {
        assertEquals("Auto", AudioDecoderMode.AUTO.displayName)
        assertEquals("Software", AudioDecoderMode.SOFTWARE.displayName)
        assertEquals("Hardware", AudioDecoderMode.HARDWARE.displayName)
        assertEquals("AAC", PreferredAudioCodec.AAC.displayName)
        assertEquals("Stereo-Fallback", PreferredAudioCodec.STEREO_FALLBACK.displayName)
    }
}
