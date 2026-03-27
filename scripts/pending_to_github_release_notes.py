#!/usr/bin/env python3
"""Write GitHub Release markdown from changelog/pending.json entries (before merge clears them)."""
from __future__ import annotations

import json
import sys
from pathlib import Path


def main() -> None:
    if len(sys.argv) != 2:
        print("Usage: pending_to_github_release_notes.py <output.md>", file=sys.stderr)
        sys.exit(1)
    out_path = Path(sys.argv[1])
    root = Path(__file__).resolve().parent.parent
    pending_path = root / "changelog" / "pending.json"
    try:
        data = json.loads(pending_path.read_text())
    except (OSError, json.JSONDecodeError) as e:
        print(f"pending.json: {e}", file=sys.stderr)
        sys.exit(1)
    entries = data.get("entries") or []
    if not isinstance(entries, list):
        print("pending.json: entries must be a list", file=sys.stderr)
        sys.exit(1)
    lines = [str(e).strip() for e in entries if str(e).strip()]
    if not lines:
        print("pending.json: entries must be non-empty", file=sys.stderr)
        sys.exit(1)
    body = "\n".join(f"- {line}" for line in lines) + "\n"
    out_path.write_text(body)


if __name__ == "__main__":
    main()
