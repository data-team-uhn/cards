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

"""Direct unit tests for the chunker's internal split/pack/heading helpers.

These carry the fiddliest logic in the module — page-marker migration, standalone-heading
look-ahead, over-budget splitting, small-tail folding — and were previously exercised only
indirectly through chunk_file(). Tokens are len(text) // 4 (see markdown_markers.count_tokens), so
a block of N tokens is a string of length 4*N."""

import json
from pathlib import Path

from bookmarks import build_line_index, build_lines_catalog
import chunker
from chunker import DEFAULT_HEADING, DEFAULT_MAX_TOKENS


def _md_path(tmp_path: Path) -> Path:
    return tmp_path / "doc.md"


class TestPagesIn:
    def test_sorted_and_deduplicated(self):
        text = "a\n<!-- page: 3 -->\nb\n<!-- page: 1 -->\n<!-- page: 3 -->"
        assert chunker._pages_in(text) == [1, 3]

    def test_no_markers(self):
        assert chunker._pages_in("") == []
        assert chunker._pages_in("plain text") == []


class TestBackmatterHeading:
    def test_atx_heading(self):
        assert chunker._backmatter_heading("## References\n\ncitation") == ["References"]

    def test_bold_heading(self):
        assert chunker._backmatter_heading("**Appendix A**\n\nbody") == ["Appendix A"]

    def test_plain_first_line(self):
        assert chunker._backmatter_heading("Plain first line\n\nmore") == ["Plain first line"]

    def test_list_marker_and_partial_emphasis_stripped(self):
        # Docling emits backmatter headings as list items with only part of them bold, e.g.
        # "- 20.0 **Appendices**" on a real protocol — neither an ATX heading nor fully bold,
        # so it used to reach the catalog verbatim.
        assert chunker._backmatter_heading("- 20.0 **Appendices**\n\nbody") == ["20.0 Appendices"]
        assert chunker._backmatter_heading("* 19.0 References") == ["19.0 References"]
        assert chunker._backmatter_heading("+ **Annexes**") == ["Annexes"]

    def test_a_plain_list_item_keeps_its_text(self):
        assert chunker._backmatter_heading("- plain item") == ["plain item"]

    def test_empty_uses_default(self):
        assert chunker._backmatter_heading("") == [DEFAULT_HEADING]
        assert chunker._backmatter_heading("   \n  ") == [DEFAULT_HEADING]


class TestSplitTrailingPageMarkers:
    def test_single_trailing_marker(self):
        assert chunker._split_trailing_page_markers("Body text\n\n<!-- page: 5 -->") \
            == ("Body text", "<!-- page: 5 -->")

    def test_multiple_trailing_markers(self):
        result = chunker._split_trailing_page_markers("X\n\n<!-- page: 5 -->\n<!-- page: 6 -->")
        assert result == ("X", "<!-- page: 5 -->\n<!-- page: 6 -->")

    def test_no_trailing_marker(self):
        assert chunker._split_trailing_page_markers("No markers here") is None


class TestMoveTrailingPageMarkers:
    def test_marker_moved_to_next_part(self):
        result = chunker._move_trailing_page_markers(["A\n\n<!-- page: 2 -->", "B body"])
        assert result == ["A", "<!-- page: 2 -->\n\nB body"]

    def test_single_part_unchanged(self):
        assert chunker._move_trailing_page_markers(["only"]) == ["only"]

    def test_last_part_marker_left_in_place(self):
        # Nowhere to move a marker at the very end.
        parts = ["A", "B\n\n<!-- page: 9 -->"]
        assert chunker._move_trailing_page_markers(parts) == parts


class TestSplitByParagraphs:
    def test_all_fits_single_part(self):
        text = "Para one.\n\nPara two.\n\nPara three."
        assert chunker._split_by_paragraphs(text, 10_000) == [text]

    def test_each_paragraph_over_budget_split(self):
        para = "x" * 40  # 10 tokens
        text = "\n\n".join([para, para, para])
        assert chunker._split_by_paragraphs(text, 5) == [para, para, para]

    def test_single_oversized_paragraph_kept_whole(self):
        big = "y" * 100  # 25 tokens, no blank-line boundary to split on
        assert chunker._split_by_paragraphs(big, 5) == [big]


