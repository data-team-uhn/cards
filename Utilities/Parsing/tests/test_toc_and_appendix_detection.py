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

from pathlib import Path

from toc_and_appendix_detection import (
    DEFAULT_MIN_STRUCTURE_TOKENS,
    appendix_heading_kind,
    is_toc_entry_line,
    mark_and_cleanup_toc,
    mark_appendix,
    mark_toc_and_appendix,
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


class TestAppendixHeadingKind:
    def test_atx_references(self):
        assert appendix_heading_kind(["## References"], 0) == "reference"

    def test_atx_appendix(self):
        assert appendix_heading_kind(["# Appendix A"], 0) == "appendix"

    def test_numbered_reference_heading(self):
        assert appendix_heading_kind(["## 18.0 References"], 0) == "reference"

    def test_isolated_bold_appendix(self):
        lines = ["", "**Appendix:**", ""]
        assert appendix_heading_kind(lines, 1) == "appendix"

    def test_bold_not_isolated_is_none(self):
        lines = ["Body text", "**Appendix**", "more body"]
        assert appendix_heading_kind(lines, 1) is None

    def test_ordinary_heading_is_none(self):
        assert appendix_heading_kind(["## Methods"], 0) is None


class TestMarkAppendix:
    def test_finds_first_backmatter_heading_after_skip_floor(self, tmp_path):
        outline_path = tmp_path / "outline.json"
        filler = [f"Filler paragraph {n}." for n in range(401)]
        md = "\n".join(filler + ["## References", "Citation text."])
        mark_appendix(md, outline_path)
        outline = read_outline(outline_path)
        assert outline["backmatterLine"] == 401

    def test_early_mention_ignored_by_skip_floor(self, tmp_path):
        outline_path = tmp_path / "outline.json"
        md = "## References\n\nCited at the very top, before the skip floor.\n"
        mark_appendix(md, outline_path)
        assert "backmatterLine" not in read_outline(outline_path)


class TestMarkTocAndAppendix:
    def test_small_document_skipped_unchanged(self, tmp_path):
        outline_path = tmp_path / "outline.json"
        md = "# Small\n\nToo short for structure detection.\n"
        assert mark_toc_and_appendix(md, outline_path) == md
        # Gated out before any outline is written.
        assert not outline_path.exists()

    def test_default_threshold_constant(self):
        assert DEFAULT_MIN_STRUCTURE_TOKENS == 20000

    def test_records_tokens_when_not_gated(self, tmp_path):
        outline_path = tmp_path / "outline.json"
        md = "# Title\n\n" + ("Some content paragraph. " * 40)
        mark_toc_and_appendix(md, outline_path, min_structure_tokens=1)
        assert read_outline(outline_path)["tokens"] == len(md) // 4


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
