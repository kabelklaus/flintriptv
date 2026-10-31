"""Tests for build_enrichment.py, stdlib unittest only, driven by fixtures/."""

from __future__ import annotations

import gzip
import io
import json
import sqlite3
import tempfile
import unittest
from pathlib import Path

from build_enrichment import (
    apply_bare_keys,
    count_keys,
    load_ratings,
    open_source,
    process_akas,
    process_basics,
    write_outputs,
)

FIXTURES_DIR = Path(__file__).parent / "fixtures"
MIN_VOTES = 500


def build_from_fixtures(min_votes: int = MIN_VOTES):
    with open(FIXTURES_DIR / "title.ratings.tsv", "r", encoding="utf-8", newline="") as f:
        ratings = load_ratings(f)
    with open(FIXTURES_DIR / "title.basics.tsv", "r", encoding="utf-8", newline="") as f:
        entries, entries_by_clean, enrichment, counts = process_basics(f, ratings, min_votes)
    with open(FIXTURES_DIR / "title.akas.tsv", "r", encoding="utf-8", newline="") as f:
        aka_titles_indexed = process_akas(f, entries, enrichment, entries_by_clean)
    apply_bare_keys(entries_by_clean, enrichment)
    counts = {**counts, **count_keys(enrichment), "aka_titles_indexed": aka_titles_indexed}
    return enrichment, counts


class FilterTests(unittest.TestCase):
    def test_adult_short_norating_lowvotes_excluded(self) -> None:
        enrichment, counts = build_from_fixtures()
        self.assertEqual(counts["rows_in"], 19)
        self.assertEqual(counts["rows_out"], 15)
        self.assertNotIn("adult flick|2018", enrichment)
        self.assertNotIn("quick clip|2005", enrichment)
        self.assertNotIn("no rating movie|2012", enrichment)
        self.assertNotIn("low votes movie|2015", enrichment)


class BothTitleFormsTests(unittest.TestCase):
    def test_primary_and_original_both_produce_keys(self) -> None:
        enrichment, _ = build_from_fixtures()
        self.assertIn("mystery harbor|2010", enrichment)
        self.assertIn("port mystere|2010", enrichment)
        self.assertEqual(enrichment["mystery harbor|2010"], enrichment["port mystere|2010"])


class UniquenessRuleTests(unittest.TestCase):
    def test_bare_key_present_alongside_year_key_when_entry_unique(self) -> None:
        """A single filtered IMDb entry with a startYear still gets both clean|YYYY and clean|."""
        enrichment, _ = build_from_fixtures()
        self.assertIn("frozen harbor|2019", enrichment)
        self.assertIn("frozen harbor|", enrichment)
        self.assertEqual(enrichment["frozen harbor|2019"], enrichment["frozen harbor|"])

    def test_no_bare_key_when_two_entries_share_clean_title_with_different_years(self) -> None:
        enrichment, _ = build_from_fixtures()
        self.assertIn("twin peaks redux|2001", enrichment)
        self.assertIn("twin peaks redux|2016", enrichment)
        self.assertNotIn("twin peaks redux|", enrichment)

    def test_bare_key_when_unique_entry_has_no_year(self) -> None:
        enrichment, _ = build_from_fixtures()
        self.assertIn("nebula rising|", enrichment)
        self.assertIn("desert bloom|", enrichment)

    def test_no_bare_key_when_two_entries_share_clean_title_without_year(self) -> None:
        enrichment, _ = build_from_fixtures()
        self.assertNotIn("duplicate dreams|", enrichment)

    def test_primary_and_original_same_clean_title_counts_as_one_entry(self) -> None:
        """primaryTitle != originalTitle but both normalize to the same clean_title (one row, one tconst)."""
        enrichment, _ = build_from_fixtures()
        self.assertIn("silent harbor|", enrichment)


class GenresTests(unittest.TestCase):
    def test_null_genres_become_empty_string(self) -> None:
        enrichment, _ = build_from_fixtures()
        self.assertEqual(enrichment["mystery harbor|2010"][2], "")


class ValueFormatTests(unittest.TestCase):
    def test_value_is_rating_votes_genrestring(self) -> None:
        enrichment, _ = build_from_fixtures()
        self.assertEqual(enrichment["frozen harbor|2019"], [7.5, 3000, "Drama,Thriller"])


class MetaConsistencyTests(unittest.TestCase):
    def test_keys_total_matches_output_and_split(self) -> None:
        enrichment, counts = build_from_fixtures()
        self.assertEqual(counts["keys_total"], len(enrichment))
        self.assertEqual(
            counts["keys_total"], counts["keys_with_year"] + counts["keys_without_year"]
        )
        self.assertEqual(counts["keys_total"], 24)
        self.assertEqual(counts["keys_with_year"], 9)
        self.assertEqual(counts["keys_without_year"], 15)


class AkasIndexingTests(unittest.TestCase):
    def test_akas_title_for_filtered_entry_creates_localized_key(self) -> None:
        """North Country (tt0000015, 2005) has an akas title 'Die Königin des Nordens'."""
        enrichment, _ = build_from_fixtures()
        self.assertIn("die konigin des nordens|2005", enrichment)
        self.assertEqual(enrichment["die konigin des nordens|2005"], enrichment["north country|2005"])

    def test_akas_title_for_excluded_entry_creates_no_key(self) -> None:
        """tt0000006 (a 'short') is filtered out in process_basics; its akas title is ignored."""
        enrichment, _ = build_from_fixtures()
        self.assertNotIn("some other title|", enrichment)

    def test_akas_collision_with_primary_title_key_keeps_first_value(self) -> None:
        """tt0000016's akas title normalizes to 'the great escape', colliding with tt0000001's
        primary-title key at the same year; the first value written wins, no crash."""
        enrichment, _ = build_from_fixtures()
        self.assertEqual(enrichment["the great escape|1963"], [8.2, 50000, "Adventure,Drama,War"])
        self.assertNotIn("the great escape|", enrichment)

    def test_bare_key_rule_is_entry_based_across_akas_titles(self) -> None:
        """tt0000017's three distinct akas titles each get their own bare key (one entry, one
        clean_title each); two different entries sharing an akas clean_title get none."""
        enrichment, counts = build_from_fixtures()
        self.assertIn("alpha signal|", enrichment)
        self.assertIn("beta call|", enrichment)
        self.assertIn("gamma light|", enrichment)
        self.assertNotIn("shared echo|", enrichment)
        self.assertEqual(counts["aka_titles_indexed"], 7)


