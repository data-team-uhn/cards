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

"""Tests for toc_and_appendix_detection: TOC entry recognition, label finding, in-place
TOC cleanup, Reference/Appendix heading detection, the size gate, and the outline reader.

The outline is derived by derive_outline, which writes nothing and returns
``(document, updates, records, line_index)`` — so what these tests used to write to
outline.json and read back is simply the second return value."""

from pathlib import Path

import toc_and_appendix_detection as tad
from bookmarks import build_line_index, build_lines_catalog
from toc_and_appendix_detection import (
    DEFAULT_MIN_STRUCTURE_TOKENS,
    derive_outline,
    is_toc_entry_line,
    read_outline,
    toc_label_line,
)


def _derive(md, markdown_path=None, min_structure_tokens=1):
    return derive_outline(md, markdown_path, min_structure_tokens)


def _derive_with_bookmarks(monkeypatch, tmp_path, md, records, min_structure_tokens=1):
    md_path = tmp_path / "doc.md"
    (tmp_path / "doc.pdf").write_bytes(b"%PDF-1.4")
    # Stub PDF is not readable; feed records via extract, and keep verify as identity so
    # tests control the exact outline without page-correction side effects.
    monkeypatch.setattr(tad, "extract_bookmarks", lambda *a, **k: records)
    monkeypatch.setattr(tad, "verify_bookmarks", lambda *a, **k: records)
    return derive_outline(md, md_path, min_structure_tokens)


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
        result, fields = tad._detect_toc(md)
        assert result == md
        assert fields == {}

    def test_reports_range_and_entries(self):
        result, fields = tad._detect_toc(self._doc())
        # The label survives cleanup with its markers stripped.
        assert "Table of Contents" in result
        assert fields["tocStartLine"] == 2
        assert fields["tocEndLine"] >= fields["tocStartLine"]
        # Tab separators are normalized to a space in the reported entries.
        assert "Introduction 1" in fields["toc"]
        assert "Results 4" in fields["toc"]

    def test_empty_input(self):
        assert tad._detect_toc("") == ("", {})


class TestMarkTocAndAppendix:
    def test_size_gate_skips_small_docs_without_bookmarks(self):
        # No bookmarks and tokens below the default threshold → empty updates, no line index.
        md = "# Small\n\nToo short for structure detection.\n"
        result, updates, records, line_index = derive_outline(md)
        assert result == md
        assert updates == {}
        assert records == []
        assert line_index is None

    def test_default_threshold_constant(self):
        assert DEFAULT_MIN_STRUCTURE_TOKENS == 20000

    def test_records_tokens_when_not_gated(self):
        md = "# Title\n\n" + ("Some content paragraph. " * 40)
        _, updates, _, _ = _derive(md)
        assert updates["tokens"] == len(md) // 4

    def test_bookmark_path_records_outline_even_when_small(self, monkeypatch, tmp_path):
        # Bookmarks beat the size gate, so a small document still carries a toc.
        known = [{"title": "Alpha", "level": 1, "page": 1}]
        _, updates, _, _ = _derive_with_bookmarks(
            monkeypatch, tmp_path, "# Tiny\n\nbody\n", known
        )
        assert updates["toc_source"] == "pdf-bookmarks"
        assert updates["toc"] == ["Alpha"]


