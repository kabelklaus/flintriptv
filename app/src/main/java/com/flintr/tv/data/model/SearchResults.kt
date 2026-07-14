package com.flintr.tv.data.model

/**
 * Search results grouped by content type
 */
data class SearchResults(
    val liveTV: List<LiveStream> = emptyList(),
    val vod: List<VodStream> = emptyList(),
    val series: List<Series> = emptyList()
) {
    val isEmpty: Boolean get() = liveTV.isEmpty() && vod.isEmpty() && series.isEmpty()
    val totalCount: Int get() = liveTV.size + vod.size + series.size
}

enum class SearchScope(val label: String) {
    LIVE_TV("TV"),
    MOVIES("Filme"),
    SERIES("Serien");

    companion object {
        val orderedScopes: List<SearchScope> = listOf(LIVE_TV, MOVIES, SERIES)
        val defaultScopes: Set<SearchScope> = orderedScopes.toSet()
    }
}

/**
 * Search state for ViewModel
 */
data class SearchState(
    val query: String = "",
    val results: SearchResults = SearchResults(),
    val isSearching: Boolean = false,
    val showMinLengthHint: Boolean = false,
    val hasSearched: Boolean = false,
    val selectedScopes: Set<SearchScope> = SearchScope.defaultScopes
) {
    fun toggledScope(scope: SearchScope): SearchState {
        val updatedScopes = if (scope in selectedScopes) {
            selectedScopes - scope
        } else {
            selectedScopes + scope
        }

        if (updatedScopes.isEmpty()) {
            return this
        }

        return copy(
            selectedScopes = updatedScopes,
            results = SearchResults(),
            isSearching = false,
            hasSearched = false
        )
    }
}
