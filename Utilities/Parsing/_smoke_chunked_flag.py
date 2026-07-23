"""Throwaway smoke test for the chunked-flag routing decision."""
import json
import shutil
import sys
from pathlib import Path

from chunker import chunk_file

base = Path(__file__).parent / "_smoke_chunked_flag"
shutil.rmtree(base, ignore_errors=True)
small_dir = base / "small"
large_dir = base / "large"
small_dir.mkdir(parents=True)
large_dir.mkdir(parents=True)

small_md = small_dir / "small.md"
small_md.write_text("# Tiny protocol\n\nSome short content.\n", encoding="utf-8")

paragraph = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. " * 60
sections = []
for i in range(1, 51):
    sections.append(f"# Section {i} Heading\n\n{paragraph}\n")
large_md = large_dir / "large.md"
large_md.write_text("\n".join(sections), encoding="utf-8")

failures = []

summary_small = chunk_file(str(small_md))
print("small:", summary_small)
small_outline_path = small_dir / "Chunks" / "outline.json"
if summary_small["chunks"] != 0:
    failures.append("small doc was chunked")
if not small_outline_path.is_file():
    failures.append("small doc has no Chunks/outline.json")
else:
    outline = json.loads(small_outline_path.read_text(encoding="utf-8"))
    print("small outline:", outline)
    if outline.get("chunked") is not False:
        failures.append("small outline chunked != false")
    if outline.get("fileId") != "small.md":
        failures.append("small outline fileId wrong")
    if outline.get("toc") != []:
        failures.append("small outline toc not empty")
    if not isinstance(outline.get("tokens"), int) or outline["tokens"] <= 0:
        failures.append("small outline tokens missing")
if (small_dir / "Chunks" / "catalog.json").exists():
    failures.append("small doc unexpectedly has catalog.json")

# Re-run to confirm idempotency (cleanup marker) and stable output.
summary_small_2 = chunk_file(str(small_md))
print("small rerun:", summary_small_2)
if summary_small_2["chunks"] != 0:
    failures.append("small doc rerun was chunked")

summary_large = chunk_file(str(large_md))
print("large:", summary_large)
large_outline_path = large_dir / "Chunks" / "outline.json"
if summary_large["chunks"] <= 0:
    failures.append("large doc was not chunked")
if not large_outline_path.is_file():
    failures.append("large doc has no Chunks/outline.json")
else:
    outline = json.loads(large_outline_path.read_text(encoding="utf-8"))
    print("large outline keys:", {k: outline[k] for k in ("fileId", "tokens", "chunked")})
    if outline.get("chunked") is not True:
        failures.append("large outline chunked != true")
if not (large_dir / "Chunks" / "catalog.json").is_file():
    failures.append("large doc has no catalog.json")

# A custom threshold larger than the large doc must flip it to unchunked.
summary_forced = chunk_file(str(large_md), min_structure_tokens=10_000_000)
print("large with huge threshold:", summary_forced)
outline = json.loads(large_outline_path.read_text(encoding="utf-8"))
if summary_forced["chunks"] != 0 or outline.get("chunked") is not False:
    failures.append("custom min_structure_tokens not honoured")
if (large_dir / "Chunks" / "catalog.json").exists():
    failures.append("catalog.json not removed after re-chunk with huge threshold")

if failures:
    print("FAILURES:", failures)
    sys.exit(1)
print("ALL CHECKS PASSED")
