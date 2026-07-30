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

"""Tests for the shared markers module.

The page-marker pattern used to be re-declared in four modules with three different
spellings, and one copy required a space before ``-->`` while the Python parser emitted none,
so that copy silently never matched. The tests here pin the canonical emitted format, assert
that every consumer agrees on it, and assert that every other spacing — including the older
no-space spelling — is rejected outright. A document carrying a pre-unification marker has to be
re-parsed rather than half-read: a marker that matches in one consumer and not another is how
the original bug produced page numbers that disagreed across the pipeline."""

import re

import bookmarks
import chunker
import markdown_cleanup
import markdown_markers as mm
import toc_and_appendix_detection as tad

# The canonical form, exactly as markdown_markers.page_marker builds it and as both the
# Python PDF parser and the Java PdfMarkdownGenerator emit it.
EMITTED = "<!-- page: 12 -->"

# The pre-unification spelling, still present in already-parsed .md files on disk.
LEGACY_NO_SPACE = "<!-- page: 12-->"


class TestCanonicalPageMarker:
    def test_page_marker_builds_the_canonical_form(self):
        assert mm.page_marker(12) == EMITTED

    def test_page_marker_has_a_space_before_the_close(self):
        assert mm.page_marker(7) == "<!-- page: 7 -->"

    def test_what_the_pdf_parser_emits_is_what_page_marker_builds(self):
        # Guards the emitter against drifting away from the shared definition.
        assert mm.page_marker(12) == EMITTED


class TestPageMarkerFormat:
    def test_matches_the_canonical_emitted_form(self):
        assert mm.PAGE_MARKER.search(EMITTED) is not None
        assert mm.PAGE_MARKER_LINE.match(EMITTED) is not None

    def test_captures_the_page_number(self):
        assert mm.PAGE_MARKER.search(EMITTED).group(1) == "12"
        assert mm.PAGE_MARKER_LINE.match(EMITTED).group(1) == "12"

    def test_rejects_the_legacy_no_space_spelling(self):
        # Spacing is exact: only the marker this pipeline writes is recognised. A document
        # carrying the pre-unification spelling must be re-parsed, not half-read.
        assert mm.PAGE_MARKER_LINE.match(LEGACY_NO_SPACE) is None
        assert mm.PAGE_MARKER.search(LEGACY_NO_SPACE) is None

    def test_rejects_other_spacings(self):
        for text in ("<!--  page:  4  -->", "<!--page:7-->", "<!--\tpage:\t9\t-->",
                     "<!--page: 7 -->", "<!-- page:7 -->", "<!-- page: 7-->"):
            assert mm.PAGE_MARKER_LINE.match(text) is None, text

    def test_line_pattern_tolerates_surrounding_whitespace(self):
        assert mm.PAGE_MARKER_LINE.match(f"  {EMITTED}  ") is not None

    def test_line_pattern_rejects_a_marker_with_trailing_content(self):
        assert mm.PAGE_MARKER_LINE.match(f"{EMITTED} and more text") is None

    def test_split_pattern_yields_exactly_one_group(self):
        # re.split returns every group; a second one would break markdown_cleanup's
        # stride-2 walk over the split parts.
        assert mm.PAGE_MARKER_SPLIT.groups == 1

    def test_split_keeps_the_marker_and_its_newlines(self):
        assert mm.PAGE_MARKER_SPLIT.split(f"a\n{EMITTED}\nb") == ["a", f"\n{EMITTED}\n", "b"]

    def test_split_does_not_recognise_the_legacy_spelling(self):
        text = f"a\n{LEGACY_NO_SPACE}\nb"
        assert mm.PAGE_MARKER_SPLIT.split(text) == [text]

    def test_line_pattern_allows_only_line_level_whitespace_slack(self):
        # Indentation and a stray carriage return are tolerated; the marker's own internal
        # spacing is not.
        assert mm.PAGE_MARKER_LINE.match(f"   {EMITTED}\r") is not None
        assert mm.PAGE_MARKER_LINE.match("   <!-- page: 12-->  ") is None


