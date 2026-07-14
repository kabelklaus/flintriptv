package com.flintr.tv.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchStateTest {

    @Test
    fun `default search state selects all scopes in display order`() {
        val state = SearchState()

        assertEquals(SearchScope.orderedScopes, state.selectedScopes.toList())
    }

    @Test
    fun `toggling selected scope disables it and resets stale search state`() {
        val state = SearchState(
            query = "test",
            results = SearchResults(vod = listOf(movie())),
            isSearching = true,
            hasSearched = true
        )

        val updated = state.toggledScope(SearchScope.MOVIES)

        assertFalse(SearchScope.MOVIES in updated.selectedScopes)
        assertEquals(SearchResults(), updated.results)
        assertFalse(updated.isSearching)
        assertFalse(updated.hasSearched)
    }

    @Test
    fun `toggling unselected scope enables it`() {
        val state = SearchState(selectedScopes = setOf(SearchScope.LIVE_TV))

        val updated = state.toggledScope(SearchScope.SERIES)

        assertEquals(setOf(SearchScope.LIVE_TV, SearchScope.SERIES), updated.selectedScopes)
    }

    @Test
    fun `last selected scope cannot be disabled`() {
        val state = SearchState(selectedScopes = setOf(SearchScope.LIVE_TV))

        val updated = state.toggledScope(SearchScope.LIVE_TV)

        assertEquals(state, updated)
        assertTrue(SearchScope.LIVE_TV in updated.selectedScopes)
    }

    private fun movie() = VodStream(
        num = null,
        name = "Testfilm",
        streamType = null,
        streamId = 1,
        streamIcon = null,
        rating = null,
        rating5Based = null,
        added = null,
        categoryId = null,
        categoryName = null,
        containerExtension = null,
        customSid = null,
        directSource = null,
        releaseDate = null,
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
}
