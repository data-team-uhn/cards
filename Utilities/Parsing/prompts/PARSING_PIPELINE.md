# Parsing Pipeline — How an upload becomes Markdown + chunks

A file (PDF / DOCX / DOC) is turned into one `<stem>.md` plus a `Chunks/` tree
(`outline.json`, and when the document is large enough `catalog.json` + `Chunk-*.md`). Two
sides cooperate: **CARDS (Java)** stages the upload onto a shared `/shared-docs` volume; a
**Docling worker (Python)** runs LibreOffice prep, Docling, and writes all derived files.

- **Java**: `modules/data-model/forms/impl/.../parse/`
- **Python**: `modules/parsing/src/main/python/`

---

## Big picture — who calls what

```mermaid
flowchart TB
    U(["Upload: PDF / DOCX / DOC"])

    subgraph Java["CARDS - Java (internal/parse)"]
        FPF["FileParserFactory.getParser"]
        SDP["SimpleDocumentParser.parse"]
        DMG["DoclingMarkdownGenerator"]
        PMS["ParsedMarkdownStore reads"]
        DCC["DoclingChatChunker"]
        PO["ParseOutline.read"]
    end

    subgraph Py["Docling worker - Python (modules/parsing)"]
        DAEMON["docling_daemon.py HTTP"]
        LO["libreoffice_convert.py"]
        GEN["docling_pdf / docling_docx"]
        WCF["write_chunk_files"]
    end

    ART[("/shared-docs/uuid/stem.md + pdf + Chunks/")]

    U --> FPF
    FPF --> SDP
    SDP -->|stage source once| ART
    SDP --> DMG
    DMG -->|"POST /parse?path=..."| DAEMON
    DAEMON --> LO
    LO -->|save docx/pdf| ART
    DAEMON --> GEN
    DAEMON --> WCF
    WCF -->|stem.md + Chunks| ART
    PMS --> ART
    PMS --> DCC
    PO --> ART
```

**Path-based round trip:** Java stages the upload under `/shared-docs/{answerUuid}/`, then
`POST /parse?path=...`. Python runs LibreOffice (DOC/DOCX), Docling, and
`write_chunk_files` (the sole writer of `{stem}.md` + `Chunks/`). The HTTP reply is a small
summary only.
---

## The daemon flow, step by step

```mermaid
sequenceDiagram
    autonumber
    participant J as CARDS_Java
    participant FS as shared_docs
    participant D as docling_daemon.py
    participant LO as libreoffice_convert
    participant P as Docling
    participant W as write_chunk_files

    Note over J: upload arrives (PDF/DOCX/DOC bytes)
    J->>FS: stage /shared-docs/uuid/file.ext
    J->>D: POST /parse?path=/shared-docs/uuid/file.ext
    D->>LO: prepare_office_document
    LO->>FS: save stem.docx / stem.pdf when needed
    D->>P: convert to Markdown
    D->>W: write_chunk_files
    W->>FS: stem.md + Chunks/
    D-->>J: summary ok markdown_path chunked logs
    J->>FS: ParseOutline.read Chunks/outline.json
```

**There is no pure-Java fallback.** Docling (daemon or CLI) is the only processor. LibreOffice
conversions and all parse-output writes happen in Python. If the daemon is unreachable or Docling
fails, `SimpleDocumentParser` fails the parse with a `DocumentParseException`.

---

## Components

### Java — stage + ask (does not write parse artifacts)

| Class | Role |
|---|---|
| `FileParserFactory` | Route PDF/DOCX/DOC → `SimpleDocumentParser` |
| `SimpleDocumentParser` | Stage upload under `/shared-docs/{answerUuid}/`, call Docling, read `{stem}.md` back; failure fails the parse |
| `DoclingMarkdownGenerator` / `DoclingParseClient` | `POST /parse?path=...`; summary only |
| `ParsedMarkdownStore` | `stageSourceFile`, read/delete/clear/resolve under `cards.parse.output.dir` (default `/shared-docs`) |
| `DoclingChatChunker` | Chunk-tree generation bookkeeping for summarization |
| `ProposalParseFolder` / `ParseOutline` | Locate `<answerDir>/<stem>.md` + `Chunks/`; read `Chunks/outline.json` |

### Python — LibreOffice + Docling + sole disk writer

