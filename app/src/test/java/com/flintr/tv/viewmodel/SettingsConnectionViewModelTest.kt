package com.flintr.tv.viewmodel

import com.flintr.tv.data.local.Credentials
import com.flintr.tv.data.local.CredentialsRepository
import com.flintr.tv.data.local.DataCacheService
import com.flintr.tv.data.model.ServerInfo
import com.flintr.tv.data.model.ServerInfoDetails
import com.flintr.tv.data.repository.XtreamRepository
import com.flintr.tv.domain.usecase.DataRefreshProgress
import com.flintr.tv.domain.usecase.DataRefreshUseCase
import com.flintr.tv.domain.usecase.RefreshStep
import com.flintr.tv.testutil.MainDispatcherRule
import com.squareup.moshi.Moshi
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsConnectionViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val server = MockWebServer()
    private lateinit var repository: XtreamRepository
    private lateinit var credentialsRepository: CredentialsRepository
    private lateinit var cacheService: DataCacheService
    private lateinit var dataRefreshUseCase: DataRefreshUseCase

    private val serverInfo = ServerInfo(
        serverInfo = ServerInfoDetails(
            url = "srv", port = "80", httpsPort = null, serverProtocol = "http",
            rtmpPort = null, timezone = null, timestampNow = null, timeNow = null
        ),
        userInfo = null
    )

    @Before
    fun setUp() {
        server.start()
        repository = mockk()
        credentialsRepository = mockk()
        cacheService = mockk(relaxed = true)
        dataRefreshUseCase = mockk()
        every { credentialsRepository.getCredentials() } returns
            Credentials(serverUrl = "http://srv", username = "chris", password = "pw")
        coEvery { repository.getServerInfo("chris", "pw") } coAnswers { Result.success(serverInfo) }
        every { dataRefreshUseCase.refreshData(any(), any()) } returns flowOf(
            DataRefreshProgress(step = RefreshStep.SAVING, message = "Fertig", isComplete = true)
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun viewModel() = SettingsConnectionViewModel(
        repository = repository,
        credentialsRepository = credentialsRepository,
        cacheService = cacheService,
        dataRefreshUseCase = dataRefreshUseCase,
        okHttpClient = OkHttpClient(),
        moshi = Moshi.Builder().build()
    )

    private fun awaitState(timeoutMs: Long = 5_000, predicate: (ConnectionTestState) -> Boolean) {
        val start = System.currentTimeMillis()
        lateinit var vmState: ConnectionTestState
        while (true) {
            mainDispatcherRule.dispatcher.scheduler.runCurrent()
            vmState = stateProvider()
            if (predicate(vmState)) return
            if (System.currentTimeMillis() - start > timeoutMs) {
                throw AssertionError("Zustand nicht erreicht, zuletzt: $vmState")
            }
            Thread.sleep(20)
        }
    }

    private lateinit var stateProvider: () -> ConnectionTestState

    @Test
    fun `init laedt ServerInfo bei vorhandenen Credentials`() = runTest {
        val vm = viewModel()

        assertEquals(serverInfo, vm.connectionTestState.value.serverInfo)
        assertFalse(vm.connectionTestState.value.isLoading)
    }

    @Test
    fun `init ohne Credentials macht nichts`() = runTest {
        every { credentialsRepository.getCredentials() } returns null

        val vm = viewModel()

        assertNull(vm.connectionTestState.value.serverInfo)
        assertNull(vm.connectionTestState.value.error)
    }

    @Test
    fun `reloadServerInfo ohne Credentials meldet Fehler`() = runTest {
        every { credentialsRepository.getCredentials() } returns null
        val vm = viewModel()

        vm.reloadServerInfo()

        assertEquals("Keine gespeicherten Anmeldedaten gefunden", vm.connectionTestState.value.error)
    }

    @Test
    fun `reloadServerInfo laedt neu und fuellt Cache`() = runTest {
        val vm = viewModel()

        vm.reloadServerInfo()

        assertEquals(serverInfo, vm.connectionTestState.value.serverInfo)
        assertTrue(vm.connectionTestState.value.cacheRefillSuccess)
        assertEquals("Fertig", vm.connectionTestState.value.cacheRefillMessage)
        verify { cacheService.clearFallbackCooldown() }
    }

    @Test
    fun `reloadServerInfo meldet Fehler des Repositories`() = runTest {
        coEvery { repository.getServerInfo("chris", "pw") } coAnswers { Result.failure(Exception("kaputt")) }
        val vm = viewModel()

        vm.reloadServerInfo()

        assertTrue(vm.connectionTestState.value.error!!.contains("kaputt"))
    }

    @Test
    fun `testConnection validiert leere Eingaben`() = runTest {
        val vm = viewModel()

        vm.testConnection("", "user", "pw")

        assertEquals("Bitte füllen Sie alle Felder aus", vm.connectionTestState.value.error)
    }

    @Test
    fun `testConnection erfolgreich setzt ServerInfo`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"server_info":{"url":"neu"},"user_info":null}""")
        )
        val vm = viewModel()
        stateProvider = { vm.connectionTestState.value }

        vm.testConnection(server.url("/").toString(), "user", "pw")
        awaitState { !it.isLoading && (it.serverInfo?.serverInfo?.url == "neu" || it.error != null) }

        assertEquals("neu", vm.connectionTestState.value.serverInfo?.serverInfo?.url)
        assertNull(vm.connectionTestState.value.error)
    }

    @Test
    fun `testConnection meldet HTTP-Fehler`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403).setBody("nope"))
        val vm = viewModel()
        stateProvider = { vm.connectionTestState.value }

        vm.testConnection(server.url("/").toString(), "user", "pw")
        awaitState { !it.isLoading && it.error != null }

        assertTrue(vm.connectionTestState.value.error!!.contains("403"))
    }

    @Test
    fun `clear-Methoden setzen Zustand zurueck`() = runTest {
        val vm = viewModel()
        vm.reloadServerInfo()

        vm.clearError()
        assertNull(vm.connectionTestState.value.error)

        vm.clearServerInfo()
        assertNull(vm.connectionTestState.value.serverInfo)

        vm.clearCacheRefillStatus()
        assertNull(vm.connectionTestState.value.cacheRefillMessage)
        assertFalse(vm.connectionTestState.value.cacheRefillSuccess)
    }
}
