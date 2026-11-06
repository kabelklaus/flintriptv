package com.flintr.tv.ui.components.player

import androidx.media3.common.C
import androidx.media3.common.TrackSelectionParameters
import com.flintr.tv.data.model.SubtitleLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Order 2: Untertitel real verdrahten - Untertitel-Toggle und Sprach-Präferenz.
 */
class SubtitleTrackTest {

    @Test
    fun `Untertitel-Toggle deaktiviert Text-Tracks wenn enabled false`() {
        val params = TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT

        val result = applySubtitlesEnabledParams(params, enabled = false)

        assertTrue(result.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT))
    }

    @Test
    fun `Untertitel-Toggle aktiviert Text-Tracks wenn enabled true`() {
        val disabled = TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()

        val result = applySubtitlesEnabledParams(disabled, enabled = true)

        assertFalse(result.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT))
    }

    @Test
    fun `Sprach-Praeferenz waehlt passende Spur bei Uebereinstimmung`() {
        val tracks = listOf(
            SubtitleTrackInfo(groupIndex = 0, trackIndex = 0, language = "en", displayLabel = "English"),
            SubtitleTrackInfo(groupIndex = 1, trackIndex = 0, language = "de", displayLabel = "Deutsch")
        )

        val picked = pickSubtitleTrackByLanguage(tracks, SubtitleLanguage.Deutsch)

        assertEquals(1, picked?.groupIndex)
    }

    @Test
    fun `Sprach-Praeferenz ist case-insensitive und toleriert 3-Buchstaben-Codes`() {
        val tracks = listOf(
            SubtitleTrackInfo(groupIndex = 0, trackIndex = 0, language = "GER", displayLabel = "German")
        )

        val picked = pickSubtitleTrackByLanguage(tracks, SubtitleLanguage.Deutsch)

        assertEquals(0, picked?.groupIndex)
    }

    @Test
    fun `Sprach-Praeferenz toleriert deu-Code fuer Deutsch`() {
        val tracks = listOf(
            SubtitleTrackInfo(groupIndex = 0, trackIndex = 0, language = "deu", displayLabel = "German")
        )

        val picked = pickSubtitleTrackByLanguage(tracks, SubtitleLanguage.Deutsch)

        assertEquals(0, picked?.groupIndex)
    }

    @Test
    fun `Sprach-Praeferenz faellt auf ersten Track zurueck wenn keine Sprache passt`() {
        val tracks = listOf(
            SubtitleTrackInfo(groupIndex = 0, trackIndex = 0, language = "fr", displayLabel = "Français")
        )

        val picked = pickSubtitleTrackByLanguage(tracks, SubtitleLanguage.Deutsch)

        assertEquals(0, picked?.groupIndex)
    }

    @Test
    fun `Sprach-Praeferenz gibt null zurueck wenn keine Tracks vorhanden`() {
        val picked = pickSubtitleTrackByLanguage(emptyList(), SubtitleLanguage.Deutsch)

        assertNull(picked)
    }
}
