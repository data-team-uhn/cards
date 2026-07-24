# `parsing` module — Python requirements & setup

The `parsing` module holds the Python pipeline that converts uploaded PDF/DOC/DOCX
documents into cleaned, chunked Markdown (parse → clean → detect TOC/appendix → chunk),
plus its pytest suite. The Java side (Stage 2) launches and talks to this code.

## Runtime dependencies

| Package | Why |
|---------|-----|
| `docling` | Main processor: `.pdf` / `.doc` / `.docx` → `.md`; also drives hierarchical chunking |
| `pypdf` | Page counting / PDF reading before batching |
| `psutil` | Lets the batch-sizing script self-optimise workers to CPU/RAM |
| `tiktoken` | Token counting support |

Test-only: `pytest`.

---

## Docling

Main processor from `.pdf`, `.doc`, `.docx` to `.md`. Also used for hierarchical chat
chunking.

- Guide: https://www.codecademy.com/article/docling-ai-a-complete-guide-to-parsing#heading-how-to-extract-tables-from-documents-using-docling
- Source / license (MIT): https://github.com/docling-project/docling?tab=MIT-1-ov-file
- Docling RAG + Local LLM: https://app.dosu.dev/097760a8-135e-4789-8234-90c8837d7f1c/documents/f371a0cf-f2e3-4f29-8598-7694998de7da
- Pipeline options reference: https://docling-project.github.io/docling/reference/pipeline_options/#docling.datamodel.pipeline_options.ThreadedPdfPipelineOptions

### Installation

1. **Pre-req:** Python 3.9+
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
   pip install docling      # PDF/DOC/DOCX -> Markdown
   pip install pypdf
   pip install psutil       # lets the script self-optimise
   pip install tiktoken
   ```

   Or in one line (with the extra tooling used during development):

   ```
   python -m pip install docling openai pypdf httpx pyinstaller tiktoken psutil
   ```

5. Set the threading environment variables (keeps per-process threads at 1 so the outer
   `ProcessPoolExecutor` owns the parallelism):

   ```
   OMP_NUM_THREADS=1
   DOCLING_NUM_THREADS=1
   ```

   (The scripts also set these defaults on import via `docling_config.py`.)

6. Add the virtual environment folder to `.gitignore`:

   ```
   Docling_env/
   ```

7. Update the top-level `README.md` with the Docling pre-req + version.

---

## Docling daemon

The first PDF request after boot is much faster because worker processes and Docling
models stay loaded between conversions instead of being spawned per file, and converter
creation is skipped. On start you get:

- **N warm PDF worker processes** (heavy models, parallel page batches)
- **1 warm DOCX converter** in the HTTP server process (lighter, single-threaded via
  `docx_lock`)

### Java side

- **`DoclingDaemonLauncher`** — OSGi component that auto-starts the daemon on CARDS boot
  (skips if one is already healthy).
- **`DoclingMarkdownGenerator`** — sends temp-file paths to the daemon over HTTP; falls
  back to per-request CLI if the daemon is down.

### Daemon internals

- Starts a warm `ProcessPoolExecutor` at boot (models loaded via `_init_worker`), with
  subprocess workers warmed at startup.
- Caches a DOCX `DocumentConverter` in the main daemon process, created at startup via
  `get_docx_converter()` in `DaemonState.__init__`. There is **no** DOCX process pool.

### Manual HTTP daemon start (optional)

```
python modules/parsing/src/main/python/docling_daemon.py --host 127.0.0.1 --port 18765
```

### Test endpoints

- `GET  http://localhost:18765/health` — readiness probe. Reports the PDF worker count
  only; it does not expose DOCX status. DOCX is still warmed — it is just not counted as a
  "worker".
- `POST http://localhost:18765/convert` — `{"input_path": "/tmp/cards-docling-….pdf"}` →
  `{"markdown": "…", "logs": "…"}`
- `POST http://localhost:18765/shutdown` — graceful stop.

### Configuration (system properties)

| Property | Default | Purpose |
|----------|---------|---------|
| `cards.docling.daemon.url` | `http://127.0.0.1:18765` | Daemon base URL |
| `cards.docling.daemon.autostart` | `true` | Start daemon with CARDS |
| `cards.docling.daemon.enabled` | `true` | Use daemon from Java |
| `cards.docling.daemon.fallback` | `true` | CLI fallback if daemon unavailable |
| `cards.docling.python` | `python` | Python interpreter |
