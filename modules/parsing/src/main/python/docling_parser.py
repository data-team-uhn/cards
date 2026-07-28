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

"""CLI entry point: convert PDF or DOCX files to Markdown using Docling."""

import argparse
import sys
from pathlib import Path

import docling_config  # noqa: F401 — apply shared Docling settings on import

from docling_batch_sizing import GB_PER_WORKER, MAX_BATCH_PAGES, positive_int
from docling_docx_parser import convert_docx
from docling_pdf_parser import convert_pdf
from markdown_markers import SUPPORTED_SUFFIXES
from toc_and_appendix_detection import DEFAULT_MIN_STRUCTURE_TOKENS


def parse_args():
    parser = argparse.ArgumentParser(
        description="Convert PDF or DOCX files to Markdown using Docling."
    )
    parser.add_argument("input_file", help="Path to a .pdf or .docx file")
    parser.add_argument(
        "--workers",
        type=positive_int,
        default=None,
        metavar="N",
        help=(
            "parallel PDF worker processes (default: auto from logical CPU cores "
            f"and RAM budget / {GB_PER_WORKER:.1f} GB per worker)"
        ),
    )
    parser.add_argument(
        "--batch-pages",
        type=positive_int,
        default=None,
        metavar="N",
        help=(
            "pages per worker batch (default: auto from page count and workers, "
            f"max {MAX_BATCH_PAGES})"
        ),
    )
    parser.add_argument(
        "--chunk",
        action="store_true",
        help=(
            "also write per-chunk .md files and catalog.json into a "
            "Chunks/ folder beside the output .md (skipped when the document is "
            f"under --min-structure-tokens, default {DEFAULT_MIN_STRUCTURE_TOKENS})"
        ),
    )
    parser.add_argument(
        "--min-structure-tokens",
        type=positive_int,
        default=DEFAULT_MIN_STRUCTURE_TOKENS,
        metavar="N",
        help=(
            "skip TOC/appendix marking and chunking when document tokens (len//4) "
            f"are below this (default: {DEFAULT_MIN_STRUCTURE_TOKENS})"
        ),
    )
    parser.add_argument(
        "--source-file",
        default=None,
        metavar="NAME",
        help=(
            "original upload basename for the <!-- source_file: ... --> header "
            "(defaults to the input file name)"
        ),
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    input_path = Path(args.input_file)

    if not input_path.exists():
        print(f"File not found: {input_path}")
        sys.exit(1)

    suffix = input_path.suffix.lower()

    if suffix not in SUPPORTED_SUFFIXES:
        print(f"Unsupported file type: {suffix}")
        print("Only .pdf and .docx are supported.")
        sys.exit(1)

    output_file = input_path.with_suffix(".md")

    if suffix == ".pdf":
        convert_pdf(
            input_path,
            output_file,
            batch_pages=args.batch_pages,
            workers=args.workers,
            chunk=args.chunk,
            min_structure_tokens=args.min_structure_tokens,
            source_file=args.source_file,
        )
    else:
        convert_docx(
            input_path,
            output_file,
            chunk=args.chunk,
            min_structure_tokens=args.min_structure_tokens,
            source_file=args.source_file,
        )

    print(f"\nSaved to {output_file}")


if __name__ == "__main__":
    main()
