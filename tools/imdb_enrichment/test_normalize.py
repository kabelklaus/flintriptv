"""Table-driven tests for normalize_title/make_key, stdlib unittest only."""

from __future__ import annotations

import unittest

from normalize import make_key, normalize_title

NORMALIZE_CASES = [
    ("EN - Marley & Me (2008)", "marley me", 2008),
    ("NF - Survive the Game  (2021)", "survive the game", 2021),
    (
        "EN - The Bride! (2026) (MULTI SUB) FRANKENSTEIN",
        "the bride frankenstein",
        2026,
    ),
    ("DE - Tokyo Tribe 2 (2006) (Ger Sub)", "tokyo tribe 2", 2006),
    ("Beverly Hills Cop Axel F", "beverly hills cop axel f", None),
    ("[X][4K][SpiZoo] Destiny Love - Sexy Skinny", "destiny love sexy skinny", None),
    ("DE - Die Königin des Nordens (2021)", "die konigin des nordens", 2021),
    ("", "", None),
    ("   ", "", None),
    ("X (1999) (2001) Tag", "x tag", 1999),
    ("Béla Tarr (1994)", "bela tarr", 1994),
    ("Axel:F", "axel f", None),
    ("Axel F", "axel f", None),
    ("[HD][2023 RERELEASE] Some Movie", "some movie", None),
    ("ABCD - Some Title (2010)", "some title", 2010),
    ("  Multiple   Spaces   Title  ", "multiple spaces title", None),
    ("4K - Dust Bunny (2025)", "dust bunny", 2025),
]


class NormalizeTitleTests(unittest.TestCase):
    def test_table(self) -> None:
        for raw, expected_title, expected_year in NORMALIZE_CASES:
            with self.subTest(raw=raw):
                clean_title, year = normalize_title(raw)
                self.assertEqual(clean_title, expected_title)
                self.assertEqual(year, expected_year)


class MakeKeyTests(unittest.TestCase):
    def test_with_year(self) -> None:
        self.assertEqual(make_key("marley me", 2008), "marley me|2008")

    def test_without_year(self) -> None:
        self.assertEqual(
            make_key("beverly hills cop axel f", None), "beverly hills cop axel f|"
        )


if __name__ == "__main__":
    unittest.main()
