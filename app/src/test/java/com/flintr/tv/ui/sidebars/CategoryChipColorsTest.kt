package com.flintr.tv.ui.sidebars

import androidx.compose.ui.graphics.Color
import com.flintr.tv.ui.theme.SharedColors
import com.flintr.tv.ui.theme.appColorsForTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryChipColorsTest {

    private val colors = appColorsForTest()

    @Test
    fun selectedButNotFocusedChipHasNoBorder() {
        val result = calculateChipColors(isSelected = true, hasFocus = false, colors = colors)

        assertEquals(
            "Die ausgewählte Kategorie darf keinen Rahmen tragen, während der Fokus woanders steht",
            Color.Transparent,
            result.border
        )
    }

    @Test
    fun focusedChipHasAccentBorder() {
        val result = calculateChipColors(isSelected = false, hasFocus = true, colors = colors)

        assertEquals(SharedColors.accent, result.border)
    }

    @Test
    fun selectedAndFocusedChipHasWhiteBorderOnTheFill() {
        val result = calculateChipColors(isSelected = true, hasFocus = true, colors = colors)

        assertEquals(Color.White, result.border)
        assertEquals(SharedColors.selectionFill, result.background)
    }

    @Test
    fun neutralChipHasNoBorder() {
        val result = calculateChipColors(isSelected = false, hasFocus = false, colors = colors)

        assertEquals(Color.Transparent, result.border)
    }
}
