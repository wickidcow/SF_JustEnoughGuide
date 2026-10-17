#!/usr/bin/env python3
"""Fresh real Paper boots with the released JEG acquisition failure as a required control."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import socket
import subprocess
import time
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
CORE_URL = "https://github.com/wickidcow/Slimefun-Legacy/releases/download/v4.1.75/Slimefun-Legacy4.1.75.jar"
CORE_SHA = "206cd1e54e81bf7d3968b2b2a4bd698f1271dca9dd474f2aebf4a7a195c1b060"
CONTROL_URL = "https://github.com/wickidcow/SF_JustEnoughGuide/releases/download/v2.1.72/SF_JustEnoughGuide2.1.72.jar"
CONTROL_SHA = "14d11f23951bdf79ba7723b301d9573d4fa1df59757273a0d945e9d292c10b9d"
USER_AGENT = "SF_JustEnoughGuide-pinyin-regression/2.1.73 (https://github.com/wickidcow/SF_JustEnoughGuide)"
PHASES = ("control", "candidate-off", "candidate-on")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise RuntimeError(message)


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def download(url: str, path: Path, expected: str, cached: Path | None = None) -> None:
    if cached is not None and cached.is_file() and digest(cached) == expected:
        shutil.copy2(cached, path)
    else:
        request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
        with urllib.request.urlopen(request, timeout=90) as response:
            data = response.read(150_000_001)
        require(0 < len(data) <= 150_000_000, "Unexpected download size")
        path.write_bytes(data)
    require(digest(path) == expected and zipfile.is_zipfile(path), f"Downloaded JAR checksum mismatch: {path.name}")


def parse_result(text: str) -> dict[str, str]:
    result = {}
    for line in text.splitlines():
        require("=" in line, "Malformed native result")
        key, value = line.split("=", 1)
        require(key not in result, "Duplicate native result field")
        result[key] = value
    return result


def validate_source(metadata: dict, checkout: str, github_sha: str | None, allow_local_dirty: bool) -> None:
    require(re.fullmatch(r"[0-9a-f]{40}", checkout) is not None, "Missing checkout commit")
    require(metadata.get("source_commit") == checkout, "Candidate source differs from the actual checkout")
    if github_sha is not None:
        require(checkout == github_sha and metadata.get("working_tree_clean") is True and not allow_local_dirty,
                "CI must use clean artifacts from this exact workflow commit")
    else:
        require(metadata.get("working_tree_clean") is True or allow_local_dirty,
                "Local dirty candidate proof requires --allow-local-dirty")


def validate_phase(phase: str, result: dict[str, str], log: str, attempts: list[str], armed: bool,
                   server_exit: int, client_exit: int | None, cached_jars: list[str], shutdown: dict[str, str],
                   minecraft: str = "26.2") -> None:
    require(minecraft in ("1.21.11", "26.2", "26.3"), "Unknown native Minecraft lane")
    require(phase in PHASES and result.get("phase") == phase, "Wrong native phase")
    require(armed, "Acquisition blocker never armed")
    require(server_exit == 0, "Server did not shut down normally")
    require(not cached_jars, "A populated JEG runtime library cache masked the test")
    require('Done (' in log and shutdown == {"status": "PASS", "phase": phase, "server_stopping": "true"},
            "Incomplete native lifecycle")
    require("JEG_PINYIN_PROBE_FAIL" not in log, "Native probe reported a failure")
    if phase == "control":
        require(result.get("status") == "CONTROL_REPRODUCED" and result.get("jeg_enabled") == "false",
                "Published negative control did not fail as expected")
        require(client_exit is None, "Control must not use a connected player")
        require(any("/com/github/houbb/pinyin/0.4.0/" in url for url in attempts), "Control did not attempt real Pinyin acquisition")
        require("JEG_OFFLINE_ACQUISITION_BLOCKED" in log and "Failed to download library" in log
                and re.search(r"Error occurred while enabling JustEnoughGuide v2\.1\.72", log) is not None,
                "Control failed for a different reason")
    else:
        connected = minecraft != "26.3"
        expected = {"status": "PASS", "jeg_enabled": "true", "pinyin": str(phase == "candidate-on").lower(),
                    "guides_owned": "2", "ordinary_search": "true", "pinyin_search_respects_config": "true",
                    "dictionaries": "20", "class_origin": "candidate", "connected_players": "1" if connected else "0",
                    "search_coverage": "connected-player" if connected else "native-cache-and-api"}
        require(all(result.get(key) == value for key, value in expected.items()), "Missing candidate acceptance evidence")
        require(int(result.get("shared_chars", "0")) > 100, "OpenCC conversion cache was not initialized")
        require(not attempts and client_exit == (0 if connected else None), "Candidate acquired a runtime dependency or lost its real client")
        for failure in ("JEG_OFFLINE_ACQUISITION_BLOCKED", "Error occurred while enabling", "Error occurred while disabling",
                        "NoClassDefFoundError", "ClassNotFoundException", "NoSuchMethodError", "ExceptionInInitializerError",
                        "AbstractMethodError", "NoSuchFieldError", "IncompatibleClassChangeError", "UnsupportedClassVersionError",
                        "BootstrapMethodError", "LinkageError", "Could not pass event", "Encountered an unexpected exception",
                        "The server has not responded for", "Considering it to be crashed"):
            require(failure not in log, f"Candidate runtime error: {failure}")
        require(re.search(r"Task #\d+ for .* generated an exception|Exception in thread \"(?:Server thread|Craft Scheduler Thread)", log) is None,
                "Candidate event/scheduler failure after acceptance")
    require(f"JEG_PINYIN_PROBE_{result['status']} phase={phase}" in log, "Native result log marker is missing")


def wait_for(process: subprocess.Popen, predicate, seconds: int, failure: str) -> None:
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        require(process.poll() is None, failure + " (process exited)")
        if predicate():
            return
        time.sleep(0.5)
    raise RuntimeError(failure + " (timeout)")


def stop(process: subprocess.Popen | None) -> None:
    if process is not None and process.poll() is None:
        process.terminate()
        try:
            process.wait(timeout=20)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=10)


def read_log(path: Path) -> str:
    return path.read_text(errors="replace") if path.exists() else ""


def run_phase(args: argparse.Namespace, root: Path, phase: str, sources: dict[str, Path], version: str) -> dict:
    server = root / phase
    plugins = server / "plugins"
    plugins.mkdir(parents=True)
    for name, source in (("Slimefun.jar", sources["core"]), ("JEG.jar", sources["control"] if phase == "control" else args.candidate),
                         ("JEGPinyinProbe.jar", args.probe)):
        shutil.copy2(source, plugins / name)
    require(digest(plugins / "JEG.jar") == (CONTROL_SHA if phase == "control" else args.candidate_sha256), "Staged JEG artifact changed")
    require(digest(plugins / "JEGPinyinProbe.jar") == args.probe_sha256 and digest(args.agent) == args.agent_sha256,
            "Staged native fixtures changed")
    shutil.copy2(sources["paper"], server / "server.jar")
    # Bootstrap/server libraries may be reused between these otherwise fresh
    # worlds. No plugin file or plugin library cache is copied between phases.
    bootstrap = root / "control" if phase != "control" else args.bootstrap_cache
    if bootstrap is not None:
        require(digest(bootstrap / "server.jar") == digest(sources["paper"]), "Engine cache belongs to another Paper build")
        for name in ("cache", "libraries", "versions"):
            previous = bootstrap / name
            if previous.is_dir():
                shutil.copytree(previous, server / name)
    (server / "eula.txt").write_text("eula=true\n")
    with socket.socket() as available:
        available.bind(("127.0.0.1", 0))
        port = available.getsockname()[1]
    (server / "server.properties").write_text(
        f"server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nlevel-name=jeg-pinyin-fixture\n"
        "level-type=minecraft:flat\ngenerate-structures=false\nspawn-protection=0\nmax-players=1\n"
        "view-distance=2\nsimulation-distance=2\npause-when-empty-seconds=-1\n"
        "enable-query=false\nenable-rcon=false\nallow-nether=false\n")
    (plugins / "Slimefun").mkdir()
    (plugins / "Slimefun/config.yml").write_text("options:\n  auto-update: false\n  language: en\n  enable-translations: false\n")
    (plugins / "JustEnoughGuide").mkdir()
    with zipfile.ZipFile(plugins / "JEG.jar") as jar:
        config = jar.read("config.yml").decode("utf-8")
    config, changes = re.subn(r"(?m)^(\s*pinyin-search:)\s*(?:true|false)\s*$",
                              r"\1 " + str(phase == "candidate-on").lower(), config)
    require(changes == 1, "Expected exactly one Pinyin config entry")
    (plugins / "JustEnoughGuide/config.yml").write_text(config)
    require(not list((plugins / "JustEnoughGuide").rglob("*.jar")), "Fixture started with a populated JEG library cache")
    (server / "pinyin-fixture.marker").write_text(str(server.resolve()) + "\n")
    (server / "probe-phase.txt").write_text(phase + "\n")
    shutil.copy2(ROOT / "tests/packaged-pinyin/dictionaries.sha256", server / "dictionaries.sha256")
    server_log = server / "server.log"
    client_log = server / "client.log"
    java_args = [args.java, "-Xms512M", "-Xmx3G", "-Djeg.pinyin.probe=disposable-only",
                 f"-Djeg.pinyin.candidate={version}", f"-Djeg.pinyin.minecraft={args.minecraft}",
                 "--add-exports=java.base/sun.net.www.protocol.http=ALL-UNNAMED",
                 "--add-exports=java.base/sun.net.www.protocol.https=ALL-UNNAMED",
                 f"-javaagent:{args.agent}={server.resolve()}", "-jar", "server.jar", "--nogui"]
    process = client = None
    with server_log.open("w") as output, client_log.open("w") as client_output:
        try:
            process = subprocess.Popen(java_args, cwd=server, stdin=subprocess.PIPE, stdout=output,
                                       stderr=subprocess.STDOUT, text=True)
            wait_for(process, lambda: "Done (" in read_log(server_log), 420, "Paper did not finish startup")
            if phase != "control" and args.minecraft != "26.3":
                client = subprocess.Popen(["node", str(ROOT / "scripts/pinyin_startup_client.js"),
                                           str(args.protocol_module), str(port), args.minecraft], cwd=server,
                                          stdin=subprocess.PIPE, stdout=client_output, stderr=subprocess.STDOUT, text=True)
                wait_for(client, lambda: "JEG client reached play:" in read_log(client_log), 60, "Native player did not connect")
            process.stdin.write(f"pinyinprobe {phase}\n")
            process.stdin.flush()
            wait_for(process, lambda: (server / "probe-result.txt").exists(), 90, "Native acceptance probe did not finish")
            if client is not None:
                require(client.poll() is None, "Native client exited before acceptance completed")
                client.stdin.write("quit\n")
                client.stdin.flush()
                client.wait(timeout=20)
            process.stdin.write("stop\n")
            process.stdin.flush()
            process.wait(timeout=90)
        finally:
            stop(client)
            stop(process)
    result = parse_result((server / "probe-result.txt").read_text())
    attempts = (server / "acquisition-attempts.txt").read_text().splitlines()
    cached_jars = [str(path.relative_to(server)) for path in (plugins / "JustEnoughGuide").rglob("*.jar")]
    armed = (server / "acquisition-blocker-armed.txt").read_text().strip() == "ARMED"
    shutdown = parse_result((server / "probe-shutdown.txt").read_text())
    validate_phase(phase, result, read_log(server_log), attempts, armed, process.returncode,
                   None if client is None else client.returncode, cached_jars, shutdown, args.minecraft)
    return {"phase": phase, "result": result, "acquisition_attempts": attempts, "runtime_cached_jars": cached_jars,
            "server_exit": process.returncode, "client_exit": None if client is None else client.returncode,
            "shutdown": shutdown,
            "jeg_sha256": digest(plugins / "JEG.jar"), "server_log_sha256": digest(server_log),
            "client_log_sha256": digest(client_log)}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--candidate", type=Path, required=True)
    parser.add_argument("--probe", type=Path, required=True)
    parser.add_argument("--agent", type=Path, required=True)
    parser.add_argument("--metadata", type=Path, required=True)
    parser.add_argument("--minecraft", choices=("1.21.11", "26.2", "26.3"), required=True)
    parser.add_argument("--protocol-module", type=Path)
    parser.add_argument("--workdir", type=Path, required=True)
    parser.add_argument("--java", default="java")
    parser.add_argument("--cached-core", type=Path)
    parser.add_argument("--cached-control", type=Path)
    parser.add_argument("--cached-server", type=Path)
    parser.add_argument("--bootstrap-cache", type=Path, help="Optional cache from this exact Paper build; no plugin files are copied")
    parser.add_argument("--allow-local-dirty", action="store_true", help="Explicit disposable proof of local uncommitted changes; forbidden in CI")
    args = parser.parse_args()
    for name in ("candidate", "probe", "agent"):
        setattr(args, name, getattr(args, name).resolve())
    if args.minecraft != "26.3":
        require(args.protocol_module is not None and args.protocol_module.is_dir(), "Pinned real-player protocol module required")
        args.protocol_module = args.protocol_module.resolve()
    work = args.workdir.resolve()
    require(work.is_relative_to(ROOT / "build") and work != ROOT / "build" and not work.exists(),
            "Use a new disposable directory below this checkout's build directory")
    metadata = json.loads(args.metadata.read_text())
    checkout = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    github_sha = os.environ.get("GITHUB_SHA", "") if os.environ.get("GITHUB_ACTIONS") == "true" else None
    validate_source(metadata, checkout, github_sha, args.allow_local_dirty)
    for key in ("candidate", "probe", "agent"):
        require(metadata.get(key + "_sha256") == digest(getattr(args, key)), f"Wrong exact {key} artifact")
        setattr(args, key + "_sha256", metadata[key + "_sha256"])
    with zipfile.ZipFile(args.candidate) as jar:
        descriptor = jar.read("plugin.yml").decode()
    version_match = re.search(r"(?m)^version:\s*['\"]?([0-9.]+)['\"]?\s*$", descriptor)
    require(version_match is not None and version_match.group(1) != "2.1.72", "Candidate is not the repaired version")
    version = version_match.group(1)
    require(metadata.get("version") == version, "Candidate metadata version differs from the actual descriptor")
    work.mkdir(parents=True)
    report = work / "evidence.json"
    evidence = {"status": "FAIL", "minecraft": args.minecraft, "candidate": metadata, "version": version,
                "core_sha256": CORE_SHA, "control_sha256": CONTROL_SHA, "phases": []}
    try:
        sources = {key: work / (key + ".jar") for key in ("core", "control", "paper")}
        download(CORE_URL, sources["core"], CORE_SHA, args.cached_core)
        download(CONTROL_URL, sources["control"], CONTROL_SHA, args.cached_control)
        request = urllib.request.Request(f"https://fill.papermc.io/v3/projects/paper/versions/{args.minecraft}/builds",
                                         headers={"User-Agent": USER_AGENT})
        with urllib.request.urlopen(request, timeout=60) as response:
            builds = json.load(response)
        channels = {"STABLE", "BETA"} if args.minecraft == "26.3" else {"STABLE"}
        eligible = [build for build in builds if build.get("channel") in channels]
        require(eligible, "No permitted Paper build for this exact Minecraft version")
        stable = [build for build in eligible if build["channel"] == "STABLE"]
        selected = max(stable or eligible, key=lambda build: build["id"])
        asset = selected["downloads"]["server:default"]
        download(asset["url"], sources["paper"], asset["checksums"]["sha256"], args.cached_server)
        evidence["paper"] = {"build": selected["id"], "channel": selected["channel"], "sha256": digest(sources["paper"])}
        for phase in PHASES:
            evidence["phases"].append(run_phase(args, work, phase, sources, version))
            report.write_text(json.dumps(evidence, indent=2) + "\n")
        evidence["status"] = "PASS"
    finally:
        report.write_text(json.dumps(evidence, indent=2) + "\n")
    print(report.read_text())


if __name__ == "__main__":
    main()
