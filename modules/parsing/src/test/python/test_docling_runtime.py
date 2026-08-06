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
here is the plumbing around Docling rather than Docling itself: shared-docs path allowlisting,
body draining, health reporting, and the batch-abandon path that runs when a page batch fails.
"""

import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from types import SimpleNamespace

import pytest

pytest.importorskip("docling", reason="docling not installed; conversion plumbing skipped")

import docling_daemon as daemon  # noqa: E402 -- must follow the importorskip guard
from docling_pdf_parser import _abandon_batches  # noqa: E402


class _FakeHandler:
    """Minimal stand-in for BaseHTTPRequestHandler's body-reading and response surface."""

    def __init__(self, body: bytes, content_length=None, headers=None):
        declared = len(body) if content_length is None else content_length
        self.headers = {"Content-Length": str(declared)}
        if headers:
            self.headers.update(headers)
        self._body = body
        self._offset = 0
        self.sent = []
        self.written = b""
        self.close_connection = False
        self.rfile = SimpleNamespace(read=self._read)
        self.wfile = SimpleNamespace(write=self._write)

    def _read(self, size):
        block = self._body[self._offset:self._offset + size]
        self._offset += len(block)
        return block

    def _write(self, data):
        self.written += data

    @property
    def unread(self):
        return len(self._body) - self._offset

    def send_response(self, status):
        self.sent.append(("status", status))

    def send_header(self, name, value):
        self.sent.append((name.lower(), value))

    def end_headers(self):
        self.sent.append(("end", None))

    def header_value(self, name):
        return next((v for k, v in self.sent if k == name.lower()), None)


class TestAbandonBatches:
    """What happens to sibling page batches when one batch fails."""

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
            _abandon_batches([failing], log=lambda _message: None)

    def test_logs_only_when_something_was_still_running(self):
        messages = []
        with ThreadPoolExecutor(max_workers=1) as pool:
            done = pool.submit(time.sleep, 0)
            done.result()
            _abandon_batches([done], log=messages.append)
        assert messages == []


class TestDrainRequestBody:
    """Leftover request bodies must be drained or the connection closed."""

    def test_drains_the_whole_body(self):
        handler = _FakeHandler(b"x" * 5000)
        daemon._drain_request_body(handler)
        assert handler.unread == 0
        assert handler.close_connection is False

    def test_no_content_length_is_a_noop(self):
        handler = _FakeHandler(b"abc")
        del handler.headers["Content-Length"]
        daemon._drain_request_body(handler)
        assert handler.unread == 3
        assert handler.close_connection is False

    def test_bad_content_length_closes_the_connection(self):
        handler = _FakeHandler(b"abc", headers={"Content-Length": "not-a-number"})
        daemon._drain_request_body(handler)
        assert handler.close_connection is True

    def test_negative_content_length_closes_the_connection(self):
        handler = _FakeHandler(b"abc", headers={"Content-Length": "-5"})
        daemon._drain_request_body(handler)
        assert handler.unread == 3
        assert handler.close_connection is True

    def test_chunked_body_closes_the_connection(self):
        handler = _FakeHandler(b"3\r\nabc\r\n0\r\n\r\n")
        handler.headers["Transfer-Encoding"] = "chunked"
        daemon._drain_request_body(handler)
        assert handler.close_connection is True

    def test_stops_at_a_short_body(self):
        handler = _FakeHandler(b"abc", content_length=10_000)
        daemon._drain_request_body(handler)
        assert handler.unread == 0
        assert handler.close_connection is True

    def test_oversized_declared_length_closes_the_connection(self):
        handler = _FakeHandler(b"x" * 100, content_length=2 * 1024 * 1024)
        daemon._drain_request_body(handler)
        assert handler.unread == 100
        assert handler.close_connection is True


