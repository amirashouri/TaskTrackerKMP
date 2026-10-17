# Test Harness Cleanup — Plan

Status: **landed, with two open rows** — the gate, the empty allowlist and the three-host
matrix all hold; what remains is `scripts/check-test-matrix.py` running in no gate and
`docs/TEST-HOSTS.md` not yet folded into `docs/TESTING.md`. See the progress log at the end.
Branch: landed on `main`; there is no open branch for this plan.
Scope: the KMP client only (`TaskTrackerKMP`). No server or wire-format changes.

This is the working plan for making the client test matrix trustworthy. It is not the
feature roadmap; `docs/PLAN.md` remains the append-only roadmap. A short pointer to this
document is added there.

## Decisions already made

- Implement all phases, not just the scripts.
- `scripts/check.sh` is the fast inner loop: JVM tests, architecture checks, and the
  baseline check.
- `scripts/check.sh full` is the release/PR gate: JVM + iOS simulator + Android host +
  architecture + baseline + summary.
- Known failures are **named**, not counted. A failure count that stays the same while the
  test identities change is not a passing gate.
- Skip means skip. A host that cannot run an assertion must not record it as a pass.
- No hand-copied host totals in prose. `test-baseline.json` is the only source of counts.

## Why this work exists

Measured from the JUnit XML on disk at the start of the task:

| Host | Tests | Failures |
|---|---:|---:|
| `jvmTest` | 1722 | 12 |
| `iosSimulatorArm64Test` | 1670 | 12 |
| `testAndroidHostTest` | 1390 | 34 |

The current flow cannot distinguish a new regression from the standing red:

1. `scripts/check.sh` runs `jvmTest` and `iosSimulatorArm64Test` without `--continue`.
   Gradle stops after the first failing task, so the “done” command is not guaranteed to
   run the rest of the matrix.
2. `scripts/check-baseline.sh` exists but is never invoked by `check.sh`; the only
   references are docs and specs.
3. `scripts/test-baseline.py` hardcodes `"exit": "red"` and records only totals. A fixed
   test plus a new failure leaves the count unchanged and the baseline green.
4. The baseline is generated from whatever XML is on disk. A filtered run, an interrupted
   run, or a deleted test class leaves stale/mixed results that still compare equal.
5. `scripts/check.sh` excludes the Android host and lint because they are red, while
   `./gradlew check` includes them. There are two definitions of “done”.
6. Host totals are duplicated in `Project.md`, `AGENTS.md`, `docs/TEST-HOSTS.md`, and
   `.opencode/skills/kmp-compose-tests/SKILL.md`; they have drifted (1588/1539/1283 in
   `Project.md` against 1722/1670/1390 in the generated baseline).

The harness also manufactures failures:

- `rasterising { }` returns early on Android, but several tests capture a nullable value
  and call `requireNotNull` **outside** the block. Nine design-system failures
  (`DateFieldContrastTest` ×5, `StoryPointsTest` ×4) are this pattern, not a rasteriser
  disagreement.
- `ProjectPickerSheetRenderTest` calls `runComposeUiTest` with no guard and no
  Robolectric runner; it fails on Android with `Build.FINGERPRINT is null`.
- The Android host has no viewport qualifier. Robolectric’s default is ~320dp, so
  `LazyColumn` and `horizontalScroll` nodes are not displayed or not composed. This
  accounts for most of the settings, projects, and shared-ui failures.
- `thisHostDeliversSystemBack()` exists, but `systemBackPopsTheSprintDetail` does not
  consult it.
- Skips are recorded as passes (`skipped: 0` in the baseline).
- Scenario entry points are triplicated by hand (shared-ui alone has ~58 per host).
  Adding a scenario body without adding three `@Test` wrappers silently runs it nowhere.

The 12 JVM/iOS failures are not one category:

- `SeedingTest` ×3 are stale test expectations: `SampleData` now seeds 18 APO items, the
  test hardcodes 8 and `APO-9`, and one test sorts `APO-10` as a string before `APO-2`.
- `SettingsViewJvmTest` ×2 are lazy-list/viewport assertions; likely test bugs, but they
  need triage.
- `TaskTrackerAppJvmTest` ×7 mix test bugs (missing editor exit-delay advancement,
  scroll/display, pointer injection) with possible real behaviour gaps
  (`theSettingsTabEndsTheSession` sees the sign-out port called 0 times;
  `filingAwayInTheEditorClosesTheSheet` sees no store write; `theLastRowOfEachList...`
  reports a 60px overlap). These must be triaged individually, not labelled
  “pre-existing” in bulk.

## Target command surface

