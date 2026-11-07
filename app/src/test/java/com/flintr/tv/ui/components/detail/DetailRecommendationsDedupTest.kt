package com.flintr.tv.ui.components.detail

import com.flintr.tv.data.model.VodStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailRecommendationsDedupTest {

    private data class Item(val title: String)

    private fun dedup(vararg titles: String): List<String> {
        val items = titles.map(::Item)
        return deduplicateRecommendations(items) { it.title }.map { it.title }
    }

    private fun movie(id: Int, name: String, releaseDate: String?): VodStream = VodStream(
        num = null,
        name = name,
        streamType = "movie",
        streamId = id,
        streamIcon = null,
        rating = null,
        rating5Based = null,
        added = null,
        categoryId = null,
        categoryName = null,
        containerExtension = null,
        customSid = null,
        directSource = null,
        releaseDate = releaseDate,
        duration = null,
        plot = null,
        cast = null,
        director = null,
        genre = null,
        backdropPath = null,
        youtubeTrailer = null,
        cover = null,
        tmdbId = null
    )

    @Test
    fun nfdEntferntUmlauteUndKollabiertDeutscheVarianten() {
        assertEquals("bose madchen", normalizeRecommendationTitle("Böse Mädchen"))
        assertEquals(listOf("Böse Mädchen"), dedup("Böse Mädchen", "Bose Madchen"))
    }

    @Test
    fun scharfesSImTitelLiefertWeiterhinEinenNichtLeerenNormalisator() {
        // ß has no NFD decomposition; the ASCII filter drops it. The important part for
        // dedup is that the title still produces a (non-blank) key, so it stays in the row.
        assertTrue(normalizeRecommendationTitle("Weißer Hai").isNotBlank())
    }

    @Test
    fun markerInDerMitteWirdNichtAlsSuffixEntfernt() {
        assertEquals("apollo 13 de special", normalizeRecommendationTitle("Apollo 13 DE Special"))
        assertEquals("4k hdr film", normalizeRecommendationTitle("4K HDR Film"))
    }

    @Test
    fun nichtLateinischeTitelMitUnterschiedlichemRawKeyKollabierenNicht() {
        val titles = listOf("Иван Васильевич", "Иван Грозный")

        assertEquals(titles, dedup(*titles.toTypedArray()))
    }

    @Test
    fun nurSuffixTitelKollabiertNichtMitEchtemTitel() {
        assertEquals(listOf("4K", "Der Film"), dedup("4K", "Der Film"))
    }

    @Test
    fun latinVariantenKollabierenAufDasErsteVorkommen() {
        val items = listOf(
            movie(1, "Apollo 13", "1995-06-30"),
            movie(2, "Apollo 13 DE", "1995-06-30"),
            movie(3, "Apollo 13 4K", "1995-06-30")
        )

        val result = deduplicateRecommendations(items) { it.name }

        assertEquals(listOf("Apollo 13"), result.map { it.name })
    }

    @Test
    fun verschiedeneJahreBleibenGetrennt() {
        val items = listOf(
            movie(1, "Apollo 13", "1995-06-30"),
            movie(2, "Apollo 13", "2020-01-01")
        )

        val result = deduplicateRecommendations(items) { it.name }

        assertEquals(listOf(1, 2), result.map { it.streamId })
    }
}
