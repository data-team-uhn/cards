# Processing Python tests

Unit tests for the document-processing Python code in `../main/python`.

Most of the suite runs anywhere, with no heavy dependencies. `test_docling_runtime.py` and
`test_cli_end_to_end.py` need `docling` itself and skip themselves via `importorskip` when it is
absent, so a plain `python -m pytest` is always safe to run.

| Test module | Covers |
|-------------|--------|
| `test_markdown_cleanup.py` | garbage-line stripping, blank-line collapsing, leading line-number removal, source-file headers |
| `test_markdown_markers.py` | the shared `<!-- page: N -->` format: exactly one spelling is accepted and every consumer agrees on it |
| `test_toc_and_appendix_detection.py` | TOC entry recognition, label finding, in-place TOC cleanup, TOCs split across page breaks, Reference/Appendix detection, the size gate, `derive_outline`'s bookmark-vs-printed-TOC fork, and `read_outline` |
| `test_chunker.py` | heading/token helpers, the `chunk_file()` routing decision (small documents left whole, large ones split into `Chunks/`), and the `Chunks/` artefacts including `bookmarks.json` |
| `test_chunker_internals.py` | the splitting internals: block packing, oversized splitting, paragraph fallback, heading resolution, running-header suppression, and the merge passes that keep a chunk from being a bare heading or a small orphaned tail |
| `test_bookmarks.py` | outline-record helpers: title normalization, page verification with off-by-one correction, the unpaged early exit, line resolution |
| `test_pdf_bookmarks.py` | flattening a PDF's embedded bookmark tree (pypdf) |
| `test_heading_numbering.py` | section-numbering depth (`1.2.3` → 3, `1.0` → 1) and roman numbering |
| `test_docling_batch_sizing.py` | the worker/RAM/batch-page arithmetic, including cgroup v1 and v2 quota reading |
| `test_docling_runtime.py` | daemon plumbing that needs no model inference: upload spooling, the size cap, truncation, body draining, health reporting, gzip negotiation, the batch-abandon path |
| `test_cli_end_to_end.py` | `docling_parser.py` run as a subprocess against a real document |

`derive_outline` writes nothing — it returns `(document, outline_fields, records)`, and
`chunker.write_chunk_files` is what puts them on disk — so the outline tests assert on that
return value rather than on a file.

## Running

From this module's directory:

```
python -m pytest
```

`conftest.py` puts `src/main/python` on `sys.path`, so the tests import the modules by
their bare names regardless of where pytest is launched. `pytest.ini` limits collection
to `src/test/python`.

Requires `pytest` and `psutil` on the interpreter used. `docling` is optional; without it the
two modules named above skip.

## From the Maven build

The tests run in the `test` phase of this module, gated by the shared `skipTests` flag (so
`-Pquick` skips them, matching the Java tests):

```
mvn test -Ptests -pl <this module's path>
```

Point Maven at a specific interpreter (for example the Docling virtualenv) with
`-Dpython.executable=/path/to/python`.
