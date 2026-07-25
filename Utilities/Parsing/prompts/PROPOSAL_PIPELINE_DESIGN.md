# Proposal Processing Pipeline — Design (__1)

Everything that happens to an uploaded proposal **after parsing completes**: the
`is_protocol` gate (Stage 0.5), extraction (Stage 1) and summarization + chat (Stage 2).

Priorities, in order: **(1) reliable, evidence-backed answers, (2) speed, (3) few LLM
calls / tokens.** One ~30k-token prompt ≈ 3 s; output tokens dominate latency, so every
step keeps responses compact.


| Call | Prompt / schema (classpath) |
|---|---|
| Stage 0.5 — `is_protocol` gate + chunk tags | `is_protocol_system.md` + `is_protocol_schema.json` (+ full `protocol_structure.md`) |
| Stage 1.1 — intake | `step1_intake_system.md` + `step1_intake_schema.json` |
| Stage 1.2 — targeted extraction | `step2_extraction_system.md`; per-batch schema built in Java; field rules from questionnaire `prompt` / `promptKey` |
| Stage 2.1 — per-chunk summary (live) | inline system + instruction in `CatalogSummarizationService` (no prompt file) |
| Stage 2.1 — deep tags (TBD) | `stage2_chunk_prompt.md` / `rubrication_prompt.md` (design below; not wired) |
| Stage 2.2 — chat router / answer | `chat_router_prompt.md` / `chat_answer_prompt.md` (TBD; skeletons below) |

## Pipeline at a glance

```
parse + clean + outline (PDF bookmarks / printed TOC) ─> Stage 0  size gate + (optional) chunk split  [code, ms]
                                              │ outline.chunked=false → no catalog; whole-doc path downstream
                                              │ outline.chunked=true  → catalog.json + Chunk-*.md
                                              Stage 0.5  `is_protocol` gate + chunk tags   [1 LLM call]
                                                 │ no  → STOP, UI: "Error: not a valid protocol: <reasoning>"
                                                 │ yes → stamp chunk tags onto catalog      [code only; skipped if unchunked]
                                              Stage 1.1  intake: fields + fulltext chunk tags [1 LLM call]
                                              Stage 1.15 extraction_hints join              [code only]
                                              Stage 1.2  targeted field extraction (+ sweep) [sequential batches]
                                              ───────────── upload-time work ends ────────────
                                              Stage 2.1  per-chunk summary fill             [background, ~1 call/chunk; deep tags TBD]
                                              Stage 2.2  chat: router -> answer              [TBD; 2 calls per question]
```

| Scenario | Upload-time calls | Wall clock |
|---|---|---|
| Not a protocol | 1 is_protocol gate → stop | ~3 s |
| Typical 30–70 pages | 1 gate + 1 intake + 1–N extraction/sweep (sequential) | ~11–18 s |
| Rare 250 pages | 1 gate + 1 intake + several sequential extract/sweep batches | ~23–33 s |
| Chat turn (TBD) | 2 (router + answer); router skipped when doc ≤ ~20k tokens | ~5–8 s |

---

## Stage 0 — Chunking (code only, the cheap way)

Done by `chunker.py` (the single splitting module), invoked two ways:
`docling_parser.py --chunk` (CLI, per file, same-process), and the daemon's `POST /chunk`
(`chunk_file(file_path)` in the same module, taking one exact already-parsed `.md`;
also its CLI: `python chunker.py <file_path>`).
**No LLM, no ML tokenizer, no Docling re-convert** — pure regex/string work over the
already-produced Markdown (milliseconds). The heavyweight chat-chunking path
(HybridChunker + HuggingFace Qwen tokenizer + document re-conversion) is deleted, and
Java no longer writes `aggregated.md` — per-file markdown plus the `Chunks/` trees are
the only parse artifacts.

**Size gate (single routing decision):** `tokens = len(md)//4` compared to
`DEFAULT_MIN_STRUCTURE_TOKENS = 20000` (aligned with the active model's
`wholeDocumentTokenLimit`). Recorded as `outline.chunked`:

- `chunked: false` — document cleaned/marked but **left unchunked**: `Chunks/` holds only
  `outline.json` (`toc: []`, no `catalog.json`, no `Chunk-*.md`). Downstream
  stages send the whole `.md` (synthetic `chunk001` via `WholeDocument`).
- `chunked: true` — full split + slim catalog as below.

Rules (when `chunked: true`):

- **Boundary** = the shallowest ATX heading level present in the *main content* (after
  any backmatter split; level-1 `#` when it exists, otherwise the topmost level present).
  Computed by `chunker._min_heading_level()` on main content only — never from a
  whole-document "top heading" hint.
- Content before the first boundary heading starts `Chunk-0` (may be packed with following
  sections up to the token budget).
