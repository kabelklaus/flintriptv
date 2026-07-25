package com.flintr.tv.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class UpdateDownloadServiceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val server = MockWebServer()
    private lateinit var service: UpdateDownloadService

    // Gueltige APK-/ZIP-Magic-Bytes plus Fuellinhalt
    private val apkBytes = byteArrayOf(0x50, 0x4B, 0x03, 0x04) + ByteArray(1024) { 1 }

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
    fun `downloadApk laedt Datei mit Fortschritt und Abschluss`() = runBlocking {
        server.enqueue(MockResponse().setBody(Buffer().write(apkBytes)))

        val events = service.downloadApk(server.url("/app.apk").toString(), "9.99").toList()

        val final = events.last()
        assertTrue(final.isComplete)
        assertNull(final.error)
        assertEquals(apkBytes.size.toLong(), final.totalBytes)
        assertEquals(1f, final.progress)
        assertTrue(events.dropLast(1).all { !it.isComplete && it.error == null })

        val file = service.getDownloadedApkFile("9.99")
        assertEquals(apkBytes.size.toLong(), file?.length())
    }

    @Test
    fun `downloadApk meldet HTTP-Fehler`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("nope"))

        val events = service.downloadApk(server.url("/app.apk").toString(), "9.99").toList()

        assertEquals(1, events.size)
        assertTrue(events.first().error!!.message!!.contains("404"))
        assertNull(service.getDownloadedApkFile("9.99"))
    }

    @Test
    fun `downloadApk verwirft Dateien ohne APK-Signatur`() = runBlocking {
        server.enqueue(MockResponse().setBody("das ist kein apk"))

        val events = service.downloadApk(server.url("/app.apk").toString(), "9.99").toList()

        val final = events.last()
        assertFalse(final.isComplete)
        assertTrue(final.error!!.message!!.contains("not a valid APK"))
        assertNull(service.getDownloadedApkFile("9.99"))
    }

    @Test
    fun `downloadApk meldet Verbindungsfehler`() = runBlocking {
        val deadUrl = server.url("/app.apk").toString()
        server.shutdown()

        val events = service.downloadApk(deadUrl, "9.99").toList()

        assertTrue(events.last().error != null)
    }

    @Test
    fun `getDownloadedApkFile liefert null ohne Datei`() {
        assertNull(service.getDownloadedApkFile("0.0"))
    }

    @Test
    fun `deleteDownloadedApk entfernt heruntergeladene Datei`() = runBlocking {
        server.enqueue(MockResponse().setBody(Buffer().write(apkBytes)))
        service.downloadApk(server.url("/app.apk").toString(), "9.99").toList()

        assertTrue(service.deleteDownloadedApk("9.99"))
        assertNull(service.getDownloadedApkFile("9.99"))
        // Erneutes Loeschen ohne Datei ist ebenfalls erfolgreich
        assertTrue(service.deleteDownloadedApk("9.99"))
    }

    @Test
    fun `DownloadProgress berechnet Prozentwert`() {
        assertEquals(0.5f, DownloadProgress(50, 100, false).progress)
        assertEquals(0f, DownloadProgress(50, 0, false).progress)
    }
}
