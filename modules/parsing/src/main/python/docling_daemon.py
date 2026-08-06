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

"""
Long-running Docling worker daemon.

Keeps a warm ProcessPoolExecutor (with loaded PDF models) alive across requests.
Java calls this over HTTP instead of spawning docling_parser.py per file.

Endpoints:
    GET  /health   -> {"status": "ok", "workers": N, "ready": true}
    POST /parse    -> ?path=/shared-docs/.../file.pdf[&chunk=true][&max_tokens=]
                      [&min_structure_tokens=]
                     -> {"ok", "markdown_path", "chunked", "chunks_dir", "logs", "filename"}
    POST /shutdown -> graceful stop (used when the caller owns the daemon process)

The daemon and the main app share ``/shared-docs`` (env ``IAP_SHARED_DOCS``).

The daemon has no authentication: every endpoint is open including ``/shutdown``.
"""

from __future__ import annotations

import argparse
import json
import os
import signal
import sys
import threading
from concurrent.futures import ProcessPoolExecutor
from concurrent.futures.process import BrokenProcessPool
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any
from urllib.parse import parse_qs, unquote, urlsplit

import docling_config  # noqa: F401 — apply shared Docling settings on import

from chunker import DEFAULT_MAX_TOKENS
from docling_batch_sizing import GB_PER_WORKER, calc_workers, positive_int
from docling_docx_parser import get_docx_converter
from docling_pdf_parser import warm_pdf_workers, _init_worker
from markdown_markers import INPUT_SUFFIXES
from parse_document import parse_document
from toc_and_appendix_detection import DEFAULT_MIN_STRUCTURE_TOKENS

DEFAULT_HOST = "127.0.0.1"
DEFAULT_PORT = 18765
DEFAULT_SHARED_DOCS = "/shared-docs"


class DaemonState:
    """Shared daemon resources."""

    def __init__(self, workers: int | None) -> None:
        # Refresh free-RAM budget at daemon start
        self.worker_count = calc_workers(workers)
        self.pdf_executor = ProcessPoolExecutor(
            max_workers=self.worker_count,
            initializer=_init_worker,
        )
        self.docx_lock = threading.Lock()
        self.docx_converter = None
        self.shutdown_requested = False
        self.pdf_executor_broken = False
        try:
            warm_pdf_workers(self.pdf_executor, self.worker_count)
            self.docx_converter = get_docx_converter()
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


def shared_docs_root() -> Path:
    """Root of the shared volume; paths outside it are refused."""
    configured = (os.environ.get("IAP_SHARED_DOCS") or DEFAULT_SHARED_DOCS).strip()
    return Path(configured).resolve()


def resolve_parse_path(raw_path: str) -> Path:
    """Resolve and allowlist a caller-supplied document path under the shared docs root.

    @param raw_path: absolute path from the ``path`` query parameter
    @return: resolved existing file path
    @raise ValueError: when the path is empty, outside the shared root, or not a file
    """
    text = (raw_path or "").strip()
    if not text:
        raise ValueError("path query parameter is required")
    candidate = Path(unquote(text)).resolve()
    root = shared_docs_root()
    try:
        candidate.relative_to(root)
    except ValueError as exc:
        raise ValueError(
            f"path must be under {root}; got {candidate}"
        ) from exc
    if not candidate.is_file():
        raise ValueError(f"document does not exist: {candidate}")
    suffix = candidate.suffix.lower()
    if suffix not in INPUT_SUFFIXES:
        raise ValueError(
            f"path must end in one of {', '.join(INPUT_SUFFIXES)}; got {candidate.name!r}"
        )
    return candidate


def _json_response(handler: BaseHTTPRequestHandler, status: int, payload: dict[str, Any]) -> None:
    """Send ``payload`` as JSON."""
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    handler.send_response(status)
    handler.send_header("Content-Type", "application/json; charset=utf-8")
    if handler.close_connection:
        handler.send_header("Connection", "close")
    handler.send_header("Content-Length", str(len(body)))
    handler.end_headers()
    handler.wfile.write(body)


def _drain_request_body(handler: BaseHTTPRequestHandler) -> None:
    """Read and discard any unread request body (path-based /parse has none)."""
    if handler.headers.get("Transfer-Encoding"):
        handler.close_connection = True
        return
    declared = handler.headers.get("Content-Length")
    if not declared:
        return
    try:
        remaining = int(declared)
    except ValueError:
        handler.close_connection = True
        return
    if remaining < 0 or remaining > 1024 * 1024:
        handler.close_connection = True
        return
    while remaining > 0:
        block = handler.rfile.read(min(65536, remaining))
        if not block:
            handler.close_connection = True
            return
        remaining -= len(block)


def _health_status() -> str:
    """The reason ``/health`` is refusing, for an operator reading the body."""
    if _STATE is None:
        return "starting"
    if _STATE.pdf_executor_broken:
        return "pdf_pool_broken"
    if _STATE.shutdown_requested:
        return "shutting_down"
    return "ok"


