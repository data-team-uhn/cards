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


class TestUnpagedDocument:
    """No page markers means no verification, and an early exit before the page map is built.

    A record's claimed page cannot be confirmed or contradicted in an unpaged document, so it
    must pass through rather than being flagged ``verified: False`` — which is what would
    happen if the records were run through the lookup against an empty page map.
    """

    UNPAGED = "## Introduction\n\nbody\n\n## Methods\n\nbody\n"

    def test_records_pass_through_untouched(self):
        records = [{"title": "Methods", "level": 1, "page": 7}]
        assert bookmarks.verify_bookmarks(records, self.UNPAGED) == records

    def test_nothing_is_flagged_unverified(self):
        out = bookmarks.verify_bookmarks([{"title": "Nowhere At All", "page": 3}], self.UNPAGED)
        assert "verified" not in out[0]

    def test_still_returns_fresh_dicts(self):
        # The early exit must keep the no-aliasing contract the loop had.
        records = [{"title": "Methods", "page": 7}]
        out = bookmarks.verify_bookmarks(records, self.UNPAGED)
        out[0]["page"] = 999
        assert records[0]["page"] == 7

    def test_marker_not_on_its_own_line_is_not_a_page(self):
        # PAGE_MARKER.search is only the cheap necessary condition: line_pages requires the
        # marker anchored to its own line, so this document is still unpaged.
        inline = "Some prose <!-- page: 4 --> continues here\n## Methods\n"
        out = bookmarks.verify_bookmarks([{"title": "Methods", "page": 9}], inline)
        assert out == [{"title": "Methods", "page": 9}]

    def test_empty_records(self):
        assert bookmarks.verify_bookmarks([], self.UNPAGED) == []


class TestBookmarksName:
    # There is no sidecar IO here any more: records live in memory for the whole run, and the
    # only file is Chunks/bookmarks.json, written by chunker.write_chunk_files for inspection
    # and read back by nothing. This module just owns the name.
    def test_name_is_the_json_file(self):
        assert bookmarks.BOOKMARKS_NAME == "bookmarks.json"

    def test_no_io_helpers_remain(self):
        assert not hasattr(bookmarks, "read_bookmarks")
        assert not hasattr(bookmarks, "write_bookmarks")


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
