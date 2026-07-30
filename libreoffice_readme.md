# LibreOffice document conversion in CARDS

LibreOffice runs inside the **Python parsing service** (daemon or CLI), not the Java JVM.
`libreoffice_convert.py` shells out to headless `soffice` before Docling starts.

## Conversions (saved beside the source immediately)

| Incoming | LibreOffice writes | Docling input |
|----------|--------------------|---------------|
| `.doc` | `{stem}.docx`, then `{stem}.pdf` | `{stem}.docx` |
| `.docx` | `{stem}.pdf` | original `.docx` |
| `.pdf` | (none) | original `.pdf` |

## Deployment

`soffice` must be on the PATH inside the Docling container (the Dockerfile installs
`libreoffice-writer`) or on the host when using the CLI. Override the executable with:

```
CARDS_LIBREOFFICE_SOFFICE=/usr/bin/soffice
# or
LIBREOFFICE_PATH=/usr/bin/soffice
```

On Windows (CLI / local daemon):

```
set CARDS_LIBREOFFICE_SOFFICE=C:\Program Files\LibreOffice\program\soffice.exe
```

The former Java system property `cards.libreoffice.soffice` is no longer used.
