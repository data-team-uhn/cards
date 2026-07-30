# Docling parsing

Docling converts PDF or DOCX documents Markdown format.

Main processor from `.pdf` and `.docx` to Markdown.
- Source : https://github.com/docling-project/docling
- Required version v2.115+

## Runtime dependencies

| Package | Why |
|---------|-----|
| `docling` | Main processor: `.pdf` / `.docx` → Markdown; also drives hierarchical chunking |
| `pypdf` | Page counting / PDF reading before batching; bookmark extraction |
| `psutil` | Lets the batch-sizing script self-optimise workers to CPU/RAM |
| `LibreOffice` (`soffice`) | DOC→DOCX, DOC→PDF, DOCX→PDF before Docling |

Test-only: `pytest`.

---

## Installation

1. **Pre-req:** Python 3.10+ (the pipeline uses PEP 604 `X | None` annotations in
   runtime-evaluated positions, which 3.9 cannot parse)
2. Use a clean virtual environment so our dependencies stay isolated:

   ```
   python -m venv Docling_env
   ```

3. Activate the virtual environment:

   ```
   # Windows
   Docling_env\Scripts\activate

   # Linux / macOS
   source Docling_env/bin/activate
   ```

4. Install the dependencies:

   ```
   pip install docling pypdf psutil
   ```

5. Set the threading environment variables (keeps per-process threads at 1 so the outer
   `ProcessPoolExecutor` owns the parallelism):

   ```
   OMP_NUM_THREADS=1
   DOCLING_NUM_THREADS=1
   ```

---

## Docling daemon

On start you get:

- **N warm PDF worker processes** (heavy models, parallel page batches)
- **1 warm DOCX converter** in the HTTP server process (lighter, single-threaded via
  `docx_lock`)

### Java side

- The daemon is **not** started by Java. Start it with Docker, or by hand for local work.
- Java **stages** the upload once under `/shared-docs/{answerUuid}/{fileName}`, then
  `DoclingParseClient` calls `POST /parse?path=...`. Python writes all derived files.
  The reply is a small summary (`ok`, `markdown_path`, `chunked`, `chunks_dir`, `logs`).

### Manual HTTP daemon start (optional)

```
set IAP_SHARED_DOCS=/shared-docs
python modules/parsing/src/main/python/docling_daemon.py --host 127.0.0.1 --port 18765
```

### Test endpoints

- `GET  http://localhost:18765/health` — readiness probe (includes `shared_docs` root).
- `POST http://localhost:18765/parse?path=/shared-docs/.../proto.pdf&chunk=true` —
  path under the shared root → summary JSON. LibreOffice prep + Docling + `write_chunk_files`.
- `POST http://localhost:18765/shutdown` — graceful stop.

Paths outside `IAP_SHARED_DOCS` (default `/shared-docs`) are refused.

The daemon has **no authentication**. Keep `--host 127.0.0.1` when running by hand, and
publish the container port as `127.0.0.1:18765:18765`.
