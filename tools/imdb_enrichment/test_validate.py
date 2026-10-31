"""Tests for validate_hits.py, stdlib unittest only, small inline fixtures."""

from __future__ import annotations

import gzip
import json
import tempfile
import unittest
from pathlib import Path

from build_enrichment import write_db
from validate_hits import classify_catalog, load_catalog, load_enrichment, match_item

ENRICHMENT = {
    "marley me|2008": [7.1, 120000, "Comedy,Drama"],
    "the bride|2026": [8.1, 1200, "Horror"],
    "nebula rising|": [6.4, 800, "SciFi"],
    "munich|2005": [7.9, 95000, "Drama,Thriller"],
}

YEAR_SHIFT_ENRICHMENT = {
    "x|2005": [5.0, 100, "Drama"],
    "x y|2005": [5.1, 150, "Drama"],
    "x|": [5.2, 200, "Drama"],
}

YEAR_PRIORITY_ENRICHMENT = {
    "x|2006": [6.0, 100, "Drama"],
    "x|2005": [6.1, 150, "Drama"],
}


class ExactMatchTests(unittest.TestCase):
    def test_exact_key_with_year_matches(self) -> None:
        status, key = match_item("EN - Marley & Me (2008)", ENRICHMENT)
        self.assertEqual(status, "exact")
        self.assertEqual(key, "marley me|2008")


class ShorteningMatchTests(unittest.TestCase):
    def test_trailing_junk_shortens_to_core_title(self) -> None:
        """'the bride frankenstein|2026' misses, shortened to 'the bride|2026' hits."""
        status, key = match_item("EN - The Bride Frankenstein (2026)", ENRICHMENT)
        self.assertEqual(status, "shortened")
        self.assertEqual(key, "the bride|2026")


class YearlessMatchTests(unittest.TestCase):
    def test_yearless_item_matches_bare_key(self) -> None:
        status, key = match_item("EN - Nebula Rising", ENRICHMENT)
        self.assertEqual(status, "exact")
        self.assertEqual(key, "nebula rising|")


class ShorteningToSingleWordTests(unittest.TestCase):
    def test_trailing_junk_shortens_down_to_single_word_key(self) -> None:
        """'munich steven spielberg|2005' misses two-word 'munich steven' too, shortens
        all the way to the one-word key 'munich|2005'."""
        status, key = match_item("Munich (2005) STEVEN SPIELBERG", ENRICHMENT)
        self.assertEqual(status, "shortened")
        self.assertEqual(key, "munich|2005")


class YearShiftMatchTests(unittest.TestCase):
    def test_year_minus_one_matches_at_full_length(self) -> None:
        """Only 'x|2005' exists; item year 2006 matches via year-1."""
        enrichment = {"x|2005": [5.0, 100, "Drama"]}
        status, key = match_item("X (2006)", enrichment)
        self.assertEqual(status, "year-shift")
        self.assertEqual(key, "x|2005")

    def test_year_plus_one_matches_at_full_length(self) -> None:
        """Only 'x|2005' exists; item year 2004 matches via year+1."""
        enrichment = {"x|2005": [5.0, 100, "Drama"]}
        status, key = match_item("X (2004)", enrichment)
        self.assertEqual(status, "year-shift")
        self.assertEqual(key, "x|2005")

    def test_bare_key_fallback_at_full_length(self) -> None:
        """Only the bare key 'x|' exists; item year 1995 falls back to it."""
        enrichment = {"x|": [5.0, 100, "Drama"]}
        status, key = match_item("X (1995)", enrichment)
        self.assertEqual(status, "year-shift")
        self.assertEqual(key, "x|")

    def test_shortened_combined_with_year_shift(self) -> None:
        """'x y z|2006' misses at full length; shortened 'x y|2005' (year-1) hits."""
        status, key = match_item("X Y Z (2006)", YEAR_SHIFT_ENRICHMENT)
        self.assertEqual(status, "shortened")
        self.assertEqual(key, "x y|2005")

    def test_exact_year_beats_year_shift(self) -> None:
        """Both 'x|2006' and 'x|2005' exist; the exact-year key wins."""
        status, key = match_item("X (2006)", YEAR_PRIORITY_ENRICHMENT)
        self.assertEqual(status, "exact")
        self.assertEqual(key, "x|2006")


