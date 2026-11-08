package com.flintr.tv.ui.components.player

import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.source.BehindLiveWindowException

/** Cap on cause-chain walks, so a circular [Throwable.cause] chain can't loop forever. */
private const val MAX_CAUSE_CHAIN_DEPTH = 10

/**
 * Whether [error] is a [BehindLiveWindowException] - the live window moved past the
 * playback position while paused, which media3 surfaces as [PlaybackException] with
 * [PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW] and the exception as its cause. Checked
 * both ways since the cause chain is what a live crash log actually shows. (depth-limited
 * against circular cause chains)
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun isBehindLiveWindowError(error: PlaybackException): Boolean {
    if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) return true
    var cause: Throwable? = error.cause
    var depth = 0
    while (cause != null && depth < MAX_CAUSE_CHAIN_DEPTH) {
        if (cause is BehindLiveWindowException) return true
        cause = cause.cause
        depth++
    }
    return false
}
