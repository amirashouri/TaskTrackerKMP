#!/usr/bin/env python3
"""Print one compact report from the JUnit XML left by a test run.

Gradle's default test logging prints a full stack trace per failure, which is useful when
debugging one test and unreadable when a host has thirty. This script reads the same XML
and prints one line per failure, plus a per-host table, and writes the same information to
`build/reports/test-summary.md`.

It is intentionally not the policy gate: `scripts/check-baseline.sh` decides whether a
failure is known. This script only makes the result readable.

  scripts/test-summary.py
  scripts/test-summary.py --hosts jvmTest --known test-known-failures.json
"""
from __future__ import annotations

import argparse
import glob
import json
import os
import sys
import xml.etree.ElementTree as ET
from datetime import datetime, timezone

HOSTS = ("jvmTest", "iosSimulatorArm64Test", "testAndroidHostTest")

CLASS_PREFIXES = ("jvmTest.", "iosSimulatorArm64Test.", "testAndroidHostTest.")

HOST_SUFFIXES = {
    "jvmTest": "[jvm]",
    "iosSimulatorArm64Test": "[iosSimulatorArm64]",
    "testAndroidHostTest": None,
}


def normalise(host: str, classname: str, name: str) -> str:
    for prefix in CLASS_PREFIXES:
        if classname.startswith(prefix):
            classname = classname[len(prefix):]
            break

    suffix = HOST_SUFFIXES.get(host)
    if suffix and name.endswith(suffix):
        name = name[:-len(suffix)]

    return f"{classname}#{name}"


def first_line(element: ET.Element) -> str:
    text = element.get("message") or element.text or ""
    for line in text.splitlines():
        line = line.strip()
        if line:
            return line[:200]
    return "(no message)"


def read_host(host: str) -> dict:
    row = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0, "failures_detail": []}
    paths = glob.glob(f"**/build/test-results/{host}/TEST-*.xml", recursive=True)

    for path in paths:
        root = ET.parse(path).getroot()
        for key in ("tests", "failures", "errors", "skipped"):
            row[key] += int(root.get(key) or 0)

        for case in root.iter("testcase"):
            bad = [child for child in case if child.tag in ("failure", "error")]
            if not bad:
                continue
            row["failures_detail"].append(
                {
                    "id": normalise(
                        host,
                        case.get("classname") or "",
                        case.get("name") or "",
                    ),
                    "kind": bad[0].tag,
                    "message": first_line(bad[0]),
                }
            )

    row["failures_detail"].sort(key=lambda failure: failure["id"])
    row["resultFiles"] = len(paths)
    return row


def selected_hosts(raw: str | None) -> list[str]:
    if not raw:
        return list(HOSTS)
    hosts = [part.strip() for part in raw.split(",") if part.strip()]
    unknown = [host for host in hosts if host not in HOSTS]
    if unknown:
        raise SystemExit(
            f"unknown host(s): {', '.join(unknown)} (use {', '.join(HOSTS)})"
        )
    return hosts


def load_known(path: str | None) -> dict:
    if not path or not os.path.isfile(path):
        return {host: {} for host in HOSTS}
    with open(path) as handle:
        document = json.load(handle)
    hosts = document.get("hosts", {})
    return {host: dict(hosts.get(host, {})) for host in HOSTS}


def markdown_table(rows: dict) -> list[str]:
    lines = [
        "| Host | Tests | Failed | Errors | Skipped |",
        "| --- | ---: | ---: | ---: | ---: |",
    ]
    for host, row in rows.items():
        lines.append(
            f"| `{host}` | {row['tests']} | {row['failures']} | "
            f"{row['errors']} | {row['skipped']} |"
        )
    return lines


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--hosts", default=None)
    parser.add_argument("--known", default="test-known-failures.json")
    parser.add_argument("--out", default="build/reports/test-summary.md")
    args = parser.parse_args()

    hosts = selected_hosts(args.hosts)
    known = load_known(args.known)
    rows = {host: read_host(host) for host in hosts}
    failures = [
        (host, failure)
        for host, row in rows.items()
        for failure in row["failures_detail"]
    ]

    print("Test summary")
    print(f"{'host':<26} {'tests':>6} {'failed':>7} {'errors':>7} {'skipped':>8}")
    for host, row in rows.items():
        print(
            f"{host:<26} {row['tests']:>6} {row['failures']:>7} "
            f"{row['errors']:>7} {row['skipped']:>8}"
        )

    if failures:
        print("\nFailures")
        for host, failure in failures:
            status = "known" if failure["id"] in known.get(host, {}) else "NEW"
            print(
                f"[{status:<5}] {host:<24} {failure['id']}  "
                f"{failure['message']}"
            )
    else:
        print("\nNo failures.")

    missing = [host for host, row in rows.items() if row["resultFiles"] == 0]
    if missing:
        print("\nNo result files for: " + ", ".join(missing))

    lines = [
        "# Test summary",
        "",
        f"Generated {datetime.now(timezone.utc).strftime('%Y-%m-%d %H:%M:%SZ')}.",
        "",
        *markdown_table(rows),
        "",
        "## Failures",
        "",
    ]
    if failures:
        lines += [
            "| Status | Host | Test | Message |",
            "| --- | --- | --- | --- |",
        ]
        for host, failure in failures:
            status = "known" if failure["id"] in known.get(host, {}) else "NEW"
            message = failure["message"].replace("|", "\\|")
            lines.append(
                f"| {status} | `{host}` | `{failure['id']}` | {message} |"
            )
    else:
        lines.append("No failures.")

    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    with open(args.out, "w") as handle:
        handle.write("\n".join(lines) + "\n")

    print(f"\nwrote {args.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
