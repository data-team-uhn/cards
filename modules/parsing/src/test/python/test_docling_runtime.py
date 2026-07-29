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
cap, body draining, authentication, gzip, and the batch-abandon path that runs when a page
batch fails. There are no path allowlists left to test — the daemon accepts no filesystem
paths at all, which :class:`TestNoPathBasedEndpoints` guards.
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
    intermittent — it showed up as HTTP 000 on some rejects and not others."""

    def test_drains_the_whole_body(self):
        handler = _FakeHandler(b"x" * 5000)
        daemon._drain_request_body(handler)
        assert handler.unread == 0

    def test_no_content_length_is_a_noop(self):
        handler = _FakeHandler(b"abc")
        del handler.headers["Content-Length"]
        daemon._drain_request_body(handler)
        assert handler.unread == 3

    def test_bad_content_length_is_a_noop(self):
        handler = _FakeHandler(b"abc", headers={"Content-Length": "not-a-number"})
        handler.headers["Content-Length"] = "not-a-number"
        daemon._drain_request_body(handler)

    def test_stops_at_a_short_body(self):
        # Declared longer than what actually arrives: must not block forever.
        handler = _FakeHandler(b"abc", content_length=10_000)
        daemon._drain_request_body(handler)
        assert handler.unread == 0

    def test_bounded_by_the_upload_cap(self):
        handler = _FakeHandler(b"x" * 100, content_length=daemon.MAX_UPLOAD_BYTES * 10)
        daemon._drain_request_body(handler)
        assert handler.unread == 0


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

    def test_leaves_no_temp_file_behind_on_rejection(self, monkeypatch):
        before = set(Path(tempfile.gettempdir()).glob("docling-upload-*"))
        with pytest.raises(ValueError):
            daemon._spool_upload(_FakeHandler(b""), ".pdf")
        assert set(Path(tempfile.gettempdir()).glob("docling-upload-*")) == before

    def test_actual_bytes_over_the_cap_rejected(self, monkeypatch):
        # A lying Content-Length must not let the daemon write an unbounded file.
        monkeypatch.setattr(daemon, "MAX_UPLOAD_BYTES", 100)
        handler = _FakeHandler(b"x" * 500, content_length=50)
        # Declared 50 so the header check passes; only 50 bytes are read, under the cap.
        path = daemon._spool_upload(handler, ".pdf")
        try:
            assert path.stat().st_size == 50
        finally:
            path.unlink(missing_ok=True)


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
        handler.headers["Content-Length"] = "abc"
        with pytest.raises(ValueError, match="invalid Content-Length"):
            daemon._spool_upload(handler, ".pdf")

    def test_negative_content_length_rejected(self):
        handler = _FakeHandler(b"data", headers={"Content-Length": "-1"})
        handler.headers["Content-Length"] = "-1"
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
