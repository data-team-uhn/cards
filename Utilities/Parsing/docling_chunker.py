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
Section-aware chunker for the CARDS proposal chat feature.

Runs the Docling HybridChunker over the per-source-file Markdown that CARDS writes
for one proposal answer and lays the result out as a browsable tree under a
``ChatChunks`` subfolder of that answer's parse folder::

    <answer>/
        aggregated.md            (input written by CARDS, not touched here)
        apps.md                  (per-source-file Markdown, one per uploaded file)
        ChatChunks/
            catalog.json         (one entry per File-* folder)
            File-apps/
                catalog.json     (one entry per Section*.md file)
                Sections-1-2-3.md   (consecutive heading-sections united to a token budget)
                Sections-4.md
                Section5.1.md       (a single section larger than the budget, split in parts)
                Section5.2.md
        aggregated_chunked.md    (the aggregate rebuilt with embedded markers)

Sections are the deepest level: each ``.md`` file directly under ``File-*`` holds one
section unit and there is no chunk level. Consecutive heading-sections are united into a
single ``Sections-<i-j-...>.md`` file as large as the token budget (:data:`DEFAULT_MAX_TOKENS`)
allows; a single heading-section larger than the budget is split into ``Section<n>.<k>.md``
parts, honouring sub-headings first and then paragraph boundaries.

Each ``catalog.json`` is an array of objects shaped like::

    {"id": "Sections-1-2-3", "summary": "", "pages": [88, 89], "sectionId": null, "fileId": "apps"}

``summary`` is always left empty here so it can be filled in by an LLM later.

This module is invoked two ways, both producing identical output:

* In-process from the Docling daemon (``POST /chunk`` -> :func:`chunk_answer_folder`).
* As a standalone CLI fallback: ``python docling_chunker.py <answer_folder>``.
"""

from __future__ import annotations

import argparse
import html
import json
import re
import shutil
import sys
from pathlib import Path
from typing import Any, Callable, Optional

from docling.chunking import HybridChunker
from docling.document_converter import DocumentConverter
from docling_core.transforms.serializer.markdown import MarkdownDocSerializer

# Name of the subfolder that holds the chat-oriented chunk tree.
CHAT_CHUNKS_DIRNAME = "ChatChunks"

# Name of the aggregate rebuilt with embedded markers.
CHUNKED_AGGREGATE_NAME = "aggregated_chunked.md"

# Per-folder catalog file name.
CATALOG_NAME = "catalog.json"

# Files in the answer folder that are outputs, never chunk inputs.
NON_INPUT_NAMES = {"aggregated.md", CHUNKED_AGGREGATE_NAME}

# The single, hardcoded tokenizer model id used for all chat chunking. Chunks are sized against this
# tokenizer; it is intentionally not configurable so every proposal is chunked identically.
MODEL_ID = "Qwen/Qwen3-8B"

# Default maximum tokens per section unit. Consecutive heading-sections are united up to this token
# budget so the output is not fragmented into many tiny files; a single heading-section larger than
# this is split into parts.
DEFAULT_MAX_TOKENS = 2000

# Deepest Markdown heading level emitted for the heading-path prefix.
MAX_HEADING_LEVEL = 6

# Matches the "PDF Page 12" page markers that the PDF parser emits as headings, so they
# can be treated as page boundaries rather than as document sections.
_PAGE_MARKER = re.compile(r"PDF\s+Page\s+(\d+)", re.IGNORECASE)


def _log_to(messages: list[str]) -> Callable[[str], None]:
    def log(message: str) -> None:
        messages.append(message)
    return log


def _is_page_heading(heading: str) -> bool:
    return bool(_PAGE_MARKER.search(heading or ""))


def _pages_in(*texts: str) -> list[int]:
    pages: set[int] = set()
    for text in texts:
        for match in _PAGE_MARKER.finditer(text or ""):
            pages.add(int(match.group(1)))
    return sorted(pages)


def _union_pages(*page_lists: list[int]) -> list[int]:
    pages: set[int] = set()
    for page_list in page_lists:
        pages.update(page_list)
    return sorted(pages)


def _format_pages(pages: list[int]) -> str:
    """Render a sorted page list as a compact ``88-90`` range or ``88,90,91`` list."""
    if not pages:
        return ""
    if len(pages) > 1 and pages == list(range(pages[0], pages[-1] + 1)):
        return f"{pages[0]}-{pages[-1]}"
    return ",".join(str(page) for page in pages)


def _build_chunker(max_tokens: int, log: Callable[[str], None]) -> HybridChunker:
    """Build a HybridChunker using the hardcoded :data:`MODEL_ID` tokenizer. Raises a
    :class:`RuntimeError` when that tokenizer cannot be loaded."""
    try:
        from docling_core.transforms.chunker.tokenizer.huggingface import HuggingFaceTokenizer
        from transformers import AutoTokenizer

        hf_tokenizer = AutoTokenizer.from_pretrained(MODEL_ID)
    except Exception as exc:  # noqa: BLE001 — surface a single clear error, no silent degradation
        raise RuntimeError(f"Could not load tokenizer '{MODEL_ID}': {exc}") from exc
    log(f"Using tokenizer '{MODEL_ID}' with max_tokens={max_tokens}")
    tokenizer = HuggingFaceTokenizer(tokenizer=hf_tokenizer, max_tokens=max_tokens)
    return HybridChunker(tokenizer=tokenizer, merge_peers=True)


def _token_counter(chunker: HybridChunker) -> Callable[[str], int]:
    """Return a ``text -> token count`` function from the chunker's tokenizer, falling back to a
    character-based estimate if the tokenizer is not accessible."""
    tokenizer = getattr(chunker, "tokenizer", None)
    if tokenizer is not None and hasattr(tokenizer, "count_tokens"):
        return tokenizer.count_tokens
    return lambda text: len(text) // 4


def _is_table_item(item: Any) -> bool:
    """Whether a chunk's doc item is a table."""
    return "table" in str(getattr(item, "label", "")).lower()


