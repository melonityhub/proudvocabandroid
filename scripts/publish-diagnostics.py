#!/usr/bin/env python3
"""Publish CI diagnostics as a neutral check run.

GitHub truncates annotation messages to ~4 KB and the raw job logs are not
always downloadable, but a check run's output text holds up to 64 KB and is
readable through the REST API. Used by the build and device jobs on failure.

usage: publish-diagnostics.py <dir> <check name>
env:   REPO (owner/name), HEAD_SHA, GH_TOKEN
"""
import json
import os
import pathlib
import subprocess
import sys

MAX_TEXT = 60000
# Most useful files first; each gets a bounded tail so nothing crowds them out.
PRIORITY = ["summary.txt", "diagnostics-tests.txt", "instrumented-tests.txt", "build.log"]
PER_FILE = {"instrumented-tests.txt": 8000, "diagnostics-tests.txt": 20000, "build.log": 25000}
DEFAULT_PER_FILE = 3000


def main() -> int:
    out_dir, name = sys.argv[1], sys.argv[2]
    parts = []
    base = pathlib.Path(out_dir)
    if base.is_dir():
        files = [p for p in base.rglob("*") if p.is_file() and p.suffix in {".txt", ".log"}]
        files.sort(key=lambda p: (PRIORITY.index(p.name) if p.name in PRIORITY else len(PRIORITY), p.name))
        for path in files:
            text = path.read_text(encoding="utf-8", errors="replace")
            limit = PER_FILE.get(path.name, DEFAULT_PER_FILE)
            parts.append(f"===== {path.name} ({len(text)} chars, last {min(limit, len(text))}) =====\n"
                         + text[-limit:])
    if not parts:
        parts.append("no diagnostic files were produced in " + out_dir)
    body = "\n\n".join(parts)[:MAX_TEXT]
    payload = {
        "name": name,
        "head_sha": os.environ["HEAD_SHA"],
        "status": "completed",
        "conclusion": "neutral",
        "output": {"title": name, "summary": "Diagnostics from the failing CI step.", "text": body},
    }
    payload_path = pathlib.Path(os.environ.get("RUNNER_TEMP", "/tmp")) / "diagnostics-check.json"
    payload_path.write_text(json.dumps(payload), encoding="utf-8")
    result = subprocess.run(
        ["gh", "api", "-X", "POST", f"repos/{os.environ['REPO']}/check-runs",
         "--input", str(payload_path)],
        capture_output=True, text=True,
    )
    print(result.stdout[:500])
    if result.returncode != 0:
        print("could not publish diagnostics:", result.stderr[:500])
    return 0  # diagnostics must never change the job result


if __name__ == "__main__":
    sys.exit(main())
