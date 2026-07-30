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

"""Convert DOCX files to Markdown using Docling."""

from pathlib import Path

import docling_config  # noqa: F401 — apply shared Docling settings on import

from docling.datamodel.base_models import InputFormat
from docling.document_converter import DocumentConverter, WordFormatOption

from docling_error_detection import ensure_conversion_ok
from markdown_cleanup import finalize_markdown

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
    return finalize_markdown(
        result.document.export_to_markdown(),
        input_path,
        source_file=source_file,
    )
