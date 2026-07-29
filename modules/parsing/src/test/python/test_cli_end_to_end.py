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

"""End-to-end tests for the ``chunker.py`` command line, run as a real subprocess.

The CLI is the only way to parse or chunk a document without a daemon, an HTTP client, an auth
token or a JVM — which makes it the tool you reach for when something is wrong, including
``docker exec`` into the parsing container to answer "is Docling working in this image at all".
Nothing in production calls it, so without a test it can break silently and be found broken at
exactly the moment it is needed. That is not hypothetical: the CLI *fallback* that used to exist
rotted this way before being removed.

These run the entry point through ``sys.executable`` rather than importing ``main()``, so
argument parsing, exit codes and stdout are covered too — the parts an in-process call skips.
``docling_parser.py`` is deliberately not exercised here: it imports the heavy ``docling``
package and would convert a real document, which belongs in a slower suite than this one.
"""

import json
import subprocess
import sys
from pathlib import Path

import pytest

from chunker import CATALOG_NAME, CHUNKS_DIRNAME, OUTLINE_NAME

CHUNKER_CLI = Path(__file__).resolve().parents[2] / "main" / "python" / "chunker.py"

# Comfortably over the default structure gate once repeated, so the document actually chunks.
PARAGRAPH = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. " * 60


def run_cli(*args):
    """Run the chunker CLI as a subprocess and return the completed process."""
    return subprocess.run(
        [sys.executable, str(CHUNKER_CLI), *[str(a) for a in args]],
        capture_output=True, text=True, encoding="utf-8", timeout=300,
    )


@pytest.fixture
def large_md(tmp_path):
    """A document big enough to be chunked, with a printed TOC and a References section."""
    path = tmp_path / "proto.md"
    toc = ["## TABLE OF CONTENTS", "1.0 Background\t3", "2.0 Methods\t5", "3.0 Analysis\t7", ""]
    body = [f"# Section {i} Heading\n\n{PARAGRAPH}\n" for i in range(1, 41)]
    path.write_text("\n".join(toc + body), encoding="utf-8")
    return path


class TestChunkerCli:
    def test_chunks_a_large_document(self, large_md):
        result = run_cli(large_md)
        assert result.returncode == 0, result.stderr
        assert "chunk file(s)" in result.stdout

        chunks_dir = large_md.parent / CHUNKS_DIRNAME
        catalog = json.loads((chunks_dir / CATALOG_NAME).read_text(encoding="utf-8"))
        outline = json.loads((chunks_dir / OUTLINE_NAME).read_text(encoding="utf-8"))
        chunk_files = sorted(p.name for p in chunks_dir.glob("Chunk-*.md"))

        assert outline["chunked"] is True
        assert outline["fileId"] == "proto.md"
        assert len(chunk_files) == len(catalog["chunks"])
        # Compared as sets: the catalog is in document order, which is not the lexicographic
        # order of the file names (Chunk-10 sorts before Chunk-2).
        assert {entry["file"] for entry in catalog["chunks"]} == set(chunk_files)

    def test_reported_count_matches_the_catalog(self, large_md):
        result = run_cli(large_md)
        catalog = json.loads(
            (large_md.parent / CHUNKS_DIRNAME / CATALOG_NAME).read_text(encoding="utf-8")
        )
        assert f"Created {len(catalog['chunks'])} chunk file(s)." in result.stdout

    def test_leaves_no_sidecars_beside_the_md(self, large_md):
        run_cli(large_md)
        # A leftover bookmarks.json is read back on the next run as authoritative PDF bookmarks.
        assert [p.name for p in large_md.parent.glob("*.json")] == []

    def test_small_document_is_left_unchunked(self, tmp_path):
        path = tmp_path / "small.md"
        path.write_text("# Tiny\n\nShort body.\n", encoding="utf-8")
        result = run_cli(path)
        assert result.returncode == 0, result.stderr
        assert "Skipped chunking" in result.stdout

        chunks_dir = tmp_path / CHUNKS_DIRNAME
        outline = json.loads((chunks_dir / OUTLINE_NAME).read_text(encoding="utf-8"))
        assert outline["chunked"] is False
        assert outline["unchunkedReason"] == "below_min_structure_tokens"
        assert not (chunks_dir / CATALOG_NAME).exists()

    def test_max_tokens_option_changes_the_split(self, large_md):
        run_cli(large_md, "--max-tokens", 400)
        many = len(list((large_md.parent / CHUNKS_DIRNAME).glob("Chunk-*.md")))
        run_cli(large_md, "--max-tokens", 4000)
        few = len(list((large_md.parent / CHUNKS_DIRNAME).glob("Chunk-*.md")))
        assert many > few, f"a smaller budget should split further ({many} vs {few})"

    def test_min_structure_tokens_option_forces_unchunked(self, large_md):
        result = run_cli(large_md, "--min-structure-tokens", 10_000_000)
        assert result.returncode == 0, result.stderr
        assert "Skipped chunking" in result.stdout
        assert not (large_md.parent / CHUNKS_DIRNAME / CATALOG_NAME).exists()

    def test_missing_file_exits_nonzero(self, tmp_path):
        result = run_cli(tmp_path / "absent.md")
        assert result.returncode == 1
        assert "does not exist" in result.stderr

    def test_rerun_is_stable(self, large_md):
        first = run_cli(large_md)
        second = run_cli(large_md)
        assert first.returncode == 0 and second.returncode == 0
        # Re-chunking must not accumulate state or change the answer.
        assert first.stdout == second.stdout

    def test_help_exits_cleanly(self):
        result = run_cli("--help")
        assert result.returncode == 0
        assert "--max-tokens" in result.stdout
        assert "--min-structure-tokens" in result.stdout
