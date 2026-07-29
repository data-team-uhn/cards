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

"""Tests for chunker: heading recognition and token helpers, plus the end-to-end
chunk_file() routing decision (small documents left whole, large ones split) and its
outline.json / catalog.json outputs."""

import json

import pytest

import chunker
from bookmarks import BOOKMARKS_NAME
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

    def test_min_heading_level(self):
        # Every heading text must clear MIN_HEADING_CHARS (5) to count as a boundary.
        lines = "# Alpha\n## Bravo\n### Gamma".split("\n")
        assert chunker._min_heading_level(lines) == 1
        assert chunker._min_heading_level(lines, deeper_than=1) == 2
        assert chunker._min_heading_level(["no headings here"]) is None


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
        assert is_neutral("<!-- page: 3 -->") is True
        assert is_neutral("Real content") is False

    def test_count_tokens_is_quarter_of_length(self):
        assert chunker.count_tokens("a" * 40) == 10
        assert chunker.count_tokens("") == 0


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


class TestRecordsWrittenIntoChunks:
    """The resolved outline records land in ``Chunks/bookmarks.json``.

    Regression: they were briefly written beside the ``.md`` instead, which is the exact path
    ``_remove_sidecars`` unlinks at the end of the same call — so the file never survived. And
    they were the pre-chunk PDF records rather than the resolved ones, so a document whose
    outline came from a printed TOC (no PDF at all) wrote nothing.
    """

    PARAGRAPH = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. " * 60

    def _records(self, path):
        return json.loads(
            (path.parent / CHUNKS_DIRNAME / BOOKMARKS_NAME).read_text(encoding="utf-8")
        )

    def _protocol(self, path):
        # Four entries, not two: the no-table path needs MIN_DENSITY_MATCHES (3) of the lines
        # after the label to look like entries before it accepts the block as a TOC.
        toc = ["## TABLE OF CONTENTS"] + [
            "1.0 Background\t2", "2.0 Objectives\t3", "3.0 Design\t4", "4.0 Analysis\t5", "",
        ]
        body = [f"# Section {i} Heading\n\n{self.PARAGRAPH}\n" for i in range(1, 51)]
        path.write_text("\n".join(toc + body), encoding="utf-8")

    def test_printed_toc_records_are_written(self, tmp_path):
        # No sibling PDF: the records exist only because derive_outline harvested the printed
        # TOC, so this is the case the first attempt missed entirely.
        path = tmp_path / "proto.md"
        self._protocol(path)
        chunk_file(str(path))
        titles = [record["title"] for record in self._records(path)]
        assert "1.0 Background" in titles
        assert "4.0 Analysis" in titles

    def test_written_inside_chunks_not_beside_the_md(self, tmp_path):
        path = tmp_path / "proto.md"
        self._protocol(path)
        chunk_file(str(path))
        assert (path.parent / CHUNKS_DIRNAME / BOOKMARKS_NAME).is_file()
        assert not (tmp_path / BOOKMARKS_NAME).exists()

    def test_absent_when_no_records_were_resolved(self, tmp_path):
        # Nothing to inspect, and outline.json's outline_source says why.
        path = tmp_path / "plain.md"
        path.write_text("# Title\n\n" + self.PARAGRAPH, encoding="utf-8")
        chunk_file(str(path))
        assert not (path.parent / CHUNKS_DIRNAME / BOOKMARKS_NAME).exists()

    def test_written_on_the_unchunked_path_too(self, tmp_path, monkeypatch):
        # Same reasoning as outline.json: a size-gated document still reports what it found.
        # It has to be the PDF path — the size gate skips printed-TOC harvesting altogether,
        # so a small document only has records when they came from real bookmarks.
        records = [{"title": "Alpha Section", "level": 1, "page": 1}]
        monkeypatch.setattr(chunker, "extract_verified_outline", lambda *a, **k: records)
        path = tmp_path / "small.md"
        path.write_text("# Tiny\n\nshort body\n", encoding="utf-8")
        (tmp_path / "small.pdf").write_bytes(b"%PDF-1.4")
        assert chunk_file(str(path), min_structure_tokens=10 ** 9)["chunks"] == 0
        assert self._records(path) == records


