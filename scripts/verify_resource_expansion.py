#!/usr/bin/env python3
"""Compare actual Gradle resource outputs; never infer parity from source matches."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path


def snapshot(root: Path) -> dict[str, bytes]:
    if not root.is_dir():
        raise ValueError(f"Missing resource output directory: {root}")
    files: dict[str, bytes] = {}
    for path in sorted(root.rglob("*")):
        if path.is_symlink():
            raise ValueError(f"Symlink is not accepted as resource evidence: {path}")
        if path.is_file():
            files[path.relative_to(root).as_posix()] = path.read_bytes()
    if not files:
        raise ValueError(f"Empty resource output directory: {root}")
    return files


def verify(before: Path, after: Path, version: str) -> dict[str, str]:
    previous, current = snapshot(before), snapshot(after)
    if previous.keys() != current.keys():
        raise ValueError(f"Resource names differ: {sorted(previous.keys() ^ current.keys())}")
    changed = [name for name in previous if previous[name] != current[name]]
    if changed:
        raise ValueError(f"Resource bytes differ: {changed}")
    if "plugin.yml" not in current:
        raise ValueError("Missing plugin.yml")
    metadata = current["plugin.yml"].decode("utf-8").splitlines()
    versions = [line for line in metadata if line.startswith("version:")]
    if not version or versions != [f"version: {version}"]:
        raise ValueError(f"Plugin version was not expanded to {version!r}: {versions}")
    return {name: hashlib.sha256(data).hexdigest() for name, data in current.items()}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("before", type=Path)
    parser.add_argument("after", type=Path)
    parser.add_argument("--version", required=True)
    args = parser.parse_args()
    try:
        digests = verify(args.before, args.after, args.version)
    except (ValueError, OSError, UnicodeError) as error:
        parser.exit(1, f"Resource expansion parity FAIL: {error}\n")
    print(json.dumps({"result": "PASS", "files": len(digests),
                      "version": args.version, "sha256": digests}, indent=2))


if __name__ == "__main__":
    main()