class TestResolveParsePath:
    """``POST /parse`` only accepts absolute paths under the shared docs root."""

    def test_accepts_a_file_under_the_shared_root(self, monkeypatch, tmp_path):
        monkeypatch.setenv("IAP_SHARED_DOCS", str(tmp_path))
        pdf = tmp_path / "proto.pdf"
        pdf.write_bytes(b"%PDF")
        resolved = daemon.resolve_parse_path(str(pdf))
        assert resolved == pdf.resolve()

    def test_accepts_doc_and_docx(self, monkeypatch, tmp_path):
        monkeypatch.setenv("IAP_SHARED_DOCS", str(tmp_path))
        for name in ("a.docx", "b.doc"):
            path = tmp_path / name
            path.write_bytes(b"x")
            assert daemon.resolve_parse_path(str(path)).name == name

    def test_rejects_paths_outside_the_root(self, monkeypatch, tmp_path):
        root = tmp_path / "shared"
        root.mkdir()
        outside = tmp_path / "other" / "proto.pdf"
        outside.parent.mkdir()
        outside.write_bytes(b"%PDF")
        monkeypatch.setenv("IAP_SHARED_DOCS", str(root))
        with pytest.raises(ValueError, match="must be under"):
            daemon.resolve_parse_path(str(outside))

    def test_rejects_missing_files(self, monkeypatch, tmp_path):
        monkeypatch.setenv("IAP_SHARED_DOCS", str(tmp_path))
        with pytest.raises(ValueError, match="does not exist"):
            daemon.resolve_parse_path(str(tmp_path / "missing.pdf"))

    def test_rejects_unsupported_suffixes(self, monkeypatch, tmp_path):
        monkeypatch.setenv("IAP_SHARED_DOCS", str(tmp_path))
        bad = tmp_path / "notes.txt"
        bad.write_text("hi", encoding="utf-8")
        with pytest.raises(ValueError, match="must end in"):
            daemon.resolve_parse_path(str(bad))

    def test_rejects_empty_path(self, monkeypatch, tmp_path):
        monkeypatch.setenv("IAP_SHARED_DOCS", str(tmp_path))
        with pytest.raises(ValueError, match="required"):
            daemon.resolve_parse_path("   ")


class TestHealthReporting:
    """``/health`` must fail its status line when the daemon is unusable."""

    def _state(self, **flags):
        state = SimpleNamespace(
            shutdown_requested=False, pdf_executor_broken=False, worker_count=2,
        )
        for name, value in flags.items():
            setattr(state, name, value)
        state.is_ready = lambda: not state.shutdown_requested and not state.pdf_executor_broken
        return state

    def test_status_is_ok_when_ready(self, monkeypatch):
        monkeypatch.setattr(daemon, "_STATE", self._state())
        assert daemon._health_status() == "ok"

    def test_broken_pool_says_so_rather_than_shutting_down(self, monkeypatch):
        monkeypatch.setattr(daemon, "_STATE", self._state(pdf_executor_broken=True))
        assert daemon._health_status() == "pdf_pool_broken"

    def test_shutdown_still_says_shutting_down(self, monkeypatch):
        monkeypatch.setattr(daemon, "_STATE", self._state(shutdown_requested=True))
        assert daemon._health_status() == "shutting_down"

    def test_broken_pool_wins_over_shutdown(self, monkeypatch):
        monkeypatch.setattr(
            daemon, "_STATE", self._state(pdf_executor_broken=True, shutdown_requested=True)
        )
        assert daemon._health_status() == "pdf_pool_broken"

    def test_no_state_yet_says_starting(self, monkeypatch):
        monkeypatch.setattr(daemon, "_STATE", None)
        assert daemon._health_status() == "starting"


class TestBrokenPoolShutsDown:
    """A broken PDF pool has to end the process, not just flip a flag."""

    def test_broken_pool_requests_shutdown(self, monkeypatch, tmp_path):
        from concurrent.futures.process import BrokenProcessPool

        state = SimpleNamespace(
            shutdown_requested=False, pdf_executor_broken=False,
            pdf_executor=None, worker_count=1, docx_lock=None, docx_converter=None,
        )
        monkeypatch.setattr(daemon, "_STATE", state)
        monkeypatch.setattr(daemon, "_SERVER", None)

        def explode(*args, **kwargs):
            raise BrokenProcessPool("forced")

        monkeypatch.setattr(daemon, "parse_document", explode)
        pdf = tmp_path / "doc.pdf"
        pdf.write_bytes(b"%PDF-1.4")

        with pytest.raises(RuntimeError, match="PDF worker pool is broken"):
            daemon._run_parse(pdf, chunk=True, max_tokens=2000, min_structure_tokens=20000)

        assert state.pdf_executor_broken is True
        assert state.shutdown_requested is True


class TestPathBasedParseContract:
    """The daemon accepts shared-docs paths; legacy byte-upload helpers stay gone."""

    def test_resolve_parse_path_exists(self):
        assert hasattr(daemon, "resolve_parse_path")
        assert hasattr(daemon, "shared_docs_root")

    def test_legacy_byte_upload_helpers_are_gone(self):
        for name in ("_spool_upload", "_safe_suffix", "MAX_UPLOAD_BYTES", "MIN_GZIP_BYTES"):
            assert not hasattr(daemon, name), name

    def test_chunk_handler_is_gone(self):
        assert not hasattr(daemon.DoclingDaemonHandler, "_handle_chunk")

    def test_no_parse_output_dir_argument(self):
        parser_source = daemon.parse_args.__code__.co_consts
        assert not any(isinstance(c, str) and "parse-output-dir" in c for c in parser_source)
