# AGENTS.md

Agent instructions for this repository. Read this before running anything.

`docs/DECISIONS.md` is the design record — the curated why behind every rule here. The
finished port log is frozen at `docs/PORT-LOG.md` and is archaeology, not instructions.
This file is the short version of *how to work here* and points at the rest.

## Setup

The build needs JDK 25. This machine has exactly one JDK and `java_home` cannot find it,
so every Gradle invocation needs:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

Without it the build dies immediately with `Unable to locate a Java Runtime`.
`scripts/check.sh` exports it for you, so prefer it over a bare `./gradlew`.

## Token-Saving & Execution Scope (CRITICAL)

To optimize token usage and prevent infinite tool loops, you must tightly scope your operational boundaries.

### 1. The Single-File Context Rule
* **Constraint:** Do not read or scan modules outside the explicit scope of your task.
* **Action:** If adding a feature or fixing a bug, request context *only* for the specific `shared` domain logic or the target UI file. Never run repository-wide string replacements or mass directory indexing.

### 2. Read with a target, not a net
* **The cap is on *searching*, not on reading.** Reading the files a task names, and their direct
  neighbours in the same package, is the job — do it without counting. What is capped is the blind
  sweep: three consecutive exploratory searches to *locate* a target you cannot name. If three turns
  have not found it, stop and ask for the path; the fourth guess costs more than the question.
* **No Speculative Reading:** Never read files to "understand the coding style" if they do not contain logic directly interacting with your current task. Lean on `Project.md` for architecture and on the task's spec (`specs/`) for its scope.

### 3. KMP Structural Isolation
* **Constraint:** Shared behavior belongs in `commonMain`; platform code belongs in a platform source set only when an API or behavior is genuinely platform-specific.
* **Action:** Keep app features and Compose UI shared across Android and iOS. Add or change `expect`/`actual` declarations only for real platform seams, and provide every required host implementation (including `jvm` when the convention plugin requires it). Do not add thin native wrappers that only forward calls.
* **Atomicity of expect/actual:** When adding or modifying an `expect` declaration, you must implement the corresponding `actual` declarations across **all matching source sets** within the *same tool transaction*. Never emit an `expect` change with a plan to add platform actuals later.

### 4. Plan-First Execution Mode (Two-Phase Protocol)
The `ACK` gate exists so a planned change cannot surprise the user; it is not a toll on every
edit. It applies to a change that is **cross-module, contract-bearing, or destructive**: it moves
a Gradle dependency edge, edits the checked architecture tables, changes a public signature,
deletes code, or touches files outside the one package the task names.
* **Phase 1 (Token-Light):** Output a short, maximum 3-bullet plan detailing exactly which files will be modified (with absolute paths and targeted functions) and why. Stop and wait for user acknowledgment (`ACK`).
* **Phase 2 (Code Generation):** Output a single sentence confirming which underlying repository skill guidelines you are actively executing against. Then, write the implementation only *after* receiving an `ACK`. Never write complete files from scratch if editing a single function; emit minimal unified diff patches.
* **Drift Constraint:** If during Phase 2 you discover that a file outside your approved Phase 1 plan must be edited, you must immediately abort execution, output an updated plan, and request a **secondary `ACK`**. Do not proceed with unapproved file modifications.
* **Fast path — no `ACK` needed:** a docs-only change, a typo or comment, or a single-file edit that
  changes no public signature and no dependency edge. Say what you are about to do in one sentence,
  then do it. Only the ceremony is skipped — the branch/PR workflow below still applies.

### 5. Clean Architecture and Package Boundaries
* **Constraint:** Gradle modules are the architecture boundary. Domain modules hold business rules and ports; data modules implement those ports; presentation modules depend on their own domain and render UI; `:composition-root` binds implementations; `:shared-ui` assembles the shared screen tree.
* **Action:** Before adding a dependency or module, inspect `allowedLibraryTable` and `packageOwnersTable` in `build-logic/convention/src/main/kotlin/tasktracker.architecture.gradle.kts`. Keep presentation independent of feature data modules and keep domain free of UI and infrastructure. Update the checked tables deliberately when the intended graph changes; never work around `verifyArchitectureGraph` or `verifyModuleImports`.
* **Action:** Put shared visual tokens and Compose resources in `:packages:design-system`. It is the only module that configures `compose.resources`.

