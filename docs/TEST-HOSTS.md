# Test Hosts — Pre-Existing Failures & Known Gaps

> **Historical during the harness cleanup.** The authoritative policy is
> [`docs/TESTING.md`](TESTING.md); the current allowlist is `test-known-failures.json`.
> This catalog is kept as measurement lore and is being folded into `docs/DECISIONS.md`
> by Phase 4 of [`docs/TEST-HARNESS-PLAN.md`](TEST-HARNESS-PLAN.md).

Reference document, not working instructions. Read this when a test fails and you need to
know whether it was already failing. For the rules, see `AGENTS.md`.

## Host Table

| Host | Task | State |
| --- | --- | --- |
| JVM | `jvmTest` | **12 failed** — the pre-existing set listed below, and the only thing the run exits red on. Totals: `test-baseline.json` |
| iOS simulator | `iosSimulatorArm64Test` | **12 failed** — the same 12. Totals: `test-baseline.json` |
| Android host | `testAndroidHostTest` | **red** — **34 failures**, all environment failures (Robolectric's default viewport is far smaller and its `Build` fields are `null`); not a regression. Split: design-system 14 rasterised-pixel gates, feature-settings-presentation 8, feature-projects-presentation 3, shared-ui 9. Totals: `test-baseline.json` |
| Desktop (the JVM app) | `:shared-ui:runDesktop` | **not a host** — Desktop *is* the `jvm` target, so the JVM row above is its suite; `Project.md` → Tests has the note and the one Desktop-only behaviour's test. Section 5 gave the shell a **width** dimension (5.1) that this host can drive through `ShellScenarios.setUpShell(width = …)` — a new capability of the existing host, not a fourth column |

## The `ttTheme` Fix

The two modules that reported no executions before are now fixed: `WorkItemCardScenarios.kt:142` and `SettingsScenarios.kt:234` called `ttTheme(ThemeScheme.Dark, content)` positionally and `ttTheme` grew an `accentPalette` parameter between `scheme` and `content`, so both `commonTest` compilations failed. Naming the argument (`content = content`) fixed both — they now read `WorkItemCardScenarios.kt:163` and `SettingsScenarios.kt:241`. The totals in `test-baseline.json` are generated from the JUnit XML by `scripts/test-baseline.py` and cross-checked against `@Test` declarations.

## The `:shared-ui` Compilation Fix

`:shared-ui` was also uncompilable until recently — `ShellPreferences.currentUserName` was a non-suspend `val` whose getter called the `suspend` `UserDefaultsStore.value(DefaultsKey)`. It is now a `StateFlow` refreshed when the router root becomes `Main`, so the module compiles and its 79 JVM / 71 iOS tests run. There is a known **coverage gap**: no scenario seeds a `CachedProfile` and asserts the board filter, so deleting the refresh call leaves the suite byte-identical. Do not read `:shared-ui` being green as evidence that filter works.

## Additional Pre-Existing Failures (unchanged by the `ttTheme` fix)

- `SeedingTest` ×3 in `:packages:feature-projects-data` (JVM and iOS hosts).
- `theLongAccentCaptionWrapsInsteadOfClipping` and `theUserDetailsSectionIsOnThePage` in `:packages:feature-settings-presentation` (JVM and iOS hosts).
- Seven `:shared-ui` shell scenarios (JVM and iOS hosts): `theSettingsTabEndsTheSession`, `aLockedSprintOffersNoTaskEdits`, `aChosenThemeOutlivesTheProcess`, `theLastRowOfEachListScrollsClearOfTheTabBar`, `theBoardReturnsToTheList`, `filingAwayInTheEditorClosesTheSheet`, `tappingASprintTaskRowOpensTheEditor`.

## Android Host: Two Distinct Failure Classes

The Compose resource packaging that used to fail 61 of the Android-host tests is **fixed** — `tasktracker.kmp.compose.gradle.kts` copies the resources into the merged assets Robolectric reads. What remains on the Android host is two distinct things:

### 1. Five `:composition-root` graph assertions need a `Context`

Room's `inMemoryDatabaseBuilder` refuses to open without one, so `testPlatformHost()` returned `Unit` and the graph never built. The fix is the split `:shared-ui` already uses: scenario bodies in `commonTest`, per-host entry points. (`HostedGraphScenarios` + `HostedGraphScenariosTest` on JVM/iOS, `HostedGraphAndroidHostTest` under Robolectric.) One of the five, `the outbox is left alone`, still cannot run on the Android host — it opens a `RoomOutboxQueue` and the Android host has no `sqliteJni` on its `java.library.path` (same limit that disables `:packages:database`'s host tests) — so it asks `thisHostOpensRoomStores()` and registers as a pass there. The other four need only a `Context` and pass on all three hosts.

### 2. Nine `:shared-ui` Compose UI assertions fail on layout, not behaviour

Part of the 34 `testAndroidHostTest` failures the host table above measures across 1283 tests. Robolectric's default viewport is far smaller than the JVM's and iOS's, so nodes inside a `LazyColumn` (`settings.versionRow`) or a `horizontalScroll` row (`board.column.in_review`) exist and report `assertIsDisplayed()` failing. That viewport has a second consequence the desktop-layout work ran into: `ShellScenarios.setUpShell(width = …)` expresses a width as a `Box(Modifier.width(…))`, which is a *request* — the box is measured against its parent's constraints, so a host with a narrower viewport silently clamps it. Robolectric clamps 1200dp below the 840dp expanded threshold, so the three wide-width rail scenarios (`aWideShellDrawsTheRailAndNoBar`, `theRailIsALeadingColumnRatherThanAStretchedOne`, `exactlyOneRailDestinationIsMarkedSelected`) **skip there and register as passes**; they ask `shellHonouredRequestedWidth` whether they got the width they asked for and print when they did not. A desktop window is not a configuration this host has. The JVM and iOS hosts honour the width and run all three, and Desktop is the JVM host. `theVersionRowHidesItselfWithNoVersion` also calls `setContent` twice, which Android's `ComponentActivity` rejects with *"has already set content"* — so making that scenario portable means changing shared assertions the JVM and iOS hosts currently pass. Two of the shared-ui nine are known-not-portable (the sheet scenarios, which read `Build` version fields that Robolectric leaves `null`, and the `setContent`-twice one): they are skipped on the Android host via `thisHostRasterisesText()`. Do not add another of these as a UI change; write a behaviour assertion instead, and measure pixels where the table says to.

## `thisHostRasterisesText()` — the rasterisation skip

A test that renders Compose and reads pixels must call `thisHostRasterisesText()` (an `expect` in `:packages:design-system`'s `commonTest` with actuals in `jvmTest`, `iosTest`, `androidHostTest`) or it will fail on that host. Be aware the skip registers as a *pass* there, not a skip.
