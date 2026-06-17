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
# PDF conversion splits the document into page-range batches processed in parallel.
# Each worker process loads the converter once via _init_worker(); page batches are reused.

import argparse
import gc
import logging
import sys
from concurrent.futures import ProcessPoolExecutor, as_completed
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
from docling.datamodel.settings import settings
from docling.document_converter import (
    DocumentConverter,
    PdfFormatOption,
    WordFormatOption,
)

from docling_error_detection import (
    DOCLING_PIPELINE_LOGGER,
    DoclingLogCollector,
    ensure_conversion_ok,
)
from markdown_cleanup import clean_markdown
from docling_batch_sizing import (
    GB_PER_WORKER,
    MAX_BATCH_PAGES,
    calc_active_workers,
    calc_batch_pages,
    calc_chunk_count,
    calc_workers,
    print_parallelism_summary,
)
from toc_cleanup import TOC_CLEANUP_MAX_PAGE, cleanup_toc_tables

# Docling internal batching/concurrency.
# Keep conservative when also using ProcessPoolExecutor, otherwise memory can spike.
settings.perf.doc_batch_concurrency = 1 # Number of docs processed in parallel
settings.perf.page_batch_concurrency = 1 # Number of page batches processed in parallel
settings.perf.page_batch_size = 1 # Number of pages Docling groups together internally for page-level processing
settings.perf.elements_batch_size = 16 # Number of extracted elements are processed together internally


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

    # 1 thread per worker; process-level parallelism uses the CPU budget.
    pdf_options.accelerator_options = AcceleratorOptions(num_threads=1, device="cpu")

    # --- Batch sizes: 1 page per call; no internal batching needed.
    pdf_options.layout_batch_size = 1
    pdf_options.table_batch_size = 1
    # Reduce pipeline-stage polling latency (default 0.5 s) for short pages.
    pdf_options.batch_polling_interval_seconds = 0.1

    # IMPORTANT: no trailing comma here. A trailing comma would create a tuple.
    pdf_options.table_structure_options = TableStructureOptions(
        mode=TableFormerMode.FAST, # ACCURATE x4 slow down for table parsing
        do_cell_matching=False, # Cell matching x2 slow down for table parsing
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


# Per-worker converter. Populated once by _init_worker() so ML models are loaded
# once per process rather than once per page.
_converter: DocumentConverter | None = None


def _init_worker() -> None:
    """Load the DocumentConverter exactly once per worker process."""
    global _converter
    _converter = build_pdf_converter()


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


def parse_pdf_chunk(args: tuple[str, int, int]) -> tuple[int, int, str, str, int, float, str | None]:
    """
    Parse one page-range batch in a separate process.
    Reuses the per-process converter loaded by _init_worker().

    Returns:
        start_page, end_page, status, markdown, markdown_length, elapsed_seconds, error
    """
    global _converter
    input_file, start_page, end_page = args
    chunk_start = perf_counter()

    try:
        if _converter is None:
            raise RuntimeError("Worker converter not initialized; _init_worker missing?")

        pipeline_logs: list[str] = []
        log_collector = DoclingLogCollector(pipeline_logs)
        pipeline_logger = logging.getLogger(DOCLING_PIPELINE_LOGGER)
        pipeline_logger.addHandler(log_collector)

        try:
            result = _converter.convert(
                input_file,
                page_range=(start_page, end_page),
            )
        finally:
            pipeline_logger.removeHandler(log_collector)

        status = str(getattr(result, "status", "unknown"))
        ensure_conversion_ok(result, pipeline_logs=pipeline_logs)

        chunk_parts: list[str] = []
        for page_no in range(start_page, end_page + 1):
            chunk_parts.append(f"\n\n---\n\n# PDF Page {page_no}\n\n---\n\n")
            page_md = result.document.export_to_markdown(page_no=page_no)
            if page_no < TOC_CLEANUP_MAX_PAGE:
                page_md = cleanup_toc_tables(page_md)
            chunk_parts.append(page_md)

        md = "".join(chunk_parts)
        elapsed = perf_counter() - chunk_start
        return start_page, end_page, status, md, len(md), elapsed, None

    except Exception as e:
        elapsed = perf_counter() - chunk_start
        return start_page, end_page, "failed", "", 0, elapsed, str(e)

    finally:
        if end_page > start_page:
            gc.collect()


def convert_pdf(
    input_path: Path,
    output_file: Path,
    *,
    batch_pages: int | None = None,
    workers: int | None = None,
) -> None:
    reader = PdfReader(str(input_path))
    total_pages = len(reader.pages)

    workers_override = workers is not None
    batch_pages_override = batch_pages is not None

    worker_count = calc_workers(workers)
    batch_page_count = calc_batch_pages(total_pages, worker_count, batch_pages)
    chunk_count = calc_chunk_count(total_pages, batch_page_count)
    active_workers = calc_active_workers(worker_count, chunk_count)

    print(f"Detected {total_pages} pages")
    print_parallelism_summary(
        total_pages=total_pages,
        workers=worker_count,
        batch_pages=batch_page_count,
        chunk_count=chunk_count,
        active_workers=active_workers,
        workers_override=workers_override,
        batch_pages_override=batch_pages_override,
    )

    chunks: list[tuple[str, int, int]] = []
    for start_page in range(1, total_pages + 1, batch_page_count):
        end_page = min(start_page + batch_page_count - 1, total_pages)
        chunks.append((str(input_path), start_page, end_page))

    if len(chunks) != chunk_count:
        raise RuntimeError(
            f"Chunk count mismatch: scheduled {len(chunks)}, expected {chunk_count}"
        )

    t0 = perf_counter()
    completed_results = []
    had_failure = False

    # Use processes, not threads, so each batch has isolated Docling state.
    # _init_worker loads the converter once per worker; chunks reuse it.
    # On Windows, this must run under if __name__ == "__main__".
    with ProcessPoolExecutor(max_workers=active_workers, initializer=_init_worker) as executor:
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
                had_failure = True
                print(f"FAILED pages {r_start}-{r_end}: {error}", file=sys.stderr)
                for pending in future_to_chunk:
                    pending.cancel()
                break
            print(
                f"Completed pages {r_start}-{r_end}: status={status}, "
                f"markdown={md_len:,} chars, time={elapsed:.2f}s"
            )

    if had_failure:
        print("One or more page batches failed.", file=sys.stderr)
        sys.exit(1)

    # as_completed returns chunks out of order, so sort before writing Markdown.
    completed_results.sort(key=lambda item: item[0])

    all_markdown: list[str] = []
    for _start_page, _end_page, _status, md, _md_len, _elapsed, _error in completed_results:
        all_markdown.append(md)

    write_start = perf_counter()
    markdown_content = clean_markdown("".join(all_markdown))
    with open(output_file, "w", encoding="utf-8") as f:
        f.write(markdown_content)
    write_end = perf_counter()

    t2 = perf_counter()

    print("\n=== Timing ===")
    print(f"Parallel processing:  {write_start - t0:.2f}s")
    print(f"File write:           {write_end - write_start:.2f}s")
    print(f"Total:                {t2 - t0:.2f}s")
    print(f"Chunks attempted:     {chunk_count}")
    print(f"Markdown characters:  {len(markdown_content):,}")


def parse_args():
    parser = argparse.ArgumentParser(
        description="Convert PDF or DOCX files to Markdown using Docling."
    )
    parser.add_argument("input_file", help="Path to a .pdf or .docx file")
    parser.add_argument(
        "--workers",
        type=int,
        default=None,
        metavar="N",
        help=(
            "parallel PDF worker processes (default: auto from logical CPU cores "
            f"and RAM budget / {GB_PER_WORKER:.1f} GB per worker)"
        ),
    )
    parser.add_argument(
        "--batch-pages",
        type=int,
        default=None,
        metavar="N",
        help=(
            "pages per worker batch (default: auto from page count and workers, "
            f"max {MAX_BATCH_PAGES})"
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

    if suffix not in [".pdf", ".docx"]:
        print(f"Unsupported file type: {suffix}")
        print("Only .pdf and .docx are supported.")
        sys.exit(1)

    output_file = input_path.with_suffix(".md")

    if suffix == ".pdf":
        if args.batch_pages is not None and args.batch_pages < 1:
            print("--batch-pages must be at least 1")
            sys.exit(1)
        if args.workers is not None and args.workers < 1:
            print("--workers must be at least 1")
            sys.exit(1)
        convert_pdf(
            input_path,
            output_file,
            batch_pages=args.batch_pages,
            workers=args.workers,
        )
    elif suffix == ".docx":
        convert_docx(input_path, output_file)

    print(f"\nSaved to {output_file}")


if __name__ == "__main__":
    main()