## Liquid Glass Design Tokens

"Liquid Glass" layouts feature heavily saturated fluid background gradients, deep backdrop blurs, and glossy reflective borders. **Do not guess or invent styling primitives.** This burns tokens through endless cosmetic iterations.

* **Constraint:** All UI components mimicking glassmorphism must strictly bind to the repository's established design primitives found in `:packages:design-system`.
* **Action:** Use existing styling modifiers instead of raw rendering logic. If styling a surface, reuse the predefined mapping tokens (e.g., `Modifier.liquidGlassCard()`). If a primitive is missing, map it using immutable shared values inside your initial Phase 1 plan before writing UI layout code.

## The command that decides whether you are done

```bash
scripts/check.sh
```

That script is the one place this command lives (it is the five tasks below); it also exports
the JDK. Scopes: `jvm`, `ios`, `android`, `graph`, `lint`, `docs`.

```bash
./gradlew jvmTest iosSimulatorArm64Test verifyArchitectureGraph verifyModuleImports :androidApp:compileDebugKotlin
```

`./gradlew check` is that, **plus** `testAndroidHostTest` **and** `:androidApp:lintDebug`. All
three of the extras are **red for documented, pre-existing reasons** (see below): the Android host
on 34 layout/raster failures, and `lintDebug` on one `[NotificationPermission]` error. Do not fix
any of them as a side effect of other work, and do not report them as a regression you caused —
check whether it was already failing.

## Test hosts

