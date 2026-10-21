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

- `composition-root/build.gradle.kts` — stamp the release version onto the `jvmJar`
  manifest as `Implementation-Version`, read from the same `-PtasktrackerVersion` property
  the release workflow already passes to `:shared-ui:packageDmg` / `packageDeb` /
  `packageMsi`. Blank when the property is absent.
- `composition-root/src/jvmMain/kotlin/com/tasktracker/compositionroot/PlatformAppVersion.jvm.kt`
  — replace the blank answer with the value the class's own jar manifest carries
  (`Package.implementationVersion`), mapped through a small internal function so the
  formatting is testable. A classes-directory host (tests, IDE) has no manifest and still
  answers blank, which keeps the row hidden rather than inventing a number.
- `composition-root/src/jvmTest/kotlin/com/tasktracker/compositionroot/PlatformAppVersionJvmTest.kt`
  *(new)* — the mapping: a version renders, blanks/whitespace/absence hide the row.
- `specs/desktop-app-version.md` — this file, kept current through the handoff.

## Contract

- [x] A packaged Desktop build reports the release version (`-PtasktrackerVersion=0.1.6`
      → `Version 0.1.6`) in the Settings About row, with no `AppVersion` change and no new
      port method. Verified by building that jar, unzipping the manifest, and calling the
      real `platformAppVersion(null)` against it: `marketing=0.1.6 display=0.1.6`.
- [x] A build with no version property still hides the row: `AppVersion.display` is `null`,
      so `SettingsScreen` renders no empty `settings.versionRow`.
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
- Manual packaging check (no UI pixels):
  `./gradlew :composition-root:jvmJar -PtasktrackerVersion=0.1.6` then
  `unzip -p composition-root/build/libs/composition-root-jvm-1.0.0.jar META-INF/MANIFEST.MF`
  must show `Implementation-Version: 0.1.6` (the jar is named for the Gradle project
  version, not the release tag). Without the property the attribute is empty.
- Structural/behavioural tests only; the `test-reviewer` subagent is not required.

## Handoff

- **Done:** the JVM `actual` now reports the release version from this module's jar
  manifest. `composition-root/build.gradle.kts` stamps `Implementation-Version` from
  `-PtasktrackerVersion` (same property the release workflow passes to the packaging tasks),
  `PlatformAppVersion.jvm.kt` maps `Package.implementationVersion` through `jvmAppVersion`,
  and `PlatformAppVersionJvmTest` pins the mapping plus the hide-when-absent rule.
  `scripts/check.sh full` is green: JVM 1940, iOS 1886, Android host 1592 (62 skipped, 0
  failed); only the JVM count moved, and `test-baseline.json` is regenerated. Mutation check:
  forcing the mapper to blank fails two tests with `expected:<0.1.6> but was:<null>`.
- **Pending:** commit, PR, and merge.
- **Next step:** commit the four files and open the PR against `main`.
- **Gotchas:** the release tag is the displayed value (`0.1.6`), not `desktopPackageVersion`
  (`1.1.6`, the macOS-bumped bundle number). `runDesktop` without `-PtasktrackerVersion`
  shows no row because a local build has no release version — deliberate, per
  `AppVersion.display`'s honesty rule. The jar is named for the Gradle project version
  (`composition-root-jvm-1.0.0.jar`), not the tag. The manifest attribute is build wiring no
  unit test can reach; the mapping test covers formatting, and the documented `unzip` check
  covers the stamp.

## Risks / notes

- The manifest attribute is build wiring no unit test can reach; the mapping test covers the
  formatting, and the `unzip` check above covers the stamp. State that residual in the
  handoff rather than implying the suite proves the packaged row.
- `runDesktop` without `-PtasktrackerVersion` runs from jars with a blank attribute, so the
  row stays hidden. That is deliberate: a local build has no release version, and a
  placeholder would be the "number nobody shipped" `AppVersion` documents. Use
  `./gradlew :shared-ui:runDesktop -PtasktrackerVersion=0.1.6` to see it in a dev run.
- `Package.implementationVersion` is `null` for classes loaded from directories, which is
  exactly the test/IDE case; treat `null` and blank the same way.
