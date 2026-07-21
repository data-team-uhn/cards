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
TOCs, clean leaders/tabs, and wrap the block in ``<TOC start>`` / ``<TOC end>``.
Entries over 40 words or with a word over 100 characters are discarded.

Appendix detection (:func:`mark_toc_and_appendix`): after TOC marking, find the first
Reference/Appendix heading (ATX or isolated bold; see :data:`REFERENCE_HEADINGS` /
:data:`APPENDIX_HEADINGS`) and insert ``<Reference>`` or ``<Appendix>`` before it.
Skips the first :data:`APPENDIX_SEARCH_SKIP_LINES` lines and any marked TOC block.
"""

from __future__ import annotations

import json
import re
from pathlib import Path

TOC_START = "<TOC start>"
TOC_END = "<TOC end>"

# Maximum words per accepted heading, and maximum characters per word within it.
MAX_HEADING_WORDS = 40
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

# Marker line inserted immediately before the first Reference or Appendix section
# heading found by mark_appendix() — reserved, like <TOC start>/<TOC end>.
REFERENCE_MARKER = "<Reference>"
APPENDIX_MARKER = "<Appendix>"

# Documents shorter than this (``len(md) // 4``) skip TOC/appendix marking — Stage 0.5
# can send them whole. Overridable via :func:`mark_toc_and_appendix`'s ``min_structure_tokens``.
DEFAULT_MIN_STRUCTURE_TOKENS = 20000

# How many lines from the start of the document mark_appendix() skips before searching
# for a Reference/Appendix heading — avoids false hits from an early in-text mention
# (e.g. an abstract citing "see References").
APPENDIX_SEARCH_SKIP_LINES = 400

# Reference-section heading words/phrases recognised by mark_appendix().
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

# Appendix-section heading words/phrases recognised by mark_appendix().
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

# An ATX heading's text only, used by mark_appendix() (the level itself doesn't matter
# there — any heading depth can start a Reference/Appendix section).
_ATX_HEADING_TEXT = re.compile(r"^#{1,6}\s+(.*\S)\s*$")

# A bold/bold+italic line (2 or 3 matching stars on each side, optional trailing colon
# inside the closing stars), e.g. "**APPENDIX**", "**Appendix:**", "**12.0 References:**".
_APPENDIX_BOLD_LINE = re.compile(r"^(\*{2,3})(.+?)\1:?$")

# Leading section numbering to strip before matching a Reference/Appendix keyword, e.g.
# "18.0 ", "13 ", "10.1. ", "17  ".
_APPENDIX_NUMBERING_PREFIX = re.compile(r"^\(?\d+(?:\.\d+)*[.)]?\s+")


def _keyword_start_pattern(phrases: list[str]) -> re.Pattern:
    """A case-insensitive pattern matching any of ``phrases`` as a whole-word prefix."""
    alternation = "|".join(re.escape(phrase) for phrase in phrases)
    return re.compile(rf"^(?:{alternation})\b", re.IGNORECASE)


_REFERENCE_HEADING_START = _keyword_start_pattern(REFERENCE_HEADINGS)
_APPENDIX_HEADING_START = _keyword_start_pattern(APPENDIX_HEADINGS)

PAGE_MARKER = re.compile(r"<PDF\s+Page\s+(\d+)>", re.IGNORECASE)

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
    """Find the first "table of contents" / "contents" label line: decorated (with ``#``
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
    """Collect an ``is_of_type`` region, continuing across ``<-- page: N-->`` boundaries as
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
    """Detect the document's TOC and wrap it in <TOC start> / <TOC end> lines.

    @param md: the full assembled Markdown document
    @param outline_path: the document's outline.json file
    @return: the document, with its TOC (if any) marked; unchanged when no TOC is found
    """
    if not md:
        return ""
    # skip if already marked
    if TOC_START in md:
        return md

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
        if _PAGE_MARKER_LINE.match(stripped):
            body_lines.append(stripped)
            continue
        cleaned_line = _clean_toc_line(line)
        if cleaned_line:
            body_lines.append(cleaned_line)
    if not body_lines:
        return md
    cleaned = ([label_line] if label_line else []) + body_lines
    # Layout: [preamble...][""][TOC_START][cleaned...][TOC_END][""][rest...]
    toc_start = label_index + 1
    toc_end = toc_start + 1 + len(cleaned)
    rebuilt = lines[:label_index] + ["", TOC_START] + cleaned + [TOC_END, ""] + lines[boundary:]
    result = re.sub(r"\n{3,}", "\n\n", "\n".join(rebuilt)).strip() + "\n"

    if outline_path is not None:
        write_outline(outline_path, {"tocStartLine": toc_start, "tocEndLine": toc_end})
    return result


def _is_neutral_line(stripped: str) -> bool:
    """Lines that neither extend nor break an isolation check: blanks, rules, page markers."""
    return stripped == "" or _RULE_LINE.match(stripped) is not None \
        or _PAGE_MARKER_LINE.match(stripped) is not None


def appendix_heading_kind(lines: list[str], index: int) -> str | None:
    """Whether lines[index] is a Reference- or Appendix-section heading.

    @return: "reference", "appendix", or None
    """
    stripped = lines[index].strip()
    atx = _ATX_HEADING_TEXT.match(stripped)
    if atx:
        text = atx.group(1).strip()
    else:
        bold = _APPENDIX_BOLD_LINE.match(stripped)
        if not bold:
            return None
        before = lines[index - 1].strip() if index > 0 else ""
        after = lines[index + 1].strip() if index + 1 < len(lines) else ""
        if not _is_neutral_line(before) or not _is_neutral_line(after):
            return None
        text = bold.group(2).strip()

    text = _APPENDIX_NUMBERING_PREFIX.sub("", text).strip()
    if _REFERENCE_HEADING_START.match(text):
        return "reference"
    if _APPENDIX_HEADING_START.match(text):
        return "appendix"
    return None


def mark_appendix(md: str, outline_path: Path | None = None) -> str:
    """Detect the document's first Reference or Appendix section heading and mark it
    with a reserved ``<Reference>`` / ``<Appendix>`` line immediately before it.

    Skips the first APPENDIX_SEARCH_SKIP_LINES lines (avoids false hits from an
    early in-text mention, e.g. unheaded undetected TOC line "**10.4 Appendix**"). Also
    skips past the marked TOC block when outline_path's outline.json has a confirmed
    tocEndLine (written by mark_and_cleanup_toc) -- read only, no re-detection fallback:
    with no outline_path, or no tocEndLine on file, only the fixed line-400 floor applies.

    Only one marker is ever inserted whichever kind of heading — reference or appendix — appears first.

    @param md: the full assembled Markdown document, ideally already passed through mark_and_cleanup_toc
    @param outline_path: the document's outline.json file
    @return: the document with a marker line inserted before the first matching heading, or unchanged
    """
    if not md:
        return md or ""
    # skip if already marked
    if REFERENCE_MARKER in md or APPENDIX_MARKER in md:
        return md

    lines = md.split("\n")
    n = len(lines)
    outline = read_outline(outline_path)
    toc_end = outline.get("tocEndLine")

    # Never search inside or before the TOC — start after it when present — so inserting
    # a marker cannot shift stored tocStartLine/tocEndLine. Still honour the skip floor
    # so short docs and early in-text mentions are ignored.
    index = APPENDIX_SEARCH_SKIP_LINES
    if isinstance(toc_end, int):
        index = max(index, toc_end + 1)

    found_index = None
    found_kind = None
    while index < n:
        kind = appendix_heading_kind(lines, index)
        if kind is not None:
            found_index, found_kind = index, kind
            break
        index += 1

    if found_index is None:
        return md

    marker = REFERENCE_MARKER if found_kind == "reference" else APPENDIX_MARKER
    rebuilt = lines[:found_index] + [marker] + lines[found_index:]
    result = "\n".join(rebuilt)
    if outline_path is not None:
        backmatter_line = backmatter_marker_line(result)
        if backmatter_line is not None:
            write_outline(outline_path, {"backmatterLine": backmatter_line})
    return result


def backmatter_marker_line(md: str, lines: list[str] | None = None) -> int | None:
    """Find the line index of whichever backmatter marker — REFERENCE_MARKER or APPENDIX_MARKER,
    set by mark_appendix — appears first in md, or None when neither is present.

    @param lines: md already split on newlines, when the caller has it split already
    @return: the line index of whichever backmatter marker appears first in md, or None
    """
    for index, line in enumerate(lines if lines is not None else md.split("\n")):
        stripped = line.strip()
        if stripped == REFERENCE_MARKER or stripped == APPENDIX_MARKER:
            return index
    return None


def mark_toc_and_appendix(
    md: str,
    outline_path: Path | None = None,
    *,
    min_structure_tokens: int = DEFAULT_MIN_STRUCTURE_TOKENS,
) -> str:
    """Mark the TOC and the first Reference/Appendix heading, then record a token-count estimate.

    Documents with fewer than ``min_structure_tokens`` (``len(md) // 4``) are returned
    unchanged — no TOC/appendix detection — so small protocols can be sent whole later.

    @param md: the full assembled Markdown document
    @param outline_path: the document's outline.json file
    @param min_structure_tokens: skip marking when the document is smaller than this
    @return: the document with TOC and backmatter markers applied (when found)
    """
    if len(md) // 4 < min_structure_tokens:
        return md
    result = mark_appendix(mark_and_cleanup_toc(md, outline_path), outline_path)
    if outline_path is not None:
        write_outline(outline_path, {"tokens": len(result) // 4})
    return result