- Consecutive top-level sections are united up to the budget (`DEFAULT_MAX_TOKENS =
  2000`, counted as `len(text)//4`); only an over-budget piece is then split into
  `Chunk-<n>.<k>.md` parts. Sub-chunk boundaries are chosen in order: **ATX sub-headings**,
  then **outline-record cut points** (bookmark / printed-TOC titles that resolve to a unique
  body line — `chunker._record_cut_keys`), then **numbered stand-out headings** (bold /
  ALL-CAPS lines with a numeric prefix Docling emitted in place of a heading), then
  paragraph (blank-line) boundaries.
- **Tail-merge**: a text-only continuation part under `MIN_TAIL_TOKENS = 500` is folded
  back into the preceding part (never emitted as a tiny file), even over budget.
- **Backmatter**: when `outline.backmatterLine` is set (the first Reference/Appendix section
  resolved from the document's outline records — `toc_and_appendix_detection.backmatter_from_records`),
  everything from that line to EOF becomes one standalone final chunk — no heading-based or
  token-budget sub-splitting.
- **Catalog heading field**: the very first emitted *main-content* chunk's *displayed*
  heading is always `["General Information"]` regardless of its content; every other
  part lists its headings as a JSON array (never comma-joined into one string): ATX
  headings at the level of the part's first (beginning) heading or one level below
  (`#` stripped; text under `MIN_HEADING_CHARS = 5` rejected; deeper levels ignored)
  plus any bold/isolated-ALL-CAPS stand-out line found anywhere in the part (unfiltered
  by level). A text-only part copies the previous entry's heading array. This per-chunk
  `heading` array is the only heading record kept — there is no document-wide heading
  array in `outline.json`; Stage 0.5 reads the `chunkNNN: heading` correspondence from
  `catalog.json` instead.
- `tocStartLine` / `tocEndLine` (cleaned TOC block line range in the `.md`) and
  `backmatterLine` (first Reference/Appendix heading) are recorded in `outline.json`
  only — no reserved TOC/backmatter marker lines are inserted into the Markdown.

Output when chunked: one `Chunks/` folder:

```
protocol.md
Chunks/
    catalog.json
    outline.json
    Chunk-0.md
    Chunk-1.md
    Chunk-2.1.md      <- oversized chunk, part 1 (chunk-aligned)
    Chunk-2.2.md
        ...
```

`catalog.json` — **Stage 0 Python emits a slim catalog**; tagging/exclusion fields are
added later by Java (Stage 0.5+) from LLM output. One entry per emitted file, in
document order. Absent entirely when `chunked: false`.

**Python Stage 0 shape** (`chunker.write_chunk_files`):

```json
{
  "fileId": "protocol.pdf",
  "chunks": [
    {
      "chunk_id": "chunk001",
      "file": "Chunk-0.md",
      "heading": ["General Information"],
      "summary": "",
      "rubric_tags": [],
      "questions_answered": [],
      "extraction_hints": [],
      "pages": [1, 2],
      "length": 1837
    }
  ]
}
```

**Later stamps** (not written by Python Stage 0; owned by Java after LLM stages):
`tag_basis` (`"heading"` | `"fulltext"` | `"deep"`), `tag_confidence`, `uncertain`,
`excluded`, `exclusion_reason`; plus filled `summary` / `rubric_tags` /
`questions_answered` / `extraction_hints` as those stages complete.

Every entry carries a non-empty `heading` **array** (never a single comma-joined
string) — a chunk spanning several sub-headings lists each one separately.

`outline.json` is **always** written (even when unchunked) and holds the routing flag plus
everything Stage 0.5 needs for input selection.

```json
{
  "fileId": "protocol.pdf",
  "tokens": 21184,           // len(md)//4
  "chunked": true,           // size-gate outcome; false → whole-document path, no catalog
  "outline_source": "pdf-bookmarks",  // who produced the outline: pdf-bookmarks | md-toc | none
  "toc": ["1.0 Introduction", "1.1 Background", "…"],  // outline record titles; [] when none
  "tocStartLine": 42,        // printed-TOC path only — cleaned TOC block line range in the .md
  "tocEndLine": 78,          // (both omitted on the bookmark path: no printed TOC is scanned)
  "backmatterLine": 954      // first Reference/Appendix record resolved to a body line
}
```

The full outline **records** (`{title, level, page, verified}`) live only in the sibling
`bookmarks.json` — they are not duplicated into `outline.json`. The values above are what
`find_toc_and_appendix` records in the sidecar; `write_chunk_files` **preserves** them into
`Chunks/outline.json` (adding `fileId` / `chunked`) rather than re-deriving `toc`.

`tocStartLine`/`tocEndLine` are recorded only on the printed-TOC path (omitted on the
bookmark path, and when no TOC is present — never nulled). `backmatterLine` is omitted when
no Reference/Appendix record resolves to a body line. Confirmed values are recorded as soon
as they're known — see "outline.json outline records" below.

### outline.json outline records (derived at chunk time)

The document outline is a list of records `{title, level|null, page|null, verified?}`, from
which `toc` (titles) and `backmatterLine` are derived. Records come from **PDF bookmarks when
available, otherwise the printed TOC** — one shape either way, owned by `bookmarks.py` +
`toc_and_appendix_detection.py`. There are **no** `<!-- Reference -->` / TOC marker lines in
the Markdown; the outline lives only in JSON.

Pre-chunk pipeline (`chunker._prepare_markdown`, run for every chunking entry point):

1. **`markdown_cleanup.clean_markdown(md)`** — strips garbage / collapses blanks (idempotent).
2. **`chunker._extract_and_save_bookmarks(md, output_file)`** — when a source PDF sits beside
   the `.md` (`<stem>.pdf`, co-located by the Java parse pipeline via
   `ParsedMarkdownStore.saveArtifact` — native PDFs **and** DOCX→PDF renditions), extract its
   bookmark outline (`pdf_bookmarks.extract_outline`, pypdf), verify/correct each record's page
   against the `<!-- page: N-->` markers (`bookmarks.verify_bookmarks` — searches page N then
   N±1, rewriting an off-by-one page or flagging `verified:false`), and write the records to a
   `bookmarks.json` sidecar. A missing / bookmark-less PDF leaves no sidecar.
3. **`toc_and_appendix_detection.find_toc_and_appendix(md, outline_path)`** — the fork
   (size-gated: below `min_structure_tokens` the document is returned unchanged, no outline):
   - **`bookmarks.json` present** (real PDF bookmarks from step 2) → authoritative outline;
     the printed TOC is left untouched;
   - **no bookmarks** → `mark_and_cleanup_toc` finds + cleans the printed TOC in place and
     records `tocStartLine` / `tocEndLine`; its entries are harvested into records
     (`_entry_to_record`: title, page-from-entry, level-from-numbering-depth), verified, and
     written to `bookmarks.json`;
   - either way it records `toc` (record titles), `backmatterLine` (first Reference/Appendix
     record resolved to a body line — `backmatter_from_records`), and `tokens`.
4. **`chunker.write_chunk_files()`** — uses the records (`bookmarks.json`) to drive
   record-based sub-chunk cuts (`_record_cut_keys` → `_subchunk_blocks`), and **preserves**
   the sidecar's outline (`toc`, `outline_source`, `backmatterLine`, TOC range) into
   `Chunks/outline.json` — adding only `fileId` / `chunked`, never re-deriving `toc` and never
   copying the records in — alongside `catalog.json` + `Chunk-*.md`. The `outline.json` sidecar
   beside the `.md` is removed after folding; `bookmarks.json` persists (regenerated on
   reconvert). `clear_prior_outputs` (CLI reconvert) first deletes any stale `outline.json` /
   `bookmarks.json` / `Chunks/` tree.

**Daemon path**: `POST /convert` returns markdown only — Java writes `<stem>.md` and
co-locates `<stem>.pdf`. `POST /chunk` (`chunk_file`) then runs the whole pre-chunk pipeline
above against the `.md` + its sibling PDF, so the outline is derived **uniformly at chunk
time** regardless of which generator produced the markdown (Docling daemon, Docling CLI, or
the Java PDFBox/POI fallback).

`tag_basis`/`tag_confidence`/`uncertain`/`excluded`/`exclusion_reason` are stamped by
code from LLM output, never asked of the model directly as catalog fields:
- `"heading"` — Stage 0.5 gate's coarse, heading-text-only guess for a chunk (or the
  code-side keyword fallback when the gate did not tag that chunk).
