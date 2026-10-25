---
name: kmp-compose-tests
description: Use when adding, running, or debugging any test in this Kotlin Multiplatform Compose project - jvmTest, iosSimulatorArm64Test, testAndroidHostTest, runComposeUiTest, commonTest source sets, or screenshot capture. Covers the three-host matrix, the Android host's known gaps, and the rasterisation skip.
related:
  - kmp-architecture-workflow
  - rendered-ui-measurement
---

# Tests in this multiplatform Compose project

TaskTracker is Kotlin Multiplatform on Compose Multiplatform 1.12.1: one `commonMain`
targeting Android and iOS, three test hosts, and a design system that owns all rendering
tokens. A test written in `commonTest` runs on all three hosts, which is the point and also
most of the difficulty.

## The three hosts

```bash
./gradlew --offline :packages:<module>:jvmTest --tests '<pattern>'   # development loop
scripts/check.sh full                                                # release gate: JVM + iOS + Android
```

Development runs focused JVM tests: one class or package per touched module, whole run
under a minute. `scripts/check.sh` is the release gate, not a per-task check — it exports
the JDK, runs Gradle with `--continue`, and accepts only failures named in
`test-known-failures.json`; the generated totals live in `test-baseline.json` and are
regenerated from that run. `docs/TESTING.md` is the authoritative policy, and
`docs/TEST-HARNESS-PLAN.md` is the phased cleanup that empties the allowlist.

`jvmTest` is the fast loop (seconds, no simulator). `iosSimulatorArm64Test` is slower but is
the only host that catches iOS-specific rendering behaviour, which is not a theoretical
concern — see the next section.

`testAndroidHostTest` has a standing allowlist. Do not fix it incidentally and do not
report it as your regression. The current causes are named per test in
`test-known-failures.json`; the two structural ones are:

