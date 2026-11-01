package com.flintr.tv.data.repository

import com.flintr.tv.data.model.RatingEntry
import com.flintr.tv.data.model.toFirestoreMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Behavioral tests for the pure rating helpers used by saveRating()/getRating(),
 * plus static source guardrails (same pattern as UserRepositoryGuardrailsTest)
 * for the Firestore-transaction wiring that can't be unit-tested without a live
 * FirebaseFirestore instance (UserRepository has no DI seam for it).
 */
class UserRepositoryRatingTest {

    @Test
    fun buildRatingContentKeyUsesMoviePrefixForMovies() {
        assertEquals("movie_123", buildRatingContentKey("movie", 123))
    }

    @Test
    fun buildRatingContentKeyUsesSeriesPrefixForSeries() {
        assertEquals("series_456", buildRatingContentKey("series", 456))
    }

    @Test
    fun upsertRatingWritesEntryUnderContentKey() {
        val entry = RatingEntry(
            contentType = "movie",
            contentId = 123,
            title = "Some Movie",
            rating = 1,
            ratedAt = "2026-09-13T10:00:00Z"
        )

        val updated = upsertRating(emptyMap(), entry)

        assertEquals(1, updated.size)
        assertEquals(entry.toFirestoreMap(), updated["movie_123"])
    }

    @Test
    fun upsertRatingOverwritesExistingEntryForSameContentKey() {
        val original = RatingEntry(
            contentType = "movie",
            contentId = 123,
            title = "Some Movie",
            rating = 0,
            ratedAt = "2026-09-13T10:00:00Z"
        )
        val updatedEntry = RatingEntry(
            contentType = "movie",
            contentId = 123,
            title = "Some Movie",
            rating = 1,
            ratedAt = "2026-09-13T11:00:00Z"
        )

        val afterFirst = upsertRating(emptyMap(), original)
        val afterSecond = upsertRating(afterFirst, updatedEntry)

        assertEquals(1, afterSecond.size)
        assertEquals(1, (afterSecond["movie_123"]?.get("rating") as Number).toInt())
        assertEquals("2026-09-13T11:00:00Z", afterSecond["movie_123"]?.get("ratedAt"))
    }

    @Test
    fun upsertRatingKeepsOtherContentKeysUntouched() {
        val movieEntry = RatingEntry("movie", 1, "A", 1, "2026-09-13T10:00:00Z")
        val seriesEntry = RatingEntry("series", 2, "B", 0, "2026-09-13T10:05:00Z")

        val afterMovie = upsertRating(emptyMap(), movieEntry)
        val afterBoth = upsertRating(afterMovie, seriesEntry)

        assertEquals(setOf("movie_1", "series_2"), afterBoth.keys)
    }

    @Test
    fun parseRatingEntryParsesAllFieldsFromFirestoreMap() {
        val entry = RatingEntry(
            contentType = "series",
            contentId = 456,
            title = "Some Series",
            rating = 1,
            ratedAt = "2026-09-13T10:00:00Z"
        )

        val parsed = parseRatingEntry(entry.toFirestoreMap())

        assertEquals(entry, parsed)
    }

    @Test
    fun parseRatingEntryReturnsNullWhenRequiredFieldMissing() {
        val incomplete = mapOf(
            "contentType" to "movie",
            "contentId" to 123,
            "title" to "X"
            // "rating" and "ratedAt" missing
        )

        assertNull(parseRatingEntry(incomplete))
    }

    @Test
    fun parseRatingEntryAllowsNullTitle() {
        val map = mapOf(
            "contentType" to "movie",
            "contentId" to 123,
            "title" to null,
            "rating" to 1,
            "ratedAt" to "2026-09-13T10:00:00Z"
        )

        val parsed = parseRatingEntry(map)

        assertEquals(null, parsed?.title)
        assertEquals(1, parsed?.rating)
    }

    // --- Static guardrails on saveRating()/getRating() (Firestore write safety,
    // same pattern as UserRepositoryGuardrailsTest) ---

    private val userRepositorySource =
        readSource("src/main/java/com/flintr/tv/data/repository/UserRepository.kt")

    private val saveRatingBlock = userRepositorySource
        .substringAfter("suspend fun saveRating(")
        .substringBefore("suspend fun ")

    private val loadAllRatingsBlock = userRepositorySource
        .substringAfter("suspend fun loadAllRatings(")
        .substringBefore("suspend fun ")

    @Test
    fun saveRatingMustUseFirestoreTransaction() {
        assertTrue(
            "saveRating() must use a Firestore transaction to safely upsert into the ratings map.",
            saveRatingBlock.contains("runTransaction")
        )
    }

    @Test
    fun saveRatingMustUseMergeToAvoidReplacingTheDocument() {
        assertTrue(
            "saveRating() must use SetOptions.merge() so the write can never wipe other user fields.",
            saveRatingBlock.contains("SetOptions.merge()")
        )
        assertTrue(
            "saveRating() must not call set() on the ratings map without merge options.",
            saveRatingBlock.contains(".set(userDocRef, mapOf(\"ratings\" to")
        )
    }

    @Test
    fun loadAllRatingsIsReadOnly() {
        assertTrue(
            "loadAllRatings() must only read the user document, never write to it.",
            loadAllRatingsBlock.contains(".get()")
        )
        assertTrue(
            "loadAllRatings() must not use a Firestore transaction (read-only bulk load).",
            !loadAllRatingsBlock.contains("runTransaction")
        )
        assertTrue(
            "loadAllRatings() must never call set() - it only reads ratings.",
            !loadAllRatingsBlock.contains(".set(")
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