class TestPackBlocks:
    def test_all_fits_merged(self):
        blocks = ["Block A text", "Block B text", "Block C text"]
        assert chunker._pack_blocks(blocks, 10_000) == \
            ["Block A text\n\nBlock B text\n\nBlock C text"]

    def test_budget_forces_flush(self):
        block = "x" * 40  # 10 tokens; two fit in 25, three do not
        assert chunker._pack_blocks([block, block, block], 25) == \
            [block + "\n\n" + block, block]

    def test_standalone_heading_never_emitted_alone(self):
        heading = "## Section Heading"
        body = "z" * 200  # 50 tokens — over budget, but the heading must not flush alone
        assert chunker._pack_blocks([heading, body], 25) == [heading + "\n\n" + body]

    def test_middle_heading_flushed_with_following_when_lookahead_over_budget(self):
        a = "a" * 80   # 20 tokens
        heading = "## Middle Heading"
        c = "c" * 80   # 20 tokens
        # a + heading + c is over 25, so a flushes and the heading attaches to c.
        assert chunker._pack_blocks([a, heading, c], 25) == [a, heading + "\n\n" + c]


class TestOutlineSizeGate:
    """The gate that decides whether the outline pass runs at all.

    It lives in ``derive_outline``: when there are no PDF bookmarks and the document is below
    ``min_structure_tokens``, derivation is skipped. Records beat the gate, because a
    bookmarked document's outline is already in hand and even a small one needs its ``toc``
    downstream.
    """

    SMALL = "# Tiny\n\n## Table of Contents\n\nAlpha\t1\nBeta\t2\nGamma\t3\n\n## Alpha\n\nbody\n"

    def _tree(self, tmp_path, monkeypatch=None, records=None):
        md_path = _md_path(tmp_path)
        if records is not None:
            (tmp_path / "doc.pdf").write_bytes(b"%PDF-1.4")
            monkeypatch.setattr(
                "toc_and_appendix_detection.extract_bookmarks",
                lambda *a, **k: records,
            )
            monkeypatch.setattr(
                "toc_and_appendix_detection.verify_bookmarks",
                lambda *a, **k: records,
            )
        return chunker.build_chunk_tree(
            self.SMALL,
            "doc.md",
            md_path,
            DEFAULT_MAX_TOKENS,
            10 ** 9,
        )

    def test_small_document_without_records_is_left_alone(self, tmp_path):
        tree = self._tree(tmp_path)
        # Not even the TOC is rewritten: the document is sent whole, so there is nothing to route.
        assert tree["markdown"] == self.SMALL
        assert tree["outline"]["toc_source"] == "none"
        assert tree["outline"]["toc"] == []
        assert tree["records"] == []

    def test_small_document_with_records_still_gets_its_outline(self, tmp_path, monkeypatch):
        tree = self._tree(tmp_path, monkeypatch, [{"title": "Alpha", "level": 1, "page": 1}])
        assert tree["outline"]["toc_source"] == "pdf-bookmarks"
        assert tree["outline"]["toc"] == ["Alpha"]

    def test_both_are_still_unchunked(self, tmp_path, monkeypatch):
        # The gate above is about the outline; the chunking decision is separate and unaffected.
        assert self._tree(tmp_path)["chunked"] is False
        assert self._tree(
            tmp_path, monkeypatch, [{"title": "Alpha", "level": 1, "page": 1}]
        )["chunked"] is False

    def test_a_large_document_runs_the_outline_pass_without_records(self, tmp_path):
        big = self.SMALL + ("Body sentence that carries it along. " * 200)
        tree = chunker.build_chunk_tree(
            big, "doc.md", _md_path(tmp_path), DEFAULT_MAX_TOKENS, 1
        )
        assert tree["outline"]["toc_source"] == "md-toc"