1. **Five `:composition-root` assertions need a `Context`.** They resolve
   `TaskTrackerDatabase`, and `inMemoryDatabaseBuilder` needs a `Context` that only a
   Robolectric sandbox provides. `testPlatformHost()` returns `Unit` instead, and the graph
   never builds. Robolectric is on the classpath and never engages: it only takes over for a
   class that names its runner, and a runner annotation is not expressible from `commonTest`.
   The fix is the split `shared-ui` already uses — bodies in `commonTest`, thin entry points
   per host, `@RunWith(RobolectricTestRunner::class)` on the Android one. One of the five,
   `the outbox is left alone`, still cannot run on the Android host — it opens a
   `RoomOutboxQueue` and the Android host has no `sqliteJni` on its `java.library.path` (same
   limit that disables `:packages:database`'s host tests) — so it asks `thisHostOpensRoomStores()`
   and registers as a pass there. The other four need only a `Context` and pass on all three
   hosts.
2. **Seven `:shared-ui` Compose UI assertions fail on layout, not behaviour.** Robolectric's
   default viewport is far smaller than the other two hosts, so a node inside a `LazyColumn` or
   a `horizontalScroll` row *exists* but reports `assertIsDisplayed()` failing. Two of the seven
   are known-not-portable (the sheet scenarios, which read `Build` version fields that
   Robolectric leaves `null`, and `theVersionRowHidesItselfWithNoVersion`, which calls
   `setContent` twice): they are skipped on the Android host via `thisHostRasterisesText()`.
   Do not add another of these as a UI change; write a behaviour assertion instead.

The resource packaging that used to fail 61 of these is **fixed**, in
`tasktracker.kmp.compose.gradle.kts`. Worth knowing why it needed a `Copy` rather than a
source directory, because the obvious fix does not exist: on AGP 9.4.1 a KMP library's
`onVariants` yields only `androidMain`, there is no `hostTestVariants` accessor, and on that
variant `variant.sources.assets` is null while `sources.resources` is not. Robolectric 4.16.1
also has no `assets` option on `@Config`. So the files have to physically land in the
directory `test_config.properties` names as `android_merged_assets`.

## Boundary Rules for Testing Failures

- **No Incremental Debugging Loops:** If a suite compilation or run fails, you are explicitly blocked from executing sequential `println` or minor code-poking patches to guess layout trees. You must pull structural info in one dump.
- **Mandatory Failure Receipt:** Immediately following any test run containing unexpected red lines, emit the `### DIAGNOSTIC RECONCILIATION` block defined once in `AGENTS.md` → rule 3, before modifying any file. It is defined there and nowhere else, so its four fields cannot drift between this skill and the contract.

## A test that reads pixels must declare where it can run

A Compose UI test that captures a frame and inspects it cannot run on the Android host, for
cause 3. Gate it:

```kotlin
@OptIn(ExperimentalTestApi::class)
class MyRenderTest {
    @Test
    fun theThing() = rasterising {
        setContent { /* ... */ }
        // read pixels
    }
}
```

`rasterising` and `thisHostRasterisesText()` live in
`packages/design-system/src/commonTest/.../TextRasterSupport.kt` — an `expect` in
`commonTest` with `actual`s in `jvmTest`, `iosTest` and `androidHostTest`. Copy that shape
into another module rather than re-inventing it.

Two caveats to carry, not to fix silently:

- The skip is reported as a **pass**, not a skip. `kotlin.test` has no portable skip and
  `org.junit.Assume` does not exist on the iOS and JVM compilations.
- Reading that target's results alone will tell you the assertions ran. They did not. The
  value is in the other two hosts, which is what makes the pair worth having: a rasterisation
  defect that only appears when they disagree is invisible to either alone.

## Source sets

```
packages/design-system/src/
  commonMain/    code and tokens; expect declarations for colours
  jvmMain/       actual colours (no native dynamic colours)
  iosMain/       actual colours from UIColor
  commonTest/    tests that must run on every host
  jvmTest/       actual for thisHostRasterisesText, JVM-only tests
  iosTest/       actual for thisHostRasterisesText
  androidHostTest/ actual for thisHostRasterisesText
```

`applyDefaultHierarchyTemplate()` is called in the convention plugin, which is what makes
`iosTest` and the explicit `actual`s work at all. Never assume a file in `src/iosMain` is
compiled — the convention plugin has a long comment about a file that sat there silently
uncompiled for a whole phase.

## Staleness traps when iterating

- Editing a file and then checking `build/reports` PNGs can show you the previous run's
  output. Re-run with `--rerun-tasks` after editing outside Gradle (`cp`, `git checkout`).
- After a revert-and-restore cycle, confirm what is actually on disk with `rg` before
  interpreting a measurement. Reading a measurement as if it described the other version of
  the file is a real and expensive mistake here.
- Gradle's configuration cache is on. If a build behaves as though it ignored an edit,
  re-run rather than debugging the code.

## Screenshot capture

`shared-ui/src/jvmTest/.../TabBarScreenshotTest.kt` is the harness. Three things in it are
load-bearing and are not stylistic choices:

- **Skia `readPixels` with an explicit `RGBA_8888` / `UNPREMUL` info**, not
  `ImageBitmap.toPixelMap()`. The `toPixelMap` version produced frames with a red channel of
  exactly zero: composable fine, reader wrong, output uniformly cyan. A surface read without
  declaring its format is the one place here where a wrong answer still looks plausible.
- **`performSemanticsAction(SemanticsActions.OnClick)`, never `performClick()`.**
  `performClick` leaves the virtual pointer resting on the node it clicked, so the desktop
  indication stays down and darkens that one segment by 0.14 of relative luminance — more
  than twice the lens's own contribution. `advanceTimeBy` does not clear it; it is not an
  animation.
- **One `runComposeUiTest` per appearance.** A `ComposeUiTest` owns a single composition, so
  looping over themes inside one means the second set of frames is a recomposition of content
  built against the first theme's tokens.

Output lands in `shared-ui/build/reports/tabbar/`. Frames are deterministic, so an
identical byte count after a real change means the change did not reach the render — check
that before believing the images.

## Things that fail silently in Compose 1.12.1

- `ImageVector.Builder.addPath` takes `fill: Brush`, not a colour. Omit it and the glyph
  renders **nothing at all**, with no error, even with a `ColorFilter` tint — because the
  source alpha is zero and `SrcIn` multiplies by it. Use `fill = SolidColor(Color.Black)` and
  let the tint do the work.
- `BasicText` has no `textAlign` parameter. It goes in the style.
- `TextStyle` has no `overflow` property. It lives on `ParagraphStyle`.