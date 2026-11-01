package com.flintr.tv.domain.recommendation

import java.util.Locale

/**
 * Normalizes raw Xtream genre strings ("Action, Adventure", "Drama|Horror") into clean,
 * deduped, lowercase genre tokens for use as score-engine map keys.
 */
object GenreNormalizer {
    private val SEPARATORS = Regex("[,|;/&]")

    fun normalize(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()

        return raw.split(SEPARATORS)
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotEmpty() }
            .distinct()
    }
}