| Module | Role |
|---|---|
| `docling_daemon.py` | **`POST /parse?path=...`** under `CARDS_SHARED_DOCS`, `GET /health`, `POST /shutdown` |
| `parse_document.py` | Shared orchestrator: LibreOffice prep → Docling → `write_chunk_files` |
| `libreoffice_convert.py` | DOC→DOCX+PDF, DOCX→PDF; saves beside source immediately |
| `docling_parser.py` | CLI entry via `parse_document` |
| `docling_pdf_parser.py` | `convert_pdf_to_markdown` — page-sharded parallel Docling |
| `docling_docx_parser.py` | DOCX → Markdown (Docling; no page markers) |
| `docling_batch_sizing.py` | Worker-count / page-batch sizing from RAM + cores |
| `docling_config.py` / `docling_error_detection.py` | Shared Docling pipeline options; parse-failure detection |
| `markdown_cleanup.py` | `clean_markdown` — strip garbage lines, collapse blanks (idempotent). Called **once per document**, by the converter only |
| **`chunker.py`** | `write_chunk_files` — **sole** writer of `{stem}.md` + `Chunks/`; `build_chunk_tree` is in-memory only |
| `toc_and_appendix_detection.py` | `derive_outline` — the outline **fork**, pure and the only entry point |
| `bookmarks.py` / `pdf_bookmarks.py` | Outline-record helpers / PDF bookmark extraction |
| `heading_numbering.py` | Section-numbering depth for heading levels |

---

## Markdown generation

- **PDF (Docling)** — `convert_pdf_to_markdown` reads the page count with `pypdf`,
  splits the pages into batches, and converts each batch in a **separate worker process**
  (`ProcessPoolExecutor`), exporting Markdown **per page** with a `<!-- page: N -->` marker
  before each. Fragments are concatenated in page order. (This per-page-range sharding is why
  bookmark/outline inference cannot run inside Docling — no single process sees the whole
  document; it runs later, in the chunker, over the assembled `.md` + sibling PDF.)
- **DOCX** — LibreOffice writes `{stem}.pdf`, then Docling converts the DOCX. No physical pages
  ⇒ no `<!-- page: N -->` markers (so `evidence.page` is null downstream).
- **DOC** — LibreOffice writes `{stem}.docx` and `{stem}.pdf`, then Docling converts the DOCX.

Every path ends the same way: Python `write_chunk_files` writes `<answerDir>/<stem>.md` and
`Chunks/` beside the staged source under `/shared-docs`.

---

## Chunking (`chunker.py`)

Pure regex/string work over the already-produced Markdown — **no LLM, no ML tokenizer, no
Docling re-convert** (milliseconds). Token counts are the cheap `len(text) // 4`.

The chunker does **not** clean. `clean_markdown` runs exactly once per document, in the
converter that produced the `.md`, and everything below takes that text as-is.

```
chunk_file(<stem>.md)                                        # md is already cleaned
  └─ write_chunk_files(md, output_file)
       ├─ extract_verified_outline(<stem>.pdf, md)  # sibling PDF bookmarks, pages verified
       └─ build_chunk_tree(md, filename, records) # pure: no filesystem, returns the whole tree
            ├─ derive_outline(md, records)        # fork → toc, backmatterLine, tokens, source
            │
            ├─ size gate:  tokens = len(md)//4  vs  DEFAULT_MIN_STRUCTURE_TOKENS (20000)
            │    ├─ below → outline only {chunked:false}; STOP (whole-doc used downstream)
            │    └─ at/above ↓
            │
            ├─ split main content at the shallowest ATX heading level
            ├─ unite consecutive sections up to DEFAULT_MAX_TOKENS (2000)
            ├─ over-budget piece → _split_oversized → _subchunk_blocks, in order:
            │       ATX sub-heading → outline-record cut → numbered stand-out → paragraph
            ├─ small text-only tail (< MIN_TAIL_TOKENS 500) folded into the previous part
            └─ backmatterLine..EOF → one standalone backmatter chunk (no sub-splitting)
       │
       └─ write Chunks/ : Chunk-*.md, catalog.json, outline.json,
                          bookmarks.json (the resolved records, when there are any)
```

Disk writing lives in `write_chunk_files` (analysis in `build_chunk_tree`). The only caller
of `write_chunk_files` is `chunk_file`:

- `parse_document(...)` — daemon / `docling_parser.py` CLI: LibreOffice → Docling → write `.md`
  → `chunk_file`.
- `python chunker.py <file>` — re-chunk an already-parsed `.md` via `chunk_file` alone.

See `PROPOSAL_PIPELINE_DESIGN.md` § *Stage 0 — Chunking* for the full splitting rules and the
`catalog.json` / `outline.json` shapes.

---

## The outline subsystem

The document **outline** is a list of records `{title, level|null, page|null, verified?}`,
held in memory for the whole run and folded into `Chunks/outline.json`. It drives three
things: the `toc` array, `backmatterLine`, and record-based sub-chunk cut points. Records
come from one of two sources, decided by a **fork** in `derive_outline`:

The CLI also drops the resolved records into `Chunks/bookmarks.json` so a run can be
inspected. Nothing reads that file back — records are always re-derived — which is what keeps
a stale copy from being mistaken for authoritative bookmarks on a later run.

