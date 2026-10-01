#!/usr/bin/env python3
"""Keep JEG's Slimefun Legacy menu deprecation suppressions narrow and intentional."""

from __future__ import annotations

import sys
from pathlib import Path

TARGETS = (
    "src/main/java/com/balugaq/jeg/utils/LegacyMachineRecipeBridge.java",
    "src/main/java/com/balugaq/jeg/utils/LegacyDoctorMenu.java",
)

FORBIDDEN = (
    "org.bukkit.ChatColor",
    ".setDisplayName(",
    ".getDisplayName()",
    ".setLore(",
    ".getLore()",
    ".setCustomName(",
    ".getCustomName()",
)


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    failures: list[str] = []

    for relative in TARGETS:
        path = root / relative
        if not path.is_file():
            failures.append(f"Missing Legacy menu compatibility bridge: {relative}")
            continue

        source = path.read_text(encoding="utf-8")
        if '@SuppressWarnings("deprecation") // Slimefun Legacy ChestMenu/ClickAction compatibility boundary.' not in source:
            failures.append(f"{relative} is missing its documented Legacy menu compatibility suppression")

        if "me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu" not in source:
            failures.append(f"{relative} no longer uses Legacy ChestMenu; remove its compatibility suppression")

        for token in FORBIDDEN:
            if token in source:
                failures.append(f"{relative} hides modernizable presentation API behind suppression: {token}")

    if failures:
        print("JEG Legacy menu deprecation boundary verification: FAIL")
        for failure in failures:
            print(f"- {failure}")
        return 1

    print("JEG Legacy menu deprecation boundary verification: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