- `"fulltext"` — Stage 1.1 intake, when the chunk's full text was sent in CHUNK.
- `"deep"` — reserved for a future Stage 2.1 deep-tag pass (`ProposalCatalog.setExcluded`
  / `FieldTagMap` already accept it). **Not written today** — live Stage 2.1 only fills
  `summary` via `CatalogSummarizationService`. When deep tagging lands, it is also the
  place `excluded`/`exclusion_reason` get populated (overturn a heading/fulltext tag
  after reading the chunk body, rather than silently replacing it).

Call coverage lives in an append-only **`llm_call_tracker.jsonl`** beside catalog.json —
one line per LLM call: `{"call": N, "step": "gate|intake|extract|sweep", "fields": [...],
"chunks": [<chunk_ids sent full-text>]}`. The gate is line 1, intake line 2 (no separate
`processing` block — one mechanism for all calls). `examined(F)` = union of `chunks`
over lines whose `fields` contain F; it drives the found_answer=false coverage gate and
sweep planning (never resend a chunk already examined for F).

Staleness is handled by **wipe-and-redo**, not versioning: a re-parse / reconvert
calls `chunker.clear_prior_outputs`, which deletes the sibling `outline.json` (if any)
and the whole `Chunks/` folder (catalog, `llm_call_tracker.jsonl`, and all derived
results with it); the pipeline then re-runs from Stage 0.

