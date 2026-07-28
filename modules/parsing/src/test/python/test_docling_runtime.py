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

"""Tests for the pieces of the daemon and PDF parser that need no model inference.

The ``docling_*`` conversion modules import the heavy ``docling`` package, so the whole file
skips when it is not installed — the rest of the suite still runs anywhere. What is covered
here is the plumbing around Docling rather than Docling itself: the request-path allowlists
that form the daemon's security boundary, the body-size cap, and the batch-abandon path that
runs when a page batch fails.
"""

import json
import tempfile
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from types import SimpleNamespace

import pytest

pytest.importorskip("docling", reason="docling not installed; conversion plumbing skipped")

import docling_daemon as daemon  # noqa: E402 -- must follow the importorskip guard
from docling_pdf_parser import _abandon_batches  # noqa: E402


class TestAbandonBatches:
    """What happens to sibling page batches when one batch fails.

    ``cancel()`` cannot stop a batch that is already running, and in daemon mode the
    executor is shared and outlives the request — so returning without waiting would leave
    those batches consuming workers on behalf of a discarded conversion.
    """

    def test_cancels_batches_that_have_not_started(self):
        with ThreadPoolExecutor(max_workers=1) as pool:
            running = pool.submit(time.sleep, 0.2)
            queued = [pool.submit(time.sleep, 5) for _ in range(3)]
            _abandon_batches([running, *queued], log=lambda _message: None)
            assert all(future.cancelled() for future in queued)

    def test_waits_for_a_batch_already_running(self):
        with ThreadPoolExecutor(max_workers=1) as pool:
            running = pool.submit(time.sleep, 0.3)
            start = time.perf_counter()
            _abandon_batches([running], log=lambda _message: None)
            elapsed = time.perf_counter() - start
        assert running.done()
        assert elapsed >= 0.25, f"returned after {elapsed:.3f}s without waiting"

    def test_swallows_a_failing_batch(self):
        def boom():
            raise RuntimeError("batch blew up")

        with ThreadPoolExecutor(max_workers=1) as pool:
            failing = pool.submit(boom)
            # Must not propagate: the conversion is already failing for another reason.
            _abandon_batches([failing], log=lambda _message: None)

    def test_logs_only_when_something_was_still_running(self):
        messages = []
        with ThreadPoolExecutor(max_workers=1) as pool:
            done = pool.submit(time.sleep, 0)
            done.result()
            _abandon_batches([done], log=messages.append)
        assert messages == []


class TestIsUnderRoot:
    def test_direct_child(self, tmp_path):
        child = tmp_path / "sub" / "file.md"
        child.parent.mkdir()
        child.write_text("x", encoding="utf-8")
        assert daemon._is_under_root(child, tmp_path.resolve()) is True

    def test_sibling_outside_root(self, tmp_path):
        root = tmp_path / "root"
        root.mkdir()
        outside = tmp_path / "outside.md"
        outside.write_text("x", encoding="utf-8")
        assert daemon._is_under_root(outside, root.resolve()) is False

    def test_parent_traversal_rejected(self, tmp_path):
        root = tmp_path / "root"
        root.mkdir()
        assert daemon._is_under_root(root / ".." / "escape.md", root.resolve()) is False


class TestIsAllowedPath:
    def _temp_file(self, suffix):
        handle = tempfile.NamedTemporaryFile(suffix=suffix, delete=False)
        handle.close()
        return Path(handle.name)

    def test_pdf_under_temp_allowed(self):
        path = self._temp_file(".pdf")
        try:
            assert daemon._is_allowed_path(path) is True
        finally:
            path.unlink()

    def test_docx_under_temp_allowed(self):
        path = self._temp_file(".docx")
        try:
            assert daemon._is_allowed_path(path) is True
        finally:
            path.unlink()

    def test_unsupported_suffix_rejected(self):
        path = self._temp_file(".txt")
        try:
            assert daemon._is_allowed_path(path) is False
        finally:
            path.unlink()

    def test_missing_file_rejected(self):
        assert daemon._is_allowed_path(Path(tempfile.gettempdir()) / "nope-missing.pdf") is False

    def test_outside_temp_rejected(self, tmp_path):
        # tmp_path is pytest's own directory, not the system temp root.
        path = tmp_path / "elsewhere.pdf"
        path.write_text("x", encoding="utf-8")
        if daemon._is_under_root(path, Path(tempfile.gettempdir()).resolve()):
            pytest.skip("pytest tmp_path lives under the system temp root on this platform")
        assert daemon._is_allowed_path(path) is False


class TestIsAllowedChunkFile:
    def test_md_under_the_parse_root_allowed(self, tmp_path, monkeypatch):
        monkeypatch.setattr(
            daemon, "_STATE", SimpleNamespace(parse_output_root=tmp_path.resolve())
        )
        path = tmp_path / "proto.md"
        path.write_text("x", encoding="utf-8")
        assert daemon._is_allowed_chunk_file(path) is True

    def test_non_md_rejected(self, tmp_path, monkeypatch):
        monkeypatch.setattr(
            daemon, "_STATE", SimpleNamespace(parse_output_root=tmp_path.resolve())
        )
        path = tmp_path / "proto.pdf"
        path.write_text("x", encoding="utf-8")
        assert daemon._is_allowed_chunk_file(path) is False

    def test_outside_the_parse_root_rejected(self, tmp_path, monkeypatch):
        root = tmp_path / "root"
        root.mkdir()
        monkeypatch.setattr(daemon, "_STATE", SimpleNamespace(parse_output_root=root.resolve()))
        path = tmp_path / "outside.md"
        path.write_text("x", encoding="utf-8")
        assert daemon._is_allowed_chunk_file(path) is False

    def test_no_state_rejects_everything(self, tmp_path, monkeypatch):
        monkeypatch.setattr(daemon, "_STATE", None)
        path = tmp_path / "proto.md"
        path.write_text("x", encoding="utf-8")
        assert daemon._is_allowed_chunk_file(path) is False


class _FakeHandler:
    """Minimal stand-in for BaseHTTPRequestHandler's body-reading surface."""

    def __init__(self, body: bytes, content_length=None):
        declared = len(body) if content_length is None else content_length
        self.headers = {"Content-Length": str(declared)}
        self.rfile = SimpleNamespace(read=lambda n: body[:n])


class TestReadJsonBody:
    def test_parses_an_object(self):
        handler = _FakeHandler(json.dumps({"file_path": "/tmp/x.md"}).encode("utf-8"))
        assert daemon._read_json_body(handler) == {"file_path": "/tmp/x.md"}

    def test_empty_body_is_an_empty_dict(self):
        assert daemon._read_json_body(_FakeHandler(b"")) == {}

    def test_non_object_rejected(self):
        with pytest.raises(ValueError, match="must be an object"):
            daemon._read_json_body(_FakeHandler(b"[1, 2, 3]"))

    def test_oversized_body_rejected_without_reading_it(self):
        # Guards on the declared length, so an abusive Content-Length cannot dictate how
        # much the daemon pulls into memory.
        handler = _FakeHandler(b"{}", content_length=daemon.MAX_REQUEST_BYTES + 1)
        with pytest.raises(ValueError, match="too large"):
            daemon._read_json_body(handler)

    def test_body_at_the_cap_is_accepted(self):
        payload = json.dumps({"pad": "x" * 100}).encode("utf-8")
        assert daemon._read_json_body(_FakeHandler(payload)) == json.loads(payload)
