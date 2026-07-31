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

"""Shared convert + chunk + write path used by the daemon and the CLI.

LibreOffice prep (when needed) runs first and saves converted files beside the source.
Docling then converts to Markdown in memory. :func:`chunker.write_chunk_files` writes
``{stem}.md`` and ``Chunks/``.
"""

from __future__ import annotations

import threading
from concurrent.futures import ProcessPoolExecutor
from pathlib import Path
from typing import Any, Callable

from docling.document_converter import DocumentConverter

from chunker import DEFAULT_MAX_TOKENS, CHUNKS_DIRNAME, clear_prior_outputs, write_chunk_files
from docling_docx_parser import convert_docx_to_markdown
from docling_pdf_parser import convert_pdf_to_markdown
from libreoffice_convert import prepare_office_document
from markdown_cleanup import source_file_basename
from markdown_markers import INPUT_SUFFIXES, SUPPORTED_SUFFIXES
from toc_and_appendix_detection import DEFAULT_MIN_STRUCTURE_TOKENS

LogFn = Callable[[str], None]


def parse_document(
    input_path: Path,
    *,
    chunk: bool = True,
    max_tokens: int = DEFAULT_MAX_TOKENS,
    min_structure_tokens: int = DEFAULT_MIN_STRUCTURE_TOKENS,
    pdf_executor: ProcessPoolExecutor | None = None,
    pdf_workers: int | None = None,
    docx_lock: threading.Lock | None = None,
    docx_converter: DocumentConverter | None = None,
    log: LogFn | None = None,
) -> dict[str, Any]:
    """LibreOffice prep, Docling convert, then write ``{stem}.md`` + ``Chunks/`` beside the source.

    @param input_path: absolute path to the staged ``.pdf`` / ``.docx`` / ``.doc``
    @param chunk: when False, write the ``.md`` only and clear any stale ``Chunks/``
    @param max_tokens: chunk budget
    @param min_structure_tokens: leave the document unchunked below this size
    @param pdf_executor: warm PDF pool (daemon); ``None`` lets Docling size its own pool
    @param pdf_workers: worker count hint for the PDF pool
    @param docx_lock: optional lock serialising DOCX Docling conversion (daemon)
    @param docx_converter: optional warm DOCX converter (daemon)
    @param log: optional line logger
    @return: summary ``{ok, markdown_path, chunked, chunks_dir, logs, filename}``
    """
    source = Path(input_path)
    if not source.is_file():
        raise FileNotFoundError(f"Document does not exist: {source}")

    suffix = source.suffix.lower()
    if suffix not in INPUT_SUFFIXES:
        raise ValueError(
            f"Unsupported file type: {suffix}; expected one of {', '.join(INPUT_SUFFIXES)}"
        )

    logs: list[str] = []

    def _log(message: str) -> None:
        logs.append(message)
        if log is not None:
            log(message)

    filename = source_file_basename(source.name)
    docling_input = prepare_office_document(source)
    if docling_input != source:
        _log(f"LibreOffice prepared '{docling_input.name}' from '{source.name}'")

    docling_suffix = docling_input.suffix.lower()
    if docling_suffix not in SUPPORTED_SUFFIXES:
        raise ValueError(f"Docling cannot convert {docling_suffix!r} (from '{source.name}')")

    if docling_suffix == ".pdf":
        markdown = convert_pdf_to_markdown(
            docling_input,
            executor=pdf_executor,
            workers=pdf_workers,
            log=_log,
            source_file=filename,
        )
    else:
        if docx_lock is not None:
            with docx_lock:
                markdown = convert_docx_to_markdown(
                    docling_input, converter=docx_converter, source_file=filename
                )
        else:
            markdown = convert_docx_to_markdown(
                docling_input, converter=docx_converter, source_file=filename
            )
        _log(f"Converted DOCX ({len(markdown):,} chars)")

    # Markdown + Chunks live beside the staged source (same stem), not a LibreOffice temp.
    output_md = source.with_suffix(".md")

    if not chunk:
        clear_prior_outputs(output_md)
        output_md.parent.mkdir(parents=True, exist_ok=True)
        output_md.write_text(markdown, encoding="utf-8")
        return {
            "ok": True,
            "markdown_path": str(output_md.resolve()),
            "chunked": False,
            "chunks_dir": None,
            "logs": "\n".join(logs),
            "filename": filename,
        }

    summary = write_chunk_files(
        markdown,
        output_md,
        filename,
        max_tokens=max_tokens,
        min_structure_tokens=min_structure_tokens,
    )
    if summary.logs:
        _log(summary.logs)

    chunks_dir_path = summary.chunks_dir or (output_md.parent / CHUNKS_DIRNAME)
    return {
        "ok": True,
        "markdown_path": str(output_md.resolve()),
        "chunked": summary.chunked,
        "chunks_dir": str(chunks_dir_path.resolve()),
        "logs": "\n".join(logs),
        "filename": filename,
    }
