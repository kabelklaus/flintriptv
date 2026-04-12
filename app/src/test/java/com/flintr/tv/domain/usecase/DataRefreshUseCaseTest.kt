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
        assertTrue(DataRefreshUseCase.isConnectivityFallbackEligible(UnknownHostException()))
        assertTrue(DataRefreshUseCase.isConnectivityFallbackEligible(ConnectException("Connection refused")))
        assertTrue(DataRefreshUseCase.isConnectivityFallbackEligible(SocketTimeoutException("timeout")))
        assertTrue(DataRefreshUseCase.isConnectivityFallbackEligible(EOFException("unexpected end of stream")))
    }

    @Test
    fun `wrapped connectivity exceptions are eligible for cache fallback`() {
        assertTrue(
            DataRefreshUseCase.isConnectivityFallbackEligible(
                RuntimeException("wrapped", UnknownHostException())
            )
        )
    }

    @Test
    fun `http and api errors are not eligible for cache fallback`() {
        assertFalse(DataRefreshUseCase.isConnectivityFallbackEligible(Exception("Failed to get live streams: 401")))
        assertFalse(DataRefreshUseCase.isConnectivityFallbackEligible(Exception("Failed to get VOD streams: 403")))
        assertFalse(DataRefreshUseCase.isConnectivityFallbackEligible(null))
    }
}
