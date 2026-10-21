# desktop-app-version — report the Desktop build's version in Settings

- **status:** done
- **priority:** P3
- **plan:** none (standalone; no `docs/PLAN.md` entry). The version row itself already
  shipped: `SettingsScreen` renders `settings_version` from `SettingsPort.appVersion()`, and
  Android (`PackageManager`) and iOS (`NSBundle`) report a real value. The gap is Desktop,
  whose `actual` deliberately returns blanks.

## Scope

`:composition-root` only — the JVM `actual`, its build script, and its `jvmTest`. No
`feature-settings-*` or `shared-ui` change: the row, the `AppVersion` type, the port method,
the graph binding, and the Settings wiring already exist and are tested.

## Files

- `composition-root/build.gradle.kts` — stamp the version onto the `jvmJar` manifest as
  `Implementation-Version`, read from the same `-PtasktrackerVersion` property the release
  workflow already passes to `:shared-ui:packageDmg` / `packageDeb` / `packageMsi`. When
  the property is absent, stamp the declared project version (`1.0.0`), which is what
  Android's `versionName` and iOS's `MARKETING_VERSION` already report for an unreleased
  build.
- `composition-root/src/jvmMain/kotlin/com/tasktracker/compositionroot/PlatformAppVersion.jvm.kt`
  — read the value the class's own jar manifest carries (`Package.implementationVersion`),
  mapped through a small internal function so the formatting is testable. A
  classes-directory host (tests, a plain IDE run) has no manifest and still answers blank,
  which keeps the row hidden rather than inventing a number.
- `composition-root/src/jvmTest/kotlin/com/tasktracker/compositionroot/PlatformAppVersionJvmTest.kt`
  *(new)* — the mapping: a version renders, blanks/whitespace/absence hide the row.
- `specs/desktop-app-version.md` — this file, kept current through the handoff.

## Contract

- [x] A packaged Desktop build reports the release version (`-PtasktrackerVersion=0.1.6`
      → `Version 0.1.6`) in the Settings About row, with no `AppVersion` change and no new
      port method. Verified by building that jar, unzipping the manifest, and calling the
      real `platformAppVersion(null)` against it: `marketing=0.1.6 display=0.1.6`.
- [x] An unstamped JVM build reports the declared project version (`1.0.0`), so Android
      Studio's `hotRunJvm` configuration — which passes no `-P` — shows the row instead of
      hiding it. The jar the hot-run task copies onto its classpath carries
      `Implementation-Version: 1.0.0`; the real `platformAppVersion(null)` against that copy
      returns `marketing=1.0.0 display=1.0.0`.
- [x] A classes-directory host (tests, a plain IDE run) still hides the row:
      `AppVersion.display` is `null`, so `SettingsScreen` renders no empty
      `settings.versionRow`.
- [x] `AppVersion.display` formatting stays the one definition: the JVM actual supplies
      `marketing` only, never a pre-formatted `"0.1.6 (…)"`.
- [x] Reverting the actual to blanks is observable: `PlatformAppVersionJvmTest` fails twice
      with `expected:<0.1.6> but was:<null>`. The manifest stamp itself is build wiring, so
      the unzip check above covers it rather than a test.

## Validation

- Run: `scripts/check.sh jvm composition-root` (the module's JVM tests) and
  `scripts/check.sh` (JVM + architecture + baseline) before the PR; `scripts/check.sh full`
  only if a shared `commonTest` changes — this task adds `jvmTest` only, so the iOS and
  Android host counts do not move.
- Counts: `jvmTest` gains the new cases; `test-baseline.json` is regenerated and
  `scripts/check-baseline.sh` must pass.
- Manual jar check (no UI pixels):
  `./gradlew :composition-root:jvmJar` then
  `unzip -p composition-root/build/libs/composition-root-jvm-1.0.0.jar META-INF/MANIFEST.MF`
  must show `Implementation-Version: 1.0.0`, the project-version default Android Studio's
  hot run picks up. With `-PtasktrackerVersion=0.1.6` the same check must show `0.1.6`
  (the jar is named for the Gradle project version, not the release tag).
- Manual hot-run check: `:shared-ui:hotRunJvm` copies that jar into
  `shared-ui/build/run/jvmMain/classpath/libs/composition-root/…`; unzip the copy's
  manifest to confirm the version travels to the process Android Studio launches.
- Structural/behavioural tests only; the `test-reviewer` subagent is not required.

## Handoff

- **Done:** the JVM `actual` reports the version from this module's jar manifest.
  `composition-root/build.gradle.kts` stamps `Implementation-Version` from
  `-PtasktrackerVersion` when a release passes it, and otherwise from the declared project
  version (`1.0.0`) — the value Android's `versionName` and iOS's `MARKETING_VERSION` report
  for an unreleased build. Android Studio's hot-reload configuration runs
  `:shared-ui:hotRunJvm`, which copies this jar onto its classpath, so the AS run shows the
  row without any per-configuration flag. `PlatformAppVersion.jvm.kt` maps
  `Package.implementationVersion` through `jvmAppVersion`, and
  `PlatformAppVersionJvmTest` pins the mapping plus the hide-when-absent rule.
- **Verified:** `:composition-root:jvmJar` with no `-P` → `Implementation-Version: 1.0.0`;
  with `-PtasktrackerVersion=0.1.7` → `0.1.7`; the hot-run copy under
  `shared-ui/build/run/jvmMain/classpath/libs/composition-root/…` → `1.0.0`; the real
  `platformAppVersion(null)` against that copied classpath → `marketing=1.0.0
  display=1.0.0`. Fast gate green: JVM 1940, 0 failed; `test-baseline.json` unchanged.
- **Pending:** commit, PR, and merge.
- **Next step:** commit the four files and open the PR against `main`.
- **Gotchas:** the release tag is the displayed value in a release (`0.1.6`), not
  `desktopPackageVersion` (`1.1.6`, the macOS-bumped bundle number). A classes-directory
  host (test suite, plain IDE run) has no manifest and hides the row. The jar is named for
  the Gradle project version (`composition-root-jvm-1.0.0.jar`), not the tag. The manifest
  attribute is build wiring no unit test can reach; the mapping test covers formatting, and
  the documented `unzip` / hot-run checks cover the stamp.

## Risks / notes

- The manifest attribute is build wiring no unit test can reach; the mapping test covers the
  formatting, and the `unzip` checks above cover the stamp. State that residual in the
  handoff rather than implying the suite proves the packaged row.
- An unstamped local build now reports the project version (`1.0.0`) instead of hiding the
  row. That matches the version Android's `versionName` and iOS's `MARKETING_VERSION`
  declare for an unreleased build, and it is what makes Android Studio's hot run show a
  value. A release build still overrides it with the tag. The earlier behaviour — hiding the
  row in every local run — made the Desktop app look like it did not know its own version.
- `Package.implementationVersion` is `null` for classes loaded from directories, which is
  exactly the test/IDE case; treat `null` and blank the same way.
