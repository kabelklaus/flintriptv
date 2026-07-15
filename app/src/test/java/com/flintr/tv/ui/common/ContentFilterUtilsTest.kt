package com.flintr.tv.ui.common

import com.flintr.tv.data.model.testLiveStream
import com.flintr.tv.data.model.testSeries
import com.flintr.tv.data.model.testVodStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Filterlogik: "Neu in den letzten 7 Tagen" und ausgeblendete Kategorien.
 */
class ContentFilterUtilsTest {

    private val nowSeconds = System.currentTimeMillis() / 1000
    private val recent = (nowSeconds - TimeUnit.DAYS.toSeconds(1)).toString()
    private val old = (nowSeconds - TimeUnit.DAYS.toSeconds(8)).toString()

    @Test
    fun sevenDaysAgoIsSevenDaysBeforeNow() {
        val expected = nowSeconds - TimeUnit.DAYS.toSeconds(7)

        // Kleine Toleranz, weil zwischen Berechnung und Aufruf Zeit vergeht
        val actual = ContentFilterUtils.sevenDaysAgoInSeconds()
        assertTrue("$actual sollte nahe $expected liegen", actual in expected..(expected + 5))
    }

    @Test
    fun recentStreamsKeepsOnlyLastSevenDays() {
        val streams = listOf(
            testLiveStream(name = "neu", added = recent),
            testLiveStream(name = "alt", added = old),
            testLiveStream(name = "ohne Datum", added = null),
            testLiveStream(name = "kaputt", added = "kein-timestamp")
        )

        val result = ContentFilterUtils.filterRecentStreams(streams)

        assertEquals(listOf("neu"), result.map { it.name })
    }

    @Test
    fun recentMoviesKeepsOnlyLastSevenDays() {
        val movies = listOf(
            testVodStream(name = "neu", added = recent),
            testVodStream(name = "alt", added = old)
        )

        val result = ContentFilterUtils.filterRecentMovies(movies)

        assertEquals(listOf("neu"), result.map { it.name })
    }

    @Test
    fun recentSeriesUsesLastModified() {
        val series = listOf(
            testSeries(name = "frisch", lastModified = recent),
            testSeries(name = "alt", lastModified = old),
            testSeries(name = "unbekannt", lastModified = null)
        )

        val result = ContentFilterUtils.filterRecentSeries(series)

        assertEquals(listOf("frisch"), result.map { it.name })
    }

    @Test
    fun emptyHiddenCategoriesFilterNothing() {
        val streams = listOf(testLiveStream(categoryId = "1"), testLiveStream(categoryId = "2"))

        assertEquals(streams, ContentFilterUtils.filterLiveStreamsByHiddenCategories(streams, emptySet()))
    }

    @Test
    fun hiddenCategoriesAreExcludedButNullCategoryStaysVisible() {
        val streams = listOf(
            testLiveStream(name = "sichtbar", categoryId = "1"),
            testLiveStream(name = "versteckt", categoryId = "18"),
            testLiveStream(name = "ohne Kategorie", categoryId = null)
        )

        val result = ContentFilterUtils.filterLiveStreamsByHiddenCategories(streams, setOf("18"))

        assertEquals(listOf("sichtbar", "ohne Kategorie"), result.map { it.name })
    }

    @Test
    fun hiddenCategoriesWorkForMoviesAndSeries() {
        val movies = listOf(
            testVodStream(name = "bleibt", categoryId = "1"),
            testVodStream(name = "weg", categoryId = "66")
        )
        val series = listOf(
            testSeries(name = "bleibt", categoryId = "1"),
            testSeries(name = "weg", categoryId = "66")
        )

        assertEquals(
            listOf("bleibt"),
            ContentFilterUtils.filterVodStreamsByHiddenCategories(movies, setOf("66")).map { it.name }
        )
        assertEquals(
            listOf("bleibt"),
            ContentFilterUtils.filterSeriesByHiddenCategories(series, setOf("66")).map { it.name }
        )
    }
}