| Host | Task | State |
| --- | --- | --- |
| JVM | `jvmTest` | **12 failed** — the pre-existing set listed below, and the only thing the run exits red on. Totals: `test-baseline.json` |
| iOS simulator | `iosSimulatorArm64Test` | **12 failed** — the same 12. Totals: `test-baseline.json` |
| Android host | `testAndroidHostTest` | **red** — **34 failures**, all environment failures (Robolectric's default viewport is far smaller and its `Build` fields are `null`); not a regression. Split: design-system 14 rasterised-pixel gates, feature-settings-presentation 8, feature-projects-presentation 3, shared-ui 9. Totals: `test-baseline.json` |
| Desktop (the JVM app) | `:shared-ui:runDesktop` | **not a host** — Desktop *is* the `jvm` target, so the JVM row above is its suite; `Project.md` → Tests has the note and the one Desktop-only behaviour's test. Section 5 gave the shell a **width** dimension (5.1) that this host can drive through `ShellScenarios.setUpShell(width = …)` — a new capability of the existing host, not a fourth column |

The two modules that reported no executions before are now fixed: `WorkItemCardScenarios.kt:142` and `SettingsScenarios.kt:234` called `ttTheme(ThemeScheme.Dark, content)` positionally and `ttTheme` grew an `accentPalette` parameter between `scheme` and `content`, so both `commonTest` compilations failed. Naming the argument (`content = content`) fixed both — they now read `WorkItemCardScenarios.kt:163` and `SettingsScenarios.kt:241`. The totals in `test-baseline.json` are generated from the JUnit XML by
`scripts/test-baseline.py` and cross-checked against `@Test` declarations.

`:shared-ui` was also uncompilable until recently — `ShellPreferences.currentUserName` was a
non-suspend `val` whose getter called the `suspend` `UserDefaultsStore.value(DefaultsKey)`. It is
now a `StateFlow` refreshed when the router root becomes `Main`, so the module compiles and its 79
JVM / 71 iOS tests run; see the finished port log (`docs/PORT-LOG.md` Phases 20/24) for the
full history. There is a known **coverage gap**: no
scenario seeds a `CachedProfile` and asserts the board filter, so deleting the refresh call leaves
the suite byte-identical. Do not read `:shared-ui` being green as evidence that filter works.

Additional pre-existing failures, unchanged by the `ttTheme` fix:

- `SeedingTest` ×3 in `:packages:feature-projects-data` (JVM and iOS hosts).
- `theLongAccentCaptionWrapsInsteadOfClipping` and `theUserDetailsSectionIsOnThePage` in
  `:packages:feature-settings-presentation` (JVM and iOS hosts).
- Seven `:shared-ui` shell scenarios (JVM and iOS hosts): `theSettingsTabEndsTheSession`,
  `aLockedSprintOffersNoTaskEdits`, `aChosenThemeOutlivesTheProcess`,
  `theLastRowOfEachListScrollsClearOfTheTabBar`, `theBoardReturnsToTheList`,
  `filingAwayInTheEditorClosesTheSheet`, `tappingASprintTaskRowOpensTheEditor`.

The Compose resource packaging that used to fail 61 of the Android-host tests is **fixed** — `tasktracker.kmp.compose.gradle.kts` copies the resources into the merged assets Robolectric reads. What remains on the Android host is two distinct things:

1. **Five `:composition-root` graph assertions need a `Context`** — Room's `inMemoryDatabaseBuilder` refuses to open without one, so `testPlatformHost()` returned `Unit` and the graph never built. The fix is the split `:shared-ui` already uses: scenario bodies in `commonTest`, per-host entry points. (`HostedGraphScenarios` + `HostedGraphScenariosTest` on JVM/iOS, `HostedGraphAndroidHostTest` under Robolectric.) One of the five, `the outbox is left alone`, still cannot run on the Android host — it opens a `RoomOutboxQueue` and the Android host has no `sqliteJni` on its `java.library.path` (same limit that disables `:packages:database`'s host tests) — so it asks `thisHostOpensRoomStores()` and registers as a pass there. The other four need only a `Context` and pass on all three hosts.
2. **Nine `:shared-ui` Compose UI assertions fail on layout, not behaviour** — part of the 34
   `testAndroidHostTest` failures the host table above measures across 1283 tests.
   Robolectric's default viewport is far smaller than the JVM's and iOS's, so nodes inside a
   `LazyColumn` (`settings.versionRow`) or a `horizontalScroll` row (`board.column.in_review`)
   exist and report `assertIsDisplayed()` failing. That viewport has a second consequence the
   desktop-layout work ran into: `ShellScenarios.setUpShell(width = …)` expresses a width as a
   `Box(Modifier.width(…))`, which is a *request* — the box is measured against its parent's
   constraints, so a host with a narrower viewport silently clamps it. Robolectric clamps 1200dp
   below the 840dp expanded threshold, so the three wide-width rail scenarios
   (`aWideShellDrawsTheRailAndNoBar`, `theRailIsALeadingColumnRatherThanAStretchedOne`,
   `exactlyOneRailDestinationIsMarkedSelected`) **skip there and register as passes**; they ask
   `shellHonouredRequestedWidth` whether they got the width they asked for and print when they
   did not. A desktop window is not a configuration this host has. The JVM and iOS hosts honour
   the width and run all three, and Desktop is the JVM host. `theVersionRowHidesItselfWithNoVersion` also
   calls `setContent` twice, which Android's `ComponentActivity` rejects with *"has already set
   content"* — so making that scenario portable means changing shared assertions the JVM and iOS
   hosts currently pass. Two of the shared-ui nine are known-not-portable (the sheet scenarios,
   which read `Build` version fields that Robolectric leaves `null`, and the `setContent`-twice
   one): they are skipped on the Android host via `thisHostRasterisesText()`. Do not add another
   of these as a UI change; write a behaviour assertion instead, and measure pixels where the
   table says to.

