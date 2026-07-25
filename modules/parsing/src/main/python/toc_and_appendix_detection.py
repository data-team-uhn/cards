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
TOC detection: find a "table of contents" / "contents" label, flatten table-shaped
TOCs, clean leaders/tabs, and record the cleaned block's line range plus entry lines
in ``outline.json`` (``tocStartLine`` / ``tocEndLine`` / ``toc``). Entries over 10
words or with a word over 100 characters are discarded.

Appendix detection (:func:`find_toc_and_appendix`): the first Reference/Appendix section
title among the document's outline records (see :data:`REFERENCE_HEADINGS` /
:data:`APPENDIX_HEADINGS`) is resolved to its body line and recorded as ``backmatterLine``
in ``outline.json``.
"""

from __future__ import annotations

import json
import re
from pathlib import Path

from bookmarks import (
    BOOKMARKS_NAME,
    line_pages,
    read_bookmarks,
    resolve_record_line,
    verify_bookmarks,
    write_bookmarks,
)
from heading_numbering import numbering_depth, roman_numbering

# Maximum words per accepted heading, and maximum characters per word within it.
MAX_HEADING_WORDS = 10
MAX_WORD_CHARS = 100
# Minimum characters for a heading extracted from a chunk (# markers already stripped).
MIN_HEADING_CHARS = 5

# Density check for the no-table (plain-line) path: at least this many of the next
# DENSITY_WINDOW non-empty lines after the label must look like TOC entries.
MIN_DENSITY_MATCHES = 3
DENSITY_WINDOW = 8

# Tolerance, in words, for a short interstitial or leading run of non-matching text — a
# running page header, or a brief divider between multiple tables/entry-groups in one
# TOC block — before concluding the block has genuinely ended.
MAX_LEADING_WORDS_FOR_CONTINUATION = 12

# Documents shorter than this (``len(md) // 4``) skip TOC/appendix marking — Stage 0.5
# can send them whole. Overridable via :func:`find_toc_and_appendix`'s ``min_structure_tokens``.
DEFAULT_MIN_STRUCTURE_TOKENS = 20000

# Reference-section heading words/phrases recognised in outline record titles.
REFERENCE_HEADINGS = [
    "References",
    "Reference",
    "Reference List",
    "List of References",
    "Bibliography",
    "Selected Bibliography",
    "Works Cited",
    "Literature Cited",
    "Cited Literature",
    "References Cited",
    "Sources Cited",
    "Reference Materials",
    "References and Notes",
    "Notes and References",
    "References and Bibliography",
    "Bibliography and References",
]

# Appendix-section heading words/phrases recognised in outline record titles.
APPENDIX_HEADINGS = [
    "Appendix",
    "Appendices",
    "Annex",
    "Annexes",
    "Attachment",
    "Attachments",
    "Supplement",
    "Supplements",
    "Supplementary Material",
    "Supplementary Materials",
    "Supplemental Material",
    "Supplemental Materials",
    "Supporting Information",
    "Additional Materials",
]

_RULE_LINE = re.compile(r"^-{3,}$")

# An ATX heading line ("## Glossary of Abbreviations", etc.) — a real heading is always a
# hard content boundary, never tolerable "page-header noise", regardless of word count.
_HEADING_LINE = re.compile(r"^#{1,6}\s+\S")

# Leading section numbering to strip before matching a Reference/Appendix keyword, e.g.
# "18.0 ", "13 ", "10.1. ", "17  ".
_APPENDIX_NUMBERING_PREFIX = re.compile(r"^\(?\d+(?:\.\d+)*[.)]?\s+")


def _keyword_start_pattern(phrases: list[str]) -> re.Pattern:
    """A case-insensitive pattern matching any of ``phrases`` as a whole-word prefix."""
    alternation = "|".join(re.escape(phrase) for phrase in phrases)
    return re.compile(rf"^(?:{alternation})\b", re.IGNORECASE)


_REFERENCE_HEADING_START = _keyword_start_pattern(REFERENCE_HEADINGS)
_APPENDIX_HEADING_START = _keyword_start_pattern(APPENDIX_HEADINGS)

PAGE_MARKER = re.compile(r"<!--\s+page:\s+(\d+)\s+-->", re.IGNORECASE)

# A page marker alone on its own line.
_PAGE_MARKER_LINE = re.compile(rf"^{PAGE_MARKER.pattern}$", re.IGNORECASE)

# A "table of contents" / "contents" label decorated with '#' and/or '*' on either side,
# e.g. "## TABLE OF CONTENTS", "**Contents**", "### Contents:".
_TOC_LABEL_DECORATED = re.compile(
    r"^[#*]+\s*(?:table\s+of\s+contents|contents)\s*[#*:]*$",
    re.IGNORECASE,
)

# The bare phrase alone, undecorated — only accepted by toc_label_line when isolated
# by blank-line neighbors (see the module docstring's step 1).
_TOC_LABEL_BARE = re.compile(r"^table\s+of\s+contents$", re.IGNORECASE)

# A TOC entry with a page reference: title, then a tab/dash/dot-leader/multi-space
# separator, then an arabic or Roman page number. E.g. "Protocol Summary\t3",
# "1.0 General Information\t3", "Summary  -  2", "Schema.......4",
# "ABBREVIATIONS AND DEFINITIONS OF TERMS - V".
TOC_ENTRY_PATTERN = re.compile(
    r"""
    ^\s*
    (?!\|)                              # Do not allow a Markdown table row
    (?:[-*+]\s+)?                       # Optional bullet marker
    (?:\*{1,3})?                        # Optional Markdown emphasis
    (?:                                 # Optional section number
        \(?
        (?:\d+|[A-Za-z])
        (?:\.\d+)*
        [.)]?
        \s+
    )?
    .+?                                 # Section title
    (?:
        \t+                             # Tab before page number
        |
        \s+[-–—]\s+                     # Hyphen/dash separator
        |
        [.…·]{2,}\s*                    # Dot leaders
        |
        \s{2,}                          # Multiple spaces
    )
    (?:page\s*)?                        # Optional word "Page"
    (?:\d{1,4}|[ivxlcdm]{1,8})          # Arabic or Roman page number
    \s*
    \*{0,3}                             # Optional closing Markdown emphasis
    \s*$
    """,
    re.IGNORECASE | re.VERBOSE,
)

# A numbered/lettered outline entry with no page reference at all, e.g.
# "1.0 **Study Team, Disclosures, and Patient Partners**", "2.0 **Introduction**".
TOC_OUTLINE_ENTRY_PATTERN = re.compile(
    r"""
    ^\s*
    (?!\|)
    (?:[-*+]\s+)?
    \*{0,3}
    (?:
        \d+(?:\.\d+)*\.?
        |
        [A-Za-z][.)]
    )
    \s+
    \S.+?
    \*{0,3}
    \s*$
    """,
    re.IGNORECASE | re.VERBOSE,
)

def read_outline(outline_path: Path | None) -> dict:
    """Read the outline.json file."""
    if outline_path is None or not outline_path.is_file():
        return {}
    try:
        return json.loads(outline_path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return {}


def write_outline(outline_path: Path | None, updates: dict) -> None:
    """Update the outline.json file with the given updates.
    """
    if outline_path is None:
        return
    existing = read_outline(outline_path)
    existing.update(updates)
    outline_path.write_text(
        json.dumps(existing, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )


def is_toc_entry_line(line: str) -> bool:
    """Whether ``line`` looks like a TOC entry on its own: short enough to be a real
    entry rather than parsing garbage (at most :data:`MAX_HEADING_WORDS` words, no word
    over :data:`MAX_WORD_CHARS` characters), and matching one of the two entry patterns.
    """
    words = line.split()
    if not words or len(words) > MAX_HEADING_WORDS:
        return False
    if any(len(word) > MAX_WORD_CHARS for word in words):
        return False
    return TOC_ENTRY_PATTERN.match(line) is not None or TOC_OUTLINE_ENTRY_PATTERN.match(line) is not None


def _is_toc_header_line(stripped: str) -> bool:
    """Whether an already-stripped line is a decorated "table of contents"/"contents" label.
    """
    return _TOC_LABEL_DECORATED.match(stripped) is not None


def toc_label_line(lines: list[str]) -> int | None:
    """Find the *first* "table of contents" / "contents" label line: decorated (with ``#``
    and/or ``*``), or the bare phrase "table of contents" alone, isolated by blank-line
    neighbors (or start/end of document).

    Used by :func:`mark_and_cleanup_toc` to locate the TOC label before the full block
    scan (density / table-flatten) can run.
    """
    n = len(lines)
    for index, line in enumerate(lines):
        stripped = line.strip()
        if _is_toc_header_line(stripped):
            return index
        if _TOC_LABEL_BARE.match(stripped):
            prev_blank = index == 0 or lines[index - 1].strip() == ""
            next_blank = index + 1 >= n or lines[index + 1].strip() == ""
            if prev_blank and next_blank:
                return index
    return None


def _is_markdown_table_line(line: str) -> bool:
    stripped = line.strip()
    return stripped.startswith("|") and stripped.endswith("|")


def _parse_table_row(line: str) -> list[str]:
    inner = line.strip()
    if inner.startswith("|"):
        inner = inner[1:]
    if inner.endswith("|"):
        inner = inner[:-1]
    return [cell.strip() for cell in inner.split("|")]


def _is_table_separator_row(cells: list[str]) -> bool:
    return bool(cells) and all(re.fullmatch(r"[\s\-:]+", cell or "") for cell in cells)


def _row_to_line(cells: list[str]) -> str:
    """Merge one table row's cells into a single line, dropping duplicate cell content
    (comparing cell values within the row) and empty cells."""
    seen: set[str] = set()
    parts: list[str] = []
    for cell in cells:
        value = cell.strip()
        if not value or value in seen:
            continue
        seen.add(value)
        parts.append(value)
    return " ".join(parts)


def _flatten_table_block(block_lines: list[str]) -> list[str]:
    """Flatten every Markdown table row in ``block_lines`` into one merged,
    duplicate-free line per row (separator rows dropped) across pages."""
    flattened: list[str] = []
    for line in block_lines:
        if not _is_markdown_table_line(line):
            flattened.append(line)
            continue
        cells = _parse_table_row(line)
        if _is_table_separator_row(cells):
            continue
        merged = _row_to_line(cells)
        if merged:
            flattened.append(merged)
    return flattened


def _find_table_start_near(lines: list[str], label_index: int) -> int | None:
    """Whether a Markdown table starts within the 2 lines after the label."""
    for offset in (1, 2):
        index = label_index + offset
        if index >= len(lines):
            return None
        stripped = lines[index].strip()
        if stripped == "":
            continue
        return index if _is_markdown_table_line(lines[index]) else None
    return None


def _confirm_plain_toc(lines: list[str], label_index: int) -> bool:
    """Whether at least MIN_DENSITY_MATCHES of the next DENSITY_WINDOW non-empty lines after the label
    look like TOC entries."""
    checked = 0
    matches = 0
    index = label_index + 1
    n = len(lines)
    while index < n and checked < DENSITY_WINDOW:
        stripped = lines[index].strip()
        if stripped != "" and not _PAGE_MARKER_LINE.match(stripped):
            checked += 1
            if is_toc_entry_line(lines[index]):
                matches += 1
        index += 1
    return matches >= MIN_DENSITY_MATCHES


def _skip_to_next_content(lines: list[str], index: int) -> int:
    """Skip forward over blank/rule lines only (not page markers) to the first
    substantive line at or after ``index``."""
    n = len(lines)
    while index < n and (lines[index].strip() == "" or _RULE_LINE.match(lines[index].strip())):
        index += 1
    return index


def _scan_block(lines: list[str], start: int, is_of_type) -> tuple[list[str], int, str]:
    """Within one page (or the whole document, if unpaged), collect contiguous
    ``is_of_type``-matching lines from ``start``, tolerating blank lines and a short
    (<= MAX_LEADING_WORDS_FOR_CONTINUATION words) non-matching interlude as a
    separator between matches. Stops at a page marker, a real heading line, a longer
    non-matching run, or end of document.

    @param is_of_type: predicate classifying a raw line as part of the block 
        (e.g. _is_markdown_table_line or is_toc_entry_line)
    @return: (collected_lines, index, reason); reason is one of "page_marker" | "body_text" | "eof"; 
        index is the position in lines at which the block ends (exclusive) — the page marker,
        the start of the non-matching run, or len(lines)
    """
    collected: list[str] = []
    pending: list[str] = []
    pending_words = 0
    index = start
    n = len(lines)
    while index < n:
        line = lines[index]
        stripped = line.strip()
        if _PAGE_MARKER_LINE.match(stripped):
            return collected, index, "page_marker"
        if is_of_type(line):
            collected.extend(pending)
            collected.append(line)
            pending = []
            pending_words = 0
            index += 1
            continue
        if stripped == "":
            pending.append(line)
            index += 1
            continue
        if _HEADING_LINE.match(stripped):
            # A real heading is always a hard content boundary — never tolerated as
            # page-header noise, however short (e.g. "## Glossary of Abbreviations").
            return collected, index - len(pending), "body_text"
        pending_words += len(stripped.split())
        if pending_words > MAX_LEADING_WORDS_FOR_CONTINUATION:
            return collected, index - len(pending), "body_text"
        pending.append(line)
        index += 1
    return collected, index - len(pending), "eof"


def _scan_region(lines: list[str], start: int, is_of_type) -> tuple[list[str], int]:
    """Collect an ``is_of_type`` region, continuing across ``<!-- page: N-->`` boundaries as
    long as the next page's leading content resumes matching (tolerating a short run of
    stray words — page-header noise — before giving up) and no label re-appears first.
    Page markers that fall *between* continued TOC pages are kept inside the collected
    block (not dropped). A page marker with no TOC continuation after it stays outside
    the block (boundary points at that marker).
    Degrades to a single :func:`_scan_block` call when the document has no page markers
    (DOCX), since ``"page_marker"`` is then never returned — one implementation covers
    both the table path and the plain-line path, and both paged and unpaged documents.

    @return: ``(collected_lines, boundary_index)`` — ``boundary_index`` is the original
        document's line index at which the block ends (exclusive); a page marker with no
        TOC continuation, real body text, a re-appearing label, or end of document all
        stay untouched from there on
    """
    all_collected: list[str] = []
    index = start
    while True:
        block, end_index, reason = _scan_block(lines, index, is_of_type)
        all_collected.extend(block)
        if reason != "page_marker":
            return all_collected, end_index
        probe = _skip_to_next_content(lines, end_index + 1)
        if probe >= len(lines) or _is_toc_header_line(lines[probe].strip()):
            return all_collected, end_index
        next_block, next_end, next_reason = _scan_block(lines, probe, is_of_type)
        if not next_block:
            return all_collected, end_index
        # TOC continues on the next page — keep the intervening page marker inside the TOC.
        all_collected.append(lines[end_index])
        all_collected.extend(next_block)
        if next_reason != "page_marker":
            return all_collected, next_end
        # Another page boundary after next_block — keep that marker too and resume after it.
        all_collected.append(lines[next_end])
        index = next_end + 1


def _block_cleanup(text: str) -> str:
    """Whole-block cleanup: normalize long dot/dash/ellipsis-leader runs (5 or more) to
    `` - ``, remove tabs and escaped underscores (``\\_``), collapse runs of more than 2
    consecutive whitespace characters within a line to one space, and collapse blank
    lines (no double newlines)."""
    text = text.replace("\t", " ")
    text = text.replace("\\_", "")
    text = re.sub(r"\.{5,}", " - ", text)
    text = re.sub(r"-{5,}", " - ", text)
    text = re.sub(r"…{5,}", " - ", text)
    text = re.sub(r"[^\S\n]{3,}", " ", text)
    #text = re.sub(r"\n{2,}", "\n", text)
    return text


def _clean_toc_line(line: str) -> str:
    """Per-line cleanup: strip ``*`` and ``#`` (emphasis/heading markers, wherever they
    appear in the line), then strip leading/trailing whitespace."""
    return line.replace("*", "").replace("#", "").strip()


def mark_and_cleanup_toc(md: str, outline_path: Path | None = None) -> str:
    """Detect the document's TOC, clean it in place, and record its line range in outline.json.

    When ``outline_path`` is set, writes ``tocStartLine`` / ``tocEndLine`` and the
    entry-only ``toc`` array.

    @param md: the full assembled Markdown document
    @param outline_path: the document's outline.json file
    @return: the document with its TOC (if any) cleaned; unchanged when no TOC is found
    """
    if not md:
        return ""

    lines = md.split("\n")
    label_index = toc_label_line(lines)
    if label_index is None:
        return md

    table_start = _find_table_start_near(lines, label_index)
    if table_start is not None:
        raw_block, boundary = _scan_region(lines, table_start, _is_markdown_table_line)
        block_lines = _flatten_table_block(raw_block)
    else:
        if not _confirm_plain_toc(lines, label_index):
            return md
        block_lines, boundary = _scan_region(lines, label_index + 1, is_toc_entry_line)

    if not block_lines:
        return md

    label_line = _clean_toc_line(lines[label_index])
    body_text = _block_cleanup("\n".join(block_lines))
    body_lines: list[str] = []
    for line in body_text.split("\n"):
        stripped = line.strip()
        if stripped == "":
            # Keep blank lines inside the TOC block; do not collapse them here.
            body_lines.append("")
            continue
        if _PAGE_MARKER_LINE.match(stripped):
            body_lines.append(stripped)
            continue
        cleaned_line = _clean_toc_line(line)
        if cleaned_line:
            body_lines.append(cleaned_line)
    if not any(line for line in body_lines):
        return md
    cleaned = ([label_line] if label_line else []) + body_lines
    # Replace the original TOC span with the cleaned block; leave surrounding lines as-is.
    rebuilt = lines[:label_index] + cleaned + lines[boundary:]
    result = "\n".join(rebuilt)
    if not result.endswith("\n"):
        result += "\n"

    if outline_path is not None:
        toc_start = label_index
        toc_end = label_index + len(cleaned) - 1
        # Entry lines only — label / page markers / interstitial noise stay out of ``toc``.
        # Match against pre-block-cleanup text so tab/leader separators still satisfy
        # :func:`is_toc_entry_line`, then store the same cleaned form as in the body.
        toc_entries: list[str] = []
        for line in block_lines:
            stripped = line.strip()
            if not stripped or _PAGE_MARKER_LINE.match(stripped):
                continue
            candidate = _clean_toc_line(line)
            if candidate and is_toc_entry_line(candidate):
                stored = _block_cleanup(candidate).strip()
                if stored:
                    toc_entries.append(stored)
        write_outline(
            outline_path,
            {"tocStartLine": toc_start, "tocEndLine": toc_end, "toc": toc_entries},
        )
    return result


# Trailing "<separator><page number>" of a cleaned TOC entry, capturing the page (arabic or
# roman). The separator is a dash, dot-leaders, or whitespace (a tab collapses to one space).
_ENTRY_PAGE = re.compile(
    r"""
    (?:
        \s+[-–—]\s+
        | \s*[.…·]{2,}\s*
        | \s+
    )
    (?:page\s*)?
    (\d{1,4}|[ivxlcdm]{1,8})
    \s*$
    """,
    re.IGNORECASE | re.VERBOSE,
)


def _page_number(token: str) -> int | None:
    """Parse a TOC page token (arabic ``"12"`` or roman ``"iv"``) to an int, or ``None``."""
    if token.isdigit():
        return int(token)
    vector = roman_numbering(token)
    return vector[0] if vector else None


def _entry_to_record(entry: str) -> dict:
    """Convert a cleaned TOC entry string into an outline record: title (trailing page
    stripped), ``page`` (its page number, or ``None``), ``level`` (numbering depth, or ``None``).
    """
    match = _ENTRY_PAGE.search(entry)
    if match:
        title = entry[: match.start()].strip()
        page = _page_number(match.group(1))
    else:
        title = entry.strip()
        page = None
    return {"title": title, "level": numbering_depth(title) or None, "page": page}


def _records_from_toc_strings(toc_strings: list) -> list[dict]:
    """Build outline records from the entry strings recorded by :func:`mark_and_cleanup_toc`."""
    records: list[dict] = []
    for entry in toc_strings:
        record = _entry_to_record(entry)
        if record["title"]:
            records.append(record)
    return records


def _is_backmatter_title(title: str) -> bool:
    """Whether ``title`` names a Reference- or Appendix-section (numbering prefix ignored)."""
    text = _APPENDIX_NUMBERING_PREFIX.sub("", title).strip()
    return bool(_REFERENCE_HEADING_START.match(text) or _APPENDIX_HEADING_START.match(text))


def backmatter_from_records(markdown: str, records: list[dict]) -> int | None:
    """The body line of the first Reference/Appendix record that resolves to a unique line,
    or ``None`` -- the outline-based replacement for the heuristic body scan."""
    positions = line_pages(markdown)
    for record in records:
        if _is_backmatter_title(record.get("title") or ""):
            line = resolve_record_line(positions, record)
            if line is not None:
                return line
    return None


def find_toc_and_appendix(
    md: str,
    outline_path: Path | None = None,
    *,
    min_structure_tokens: int = DEFAULT_MIN_STRUCTURE_TOKENS,
) -> str:
    """Derive the document's outline and record it in outline.json (``toc`` titles,
    ``backmatterLine``), forking on whether PDF bookmarks are available.

    When a ``bookmarks.json`` sidecar already exists beside ``outline_path`` (real PDF
    bookmarks, extracted upstream), it is the authoritative outline: the printed TOC is left
    untouched. Otherwise the printed TOC is detected and cleaned in place, its entries are
    harvested into outline records, verified/​corrected against the document, and written to
    ``bookmarks.json``. Either way ``toc`` and ``backmatterLine`` come from the records.

    A *bookmarked* document always records its outline (the bookmark path is cheap and leaves
    the ``.md`` untouched) so even a small one carries a ``toc`` for Stage 0.5. A document with
    **no** bookmarks and fewer than ``min_structure_tokens`` (``len(md) // 4``) is returned
    unchanged with no outline — it is sent whole later.

    @param md: the full assembled Markdown document
    @param outline_path: the document's outline.json file
    @param min_structure_tokens: skip printed-TOC detection below this (no-bookmark path only)
    @return: the document, with the printed TOC cleaned in place only on the no-bookmark path
    """
    bookmarks_path = outline_path.with_name(BOOKMARKS_NAME) if outline_path is not None else None
    records = read_bookmarks(bookmarks_path)
    tokens = len(md) // 4
    if records:
        # Authoritative PDF bookmarks: record the outline regardless of size (cheap; the .md
        # is left untouched), skipping printed-TOC detection and cleanup entirely.
        result = md
        outline_source = "pdf-bookmarks"
    elif tokens < min_structure_tokens:
        # Small document, no bookmarks: skip printed-TOC detection; it is sent whole later.
        return md
    else:
        # Large document, no bookmarks: clean the printed TOC in place, harvest it to records.
        result = mark_and_cleanup_toc(md, outline_path)
        records = verify_bookmarks(
            _records_from_toc_strings(read_outline(outline_path).get("toc", [])), result
        )
        if records and bookmarks_path is not None:
            write_bookmarks(bookmarks_path, records)
        outline_source = "md-toc" if records else "none"

    write_outline(outline_path, {
        "tokens": tokens,
        "outline_source": outline_source,
        "toc": [r["title"] for r in records if r.get("title")],
    })

    backmatter_line = backmatter_from_records(result, records)
    if backmatter_line is not None:
        write_outline(outline_path, {"backmatterLine": backmatter_line})
    write_outline(outline_path, {"tokens": len(result) // 4})
    return result
