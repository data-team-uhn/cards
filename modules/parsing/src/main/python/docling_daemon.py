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

"""
Long-running Docling worker daemon.

Keeps a warm ProcessPoolExecutor (with loaded PDF models) alive across requests.
Java calls this over HTTP instead of spawning docling_parser.py per file.

Endpoints:
    GET  /health   -> {"status": "ok", "workers": N, "ready": true}
    POST /parse    -> raw document bytes in the body,
                      ?filename=proto.pdf&chunk=true[&max_tokens=&min_structure_tokens=]
                     -> {"markdown", "chunked", "outline", "catalog", "chunks":[{"file","text"}], "logs"}
    POST /shutdown -> graceful stop (used when the caller owns the daemon process)

The daemon exchanges **no filesystem paths at all**. A document arrives as bytes on ``/parse`` and
the entire result — Markdown plus the chunk tree — is returned in the reply, so nothing needs to be
shared with the caller and the daemon runs in its own container. The only file it ever writes is the
temp spool for the upload it is currently handling.

An earlier path-based pair (``POST /convert`` with an ``input_path``, ``POST /chunk`` with a
``file_path``) has been removed. Both required the daemon to see the caller's filesystem, which
cannot work across a container boundary, and both are superseded by ``/parse``. Their removal is
what makes "touches no filesystem" an enforced property rather than a convention: with no path ever
accepted, there is nothing to allowlist. The CLI (``docling_parser.py``, ``chunker.py``) calls the
same chunker functions in-process and never needed the endpoints.

Every endpoint except ``/health`` requires ``Authorization: Bearer <token>`` when
``$DOCLING_AUTH_TOKEN`` is set; unset means no authentication, which is only safe on loopback.
"""

from __future__ import annotations

import argparse
import gzip
import hmac
import json
import os
import signal
import sys
import tempfile
import threading
from concurrent.futures import ProcessPoolExecutor
from concurrent.futures.process import BrokenProcessPool
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any
from urllib.parse import parse_qs, urlsplit

import docling_config  # noqa: F401 — apply shared Docling settings on import

from chunker import DEFAULT_MAX_TOKENS, build_chunk_tree
from docling_batch_sizing import GB_PER_WORKER, calc_workers, positive_int
from docling_docx_parser import convert_docx_to_markdown, get_docx_converter
from docling_pdf_parser import convert_pdf_to_markdown, warm_pdf_workers, _init_worker
from markdown_markers import SUPPORTED_SUFFIXES
from pdf_bookmarks import extract_verified_outline
from toc_and_appendix_detection import DEFAULT_MIN_STRUCTURE_TOKENS

DEFAULT_HOST = "127.0.0.1"
DEFAULT_PORT = 18765

# Shared secret required on every endpoint except /health. Unset means no authentication,
# which is only defensible on loopback — see the --host help text.
AUTH_TOKEN_ENV = "DOCLING_AUTH_TOKEN"

# Cap on an uploaded document on /parse. The body is streamed to a temp file rather than held in
# memory, and the limit is enforced as bytes arrive rather than trusted from Content-Length.
MAX_UPLOAD_BYTES = 256 * 1024 * 1024

# Chunks of the upload stream read at a time.
_UPLOAD_CHUNK_BYTES = 1024 * 1024

# Responses at or above this size are gzipped when the client advertises support. The payload
# is Markdown and JSON, so it compresses several-fold for the cost of a little CPU.
MIN_GZIP_BYTES = 8 * 1024


class DaemonState:
    """Shared daemon resources."""

    def __init__(self, workers: int | None) -> None:
        self.worker_count = calc_workers(workers)
        self.pdf_executor = ProcessPoolExecutor(
            max_workers=self.worker_count,
            initializer=_init_worker,
        )
        self.docx_lock = threading.Lock()
        self.shutdown_requested = False
        self.pdf_executor_broken = False
        try:
            warm_pdf_workers(self.pdf_executor, self.worker_count)
            get_docx_converter()
        except Exception:
            self.pdf_executor.shutdown(wait=False, cancel_futures=True)
            raise

    def is_ready(self) -> bool:
        """Return True if the daemon can accept conversion requests.

        A broken PDF pool fails the whole daemon (not just PDF requests) on purpose:
        it signals callers to fall back and operators to restart, since the pool
        cannot recover in-process. DOCX conversion would still work, but reporting
        unready keeps behaviour predictable.
        """
        return not self.shutdown_requested and not self.pdf_executor_broken

    def close(self) -> None:
        self.pdf_executor.shutdown(wait=True, cancel_futures=True)


_STATE: DaemonState | None = None
_SERVER: ThreadingHTTPServer | None = None