def _table_markdown(dl_doc: Any, ref: str, log: Callable[[str], None]) -> Optional[str]:
    """Export the table referenced by ``ref`` (e.g. ``#/tables/3``) as a whole Markdown grid.

    Resolving via the document (rather than the chunk's doc item) returns the complete table even
    when the chunker split it across several chunks, which is what keeps a table inseparable.
    """
    match = re.fullmatch(r"#/tables/(\d+)", ref or "")
    if match is None:
        return None
    try:
        return dl_doc.tables[int(match.group(1))].export_to_markdown(dl_doc).strip()
    except Exception as exc:  # noqa: BLE001 — a bad table must not abort the file
        log(f"Could not export table '{ref}' to Markdown: {exc}")
        return None


def _resolve_doc_item(dl_doc: Any, item: Any) -> Any:
    """Resolve a chunk's doc item to the real document item it references. Chunks hold reference
    wrappers that the serializer renders as ``<!-- missing-text -->`` in isolation, so we look the
    item up by its ``self_ref`` (e.g. ``#/texts/82``) before serializing."""
    ref = getattr(item, "self_ref", "") or ""
    match = re.fullmatch(r"#/(\w+)/(\d+)", ref)
    if match is not None:
        collection = getattr(dl_doc, match.group(1), None)
        if collection is not None and 0 <= int(match.group(2)) < len(collection):
            return collection[int(match.group(2))]
    return item


def _serialize_item(dl_doc: Any, serializer: Any, item: Any, log: Callable[[str], None]) -> str:
    """Serialize one non-table doc item to Markdown (lists, bold, italics, code, captions preserved).
    Falls back to plain text when serialization yields nothing usable; genuinely content-less items
    (e.g. images) that render as a bare ``<!-- ... -->`` comment then resolve to empty and are dropped."""
    resolved = _resolve_doc_item(dl_doc, item)
    try:
        text = serializer.serialize(item=resolved).text.strip()
    except Exception as exc:  # noqa: BLE001 — one item must not abort the chunk
        log(f"Could not serialize item to Markdown ({exc}); using plain text")
        text = ""
    if not text or re.fullmatch(r"<!--.*-->", text, flags=re.DOTALL):
        text = (getattr(resolved, "text", "") or getattr(item, "text", "") or "").strip()
    return text


