package com.flintr.tv.utils

import android.util.Log
import androidx.compose.ui.graphics.Color
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*
import com.flintr.tv.utils.error.reportWarning
import com.flintr.tv.utils.error.reportControlFlow

/**
 * Formats an expiration date string into a human-readable format with status.
 * Supports various date formats including Unix timestamps (seconds and milliseconds) and date strings.
 * 
 * @param expDate The expiration date string, can be Unix timestamp or date string
 * @return Formatted string like "Account läuft ab: 15.03.2025 (noch 23 Tage)" or null if no date
 */
fun formatExpirationDate(expDate: String?): String? {
    if (expDate.isNullOrBlank()) {
        return null
    }
    
    val expirationDate = parseExpirationDate(expDate) ?: return null
    
    // Format date as dd.MM.yyyy
    val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    val formattedDate = expirationDate.format(dateFormatter)
    
    // Calculate days until expiration
    val today = LocalDate.now()
    val daysUntilExpiration = ChronoUnit.DAYS.between(today, expirationDate)
    
    // Determine status text
    val statusText = when {
        daysUntilExpiration > 0 -> "noch $daysUntilExpiration ${if (daysUntilExpiration == 1L) "Tag" else "Tage"}"
        daysUntilExpiration == 0L -> "läuft heute ab"
        else -> "abgelaufen"
    }
    
    return "Account läuft ab: $formattedDate ($statusText)"
}

/**
 * Determines the color for expiration date display based on days until expiration.
 * 
 * @param expDate The expiration date string
 * @param normalColor Normal color for dates > 7 days
 * @param warningColor Warning color for dates <= 7 days and > 0 days
 * @param expiredColor Expired color for dates in the past
 * @return Color to use for the expiration date display
 */
fun getExpirationDateColor(
    expDate: String?,
    normalColor: Color,
    warningColor: Color,
    expiredColor: Color
): Color {
    if (expDate.isNullOrBlank()) {
        return normalColor
    }
    
    val expirationDate = parseExpirationDate(expDate) ?: return normalColor
    
    val today = LocalDate.now()
    val daysUntilExpiration = ChronoUnit.DAYS.between(today, expirationDate)
    
    return when {
        daysUntilExpiration < 0 -> expiredColor
        daysUntilExpiration <= 7 -> warningColor
        else -> normalColor
    }
}

/**
 * Parses the timestamps we persist/still receive (ISO instant, offset date-time, local
 * date-time as UTC, or epoch millis). A malformed value must never drop the entry: it
 * falls back to 0 and reports the original [Instant.parse] error via [onError].
 */
fun parseTimestampMillisOrZero(raw: String, onError: (Exception) -> Unit): Long {
    return try {
        Instant.parse(raw).toEpochMilli()
    } catch (instantError: Exception) {
        try {
            OffsetDateTime.parse(raw).toInstant().toEpochMilli()
        } catch (offsetError: Exception) {
            try {
                LocalDateTime.parse(raw).toInstant(ZoneOffset.UTC).toEpochMilli()
            } catch (localError: Exception) {
                raw.toLongOrNull() ?: run {
                    onError(instantError)
                    0L
                }
            }
        }
    }
}

/**
 * Parses an expiration date string into a LocalDate.
 * Supports Unix timestamps (seconds and milliseconds) and various date string formats.
 * 
 * @param expDate The expiration date string
 * @return LocalDate if parsing succeeds, null otherwise
 */
private fun parseExpirationDate(expDate: String): LocalDate? {
    // Try parsing as Unix timestamp (numeric string)
    if (expDate.all { it.isDigit() }) {
        try {
            val timestamp = expDate.toLong()
            // Determine if it's seconds or milliseconds based on string length
            // Unix timestamps in seconds typically have 10 or fewer digits
            val date = if (expDate.length <= 10) {
                // Likely seconds (10 digits or less)
                Date(timestamp * 1000)
            } else {
                // Likely milliseconds (more than 10 digits)
                Date(timestamp)
            }
            return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
        } catch (e: Exception) {
            reportWarning("DateUtils", "Failed to parse timestamp: $expDate", e)
        }
    }
    
    // Try parsing as date string in various formats
    val dateFormats = listOf(
        "yyyy-MM-dd",
        "dd.MM.yyyy",
        "yyyy/MM/dd",
        "dd-MM-yyyy",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ss.SSS",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
    )
    
    for (format in dateFormats) {
        try {
            val sdf = SimpleDateFormat(format, Locale.getDefault())
            sdf.isLenient = false
            val date = sdf.parse(expDate)
            if (date != null) {
                return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
            }
        } catch (e: Exception) {
            // Try next format
            reportControlFlow("DateUtils.parseFlexibleDate", e)
        }
    }
    
    Log.w("DateUtils", "Failed to parse expiration date: $expDate")
    return null
}
