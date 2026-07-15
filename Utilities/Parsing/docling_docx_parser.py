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

from docling_error_detection import ensure_conversion_ok
from docling_section_splitter import write_section_files
from markdown_cleanup import clean_markdown

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
) -> str:
    """
    Convert a DOCX file to Markdown and return the text.

    @param input_path: path to the source .docx file
    @param converter: optional reusable converter instance
    @return: cleaned Markdown text
    """
    active_converter = converter if converter is not None else get_docx_converter()
    result = active_converter.convert(str(input_path))
    ensure_conversion_ok(result)
    return clean_markdown(result.document.export_to_markdown())


def convert_docx(input_path: Path, output_file: Path, *, split_sections: bool = False) -> None:
    """
    Convert a DOCX file to Markdown and write it to output_file.

    @param input_path: path to the source .docx file
    @param output_file: path where Markdown output is written
    @param split_sections: also write per-section .md files and catalog.json beside output_file
    """
    t0 = perf_counter()

    converter = get_docx_converter()

    t1 = perf_counter()

    try:
        markdown_content = convert_docx_to_markdown(input_path, converter=converter)
    except RuntimeError as exc:
        print(f"Conversion failed: {exc}", file=sys.stderr)
        sys.exit(1)

    t2 = perf_counter()
    t3 = perf_counter()

    with open(output_file, "w", encoding="utf-8") as f:
        f.write(markdown_content)

    t4 = perf_counter()

    if split_sections:
        sections_dir = write_section_files(markdown_content, output_file, input_path.name)

    t5 = perf_counter()

    print(f"Markdown length: {len(markdown_content):,} characters")

    print("\n=== Timing ===")
    print(f"Converter init:      {t1 - t0:.2f}s")
    print(f"Document convert:    {t2 - t1:.2f}s")
    print(f"Markdown export:     {t3 - t2:.2f}s")
    print(f"File write:          {t4 - t3:.2f}s")
    if split_sections:
        print(f"Section split:       {t5 - t4:.2f}s")
        print(f"Sections written to {sections_dir}")
    print(f"Total:               {t5 - t0:.2f}s")
