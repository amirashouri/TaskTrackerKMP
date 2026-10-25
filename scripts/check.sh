#!/usr/bin/env bash
#
# The one place the release validation command lives.
#
# Development runs focused unit tests, not this script: one class or package per
# touched module via `./gradlew :packages:<module>:jvmTest --tests <pattern>`.
# See `AGENTS.md` → "Verification" and `docs/TESTING.md`; this file is the gate a
# release is cut against.
#
# `AGENTS.md`, `Project.md` and `README.md` used to each spell out the JAVA_HOME
# export and the five-task Gradle line, so the three could (and did) drift. This
# script is the contract: the docs describe scopes, this file says what they run.
#
# Usage:
#   scripts/check.sh                    # gate: JVM + architecture + baseline
#   scripts/check.sh fast               # same as the default
#   scripts/check.sh fast packages:core # JVM tests for one module (still a gate)
#   scripts/check.sh quick shared-ui 'com.tasktracker.sharedui.*Sidebar*'
#                                       # one module, filtered, iteration only
#   scripts/check.sh full               # all three hosts + graph + baseline
#   scripts/check.sh all                # alias for full
#   scripts/check.sh jvm                # jvmTest only
#   scripts/check.sh ios                # iosSimulatorArm64Test only
#   scripts/check.sh android            # testAndroidHostTest only
#   scripts/check.sh graph              # architecture checks + Android compile
#   scripts/check.sh docs               # a docs-only change: nothing to build
#   scripts/check.sh lint               # :androidApp:lintDebug
#
# A red test result is only a failure when the test is not in
# test-known-failures.json. `check-baseline.sh` makes that distinction; this script
# returns green for an allowlisted failure and red for a new one.
#
# `quick` is the one scope that is not a gate: it keeps the previous result XML (so
# Gradle can skip unchanged tests), applies `--tests` filters, and skips the baseline
# and known-failure steps. Use it while editing; run `fast` before pushing.
set -uo pipefail

cd "$(dirname "$0")/.." || exit 1

# The build needs JDK 25, and `java_home` cannot find this machine's copy.
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"

# A hosted runner is a single-use VM, so a daemon is started only to be discarded, and
# its orchestration competes with the build for the same cores. Measured on run
# 39042728812, before these flags: JVM 6m54s, Android 6m01s, iOS 34m07s. The three
# Gradle flags repeat the committed gradle.properties deliberately, so the settings
# survive a CI checkout that changes that file. Local runs keep the daemon.
if [ "${GITHUB_ACTIONS:-false}" = "true" ]; then
  export GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.parallel=true -Dorg.gradle.caching=true -Dorg.gradle.configuration-cache=true"
fi

# The two cheap checks every gate can afford. They read the build scripts, not the
# code, so they are minutes cheaper than an Android compile and still catch the
# mistakes that no test can see: a new dependency edge, a module import that crosses
# a layer boundary.
ARCH_TASKS=(
  verifyArchitectureGraph
  verifyModuleImports
)

# Room's KSP pass and Android resource compilation. The heaviest non-test task in the
# build, and the reason `fast` used to take as long as it did; it belongs in `graph`
# (where a dependency change is already the thing being checked) and in `full`.
ANDROID_COMPILE_TASKS=(
  :androidApp:compileDebugKotlin
)

GRAPH_TASKS=(
  "${ARCH_TASKS[@]}"
  "${ANDROID_COMPILE_TASKS[@]}"
)

scope="${1:-default}"
hosts=""
tasks=()
# Gates prune stale XML; `quick` keeps it so Gradle can report up-to-date. See the
# deletion below for why pruning exists and why it must not run on every edit.
prune_results=1

module_task() {
  case "$1" in
    :*) printf '%s:jvmTest' "$1" ;;
    *) printf ':%s:jvmTest' "$1" ;;
  esac
}