def _accepts_gzip(handler: BaseHTTPRequestHandler) -> bool:
    """Whether the client advertised gzip in ``Accept-Encoding``."""
    header = handler.headers.get("Accept-Encoding", "") or ""
    return "gzip" in header.lower()


def _json_response(handler: BaseHTTPRequestHandler, status: int, payload: dict[str, Any]) -> None:
    """Send ``payload`` as JSON, gzipped when it is large and the client accepts it.

    A parsed protocol's Markdown plus its chunk tree runs to megabytes of text; compressing it
    costs little CPU and saves most of the transfer, which matters once the daemon is a network
    hop away rather than a loopback call.
    """
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    encoding = None
    if len(body) >= MIN_GZIP_BYTES and _accepts_gzip(handler):
        body = gzip.compress(body)
        encoding = "gzip"
    handler.send_response(status)
    handler.send_header("Content-Type", "application/json; charset=utf-8")
    if encoding:
        handler.send_header("Content-Encoding", encoding)
    handler.send_header("Content-Length", str(len(body)))
    handler.end_headers()
    handler.wfile.write(body)


def _expected_token() -> str | None:
    """The configured shared secret, or ``None`` when authentication is disabled."""
    token = os.environ.get(AUTH_TOKEN_ENV)
    return token if token else None


def _is_authorized(handler: BaseHTTPRequestHandler) -> bool:
    """Whether the request carries the shared secret.

    Always true when no token is configured, so a loopback deployment needs no change. The
    comparison is constant-time: the token is a fixed secret, and an early-exit compare would
    leak it a byte at a time to a caller that can retry.
    """
    expected = _expected_token()
    if expected is None:
        return True
    header = handler.headers.get("Authorization", "") or ""
    prefix = "Bearer "
    if not header.startswith(prefix):
        return False
    return hmac.compare_digest(header[len(prefix):].strip(), expected)








def _safe_suffix(filename: str) -> str:
    """The supported extension of ``filename``, lowercased.

    @param filename: the client-supplied document name
    @return: ``".pdf"`` or ``".docx"``
    @raise ValueError: when the name has no supported extension
    """
    suffix = Path(filename or "").suffix.lower()
    if suffix not in SUPPORTED_SUFFIXES:
        raise ValueError(
            f"filename must end in one of {', '.join(SUPPORTED_SUFFIXES)}; got {filename!r}"
        )
    return suffix


def _drain_request_body(handler: BaseHTTPRequestHandler) -> None:
    """Read and discard any unread request body.

    An error response written while the client is still uploading gets the connection reset
    before the client can read it, so the caller sees a transport failure instead of the 400
    that says what was wrong. Whether it happens depends on how much of the body fitted in
    socket buffers, which makes it intermittent — worse than a consistent failure. Draining
    first keeps the exchange well-formed and leaves a keep-alive connection reusable.
    """
    declared = handler.headers.get("Content-Length")
    if not declared:
        return
    try:
        remaining = min(int(declared), MAX_UPLOAD_BYTES)
    except ValueError:
        return
    while remaining > 0:
        block = handler.rfile.read(min(_UPLOAD_CHUNK_BYTES, remaining))
        if not block:
            return
        remaining -= len(block)


def _spool_upload(handler: BaseHTTPRequestHandler, suffix: str) -> Path:
    """Stream the request body to a temp file inside the daemon's own filesystem.

    Streamed rather than read whole so a large document does not have to fit in memory, and the
    length is enforced as bytes arrive rather than trusted from ``Content-Length`` — a lying
    header must not be able to make the daemon write an unbounded file.

    @param handler: the active request
    @param suffix: the extension to give the temp file, which Docling uses to pick a backend
    @return: the temp file's path; the caller must delete it
    @raise ValueError: when ``Content-Length`` is missing or unparseable, or the body is empty or
        exceeds :data:`MAX_UPLOAD_BYTES`
    """
    declared = handler.headers.get("Content-Length")
    if declared is None:
        # Refused rather than read up to the cap: BaseHTTPRequestHandler does not decode chunked
        # transfer-encoding, so a bodiless-looking request would otherwise block this thread on
        # rfile.read() until the client sent MAX_UPLOAD_BYTES or hung up.
        raise ValueError("Content-Length is required")
    try:
        length = int(declared)
    except ValueError:
        raise ValueError(f"invalid Content-Length: {declared!r}") from None
    if length < 0:
        raise ValueError(f"invalid Content-Length: {declared!r}")
    if length > MAX_UPLOAD_BYTES:
        raise ValueError(f"document too large ({length} > {MAX_UPLOAD_BYTES} bytes)")

    remaining = length
    written = 0
    handle = tempfile.NamedTemporaryFile(prefix="docling-upload-", suffix=suffix, delete=False)
    path = Path(handle.name)
    try:
        with handle:
            while remaining > 0:
                block = handler.rfile.read(min(_UPLOAD_CHUNK_BYTES, remaining))
                if not block:
                    break
                written += len(block)
                if written > MAX_UPLOAD_BYTES:
                    raise ValueError(f"document exceeds {MAX_UPLOAD_BYTES} bytes")
                handle.write(block)
                remaining -= len(block)
        if written == 0:
            raise ValueError("request body is empty")
        return path
    except BaseException:
        path.unlink(missing_ok=True)
        raise



