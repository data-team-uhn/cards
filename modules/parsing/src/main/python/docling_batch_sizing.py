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
Derive Docling PDF outer-parallelism settings from CPU and RAM.

Two lifetimes:

Startup snapshot (module import — CPU topology and total RAM)
    read_logical_core_count, read_physical_core_count,
    read_total_ram_gb, calc_max_workers_by_ram

At daemon start / each ``calc_workers()`` with no override
    read_available_ram_gb, calc_ram_budget_gb, refresh_default_max_workers

Per PDF parse (call from convert_pdf_to_markdown before processing)
    calc_workers, calc_batch_pages, calc_chunk_count, calc_active_workers,
    print_parallelism_summary

CLI mapping
    --workers → calc_workers(workers_override=...)
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

#   cgroup v2 / v1 files holding a container's CPU quota and memory ceiling. Read because the
#   host-level counts below are blind to container limits: in a 4 GB container on a 32 GB host,
#   psutil reports 32 GB, so worker budgeting would oversubscribe by 8x and get OOM-killed.
_CGROUP_V2_CPU_MAX = "/sys/fs/cgroup/cpu.max"
_CGROUP_V2_MEMORY_MAX = "/sys/fs/cgroup/memory.max"
_CGROUP_V1_CPU_QUOTA = "/sys/fs/cgroup/cpu/cpu.cfs_quota_us"
_CGROUP_V1_CPU_PERIOD = "/sys/fs/cgroup/cpu/cpu.cfs_period_us"
_CGROUP_V1_MEMORY_LIMIT = "/sys/fs/cgroup/memory/memory.limit_in_bytes"

#   A cgroup memory ceiling at or above this is "unlimited" in practice, not a real limit.
_CGROUP_UNLIMITED_BYTES = 1 << 62


def _read_first_line(path: str) -> str | None:
    """The first line of a sysfs/cgroup file, or ``None`` when it cannot be read."""
    try:
        with open(path, encoding="utf-8") as handle:
            return handle.readline().strip()
    except OSError:
        return None


def read_cgroup_cpu_limit() -> float | None:
    """The container's CPU quota in cores, or ``None`` when unlimited or not containerised."""
    v2 = _read_first_line(_CGROUP_V2_CPU_MAX)
    if v2:
        parts = v2.split()
        if len(parts) == 2 and parts[0] != "max":
            try:
                quota, period = int(parts[0]), int(parts[1])
            except ValueError:
                return None
            if quota > 0 and period > 0:
                return quota / period
        return None
    quota_raw = _read_first_line(_CGROUP_V1_CPU_QUOTA)
    period_raw = _read_first_line(_CGROUP_V1_CPU_PERIOD)
    if quota_raw and period_raw:
        try:
            quota, period = int(quota_raw), int(period_raw)
        except ValueError:
            return None
        if quota > 0 and period > 0:
            return quota / period
    return None


def read_cgroup_memory_limit_gb() -> float | None:
    """The container's memory ceiling in GB, or ``None`` when unlimited or not containerised."""
    for path in (_CGROUP_V2_MEMORY_MAX, _CGROUP_V1_MEMORY_LIMIT):
        raw = _read_first_line(path)
        if not raw or raw == "max":
            continue
        try:
            limit = int(raw)
        except ValueError:
            continue
        if 0 < limit < _CGROUP_UNLIMITED_BYTES:
            return limit / (1024 ** 3)
    return None


def read_logical_core_count() -> int:
    """
    Read usable logical CPU count, honouring a container quota and CPU affinity.

    ``os.cpu_count()`` reports the host's CPUs even inside a container, so it is only the last
    resort here.
    """
    limit = read_cgroup_cpu_limit()
    # process_cpu_count() respects affinity (Python 3.13+); sched_getaffinity is the POSIX
    # equivalent on older versions.
    affinity = getattr(os, "process_cpu_count", lambda: None)()
    if affinity is None and hasattr(os, "sched_getaffinity"):
        affinity = len(os.sched_getaffinity(0))
    usable = affinity or os.cpu_count() or 4
    if limit is not None:
        return max(1, min(usable, math.ceil(limit)))
    return usable

