#!/usr/bin/env python3
"""Summarize javac deprecation/removal warnings and optionally fail on any finding."""

from __future__ import annotations

import argparse
import re
from pathlib import Path

WARNING = re.compile(r"warning: \[(deprecation|removal)\] (.+)")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("log")
    parser.add_argument("--fail-on-warnings", action="store_true")
    args = parser.parse_args()

    log = Path(args.log)
    text = log.read_text(encoding="utf-8", errors="replace") if log.is_file() else ""
    findings = []
    for line in text.splitlines():
        match = WARNING.search(line)
        if match:
            findings.append((match.group(1), line.strip()))

    deprecations = sum(kind == "deprecation" for kind, _ in findings)
    removals = sum(kind == "removal" for kind, _ in findings)

    report = Path("build/reports/deprecations.md")
    report.parent.mkdir(parents=True, exist_ok=True)
    lines = [
        "# JEG compatibility warning report",
        "",
        f"- Deprecation warnings: **{deprecations}**",
        f"- Removal warnings: **{removals}**",
        f"- Total compatibility warnings: **{len(findings)}**",
    ]
    if findings:
        lines.extend(["", "## Findings", ""])
        lines.extend(f"- `{line}`" for _, line in findings)
    report.write_text("\n".join(lines) + "\n", encoding="utf-8")

    print(
        f"Compatibility warning report written with {len(findings)} warning(s): "
        f"{deprecations} deprecation, {removals} removal."
    )
    if args.fail_on_warnings and findings:
        print("Compatibility warning gate: FAIL")
        return 1

    print("Compatibility warning gate: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
