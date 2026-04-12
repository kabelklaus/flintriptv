package com.flintr.tv.domain.usecase

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.EOFException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class DataRefreshUseCaseTest {

    @Test
    fun `connectivity exceptions are eligible for cache fallback`() {
        assertTrue(DataRefreshUseCase.isConnectivityFallbackEligible(null, UnknownHostException()))
        assertTrue(DataRefreshUseCase.isConnectivityFallbackEligible(null, ConnectException("Connection refused")))
        assertTrue(DataRefreshUseCase.isConnectivityFallbackEligible(null, SocketTimeoutException("timeout")))
        assertTrue(DataRefreshUseCase.isConnectivityFallbackEligible(null, EOFException("unexpected end of stream")))
    }

    @Test
    fun `connectivity messages are eligible for cache fallback`() {
        assertTrue(
            DataRefreshUseCase.isConnectivityFallbackEligible(
                "unexpected end of stream on http://example.com/...",
                null
            )
        )
        assertTrue(
            DataRefreshUseCase.isConnectivityFallbackEligible(
                "Failed to connect to /192.168.1.2:443",
                null
            )
        )
    }

    @Test
    fun `http and api errors are not eligible for cache fallback`() {
        assertFalse(DataRefreshUseCase.isConnectivityFallbackEligible("Failed to get live streams: 401", null))
        assertFalse(DataRefreshUseCase.isConnectivityFallbackEligible("Failed to get VOD streams: 403", null))
        assertFalse(DataRefreshUseCase.isConnectivityFallbackEligible("Keine Daten nach 4 Versuchen", null))
    }
}