```bash
scripts/check.sh fast [module...]   # JVM only, module-scoped; fast inner loop
scripts/check.sh                    # JVM + graph + baseline + summary; green
scripts/check.sh full               # JVM + iOS + Android + graph + baseline + summary
scripts/check.sh ios|android        # targeted host
scripts/check.sh lint|docs
```

Rules:

- Every Gradle invocation uses `--continue` so one failing host does not hide the others.
- Before a host runs, its old `build/test-results/<host>` directories are removed. A
  result is evidence only when it came from the run being reported.
- After the run, one compact summary is printed and the baseline check runs for the hosts
  that actually ran. The script exits nonzero on any unexpected failure.
- `full` is required for rendering, `expect`/`actual`, and cross-platform changes. The
  fast default is for domain/data/unit work.
- `scripts/check-baseline.sh` is part of the command, not a document.

## Known-failure model

`test-known-failures.json` is a checked-in allowlist:

```json
{
  "jvmTest": {
    "com.tasktracker.featureprojectsdata.SeedingTest#theSeededBoardIsTheFixtureTheOriginalShipped": "stale fixture; Phase 1"
  },
  "iosSimulatorArm64Test": {},
  "testAndroidHostTest": {}
}
```

- It starts with the current 12/12/34 entries and a reason for each.
- A failure not in the list fails the gate.
- A listed test that now passes fails the gate as a stale entry, so entries cannot rot.
- A host with zero results fails the gate.
- When a phase fixes a test, its entry is removed in the same change.
- Empty is the target and the release condition.

`test-baseline.json` remains generated: per-host totals and the actual failure identities,
with no hardcoded `exit` or `why`. The allowlist is the policy; the baseline is the
measurement.

## Phases

### Phase 0 — Make the gate honest

**Goal:** the fast gate is green and meaningful before any test is fixed.

**Files**

- `scripts/check.sh` — scopes, `--continue`, result cleanup, summary call, baseline call,
  aggregate exit status; `all` remains an alias for `full`.
- `scripts/test-baseline.py` — failure identities, zero-result guard, no hardcoded red;
  `--hosts` so a partial run checks only the hosts it ran.
- `scripts/check-baseline.sh` — compare counts, failure identities, stale allowlist
  entries, and missing host results.
- `scripts/test-summary.py` — **new**; parse the JUnit XML, print a compact
  `module | test | first line` failure table, write `build/reports/test-summary.md`.
- `test-baseline.json` — regenerate.
- `test-known-failures.json` — **new**; seed with the current 12/12/34 and a reason each.
- `docs/TESTING.md` — **new**; the single policy for hosts, source sets, guards, and
  commands.
- `Project.md`, `AGENTS.md`, `README.md`, `docs/TEST-HOSTS.md` — point at
  `docs/TESTING.md`; remove duplicated host tables and stale counts. Full consolidation
  is Phase 4.

**Acceptance**

- `scripts/check.sh fast` runs only JVM and exits 0 when only allowlisted failures occur.
- `scripts/check.sh full` runs all three hosts even when one fails, prints every failure
  once, and exits nonzero only for an unexpected failure or stale entry.
- Deleting a test class cannot leave a green baseline.
- `test-baseline.json` no longer contains `"exit": "red"`.

### Phase 1 — Make JVM/iOS green

**Goal:** zero failures on both JVM and iOS; the allowlist entries for those hosts are
removed.

**Files**

- `packages/feature-projects-data/src/commonTest/kotlin/com/tasktracker/featureprojectsdata/SeedingTest.kt`
  — numeric key sort; derive expected counts and next key from `SampleData` instead of
  hardcoding 8/`APO-9`; pin the fixture shape in one place.
- `shared-ui/src/commonTest/kotlin/com/tasktracker/sharedui/ShellScenarios.kt` — advance
  the editor exit delay before asserting dismissal; scroll before display assertions;
  use semantics actions for wiring; consult `thisHostDeliversSystemBack`; triage the
  sign-out, file-away, and scroll-clear assertions as product defects or test defects.
- `packages/feature-settings-presentation/src/commonTest/kotlin/com/tasktracker/featuresettingspresentation/SettingsScenarios.kt`
  and `ThemeOptionScenarios.kt` — lazy-list scrolling/viewport assertions; keep the
  “user details leads the page” claim only if that is still the intended layout.
- `shared-ui/src/jvmTest/kotlin/com/tasktracker/sharedui/TaskTrackerAppJvmTest.kt` and
  `iosTest/.../TaskTrackerAppIosTest.kt` — only if a host entry point needs a viewport or
  clock hook.
- `test-known-failures.json` — remove each fixed entry in the same change.
- `test-baseline.json` — regenerate after the final fix.

**Acceptance**

- `scripts/check.sh full` reports 0 failures on JVM and iOS.
- Any genuinely broken product behaviour is fixed in production code with a test that
  fails without the fix; it is not left in the allowlist.