(Stage 0's chunk split is nearly free, so it runs before the gate; a rejected document's
split output is simply discarded by wipe-and-redo. The gate could be moved ahead of the
split to save those milliseconds, but the gate's real purpose is to prevent the expensive
Stage 1+ LLM calls, not the split.)

---

## Stage 0.5 — is_protocol gate + chunk tags (1 LLM call)

Implemented by `ProtocolGateService`. Right after Stage 0, before any extraction, one
cheap structured call decides whether the uploaded document is a research protocol, and
— in the same call — assigns every catalog chunk its single most probable rubric tag.
Non-protocol never reaches extraction. Authoring notes: `is_protocol_prompt.md`; runtime
loads `is_protocol_system.md` + `is_protocol_schema.json`.

### Input selection (code — follows `outline.chunked`)

The chunker's recorded `chunked` in `outline.json` flag is the routing decision; the
`chunkNNN: heading` correspondence comes from `catalog.json`:

1. **`chunked: false` (small document)** — send the **whole** `.md` as
   `INPUT (full document)`. No catalog; chunk stamp is a no-op.
2. **`chunked: true` + TOC present** — send `outline.toc` entry lines **only** (no first
   chunk — a TOC already maps the whole document's structure). Header label:
   `"table of contents"`.
3. **`chunked: true` (no TOC) + catalog present** — send the catalog outline
   (`chunkNNN: heading` lines) joined by newlines, plus the first catalog chunk under
   `### FIRST CHUNK`. Header label: `"catalog outline + first chunk"`.

The document head is **never** sent. A chunked document with neither a TOC nor a catalog
assembles no INPUT, so the gate fails open (treated as a protocol) rather than sending a
raw prefix of the document.

Whenever the catalog is non-empty **and** the selected INPUT is not already the catalog
outline (case 3), a separate `## CATALOG` block of `chunkNNN: heading` lines is appended
so chunk tagging always has its targets when any exist.

### The call

Send `## PROTOCOL_STRUCTURE` = full **`protocol_structure.md`** (not the short glossary)
+ `## INPUT (<label>)` + optional `## CATALOG`. Output token budget:
`GATE_BASE_TOKENS (400) + 30 × |chunks|`.

**Output:** `{is_protocol: bool, confidence: 0–1, reasoning: string, chunk_tags:
[{chunk_id, tag, confidence}]}`. `chunk_tags` tags each chunk with a single
most-probable B.1–B.17 rubric from its heading text alone (no chunk body at this stage)
— a coarse guess later refined by Stage 1.1 (fulltext). Deep refinement is still TBD.

**Decision (code):**
- `is_protocol = true` → stamp `chunk_tags` onto the catalog when present
  (`ProtocolGateService.stampCatalog`, from `ChunkExtractionServlet` before intake):
  each returned `chunk_id` gets `rubric_tags`, `tag_basis="heading"`, `tag_confidence`,
  and `uncertain=true`; a chunk id repeated in the response keeps its first tag. Unknown
  ids and chunks the gate did not tag are left untouched — Stage 1.1's fallback covers
  them. Continue to Stage 1.1.
- `is_protocol = false` → **stop**. No further LLM calls, no extraction, no catalog
  stamping. UI: `Error: this is not a valid protocol: <reasoning>`.
- Fail-open (unparseable after one re-ask, or no assemblable input) → treat as protocol
  with `failedOpen=true` and empty `chunk_tags` (transport/HTTP errors still propagate
  to the servlet). No gate stamps; Stage 1.1's fallback tags every chunk. A junk upload
  that slips through only wastes the intake call — a legitimate proposal must not be
  rejected over an LLM hiccup.

---

## Stage 1, Step 1 — Intake call (1 LLM call)

- Before making the call, set a per-call max_tokens computed from the catalog:
  max_tokens ≈ ~2,000 (ten fields) + ~30 × chunk_count + ~500 margin (60 chunks →
  ~4.3k; 110 → ~5.8k). The shared global LLM output ceiling would otherwise silently
  truncate the chunk-tag map on chunk-heavy documents.

Implemented by `ProposalIntakeService` / `IntakePayload`. Authoring notes:
`step1_intake_prompt.md`; runtime loads `step1_intake_system.md` +
`step1_intake_schema.json`. One call extracts the protocol-plausible intake fields
(below) and returns `chunk_tags` for full-text chunks. The field set is driven by the
questionnaire (`prompt` / `promptKey` per question); only fields a protocol realistically
contains are extraction-enabled; administrative/contractual fields stay as manual entry.

### Payload (static prefix first, byte-identical across documents, for prompt caching)

| # | Block | ~Tokens |
|---|---|---|
| 1 | System prompt | ~700 |
| 2 | STUDY_CATEGORIES — the Research Study Description taxonomy verbatim (the form's detailed check-all-that-apply classification) | ~300 |
| 3 | PROTOCOL_STRUCTURE_GLOSSARY — `protocol_structure_glossary.md` (B.1–B.17) | ~360 |
| 4 | SCHEMA — the extraction-enabled questions' rules prompts | ~600–900 |
| 5 | CATALOG — plain `chunkNNN: heading` lines (never raw catalog.json); ~150-char opening snippet **only** for chunks not in the excerpt | 0.4–1.2k |
| 6 | CHUNK — excerpt with inline `[chunk:chunkNNN]` markers, `<!-- page: N-->` preserved | ~18–20k |

**Excerpt selection** (`IntakePayload`): when the selectable non-reference text fits the
active model's `wholeDocumentTokenLimit` (default **20000**, same threshold recorded as
`outline.chunked`), send it **all** and omit CATALOG snippets. When it does not fit, the
excerpt is **tag-driven** — the intake fields carry their own `tags` (the questionnaire's
`field_id → rubric tags`, B.1–B.17; see `PIQuestionnaire.xml`), and those decide which
chunks to send:

- **Primary (tag-driven):** take the union of the extraction fields' `tags` and select
  every chunk whose Stage 0.5 gate-assigned `rubric_tags` intersect that union, in
  document order, up to `wholeDocumentTokenLimit` — the chunks the previous (gate) call
  tagged as belonging to the rubrics these fields are extracted from, i.e. the ones most
  likely to carry the answers.
- **Fallback:** if the tag-driven pass selects **no** chunks (no field carried tags, or
  no chunk's gate tags match any of them), fall back to filling **consecutively from the
  front** in document order up to the same budget.

Reference-list chunks are dropped when the heading matches references/bibliography/…
**and** the chunk index is in the last 40% of the catalog
(`REFERENCE_TAIL_FRACTION = 0.6`). Appendices are NOT stripped — they stay
sweep-eligible. Unchunked docs use synthetic `WholeDocument` (`chunk001` = whole `.md`).

The questionnaire `tags` reach the selector via `FieldSpec`
(`{key, taskLabel, rules, tags}`): `ChunkExtractionServlet.buildFieldSpecs` reads the
multi-valued `tags` property off each question, and `IntakePayload.selectFullTextChunks`
runs the tag-driven pass (`tagDrivenSelection`) before the document-order fallback
(`frontFill`).

### Output (fields FIRST so truncation never costs them)

Every field uses the servlet contract `{found_answer, confidence, value, reasoning,
evidence:[{quote, chunk_id, page|null}]}`. Extraction-enabled (protocol-plausible) fields:

The field set is defined in **`PIQuestionnaire.xml`** (test-resources Questionnaires;
one `prompt`-bearing question per field, `promptKey` = the JSON key below):

- `study_title` — official protocol title; optional `short_title`; verbatim source language.
- `study_category` — the **Research Study Description** top category, exactly one of six
  canonical labels (e.g. "Investigator-Initiated Prospective (Full Study)").
- `study_category_flags` — the check-all-that-apply sub-flags valid for that category
  (e.g. {Health Canada Regulated, Non-regulated, Data/Material Transfer, Retrospective
  Data/Material Transfer, Visiting Personnel}), comma-separated canonical labels, one
  evidence quote per flag. Two fields because the servlet stores one value per question;
  together they are the classification at full form granularity.
- `lead_investigator` — full name; PI-equivalent roles only (Lead/Qualified/site PI);
  `lead_investigator_email` when present.
- `participating_investigators` — zero or more {name, email, institution}.
- `lead_institution` / `participating_institutions` — from {CAMH, SRI, UHT, UHN}.
- `regulatory_sponsor` — sponsor name, or "N/A".
- `study_drug_device` — whether a study drug/device is used, and which.
- `chunk_tags` — for every chunk sent in full in CHUNK (not every catalog entry —
  chunks not sent in full were already tagged by the Stage 0.5 gate): `{chunk_id,
  tags:[B.x], confidence, uncertain}`. 1–2 top-level tags only. No heading echo, no
  evidence, **no basis** (code knows it — always `"fulltext"` for these).

### After the call (code-side)

- Enum-validate every protocol-structure tag and every classification category/sub-flag against the taxonomy; drop invalid rather than re-call.
- `classification.value` non-null iff `found_answer=true`.
- On invalid JSON / schema failure: one re-ask with the validation error appended, then
  degrade gracefully (persist fields as unreviewed, mark all chunks uncertain) —
  never block the upload pipeline on a failed intake call.
- Verify every extracted filed evidence quote by normalized fuzzy match against the chunk `.md`
  (strip `<!-- page: N-->` / `[chunk:chunkNNN]` markers from both sides first — they
  interrupt sentences). Failed match → downgrade confidence, route field to Step 2.
- Append the intake line to `llm_call_tracker.jsonl` (line 2, after the gate's line 1):
  `{"call": 2, "step": "intake", "fields":
  ["study_title", "study_category", "study_category_flags", ...all intake promptKeys],
  "chunks": [<full-text chunk_ids>]}` — the same list the `tag_basis` stamp reads from.

### User confirmation (UI)
User sees the result of extractiona and gives feedback.
Ideally clicks "confirm" marking the categorisation as confidence 1.

### Tags validation (code-side)
- Every chunk sent in full (in CHUNK) gets its tag from `chunk_tags`; a fulltext
  chunk missing from a truncated/invalid response falls back to a keyword-guessed
  heading-based tag, marked `uncertain=true`. If a chunk appears twice in
  `chunk_tags`, keep the first.
- A chunk **not** sent in full keeps whatever the Stage 0.5 gate already stamped
  (`tag_basis="heading"`) — Stage 1.1 does not re-tag it. The one exception: a chunk
  the gate never tagged at all (empty `rubric_tags` — e.g. the gate failed open, or the
  chunk's tag was dropped from a truncated gate response) still gets the same
  keyword-guessed fallback, `tag_basis="heading"`, `uncertain=true`, so no chunk is ever
  left completely untagged.
- Stamp `tag_basis="fulltext"` and `tag_confidence` (from the model's `confidence`, or
  the fallback's own low confidence) only for chunks sent in full; chunks left untouched
  keep whatever `tag_basis`/`tag_confidence` they already had.
- Assign `extraction_hints` per chunk regardless of which stage last touched its tag.
  The static `field_id → [rubric tags]` map is created per extraction schema.
- **LOAD-BEARING FAIL-OPEN RULE**: chunks that are untagged, `uncertain=true`, or
  `tag_basis="heading"` are **wildcards** — they match every field and stay eligible for
  Step 2 sweeps. Without this the pipeline silently degrades to fail-closed (a garbage
  heading → wrong tag → chunk never read → field falsely "absent").

---

## Stage 1, Step 2 — Targeted field extraction (+ sweep)

Implemented by `ProposalStep2Service` + `Step2Planner`. Framing prompt:
`step2_extraction_system.md`; per-batch JSON schema is built in Java for exactly that
batch's field keys. Field rules still come from the questionnaire (`prompt` /
`promptKey`).

**Pending fields:** missing, or `found_answer=true` with `confidence < PENDING_CONFIDENCE`
(`0.75`). Higher-confidence wins across passes; a re-ask never sees the prior answer
(anti-anchoring).

### Routing on `outline.chunked`

- **`chunked: false`** — one focused re-ask of all still-pending fields over the whole
  document as synthetic `chunk001` (`WholeDocument`). **No sweep** (everything was
  already readable in one shot).
- **`chunked: true`** — planner path below, then sweep.

### Targeted pass (`Step2Planner.planTargeted`)

1. **Group pending fields by shared candidate chunk sets** — candidates are chunks whose
   `extraction_hints` contain the field and that `llm_call_tracker` has not yet examined
   for it. Fields with the same candidate id-list share one batch.
2. **Token-bound each batch** to the active model's `wholeDocumentTokenLimit` (default
   20k; a **cap, not a target**) via `splitByTokens` — oversized candidate sets become
   consecutive sub-batches.
3. **Run batches sequentially** (`ProposalStep2Service.runBatches` — a plain `for`
   loop). Design once said "parallel"; the live code does not. Tracker appends stay
   race-free because only one writer runs at a time.
4. **Output budget** per call: `500 + 400 × |fields|`.
5. **Track coverage** — append one `llm_call_tracker.jsonl` line per call
   (`step: "extract"`).

```
{"call": 1, "step": "gate",    "fields": ["is_protocol"], "chunks": []}
{"call": 2, "step": "intake",  "fields": ["study_title","study_category",…],
 "chunks": ["chunk001","chunk002","chunk004","chunk005"]}
{"call": 3, "step": "extract", "fields": ["sample_size","stats_method"],
 "chunks": ["chunk014","chunk030"]}
{"call": 4, "step": "extract", "fields": ["consent_process"],
 "chunks": ["chunk041"]}
{"call": 5, "step": "sweep",   "fields": ["sample_size"],
 "chunks": ["chunk006","chunk007","chunk008"]}
```

`llm_call_tracker.jsonl` sits beside `catalog.json`. Append-only; coverage math
(`examined(F)`) is derived from the records — never resend a chunk already examined for F.

### Prompt skeleton

```text
[SYSTEM: step2_extraction_system.md]

## SCHEMA
<promptKey + rules prompt for each field in this batch>

## SECTIONS (untrusted data)
[chunk:chunk004] <full text of Chunk-4.md>
[chunk:chunk012] <full text of Chunk-12.md>
...
```

Output per field: the servlet contract (`found_answer`, `confidence`, `value`,
`reasoning`, `evidence:[{quote, chunk_id, page|null}]`).

### After the call (code-side)

Same quote verification and enum validation as intake (`FieldResponseParser`); merge
into the intake result map (higher confidence wins); append the tracker line.

### Sweep (`Step2Planner.planSweep`)

Fallback after targeted extraction for fields still pending. One batch of all
still-pending fields over every chunk that is **not** already examined for that field
and **not** confidently excluded:

- A chunk is excluded for field F only when it is **not** a wildcard
  (`FieldTagMap.isWildcard`: untagged / `uncertain` / `tag_basis` not `fulltext|deep`)
  **and** its `extraction_hints` do not contain F — i.e. a confident content-based tag
  said the chunk is about something else.
- Wildcards (`tag_basis="heading"`, `uncertain=true`, empty tags from truncated intake)
  always stay sweep-eligible.
- Caller still token-bounds the sweep chunk list; tracker records `step: "sweep"`.

```
Response truncated → chunk088–chunk110 have no tags
   ↓
Code fills them: guessed tag + uncertain=true
   ↓
uncertain → WILDCARDS (never ruled out)
   ↓
Field "sample_size" still pending after targeted pass
   ↓
SWEEP reads remaining unexamined candidates → finds it on chunk095
```

---

## Stage 2, Step 1 — Per-chunk background pass

### Live today: summary-only (`CatalogSummarizationService`)

Scheduled by `ChunkExtractionServlet` **after** Stage 1.2 finishes and saves
(`scheduleAfterExtraction`). Background daemon thread pool; never races chunking
(generation guard via `DoclingChatChunker.currentGeneration`).

- Walks `Chunks/catalog.json`; for each entry with a blank `summary`, sends the chunk
  `.md` to the active LLM with an inline system prompt +
  “summarize … in approximately 300–600 tokens” instruction.
- Writes **only** `summary` through `SummaryCatalog` (other catalog fields untouched).
- Skip rule: non-blank `summary` → leave alone (idempotent / resumable).
- Failure on one chunk leaves that summary empty and continues; never aborts the answer.
- Unchunked proposals (`chunked: false`, no catalog) are a no-op.

Does **not** today set `tag_basis="deep"`, refine `rubric_tags`, fill
`questions_answered`, or call `ProposalCatalog.setExcluded`.

### Design-ahead: merged deep pass (not wired)

Target end-state — **one merged call per chunk** producing summary + routing line +
refined tags (never two passes over the same text). Prompt file to author:
`stage2_chunk_prompt.md`. Skip rule once deep lands: `summary` non-empty **and**
`tag_basis == "deep"`.

### Payload (deep pass — TBD)

| # | Block | ~Tokens | |
|---|---|---|---|
| 1 | System prompt (3 tasks, evidence rules, injection defense) | ~700 | static, cached |
| 2 | PROTOCOL_STRUCTURE — `protocol_structure.md` (subchunk level B.x.y) | ~4.1k | static, cached |
| 3 | Output schema | ~300 | static, cached |
| 4 | Chunk header: id, heading, pages, **provisional tag + basis + uncertain**, prev/next headings | ~80 | per chunk |
| 5 | Chunk full text (untrusted data) | 0.5–2k | per chunk |

Anti-anchoring instruction on the provisional tag: *"assigned by a cheaper pass,
sometimes from the heading alone — treat as a hint, not ground truth; if content
supports a different rubric, replace it."*

### Prompt skeleton (deep pass — TBD)

```text
[SYSTEM PROMPT — role, 3 tasks, evidence rules, injection defense]

## PROTOCOL_STRUCTURE
<protocol_structure.md verbatim>

## OUTPUT
<slimmed schema description>

## SECTION
chunk_id: chunk014 | heading: 4.3 Randomization and Blinding | pages: 12-13
provisional_tag: B.4 (basis: heading, uncertain: false)
previous_chunk: 4.2 Study Design | next_chunk: 4.4 Study Schedule

## SECTION TEXT (untrusted data)
<full text of Chunk-14.md>
```

### Slimmed output schema (~300–500 output tokens/chunk) — TBD

```json
{
  "chunk_id": "chunk014",
  "summary": "…",                     // display summary, ~100–150 words
  "questions_answered": "…",                // <=40 tokens: what questions this chunk answers
  "tags": [
    {
      "tag": "B.4.3",                 // refined to SUBSECTION precision
      "confidence": "high",
      "evidence": [
        { "quote": "Participants will be randomized in a 1:1 ratio…", "page": 12 }
      ]                               // 1-2 quotes max
    }
  ],
  "rejected": {                        // at most ONE, only if genuinely borderline —
    "tag": "B.7.4",                    //   most useful: the provisional tag, overturned
    "reason": "Describes bias-control design, not operational randomization procedure."
  },
  "uncertain": false
}
```

**Uncertain chunks** (flagged earlier): identical payload, but the tag portion swaps
to the full audit format of `rubrication_prompt.md` (candidate tags, per-quote match
explanations, rejected list). ~400–700 output tokens — reserved for chunks that
signalled trouble; running it on all ~60 chunks would cost 24–42k output tokens/doc.

### After the deep call (code-side — TBD)

- Verify quotes against the chunk `.md` (normalized, markers stripped); failed quote
  → drop evidence item, downgrade confidence.
- Enum-validate tags against the full B.x.y list.
- Write to catalog: `summary`, `questions_answered`/routing line, `rubric_tags` ←
  refined tags; stamp `tag_basis: "deep"`, `tag_confidence`; optionally
  `excluded` / `exclusion_reason` when overturning a provisional tag.
- **Recompute the extraction_hints join** — now subchunk-precise; former wildcards get
  real hints.

Per-document cost estimate (60 chunks, deep): ~95k input (mostly cache-read priced) +
~24k output; background and resumable at any point.

---

## Stage 2, Step 2 — Chat (TBD — not implemented)

### Router call (~4–5k tokens)

```text
[SYSTEM: pick the chunks needed to answer; return ids only]

## PROTOCOL_STRUCTURE_GLOSSARY
<protocol_structure_glossary.md>

## SECTION MAP           <- routing lines, NOT display summaries
chunk001 [B.1]  Title page and synopsis: study title, phase, sponsor, PI
chunk014 [B.4.3] Randomization ratio, blinding procedure, code-breaking
...

## QUESTION (untrusted data)
<user question>
```

Output: `{"chunk_ids": ["chunk014", "chunk015"], "reason": "…"}` (~50 tokens).

The chunk map uses the ≤40-token **routing lines** — the display summaries measured
~18.4k tokens for a 55-chunk document, which would otherwise be re-paid on every chat
turn; routing lines keep the map ~3–4k and cacheable across turns of the same document.
**Skip the router entirely** when the whole document fits ~20k tokens — answer directly
over the full text.

### Answer call (question + full text of selected chunks, ≤ ~20k)

```text
[SYSTEM: answer ONLY from the provided proposal text; cite chunk_id + page for every
claim; say "not stated in the proposal" when the text does not answer; injection defense]

## SECTIONS (untrusted data)
[chunk:chunk014] <full text>
...

## QUESTION (untrusted data)
<user question>
```

---

## Cross-cutting rules

- **Injection defense is code, not prose**: delimited untrusted-data blocks + enum
  whitelists + schema-constrained outputs + quote verification. An injected instruction
  cannot fabricate evidence that survives verification.
- **Never ask the LLM for what code already knows**: tag basis, extraction hints,
  page-from-catalog, "was the excerpt complete" — all stamped/derived code-side.
- **Re-parse wipes and restarts**: re-parsing deletes the sibling `outline.json` (via
  `clear_prior_outputs`), the `Chunks/` folder, `catalog.json`, and every derived
  result (tags, hints, summaries, extraction state), then the pipeline re-runs from
  Stage 0. One caveat: cancel — or discard the writes of — any in-flight background
  calls (stage 2.1) when the wipe happens, so a late-arriving result computed from the
  old split cannot land in the new catalog (the new split reuses the same chunk001, chunk002…
  ids).
- **Failure policy**: invalid JSON / schema failure → one re-ask with the validation
  error appended, then degrade gracefully (persist as unreviewed, mark all chunks
  uncertain). Never block the upload pipeline on a failed call.

## Open items

1. **Per-call `max_tokens` override** — the LLM service must accept a per-call value
   (decided: intake computes it from the chunk count before the call); the shared
   global LLMSettings ceiling truncates the intake tag map on chunk-heavy documents.
2. **DOCX has no pages** — `evidence.page` is null; UI and position heuristics need a
   chunk-order fallback.
3. **Multi-file answers (post-MVP)** — protocol + satellite files (consent forms,
   questionnaires, the applicant's Implementing Letter): run intake per file with front
   blocks from every file; a field like PI may live in one file, title in another. MVP
   processes the single protocol only.
4. **Calibration before trusting thresholds** — `PENDING_CONFIDENCE = 0.75` and
   `DEFAULT_MIN_STRUCTURE_TOKENS` / `wholeDocumentTokenLimit` (default 20k) are
   working values; build a 10–20 proposal gold set, and benchmark provider decode
   tokens/s (all wall-clock claims assume ≥300 tok/s).
5. **Classification taxonomy source** — classification now targets the form's Research
   Study Description taxonomy (contract/conduct structure), which supersedes the earlier
   `Project_Categories.md` risk taxonomy for the extraction field. Confirm whether the
   risk taxonomy is still needed anywhere (e.g. a separate field) or fully retired; the
   `Project_Categories.md` file and the old `PROJECT_CATEGORIES` payload block are unused
   by intake once the switch lands.
6. **New PIQuestionnaire** — author from the Research Study Implementing Letter form,
   inspired by `PrompterDemoQuestionnaire.xml`: every form field becomes a question;
   only protocol-plausible fields (§ Stage 1.1 output) carry a `prompt`/extraction flag;
   the rest are manual entry. The `field_id → rubric tags` map (Stage 1.15) is authored
   alongside it.
7. **Stage 2.1 deep pass + Stage 2.2 chat** — summary fill is live; merged
   summary+tags+exclude and the chat router/answer path are still to be wired.
8. **Stage 1.2 parallelism** — batches run sequentially today; parallelizing
   independent extract batches (with append-only tracker) remains an optimization.