def _run_parse(
    input_path: Path,
    *,
    chunk: bool,
    max_tokens: int,
    min_structure_tokens: int,
) -> dict[str, Any]:
    """LibreOffice + Docling + chunk_file on a shared-docs path."""
    assert _STATE is not None
    try:
        return parse_document(
            input_path,
            chunk=chunk,
            max_tokens=max_tokens,
            min_structure_tokens=min_structure_tokens,
            pdf_executor=_STATE.pdf_executor,
            pdf_workers=_STATE.worker_count,
            docx_lock=_STATE.docx_lock,
            docx_converter=_STATE.docx_converter,
            # Echo progress to stderr as well: on failure the HTTP reply carries only the
            # summary message, so the container log is the only place the per-batch
            # diagnostics (e.g. "FAILED pages 4-6: ...") survive.
            log=lambda message: print(message, file=sys.stderr, flush=True),
        )
    except BrokenProcessPool as exc:
        _STATE.pdf_executor_broken = True
        _request_shutdown()
        raise RuntimeError("PDF worker pool is broken; restart the daemon") from exc


class DoclingDaemonHandler(BaseHTTPRequestHandler):
    """HTTP request handler for the Docling worker daemon."""

    protocol_version = "HTTP/1.1"
    timeout = 120

    def log_message(self, format: str, *args: Any) -> None:
        # Docker's HEALTHCHECK probes /health every 30s; logging each one only adds noise.
        if self.path == "/health":
            return
        sys.stderr.write("%s - %s\n" % (self.address_string(), format % args))

    def do_GET(self) -> None:
        if self.path != "/health":
            _json_response(self, HTTPStatus.NOT_FOUND, {"error": "not found"})
            return

        ready = _STATE is not None and _STATE.is_ready()
        _json_response(
            self,
            HTTPStatus.OK if ready else HTTPStatus.SERVICE_UNAVAILABLE,
            {
                "status": _health_status(),
                "workers": _STATE.worker_count if _STATE is not None else 0,
                "ready": ready,
                "shared_docs": str(shared_docs_root()),
            },
        )

    def do_POST(self) -> None:
        if self.path == "/shutdown":
            _request_shutdown()
            _json_response(self, HTTPStatus.OK, {"status": "shutting_down"})
            return

        if self.path.split("?", 1)[0] == "/parse":
            self._handle_parse()
            return

        _drain_request_body(self)
        _json_response(self, HTTPStatus.NOT_FOUND, {"error": "not found"})

    def _handle_parse(self) -> None:
        """Parse a document already on the shared volume.

        Query: ``?path=/shared-docs/.../file.pdf&chunk=true&max_tokens=2000&min_structure_tokens=20000``.
        """
        _drain_request_body(self)
        try:
            if _STATE is None or not _STATE.is_ready():
                _json_response(
                    self, HTTPStatus.SERVICE_UNAVAILABLE, {"error": "daemon shutting down"}
                )
                return

            query = parse_qs(urlsplit(self.path).query)
            input_path = resolve_parse_path(query.get("path", [""])[0] or "")

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

            payload = _run_parse(
                input_path,
                chunk=chunk,
                max_tokens=options.get("max_tokens", DEFAULT_MAX_TOKENS),
                min_structure_tokens=options.get(
                    "min_structure_tokens", DEFAULT_MIN_STRUCTURE_TOKENS
                ),
            )
            _json_response(self, HTTPStatus.OK, payload)
        except ValueError as exc:
            _json_response(self, HTTPStatus.BAD_REQUEST, {"error": str(exc)})
        except Exception as exc:
            _json_response(self, HTTPStatus.INTERNAL_SERVER_ERROR, {"error": str(exc)})


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
            f"bind address (default: {DEFAULT_HOST}). The endpoints have no authentication, so "
            "binding anywhere but loopback lets the network parse documents and call /shutdown; "
            "in a container bind 0.0.0.0 but publish the port to 127.0.0.1 on the host"
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


def _warn_if_exposed(host: str) -> None:
    """Say so, loudly, when the bind address is not loopback."""
    if host in ("127.0.0.1", "::1", "localhost"):
        return
    print(
        f"WARNING: binding {host}, not loopback. No endpoint authenticates, so anyone who can "
        f"reach this port can upload documents, spend the worker pool and call /shutdown. In "
        f"Docker, publish as \"127.0.0.1:<port>:{DEFAULT_PORT}\" so only this host can reach it.",
        file=sys.stderr,
        flush=True,
    )


def main() -> None:
    global _STATE, _SERVER

    args = parse_args()
    _warn_if_exposed(args.host)
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
        f"with {_STATE.worker_count} warm PDF workers "
        f"(shared docs root: {shared_docs_root()})",
        flush=True,
    )

    try:
        _SERVER.serve_forever()
    finally:
        pool_broke = _STATE.pdf_executor_broken
        _STATE.close()
        _SERVER.server_close()
        print("Docling daemon stopped", flush=True)

    if pool_broke:
        print(
            "PDF worker pool broke and cannot be rebuilt in-process; exiting so a fresh "
            "daemon is started",
            file=sys.stderr,
            flush=True,
        )
        sys.exit(1)


if __name__ == "__main__":
    main()
