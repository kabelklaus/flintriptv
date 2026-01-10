package com.flintr.tv.data.repository

import com.flintr.tv.BuildConfig
import com.flintr.tv.data.api.GitHubApiService
import com.flintr.tv.data.model.UpdateInfo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateRepository @Inject constructor(
    private val gitHubApiService: GitHubApiService
) {
    
    suspend fun checkForUpdates(currentVersion: String): UpdateInfo {
        val response = gitHubApiService.getLatestRelease(
            owner = BuildConfig.GITHUB_REPO_OWNER,
            repo = BuildConfig.GITHUB_REPO_NAME
        )
        
        // Read body only once to prevent OkHttp connection leak
        val release = response.body()
        if (!response.isSuccessful || release == null) {
            // Close error body if present
            response.errorBody()?.close()
            throw Exception("Failed to fetch release: HTTP ${response.code()}")
        }
        
        // Skip pre-releases
        if (release.prerelease) {
            return UpdateInfo(
                hasUpdate = false,
                stableVersion = null,
                betaVersion = null,
                downloadUrl = null
            )
        }
        
        // Extract version from tag (remove 'v' prefix if present)
        val releaseVersion = release.tagName.removePrefix("v")
        
        // Compare versions
        val hasUpdate = compareVersions(currentVersion, releaseVersion) < 0
        
        // Find APK asset
        val apkAsset = release.assets.firstOrNull { asset ->
            asset.name.endsWith(".apk", ignoreCase = true) &&
            asset.contentType == "application/vnd.android.package-archive"
        }
        
        // Use API URL for private repos (with token), browser_download_url for public repos
        val downloadUrl = apkAsset?.run {
            if (BuildConfig.GITHUB_TOKEN.isNotEmpty() && url != null) {
                // Use API URL for authenticated downloads (private repos)
                url
            } else {
                // Use browser_download_url for public repos
                browserDownloadUrl
            }
        }
        
        return UpdateInfo(
            hasUpdate = hasUpdate,
            stableVersion = if (hasUpdate) releaseVersion else null,
            betaVersion = null,
            downloadUrl = downloadUrl
        )
    }
    
    /**
     * Compares two version strings.
     * @return negative if version1 < version2, zero if equal, positive if version1 > version2
     */
    private fun compareVersions(version1: String, version2: String): Int {
        val v1Parts = version1.split(".").map { it.toIntOrNull() ?: 0 }
        val v2Parts = version2.split(".").map { it.toIntOrNull() ?: 0 }
        
        val maxLength = maxOf(v1Parts.size, v2Parts.size)
        
        for (i in 0 until maxLength) {
            val v1Part = v1Parts.getOrElse(i) { 0 }
            val v2Part = v2Parts.getOrElse(i) { 0 }
            
            when {
                v1Part < v2Part -> return -1
                v1Part > v2Part -> return 1
            }
        }
        
        return 0
    }
}
