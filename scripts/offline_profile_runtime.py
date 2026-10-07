#!/usr/bin/env python3
"""Real-server, full-bundle proof for JEG's offline profile listener. CI only.

The published control must reproduce its known listener failure. The candidate must
install and retain JEGGuideHistory through repeated async events and a profile reload.
This does not modify releases or run against a supplied server/world.
"""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import time
import urllib.request
import uuid
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TOOLS_SOURCE = "97afb3b74668775f1b36386d48a727cf00b8faec"
LISTENER = "com/balugaq/jeg/core/listeners/GuideHistoryPatchListener.class"
EVENT_ERROR = "Could not pass event AsyncProfileLoadEvent to JustEnoughGuide v2.1.71"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def checksum(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def parse_result(text: str) -> dict[str, str]:
    rows = [line.split("=", 1) for line in text.splitlines() if "=" in line]
    require(len(rows) == len({row[0] for row in rows}), "Duplicate result field")
    return dict(rows)


def validate_phase(phase: str, result: dict[str, str], log: str) -> None:
    require(phase in {"control", "candidate-create", "candidate-reload"}, "Unknown result phase")
    require(result.get("phase") == phase and result.get("addons_enabled") == "45", "Wrong phase/addon evidence")
    require("JEG_OFFLINE_PROBE_FAIL" not in log, "Probe assertion failed")
    require(not re.search(r"Error occurred while enabling|NoClassDefFoundError|NoSuchMethodError|AbstractMethodError|IncompatibleClassChangeError", log),
            "Startup or linkage failure")
    event_errors = len(re.findall(r"Could not pass event", log))
    if phase == "control":
        require(result.get("status") == "CONTROL_REPRODUCED" and result.get("events") == "1"
                and result.get("patched") == "false", "Published control did not reproduce the callback failure")
        require(event_errors == 1 and log.count(EVENT_ERROR) == 1, "Control needs exactly its known JEG event failure")
        require("java.lang.NullPointerException" in log and "GuideHistoryPatchListener.onProfileLoad" in log,
                "Control failure is not the expected listener null-player exception")
    else:
        require(result.get("status") == "PASS" and result.get("events") == "3"
                and result.get("patched") == "true" and result.get("history_preserved") == "true",
                "Candidate did not complete guide-history installation and repeat-event preservation")
        require(event_errors == 0 and "NullPointerException" not in log, "Candidate still has a runtime exception")
    severe = [line for line in log.splitlines() if re.search(r"(?:ERROR|SEVERE)\]", line)]
    require(len(severe) == (1 if phase == "control" else 0), "Unexpected server error log")
    if phase == "control":
        require(EVENT_ERROR in severe[0], "Unexpected control error log")
    else:
        require("Previous clean shutdown: Yes" in log or re.search(r"previous shutdown\s+Clean", log),
                "Missing previous clean-shutdown evidence")


def read_candidate(jar: Path, metadata: Path, expected_source: str) -> tuple[str, bytes]:
    proof = json.loads(metadata.read_text())
    require(re.fullmatch(r"[0-9a-f]{40}", expected_source) is not None, "Missing exact candidate source")
    require(proof.get("source") == expected_source and proof.get("sha256") == checksum(jar), "Candidate provenance mismatch")
    with zipfile.ZipFile(jar) as archive:
        require(archive.testzip() is None, "Candidate CRC failure")
        descriptor = archive.read("plugin.yml").decode()
        require(re.search(r"(?m)^name:\s*['\"]?JustEnoughGuide['\"]?\s*$", descriptor), "Wrong candidate plugin")
        match = re.search(r"(?m)^version:\s*['\"]?(\d+(?:\.\d+)+)['\"]?\s*$", descriptor)
        require(match is not None, "Missing numeric candidate version")
        return match.group(1), archive.read(LISTENER)


def run_phase(server: Path, phase: str) -> dict[str, str]:
    result = server / f"probe-{phase}.txt"
    require(not result.exists(), "Stale probe evidence")
    name = "JEGControlProbe" if phase == "control" else "JEGPatchedProbe"
    owner = str(uuid.UUID(bytes=hashlib.md5(("OfflinePlayer:" + name).encode()).digest(), version=3))
    (server / "probe-phase.txt").write_text(phase)
    (server / "probe-owner.txt").write_text(owner)
    log_path = server.parent / f"{phase}.console.log"
    with log_path.open("w") as log:
        process = subprocess.Popen(["java", "-Xms512M", "-Xmx3G", "-Djeg.offline.probe=disposable-only", "-jar", "server.jar", "--nogui"],
                                   cwd=server, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT, text=True)
        try:
            deadline = time.monotonic() + 420
            while "Done (" not in log_path.read_text(errors="replace"):
                require(process.poll() is None, "Server exited before probe")
                if time.monotonic() > deadline:
                    raise TimeoutError("Server startup timed out")
                time.sleep(1)
            time.sleep(10)
            process.stdin.write(f"offlineprobe {phase}\n")
            process.stdin.flush()
            deadline = time.monotonic() + 120
            while not result.exists():
                require(process.poll() is None, "Server exited during probe")
                if time.monotonic() > deadline:
                    raise TimeoutError("Probe did not produce evidence")
                time.sleep(1)
            process.stdin.write("sf doctor status\nstop\n")
            process.stdin.flush()
            require(process.wait(timeout=90) == 0, "Server shutdown failed")
        finally:
            if process.poll() is None:
                try:
                    process.stdin.write("stop\n")
                    process.stdin.flush()
                    process.wait(timeout=30)
                except (OSError, subprocess.TimeoutExpired):
                    process.terminate()
                    try:
                        process.wait(timeout=10)
                    except subprocess.TimeoutExpired:
                        process.kill()
                        process.wait(timeout=10)
    values = parse_result(result.read_text())
    require(values.get("owner") == owner, "Wrong result owner")
    validate_phase(phase, values, log_path.read_text(errors="replace"))
    return values


def compile_probe(work: Path, core: Path) -> Path:
    project = work / "probe-build"
    resources = project / "resources"
    resources.mkdir(parents=True)
    (resources / "plugin.yml").write_text(
        "name: OfflineProfileProbe\nversion: '1.0.0'\nmain: io.github.wickidcow.jegtest.OfflineProfileProbe\n"
        "api-version: '1.21.11'\ndepend: [Slimefun, JustEnoughGuide]\ncommands:\n  offlineprobe:\n    description: Disposable CI probe only\n")
    (project / "pom.xml").write_text(f'''<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>
<groupId>io.github.wickidcow.test</groupId><artifactId>offline-profile-probe</artifactId><version>1.0.0</version>
<properties><maven.compiler.release>21</maven.compiler.release><project.build.sourceEncoding>UTF-8</project.build.sourceEncoding></properties>
<repositories><repository><id>paper</id><url>https://repo.papermc.io/repository/maven-public/</url></repository></repositories>
<dependencies><dependency><groupId>io.papermc.paper</groupId><artifactId>paper-api</artifactId><version>1.21.11-R0.1-SNAPSHOT</version><scope>provided</scope></dependency>
<dependency><groupId>local.fixture</groupId><artifactId>published-slimefun</artifactId><version>4.1.70</version><scope>system</scope><systemPath>{core}</systemPath></dependency></dependencies>
<build><sourceDirectory>{ROOT / 'tests/offline-profile-runtime'}</sourceDirectory><resources><resource><directory>{resources}</directory></resource></resources>
<plugins><plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-compiler-plugin</artifactId><version>3.14.1</version></plugin></plugins></build></project>''')
    with (work / "compile.log").open("w") as log:
        subprocess.run(["mvn", "--batch-mode", "--no-transfer-progress", "-f", str(project / "pom.xml"), "package"],
                       stdout=log, stderr=subprocess.STDOUT, check=True, timeout=300)
    return project / "target/offline-profile-probe-1.0.0.jar"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--minecraft", required=True, choices=["1.21.11", "26.2", "26.3"])
    parser.add_argument("--work-dir", required=True, type=Path)
    parser.add_argument("--tools", required=True, type=Path)
    parser.add_argument("--candidate", required=True, type=Path)
    parser.add_argument("--candidate-meta", required=True, type=Path)
    args = parser.parse_args()
    require(os.environ.get("GITHUB_ACTIONS") == "true", "Ephemeral GitHub Actions workspace required")
    workspace = Path(os.environ["GITHUB_WORKSPACE"]).resolve()
    work = args.work_dir.resolve()
    require(work.is_relative_to(workspace / "build") and work != workspace / "build" and not work.exists(),
            "Refusing existing or non-disposable work directory")
    tools = args.tools.resolve()
    require(subprocess.check_output(["git", "-C", str(tools), "rev-parse", "HEAD"], text=True).strip() == TOOLS_SOURCE,
            "Legacy fixture tooling source moved")
    spec = importlib.util.spec_from_file_location("legacy_upgrade", tools / "scripts/published_upgrade_smoke.py")
    helper = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(helper)
    source = os.environ["GITHUB_SHA"]
    version, candidate_listener = read_candidate(args.candidate, args.candidate_meta, source)
    work.mkdir(parents=True)
    server = work / "server"
    plugins = server / "plugins"
    plugins.mkdir(parents=True)
    evidence = {"status": "INCOMPLETE", "minecraft": args.minecraft, "candidate_source": source,
                "candidate_sha256": checksum(args.candidate), "candidate_version": version, "phases": [],
                "scope": "published 4.1.70 full bundle; only JEG replaced after negative control"}
    report = work / "runtime-evidence.json"
    try:
        assets = {}
        for key in ("new-core", "new-addons"):
            tag, name, sha, size = helper.ASSETS[key]
            destination = work / name
            helper.download(f"https://github.com/wickidcow/Slimefun-Legacy/releases/download/{tag}/{name}", destination, sha, size)
            assets[key] = destination
        manifest, jars, plugin_rows = helper.inspect_bundle(assets["new-addons"], helper.SOURCE)
        with zipfile.ZipFile(assets["new-core"]) as core:
            require("git.source.commit=" + helper.SOURCE in core.read("git.properties").decode(), "Wrong published core source")
        jeg_name = next(row["jar"] for row in manifest["addons"] if row["repository"] == "wickidcow/SF_JustEnoughGuide")
        with zipfile.ZipFile(io.BytesIO(jars[jeg_name])) as control:
            control_listener = control.read(LISTENER)
        require(control_listener != candidate_listener, "Candidate contains the published listener; no fix under test")
        evidence["published_core_source"] = helper.SOURCE
        evidence["bundle_revision"] = manifest["bundle_revision"]
        evidence["published_jeg_sha256"] = hashlib.sha256(jars[jeg_name]).hexdigest()
        evidence["listener_sha256"] = {"control": hashlib.sha256(control_listener).hexdigest(),
                                       "candidate": hashlib.sha256(candidate_listener).hexdigest()}
        for name, contents in jars.items():
            (plugins / name).write_bytes(contents)
        shutil.copy2(assets["new-core"], plugins / "Slimefun.jar")
        shutil.copy2(compile_probe(work, assets["new-core"]), plugins / "OfflineProfileProbe.jar")
        request = urllib.request.Request(f"https://fill.papermc.io/v3/projects/paper/versions/{args.minecraft}/builds",
                                         headers={"User-Agent": helper.USER_AGENT})
        with urllib.request.urlopen(request, timeout=45) as response:
            selected = helper.paper_build(args.minecraft, json.load(response))
        asset = selected["downloads"]["server:default"]
        helper.download(asset["url"], server / "server.jar", asset["checksums"]["sha256"], asset.get("size"))
        evidence["paper"] = {"build": selected["id"], "channel": selected["channel"], "sha256": checksum(server / "server.jar")}
        provider_flags = ["--allow-prerelease"] if args.minecraft == "26.3" else []
        subprocess.run(["python3", str(tools / "scripts/prepare_worldedit_runtime.py"), "--minecraft", args.minecraft,
                        "--output", str(plugins), *provider_flags], check=True, timeout=180)
        (server / "eula.txt").write_text("eula=true\n")
        (server / "server.properties").write_text(
            "server-ip=127.0.0.1\nonline-mode=false\nlevel-name=jeg-offline-fixture\nmax-players=1\n"
            "spawn-protection=0\nview-distance=2\nsimulation-distance=2\npause-when-empty-seconds=-1\n"
            "enable-query=false\nenable-rcon=false\nallow-nether=false\n")
        (plugins / "Slimefun").mkdir(exist_ok=True)
        (plugins / "Slimefun/config.yml").write_text("options:\n  auto-update: false\n  language: en\n  enable-translations: false\n")
        (server / "expected-plugins.tsv").write_text(plugin_rows)
        evidence["phases"].append(run_phase(server, "control"))
        (plugins / jeg_name).unlink()
        shutil.copy2(args.candidate, plugins / "JEG-under-test.jar")
        candidate_rows = "".join(f"JustEnoughGuide\t{version}\n" if row.startswith("JustEnoughGuide\t") else row + "\n"
                                 for row in plugin_rows.splitlines())
        (server / "expected-plugins.tsv").write_text(candidate_rows)
        for phase in ("candidate-create", "candidate-reload"):
            require(checksum(plugins / "JEG-under-test.jar") == evidence["candidate_sha256"], "Staged JEG candidate changed")
            evidence["phases"].append(run_phase(server, phase))
        evidence["status"] = "PASS"
    finally:
        report.write_text(json.dumps(evidence, indent=2) + "\n")
    print(report.read_text())


if __name__ == "__main__":
    main()
