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
Split an already-produced Markdown document into per-chunk files by top level headings.

Given the final Markdown that :mod:`docling_pdf_parser` / :mod:`docling_docx_parser`
writes for a document, this module lays out a browsable chunk folder beside that ``.md``
file::

    protocol.md
    Chunks/
        catalog.json
        outline.json
        Chunk-0.md        (everything before the first level-1 heading, if any)
        Chunk-1.md
        Chunk-2.1.md      (a chunk larger than the token budget, split into parts)
        Chunk-2.2.md

Content before the first boundary heading, if any, becomes the start of ``Chunk-0``.

Consecutive top-level sections are united in document order until they fill
:data:`DEFAULT_MAX_TOKENS`, then written as one chunk file. A united (or single) section
still larger than the budget is split into ``Chunk-<n>.<k>`` parts by sub-headings (a level
deeper than the top) and then paragraph (blank-line) boundaries. A text-only tail part
smaller than :data:`MIN_TAIL_TOKENS` is not cut off — it is folded back into the preceding
part even if that pushes it over the budget.

Everything from the <Reference>/<Appendix> marker to the end of the document
becomes one standalone final chunk. That region is excluded from ``outline.json``'s
document-wide heading array. Marked TOC lines are excluded from that array as well
when ``tocStartLine``/``tocEndLine`` are known.

All chunks are summarised in ``catalog.json``

    {
      "fileId": "protocol.pdf",
      "chunks": [
        {
          "chunk_id": "chunk001",
          "file": "Chunk-1.md",
          "heading": ["Introduction"],
          "summary": "",
          "rubric_tags": [],
          "questions_answered": [],
          "extraction_hints": [],
          "pages": [1, 2],
          "length": 1837
        }, ...
      ]
    }

``summary``, ``rubric_tags``, ``questions_answered`` and ``extraction_hints`` are always left
empty here so they can be filled in later. ``pages`` lists the -- page: numbers referenced within
a chunk (from the ``<!-- page: N-->`` markers the PDF parser emits); it is empty for DOCX.
``length`` is the character count of the chunk file's content.