def _parse_document(
    input_path: Path,
    *,
    filename: str,
    chunk: bool,
    max_tokens: int,
    min_structure_tokens: int,
) -> dict[str, Any]:
    """Convert a document and, when asked, build its chunk tree — all in memory.

    This is the filesystem-free path: nothing is written except the caller's own upload temp
    file, so the daemon can run in a separate container with no volume shared with its caller.
    Records come from the uploaded PDF itself, so bookmark-derived outlines still work.

    @param input_path: the spooled upload
    @param filename: the original document name, used for the source_file header and fileId
    @param chunk: also split the document into its chunk tree
    @param max_tokens: chunk budget
    @param min_structure_tokens: leave the document unchunked below this size
    @return: the response payload
    """
    markdown, logs = _convert_file(input_path, source_file=filename)
    if not chunk:
        return {"markdown": markdown, "chunked": False, "logs": logs}

    records = []
    if input_path.suffix.lower() == ".pdf":
        records = extract_verified_outline(input_path, markdown)

    tree = build_chunk_tree(
        markdown,
        filename,
        max_tokens=max_tokens,
        min_structure_tokens=min_structure_tokens,
        records=records,
    )
    return {
        "markdown": tree["markdown"],
        "chunked": tree["chunked"],
        "outline": tree["outline"],
        "catalog": tree["catalog"],
        "chunks": tree["chunks"],
        "logs": logs,
    }


def _convert_file(input_path: Path, source_file: str | None = None) -> tuple[str, str]:
    logs: list[str] = []

    def log(message: str) -> None:
        logs.append(message)

    suffix = input_path.suffix.lower()
    if suffix == ".pdf":
        try:
            markdown = convert_pdf_to_markdown(
                input_path,
                executor=_STATE.pdf_executor,
                workers=_STATE.worker_count,
                log=log,
                source_file=source_file,
            )
        except BrokenProcessPool as exc:
            _STATE.pdf_executor_broken = True
            raise RuntimeError("PDF worker pool is broken; restart the daemon") from exc
    elif suffix == ".docx":
        with _STATE.docx_lock:
            markdown = convert_docx_to_markdown(input_path, source_file=source_file)
        log(f"Converted DOCX ({len(markdown):,} chars)")
    else:
        raise ValueError(f"Unsupported file type: {suffix}")

    return markdown, "\n".join(logs)


