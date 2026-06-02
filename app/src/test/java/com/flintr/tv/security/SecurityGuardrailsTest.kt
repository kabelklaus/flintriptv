package com.flintr.tv.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class SecurityGuardrailsTest {

    @Test
    fun normalApiClientDoesNotUseBodyLogging() {
        val appModule = readSource("src/main/java/com/flintr/tv/di/AppModule.kt")
        val normalClientBlock = appModule.substringAfter("fun provideOkHttpClient()")
            .substringBefore("fun provideXtreamRepository(")

        assertFalse(
            "The normal API client must not log request/response bodies because credentials are passed as query params.",
            normalClientBlock.contains("HttpLoggingInterceptor.Level.BODY")
        )
    }

    @Test
    fun appDoesNotAllowGlobalCleartextTraffic() {
        val manifest = readSource("src/main/AndroidManifest.xml")

        assertFalse(
            "Global cleartext traffic must not be enabled for an app that sends credentials over network requests.",
            manifest.contains("""android:usesCleartextTraffic="true"""")
        )
        assertTrue(
            "Cleartext traffic should be explicitly disabled unless a host-specific exception is added intentionally.",
            manifest.contains("""android:usesCleartextTraffic="false"""")
        )
    }

    @Test
    fun knownSensitiveLogPatternsAreNotPresent() {
        val filesToCheck = listOf(
            "src/main/java/com/flintr/tv/viewmodel/MainViewModel.kt",
            "src/main/java/com/flintr/tv/data/repository/UserRepository.kt",
            "src/main/java/com/flintr/tv/data/local/WatchProgressService.kt",
            "src/main/java/com/flintr/tv/data/local/WatchedHistoryService.kt"
        )
        val sensitivePatterns = listOf(
            "Server info loaded successfully: \$serverInfo",
            "Username existence check: \$username",
            "Starting migration from local storage to Firestore for user: \$username",
            "Saving progress: key=\$key",
            "Found progress: key=\$key",
            "cleanupCompletedProgress() - checking key=\$key",
            "Marking as watched: key=\$key",
            "Marked as watched successfully: key=\$key"
        )

        filesToCheck.forEach { file ->
            val source = readSource(file)
            sensitivePatterns.forEach { pattern ->
                assertFalse(
                    "Sensitive log pattern '$pattern' must not be present in $file.",
                    source.contains(pattern)
                )
            }
        }
    }

    private fun readSource(path: String): String {
        val sourcePath = resolveProjectPath(path)
        return String(Files.readAllBytes(sourcePath))
    }

    private fun resolveProjectPath(path: String): Path {
        val moduleRelative = Paths.get(path)
        if (Files.exists(moduleRelative)) return moduleRelative

        val rootRelative = Paths.get("app").resolve(path)
        if (Files.exists(rootRelative)) return rootRelative

        error("Could not resolve test source path: $path")
    }
}
