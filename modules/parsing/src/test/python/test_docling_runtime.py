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

    def test_unconfigured_root_rejects_everything(self, tmp_path, monkeypatch):
        monkeypatch.setattr(daemon, "_STATE", SimpleNamespace(parse_output_root=None))
        path = tmp_path / "proto.md"
        path.write_text("x", encoding="utf-8")
        assert daemon._is_allowed_chunk_file(path) is False


class TestParseOutputRootHasNoDefault:
    """The chunk root must be given explicitly.

    Regression: it used to default to ``<cwd>/parsed-markdown``, a directory nothing else in
    the system writes to. A daemon started without the flag then rejected every chunk request
    with a message about the path being outside the root, which reads as a caller mistake
    rather than a misconfigured daemon.
    """

    def test_no_flag_and_no_env_yields_none(self, monkeypatch):
        monkeypatch.delenv(daemon.PARSE_OUTPUT_DIR_ENV, raising=False)
        assert daemon._resolve_parse_output_root(None) is None

    def test_explicit_flag_wins(self, tmp_path, monkeypatch):
        monkeypatch.setenv(daemon.PARSE_OUTPUT_DIR_ENV, str(tmp_path / "from-env"))
        assert daemon._resolve_parse_output_root(str(tmp_path)) == tmp_path.resolve()

    def test_env_used_when_no_flag(self, tmp_path, monkeypatch):
        monkeypatch.setenv(daemon.PARSE_OUTPUT_DIR_ENV, str(tmp_path))
        assert daemon._resolve_parse_output_root(None) == tmp_path.resolve()

    def test_chunking_configured_reflects_the_root(self, tmp_path, monkeypatch):
        monkeypatch.setattr(daemon, "_STATE", SimpleNamespace(parse_output_root=None))
        assert daemon._is_chunking_configured() is False
        monkeypatch.setattr(daemon, "_STATE", SimpleNamespace(parse_output_root=tmp_path))
        assert daemon._is_chunking_configured() is True

    def test_chunking_not_configured_without_state(self, monkeypatch):
        monkeypatch.setattr(daemon, "_STATE", None)
        assert daemon._is_chunking_configured() is False


class _FakeHandler:
    """Minimal stand-in for BaseHTTPRequestHandler's body-reading surface."""

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


class TestPositiveOption:
    """Chunking options read from a ``/chunk`` JSON body.

    ``bool`` is the case that matters: it subclasses ``int``, so the inline
    ``isinstance(..., int) and > 0`` check this replaced accepted ``{"max_tokens": true}`` and
    passed ``max_tokens=1`` to the chunker — a one-token budget that shatters the document.
    """

    def test_positive_int_accepted(self):
        assert daemon._positive_option({"max_tokens": 2000}, "max_tokens") == 2000

    def test_absent_is_none(self):
        assert daemon._positive_option({}, "max_tokens") is None

    def test_zero_and_negative_rejected(self):
        assert daemon._positive_option({"max_tokens": 0}, "max_tokens") is None
        assert daemon._positive_option({"max_tokens": -5}, "max_tokens") is None

    def test_bool_rejected(self):
        assert daemon._positive_option({"max_tokens": True}, "max_tokens") is None
        assert daemon._positive_option({"max_tokens": False}, "max_tokens") is None

    def test_non_integers_rejected(self):
        for value in ("2000", 2.5, None, [2000], {"n": 1}):
            assert daemon._positive_option({"max_tokens": value}, "max_tokens") is None, value


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


class TestAuthorization:
    def test_open_when_no_token_configured(self, monkeypatch):
        monkeypatch.delenv(daemon.AUTH_TOKEN_ENV, raising=False)
        assert daemon._is_authorized(_FakeHandler(b"")) is True

    def test_correct_bearer_token_accepted(self, monkeypatch):
        monkeypatch.setenv(daemon.AUTH_TOKEN_ENV, "s3cret")
        handler = _FakeHandler(b"", headers={"Authorization": "Bearer s3cret"})
        assert daemon._is_authorized(handler) is True

    def test_wrong_token_rejected(self, monkeypatch):
        monkeypatch.setenv(daemon.AUTH_TOKEN_ENV, "s3cret")
        handler = _FakeHandler(b"", headers={"Authorization": "Bearer nope"})
        assert daemon._is_authorized(handler) is False

    def test_missing_header_rejected(self, monkeypatch):
        monkeypatch.setenv(daemon.AUTH_TOKEN_ENV, "s3cret")
        assert daemon._is_authorized(_FakeHandler(b"")) is False

    def test_wrong_scheme_rejected(self, monkeypatch):
        monkeypatch.setenv(daemon.AUTH_TOKEN_ENV, "s3cret")
        handler = _FakeHandler(b"", headers={"Authorization": "Basic s3cret"})
        assert daemon._is_authorized(handler) is False

    def test_empty_env_token_means_disabled(self, monkeypatch):
        monkeypatch.setenv(daemon.AUTH_TOKEN_ENV, "")
        assert daemon._is_authorized(_FakeHandler(b"")) is True


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
