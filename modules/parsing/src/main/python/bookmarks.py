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
Shared helpers for a document's outline records. A record is
``{"title", "level"|None, "page"|None, "verified"?}``, produced either from a PDF's embedded
bookmarks (see :mod:`pdf_bookmarks`) or, when there are none, from the printed table of
contents. Records live in memory for the whole run; the copy in ``Chunks/bookmarks.json`` is
written for inspection only. This module carries the dependency-free pieces (title
normalization, page-region lookup, verification) so both the pypdf-backed extractor and the
chunker can use them.

Verification (:func:`verify_bookmarks`) matches a record's title against the page it claims,
using the ``<!-- page: N -->`` markers the PDF parser emits, and corrects an off-by-one page
pointer (bookmarks often point one page early, to the bottom of the previous page). A record
is only ever marked ``"verified": False`` -- when its title cannot be located on its page or
either neighbour; a located/corrected record carries no ``verified`` key.

Resolving records to body lines goes through a :class:`LineIndex` built once per document by
:func:`build_line_index`: :func:`resolve_record_line` then costs only as much as the number
of lines sharing the record's title, instead of re-scanning every line of the document for
every record.
"""

from __future__ import annotations

import re
from typing import NamedTuple

from markdown_markers import PAGE_MARKER, PAGE_MARKER_LINE

# Name of the file the resolved outline records are written to, inside ``Chunks/``. Written for
# inspecting a CLI run and read back by nothing; see ``chunker.write_chunk_files``.
BOOKMARKS_NAME = "bookmarks.json"

_NON_ALNUM = re.compile(r"[^a-z0-9]+")


def normalize_title(text: str) -> str:
    """A comparison key for a heading/title: casefolded, alphanumerics only. So ``"## 1.0
    Background:"`` and a bookmark ``"1.0 Background"`` both key to ``"10background"``."""
    return _NON_ALNUM.sub("", text.casefold())


def line_pages(markdown: str, lines: list[str] | None = None) -> list[tuple[int, int, str]]:
    """``(line_index, page, key)`` for every non-marker line with a non-empty normalized
    key, where ``page`` is the 1-based page from the preceding ``<!-- page: N -->`` marker
    (0 before the first). The single scan every other lookup in this module is built on.

    @param markdown: the assembled Markdown document
    @param lines: ``markdown`` already split on newlines, when the caller has it split
        already (avoids re-splitting a large document)
    @return: one tuple per keyed line, in document order
    """
    positions: list[tuple[int, int, str]] = []
    current = 0
    for index, line in enumerate(lines if lines is not None else markdown.split("\n")):
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
    """Map each page number to normalized line keys from :func:`line_pages` output."""
    pages: dict[int, set[str]] = {}
    for _index, page, key in positions:
        pages.setdefault(page, set()).add(key)
    return pages


def page_line_texts(markdown: str, lines: list[str] | None = None) -> dict[int, set[str]]:
    """Map each 1-based page number to the set of normalized full-line keys on that page,
    per the ``<!-- page: N -->`` markers. Lines before the first marker (the source header)
    are page 0; an unpaged document (DOCX) yields only page 0.

    @param markdown: the assembled Markdown document
    @param lines: ``markdown`` already split on newlines, when available
    @return: page number -> set of normalized line keys
    """
    return pages_from_positions(line_pages(markdown, lines))


class LineIndex(NamedTuple):
    """A document's keyed body lines, arranged for repeated record lookup.

    @param by_key: normalized title -> the ``(line_index, page)`` pairs carrying it
    @param has_pages: whether the document carries any real page markers (``False`` for
        DOCX and anything else unpaged, where a record's claimed page means nothing)
    """

    by_key: dict[str, list[tuple[int, int]]]
    has_pages: bool


def build_line_index(positions: list[tuple[int, int, str]]) -> LineIndex:
    """Group :func:`line_pages` output by normalized title.

    Build this once per document and hand it to :func:`resolve_record_line` for every
    record; resolving then touches only the lines that share the record's title.

    @param positions: output of :func:`line_pages`
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
    *,
    lines: list[str] | None = None,
    positions: list[tuple[int, int, str]] | None = None,
) -> list[dict]:
    """Check each record's page against ``markdown`` and fix off-by-one pages.

    Looks for the title on ``page``, ``page-1``, or ``page+1``. Rewrites ``page`` number when
    found nearby; sets ``"verified": False`` when not found (never sets ``True``).
    Skips records with no page/title. Unpaged docs (no ``<!-- page: N -->``) are unchanged.
    Always returns new dicts.

    @param records: outline records (``title``, ``level``, ``page``)
    @param markdown: Markdown with page markers
    @param lines: pre-split lines, when available
    @param positions: precomputed :func:`line_pages` output, when available
    @return: corrected records
    """
    # Checked before page_line_texts, which walks and normalizes every line of the document to
    # build a map that an unpaged document then discards unused. A necessary condition only:
    # line_pages matches the marker anchored to its own line, so a hit here still has to be
    # confirmed below — but a miss is conclusive, and it is the DOCX case every time.
    if not PAGE_MARKER.search(markdown):
        return [dict(record) for record in records]

    if positions is None:
        positions = line_pages(markdown, lines)
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
