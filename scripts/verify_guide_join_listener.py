#!/usr/bin/env python3
"""Prevent regression to double Slimefun guide books on a first join.

The source contract intentionally checks *both* unregister and restore:
upstream issue #117 fixed the first join hook but forgot the unload restore.
"""
from pathlib import Path

path = Path("src/main/java/com/balugaq/jeg/core/managers/ListenerManager.java")
source = path.read_text(encoding="utf-8")
assert source.count("public void load() {") == 1
assert source.count("public void unload() {") == 1

load = source.split("public void load() {", 1)[1].split(
    "private void registerListeners()", 1
)[0]
unload = source.split("public void unload() {", 1)[1].split(
    "private void unregisterListeners()", 1
)[0]

expected_load = (
    "PlayerJoinEvent.getHandlerList().getRegisteredListeners()",
    "PlayerJoinEvent.getHandlerList().unregister(rl)",
    "PlayerJoinEvent.getHandlerList().bake()",
    "slimefunGuideListener = rl",
)
expected_unload = (
    "PlayerJoinEvent.getHandlerList().register(slimefunGuideListener)",
    "PlayerJoinEvent.getHandlerList().bake()",
    "slimefunGuideListener = null",
)

for needle in expected_load:
    assert needle in load, f"Missing join listener replacement: {needle}"
for needle in expected_unload:
    assert needle in unload, f"Missing join listener restoration: {needle}"
assert "PlayerRightClickEvent.getHandlerList()" not in source, (
    "Only join-time guide giveaway should be intercepted, never right-click"
)
assert "SlimefunGuideListener" in load, (
    "Do not disable unrelated player-join listeners"
)
print("JEG first-join guide listener contract: PASS")
