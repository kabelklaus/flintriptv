package com.flintr.tv.ui.components.detail

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Display-layer formatting for the detail-header meta line ("runtime • releaseDate", built raw by
 * MovieDetailScreen/SeriesDetailScreen from Xtream fields). Pure functions, no Compose - the raw
 * fields stay untouched upstream, only the rendered meta line is normalized here:
 *
 *  - dates: every known source format (ISO, slashes, dots, dashes, optional trailing time, epoch
 *    10-13 digits) becomes "TT.MM.JJJJ"; unparsable values show raw (or just the contained
 *    19xx/20xx year) - never a crash, never an empty string
 *  - runtime: bare numbers get " Min" ("155" -> "155 Min"); values that already carry a unit
 *    ("2h 35min", "155 min") pass through whitespace-normalized; unparsable/empty values stay
 *    empty - never "0 Min"
 *
 * [formatMetaLine] maps these rules onto the separator-joined meta string ("•", optionally "|"),
 * keeping the separators and their order intact.
 */

private val GERMAN_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

private val WHITESPACE_RUN = Regex("\\s+")
private val TRAILING_TIME = Regex("^(\\d{4}[-/.]\\d{1,2}[-/.]\\d{1,2}|\\d{1,2}[-/.]\\d{1,2}[-/.]\\d{4})([ T].*)?$")
private val EPOCH_ONLY = Regex("^\\d{10,13}$")
private val YEAR_FIRST_DATE = Regex("^(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})$")
private val YEAR_LAST_DATE = Regex("^(\\d{1,2})[-/.](\\d{1,2})[-/.](\\d{4})$")
private val CONTAINED_YEAR = Regex("(?<!\\d)(19|20)\\d{2}(?!\\d)")
private val BARE_INTEGER = Regex("^\\d+$")
private val BARE_DECIMAL = Regex("^\\d+[.,]\\d+$")

private val META_TOKEN = Regex("[^•|]+|[•|]")
private val SEPARATOR_RUN = Regex("([•|])(\\s*[•|])+")
private val LEADING_SEPARATOR = Regex("^\\s*[•|]\\s*")
private val TRAILING_SEPARATOR = Regex("\\s*[•|]\\s*$")
private val SEPARATOR_SPACING = Regex("\\s*([•|])\\s*")

/**
 * Formats a raw date value as "TT.MM.JJJJ". Understands ISO ("2013-12-25", "2013/12/25"), German
 * ("25.12.2013"), dashed ("25-12-2013"), US-slash ("12/25/2013"), values with a trailing time
 * ("2013-12-25 00:00:00", "2013-12-25T00:00:00") and epoch timestamps (10 digits = seconds,
 * 11-13 digits = milliseconds, UTC). Unparsable values are shown raw; if they contain a 19xx/20xx
 * year, just the year is shown. Never crashes, never returns an empty string for non-empty input.
 */
fun formatDate(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    val value = raw.trim()
    parseDateOrNull(value)?.let { return it.format(GERMAN_DATE) }
    CONTAINED_YEAR.find(value)?.let { return it.value }
    return value
}

/**
 * Formats a raw runtime value: bare numbers get " Min" appended ("155" -> "155 Min"; a bare
 * 19xx/20xx year-like value is kept as-is - it is a date, not a runtime). Values that already
 * carry a unit ("2h 35min", "155 min", "2:35") pass through whitespace-normalized. Unparsable or
 * empty values return "" - never "0 Min".
 */
fun formatRuntime(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    val value = raw.trim().replace(WHITESPACE_RUN, " ")
    return when {
        BARE_INTEGER.matches(value) -> if (isYearLike(value)) value else "$value Min"
        BARE_DECIMAL.matches(value) -> "$value Min"
        value.any { it.isDigit() } && value.any { it.isLetter() || it == ':' } -> value
        else -> ""
    }
}