class UnmatchedTests(unittest.TestCase):
    def test_trash_name_stays_unmatched(self) -> None:
        status, key = match_item("[X][4K][SpiZoo] Destiny Love - Sexy Scene", ENRICHMENT)
        self.assertEqual(status, "unmatched")
        self.assertIsNone(key)


class ReportConsistencyTests(unittest.TestCase):
    def test_total_equals_matched_plus_unmatched(self) -> None:
        movies = [
            {"name": "EN - Marley & Me (2008)"},
            {"name": "EN - The Bride Frankenstein (2026)"},
            {"name": "EN - Nebula Rising"},
            {"name": "[X][4K][SpiZoo] Destiny Love - Sexy Scene"},
        ]
        stats = classify_catalog(movies, "name", ENRICHMENT)
        self.assertEqual(stats["total"], 4)
        self.assertEqual(stats["exact"], 2)
        self.assertEqual(stats["shortened"], 1)
        self.assertEqual(stats["unmatched"], 1)
        self.assertEqual(
            stats["total"], stats["exact"] + stats["shortened"] + stats["unmatched"]
        )


class LoadFixtureFileTests(unittest.TestCase):
    def test_load_enrichment_and_catalog_from_disk(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir:
            tmp_path = Path(tmp_dir)

            enrichment_path = tmp_path / "imdb-enrichment.db"
            write_db(enrichment_path, ENRICHMENT)

            movies_path = tmp_path / "movies.json"
            movies_path.write_text(
                json.dumps([{"name": "EN - Marley & Me (2008)", "rating": "7.0"}]),
                encoding="utf-8",
            )

            loaded_enrichment = load_enrichment(str(enrichment_path))
            loaded_movies = load_catalog(str(movies_path))

            self.assertEqual(len(loaded_enrichment), len(ENRICHMENT))
            for key, value in ENRICHMENT.items():
                self.assertEqual(loaded_enrichment[key], value)
            self.assertNotIn("missing|2000", loaded_enrichment)
            self.assertEqual(loaded_movies[0]["name"], "EN - Marley & Me (2008)")

    def test_load_enrichment_from_gzipped_db(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir:
            tmp_path = Path(tmp_dir)
            db_path = tmp_path / "imdb-enrichment.db"
            write_db(db_path, ENRICHMENT)
            gz_path = tmp_path / "imdb-enrichment.db.gz"
            with open(db_path, "rb") as src, gzip.open(gz_path, "wb") as dst:
                dst.write(src.read())

            loaded_enrichment = load_enrichment(str(gz_path))

            self.assertEqual(loaded_enrichment["munich|2005"], [7.9, 95000, "Drama,Thriller"])


class SqliteLookupChainTests(unittest.TestCase):
    """The lookup chain runs unchanged against the SQLite-backed enrichment."""

    def test_match_item_against_db(self) -> None:
        with tempfile.TemporaryDirectory() as tmp_dir:
            db_path = Path(tmp_dir) / "imdb-enrichment.db"
            write_db(db_path, {**ENRICHMENT, **YEAR_PRIORITY_ENRICHMENT})
            enrichment = load_enrichment(str(db_path))

            self.assertEqual(match_item("EN - Marley & Me (2008)", enrichment), ("exact", "marley me|2008"))
            self.assertEqual(match_item("X (2007)", enrichment), ("year-shift", "x|2006"))
            self.assertEqual(
                match_item("Munich (2005) STEVEN SPIELBERG", enrichment), ("shortened", "munich|2005")
            )
            self.assertEqual(
                match_item("[X][4K][SpiZoo] Destiny Love - Sexy Scene", enrichment), ("unmatched", None)
            )


if __name__ == "__main__":
    unittest.main()
