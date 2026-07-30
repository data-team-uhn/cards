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

"""
Convert PDF files to Markdown using Docling.

Splits the document into page-range batches processed in parallel.
Each worker process loads the converter once via _init_worker(); page batches are reused.
"""

import gc
import logging
from concurrent.futures import ProcessPoolExecutor, as_completed
from pathlib import Path
from time import perf_counter
from typing import Callable

import docling_config  # noqa: F401 — apply shared Docling settings on import
from docling_config import PDF_PIPELINE_OPTIONS

from pypdf import PdfReader

from docling.datamodel.base_models import InputFormat
from docling.document_converter import DocumentConverter, PdfFormatOption

from docling_batch_sizing import (
    calc_active_workers,
    calc_batch_pages,
    calc_chunk_count,
    calc_workers,
    print_parallelism_summary,
)
from docling_error_detection import (
    DOCLING_PIPELINE_LOGGER,
    DoclingLogCollector,
    ensure_conversion_ok,
)
from markdown_cleanup import finalize_markdown
from markdown_markers import page_marker


def build_pdf_converter() -> DocumentConverter:
    """Create a DocumentConverter for PDF processing."""
    return DocumentConverter(
        format_options={
            InputFormat.PDF: PdfFormatOption(
                pipeline_options=PDF_PIPELINE_OPTIONS
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


def _warm_worker() -> bool:
    """Touch the per-worker converter so model weights are loaded at daemon startup."""
    return _converter is not None


LogFn = Callable[[str], None]


def _run_pdf_chunks(
    chunks: list[tuple[str, int, int]],
    executor: ProcessPoolExecutor,
    *,
    log: LogFn,
) -> list[tuple[int, int, str, str, int, float, str | None]]:
    """Submit page batches to executor and collect results in page order."""
    completed_results: list[tuple[int, int, str, str, int, float, str | None]] = []
    had_failure = False

    future_to_chunk = {
        executor.submit(parse_pdf_chunk, chunk): chunk
        for chunk in chunks
    }

    for future in as_completed(future_to_chunk):
        _, start_page, end_page = future_to_chunk[future]
        try:
            result = future.result()
        except Exception as e:
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
            log(f"FAILED pages {r_start}-{r_end}: {error}")
            _abandon_batches(future_to_chunk, log=log)
            break
        log(
            f"Completed pages {r_start}-{r_end}: status={status}, "
            f"markdown={md_len:,} chars, time={elapsed:.2f}s"
        )

    if had_failure:
        raise RuntimeError("One or more page batches failed.")

    completed_results.sort(key=lambda item: item[0])
    return completed_results


def _abandon_batches(futures, *, log: LogFn) -> None:
    """Cancel what has not started and wait out what already has, before giving up on a
    conversion.

    ``cancel()`` cannot stop a batch that is already running. In daemon mode the executor is
    shared and outlives the request, so simply returning would leave those batches consuming
    workers and RAM on behalf of a conversion whose result is already discarded — starving
    the next request. Waiting here bounds that to the in-flight batches only.
    """
    still_running = [future for future in futures if not future.cancel() and not future.done()]
    if not still_running:
        return
    log(f"Waiting for {len(still_running)} in-flight page batch(es) to stop")
    for future in still_running:
        try:
            future.result()
        except Exception:  # noqa: BLE001 -- already failing; a batch's outcome is moot now
            pass


def convert_pdf_to_markdown(
    input_path: Path,
    *,
    batch_pages: int | None = None,
    workers: int | None = None,
    executor: ProcessPoolExecutor | None = None,
    log: LogFn | None = None,
    source_file: str | None = None,
) -> str:
    """
    Convert a PDF file to Markdown and return the text.

    @param input_path: path to the source .pdf file
    @param batch_pages: optional override for pages per worker batch
    @param workers: optional override for parallel worker process count
    @param executor: optional persistent ProcessPoolExecutor (daemon mode)
    @param log: optional log sink; defaults to print
    @param source_file: optional original upload name for the source_file header
        (defaults to ``input_path.name``, which may be a temp basename)
    @return: cleaned Markdown text
    """
    log_fn = log if log is not None else print

    reader = PdfReader(str(input_path))
    total_pages = len(reader.pages)

    workers_override = workers is not None
    batch_pages_override = batch_pages is not None

    worker_count = calc_workers(workers)
    batch_page_count = calc_batch_pages(total_pages, worker_count, batch_pages)
    chunk_count = calc_chunk_count(total_pages, batch_page_count)
    active_workers = calc_active_workers(worker_count, chunk_count)

    log_fn(f"Detected {total_pages} pages")
    print_parallelism_summary(
        total_pages=total_pages,
        workers=worker_count,
        batch_pages=batch_page_count,
        chunk_count=chunk_count,
        # None when the caller handed us its pool: active_workers only caps the one we build
        # ourselves below, so reporting it for a shared daemon pool overstates the limit.
        active_workers=active_workers if executor is None else None,
        workers_override=workers_override,
        batch_pages_override=batch_pages_override,
        log=log_fn,
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

    if executor is None:
        with ProcessPoolExecutor(max_workers=active_workers, initializer=_init_worker) as pool:
            completed_results = _run_pdf_chunks(chunks, pool, log=log_fn)
    else:
        completed_results = _run_pdf_chunks(chunks, executor, log=log_fn)

    all_markdown: list[str] = []
    for _start_page, _end_page, _status, md, _md_len, _elapsed, _error in completed_results:
        all_markdown.append(md)

    parallel_end = perf_counter()
    markdown_content = finalize_markdown(
        "".join(all_markdown),
        input_path,
        source_file=source_file,
    )
    total_end = perf_counter()

    log_fn("\n=== Timing ===")
    log_fn(f"Parallel processing:  {parallel_end - t0:.2f}s")
    log_fn(f"Cleanup and assembly: {total_end - parallel_end:.2f}s")
    log_fn(f"Total:                {total_end - t0:.2f}s")
    log_fn(f"Chunks attempted:     {chunk_count}")
    log_fn(f"Markdown characters:  {len(markdown_content):,}")

    return markdown_content


WORKER_WARMUP_TIMEOUT_SECONDS = 120


def warm_pdf_workers(executor: ProcessPoolExecutor, worker_count: int) -> None:
    """Run a no-op task in each worker process to load Docling models eagerly."""
    futures = [executor.submit(_warm_worker) for _ in range(worker_count)]
    for future in futures:
        if not future.result(timeout=WORKER_WARMUP_TIMEOUT_SECONDS):
            raise RuntimeError("PDF worker warm-up failed: converter not initialized")


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
            chunk_parts.append(f"\n{page_marker(page_no)}\n")
            page_md = result.document.export_to_markdown(page_no=page_no)
            chunk_parts.append(page_md)

        md = "".join(chunk_parts)
        elapsed = perf_counter() - chunk_start
        return start_page, end_page, status, md, len(md), elapsed, None

    except Exception as e:
        elapsed = perf_counter() - chunk_start
        return start_page, end_page, "failed", "", 0, elapsed, str(e)

    finally:
        # Every batch, not just multi-page ones: auto-sizing routinely lands on 1 page per
        # batch, which is exactly where per-batch churn is highest, so the old
        # ``end_page > start_page`` gate skipped the collection whenever it mattered most.
        gc.collect()
