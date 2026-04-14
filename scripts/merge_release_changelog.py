#!/usr/bin/env python3
"""
Merge changelog/pending.json into app/src/main/assets/changelog.json with versionName/versionCode,
write Fastlane Play changelog file, then reset pending entries.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

PENDING_TEMPLATE = {
    "_readme": (
        "User-facing strings for the next release. The release pipeline stamps versionName/versionCode, "
        "merges entries into app/src/main/assets/changelog.json, then resets entries to [].\n"
        "If you don't see your entry, it means that it's been already released."
    ),
    "entries": [],
}


def main() -> None:
    if len(sys.argv) < 4:
        print(
            "Usage: merge_release_changelog.py <versionName> <versionCode> <fastlane_changelog_txt_path>"
            " [play_release_notes_path]",
            file=sys.stderr,
        )
        sys.exit(1)
    version_name = sys.argv[1]
    version_code = int(sys.argv[2])
    fastlane_path = Path(sys.argv[3])
    play_notes_path = Path(sys.argv[4]) if len(sys.argv) >= 5 else None

    root = Path(__file__).resolve().parent.parent
    pending_path = root / "changelog" / "pending.json"
    assets_json = root / "app" / "src" / "main" / "assets" / "changelog.json"

    try:
        data = json.loads(pending_path.read_text())
    except json.JSONDecodeError as e:
        print(f"pending.json: invalid JSON: {e}", file=sys.stderr)
        sys.exit(1)
    entries = data.get("entries") or []
    if not isinstance(entries, list):
        print("pending.json: entries must be a list", file=sys.stderr)
        sys.exit(1)
    items = [str(e).strip() for e in entries if str(e).strip()]
    if not items:
        print("pending.json: entries must be non-empty", file=sys.stderr)
        sys.exit(1)

    if assets_json.is_file():
        bundle = json.loads(assets_json.read_text())
    else:
        bundle = {"releases": []}
    releases = bundle.get("releases") or []
    if not isinstance(releases, list):
        releases = []
    releases = [r for r in releases if r.get("versionCode") != version_code]
    releases.insert(
        0,
        {"versionName": version_name, "versionCode": version_code, "items": items},
    )
    bundle["releases"] = releases
    assets_json.write_text(json.dumps(bundle, indent=2) + "\n")

    fastlane_path.parent.mkdir(parents=True, exist_ok=True)
    fastlane_path.write_text("\n".join(items) + "\n")

    # Google Play release notes: wrap each locale's changelog in <locale> tags.
    # Currently only en-US is generated; translated changelogs can be added later.
    if play_notes_path is not None:
        play_notes_path.parent.mkdir(parents=True, exist_ok=True)
        notes_text = "\n".join(items)
        play_notes_path.write_text(f"<en-US>\n{notes_text}\n</en-US>\n")

    pending_path.write_text(json.dumps(PENDING_TEMPLATE, indent=2) + "\n")
    print(f"Changelog merged for {version_name} ({version_code}), {len(items)} item(s)")


if __name__ == "__main__":
    main()