class TestIsHeadingOnly:
    def test_single_heading(self):
        assert chunker._is_heading_only("# 6.0 Schedule of Assessments") is True

    def test_two_headings_with_no_prose(self):
        # Broader than _is_standalone_heading, which requires exactly one content line: two
        # headings back to back are just as unusable as a chunk file.
        assert chunker._is_heading_only("# 7.0 Analysis\n\n## 7.1 Primary") is True

    def test_heading_with_body_is_not(self):
        assert chunker._is_heading_only("# 6.0 Schedule\n\nSome prose.") is False

    def test_blank_and_neutral_lines_ignored(self):
        assert chunker._is_heading_only("\n<!-- page: 4 -->\n# 6.0 Schedule\n\n---\n") is True

    def test_body_only_is_not(self):
        assert chunker._is_heading_only("Just prose, no heading.") is False

    def test_empty_is_not(self):
        assert chunker._is_heading_only("") is False


class TestMergeHeadingOnlyParts:
    """No chunk file may be a bare title.

    Regression, reproduced at default settings: a section whose body is one over-budget
    paragraph made _split_by_paragraphs flush the heading alone, giving a 29-byte
    'Chunk-1.1.md' holding '# 6.0 Schedule of Assessments' and nothing else, with the table
    next to it labelled only by inheritance. Docling emits a table as consecutive '|' lines
    with no blank line, so the whole table is one paragraph and this is not an edge case.
    """

    def test_heading_takes_the_following_part(self):
        assert chunker._merge_heading_only_parts(["# 6.0 Schedule", "| a | b |"]) == \
            ["# 6.0 Schedule\n\n| a | b |"]

    def test_trailing_heading_folds_backwards(self):
        # _pack_blocks guards its lookahead with `index + 1 < n`, so a document ending on a bare
        # heading leaves it last — where there is nothing after it to take.
        assert chunker._merge_heading_only_parts(["Body text.", "## 5.2 Deferred"]) == \
            ["Body text.\n\n## 5.2 Deferred"]

    def test_consecutive_headings_take_the_body(self):
        parts = ["# 7.0 Analysis", "## 7.1 Primary", "Prose body."]
        assert chunker._merge_heading_only_parts(parts) == \
            ["# 7.0 Analysis\n\n## 7.1 Primary\n\nProse body."]

    def test_middle_heading_merges_forwards_not_backwards(self):
        parts = ["First body.", "## 5.2 Second", "Second body."]
        assert chunker._merge_heading_only_parts(parts) == \
            ["First body.", "## 5.2 Second\n\nSecond body."]

    def test_a_lone_heading_part_is_left_alone(self):
        # Nothing to merge with; a document that is only a heading has no better answer.
        assert chunker._merge_heading_only_parts(["# 6.0 Schedule"]) == ["# 6.0 Schedule"]

    def test_parts_without_bare_headings_are_untouched(self):
        parts = ["# 1.0 Intro\n\nbody", "## 1.1 More\n\nbody"]
        assert chunker._merge_heading_only_parts(parts) == parts

    def test_empty(self):
        assert chunker._merge_heading_only_parts([]) == []


class TestNoHeadingOnlyChunkFiles:
    """The same two cases end to end, at default max_tokens."""

    def _files(self, md, tmp_path):
        tree = chunker.build_chunk_tree(
            md, "doc.md", _md_path(tmp_path), DEFAULT_MAX_TOKENS, 1
        )
        return [(entry["file"], len(chunk["text"]))
                for entry, chunk in zip(tree["catalog"]["chunks"], tree["chunks"])]

    def test_heading_stays_with_an_over_budget_table(self, tmp_path):
        # One row of ten cells is ~20 tokens, so 130 rows clears the 2000-token budget.
        row = "| " + " | ".join(["Procedure with a realistic label"] + ["X"] * 9) + " |"
        table = "\n".join(["| A | B | C | D | E | F | G | H | I | J |"] + [row] * 130)
        files = self._files(f"# 6.0 Schedule of Assessments\n\n{table}", tmp_path)
        assert len(files) == 1, files
        assert files[0][1] > 1000, files

    def test_trailing_bare_heading_is_not_its_own_file(self, tmp_path):
        md = ("# 5.0 Methods\n\n## 5.1 First Subsection\n\n"
              + ("Body sentence that carries it along. " * 220)
              + "\n\n## 5.2 Deferred Subsection\n")
        files = self._files(md, tmp_path)
        assert all(size > 100 for _, size in files), files


