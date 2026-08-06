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

"""Unit tests for the pypdf outline flattening (via a fake reader; no real PDF needed)."""

import pdf_bookmarks
from bookmarks import build_lines_catalog, verify_bookmarks


def _verify(records, md: str):
    return verify_bookmarks(records, md, build_lines_catalog(md.split("\n")))


class FakeDest:
    def __init__(self, title, page):
        self.title = title
        self._page = page


class FakeReader:
    """Duck-typed stand-in for a pypdf reader: exposes ``outline`` and page lookup."""

    def __init__(self, outline):
        self.outline = outline

    def get_destination_page_number(self, dest):
        return dest._page


class TestExtractBookmarks:
    def test_levels_and_pages(self):
        a, a1, a2, b = FakeDest("A", 0), FakeDest("A.1", 0), FakeDest("A.2", 1), FakeDest("B", 2)
        out = pdf_bookmarks.extract_bookmarks(FakeReader([a, [a1, a2], b]))
        assert out == [
            {"title": "A", "level": 1, "page": 1},
            {"title": "A.1", "level": 2, "page": 1},
            {"title": "A.2", "level": 2, "page": 2},
            {"title": "B", "level": 1, "page": 3},
        ]

    def test_empty_title_skipped(self):
        out = pdf_bookmarks.extract_bookmarks(FakeReader([FakeDest("", 0), FakeDest("Real", 1)]))
        assert out == [{"title": "Real", "level": 1, "page": 2}]

    def test_whitespace_collapsed_in_title(self):
        out = pdf_bookmarks.extract_bookmarks(FakeReader([FakeDest("  Study   Design ", 0)]))
        assert out == [{"title": "Study Design", "level": 1, "page": 1}]

    def test_bad_source_returns_empty(self):
        assert pdf_bookmarks.extract_bookmarks("/no/such/file.pdf") == []


class TestExtractVerified:
    def test_corrects_page(self):
        out = _verify(
            pdf_bookmarks.extract_bookmarks(FakeReader([FakeDest("Methods", 0)])),
            "<!-- page: 2 -->\n## Methods",
        )
        assert out[0]["page"] == 2 and "verified" not in out[0]

    def test_flags_missing(self):
        out = _verify(
            pdf_bookmarks.extract_bookmarks(FakeReader([FakeDest("Ghost", 4)])),
            "<!-- page: 1 -->\n## Real",
        )
        assert out[0]["verified"] is False
