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

"""Tests for docling_batch_sizing: the pure worker/RAM/batch-page arithmetic used to
derive Docling's outer parallelism. Machine-dependent snapshot values are not asserted;
every function here is exercised with explicit inputs."""

import argparse
import math

import pytest

import docling_batch_sizing as bs
from docling_batch_sizing import (
    GB_PER_WORKER,
    MAX_BATCH_PAGES,
    RAM_AVAILABLE_HEADROOM,
    RAM_TOTAL_HEADROOM,
    calc_active_workers,
    calc_batch_pages,
    calc_chunk_count,
    calc_max_workers_by_ram,
    calc_ram_budget_gb,
    calc_workers,
    positive_int,
)


class TestRamBudget:
    def test_zero_total_uses_available_headroom(self):
        assert calc_ram_budget_gb(0, 16) == 16 * RAM_AVAILABLE_HEADROOM

    def test_tight_available_uses_available_headroom(self):
        # available < total * RAM_TIGHT_FRACTION (0.35)
        assert calc_ram_budget_gb(64, 10) == 10 * RAM_AVAILABLE_HEADROOM

    def test_normal_uses_min_of_both_headrooms(self):
        # 64 GB total, 40 GB available: min(64*0.85, 40*0.90) = min(54.4, 36.0) = 36.0
        expected = min(64 * RAM_TOTAL_HEADROOM, 40 * RAM_AVAILABLE_HEADROOM)
        assert calc_ram_budget_gb(64, 40) == expected