/**
 * Formats the whole meta line (segments joined by "•", optionally "|"): the last segment is the
 * date slot (raw fallback, never dropped), all other segments get date-then-runtime treatment
 * (a real date or a bare year wins, bare numbers get " Min", unit values pass through, unparsable
 * runtime values are dropped). Separators and segment order stay as delivered.
 */
fun formatMetaLine(meta: String?): String? {
    if (meta.isNullOrBlank()) return meta
    val tokens = META_TOKEN.findAll(meta).map { it.value }.toList()
    val textIndices = tokens.indices.filter { !isSeparatorToken(tokens[it]) }
    val lastSlot = textIndices.size - 1
    val formatted = tokens.mapIndexed { index, token ->
        if (isSeparatorToken(token)) {
            token
        } else {
            val value = token.trim()
            val slot = textIndices.indexOf(index)
            if (slot == lastSlot && textIndices.size > 1) {
                formatDate(value)
            } else {
                parseDateOrNull(value)?.format(GERMAN_DATE)
                    ?: CONTAINED_YEAR.find(value)?.value
                    ?: formatRuntime(value)
            }
        }
    }.joinToString("")
    return normalizeMetaSpacing(formatted)
}

/**
 * Normalizes a raw season label for the German UI: every "Season N" (case-insensitive, optional
 * ".", ":", "#" or "-" between word and number, leading zeros dropped) becomes "Staffel N"; the
 * rest of the label stays untouched ("Staffel 3" passes through, "Specials" stays "Specials").
 */
fun formatSeasonLabel(raw: String): String =
    SEASON_WORD.replace(raw.trim()) { "Staffel ${it.groupValues[1].trimStart('0').ifEmpty { "0" }}" }

private val SEASON_WORD = Regex("\\bseason\\s*[.:#-]?\\s*(\\d+)", RegexOption.IGNORE_CASE)

private fun isSeparatorToken(token: String): Boolean = token == "•" || token == "|"

private fun isYearLike(value: String): Boolean =
    value.length == 4 && value.toIntOrNull()?.let { it in 1900..2099 } == true

/** Parses a date value (time part and epoch included); null when the value is no valid date. */
private fun parseDateOrNull(value: String): LocalDate? {
    val core = TRAILING_TIME.matchEntire(value)?.groupValues?.get(1) ?: value
    if (EPOCH_ONLY.matches(core)) {
        val epoch = core.toLong()
        // 10 digits are epoch seconds; 11-13 digits are only plausible as milliseconds.
        val instant = if (core.length == 10) Instant.ofEpochSecond(epoch) else Instant.ofEpochMilli(epoch)
        return instant.atZone(ZoneOffset.UTC).toLocalDate()
    }
    YEAR_FIRST_DATE.matchEntire(core)?.let { m ->
        return validDate(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
    }
    YEAR_LAST_DATE.matchEntire(core)?.let { m ->
        val first = m.groupValues[1].toInt()
        val second = m.groupValues[2].toInt()
        val year = m.groupValues[3].toInt()
        // Unambiguous when one part is > 12; ambiguous values default to day-first (German).
        val (month, day) = when {
            first > 12 -> second to first
            second > 12 -> first to second
            else -> second to first
        }
        return validDate(year, month, day)
    }
    return null
}

private fun validDate(year: Int, month: Int, day: Int): LocalDate? = try {
    LocalDate.of(year, month, day)
} catch (_: DateTimeException) {
    null
}

/** Collapses separator runs left by dropped segments and normalizes spacing around separators. */
private fun normalizeMetaSpacing(raw: String): String {
    var out = SEPARATOR_RUN.replace(raw, "$1")
    out = LEADING_SEPARATOR.replace(out, "")
    out = TRAILING_SEPARATOR.replace(out, "")
    out = SEPARATOR_SPACING.replace(out, " $1 ")
    return WHITESPACE_RUN.replace(out, " ").trim()
}
