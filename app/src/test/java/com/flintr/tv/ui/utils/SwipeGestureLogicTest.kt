package com.flintr.tv.ui.utils

import androidx.compose.ui.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reine Berechnungs-/Entscheidungslogik der vertikalen Swipe-Erkennung
 * (calculateVerticalSwipeVelocity, isVerticalSwipe, checkSwipeVelocityAndDirection).
 * Framework-frei, daher ohne Compose-Testinfrastruktur direkt testbar.
 */
class SwipeGestureLogicTest {

    @Test
    fun `calculateVerticalSwipeVelocity berechnet px pro Sekunde`() {
        val velocity = calculateVerticalSwipeVelocity(
            startY = 0f,
            startTime = 0L,
            currentY = 100f,
            currentTime = 200L
        )

        assertEquals(500f, velocity!!, 0.01f)
    }

    @Test
    fun `calculateVerticalSwipeVelocity liefert null ohne Wegstrecke`() {
        val velocity = calculateVerticalSwipeVelocity(
            startY = 50f,
            startTime = 0L,
            currentY = 50f,
            currentTime = 200L
        )

        assertNull(velocity)
    }

    @Test
    fun `calculateVerticalSwipeVelocity liefert null bei nicht positiver Zeitspanne`() {
        val velocity = calculateVerticalSwipeVelocity(
            startY = 0f,
            startTime = 200L,
            currentY = 100f,
            currentTime = 200L
        )

        assertNull(velocity)
    }

    @Test
    fun `isVerticalSwipe erkennt gueltigen vertikalen Swipe`() {
        assertTrue(
            isVerticalSwipe(startX = 0f, startY = 0f, currentX = 5f, currentY = 60f, density = 1f)
        )
    }

    @Test
    fun `isVerticalSwipe lehnt zu kurze vertikale Distanz ab`() {
        assertFalse(
            isVerticalSwipe(startX = 0f, startY = 0f, currentX = 0f, currentY = 30f, density = 1f)
        )
    }

    @Test
    fun `isVerticalSwipe lehnt zu grosse horizontale Abweichung ab`() {
        assertFalse(
            isVerticalSwipe(startX = 0f, startY = 0f, currentX = 40f, currentY = 60f, density = 1f)
        )
    }

    @Test
    fun `checkSwipeVelocityAndDirection ruft onSwipeUp bei Swipe nach oben auf`() {
        var upCalls = 0
        var downCalls = 0

        val handled = checkSwipeVelocityAndDirection(
            startY = 100f,
            currentY = 0f,
            startTime = 0L,
            currentTime = 100L,
            onSwipeUp = { upCalls++ },
            onSwipeDown = { downCalls++ }
        )

        assertTrue(handled)
        assertEquals(1, upCalls)
        assertEquals(0, downCalls)
    }

    @Test
    fun `checkSwipeVelocityAndDirection ruft onSwipeDown bei Swipe nach unten auf`() {
        var upCalls = 0
        var downCalls = 0

        val handled = checkSwipeVelocityAndDirection(
            startY = 0f,
            currentY = 100f,
            startTime = 0L,
            currentTime = 100L,
            onSwipeUp = { upCalls++ },
            onSwipeDown = { downCalls++ }
        )

        assertTrue(handled)
        assertEquals(0, upCalls)
        assertEquals(1, downCalls)
    }

    @Test
    fun `checkSwipeVelocityAndDirection ignoriert zu langsame Bewegung`() {
        val handled = checkSwipeVelocityAndDirection(
            startY = 100f,
            currentY = 0f,
            startTime = 0L,
            currentTime = 10_000L,
            onSwipeUp = { throw AssertionError("sollte nicht aufgerufen werden") },
            onSwipeDown = null
        )

        assertFalse(handled)
    }

    @Test
    fun `checkSwipeVelocityAndDirection liefert false ohne passenden Handler`() {
        val handled = checkSwipeVelocityAndDirection(
            startY = 100f,
            currentY = 0f,
            startTime = 0L,
            currentTime = 100L,
            onSwipeUp = null,
            onSwipeDown = { throw AssertionError("sollte nicht aufgerufen werden") }
        )

        assertFalse(handled)
    }

    @Test
    fun `createVerticalSwipeGestureModifier ohne Handler gibt Modifier unveraendert zurueck`() {
        val result = Modifier.createVerticalSwipeGestureModifier()

        assertSame(Modifier, result)
    }
}
