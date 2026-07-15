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
Split an already-produced Markdown document into per-section files.

Given the final Markdown that :mod:`docling_pdf_parser` / :mod:`docling_docx_parser`
writes for a document, this module lays out a browsable per-file folder under a shared
``Sections`` root next to that ``.md`` file::

    protocol.md
    Sections/
        protocol/
            catalog.json
            Section-0.md        (everything before the first level-1 heading, if any)
            Section-1.md
            Section-2.1.md      (a section larger than the token budget, split into parts)
            Section-2.2.md

Section boundaries are the document's own headings, read literally from the Markdown text
(not from Docling's heading metadata). The boundary is the shallowest heading level present:
level-1 (``#``) when the document has any, otherwise the first (topmost) heading level it
does have. Content before the first boundary heading, if any, becomes ``Section-0``. A
section larger than :data:`DEFAULT_MAX_TOKENS` is split into ``Section-<n>.<k>`` parts:
sub-headings (the shallowest level deeper than the boundary) are honoured first, and any
resulting part still over budget is then split at paragraph (blank-line) boundaries. A
text-only tail part smaller than :data:`MIN_TAIL_TOKENS` is not cut off — it is folded back
into the preceding part even if that pushes it over the budget.

Every entry carries a non-empty ``heading``. The very first emitted section is always recorded
as :data:`DEFAULT_HEADING`, regardless of its content. Every other section is named from its
own headings: the level of its first (beginning) heading is taken, and every heading at that
level or one level below is listed comma-separated (``#`` markers stripped; deeper headings
ignored). A text-only part copies the heading of the entry before it.

``catalog.json`` is a single object listing every emitted section file in order::

    {
      "fileId": "protocol.pdf",
      "sections": [
        {
          "section_id": "s001",
          "file": "Section-1.md",
          "heading": "Introduction",
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
empty here so they can be filled in later. ``pages`` lists the PDF page numbers referenced within a
section (from the ``<PDF Page N>`` markers the PDF parser emits); it is empty for DOCX.
``length`` is the character count of the section file's content.

Token counts use a cheap character-based heuristic (``len(text) // 4``); no ML tokenizer
is loaded.

Two entry points cover the two flows:

* :func:`write_section_files` — one Markdown document; used by ``docling_parser.py
  --split-sections`` right after parsing.
* :func:`chunk_answer_folder` — a whole CARDS answer folder: the ``Sections`` root is
  wiped and every per-source ``.md`` is split in turn. Invoked in-process from the
  Docling daemon (``POST /chunk``) or as a standalone CLI fallback:
  ``python docling_section_splitter.py <answer_folder>``.
"""

from __future__ import annotations

import argparse
import json
import re
import shutil
import sys
from pathlib import Path
from typing import Any

# Default maximum tokens per section file. A section larger than this is split into parts.
DEFAULT_MAX_TOKENS = 2000

# A text-only continuation part smaller than this is folded back into the preceding part
# instead of being cut off into its own file, even when that pushes the preceding part over
# the token budget.
MIN_TAIL_TOKENS = 500

# Heading recorded for the leading section (content before the first heading) and any other
# section that has no heading of its own.
DEFAULT_HEADING = "General Information"

# Name of the per-document catalog file written into the sections folder.
CATALOG_NAME = "catalog.json"

# Name of the shared root folder holding one per-source-file sections subfolder each.
SECTIONS_DIRNAME = "Sections"

# Files in an answer folder that are outputs of other steps, never split inputs
# (aggregated.md is no longer written, but legacy folders may still contain one).
NON_INPUT_NAMES = {"aggregated.md"}

# Any ATX Markdown heading line; group 1 is the '#' run, group 2 is the heading text.
_HEADING = re.compile(r"^(#{1,6})(?!#)\s+(.*\S)\s*$")

# The "<PDF Page N>" markers the PDF parser emits between page batches.
_PAGE_MARKER = re.compile(r"<PDF\s+Page\s+(\d+)>", re.IGNORECASE)


def _count_tokens(text: str) -> int:
    """Estimate the token count of a string with a cheap character-based heuristic."""
    return len(text) // 4


def _heading_level(line: str) -> int | None:
    """Return the heading level (number of leading ``#``) of a line, or ``None`` if it is
    not a Markdown heading."""
    match = _HEADING.match(line)
    return len(match.group(1)) if match else None


def _heading_text(line: str) -> str | None:
    """Return a heading line's text (``#`` markers stripped), or ``None`` if not a heading."""
    match = _HEADING.match(line)
    return match.group(2).strip() if match else None


def _min_heading_level(text: str, deeper_than: int = 0) -> int | None:
    """Return the shallowest heading level appearing in ``text`` that is deeper than
    ``deeper_than``, or ``None`` if no such heading exists."""
    best: int | None = None
    for line in text.split("\n"):
        level = _heading_level(line)
        if level is not None and level > deeper_than and (best is None or level < best):
            best = level
    return best


def _pages_in(text: str) -> list[int]:
    """Return the sorted, de-duplicated PDF page numbers referenced in a string."""
    pages: set[int] = set()
    for match in _PAGE_MARKER.finditer(text or ""):
        pages.add(int(match.group(1)))
    return sorted(pages)


def _split_into_top_sections(markdown_content: str) -> list[dict]:
    """Split the document at its shallowest heading level.

    The section boundary is the shallowest heading level present in the document: level-1
    (``#``) when the document has any, otherwise the first (topmost) heading level it does
    have. A document with no headings at all is returned as a single ``number == 0`` section.

    @param markdown_content: the full Markdown document
    @return: sections in document order, each ``{"number", "heading", "level", "text"}``;
        content before the first boundary heading (if any) is section ``number == 0`` with an
        empty heading, and the remaining sections are numbered from 1
    """
    boundary_level = _min_heading_level(markdown_content)
    if boundary_level is None:
        text = markdown_content.strip()
        return [{"number": 0, "heading": "", "level": 0, "text": text}] if text else []

    preamble_lines: list[str] = []
    sections: list[dict] = []
    current: dict | None = None

    for line in markdown_content.split("\n"):
        if _heading_level(line) == boundary_level:
            if current is not None:
                sections.append(current)
            current = {"heading": _HEADING.match(line).group(2).strip(), "lines": [line]}
        elif current is None:
            preamble_lines.append(line)
        else:
            current["lines"].append(line)

    if current is not None:
        sections.append(current)

    result: list[dict] = []
    preamble_text = "\n".join(preamble_lines).strip()
    if preamble_text:
        result.append({"number": 0, "heading": "", "level": boundary_level, "text": preamble_text})
    for section_number, section in enumerate(sections, start=1):
        result.append({
            "number": section_number,
            "heading": section["heading"],
            "level": boundary_level,
            "text": "\n".join(section["lines"]).strip(),
        })
    return result


def _subsection_blocks(section_text: str, boundary_level: int) -> list[str]:
    """Split a section's text at its shallowest sub-heading level.

    The first block holds the section's own boundary heading and any lead-in text before the
    first sub-heading; each subsequent block is one sub-section. A section with no sub-headings
    (no heading deeper than ``boundary_level``) yields a single block (the whole text).
    """
    sub_level = _min_heading_level(section_text, deeper_than=boundary_level)
    if sub_level is None:
        stripped = section_text.strip()
        return [stripped] if stripped else []

    blocks: list[str] = []
    current: list[str] = []
    for line in section_text.split("\n"):
        if _heading_level(line) == sub_level and current:
            blocks.append("\n".join(current).strip())
            current = [line]
        else:
            current.append(line)
    if current:
        blocks.append("\n".join(current).strip())
    return [block for block in blocks if block]


def _split_by_paragraphs(text: str, max_tokens: int) -> list[str]:
    """Split text into parts no larger than the budget at blank-line (paragraph) boundaries.

    A single paragraph larger than the budget is kept whole (nothing is split mid-paragraph).
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
        parts.append(current)
        current = paragraph
    if current is not None:
        parts.append(current)
    return parts


def _pack_blocks(blocks: list[str], max_tokens: int) -> list[str]:
    """Unite consecutive blocks into parts as large as the budget allows."""
    parts: list[str] = []
    current: str | None = None
    for block in blocks:
        candidate = block if current is None else current + "\n\n" + block
        if current is None or _count_tokens(candidate) <= max_tokens:
            current = candidate
            continue
        parts.append(current)
        current = block
    if current is not None:
        parts.append(current)
    return parts


def _split_oversized(section_text: str, boundary_level: int, max_tokens: int) -> list[str]:
    """Split an over-budget section into parts.

    Sub-headings (the shallowest heading level deeper than ``boundary_level``) are honoured
    first: consecutive sub-sections are united up to the budget. If the section has no
    sub-headings, or a united part is still over budget, that piece is split at paragraph
    boundaries.
    """
    blocks = _subsection_blocks(section_text, boundary_level)
    packed = _split_by_paragraphs(section_text, max_tokens) if len(blocks) <= 1 \
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
    A part that carries any heading (a sub-section) is never merged, and the merge is applied
    even when it pushes the preceding part over the token budget.
    """
    merged: list[str] = []
    for part in parts:
        if merged and _min_heading_level(part) is None and _count_tokens(part) < min_tokens:
            merged[-1] = merged[-1].rstrip() + "\n\n" + part.lstrip()
        else:
            merged.append(part)
    return merged


def _part_heading(part_text: str, previous_heading: str | None) -> str:
    """Derive the catalog heading for one section-file part.

    The level of the part's first (beginning) heading is taken, and every heading at that level
    or one level below it is collected and joined comma-separated (``#`` markers stripped);
    headings deeper than one level below are ignored. A part with no heading copies the heading
    of the entry before it, and :data:`DEFAULT_HEADING` is used only when the beginning heading
    is missing and there is no preceding entry to copy from.
    """
    beginning_level: int | None = None
    headings: list[str] = []
    for line in part_text.split("\n"):
        level = _heading_level(line)
        if level is None:
            continue
        if beginning_level is None:
            beginning_level = level
        if beginning_level <= level <= beginning_level + 1:
            text = _heading_text(line)
            if text:
                headings.append(text)
    if headings:
        return ", ".join(headings)
    return previous_heading or DEFAULT_HEADING


def _catalog_entry(section_id: str, file_name: str, heading: str, pages: list[int], length: int) -> dict:
    """Build one ``catalog.json`` section entry, with empty summary/tag/question/hint fields."""
    return {
        "section_id": section_id,
        "file": file_name,
        "heading": heading,
        "summary": "",
        "rubric_tags": [],
        "questions_answered": [],
        "extraction_hints": [],
        "pages": pages,
        "length": length,
    }


def write_section_files(
    markdown_content: str,
    output_file: Path,
    filename: str,
    max_tokens: int = DEFAULT_MAX_TOKENS,
) -> Path:
    """Write per-section Markdown files and a ``catalog.json`` beside ``output_file``.

    @param markdown_content: the full Markdown document already written to ``output_file``
    @param output_file: the main ``.md`` file; its sections land in ``Sections/<stem>/`` beside it
    @param filename: the original input file name (with extension), recorded as ``fileId``
    @param max_tokens: maximum tokens per section file before it is split into parts
    @return: the path to the created sections folder
    """
    sections_dir = output_file.parent / SECTIONS_DIRNAME / output_file.stem
    if sections_dir.exists():
        shutil.rmtree(sections_dir)
    sections_dir.mkdir(parents=True, exist_ok=True)

    catalog_sections: list[dict] = []
    next_id = 1

    for section in _split_into_top_sections(markdown_content):
        number = section["number"]
        boundary_level = section["level"]
        text = section["text"]

        if _count_tokens(text) > max_tokens:
            parts = _split_oversized(text, boundary_level, max_tokens)
        else:
            parts = [text]
        parts = _merge_small_text_tails(parts, MIN_TAIL_TOKENS)

        single_part = len(parts) == 1
        for part_index, part_text in enumerate(parts, start=1):
            name = f"Section-{number}.md" if single_part else f"Section-{number}.{part_index}.md"
            (sections_dir / name).write_text(part_text + "\n", encoding="utf-8")
            if not catalog_sections:
                # The very first section is always the default header, regardless of content.
                heading = DEFAULT_HEADING
            else:
                heading = _part_heading(part_text, catalog_sections[-1]["heading"])
            catalog_sections.append(
                _catalog_entry(f"s{next_id:03d}", name, heading, _pages_in(part_text), len(part_text))
            )
            next_id += 1

    catalog = {"fileId": filename, "sections": catalog_sections}
    (sections_dir / CATALOG_NAME).write_text(
        json.dumps(catalog, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )
    return sections_dir


def _input_files(folder: Path) -> list[Path]:
    return sorted(
        path for path in folder.glob("*.md")
        if path.is_file() and path.name.lower() not in NON_INPUT_NAMES
    )


def _section_count(sections_dir: Path) -> int:
    """Number of section entries in a freshly written sections folder's catalog."""
    catalog = json.loads((sections_dir / CATALOG_NAME).read_text(encoding="utf-8"))
    return len(catalog.get("sections", []))


def chunk_answer_folder(
    folder: str,
    max_tokens: int = DEFAULT_MAX_TOKENS,
) -> dict[str, Any]:
    """Split every per-source-file Markdown in an answer folder into its section tree.

    Returns a summary dict: ``{"files", "sections", "logs"}``. Raises
    :class:`FileNotFoundError` when the folder or its inputs are missing and
    :class:`RuntimeError` when no input could be split.
    """
    log_lines: list[str] = []

    folder_path = Path(folder)
    if not folder_path.is_dir():
        raise FileNotFoundError(f"Answer folder does not exist: {folder_path}")

    inputs = _input_files(folder_path)
    if not inputs:
        raise FileNotFoundError(f"No per-file Markdown to chunk in: {folder_path}")

    # Wipe the whole Sections root so subfolders of removed/renamed source files never linger.
    sections_root = folder_path / SECTIONS_DIRNAME
    if sections_root.exists():
        shutil.rmtree(sections_root)

    files = 0
    sections = 0
    for input_path in inputs:
        try:
            markdown = input_path.read_text(encoding="utf-8", errors="replace")
            sections_dir = write_section_files(
                markdown, input_path, input_path.name, max_tokens=max_tokens
            )
            count = _section_count(sections_dir)
            log_lines.append(
                f"Split '{input_path.name}' into {count} section file(s) "
                f"in '{SECTIONS_DIRNAME}/{sections_dir.name}'"
            )
            files += 1
            sections += count
        except Exception as exc:  # noqa: BLE001 — one bad file must not abort the whole answer
            log_lines.append(f"Failed to split '{input_path.name}': {exc}")

    if files == 0:
        raise RuntimeError(
            f"Failed to split any Markdown files in {folder_path} ({len(inputs)} input(s))"
        )

    return {
        "files": files,
        "sections": sections,
        "logs": "\n".join(log_lines),
    }


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Light section splitter for a CARDS proposal answer folder."
    )
    parser.add_argument(
        "answer_folder",
        help="Path to a parse folder containing per-source-file .md",
    )
    parser.add_argument(
        "--max-tokens",
        type=int,
        default=DEFAULT_MAX_TOKENS,
        help=f"Maximum tokens per section file (default: {DEFAULT_MAX_TOKENS})",
    )
    args = parser.parse_args()

    try:
        summary = chunk_answer_folder(
            folder=args.answer_folder,
            max_tokens=args.max_tokens,
        )
    except (FileNotFoundError, RuntimeError) as exc:
        print(str(exc), file=sys.stderr, flush=True)
        sys.exit(1)

    if summary["logs"]:
        print(summary["logs"], flush=True)
    print(
        f"Created {summary['sections']} section file(s) for {summary['files']} input file(s).",
        flush=True,
    )


if __name__ == "__main__":
    main()
