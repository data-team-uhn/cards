# Docling parsing

Docling converts PDF or DOCX documents Markdown format.

Main processor from `.pdf` and `.docx` to Markdown.
- Source : https://github.com/docling-project/docling
- Required version v2.115+

## Runtime dependencies

| Package | Why |
|---------|-----|
| `docling` | Main processor: `.pdf` / `.doc` / `.docx` → `.md`; also drives hierarchical chunking |
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
# Optional: only if the shared root is not /shared-docs
# set IAP_SHARED_DOCS=C:\path\to\shared-docs

python modules/documents/processing/src/main/python/docling_daemon.py --host 127.0.0.1 --port 18765
```

### Test endpoints

- `GET  http://localhost:18765/health` — readiness probe (includes `shared_docs` root).
- `POST http://localhost:18765/parse?path=/shared-docs/.../proto.pdf&chunk=true` —
  path under the shared root → summary JSON. LibreOffice prep + Docling + `write_chunk_files`.
- `POST http://localhost:18765/shutdown` — graceful stop.

Paths outside `IAP_SHARED_DOCS` (default `/shared-docs`) are refused.

The daemon has **no authentication**. Every endpoint, `/shutdown` included, is open to whoever can
reach the port, so nothing that can route to it may be untrusted. Parsing is also slow, which makes
a reachable endpoint a cheap denial-of-service target. Two ways to hold that line:

- **The deployment** (`docker-compose.yml`) publishes **no port at all**. Every container port is
  reachable by service name inside a Compose network without publishing, so IAP running as a
  sibling service reaches the daemon at `http://docling:18765` while nothing on or off the host
  has a route in. This is the intended setup.
- **A hand-run daemon** stays on loopback: keep the `--host 127.0.0.1` default when starting it
  directly, or publish the container port as `127.0.0.1:18765:18765` rather than `18765:18765`. A
  bare `18765:18765` binds every host interface, and Docker's forwarding rules sit ahead of the
  host firewall, so it is reachable by anyone who can route to the host.

In a container the process itself still binds `0.0.0.0`, and that cannot be tightened: Docker
forwards traffic to the container's `eth0`, not its loopback, so a daemon listening only on the
container's loopback refuses every connection.

### Configuration (system properties)

| Property | Default | Purpose |
|----------|---------|---------|
| `iap.docling.daemon.url` | `http://127.0.0.1:18765` | Daemon base URL. The default suits a hand-run daemon; the Compose deployment must override it to `http://docling:18765`, the daemon's service name on the Compose network |
| `iap.docling.timeout.minutes` | `30` | Per-document parse timeout |
| `iap.docling.parse.output.dir` | `/shared-docs` | Where Java writes `<answer-uuid>/<name>.md` and `Chunks/`. Java-side only — the daemon never sees it |

Java never starts the daemon. Run it yourself — in Docker for a real deployment, or by hand for
local work (see [Manual daemon start](#manual-http-daemon-start-optional)).

Java talks to the daemon over `POST /parse` and nothing else; when the
daemon cannot be reached, parsing falls through to the pure-Java PDFBox/POI generators, which need
nothing external.
