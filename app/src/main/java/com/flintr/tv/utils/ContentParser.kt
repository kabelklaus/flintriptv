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
        
        // Extract rating fields using destructuring
        val (rating: Any?, rating5Based: Double?) = when (item) {
            is VodStream -> item.rating to item.rating5Based
            is Series -> item.rating to item.rating5Based
            else -> return null
        }
        
        // Try rating first (usually 0-10 scale)
        rating?.let { r ->
            parseRating(r)?.let { parsed ->
                return String.format("%.1f", parsed)
            }
        }
        
        // Try rating_5based (0-5 scale, convert to 0-10)
        rating5Based?.let { r5 ->
            return String.format("%.1f", r5 * 2.0)
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
            is String -> rating.toDoubleOrNull()
            else -> null
        }
    }
}