class TestMergeSmallTextTails:
    def test_small_text_tail_folded_into_previous(self):
        assert chunker._merge_small_text_tails(["First part body.", "tiny"], 500) == \
            ["First part body.\n\ntiny"]

    def test_tail_with_heading_never_merged(self):
        parts = ["First", "## Second Heading"]
        assert chunker._merge_small_text_tails(parts, 500) == parts

    def test_large_tail_not_merged(self):
        big = "w" * 4000  # 1000 tokens
        assert chunker._merge_small_text_tails(["First", big], 500) == ["First", big]


class TestSplitIntoTopChunks:
    def test_preamble_and_sections(self):
        lines = ["preamble text", "# Section One", "body one", "# Section Two", "body two"]
        chunks = chunker._split_into_top_chunks(lines, 1)
        assert [c["number"] for c in chunks] == [0, 1, 2]
        assert chunks[0]["text"] == "preamble text"
        # Each section keeps its own heading line at the head of its text; catalog labels
        # are derived per emitted part later, by _part_heading.
        assert chunks[1]["text"] == "# Section One\nbody one"
        assert chunks[2]["text"] == "# Section Two\nbody two"

    def test_no_headings_single_chunk(self):
        assert chunker._split_into_top_chunks(["just", "text"], None) == \
            [{"number": 0, "text": "just\ntext"}]

    def test_empty(self):
        assert chunker._split_into_top_chunks([], None) == []


class TestSubchunkBlocks:
    def test_splits_at_subheadings(self):
        chunk_text = (
            "# Main Section\n\nlead in text\n\n"
            "## Sub Alpha\n\naaa\n\n"
            "## Sub Beta\n\nbbb"
        )
        assert chunker._subchunk_blocks(chunk_text, 1) == [
            "# Main Section\n\nlead in text",
            "## Sub Alpha\n\naaa",
            "## Sub Beta\n\nbbb",
        ]

    def test_no_subheadings_single_block(self):
        assert chunker._subchunk_blocks("# Main Section\n\njust body", 1) == \
            ["# Main Section\n\njust body"]


class TestPartHeading:
    def test_collects_atx_within_two_levels(self):
        part = "## First Heading\n\ntext\n\n### Sub Heading Here"
        assert chunker._part_heading(part, None) == ["First Heading", "Sub Heading Here"]

    def test_excludes_headings_deeper_than_beginning_plus_one(self):
        part = "## Alpha Heading\n\n### Beta Heading\n\n#### Gamma Heading"
        assert chunker._part_heading(part, None) == ["Alpha Heading", "Beta Heading"]

    def test_no_heading_copies_previous(self):
        assert chunker._part_heading("no heading text", ["Prev Heading"]) == ["Prev Heading"]

    def test_no_heading_no_previous_uses_default(self):
        assert chunker._part_heading("no heading text", None) == [DEFAULT_HEADING]


