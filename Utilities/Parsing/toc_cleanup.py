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

"""Flatten dot-leader markdown tables typical of table-of-contents pages."""

import re

TOC_CLEANUP_MAX_PAGE = 10

_TOC_DOT_CELL = re.compile(r"\.{4,}")
_TOC_MIN_DOT_CELLS = 2


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
            output.append(lines[index])
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
        output.extend(converted)
        if index < len(lines) and lines[index].strip():
            output.append("")

    return "\n".join(output)
