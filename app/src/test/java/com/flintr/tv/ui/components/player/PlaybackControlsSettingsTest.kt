package com.flintr.tv.ui.components.player

import androidx.media3.ui.AspectRatioFrameLayout
import com.flintr.tv.data.model.AudioLanguage
import com.flintr.tv.data.model.VideoQuality
import com.flintr.tv.data.model.VideoResizeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Order 3: Audio-Sprache, Seitenverhältnis, Max-Videoauflösung - pure mapping/selection logic.
 */
class PlaybackControlsSettingsTest {

    @Test
    fun `ResizeMode-Mapping FIT hat keine erzwungene Aspect Ratio`() {
        val (resizeMode, aspectRatio) = mapResizeMode(VideoResizeMode.FIT)

        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FIT, resizeMode)
        assertNull(aspectRatio)
    }

    @Test
    fun `ResizeMode-Mapping FILL hat keine erzwungene Aspect Ratio`() {
        val (resizeMode, aspectRatio) = mapResizeMode(VideoResizeMode.FILL)

        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FILL, resizeMode)
        assertNull(aspectRatio)
    }

    @Test
    fun `ResizeMode-Mapping ZOOM hat keine erzwungene Aspect Ratio`() {
        val (resizeMode, aspectRatio) = mapResizeMode(VideoResizeMode.ZOOM)

        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, resizeMode)
        assertNull(aspectRatio)
    }

    @Test
    fun `ResizeMode-Mapping 16-9 erzwingt Aspect Ratio auf FIT-Basis`() {
        val (resizeMode, aspectRatio) = mapResizeMode(VideoResizeMode.ASPECT_16_9)

        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FIT, resizeMode)
        assertEquals(16f / 9f, aspectRatio!!, 0.0001f)
    }

    @Test
    fun `ResizeMode-Mapping 4-3 erzwingt Aspect Ratio auf FIT-Basis`() {
        val (resizeMode, aspectRatio) = mapResizeMode(VideoResizeMode.ASPECT_4_3)

        assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FIT, resizeMode)
        assertEquals(4f / 3f, aspectRatio!!, 0.0001f)
    }

    @Test
    fun `Max-Videoauflösung-Mapping Auto hat keine Grenze`() {
        assertNull(mapVideoQualityToMaxSize(VideoQuality.Auto))
    }

    @Test
    fun `Max-Videoauflösung-Mapping 1080p`() {
        assertEquals(1920 to 1080, mapVideoQualityToMaxSize(VideoQuality.Quality1080p))
    }

    @Test
    fun `Max-Videoauflösung-Mapping 720p`() {
        assertEquals(1280 to 720, mapVideoQualityToMaxSize(VideoQuality.Quality720p))
    }

    @Test
    fun `Max-Videoauflösung-Mapping 480p`() {
        assertEquals(854 to 480, mapVideoQualityToMaxSize(VideoQuality.Quality480p))
    }

    @Test
    fun `Max-Videoauflösung-Mapping 360p`() {
        assertEquals(640 to 360, mapVideoQualityToMaxSize(VideoQuality.Quality360p))
    }

    @Test
    fun `Audio-Sprach-Vorauswahl waehlt passenden Track`() {
        val tracks = listOf(
            AudioTrackInfo(groupIndex = 0, trackIndex = 0, codecs = null, channelCount = 2, sampleRate = 48000, language = "en", displayLabel = "English"),
            AudioTrackInfo(groupIndex = 1, trackIndex = 0, codecs = null, channelCount = 2, sampleRate = 48000, language = "de", displayLabel = "Deutsch")
        )

        val picked = pickAudioTrackByLanguage(tracks, AudioLanguage.Deutsch)

        assertEquals(1, picked?.groupIndex)
    }

    @Test
    fun `Audio-Sprach-Vorauswahl toleriert 3-Buchstaben-Codes`() {
        val tracks = listOf(
            AudioTrackInfo(groupIndex = 0, trackIndex = 0, codecs = null, channelCount = 2, sampleRate = 48000, language = "deu", displayLabel = "German")
        )

        val picked = pickAudioTrackByLanguage(tracks, AudioLanguage.Deutsch)

        assertEquals(0, picked?.groupIndex)
    }

    @Test
    fun `Audio-Sprach-Vorauswahl gibt null zurueck wenn keine Sprache passt`() {
        val tracks = listOf(
            AudioTrackInfo(groupIndex = 0, trackIndex = 0, codecs = null, channelCount = 2, sampleRate = 48000, language = "fr", displayLabel = "Français")
        )

        val picked = pickAudioTrackByLanguage(tracks, AudioLanguage.Deutsch)

        assertNull(picked)
    }

    @Test
    fun `Audio-Sprach-Vorauswahl ueberspringt nicht unterstuetzte Tracks`() {
        val tracks = listOf(
            AudioTrackInfo(groupIndex = 0, trackIndex = 0, codecs = "ec-3", channelCount = 6, sampleRate = 48000, language = "de", displayLabel = "Deutsch DD+", isSupported = false),
            AudioTrackInfo(groupIndex = 1, trackIndex = 0, codecs = "mp4a", channelCount = 2, sampleRate = 48000, language = "de", displayLabel = "Deutsch AAC")
        )

        assertEquals(1, pickAudioTrackByLanguage(tracks, AudioLanguage.Deutsch)?.groupIndex)
        assertNull(pickAudioTrackByLanguage(tracks.take(1), AudioLanguage.Deutsch))
    }

    @Test
    fun `Audio-Sprach-Vorauswahl gibt null zurueck wenn keine Tracks vorhanden`() {
        assertNull(pickAudioTrackByLanguage(emptyList(), AudioLanguage.Deutsch))
    }
}