- The editor-dismiss and scroll assertions are mutation-checked: reverting the fix makes
  the named test fail.

### Phase 2 — Make the Android host green

**Goal:** zero failures on `testAndroidHostTest`; skipped tests are real skips.

**Files**

- `packages/design-system/src/commonTest/kotlin/com/tasktracker/designsystem/TextRasterSupport.kt`
  — add `expect fun assumeHostRasterisesText()`; `rasterising` calls it before
  `runComposeUiTest`.
- `packages/design-system/src/androidHostTest/kotlin/com/tasktracker/designsystem/TextRasterSupport.androidHost.kt`
  — actual uses `org.junit.Assume.assumeTrue(false, reason)`.
- `packages/design-system/src/jvmTest/.../TextRasterSupport.jvm.kt` and
  `iosTest/.../TextRasterSupport.ios.kt` — no-op actuals.
- `shared-ui/src/commonTest/kotlin/com/tasktracker/sharedui/ComposeUiHostSupport.kt` and
  the three host actuals — same contract.
- `packages/feature-settings-presentation/src/commonTest/kotlin/com/tasktracker/featuresettingspresentation/RasterSupport.kt`
  and the three host actuals — same contract.
- `shared-ui/src/commonTest/kotlin/com/tasktracker/sharedui/ProjectPickerSheetRenderTest.kt`
  — gate both tests before `runComposeUiTest`; the structural scaffold test either moves
  to a scenario object with an Android entry point or is honestly skipped there.
- `shared-ui/src/commonTest/kotlin/com/tasktracker/sharedui/ShellScenarios.kt` — add
  `assumeHostCapability(...)` for wide width, system back, and Room-store scenarios.
- `build-logic/convention/src/main/kotlin/tasktracker.kmp.compose.gradle.kts` — provide a
  phone viewport/`@Config` base for Compose host tests; keep `GraphicsMode.NATIVE`.
- `build-logic/convention/src/main/kotlin/tasktracker.kmp.library.gradle.kts` — only if
  the shared test configuration must be applied to non-Compose modules.
- `*/src/androidHostTest/resources/robolectric.properties` (or the convention-generated
  equivalent) — `sdk=34`, a phone qualifier such as `w411dp-h891dp`; wide scenarios opt
  into a wide qualifier or skip honestly.
- Android host test classes — confirm every Compose test class has
  `@RunWith(RobolectricTestRunner::class)`; fix the `setContent`-twice scenarios and
  `performScrollTo` failures.
- `test-known-failures.json` — remove fixed entries.

**Acceptance**

- `scripts/check.sh android` reports 0 failures.
- Pixel tests report as **skipped** on Android, not as passes; `skipped` in the baseline
  reflects the capability gaps.
- The wide-width and system-back scenarios either run under a host that supports them or
  skip with a reason; they do not fail because the host cannot express them.
- The 18px/60px layout assertions are re-checked at a realistic phone viewport; a real
  overlap remains a product fix, not a viewport excuse.

### Phase 3 — Keep the matrix from rotting

**Goal:** adding a scenario cannot silently omit a host.

**Files**

- `shared-ui/src/commonTest/kotlin/com/tasktracker/sharedui/ShellScenariosTest.kt` —
  **new** abstract base with the `@Test` methods, inherited by
  `TaskTrackerAppJvmTest`, `TaskTrackerAppIosTest`, and `TaskTrackerAppAndroidHostTest`.
- The same pattern for the other scenario objects:
  `SettingsScenarios`, `ProjectsListScenarios`, `WorkItemCardScenarios`,
  `BoardLaneScenarios`, `BoardColumnScenarios`, `ArchiveRoundTripScenarios`,
  `ProjectBacklogScenarios`, `GlassIntensityScenarios`, `SignInScenarios`.
- `scripts/check-test-matrix.py` — **new fallback** if Kotlin/Native does not discover
  inherited `@Test` methods; assert every scenario has exactly one entry point per
  runnable host.

**Acceptance**

- A scenario body without a host entry point either fails to compile or fails the matrix
  check.
- The host classes contain no duplicated scenario wiring.
- The spike result (inheritance works / fallback script) is recorded in this document.

### Phase 4 — CI and documentation consolidation

**Goal:** the matrix is enforced on every change and the policy exists once.

**Files**

- `.github/workflows/tests.yml` — **new**: JVM on Ubuntu, iOS on macOS, Android on
  Ubuntu; Gradle cache; upload JUnit XML and the summary; run the baseline check.
- `docs/TESTING.md` — finish the single policy; merge the durable parts of
  `docs/TEST-HOSTS.md`; delete or redirect `docs/TEST-HOSTS.md`.