class TestOutlineReader:
    # Only the reader survives: outline.json is written by chunker.write_chunk_files, in one
    # shot from the tree, so there is no merging writer here any more.
    def test_read_missing_returns_empty(self, tmp_path):
        assert read_outline(tmp_path / "nope.json") == {}
        assert read_outline(None) == {}

    def test_read_unparseable_returns_empty(self, tmp_path):
        broken = tmp_path / "outline.json"
        broken.write_text("{not json", encoding="utf-8")
        assert read_outline(broken) == {}


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

    def test_manual_path_records_toc_and_backmatter(self):
        _, updates, records, _ = _derive(self._doc_with_toc())
        assert updates["toc_source"] == "md-toc"
        assert updates["toc"] == ["1.0 Introduction", "2.0 Methods", "References"]
        assert "backmatterLine" in updates
        # The harvested records come back as the third return value; they are not written to disk.
        assert {"title": "1.0 Introduction", "level": 1, "page": 1} in records

    def test_bookmark_path_discards_the_printed_toc_entries(self, monkeypatch, tmp_path):
        # Renamed from test_bookmark_path_skips_printed_toc: the printed TOC is no longer skipped.
        # It is cleaned like anywhere else, so the document has one version and one line
        # numbering, and its *entries* are what the records displace — ``toc`` comes from the
        # records, not from the page.
        known = [{"title": "Alpha", "level": 1, "page": 1}]
        result, updates, _, _ = _derive_with_bookmarks(
            monkeypatch, tmp_path, self._doc_with_toc(), known
        )
        assert updates["toc_source"] == "pdf-bookmarks"
        assert updates["toc"] == ["Alpha"]
        # The label survives cleanup, so the block is still identifiable in the output.
        assert "## Table of Contents" in result
        # And its range is now reported, which is what lets the backmatter lookup and the record
        # cut keys exclude it. It used to be absent on this path.
        assert isinstance(updates["tocStartLine"], int)
        assert updates["tocEndLine"] >= updates["tocStartLine"]

    def test_bookmark_path_reports_a_toc_range_where_it_used_to_report_none(
        self, monkeypatch, tmp_path
    ):
        # The regression this refactor closes: without a range, a page-less "References" entry
        # collides with the body heading it points at, and both backmatter_from_records and
        # chunker._record_cut_keys silently fail open.
        pageless = [{"title": "References", "level": None, "page": None}]
        _, updates, _, _ = _derive_with_bookmarks(
            monkeypatch, tmp_path, self._doc_with_toc(), pageless
        )
        assert "tocStartLine" in updates
        assert "backmatterLine" in updates

    def test_toc_source_none_when_nothing_found(self):
        _, updates, _, _ = _derive(
            "# Title\n\n" + ("body paragraph. " * 40)
        )
        assert updates["toc_source"] == "none"


# A running header of the kind Docling leaves at the top of every protocol page. Well over
# MAX_LEADING_WORDS_FOR_CONTINUATION on its own, which is the point: the in-block prose
# tolerance cannot absorb it, so a TOC split across pages needs the page-break probe.
RUNNING_HEADER = "REB Protocol 24-5450 Version 3.0 dated 12 March 2026 Confidential Page 2 of 48"


class TestTocAcrossPageBreaks:
    """A printed TOC that continues on the next PDF page.

    Regression: the module's page-marker pattern required a space before ``-->`` that the
    PDF parser never emits, so ``_scan_block`` never reported a page boundary and the whole
    cross-page continuation branch was unreachable. A two-page TOC lost its second half.
    """

    def _doc(self, separator):
        return "\n".join([
            "<!-- page: 1 -->",
            "## TABLE OF CONTENTS",
            "1.0 Background and Rationale\t3",
            "2.0 Study Objectives\t5",
            "3.0 Eligibility Criteria\t7",
            "4.0 Study Design\t9",
            "<!-- page: 2 -->",
            *separator,
            "5.0 Statistical Analysis\t12",
            "6.0 Data Management\t15",
            "7.0 Ethical Considerations\t18",
            "8.0 References and Appendices\t22",
            "",
            "<!-- page: 3 -->",
            "## 1.0 Background and Rationale",
            "Body text of the background section.",
        ])

    def _entries(self, separator):
        return tad._detect_toc(self._doc(separator))[1]["toc"]

    def test_continues_straight_across_a_page_marker(self):
        entries = self._entries([])
        assert len(entries) == 8
        assert any("Statistical Analysis" in entry for entry in entries)

    def test_continues_past_a_running_header(self):
        entries = self._entries([RUNNING_HEADER])
        assert len(entries) == 8, entries
        assert any("References and Appendices" in entry for entry in entries)

    def test_running_header_is_not_kept_as_an_entry(self):
        entries = self._entries([RUNNING_HEADER])
        assert not any("Confidential" in entry for entry in entries)

    def test_stops_when_the_next_page_is_body_text(self):
        body = ["## Introduction Section", "Real body prose starts here."]
        entries = self._entries(body)
        # A real heading after the break is a hard boundary: only page 1's entries.
        assert len(entries) == 4

    def test_gives_up_after_too_much_header_noise(self):
        noise = [f"{RUNNING_HEADER} line {i}" for i in range(6)]
        entries = self._entries(noise)
        assert len(entries) == 4


