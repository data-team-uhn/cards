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

# Converts PDF and DOCX files into Markdown.
# PDF conversion uses Docling's threaded StandardPdfPipeline in a single process.
# Large PDFs are processed in sequential page-range chunks to cap peak memory
# (full-document convert can OOM when many rasterized pages sit in pipeline queues).

import argparse
import gc
import os
import sys
from pathlib import Path
from time import perf_counter

from pypdf import PdfReader

from docling.datamodel.base_models import InputFormat
from docling.datamodel.accelerator_options import AcceleratorOptions
from docling.datamodel.pipeline_options import (
    PdfPipelineOptions,
    TableFormerMode,
    TableStructureOptions,
)
from docling.document_converter import (
    DocumentConverter,
    PdfFormatOption,
    WordFormatOption,
)

_cpu_count = os.cpu_count() or 4
_INFERENCE_THREADS = min(4, max(1, _cpu_count // 2))

# Pages per convert() call. Keeps peak memory bounded while still allowing
# modest batching inside the threaded pipeline.
PDF_PAGE_CHUNK_SIZE = 8

# Limit how many pages can sit between pipeline stages at once (default is 100).
_PIPELINE_QUEUE_MAX_SIZE = 4

# Batch sizes for StandardPdfPipeline threaded layout/table stages.
_LAYOUT_BATCH_SIZE = 2
_TABLE_BATCH_SIZE = 2


def build_pdf_options() -> PdfPipelineOptions:
    """Create PDF pipeline options optimised for speed: no OCR, no images."""
    pdf_options = PdfPipelineOptions()

    # --- Features: disable everything not needed ---
    pdf_options.do_ocr = False
    pdf_options.do_table_structure = True
    pdf_options.do_code_enrichment = False
    pdf_options.do_formula_enrichment = False
    pdf_options.do_picture_classification = False
    pdf_options.do_picture_description = False
    pdf_options.do_chart_extraction = False

    # --- Image/page generation: all off ---
    pdf_options.generate_page_images = False
    pdf_options.generate_picture_images = False
    pdf_options.generate_table_images = False
    pdf_options.generate_parsed_pages = False

    # Use the PDF's embedded text layer directly instead of re-extracting via
    # the layout model. The layout model still runs for structure detection;
    # this only skips the redundant text recognition pass.
    pdf_options.force_backend_text = True

    pdf_options.accelerator_options = AcceleratorOptions(
        num_threads=_INFERENCE_THREADS,
        device="cpu",
    )

    pdf_options.layout_batch_size = _LAYOUT_BATCH_SIZE
    pdf_options.table_batch_size = _TABLE_BATCH_SIZE
    pdf_options.batch_polling_interval_seconds = 0.1
    pdf_options.queue_max_size = _PIPELINE_QUEUE_MAX_SIZE

    # IMPORTANT: no trailing comma here. A trailing comma would create a tuple.
    pdf_options.table_structure_options = TableStructureOptions(
        mode=TableFormerMode.ACCURATE,
        do_cell_matching=False,
    )

    return pdf_options


def build_pdf_converter() -> DocumentConverter:
    """Create a DocumentConverter for PDF processing."""
    return DocumentConverter(
        format_options={
            InputFormat.PDF: PdfFormatOption(
                pipeline_options=build_pdf_options()
            )
        }
    )


def convert_docx(input_path: Path, output_file: Path) -> None:
    t0 = perf_counter()

    converter = DocumentConverter(
        format_options={
            InputFormat.DOCX: WordFormatOption()
        }
    )

    t1 = perf_counter()

    result = converter.convert(str(input_path))

    t2 = perf_counter()

    markdown_content = result.document.export_to_markdown()

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


def convert_pdf(
    input_path: Path,
    output_file: Path,
) -> None:
    reader = PdfReader(str(input_path))
    total_pages = len(reader.pages)

    print(f"Detected {total_pages} pages")
    print(f"Page chunk size: {PDF_PAGE_CHUNK_SIZE}")
    print(f"Inference threads: {_INFERENCE_THREADS}")
    print(f"Pipeline queue max: {_PIPELINE_QUEUE_MAX_SIZE}")
    print(f"Layout batch size: {_LAYOUT_BATCH_SIZE}")
    print(f"Table batch size: {_TABLE_BATCH_SIZE}")

    chunks: list[tuple[int, int]] = []
    for start_page in range(1, total_pages + 1, PDF_PAGE_CHUNK_SIZE):
        end_page = min(start_page + PDF_PAGE_CHUNK_SIZE - 1, total_pages)
        chunks.append((start_page, end_page))

    print(f"Chunks scheduled: {len(chunks)}")

    t0 = perf_counter()

    converter = build_pdf_converter()
    t1 = perf_counter()

    all_markdown: list[str] = []
    total_markdown_chars = 0
    failed_chunks: list[tuple[int, int, str]] = []

    for start_page, end_page in chunks:
        chunk_label = f"pages {start_page}-{end_page}"
        chunk_start = perf_counter()

        try:
            result = converter.convert(
                str(input_path),
                page_range=(start_page, end_page),
            )
        except Exception as exc:
            failed_chunks.append((start_page, end_page, str(exc)))
            print(f"FAILED {chunk_label}: {exc}")
            gc.collect()
            continue

        chunk_elapsed = perf_counter() - chunk_start
        status = str(getattr(result, "status", "unknown"))

        if getattr(result, "document", None) is None:
            error_message = getattr(result, "errors", None) or "No document returned"
            failed_chunks.append((start_page, end_page, str(error_message)))
            print(f"FAILED {chunk_label}: {error_message}")
            gc.collect()
            continue

        md = result.document.export_to_markdown()
        md_len = len(md)

        for page_no in range(start_page, end_page + 1):
            all_markdown.append(f"\n\n---\n\n# PDF Page {page_no}\n\n---\n\n")
        all_markdown.append(md)
        total_markdown_chars += md_len

        print(
            f"Completed {chunk_label}: status={status}, "
            f"markdown={md_len:,} chars, time={chunk_elapsed:.2f}s"
        )

        if result.errors:
            for err in result.errors:
                print(f"  warning: {err.error_message}")

        gc.collect()

    t2 = perf_counter()

    if not all_markdown and failed_chunks:
        print("\nConversion failed for all chunks.")
        for start_page, end_page, message in failed_chunks:
            print(f"  pages {start_page}-{end_page}: {message}")
        sys.exit(1)

    for start_page, end_page, message in failed_chunks:
        for page_no in range(start_page, end_page + 1):
            all_markdown.append(f"\n\n---\n\n# PDF Page {page_no}\n\n---\n\n")
        all_markdown.append(
            f"FAILED TO PROCESS PAGES {start_page}-{end_page}: {message}\n\n"
        )

    with open(output_file, "w", encoding="utf-8") as f:
        f.write("".join(all_markdown))
    t3 = perf_counter()

    print(f"\nMarkdown characters: {total_markdown_chars:,}")
    if failed_chunks:
        print(f"Failed chunks: {len(failed_chunks)}")

    print("\n=== Timing ===")
    print(f"Converter init:      {t1 - t0:.2f}s")
    print(f"Document convert:    {t2 - t1:.2f}s")
    print(f"File write:          {t3 - t2:.2f}s")
    print(f"Total:               {t3 - t0:.2f}s")
    print(f"Chunks attempted:    {len(chunks)}")


def parse_args():
    parser = argparse.ArgumentParser(
        description="Convert PDF or DOCX files to Markdown using Docling."
    )
    parser.add_argument("input_file", help="Path to a .pdf or .docx file")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    input_path = Path(args.input_file)

    if not input_path.exists():
        print(f"File not found: {input_path}")
        sys.exit(1)

    suffix = input_path.suffix.lower()

    if suffix not in [".pdf", ".docx"]:
        print(f"Unsupported file type: {suffix}")
        print("Only .pdf and .docx are supported.")
        sys.exit(1)

    output_file = input_path.with_suffix(".md")

    if suffix == ".pdf":
        convert_pdf(
            input_path,
            output_file,
        )
    elif suffix == ".docx":
        convert_docx(input_path, output_file)

    print(f"\nSaved to {output_file}")


if __name__ == "__main__":
    main()
