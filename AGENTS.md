# AGENTS.md

Agent instructions for this repository. Read this before running anything.

`docs/DECISIONS.md` is the design record — the curated why behind every rule here.
This file is the short version of *how to work here* and points at the rest.

For build setup, JDK, run commands, and test-host details see **`Project.md`** and
**`docs/TEST-HOSTS.md`**. This file contains rules only; facts live there.

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
The `ACK` gate exists so a planned change cannot surprise the user; it is not a toll on every edit.

**ACK is required if and only if any of these are true:**
- The change touches 2+ Gradle modules.
- It changes a public API signature or a wire format (JSON key, DTO field).
- It modifies `tasktracker.architecture.gradle.kts` (the checked architecture graph).
- It deletes code or removes a dependency.
- It touches files outside the one package the task names.
- It touches both repos (`TaskTrackerKMP` and `TaskTrackerServer`).

**Everything else is the fast path:** a docs-only change, a typo or comment, or a
single-file edit that changes no public signature and no dependency edge. Say what
you are about to do in one sentence, then do it. The branch/PR workflow below still
applies.

* **Phase 1 (Token-Light):** Output a short, maximum 3-bullet plan detailing exactly which files will be modified (with absolute paths and targeted functions) and why. Stop and wait for user acknowledgment (`ACK`).
* **Phase 2 (Code Generation):** Write the implementation only *after* receiving an `ACK`. Never write complete files from scratch if editing a single function; emit minimal unified diff patches.
* **Drift Constraint:** If during Phase 2 you discover that a file outside your approved Phase 1 plan must be edited, you must immediately abort execution, output an updated plan, and request a **secondary `ACK`**. Do not proceed with unapproved file modifications.

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

That script exports the JDK and runs the five-task Gradle line. Scopes and details: `Project.md`.

`./gradlew check` adds `testAndroidHostTest` and `:androidApp:lintDebug`. Both are **red for
documented, pre-existing reasons** — see `docs/TEST-HOSTS.md`. Do not report them as regressions.

## Test hosts (summary)

| Host | Task | State |
| --- | --- | --- |
| JVM | `jvmTest` | 12 pre-existing failures (see `docs/TEST-HOSTS.md`) |
| iOS | `iosSimulatorArm64Test` | same 12 |
| Android | `testAndroidHostTest` | 34 pre-existing Robolectric failures (see `docs/TEST-HOSTS.md`) |

Full failure catalog, host-specific gotchas, and the `thisHostRasterisesText()` rasterisation
skip: **`docs/TEST-HOSTS.md`**. Read it when a test fails, not before every task.

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

Structural does not mean unverified — delete or invert the modifier and confirm the
assertion goes red (seconds, not a build cycle). The expensive form is what the
`rendered-ui-measurement` skill is for.

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
* **Anti-Monolith Commits:** To allow parallel feature development without merge failures, aggressively enforce the **Presentation Splitting Rules** below. If two concurrent tasks require additions to the same package layer, they must land in completely separate files rather than appending code to shared module files.
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

**2. Do the task on that branch**, under the phase protocol and the rules below.

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

Run the `test-reviewer` subagent (`.opencode/agent/test-reviewer.md`) against any
**pixel-reading, contrast, or rendered-output test** this task adds. For structural or
behavioral unit tests, the mutation-check (rule 1, below) is sufficient evidence — the
subagent is not required. A pixel test review that was not run is not an approval.

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

**Scope:** This ritual is **mandatory** for pixel-reading tests, contrast tests, and any test
using `thisHostRasterisesText()`. For structural assertions (modifier present/absent, composable
in tree, thrown exceptions, simple data transforms), a single green run is sufficient — the
false-green danger is low and the mutation is obvious.

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

When a test or build fails **unexpectedly** (not a simple compile error), output this
diagnostic block before attempting any theorizing:

```markdown
### DIAGNOSTIC RECONCILIATION
*   **Failing Scenario/Assertion:** [Name]
*   **Host Environment:** [JVM / iOS / Android Host]
*   **Expected vs Actual Primitives:** [Token Values/Hex/Dimensions]
*   **System State Summary:** [Dump of relevant Cache/Outbox/Router states]
```

A missing-import or type-mismatch compile error does **not** require this block — fix it
and move on. The block is for when a green test goes red and you do not know why.

## 4. Prose last, with numbers frozen
Do not write explanatory comments while the numbers are still moving. Investigate first,
freeze the numbers, then write the rationale once. Comments rewritten three times as a
figure changes are how contradictory comments get left behind in the tree.

## Skills
Skills under `.opencode/skills/*/SKILL.md` carry the detail this file deliberately omits.
**Read each skill's frontmatter `description` at session start.** Load the full skill
instructions **on demand** — only when its description matches your task, not pre-emptively.
This file already contains the rules; skills add the procedural depth for specific workflows.

