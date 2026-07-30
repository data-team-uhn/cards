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
Detect the section-numbering prefix of a heading and return it as a level vector.

Ported and adapted from the numbering heuristics in krrome/docling-hierarchical-pdf
(``hierarchical/parsers.py``, Apache-2.0), then hardened against the numbering styles seen
across the real REB protocol corpus. A heading's *numbering depth* -- how many components
its leading number has -- is the one hierarchy signal that survives Docling's Markdown
export intact, so it lets the chunker recover the level of a numbered heading that Docling
emitted as bold body text instead of an ATX ``#`` heading.

    "1 Background"        -> [1]           (depth 1)
    "1.0 General Info"    -> [1]           (depth 1 -- a trailing ".0" is a top-level marker)
    "1.2 Methods"         -> [1, 2]        (depth 2)
    "2.3.1.1 Measures"    -> [2, 3, 1, 1]  (depth 4)
    "3. 1. 1 Aim 1"       -> [3, 1, 1]     (depth 3 -- space-mangled numbering still parses)
    "A. Consent"          -> []            (letter: use :func:`letter_numbering`)
    "II. Results"         -> []            (roman: use :func:`roman_numbering`)

:func:`numbering_vector` / :func:`numbering_depth` accept Arabic-decimal only. Letter and
Roman have their own helpers (:func:`letter_numbering`, :func:`roman_numbering`) for callers
that need them (e.g. TOC page tokens). In the protocol corpus, letter/Roman markers appear
almost only as sub-levels nested under a numeric outline, and a bare leading "I"/"V"/"A" of
an ordinary heading word is an easy false positive.
"""

from __future__ import annotations

import re

# Arabic numbering. A separator is a dot or dash, optionally padded with spaces, so both
# "1.2.3" and a space-mangled "3. 1. 1" parse -- but a bare space is NOT a separator, so a
# plain title like "3 5-year survival" does not become [3, 5].
_NUMERIC_SEP = r"\s*[.\-]\s*"
_NUMERIC_MULTI = re.compile(rf"^(\d+(?:{_NUMERIC_SEP}\d+)+)")
# A lone leading integer, only when followed by a separator / bracket / colon / whitespace /
# end -- so "1 Background", "1.", "1:", "1)" match, but "1st" and "1-year" do not.
_NUMERIC_SINGLE = re.compile(r"^\d+(?=[.):\s]|$)")
_NUMERIC_TOKENS = re.compile(r"\d+")

# A single leading letter followed by a separator ("A. ", "b) ", "C - ").
_LETTER_PREFIX = re.compile(r"^([A-Za-z])[.)\s-]+")

# Roman numerals at the start, optionally dotted ("II.", "XI.2"), or a bare run.
_ROMAN_PREFIX = re.compile(r"^((?:[IVXLCDM]+[.\s-])+|[IVXLCDM]+$)", re.IGNORECASE)
_ROMAN_TOKEN = re.compile(r"[IVXLCDM]+", re.IGNORECASE)
_ROMAN_SPLIT = re.compile(r"[.\s-]")
_ROMAN_VALUES = {
    "M": 1000, "CM": 900, "D": 500, "CD": 400, "C": 100, "XC": 90,
    "L": 50, "XL": 40, "X": 10, "IX": 9, "V": 5, "IV": 4, "I": 1,
}


def numerical_numbering(text: str) -> list[int]:
    """The raw Arabic-decimal numbering vector at the start of ``text`` (``[]`` if none).

    Faithful parse -- no normalization: ``"1.0 X"`` -> ``[1, 0]``. Callers that want a
    hierarchy *depth* should go through :func:`numbering_vector` / :func:`numbering_depth`,
    which collapse the ``X.0`` top-level convention (see :func:`_strip_trailing_zeros`).
    """
    stripped = text.strip()
    multi = _NUMERIC_MULTI.match(stripped)
    if multi:
        return [int(token) for token in _NUMERIC_TOKENS.findall(multi.group(1))]
    single = _NUMERIC_SINGLE.match(stripped)
    if single:
        return [int(single.group(0))]
    return []


def letter_numbering(text: str) -> list[int]:
    """The letter-numbering vector (``A``=1, ``b``=2, ...) at the start of ``text``."""
    match = _LETTER_PREFIX.match(text.strip())
    if not match:
        return []
    return [ord(match.group(1).lower()) - ord("a") + 1]


def _roman_to_int(roman: str) -> int:
    """Convert a Roman-numeral token to its integer value."""
    upper = roman.upper()
    index = 0
    result = 0
    while index < len(upper):
        pair = upper[index:index + 2]
        if len(pair) == 2 and pair in _ROMAN_VALUES:
            result += _ROMAN_VALUES[pair]
            index += 2
        else:
            result += _ROMAN_VALUES[upper[index]]
            index += 1
    return result


def roman_numbering(text: str) -> list[int]:
    """The Roman-numeral numbering vector at the start of ``text`` (``[]`` if none).

    Examples: ``"II. Results"`` -> ``[2]``, ``"XIII Introduction"`` -> ``[13]``.
    """
    match = _ROMAN_PREFIX.match(text.strip())
    if not match:
        return []
    vector: list[int] = []
    for token in _ROMAN_SPLIT.split(match.group(0)):
        if not token:
            continue
        if _ROMAN_TOKEN.fullmatch(token):
            vector.append(_roman_to_int(token))
        elif token.isdigit():
            vector.append(int(token))
    return vector


def _strip_trailing_zeros(vector: list[int]) -> list[int]:
    """Drop trailing ``0`` components (keeping at least one), so the pervasive clinical-
    protocol convention where ``"1.0"`` / ``"10.0"`` denotes a *top-level* section maps to
    depth 1, while ``"1.1"`` stays depth 2. ``"0.0"`` -> ``[0]`` (still depth 1).
    """
    end = len(vector)
    while end > 1 and vector[end - 1] == 0:
        end -= 1
    return vector[:end]


def numbering_vector(text: str) -> list[int]:
    """The Arabic-decimal heading-numbering vector for ``text`` (``[]`` when it has none),
    normalized for hierarchy depth (trailing ``.0`` collapsed).

    Letter and Roman prefixes are ignored here; use :func:`letter_numbering` /
    :func:`roman_numbering` when those styles are needed.

    @param text: the heading text (``#``/``**`` markers already stripped)
    @return: the numbering vector, or ``[]``
    """
    numeric = numerical_numbering(text)
    if numeric:
        return _strip_trailing_zeros(numeric)
    return []


def numbering_depth(text: str) -> int:
    """How many levels deep ``text``'s Arabic-decimal numbering prefix is (``0`` when none).

    @param text: the heading text (``#``/``**`` markers already stripped)
    @return: the numbering depth, or ``0``
    """
    return len(numbering_vector(text))
