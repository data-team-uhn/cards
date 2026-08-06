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

"""Unit tests for LibreOffice prep — especially soft-fail sibling PDF conversion."""

from pathlib import Path

import libreoffice_convert as lo


class TestPrepareOfficeDocument:
    def test_docx_pdf_failure_still_returns_docx(self, tmp_path, monkeypatch):
        docx = tmp_path / "report.docx"
        docx.write_bytes(b"pk")
        logs: list[str] = []

        def boom(input_path, target_format, output_dir=None):
            raise RuntimeError("javaldx: Could not find a Java Runtime Environment!")

        monkeypatch.setattr(lo, "convert", boom)
        result = lo.prepare_office_document(docx, log=logs.append)
        assert result == docx
        assert any("continuing without sibling PDF" in line for line in logs)

    def test_docx_pdf_success_still_returns_docx(self, tmp_path, monkeypatch):
        docx = tmp_path / "report.docx"
        docx.write_bytes(b"pk")
        calls: list[str] = []

        def fake_convert(input_path, target_format, output_dir=None):
            calls.append(target_format)
            out = Path(output_dir or input_path.parent) / f"{input_path.stem}.pdf"
            out.write_bytes(b"%PDF")
            return out

        monkeypatch.setattr(lo, "convert", fake_convert)
        assert lo.prepare_office_document(docx) == docx
        assert calls == [lo._PDF_CONVERT_TO]

    def test_doc_requires_docx_conversion(self, tmp_path, monkeypatch):
        source = tmp_path / "legacy.doc"
        source.write_bytes(b"doc")

        def boom(input_path, target_format, output_dir=None):
            raise RuntimeError("docx convert failed")

        monkeypatch.setattr(lo, "convert", boom)
        try:
            lo.prepare_office_document(source)
            assert False, "expected RuntimeError"
        except RuntimeError as exc:
            assert "docx convert failed" in str(exc)

    def test_doc_pdf_failure_still_returns_docx(self, tmp_path, monkeypatch):
        source = tmp_path / "legacy.doc"
        source.write_bytes(b"doc")
        docx = tmp_path / "legacy.docx"
        logs: list[str] = []

        def fake_convert(input_path, target_format, output_dir=None):
            if target_format == "docx":
                docx.write_bytes(b"pk")
                return docx
            raise RuntimeError("pdf export failed")

        monkeypatch.setattr(lo, "convert", fake_convert)
        assert lo.prepare_office_document(source, log=logs.append) == docx
        assert any("continuing without sibling PDF" in line for line in logs)

    def test_pdf_passthrough(self, tmp_path):
        pdf = tmp_path / "native.pdf"
        pdf.write_bytes(b"%PDF")
        assert lo.prepare_office_document(pdf) == pdf
