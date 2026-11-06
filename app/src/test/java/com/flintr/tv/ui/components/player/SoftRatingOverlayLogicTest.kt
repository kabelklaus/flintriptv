package com.flintr.tv.ui.components.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure decision logic for Task F2's soft rating overlay: visible in the last 60s of a
 * VOD or a series' last episode, unless already rated this session, not yet playing
 * (remaining unknown), or the content isn't ratable (live).
 */
class SoftRatingOverlayLogicTest {

    @Test
    fun `sichtbar bei VOD mit weniger als 60s verbleibend`() {
        assertTrue(
            shouldShowSoftRatingOverlay(
                contentType = "movie",
                remainingMillis = 59_000L,
                hasNextEpisode = false,
                hasRatedInSession = false
            )
        )
    }

    @Test
    fun `nicht sichtbar bei mehr als 60s verbleibend`() {
        assertFalse(
            shouldShowSoftRatingOverlay(
                contentType = "movie",
                remainingMillis = 61_000L,
                hasNextEpisode = false,
                hasRatedInSession = false
            )
        )
    }

    @Test
    fun `sichtbar exakt bei 60s verbleibend`() {
        assertTrue(
            shouldShowSoftRatingOverlay(
                contentType = "movie",
                remainingMillis = 60_000L,
                hasNextEpisode = false,
                hasRatedInSession = false
            )
        )
    }

    @Test
    fun `nicht sichtbar wenn remaining 0 (Stream bereits zu Ende)`() {
        assertFalse(
            shouldShowSoftRatingOverlay(
                contentType = "movie",
                remainingMillis = 0L,
                hasNextEpisode = false,
                hasRatedInSession = false
            )
        )
    }

    @Test
    fun `serie mit naechster Folge zeigt Overlay nicht (Auto-Next uebernimmt)`() {
        assertFalse(
            shouldShowSoftRatingOverlay(
                contentType = "series",
                remainingMillis = 30_000L,
                hasNextEpisode = true,
                hasRatedInSession = false
            )
        )
    }

    @Test
    fun `serie ohne naechste Folge zeigt Overlay`() {
        assertTrue(
            shouldShowSoftRatingOverlay(
                contentType = "series",
                remainingMillis = 30_000L,
                hasNextEpisode = false,
                hasRatedInSession = false
            )
        )
    }

    @Test
    fun `live zeigt niemals Overlay`() {
        assertFalse(
            shouldShowSoftRatingOverlay(
                contentType = "live",
                remainingMillis = 30_000L,
                hasNextEpisode = false,
                hasRatedInSession = false
            )
        )
    }

    @Test
    fun `verschwindet nach Rating in dieser Session`() {
        assertFalse(
            shouldShowSoftRatingOverlay(
                contentType = "movie",
                remainingMillis = 30_000L,
                hasNextEpisode = false,
                hasRatedInSession = true
            )
        )
    }
}
