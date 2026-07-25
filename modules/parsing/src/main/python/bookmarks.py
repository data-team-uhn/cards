#
#  Licensed to the Apache Software Foundation (ASF) under one
#  or more contributor license agreements.  See the NOTICE file
#  distributed with this work for additional information
#  regarding copyright ownership.  The ASF licenses this file
#  to you under the Apache License, Version 2.0 (the
#  "License"); you may not use this file except in compliance
#  with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing,
#  software distributed under the License is distributed on an
#  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
#  KIND, either express or implied.  See the License for the
#  specific language governing permissions and limitations
#  under the License.
#

"""
Shared helpers for the document-outline records stored in ``bookmarks.json`` beside a
parsed ``.md``. A record is ``{"title", "level"|None, "page"|None, "verified"?}``, produced
either from a PDF's embedded bookmarks (see :mod:`pdf_bookmarks`) or, when there are none,
from the printed table of contents. This module carries only the dependency-free pieces
(title normalization, page-region lookup, verification, sidecar IO) so both the pypdf-backed
extractor and the chunker can use them.

Verification (:func:`verify_bookmarks`) matches a record's title against the page it claims,
using the ``<!-- page: N -->`` markers the PDF parser emits, and corrects an off-by-one page
pointer (bookmarks often point one page early, to the bottom of the previous page). A record
is only ever marked ``"verified": False`` -- when its title cannot be located on its page or
either neighbour; a located/corrected record carries no ``verified`` key.
"""

from __future__ import annotations

import json
import re
from pathlib import Path

# Name of the outline sidecar written beside a document's ``.md``.
BOOKMARKS_NAME = "bookmarks.json"

# A ``<!-- page: N -->`` marker alone on its line (lenient on inner spacing).
_PAGE_MARKER_LINE = re.compile(r"^\s*<!--\s*page:\s*(\d+)\s*-->\s*$", re.IGNORECASE)

_NON_ALNUM = re.compile(r"[^a-z0-9]+")


def normalize_title(text: str) -> str:
    """A comparison key for a heading/title: casefolded, alphanumerics only. So ``"## 1.0
    Background:"`` and a bookmark ``"1.0 Background"`` both key to ``"10background"``."""
    return _NON_ALNUM.sub("", text.casefold())


def page_line_texts(markdown: str) -> dict[int, set[str]]:
    """Map each 1-based page number to the set of normalized full-line keys on that page,
    per the ``<!-- page: N -->`` markers. Lines before the first marker (the source header)
    are page 0; an unpaged document (DOCX) yields only page 0."""
    pages: dict[int, set[str]] = {}
    current = 0
    for line in markdown.split("\n"):
        marker = _PAGE_MARKER_LINE.match(line)
        if marker:
            current = int(marker.group(1))
            continue
        key = normalize_title(line)
        if key:
            pages.setdefault(current, set()).add(key)
    return pages


def _locate_page(pages: dict[int, set[str]], key: str, claimed: int) -> int | None:
    """The page on which ``key`` appears as a full line: the claimed page first, then its
    previous and next neighbour. ``None`` when it appears on none of the three."""
    for candidate in (claimed, claimed - 1, claimed + 1):
        if key in pages.get(candidate, ()):
            return candidate
    return None


def verify_bookmarks(records: list[dict], markdown: str) -> list[dict]:
    """Verify each record's page against ``markdown`` and correct off-by-one pointers.

    For a record with an integer ``page``: if its title is found on that page, it is left
    unchanged; if found on page-1 or page+1, its ``page`` is rewritten to where it was
    found; if found on none of the three, ``"verified": False`` is set (page left as-is).
    Records without a page, or with an empty title key, are returned unchanged. Never sets
    ``"verified": True``.

    @param records: outline records (each ``{"title", "level"|None, "page"|None}``)
    @param markdown: the assembled Markdown, carrying ``<!-- page: N -->`` markers
    @return: a new list of records with pages corrected and non-locatable ones flagged
    """
    pages = page_line_texts(markdown)
    has_pages = any(page_no > 0 for page_no in pages)
    verified: list[dict] = []
    for record in records:
        result = dict(record)
        page = result.get("page")
        key = normalize_title(result.get("title") or "")
        if has_pages and isinstance(page, int) and key:
            located = _locate_page(pages, key, page)
            if located is None:
                result["verified"] = False
            elif located != page:
                result["page"] = located
        verified.append(result)
    return verified


def read_bookmarks(path: Path | None) -> list[dict]:
    """Read a ``bookmarks.json`` sidecar, or ``[]`` when it is missing or unreadable."""
    if path is None or not path.is_file():
        return []
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return []
    return data if isinstance(data, list) else []


def write_bookmarks(path: Path, records: list[dict]) -> None:
    """Write outline ``records`` to a ``bookmarks.json`` sidecar."""
    path.write_text(json.dumps(records, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def line_pages(markdown: str) -> list[tuple[int, int, str]]:
    """``(line_index, page, key)`` for every non-marker line with a non-empty normalized
    key, where ``page`` is the 1-based page from the preceding ``<!-- page: N -->`` marker
    (0 before the first). Used to resolve an outline record to a body line."""
    positions: list[tuple[int, int, str]] = []
    current = 0
    for index, line in enumerate(markdown.split("\n")):
        marker = _PAGE_MARKER_LINE.match(line)
        if marker:
            current = int(marker.group(1))
            continue
        key = normalize_title(line)
        if key:
            positions.append((index, current, key))
    return positions


def resolve_record_line(
    positions: list[tuple[int, int, str]], record: dict, *, exclude=frozenset()
) -> int | None:
    """The body line index a record's title maps to, or ``None`` when it is absent or
    ambiguous. When the document is paged and the record's ``page`` is trusted (an integer,
    not explicitly ``verified: False``), only lines on exactly that page match; otherwise any
    page. The match must be unique among non-``exclude`` lines -- zero or several yield
    ``None`` (fail-open, never resolve to the wrong line).

    @param positions: output of :func:`line_pages` for the document
    @param record: an outline record (``{"title", "page"|None, "verified"?}``)
    @param exclude: line indices that are not eligible (e.g. ATX headings, the TOC range)
    @return: the unique matching line index, or ``None``
    """
    key = normalize_title(record.get("title") or "")
    if not key:
        return None
    page = record.get("page")
    has_pages = any(page_no > 0 for _, page_no, _ in positions)
    trust_page = has_pages and isinstance(page, int) and record.get("verified") is not False
    matches = [
        index
        for index, page_no, line_key in positions
        if line_key == key and index not in exclude and (page_no == page if trust_page else True)
    ]
    return matches[0] if len(matches) == 1 else None
