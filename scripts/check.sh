#!/usr/bin/env bash
#
# The one place the validation command lives.
#
# `AGENTS.md`, `Project.md` and `README.md` used to each spell out the JAVA_HOME
# export and the five-task Gradle line, so the three could (and did) drift. This
# script is the contract: the docs describe scopes, this file says what they run.
#
# Usage:
#   scripts/check.sh                    # fast default: JVM + graph + baseline
#   scripts/check.sh fast               # same as the default
#   scripts/check.sh fast packages:core # JVM tests for one module
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

GRAPH_TASKS=(
  verifyArchitectureGraph
  verifyModuleImports
  :androidApp:compileDebugKotlin
)

scope="${1:-default}"
hosts=""
tasks=()

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
      tasks=(jvmTest "${GRAPH_TASKS[@]}")
    fi
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
    echo "use: default|fast|full|all|jvm|ios|android|graph|lint|docs" >&2
    exit 2
    ;;
esac

# A result is evidence only when it came from this run. Removing the old XML also
# makes a deleted test class disappear from the baseline instead of surviving as a
# stale file that still compares equal.
if [ -n "$hosts" ]; then
  IFS=',' read -r -a host_array <<< "$hosts"
  for host in "${host_array[@]}"; do
    find . -type d -path "*/build/test-results/$host" -prune -exec rm -rf {} + 2>/dev/null || true
  done
  unset IFS
fi

log="$(mktemp -t tasktracker-check.XXXXXX)"
trap 'rm -f "$log"' EXIT

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
