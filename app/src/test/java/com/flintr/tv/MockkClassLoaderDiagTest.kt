package com.flintr.tv

import io.mockk.every
import io.mockk.mockk
import com.flintr.tv.data.local.CredentialsRepository
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MockkClassLoaderDiagTest {

    @Test
    fun dumpClassLoaders() {
        val testCl = javaClass.classLoader
        println("DIAG testClass CL          = $testCl")
        println("DIAG MockKDsl CL           = ${Class.forName("io.mockk.MockKDsl").classLoader}")
        println("DIAG JvmMockKGateway CL    = ${Class.forName("io.mockk.impl.JvmMockKGateway").classLoader}")
        val dispatcherViaTestCl = Class.forName("io.mockk.proxy.jvm.dispatcher.JvmMockKDispatcher", false, testCl)
        println("DIAG Dispatcher via testCL = ${dispatcherViaTestCl.classLoader}")
        val dispatcherViaSystem = Class.forName(
            "io.mockk.proxy.jvm.dispatcher.JvmMockKDispatcher", false, ClassLoader.getSystemClassLoader()
        )
        println("DIAG Dispatcher via sysCL  = ${dispatcherViaSystem.classLoader}")
        println("DIAG same Dispatcher class = ${dispatcherViaTestCl === dispatcherViaSystem}")

        val repo = mockk<CredentialsRepository>()
        every { repo.getCredentials() } returns null
        println("DIAG CredentialsRepository CL = ${CredentialsRepository::class.java.classLoader}")
        println("DIAG mock class CL            = ${repo.javaClass.classLoader}")
    }
}
