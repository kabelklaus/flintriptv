package com.flintr.tv.ui.components.player

import androidx.media3.common.MimeTypes
import com.flintr.tv.data.model.Subtitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Order 2: Sidecar-Untertitel aus dem Xtream `subtitles`-Feld als
 * [androidx.media3.common.MediaItem.SubtitleConfiguration]. Robolectric, weil
 * [buildSubtitleConfigurations] echtes `android.net.Uri.parse` aufruft.
 */
@RunWith(RobolectricTestRunner::class)
class SubtitleConfigurationTest {

    @Test
    fun `baut eine Config pro Subtitle mit korrekter Uri und Sprache`() {
        val subtitles = listOf(
            Subtitle(url = "https://example.com/subs/de.srt", lang = "de"),
            Subtitle(url = "https://example.com/subs/en.vtt", lang = "en")
        )

        val configs = buildSubtitleConfigurations(subtitles)

        assertEquals(2, configs.size)
        assertEquals("https://example.com/subs/de.srt", configs[0].uri.toString())
        assertEquals("de", configs[0].language)
        assertEquals("https://example.com/subs/en.vtt", configs[1].uri.toString())
        assertEquals("en", configs[1].language)
    }

    @Test
    fun `erkennt srt als APPLICATION_SUBRIP`() {
        val configs = buildSubtitleConfigurations(
            listOf(Subtitle(url = "https://example.com/subs/movie.srt", lang = "de"))
        )

        assertEquals(MimeTypes.APPLICATION_SUBRIP, configs.single().mimeType)
    }

    @Test
    fun `erkennt vtt als TEXT_VTT`() {
        val configs = buildSubtitleConfigurations(
            listOf(Subtitle(url = "https://example.com/subs/movie.vtt", lang = "en"))
        )

        assertEquals(MimeTypes.TEXT_VTT, configs.single().mimeType)
    }

    @Test
    fun `faellt fuer unbekannte Dateiendungen auf SUBRIP zurueck`() {
        val configs = buildSubtitleConfigurations(
            listOf(Subtitle(url = "https://example.com/subs/movie", lang = "de"))
        )

        assertEquals(MimeTypes.APPLICATION_SUBRIP, configs.single().mimeType)
    }

    @Test
    fun `ueberspringt Subtitles mit null oder leerer Url`() {
        val subtitles = listOf(
            Subtitle(url = null, lang = "de"),
            Subtitle(url = "", lang = "en"),
            Subtitle(url = "https://example.com/subs/fr.srt", lang = "fr")
        )

        val configs = buildSubtitleConfigurations(subtitles)

        assertEquals(1, configs.size)
        assertTrue(configs.single().uri.toString().endsWith("fr.srt"))
    }

    @Test
    fun `gibt leere Liste zurueck wenn keine Subtitles vorhanden`() {
        val configs = buildSubtitleConfigurations(emptyList())

        assertTrue(configs.isEmpty())
    }
}
