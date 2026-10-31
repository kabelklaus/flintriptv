"""Validate imdb-enrichment.db (or .db.gz) hit rate against a real Xtream catalog export."""

from __future__ import annotations

import argparse
import atexit
import gzip
import json
import os
import shutil
import sqlite3
import tempfile

from normalize import make_key, normalize_title

MOVIE_WARN_THRESHOLD = 60.0
SERIES_WARN_THRESHOLD = 75.0
SAMPLE_SIZE = 5


class SqliteEnrichment:
    """Read-only view on the `enrich` table with the `in` / `[key]` access the lookup chain uses.
    Values come back as [rating, numvotes, genres], the same shape the old JSON map had."""

    def __init__(self, conn: sqlite3.Connection):
        self._conn = conn

    def get(self, key: str):
        row = self._conn.execute(
            "SELECT rating, numvotes, genres FROM enrich WHERE key = ?", (key,)
        ).fetchone()
        return None if row is None else [row[0], row[1], row[2]]

    def __contains__(self, key: str) -> bool:
        return self.get(key) is not None

    def __getitem__(self, key: str):
        value = self.get(key)
        if value is None:
            raise KeyError(key)
        return value

    def __len__(self) -> int:
        return self._conn.execute("SELECT COUNT(*) FROM enrich").fetchone()[0]


def load_enrichment(path: str) -> SqliteEnrichment:
    """Open the enrichment DB; a .gz path is decompressed into a temp file first."""
    if path.endswith(".gz"):
        fd, tmp_path = tempfile.mkstemp(suffix=".db")
        with os.fdopen(fd, "wb") as dst, gzip.open(path, "rb") as src:
            shutil.copyfileobj(src, dst)
        atexit.register(os.remove, tmp_path)
        path = tmp_path
    return SqliteEnrichment(sqlite3.connect(path))


def load_catalog(path: str) -> list:
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def _key_candidates(clean_title: str, year: int | None) -> list[str]:
    """Key variants tried for one title-length variant, in lookup-chain order.
    With a year: exact year, then year-1, year+1, then the bare key. Without a
    year, only the bare key applies."""
    if year is None:
        return [make_key(clean_title, None)]
    return [
        make_key(clean_title, year),
        make_key(clean_title, year - 1),
        make_key(clean_title, year + 1),
        make_key(clean_title, None),
    ]


def match_item(name: str, enrichment: dict) -> tuple[str, str | None]:
    """Run the app's lookup chain for one catalog item; returns (status, matched_key).

    Tries title-length variants from full length down to a single word (stripping
    trailing words from the right one at a time). For each length variant, tries
    the key variants from `_key_candidates` in order. First hit wins:
    - "exact": full-length variant, exact-year key.
    - "year-shift": full-length variant, matched via year-1/year+1/bare key.
    - "shortened": any shortened (non-full-length) variant, any key variant.
    """
    clean, year = normalize_title(name)
    if not clean:
        return "unmatched", None

    words = clean.split()
    for position in range(len(words)):
        variant = " ".join(words[: len(words) - position])
        is_full_length = position == 0
        for key_index, key in enumerate(_key_candidates(variant, year)):
            if key in enrichment:
                if is_full_length:
                    return ("exact" if key_index == 0 else "year-shift"), key
                return "shortened", key

    return "unmatched", None


def classify_catalog(items: list, name_field: str, enrichment: dict) -> dict:
    exact_samples = []
    year_shift_samples = []
    shortened_samples = []
    unmatched_samples = []
    exact = year_shift = shortened = unmatched = 0

    for item in items:
        name = item.get(name_field, "") or ""
        status, key = match_item(name, enrichment)
        if status == "exact":
            exact += 1
            exact_samples.append((name, key))
        elif status == "year-shift":
            year_shift += 1
            year_shift_samples.append((name, key))
        elif status == "shortened":
            shortened += 1
            shortened_samples.append((name, key))
        else:
            unmatched += 1
            unmatched_samples.append(name)

    return {
        "total": len(items),
        "exact": exact,
        "year_shift": year_shift,
        "shortened": shortened,
        "unmatched": unmatched,
        "exact_samples": exact_samples,
        "year_shift_samples": year_shift_samples,
        "shortened_samples": shortened_samples,
        "unmatched_samples": unmatched_samples,
    }


def format_report(label: str, stats: dict, warn_threshold: float) -> list[str]:
    total = stats["total"]
    lines = [f"== {label} =="]
    if total == 0:
        lines.append("  (keine Items)")
        return lines

    exact = stats["exact"]
    year_shift = stats["year_shift"]
    shortened = stats["shortened"]
    unmatched = stats["unmatched"]
    total_quote = 100.0 * (exact + year_shift + shortened) / total

    lines.append(f"  total:                 {total}")
    lines.append(f"  matched (exact):       {exact} ({100.0 * exact / total:.1f}%)")
    lines.append(f"  matched (year-shift):  {year_shift} ({100.0 * year_shift / total:.1f}%)")
    lines.append(f"  matched (shortened):   {shortened} ({100.0 * shortened / total:.1f}%)")
    lines.append(f"  unmatched:             {unmatched} ({100.0 * unmatched / total:.1f}%)")
    lines.append(f"  TOTAL QUOTE:           {total_quote:.1f}%")
    if total_quote < warn_threshold:
        lines.append(f"  WARNUNG: {label}-Quote liegt unter {warn_threshold:.0f}%!")
    return lines


def format_samples(enrichment: dict, matched_samples: list, unmatched_samples: list) -> list[str]:
    lines = ["== Stichproben: matched =="]
    for name, key in matched_samples[:SAMPLE_SIZE]:
        rating = enrichment[key][0]
        lines.append(f"  {name!r} -> {key!r} (rating={rating})")

    lines.append("== Stichproben: unmatched ==")
    for name in unmatched_samples[:SAMPLE_SIZE]:
        lines.append(f"  {name!r}")
    return lines


def build_report(enrichment: dict, movies: list, series: list) -> str:
    movie_stats = classify_catalog(movies, "name", enrichment)
    series_stats = classify_catalog(series, "name", enrichment)

    lines = []
    lines.extend(format_report("Movies", movie_stats, MOVIE_WARN_THRESHOLD))
    lines.append("")
    lines.extend(format_report("Series", series_stats, SERIES_WARN_THRESHOLD))
    lines.append("")

    matched_samples = (
        movie_stats["exact_samples"]
        + movie_stats["year_shift_samples"]
        + movie_stats["shortened_samples"]
        + series_stats["exact_samples"]
        + series_stats["year_shift_samples"]
        + series_stats["shortened_samples"]
    )
    unmatched_samples = movie_stats["unmatched_samples"] + series_stats["unmatched_samples"]
    lines.extend(format_samples(enrichment, matched_samples, unmatched_samples))

    return "\n".join(lines)


def main(argv=None) -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--enrichment", required=True)
    parser.add_argument("--movies", required=True)
    parser.add_argument("--series", required=True)
    args = parser.parse_args(argv)

    enrichment = load_enrichment(args.enrichment)
    movies = load_catalog(args.movies)
    series = load_catalog(args.series)

    print(build_report(enrichment, movies, series))


if __name__ == "__main__":
    main()