class NewTitleTypesTests(unittest.TestCase):
    """tvSpecial (Netflix-style standup specials) and video (direct-to-video) must pass the
    titleType filter alongside the original movie/tvMovie/tvSeries/tvMiniSeries set; tvEpisode
    must keep being excluded. Uses in-memory fixtures so the shared fixtures/ counts asserted
    elsewhere stay untouched."""

    @staticmethod
    def _basics_stream(tconst: str, title_type: str, title: str, start_year: str) -> io.StringIO:
        header = "tconst\ttitleType\tprimaryTitle\toriginalTitle\tisAdult\tstartYear\tendYear\truntimeMinutes\tgenres\n"
        row = f"{tconst}\t{title_type}\t{title}\t{title}\t0\t{start_year}\t\\N\t60\tComedy\n"
        return io.StringIO(header + row)

    def test_tv_special_with_enough_votes_is_included(self) -> None:
        ratings = {"tt0009001": (7.0, 500)}
        stream = self._basics_stream("tt0009001", "tvSpecial", "Hot Forever", "2022")
        _, _, enrichment, counts = process_basics(stream, ratings, MIN_VOTES)
        self.assertEqual(counts["rows_out"], 1)
        self.assertIn("hot forever|2022", enrichment)

    def test_video_with_enough_votes_is_included(self) -> None:
        ratings = {"tt0009002": (7.0, 500)}
        stream = self._basics_stream("tt0009002", "video", "Look At You", "2022")
        _, _, enrichment, counts = process_basics(stream, ratings, MIN_VOTES)
        self.assertEqual(counts["rows_out"], 1)
        self.assertIn("look at you|2022", enrichment)

    def test_tv_episode_stays_excluded(self) -> None:
        ratings = {"tt0009003": (7.0, 500)}
        stream = self._basics_stream("tt0009003", "tvEpisode", "Some Episode", "2022")
        _, _, enrichment, counts = process_basics(stream, ratings, MIN_VOTES)
        self.assertEqual(counts["rows_out"], 0)
        self.assertNotIn("some episode|2022", enrichment)


class SqliteOutputTests(unittest.TestCase):
    """write_outputs emits imdb-enrichment.db + .db.gz + meta.json, and no JSON map anymore."""

    def setUp(self) -> None:
        self._tmp = tempfile.TemporaryDirectory()
        self.out_dir = Path(self._tmp.name)
        self.enrichment, counts = build_from_fixtures()
        self.meta = {"built_at": "2026-10-31T00:00:00Z", **counts}
        write_outputs(str(self.out_dir), self.enrichment, self.meta)

    def tearDown(self) -> None:
        self._tmp.cleanup()

    def _query(self, db_path: Path, sql: str, params=()):
        conn = sqlite3.connect(str(db_path))
        try:
            return conn.execute(sql, params).fetchall()
        finally:
            conn.close()

    def test_output_files(self) -> None:
        self.assertTrue((self.out_dir / "imdb-enrichment.db").exists())
        self.assertTrue((self.out_dir / "imdb-enrichment.db.gz").exists())
        self.assertTrue((self.out_dir / "imdb-enrichment.meta.json").exists())
        self.assertFalse((self.out_dir / "imdb-enrichment.json.gz").exists())

    def test_db_integrity_and_row_count_matches_meta(self) -> None:
        db_path = self.out_dir / "imdb-enrichment.db"
        self.assertEqual(self._query(db_path, "PRAGMA integrity_check"), [("ok",)])
        meta = json.loads((self.out_dir / "imdb-enrichment.meta.json").read_text(encoding="utf-8"))
        (row_count,) = self._query(db_path, "SELECT COUNT(*) FROM enrich")[0]
        self.assertEqual(row_count, meta["keys_total"])
        self.assertEqual(row_count, 24)

    def test_db_row_values_match_enrichment(self) -> None:
        rows = self._query(
            self.out_dir / "imdb-enrichment.db",
            "SELECT rating, numvotes, genres FROM enrich WHERE key = ?",
            ("frozen harbor|2019",),
        )
        self.assertEqual(rows, [(7.5, 3000, "Drama,Thriller")])

    def test_gzipped_db_decompresses_to_identical_db(self) -> None:
        with gzip.open(self.out_dir / "imdb-enrichment.db.gz", "rb") as f:
            unpacked = f.read()
        self.assertEqual(unpacked, (self.out_dir / "imdb-enrichment.db").read_bytes())


class OpenSourceFixturesTests(unittest.TestCase):
    def test_open_source_reads_plain_tsv_from_fixtures_dir(self) -> None:
        stream = open_source("title.ratings", "https://example.invalid/unused", str(FIXTURES_DIR))
        try:
            self.assertIsInstance(stream, io.TextIOBase)
            ratings = load_ratings(stream)
            self.assertEqual(ratings["tt0000001"], (8.2, 50000))
        finally:
            stream.close()


if __name__ == "__main__":
    unittest.main()
