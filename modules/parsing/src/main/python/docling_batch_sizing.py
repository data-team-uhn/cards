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

"""
Derive Docling PDF outer-parallelism settings from CPU and RAM.

Two lifetimes:

Startup snapshot (module import)
    read_logical_core_count, read_physical_core_count,
    read_total_ram_gb, read_available_ram_gb,
    calc_ram_budget_gb, calc_max_workers_by_cpu, calc_max_workers_by_ram

Per PDF parse (call from convert_pdf before processing)
    calc_workers, calc_batch_pages, calc_chunk_count, calc_active_workers,
    print_parallelism_summary

CLI mapping
    --workers      → calc_workers(workers_override=...)
    --batch-pages  → calc_batch_pages(..., batch_pages_override=...)
    Everything else is auto-derived; no CLI flags.
"""

import argparse
import math
import os

import psutil

# --- tuning constants ---------------------------------------------------------

#   Estimated RAM (GB) per ProcessPool worker (full Docling model stack).
GB_PER_WORKER = 2.0

#   Fraction of installed RAM treated as usable for worker budgeting (85%).
RAM_TOTAL_HEADROOM = 0.85

#   Fraction of currently free RAM treated as usable (90%).
RAM_AVAILABLE_HEADROOM = 0.90

#   When available/total RAM falls below this, budget uses available-only.
RAM_TIGHT_FRACTION = 0.35

#   Upper cap for auto-calculated batch-pages (limits per-chunk memory).
#   CLI: --batch-pages overrides auto entirely when set.
MAX_BATCH_PAGES = 4

#   Auto batch-pages targets roughly workers × this many chunks.
CHUNKS_PER_WORKER_TARGET = 8


# =============================================================================
# Startup snapshot — evaluated once when this module is imported
# =============================================================================

def read_logical_core_count() -> int:
    """
    Read logical CPU count (includes hyperthreads).
    """
    return os.cpu_count() or 4

