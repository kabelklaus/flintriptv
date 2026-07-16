package com.flintr.tv.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationStateTest {

    @Test
    fun `DestinationType Serialisierung ist verlustfrei`() {
        DestinationType.entries.forEach { type ->
            assertEquals(type, DestinationType.fromSerializedString(type.toSerializedString()))
        }
    }

    @Test
    fun `unbekannte Strings werden zu UNKNOWN deserialisiert`() {
        assertEquals(DestinationType.UNKNOWN, DestinationType.fromSerializedString("KAPUTT"))
        assertEquals(DestinationType.UNKNOWN, DestinationType.fromSerializedString(""))
    }

    @Test
    fun `Enum-Konstruktor speichert DestinationType typsicher`() {
        val state = NavigationStateBeforePlayer(
            destinationType = DestinationType.SERIES,
            categoryId = "42",
            contentId = "series_789_1_101",
            scrollIndex = 3,
            scrollOffset = 120
        )

        assertEquals(DestinationType.SERIES, state.destinationType)
        assertEquals("42", state.categoryId)
        assertEquals("series_789_1_101", state.contentId)
        assertEquals(3, state.scrollIndex)
        assertEquals(120, state.scrollOffset)
    }

    @Test
    fun `String-Konstruktor mit unbekanntem Typ liefert UNKNOWN`() {
        val state = NavigationStateBeforePlayer(
            destinationTypeString = "VERALTETER_WERT",
            categoryId = null,
            contentId = "live_123",
            scrollIndex = 0,
            scrollOffset = 0
        )

        assertEquals(DestinationType.UNKNOWN, state.destinationType)
    }
}
