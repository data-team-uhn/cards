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

"""Convert DOCX files to Markdown using Docling."""

import sys
from pathlib import Path
from time import perf_counter

import docling_config  # noqa: F401 — apply shared Docling settings on import

from docling.datamodel.base_models import InputFormat
from docling.document_converter import DocumentConverter, WordFormatOption

from chunker import clear_prior_outputs, write_chunk_files
from docling_error_detection import ensure_conversion_ok
from markdown_cleanup import clean_markdown, resolve_source_file_name, source_file_header
from markdown_markers import count_tokens
from toc_and_appendix_detection import DEFAULT_MIN_STRUCTURE_TOKENS

_docx_converter: DocumentConverter | None = None


def get_docx_converter() -> DocumentConverter:
    """Return a process-wide DOCX converter, creating it on first use."""
    global _docx_converter
    if _docx_converter is None:
        _docx_converter = DocumentConverter(
            format_options={
                InputFormat.DOCX: WordFormatOption()
            }
        )
    return _docx_converter


def convert_docx_to_markdown(
    input_path: Path,
    *,
    converter: DocumentConverter | None = None,
    source_file: str | None = None,
) -> str:
    """
    Convert a DOCX file to Markdown and return the text.

    @param input_path: path to the source .docx file
    @param converter: optional reusable converter instance
    @param source_file: optional original upload name for the source_file header
        (defaults to ``input_path.name``, which may be a temp basename)
    @return: cleaned Markdown text
    """
    active_converter = converter if converter is not None else get_docx_converter()
    result = active_converter.convert(str(input_path))
    ensure_conversion_ok(result)
    cleaned = clean_markdown(result.document.export_to_markdown())
    display_name = resolve_source_file_name(input_path, source_file)
    return f"{source_file_header(display_name)}\n{cleaned}"


def convert_docx(
    input_path: Path,
    output_file: Path,
    *,
    min_structure_tokens: int = DEFAULT_MIN_STRUCTURE_TOKENS,
) -> None:
    """
    Convert a DOCX file to Markdown and write it, plus its chunk tree, beside ``output_file``.

    Always chunks, mirroring the daemon's ``POST /parse`` (which defaults ``chunk=true``), so the
    CLI and the service produce the same artifacts for the same document — that is what makes the
    two paths comparable when checking they have not drifted apart.

    @param input_path: path to the source .docx file
    @param output_file: path where Markdown output is written
    @param min_structure_tokens: leave the document unchunked below this size, as the daemon does
    """
    t0 = perf_counter()

    converter = get_docx_converter()

    t1 = perf_counter()

    # Drop any previous convert's outline sidecar and Chunks/ before writing anew.
    clear_prior_outputs(output_file)
    try:
        markdown_content = convert_docx_to_markdown(input_path, converter=converter)
    except Exception as exc:
        # Includes RuntimeError from a failed conversion as well as reader errors from an
        # unreadable/corrupt DOCX; surface a clean message instead of a traceback.
        print(f"DOCX conversion failed: {exc}", file=sys.stderr)
        sys.exit(1)

    t2 = perf_counter()

    with open(output_file, "w", encoding="utf-8") as f:
        f.write(markdown_content)

    t3 = perf_counter()

    chunks_dir = write_chunk_files(
        markdown_content,
        output_file,
        resolve_source_file_name(input_path),
        min_structure_tokens=min_structure_tokens,
    )

    t4 = perf_counter()
    tokens = count_tokens(markdown_content)

    print(f"Markdown length: {len(markdown_content):,} characters")
    print(f"Token estimate:  {tokens:,}")

    print("\n=== Timing ===")
    print(f"Converter init:      {t1 - t0:.2f}s")
    print(f"Convert and export:  {t2 - t1:.2f}s")
    print(f"File write:          {t3 - t2:.2f}s")
    print(f"Chunk split:         {t4 - t3:.2f}s")
    if chunks_dir is not None:
        chunk_count = sum(1 for _ in chunks_dir.glob("Chunk-*.md"))
        print(f"Chunks written to {chunks_dir} ({chunk_count} chunk file(s))")
    else:
        print(
            f"Chunking skipped "
            f"({tokens} tokens < {min_structure_tokens} min_structure_tokens)"
        )
    print(f"Total:               {t4 - t0:.2f}s")