- `Project.md` — link to `docs/TESTING.md`; remove hand-copied totals and the duplicated
  host tables. Leave historical measurement lore in `docs/DECISIONS.md`.
- `AGENTS.md` — host table becomes a link to the generated baseline and
  `docs/TESTING.md`.
- `README.md` — point at the fast/full commands.
- `.opencode/skills/kmp-compose-tests/SKILL.md` — update the guard and host instructions
  to match the new contract.

**Acceptance**

- A PR with a new failing test fails the gate even if the failure count is unchanged.
- A PR that fixes an allowlisted test but leaves the entry fails the gate.
- The docs contain no hand-written host totals.
- `scripts/check.sh full` is green on `main`.

> **Amended 2026-10-17.** The two lines above said "fails CI" because `tests.yml` was
> written to run on every push. It now runs on `workflow_dispatch` and a nightly schedule
> only, so a PR is gated by `scripts/check.sh` rather than by CI. The goal is unchanged in
> substance — the matrix is still enforced, by the local gate — and the measurements
> behind the change are in `docs/DECISIONS.md` → "Continuous integration".

## Risks and rollback

- **`org.junit.Assume` is JUnit 4** — available on the JVM and Android hosts (the host
  classes already import `org.junit.Test`), but not on Kotlin/Native. The `expect/actual`
  keeps it in `androidHostTest`; the JVM and iOS actuals are no-ops. If an actual cannot
  be placed where the common test can see it, fall back to moving pixel tests into a
  JVM+iOS-only source set.
- **Robolectric viewport** — a phone qualifier changes layout and may expose real
  defects. Fix the defects or narrow the test claim; do not widen the viewport until the
  test passes.
- **Base-class inheritance** — Kotlin/Native test discovery may not inherit `@Test`
  methods. Phase 3 has a script fallback and does not block Phases 0–2.
- **Baseline churn** — every phase changes test counts and skips. Regenerate
  `test-baseline.json` in the same change as the test edit; never hand-edit it.
- **Scope creep** — fixing the 12 may uncover real product defects. Those are in scope
  for this task only when they are the reason a named test is red; unrelated dead code
  is reported, not swept.

## Definition of done

- `scripts/check.sh fast` is green on a clean checkout.
- `scripts/check.sh full` is green on a clean checkout.
- `test-baseline.json` reports 0 failures and 0 errors on all three hosts; `skipped`
  entries are explained by real host capabilities.
- `test-known-failures.json` is empty.
- The matrix check (base class or script) passes.
- CI is green on a PR that changes tests. *(Amended 2026-10-17: PRs no longer run CI;
  `scripts/check.sh` is green on a PR that changes tests. See `docs/DECISIONS.md` →
  "Continuous integration".)*
- `docs/TESTING.md` is the only place the policy and commands are written down.

## Progress log

Rewritten 2026-10-17 (`CLI-13`) against the tree rather than against the phase marks. The four
phases that read "not started" here had in fact largely landed, which is the cost of a log nobody
owns; the notes below name the evidence instead of a status word, and the two rows that are not
"done" are the reason this document still has an owner.

| Phase | State | Notes |
|---|---|---|
| 0 — honest gate | done | `scripts/check.sh` and `full` run the three hosts through `test-summary.py`, then `scripts/check-baseline.sh` compares the result against `test-baseline.json` — so a fixed test plus a new failure cannot cancel out, which is the defect this phase exists for. |
| 1 — JVM/iOS green | done | Zero failures on both hosts, and `test-known-failures.json` is **empty for all three**. Nothing is expected to fail, so an unexpected failure fails the gate rather than being absorbed by the allowlist. |
| 2 — Android green | done | `testAndroidHostTest` runs inside `check.sh full` with zero failures. The skipped tests are the documented capability gaps (`docs/TESTING.md`) and report as *skipped*, not as passes — which is the phase's acceptance criterion. |
| 3 — matrix integrity | **partly** | The abstract base is real (`TaskTrackerAppScenarios`, inherited by all three host classes), so a scenario body with no host entry point is a compile error — the strong half of the goal. `scripts/check-test-matrix.py` exists as the fallback the spike did not need, but **no gate runs it**, so a scenario wired into fewer hosts than it claims is a review catch and not a failure. The spike result is also still not written down here, which is this phase's own outstanding item. |
| 4 — CI and docs | **partly** | `.github/workflows/tests.yml` runs the matrix, on `workflow_dispatch` and a nightly schedule rather than per push (see the amendment above; PRs are gated by `scripts/check.sh`). Host totals are not hand-copied — `test-baseline.json` is the only source. But `docs/TEST-HOSTS.md` still exists beside `docs/TESTING.md` instead of being merged into it and deleted, so the "one place the policy is written down" item of the definition of done is not met. |