def _table_chunk_body(dl_doc: Any, serializer: Any, items: list[Any], seen_tables: set,
    log: Callable[[str], None]) -> Optional[str]:
    """Build the body of a table-bearing chunk: surrounding content as Markdown (in order), each
    not-yet-seen table rendered as a whole Markdown grid. Returns ``None`` when the chunk carries no
    new body content (e.g. it is only the continuation of a table already emitted)."""
    body: list[str] = []
    for item in items:
        if _is_table_item(item):
            ref = getattr(item, "self_ref", "")
            if ref and ref not in seen_tables:
                table_md = _table_markdown(dl_doc, ref, log)
                if table_md:
                    body.append(table_md)
        else:
            text = _serialize_item(dl_doc, serializer, item, log)
            if text:
                body.append(text)
    return "\n\n".join(body) if body else None


def _real_headings(headings: list[str]) -> list[str]:
    """The heading path with the synthetic ``PDF Page N`` markers stripped out."""
    return [heading for heading in headings if not _is_page_heading(heading)]


def _heading_levels(input_path: Path) -> dict[str, int]:
    """Map each heading's text to the literal Markdown level (count of leading ``#``) it has in the
    source file. Read from the source rather than Docling because Docling renormalizes levels (it
    shifts, for example, a source ``### Abstract`` down to level 2); we want the document's own levels."""
    levels: dict[str, int] = {}
    pattern = re.compile(r"^(#{1,6})\s+(.*\S)\s*$")
    try:
        content = input_path.read_text(encoding="utf-8", errors="replace")
    except OSError:
        return levels
    for line in content.splitlines():
        match = pattern.match(line)
        if match:
            heading = html.unescape(match.group(2)).strip()
            if heading and heading not in levels:
                levels[heading] = len(match.group(1))
    return levels


def _heading_block(headings: list[str], levels: dict[str, int], start: int = 0) -> str:
    """Render a heading path as Markdown headings, restoring the ``#`` markers (and page markers) that
    the chunker otherwise flattens to plain lines. The level is the source file's own (via
    :func:`_heading_levels`); the path depth is the fallback. ``start`` skips the leading headings
    already shown by a preceding chunk, so a shared parent heading is not repeated."""
    lines = []
    for index in range(start, len(headings)):
        text = html.unescape(headings[index]).lstrip("#").strip()
        if not text:
            continue
        level = levels.get(text, index + 1)
        level = max(1, min(level, MAX_HEADING_LEVEL))
        lines.append("#" * level + " " + text)
    return "\n".join(lines)


