#!/usr/bin/env bash
#
# Fail when the committed baseline or the known-failure list no longer describes the run.
#
# This is the ratchet behind the "counts and names come from the build" rule. It checks
# three things for every host that ran:
#
#   1. the committed totals match the fresh JUnit XML;
#   2. every failing test is named in test-known-failures.json;
#   3. every named known failure still fails, so a fixed test cannot leave a stale excuse
#      behind.
#
# A failure count alone is not enough: fixing one test and breaking another leaves the
# count unchanged, and that is exactly the regression the old baseline could not see.
#
#   scripts/check.sh full && scripts/check-baseline.sh
#   scripts/check-baseline.sh --hosts jvmTest
set -euo pipefail

cd "$(dirname "$0")/.."

hosts="jvmTest,iosSimulatorArm64Test,testAndroidHostTest"
while [ "$#" -gt 0 ]; do
  case "$1" in
    --hosts)
      hosts="${2:?--hosts needs a comma-separated value}"
      shift 2
      ;;
    --hosts=*)
      hosts="${1#--hosts=}"
      shift
      ;;
    *)
      echo "unknown argument: $1 (use --hosts <host,host>)" >&2
      exit 2
      ;;
  esac
done

for file in test-baseline.json test-known-failures.json; do
  if [ ! -f "$file" ]; then
    echo "$file is missing; run scripts/check.sh full and regenerate the baseline." >&2
    exit 2
  fi
done

tmp="$(mktemp -t test-baseline.XXXXXX)"
trap 'rm -f "$tmp"' EXIT

if ! python3 scripts/test-baseline.py --out "$tmp" --hosts "$hosts" >/dev/null; then
  echo >&2
  echo "Could not measure the selected hosts. Run the test host first." >&2
  exit 1
fi

python3 - "$tmp" "$hosts" <<'PY'
from __future__ import annotations

import json
import sys

actual_path, raw_hosts = sys.argv[1], sys.argv[2]
hosts = [host.strip() for host in raw_hosts.split(",") if host.strip()]

with open(actual_path) as handle:
    actual = json.load(handle)["hosts"]
with open("test-baseline.json") as handle:
    committed = json.load(handle)["hosts"]
with open("test-known-failures.json") as handle:
    known = json.load(handle).get("hosts", {})

problems: list[str] = []

for host in hosts:
    measured = actual.get(host)
    baseline = committed.get(host)

    if measured is None:
        problems.append(f"{host}: the run produced no results")
        continue
    if measured["tests"] == 0:
        problems.append(f"{host}: the run produced zero tests")
        continue
    if baseline is None:
        problems.append(f"{host}: no entry in test-baseline.json")
        continue

    for key in ("tests", "failures", "errors", "skipped"):
        if baseline.get(key) != measured.get(key):
            problems.append(
                f"{host}: {key} is {baseline.get(key)} in test-baseline.json "
                f"but {measured.get(key)} in the run; regenerate with "
                "scripts/test-baseline.py after scripts/check.sh full"
            )

    failed = set(measured.get("failed", []))
    if len(failed) != measured["failures"] + measured["errors"]:
        problems.append(
            f"{host}: {len(failed)} failing test names for "
            f"{measured['failures']} failures + {measured['errors']} errors"
        )

    if measured["errors"]:
        problems.append(
            f"{host}: {measured['errors']} test errors; errors are not allowlisted"
        )

    expected = set(known.get(host, {}))
    unexpected = sorted(failed - expected)
    stale = sorted(expected - failed)

    for test in unexpected:
        problems.append(f"{host}: unexpected failure: {test}")
    for test in stale:
        problems.append(
            f"{host}: known failure now passes, remove it from "
            f"test-known-failures.json: {test}"
        )

if problems:
    print("Test baseline check failed:", file=sys.stderr)
    for problem in problems:
        print(f"  - {problem}", file=sys.stderr)
    raise SystemExit(1)

for host in hosts:
    row = actual[host]
    count = len(row.get("failed", []))
    if count:
        print(f"{host}: {row['tests']} tests, {count} known failure(s)")
    else:
        print(f"{host}: {row['tests']} tests, 0 failures")
PY
