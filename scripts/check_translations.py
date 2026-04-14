#!/usr/bin/env python3
"""Fail if any translation file is missing string keys present in the default strings.xml."""
from __future__ import annotations

import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def extract_keys(path: Path) -> set[str]:
    tree = ET.parse(path)
    return {el.attrib["name"] for el in tree.findall("string") if "name" in el.attrib}


def main() -> None:
    res_dir = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "res"
    default_path = res_dir / "values" / "strings.xml"
    if not default_path.is_file():
        print(f"Missing default strings: {default_path}", file=sys.stderr)
        sys.exit(1)

    default_keys = extract_keys(default_path)
    translation_dirs = sorted(
        d for d in res_dir.iterdir()
        if d.is_dir() and d.name.startswith("values-") and (d / "strings.xml").is_file()
    )

    if not translation_dirs:
        print("No translation directories found; nothing to check.")
        sys.exit(0)

    errors: list[str] = []
    for td in translation_dirs:
        lang_path = td / "strings.xml"
        lang_keys = extract_keys(lang_path)
        missing = sorted(default_keys - lang_keys)
        extra = sorted(lang_keys - default_keys)
        if missing:
            errors.append(f"{td.name}/strings.xml is missing {len(missing)} key(s): {', '.join(missing)}")
        if extra:
            errors.append(f"{td.name}/strings.xml has {len(extra)} extra key(s) not in default: {', '.join(extra)}")

    if errors:
        print("Translation check FAILED:", file=sys.stderr)
        for e in errors:
            print(f"  - {e}", file=sys.stderr)
        sys.exit(1)

    print(f"OK: all {len(translation_dirs)} translation(s) have {len(default_keys)} keys matching the default.")


if __name__ == "__main__":
    main()
