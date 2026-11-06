package com.flintr.tv.ui.screens

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Before
import org.junit.Test

/**
 * Verhaltenstests für [PlayerSurfaceLifecycleObserver]: Standby (ON_STOP) muss die Video-
 * Surface sauber freigeben, Wake (ON_START) sie wieder anhängen und Wiedergabe nur dann
 * fortsetzen, wenn vor dem Standby tatsächlich gespielt wurde (kein erfundenes Auto-Play).
 */
class PlayerSurfaceLifecycleObserverTest {

    private lateinit var player: Player
    private lateinit var playerView: PlayerView
    private lateinit var owner: LifecycleOwner
    private lateinit var observer: PlayerSurfaceLifecycleObserver

    @Before
    fun setUp() {
        player = mockk(relaxed = true)
        playerView = mockk(relaxed = true)
        owner = mockk(relaxed = true)
        observer = PlayerSurfaceLifecycleObserver(
            player = { player },
            playerView = { playerView }
        )
    }

    @Test
    fun `ON_STOP waehrend Wiedergabe pausiert und gibt Surface frei`() {
        every { player.isPlaying } returns true

        observer.onStateChanged(owner, Lifecycle.Event.ON_STOP)

        verify { player.pause() }
        verify { playerView.player = null }
    }

    @Test
    fun `ON_STOP waehrend Pause gibt Surface trotzdem frei`() {
        every { player.isPlaying } returns false

        observer.onStateChanged(owner, Lifecycle.Event.ON_STOP)

        verify { player.pause() }
        verify { playerView.player = null }
    }

    @Test
    fun `ON_START nach Standby waehrend Wiedergabe haengt Surface an und spielt weiter`() {
        every { player.isPlaying } returns true
        observer.onStateChanged(owner, Lifecycle.Event.ON_STOP)

        observer.onStateChanged(owner, Lifecycle.Event.ON_START)

        verifyOrder {
            playerView.player = player
            player.play()
        }
    }

    @Test
    fun `ON_START nach Standby waehrend Pause haengt Surface an ohne weiterzuspielen`() {
        every { player.isPlaying } returns false
        observer.onStateChanged(owner, Lifecycle.Event.ON_STOP)

        observer.onStateChanged(owner, Lifecycle.Event.ON_START)

        verify { playerView.player = player }
        verify(exactly = 0) { player.play() }
    }

    @Test
    fun `ON_START ohne vorheriges ON_STOP erfindet kein Auto-Play`() {
        observer.onStateChanged(owner, Lifecycle.Event.ON_START)

        verify { playerView.player = player }
        verify(exactly = 0) { player.play() }
    }

    @Test
    fun `zwei Standby-Wake-Zyklen mit unterschiedlichem Zustand leaken nicht`() {
        // Zyklus 1: spielte, muss nach Wake weiterspielen
        every { player.isPlaying } returns true
        observer.onStateChanged(owner, Lifecycle.Event.ON_STOP)
        observer.onStateChanged(owner, Lifecycle.Event.ON_START)
        verify(exactly = 1) { player.play() }

        // Zyklus 2: pausiert, darf nach Wake NICHT automatisch weiterspielen
        every { player.isPlaying } returns false
        observer.onStateChanged(owner, Lifecycle.Event.ON_STOP)
        observer.onStateChanged(owner, Lifecycle.Event.ON_START)
        verify(exactly = 1) { player.play() } // weiterhin nur 1x insgesamt
    }

    @Test
    fun `andere Lifecycle-Events werden ignoriert`() {
        observer.onStateChanged(owner, Lifecycle.Event.ON_PAUSE)
        observer.onStateChanged(owner, Lifecycle.Event.ON_RESUME)
        observer.onStateChanged(owner, Lifecycle.Event.ON_CREATE)
        observer.onStateChanged(owner, Lifecycle.Event.ON_DESTROY)

        verify(exactly = 0) { player.pause() }
        verify(exactly = 0) { player.play() }
        verify(exactly = 0) { playerView.player = null }
        verify(exactly = 0) { playerView.player = player }
    }

    @Test
    fun `fehlender Player wird ohne Absturz ignoriert`() {
        val observerWithoutPlayer = PlayerSurfaceLifecycleObserver(
            player = { null },
            playerView = { playerView }
        )

        observerWithoutPlayer.onStateChanged(owner, Lifecycle.Event.ON_STOP)
        observerWithoutPlayer.onStateChanged(owner, Lifecycle.Event.ON_START)

        verify(exactly = 0) { playerView.player = null }
        verify(exactly = 0) { playerView.player = any<Player>() }
    }
}
