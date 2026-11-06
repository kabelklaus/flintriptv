package com.flintr.tv.ui.navigation

import com.flintr.tv.data.model.Category
import com.flintr.tv.ui.common.UiConstants.IN_PROGRESS_CATEGORY_ID
import com.flintr.tv.ui.common.UiConstants.NEW_CATEGORY_ID
import com.flintr.tv.ui.common.UiConstants.RECOMMENDED_CATEGORY_ID
import com.flintr.tv.ui.common.UiConstants.WATCHLIST_CATEGORY_ID
import com.flintr.tv.ui.screens.HomeDestination
import org.junit.Assert.assertEquals
import org.junit.Test

class SpecialCategoriesOrderTest {

    private val serverCategories = listOf(Category("1", "Action"), Category("2", "Drama"))

    private fun idsFor(destination: HomeDestination): List<String> =
        createSpecialCategories(
            destination = destination,
            sortedCategories = serverCategories,
            watchlistCategoryName = "Watchlist",
            newCategoryName = "Neu",
            inProgressCategoryName = "Angefangene Inhalte",
            recommendedCategoryName = "Für dich"
        ).map { it.categoryId }

    private val vodOrder = listOf(
        WATCHLIST_CATEGORY_ID,
        IN_PROGRESS_CATEGORY_ID,
        RECOMMENDED_CATEGORY_ID,
        NEW_CATEGORY_ID,
        "1",
        "2"
    )

    @Test
    fun `VOD Reihenfolge ist Watchlist, Angefangen, Fuer dich, Neu, dann Server-Kategorien`() {
        assertEquals(vodOrder, idsFor(HomeDestination.VOD))
    }

    @Test
    fun `Serien Reihenfolge ist Watchlist, Angefangen, Fuer dich, Neu, dann Server-Kategorien`() {
        assertEquals(vodOrder, idsFor(HomeDestination.Series))
    }

    @Test
    fun `LiveTV hat nur Watchlist und Neu vor den Server-Kategorien`() {
        assertEquals(
            listOf(WATCHLIST_CATEGORY_ID, NEW_CATEGORY_ID, "1", "2"),
            idsFor(HomeDestination.LiveTV)
        )
    }

    @Test
    fun `Ziele ohne Kategorien bekommen keine Spezial-Kategorien`() {
        assertEquals(listOf("1", "2"), idsFor(HomeDestination.EPG))
    }
}
