#!/usr/bin/env python3
"""
Generate fastlane changelogs from changelog.json.

Reads the first (latest) entry in app/src/main/assets/changelog.json and writes:
  - fastlane/metadata/android/en-US/changelogs/next.txt

The file is named "next.txt" because the real versionCode is not known until
release time.  The release pipeline renames it to {versionCode}.txt.

Run this on your feature branch after editing changelog.json so that Crowdin can
translate the fastlane changelog before you merge and release.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path


def main() -> None:
    root = Path(__file__).resolve().parent.parent
    changelog_path = root / "app" / "src" / "main" / "assets" / "changelog.json"

    if not changelog_path.is_file():
        print(f"Missing {changelog_path}", file=sys.stderr)
        sys.exit(1)

    try:
        data = json.loads(changelog_path.read_text())
    except json.JSONDecodeError as e:
        print(f"changelog.json: invalid JSON: {e}", file=sys.stderr)
        sys.exit(1)

    releases = data.get("releases") or []
    if not releases:
        print("changelog.json: no releases found", file=sys.stderr)
        sys.exit(1)

    latest = releases[0]
    items = latest.get("items") or []

    if not items:
        print("changelog.json: latest release has no items", file=sys.stderr)
        sys.exit(1)

    text = "\n".join(items)

    # Write as next.txt — renamed to {versionCode}.txt by the release pipeline
    changelogs_dir = root / "fastlane" / "metadata" / "android" / "en-US" / "changelogs"
    changelogs_dir.mkdir(parents=True, exist_ok=True)
    next_path = changelogs_dir / "next.txt"
    next_path.write_text(text + "\n")
    print(f"Wrote {next_path.relative_to(root)}")

    print(f"Generated changelog for next release, {len(items)} item(s)")


if __name__ == "__main__":
    main()
