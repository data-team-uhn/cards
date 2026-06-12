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
# PDF conversion processes explicit page-range batches.
# PDF batches are processed in parallel using separate processes.
# Each PDF batch creates a fresh DocumentConverter to avoid retained backend caches.

import argparse
import gc
import os
import sys
from concurrent.futures import ProcessPoolExecutor, as_completed
from pathlib import Path
from time import perf_counter

from pypdf import PdfReader

from docling.datamodel.base_models import InputFormat
from docling.datamodel.pipeline_options import (
    PdfPipelineOptions,
    TableFormerMode,
    TableStructureOptions,
)
from docling.datamodel.settings import settings
from docling.document_converter import (
    DocumentConverter,
    PdfFormatOption,
    WordFormatOption,
)


PDF_BATCH_PAGES = 5
PDF_BATCH_WORKERS = 2

# These environment variables are most useful when set before Python starts,
# but keeping defaults here is convenient for local runs.
os.environ.setdefault("OMP_NUM_THREADS", "2")
os.environ.setdefault("DOCLING_NUM_THREADS", "2")

# Docling internal batching/concurrency.
# Keep conservative when also using ProcessPoolExecutor, otherwise memory can spike.
settings.perf.doc_batch_concurrency = 1 # Number of docs processed in parallel
settings.perf.page_batch_concurrency = 1 # Number of page batches processed in parallel
settings.perf.page_batch_size = 1 # Number of pages Docling groups together internally for page-level processing
settings.perf.elements_batch_size = 8 # Number of extracted elements are processed together internally


def build_pdf_options() -> PdfPipelineOptions:
    """Create PDF pipeline options for each batch/converter."""
    pdf_options = PdfPipelineOptions()
    pdf_options.do_ocr = False
    pdf_options.do_table_structure = True
    pdf_options.generate_page_images = False
    pdf_options.generate_picture_images = False
    pdf_options.do_code_enrichment = False
    pdf_options.do_formula_enrichment = False

    # IMPORTANT: no trailing comma here. A trailing comma would create a tuple.
    pdf_options.table_structure_options = TableStructureOptions(
        mode=TableFormerMode.ACCURATE,
        do_cell_matching=False,
    )

    return pdf_options


def build_pdf_converter() -> DocumentConverter:
    """Create a fresh converter. Called once per PDF page batch."""
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


def parse_pdf_chunk(args: tuple[str, int, int]) -> tuple[int, int, str, str, int, float, str | None]:
    """
    Parse one page-range batch in a separate process.

    Returns:
        start_page, end_page, status, markdown, markdown_length, elapsed_seconds, error
    """
    input_file, start_page, end_page = args
    chunk_start = perf_counter()
    converter = None

    try:
        converter = build_pdf_converter()
        result = converter.convert(
            input_file,
            page_range=(start_page, end_page),
        )

        status = str(getattr(result, "status", "unknown"))

        if getattr(result, "document", None) is None:
            error_message = getattr(result, "errors", None) or "No document returned"
            raise RuntimeError(error_message)

        md = result.document.export_to_markdown()
        elapsed = perf_counter() - chunk_start
        return start_page, end_page, status, md, len(md), elapsed, None

    except Exception as e:
        elapsed = perf_counter() - chunk_start
        return start_page, end_page, "failed", "", 0, elapsed, str(e)

    finally:
        if converter is not None:
            del converter
        gc.collect()


def convert_pdf(
    input_path: Path,
    output_file: Path,
) -> None:
    reader = PdfReader(str(input_path))
    total_pages = len(reader.pages)

    print(f"Detected {total_pages} pages")
    print(f"PDF batch pages: {PDF_BATCH_PAGES}")
    print(f"PDF batch workers: {PDF_BATCH_WORKERS}")
    print(f"Docling perf settings: {settings.perf}")

    chunks: list[tuple[str, int, int]] = []
    for start_page in range(1, total_pages + 1, PDF_BATCH_PAGES):
        end_page = min(start_page + PDF_BATCH_PAGES - 1, total_pages)
        chunks.append((str(input_path), start_page, end_page))

    print(f"Chunks scheduled: {len(chunks)}")

    t0 = perf_counter()
    completed_results = []

    # Use processes, not threads, so each batch has isolated Docling state.
    # On Windows, this must run under if __name__ == "__main__".
    with ProcessPoolExecutor(max_workers=PDF_BATCH_WORKERS) as executor:
        future_to_chunk = {
            executor.submit(parse_pdf_chunk, chunk): chunk
            for chunk in chunks
        }

        for future in as_completed(future_to_chunk):
            _, start_page, end_page = future_to_chunk[future]
            try:
                result = future.result()
            except Exception as e:
                # This catches executor-level failures, not normal conversion failures.
                result = (
                    start_page,
                    end_page,
                    "failed",
                    "",
                    0,
                    0.0,
                    f"Executor failure: {e}",
                )

            completed_results.append(result)

            r_start, r_end, status, _md, md_len, elapsed, error = result
            if error:
                print(f"FAILED pages {r_start}-{r_end}: {error}")
            else:
                print(f"Completed pages {r_start}-{r_end}: status={status}, markdown={md_len:,} chars, time={elapsed:.2f}s")

    # as_completed returns chunks out of order, so sort before writing Markdown.
    completed_results.sort(key=lambda item: item[0])

    all_markdown: list[str] = []
    total_markdown_chars = 0

    for start_page, end_page, _status, md, md_len, _elapsed, error in completed_results:
        all_markdown.append(
            f"\n\n---\n\n# PDF Pages {start_page}-{end_page}\n\n---\n\n"
        )
        if error:
            all_markdown.append(
                f"FAILED TO PROCESS PAGES {start_page}-{end_page}: {error}\n\n"
            )
        else:
            all_markdown.append(md)
            total_markdown_chars += md_len

    write_start = perf_counter()
    with open(output_file, "w", encoding="utf-8") as f:
        f.write("\n".join(all_markdown))
    write_end = perf_counter()

    t2 = perf_counter()

    print("\n=== Timing ===")
    print(f"Parallel processing:  {write_start - t0:.2f}s")
    print(f"File write:           {write_end - write_start:.2f}s")
    print(f"Total:                {t2 - t0:.2f}s")
    print(f"Chunks attempted:     {len(chunks)}")
    print(f"Markdown characters:  {total_markdown_chars:,}")


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