Consequence for new tests: a test that renders Compose and reads pixels must call `thisHostRasterisesText()` (an `expect` in `:packages:design-system`'s `commonTest` with actuals in `jvmTest`, `iosTest`, `androidHostTest`) or it will fail on that host. Be aware the skip registers as a *pass* there, not a skip.

## When a UI change needs a rendered-pixel test
Default to a structural assertion. Render pixels only when the property does not survive
being read off the modifier chain.

One question decides it: **can you write the failing assertion without rendering anything?**

| **The property** | **Assert it** |
|---|---|
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

If the honest answer to "can I assert this without rendering?" is no, render it. Do not use
this section to justify skipping a measurement you cannot make any other way.

## Multi-Agent Parallelism & Worktree Isolation

When multiple agent instances operate simultaneously across separate branches or git worktrees, they must adhere to strict synchronization boundaries to prevent state corruption, stale compilation locks, and logical merge conflicts.

### 1. Worktree Path & Workspace Isolation
* **Untracked State Trashing:** Files designated as untracked or gitignored (such as `NavigationHost.kt` and parts of `:shared-ui`) are shared across local environments if using standard directory switches. You must use `git worktree add <path> <branch>` to completely isolate files, local build caches, and test artifacts.
* **Gradle Daemon Collision:** Running parallel builds across different worktrees simultaneously on the same host can lead to lock collisions on the Gradle build cache and configuration cache. Always pass `--no-daemon` or explicitly target isolated project cache directories if executing tasks concurrently:
  ```bash
  ./gradlew jvmTest --no-daemon -Dorg.gradle.project.cachedir=.gradle-worktree-cache
  ```

### 2. Upstream Reconciliation Protocol
* **The Stale-Base Trap:** Before submitting a Phase 1 Plan for user `ACK`, you must pull the latest upstream tracking branch (`git fetch origin && git rebase origin/main`) and verify that no concurrent tasks have modified files or layers within your designated target scope.
* **Test Baseline Overwrites:** `test-baseline.json` is regenerated in the same change as any test addition. If a parallel agent commits an updated baseline while you are running, your local copy is stale: pull, re-run `scripts/check.sh all`, regenerate with `scripts/test-baseline.py` from the *merged* JUnit XML, and only then request final review.

### 3. Structural Splitting to Reduce Concurrency Conflicts
* **Anti-Monolith Commits:** To allow parallel feature development without merge failures, aggressively enforce the **Presentation Splitting Rules**. If two concurrent tasks require additions to the same package layer, they must land in completely separate files rather than appending code to shared module files.
* **DI Graph Splitting:** When modifying the dependency graph in `:composition-root` or the convention tables in `tasktracker.architecture.gradle.kts`, keep declarations organized cleanly within unique feature blocks. Do not interleave dependency links, or the text-based git merge tools will produce unresolvable structural conflicts.


## Task workflow: branch, push, PR, review, merge
Every task is one branch and one pull request. That is the only path a change takes to
`main`; task work is never committed directly to `main`.

**1. One task, one `feat/` branch.** Before touching a file, cut a branch from a fresh `main`:

```bash
git fetch origin
git switch main && git pull --ff-only
git switch -c feat/<kebab-case-task-slug>
```

A second task starts its own branch — never stack unrelated tasks in one PR. The
Stale-Base Trap above still applies before you plan.

**2. Do the task on that branch**, under the phase protocol and the four rules.

**3. Push when — and only when — it is done.** "Done" means the deciding command is green:

```bash
scripts/check.sh
```

```bash
git push -u origin feat/<kebab-case-task-slug>
```

**4. Open the PR against `main`.**

```bash
gh pr create --base main --title "<what the task did>" --body "<what changed, and which command verified it>"
```

**5. Review the PR before any merge.** Read your own diff as a stranger's: `gh pr diff <number>`.
The review confirms that only files inside the approved plan changed, there is no drive-by
cleanup, behavior changes ship with tests, and the validation command is green on the PR.
Run the `test-reviewer` subagent (`.opencode/agent/test-reviewer.md`) against any test this
task adds. A review that was not run is not an approval.

**6. Merge only once the review approves.** If the review is clean and the PR is green:

```bash
gh pr merge <number> --merge --delete-branch
```

If the review finds a defect, fix it on the same branch, push, and re-review — a red or
unreviewed PR is never merged. When the user asked to be the approver, wait for their
explicit approval; your own review passing is not their approval.

## Four rules
These exist because each one has already cost real time here.

## 1. Mutation-check before you trust a green
A new test that has never failed is not evidence of anything. Before believing any
assertion you wrote:

1. Run it. Green.
2. Revert the fix in the production file.
3. Run it again. **It must fail**, and the message must name the actual defect.
4. Restore the fix. Run again. Green.

If step 3 is green, the test is not measuring what you fixed. Throw it away and write a
different one. `docs/DECISIONS.md` records 23 mutations run and 23 caught, and specifically two that
were missed on the first pass — both because the *test* was at fault.

Write the failure message as part of the test. Put the measured number and both colours in
it. That message is frequently how the real bug gets found.

## 2. Measure rendered pixels, never model the composite
**Scope first**: [when a UI change needs a rendered-pixel test],
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

## 3. One complete diagnostic, never incremental
When a measurement or scenario is wrong, add **one** diagnostic that dumps everything you might need—token values, per-row counts, per-row extremes, bounding boxes—then read it once. Adding one `println` per hypothesis and rebuilding is prohibited.

Your immediate next response after a test failure must output your diagnostic block matching this layout before attempting any theorizing:

```markdown
### DIAGNOSTIC RECONCILIATION
*   **Failing Scenario/Assertion:** [Name]
*   **Host Environment:** [JVM / iOS / Android Host]
*   **Expected vs Actual Primitives:** [Token Values/Hex/Dimensions]
*   **System State Summary:** [Dump of relevant Cache/Outbox/Router states]
```

## 4. Prose last, with numbers frozen
Do not write explanatory comments while the numbers are still moving. Investigate first,
freeze the numbers, then write the rationale once. Comments rewritten three times as a
figure changes are how contradictory comments get left behind in the tree.

## Skills
At session start, discover the repository skills under `.opencode/skills/*/SKILL.md` and
read each skill's frontmatter description. Before starting task work, read the full
instructions for every skill whose description matches the task; follow its referenced
resources as needed. This applies to every agent using this repository, not only tasks
that explicitly mention a skill. These skills carry the detail this file deliberately omits.

*   `kmp-architecture-workflow` — placing feature logic and platform-specific behavior: Clean Architecture boundaries, the checked Gradle graph, KMP source sets, Liquid Glass design-system ownership.
*   `kmp-compose-tests` — adding, running or debugging tests: the three-host matrix, source-set layout, the Android host's known gaps, the rasterisation skip, screenshot capture.
*   `rendered-ui-measurement` — proving a UI assertion: the mutation-first ritual, twin-frame differencing and its contamination traps, estimator robustness, measuring on two rasterisers.
*   `compose-presentation-splitting` — adding or changing Compose screens, rows, previews or components in any `*-presentation` module or `:shared-ui`: the file-splitting and reuse limits.
*   `kmp-desktop-entry` — the Compose Desktop (JVM) target: the `shared-ui/src/jvmMain` entry point, the `compose.desktop.currentOs` classpath change, the run task, per-OS caveats.
*   `kmp-room-database-migrations` — Room schemas, DAOs, outbox queues, builders and transactions in `:packages:database` / `:packages:sync`: Context provisioning, split scenario testing, migration verification.

There is also a `test-reviewer` subagent (`.opencode/agent/test-reviewer.md`) that reviews
a proposed test adversarially, specifically for whether it can fail.

## Where a task's detail lives
*   A pending task's *how* lives in one spec, `specs/<plan-id>-<slug>.md`. Read that spec and this
  file, plus the files the spec names — not all of `docs/PLAN.md`. The plan is the append-only
  roadmap and finished log, and reading a thousand lines to find one entry is the cost this split
  removes. See `specs/README.md` for the format and the lifecycle.
*   **Counts are generated, not written.** Host totals live in `test-baseline.json`, written by
  `scripts/test-baseline.py` from the JUnit XML; `scripts/check-baseline.sh` fails when the file and
  the build disagree. Never hand-copy a total into prose — the old baseline said to update it by
  hand and still drifted three times. A spec says how the counts should *move*, not what they are.

## Local rules
The root file above is the repo-wide contract. Rules that are true in only one package
live next to the code they govern — a local file may only contain rules that are *false elsewhere*, so the two can never drift into saying the same thing twice:

*   `packages/design-system/AGENTS.md` — token ownership, `compose.resources`, pixel tests
*   `packages/database/AGENTS.md` — foreign keys, DAO ownership, migrations
*   `packages/feature-projects-presentation/AGENTS.md` — file splitting, fixtures, previews
*   `shared-ui/AGENTS.md` — untracked files, router ownership, host-split tests

Working in one of those packages, read both files. Working anywhere else, the root file
is the whole contract.

## Conventions
*   **Comments explain why, and carry the measurement.** This house style is not decoration;
a change to a non-obvious value is expected to arrive with the number that justifies it.
Routine tuning does not need a paragraph.
*   **Counts are generated, not written.** Host totals live in `test-baseline.json` (see `scripts/test-baseline.py`), and `scripts/check-baseline.sh` fails when the file and the JUnit XML disagree — regenerate it in the same change as any test addition. Do not hand-copy a total into `Project.md`, `AGENTS.md` or a spec: that habit was wrong three times. Cross-check a suspicious count against the `@Test` declarations in the source, never `grep -c` alone, which over-counts the backticked mentions in KDoc.
*   `NavigationHost.kt` and most of `shared-ui` are untracked. Git cannot recover an accidental edit. Do not use broad scripted search-and-replace in this tree; read the file and edit deliberately.
*   **Compose Multiplatform 1.12.1 specifics** bite regularly: `BasicText` has no `textAlign` parameter (it goes in the style), `TextStyle` has no `overflow` property, and `ImageVector.Builder.addPath` takes a `Brush` fill rather than a colour — omitting it renders nothing at all, silently, even with a `ColorFilter` tint.
*   **No drive-by cleanup.** While changing code, do not also delete what looks unused — imports, private helpers, parameters, files. "Unused" is a claim that needs its own proof and its own diff: the sweep that removed 109 imports across 58 files needed a dedicated detector, a hand-triaged list of 80 false positives (`by`-delegation's `getValue`/`setValue`, operator `get`, and `@Preview` functions all read as unused to any name matcher), a stash-revert mutation check, and a full three-host build — and it only moved behind an explicitly ACK'd plan. A deletion riding inside a feature diff cannot be verified in the same sitting: the feature's tests prove the feature, not the deletion, and the two failure modes (over-removal vs. the feature's own bug) become indistinguishable. Dead code you notice incidentally goes in your report to the user, never in the diff; sweeping it is a separate, separately acknowledged task.

## Splitting a presentation screen into files
A presentation file grows by addition — a row, a badge, a footer — and each addition makes
the whole harder to hold in your head and a piece in it harder to reuse. So the unit of a
presentation file is **one public composable**, and the file is named after it.

*   **A new public composable gets its own file, never an append to someone else's.** `ProjectRow.kt`, `WorkItemCard.kt`, `BoardColumn.kt`. A public `@Composable` is something other screens read and tests reach for, so it has a name and a path of its own.
*   **Its private helpers, tag constants, `@Preview`s and file-local `const val`s move with it.** They are that component's contract: a tag left in another file is a tag the next rename misses, and a preview stored elsewhere stops showing what its name says. See `ProjectRow.kt` and `WorkItemCard.kt` — the tags sit at the bottom of the file that draws the nodes.
*   **Split when a file passes ~400 lines or carries more than one top-level `@Composable`.** That is a prompt to look, not a lint error: below it a screen and its one helper read better together. `ProjectRow.kt` (223), `WorkItemCard.kt` (326) and `BoardColumn.kt` (298) are the shape to aim at; `WorkItemEditor.kt` (754) and `ProjectBoardScreen.kt` (492) are still past it. The worked example is the 1158-line `SprintScreens.kt`, split into `SprintListScreen.kt`, `SprintListRowView.kt`, `SprintProgressBar.kt`, `SprintEditorSheet.kt`, `SprintDetailScreen.kt`, `SprintTotal.kt` and `SprintSummaryScreen.kt`: the five screens, plus the two composables (`SprintProgressBar`, `SprintTotal`) that two screens each drew and so could not stay private in either.
*   **Reuse comes from the boundary, not from copy-paste.** Two screens that want the same row import one file instead of each drawing it, which is what makes the shared piece visible and changeable in one place.

This is a *presentation* rule: it says where a `@Composable` lives, and does not change how
models, use cases or domain code are organised.