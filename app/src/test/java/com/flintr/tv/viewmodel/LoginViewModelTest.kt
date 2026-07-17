package com.flintr.tv.viewmodel

import com.flintr.tv.data.local.Credentials
import com.flintr.tv.data.local.CredentialsRepository
import com.flintr.tv.data.repository.UserRepository
import com.flintr.tv.data.repository.XtreamRepository
import com.flintr.tv.testutil.MainDispatcherRule
import com.squareup.moshi.Moshi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.OkHttpClient
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
class LoginViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val server = MockWebServer()
    private lateinit var credentialsRepository: CredentialsRepository
    private lateinit var userRepository: UserRepository
    private lateinit var viewModel: LoginViewModel

    @Before
    fun setUp() {
        server.start()
        credentialsRepository = mockk(relaxed = true)
        userRepository = mockk(relaxed = true)
        coEvery { userRepository.saveUsername(any()) } coAnswers { Result.success(Unit) }

        viewModel = LoginViewModel(
            credentialsRepository = credentialsRepository,
            repository = mockk<XtreamRepository>(relaxed = true),
            userRepository = userRepository,
            okHttpClient = OkHttpClient(),
            moshi = Moshi.Builder().build()
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    /**
     * Wartet auf den Abschluss des echten HTTP-Roundtrips: Die Resumption wird
     * auf dem Test-Scheduler eingeplant und muss dort ausgeführt werden.
     */
    private fun awaitLoginFinished(timeoutMs: Long = 5_000) {
        val start = System.currentTimeMillis()
        while (viewModel.uiState.value.isLoading || (!viewModel.uiState.value.loginSuccess && viewModel.uiState.value.error == null)) {
            if (System.currentTimeMillis() - start > timeoutMs) {
                throw AssertionError("Login-Zustand wurde nicht innerhalb von ${timeoutMs}ms final")
            }
            Thread.sleep(20)
            mainDispatcherRule.dispatcher.scheduler.runCurrent()
        }
    }

    @Test
    fun `leere Felder liefern Validierungsfehler ohne Netzwerkaufruf`() = runTest {
        viewModel.login("", "user", "pw")

        assertEquals("Bitte füllen Sie alle Felder aus", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.loginSuccess)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `erfolgreicher Login speichert Credentials und Benutzernamen`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"server_info":{"url":"srv"},"user_info":{"username":"user","auth":1}}""")
        )
        val serverUrl = server.url("/").toString().removeSuffix("/")

        viewModel.login(serverUrl, "user", "pw")
        awaitLoginFinished()

        assertTrue(viewModel.uiState.value.loginSuccess)
        assertNull(viewModel.uiState.value.error)
        verify { credentialsRepository.saveCredentials(Credentials(serverUrl, "user", "pw")) }
        coVerify { userRepository.saveUsername("user") }
    }

    @Test
    fun `HTTP-Fehler liefert Fehlermeldung mit Statuscode`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("nope"))

        viewModel.login(server.url("/").toString(), "user", "falsch")
        awaitLoginFinished()

        assertFalse(viewModel.uiState.value.loginSuccess)
        assertTrue(viewModel.uiState.value.error!!.contains("401"))
        verify(exactly = 0) { credentialsRepository.saveCredentials(any()) }
    }

    @Test
    fun `Verbindungsfehler liefert Fehlermeldung`() = runTest {
        // Server sofort schliessen -> Verbindung schlaegt fehl
        val deadUrl = server.url("/").toString()
        server.shutdown()

        viewModel.login(deadUrl, "user", "pw")
        awaitLoginFinished()

        assertFalse(viewModel.uiState.value.loginSuccess)
        assertTrue(viewModel.uiState.value.error!!.startsWith("Verbindungsfehler"))
    }

    @Test
    fun `clearError und resetLoginSuccess setzen Zustand zurueck`() = runTest {
        viewModel.login("", "", "")
        viewModel.clearError()
        assertNull(viewModel.uiState.value.error)

        viewModel.resetLoginSuccess()
        assertFalse(viewModel.uiState.value.loginSuccess)
    }
}
