package com.flintr.tv.data.tmdb

import org.junit.Assert.assertEquals
import org.junit.Test

/** Table-driven test for [TitleNormalizer]. */
class TitleNormalizerTest {

    data class Case(val raw: String, val expectedTitle: String, val expectedYear: Int?)

    private val cases = listOf(
        Case("EN - Marley & Me (2008)", "marley me", 2008),
        Case("NF - Survive the Game  (2021)", "survive the game", 2021),
        Case(
            "EN - The Bride! (2026) (MULTI SUB) FRANKENSTEIN",
            "the bride frankenstein",
            2026
        ),
        Case("DE - Tokyo Tribe 2 (2006) (Ger Sub)", "tokyo tribe 2", 2006),
        Case("Beverly Hills Cop Axel F", "beverly hills cop axel f", null),
        Case("[X][4K][SpiZoo] Destiny Love - Sexy Skinny", "destiny love sexy skinny", null),
        Case("DE - Die Königin des Nordens (2021)", "die konigin des nordens", 2021),
        Case("", "", null),
        Case("   ", "", null),
        Case("X (1999) (2001) Tag", "x tag", 1999),
        Case("Béla Tarr (1994)", "bela tarr", 1994),
        Case("Axel:F", "axel f", null),
        Case("Axel F", "axel f", null),
        Case("[HD][2023 RERELEASE] Some Movie", "some movie", null),
        Case("ABCD - Some Title (2010)", "some title", 2010),
        Case("  Multiple   Spaces   Title  ", "multiple spaces title", null),
        Case("4K - Dust Bunny (2025)", "dust bunny", 2025)
    )

    @Test
    fun `normalize matches reference table`() {
        cases.forEach { case ->
            val (title, year) = TitleNormalizer.normalize(case.raw)
            assertEquals("title for '${case.raw}'", case.expectedTitle, title)
            assertEquals("year for '${case.raw}'", case.expectedYear, year)
        }
    }

    @Test
    fun `makeKey with year`() {
        assertEquals("marley me|2008", TitleNormalizer.makeKey("marley me", 2008))
    }

    @Test
    fun `makeKey without year`() {
        assertEquals(
            "beverly hills cop axel f|",
            TitleNormalizer.makeKey("beverly hills cop axel f", null)
        )
    }
}
