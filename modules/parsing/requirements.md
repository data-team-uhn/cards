# `parsing` module — Python requirements & setup

The `parsing` module holds the Python pipeline that converts uploaded PDF/DOC/DOCX
documents into cleaned, chunked Markdown plus its pytest suite. The Java side operates daemons and runs communication.

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

Main processor from `.pdf` and `.docx` to `.md`.
- Source : https://github.com/docling-project/docling
- Required versin v2.115+

### Installation

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
   pip install docling
   pip install pypdf
   pip install psutil
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

---

## Docling daemon

The first PDF request after boot is much faster because worker processes and Docling
models stay loaded between conversions instead of being spawned per file, and converter
creation is skipped. On start you get:

- **N warm PDF worker processes** (heavy models, parallel page batches)
- **1 warm DOCX converter** in the HTTP server process (lighter, single-threaded via
  `docx_lock`)

### Java side

- The daemon is **not** started by Java. Start it with Docker, or by hand for local work.
- **`DoclingMarkdownGenerator`** / **`DoclingParseClient`** — send the document *bytes* to
  `POST /parse` and receive the Markdown and chunk tree in one reply. No paths are exchanged, so
  the daemon needs no access to the JVM's filesystem. If it cannot be reached, parsing falls
  through to the pure-Java PDFBox/POI generators.

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
- `POST http://localhost:18765/parse?filename=proto.pdf&chunk=true` — the document bytes as the
  request body → `{"markdown", "chunked", "outline", "catalog", "chunks":[{"file","text"}], "logs"}`.
  This is what Java uses.
- `POST http://localhost:18765/convert` — `{"input_path": "/tmp/…​.pdf"}` → `{"markdown", "logs"}`.
  Path-based, so it needs a shared filesystem; kept for local CLI-style testing only.
- `POST http://localhost:18765/shutdown` — graceful stop.

Send `Authorization: Bearer $DOCLING_AUTH_TOKEN` when the daemon has a token configured, and
`Accept-Encoding: gzip` — a parsed protocol's reply compresses roughly 5x.

### Configuration (system properties)

| Property | Default | Purpose |
|----------|---------|---------|
| `cards.docling.daemon.url` | `http://127.0.0.1:18765` | Daemon base URL |
| `cards.docling.auth.token` | *(unset)* | Shared secret sent as `Authorization: Bearer …`; must match the daemon's `$DOCLING_AUTH_TOKEN` |
| `cards.docling.timeout.minutes` | `30` | Per-document parse timeout |
| `cards.parse.output.dir` | `<user.dir>/cards-parsed-markdown` | Where Java writes `<answer-uuid>/<name>.md` and `Chunks/` |

Java never starts the daemon. Run it yourself — in Docker for a real deployment, or by hand for
local work (see [Manual daemon start](#manual-http-daemon-start-optional)). There is no
`cards.docling.daemon.autostart`, `cards.docling.daemon.script`, `cards.docling.daemon.python` or
`cards.docling.python`: the OSGi component that used those (`DoclingDaemonLauncher`) has been
removed, so Java has no local-Python dependency of any kind.

There is no CLI fallback. Java talks to the daemon over `POST /parse` and nothing else; when the
daemon cannot be reached, parsing falls through to the pure-Java PDFBox/POI generators, which need
nothing external. The properties that configured the old local-Python path
(`cards.docling.script`, `cards.docling.daemon.enabled`, `cards.docling.daemon.fallback`,
`cards.docling.chunk.enabled`, `cards.docling.chunk.fallback`, `cards.docling.chunk.script`) and the
launcher (`cards.docling.daemon.autostart`, `cards.docling.daemon.script`,
`cards.docling.daemon.python`, `cards.docling.python`) no longer exist — setting them does nothing.
