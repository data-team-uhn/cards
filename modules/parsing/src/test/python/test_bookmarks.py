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

MD = ("<!-- page: 1 -->\n## Introduction\n"
      "<!-- page: 2 -->\n## Methods\n"
      "<!-- page: 3 -->\n## Results\n")


class TestNormalizeTitle:
    def test_strips_markup(self):
        assert bookmarks.normalize_title("## 1.0 Background:") == "10background"

    def test_casefold(self):
        assert bookmarks.normalize_title("**Introduction**") == "introduction"

    def test_empty(self):
        assert bookmarks.normalize_title("  ---  ") == ""


class TestPageLineTexts:
    def test_groups_by_page(self):
        pages = bookmarks.page_line_texts("<!-- page: 1 -->\n## Intro\ntext\n<!-- page: 2 -->\n## Methods")
        assert pages[1] == {"intro", "text"}
        assert pages[2] == {"methods"}

    def test_exact_marker_spacing_required(self):
        # Only the canonical marker starts a page; anything else is ordinary content, so its
        # text keys onto page 0 instead of opening page 4.
        for marker in ("<!--  page:  4  -->", "<!-- page: 4-->", "<!--page:4-->"):
            pages = bookmarks.page_line_texts(f"{marker}\nFoo")
            assert 4 not in pages, marker
            assert "foo" in pages[0], marker


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
        md = "<!-- page: 1 -->\n## Intro\n<!-- page: 2 -->\n## Methods"
        assert bookmarks.line_pages(md) == [(1, 1, "intro"), (3, 2, "methods")]


class TestBuildLineIndex:
    def test_groups_positions_by_title_key(self):
        md = "<!-- page: 1 -->\n## Intro\n<!-- page: 2 -->\n## Intro\ntext"
        index = bookmarks.build_line_index(bookmarks.line_pages(md))
        assert index.by_key["intro"] == [(1, 1), (3, 2)]
        assert index.by_key["text"] == [(4, 2)]

    def test_has_pages_true_for_a_paged_document(self):
        index = bookmarks.build_line_index(bookmarks.line_pages(MD))
        assert index.has_pages is True

    def test_has_pages_false_for_an_unpaged_document(self):
        # DOCX output carries no page markers, so every line is page 0.
        index = bookmarks.build_line_index(bookmarks.line_pages("## Intro\nbody text"))
        assert index.has_pages is False

    def test_empty_document(self):
        index = bookmarks.build_line_index(bookmarks.line_pages(""))
        assert index.by_key == {} and index.has_pages is False

    def test_accepts_pre_split_lines(self):
        md = "<!-- page: 1 -->\n## Intro"
        assert bookmarks.line_pages(md, md.split("\n")) == bookmarks.line_pages(md)


class TestResolveRecordLine:
    # "Methods" appears on both page 2 (line 3) and page 3 (line 5).
    MD = "<!-- page: 1 -->\n## Intro\n<!-- page: 2 -->\n## Methods\n<!-- page: 3 -->\n## Methods"

    def _index(self):
        return bookmarks.build_line_index(bookmarks.line_pages(self.MD))

    def test_page_guard_disambiguates(self):
        assert bookmarks.resolve_record_line(self._index(), {"title": "Methods", "page": 2}) == 3

    def test_ambiguous_without_page_guard_is_none(self):
        record = {"title": "Methods", "page": 2, "verified": False}
        assert bookmarks.resolve_record_line(self._index(), record) is None

    def test_unique_match(self):
        assert bookmarks.resolve_record_line(self._index(), {"title": "Intro", "page": 1}) == 1

    def test_absent_returns_none(self):
        assert bookmarks.resolve_record_line(self._index(), {"title": "Ghost", "page": 1}) is None

    def test_exclude_drops_the_only_match(self):
        record = {"title": "Intro", "page": 1}
        assert bookmarks.resolve_record_line(self._index(), record, exclude={1}) is None

    def test_exclude_disambiguates_two_matches(self):
        # Both "Methods" lines match without a trusted page; excluding one leaves exactly
        # one candidate. This is how the printed-TOC range rescues backmatter detection.
        record = {"title": "Methods", "page": 2, "verified": False}
        assert bookmarks.resolve_record_line(self._index(), record, exclude={3}) == 5

    def test_empty_title_returns_none(self):
        assert bookmarks.resolve_record_line(self._index(), {"title": "  ---  "}) is None
        assert bookmarks.resolve_record_line(self._index(), {}) is None

    def test_unpaged_document_ignores_the_claimed_page(self):
        index = bookmarks.build_line_index(bookmarks.line_pages("## Intro\nbody"))
        assert bookmarks.resolve_record_line(index, {"title": "Intro", "page": 99}) == 0
