package com.flintr.tv.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Die Rahmenfarbe ist die Stelle, an der Fokus und Selektion früher
 * vermischt wurden. Reine Funktion, deshalb ohne Robolectric testbar.
 */
class FocusRingColorTest {

    @Test
    fun noBorderWhenNotFocused() {
        assertEquals(
            Color.Transparent,
            focusRingColor(isFocused = false, onSelectionFill = false)
        )
    }

    @Test
    fun selectionAloneNeverDrawsABorder() {
        assertEquals(
            "Eine ausgewählte, aber nicht fokussierte Fläche darf keinen Rahmen tragen",
            Color.Transparent,
            focusRingColor(isFocused = false, onSelectionFill = true)
        )
    }

    @Test
    fun focusOnDarkSurfaceUsesAccent() {
        assertEquals(
            SharedColors.accent,
            focusRingColor(isFocused = true, onSelectionFill = false)
        )
    }

    @Test
    fun focusOnSelectionFillUsesWhiteForContrast() {
        assertEquals(
            "Azur auf der blauen Füllung erreicht nur 1,76:1 und verschmiert",
            Color.White,
            focusRingColor(isFocused = true, onSelectionFill = true)
        )
    }
}
