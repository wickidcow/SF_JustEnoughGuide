#!/usr/bin/env python3
"""Hold automatic publication until the same master commit passes every required JEG gate."""
import argparse
import json
import os
import re
import time
import urllib.parse
import urllib.request

WORKFLOWS = ("build.yml", "offline-profile-listener.yml", "offline-profile-runtime.yml", "pinyin-startup-runtime.yml")


def matching_run(runs: list[dict], head: str) -> dict | None:
    matches = [run for run in runs if run.get("head_sha") == head and run.get("head_branch") == "master"
               and run.get("event") == "push"]
    return max(matches, key=lambda run: run["id"]) if matches else None


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", required=True)
    parser.add_argument("--head", required=True)
    args = parser.parse_args()
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", args.repo) or not re.fullmatch(r"[0-9a-f]{40}", args.head):
        raise SystemExit("Invalid release source identity")
    query = urllib.parse.urlencode({"head_sha": args.head, "event": "push", "per_page": 30})
    deadline = time.monotonic() + 65 * 60
    while time.monotonic() < deadline:
        passed = []
        pending = []
        for workflow in WORKFLOWS:
            url = f"https://api.github.com/repos/{args.repo}/actions/workflows/{workflow}/runs?{query}"
            request = urllib.request.Request(url, headers={"Accept": "application/vnd.github+json",
                                             "Authorization": "Bearer " + os.environ["GH_TOKEN"],
                                             "User-Agent": "SF_JustEnoughGuide-release-gate"})
            with urllib.request.urlopen(request, timeout=60) as response:
                run = matching_run(json.load(response)["workflow_runs"], args.head)
            if run is not None and run.get("status") == "completed":
                if run.get("conclusion") != "success":
                    raise SystemExit(f"Required JEG gate did not pass: {run['html_url']} ({run.get('conclusion')})")
                passed.append(run["html_url"])
            else:
                pending.append(workflow)
        if not pending:
            print("All exact-master JEG release gates passed:\n" + "\n".join(passed), flush=True)
            return
        print(f"Waiting for {', '.join(pending)} at {args.head}", flush=True)
        time.sleep(30)
    raise SystemExit("Timed out waiting for the exact-master JEG release gates; nothing was published")


if __name__ == "__main__":
    main()
