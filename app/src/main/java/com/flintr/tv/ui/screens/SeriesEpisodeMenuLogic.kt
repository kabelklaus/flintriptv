package com.flintr.tv.ui.screens

import com.flintr.tv.data.model.Season

/**
 * Aktionen des Long-Press-Menüs auf Episodenkarten.
 */
enum class EpisodeMenuAction { MARK_WATCHED, MARK_UNWATCHED, MARK_PREVIOUS_WATCHED }

object SeriesEpisodeMenuLogic {

    private const val OPTION_TOGGLE_WATCHED = 0
    private const val OPTION_MARK_PREVIOUS_WATCHED = 1

    fun buildEpisodeMenuOptions(isWatched: Boolean): List<String> {
        val toggleLabel = if (isWatched) "Als ungesehen markieren" else "Als gesehen markieren"
        return listOf(toggleLabel, "Alle vorherigen als gesehen markieren")
    }

    fun menuActionForOption(index: Int, isWatched: Boolean): EpisodeMenuAction? {
        return when (index) {
            OPTION_TOGGLE_WATCHED ->
                if (isWatched) EpisodeMenuAction.MARK_UNWATCHED else EpisodeMenuAction.MARK_WATCHED
            OPTION_MARK_PREVIOUS_WATCHED -> EpisodeMenuAction.MARK_PREVIOUS_WATCHED
            else -> null
        }
    }

    /**
     * Liefert die IDs aller Episoden vor der Ziel-Episode in Abspielreihenfolge
     * (Staffeln nach Nummer sortiert), ohne bereits gesehene Episoden.
     */
    fun previousEpisodeIds(
        seasons: List<Season>,
        targetSeasonNumber: Int,
        targetEpisodeId: Int,
        alreadyWatchedIds: Set<Int>
    ): List<Int> {
        val orderedEpisodes = seasons
            .sortedBy { it.seasonNumber }
            .flatMap { season ->
                (season.episodes ?: emptyList()).map { episode -> season.seasonNumber to episode }
            }

        val targetIndex = orderedEpisodes.indexOfFirst { (seasonNumber, episode) ->
            seasonNumber == targetSeasonNumber && episode.id == targetEpisodeId
        }
        if (targetIndex < 0) return emptyList()

        return orderedEpisodes
            .take(targetIndex)
            .map { (_, episode) -> episode.id }
            .filterNot { it in alreadyWatchedIds }
    }
}
