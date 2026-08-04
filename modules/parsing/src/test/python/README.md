# Processing Python tests

Unit tests for the document-processing Python code in `../main/python`.

Most of the suite runs anywhere, with no heavy dependencies. `test_docling_runtime.py`
needs `docling` itself and skips via `importorskip` when it is absent, so a plain
`python -m pytest` is always safe to run.

Production shape (what these tests exercise pieces of): Java stages an upload under
`IAP_SHARED_DOCS` (default `/shared-docs`); the Docling daemon runs
`parse_document` → LibreOffice prep → Docling → `write_chunk_files`, which is the
**sole** writer of `{stem}.md` + `Chunks/`. `build_chunk_tree` / `derive_outline` are
in-memory only.

| Test module | Covers |
|-------------|--------|
| `test_markdown_cleanup.py` | garbage-line stripping, blank-line collapsing, leading line-number removal, source-file headers |
| `test_markdown_markers.py` | shared `<!-- page: N -->` format (one accepted spelling), token helpers, supported suffixes |
| `test_toc_and_appendix_detection.py` | TOC entry recognition, label finding, in-place TOC cleanup, TOCs split across page breaks, Reference/Appendix detection, `derive_outline`'s bookmark-vs-printed-TOC fork, and `read_outline` |
| `test_chunker.py` | heading/token helpers; `chunk_file()` routing (small docs left whole, large ones split into `Chunks/`); `outline.json` / `catalog.json` / `bookmarks.json`; sidecar cleanup and `clear_prior_outputs` |
| `test_chunker_internals.py` | splitting internals via `build_chunk_tree`: block packing, oversized splitting, paragraph fallback, heading resolution, running-header suppression, merge passes that avoid bare-heading or tiny orphan chunks |
| `test_bookmarks.py` | outline-record helpers: title normalization, page verification with off-by-one correction, unpaged early exit, line resolution, `LineIndex` |
| `test_pdf_bookmarks.py` | flattening a PDF's embedded bookmark tree (pypdf; fake reader, no real PDF needed) |
| `test_heading_numbering.py` | section-numbering depth (`1.2.3` → 3, `1.0` → 1) and roman numbering |
| `test_docling_batch_sizing.py` | worker/RAM/batch-page arithmetic, including cgroup v1 and v2 quota reading |
| `test_docling_runtime.py` | daemon plumbing with no model inference: `IAP_SHARED_DOCS` path allowlisting for `POST /parse`, body draining, health reporting, broken PDF-pool shutdown, batch-abandon path; asserts legacy byte-upload / gzip helpers are gone |
| `test_cli_end_to_end.py` | `chunker.py` run as a subprocess (re-chunk an existing `.md`); does **not** call `docling_parser.py` / Docling |

`derive_outline` writes nothing — it returns
`(document, outline_fields, records, line_index)` — and `chunker.write_chunk_files` is
what puts the outline (and markdown / chunks) on disk. Outline tests assert on that
return value rather than on a file. The size gate (`min_structure_tokens`) lives in
`build_chunk_tree`, not in `derive_outline`.

## Running

From the `modules/documents/processing` directory (where `pytest.ini` lives):

```
python -m pytest
```

Or from this directory:

```
python -m pytest .
```

`conftest.py` puts `src/main/python` on `sys.path`, so the tests import modules by bare
name regardless of where pytest is launched. `pytest.ini` limits collection to
`src/test/python`.

Requires `pytest` and `psutil` on the interpreter used. `pypdf` is needed for
`test_pdf_bookmarks.py`. `docling` is optional; without it `test_docling_runtime.py`
skips.

## From the Maven build

The tests run in the `test` phase of `modules/documents/processing`, gated by the shared `skipTests`
flag (`-Pquick` skips them, matching the Java tests):

```
mvn test -Ptests -pl modules/documents/processing
```