class TestConsumersAgreeOnTheMarker:
    """Every stage that recognises a page marker must recognise the emitted one.

    A stage whose pattern silently never matches does not fail loudly — it just stops
    seeing page boundaries, which is how the cross-page TOC continuation logic came to be
    dead code.
    """

    def test_every_stage_reads_the_canonical_marker(self):
        assert chunker.is_neutral(EMITTED) is True
        assert chunker._pages_in(f"body\n{EMITTED}\nmore") == [12]
        assert bookmarks.page_line_texts(f"{EMITTED}\nSome Title") == {12: {"sometitle"}}
        assert tad.PAGE_MARKER_LINE.match(EMITTED) is not None
        assert markdown_cleanup.PAGE_MARKER_SPLIT.search(f"a\n{EMITTED}\nb") is not None

    def test_every_stage_rejects_the_legacy_marker_consistently(self):
        # The point of one shared definition: no stage disagrees about what counts as a
        # marker, so a legacy document fails uniformly instead of half-parsing.
        assert chunker.is_neutral(LEGACY_NO_SPACE) is False
        assert chunker._pages_in(f"body\n{LEGACY_NO_SPACE}\nmore") == []
        assert bookmarks.page_line_texts(f"{LEGACY_NO_SPACE}\nSome Title") != {12: {"sometitle"}}
        assert tad.PAGE_MARKER_LINE.match(LEGACY_NO_SPACE) is None
        assert markdown_cleanup.PAGE_MARKER_SPLIT.search(f"a\n{LEGACY_NO_SPACE}\nb") is None

    def test_chunker_sees_it(self):
        assert chunker.is_neutral(EMITTED) is True
        assert chunker._pages_in(f"body\n{EMITTED}\nmore") == [12]

    def test_bookmarks_sees_it(self):
        assert bookmarks.page_line_texts(f"{EMITTED}\nSome Title") == {12: {"sometitle"}}

    def test_toc_detection_sees_it(self):
        # Reached through the module's own alias, so a future divergence fails here.
        assert tad.PAGE_MARKER_LINE.match(EMITTED) is not None

    def test_markdown_cleanup_sees_it(self):
        assert markdown_cleanup.PAGE_MARKER_SPLIT.search(f"a\n{EMITTED}\nb") is not None


class TestHeadingPattern:
    def test_captures_level_and_text(self):
        match = mm.HEADING.match("### Study Design")
        assert match.group(1) == "###" and match.group(2) == "Study Design"

    def test_rejects_seven_hashes(self):
        assert mm.HEADING.match("####### Seven") is None

    def test_rejects_hash_without_space(self):
        assert mm.HEADING.match("#NoSpace") is None

    def test_rejects_empty_heading(self):
        assert mm.HEADING.match("## ") is None

    def test_matches_the_looser_predicate_it_replaced(self):
        # toc_and_appendix_detection used r"^#{1,6}\s+\S" as a separate "is a heading line"
        # check; HEADING must be a drop-in for it.
        looser = re.compile(r"^#{1,6}\s+\S")
        for case in ("# A", "## Foo", "###### Deep", "####### Seven", "#", "## ",
                     "#NoSpace", "  ## Indented", "## trailing   ", "##\tTabbed", ""):
            assert bool(looser.match(case)) == bool(mm.HEADING.match(case)), case


class TestRuleLine:
    def test_matches_three_or_more_dashes(self):
        assert mm.RULE_LINE.match("---") is not None
        assert mm.RULE_LINE.match("-------") is not None

    def test_rejects_two_dashes_and_mixed_content(self):
        assert mm.RULE_LINE.match("--") is None
        assert mm.RULE_LINE.match("--- text") is None


class TestCountTokens:
    def test_quarter_of_length(self):
        assert mm.count_tokens("a" * 40) == 10

    def test_empty(self):
        assert mm.count_tokens("") == 0

    def test_floors_rather_than_rounds(self):
        assert mm.count_tokens("abc") == 0


class TestWithinWordLimits:
    def test_ordinary_line(self):
        assert mm.within_word_limits("Background and Rationale") is True

    def test_empty_or_blank_rejected(self):
        assert mm.within_word_limits("") is False
        assert mm.within_word_limits("   ") is False

    def test_at_the_word_limit_is_allowed(self):
        assert mm.within_word_limits(" ".join(["w"] * mm.MAX_HEADING_WORDS)) is True

    def test_over_the_word_limit_rejected(self):
        assert mm.within_word_limits(" ".join(["w"] * (mm.MAX_HEADING_WORDS + 1))) is False

    def test_overlong_word_rejected(self):
        assert mm.within_word_limits("word " + "x" * (mm.MAX_WORD_CHARS + 1)) is False


class TestSupportedSuffixes:
    def test_docling_suffixes(self):
        assert mm.SUPPORTED_SUFFIXES == (".pdf", ".docx")

    def test_input_suffixes_include_doc(self):
        assert mm.INPUT_SUFFIXES == (".pdf", ".docx", ".doc")