def _sections_from_headings(records: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """Group chunk records into sections that follow the Markdown heading structure: a new section
    opens at each new top-level heading. Records with no heading — a preamble before the first
    heading, or a page-break continuation whose heading Docling dropped — stay in the current
    section, so a section that spans pages is not fragmented.

    @param records each ``{"text", "pages", "headings"}`` in document order, ``headings`` being the
        real heading path with page markers stripped
    @return sections, each ``{"title", "pages", "chunks": [{"text", "pages"}]}``
    """
    sections: list[dict[str, Any]] = []
    current_top: Any = None
    for record in records:
        real = record["real"]
        top = real[0] if real else None
        if not sections or (top is not None and top != current_top):
            sections.append({"title": top, "records": []})
            current_top = top
        sections[-1]["records"].append(record)
    return sections


def _common_prefix_len(first: list[str], second: list[str]) -> int:
    """Number of leading heading entries two paths share, used to avoid repeating a parent heading
    that the previous chunk already printed."""
    count = 0
    for left, right in zip(first, second):
        if left != right:
            break
        count += 1
    return count


def _render_record(record: dict[str, Any], levels: dict[str, int],
    previous_path: Optional[list[str]]) -> str:
    """Render a record as ``heading prefix + body``, skipping the heading prefix shared with the
    previous record so a merged chunk shows each shared parent heading only once."""
    path = record["path"]
    start = _common_prefix_len(previous_path, path) if previous_path else 0
    prefix = _heading_block(path, levels, start)
    body = record["body"]
    return (prefix + "\n\n" + body) if prefix else body


def _merge_chunks(records: list[dict[str, Any]], levels: dict[str, int],
    count_tokens: Callable[[str], int], max_tokens: int = DEFAULT_MAX_TOKENS) -> list[dict[str, Any]]:
    """Unite consecutive records into chunks as large as the token budget allows, so small adjacent
    sub-sections (e.g. 2.1 and 2.2) are not emitted as separate tiny chunks. A shared parent heading
    is printed once; pages are unioned. A single record larger than the budget simply becomes its own
    chunk (nothing is split)."""
    chunks: list[dict[str, Any]] = []
    text: Optional[str] = None
    pages: list[int] = []
    path: Optional[list[str]] = None
    for record in records:
        if text is not None:
            candidate = text + "\n\n" + _render_record(record, levels, path)
            if count_tokens(candidate) <= max_tokens:
                text = candidate
                pages = _union_pages(pages, record["pages"])
                path = record["path"]
                continue
            chunks.append({"text": text, "pages": pages})
        text = _render_record(record, levels, None)
        pages = list(record["pages"])
        path = record["path"]
    if text is not None:
        chunks.append({"text": text, "pages": pages})
    return chunks


def _render_records(records: list[dict[str, Any]], levels: dict[str, int]) -> tuple[str, list[int]]:
    """Render all of one heading-section's records to a single Markdown string, sharing heading
    prefixes across consecutive records so a repeated parent heading is printed once, and return that
    text together with the union of the records' pages."""
    parts: list[str] = []
    previous_path: Optional[list[str]] = None
    for record in records:
        parts.append(_render_record(record, levels, previous_path))
        previous_path = record["path"]
    pages = _union_pages(*[record["pages"] for record in records]) if records else []
    return "\n\n".join(parts), pages


def _split_by_paragraphs(text: str, pages: list[int], count_tokens: Callable[[str], int],
    max_tokens: int) -> list[dict[str, Any]]:
    """Split a Markdown string into parts no larger than the token budget at blank-line (paragraph)
    boundaries. A single paragraph larger than the budget is kept whole (nothing is split mid-paragraph).
    Every part inherits the same page list, since paragraph-level page attribution is not tracked."""
    parts: list[dict[str, Any]] = []
    current: Optional[str] = None
    for paragraph in text.split("\n\n"):
        if paragraph.strip() == "":
            continue
        candidate = paragraph if current is None else current + "\n\n" + paragraph
        if current is None or count_tokens(candidate) <= max_tokens:
            current = candidate
            continue
        parts.append({"text": current, "pages": list(pages)})
        current = paragraph
    if current is not None:
        parts.append({"text": current, "pages": list(pages)})
    return parts


def _split_section(section: dict[str, Any], levels: dict[str, int],
    count_tokens: Callable[[str], int], max_tokens: int) -> list[dict[str, Any]]:
    """Split one oversized heading-section into ``Section<n>.<k>`` parts. Sub-headings are honoured
    first by uniting the section's records up to the budget (:func:`_merge_chunks`); any resulting part
    that is still too large is then split at paragraph boundaries."""
    subparts: list[dict[str, Any]] = []
    for part in _merge_chunks(section["records"], levels, count_tokens, max_tokens):
        if count_tokens(part["text"]) > max_tokens:
            subparts.extend(_split_by_paragraphs(part["text"], part["pages"], count_tokens, max_tokens))
        else:
            subparts.append(part)
    units: list[dict[str, Any]] = []
    for part_index, subpart in enumerate(subparts, start=1):
        units.append({
            "id": f"Section{section['index']}.{part_index}",
            "title": section["title"],
            "text": subpart["text"],
            "pages": subpart["pages"],
        })
    return units


def _finish_whole_unit(accumulator: dict[str, Any]) -> dict[str, Any]:
    """Turn an accumulator of one or more whole heading-sections into a ``Sections-<i-j-...>`` unit."""
    joined = "-".join(str(index) for index in accumulator["indices"])
    return {
        "id": f"Sections-{joined}",
        "title": accumulator["title"],
        "text": accumulator["text"],
        "pages": accumulator["pages"],
    }


def _pack_sections(sections: list[dict[str, Any]], levels: dict[str, int],
    count_tokens: Callable[[str], int], max_tokens: int) -> list[dict[str, Any]]:
    """Pack consecutive heading-sections into output units no larger than the token budget. A section
    that alone exceeds the budget breaks the current run and is split via :func:`_split_section`;
    every other section is united with its neighbours into a ``Sections-<...>`` unit.

    @param sections rendered heading-sections, each ``{"index", "title", "text", "pages", "records"}``
    @return the output units in order, each ``{"id", "title", "text", "pages"}``
    """
    units: list[dict[str, Any]] = []
    accumulator: Optional[dict[str, Any]] = None
    for section in sections:
        if count_tokens(section["text"]) > max_tokens:
            if accumulator is not None:
                units.append(_finish_whole_unit(accumulator))
                accumulator = None
            units.extend(_split_section(section, levels, count_tokens, max_tokens))
            continue
        if accumulator is None:
            accumulator = _start_accumulator(section)
            continue
        candidate = accumulator["text"] + "\n\n" + section["text"]
        if count_tokens(candidate) <= max_tokens:
            accumulator["text"] = candidate
            accumulator["indices"].append(section["index"])
            accumulator["pages"] = _union_pages(accumulator["pages"], section["pages"])
        else:
            units.append(_finish_whole_unit(accumulator))
            accumulator = _start_accumulator(section)
    if accumulator is not None:
        units.append(_finish_whole_unit(accumulator))
    return units


def _start_accumulator(section: dict[str, Any]) -> dict[str, Any]:
    """Begin a new whole-section accumulator seeded with one heading-section."""
    return {
        "indices": [section["index"]],
        "title": section["title"],
        "text": section["text"],
        "pages": list(section["pages"]),
    }


def _chunk_file(input_path: Path, chunker: HybridChunker, max_tokens: int,
    log: Callable[[str], None]) -> dict[str, Any]:
    """Chunk one Markdown file into an in-memory file/section structure.

    Returns a dict: ``{"fileId", "folder", "sections": [...], "pages": [...]}`` where each
    section is a leaf output unit ``{"id", "title", "text", "pages"}`` (no chunk level).
    """
    file_id = input_path.stem
    dl_doc = DocumentConverter().convert(source=str(input_path)).document
    serializer = MarkdownDocSerializer(doc=dl_doc)
    levels = _heading_levels(input_path)

    records: list[dict[str, Any]] = []
    seen_tables: set = set()
    for chunk in chunker.chunk(dl_doc=dl_doc):
        meta = getattr(chunk, "meta", None)
        headings = list(getattr(meta, "headings", None) or [])
        pages = _union_pages(_pages_in(*headings), _pages_in(chunk.text))
        items = list(getattr(meta, "doc_items", None) or [])
        table_refs = [getattr(item, "self_ref", "") for item in items if _is_table_item(item)]
        if not table_refs:
            # Plain chunk: the chunker's own Markdown body (bold, italics, lists, code preserved).
            body: Optional[str] = chunk.text
        else:
            # Table-bearing chunk: render whole tables, dropping continuations of split tables.
            body = _table_chunk_body(dl_doc, serializer, items, seen_tables, log)
            seen_tables.update(ref for ref in table_refs if ref)
        if body and body.strip():
            records.append({
                "body": body.strip(),
                "pages": pages,
                "path": headings,
                "real": _real_headings(headings),
            })

    count_tokens = _token_counter(chunker)
    heading_sections = _sections_from_headings(records)
    rendered: list[dict[str, Any]] = []
    for section_index, section in enumerate(heading_sections, start=1):
        text, pages = _render_records(section["records"], levels)
        rendered.append({
            "index": section_index,
            "title": section["title"],
            "text": text,
            "pages": pages,
            "records": section["records"],
        })
    units = _pack_sections(rendered, levels, count_tokens, max_tokens)

    file_pages = _union_pages(*[unit["pages"] for unit in units]) if units else []
    log(f"Chunked '{input_path.name}': {len(heading_sections)} heading-section(s) "
        f"packed into {len(units)} section unit(s)")
    return {
        "fileId": file_id,
        "folder": f"File-{file_id}",
        "sections": units,
        "pages": file_pages,
    }


def _catalog_entry(item_id: str, pages: list[int], section_id: Optional[str], file_id: str) -> dict[str, Any]:
    return {
        "id": item_id,
        "summary": "",
        "pages": pages,
        "sectionId": section_id,
        "fileId": file_id,
    }


def _write_json(path: Path, payload: Any) -> None:
    path.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def _write_tree(chat_dir: Path, files: list[dict[str, Any]]) -> None:
    """Write the File-*/Section*.md tree and a catalog.json into the ChatChunks root and every File
    folder. Sections are the deepest level: each section unit is one ``.md`` file directly under its
    File folder, and the File folder's catalog has one entry per such file."""
    file_catalog: list[dict[str, Any]] = []
    for file_data in files:
        file_id = file_data["fileId"]
        file_dir = chat_dir / file_data["folder"]
        file_dir.mkdir(parents=True, exist_ok=True)
        file_catalog.append(_catalog_entry(file_data["folder"], file_data["pages"], None, file_id))

        section_catalog: list[dict[str, Any]] = []
        for section in file_data["sections"]:
            (file_dir / f"{section['id']}.md").write_text(section["text"] + "\n", encoding="utf-8")
            section_catalog.append(_catalog_entry(section["id"], section["pages"], None, file_id))
        _write_json(file_dir / CATALOG_NAME, section_catalog)

    _write_json(chat_dir / CATALOG_NAME, file_catalog)


def _marker(**fields: Any) -> str:
    parts = []
    for key, value in fields.items():
        if value is None or value == "":
            continue
        parts.append(f"{key}={value}")
    return "<!-- " + " ".join(parts) + " -->"


def _write_chunked_aggregate(target: Path, files: list[dict[str, Any]]) -> None:
    """Rebuild the aggregate as Markdown with embedded file/section reference markers."""
    lines: list[str] = []
    for file_data in files:
        file_id = file_data["fileId"]
        lines.append(f"# File: {file_id} {_marker(file_id=file_id, pages=_format_pages(file_data['pages']))}")
        lines.append("")
        for section in file_data["sections"]:
            title = section["title"] or section["id"]
            marker = _marker(file_id=file_id, section_id=section["id"], pages=_format_pages(section["pages"]))
            lines.append(f"## {title} {marker}")
            lines.append("")
            lines.append(section["text"].strip())
            lines.append("")
    target.write_text("\n".join(lines).rstrip() + "\n", encoding="utf-8")


def _input_files(folder: Path) -> list[Path]:
    return sorted(
        path for path in folder.glob("*.md")
        if path.is_file() and path.name.lower() not in NON_INPUT_NAMES
    )


def chunk_answer_folder(
    folder: str,
    max_tokens: int = DEFAULT_MAX_TOKENS,
) -> dict[str, Any]:
    """Chunk every per-source-file Markdown in an answer folder into the ChatChunks tree.

    Returns a summary dict: ``{"files", "sections", "logs"}``. Raises
    :class:`FileNotFoundError` when the folder or its inputs are missing.
    """
    messages: list[str] = []
    log = _log_to(messages)

    folder_path = Path(folder)
    if not folder_path.is_dir():
        raise FileNotFoundError(f"Answer folder does not exist: {folder_path}")

    inputs = _input_files(folder_path)
    if not inputs:
        raise FileNotFoundError(f"No per-file Markdown to chunk in: {folder_path}")

    chunker = _build_chunker(max_tokens, log)

    files: list[dict[str, Any]] = []
    for input_path in inputs:
        try:
            files.append(_chunk_file(input_path, chunker, max_tokens, log))
        except Exception as exc:  # noqa: BLE001 — one bad file must not abort the whole answer
            log(f"Failed to chunk '{input_path.name}': {exc}")

    if not files:
        raise RuntimeError(
            f"Failed to chunk any Markdown files in {folder_path} ({len(inputs)} input(s))"
        )

    chat_dir = folder_path / CHAT_CHUNKS_DIRNAME
    if chat_dir.exists():
        shutil.rmtree(chat_dir)
    chat_dir.mkdir(parents=True, exist_ok=True)

    _write_tree(chat_dir, files)
    _write_chunked_aggregate(folder_path / CHUNKED_AGGREGATE_NAME, files)
    log(f"Wrote ChatChunks tree to {chat_dir} and {CHUNKED_AGGREGATE_NAME}")

    return {
        "files": len(files),
        "sections": sum(len(file_data["sections"]) for file_data in files),
        "logs": "\n".join(messages),
    }


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Section-aware Docling HybridChunker for a CARDS proposal answer folder."
    )
    parser.add_argument(
        "answer_folder",
        help="Path to a parse folder containing per-source-file .md and aggregated.md",
    )
    parser.add_argument(
        "--max-tokens",
        type=int,
        default=DEFAULT_MAX_TOKENS,
        help=f"Maximum tokens per section unit (default: {DEFAULT_MAX_TOKENS})",
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
        f"Created {summary['sections']} section unit(s) in {summary['files']} file(s).",
        flush=True,
    )


if __name__ == "__main__":
    main()
