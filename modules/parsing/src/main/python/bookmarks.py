# Copyright 2026 DATA @ UHN. See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

"""
Outline-record helpers shared by the PDF bookmark extractor and the chunker.

A record is ``{"title", "level"|None, "page"|None, "verified"?}``
Sources: embedded PDF bookmarks (:mod:`pdf_bookmarks`) or, if none, the printed TOC.
Kept in memory for the run only — nothing persists the records to disk.

:func:`verify_bookmarks` checks each title against ``<!-- page: N -->`` regions and fixes common
off-by-one page pointers. Failure sets ``"verified": False``; success omits the key.

:func:`build_line_index` / :func:`resolve_record_line` map records to body lines
without re-scanning the whole document per record.
"""

from __future__ import annotations

import re
from typing import NamedTuple

from markdown_markers import PAGE_MARKER, PAGE_MARKER_LINE

BOOKMARKS_NAME = "bookmarks.json"

_NON_ALNUM = re.compile(r"[^a-z0-9]+")


def normalize_title(text: str) -> str:
    """A comparison key for a heading/title: casefolded, alphanumerics only. So ``"## 1.0
    Background:"`` and a bookmark ``"1.0 Background"`` both key to ``"10background"``."""
    return _NON_ALNUM.sub("", text.casefold())


def build_lines_catalog(lines: list[str]) -> list[tuple[int, int, str]]:
    """Scan the document once: each content line → ``(line_index, page, normalized_heading_key)``.

    ``page`` is the 1-based page from the preceding ``<!-- page: N -->`` marker
    (0 before the first). Marker lines and empty/keyless lines are skipped. Every other
    lookup in this module is built on this scan.

    Answers the question for each real content line, which PDF page it belongs to (from those page markers).
    positions list is later used to verify bookmarks and find headings without re-scanning the whole file.

    @param lines: document already split on newlines
    @return: one tuple per keyed line, in document order
    """
    positions: list[tuple[int, int, str]] = []
    current = 0
    for index, line in enumerate(lines):
        marker = PAGE_MARKER_LINE.match(line)
        if marker:
            current = int(marker.group(1))
            continue
        key = normalize_title(line)
        if key:
            positions.append((index, current, key))
    return positions


def pages_from_positions(
    positions: list[tuple[int, int, str]],
) -> dict[int, set[str]]:
    """Map each page number to normalized line keys from :func:`build_lines_catalog` output."""
    pages: dict[int, set[str]] = {}
    for _index, page, key in positions:
        pages.setdefault(page, set()).add(key)
    return pages


def page_line_texts(markdown: str) -> dict[int, set[str]]:
    """Map each 1-based page number to the set of normalized full-line keys on that page,
    per the ``<!-- page: N -->`` markers. Lines before the first marker (the source header)
    are page 0; an unpaged document (DOCX) yields only page 0.

    @param markdown: the assembled Markdown document
    @return: page number -> set of normalized line keys
    """
    return pages_from_positions(build_lines_catalog(markdown.split("\n")))


class LineIndex(NamedTuple):
    """A document's keyed body lines, arranged for repeated record lookup.

    @param by_key: normalized title -> the ``(line_index, page)`` pairs carrying it
    @param has_pages: whether the document carries any real page markers (``False`` for
        DOCX and anything else unpaged, where a record's claimed page means nothing)
    """

    by_key: dict[str, list[tuple[int, int]]]
    has_pages: bool


def build_line_index(positions: list[tuple[int, int, str]]) -> LineIndex:
    """Group :func:`build_lines_catalog` output by normalized title.

    Build this once per document and hand it to :func:`resolve_record_line` for every
    record; resolving then touches only the lines that share the record's title.

    @param positions: output of :func:`build_lines_catalog`
    @return: the index
    """
    by_key: dict[str, list[tuple[int, int]]] = {}
    has_pages = False
    for index, page, key in positions:
        by_key.setdefault(key, []).append((index, page))
        if page > 0:
            has_pages = True
    return LineIndex(by_key=by_key, has_pages=has_pages)


def _locate_page(pages: dict[int, set[str]], key: str, claimed: int) -> int | None:
    """The page on which ``key`` appears as a full line: the claimed page first, then its
    previous and next neighbour. ``None`` when it appears on none of the three."""
    for candidate in (claimed, claimed - 1, claimed + 1):
        if key in pages.get(candidate, ()):
            return candidate
    return None


def verify_bookmarks(
    records: list[dict],
    markdown: str,
    positions: list[tuple[int, int, str]],
) -> list[dict]:
    """Check each record's page against ``markdown`` and fix off-by-one pages.

    Looks for the title on ``page``, ``page-1``, or ``page+1``. Rewrites ``page`` number when
    found nearby; sets ``"verified": False`` when not found (never sets ``True``).
    Skips records with no page/title. Unpaged docs (no ``<!-- page: N -->``) are unchanged.
    Always returns new dicts.

    @param records: outline records (``title``, ``level``, ``page``)
    @param markdown: Markdown with page markers
    @param positions: precomputed :func:`build_lines_catalog` for ``markdown``
    @return: corrected records
    """
    # Cheap necessary condition: an unpaged document (DOCX) never needs the page map.
    # build_lines_catalog matches the marker anchored to its own line, so a hit here still has
    # to be confirmed below — but a miss is conclusive.
    if not PAGE_MARKER.search(markdown):
        return [dict(record) for record in records]

    pages = pages_from_positions(positions)
    if not any(page_no > 0 for page_no in pages):
        # Markers exist, but none on a line of its own, so no real page was ever opened.
        return [dict(record) for record in records]

    verified: list[dict] = []
    for record in records:
        result = dict(record)
        page = result.get("page")
        key = normalize_title(result.get("title") or "")
        if isinstance(page, int) and key:
            located = _locate_page(pages, key, page)
            if located is None:
                result["verified"] = False
            elif located != page:
                result["page"] = located
        verified.append(result)
    return verified


def resolve_record_line(
    index: LineIndex, record: dict, *, exclude=frozenset()
) -> int | None:
    """Body line for a record's title, or ``None`` if missing or ambiguous.

    With a trusted page (paged doc, integer ``page``, not ``verified: False``), only that
    page is considered; otherwise any page. Zero or multiple matches among non-``exclude``
    lines return ``None`` (fail-open).

    @param index: :func:`build_line_index` for the document
    @param record: outline record (``title``, ``page``, optional ``verified``)
    @param exclude: ineligible line indices (e.g. ATX headings, TOC range)
    @return: unique matching line index, or ``None``
    """
    key = normalize_title(record.get("title") or "")
    if not key:
        return None
    candidates = index.by_key.get(key)
    if not candidates:
        return None
    page = record.get("page")
    trust_page = index.has_pages and isinstance(page, int) and record.get("verified") is not False
    found: int | None = None
    for line_index, page_no in candidates:
        if line_index in exclude or (trust_page and page_no != page):
            continue
        if found is not None:
            # Several eligible lines carry this title -- fail open rather than guess.
            return None
        found = line_index
    return found
