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

/**
 * Search state for ViewModel
 */
data class SearchState(
    val query: String = "",
    val results: SearchResults = SearchResults(),
    val isSearching: Boolean = false,
    val showMinLengthHint: Boolean = false
)
