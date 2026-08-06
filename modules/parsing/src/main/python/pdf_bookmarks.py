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
Extract a PDF's embedded bookmark outline into :mod:`bookmarks` records.

Uses ``pypdf`` (already a parser dependency) rather than PyMuPDF. ``reader.outline`` is a
nested list -- a child group follows its parent as a sub-list -- flattened here into ordered
``{"title", "level", "page"}`` records with 1-based pages. ``pypdf`` is imported lazily so
importing this module (e.g. in tests using a fake reader, or in the chunker for a document
with no sibling PDF) does not require it.
"""

from __future__ import annotations

from pathlib import Path, PurePath


def extract_bookmarks(source) -> list[dict]:
    """Flatten a PDF's bookmark outline into ordered ``{title, level, page}`` records.

    ``source`` is a path (opened with pypdf) or an already-constructed reader (duck-typed:
    anything exposing ``outline`` and ``get_destination_page_number``). Returns ``[]`` when
    the PDF has no bookmarks or cannot be read.
    """
    try:
        if isinstance(source, (str, Path, PurePath)):
            from pypdf import PdfReader
            reader = PdfReader(str(source))
        else:
            reader = source
        raw = reader.outline
    except Exception:  # noqa: BLE001 -- any reader/parse failure means "no usable outline"
        return []
    records: list[dict] = []
    _flatten(reader, raw, 1, records)
    return records


def _flatten(reader, items, level: int, out: list[dict]) -> None:
    for item in items:
        if isinstance(item, list):
            _flatten(reader, item, level + 1, out)
            continue
        try:
            title = _title_of(item)
            if not title:
                continue
            out.append({"title": title, "level": level, "page": _page_of(reader, item)})
        except Exception:  # noqa: BLE001 -- skip a single malformed bookmark, keep the rest
            continue


def _title_of(item) -> str:
    title = getattr(item, "title", None)
    return " ".join(str(title).split()).strip() if title is not None else ""


def _page_of(reader, item) -> int | None:
    try:
        index = reader.get_destination_page_number(item)
    except Exception:  # noqa: BLE001
        return None
    return index + 1 if isinstance(index, int) and index >= 0 else None