class TestResumeAfterPageBreak:
    LINES = ["<!-- page: 2 -->", RUNNING_HEADER, "5.0 Statistical Analysis\t12"]

    def test_finds_the_resumption_line_past_noise(self):
        assert tad._resume_after_page_break(self.LINES, 1, tad.is_toc_entry_line) == 2

    def test_none_at_a_real_heading(self):
        lines = ["<!-- page: 2 -->", "## Real Heading Here", "1.0 Entry\t3"]
        assert tad._resume_after_page_break(lines, 1, tad.is_toc_entry_line) is None

    def test_none_when_the_label_reappears(self):
        lines = ["<!-- page: 2 -->", "## Table of Contents", "1.0 Entry\t3"]
        assert tad._resume_after_page_break(lines, 1, tad.is_toc_entry_line) is None

    def test_none_past_end_of_document(self):
        assert tad._resume_after_page_break(["<!-- page: 2 -->"], 1, tad.is_toc_entry_line) is None

    def test_none_when_noise_exceeds_the_allowance(self):
        noise = [f"noise line {i} with several words in it" for i in range(6)]
        lines = ["<!-- page: 2 -->", *noise, "1.0 Entry\t3"]
        assert tad._resume_after_page_break(lines, 1, tad.is_toc_entry_line) is None


class TestOverlongTocEntry:
    """One TOC entry longer than the word limit must not end the block.

    Regression: such an entry fails ``is_toc_entry_line`` on word count, was then charged
    against the prose tolerance as if it were body text, and ended the TOC — silently
    dropping every entry after it, References and Appendices included, which in turn left
    ``backmatterLine`` unset.
    """

    def _doc(self):
        return "\n".join([
            "## TABLE OF CONTENTS",
            "1.0 Background\t3",
            "2.0 Objectives\t5",
            # 15 words: over MAX_HEADING_WORDS, but structurally a page-numbered entry.
            "6.11 Adaptations for outcome assessment for patients who cannot attend "
            "facility based outcome assessment\t15",
            "7.0 Ethics\t16",
            "19.0 References\t20",
            "20.0 Appendices\t27",
            "",
            "## 1.0 Background",
            "body",
        ])

    def _entries(self):
        return tad._detect_toc(self._doc())[1]["toc"]

    def test_entries_after_the_long_one_survive(self):
        entries = self._entries()
        assert any("References" in entry for entry in entries), entries
        assert any("Appendices" in entry for entry in entries), entries

    def test_the_long_entry_itself_is_still_not_recorded(self):
        assert not any("Adaptations" in entry for entry in self._entries())

    def test_page_numbered_entry_predicate_ignores_length(self):
        long_entry = ("6.11 Adaptations for outcome assessment for patients who cannot "
                      "attend facility based outcome assessment\t15")
        assert is_toc_entry_line(long_entry) is False
        assert tad.is_page_numbered_entry(long_entry) is True

    def test_numbered_body_prose_is_not_page_numbered_entry(self):
        # The trailing-page-number requirement is what keeps this safe: ordinary numbered
        # prose must not look like an entry, or the scan would run on into a numbered list.
        for prose in ("1. The patient will be assessed for eligibility at baseline.",
                      "2) Participants receive standard of care prehabilitation therapy.",
                      "3.1 Data will be collected using the validated instrument above."):
            assert tad.is_page_numbered_entry(prose) is False, prose


class TestBackmatterTocExclusion:
    """The printed TOC stays in the document, so a page-less TOC entry is textually
    identical to the body heading it points at. Without the TOC range excluded both lines
    match the record, ``resolve_record_line`` fails open, and the backmatter split is lost.
    """

    MD = "\n".join([
        "TABLE OF CONTENTS",
        "1.0 Background",
        "2.0 Study Design",
        "8.0 References",
        "",
        "## 1.0 Background",
        "body",
        "## 8.0 References",
        "Smith J et al. Lancet. 2020.",
    ])
    RECORDS = [{"title": "8.0 References", "level": 1, "page": None}]

    def _index(self):
        return build_line_index(build_lines_catalog(self.MD.split("\n")))

    def test_ambiguous_without_the_toc_range(self):
        assert tad.backmatter_from_records(self.RECORDS, self._index()) is None

    def test_resolves_to_the_body_heading_with_the_toc_range(self):
        assert tad.backmatter_from_records(self.RECORDS, self._index(), (0, 3)) == 7

    def test_no_backmatter_record_short_circuits(self):
        records = [{"title": "1.0 Background", "page": None}]
        assert tad.backmatter_from_records(records, self._index(), (0, 3)) is None

    def test_end_to_end_records_backmatter_for_a_pageless_toc(self, tmp_path):
        outline_path = tmp_path / "outline.json"
        doc = "\n".join([
            "## Table of Contents",
            "1.0 Background",
            "2.0 Study Design",
            "8.0 References",
            "",
            "## 1.0 Background",
            "body paragraph. " * 20,
            "## 2.0 Study Design",
            "body paragraph. " * 20,
            "## 8.0 References",
            "Smith J et al. Lancet. 2020.",
        ])
        _, updates, _, _ = _derive(doc)
        assert updates["toc_source"] == "md-toc"
        assert isinstance(updates["backmatterLine"], int)
        # It must point at the body heading, not the TOC entry.
        assert updates["backmatterLine"] > updates["tocEndLine"]


