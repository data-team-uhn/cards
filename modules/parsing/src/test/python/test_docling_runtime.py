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
here is the plumbing around Docling rather than Docling itself: upload spooling and its size
cap, truncation and body draining, health reporting, gzip negotiation, and the batch-abandon
path that runs when a page batch fails. Nothing authenticates any more — the bind address is
the whole access control — and there are no path allowlists left to test, since the daemon
accepts no filesystem paths at all, which :class:`TestNoPathBasedEndpoints` guards.
"""

import gzip
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


class TestDrainRequestBody:
    """An error response written while the client is still uploading resets the connection
    before the client can read it, so the caller sees a transport failure rather than the 400
    explaining what was wrong. Whether it happens depends on socket buffering, which makes it
    intermittent — it showed up as HTTP 000 on some rejects and not others.

    Under HTTP/1.1 keep-alive the drain also owns the framing decision: a body it fully
    consumed leaves the connection reusable, and one it could not must close the connection,
    or the leftover bytes become the start of the next request."""

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
        # Where the body ends is unknowable, so the connection must not be reused.
        handler = _FakeHandler(b"abc", headers={"Content-Length": "not-a-number"})
        daemon._drain_request_body(handler)
        assert handler.close_connection is True

    def test_negative_content_length_closes_the_connection(self):
        handler = _FakeHandler(b"abc", headers={"Content-Length": "-5"})
        daemon._drain_request_body(handler)
        assert handler.unread == 3
        assert handler.close_connection is True

    def test_chunked_body_closes_the_connection(self):
        # BaseHTTPRequestHandler does not decode chunked bodies, so it cannot be drained.
        handler = _FakeHandler(b"3\r\nabc\r\n0\r\n\r\n")
        handler.headers["Transfer-Encoding"] = "chunked"
        daemon._drain_request_body(handler)
        assert handler.close_connection is True

    def test_stops_at_a_short_body(self):
        # Declared longer than what actually arrives: must not block forever. The client hung
        # up mid-body, so there is no connection worth keeping either.
        handler = _FakeHandler(b"abc", content_length=10_000)
        daemon._drain_request_body(handler)
        assert handler.unread == 0
        assert handler.close_connection is True

    def test_bounded_by_the_upload_cap(self):
        # An over-cap body is not drained at all — the connection is closed instead, so a lying
        # header cannot make the daemon read without bound just to save the connection.
        handler = _FakeHandler(b"x" * 100, content_length=daemon.MAX_UPLOAD_BYTES * 10)
        daemon._drain_request_body(handler)
        assert handler.unread == 100
        assert handler.close_connection is True

    def test_a_body_drained_exactly_keeps_the_connection(self):
        handler = _FakeHandler(b"x" * 100, content_length=100)
        daemon._drain_request_body(handler)
        assert handler.unread == 0
        assert handler.close_connection is False


class TestSafeSuffix:
    def test_accepts_supported_types(self):
        assert daemon._safe_suffix("proto.pdf") == ".pdf"
        assert daemon._safe_suffix("proto.DOCX") == ".docx"

    def test_rejects_anything_else(self):
        for name in ("notes.txt", "archive.zip", "noextension", ""):
            with pytest.raises(ValueError, match="filename must end in"):
                daemon._safe_suffix(name)


class TestSpoolUpload:
    def test_writes_the_body_to_a_temp_file(self):
        handler = _FakeHandler(b"PK\x03\x04payload")
        path = daemon._spool_upload(handler, ".docx")
        try:
            assert path.read_bytes() == b"PK\x03\x04payload"
            assert path.suffix == ".docx"
        finally:
            path.unlink(missing_ok=True)

    def test_empty_body_rejected(self):
        with pytest.raises(ValueError, match="empty"):
            daemon._spool_upload(_FakeHandler(b""), ".pdf")

    def test_declared_length_over_the_cap_rejected(self):
        handler = _FakeHandler(b"x" * 10, content_length=daemon.MAX_UPLOAD_BYTES + 1)
        with pytest.raises(ValueError, match="too large"):
            daemon._spool_upload(handler, ".pdf")

    def test_leaves_no_temp_file_behind_on_rejection(self):
        before = set(Path(tempfile.gettempdir()).glob("docling-upload-*"))
        with pytest.raises(ValueError):
            daemon._spool_upload(_FakeHandler(b""), ".pdf")
        assert set(Path(tempfile.gettempdir()).glob("docling-upload-*")) == before

    def test_reads_only_the_declared_length(self, monkeypatch):
        # Renamed from test_actual_bytes_over_the_cap_rejected, which asserted the opposite of
        # its name: it declared 50 under a patched cap of 100 and checked the file *was* written.
        # Nothing is rejected here, and nothing can be — the cap is checked against
        # Content-Length before a byte is read, and only that many bytes are ever read, so a
        # header claiming less than the client sends just leaves the surplus in the socket.
        monkeypatch.setattr(daemon, "MAX_UPLOAD_BYTES", 100)
        handler = _FakeHandler(b"x" * 500, content_length=50)
        path = daemon._spool_upload(handler, ".pdf")
        try:
            assert path.stat().st_size == 50
            assert handler.unread == 450
        finally:
            path.unlink(missing_ok=True)


class TestTruncatedUpload:
    """A body that stops short of ``Content-Length`` must be rejected, not parsed.

    Regression: the read loop broke on the first empty read and returned the partial file, so a
    client disconnecting mid-upload had its half-document handed to Docling, which parsed
    whatever it could and the daemon answered 200 with the result.
    """

    def test_short_body_rejected(self):
        handler = _FakeHandler(b"x" * 40, content_length=200)
        with pytest.raises(ValueError, match="truncated upload: got 40 of 200"):
            daemon._spool_upload(handler, ".pdf")

    def test_no_temp_file_left_behind(self):
        before = set(Path(tempfile.gettempdir()).glob("docling-upload-*"))
        with pytest.raises(ValueError, match="truncated"):
            daemon._spool_upload(_FakeHandler(b"x" * 40, content_length=200), ".pdf")
        assert set(Path(tempfile.gettempdir()).glob("docling-upload-*")) == before

    def test_empty_body_still_reports_empty(self):
        # The more specific message wins when nothing at all arrived.
        with pytest.raises(ValueError, match="request body is empty"):
            daemon._spool_upload(_FakeHandler(b"", content_length=200), ".pdf")

    def test_exact_length_accepted(self):
        handler = _FakeHandler(b"x" * 200, content_length=200)
        path = daemon._spool_upload(handler, ".pdf")
        try:
            assert path.stat().st_size == 200
        finally:
            path.unlink(missing_ok=True)


class TestHealthReporting:
    """``/health`` must fail its status line, not just its body, when the daemon is unusable.

    Regression: it answered 200 with ``ready: false``. Both container probes are
    ``curl -fsS .../health``, which reads the status line and never the body, so a daemon that
    returned 503 from every /parse was reported healthy forever and nothing restarted it.
    """

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
        # It used to report "shutting_down" for a broken pool, sending whoever was debugging it
        # looking for a shutdown that never happened.
        monkeypatch.setattr(daemon, "_STATE", self._state(pdf_executor_broken=True))
        assert daemon._health_status() == "pdf_pool_broken"

    def test_shutdown_still_says_shutting_down(self, monkeypatch):
        monkeypatch.setattr(daemon, "_STATE", self._state(shutdown_requested=True))
        assert daemon._health_status() == "shutting_down"

    def test_broken_pool_wins_over_shutdown(self, monkeypatch):
        # _request_shutdown sets shutdown_requested, so both flags are on once the pool breaks;
        # the pool is the cause and the more useful thing to report.
        monkeypatch.setattr(
            daemon, "_STATE", self._state(pdf_executor_broken=True, shutdown_requested=True)
        )
        assert daemon._health_status() == "pdf_pool_broken"

    def test_no_state_yet_says_starting(self, monkeypatch):
        monkeypatch.setattr(daemon, "_STATE", None)
        assert daemon._health_status() == "starting"


class TestBrokenPoolShutsDown:
    """A broken PDF pool has to end the process, not just flip a flag.

    The pool cannot be rebuilt in-process, so a fresh process is the only recovery — and only an
    exit triggers one, because a container restart policy reacts to the process ending and never
    to health status. Staying up answering 503 forever was the failure mode.
    """

    def test_broken_pool_requests_shutdown(self, monkeypatch, tmp_path):
        from concurrent.futures.process import BrokenProcessPool

        state = SimpleNamespace(
            shutdown_requested=False, pdf_executor_broken=False,
            pdf_executor=None, worker_count=1,
        )
        monkeypatch.setattr(daemon, "_STATE", state)
        monkeypatch.setattr(daemon, "_SERVER", None)

        def explode(*args, **kwargs):
            raise BrokenProcessPool("forced")

        monkeypatch.setattr(daemon, "convert_pdf_to_markdown", explode)
        pdf = tmp_path / "doc.pdf"
        pdf.write_bytes(b"%PDF-1.4")

        with pytest.raises(RuntimeError, match="PDF worker pool is broken"):
            daemon._convert_file(pdf, source_file="doc.pdf")

        assert state.pdf_executor_broken is True
        # _request_shutdown was reached, so serve_forever stops and main() exits non-zero.
        assert state.shutdown_requested is True


class TestSpoolUploadRejections:
    """The Content-Length checks, which are also the drain-sensitive paths.

    _spool_upload raises before or partway through reading the body, so its caller must drain the
    remainder before answering — otherwise the 400 goes into a connection the client is still
    writing to and the client sees a transport failure instead. That is why body_consumed is only
    set after this function returns.
    """

    def test_missing_content_length_rejected(self):
        handler = _FakeHandler(b"data")
        del handler.headers["Content-Length"]
        with pytest.raises(ValueError, match="Content-Length is required"):
            daemon._spool_upload(handler, ".pdf")

    def test_unparseable_content_length_rejected(self):
        handler = _FakeHandler(b"data", headers={"Content-Length": "abc"})
        with pytest.raises(ValueError, match="invalid Content-Length"):
            daemon._spool_upload(handler, ".pdf")

    def test_negative_content_length_rejected(self):
        handler = _FakeHandler(b"data", headers={"Content-Length": "-1"})
        with pytest.raises(ValueError, match="invalid Content-Length"):
            daemon._spool_upload(handler, ".pdf")

    def test_oversized_declared_length_reports_the_number_not_the_string(self):
        # The message used to be matched by substring to tell these errors apart; it now carries
        # the parsed int, and nothing depends on the wording.
        handler = _FakeHandler(b"x", content_length=daemon.MAX_UPLOAD_BYTES + 5)
        with pytest.raises(ValueError, match=str(daemon.MAX_UPLOAD_BYTES + 5)):
            daemon._spool_upload(handler, ".pdf")

    def test_body_is_left_unread_when_the_declared_length_is_rejected(self):
        # Precisely why the caller must drain: nothing has been consumed at this point.
        handler = _FakeHandler(b"y" * 4096, content_length=daemon.MAX_UPLOAD_BYTES + 1)
        with pytest.raises(ValueError):
            daemon._spool_upload(handler, ".pdf")
        assert handler.unread == 4096


class TestJsonResponseGzip:
    def _payload(self, size):
        return {"markdown": "a" * size}

    def test_large_payload_gzipped_when_accepted(self):
        handler = _FakeHandler(b"", headers={"Accept-Encoding": "gzip, deflate"})
        daemon._json_response(handler, 200, self._payload(daemon.MIN_GZIP_BYTES * 4))
        assert handler.header_value("Content-Encoding") == "gzip"
        assert json.loads(gzip.decompress(handler.written))["markdown"].startswith("aaa")

    def test_not_gzipped_when_client_does_not_accept(self):
        handler = _FakeHandler(b"")
        daemon._json_response(handler, 200, self._payload(daemon.MIN_GZIP_BYTES * 4))
        assert handler.header_value("Content-Encoding") is None
        assert json.loads(handler.written)["markdown"].startswith("aaa")

    def test_small_payload_not_gzipped(self):
        handler = _FakeHandler(b"", headers={"Accept-Encoding": "gzip"})
        daemon._json_response(handler, 200, {"ok": True})
        assert handler.header_value("Content-Encoding") is None

    def test_content_length_matches_the_bytes_sent(self):
        handler = _FakeHandler(b"", headers={"Accept-Encoding": "gzip"})
        daemon._json_response(handler, 200, self._payload(daemon.MIN_GZIP_BYTES * 4))
        assert int(handler.header_value("Content-Length")) == len(handler.written)



class TestNoPathBasedEndpoints:
    """The daemon must not accept filesystem paths again.

    ``POST /convert`` (an ``input_path``) and ``POST /chunk`` (a ``file_path``) were removed: both
    required the daemon to see the caller's filesystem, which cannot work once it runs in its own
    container. With no path ever accepted there is nothing to allowlist, which is why the helpers
    below are gone too. This test exists so re-adding one is a deliberate act rather than an
    accident — if it fails, the filesystem-free property has been given up.
    """

    def test_path_accepting_helpers_are_gone(self):
        for name in ("_is_allowed_path", "_is_allowed_chunk_file", "_is_under_root",
                     "_is_chunking_configured", "_resolve_parse_output_root",
                     "_read_json_body", "_positive_option"):
            assert not hasattr(daemon, name), name

    def test_chunk_handler_is_gone(self):
        assert not hasattr(daemon.DoclingDaemonHandler, "_handle_chunk")

    def test_no_chunk_root_on_the_daemon_state(self):
        assert "parse_output_root" not in daemon.DaemonState.__init__.__code__.co_varnames

    def test_no_parse_output_dir_argument(self):
        parser_source = daemon.parse_args.__code__.co_consts
        assert not any(isinstance(c, str) and "parse-output-dir" in c for c in parser_source)
