# LibreOffice document conversion in IAP

LibreOffice runs inside the **Python parsing service** (daemon or CLI).
`libreoffice_convert.py` shells out to headless `soffice` before Docling starts.

## Conversions (saved beside the source immediately)

| Incoming | LibreOffice writes | Docling input |
|----------|--------------------|---------------|
| `.doc` | `{stem}.docx`, then `{stem}.pdf` | `{stem}.docx` |
| `.docx` | `{stem}.pdf` | original `.docx` |
| `.pdf` | (none) | original `.pdf` |

### Installation

1. If interested in using CLI install LibreOffice from https://www.libreoffice.org/download/ or:

   ```
   # Debian / Ubuntu
   sudo apt update
   sudo apt install libreoffice
   ```

   or (Dockerfile / non-interactive):

   ```
   apt-get update && \
     apt-get install -y libreoffice libreoffice-java-common default-jre-headless && \
     rm -rf /var/lib/apt/lists/*

   The Docling image already installs ``libreoffice-writer``, ``libreoffice-java-common``,
   and ``default-jre-headless`` — Writer PDF export needs a JVM even for headless
   ``docx`` → ``pdf``.
   ```

2. Ensure `soffice` is on the system PATH (or set `IAP_LIBREOFFICE_SOFFICE`). Common locations:

   - Windows: `C:/Program Files/LibreOffice/program/soffice.exe`
   - Windows: `C:/Program Files (x86)/LibreOffice/program/soffice.exe`
   - Linux: `/usr/bin/soffice`
   - Linux: `/usr/lib/libreoffice/program/soffice`
   - macOS: `/Applications/LibreOffice.app/Contents/MacOS/soffice`

    `soffice` must be on the PATH inside the Docling container (the Dockerfile installs
    `libreoffice-writer`) or on the host when using the CLI. Override the executable with:

    ```
    IAP_LIBREOFFICE_SOFFICE=/usr/bin/soffice
    # or
    LIBREOFFICE_PATH=/usr/bin/soffice
    ```

    On Windows (CLI / local daemon):

    ```
    set IAP_LIBREOFFICE_SOFFICE=C:\Program Files\LibreOffice\program\soffice.exe
    ```

3. Test:

   Windows:

   ```
   "C:\Program Files\LibreOffice\program\soffice.exe" --version
   "C:\Program Files\LibreOffice\program\soffice.exe" ^
     --headless ^
     --convert-to docx ^
     --outdir C:\output ^
     C:\input\test.doc
   ```

   Linux:

   ```
   soffice --version
   soffice --headless \
     --convert-to docx \
     --outdir output \
     input.doc
   ```

### Configuration (system properties / env)

| Property / env | Default | Purpose |
|----------------|---------|---------|
| `iap.docling.daemon.url` | `http://127.0.0.1:18765` | Daemon base URL |
| `iap.docling.timeout.minutes` | `30` | Per-document parse timeout |
| `iap.docling.parse.output.dir` / `IAP_SHARED_DOCS` | `/shared-docs` | Shared staging + parse output root |
| `IAP_LIBREOFFICE_SOFFICE` | `soffice` | LibreOffice executable |

Java never starts the daemon. When the daemon cannot be reached, parsing fails. There is no
fallback processor.

---

## Shared volume

Java and the Docling daemon share **`/shared-docs`** (env `IAP_SHARED_DOCS`, JVM property
`iap.docling.parse.output.dir`). Layout:

```
/shared-docs/{answerUuid}/
  {stem}.pdf|.docx|.doc   # staged by Java
  {stem}.docx / {stem}.pdf  # LibreOffice conversions (Python)
  {stem}.md                 # write_chunk_files only
  Chunks/                   # write_chunk_files only
```
