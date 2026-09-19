#!/usr/bin/env python3
"""Fail when player/admin-facing CJK text is introduced into the maintained English fork."""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CJK = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff]")
JAVA_STRING = re.compile(r'"(?:\\.|[^"\\])*"')

failures: list[str] = []

# Resource files are directly exposed to server owners, so comments/defaults must be English too.
for path in sorted((ROOT / "src/main/resources").rglob("*")):
    if not path.is_file() or path.suffix.lower() not in {".yml", ".yaml", ".properties", ".json"}:
        continue
    text = path.read_text(encoding="utf-8")
    for line_number, line in enumerate(text.splitlines(), 1):
        if CJK.search(line):
            failures.append(f"{path.relative_to(ROOT)}:{line_number}: {line.strip()}")

# Java comments may retain upstream notes, but runtime string literals must remain English-facing.
for path in sorted((ROOT / "src/main/java").rglob("*.java")):
    text = path.read_text(encoding="utf-8")
    for line_number, line in enumerate(text.splitlines(), 1):
        for literal in JAVA_STRING.findall(line):
            if CJK.search(literal):
                failures.append(f"{path.relative_to(ROOT)}:{line_number}: {literal}")
                break

if failures:
    print("Non-English-facing CJK text detected:")
    for failure in failures:
        print(" -", failure)
    print(f"\n{len(failures)} violation(s). Translate the exposed text or explicitly redesign it so it is not user-facing.")
    sys.exit(1)

print("English-facing text audit passed.")