```mermaid
flowchart TD
    A["write_chunk_files / daemon POST /parse"] --> B{"a PDF with bookmarks in hand?"}
    B -->|yes| C["extract_outline pypdf, then verify_bookmarks page-correct"]
    B -->|no| D["no records to pass in"]
    C --> E{"derive_outline: any records passed in?"}
    D --> E
    E -->|yes| F["AUTHORITATIVE: use records; printed TOC left untouched"]
    E -->|no| G["_detect_toc: clean printed TOC in place; harvest entries to records"]
    F --> H["toc = record titles; backmatterLine from records; record cut-keys drive splits"]
    G --> H
```

The producer is recorded as **`outline_source`** in `outline.json` — `pdf-bookmarks`,
`md-toc`, or `none` — and echoed in the chunk logs (`chunk_file` → `outline_source=…`),
so you can tell after the fact (or live) which path produced a document's `bookmarks.json`.

Key behaviours:

- **Verification / page-correction** (`bookmarks.verify_bookmarks`) — a bookmark often points
  one page early (header at the top of the next page). For each record the title is looked up
  on its claimed page, then N−1 and N+1; found on a neighbour ⇒ the page is **rewritten**;
  found nowhere ⇒ `verified: false`. Unpaged documents (DOCX) skip this.
- **Manual harvest** (`_entry_to_record`) — a printed-TOC entry becomes a record: title (page
  stripped), page (its trailing number), level (numbering depth via `heading_numbering`).
- **Record → line resolution** (`resolve_record_line`) — a record maps to a body line only if
  its title (normalized: casefold + alphanumerics) matches **exactly one** eligible line;
  when its page is trusted, only lines on **exactly that page** count. Fail-open: 0 or >1
  matches resolve to nothing rather than the wrong line.
- **Record cut points** (`_record_cut_keys`) — records that resolve uniquely to a non-ATX,
  non-TOC-range body line become sub-chunk boundaries — recovering section headings Docling
  emitted as plain/bold text instead of `#`.

The source PDF reaches the chunker because Java co-locates it beside the `.md`
(`ParsedMarkdownStore.saveArtifact`): DOCX→PDF renditions via `LibreOfficeConverter`, native
PDFs via `PdfParser.onDocumentBytes`.

---

## On-disk artifacts (per answer)

```
<answerDir>/
    <stem>.md                 # the parsed Markdown (Java-written)
    <stem>.pdf                # co-located source / rendition (for the bookmark outline)
    bookmarks.json            # outline records (regenerated on reconvert)
    Chunks/
        outline.json          # ALWAYS written: fileId, tokens, chunked, outline_source, toc, ranges (records stay in bookmarks.json)
        catalog.json          # only when chunked: one slim entry per Chunk-*.md
        Chunk-0.md            # content before the first boundary heading (if any)
        Chunk-1.md
        Chunk-2.1.md          # an oversized chunk split into parts
        Chunk-2.2.md
        llm_call_tracker.jsonl  # appended later by the Java LLM stages
```

`clear_prior_outputs(output_file)` deletes sibling `outline.json` / `bookmarks.json` and the
whole `Chunks/` tree. `write_chunk_files` always replaces `Chunks/`; `parse_document` with
`chunk=false` writes the `.md` and then calls `clear_prior_outputs` so a prior run's chunks
cannot linger. Staleness is handled by **wipe-and-redo**, not versioning.

---

## Run modes

| | Daemon (production) | CLI / inline |
|---|---|---|
| Convert + chunk | Java stages path under `/shared-docs`, `POST /parse?path=…` → `parse_document` → `write_chunk_files` | `docling_parser.py <file>` same path, or `python chunker.py <file>` re-chunks an existing `.md` |
| Files owned by | Python on the shared volume (Java stages the upload only) | Python (writes `.md` + `Chunks/` itself) |
| Source PDF for outline | Sibling `<stem>.pdf` beside the staged file (native or LibreOffice) | same, when a sibling `<stem>.pdf` exists |

Both modes share `chunker.py`, so the outline + chunk logic is identical.

---

## Key constants

| Constant | Value | Meaning |
|---|---|---|
| `DEFAULT_MIN_STRUCTURE_TOKENS` | 20000 | Size gate: below this the doc is left unchunked (whole-document downstream) |
| `DEFAULT_MAX_TOKENS` | 2000 | Target max tokens per chunk file before an over-budget piece is split |
| `MIN_TAIL_TOKENS` | 500 | A text-only tail smaller than this is folded back into the previous part |
| `MAX_HEADING_WORDS` / `MAX_WORD_CHARS` / `MIN_HEADING_CHARS` | 10 / 100 / 5 | Heading-validity filters (reject run-ons, garbage, `Table …` captions) |