def read_physical_core_count() -> int:
    """
    Read physical CPU core count.
    """
    logical = read_logical_core_count()
    return psutil.cpu_count(logical=False) or max(1, logical // 2)

def read_total_ram_gb() -> float:
    """
    Read installed RAM in gigabytes.
    """
    return psutil.virtual_memory().total / (1024 ** 3)

def read_available_ram_gb() -> float:
    """
    Read free RAM in gigabytes at import time.
    """
    return psutil.virtual_memory().available / (1024 ** 3)

def calc_ram_budget_gb(total_gb: float, available_gb: float) -> float:
    """
    Compute gigabytes of RAM safe for parallel Docling model loads.
    """
    if total_gb <= 0:
        return available_gb * RAM_AVAILABLE_HEADROOM

    if available_gb < total_gb * RAM_TIGHT_FRACTION:
        return available_gb * RAM_AVAILABLE_HEADROOM

    return min(
        total_gb * RAM_TOTAL_HEADROOM,
        available_gb * RAM_AVAILABLE_HEADROOM,
    )

def calc_max_workers_by_ram(ram_budget_gb: float) -> int:
    """
    Upper bound on workers from RAM budget.
    """
    return max(2, int(ram_budget_gb // GB_PER_WORKER))


# CPU topology at startup.
LOGICAL_CORE_COUNT = read_logical_core_count()
PHYSICAL_CORE_COUNT = read_physical_core_count()

# RAM at startup.
TOTAL_RAM_GB = read_total_ram_gb()
AVAILABLE_RAM_GB = read_available_ram_gb()

# RAM_BUDGET_GB       — safe RAM for model loads.
# MAX_WORKERS_BY_RAM  — worker cap from that RAM budget.
RAM_BUDGET_GB = calc_ram_budget_gb(TOTAL_RAM_GB, AVAILABLE_RAM_GB)
MAX_WORKERS_BY_RAM = calc_max_workers_by_ram(RAM_BUDGET_GB)

# MAX_WORKERS_BY_CPU  — worker cap from CPU topology.
MAX_WORKERS_BY_CPU = LOGICAL_CORE_COUNT
DEFAULT_MAX_WORKERS = max(1, min(MAX_WORKERS_BY_CPU, MAX_WORKERS_BY_RAM))


# =============================================================================
# Per PDF parse — call before each conversion
# =============================================================================

def positive_int(value: str) -> int:
    """An ``argparse`` type for options that must be 1 or greater (``--workers``,
    ``--batch-pages``), so a bad value is rejected with a clear message up front instead
    of failing deep inside ``ProcessPoolExecutor`` or ``range``.

    @param value: the raw command-line string
    @return: the parsed integer
    @raise argparse.ArgumentTypeError: when ``value`` is not an integer of 1 or more
    """
    try:
        parsed = int(value)
    except ValueError:
        raise argparse.ArgumentTypeError(f"expected an integer, got {value!r}") from None
    if parsed < 1:
        raise argparse.ArgumentTypeError(f"must be 1 or greater, got {parsed}")
    return parsed


def calc_workers(workers_override: int | None = None) -> int:
    """
    Resolve the number of parallel worker processes. CLI: --workers

    Clamped to at least 1: ``ProcessPoolExecutor(max_workers=0)`` raises, so a bad
    override must not reach it even if it bypassed :func:`positive_int`.
    """
    if workers_override is not None:
        return max(1, workers_override)
    return DEFAULT_MAX_WORKERS


def calc_batch_pages(
    total_pages: int,
    workers: int,
    batch_pages_override: int | None = None,
) -> int:
    """
    Resolve pages processed per worker chunk. CLI: --batch-pages

    Clamped to at least 1: this becomes a ``range`` step, and a step of 0 raises.
    """
    if batch_pages_override is not None:
        return max(1, batch_pages_override)
    if total_pages <= 0:
        return 1
    target_chunks = max(workers * CHUNKS_PER_WORKER_TARGET, workers)
    batch_pages = max(1, math.ceil(total_pages / target_chunks))
    return min(batch_pages, MAX_BATCH_PAGES)


def calc_chunk_count(total_pages: int, batch_pages: int) -> int:
    """
    Count page-range chunks the PDF will be split into.
    """
    if total_pages <= 0:
        return 0
    return math.ceil(total_pages / batch_pages)


def calc_active_workers(workers: int, chunk_count: int) -> int:
    """
    Resolve ProcessPoolExecutor max_workers.
    Rationale: never spawn idle workers when chunks < workers.
    """
    if chunk_count <= 0:
        return max(1, workers)
    return min(workers, chunk_count)


def print_parallelism_summary(
    *,
    total_pages: int,
    workers: int,
    batch_pages: int,
    chunk_count: int,
    active_workers: int,
    workers_override: bool,
    batch_pages_override: bool,
    log=print,
) -> None:
    """
    Report startup snapshot and resolved per-parse parallelism values on start of each PDF conversion.

    @param log: line sink; defaults to ``print``. The daemon passes its per-request
        collector, so the summary reaches the caller's ``logs`` instead of only the
        daemon's own stdout.
    """
    ram_line = (
        f"{TOTAL_RAM_GB:.0f} GB total, {AVAILABLE_RAM_GB:.1f} GB available "
        f"({RAM_BUDGET_GB:.1f} GB budget @ {GB_PER_WORKER:.1f} GB/worker)"
    )

    workers_source = "manual" if workers_override else "auto"
    batch_source = "manual" if batch_pages_override else "auto"

    log("=== Parallelism tuning ===")
    log(f"CPU: {PHYSICAL_CORE_COUNT} physical / {LOGICAL_CORE_COUNT} logical cores")
    log(f"RAM: {ram_line}")
    log(
        f"Workers: {workers} ({workers_source}; "
        f"cpu cap={MAX_WORKERS_BY_CPU}, ram cap={MAX_WORKERS_BY_RAM})"
    )
    log(f"Batch pages: {batch_pages} ({batch_source}; max={MAX_BATCH_PAGES})")
    log(f"Chunks: {chunk_count} for {total_pages} pages")
    log(f"Active workers: {active_workers}")
