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

"""Flatten dot-leader markdown tables typical of table-of-contents pages.

Helper module to process Table of Contents (TOC) parsed from PDF tables into rows.
Docling often misparses TOC pages as wide, sparse multi-column markdown tables.
This module detects those TOC-like tables on the first 10 PDF pages, rewrites each
row as a single plain-text line (dropping duplicate and empty cells), and
normalizes long dot-leader runs into `` - ``. The sections below describe when
that cleanup runs, how TOC tables are recognized, and how dot leaders are
normalized.

When it runs
------------
Called from docling_pdf_parser during PDF conversion on pages 1-9 only
(page_no < TOC_CLEANUP_MAX_PAGE, where TOC_CLEANUP_MAX_PAGE = 10).
DOCX conversion does not apply TOC cleanup.

How a table is found
--------------------
1. Scan the page markdown line by line.
2. Group consecutive markdown table lines (lines that start and end with |).
3. Parse each row into cells by splitting on |.

How a table is classified as TOC
--------------------------------
A table block is treated as a TOC when all of the following hold:

- At least 2 columns (column_count >= 2).
- At least 2 data rows contain a dot leader: a cell matching ``\\.{4,}``
  (4 or more consecutive dots).
- Separator rows (e.g. |---|---|) are excluded from detection.

Column count then refines the decision:

- 2-column tables: if the rules above pass, the table is a TOC.
- 3+ column tables: additionally require at least 2 dot-leader cells total
  across all data rows (_TOC_MIN_DOT_CELLS = 2).

There is no heading check (e.g. "Table of Contents") and no page-number
pattern check; detection is purely structural (markdown table + dot leaders).

What happens after detection
----------------------------
For each non-separator row in a detected TOC table:

1. Collect non-empty cell values left to right.
2. Drop duplicate values (same text in multiple columns).
3. Join the remaining values with a space into one plain-text line.
4. Run dot-leader cleanup on that line (see below).
5. Emit each flattened row separated by a blank line from the next.

Dot-leader cleanup
------------------
Applied to every non-table line as it is emitted, and to each flattened TOC row
after step 3 above. Any run of more than 4 consecutive dots (5 or more) is
replaced with a spaced dash: `` - ``. Runs of 4 dots or fewer are left
unchanged.

What is not considered TOC
--------------------------
- Tables with fewer than 2 columns.
- Tables with fewer than 2 rows containing dot leaders.
- Non-table content (headings, paragraphs, etc.) — still receives dot-leader
  cleanup even though it is not flattened.
- Pages 10 and above.
- Plain 2-column tables without dot leaders (e.g. | A | B |).
"""

import re

TOC_CLEANUP_MAX_PAGE = 10

_TOC_DOT_CELL = re.compile(r"\.{4,}")
_TOC_MIN_DOT_CELLS = 2
_CONSECUTIVE_DOTS = re.compile(r"\.{5,}")


def _cleanup_dots(line: str) -> str:
    """Replace runs of more than 4 consecutive dots with ' - '."""
    return _CONSECUTIVE_DOTS.sub(" - ", line)


def _is_markdown_table_line(line: str) -> bool:
    stripped = line.strip()
    return stripped.startswith("|") and stripped.endswith("|")


def _parse_markdown_table_row(line: str) -> list[str]:
    inner = line.strip()
    if not inner.startswith("|"):
        return []
    inner = inner[1:]
    if inner.endswith("|"):
        inner = inner[:-1]
    return [cell.strip() for cell in inner.split("|")]


def _is_table_separator_row(cells: list[str]) -> bool:
    return bool(cells) and all(re.fullmatch(r"[\s\-:]+", cell or "") for cell in cells)


def _is_toc_table(rows: list[list[str]]) -> bool:
    if not rows:
        return False
    column_count = max(len(row) for row in rows)
    if column_count < 2:
        return False

    data_rows = [row for row in rows if not _is_table_separator_row(row)]
    if not data_rows:
        return False

    dot_rows = sum(
        1
        for row in data_rows
        if any(_TOC_DOT_CELL.search(cell) for cell in row)
    )
    if dot_rows < 2:
        return False

    if column_count == 2:
        return True

    dot_cells = sum(
        1
        for row in data_rows
        for cell in row
        if _TOC_DOT_CELL.search(cell)
    )
    return dot_cells >= _TOC_MIN_DOT_CELLS


def _toc_row_to_line(cells: list[str]) -> str:
    seen: set[str] = set()
    parts: list[str] = []
    for cell in cells:
        value = cell.strip()
        if not value or value in seen:
            continue
        seen.add(value)
        parts.append(value)
    return " ".join(parts)


def _convert_toc_table(table_lines: list[str]) -> list[str] | None:
    rows: list[list[str]] = []
    for line in table_lines:
        cells = _parse_markdown_table_row(line)
        if cells:
            rows.append(cells)
    if not _is_toc_table(rows):
        return None

    converted: list[str] = []
    for cells in rows:
        if _is_table_separator_row(cells):
            continue
        line = _toc_row_to_line(cells)
        line = _cleanup_dots(line)
        if line:
            converted.append(line)
    return converted or None


def cleanup_toc_tables(md: str) -> str:
    """Flatten wide dot-leader tables (typical TOCs) into one line per row."""
    if not md:
        return md or ""

    lines = md.split("\n")
    output: list[str] = []
    index = 0

    while index < len(lines):
        if not _is_markdown_table_line(lines[index]):
            output.append(_cleanup_dots(lines[index]))
            index += 1
            continue

        table_start = index
        while index < len(lines) and _is_markdown_table_line(lines[index]):
            index += 1

        table_lines = lines[table_start:index]
        converted = _convert_toc_table(table_lines)
        if converted is None:
            output.extend(table_lines)
            continue

        if output and output[-1].strip():
            output.append("")
        for toc_index, toc_line in enumerate(converted):
            if toc_index > 0:
                output.append("")
            output.append(toc_line)
        if index < len(lines) and lines[index].strip():
            output.append("")

    return "\n".join(output)
