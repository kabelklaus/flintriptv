package com.flintr.tv.viewmodel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.flintr.tv.data.repository.UpdateRepository
import com.flintr.tv.di.ResourcesProvider
import com.flintr.tv.testutil.MainDispatcherRule
import com.flintr.tv.utils.DownloadProgress
import com.flintr.tv.utils.UpdateDownloadService
import com.flintr.tv.utils.UpdateInstaller
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class UpdateViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var updateRepository: UpdateRepository
    private lateinit var downloadService: UpdateDownloadService
    private lateinit var installer: UpdateInstaller
    private lateinit var resourcesProvider: ResourcesProvider
    private lateinit var viewModel: UpdateViewModel

    @Before
    fun setUp() {
        updateRepository = mockk()
        downloadService = mockk()
        installer = mockk()
        resourcesProvider = mockk()
        every { resourcesProvider.getString(any(), *anyVararg()) } returns "error"

        viewModel = UpdateViewModel(
            updateRepository = updateRepository,
            downloadService = downloadService,
            installer = installer,
            resourcesProvider = resourcesProvider
        )
    }

    @Test
    fun `downloadUpdate ignores concurrent call`() = runTest {
        // downloadApk-Flow haengt an einem Gate fest, damit der erste
        // Download noch "laeuft", wenn der zweite Aufruf kommt.
        val gate = CompletableDeferred<Unit>()
        every { downloadService.downloadApk(any(), any()) } returns flow {
            gate.await()
            emit(DownloadProgress(100, 100, true))
        }
        every { downloadService.getDownloadedApkFile(any()) } returns null

        viewModel.downloadUpdate("https://example.com/app.apk", "9.99")
        viewModel.downloadUpdate("https://example.com/app.apk", "9.99")

        verify(exactly = 1) { downloadService.downloadApk(any(), any()) }
        gate.complete(Unit)
    }
}

/**
 * F7 auf Service-Ebene: reale UpdateDownloadService gegen MockWebServer mit
 * chunked Response (kein Content-Length-Header, contentLength() == -1).
 */
@RunWith(RobolectricTestRunner::class)
class UpdateDownloadServiceContentLengthTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val server = MockWebServer()
    private lateinit var service: UpdateDownloadService

    private val apkBytes = byteArrayOf(0x50, 0x4B, 0x03, 0x04) + ByteArray(4096) { 1 }

    @Before
    fun setUp() {
        server.start()
        service = UpdateDownloadService(OkHttpClient(), context)
    }

    @After
    fun tearDown() {
        server.shutdown()
        File(context.cacheDir, "app-v9.99.apk").delete()
    }

    @Test
    fun `downloadApk ohne Content-Length meldet niemals -1 im finalen Fortschritt`() = runBlocking {
        server.enqueue(MockResponse().setChunkedBody(Buffer().write(apkBytes), 512))

        val events = service.downloadApk(server.url("/app.apk").toString(), "9.99").toList()

        val final = events.last()
        assertTrue(final.isComplete)
        assertEquals(apkBytes.size.toLong(), final.bytesDownloaded)
        assertEquals(apkBytes.size.toLong(), final.totalBytes)
        assertTrue(events.none { it.totalBytes < 0 || it.bytesDownloaded < 0 })
    }
}
