package com.flintr.tv.viewmodel

import com.flintr.tv.data.model.UpdateInfo
import com.flintr.tv.data.repository.UpdateRepository
import com.flintr.tv.di.ResourcesProvider
import com.flintr.tv.testutil.MainDispatcherRule
import com.flintr.tv.utils.DownloadProgress
import com.flintr.tv.utils.UpdateDownloadService
import com.flintr.tv.utils.UpdateInstaller
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class UpdateViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var updateRepository: UpdateRepository
    private lateinit var downloadService: UpdateDownloadService
    private lateinit var installer: UpdateInstaller
    private lateinit var viewModel: UpdateViewModel

    // Liefert lesbare Strings statt Android-Ressourcen
    private val resourcesProvider = ResourcesProvider { resId, args ->
        "res:$resId:${args.joinToString(",")}"
    }

    @Before
    fun setUp() {
        updateRepository = mockk()
        downloadService = mockk(relaxed = true)
        installer = mockk(relaxed = true)
        viewModel = UpdateViewModel(updateRepository, downloadService, installer, resourcesProvider)
    }

    @Test
    fun `checkForUpdates setzt UpdateInfo bei Erfolg`() = runTest {
        val info = UpdateInfo(hasUpdate = true, stableVersion = "2.45")
        coEvery { updateRepository.checkForUpdates("2.44") } returns info

        viewModel.checkForUpdates("2.44")

        assertEquals(info, viewModel.updateInfo.value)
        assertEquals("2.44", viewModel.currentVersion.value)
        assertFalse(viewModel.isCheckingUpdates.value)
        assertNull(viewModel.errorMessage.value)
    }

    @Test
    fun `checkForUpdates meldet Fehler bei Exception`() = runTest {
        coEvery { updateRepository.checkForUpdates(any()) } throws Exception("HTTP 500")

        viewModel.checkForUpdates("2.44")

        assertTrue(viewModel.errorMessage.value!!.contains("HTTP 500"))
        assertFalse(viewModel.isCheckingUpdates.value)
        assertNull(viewModel.updateInfo.value)
    }

    @Test
    fun `downloadUpdate aktualisiert Fortschritt und installiert nach Abschluss`() = runTest {
        val apk = File.createTempFile("update", ".apk")
        every { downloadService.downloadApk("https://dl/apk", "2.45") } returns flowOf(
            DownloadProgress(50, 100, false),
            DownloadProgress(100, 100, true)
        )
        every { downloadService.getDownloadedApkFile("2.45") } returns apk
        every { installer.installApk(apk) } returns true

        viewModel.downloadUpdate("https://dl/apk", "2.45")

        assertTrue(viewModel.downloadProgress.value!!.isComplete)
        assertNull(viewModel.errorMessage.value)
    }

    @Test
    fun `fehlgeschlagene Installation meldet Fehler`() = runTest {
        val apk = File.createTempFile("update", ".apk")
        every { downloadService.downloadApk(any(), any()) } returns flowOf(DownloadProgress(100, 100, true))
        every { downloadService.getDownloadedApkFile(any()) } returns apk
        every { installer.installApk(apk) } returns false

        viewModel.downloadUpdate("https://dl/apk", "2.45")

        assertTrue(viewModel.errorMessage.value!!.isNotBlank())
    }

    @Test
    fun `fehlende APK-Datei meldet Fehler`() = runTest {
        every { downloadService.downloadApk(any(), any()) } returns flowOf(DownloadProgress(100, 100, true))
        every { downloadService.getDownloadedApkFile(any()) } returns null

        viewModel.downloadUpdate("https://dl/apk", "2.45")

        assertTrue(viewModel.errorMessage.value!!.isNotBlank())
    }

    @Test
    fun `Download-Fehler im Fortschritt meldet Fehlermeldung`() = runTest {
        every { downloadService.downloadApk(any(), any()) } returns flowOf(
            DownloadProgress(0, 0, false, error = Exception("kein Netz"))
        )

        viewModel.downloadUpdate("https://dl/apk", "2.45")

        assertTrue(viewModel.errorMessage.value!!.contains("kein Netz"))
    }

    @Test
    fun `clearError setzt Fehlermeldung zurueck`() = runTest {
        coEvery { updateRepository.checkForUpdates(any()) } throws Exception("kaputt")
        viewModel.checkForUpdates("2.44")

        viewModel.clearError()

        assertNull(viewModel.errorMessage.value)
    }
}
