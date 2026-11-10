package com.flintr.tv.data.tmdb

import java.text.Normalizer
import java.util.Locale

/**
 * Normalizes catalog titles (e.g. `"EN - The Bride! (2026) (MULTI SUB) FRANKENSTEIN"`)
 * into a `(cleanTitle, year)` pair for TMDB search queries and builds cache keys.
 */
object TitleNormalizer {

    private val BRACKET_TAGS = Regex("\\[[^\\]]*\\]")
    private val LANG_PREFIX = Regex("^[A-Z0-9]{2,4}\\s*-\\s*")
    private val YEAR_GROUP = Regex("\\(((?:19|20)\\d{2})\\)")
    private val PAREN_GROUP = Regex("\\([^)]*\\)")
    private val COMBINING_MARKS = Regex("\\p{M}")
    private val PUNCTUATION = Regex("\\p{P}")
    private val WHITESPACE = Regex("\\s+")

    /** Strips scene tags, language prefix and parenthetical tags; extracts the release year. */
    fun normalize(name: String): Pair<String, Int?> {
        if (name.isBlank()) return "" to null

        var text = BRACKET_TAGS.replace(name, "")
        text = text.trim()
        text = LANG_PREFIX.replace(text, "")

        val year = YEAR_GROUP.find(text)?.groupValues?.get(1)?.toInt()
        text = PAREN_GROUP.replace(text, "")

        text = Normalizer.normalize(text, Normalizer.Form.NFD)
        text = COMBINING_MARKS.replace(text, "")

        text = text.lowercase(Locale.ROOT)
        text = PUNCTUATION.replace(text, " ")
        text = WHITESPACE.replace(text, " ").trim()

        return text to year
    }

    /** Builds the cache key used to match normalized titles against TMDB search results. */
    fun makeKey(clean: String, year: Int?): String =
        if (year == null) "$clean|" else "$clean|$year"
}
