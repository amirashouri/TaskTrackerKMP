# AGENTS.md

Agent instructions for this repository. Read this before running anything.

`PLAN.md` is the project's design record — 2700 lines of phase history, decisions, and
measured justifications. It is the source of truth for *why* things are the way they are.
This file is the short version of *how to work here* and points at the rest.

## Setup

The build needs JDK 25. This machine has exactly one JDK and `java_home` cannot find it,
so every Gradle invocation needs:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

Without it the build dies immediately with `Unable to locate a Java Runtime`.

## Token-Saving & Execution Scope (CRITICAL)

To optimize token usage and prevent infinite tool loops, you must tightly scope your operational boundaries.

### 1. The Single-File Context Rule
* **Constraint:** Do not read or scan modules outside the explicit scope of your task.
* **Action:** If adding a feature or fixing a bug, request context *only* for the specific `shared` domain logic or the target UI file. Never run repository-wide string replacements or mass directory indexing.

### 2. KMP Structural Isolation
* **Constraint:** Shared behavior belongs in `commonMain`; platform code belongs in a platform source set only when an API or behavior is genuinely platform-specific.
* **Action:** Keep app features and Compose UI shared across Android and iOS. Add or change `expect`/`actual` declarations only for real platform seams, and provide every required host implementation (including `jvm` when the convention plugin requires it). Do not add thin native wrappers that only forward calls.

### 3. Plan-First Execution Mode (Two-Phase Protocol)
For any task requiring modifications across more than one file, you must execute in two phases:
* **Phase 1 (Token-Light):** Output a short, maximum 3-bullet plan detailing exactly which files will be modified and why. Stop and wait for user acknowledgment (`ACK`).
* **Phase 2 (Code Generation):** Write the implementation only *after* receiving an `ACK`. Never write complete files from scratch if editing a single function; emit minimal unified diff patches.

### 4. Clean Architecture and Package Boundaries
* **Constraint:** Gradle modules are the architecture boundary. Domain modules hold business rules and ports; data modules implement those ports; presentation modules depend on their own domain and render UI; `:composition-root` binds implementations; `:shared-ui` assembles the shared screen tree.
* **Action:** Before adding a dependency or module, inspect `allowedLibraryTable` and `packageOwnersTable` in `build-logic/convention/src/main/kotlin/tasktracker.architecture.gradle.kts`. Keep presentation independent of feature data modules and keep domain free of UI and infrastructure. Update the checked tables deliberately when the intended graph changes; never work around `verifyArchitectureGraph` or `verifyModuleImports`.
* **Action:** Put shared visual tokens and Compose resources in `:packages:design-system`. It is the only module that configures `compose.resources`.

## Liquid Glass Design Tokens

"Liquid Glass" layouts feature heavily saturated fluid background gradients, deep backdrop blurs, and glossy reflective borders. **Do not guess or invent styling primitives.** This burns tokens through endless cosmetic iterations.

* **Constraint:** All UI components mimicking glassmorphism must strictly bind to the repository's established design primitives found in `:packages:design-system`.
* **Action:** Use existing styling modifiers instead of raw rendering logic. If styling a surface, reuse the predefined mapping tokens (e.g., `Modifier.liquidGlassCard()`). If a primitive is missing, map it using immutable shared values inside your initial Phase 1 plan before writing UI layout code.

## The command that decides whether you are done

```bash
./gradlew jvmTest iosSimulatorArm64Test verifyArchitectureGraph verifyModuleImports :androidApp:compileDebugKotlin
```

`./gradlew check` is that plus `testAndroidHostTest`, which is **red for documented,
pre-existing reasons** (see above). Do not fix it as a side effect of other work, and do
not report it as a regression you caused — check whether it was already failing.

## Test hosts

