#!/usr/bin/env python3
"""
Generate the English fastlane changelog from app/src/main/assets/changelog.json.

Behavior:
  - If the top entry has versionName == "next", writes its items to
    fastlane/metadata/android/en-US/changelogs/next.txt (named "next.txt"
    because the real versionCode is not known until release time; the release
    pipeline renames it to {versionCode}.txt).
  - If the top entry is a real released version (post-release state), removes
    any leftover next.txt so a stale file from a pre-release branch can't be
    accidentally resurrected.

This script is the single source of truth for next.txt generation. It is
invoked by .github/workflows/sync-fastlane-next-changelog.yml on push to main.
It is safe to run locally as well (e.g. to preview what the sync workflow
would produce); it never touches anything other than en-US/next.txt.
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
    changelogs_dir = root / "fastlane" / "metadata" / "android" / "en-US" / "changelogs"
    next_path = changelogs_dir / "next.txt"

    # Only the pending "next" entry should produce a next.txt. After a release the
    # top entry is a real version (e.g. "1.12.0") whose changelog has already been
    # renamed to {versionCode}.txt — re-creating next.txt from it would resurrect
    # the just-released text and ship it as the *following* release's changelog.
    if latest.get("versionName") != "next":
        if next_path.is_file():
            next_path.unlink()
            print(
                f"Removed stale {next_path.relative_to(root)} "
                f"(top entry is {latest.get('versionName')!r}, not 'next')"
            )
        else:
            print("No pending 'next' entry in changelog.json; nothing to do.")
        return

    items = latest.get("items") or []

    if not items:
        print("changelog.json: pending 'next' release has no items", file=sys.stderr)
        sys.exit(1)

    text = "\n".join(items)

    changelogs_dir.mkdir(parents=True, exist_ok=True)
    next_path.write_text(text + "\n")
    print(f"Wrote {next_path.relative_to(root)}")

    print(f"Generated changelog for next release, {len(items)} item(s)")


if __name__ == "__main__":
    main()
