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

Everything from ``backmatterLine`` (the first Reference/Appendix heading recorded in
the sidecar ``outline.json``) to the end of the document becomes one standalone final
chunk.

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
          "length": 1837,
          "isAppendix": false
        }, ...
      ]
    }

``summary``, ``rubric_tags``, ``questions_answered`` and ``extraction_hints`` are always left
empty here so they can be filled in later. ``isAppendix`` is ``true`` only for the
backmatter (Reference/Appendix) chunk; that chunk is never sent to the summarizer.
``pages`` lists the <!-- page: N --> numbers referenced within a chunk (from the
``<!-- page: N -->`` markers the PDF parser emits); it is empty for DOCX.
``length`` is the character count of the chunk file's content.

Token counts come from :func:`markdown_markers.count_tokens`, a cheap character-based
heuristic (``len(text) // 4``); no ML tokenizer is loaded.

Entry points:

* :func:`build_chunk_tree` — analyse Markdown into a chunk tree (may read a sibling ``.pdf``
  for bookmarks); writes nothing.
* :func:`write_chunk_files` — sole disk writer of ``{stem}.md`` + ``Chunks/``. Called by
  :func:`parse_document.parse_document` and :func:`chunk_file`.
"""

from __future__ import annotations

import argparse
import json
import re
import shutil
import sys
from pathlib import Path
from typing import Any, Callable, NamedTuple

from bookmarks import (
    BOOKMARKS_NAME,
    LineIndex,
    normalize_title,
    resolve_record_line,
)
from heading_numbering import numbering_depth
from markdown_markers import (
    HEADING,
    MIN_HEADING_CHARS,
    PAGE_MARKER,
    PAGE_MARKER_LINE,
    RULE_LINE,
    count_tokens,
    within_word_limits,
)
from toc_and_appendix_detection import (
    DEFAULT_MIN_STRUCTURE_TOKENS,
    derive_outline,
    is_toc_entry_line,
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

# Per-document outline file written inside Chunks/ (TOC / token size / routing). The same base
# name is used by the transient sidecar an older pipeline wrote beside the .md; write_chunk_files
# deletes any such leftover once done (see :func:`_remove_sidecars`) so it cannot be read back on
# a later run as if it were real PDF bookmarks.
OUTLINE_NAME = "outline.json"

# Name of the folder, beside a document's .md, holding its chunk files, catalog and outline.
CHUNKS_DIRNAME = "Chunks"

# ``unchunkedReason`` recorded when a document was deliberately left whole because it is below
# ``min_structure_tokens``. This is the only unchunked state: a parse that never reaches the
# chunker fails on the Java side instead of writing Markdown without a chunk tree.
UNCHUNKED_BELOW_THRESHOLD = "below_min_structure_tokens"

# A line recurring at least this many times across the document is page furniture (a running
# header or footer), never a heading — see :func:`repeated_lines`. Three rather than two so a
# heading that genuinely appears twice is not discarded.
MIN_RUNNING_HEADER_PAGES = 3

# A line that is entirely bold or bold+italic (2 or 3 matching stars on each side,
# optionally ending with ':'), e.g. "**13.0 Funding**" or "***13.0 Funding***".
_BOLD_LINE = re.compile(r"^(\*{2,3})(.+?)\1:?$")

# A leading Markdown list marker, e.g. the "- " of "- 20.0 **Appendices**".
_LIST_MARKER = re.compile(r"^[-*+]\s+")


def is_neutral(stripped: str) -> bool:
    """Lines that neither extend nor break a region: blanks, page markers, rules."""
    return stripped == "" or RULE_LINE.match(stripped) is not None \
        or PAGE_MARKER_LINE.match(stripped) is not None


def valid_heading(text: str) -> bool:
    """Whether a heading candidate is usable: longer than 4 characters
    (:data:`markdown_markers.MIN_HEADING_CHARS`), within the shared word limits
    (:func:`markdown_markers.within_word_limits`), and not a table caption (text already
    stripped of ``#`` / ``**`` markers must not start with ``Table ``).
    """
    if text.casefold().startswith("table "):
        return False
    if len(text) < MIN_HEADING_CHARS:
        return False
    return within_word_limits(text)


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


def _numbered_standout_depth(lines: list[str], index: int) -> int | None:
    """The section-numbering depth of an isolated bold/ALL-CAPS stand-out heading at
    ``lines[index]`` -- e.g. ``**3.2.1 Recruitment**`` -> 3 -- or ``None`` when the line is
    not such a heading or carries no numeric prefix.

    Used only as a sub-chunk split fallback (see :func:`_subchunk_blocks`): Docling
    sometimes demotes a deep numbered heading to bold body text, which no ATX-based cut
    would catch. Only Arabic-decimal numbering is honoured, to avoid false positives.
    """
    text = _standout_heading(lines, index)
    if text is None or not valid_heading(text):
        return None
    depth = numbering_depth(text)
    return depth or None


def _split_lines_at(lines: list[str], is_boundary: Callable[[int], bool]) -> list[str]:
    """Group ``lines`` into blocks, starting a new block at each boundary line. A boundary
    at the very start (nothing accumulated yet) does not create an empty leading block.
    """
    blocks: list[str] = []
    current: list[str] = []
    for index, line in enumerate(lines):
        if is_boundary(index) and current:
            blocks.append("\n".join(current).strip())
            current = [line]
        else:
            current.append(line)
    if current:
        blocks.append("\n".join(current).strip())
    return [block for block in blocks if block]


def _match_heading(line: str) -> tuple[int, str] | None:
    """Match ``line`` against the ATX heading regex once, returning ``(level, text)`` —
    the heading level (number of leading ``#``) and its text with the ``#`` markers
    stripped — or ``None`` if the line is not an ATX heading.

    Does **not** apply :func:`valid_heading`; callers that decide chunk cuts or catalog
    labels must filter via :func:`_heading_level` or :func:`valid_heading` themselves.
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


def _min_heading_level(lines: list[str], deeper_than: int = 0) -> int | None:
    """Return the shallowest heading level appearing in ``lines`` that is deeper than
    ``deeper_than``, or ``None`` if no such heading exists.

    @param lines: the already-split lines to scan
    @param deeper_than: only levels strictly deeper than this count
    @return: the shallowest qualifying heading level, or ``None``
    """
    best: int | None = None
    for line in lines:
        level = _heading_level(line)
        if level is not None and level > deeper_than and (best is None or level < best):
            best = level
    return best


def _pages_in(text: str) -> list[int]:
    """Return the sorted, de-duplicated ``<!-- page: N -->`` numbers referenced in a string."""
    pages: set[int] = set()
    for match in PAGE_MARKER.finditer(text or ""):
        pages.add(int(match.group(1)))
    return sorted(pages)


def _backmatter_heading(text: str) -> list[str]:
    """The heading array for a backmatter chunk: the text of the block's first non-empty line as a
    displayable label, with ATX ``#``, a leading list marker and Markdown emphasis removed.

    The list marker and inline emphasis matter because Docling routinely emits a backmatter
    heading as a list item with only part of it bold — ``- 20.0 **Appendices**`` on one real
    protocol, which reached the catalog verbatim because it is neither an ATX heading nor a
    fully-bold line.
    """
    for line in text.split("\n"):
        stripped = line.strip()
        if not stripped:
            continue
        atx = _match_heading(line)
        if atx:
            return [atx[1]]
        candidate = _LIST_MARKER.sub("", stripped)
        bold = _BOLD_LINE.match(candidate)
        if bold:
            return [bold.group(2).strip()]
        cleaned = candidate.replace("*", "").strip()
        return [cleaned or candidate]
    return [DEFAULT_HEADING]


def _split_into_top_chunks(lines: list[str], boundary_level: int | None) -> list[dict]:
    """Split the document at its shallowest heading level.

    The chunk boundary is the shallowest heading level present in the document: level-1
    (``#``) when the document has any, otherwise the first (topmost) heading level it does
    have. A document with no headings at all is returned as a single ``number == 0`` chunk.

    @param lines: the main-content Markdown already split on newlines
    @param boundary_level: the document's shallowest ATX heading level, or ``None`` when it
        has none at all — computed once by the caller (:func:`write_chunk_files`) via
        :func:`_min_heading_level`
    @return: chunks in document order, each ``{"number", "text"}``; content before the first
        boundary heading (if any) is chunk ``number == 0``, and the remaining chunks are
        numbered from 1. Each chunk's own heading line stays at the head of its ``text``;
        catalog labels are derived later by :func:`_part_heading`, per emitted part.
    """
    if boundary_level is None:
        text = "\n".join(lines).strip()
        return [{"number": 0, "text": text}] if text else []

    preamble_lines: list[str] = []
    chunks: list[list[str]] = []
    current: list[str] | None = None

    for line in lines:
        if _heading_level(line) == boundary_level:
            if current is not None:
                chunks.append(current)
            current = [line]
        elif current is None:
            preamble_lines.append(line)
        else:
            current.append(line)

    if current is not None:
        chunks.append(current)

    result: list[dict] = []
    preamble_text = "\n".join(preamble_lines).strip()
    if preamble_text:
        result.append({"number": 0, "text": preamble_text})
    for chunk_number, chunk_lines in enumerate(chunks, start=1):
        result.append({"number": chunk_number, "text": "\n".join(chunk_lines).strip()})
    return result


def _subchunk_blocks(chunk_text: str, boundary_level: int, cut_keys=frozenset()) -> list[str]:
    """Split a chunk's text at its shallowest sub-heading level.

    The first block holds the chunk's own boundary heading and any lead-in text before the
    first sub-heading; each subsequent block is one sub-chunk.

    With no ATX sub-heading (nothing deeper than ``boundary_level``), fall back in order to:
    (1) outline-record cut points -- non-ATX lines whose normalized text is in ``cut_keys``
    (bookmark / printed-TOC titles that resolved to a unique body line; see
    :func:`_record_cut_keys`); then (2) the shallowest *numbered stand-out* heading (a
    bold/ALL-CAPS line with a numeric prefix Docling emitted in place of a heading). With
    none of these, the whole text is a single block.
    """
    lines = chunk_text.split("\n")
    sub_level = _min_heading_level(lines, deeper_than=boundary_level)
    if sub_level is not None:
        return _split_lines_at(lines, lambda index: _heading_level(lines[index]) == sub_level)

    if cut_keys:
        record_boundaries = {
            index
            for index, line in enumerate(lines)
            if _match_heading(line) is None and normalize_title(line) in cut_keys
        }
        if record_boundaries:
            return _split_lines_at(lines, lambda index: index in record_boundaries)

    depth_by_index = {
        index: depth
        for index in range(len(lines))
        if (depth := _numbered_standout_depth(lines, index)) is not None
    }
    if not depth_by_index:
        stripped = chunk_text.strip()
        return [stripped] if stripped else []
    top_depth = min(depth_by_index.values())
    return _split_lines_at(lines, lambda index: depth_by_index.get(index) == top_depth)


def _split_trailing_page_markers(text: str) -> tuple[str, str] | None:
    """If ``text`` ends with one or more ``<!-- page: N -->`` lines (and blank lines around
    them), return ``(body, markers_block)``. ``None`` when it does not end that way.
    """
    lines = text.split("\n")
    end = len(lines) - 1
    while end >= 0 and lines[end].strip() == "":
        end -= 1
    if end < 0 or not PAGE_MARKER_LINE.match(lines[end].strip()):
        return None
    start = end
    while start > 0:
        prev = lines[start - 1].strip()
        if prev == "" or PAGE_MARKER_LINE.match(prev):
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
    """Never leave a ``<!-- page: N -->`` marker at the end of a part: move trailing marker
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
    When a part is closed, a trailing ``<!-- page: N -->`` run is moved onto the next paragraph.
    """
    parts: list[str] = []
    current: str | None = None
    for paragraph in text.split("\n\n"):
        if paragraph.strip() == "":
            continue
        candidate = paragraph if current is None else current + "\n\n" + paragraph
        if current is None or count_tokens(candidate) <= max_tokens:
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

    Trailing ``<!-- page: N -->`` markers are never left at the end of a flushed part — they
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
            if count_tokens(lookahead) <= max_tokens:
                current = current + "\n\n" + block
                index += 1
                continue
            body, current = _flush_without_trailing_page_markers(current, block)
            if body is not None:
                parts.append(body)
            index += 1
            continue

        candidate = current + "\n\n" + block
        if count_tokens(candidate) <= max_tokens:
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


def _split_oversized(
    chunk_text: str, boundary_level: int, max_tokens: int, cut_keys=frozenset()
) -> list[str]:
    """Split an over-budget chunk into parts.

    Sub-headings (the shallowest heading level deeper than ``boundary_level``) are honoured
    first: consecutive sub-chunks are united up to the budget. If the chunk has no
    sub-headings, or a united part is still over budget, that piece is split at paragraph
    boundaries. ``cut_keys`` are outline-record cut points used as a sub-heading fallback
    (see :func:`_subchunk_blocks`).
    """
    blocks = _subchunk_blocks(chunk_text, boundary_level, cut_keys)
    packed = _split_by_paragraphs(chunk_text, max_tokens) if len(blocks) <= 1 \
        else _pack_blocks(blocks, max_tokens)

    parts: list[str] = []
    for part in packed:
        if count_tokens(part) > max_tokens:
            parts.extend(_split_by_paragraphs(part, max_tokens))
        else:
            parts.append(part)
    return parts


def _is_heading_only(part: str) -> bool:
    """Whether ``part`` is nothing but cut-worthy ATX headings — no body text at all.

    Broader than :func:`_is_standalone_heading`, which requires exactly one content line: two
    consecutive headings with no prose between them are just as unusable as a chunk.
    """
    content = [line for line in part.split("\n") if not is_neutral(line.strip())]
    return bool(content) and all(_heading_level(line) is not None for line in content)


def _merge_heading_only_parts(parts: list[str]) -> list[str]:
    """Fold a part that is only a heading into a neighbour, so no chunk file is a bare title.

    Two paths produce one. :func:`_split_by_paragraphs` flushes the heading alone whenever the
    section's body is a single over-budget paragraph — routine for a schedule-of-assessments
    table, which Docling emits as consecutive ``|`` lines with no blank line, hence one
    paragraph. And :func:`_pack_blocks` guards its stand-alone-heading lookahead with
    ``index + 1 < n``, so a document ending on a bare heading (an empty final section) leaves
    it as the last part.

    The direction has to vary: a heading takes the part *after* it, which is the rule
    :func:`_pack_blocks` already applies to blocks, but a trailing heading has nothing to take
    and folds *backwards* instead. :func:`_merge_small_text_tails` cannot do this — it only
    merges backwards, and it refuses any part carrying a heading.

    Merging can push a part over ``max_tokens``. That is the existing trade in
    :func:`_pack_blocks` ("pull the next block in even over budget") and it is the better one:
    a 29-byte chunk carries no content for the summarizer, and splitting a heading from its
    table leaves the table identified only by an inherited label.
    """
    merged: list[str] = []
    for part in parts:
        if merged and _is_heading_only(merged[-1]):
            merged[-1] = merged[-1].rstrip() + "\n\n" + part.lstrip()
            continue
        merged.append(part)
    if len(merged) > 1 and _is_heading_only(merged[-1]):
        trailing = merged.pop()
        merged[-1] = merged[-1].rstrip() + "\n\n" + trailing.lstrip()
    return merged


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
        if merged and _min_heading_level(part.split("\n")) is None \
                and count_tokens(part) < min_tokens:
            merged[-1] = merged[-1].rstrip() + "\n\n" + part.lstrip()
        else:
            merged.append(part)
    return merged


def repeated_lines(lines: list[str], min_occurrences: int = MIN_RUNNING_HEADER_PAGES) -> frozenset:
    """Normalized keys of lines that recur at least ``min_occurrences`` times in the document —
    running headers and footers.

    Recurrence is the signal that separates the two, because nothing else does: a running header
    like ``CONFIDENTIAL`` sits immediately after a ``<!-- page: N -->`` marker with a blank line
    after it, which is exactly as *isolated* as a genuine stand-out heading at the top of a page.
    Rejecting anything adjacent to a page marker would throw the real headings away with it; a real
    heading appears once, a running header appears on every page.

    @param lines: the document's lines
    @param min_occurrences: how many times a line must recur to count as furniture
    @return: normalized keys to refuse as headings
    """
    counts: dict[str, int] = {}
    for line in lines:
        stripped = line.strip()
        if not stripped or PAGE_MARKER_LINE.match(stripped):
            continue
        key = normalize_title(stripped)
        if key:
            counts[key] = counts.get(key, 0) + 1
    return frozenset(key for key, count in counts.items() if count >= min_occurrences)


def _part_heading(
    part_text: str, previous_heading: list[str] | None, repeated: frozenset = frozenset()
) -> list[str]:
    """Derive the heading array for one chunk file part in the order they appear.

    Collects ATX headings and any bold or isolated ALL-CAPS stand-out lines.
    Each candidate must pass :func:`valid_heading` (rejects too short, too long, or too many words).
    A part with no heading of its own copies the heading array of previous chunk.
    ``[`` :data:`DEFAULT_HEADING` ``]`` is used only when the
    beginning heading is missing and there is no preceding entry to copy from.

    @param part_text: the emitted chunk part
    @param previous_heading: the preceding catalog entry's heading array, when there is one
    @param repeated: normalized keys of document-wide recurring lines to refuse
        (see :func:`repeated_lines`) — page furniture, not headings
    @return: the heading array for this part
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
        if valid_heading(text) and normalize_title(text) not in repeated:
            headings.append(text)
    if headings:
        return headings
    return previous_heading or [DEFAULT_HEADING]


def _write_json(path: Path, data: object) -> None:
    """Write ``data`` as pretty-printed UTF-8 JSON with a trailing newline."""
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def _remove_sidecars(output_file: Path) -> None:
    """Delete any leftover ``outline.json`` / ``bookmarks.json`` sitting *beside*
    ``output_file``.

    Nothing writes those paths any more — the outline and the records go into ``Chunks/`` —
    so this only clears artefacts left by an older version of the pipeline. It matters
    because a stale sidecar beside the ``.md`` is indistinguishable from a fresh one to
    anyone inspecting the folder, and an earlier revision read it back as *authoritative PDF
    bookmarks*, which suppressed printed-TOC detection and reported the previous document's
    outline for the current one.
    """
    output_file.with_name(OUTLINE_NAME).unlink(missing_ok=True)
    output_file.with_name(BOOKMARKS_NAME).unlink(missing_ok=True)


def clear_prior_outputs(output_file: Path) -> None:
    """Remove a previous convert's sibling sidecars and ``Chunks/`` folder
    (including ``catalog.json``) beside ``output_file``, so a reconvert cannot reuse
    stale ranges or chunk files.
    """
    _remove_sidecars(output_file)
    chunks_dir = output_file.parent / CHUNKS_DIRNAME
    if chunks_dir.exists():
        shutil.rmtree(chunks_dir)


def _record_cut_keys(
    records: list[dict],
    lines: list[str],
    toc_range: tuple[int, int] | None,
    index: LineIndex,
) -> frozenset:
    """Normalized title keys of outline records that resolve to exactly one eligible body
    line -- not an ATX heading, not inside ``toc_range`` -- via
    :func:`bookmarks.resolve_record_line` (exact page when the record's page is trusted).
    These become sub-chunk cut points for sections Docling left without an ATX sub-heading.
    """
    if not records:
        return frozenset()
    exclude = {
        line_index for line_index, line in enumerate(lines) if _match_heading(line) is not None
    }
    if toc_range is not None:
        exclude |= set(range(toc_range[0], toc_range[1] + 1))
    keys: set[str] = set()
    for record in records:
        if resolve_record_line(index, record, exclude=exclude) is not None:
            key = normalize_title(record.get("title") or "")
            if key:
                keys.add(key)
    return frozenset(keys)


class ChunkingSummary(NamedTuple):
    """Summary from :func:`write_chunk_files`."""

    chunks_dir: Path | None
    chunked: bool
    chunk_count: int
    toc_source: str
    logs: str


def write_chunk_files(
    markdown_content: str,
    output_file: Path,
    filename: str,
    max_tokens: int = DEFAULT_MAX_TOKENS,
    *,
    min_structure_tokens: int = DEFAULT_MIN_STRUCTURE_TOKENS,
) -> ChunkingSummary:
    """Write ``output_file`` (``.md``) and a sibling ``Chunks/`` tree.

    Sole disk writer for parse outputs (:func:`parse_document.parse_document` and
    :func:`chunk_file`). ``markdown_content`` must already be cleaned.

    Always writes ``Chunks/outline.json``. If the document is below
    ``min_structure_tokens``, chunking is skipped (outline only). Otherwise the body is
    split by headings up to ``max_tokens``, with References/Appendix as one final chunk.

    @param markdown_content: cleaned Markdown
    @param output_file: path of the ``.md`` to write
    @param filename: original upload name (stored as ``fileId``)
    @param max_tokens: max tokens per chunk before further splitting
    @param min_structure_tokens: below this, leave the document unchunked
    @return: :class:`ChunkingSummary`
    """
    tree = build_chunk_tree(
        markdown_content,
        filename,
        output_file,
        max_tokens,
        min_structure_tokens,
    )
    # write markdown file
    output_file.parent.mkdir(parents=True, exist_ok=True)
    output_file.write_text(tree["markdown"], encoding="utf-8")

    chunks_dir = output_file.parent / CHUNKS_DIRNAME
    if chunks_dir.exists():
        shutil.rmtree(chunks_dir)
    chunks_dir.mkdir(parents=True, exist_ok=True)
    # write outline.json file
    _write_json(chunks_dir / OUTLINE_NAME, tree["outline"])
    _remove_sidecars(output_file)

    toc_source = tree["outline"].get("toc_source")
    chunk_count = len(tree["chunks"])

    if not tree["chunked"]:
        tokens = count_tokens(tree["markdown"])
        return ChunkingSummary(
            chunks_dir=None,
            chunked=False,
            chunk_count=0,
            toc_source=toc_source,
            logs=(
                f"Skipped chunking '{output_file.name}' "
                f"({tokens} tokens < {min_structure_tokens} min_structure_tokens); "
                f"recorded chunked=false, toc_source={toc_source} in "
                f"{CHUNKS_DIRNAME}/{OUTLINE_NAME}"
            ),
        )

    for chunk in tree["chunks"]:
        (chunks_dir / chunk["file"]).write_text(chunk["text"] + "\n", encoding="utf-8")
    # write catalog.json file
    _write_json(chunks_dir / CATALOG_NAME, tree["catalog"])
    return ChunkingSummary(
        chunks_dir=chunks_dir,
        chunked=True,
        chunk_count=chunk_count,
        toc_source=toc_source,
        logs=(
            f"Split '{output_file.name}' into {chunk_count} chunk file(s) in "
            f"'{CHUNKS_DIRNAME}/' (toc_source={toc_source})"
        ),
    )


def build_chunk_tree(
    markdown_content: str,
    filename: str,
    markdown_path: Path,
    max_tokens: int,
    min_structure_tokens: int,
) -> dict[str, Any]:
    """Analyse and split an already-cleaned document into its chunk tree.

    Writes nothing. Callers that need the tree on disk go through :func:`write_chunk_files`.

    @param markdown_content: the full Markdown document, already cleaned
    @param filename: the original input file name (with extension), recorded as ``fileId``
    @param markdown_path: path of the ``.md`` (sibling ``.pdf`` supplies bookmarks in
        :func:`derive_outline`)
    @param max_tokens: target maximum tokens per chunk
    @param min_structure_tokens: leave the document unchunked below this size
    @return: ``{"markdown", "chunked", "outline", "catalog", "chunks", "records"}``, where
        ``chunks`` is a list of ``{"file", "text"}`` in document order, ``catalog`` is ``None``
        when the document was left unchunked, and ``records`` is the outline the document was
        actually analysed against — PDF bookmarks when available, otherwise whatever was
        harvested from the printed TOC (empty when the size gate skipped detection)
    """
    md_file, outline, toc_records, line_index = derive_outline(
        markdown_content,
        markdown_path,
        min_structure_tokens,
    )

    # The size gate is the pipeline's single binary routing decision, recorded as ``chunked``
    # in the outline — which is always produced, even when chunking is skipped, so downstream
    # routing has a uniform answer. ``derive_outline`` returns no fields when it gated out, so
    # stamp the identity/defaults here to keep both paths' outlines the same shape.
    tokens = count_tokens(md_file)
    to_be_chunked = tokens >= min_structure_tokens
    outline.setdefault("toc_source", "none")
    outline.setdefault("toc", [])
    outline.setdefault("tokens", tokens)
    outline["chunked"] = to_be_chunked
    outline["fileId"] = filename

    if not to_be_chunked:
        # Say *why* it is unchunked (deliberate send-it-whole below the size gate).
        outline["unchunkedReason"] = UNCHUNKED_BELOW_THRESHOLD
        return {
            "markdown": md_file,
            "chunked": False,
            "outline": outline,
            "catalog": None,
            "chunks": [],
            "records": toc_records,
        }

    # One shared split of the full document, reused by every detection pass below
    # instead of each re-splitting the same (potentially large) document on "\n".
    md_lines = md_file.split("\n")
    # Page furniture, identified once for the whole document: a per-part view cannot tell a
    # running header from a heading, because within one page each appears exactly once.
    repeated = repeated_lines(md_lines)

    toc_start = outline.get("tocStartLine")
    toc_end = outline.get("tocEndLine")
    toc_range = (toc_start, toc_end) if isinstance(toc_start, int) and isinstance(toc_end, int) \
        else None
    backmatter_line = outline.get("backmatterLine")
    if not isinstance(backmatter_line, int):
        backmatter_line = None
    main_lines = md_lines[:backmatter_line] if backmatter_line is not None else md_lines
    backmatter_text = (
        "\n".join(md_lines[backmatter_line:]).strip() if backmatter_line is not None else None
    )
    boundary_level = _min_heading_level(main_lines)

    catalog_chunks: list[dict] = []
    chunks: list[dict] = []
    next_id = 1

    # Unite consecutive top-level sections up to the token budget, then split only
    # those united parts that are still over budget (a single oversized section).
    top_chunks = _split_into_top_chunks(main_lines, boundary_level)
    top_texts = [chunk["text"] for chunk in top_chunks if chunk["text"]]
    packed = _pack_blocks(top_texts, max_tokens) if top_texts else []
    packed = _move_trailing_page_markers(packed)
    # Prefer Chunk-0 when the document has a leading preamble; otherwise start at 1.
    first_number = 0 if (top_chunks and top_chunks[0]["number"] == 0) else 1
    split_level = boundary_level if boundary_level is not None else 0
    # Size gate already returned above when derive_outline skipped the index.
    assert line_index is not None
    cut_keys = _record_cut_keys(toc_records, md_lines, toc_range, line_index)

    def add(name: str, text: str, heading: list[str], is_appendix: bool) -> None:
        nonlocal next_id
        chunks.append({"file": name, "text": text})
        catalog_chunks.append({
            "chunk_id": f"chunk{next_id:03d}",
            "file": name,
            "heading": heading,
            "summary": "",
            "rubric_tags": [],
            "questions_answered": [],
            "extraction_hints": [],
            "pages": _pages_in(text),
            "length": len(text),
            "isAppendix": is_appendix,
        })
        next_id += 1

    for offset, packed_text in enumerate(packed):
        number = first_number + offset
        if count_tokens(packed_text) > max_tokens:
            parts = _split_oversized(packed_text, split_level, max_tokens, cut_keys)
        else:
            parts = [packed_text]
        parts = _merge_heading_only_parts(parts)
        parts = _move_trailing_page_markers(_merge_small_text_tails(parts, MIN_TAIL_TOKENS))

        single_part = len(parts) == 1
        for part_index, part_text in enumerate(parts, start=1):
            name = f"Chunk-{number}.md" if single_part else f"Chunk-{number}.{part_index}.md"
            previous_heading = catalog_chunks[-1]["heading"] if catalog_chunks else None
            if first_number == 0 and not catalog_chunks:
                # The preamble chunk is labelled DEFAULT_HEADING whatever it contains
                heading = [DEFAULT_HEADING]
            else:
                # Everything else gets its real heading
                heading = _part_heading(part_text, previous_heading, repeated)
            add(name, part_text, heading, False)

    if backmatter_text:
        add(f"Chunk-{first_number + len(packed)}.md", backmatter_text,
            _backmatter_heading(backmatter_text), True)

    return {
        "markdown": md_file,
        "chunked": True,
        "outline": outline,
        "catalog": {"fileId": filename, "chunks": catalog_chunks},
        "chunks": chunks,
        "records": toc_records,
    }


def chunk_file(
    file_path: str,
    max_tokens: int = DEFAULT_MAX_TOKENS,
    *,
    min_structure_tokens: int = DEFAULT_MIN_STRUCTURE_TOKENS,
    filename: str | None = None,
) -> dict[str, Any]:
    """Split one already-parsed Markdown file into its chunk tree.

    "Already-parsed" means written by one of the converters, which is what makes the file
    already cleaned — nothing in the chunker cleans. Pointed at a hand-written ``.md``, this
    chunks it as-is.

    Documents under ``min_structure_tokens`` are not chunked (``chunks`` is 0),
    but the decision is still recorded in ``Chunks/outline.json`` (``chunked: false``).

    @param file_path: path to the parsed ``.md``
    @param max_tokens: target maximum tokens per chunk file
    @param min_structure_tokens: skip chunking when the document is smaller than this
    @param filename: original upload name for ``fileId`` / headers; defaults to the ``.md`` name
    @return: summary dict ``{"chunks", "logs"}``
    @raise FileNotFoundError: when the file does not exist
    """
    path = Path(file_path)
    if not path.is_file():
        raise FileNotFoundError(f"File does not exist: {path}")

    markdown = path.read_text(encoding="utf-8", errors="replace")
    summary = write_chunk_files(
        markdown,
        path,
        filename if filename is not None else path.name,
        max_tokens=max_tokens,
        min_structure_tokens=min_structure_tokens,
    )
    return {
        "chunks": summary.chunk_count,
        "logs": summary.logs,
    }


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Light chunker for a single parsed Markdown file."
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
