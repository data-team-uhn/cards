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

"""Tests for chunker: heading recognition and token helpers, plus the end-to-end
chunk_file() routing decision (small documents left whole, large ones split) and its
outline.json / catalog.json outputs."""

import json

import pytest

import chunker
from chunker import (
    CATALOG_NAME,
    CHUNKS_DIRNAME,
    OUTLINE_NAME,
    chunk_file,
    is_neutral,
    valid_heading,
)


class TestValidHeading:
    def test_ordinary_heading(self):
        assert valid_heading("Introduction") is True

    def test_too_short_rejected(self):
        assert valid_heading("Hi") is False

    def test_table_caption_rejected(self):
        assert valid_heading("Table 1: Baseline characteristics") is False

    def test_too_many_words_rejected(self):
        assert valid_heading("one two three four five six seven eight nine ten eleven") is False

    def test_overlong_word_rejected(self):
        assert valid_heading("word " + "x" * 101) is False


class TestHeadingMatching:
    def test_match_heading_level_and_text(self):
        assert chunker._match_heading("## Foo Bar") == (2, "Foo Bar")

    def test_match_heading_deepest_level(self):
        assert chunker._match_heading("###### Deep Heading") == (6, "Deep Heading")

    def test_match_heading_seven_hashes_is_not_a_heading(self):
        assert chunker._match_heading("####### Seven") is None

    def test_match_heading_plain_line(self):
        assert chunker._match_heading("plain text line") is None

    def test_heading_level_filters_invalid_headings(self):
        assert chunker._heading_level("## Introduction") == 2
        # A "Table ..." caption is a heading syntactically but not a chunk boundary.
        assert chunker._heading_level("## Table 1: Overview") is None

    def test_heading_text_strips_markers(self):
        assert chunker._heading_text("### Study Design") == "Study Design"
        assert chunker._heading_text("not a heading") is None

    def test_min_heading_level(self):
        # Every heading text must clear MIN_HEADING_CHARS (5) to count as a boundary.
        text = "# Alpha\n## Bravo\n### Gamma"
        assert chunker._min_heading_level(text) == 1
        assert chunker._min_heading_level(text, deeper_than=1) == 2
        assert chunker._min_heading_level("no headings here") is None


class TestStandoutHeading:
    def test_isolated_bold_heading(self):
        lines = ["", "**FUNDING SOURCE**", ""]
        assert chunker._standout_heading(lines, 1) == "FUNDING SOURCE"

    def test_isolated_all_caps_heading(self):
        lines = ["", "REFERENCES", ""]
        assert chunker._standout_heading(lines, 1) == "REFERENCES"

    def test_not_isolated_returns_none(self):
        lines = ["body text", "**FUNDING SOURCE**", "more body"]
        assert chunker._standout_heading(lines, 1) is None


class TestNeutralAndTokens:
    def test_is_neutral(self):
        assert is_neutral("") is True
        assert is_neutral("---") is True
        assert is_neutral("<!-- page: 3-->") is True
        assert is_neutral("Real content") is False

    def test_count_tokens_is_quarter_of_length(self):
        assert chunker._count_tokens("a" * 40) == 10
        assert chunker._count_tokens("") == 0


class TestChunkFile:
    def _write_small(self, tmp_path):
        path = tmp_path / "small.md"
        path.write_text("# Tiny protocol\n\nSome short content.\n", encoding="utf-8")
        return path

    def _write_large(self, tmp_path):
        paragraph = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. " * 60
        sections = [f"# Section {i} Heading\n\n{paragraph}\n" for i in range(1, 51)]
        path = tmp_path / "large.md"
        path.write_text("\n".join(sections), encoding="utf-8")
        return path

    def test_missing_file_raises(self, tmp_path):
        with pytest.raises(FileNotFoundError):
            chunk_file(str(tmp_path / "does-not-exist.md"))

    def test_small_document_not_chunked(self, tmp_path):
        path = self._write_small(tmp_path)
        summary = chunk_file(str(path))
        assert summary["chunks"] == 0

        outline_path = path.parent / CHUNKS_DIRNAME / OUTLINE_NAME
        assert outline_path.is_file()
        outline = json.loads(outline_path.read_text(encoding="utf-8"))
        assert outline["chunked"] is False
        assert outline["fileId"] == "small.md"
        assert outline["toc"] == []
        assert isinstance(outline["tokens"], int) and outline["tokens"] > 0
        # No catalog for an unchunked document.
        assert not (path.parent / CHUNKS_DIRNAME / CATALOG_NAME).exists()

    def test_small_document_rerun_is_stable(self, tmp_path):
        path = self._write_small(tmp_path)
        assert chunk_file(str(path))["chunks"] == 0
        assert chunk_file(str(path))["chunks"] == 0

    def test_large_document_chunked(self, tmp_path):
        path = self._write_large(tmp_path)
        summary = chunk_file(str(path))
        assert summary["chunks"] > 0

        chunks_dir = path.parent / CHUNKS_DIRNAME
        outline = json.loads((chunks_dir / OUTLINE_NAME).read_text(encoding="utf-8"))
        assert outline["chunked"] is True
        assert (chunks_dir / CATALOG_NAME).is_file()
        catalog = json.loads((chunks_dir / CATALOG_NAME).read_text(encoding="utf-8"))
        assert len(catalog["chunks"]) == summary["chunks"]

    def test_huge_threshold_forces_unchunked(self, tmp_path):
        path = self._write_large(tmp_path)
        summary = chunk_file(str(path), min_structure_tokens=10_000_000)
        assert summary["chunks"] == 0
        outline = json.loads(
            (path.parent / CHUNKS_DIRNAME / OUTLINE_NAME).read_text(encoding="utf-8")
        )
        assert outline["chunked"] is False
        assert not (path.parent / CHUNKS_DIRNAME / CATALOG_NAME).exists()