class DoclingDaemonHandler(BaseHTTPRequestHandler):
    """HTTP request handler for the Docling worker daemon."""

    def log_message(self, format: str, *args: Any) -> None:
        sys.stderr.write("%s - %s\n" % (self.address_string(), format % args))

    def do_GET(self) -> None:
        if self.path != "/health":
            _json_response(self, HTTPStatus.NOT_FOUND, {"error": "not found"})
            return

        ready = _STATE is not None and _STATE.is_ready()
        _json_response(
            self,
            HTTPStatus.OK,
            {
                "status": "ok" if ready else "shutting_down",
                "workers": _STATE.worker_count if _STATE is not None else 0,
                "ready": ready,
            },
        )

    def do_POST(self) -> None:
        if not _is_authorized(self):
            # Drain first, or a rejected upload resets before the client can read the 401.
            _drain_request_body(self)
            _json_response(self, HTTPStatus.UNAUTHORIZED, {"error": "unauthorized"})
            return

        if self.path == "/shutdown":
            _request_shutdown()
            _json_response(self, HTTPStatus.OK, {"status": "shutting_down"})
            return

        if self.path.split("?", 1)[0] == "/parse":
            self._handle_parse()
            return

        # No path-based endpoints remain, so an unknown route is drained before answering:
        # the caller may already be streaming a document body.
        _drain_request_body(self)
        _json_response(self, HTTPStatus.NOT_FOUND, {"error": "not found"})

    def _handle_parse(self) -> None:
        """Convert (and optionally chunk) a document uploaded in the request body.

        The document arrives as bytes and the whole result is returned, so caller and daemon
        need not share a disk. That is what makes running the daemon in its own container
        viable, and it avoids a disk round-trip between converting and chunking.

        Options come from the query string so the body can be the raw document:
        ``?filename=proto.pdf&chunk=true&max_tokens=2000&min_structure_tokens=20000``.
        """
        temp_path: Path | None = None
        body_consumed = False
        try:
            if _STATE is None or not _STATE.is_ready():
                _drain_request_body(self)
                _json_response(
                    self, HTTPStatus.SERVICE_UNAVAILABLE, {"error": "daemon shutting down"}
                )
                return

            query = parse_qs(urlsplit(self.path).query)
            filename = (query.get("filename", [""])[0] or "").strip()
            if not filename:
                raise ValueError("filename query parameter is required")
            # Only the basename: the value names the document for the source_file header and
            # fileId, and must not be able to steer any path.
            filename = Path(filename).name
            suffix = _safe_suffix(filename)

            chunk = (query.get("chunk", ["true"])[0] or "true").lower() not in ("false", "0", "no")
            options: dict[str, Any] = {}
            for name in ("max_tokens", "min_structure_tokens"):
                raw = query.get(name, [None])[0]
                if raw:
                    try:
                        parsed = int(raw)
                    except ValueError:
                        raise ValueError(f"{name} must be an integer; got {raw!r}") from None
                    if parsed < 1:
                        raise ValueError(f"{name} must be 1 or greater; got {parsed}")
                    options[name] = parsed

            temp_path = _spool_upload(self, suffix)
            # Only now is the body genuinely consumed. Setting this before the call meant the
            # except branches below skipped the drain in exactly the cases where _spool_upload
            # raises with body still unread — an oversized declared Content-Length (raises before
            # reading a byte) or an over-cap stream (raises mid-body) — so the 400 was written into
            # a connection the client was still writing to.
            body_consumed = True
            payload = _parse_document(
                temp_path,
                filename=filename,
                chunk=chunk,
                max_tokens=options.get("max_tokens", DEFAULT_MAX_TOKENS),
                min_structure_tokens=options.get(
                    "min_structure_tokens", DEFAULT_MIN_STRUCTURE_TOKENS
                ),
            )
            _json_response(self, HTTPStatus.OK, payload)
        except ValueError as exc:
            if not body_consumed:
                _drain_request_body(self)
            _json_response(self, HTTPStatus.BAD_REQUEST, {"error": str(exc)})
        except Exception as exc:
            if not body_consumed:
                _drain_request_body(self)
            _json_response(self, HTTPStatus.INTERNAL_SERVER_ERROR, {"error": str(exc)})
        finally:
            if temp_path is not None:
                temp_path.unlink(missing_ok=True)


def _request_shutdown() -> None:
    global _STATE, _SERVER
    if _STATE is not None:
        _STATE.shutdown_requested = True
    if _SERVER is not None:
        threading.Thread(target=_SERVER.shutdown, daemon=True).start()


def _handle_signal(signum: int, _frame: Any) -> None:
    _request_shutdown()


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Run a long-lived Docling HTTP worker with a warm process pool."
    )
    parser.add_argument(
        "--host",
        default=DEFAULT_HOST,
        help=(
            f"bind address (default: {DEFAULT_HOST}). Binding anywhere but loopback exposes the "
            f"endpoints to the network, so set ${AUTH_TOKEN_ENV} when you do — without it every "
            "endpoint except /health is unauthenticated"
        ),
    )
    parser.add_argument(
        "--port",
        type=int,
        default=DEFAULT_PORT,
        help=f"listen port (default: {DEFAULT_PORT})",
    )
    parser.add_argument(
        "--workers",
        type=positive_int,
        default=None,
        metavar="N",
        help=(
            "parallel PDF worker processes (default: auto from logical CPU cores "
            f"and RAM budget / {GB_PER_WORKER:.1f} GB per worker)"
        ),
    )
    return parser.parse_args()


def main() -> None:
    global _STATE, _SERVER

    args = parse_args()
    try:
        _STATE = DaemonState(args.workers)
    except Exception as e:
        print(f"Docling daemon initialization failed: {e}", file=sys.stderr, flush=True)
        sys.exit(1)

    signal.signal(signal.SIGTERM, _handle_signal)
    signal.signal(signal.SIGINT, _handle_signal)

    _SERVER = ThreadingHTTPServer((args.host, args.port), DoclingDaemonHandler)
    print(
        f"Docling daemon listening on http://{args.host}:{args.port} "
        f"with {_STATE.worker_count} warm PDF workers",
        flush=True,
    )

    try:
        _SERVER.serve_forever()
    finally:
        _STATE.close()
        _SERVER.server_close()
        print("Docling daemon stopped", flush=True)


if __name__ == "__main__":
    main()