Token counts use a cheap character-based heuristic (``len(text) // 4``); no ML tokenizer is loaded.

Two entry points cover the two flows:

* :func:`write_chunk_files` — one Markdown document already in hand; used by
  ``docling_parser.py --chunk`` right after parsing.
* :func:`chunk_file` — one already-parsed ``.md`` file, given its exact path (MVP: one
  proposal file per answer, so there is exactly one file to chunk). Invoked in-process
  from the Docling daemon (``POST /chunk``) or as a standalone CLI fallback:
  ``python chunker.py <file_path>``.
"""

from __future__ import annotations

import argparse
import json
import re
import shutil
import sys
from pathlib import Path
from typing import Any

from markdown_cleanup import clean_markdown
from toc_and_appendix_detection import (
    DEFAULT_MIN_STRUCTURE_TOKENS,
    MAX_HEADING_WORDS,
    MAX_WORD_CHARS,
    MIN_HEADING_CHARS,
    TOC_END,
    TOC_START,
    backmatter_marker_line,
    is_toc_entry_line,
    mark_toc_and_appendix,
    read_outline,
)

# Default maximum tokens per chunk file. A chunk larger than this is split into parts.
DEFAULT_MAX_TOKENS = 2000

# A text-only continuation part smaller than this is folded back into the preceding part
# instead of being cut off into its own file, even when that pushes the preceding part over
# the token budget.
MIN_TAIL_TOKENS = 500

# Heading recorded for the leading chunk (content before the first heading) and any other
# chunk that has no heading of its own.
DEFAULT_HEADING = "General Information"

# Name of the per-document catalog file written into the chunks folder.
CATALOG_NAME = "catalog.json"

# Per-document outline file written beside the catalog (TOC / heading array / token size).
# Same base name as the preliminary sidecar :func:`toc_and_appendix_detection.mark_toc_and_appendix`
# writes beside the .md (no collision -- that one lives beside output_file, this one inside
# Chunks/, which is wiped and recreated below); write_chunk_files folds the sidecar's
# findings into this final version and removes the sidecar once done.
OUTLINE_NAME = "outline.json"

# Name of the folder, beside a document's .md, holding its chunk files, catalog and outline.
CHUNKS_DIRNAME = "Chunks"

# A line that is entirely bold or bold+italic (2 or 3 matching stars on each side,
# optionally ending with ':'), e.g. "**13.0 Funding**" or "***13.0 Funding***".
_BOLD_LINE = re.compile(r"^(\*{2,3})(.+?)\1:?$")

# ATX heading line; group 1 = the '#' run, group 2 = the text.
HEADING = re.compile(r"^(#{1,6})(?!#)\s+(.*\S)\s*$")

_RULE_LINE = re.compile(r"^-{3,}$")

# A "<!-- page: N-->" marker, page number captured.
_PAGE_MARKER = re.compile(r"<!-- page: (\d+)-->", re.IGNORECASE)

# A page marker alone on its own line.
_PAGE_MARKER_LINE = re.compile(rf"^{_PAGE_MARKER.pattern}$", re.IGNORECASE)


def is_neutral(stripped: str) -> bool:
    """Lines that neither extend nor break a region: blanks, page markers, rules."""
    return stripped == "" or _RULE_LINE.match(stripped) is not None \
        or _PAGE_MARKER_LINE.match(stripped) is not None


def valid_heading(text: str) -> bool:
    """Whether a heading candidate is usable: longer than 4 characters
    (:data:`MIN_HEADING_CHARS`), at most :data:`MAX_HEADING_WORDS` words, no word
    over :data:`MAX_WORD_CHARS` characters, and not a table caption (text already
    stripped of ``#`` / ``**`` markers must not start with ``Table ``).
    """
    if text.casefold().startswith("table "):
        return False
    if len(text) < MIN_HEADING_CHARS:
        return False
    words = text.split()
    if not words or len(words) > MAX_HEADING_WORDS:
        return False
    return all(len(word) <= MAX_WORD_CHARS for word in words)


def _standout_heading(lines: list[str], index: int) -> str | None:
    """A bold/bold+italic (``**...**``/``***...***``) or isolated ALL-CAPS stand-out
    heading at ``lines[index]``, or ``None``, e.g. ``**POTENTIAL IMPACT OF RESEARCH**``,
    ``**13.0 Funding**``, ``REFERENCES``.

    Isolation — blank/neutral lines on both sides, or a document/page boundary — is what
    lets a line with no structural marker of its own (unlike an ATX heading) be trusted as
    a heading rather than emphasis or shouting inside a paragraph.
    """
    stripped = lines[index].strip()
    # A lenient, leading-pipe-only check on purpose: this only needs to SKIP anything
    # table-ish (better to under-collect than to mistake a stray cell's content for a
    # heading), unlike toc_and_appendix_detection's stricter both-ends table-line check, which
    # parses actual table structure and cannot afford a false positive there.
    if not stripped or is_neutral(stripped) or stripped.startswith("|"):
        return None
    before = lines[index - 1].strip() if index > 0 else ""
    after = lines[index + 1].strip() if index + 1 < len(lines) else ""
    if not is_neutral(before) or not is_neutral(after):
        return None

    bold = _BOLD_LINE.match(stripped)
    if bold:
        return bold.group(2).strip()
    if stripped == stripped.upper() and len(re.sub(r"[^A-Z]", "", stripped)) >= 3 \
            and not is_toc_entry_line(stripped) and not HEADING.match(stripped):
        return stripped
    return None


def _count_tokens(text: str) -> int:
    """Estimate the token count of a string with a cheap character-based heuristic."""
    return len(text) // 4


def _match_heading(line: str) -> tuple[int, str] | None:
    """Match ``line`` against the ATX heading regex once, returning ``(level, text)`` —
    the heading level (number of leading ``#``) and its text with the ``#`` markers
    stripped — or ``None`` if the line is not an ATX heading.

    Does **not** apply :func:`valid_heading`; callers that decide chunk cuts or catalog
    labels must filter via :func:`_heading_level` / :func:`_heading_text` or
    :func:`valid_heading` themselves.
    """
    match = HEADING.match(line)
    if match is None:
        return None
    return len(match.group(1)), match.group(2).strip()


def _heading_level(line: str) -> int | None:
    """Return the heading level used for chunk cuts, or ``None`` if the line is not an
    ATX heading or its text fails :func:`valid_heading` (same rules as catalog/outline
    labels — e.g. ``Table …`` captions do not start or end a chunk).
    """
    matched = _match_heading(line)
    if matched is None or not valid_heading(matched[1]):
        return None
    return matched[0]


def _heading_text(line: str) -> str | None:
    """Return a cut-worthy heading's text (``#`` stripped), or ``None`` if the line is
    not an ATX heading or fails :func:`valid_heading`.
    """
    matched = _match_heading(line)
    if matched is None or not valid_heading(matched[1]):
        return None
    return matched[1]


def _min_heading_level(
    text: str, deeper_than: int = 0, lines: list[str] | None = None
) -> int | None:
    """Return the shallowest heading level appearing in ``text`` that is deeper than
    ``deeper_than``, or ``None`` if no such heading exists.

    @param lines: ``text`` already split on newlines, when the caller has it split
        already
    """
    best: int | None = None
    for line in (lines if lines is not None else text.split("\n")):
        level = _heading_level(line)
        if level is not None and level > deeper_than and (best is None or level < best):
            best = level
    return best


def _pages_in(text: str) -> list[int]:
    """Return the sorted, de-duplicated -- page: numbers referenced in a string."""
    pages: set[int] = set()
    for match in _PAGE_MARKER.finditer(text or ""):
        pages.add(int(match.group(1)))
    return sorted(pages)


def _split_off_backmatter(
    markdown_content: str, marker_line: int | None = None, lines: list[str] | None = None
) -> tuple[str, str | None]:
    """Split the document at its first ``<Reference>``/``<Appendix>`` marker: 
    everything from the marker to the end of the document becomes a standalone backmatter chunk.

    @param marker_line: the marker's line index, when already known
    @param lines: ``markdown_content`` already split on newlines, when the caller has it split already
    @return: ``(main_content, backmatter_text)``; ``backmatter_text`` is None when no marker is present
    """
    if lines is None:
        lines = markdown_content.split("\n")
    line = marker_line if marker_line is not None else backmatter_marker_line(markdown_content, lines=lines)
    if line is None:
        return markdown_content, None
    return "\n".join(lines[:line]).rstrip(), "\n".join(lines[line:]).strip()


def _backmatter_heading(text: str) -> list[str]:
    """The heading array for a backmatter chunk: the text of the heading line
    immediately following the ``<Reference>``/``<Appendix>`` marker (the block's first
    line), ATX ``#`` markers or bold ``**`` stripped."""
    lines = text.split("\n")
    for line in lines[1:]:
        stripped = line.strip()
        if not stripped:
            continue
        atx = _match_heading(line)
        if atx:
            return [atx[1]]
        bold = _BOLD_LINE.match(stripped)
        if bold:
            return [bold.group(2).strip()]
        return [stripped]
    return [DEFAULT_HEADING]


def _split_into_top_chunks(markdown_content: str, boundary_level: int | None) -> list[dict]:
    """Split the document at its shallowest heading level.

    The chunk boundary is the shallowest heading level present in the document: level-1
    (``#``) when the document has any, otherwise the first (topmost) heading level it does
    have. A document with no headings at all is returned as a single ``number == 0`` chunk.

    @param markdown_content: the full Markdown document
    @param boundary_level: the document's shallowest ATX heading level, or ``None`` when it
        has none at all — computed once by the caller (:func:`write_chunk_files`) via
        :func:`_min_heading_level`
    @return: chunks in document order, each ``{"number", "heading", "level", "text"}``;
        content before the first boundary heading (if any) is chunk ``number == 0`` with an
        empty heading, and the remaining chunks are numbered from 1
    """
    if boundary_level is None:
        text = markdown_content.strip()
        return [{"number": 0, "heading": "", "level": 0, "text": text}] if text else []

    preamble_lines: list[str] = []
    chunks: list[dict] = []
    current: dict | None = None

    for line in markdown_content.split("\n"):
        if _heading_level(line) == boundary_level:
            if current is not None:
                chunks.append(current)
            current = {"heading": _heading_text(line), "lines": [line]}
        elif current is None:
            preamble_lines.append(line)
        else:
            current["lines"].append(line)

    if current is not None:
        chunks.append(current)

    result: list[dict] = []
    preamble_text = "\n".join(preamble_lines).strip()
    if preamble_text:
        result.append({"number": 0, "heading": "", "level": boundary_level, "text": preamble_text})
    for chunk_number, chunk in enumerate(chunks, start=1):
        result.append({
            "number": chunk_number,
            "heading": chunk["heading"],
            "level": boundary_level,
            "text": "\n".join(chunk["lines"]).strip(),
        })
    return result


def _subchunk_blocks(chunk_text: str, boundary_level: int) -> list[str]:
    """Split a chunk's text at its shallowest sub-heading level.

    The first block holds the chunk's own boundary heading and any lead-in text before the
    first sub-heading; each subsequent block is one sub-chunk. A chunk with no sub-headings
    (no heading deeper than ``boundary_level``) yields a single block (the whole text).
    """
    sub_level = _min_heading_level(chunk_text, deeper_than=boundary_level)
    if sub_level is None:
        stripped = chunk_text.strip()
        return [stripped] if stripped else []

    blocks: list[str] = []
    current: list[str] = []
    for line in chunk_text.split("\n"):
        if _heading_level(line) == sub_level and current:
            blocks.append("\n".join(current).strip())
            current = [line]
        else:
            current.append(line)
    if current:
        blocks.append("\n".join(current).strip())
    return [block for block in blocks if block]


def _split_trailing_page_markers(text: str) -> tuple[str, str] | None:
    """If ``text`` ends with one or more ``<!-- page: N-->`` lines (and blank lines around
    them), return ``(body, markers_block)``. ``None`` when it does not end that way.
    """
    lines = text.split("\n")
    end = len(lines) - 1
    while end >= 0 and lines[end].strip() == "":
        end -= 1
    if end < 0 or not _PAGE_MARKER_LINE.match(lines[end].strip()):
        return None
    start = end
    while start > 0:
        prev = lines[start - 1].strip()
        if prev == "" or _PAGE_MARKER_LINE.match(prev):
            start -= 1
            continue
        break
    while start <= end and lines[start].strip() == "":
        start += 1
    body = "\n".join(lines[:start]).rstrip()
    markers = "\n".join(lines[start:]).strip()
    if not markers:
        return None
    return body, markers


def _flush_without_trailing_page_markers(current: str, nxt: str) -> tuple[str | None, str]:
    """Close ``current`` before ``nxt``: move a trailing page-marker run onto ``nxt``.

    Returns ``(body_to_emit_or_None, next_current)``.
    """
    extracted = _split_trailing_page_markers(current)
    if extracted is None:
        return current, nxt
    body, markers = extracted
    new_next = (markers + "\n\n" + nxt.lstrip()).strip()
    body = body.strip()
    return (body if body else None), new_next


def _move_trailing_page_markers(parts: list[str]) -> list[str]:
    """Never leave a ``<!-- page: N-->`` marker at the end of a part: move trailing marker
    runs to the start of the next part. Empty parts left behind are dropped. The last
    part is unchanged (nowhere to move markers).
    """
    if len(parts) < 2:
        return parts
    result = list(parts)
    for index in range(len(result) - 1):
        extracted = _split_trailing_page_markers(result[index])
        if extracted is None:
            continue
        body, markers = extracted
        result[index] = body
        result[index + 1] = (markers + "\n\n" + result[index + 1].lstrip()).strip()
    return [part for part in result if part.strip()]


def _split_by_paragraphs(text: str, max_tokens: int) -> list[str]:
    """Split text into parts no larger than the budget at blank-line (paragraph) boundaries.

    A single paragraph larger than the budget is kept whole (nothing is split mid-paragraph).
    When a part is closed, a trailing ``<!-- page: N-->`` run is moved onto the next paragraph.
    """
    parts: list[str] = []
    current: str | None = None
    for paragraph in text.split("\n\n"):
        if paragraph.strip() == "":
            continue
        candidate = paragraph if current is None else current + "\n\n" + paragraph
        if current is None or _count_tokens(candidate) <= max_tokens:
            current = candidate
            continue
        body, current = _flush_without_trailing_page_markers(current, paragraph)
        if body is not None:
            parts.append(body)
    if current is not None:
        parts.append(current)
    return _move_trailing_page_markers(parts)


def _is_standalone_heading(block: str) -> bool:
    """Whether ``block`` is only a cut-worthy ATX heading (plus optional blank/neutral lines)."""
    content: list[str] = []
    for line in block.split("\n"):
        if is_neutral(line.strip()):
            continue
        content.append(line)
    return len(content) == 1 and _heading_level(content[0]) is not None


def _pack_blocks(blocks: list[str], max_tokens: int) -> list[str]:
    """Unite consecutive blocks into parts as large as the budget allows.

    When the next block is a stand-alone heading (heading line, no body text), look ahead
    one more block: only keep merging into the current part if ``current + heading +
    following`` still fits. If not, flush the current part and start a new merge from
    that stand-alone heading. A new part that is still only a stand-alone heading never
    flushes alone — it always takes at least the following block (even over budget; the
    oversized splitter handles that later).

    Trailing ``<!-- page: N-->`` markers are never left at the end of a flushed part — they
    move onto the start of the next part.
    """
    parts: list[str] = []
    current: str | None = None
    index = 0
    n = len(blocks)
    while index < n:
        block = blocks[index]
        if current is None:
            current = block
            index += 1
            continue

        if _is_standalone_heading(block) and index + 1 < n:
            following = blocks[index + 1]
            lookahead = current + "\n\n" + block + "\n\n" + following
            if _count_tokens(lookahead) <= max_tokens:
                current = current + "\n\n" + block
                index += 1
                continue
            body, current = _flush_without_trailing_page_markers(current, block)
            if body is not None:
                parts.append(body)
            index += 1
            continue

        candidate = current + "\n\n" + block
        if _count_tokens(candidate) <= max_tokens:
            current = candidate
            index += 1
            continue
        if _is_standalone_heading(current):
            # Do not emit a heading-only part — pull the next block in even over budget.
            current = candidate
            index += 1
            continue
        body, current = _flush_without_trailing_page_markers(current, block)
        if body is not None:
            parts.append(body)
        index += 1

    if current is not None:
        parts.append(current)
    return _move_trailing_page_markers(parts)


def _split_oversized(chunk_text: str, boundary_level: int, max_tokens: int) -> list[str]:
    """Split an over-budget chunk into parts.

    Sub-headings (the shallowest heading level deeper than ``boundary_level``) are honoured
    first: consecutive sub-chunks are united up to the budget. If the chunk has no
    sub-headings, or a united part is still over budget, that piece is split at paragraph
    boundaries.
    """
    blocks = _subchunk_blocks(chunk_text, boundary_level)
    packed = _split_by_paragraphs(chunk_text, max_tokens) if len(blocks) <= 1 \
        else _pack_blocks(blocks, max_tokens)

    parts: list[str] = []
    for part in packed:
        if _count_tokens(part) > max_tokens:
            parts.extend(_split_by_paragraphs(part, max_tokens))
        else:
            parts.append(part)
    return parts


def _merge_small_text_tails(parts: list[str], min_tokens: int) -> list[str]:
    """Fold a text-only continuation part smaller than ``min_tokens`` back into the part
    before it, so a small tail cut off from the previous part is not emitted as its own file.
    A part that carries any *cut-worthy* ATX heading (see :func:`_heading_level` /
    :func:`valid_heading`) is never merged; lines that look like headings but fail
    validation (e.g. ``## Table …``) do not block the merge. The merge is applied even
    when it pushes the preceding part over the token budget.
    """
    merged: list[str] = []
    for part in parts:
        if merged and _min_heading_level(part) is None and _count_tokens(part) < min_tokens:
            merged[-1] = merged[-1].rstrip() + "\n\n" + part.lstrip()
        else:
            merged.append(part)
    return merged


def _part_heading(part_text: str, previous_heading: list[str] | None) -> list[str]:
    """Derive the heading array for one chunk file part in the order they appear.

    Collects ATX headings and any bold or isolated ALL-CAPS stand-out lines.
    Each candidate must pass :func:`valid_heading` (rejects too short, too long, or too many words).
    A part with no heading of its own copies the heading array of previous chunk.
    ``[`` :data:`DEFAULT_HEADING` ``]`` is used only when the
    beginning heading is missing and there is no preceding entry to copy from.
    """
    lines = part_text.split("\n")
    beginning_level: int | None = None
    headings: list[str] = []
    for index, line in enumerate(lines):
        atx = _match_heading(line)
        if atx is not None:
            level, text = atx
            if beginning_level is None:
                beginning_level = level
            if not (beginning_level <= level <= beginning_level + 1):
                continue
        else:
            text = _standout_heading(lines, index)
            if text is None:
                continue
        if valid_heading(text):
            headings.append(text)
    if headings:
        return headings
    return previous_heading or [DEFAULT_HEADING]


def _aggregate_headings(
    detected_headings: list[list[str]],
    *,
    lines: list[str] | None = None,
    toc_range: tuple[int, int] | None = None,
    end_line: int | None = None,
) -> list[str]:
    """The document-wide heading array for ``outline.json``.

    When ``lines`` is given, headings are collected directly from those lines (same ATX /
    stand-out rules as :func:`_part_heading`), skipping any ``toc_range`` and stopping
    before ``end_line`` (the backmatter marker). Otherwise falls back to flattening
    ``detected_headings`` from chunk parts.

    Deduplicated case-insensitively; :data:`DEFAULT_HEADING` is filtered out.
    """
    if lines is not None:
        limit = end_line if end_line is not None else len(lines)
        headings: list[str] = []
        seen: set[str] = set()
        default_key = DEFAULT_HEADING.casefold()
        beginning_level: int | None = None
        for index in range(limit):
            if toc_range is not None and toc_range[0] <= index <= toc_range[1]:
                continue
            atx = _match_heading(lines[index])
            if atx is not None:
                level, text = atx
                if beginning_level is None:
                    beginning_level = level
                if not (beginning_level <= level <= beginning_level + 1):
                    continue
            else:
                text = _standout_heading(lines, index)
                if text is None:
                    continue
            if not valid_heading(text):
                continue
            key = text.casefold()
            if key == default_key or key in seen:
                continue
            seen.add(key)
            headings.append(text)
        return headings

    headings = []
    seen = set()
    default_key = DEFAULT_HEADING.casefold()
    for part_headings in detected_headings:
        for heading in part_headings:
            key = heading.casefold()
            if key == default_key or key in seen:
                continue
            seen.add(key)
            headings.append(heading)
    return headings


def _toc_lines(lines: list[str]) -> list[str]:
    """The marked TOC's entry lines — everything between the reserved ``<!-- TOC start -->``
    and ``<!-- TOC end -->`` markers, blank and page-marker lines dropped — or ``[]`` when
    the document has no marked TOC.
    """
    try:
        start = lines.index(TOC_START)
        end = lines.index(TOC_END, start + 1)
    except ValueError:
        return []
    entries: list[str] = []
    for line in lines[start + 1:end]:
        stripped = line.strip()
        if stripped and not _PAGE_MARKER_LINE.match(stripped):
            entries.append(stripped)
    return entries


def _prepare_markdown(markdown_content: str, output_file: Path, min_structure_tokens: int) -> str:
    """Run the shared pre-chunking pipeline on a document: garbage-line cleanup
    (idempotent — a ``<!-- cleaned -->``-marked document passes through unchanged), then
    TOC/appendix marking (size-gated internally, sidecar ``outline.json`` written beside
    ``output_file``). When anything changed, the updated Markdown is written back to
    ``output_file`` so the stored ``.md`` always matches what was chunked.

    This runs here rather than in the PDF/DOCX parsers so that *every* chunking entry
    point — including Java-generated Markdown sent straight to ``chunk_file`` — goes
    through the same cleanup and structure detection.
    """
    prepared = clean_markdown(markdown_content)
    prepared = mark_toc_and_appendix(
        prepared,
        output_file.with_name(OUTLINE_NAME),
        min_structure_tokens=min_structure_tokens,
    )
    if prepared != markdown_content:
        output_file.write_text(prepared, encoding="utf-8")
    return prepared


def clear_prior_outputs(output_file: Path) -> None:
    """Remove a previous convert's sibling ``outline.json`` and ``Chunks/`` folder
    (including ``catalog.json``) beside ``output_file``, so a reconvert cannot reuse
    stale ranges or chunk files.
    """
    output_file.with_name(OUTLINE_NAME).unlink(missing_ok=True)
    chunks_dir = output_file.parent / CHUNKS_DIRNAME
    if chunks_dir.exists():
        shutil.rmtree(chunks_dir)


def write_chunk_files(
    markdown_content: str,
    output_file: Path,
    filename: str,
    max_tokens: int = DEFAULT_MAX_TOKENS,
    *,
    min_structure_tokens: int = DEFAULT_MIN_STRUCTURE_TOKENS,
) -> Path | None:
    """Write per-chunk Markdown files, an ``outline.json`` and a ``catalog.json`` into a
    ``Chunks/`` folder beside ``output_file``.

    The document first goes through the shared pre-chunking pipeline
    (:func:`_prepare_markdown`): garbage-line cleanup (idempotent via the
    ``<!-- cleaned -->`` marker) and TOC/appendix marking, with the result written back
    to ``output_file`` when anything changed. Documents with fewer than
    ``min_structure_tokens`` (``len // 4``) are still cleaned, but not chunked —
    returns ``None`` without creating ``Chunks/``.

    A ``<Reference>``/``<Appendix>`` marker (see :func:`toc_and_appendix_detection.mark_appendix`),
    when present, splits off everything from it to the end of the document as one
    standalone backmatter chunk — no heading-based sub-splitting, no token-budget
    splitting. The remaining main content is split at the shallowest heading level, then
    consecutive sections are united up to ``max_tokens`` before any over-budget piece is
    split further.

    @param markdown_content: the full Markdown document already written to ``output_file``
    @param output_file: the main ``.md`` file; its chunks land in ``Chunks/`` beside it
    @param filename: the original input file name (with extension), recorded as ``fileId``
    @param max_tokens: target maximum tokens per chunk file; consecutive top-level sections
        are packed up to this budget, and only then is an over-budget piece split into parts
    @param min_structure_tokens: skip chunking when the document is smaller than this
    @return: the path to the created chunks folder, or ``None`` when chunking was skipped
    """
    markdown_content = _prepare_markdown(markdown_content, output_file, min_structure_tokens)
    if len(markdown_content) // 4 < min_structure_tokens:
        return None

    chunks_dir = output_file.parent / CHUNKS_DIRNAME
    if chunks_dir.exists():
        shutil.rmtree(chunks_dir)
    chunks_dir.mkdir(parents=True, exist_ok=True)

    # One shared split of the full document, reused by every detection pass below
    # instead of each re-splitting the same (potentially large) document on "\n".
    lines = markdown_content.split("\n")

    # The TOC range comes from the reserved markers themselves — exact by construction —
    # with the sidecar outline.json (written by mark_toc_and_appendix in
    # _prepare_markdown above) as a fallback for marker-less documents.
    sidecar = read_outline(output_file.with_name(OUTLINE_NAME))
    try:
        marker_start = lines.index(TOC_START)
        toc_range = (marker_start, lines.index(TOC_END, marker_start + 1))
    except ValueError:
        toc_start = sidecar.get("tocStartLine")
        toc_end = sidecar.get("tocEndLine")
        if isinstance(toc_start, int) and isinstance(toc_end, int):
            toc_range = (toc_start, toc_end)
        else:
            toc_range = None
    backmatter_line = sidecar.get("backmatterLine")
    if not isinstance(backmatter_line, int):
        backmatter_line = backmatter_marker_line(markdown_content, lines=lines)

    main_content, backmatter_text = _split_off_backmatter(markdown_content, backmatter_line, lines=lines)
    main_lines = lines[:backmatter_line] if backmatter_line is not None else lines
    boundary_level = _min_heading_level(main_content, lines=main_lines)

    catalog_chunks: list[dict] = []
    detected_headings: list[list[str]] = []
    next_id = 1

    # Unite consecutive top-level sections up to the token budget, then split only
    # those united parts that are still over budget (a single oversized section).
    top_chunks = _split_into_top_chunks(main_content, boundary_level)
    top_texts = [chunk["text"] for chunk in top_chunks if chunk["text"]]
    packed = _pack_blocks(top_texts, max_tokens) if top_texts else []
    packed = _move_trailing_page_markers(packed)
    # Prefer Chunk-0 when the document has a leading preamble; otherwise start at 1.
    first_number = 0 if (top_chunks and top_chunks[0]["number"] == 0) else 1
    split_level = boundary_level if boundary_level is not None else 0

    for offset, packed_text in enumerate(packed):
        number = first_number + offset
        if _count_tokens(packed_text) > max_tokens:
            parts = _split_oversized(packed_text, split_level, max_tokens)
        else:
            parts = [packed_text]
        parts = _move_trailing_page_markers(_merge_small_text_tails(parts, MIN_TAIL_TOKENS))

        single_part = len(parts) == 1
        for part_index, part_text in enumerate(parts, start=1):
            name = f"Chunk-{number}.md" if single_part else f"Chunk-{number}.{part_index}.md"
            (chunks_dir / name).write_text(part_text + "\n", encoding="utf-8")
            previous_heading = catalog_chunks[-1]["heading"] if catalog_chunks else None
            detected = _part_heading(part_text, previous_heading)
            detected_headings.append(detected)
            # The very first chunk is always the default header, regardless of content;
            # its real detected heading (if any) still feeds outline.json's aggregation.
            heading = detected if catalog_chunks else [DEFAULT_HEADING]
            catalog_entry = {
                "chunk_id": f"s{next_id:03d}",
                "file": name,
                "heading": heading,
                "summary": "",
                "rubric_tags": [],
                "questions_answered": [],
                "extraction_hints": [],
                "pages": _pages_in(part_text),
                "length": len(part_text),
            }
            catalog_chunks.append(catalog_entry)
            next_id += 1

    # A document that collapses into a single main-content chunk is short enough to be
    # sent to the LLM whole (see the Stage 0.5/1.1 whole-document thresholds) — there is
    # no chunk selection for a heading outline to assist with, so it is left empty
    # rather than populated with headings nothing downstream will ever read.
    headings = (
        _aggregate_headings(
            detected_headings,
            lines=lines,
            toc_range=toc_range,
            end_line=backmatter_line,
        )
        if len(catalog_chunks) > 1 else []
    )

    if backmatter_text:
        name = f"Chunk-{first_number + len(packed)}.md"
        (chunks_dir / name).write_text(backmatter_text + "\n", encoding="utf-8")
        catalog_chunks.append({
            "chunk_id": f"s{next_id:03d}",
            "file": name,
            "heading": _backmatter_heading(backmatter_text),
            "summary": "",
            "rubric_tags": [],
            "questions_answered": [],
            "extraction_hints": [],
            "pages": _pages_in(backmatter_text),
            "length": len(backmatter_text),
        })
        next_id += 1

    outline = {
        "fileId": filename,
        "tokens": len(markdown_content) // 4,
        "headings": headings,
        "toc": _toc_lines(lines),
    }
    if toc_range is not None:
        outline["tocStartLine"], outline["tocEndLine"] = toc_range
    if backmatter_line is not None:
        outline["backmatterLine"] = backmatter_line
    (chunks_dir / OUTLINE_NAME).write_text(
        json.dumps(outline, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )

    catalog = {"fileId": filename, "chunks": catalog_chunks}
    (chunks_dir / CATALOG_NAME).write_text(
        json.dumps(catalog, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )

    # The sidecar's findings are now folded into Chunks/outline.json above; remove it
    # so a stale preliminary file never lingers beside the .md.
    output_file.with_name(OUTLINE_NAME).unlink(missing_ok=True)

    return chunks_dir


def _chunk_count(chunks_dir: Path) -> int:
    """Number of chunk entries in a freshly written chunks folder's catalog."""
    catalog = json.loads((chunks_dir / CATALOG_NAME).read_text(encoding="utf-8"))
    return len(catalog.get("chunks", []))


def chunk_file(
    file_path: str,
    max_tokens: int = DEFAULT_MAX_TOKENS,
    *,
    min_structure_tokens: int = DEFAULT_MIN_STRUCTURE_TOKENS,
) -> dict[str, Any]:
    """Split one already-parsed Markdown file into its chunk tree.

    MVP scope: one proposal file per answer, so this takes the exact file to chunk rather
    than scanning a folder for inputs. Documents under ``min_structure_tokens`` are not
    chunked (``chunks`` is 0).

    Returns a summary dict: ``{"chunks", "logs"}``. Raises :class:`FileNotFoundError` when
    the file does not exist.
    """
    path = Path(file_path)
    if not path.is_file():
        raise FileNotFoundError(f"File does not exist: {path}")

    markdown = path.read_text(encoding="utf-8", errors="replace")
    chunks_dir = write_chunk_files(
        markdown,
        path,
        path.name,
        max_tokens=max_tokens,
        min_structure_tokens=min_structure_tokens,
    )
    if chunks_dir is None:
        tokens = len(markdown) // 4
        return {
            "chunks": 0,
            "logs": (
                f"Skipped chunking '{path.name}' "
                f"({tokens} tokens < {min_structure_tokens} min_structure_tokens)"
            ),
        }
    count = _chunk_count(chunks_dir)
    return {
        "chunks": count,
        "logs": f"Split '{path.name}' into {count} chunk file(s) in '{CHUNKS_DIRNAME}/'",
    }


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Light chunker for a single parsed CARDS proposal Markdown file."
    )
    parser.add_argument(
        "file_path",
        help="Path to a parsed .md file to chunk",
    )
    parser.add_argument(
        "--max-tokens",
        type=int,
        default=DEFAULT_MAX_TOKENS,
        help=f"Maximum tokens per chunk file (default: {DEFAULT_MAX_TOKENS})",
    )
    parser.add_argument(
        "--min-structure-tokens",
        type=int,
        default=DEFAULT_MIN_STRUCTURE_TOKENS,
        help=(
            "Skip chunking when document tokens (len//4) are below this "
            f"(default: {DEFAULT_MIN_STRUCTURE_TOKENS})"
        ),
    )
    args = parser.parse_args()

    try:
        summary = chunk_file(
            file_path=args.file_path,
            max_tokens=args.max_tokens,
            min_structure_tokens=args.min_structure_tokens,
        )
    except FileNotFoundError as exc:
        print(str(exc), file=sys.stderr, flush=True)
        sys.exit(1)

    if summary["logs"]:
        print(summary["logs"], flush=True)
    print(f"Created {summary['chunks']} chunk file(s).", flush=True)


if __name__ == "__main__":
    main()
