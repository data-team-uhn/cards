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

"""Tests for toc_and_appendix_detection: TOC entry recognition, label finding, in-place
TOC cleanup with outline.json side effects, Reference/Appendix heading detection, the
size gate, and the outline read/write helpers."""

import json
from pathlib import Path

import toc_and_appendix_detection as tad
from toc_and_appendix_detection import (
    DEFAULT_MIN_STRUCTURE_TOKENS,
    is_toc_entry_line,
    mark_and_cleanup_toc,
    find_toc_and_appendix,
    read_outline,
    toc_label_line,
    write_outline,
)


class TestIsTocEntryLine:
    def test_tab_separated_entry(self):
        assert is_toc_entry_line("Introduction\t3") is True

    def test_numbered_entry_with_tab(self):
        assert is_toc_entry_line("1.0 General Information\t3") is True

    def test_dash_separated_entry(self):
        assert is_toc_entry_line("Summary  -  2") is True

    def test_dot_leader_entry(self):
        assert is_toc_entry_line("Schema.......4") is True

    def test_roman_numeral_page(self):
        assert is_toc_entry_line("Abbreviations - v") is True

    def test_numbered_outline_without_page(self):
        assert is_toc_entry_line("2.0 **Introduction**") is True

    def test_too_many_words_rejected(self):
        line = "This heading has far too many words to be a table entry line indeed\t3"
        assert is_toc_entry_line(line) is False

    def test_plain_sentence_rejected(self):
        assert is_toc_entry_line("This is a normal sentence.") is False

    def test_empty_rejected(self):
        assert is_toc_entry_line("") is False


class TestTocLabelLine:
    def test_decorated_atx_label(self):
        lines = ["# Protocol", "", "## Table of Contents", "", "Intro\t1"]
        assert toc_label_line(lines) == 2

    def test_bold_contents_label(self):
        lines = ["**Contents**", "Intro\t1"]
        assert toc_label_line(lines) == 0

    def test_bare_label_requires_isolation(self):
        isolated = ["", "table of contents", ""]
        assert toc_label_line(isolated) == 1
        not_isolated = ["preamble text", "table of contents", "more text"]
        assert toc_label_line(not_isolated) is None

    def test_absent_label(self):
        assert toc_label_line(["# Title", "", "Body"]) is None


class TestMarkAndCleanupToc:
    def _doc(self):
        return (
            "# Study Protocol\n"
            "\n"
            "## Table of Contents\n"
            "\n"
            "Introduction\t1\n"
            "Background\t2\n"
            "Methods\t3\n"
            "Results\t4\n"
            "\n"
            "## Introduction\n"
            "\n"
            "The study begins here.\n"
        )

    def test_no_label_returns_unchanged(self):
        md = "# Title\n\nJust body text, no contents label.\n"
        assert mark_and_cleanup_toc(md, None) == md

    def test_outline_records_range_and_entries(self, tmp_path):
        outline_path = tmp_path / "outline.json"
        result = mark_and_cleanup_toc(self._doc(), outline_path)
        # The label survives cleanup with its markers stripped.
        assert "Table of Contents" in result
        outline = read_outline(outline_path)
        assert outline["tocStartLine"] == 2
        assert outline["tocEndLine"] >= outline["tocStartLine"]
        # Tab separators are normalized to a space in the stored entries.
        assert "Introduction 1" in outline["toc"]
        assert "Results 4" in outline["toc"]

    def test_empty_input(self):
        assert mark_and_cleanup_toc("", None) == ""


