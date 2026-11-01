"""Build the IMDb enrichment SQLite DB (imdb-enrichment.db + .db.gz) from the official title.basics/title.ratings dumps."""

from __future__ import annotations

import argparse
import csv
import gzip
import io
import json
import shutil
import sqlite3
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

from normalize import make_key, normalize_title

RATINGS_URL = "https://datasets.imdbws.com/title.ratings.tsv.gz"
BASICS_URL = "https://datasets.imdbws.com/title.basics.tsv.gz"
AKAS_URL = "https://datasets.imdbws.com/title.akas.tsv.gz"
TITLE_TYPES = {"movie", "tvMovie", "tvSeries", "tvMiniSeries", "tvSpecial", "video"}


def open_source(stem: str, url: str, fixtures_dir: str | None):
    """Return a text-mode line stream for a dump, reading from fixtures or streaming the gzip download."""
    if fixtures_dir:
        gz_path = Path(fixtures_dir) / f"{stem}.tsv.gz"
        if gz_path.exists():
            return io.TextIOWrapper(gzip.GzipFile(fileobj=open(gz_path, "rb")), encoding="utf-8", newline="")
        tsv_path = Path(fixtures_dir) / f"{stem}.tsv"
        return open(tsv_path, "r", encoding="utf-8", newline="")
    response = urllib.request.urlopen(url)
    return io.TextIOWrapper(gzip.GzipFile(fileobj=response), encoding="utf-8", newline="")


def iter_tsv_rows(stream):
    reader = csv.reader(stream, delimiter="\t", quoting=csv.QUOTE_NONE)
    header = next(reader)
    for row in reader:
        yield dict(zip(header, row))


def load_ratings(stream) -> dict[str, tuple[float, int]]:
    """tconst -> (averageRating, numVotes)."""
    ratings: dict[str, tuple[float, int]] = {}
    for row in iter_tsv_rows(stream):
        ratings[row["tconst"]] = (float(row["averageRating"]), int(row["numVotes"]))
    return ratings


def process_basics(stream, ratings: dict[str, tuple[float, int]], min_votes: int):
    """Stream title.basics once: filter rows and index primary/original titles.

    Returns (entries, entries_by_clean, enrichment, counts). `entries` maps
    tconst -> {"value": value, "start_year": start_year} for every filtered row,
    so a later akas pass can look up whether a titleId survived filtering and
    which year to key its localized titles under. `entries_by_clean` stays open
    for that akas pass to add more clean_titles before the bare-key pass runs.
    """
    entries: dict[str, dict] = {}
    entries_by_clean: dict[str, dict[str, list]] = {}
    enrichment: dict[str, list] = {}

    rows_in = 0
    rows_out = 0

    for row in iter_tsv_rows(stream):
        rows_in += 1

        if row["isAdult"] == "1":
            continue
        if row["titleType"] not in TITLE_TYPES:
            continue

        rating_info = ratings.get(row["tconst"])
        if rating_info is None:
            continue
        avg_rating, num_votes = rating_info
        if num_votes < min_votes:
            continue

        rows_out += 1

        start_year = None if row["startYear"] == "\\N" else int(row["startYear"])
        genres = [] if row["genres"] == "\\N" else row["genres"].split(",")
        value = [avg_rating, num_votes, ",".join(genres)]
        entries[row["tconst"]] = {"value": value, "start_year": start_year}

        primary_clean, _ = normalize_title(row["primaryTitle"])
        original_clean, _ = normalize_title(row["originalTitle"])

        for clean in {primary_clean, original_clean}:
            if not clean:
                continue
            if start_year is not None:
                enrichment[make_key(clean, start_year)] = value
            entries_by_clean.setdefault(clean, {})[row["tconst"]] = value

    counts = {"rows_in": rows_in, "rows_out": rows_out}
    return entries, entries_by_clean, enrichment, counts


