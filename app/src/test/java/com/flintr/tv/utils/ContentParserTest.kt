package com.flintr.tv.utils

import com.flintr.tv.data.model.testSeries
import com.flintr.tv.data.model.testVodStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Rating-Extraktion: rating (0-10) hat Vorrang, sonst rating_5based × 2.
 */
class ContentParserTest {

    @Test
    fun nullAndUnknownTypesReturnNull() {
        assertNull(ContentParser.getRating(null))
        assertNull(ContentParser.getRating("kein Model"))
    }

    @Test
    fun vodRatingStringIsFormattedWithOneDecimal() {
        assertEquals("8.0", ContentParser.getRating(testVodStream(rating = "8")))
        assertEquals("7.6", ContentParser.getRating(testVodStream(rating = "7.6")))
    }

    @Test
    fun unparseableRatingFallsBackToFiveBasedScale() {
        val stream = testVodStream(rating = "N/A", rating5Based = 3.8)

        assertEquals("7.6", ContentParser.getRating(stream))
    }

    @Test
    fun missingRatingUsesFiveBasedScaleTimesTwo() {
        assertEquals("8.0", ContentParser.getRating(testVodStream(rating5Based = 4.0)))
    }

    @Test
    fun noRatingAtAllReturnsNull() {
        assertNull(ContentParser.getRating(testVodStream()))
    }

    @Test
    fun seriesRatingWorksLikeVodRating() {
        assertEquals("9.1", ContentParser.getRating(testSeries(rating = "9.1")))
        assertEquals("7.0", ContentParser.getRating(testSeries(rating5Based = 3.5)))
        assertNull(ContentParser.getRating(testSeries()))
    }
}
