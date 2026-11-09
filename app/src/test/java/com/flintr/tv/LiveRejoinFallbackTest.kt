package com.flintr.tv

import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.source.BehindLiveWindowException
import com.flintr.tv.ui.components.player.MAX_LIVE_REJOIN_ATTEMPTS
import com.flintr.tv.ui.components.player.decideLiveRejoin
import com.flintr.tv.ui.components.player.isBehindLiveWindowError
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class LiveRejoinFallbackTest {

    // --- isBehindLiveWindowError (live-resume auto-rejoin) ---

    @Test
    fun `errorCode BEHIND_LIVE_WINDOW is detected even without a matching cause`() {
        val error = PlaybackException("live window gone", null, PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW)
        assertTrue(isBehindLiveWindowError(error))
    }

    @Test
    fun `BehindLiveWindowException two levels deep in the cause chain is detected`() {
        val cause = RuntimeException(BehindLiveWindowException())
        val error = PlaybackException("source error", cause, PlaybackException.ERROR_CODE_IO_UNSPECIFIED)
        assertTrue(isBehindLiveWindowError(error))
    }

    @Test
    fun `unrelated network error is not mistaken for a behind-live-window error`() {
        val error = PlaybackException(
            "timeout",
            IOException("timeout"),
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
        )
        assertFalse(isBehindLiveWindowError(error))
    }

    // --- decideLiveRejoin (rejoin retry cap) ---

    @Test
    fun `rejoin is allowed below the attempt cap`() {
        for (attempts in 0 until MAX_LIVE_REJOIN_ATTEMPTS) {
            assertTrue("attempts=$attempts", decideLiveRejoin(isBehindLiveWindow = true, attempts = attempts))
        }
    }

    @Test
    fun `rejoin is blocked once the attempt cap is reached`() {
        assertFalse(decideLiveRejoin(isBehindLiveWindow = true, attempts = MAX_LIVE_REJOIN_ATTEMPTS))
        assertFalse(decideLiveRejoin(isBehindLiveWindow = true, attempts = MAX_LIVE_REJOIN_ATTEMPTS + 1))
    }

    @Test
    fun `rejoin is blocked when the error is not behind-live-window, regardless of attempts`() {
        assertFalse(decideLiveRejoin(isBehindLiveWindow = false, attempts = 0))
    }
}