class TestSidecarCleanup:
    """Nothing is left beside the ``.md``: the outline and the records go into ``Chunks/``.

    Regression: a run that harvested a printed TOC used to leave ``bookmarks.json`` beside the
    ``.md``, and the next run read it back as *authoritative PDF bookmarks*, skipped
    printed-TOC detection, and reported the previous document's outline for the current one.
    Neither half is possible now — nothing writes that path and nothing reads it — but the
    cleanup stays, because an older version's leftover is indistinguishable from a fresh file
    to anyone inspecting the folder.
    """

    PARAGRAPH = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. " * 60

    def _write(self, path, toc_titles):
        toc = ["## TABLE OF CONTENTS"] + [f"{t}\t{i + 2}" for i, t in enumerate(toc_titles)] + [""]
        body = [f"# Section {i} Heading\n\n{self.PARAGRAPH}\n" for i in range(1, 51)]
        path.write_text("\n".join(toc + body), encoding="utf-8")

    def _outline(self, path):
        return json.loads(
            (path.parent / CHUNKS_DIRNAME / OUTLINE_NAME).read_text(encoding="utf-8")
        )

    def test_no_sidecars_left_beside_the_md(self, tmp_path):
        path = tmp_path / "proto.md"
        self._write(path, ["1.0 Background", "2.0 Objectives", "3.0 Design", "4.0 Analysis"])
        chunk_file(str(path))
        assert self._outline(path)["outline_source"] == "md-toc"
        assert not (tmp_path / BOOKMARKS_NAME).exists()
        assert not (tmp_path / OUTLINE_NAME).exists()

    def test_rerun_on_new_content_does_not_reuse_the_old_outline(self, tmp_path):
        path = tmp_path / "proto.md"
        self._write(path, ["1.0 Background", "2.0 Objectives", "3.0 Design", "4.0 Analysis"])
        chunk_file(str(path))
        assert self._outline(path)["toc"] == [
            "1.0 Background", "2.0 Objectives", "3.0 Design", "4.0 Analysis",
        ]

        # Same output path, entirely different document.
        self._write(path, ["9.0 Completely New", "8.0 Other Topic", "7.0 Third Thing"])
        chunk_file(str(path))
        outline = self._outline(path)
        assert outline["outline_source"] == "md-toc"
        assert outline["toc"] == ["9.0 Completely New", "8.0 Other Topic", "7.0 Third Thing"]

    def test_unchunked_path_also_clears_sidecars(self, tmp_path):
        path = tmp_path / "small.md"
        path.write_text("# Tiny protocol\n\nShort content.\n", encoding="utf-8")
        (tmp_path / BOOKMARKS_NAME).write_text(
            json.dumps([{"title": "Alpha Section", "level": 1, "page": 1}]), encoding="utf-8"
        )
        chunk_file(str(path), min_structure_tokens=10 ** 9)
        # A leftover sidecar is neither honoured nor left behind: the outline comes from
        # the document alone, and the stale file is removed on the unchunked path too.
        assert self._outline(path)["toc"] == []
        assert self._outline(path)["outline_source"] == "none"
        assert not (tmp_path / BOOKMARKS_NAME).exists()

    def test_clear_prior_outputs_removes_both_sidecars_and_chunks(self, tmp_path):
        path = tmp_path / "doc.md"
        path.write_text("# Doc\n\nbody\n", encoding="utf-8")
        (tmp_path / BOOKMARKS_NAME).write_text("[]", encoding="utf-8")
        (tmp_path / OUTLINE_NAME).write_text("{}", encoding="utf-8")
        (tmp_path / CHUNKS_DIRNAME).mkdir()
        (tmp_path / CHUNKS_DIRNAME / "Chunk-1.md").write_text("stale", encoding="utf-8")
        chunker.clear_prior_outputs(path)
        assert not (tmp_path / BOOKMARKS_NAME).exists()
        assert not (tmp_path / OUTLINE_NAME).exists()
        assert not (tmp_path / CHUNKS_DIRNAME).exists()
