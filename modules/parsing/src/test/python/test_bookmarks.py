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

"""Unit tests for the dependency-free outline-record helpers in ``bookmarks``."""

import bookmarks

MD = ("<!-- page: 1-->\n## Introduction\n"
      "<!-- page: 2-->\n## Methods\n"
      "<!-- page: 3-->\n## Results\n")


class TestNormalizeTitle:
    def test_strips_markup(self):
        assert bookmarks.normalize_title("## 1.0 Background:") == "10background"

    def test_casefold(self):
        assert bookmarks.normalize_title("**Introduction**") == "introduction"

    def test_empty(self):
        assert bookmarks.normalize_title("  ---  ") == ""


class TestPageLineTexts:
    def test_groups_by_page(self):
        pages = bookmarks.page_line_texts("<!-- page: 1-->\n## Intro\ntext\n<!-- page: 2-->\n## Methods")
        assert pages[1] == {"intro", "text"}
        assert pages[2] == {"methods"}

    def test_lenient_marker_spacing(self):
        pages = bookmarks.page_line_texts("<!--  page:  4  -->\nFoo")
        assert pages[4] == {"foo"}


class TestVerifyBookmarks:
    def test_found_on_page_unchanged(self):
        assert bookmarks.verify_bookmarks([{"title": "Methods", "level": 1, "page": 2}], MD) \
            == [{"title": "Methods", "level": 1, "page": 2}]

    def test_corrects_to_next_page(self):
        out = bookmarks.verify_bookmarks([{"title": "Results", "page": 2}], MD)
        assert out[0]["page"] == 3 and "verified" not in out[0]

    def test_corrects_to_prev_page(self):
        out = bookmarks.verify_bookmarks([{"title": "Introduction", "page": 2}], MD)
        assert out[0]["page"] == 1 and "verified" not in out[0]

    def test_not_found_sets_verified_false(self):
        out = bookmarks.verify_bookmarks([{"title": "Appendix", "page": 2}], MD)
        assert out[0]["verified"] is False and out[0]["page"] == 2

    def test_pageless_unchanged(self):
        assert bookmarks.verify_bookmarks([{"title": "Preface", "page": None}], MD) \
            == [{"title": "Preface", "page": None}]

    def test_input_not_mutated(self):
        records = [{"title": "Results", "page": 2}]
        bookmarks.verify_bookmarks(records, MD)
        assert records == [{"title": "Results", "page": 2}]


class TestSidecarIO:
    def test_roundtrip(self, tmp_path):
        path = tmp_path / bookmarks.BOOKMARKS_NAME
        records = [{"title": "A", "level": 1, "page": 1}]
        bookmarks.write_bookmarks(path, records)
        assert bookmarks.read_bookmarks(path) == records

    def test_missing_returns_empty(self, tmp_path):
        assert bookmarks.read_bookmarks(tmp_path / "nope.json") == []

    def test_non_list_returns_empty(self, tmp_path):
        path = tmp_path / bookmarks.BOOKMARKS_NAME
        path.write_text('{"not": "a list"}', encoding="utf-8")
        assert bookmarks.read_bookmarks(path) == []


class TestLinePages:
    def test_indices_pages_keys(self):
        md = "<!-- page: 1-->\n## Intro\n<!-- page: 2-->\n## Methods"
        assert bookmarks.line_pages(md) == [(1, 1, "intro"), (3, 2, "methods")]


class TestResolveRecordLine:
    # "Methods" appears on both page 2 (line 3) and page 3 (line 5).
    MD = "<!-- page: 1-->\n## Intro\n<!-- page: 2-->\n## Methods\n<!-- page: 3-->\n## Methods"

    def _positions(self):
        return bookmarks.line_pages(self.MD)

    def test_page_guard_disambiguates(self):
        assert bookmarks.resolve_record_line(self._positions(), {"title": "Methods", "page": 2}) == 3

    def test_ambiguous_without_page_guard_is_none(self):
        record = {"title": "Methods", "page": 2, "verified": False}
        assert bookmarks.resolve_record_line(self._positions(), record) is None

    def test_unique_match(self):
        assert bookmarks.resolve_record_line(self._positions(), {"title": "Intro", "page": 1}) == 1

    def test_absent_returns_none(self):
        assert bookmarks.resolve_record_line(self._positions(), {"title": "Ghost", "page": 1}) is None

    def test_exclude_drops_the_only_match(self):
        record = {"title": "Intro", "page": 1}
        assert bookmarks.resolve_record_line(self._positions(), record, exclude={1}) is None