case "$scope" in
  default|fast)
    hosts="jvmTest"
    if [ "$#" -gt 1 ]; then
      shift
      for module in "$@"; do
        tasks+=("$(module_task "$module")")
      done
    else
      tasks=(jvmTest "${ARCH_TASKS[@]}")
    fi
    ;;
  quick)
    if [ "$#" -lt 2 ]; then
      echo "quick needs a module: scripts/check.sh quick <module> [test-filter...]" >&2
      exit 2
    fi
    tasks=("$(module_task "$2")")
    shift 2
    # Every remaining argument becomes a `--tests` filter. Repeated filters are a
    # union in Gradle, so one invocation can cover, for example, both new scenarios
    # and the class they live in.
    for pattern in "$@"; do
      tasks+=(--tests "$pattern")
    done
    # Iteration only: no known-failure summary and no baseline, because both measure
    # whole-host totals and a filtered run would report every unrun test as missing.
    hosts=""
    prune_results=0
    ;;
  full|all)
    hosts="jvmTest,iosSimulatorArm64Test,testAndroidHostTest"
    tasks=(
      jvmTest
      iosSimulatorArm64Test
      testAndroidHostTest
      "${GRAPH_TASKS[@]}"
    )
    ;;
  jvm)
    hosts="jvmTest"
    tasks=(jvmTest)
    ;;
  ios)
    hosts="iosSimulatorArm64Test"
    tasks=(iosSimulatorArm64Test)
    ;;
  android)
    hosts="testAndroidHostTest"
    tasks=(testAndroidHostTest)
    ;;
  graph)
    tasks=("${GRAPH_TASKS[@]}")
    ;;
  lint)
    tasks=(:androidApp:lintDebug)
    ;;
  docs)
    echo "docs-only change: nothing to compile or test."
    exit 0
    ;;
  *)
    echo "unknown scope: '$scope'" >&2
    echo "use: default|fast|quick <module> [filter...]|full|all|jvm|ios|android|graph|lint|docs" >&2
    exit 2
    ;;
esac

# A result is evidence only when it came from this run. Removing the old XML also
# makes a deleted test class disappear from the baseline instead of surviving as a
# stale file that still compares equal.
#
# This is a *gate* step, and deliberately not an every-edit step: deleting the output
# makes Gradle's test task out of date, so the next `check.sh` re-runs the whole suite
# instead of reporting UP-TO-DATE. `quick` skips it and gives up the freshness
# guarantee in exchange for a run that finishes in seconds.
if [ "$prune_results" -eq 1 ] && [ -n "$hosts" ]; then
  IFS=',' read -r -a host_array <<< "$hosts"
  for host in "${host_array[@]}"; do
    find . -type d -path "*/build/test-results/$host" -prune -exec rm -rf {} + 2>/dev/null || true
  done
  unset IFS
fi

log="$(mktemp -t tasktracker-check.XXXXXX)"
trap 'rm -f "$log"' EXIT

if [ "$scope" = "quick" ]; then
  echo "==> quick: iteration only — no baseline check, no known-failure summary."
  echo "    The results directory is kept so unchanged tests can be skipped."
  echo "    Release gate, not a per-task check: scripts/check.sh full"
fi

echo "==> ./gradlew --continue ${tasks[*]}"
gradle_status=0
./gradlew --continue "${tasks[@]}" 2>&1 | tee "$log" || gradle_status=${PIPESTATUS[0]}

failed_tasks=""
if [ -s "$log" ]; then
  failed_tasks="$(grep -E '^> Task .* FAILED$' "$log" | sed -E 's/^> Task (.*) FAILED$/\1/' || true)"
fi

non_test_failed=""
if [ -n "$failed_tasks" ]; then
  non_test_failed="$(printf '%s\n' "$failed_tasks" | grep -vE '(jvmTest|iosSimulatorArm64Test|iosArm64Test|testAndroidHostTest)$' || true)"
fi

gate_status=0
if [ "$gradle_status" -ne 0 ]; then
  if [ -z "$failed_tasks" ]; then
    echo >&2
    echo "Gradle failed without naming a failed task; treating this as a build failure." >&2
    gate_status=1
  elif [ -n "$non_test_failed" ]; then
    echo >&2
    echo "Non-test task(s) failed:" >&2
    printf '  %s\n' $non_test_failed >&2
    gate_status=1
  elif [ -z "$hosts" ]; then
    gate_status=1
  fi
fi

if [ -n "$hosts" ]; then
  python3 scripts/test-summary.py \
    --hosts "$hosts" \
    --known test-known-failures.json || gate_status=1

  if ! scripts/check-baseline.sh --hosts "$hosts"; then
    gate_status=1
  fi
fi

if [ "$gate_status" -eq 0 ] && [ "$gradle_status" -ne 0 ]; then
  echo "Only known test failures; the gate is green."
fi

exit "$gate_status"
