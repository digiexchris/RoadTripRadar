#!/usr/bin/env python3
"""Fail if changelog/pending.json has no non-empty entries (for release validation)."""
from __future__ import annotations

import json
import sys
from pathlib import Path


def main() -> None:
    root = Path(__file__).resolve().parent.parent
    pending_path = root / "changelog" / "pending.json"
    if not pending_path.is_file():
        print(f"Missing {pending_path}", file=sys.stderr)
        sys.exit(1)
    try:
        data = json.loads(pending_path.read_text())
    except json.JSONDecodeError as e:
        print(f"pending.json: invalid JSON: {e}", file=sys.stderr)
        sys.exit(1)
    entries = data.get("entries") or []
    if not isinstance(entries, list):
        print("pending.json: entries must be a list", file=sys.stderr)
        sys.exit(1)
    lines = [str(e).strip() for e in entries if str(e).strip()]
    if not lines:
        print(
            "changelog/pending.json must contain at least one non-empty entry "
            "before running a release.",
            file=sys.stderr,
        )
        sys.exit(1)
    print(f"OK: {len(lines)} pending changelog entr(y/ies)")


if __name__ == "__main__":
    main()
