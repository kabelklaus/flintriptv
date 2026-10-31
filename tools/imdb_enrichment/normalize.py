"""Title normalization and IMDb lookup-key generation for the enrichment pipeline."""

from __future__ import annotations

import re
import unicodedata

_BRACKET_TAGS = re.compile(r"\[[^\]]*\]")
_LANG_PREFIX = re.compile(r"^[A-Z0-9]{2,4}\s*-\s*")
_YEAR_GROUP = re.compile(r"\(((?:19|20)\d{2})\)")
_PAREN_GROUP = re.compile(r"\([^)]*\)")
_WHITESPACE = re.compile(r"\s+")


def normalize_title(name: str) -> tuple[str, int | None]:
    """Strip scene tags, language prefix and parenthetical tags; extract the release year."""
    if not name or not name.strip():
        return "", None

    text = _BRACKET_TAGS.sub("", name)
    text = text.strip()
    text = _LANG_PREFIX.sub("", text)

    year_match = _YEAR_GROUP.search(text)
    year = int(year_match.group(1)) if year_match else None
    text = _PAREN_GROUP.sub("", text)

    text = unicodedata.normalize("NFD", text)
    text = "".join(c for c in text if not unicodedata.combining(c))

    text = text.lower()
    text = "".join(" " if unicodedata.category(c).startswith("P") else c for c in text)
    text = _WHITESPACE.sub(" ", text).strip()

    return text, year


def make_key(clean_title: str, year: int | None) -> str:
    """Build the lookup key used to match normalized titles against the IMDb dataset."""
    if year is None:
        return f"{clean_title}|"
    return f"{clean_title}|{year}"
