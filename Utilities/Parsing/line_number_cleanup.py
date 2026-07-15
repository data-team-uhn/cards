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

"""Remove margin line-number columns misparsed by Docling on PDF pages.

Docling sometimes extracts left-margin line numbers as a separate text block at
the top of each page (one integer per line, often separated by blank lines)
instead of keeping them in the margin. This module detects and strips those
blocks from per-page markdown before downstream processing.

Detection heuristic
-------------------
At the start of a page body (after optional leading blank lines), collect a
*run* of lines that contain only digits (``^\\d+$``), allowing blank lines
between them. The run is treated as margin line numbers and removed when:

1. The run has at least ``MIN_RUN_LENGTH`` entries (default 5), and
2. Every value increases by exactly 1 (a consecutive sequence).

Short runs (fewer than ``MIN_RUN_LENGTH``) are kept to avoid stripping small
numbered lists that legitimately appear at the top of a page.

When it runs
------------
Called from ``markdown_cleanup.clean_markdown`` after PDF pages are assembled
(with ``<PDF Page N>`` markers). DOCX conversion is unaffected.
"""

import re

MIN_RUN_LENGTH = 5

_LINE_NUMBER = re.compile(r"^\d+$")
_PAGE_MARKER = re.compile(r"(\n\n---\n\n<PDF Page \d+>\n\n---\n\n)")


def _is_consecutive(values: list[int]) -> bool:
    """Return True when values form a +1 sequence."""
    if len(values) < 2:
        return True
    return all(values[index + 1] - values[index] == 1 for index in range(len(values) - 1))


def _leading_line_number_run(lines: list[str]) -> tuple[list[int], int]:
    """
    Scan lines from the top and return a leading digit-only run.

    @return: (run values, index of first line after the run)
    """
    index = 0
    run: list[int] = []

    while index < len(lines):
        stripped = lines[index].strip()
        if stripped == "":
            index += 1
            continue
        if _LINE_NUMBER.fullmatch(stripped):
            run.append(int(stripped))
            index += 1
            while index < len(lines) and lines[index].strip() == "":
                index += 1
            continue
        break

    return run, index


def cleanup_page_margin_line_numbers(page_md: str) -> str:
    """
    Remove a leading margin line-number block from one page body.

    @param page_md: markdown for a single PDF page (no page header)
    @return: page markdown with margin line numbers removed when detected
    """
    if not page_md:
        return page_md

    lines = page_md.split("\n")
    run, end_index = _leading_line_number_run(lines)
    if len(run) < MIN_RUN_LENGTH or not _is_consecutive(run):
        return page_md

    remaining = lines[end_index:]
    while remaining and remaining[0].strip() == "":
        remaining = remaining[1:]
    return "\n".join(remaining)


def cleanup_margin_line_numbers(md: str) -> str:
    """
    Remove margin line-number blocks from assembled PDF markdown.

    Splits on ``<PDF Page N>`` markers inserted by ``docling_pdf_parser`` and
    cleans each page body independently.

    @param md: full markdown document
    @return: markdown with detected margin line-number blocks removed
    """
    if not md:
        return md or ""

    parts = _PAGE_MARKER.split(md)
    if len(parts) == 1:
        return cleanup_page_margin_line_numbers(md)

    cleaned_parts: list[str] = [parts[0]]
    index = 1
    while index < len(parts):
        cleaned_parts.append(parts[index])
        body = parts[index + 1] if index + 1 < len(parts) else ""
        cleaned_parts.append(cleanup_page_margin_line_numbers(body))
        index += 2
    return "".join(cleaned_parts)
