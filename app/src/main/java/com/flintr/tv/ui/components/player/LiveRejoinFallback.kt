package com.flintr.tv.ui.components.player

import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.source.BehindLiveWindowException

/**
 * Whether [error] is a [BehindLiveWindowException] - the live window moved past the
 * playback position while paused, which media3 surfaces as [PlaybackException] with
 * [PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW] and the exception as its cause. Checked
 * both ways since the cause chain is what a live crash log actually shows.
 */
internal fun isBehindLiveWindowError(error: PlaybackException): Boolean {
    if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) return true
    var cause: Throwable? = error.cause
    while (cause != null) {
        if (cause is BehindLiveWindowException) return true
        cause = cause.cause
    }
    return false
}
