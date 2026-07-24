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

"""Tests for docling_batch_sizing: the pure worker/RAM/batch-page arithmetic used to
derive Docling's outer parallelism. Machine-dependent snapshot values are not asserted;
every function here is exercised with explicit inputs."""

import math

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

    def test_minimum_of_two(self):
        # A tiny budget still yields at least 2 workers.
        assert calc_max_workers_by_ram(1.0) == 2


class TestCalcWorkers:
    def test_override_wins(self):
        assert calc_workers(workers_override=7) == 7

    def test_default_is_positive(self):
        assert calc_workers() >= 1
        assert calc_workers() == bs.DEFAULT_MAX_WORKERS


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


class TestModuleSnapshot:
    def test_default_max_workers_is_positive(self):
        assert bs.DEFAULT_MAX_WORKERS >= 1

    def test_logical_cores_positive(self):
        assert bs.LOGICAL_CORE_COUNT >= 1