class TestMarkTocAndAppendix:
    def test_small_document_skipped_unchanged(self, tmp_path):
        outline_path = tmp_path / "outline.json"
        md = "# Small\n\nToo short for structure detection.\n"
        assert find_toc_and_appendix(md, outline_path) == md
        # Gated out before any outline is written.
        assert not outline_path.exists()

    def test_default_threshold_constant(self):
        assert DEFAULT_MIN_STRUCTURE_TOKENS == 20000

    def test_records_tokens_when_not_gated(self, tmp_path):
        outline_path = tmp_path / "outline.json"
        md = "# Title\n\n" + ("Some content paragraph. " * 40)
        find_toc_and_appendix(md, outline_path, min_structure_tokens=1)
        assert read_outline(outline_path)["tokens"] == len(md) // 4

    def test_bookmark_path_records_outline_even_when_small(self, tmp_path):
        # Bookmarks present -> outline recorded regardless of the size gate, so a small
        # document still carries a toc (for the Stage 0.5 toc-only path).
        (tmp_path / "bookmarks.json").write_text(
            json.dumps([{"title": "Alpha", "level": 1, "page": 1}]) + "\n", encoding="utf-8")
        outline_path = tmp_path / "outline.json"
        find_toc_and_appendix("# Tiny\n\nbody\n", outline_path, min_structure_tokens=10 ** 9)
        outline = read_outline(outline_path)
        assert outline["outline_source"] == "pdf-bookmarks"
        assert outline["toc"] == ["Alpha"]


class TestOutlineReadWrite:
    def test_read_missing_returns_empty(self, tmp_path):
        assert read_outline(tmp_path / "nope.json") == {}
        assert read_outline(None) == {}

    def test_write_then_read_roundtrip_merges(self, tmp_path):
        outline_path = tmp_path / "outline.json"
        write_outline(outline_path, {"fileId": "doc.md", "tokens": 10})
        write_outline(outline_path, {"tokens": 20, "chunked": True})
        outline = read_outline(outline_path)
        assert outline == {"fileId": "doc.md", "tokens": 20, "chunked": True}

    def test_write_none_path_is_noop(self):
        # Must not raise.
        write_outline(None, {"tokens": 1})


class TestEntryToRecord:
    def test_dash_page_and_level(self):
        assert tad._entry_to_record("1.0 Background - 9") == {"title": "1.0 Background", "level": 1, "page": 9}

    def test_tab_collapsed_single_space(self):
        assert tad._entry_to_record("Introduction 1") == {"title": "Introduction", "level": None, "page": 1}

    def test_roman_page(self):
        assert tad._entry_to_record("Abbreviations - v") == {"title": "Abbreviations", "level": None, "page": 5}

    def test_no_page_keeps_level(self):
        assert tad._entry_to_record("2.0 Introduction") == {"title": "2.0 Introduction", "level": 1, "page": None}


class TestMarkTocAndAppendixFork:
    def _doc_with_toc(self):
        return (
            "# Study Protocol\n\n## Table of Contents\n\n"
            "1.0 Introduction\t1\n2.0 Methods\t2\nReferences\t3\n\n"
            "## 1.0 Introduction\n\nbody\n\n## 2.0 Methods\n\nbody\n\n## References\n\ncites\n"
        )

    def test_manual_path_records_toc_and_backmatter(self, tmp_path):
        outline_path = tmp_path / "outline.json"
        find_toc_and_appendix(self._doc_with_toc(), outline_path, min_structure_tokens=1)
        outline = read_outline(outline_path)
        assert outline["outline_source"] == "md-toc"
        assert outline["toc"] == ["1.0 Introduction", "2.0 Methods", "References"]
        assert "backmatterLine" in outline
        records = json.loads((tmp_path / "bookmarks.json").read_text(encoding="utf-8"))
        assert {"title": "1.0 Introduction", "level": 1, "page": 1} in records

    def test_bookmark_path_skips_printed_toc(self, tmp_path):
        outline_path = tmp_path / "outline.json"
        # A bookmarks.json already present -> printed TOC left untouched; toc comes from records.
        (tmp_path / "bookmarks.json").write_text(
            json.dumps([{"title": "Alpha", "level": 1, "page": 1}]) + "\n", encoding="utf-8")
        result = find_toc_and_appendix(self._doc_with_toc(), outline_path, min_structure_tokens=1)
        assert "## Table of Contents" in result
        outline = read_outline(outline_path)
        assert outline["outline_source"] == "pdf-bookmarks"
        assert outline["toc"] == ["Alpha"]

    def test_outline_source_none_when_nothing_found(self, tmp_path):
        outline_path = tmp_path / "outline.json"
        find_toc_and_appendix("# Title\n\n" + ("body paragraph. " * 40), outline_path, min_structure_tokens=1)
        assert read_outline(outline_path)["outline_source"] == "none"