def process_akas(stream, entries: dict[str, dict], enrichment: dict, entries_by_clean: dict) -> int:
    """Stream title.akas once: index localized/alternative titles for already-filtered entries.

    An akas row is "accepted" when its titleId is one of the filtered `entries`
    (title/adult/votes filters already happened in process_basics; akas rows for
    anything else are skipped and don't count). Akas titles carry no year of their
    own, so the key always uses the entry's startYear, not a year parsed out of the
    akas title text. First write wins: an akas title never overwrites a key already
    set by a primary/original title or an earlier akas row, so results stay
    deterministic regardless of akas row order. The bare-key pass runs once, after
    both this and process_basics have contributed to entries_by_clean, so it still
    counts entries (not titles) per clean_title.
    """
    aka_titles_indexed = 0
    for row in iter_tsv_rows(stream):
        tconst = row["titleId"]
        entry = entries.get(tconst)
        if entry is None:
            continue
        aka_titles_indexed += 1

        clean, _ = normalize_title(row["title"])
        if not clean:
            continue

        value = entry["value"]
        start_year = entry["start_year"]
        if start_year is not None:
            key = make_key(clean, start_year)
            if key not in enrichment:
                enrichment[key] = value
        entries_by_clean.setdefault(clean, {})[tconst] = value

    return aka_titles_indexed


def apply_bare_keys(entries_by_clean: dict, enrichment: dict) -> None:
    """A clean_title gets a year-less key only when exactly one IMDb entry (tconst) uses it."""
    for clean, tconst_map in entries_by_clean.items():
        if len(tconst_map) != 1:
            continue
        (value,) = tconst_map.values()
        enrichment[make_key(clean, None)] = value


def count_keys(enrichment: dict) -> dict:
    keys_with_year = sum(1 for key in enrichment if not key.endswith("|"))
    return {
        "keys_total": len(enrichment),
        "keys_with_year": keys_with_year,
        "keys_without_year": len(enrichment) - keys_with_year,
    }


def write_db(db_path: Path, enrichment: dict) -> None:
    """Write the key -> (rating, numvotes, genres) map as a SQLite table the app queries by primary key."""
    if db_path.exists():
        db_path.unlink()
    conn = sqlite3.connect(str(db_path))
    try:
        conn.execute(
            "CREATE TABLE enrich(key TEXT PRIMARY KEY, rating REAL, numvotes INTEGER, genres TEXT) WITHOUT ROWID"
        )
        conn.executemany(
            "INSERT INTO enrich(key, rating, numvotes, genres) VALUES (?, ?, ?, ?)",
            ((key, value[0], value[1], value[2]) for key, value in sorted(enrichment.items())),
        )
        conn.commit()
        conn.execute("VACUUM")
    finally:
        conn.close()


def write_outputs(out_dir: str, enrichment: dict, meta: dict) -> None:
    out_path = Path(out_dir)
    out_path.mkdir(parents=True, exist_ok=True)
    db_path = out_path / "imdb-enrichment.db"
    write_db(db_path, enrichment)
    with open(db_path, "rb") as src, gzip.open(out_path / "imdb-enrichment.db.gz", "wb") as dst:
        shutil.copyfileobj(src, dst)
    with open(out_path / "imdb-enrichment.meta.json", "w", encoding="utf-8") as f:
        json.dump(meta, f, indent=2)


def main(argv=None) -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", required=True)
    parser.add_argument("--min-votes", type=int, default=500)
    parser.add_argument("--basics", default=BASICS_URL)
    parser.add_argument("--ratings", default=RATINGS_URL)
    parser.add_argument("--akas", default=AKAS_URL)
    parser.add_argument("--fixtures", default=None)
    args = parser.parse_args(argv)

    ratings_stream = open_source("title.ratings", args.ratings, args.fixtures)
    try:
        ratings = load_ratings(ratings_stream)
    finally:
        ratings_stream.close()

    basics_stream = open_source("title.basics", args.basics, args.fixtures)
    try:
        entries, entries_by_clean, enrichment, counts = process_basics(basics_stream, ratings, args.min_votes)
    finally:
        basics_stream.close()

    akas_stream = open_source("title.akas", args.akas, args.fixtures)
    try:
        aka_titles_indexed = process_akas(akas_stream, entries, enrichment, entries_by_clean)
    finally:
        akas_stream.close()

    apply_bare_keys(entries_by_clean, enrichment)
    key_counts = count_keys(enrichment)

    meta = {
        "built_at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "min_votes": args.min_votes,
        "sources": ["title.basics", "title.ratings", "title.akas"],
        "aka_titles_indexed": aka_titles_indexed,
        **counts,
        **key_counts,
    }
    write_outputs(args.out, enrichment, meta)
    print(
        f"wrote {key_counts['keys_total']} keys ({counts['rows_out']}/{counts['rows_in']} rows, "
        f"{aka_titles_indexed} akas) to {args.out}"
    )


if __name__ == "__main__":
    main()
