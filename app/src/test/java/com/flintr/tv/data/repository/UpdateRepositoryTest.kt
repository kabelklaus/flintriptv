package com.flintr.tv.data.repository

import com.flintr.tv.BuildConfig
import com.flintr.tv.data.api.GitHubApiService
import com.flintr.tv.data.model.GitHubAsset
import com.flintr.tv.data.model.GitHubRelease
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.Response

class UpdateRepositoryTest {

    private fun apkAsset(
        name: String = "flintrtv.apk",
        url: String? = "https://api.github.com/assets/1",
        browserDownloadUrl: String = "https://github.com/download/flintrtv.apk",
        contentType: String = "application/vnd.android.package-archive"
    ) = GitHubAsset(
        name = name,
        url = url,
        browserDownloadUrl = browserDownloadUrl,
        contentType = contentType,
        size = 1024L
    )

    private fun release(
        tagName: String,
        prerelease: Boolean = false,
        body: String? = "Changelog",
        assets: List<GitHubAsset> = listOf(apkAsset())
    ) = GitHubRelease(
        tagName = tagName,
        name = "Release $tagName",
        prerelease = prerelease,
        publishedAt = "2026-07-16T10:00:00Z",
        body = body,
        assets = assets
    )

    private fun repositoryReturning(response: Response<GitHubRelease>) =
        UpdateRepository(GitHubApiService { _, _ -> response })

    @Test
    fun `neuere Version liefert Update mit Version und Changelog`() = runBlocking {
        val repository = repositoryReturning(Response.success(release(tagName = "v2.44")))

        val info = repository.checkForUpdates(currentVersion = "2.43")

        assertTrue(info.hasUpdate)
        assertEquals("2.44", info.stableVersion)
        assertEquals("Changelog", info.changelog)
        assertNull(info.betaVersion)
    }

    @Test
    fun `gleiche oder aeltere Version liefert kein Update`() = runBlocking {
        val sameVersion = repositoryReturning(Response.success(release(tagName = "v2.43")))
            .checkForUpdates(currentVersion = "2.43")
        assertFalse(sameVersion.hasUpdate)
        assertNull(sameVersion.stableVersion)
        assertNull(sameVersion.changelog)

        val olderRelease = repositoryReturning(Response.success(release(tagName = "v2.42")))
            .checkForUpdates(currentVersion = "2.43")
        assertFalse(olderRelease.hasUpdate)
    }

    @Test
    fun `Versionsvergleich behandelt unterschiedlich lange Versionen`() = runBlocking {
        val patchUpdate = repositoryReturning(Response.success(release(tagName = "2.43.1")))
            .checkForUpdates(currentVersion = "2.43")
        assertTrue(patchUpdate.hasUpdate)
        assertEquals("2.43.1", patchUpdate.stableVersion)

        val majorUpdate = repositoryReturning(Response.success(release(tagName = "v3.0")))
            .checkForUpdates(currentVersion = "2.99")
        assertTrue(majorUpdate.hasUpdate)
    }

    @Test
    fun `nicht numerische Versionsteile werden als 0 gewertet`() = runBlocking {
        val info = repositoryReturning(Response.success(release(tagName = "vX.Y")))
            .checkForUpdates(currentVersion = "0.0")

        assertFalse(info.hasUpdate)
    }

    @Test
    fun `Pre-Release wird uebersprungen`() = runBlocking {
        val info = repositoryReturning(Response.success(release(tagName = "v9.99", prerelease = true)))
            .checkForUpdates(currentVersion = "2.43")

        assertFalse(info.hasUpdate)
        assertNull(info.stableVersion)
        assertNull(info.downloadUrl)
        assertNull(info.changelog)
    }

    @Test
    fun `APK-Asset wird anhand Name und Content-Type ausgewaehlt`() = runBlocking {
        val expectedUrl = "https://example.com/expected.apk"
        val info = repositoryReturning(
            Response.success(
                release(
                    tagName = "v9.99",
                    assets = listOf(
                        apkAsset(name = "mapping.txt", contentType = "text/plain"),
                        apkAsset(name = "notes.apk", contentType = "text/plain"),
                        // url == browserDownloadUrl, damit die Erwartung unabhaengig
                        // vom GITHUB_TOKEN-BuildConfig-Wert ist
                        apkAsset(name = "FlintRTV.APK", url = expectedUrl, browserDownloadUrl = expectedUrl)
                    )
                )
            )
        ).checkForUpdates(currentVersion = "2.43")

        assertTrue(info.hasUpdate)
        assertEquals(expectedUrl, info.downloadUrl)
    }

    @Test
    fun `ohne API-URL wird die Browser-Download-URL verwendet`() = runBlocking {
        val info = repositoryReturning(
            Response.success(
                release(
                    tagName = "v9.99",
                    assets = listOf(apkAsset(url = null, browserDownloadUrl = "https://github.com/dl.apk"))
                )
            )
        ).checkForUpdates(currentVersion = "2.43")

        assertEquals("https://github.com/dl.apk", info.downloadUrl)
    }

    @Test
    fun `ohne passendes APK-Asset ist die Download-URL null`() = runBlocking {
        val info = repositoryReturning(
            Response.success(release(tagName = "v9.99", assets = emptyList()))
        ).checkForUpdates(currentVersion = "2.43")

        assertTrue(info.hasUpdate)
        assertNull(info.downloadUrl)
    }

    @Test
    fun `leerer Changelog wird als null gemeldet`() = runBlocking {
        val info = repositoryReturning(Response.success(release(tagName = "v9.99", body = "  ")))
            .checkForUpdates(currentVersion = "2.43")

        assertTrue(info.hasUpdate)
        assertNull(info.changelog)
    }

    @Test
    fun `HTTP-Fehler wirft Exception mit Statuscode`() {
        val errorBody = "not found".toResponseBody("text/plain".toMediaType())
        val repository = repositoryReturning(Response.error(404, errorBody))

        try {
            runBlocking { repository.checkForUpdates(currentVersion = "2.43") }
            fail("Expected exception for HTTP 404")
        } catch (e: Exception) {
            assertTrue(e.message!!.contains("404"))
        }
    }

    @Test
    fun `Token-Logik nutzt API-URL nur bei vorhandenem Token`() = runBlocking {
        val apiUrl = "https://api.github.com/assets/42"
        val browserUrl = "https://github.com/dl.apk"
        val info = repositoryReturning(
            Response.success(
                release(
                    tagName = "v9.99",
                    assets = listOf(apkAsset(url = apiUrl, browserDownloadUrl = browserUrl))
                )
            )
        ).checkForUpdates(currentVersion = "2.43")

        val expected = if (BuildConfig.GITHUB_TOKEN.isNotEmpty()) apiUrl else browserUrl
        assertEquals(expected, info.downloadUrl)
    }
}