class TestBackmatterOnTheBookmarksPath:
    """The printed TOC has to be excluded from the backmatter lookup on *both* paths.

    Regression: authoritative bookmarks leave the printed TOC in the document on purpose, but
    ``toc_range`` was only known on the path that ran TOC detection. Without it, a page-less
    "19.0 References" entry is textually identical to the body heading it points at, both lines
    match the record, ``resolve_record_line`` fails open, and ``backmatterLine`` is dropped —
    leaving References and Appendices buried in the main chunks for every bookmarked document.
    """

    def _doc(self):
        return "\n".join([
            "# Study Protocol", "", "## Table of Contents", "",
            "1.0 Background", "2.0 Methods", "19.0 References", "",
            "## 1.0 Background", "body paragraph. " * 30,
            "## 2.0 Methods", "body paragraph. " * 30,
            "## 19.0 References", "Smith J et al. Lancet. 2020.",
        ])

    RECORDS = [{"title": "1.0 Background", "level": 1, "page": 3},
               {"title": "2.0 Methods", "level": 1, "page": 5},
               {"title": "19.0 References", "level": 1, "page": 20}]

    def _backmatter(self, monkeypatch, tmp_path, records):
        doc = self._doc()
        if records is None:
            _, updates, _, _ = _derive(doc)
        else:
            _, updates, _, _ = _derive_with_bookmarks(
                monkeypatch, tmp_path, doc, records
            )
        line = updates.get("backmatterLine")
        return line, (doc.split("\n")[line] if isinstance(line, int) else None)

    def test_resolves_with_paged_bookmarks(self, monkeypatch, tmp_path):
        line, text = self._backmatter(monkeypatch, tmp_path, self.RECORDS)
        assert text == "## 19.0 References", (line, text)

    def test_resolves_with_page_less_bookmarks(self, monkeypatch, tmp_path):
        # The case that failed: no page to disambiguate with, so the TOC line had to be excluded.
        pageless = [dict(record, page=None) for record in self.RECORDS]
        line, text = self._backmatter(monkeypatch, tmp_path, pageless)
        assert text == "## 19.0 References", (line, text)

    def test_points_at_the_body_not_the_toc_entry(self, monkeypatch, tmp_path):
        line, _ = self._backmatter(monkeypatch, tmp_path, self.RECORDS)
        assert line > self._doc().split("\n").index("19.0 References")

    def test_matches_the_printed_toc_path(self, monkeypatch, tmp_path):
        # Printed path first so an extract/verify monkeypatch cannot leak into it.
        without, _ = self._backmatter(monkeypatch, tmp_path, None)
        with_bookmarks, _ = self._backmatter(monkeypatch, tmp_path, self.RECORDS)
        assert with_bookmarks == without


class TestOutlineIsOneObject:
    def test_all_fields_arrive_in_a_single_dict(self):
        # The fields used to be spread over three read-modify-write cycles of outline.json,
        # which wrote "tokens" twice with two different values. Returning one dict makes that
        # class of bug unrepresentable, so this asserts the shape rather than a call count.
        doc = "\n".join([
            "## Table of Contents",
            "1.0 Background\t3",
            "2.0 Objectives\t5",
            "3.0 Design\t7",
            "",
            "## 1.0 Background",
            "body paragraph. " * 60,
        ])
        _, updates, _, _ = _derive(doc)
        assert {"tocStartLine", "tocEndLine", "toc", "tokens", "toc_source"} <= set(updates)

    def test_tokens_reflect_the_returned_document(self):
        doc = "# Title\n\n" + ("Some content paragraph. " * 40)
        result, updates, _, _ = _derive(doc)
        assert updates["tokens"] == len(result) // 4
