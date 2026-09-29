#!/usr/bin/env python3
"""Self-test the JEG deprecation/removal warning parser against CI-style log lines."""

from __future__ import annotations

import subprocess
import sys
import tempfile
from pathlib import Path


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    script = root / "scripts" / "summarize_deprecations.py"

    sample = (
        "\x1b[33m/home/runner/work/JEG/Test.java:42: warning: [deprecation] "
        "ChatColor in org.bukkit has been deprecated\x1b[0m\n"
        "/home/runner/work/JEG/Test.java:43: warning: [removal] oldApi() has been deprecated and marked for removal\n"
    )

    with tempfile.TemporaryDirectory() as tmp:
        log = Path(tmp) / "compile.log"
        log.write_text(sample, encoding="utf-8")
        result = subprocess.run(
            [sys.executable, str(script), str(log), "--fail-on-warnings"],
            cwd=root,
            capture_output=True,
            text=True,
            check=False,
        )

    output = result.stdout + result.stderr
    if result.returncode == 0:
        print("Warning parser self-test: FAIL")
        print("- fail-on-warnings accepted known compatibility warnings")
        return 1
    if "2 warning(s): 1 deprecation, 1 removal" not in output:
        print("Warning parser self-test: FAIL")
        print("- expected deprecation/removal counts were not detected")
        print(output)
        return 1

    print("Warning parser self-test: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
