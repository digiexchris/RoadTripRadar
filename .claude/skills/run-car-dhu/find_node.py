#!/usr/bin/env python3
"""Find a UI node in a uiautomator dump and print its center coords.

Usage:
  find_node.py <dump.xml> <needle>            # match node whose TEXT contains needle
  find_node.py <dump.xml> <needle> --desc      # match node whose CONTENT-DESC contains needle
  find_node.py <dump.xml> <needle> --last      # use the last match instead of the first

Prints "CX CY" (center, in screen pixels) and exits 0; exits 1 if not found.
The dump is produced by `adb shell uiautomator dump` (pulled to a host file).
"""
import re, sys

xml_path = sys.argv[1]
needle = sys.argv[2]
search_desc = "--desc" in sys.argv[3:]
which = "last" if "--last" in sys.argv[3:] else "first"

xml = open(xml_path).read()
matches = []
for m in re.finditer(r"<node[^>]*>", xml):
    node = m.group(0)
    hay = ""
    if search_desc:
        dm = re.search(r'content-desc="([^"]*)"', node)
        hay = dm.group(1) if dm else ""
    else:
        tm = re.search(r'text="([^"]*)"', node)
        hay = tm.group(1) if tm else ""
    if needle in hay:
        bm = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
        if bm:
            x1, y1, x2, y2 = map(int, bm.groups())
            matches.append(((x1 + x2) // 2, (y1 + y2) // 2))

if not matches:
    sys.exit(1)
x, y = matches[-1] if which == "last" else matches[0]
print(f"{x} {y}")