class TestFirstChunkHeading:
    """The first catalog entry must use its real heading when it has one.

    Regression: the first entry was forced to DEFAULT_HEADING regardless of content, so every
    document without a preamble lost a perfectly good label like "1.0 Introduction" — and it
    contradicted _part_heading's own documented contract, which already falls back to
    DEFAULT_HEADING exactly when a part has no heading and there is no previous entry to copy.
    """

    PARAGRAPH = "Body sentence that carries the section text along. " * 40

    def _tree(self, markdown, tmp_path, max_tokens=DEFAULT_MAX_TOKENS):
        return chunker.build_chunk_tree(
            markdown, "doc.md", _md_path(tmp_path), max_tokens, 1
        )

    def test_no_preamble_uses_the_real_heading(self, tmp_path):
        md = f"# 1.0 Introduction{chr(10)}{chr(10)}{self.PARAGRAPH}"
        first = self._tree(md, tmp_path)["catalog"]["chunks"][0]
        assert first["heading"] == ["1.0 Introduction"]
        assert first["file"] == "Chunk-1.md"

    def test_preamble_with_no_headings_uses_the_default(self, tmp_path):
        # The preamble has to be over budget to stand alone: a short one is packed together with
        # the following section, and that combined chunk really does contain "1.0 Introduction",
        # so labelling it with the heading is right.
        preamble = "Loose front matter carrying no heading whatsoever. " * 40
        md = (f"{preamble}{chr(10)}{chr(10)}"
              f"# 1.0 Introduction{chr(10)}{chr(10)}{self.PARAGRAPH}")
        chunks = self._tree(md, tmp_path, max_tokens=300)["catalog"]["chunks"]
        assert chunks[0]["file"] == "Chunk-0.md"
        assert chunks[0]["heading"] == [DEFAULT_HEADING]
        # And the section that follows keeps its own heading.
        assert any(c["heading"] == ["1.0 Introduction"] for c in chunks[1:]), \
            [c["heading"] for c in chunks]

    def test_preamble_chunk_keeps_the_default_even_when_packed(self, tmp_path):
        # A short preamble is packed together with the following section, so the chunk does
        # contain "1.0 Introduction" — but Chunk-0 is front matter and is labelled as such
        # regardless of what got packed into it.
        md = (f"One short front-matter line.{chr(10)}{chr(10)}"
              f"# 1.0 Introduction{chr(10)}{chr(10)}{self.PARAGRAPH}")
        first = self._tree(md, tmp_path)["catalog"]["chunks"][0]
        assert first["file"] == "Chunk-0.md"
        assert first["heading"] == [DEFAULT_HEADING]

    def test_preamble_stand_out_lines_do_not_become_the_label(self, tmp_path):
        # A title block's bold/ALL-CAPS lines are field labels, not section titles.
        preamble = (f"**PRINCIPAL INVESTIGATOR:**{chr(10)}{chr(10)}Dr Somebody{chr(10)}{chr(10)}"
                    + "Front matter prose that runs on for a while. " * 40)
        md = (f"{preamble}{chr(10)}{chr(10)}# 1.0 Introduction{chr(10)}{chr(10)}{self.PARAGRAPH}")
        chunks = self._tree(md, tmp_path, max_tokens=300)["catalog"]["chunks"]
        assert chunks[0]["heading"] == [DEFAULT_HEADING]

    def test_later_chunks_unaffected(self, tmp_path):
        md = "".join(
            f"# {i}.0 Section Heading{chr(10)}{chr(10)}{self.PARAGRAPH}{chr(10)}{chr(10)}"
            for i in range(1, 6)
        )
        headings = [c["heading"] for c in self._tree(md, tmp_path)["catalog"]["chunks"]]
        assert all(h != [DEFAULT_HEADING] for h in headings), headings