def read_physical_core_count() -> int:
    """
    Read physical CPU core count, never reporting more than the usable logical count.
    """
    logical = read_logical_core_count()
    physical = psutil.cpu_count(logical=False) or max(1, logical // 2)
    return max(1, min(physical, logical))

def read_total_ram_gb() -> float:
    """
    Read the memory ceiling in gigabytes: the container's limit when there is one, else
    installed RAM.
    """
    limit = read_cgroup_memory_limit_gb()
    total = psutil.virtual_memory().total / (1024 ** 3)
    return min(total, limit) if limit is not None else total

def read_available_ram_gb() -> float:
    """
    Read free RAM in gigabytes now, capped by the container's memory ceiling.
    """
    available = psutil.virtual_memory().available / (1024 ** 3)
    limit = read_cgroup_memory_limit_gb()
    return min(available, limit) if limit is not None else available

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

    Floors at 1, not 2. A floor of 2 guaranteed roughly 4 GB of model stacks regardless of the
    budget, which in a 2-3 GB container recreated exactly the OOM kill the cgroup reading above
    exists to prevent. One worker that fits beats two that do not.
    """
    return max(1, int(ram_budget_gb // GB_PER_WORKER))


# CPU topology at startup.
LOGICAL_CORE_COUNT = read_logical_core_count()
PHYSICAL_CORE_COUNT = read_physical_core_count()

# RAM: total is stable; available / budget are refreshed by refresh_default_max_workers().
TOTAL_RAM_GB = read_total_ram_gb()
AVAILABLE_RAM_GB = read_available_ram_gb()

# RAM_BUDGET_GB       — safe RAM for model loads.
# MAX_WORKERS_BY_RAM  — worker cap from that RAM budget.
RAM_BUDGET_GB = calc_ram_budget_gb(TOTAL_RAM_GB, AVAILABLE_RAM_GB)
MAX_WORKERS_BY_RAM = calc_max_workers_by_ram(RAM_BUDGET_GB)

# MAX_WORKERS_BY_CPU  — worker cap from CPU topology.
MAX_WORKERS_BY_CPU = LOGICAL_CORE_COUNT
DEFAULT_MAX_WORKERS = max(1, min(MAX_WORKERS_BY_CPU, MAX_WORKERS_BY_RAM))


def refresh_default_max_workers() -> int:
    """Re-read free RAM and update :data:`DEFAULT_MAX_WORKERS`.

    Call at daemon start (and whenever auto worker count is resolved) so a long-lived
    process does not keep budgeting from import-time free memory.
    """
    global AVAILABLE_RAM_GB, RAM_BUDGET_GB, MAX_WORKERS_BY_RAM, DEFAULT_MAX_WORKERS
    AVAILABLE_RAM_GB = read_available_ram_gb()
    RAM_BUDGET_GB = calc_ram_budget_gb(TOTAL_RAM_GB, AVAILABLE_RAM_GB)
    MAX_WORKERS_BY_RAM = calc_max_workers_by_ram(RAM_BUDGET_GB)
    DEFAULT_MAX_WORKERS = max(1, min(MAX_WORKERS_BY_CPU, MAX_WORKERS_BY_RAM))
    return DEFAULT_MAX_WORKERS


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
    When ``workers_override`` is omitted, free RAM is refreshed first.
    """
    if workers_override is not None:
        return max(1, workers_override)
    return refresh_default_max_workers()


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
    active_workers: int | None,
    workers_override: bool,
    batch_pages_override: bool,
    log=print,
) -> None:
    """
    Report startup snapshot and resolved per-parse parallelism values on start of each PDF conversion.

    @param active_workers: how many workers this conversion will actually occupy, or ``None``
        when the pool is not ours to size. It only bounds anything on the path that creates its
        own ProcessPoolExecutor; in daemon mode the pool is shared and pre-warmed at whatever
        size the daemon chose, so reporting a number here claimed a limit that was not applied.
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
    if active_workers is not None:
        log(f"Active workers: {active_workers}")
    else:
        log(f"Active workers: shared daemon pool ({workers})")
