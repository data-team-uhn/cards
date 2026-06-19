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
Long-running Docling worker daemon for CARDS.

Keeps a warm ProcessPoolExecutor (with loaded PDF models) alive across requests.
Java calls this over HTTP instead of spawning docling_parser.py per file.

Endpoints:
    GET  /health   -> {"status": "ok", "workers": N, "ready": true}
    POST /convert  -> {"input_path": "/abs/path/file.pdf"} -> {"markdown": "...", "logs": "..."}
    POST /shutdown -> graceful stop (used when CARDS owns the daemon process)
"""

from __future__ import annotations

import argparse
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

import docling_config  # noqa: F401 — apply shared Docling settings on import

from docling_batch_sizing import GB_PER_WORKER, calc_workers
from docling_docx_parser import convert_docx_to_markdown, get_docx_converter
from docling_pdf_parser import convert_pdf_to_markdown, warm_pdf_workers, _init_worker

SUPPORTED_SUFFIXES = (".pdf", ".docx")
DEFAULT_HOST = "127.0.0.1"
DEFAULT_PORT = 18765


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


def _json_response(handler: BaseHTTPRequestHandler, status: int, payload: dict[str, Any]) -> None:
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    handler.send_response(status)
    handler.send_header("Content-Type", "application/json; charset=utf-8")
    handler.send_header("Content-Length", str(len(body)))
    handler.end_headers()
    handler.wfile.write(body)


def _read_json_body(handler: BaseHTTPRequestHandler) -> dict[str, Any]:
    length = int(handler.headers.get("Content-Length", "0"))
    if length <= 0:
        return {}
    raw = handler.rfile.read(length)
    parsed = json.loads(raw.decode("utf-8"))
    if not isinstance(parsed, dict):
        raise ValueError("JSON body must be an object")
    return parsed


def _is_allowed_path(path: Path) -> bool:
    resolved = path.resolve()
    if not resolved.is_file():
        return False
    if resolved.suffix.lower() not in SUPPORTED_SUFFIXES:
        return False
    temp_root = Path(tempfile.gettempdir()).resolve()
    try:
        return resolved.is_relative_to(temp_root)
    except AttributeError:
        return str(resolved).startswith(str(temp_root) + os.sep)


def _convert_file(input_path: Path) -> tuple[str, str]:
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
            )
        except BrokenProcessPool as exc:
            _STATE.pdf_executor_broken = True
            raise RuntimeError("PDF worker pool is broken; restart the daemon") from exc
    elif suffix == ".docx":
        with _STATE.docx_lock:
            markdown = convert_docx_to_markdown(input_path)
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
        if self.path == "/shutdown":
            _request_shutdown()
            _json_response(self, HTTPStatus.OK, {"status": "shutting_down"})
            return

        if self.path != "/convert":
            _json_response(self, HTTPStatus.NOT_FOUND, {"error": "not found"})
            return

        if _STATE is None or not _STATE.is_ready():
            _json_response(self, HTTPStatus.SERVICE_UNAVAILABLE, {"error": "daemon shutting down"})
            return

        try:
            body = _read_json_body(self)
            input_value = body.get("input_path")
            if not input_value or not isinstance(input_value, str):
                raise ValueError("input_path is required")

            input_path = Path(input_value)
            if not _is_allowed_path(input_path):
                raise ValueError("input_path must be an existing .pdf or .docx under the system temp directory")

            markdown, logs = _convert_file(input_path)

            _json_response(
                self,
                HTTPStatus.OK,
                {
                    "markdown": markdown,
                    "logs": logs,
                },
            )
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
        help=f"bind address (default: {DEFAULT_HOST})",
    )
    parser.add_argument(
        "--port",
        type=int,
        default=DEFAULT_PORT,
        help=f"listen port (default: {DEFAULT_PORT})",
    )
    parser.add_argument(
        "--workers",
        type=int,
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