class TestRepeatedLines:
    """Running headers/footers must not become catalog headings.

    A line like CONFIDENTIAL sits immediately after a page marker with a blank line after it,
    which is exactly as isolated as a real stand-out heading at the top of a page. Recurrence is
    the only signal that separates them, so refusing anything next to a page marker would throw
    the real headings away too.
    """

    def test_detects_a_line_repeated_on_every_page(self):
        lines = []
        for page in range(1, 5):
            lines += [f"<!-- page: {page} -->", "CONFIDENTIAL", "", "Body text."]
        repeated = chunker.repeated_lines(lines)
        assert chunker.normalize_title("CONFIDENTIAL") in repeated
        assert chunker.normalize_title("Body text.") in repeated

    def test_a_heading_appearing_once_is_not_repeated(self):
        lines = ["<!-- page: 1 -->", "REAL HEADING", "", "prose", "more prose"]
        assert chunker.repeated_lines(lines) == frozenset()

    def test_twice_is_not_enough(self):
        lines = ["SEEN TWICE", "", "SEEN TWICE", "", "other"]
        assert chunker.repeated_lines(lines) == frozenset()

    def test_page_markers_themselves_are_ignored(self):
        lines = [f"<!-- page: {n} -->" for n in range(1, 6)]
        assert chunker.repeated_lines(lines) == frozenset()

    def test_part_heading_refuses_a_repeated_line(self):
        part = f"<!-- page: 7 -->{chr(10)}CONFIDENTIAL{chr(10)}{chr(10)}Body text follows."
        repeated = frozenset({chunker.normalize_title("CONFIDENTIAL")})
        # Without the set it is accepted as a stand-out heading; with it, the part falls through.
        assert chunker._part_heading(part, None) == ["CONFIDENTIAL"]
        assert chunker._part_heading(part, None, repeated) == [DEFAULT_HEADING]

    def test_a_real_heading_after_a_page_marker_still_counts(self):
        part = f"<!-- page: 7 -->{chr(10)}**5.0 METHODS**{chr(10)}{chr(10)}Body text follows."
        repeated = frozenset({chunker.normalize_title("CONFIDENTIAL")})
        assert chunker._part_heading(part, None, repeated) == ["5.0 METHODS"]


class TestSplitOversized:
    def test_no_subheadings_paragraph_split(self):
        chunk_text = "\n\n".join(["p" * 40] * 4)  # four 10-token paragraphs
        parts = chunker._split_oversized(chunk_text, 0, 15)
        assert len(parts) == 4
        assert all(chunker.count_tokens(p) <= 15 for p in parts)

    def test_with_subheadings_packs_then_stays_within_budget(self):
        chunk_text = "# Top Heading\n\n" + "\n\n".join(
            f"## Sub {i} Heading\n\n" + "q" * 40 for i in range(1, 5)
        )
        parts = chunker._split_oversized(chunk_text, 1, 25)
        assert len(parts) >= 2
        assert all(chunker.count_tokens(p) <= 25 for p in parts)


class TestSubchunkBlocksNumberedFallback:
    def test_splits_at_bold_numbered_when_no_atx(self):
        # No ATX sub-heading, but Docling left bold numbered headings: split on those.
        text = (
            "## 5 Analysis\n\nlead-in\n\n"
            "**5.1 First**\n\nalpha body\n\n"
            "**5.2 Second**\n\nbeta body"
        )
        blocks = chunker._subchunk_blocks(text, boundary_level=2)
        assert len(blocks) == 3
        assert blocks[0].startswith("## 5 Analysis")
        assert blocks[1].startswith("**5.1 First**")
        assert blocks[2].startswith("**5.2 Second**")

    def test_atx_subheadings_take_precedence(self):
        # A real ATX sub-heading wins; the bold line is not treated as a boundary.
        text = "## 5 Analysis\n\n### 5.1 First\n\na\n\n**bold note**\n\nb"
        blocks = chunker._subchunk_blocks(text, boundary_level=2)
        assert len(blocks) == 2

    def test_no_numbered_standout_single_block(self):
        text = "## 5 Analysis\n\njust paragraphs\n\nmore text"
        assert chunker._subchunk_blocks(text, boundary_level=2) == [text]


class TestBookmarksStorage:
    def _outline(self, tmp_path):
        return json.loads((tmp_path / "Chunks" / "outline.json").read_text(encoding="utf-8"))

    def test_a_leftover_sidecar_is_not_treated_as_bookmarks(self, tmp_path):
        # Records now come from a sibling PDF or the printed TOC only. A bookmarks.json left
        # beside the .md by an older run is ignored, which is what stops it reporting the
        # previous document's outline for this one.
        md = tmp_path / "doc.md"
        md.write_text("# Title\n\nshort body\n", encoding="utf-8")
        records = [{"title": "Alpha Section", "level": 1, "page": 1}]
        (tmp_path / "bookmarks.json").write_text(json.dumps(records) + "\n", encoding="utf-8")
        chunker.chunk_file(str(md), min_structure_tokens=10 ** 9)
        outline = self._outline(tmp_path)
        assert outline["toc_source"] == "none"
        assert outline["toc"] == []
        assert "bookmarks" not in outline

    def test_no_sibling_pdf_no_toc(self, tmp_path):
        md = tmp_path / "doc.md"
        md.write_text("# Title\n\nshort body\n", encoding="utf-8")
        chunker.chunk_file(str(md), min_structure_tokens=10 ** 9)
        outline = self._outline(tmp_path)
        assert outline["toc"] == []
        assert outline["toc_source"] == "none"
        assert "bookmarks" not in outline


