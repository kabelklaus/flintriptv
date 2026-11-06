package com.flintr.tv.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Guardrails against the 2026-07 data-loss incident: saveUsername() performed a
 * full-document set() as fallback when the existence check failed, replacing the
 * user document and wiping watchedHistory/watchProgress/favorites.
 */
class UserRepositoryGuardrailsTest {

    private val userRepositorySource =
        readSource("src/main/java/com/flintr/tv/data/repository/UserRepository.kt")

    private val saveUsernameBlock = userRepositorySource
        .substringAfter("suspend fun saveUsername(")
        .substringBefore("suspend fun ")

    @Test
    fun saveUsernameMustNotWriteWhenExistenceCheckFails() {
        assertFalse(
            "saveUsername() must abort when the existence check fails - a blind write can wipe the user document.",
            saveUsernameBlock.contains("attempting write anyway")
        )
        assertTrue(
            "saveUsername() must return the failure of the existence check instead of writing.",
            saveUsernameBlock.contains("return Result.failure(it)")
        )
    }

    @Test
    fun saveUsernameMustUseMergeToAvoidReplacingTheDocument() {
        assertTrue(
            "saveUsername() must use SetOptions.merge() so a wrong existence check can never wipe existing fields.",
            saveUsernameBlock.contains(".set(userData, SetOptions.merge())")
        )
        assertFalse(
            "saveUsername() must not call set() without merge options.",
            saveUsernameBlock.contains(".set(userData)")
        )
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
