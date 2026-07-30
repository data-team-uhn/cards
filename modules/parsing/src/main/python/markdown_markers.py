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
The Markdown markers and size limits shared by every stage of the parsing pipeline.
"""

from __future__ import annotations

import re


def page_marker(page_number: int) -> str:
    """The canonical page marker for ``page_number``: ``<!-- page: 12 -->``.

    @param page_number: the 1-based page number
    @return: the marker text, without surrounding newlines
    """
    return f"<!-- page: {page_number} -->"


def page_marker_pattern(number: str) -> str:
    """The page marker as a pattern fragment, with ``number`` substituted for the
    page-number part.

    @param number: the sub-pattern to use for the page number
    @return: the marker pattern fragment, unanchored
    """
    return rf"<!-- page: {number} -->"


# A page marker anywhere in a string, page number captured. Use with ``finditer`` to
# collect the pages a block of text refers to.
PAGE_MARKER = re.compile(page_marker_pattern(r"(\d+)"), re.IGNORECASE)

# A page marker alone on its own line, page number captured. The marker's own spacing is
# exact (see :func:`page_marker_pattern`); the ``\s*`` here is only line-level slack for
# indentation or a stray carriage return, and matches whether or not the caller stripped the
# line first.
PAGE_MARKER_LINE = re.compile(rf"^\s*{page_marker_pattern(r'(\d+)')}\s*$", re.IGNORECASE)

# A page marker on its own line *including* the newlines around it, as a single capturing
# group, for ``re.split``. The page number is intentionally NOT captured: ``re.split``
# returns every group, and a second group would break the caller's stride-2 walk over the
# split parts.
PAGE_MARKER_SPLIT = re.compile(rf"(\n{page_marker_pattern(r'\d+')}\n)", re.IGNORECASE)

# A horizontal-rule line ("---", "-----", ...).
RULE_LINE = re.compile(r"^-{3,}$")

# An ATX heading line; group 1 = the '#' run, group 2 = the heading text. The ``(?!#)``
# guard rejects a 7+ '#' run, which is not a heading in Markdown.
HEADING = re.compile(r"^(#{1,6})(?!#)\s+(.*\S)\s*$")

# Maximum words per accepted heading/TOC entry, and maximum characters per word within it.
# A line breaching either is parsing garbage rather than a real heading.
MAX_HEADING_WORDS = 10
MAX_WORD_CHARS = 100

# Minimum characters for a heading extracted from a chunk ('#' markers already stripped).
MIN_HEADING_CHARS = 5

# Input file types Docling itself can convert (after LibreOffice prep).
SUPPORTED_SUFFIXES = (".pdf", ".docx")

# Staged upload types the daemon / CLI accept; ``.doc`` is converted to ``.docx`` first.
INPUT_SUFFIXES = (".pdf", ".docx", ".doc")


def count_tokens(text: str) -> int:
    """Estimate the token count of a string with a cheap character-based heuristic.

    @param text: the string to measure
    @return: the estimated token count
    """
    return len(text) // 4


def within_word_limits(text: str) -> bool:
    """Whether ``text`` is short enough to be a real heading or TOC entry rather than
    parsing garbage: at least one word, at most :data:`MAX_HEADING_WORDS` words, and no
    single word longer than :data:`MAX_WORD_CHARS` characters.

    @param text: the candidate line, markers already stripped
    @return: ``True`` when the line is within both limits
    """
    words = text.split()
    if not words or len(words) > MAX_HEADING_WORDS:
        return False
    return all(len(word) <= MAX_WORD_CHARS for word in words)
