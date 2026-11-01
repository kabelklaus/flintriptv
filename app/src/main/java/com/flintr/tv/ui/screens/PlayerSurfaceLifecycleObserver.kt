package com.flintr.tv.ui.screens

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView

/**
 * Detaches/reattaches the ExoPlayer video surface across ON_STOP/ON_START.
 *
 * On some TV boxes (observed on Amlogic/MiBox) the window surface is torn down on standby
 * without ExoPlayer reliably noticing, leaving the decoder attached to a dead surface after
 * wake: audio keeps playing but video freezes on the last frame ("decoder zombie").
 * Explicitly releasing the surface on ON_STOP and reattaching it on ON_START avoids
 * depending on that notification. Playback resumes on ON_START only if it was actually
 * playing when ON_STOP happened.
 */
class PlayerSurfaceLifecycleObserver(
    private val player: () -> Player?,
    private val playerView: () -> PlayerView?
) : LifecycleEventObserver {

    private var wasPlayingBeforeStop = false

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_STOP -> onStop()
            Lifecycle.Event.ON_START -> onStart()
            else -> Unit
        }
    }

    private fun onStop() {
        val currentPlayer = player() ?: return
        wasPlayingBeforeStop = currentPlayer.isPlaying
        currentPlayer.pause()
        playerView()?.player = null
    }

    private fun onStart() {
        val currentPlayer = player() ?: return
        playerView()?.player = currentPlayer
        if (wasPlayingBeforeStop) {
            currentPlayer.play()
        }
    }
}
