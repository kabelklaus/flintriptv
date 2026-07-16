package com.flintr.tv.ui.screens

import com.flintr.tv.data.local.SmartResumeTarget
import com.flintr.tv.data.model.Episode
import com.flintr.tv.data.model.Season

/**
 * Label-Typ für den Weiterschauen-Button in der Serien-Detailansicht.
 */
enum class ResumeLabelType { RESUME, NEXT, START }

/**
 * Aufgelöste Ziel-Episode für den Weiterschauen-Button.
 */
data class ResumeTargetInfo(
    val episode: Episode,
    val seasonNumber: Int,
    val episodeNumber: Int
)

object SeriesResumeLogic {

    fun resolveResumeEpisode(seasons: List<Season>, target: SmartResumeTarget?): ResumeTargetInfo? {
        val sortedSeasons = seasons.sortedBy { it.seasonNumber }

        if (target != null) {
            val targetSeason = sortedSeasons.firstOrNull { it.seasonNumber == target.seasonNumber }
            val targetEpisode = targetSeason?.episodes?.firstOrNull {
                it.id == target.episodeId || (it.episodeNum ?: it.id) == target.episodeNumber
            }
            if (targetSeason != null && targetEpisode != null) {
                return ResumeTargetInfo(
                    episode = targetEpisode,
                    seasonNumber = targetSeason.seasonNumber,
                    episodeNumber = targetEpisode.episodeNum ?: targetEpisode.id
                )
            }
        }

        val fallbackSeason = sortedSeasons.firstOrNull { !it.episodes.isNullOrEmpty() } ?: return null
        val fallbackEpisode = fallbackSeason.episodes?.firstOrNull() ?: return null
        return ResumeTargetInfo(
            episode = fallbackEpisode,
            seasonNumber = fallbackSeason.seasonNumber,
            episodeNumber = fallbackEpisode.episodeNum ?: fallbackEpisode.id
        )
    }

    fun determineResumeLabelType(hasProgressOnTarget: Boolean, hasAnyWatchHistory: Boolean): ResumeLabelType {
        return when {
            hasProgressOnTarget -> ResumeLabelType.RESUME
            hasAnyWatchHistory -> ResumeLabelType.NEXT
            else -> ResumeLabelType.START
        }
    }

    fun buildResumeButtonLabel(type: ResumeLabelType, seasonNumber: Int, episodeNumber: Int): String {
        val prefix = when (type) {
            ResumeLabelType.RESUME -> "Weiterschauen"
            ResumeLabelType.NEXT -> "Nächste Folge"
            ResumeLabelType.START -> "Abspielen"
        }
        return "$prefix: S${seasonNumber}E$episodeNumber"
    }
}
