# Parsing Python tests

Unit tests for the document-parsing Python code in `Utilities/Parsing/`. They cover the
pieces that run without the heavy `docling` dependency:

- `markdown_cleanup.py` — garbage-line stripping, blank-line collapsing, the
  `<!-- cleaned -->` idempotency marker, leading line-number removal, source-file headers.
- `toc_and_appendix_detection.py` — TOC entry recognition, label finding, in-place TOC
  cleanup with `outline.json` side effects, Reference/Appendix heading detection, the
  size gate, and the outline read/write helpers.
- `chunker.py` — heading/token helpers and the end-to-end `chunk_file()` routing
  decision (small documents left whole, large ones split into `Chunks/`).
- `docling_batch_sizing.py` — the worker/RAM/batch-page arithmetic.

The `docling_*` parser/daemon modules are not tested here; they import `docling` and are
exercised through the running application instead.

## Running

From `Utilities/Parsing/`:

```
python -m pytest tests
```

`conftest.py` puts the `Parsing/` directory on `sys.path`, so the tests import the
modules by their bare names regardless of where pytest is launched. `pytest.ini` limits
collection to this `tests/` folder.

Requires `pytest` and `psutil` on the interpreter used.

## From the Maven build

The tests run in the `test` phase of the `cards-parsing-python-tests` module, gated by
the shared `skipTests` flag (so `-Pquick` skips them, matching the Java tests):

```
mvn test -Ptests -pl Utilities/Parsing
```

Point Maven at a specific interpreter (for example the Docling virtualenv) with
`-Dpython.executable=/path/to/python`.
