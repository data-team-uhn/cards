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

"""Headless LibreOffice conversions for the parsing pipeline.

Runs before Docling. Converted files are written beside the source immediately:

* ``.doc``  -> ``{stem}.docx`` then ``{stem}.pdf``
* ``.docx`` -> ``{stem}.pdf``

``soffice`` is resolved from ``IAP_LIBREOFFICE_SOFFICE`` or ``LIBREOFFICE_PATH``, else
``soffice`` on PATH.
"""

from __future__ import annotations

import os
import shutil
import subprocess
import tempfile
from pathlib import Path
from typing import Callable

# Explicit Writer PDF export filter (matches the former Java LibreOfficeConverter).
_PDF_CONVERT_TO = "pdf:writer_pdf_Export"

_CONVERSION_TIMEOUT_SECONDS = 120

LogFn = Callable[[str], None]


def resolve_soffice_path() -> str:
    """Absolute or PATH-relative path to the LibreOffice executable."""
    for key in ("IAP_LIBREOFFICE_SOFFICE", "LIBREOFFICE_PATH"):
        configured = (os.environ.get(key) or "").strip()
        if configured:
            return configured
    return "soffice"


def convert(input_path: Path, target_format: str, output_dir: Path | None = None) -> Path:
    """Convert ``input_path`` with headless LibreOffice.

    @param input_path: source file
    @param target_format: ``--convert-to`` argument (e.g. ``docx``, ``pdf:writer_pdf_Export``)
    @param output_dir: directory LibreOffice writes into; defaults to the source's parent
    @return: path to the converted file
    @raise FileNotFoundError: when the source is missing
    @raise RuntimeError: when soffice fails, times out, or does not produce the expected file
    """
    source = Path(input_path)
    if not source.is_file():
        raise FileNotFoundError(f"LibreOffice input does not exist: {source}")

    out_dir = Path(output_dir) if output_dir is not None else source.parent
    out_dir.mkdir(parents=True, exist_ok=True)

    extension = target_format.split(":", 1)[0]
    expected = out_dir / f"{source.stem}.{extension}"

    profile_dir = Path(tempfile.mkdtemp(prefix="iap-lo-profile-"))
    try:
        command = [
            resolve_soffice_path(),
            "--headless",
            f"-env:UserInstallation={profile_dir.as_uri()}",
            "--convert-to",
            target_format,
            "--outdir",
            str(out_dir.resolve()),
            str(source.resolve()),
        ]
        try:
            completed = subprocess.run(
                command,
                capture_output=True,
                text=True,
                timeout=_CONVERSION_TIMEOUT_SECONDS,
                check=False,
            )
        except FileNotFoundError as exc:
            raise RuntimeError(
                f"LibreOffice executable not found ({resolve_soffice_path()!r})"
            ) from exc
        except subprocess.TimeoutExpired as exc:
            raise RuntimeError(
                f"LibreOffice conversion timed out for '{source.name}'"
            ) from exc

        if completed.returncode != 0:
            detail = (completed.stdout or "") + (completed.stderr or "")
            raise RuntimeError(
                f"LibreOffice conversion failed (exit {completed.returncode}) for "
                f"'{source.name}': {detail.strip() or '(no output)'}"
            )
        if not expected.is_file():
            raise RuntimeError(
                f"LibreOffice conversion succeeded but output not found: {expected}"
            )
        return expected
    finally:
        shutil.rmtree(profile_dir, ignore_errors=True)


def _convert_sibling_pdf(source: Path, log: LogFn | None = None) -> None:
    """Best-effort sibling PDF for later bookmark extraction.

    Docling parses the ``.docx`` either way; a missing PDF only means
    ``toc_source`` falls back to the printed TOC (or none).
    """
    try:
        convert(source, _PDF_CONVERT_TO, source.parent)
    except (RuntimeError, FileNotFoundError, OSError) as exc:
        message = (
            f"LibreOffice PDF conversion failed for '{source.name}'; "
            f"continuing without sibling PDF (bookmarks unavailable): {exc}"
        )
        if log is not None:
            log(message)


def prepare_office_document(input_path: Path, *, log: LogFn | None = None) -> Path:
    """Run the LibreOffice prep step for a ``.doc`` / ``.docx`` and return the Docling input.

    Converted files are saved beside ``input_path`` immediately:

    * ``.doc``  -> ``{stem}.docx`` then best-effort ``{stem}.pdf``; Docling receives the ``.docx``
    * ``.docx`` -> best-effort ``{stem}.pdf``; Docling receives the original ``.docx``
    * anything else is returned unchanged (no LibreOffice)

    Sibling PDF conversion is best-effort: failure is logged and ignored so Docling can still
    parse the Word document. ``.doc`` → ``.docx`` remains required.

    @param input_path: staged source file under the shared docs tree
    @param log: optional line logger for soft PDF failures
    @return: path Docling should convert (``.pdf`` or ``.docx``)
    """
    source = Path(input_path)
    suffix = source.suffix.lower()
    if suffix == ".doc":
        docx_path = convert(source, "docx", source.parent)
        _convert_sibling_pdf(source, log)
        return docx_path
    if suffix == ".docx":
        _convert_sibling_pdf(source, log)
        return source
    return source
