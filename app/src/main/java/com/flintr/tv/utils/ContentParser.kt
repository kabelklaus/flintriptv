package com.flintr.tv.utils

import com.flintr.tv.data.model.Series
import com.flintr.tv.data.model.VodStream
import java.util.Locale

/**
 * Utility functions for parsing content data
 * Translated from Flutter content_parser.dart
 */
object ContentParser {
    /**
     * Extracts rating from VodStream or Series
     * Priority: rating (0-10) -> rating_5based (0-5, ×2) -> imdb_rating/imdbRating
     * Returns formatted string with 1 decimal place (e.g., "8.0", "7.6") or null
     */
    fun getRating(item: Any?): String? {
        if (item == null) return null
        
        // Extract rating fields using destructuring
        val (rating: Any?, rating5Based: Double?) = when (item) {
            is VodStream -> item.rating to item.rating5Based
            is Series -> item.rating to item.rating5Based
            else -> return null
        }
        
        // Try rating first (usually 0-10 scale)
        rating?.let { r ->
            parseRating(r)?.let { parsed ->
                return String.format(Locale.US, "%.1f", parsed)
            }
        }
        
        // Try rating_5based (0-5 scale, convert to 0-10)
        rating5Based?.let { r5 ->
            return String.format(Locale.US, "%.1f", r5 * 2.0)
        }
        
        return null
    }
    
    private val EPISODE_CODE_REGEX = Regex("(?i)\\bS\\d{1,3}\\s?E\\d{1,3}\\b")
    private val LANGUAGE_PREFIX_REGEX = Regex("^[A-Z]{2,3} - ")
    private val PARENTHESES_SUFFIX_REGEX = Regex("\\s*\\([^)]*\\)")
    private val TITLE_SEPARATORS = charArrayOf(' ', '-', ':', '.', '–')

    /**
     * Extrahiert den reinen Episodennamen aus einem Provider-Titel wie
     * "DE - The Boys - S02E05 - Wir müssen jetzt los" -> "Wir müssen jetzt los".
     */
    fun extractEpisodeName(rawTitle: String?, seriesName: String?): String? {
        val title = rawTitle?.trim().orEmpty()
        if (title.isEmpty()) return null

        // Nach dem SxxExx-Code steht der eigentliche Episodenname
        val codeMatch = EPISODE_CODE_REGEX.find(title)
        if (codeMatch != null) {
            val afterCode = title.substring(codeMatch.range.last + 1).trim(*TITLE_SEPARATORS)
            return afterCode.ifEmpty { null }
        }

        // Kein Code vorhanden: Sprach- und Serienpräfix entfernen, falls vorhanden
        var result = title.replaceFirst(LANGUAGE_PREFIX_REGEX, "")
        seriesName?.let { name ->
            val baseSeriesName = name
                .replaceFirst(LANGUAGE_PREFIX_REGEX, "")
                .replace(PARENTHESES_SUFFIX_REGEX, "")
                .trim()
            if (baseSeriesName.isNotEmpty() && result.startsWith(baseSeriesName, ignoreCase = true)) {
                result = result.substring(baseSeriesName.length)
            }
        }
        return result.trim(*TITLE_SEPARATORS).ifEmpty { null }
    }

    /**
     * Liefert Kandidaten für das Hintergrundbild der Serie in Prioritätsreihenfolge:
     * Backdrops -> erstes Episodenbild -> Cover. Schlägt ein Kandidat beim Laden
     * fehl, kann die UI den nächsten probieren.
     */
    fun seriesBackdropCandidates(series: Series): List<String> {
        val backdrops = series.backdropPath.orEmpty()
        val firstEpisodeImage = series.seasons
            ?.sortedBy { it.seasonNumber }
            ?.asSequence()
            ?.flatMap { it.episodes.orEmpty() }
            ?.mapNotNull { it.info?.movieImage }
            ?.firstOrNull { it.isNotBlank() }

        return imageCandidates(*backdrops.toTypedArray(), firstEpisodeImage, series.cover)
    }

    /**
     * Filtert Bild-URL-Kandidaten: leere und doppelte Einträge fliegen raus,
     * die Reihenfolge bleibt erhalten.
     */
    fun imageCandidates(vararg urls: String?): List<String> {
        return urls.filterNotNull()
            .filter { it.isNotBlank() }
            .distinct()
    }

    /**
     * Parses rating from String or Number
     * Returns Double or null if parsing fails
     */
    private fun parseRating(rating: Any?): Double? {
        if (rating == null) return null
        
        return when (rating) {
            is Number -> rating.toDouble()
            is String -> {
                val parsed = rating.toDoubleOrNull()
                parsed
            }
            else -> null
        }
    }
}