class TestMaxWorkersByRam:
    def test_floor_division_by_gb_per_worker(self):
        assert calc_max_workers_by_ram(10.0) == int(10.0 // GB_PER_WORKER)

    def test_minimum_of_one(self):
        # A budget too small for even one worker still yields 1, never 2: a floor of 2 meant
        # ~4 GB of model stacks in a container that may only have 2, reinstating the OOM the
        # cgroup limits are read to avoid.
        assert calc_max_workers_by_ram(1.0) == 1
        assert calc_max_workers_by_ram(0.0) == 1


class TestCalcWorkers:
    def test_override_wins(self):
        assert calc_workers(workers_override=7) == 7

    def test_default_is_positive(self):
        assert calc_workers() >= 1
        assert calc_workers() == bs.DEFAULT_MAX_WORKERS

    def test_non_positive_override_clamped_to_one(self):
        # ProcessPoolExecutor(max_workers=0) raises, so 0 must never reach it.
        assert calc_workers(workers_override=0) == 1
        assert calc_workers(workers_override=-4) == 1


class TestBatchPages:
    def test_override_wins(self):
        assert calc_batch_pages(1000, 4, batch_pages_override=9) == 9

    def test_non_positive_pages_is_one(self):
        assert calc_batch_pages(0, 4) == 1
        assert calc_batch_pages(-5, 4) == 1

    def test_capped_at_max(self):
        # 1000 pages, 2 workers -> target 16 chunks -> ceil(1000/16)=63, capped at MAX_BATCH_PAGES.
        assert calc_batch_pages(1000, 2) == MAX_BATCH_PAGES

    def test_small_document_uses_one_page_batches(self):
        # 10 pages, 4 workers -> target 32 -> ceil(10/32)=1.
        assert calc_batch_pages(10, 4) == 1


class TestChunkCount:
    def test_zero_pages(self):
        assert calc_chunk_count(0, 4) == 0

    def test_ceiling_division(self):
        assert calc_chunk_count(10, 4) == math.ceil(10 / 4)  # 3


class TestActiveWorkers:
    def test_no_chunks_keeps_at_least_one_worker(self):
        assert calc_active_workers(8, 0) == 8
        assert calc_active_workers(0, 0) == 1

    def test_never_more_workers_than_chunks(self):
        assert calc_active_workers(8, 3) == 3

    def test_never_more_chunks_than_workers(self):
        assert calc_active_workers(2, 10) == 2


class TestBatchPagesOverrideClamped:
    def test_non_positive_override_clamped_to_one(self):
        # This value becomes a range() step; a step of 0 raises.
        assert calc_batch_pages(100, 4, batch_pages_override=0) == 1
        assert calc_batch_pages(100, 4, batch_pages_override=-2) == 1

    def test_clamped_override_still_produces_usable_page_ranges(self):
        batch = calc_batch_pages(10, 4, batch_pages_override=0)
        assert list(range(1, 11, batch)) == list(range(1, 11))


class TestPositiveInt:
    def test_accepts_one_and_above(self):
        assert positive_int("1") == 1
        assert positive_int("12") == 12

    def test_rejects_zero_and_negative(self):
        for value in ("0", "-1", "-99"):
            with pytest.raises(argparse.ArgumentTypeError, match="1 or greater"):
                positive_int(value)

    def test_rejects_non_integers(self):
        for value in ("abc", "", "2.5"):
            with pytest.raises(argparse.ArgumentTypeError, match="expected an integer"):
                positive_int(value)


class TestPrintParallelismSummary:
    def test_routes_every_line_to_the_given_sink(self):
        # The daemon passes its per-request collector; using bare print would strand the
        # summary on the daemon's stdout instead of returning it to the caller.
        lines = []
        bs.print_parallelism_summary(
            total_pages=10, workers=2, batch_pages=4, chunk_count=3, active_workers=2,
            workers_override=False, batch_pages_override=True, log=lines.append,
        )
        assert lines[0] == "=== Parallelism tuning ==="
        assert any("Batch pages: 4 (manual" in line for line in lines)
        assert any("Workers: 2 (auto" in line for line in lines)

    def test_defaults_to_print(self, capsys):
        bs.print_parallelism_summary(
            total_pages=1, workers=1, batch_pages=1, chunk_count=1, active_workers=1,
            workers_override=False, batch_pages_override=False,
        )
        assert "=== Parallelism tuning ===" in capsys.readouterr().out


class TestCgroupLimits:
    """Container limits must win over host figures.

    ``os.cpu_count()`` and ``psutil.virtual_memory()`` both report the *host* inside a
    container. Budgeting from those in a 4 GB container on a 32 GB host picks 8 workers at
    ~2 GB each — 16 GB against a 4 GB ceiling, i.e. a guaranteed OOM kill.
    """

    def _cgroup_v2(self, tmp_path, monkeypatch, cpu_max, memory_max):
        (tmp_path / "cpu.max").write_text(cpu_max, encoding="utf-8")
        (tmp_path / "memory.max").write_text(memory_max, encoding="utf-8")
        monkeypatch.setattr(bs, "_CGROUP_V2_CPU_MAX", str(tmp_path / "cpu.max"))
        monkeypatch.setattr(bs, "_CGROUP_V2_MEMORY_MAX", str(tmp_path / "memory.max"))

    def test_cpu_quota_read_as_cores(self, tmp_path, monkeypatch):
        self._cgroup_v2(tmp_path, monkeypatch, "200000 100000\n", "max\n")
        assert bs.read_cgroup_cpu_limit() == 2.0

    def test_fractional_cpu_quota_rounds_up_to_one_worker(self, tmp_path, monkeypatch):
        self._cgroup_v2(tmp_path, monkeypatch, "50000 100000\n", "max\n")
        assert bs.read_cgroup_cpu_limit() == 0.5
        assert bs.read_logical_core_count() == 1

    def test_cpu_max_means_unlimited(self, tmp_path, monkeypatch):
        self._cgroup_v2(tmp_path, monkeypatch, "max 100000\n", "max\n")
        assert bs.read_cgroup_cpu_limit() is None

    def test_memory_limit_read_as_gb(self, tmp_path, monkeypatch):
        self._cgroup_v2(tmp_path, monkeypatch, "max 100000\n", f"{4 * 1024 ** 3}\n")
        assert bs.read_cgroup_memory_limit_gb() == 4.0

    def test_memory_max_means_unlimited(self, tmp_path, monkeypatch):
        self._cgroup_v2(tmp_path, monkeypatch, "max 100000\n", "max\n")
        assert bs.read_cgroup_memory_limit_gb() is None

    def test_absurdly_large_memory_limit_treated_as_unlimited(self, tmp_path, monkeypatch):
        # Some runtimes write a sentinel near 2^63 rather than "max".
        self._cgroup_v2(tmp_path, monkeypatch, "max 100000\n", "9223372036854771712\n")
        assert bs.read_cgroup_memory_limit_gb() is None

    def test_garbage_contents_ignored(self, tmp_path, monkeypatch):
        self._cgroup_v2(tmp_path, monkeypatch, "not a quota\n", "not a number\n")
        assert bs.read_cgroup_cpu_limit() is None
        assert bs.read_cgroup_memory_limit_gb() is None

    def test_missing_files_ignored(self, tmp_path, monkeypatch):
        monkeypatch.setattr(bs, "_CGROUP_V2_CPU_MAX", str(tmp_path / "absent"))
        monkeypatch.setattr(bs, "_CGROUP_V2_MEMORY_MAX", str(tmp_path / "absent"))
        monkeypatch.setattr(bs, "_CGROUP_V1_CPU_QUOTA", str(tmp_path / "absent"))
        monkeypatch.setattr(bs, "_CGROUP_V1_MEMORY_LIMIT", str(tmp_path / "absent"))
        assert bs.read_cgroup_cpu_limit() is None
        assert bs.read_cgroup_memory_limit_gb() is None

    def test_cgroup_v1_fallback(self, tmp_path, monkeypatch):
        (tmp_path / "quota").write_text("400000\n", encoding="utf-8")
        (tmp_path / "period").write_text("100000\n", encoding="utf-8")
        (tmp_path / "limit").write_text(f"{2 * 1024 ** 3}\n", encoding="utf-8")
        monkeypatch.setattr(bs, "_CGROUP_V2_CPU_MAX", str(tmp_path / "absent"))
        monkeypatch.setattr(bs, "_CGROUP_V2_MEMORY_MAX", str(tmp_path / "absent"))
        monkeypatch.setattr(bs, "_CGROUP_V1_CPU_QUOTA", str(tmp_path / "quota"))
        monkeypatch.setattr(bs, "_CGROUP_V1_CPU_PERIOD", str(tmp_path / "period"))
        monkeypatch.setattr(bs, "_CGROUP_V1_MEMORY_LIMIT", str(tmp_path / "limit"))
        assert bs.read_cgroup_cpu_limit() == 4.0
        assert bs.read_cgroup_memory_limit_gb() == 2.0

    def test_ram_readings_capped_by_the_container(self, tmp_path, monkeypatch):
        self._cgroup_v2(tmp_path, monkeypatch, "max 100000\n", f"{1 * 1024 ** 3}\n")
        assert bs.read_total_ram_gb() == 1.0
        assert bs.read_available_ram_gb() <= 1.0

    def test_cores_never_exceed_the_quota(self, tmp_path, monkeypatch):
        # <= rather than ==, which is what the name claims: the function returns
        # min(usable, ceil(quota)), so on a single-core runner the answer is 1, not 2.
        self._cgroup_v2(tmp_path, monkeypatch, "200000 100000\n", "max\n")
        assert bs.read_logical_core_count() <= 2
        assert bs.read_physical_core_count() <= 2


class TestModuleSnapshot:
    def test_default_max_workers_is_positive(self):
        assert bs.DEFAULT_MAX_WORKERS >= 1

    def test_logical_cores_positive(self):
        assert bs.LOGICAL_CORE_COUNT >= 1