*   `kmp-architecture-workflow` — placing feature logic and platform-specific behavior: Clean Architecture boundaries, the checked Gradle graph, KMP source sets, Liquid Glass design-system ownership.
*   `kmp-compose-tests` — adding, running or debugging tests: the three-host matrix, source-set layout, the Android host's known gaps, the rasterisation skip, screenshot capture.
*   `rendered-ui-measurement` — proving a UI assertion: the mutation-first ritual, twin-frame differencing and its contamination traps, estimator robustness, measuring on two rasterisers.
*   `kmp-desktop-entry` — the Compose Desktop (JVM) target: the `shared-ui/src/jvmMain` entry point, the `compose.desktop.currentOs` classpath change, the run task, per-OS caveats.
*   `kmp-room-database-migrations` — Room schemas, DAOs, outbox queues, builders and transactions in `:packages:database` / `:packages:sync`: Context provisioning, split scenario testing, migration verification.

The `test-reviewer` subagent (`.opencode/agent/test-reviewer.md`) reviews a proposed test
adversarially — use it for pixel-reading tests only.

## Where a task's detail lives
*   A pending task's *how* lives in one spec, `specs/<plan-id>-<slug>.md`. Read that spec and this
  file, plus the files the spec names — not all of `docs/PLAN.md`. The plan is the append-only
  roadmap and finished log, and reading a thousand lines to find one entry is the cost this split
  removes. See `specs/README.md` for the format and the lifecycle.

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
*   **Counts are generated, not written.** Host totals live in `test-baseline.json`, written by
  `scripts/test-baseline.py` from the JUnit XML; `scripts/check-baseline.sh` fails when the file and
  the build disagree. Regenerate it in the same change as any test addition. Do not hand-copy a
  total into `Project.md`, `AGENTS.md` or a spec. Cross-check a suspicious count against the `@Test`
  declarations in the source, never `grep -c` alone.
*   `NavigationHost.kt` and most of `shared-ui` are untracked. Git cannot recover an accidental edit. Do not use broad scripted search-and-replace in this tree; read the file and edit deliberately.
*   **Compose Multiplatform 1.12.1 specifics** bite regularly: `BasicText` has no `textAlign` parameter (it goes in the style), `TextStyle` has no `overflow` property, and `ImageVector.Builder.addPath` takes a `Brush` fill rather than a colour — omitting it renders nothing at all, silently, even with a `ColorFilter` tint.
*   **No drive-by cleanup.** While changing code, do not also delete what looks unused — imports, private helpers, parameters, files. "Unused" is a claim that needs its own proof and its own diff. Dead code you notice incidentally goes in your report to the user, never in the diff; sweeping it is a separate, separately acknowledged task.

## Splitting a presentation screen into files
A presentation file grows by addition — a row, a badge, a footer — and each addition makes
the whole harder to hold in your head and a piece in it harder to reuse. So the unit of a
presentation file is **one public composable**, and the file is named after it.

*   **A new public composable gets its own file, never an append to someone else's.** `ProjectRow.kt`, `WorkItemCard.kt`, `BoardColumn.kt`. A public `@Composable` is something other screens read and tests reach for, so it has a name and a path of its own.
*   **Its private helpers, tag constants, `@Preview`s and file-local `const val`s move with it.** They are that component's contract: a tag left in another file is a tag the next rename misses, and a preview stored elsewhere stops showing what its name says. See `ProjectRow.kt` and `WorkItemCard.kt` — the tags sit at the bottom of the file that draws the nodes.
*   **Split when a file passes ~400 lines or carries more than one top-level `@Composable`.** That is a prompt to look, not a lint error: below it a screen and its one helper read better together. `ProjectRow.kt` (223), `WorkItemCard.kt` (326) and `BoardColumn.kt` (298) are the shape to aim at; `WorkItemEditor.kt` (754) and `ProjectBoardScreen.kt` (492) are still past it.
*   **Reuse comes from the boundary, not from copy-paste.** Two screens that want the same row import one file instead of each drawing it, which is what makes the shared piece visible and changeable in one place.

**Compose Multiplatform 1.12.1 specifics** (do not write syntax illegal under this runtime):
1. `BasicText` lacks a `textAlign` parameter. Pass alignment variables inside the `style` block.
2. `TextStyle` lacks an `overflow` setting. It must be designated on the `ParagraphStyle` layer.
3. `ImageVector.Builder.addPath` requires a `fill: Brush`. Omitting this or passing a direct color prints absolute transparency. Always use `fill = SolidColor(Color.Black)` combined with color tints.

**Liquid Glass Modifier Bindings:** Never build arbitrary layouts using raw rendering primitives, blurs, or gradients. You must bind layouts exclusively to the established designTokens in `:packages:design-system` using dedicated extension maps (e.g., `Modifier.liquidGlassCard()`).

This is a *presentation* rule: it says where a `@Composable` lives, and does not change how
models, use cases or domain code are organised.
