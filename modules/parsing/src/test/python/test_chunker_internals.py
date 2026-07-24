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

"""Direct unit tests for the chunker's internal split/pack/heading helpers.

These carry the fiddliest logic in the module — page-marker migration, standalone-heading
look-ahead, over-budget splitting, small-tail folding — and were previously exercised only
indirectly through chunk_file(). Tokens are len(text) // 4 (see chunker._count_tokens), so
a block of N tokens is a string of length 4*N."""

import chunker
from chunker import DEFAULT_HEADING


class TestPagesIn:
    def test_sorted_and_deduplicated(self):
        text = "a\n<!-- page: 3-->\nb\n<!-- page: 1-->\n<!-- page: 3-->"
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

    def test_empty_uses_default(self):
        assert chunker._backmatter_heading("") == [DEFAULT_HEADING]
        assert chunker._backmatter_heading("   \n  ") == [DEFAULT_HEADING]


class TestSplitTrailingPageMarkers:
    def test_single_trailing_marker(self):
        assert chunker._split_trailing_page_markers("Body text\n\n<!-- page: 5-->") \
            == ("Body text", "<!-- page: 5-->")

    def test_multiple_trailing_markers(self):
        result = chunker._split_trailing_page_markers("X\n\n<!-- page: 5-->\n<!-- page: 6-->")
        assert result == ("X", "<!-- page: 5-->\n<!-- page: 6-->")

    def test_no_trailing_marker(self):
        assert chunker._split_trailing_page_markers("No markers here") is None


class TestMoveTrailingPageMarkers:
    def test_marker_moved_to_next_part(self):
        result = chunker._move_trailing_page_markers(["A\n\n<!-- page: 2-->", "B body"])
        assert result == ["A", "<!-- page: 2-->\n\nB body"]

    def test_single_part_unchanged(self):
        assert chunker._move_trailing_page_markers(["only"]) == ["only"]

    def test_last_part_marker_left_in_place(self):
        # Nowhere to move a marker at the very end.
        parts = ["A", "B\n\n<!-- page: 9-->"]
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
        assert chunks[0]["heading"] == "" and chunks[0]["text"] == "preamble text"
        assert chunks[1]["heading"] == "Section One"
        assert chunks[1]["text"] == "# Section One\nbody one"
        assert chunks[2]["heading"] == "Section Two"

    def test_no_headings_single_chunk(self):
        assert chunker._split_into_top_chunks(["just", "text"], None) == \
            [{"number": 0, "heading": "", "level": 0, "text": "just\ntext"}]

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


class TestSplitOversized:
    def test_no_subheadings_paragraph_split(self):
        chunk_text = "\n\n".join(["p" * 40] * 4)  # four 10-token paragraphs
        parts = chunker._split_oversized(chunk_text, 0, 15)
        assert len(parts) == 4
        assert all(chunker._count_tokens(p) <= 15 for p in parts)

    def test_with_subheadings_packs_then_stays_within_budget(self):
        chunk_text = "# Top Heading\n\n" + "\n\n".join(
            f"## Sub {i} Heading\n\n" + "q" * 40 for i in range(1, 5)
        )
        parts = chunker._split_oversized(chunk_text, 1, 25)
        assert len(parts) >= 2
        assert all(chunker._count_tokens(p) <= 25 for p in parts)
