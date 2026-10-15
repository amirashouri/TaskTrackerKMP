#!/usr/bin/env bash
#
# Fail when the committed test-baseline.json no longer matches the JUnit XML on disk.
#
# This is the ratchet behind the "counts come from the build" rule: a test addition
# cannot silently leave the baseline behind, because the numbers in it are compared
# against the artifact every run. Regenerate with scripts/test-baseline.py.
#
#   scripts/check.sh all && scripts/check-baseline.sh
set -euo pipefail

cd "$(dirname "$0")/.."

if [ ! -f test-baseline.json ]; then
  echo "test-baseline.json is missing — generate it with scripts/test-baseline.py" >&2
  exit 2
fi

tmp="$(mktemp -t test-baseline.XXXXXX)"
trap 'rm -f "$tmp"' EXIT

python3 scripts/test-baseline.py --out "$tmp" >/dev/null

if diff -u test-baseline.json "$tmp"; then
  echo "test-baseline.json matches the JUnit XML on disk."
else
  echo >&2
  echo "test-baseline.json is stale. Regenerate it with scripts/test-baseline.py" >&2
  echo "and commit the result in the same change as the test." >&2
  exit 1
fi
