package com.flintr.tv.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

class ConnectivityErrorUtilsTest {

    @Test
    fun ioExceptionIsConnectivityError() {
        assertTrue(IOException("offline").isConnectivityError())
        assertTrue(SocketTimeoutException("timeout").isConnectivityError())
    }

    @Test
    fun wrappedIoExceptionIsConnectivityError() {
        val wrapped = RuntimeException(IllegalStateException(IOException("offline")))

        assertTrue(wrapped.isConnectivityError())
    }

    @Test
    fun nonIoExceptionIsNotConnectivityError() {
        assertFalse(RuntimeException("kaputt").isConnectivityError())
        assertFalse(IllegalArgumentException(RuntimeException()).isConnectivityError())
    }
}
