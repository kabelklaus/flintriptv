package com.flintr.tv.domain.recommendation

import org.junit.Assert.assertEquals
import org.junit.Test

class GenreNormalizerTest {

    @Test
    fun `splits on comma`() {
        assertEquals(listOf("action", "adventure"), GenreNormalizer.normalize("Action, Adventure"))
    }

    @Test
    fun `splits on pipe`() {
        assertEquals(listOf("drama", "horror"), GenreNormalizer.normalize("Drama|Horror"))
    }

    @Test
    fun `splits on ampersand semicolon and slash`() {
        assertEquals(
            listOf("sci-fi", "fantasy", "thriller"),
            GenreNormalizer.normalize("Sci-Fi & Fantasy; Thriller")
        )
        assertEquals(listOf("action", "drama"), GenreNormalizer.normalize("Action/Drama"))
    }

    @Test
    fun `returns empty list for null`() {
        assertEquals(emptyList<String>(), GenreNormalizer.normalize(null))
    }

    @Test
    fun `returns empty list for blank string`() {
        assertEquals(emptyList<String>(), GenreNormalizer.normalize(""))
    }

    @Test
    fun `returns empty list for only separators and whitespace`() {
        assertEquals(emptyList<String>(), GenreNormalizer.normalize("  , , "))
    }

    @Test
    fun `lowercases and dedupes`() {
        assertEquals(listOf("action"), GenreNormalizer.normalize("Action, ACTION"))
    }

    @Test
    fun `trims whitespace around tokens`() {
        assertEquals(listOf("action", "drama"), GenreNormalizer.normalize("  Action  ,  Drama  "))
    }
}
