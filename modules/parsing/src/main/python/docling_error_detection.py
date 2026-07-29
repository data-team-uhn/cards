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

"""Detect Docling conversion failures from results and pipeline logs."""

import logging

from docling.datamodel.base_models import ConversionStatus

DOCLING_PIPELINE_LOGGER = "docling.pipeline.standard_pdf_pipeline"
PIPELINE_FAILURE_MARKERS = ("preprocess failed", "bad_alloc")


def normalized_status(status) -> ConversionStatus | None:
    """
    Coerce a Docling conversion status value to ConversionStatus.
    Returns: ConversionStatus enum, or None if status is missing or unrecognized.
    """
    if status is None:
        return None
    if isinstance(status, ConversionStatus):
        return status
    try:
        return ConversionStatus(status)
    except ValueError:
        return None


def is_pipeline_failure_log(message: str) -> bool:
    lowered = message.lower()
    return any(marker in lowered for marker in PIPELINE_FAILURE_MARKERS)


class DoclingLogCollector(logging.Handler):
    """Capture Docling pipeline ERROR logs inside a worker process."""

    def __init__(self, messages: list[str]) -> None:
        super().__init__(level=logging.ERROR)
        self._messages = messages

    def emit(self, record: logging.LogRecord) -> None:
        self._messages.append(record.getMessage())


def conversion_failure_message(
    result,
    *,
    pipeline_logs: list[str] | None = None,
) -> str | None:
    """
    Docling can log preprocess OOM (std::bad_alloc) yet still return SUCCESS with
    empty pages. Treat pipeline ERROR logs, result.errors, and non-success status
    as hard failures.
    """
    status = normalized_status(getattr(result, "status", None))
    errors = getattr(result, "errors", None) or []
    details = [
        getattr(item, "error_message", str(item))
        for item in errors
    ]

    if pipeline_logs:
        for message in pipeline_logs:
            if is_pipeline_failure_log(message):
                details.append(message)

    if not details:
        if status in (ConversionStatus.FAILURE, ConversionStatus.PARTIAL_SUCCESS):
            status_label = getattr(status, "value", status)
            return f"status={status_label}: no error details"
        return None

    status_label = getattr(status, "value", status) if status is not None else "unknown"
    return f"status={status_label}: {'; '.join(details)}"


def ensure_conversion_ok(
    result,
    *,
    pipeline_logs: list[str] | None = None,
) -> None:
    """
    Raise RuntimeError when conversion failed or no document was returned.

    Combines conversion_failure_message and document presence checks.
    """
    failure = conversion_failure_message(result, pipeline_logs=pipeline_logs)
    if failure is not None:
        raise RuntimeError(failure)
    if getattr(result, "document", None) is None:
        raise RuntimeError("No document returned")
