package com.flintr.tv.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.flintr.tv.data.model.AudioLanguage
import com.flintr.tv.data.model.SubtitleLanguage
import com.flintr.tv.data.model.VideoQuality
import com.flintr.tv.data.model.VideoResizeMode
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

    @Test
    fun `SubtitlesEnabled ist standardmaessig false`() {
        assertFalse(repository.getSubtitlesEnabled())
    }

    @Test
    fun `SubtitlesEnabled wird persistiert und wieder gelesen`() {
        repository.setSubtitlesEnabled(true)
        assertTrue(repository.getSubtitlesEnabled())

        repository.setSubtitlesEnabled(false)
        assertFalse(repository.getSubtitlesEnabled())
    }

    @Test
    fun `PreferredSubtitleLanguage ist standardmaessig Deutsch`() {
        assertEquals(SubtitleLanguage.Deutsch, repository.getPreferredSubtitleLanguage())
    }

    @Test
    fun `PreferredSubtitleLanguage wird persistiert und wieder gelesen`() {
        repository.setPreferredSubtitleLanguage(SubtitleLanguage.English)
        assertEquals(SubtitleLanguage.English, repository.getPreferredSubtitleLanguage())

        repository.setPreferredSubtitleLanguage(SubtitleLanguage.Francais)
        assertEquals(SubtitleLanguage.Francais, repository.getPreferredSubtitleLanguage())
    }

    @Test
    fun `PreferredAudioLanguage ist standardmaessig Deutsch`() {
        assertEquals(AudioLanguage.Deutsch, repository.getPreferredAudioLanguage())
    }

    @Test
    fun `PreferredAudioLanguage wird persistiert und wieder gelesen`() {
        repository.setPreferredAudioLanguage(AudioLanguage.Espanol)
        assertEquals(AudioLanguage.Espanol, repository.getPreferredAudioLanguage())

        repository.setPreferredAudioLanguage(AudioLanguage.English)
        assertEquals(AudioLanguage.English, repository.getPreferredAudioLanguage())
    }

    @Test
    fun `VideoResizeMode ist standardmaessig FIT`() {
        assertEquals(VideoResizeMode.FIT, repository.getVideoResizeMode())
    }

    @Test
    fun `VideoResizeMode wird persistiert und wieder gelesen`() {
        repository.setVideoResizeMode(VideoResizeMode.ZOOM)
        assertEquals(VideoResizeMode.ZOOM, repository.getVideoResizeMode())

        repository.setVideoResizeMode(VideoResizeMode.ASPECT_16_9)
        assertEquals(VideoResizeMode.ASPECT_16_9, repository.getVideoResizeMode())
    }

    @Test
    fun `AutoPlayNext ist standardmaessig false`() {
        assertFalse(repository.isAutoPlayNextEnabled())
    }

    @Test
    fun `AutoPlayNext wird persistiert und wieder gelesen`() {
        repository.setAutoPlayNextEnabled(true)
        assertTrue(repository.isAutoPlayNextEnabled())

        repository.setAutoPlayNextEnabled(false)
        assertFalse(repository.isAutoPlayNextEnabled())
    }

    @Test
    fun `RememberPosition ist standardmaessig true`() {
        assertTrue(repository.isRememberPositionEnabled())
    }

    @Test
    fun `RememberPosition wird persistiert und wieder gelesen`() {
        repository.setRememberPositionEnabled(false)
        assertFalse(repository.isRememberPositionEnabled())

        repository.setRememberPositionEnabled(true)
        assertTrue(repository.isRememberPositionEnabled())
    }

    @Test
    fun `VideoQuality ist standardmaessig Auto`() {
        assertEquals(VideoQuality.Auto, repository.getVideoQuality())
    }

    @Test
    fun `VideoQuality wird persistiert und wieder gelesen`() {
        repository.setVideoQuality(VideoQuality.Quality1080p)
        assertEquals(VideoQuality.Quality1080p, repository.getVideoQuality())

        repository.setVideoQuality(VideoQuality.Quality480p)
        assertEquals(VideoQuality.Quality480p, repository.getVideoQuality())
    }
}