class TestRecordCutKeys:
    def _index(self, md: str):
        lines = md.split("\n")
        return lines, build_line_index(build_lines_catalog(lines))

    def test_resolves_unique_non_atx(self):
        md = "<!-- page: 1 -->\n## 5 Analysis\n\nData Sharing\n\nbody"
        lines, index = self._index(md)
        keys = chunker._record_cut_keys(
            [{"title": "Data Sharing", "page": 1}], lines, None, index
        )
        assert keys == frozenset({chunker.normalize_title("Data Sharing")})

    def test_excludes_atx_match(self):
        md = "<!-- page: 1 -->\n## Data Sharing\n\nbody"
        lines, index = self._index(md)
        keys = chunker._record_cut_keys(
            [{"title": "Data Sharing", "page": 1}], lines, None, index
        )
        assert keys == frozenset()

    def test_excludes_toc_range(self):
        md = "<!-- page: 1 -->\nData Sharing\n\nbody"  # "Data Sharing" is line index 1
        lines, index = self._index(md)
        keys = chunker._record_cut_keys(
            [{"title": "Data Sharing", "page": 1}], lines, (1, 1), index
        )
        assert keys == frozenset()

    def test_no_records_is_empty(self):
        lines, index = self._index("x")
        assert chunker._record_cut_keys([], lines, None, index) == frozenset()


class TestSubchunkBlocksRecordTier:
    def test_splits_at_resolved_record_lines(self):
        text = "## 5 Analysis\n\nlead\n\nData Sharing\n\nalpha\n\nGenomic Data\n\nbeta"
        keys = frozenset({chunker.normalize_title("Data Sharing"), chunker.normalize_title("Genomic Data")})
        blocks = chunker._subchunk_blocks(text, boundary_level=2, cut_keys=keys)
        assert len(blocks) == 3
        assert blocks[1].startswith("Data Sharing")
        assert blocks[2].startswith("Genomic Data")

    def test_atx_line_not_used_by_record_tier(self):
        text = "## 5 Analysis\n\n## Data Sharing\n\nbody"
        keys = frozenset({chunker.normalize_title("Data Sharing")})
        assert chunker._subchunk_blocks(text, boundary_level=2, cut_keys=keys) == [text]

    def test_records_take_precedence_over_numbered_standout(self):
        text = "## 5 Analysis\n\nData Sharing\n\nalpha\n\n**5.1 Sub**\n\nbeta"
        keys = frozenset({chunker.normalize_title("Data Sharing")})
        blocks = chunker._subchunk_blocks(text, boundary_level=2, cut_keys=keys)
        assert blocks[1].startswith("Data Sharing") and "**5.1 Sub**" in blocks[1]

    def test_cut_keys_thread_through_split_oversized(self):
        text = "## 5 Analysis\n\n" + "x" * 120 + "\n\nData Sharing\n\n" + "y" * 120
        keys = frozenset({chunker.normalize_title("Data Sharing")})
        parts = chunker._split_oversized(text, 2, 20, keys)
        assert any(part.startswith("Data Sharing") for part in parts)

    def test_unnumbered_bold_does_not_split(self):
        # Bold but no numeric prefix -> not a boundary; whole chunk stays one block.
        text = "## 5 Analysis\n\nlead\n\n**Important Note**\n\nbody\n\n**Another Note**\n\nmore"
        assert chunker._subchunk_blocks(text, boundary_level=2) == [text]
