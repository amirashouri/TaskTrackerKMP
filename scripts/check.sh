#!/usr/bin/env bash
#
# The one place the validation command lives.
#
# `AGENTS.md`, `Project.md` and `README.md` used to each spell out the JAVA_HOME
# export and the five-task Gradle line, so the three could (and did) drift. This
# script is the contract: the docs describe scopes, this file says what they run.
#
# Usage:
#   scripts/check.sh            # the deciding command (three hosts + the graph checks)
#   scripts/check.sh jvm        # jvmTest only                      (fast feedback)
#   scripts/check.sh ios        # iosSimulatorArm64Test only        (rendered pixels)
#   scripts/check.sh android    # testAndroidHostTest only          (Robolectric; red, below)
#   scripts/check.sh graph      # verifyArchitectureGraph verifyModuleImports :androidApp:compileDebugKotlin
#   scripts/check.sh docs       # a docs-only change: nothing to build
#   scripts/check.sh lint       # :androidApp:lintDebug             (red for a known reason)
#
# `android` and `lint` are red for documented, pre-existing reasons — see AGENTS.md
# ("Test hosts"). They are here so the red is reproducible, not so it gets fixed.
set -euo pipefail

cd "$(dirname "$0")/.."

# The build needs JDK 25, and `java_home` cannot find this machine's copy.
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"

DECIDING="jvmTest iosSimulatorArm64Test verifyArchitectureGraph verifyModuleImports :androidApp:compileDebugKotlin"

scope="${1:-all}"
case "$scope" in
  all)     exec ./gradlew $DECIDING ;;
  jvm)     exec ./gradlew jvmTest ;;
  ios)     exec ./gradlew iosSimulatorArm64Test ;;
  android) exec ./gradlew testAndroidHostTest ;;
  graph)   exec ./gradlew verifyArchitectureGraph verifyModuleImports :androidApp:compileDebugKotlin ;;
  lint)    exec ./gradlew :androidApp:lintDebug ;;
  docs)    echo "docs-only change: nothing to compile or test."; exit 0 ;;
  *)       echo "unknown scope: '$scope' (use all|jvm|ios|android|graph|lint|docs)" >&2; exit 2 ;;
esac