| Host | Task | State |
| --- | --- | --- |
| JVM | `jvmTest` | **green** (1331 tests, 5 failed — pre-existing `SeedingTest` ×3 and `shared-ui`'s `aChosenThemeOutlivesTheProcess` + `theBoardReturnsToTheList`) |
| iOS simulator | `iosSimulatorArm64Test` | **green** (1303 tests, 5 failed — the same 5) |
| Android host | `testAndroidHostTest` | **red** — 787 tests, 31 failures, all `testAndroidHostTest` environment failures (Robolectric's default viewport is far smaller and its `Build` fields are `null`); not a regression — the count is the measured whole-command set (design-system 14 rasterised-pixel gates, feature-settings-presentation 7, feature-projects-presentation 3, shared-ui 7) |

The two modules that reported no executions before are now fixed: `WorkItemCardScenarios.kt:142` and `SettingsScenarios.kt:234` called `ttTheme(ThemeScheme.Dark, content)` positionally and `ttTheme` grew an `accentPalette` parameter between `scheme` and `content`, so both `commonTest` compilations failed. Naming the argument (`content = content`) fixed both — they now read `WorkItemCardScenarios.kt:163` and `SettingsScenarios.kt:241`. The totals are measured from the JUnit XML, cross-checked against `@Test` declarations.

`:shared-ui` was also uncompilable until recently — `ShellPreferences.currentUserName` was a
non-suspend `val` whose getter called the `suspend` `UserDefaultsStore.value(DefaultsKey)`. It is
now a `StateFlow` refreshed when the router root becomes `Main`, so the module compiles and its 48
JVM / 46 iOS tests run; see PLAN.md §20.10 and Phase 24.4 (its backlog section scenarios moved
here and its host registration was re-aligned to the canonical 13-method mapping). §20.10 also
records a known **coverage gap**: no
scenario seeds a `CachedProfile` and asserts the board filter, so deleting the refresh call leaves
the suite byte-identical. Do not read `:shared-ui` being green as evidence that filter works.

Additional pre-existing failures, unchanged by the `ttTheme` fix:

- `SeedingTest` ×3 in `:packages:feature-projects-data` (JVM and iOS hosts).
- `aChosenThemeOutlivesTheProcess` and `theBoardReturnsToTheList` in `:shared-ui` (all three hosts).

The Compose resource packaging that used to fail 61 of the Android-host tests is **fixed** — `tasktracker.kmp.compose.gradle.kts` copies the resources into the merged assets Robolectric reads. What remains on the Android host is two distinct things:

1. **Five `:composition-root` graph assertions need a `Context`** — Room's `inMemoryDatabaseBuilder` refuses to open without one, so `testPlatformHost()` returned `Unit` and the graph never built. The fix is the split `:shared-ui` already uses: scenario bodies in `commonTest`, per-host entry points. (`HostedGraphScenarios` + `HostedGraphScenariosTest` on JVM/iOS, `HostedGraphAndroidHostTest` under Robolectric.) One of the five, `the outbox is left alone`, still cannot run on the Android host — it opens a `RoomOutboxQueue` and the Android host has no `sqliteJni` on its `java.library.path` (same limit that disables `:packages:database`'s host tests) — so it asks `thisHostOpensRoomStores()` and registers as a pass there. The other four need only a `Context` and pass on all three hosts.
2. **Seven `:shared-ui` Compose UI assertions fail on layout, not behaviour** — one of the 31
   `testAndroidHostTest` failures the host table above now measures across 787 tests.
   Robolectric's default viewport is far smaller than the JVM's and iOS's, so nodes inside a
   `LazyColumn` (`settings.versionRow`) or a `horizontalScroll` row (`board.column.in_review`)
   exist and report `assertIsDisplayed()` failing. `theVersionRowHidesItselfWithNoVersion` also
   calls `setContent` twice, which Android's `ComponentActivity` rejects with *"has already set
   content"* — so making that scenario portable means changing shared assertions the JVM and iOS
   hosts currently pass. Two of the shared-ui seven are known-not-portable (the sheet scenarios,
   which read `Build` version fields that Robolectric leaves `null`, and the `setContent`-twice
   one): they are skipped on the Android host via `thisHostRasterisesText()`. Do not add another
   of these as a UI change; write a behaviour assertion instead, and measure pixels where the
   table says to.

On top of that, and also confirmed pre-existing by stashing:

- `SeedingTest` — 3 failures in `:feature-projects-data`, on the JVM and iOS hosts.
- `aChosenThemeOutlivesTheProcess` and `theBoardReturnsToTheList` in `:shared-ui`, on all
  three hosts.

The Compose resource packaging that used to fail 61 of these is **fixed** — see
`tasktracker.kmp.compose.gradle.kts`, which copies the resources into the merged assets
Robolectric reads. What remains is two distinct things:

1. **Five `:composition-root` assertions need a `Context`** — Room's
   `inMemoryDatabaseBuilder` refuses to open without one, so `testPlatformHost()` returns
   `Unit` and the graph never builds. A real `Context` needs
   `@RunWith(RobolectricTestRunner::class)`, which cannot be written in `commonTest`. The fix
   is the split `shared-ui` already uses: bodies in `commonTest`, entry points per host.
2. **Seven Compose UI assertions fail on layout, not behaviour.** Robolectric's default
   viewport is far smaller than the other two hosts, so content inside a `LazyColumn`
   (`settings.versionRow`) or a `horizontalScroll` row (`board.column.in_review`) exists but
   is not displayed. `theVersionRowHidesItselfWithNoVersion` has a second, independent
   problem: it calls `setContent` twice, which Android's `ComponentActivity` rejects with
   *"has already set content"*, so making that scenario portable means changing shared
   assertions that the JVM and iOS hosts currently pass.

Consequence for new tests: a test that renders Compose and reads pixels must call
`thisHostRasterisesText()` (an `expect` in `:packages:design-system`'s `commonTest` with
actuals in `jvmTest`, `iosTest`, `androidHostTest`) or it will fail on that host. Be aware
the skip registers as a *pass* there, not a skip.

## When a UI change needs a rendered-pixel test

Default to a structural assertion. Render pixels only when the property does not survive
being read off the modifier chain.

One question decides it: **can you write the failing assertion without rendering anything?**

| The property | Assert it |
| --- | --- |
| a fill clipped to the shape its own outline uses | structural |
| a modifier present, absent, or ordered | structural |
| a token's value, or a relationship between two | structural |
| shown or hidden in the tree | structural |
| ink's contrast over a translucent fill | **pixels** |
| a value composited *under another translucent layer* | **pixels** |
| a silhouette, a blur, a hairline, antialiasing | **pixels** |
| anything a second rasteriser might disagree about | **pixels, both hosts** |

Mandatory whatever the table says: a change to a colour, an alpha, a fill ramp, or any
number a test already holds to an exact value. Those are the four things a source-level
assertion provably cannot see, and two of the real defects in this repo were one of them.

Structural does not mean unverified. Rule 1 still applies, in its cheap form — delete or
invert the modifier and confirm the assertion goes red. That is seconds of editing rather
than a build cycle, and it is the entire reason to prefer a structural assertion. The
expensive form of the same ritual is what the `rendered-ui-measurement` skill is for.

Know what you are giving up. A structural assertion is a **proxy**: it asserts the intent is
spelled, not that the pixels came out. Reimplement the lens's wash as a `drawOutline` with the
right shape and delete the `.clip(CircleShape)`, and a structural test goes red on correct
pixels. That is a false red rather than a missed defect, which is the cheap direction to
fail, but it is not free either.

If the honest answer to "can I assert this without rendering?" is no, render it. Do not use
this section to justify skipping a measurement you cannot make any other way.

## Four rules

These exist because each one has already cost real time here.

### 1. Mutation-check before you trust a green

A new test that has never failed is not evidence of anything. Before believing any
assertion you wrote:

1. Run it. Green.
2. Revert the fix in the production file.
3. Run it again. **It must fail**, and the message must name the actual defect.
4. Restore the fix. Run again. Green.

If step 3 is green, the test is not measuring what you fixed. Throw it away and write a
different one. PLAN.md records 23 mutations run and 23 caught, and specifically two that
were missed on the first pass — both because the *test* was at fault.

Write the failure message as part of the test. Put the measured number and both colours in
it. That message is frequently how the real bug gets found.

### 2. Measure rendered pixels, never model the composite

**Scope first**: [when a UI change needs a rendered-pixel test](#when-a-ui-change-needs-a-rendered-pixel-test),
above. This rule says *how* to measure once you have decided to; that one says *whether* to.

A correct token in the wrong compositing is a real and recurring defect in this codebase —
the sign-in button (see `GlassFillContrastTest`) and the tab bar captions were both
instances. You cannot catch it by computing an alpha blend in a test; the blend is not what
the compositor does.

Render the real component and read pixels. `tools/inspect_frame.py` reads a PNG with no
third-party dependencies and prints geometry, contrast, and an ASCII map.

Run the measurement on **both** the JVM and the iOS simulator before believing a number.
They disagree: the same token on the same backdrop measured 5.37:1 on the JVM and 3.98:1 on
iOS, because iOS rasterizes a small glyph's stroke at roughly 85% coverage. Tuning to the
JVM number alone ships a defect to the platform that matters.

### 3. One complete diagnostic, never incremental

When a measurement is wrong, add **one** diagnostic that dumps everything you might need —
token values, per-row counts, per-row extremes, bounding boxes — then read it once. Adding
one `println` per hypothesis and rebuilding is the same as having no diagnostic, only
slower. Partial diagnostics send you theorising for cycles instead of looking.

### 4. Prose last, with numbers frozen

Do not write explanatory comments while the numbers are still moving. Investigate first,
freeze the numbers, then write the rationale once. Comments rewritten three times as a
figure changes are how contradictory comments get left behind in the tree.

## Skills

At session start, discover the repository skills under `.opencode/skills/*/SKILL.md` and
read each skill's frontmatter description. Before starting task work, read the full
instructions for every skill whose description matches the task; follow its referenced
resources as needed. This applies to every agent using this repository, not only tasks
that explicitly mention a skill. These skills carry the detail this file deliberately omits.

- **`kmp-compose-tests`** — adding or running tests in this multiplatform Compose project:
  the host matrix, source-set layout, the rasterisation skip, screenshot capture.
- **`rendered-ui-measurement`** — proving a UI assertion: the mutation ritual, twin-frame
  diffing and its contamination traps, contrast measurement on two rasterisers.
- **`kmp-architecture-workflow`** — placing feature logic and platform-specific behavior in
  this project's KMP module/source-set structure, checking dependency edges, and preserving
  shared Android/iOS UI.

There is also a `test-reviewer` subagent (`.opencode/agent/test-reviewer.md`) that reviews
a proposed test adversarially, specifically for whether it can fail.

## Conventions

- **Comments explain why, and carry the measurement.** This house style is not decoration;
  a change to a non-obvious value is expected to arrive with the number that justifies it.
  Routine tuning does not need a paragraph.
- **`PLAN.md` is updated in the same change as any test addition**, including the test-count
  baseline. It says so twice and has been wrong three times. Counts come from the JUnit XML
  the build writes, cross-checked against `@Test` in the source — never from `grep -c`
  alone, which over-counts because KDoc mentions the string in backticks.
- **`NavigationHost.kt` and most of `shared-ui` are untracked.** Git cannot recover an
  accidental edit. Do not use broad scripted search-and-replace in this tree; read the file
  and edit deliberately.
- **Compose Multiplatform 1.12.1 specifics** bite regularly: `BasicText` has no `textAlign`
  parameter (it goes in the style), `TextStyle` has no `overflow` property, and
  `ImageVector.Builder.addPath` takes a `Brush` fill rather than a colour — omitting it
  renders nothing at all, silently, even with a `ColorFilter` tint.
