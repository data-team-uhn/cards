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

"""Post-processing cleanup for generated markdown output."""

import re
from pathlib import Path

from markdown_markers import PAGE_MARKER_SPLIT

#
# Matches empty Markdown-like headings, decorative lines, symbol-only lines, box-drawing lines.
#
_GARBAGE_LINE = re.compile(
    r"^(?!\s*$)(?!.*[^\W_])(?!.*[|-]).+$",
    re.UNICODE,
)

MIN_RUN_LENGTH = 25

_LINE_NUMBER = re.compile(r"^\d+$")
_IMAGE_PLACEHOLDER = re.compile(r"^\s*<!--\s*image\s*-->\s*$")

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


def cleanup_page_leading_line_numbers(page_md: str) -> str:
    """
    Remove a leading leading line-number block from one page body.

    @param page_md: markdown for a single page body (no page header)
    @return: page markdown with leading line numbers removed when detected
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


def cleanup_leading_line_numbers(md: str) -> str:
    """
    Remove leading line-number blocks from assembled PDF markdown.

    Splits on ``<!-- page: N -->`` markers inserted by ``docling_pdf_parser`` and
    cleans each page body independently.

    @param md: full markdown document
    @return: markdown with detected leading line-number blocks removed
    """
    if not md:
        return md or ""

    parts = PAGE_MARKER_SPLIT.split(md)
    if len(parts) == 1:
        return cleanup_page_leading_line_numbers(md)

    cleaned_parts: list[str] = [parts[0]]
    index = 1
    while index < len(parts):
        cleaned_parts.append(parts[index])
        body = parts[index + 1] if index + 1 < len(parts) else ""
        cleaned_parts.append(cleanup_page_leading_line_numbers(body))
        index += 2
    return "".join(cleaned_parts)


def _escape_comment(value: str) -> str:
    """Escape HTML-comment-hostile sequences (``--`` cannot appear inside an HTML comment)."""
    return value.replace("--", "\u2014")


def source_file_basename(source_file: str) -> str:
    """Return the final component of a client-supplied file name.

    Upload names may originate on an operating system other than the one running this code.
    Normalize Windows separators before handing the value to :class:`Path`, so a Windows path
    received by the Linux daemon cannot leak its directory components into generated metadata.
    """
    return Path(source_file.replace("\\", "/")).name


def resolve_source_file_name(input_path: Path, source_file: str | None = None) -> str:
    """Return the display name for a ``source_file`` header.

    Prefer an explicit original name (e.g. the upload basename) when provided;
    otherwise fall back to the on-disk path name. Always returns a basename so a
    full path cannot leak into the markdown comment.
    """
    if source_file and source_file.strip():
        return source_file_basename(source_file.strip())
    return input_path.name


def source_file_header(source_file: str) -> str:
    """The reserved ``<!-- source_file: ... -->`` header naming a document's original input file.

    Prepended by the PDF/DOCX parsers after cleanup, not by :func:`clean_markdown`.
    """
    return f"<!-- source_file: {_escape_comment(source_file)} -->"


def finalize_markdown(
    raw_markdown: str,
    input_path: Path,
    *,
    source_file: str | None = None,
) -> str:
    """Clean Docling export and prepend the ``source_file`` header.

    Shared by the PDF and DOCX converters so header assembly stays in one place.

    @param raw_markdown: Markdown as exported by Docling (may be empty)
    @param input_path: on-disk path used when ``source_file`` is omitted
    @param source_file: optional original upload name for the header
    @return: cleaned Markdown with a leading ``<!-- source_file: ... -->`` line
    """
    cleaned = clean_markdown(raw_markdown)
    display_name = resolve_source_file_name(input_path, source_file)
    return f"{source_file_header(display_name)}\n{cleaned}"


def clean_markdown(md: str) -> str:
    """
    Collapse blank lines, remove empty headings / image placeholders, and strip
    decorative garbage lines.

    Called exactly once per document, by the converter that produced it — nothing downstream
    re-cleans, and no sentinel is written into the output to enforce that. Every step here is a
    removal or a collapse, so a second call would be harmless anyway.

    @param md: Markdown as exported by Docling, or an empty value
    @return: the cleaned text; ``""`` for empty input
    """
    if not md:
        return ""

    # Special case: cleanup leading line numbers at every line
    without_line_numbers = cleanup_leading_line_numbers(md)
    kept_lines = [
        line
        for line in without_line_numbers.split("\n")
        if not _GARBAGE_LINE.match(line) and not _IMAGE_PLACEHOLDER.match(line)
    ]
    return re.sub(r"\n{3,}", "\n\n", "\n".join(kept_lines)).strip()
