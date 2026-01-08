package com.flintr.tv.utils

import com.flintr.tv.data.model.Series
import com.flintr.tv.data.model.VodStream

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
        
        when (item) {
            is VodStream -> {
                // Try rating first (usually 0-10 scale)
                val rating = item.rating
                if (rating != null) {
                    val parsed = parseRating(rating)
                    if (parsed != null) {
                        return String.format("%.1f", parsed)
                    }
                }
                
                // Try rating_5based (0-5 scale, convert to 0-10)
                val rating5 = item.rating5Based
                if (rating5 != null) {
                    return String.format("%.1f", rating5 * 2.0)
                }
                
                // Note: VodStream doesn't have imdb_rating field in current model
                // If needed, add it to the data model
            }
            is Series -> {
                // Try rating first (usually 0-10 scale)
                val rating = item.rating
                if (rating != null) {
                    val parsed = parseRating(rating)
                    if (parsed != null) {
                        return String.format("%.1f", parsed)
                    }
                }
                
                // Try rating_5based (0-5 scale, convert to 0-10)
                val rating5 = item.rating5Based
                if (rating5 != null) {
                    return String.format("%.1f", rating5 * 2.0)
                }
            }
        }
        
        return null
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

