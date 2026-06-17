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
from markdown_cleanup import clean_markdown


def convert_docx(input_path: Path, output_file: Path) -> None:
    """
    Convert a DOCX file to Markdown and write it to output_file.

    @param input_path: path to the source .docx file
    @param output_file: path where Markdown output is written
    """
    t0 = perf_counter()

    converter = DocumentConverter(
        format_options={
            InputFormat.DOCX: WordFormatOption()
        }
    )

    t1 = perf_counter()

    result = converter.convert(str(input_path))

    t2 = perf_counter()

    try:
        ensure_conversion_ok(result)
    except RuntimeError as exc:
        print(f"Conversion failed: {exc}", file=sys.stderr)
        sys.exit(1)

    markdown_content = clean_markdown(result.document.export_to_markdown())

    t3 = perf_counter()

    with open(output_file, "w", encoding="utf-8") as f:
        f.write(markdown_content)

    t4 = perf_counter()

    print(f"Status: {getattr(result, 'status', 'unknown')}")
    print(f"Markdown length: {len(markdown_content):,} characters")

    print("\n=== Timing ===")
    print(f"Converter init:      {t1 - t0:.2f}s")
    print(f"Document convert:    {t2 - t1:.2f}s")
    print(f"Markdown export:     {t3 - t2:.2f}s")
    print(f"File write:          {t4 - t3:.2f}s")
    print(f"Total:               {t4 - t0:.2f}s")
