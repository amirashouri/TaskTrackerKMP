# AGENTS.md

Agent instructions for this repository. Read this before running anything.

> **Cross-project rules:** if your task touches both repos, read `../AGENTS.md`
> first — it governs wire format contracts, the dual-repo ACK protocol, and
> build isolation boundaries. This file is the contract for KMP-only work.

`docs/DECISIONS.md` is the design record — the curated why behind every rule here.
This file is the short version of *how to work here* and points at the rest.

For build setup, JDK, run commands, and test-host details see **`Project.md`** and
**`docs/TEST-HOSTS.md`**. This file contains rules only; facts live there.

`docs/PLAN.md` is the concise active roadmap; `docs/PLAN-ARCHIVE.md` preserves
detailed history. Read the selected task's spec, not the entire archive.

## Execution scope

### 1. Read with a target, not a net
Reading the files a task names, and their direct neighbours in the same package,
is the job. What is capped is the blind sweep: **three consecutive exploratory
searches** to locate a target you cannot name. If three turns have not found it,
stop and ask for the path. Never read files to "understand the coding style" if
they do not contain logic directly interacting with your current task — lean on
`Project.md` for architecture and `specs/` for scope.

### 2. KMP Structural Isolation
Shared behavior belongs in `commonMain`; platform code belongs in a platform
source set only when an API or behavior is genuinely platform-specific. Add or
change `expect`/`actual` declarations only for real platform seams, and provide
every required host implementation (including `jvm` when the convention plugin
requires it) **in the same transaction** — never emit an `expect` change with a
plan to add platform actuals later.

### 3. Plan-First Protocol
The `ACK` gate exists so a planned change cannot surprise the user; it is not a
toll on every edit.

**ACK is required if any of these are true:**
- The change touches 2+ Gradle modules.
- It changes a public API signature or a wire format (JSON key, DTO field).
- It modifies `tasktracker.architecture.gradle.kts`.
- It deletes code or removes a dependency.
- It touches files outside the one package the task names.
- It touches both repos — follow `../AGENTS.md` for the cross-project protocol.

**Fast path (no ACK):** docs-only change, typo/comment, or a single-file edit
that changes no public signature and no dependency edge. Say what you are about
to do in one sentence, then do it.

- **Phase 1:** Max 3-bullet plan with exact files (absolute paths) and targeted
  functions. Stop and wait for `ACK`.
- **Phase 2:** Write the implementation after `ACK`. Emit minimal diffs, never
  whole files when editing a single function.
- **Drift:** If a file outside the approved plan must be edited, abort, output
  an updated plan, and request a secondary `ACK`.

### 4. Task specs and status

- For roadmap work, select the task from `docs/PLAN.md`. If it has no spec,
  consult only its matching section in `docs/PLAN-ARCHIVE.md`, then create a
  spec from `specs/_template.md` before implementation.
- Keep a spec `in-progress` while implementation, validation, or review
  remains. At the end of each session, update `## Handoff` with verified
  progress, pending work, the next step, and gotchas.
- After the contract and required checks pass and the diff review is approved,
  set the spec to `done` in the final task-branch update before merge. Update
  the active roadmap status in the same change when applicable.
- Use provider-neutral handoff language; the committed spec, not chat context,
  carries task state between agents, tools, and sessions.

### 5. Clean Architecture and Package Boundaries
Gradle modules are the architecture boundary. Domain holds rules and ports;
data implements those ports; presentation renders UI; `:composition-root` binds;
`:shared-ui` assembles the screen tree. Before adding a dependency or module,
inspect `allowedLibraryTable` and `packageOwnersTable` in
`build-logic/convention/src/main/kotlin/tasktracker.architecture.gradle.kts`.
Never work around `verifyArchitectureGraph` or `verifyModuleImports`. Shared
visual tokens and Compose resources go in `:packages:design-system` (the only
module that configures `compose.resources`).

## Liquid Glass Design Tokens

All glassmorphism UI must bind to the established design primitives in
`:packages:design-system`. Use existing modifiers (e.g. `Modifier.liquidGlassCard()`)
instead of raw rendering logic. If a primitive is missing, map it in your Phase 1
plan before writing UI code. Do not guess or invent styling primitives — this
burns tokens through endless cosmetic iterations.

## The command that decides whether you are done

```bash
scripts/check.sh          # fast: JVM + graph + baseline + summary
scripts/check.sh full     # all three hosts; required for UI / expect-actual work
```

The script exports the JDK, runs Gradle with `--continue`, and treats only
failures named in `test-known-failures.json` as acceptable. Policy:
`docs/TESTING.md`. Read it when a test fails, not before every task.

## Test hosts

| Host | Task | State |
| --- | --- | --- |
| JVM | `jvmTest` | fast gate; known failures in `test-known-failures.json` |
| iOS | `iosSimulatorArm64Test` | required for rendering / expect-actual |
| Android | `testAndroidHostTest` | Robolectric; same allowlist |

## When a UI change needs a rendered-pixel test

Default to a structural assertion. Render pixels only when the property does not
survive being read off the modifier chain. **One question:** can you write the
failing assertion without rendering anything?

| Property | Assert |
|---|---|
| fill clipped to its own outline shape, modifier present/absent/ordered, token value or relationship, shown/hidden in tree | structural |
| ink contrast over translucent fill, value composited under translucent layer, silhouette/blur/hairline/antialiasing, anything a second rasteriser might disagree about | **pixels, both hosts** |

**Mandatory regardless of the table:** a change to a colour, alpha, fill ramp,
or any number a test already holds to an exact value → pixels. Two real defects
in this repo were one of these.

Structural still needs verification — delete or invert the modifier and confirm
the assertion goes red (seconds, not a build cycle).

## Mutation-check before you trust a green

A new test that has never failed is not evidence. Before believing any assertion:

1. Run it. Green.
2. Revert the fix in the production file.
3. Run again. **It must fail**, naming the actual defect.
4. Restore the fix. Run. Green.

If step 3 is green, the test is wrong — throw it away and write a different one.
**Mandatory** for pixel/contrast tests. **Optional** for structural assertions
(false-green danger is low, mutation is obvious). Write the failure message as
part of the test, with the measured number and both colours in it.

## Measure pixels, never model the composite

A correct token in the wrong compositing is a recurring defect here
(`GlassFillContrastTest`, tab bar captions). You cannot catch it by computing an
alpha blend — the blend is not what the compositor does. Render the real
component and read pixels with `tools/inspect_frame.py`. Run on **both** JVM and
iOS — they disagree (same token measured 5.37:1 on JVM vs 3.98:1 on iOS due to
stroke coverage differences).

## Diagnostic discipline

When a green test goes red and you do not know why, output this block before
theorizing:

```markdown
### DIAGNOSTIC RECONCILIATION
*   **Failing Scenario/Assertion:** [Name]
*   **Host Environment:** [JVM / iOS / Android]
*   **Expected vs Actual Primitives:** [Token Values/Hex/Dimensions]
*   **System State Summary:** [Dump of relevant Cache/Outbox/Router states]
```

Simple compile errors do not need this block — fix and move on. When
investigating, add **one** diagnostic that dumps everything, then read it once.
One `println` per hypothesis and rebuilding is prohibited.

**Prose last, with numbers frozen:** do not write explanatory comments while the
numbers are still moving. Investigate, freeze the numbers, then write the
rationale once.

## Multi-Agent Parallelism

If multiple agents work concurrently across worktrees:
- Use `git worktree add` — a directory switch shares build caches and generated
  state, so two agents on one tree will overwrite each other.
- Pass `--no-daemon` or isolate the cache dir to avoid Gradle daemon collisions.
- Before planning, `git fetch origin && git rebase origin/main` and verify no
  concurrent task touched your target scope.
- `test-baseline.json` is regenerated with any test addition — if a parallel
  agent commits a baseline update while you run, pull and regenerate from merged
  JUnit XML before requesting review.
- If two concurrent tasks touch the same package layer, they must land in
  separate files, not appended to shared module files.

## Task workflow: branch, push, PR, review, merge

Every task is one branch and one PR to `main`. Never commit directly to `main`.

```bash
git fetch origin
git switch main && git pull --ff-only
git switch -c feat/<kebab-case-task-slug>
# ... do the task, then:
scripts/check.sh          # must be green before pushing
git push -u origin feat/<kebab-case-task-slug>
gh pr create --base main --title "<what the task did>" --body "<what changed, and which command verified it>"
```

**Review before merge:** read your diff as a stranger (`gh pr diff <number>`).
Confirm: only planned files changed, no drive-by cleanup, behaviour changes ship
with tests, validation command is green. Run the `test-reviewer` subagent
(`.opencode/agent/test-reviewer.md`) against any **pixel-reading test** — not
needed for structural tests.

Merge when review is clean and PR is green:
```bash
gh pr merge <number> --merge --delete-branch
```
If the user asked to be the approver, wait for their explicit approval — your own
review passing is not their approval.

## Conventions

- **Comments explain why, and carry the measurement.** A change to a non-obvious
  value arrives with the number that justifies it. Routine tuning needs no
  paragraph.
- **Counts are generated, not written.** Host totals live in `test-baseline.json`,
  written by `scripts/test-baseline.py`. Regenerate in the same change as any test
  addition. Never hand-copy a total into `Project.md`, `AGENTS.md`, or a spec.
- **`NavigationHost.kt` and `:shared-ui` are large** — a broad scripted replace
  across a tree this size produces a diff nobody reads. Edit deliberately.
- **No drive-by cleanup.** Do not delete what looks unused while changing code —
  imports, helpers, parameters, files. Dead code you notice goes in your report
  to the user, never in the diff. Sweeping is a separately acknowledged task.

## Compose Multiplatform 1.12.1 specifics (do not violate)

1. `BasicText` has no `textAlign` parameter — pass alignment inside the `style` block.
2. `TextStyle` has no `overflow` — set it on `ParagraphStyle`.
3. `ImageVector.Builder.addPath` requires `fill: Brush`. Omitting it or passing
   a direct colour renders nothing, silently, even with a `ColorFilter` tint.
   Use `fill = SolidColor(Color.Black)` combined with colour tints.

## Splitting a presentation screen into files

The unit of a presentation file is **one public composable**, named after it.
A new `@Composable` gets its own file — never an append. Its private helpers,
tag constants, `@Preview`s, and file-local `const val`s move with it.

Split when a file passes ~400 lines or carries more than one top-level
`@Composable`. Below that, a screen and its helper read better together.
Reuse comes from the boundary — two screens that want the same row import one
file, not copy-paste.

This is a *presentation* rule — it does not change how models, use cases, or
domain code are organised.

## Skills

Skills under `.opencode/skills/*/SKILL.md` carry the detail this file omits.
**Read each skill's frontmatter `description` at session start.** Load full
instructions **on demand** — only when its description matches your task.

- `kmp-architecture-workflow` — Clean Architecture boundaries, the checked Gradle
  graph, KMP source sets, Liquid Glass design-system ownership.
- `kmp-compose-tests` — the three-host matrix, source-set layout, Android host
  gaps, the rasterisation skip, screenshot capture.
- `rendered-ui-measurement` — mutation-first ritual, twin-frame differencing,
  estimator robustness, measuring on two rasterisers.
- `kmp-desktop-entry` — Compose Desktop (JVM) target: entry point, classpath,
  run task, per-OS caveats.
- `kmp-room-database-migrations` — Room schemas, DAOs, outbox queues, builders,
  transactions, migration verification.

The `test-reviewer` subagent (`.opencode/agent/test-reviewer.md`) reviews a
proposed test adversarially — use it for pixel-reading tests only.

## Task detail and continuity

Use the concise `docs/PLAN.md` to choose work. Read the selected spec and its
named files; if no spec exists, read only that task's section in
`docs/PLAN-ARCHIVE.md` and create a spec before implementation. Keep the
spec's `## Handoff` current so another agent, tool, or session can resume from
the committed state. For cross-repository work, follow `../AGENTS.md` §6.

## Local rules

Rules true in only one package live next to the code they govern. A local file
may only contain rules that are *false elsewhere*:

- `packages/design-system/AGENTS.md` — token ownership, `compose.resources`, pixel tests
- `packages/database/AGENTS.md` — foreign keys, DAO ownership, migrations
- `packages/feature-projects-presentation/AGENTS.md` — file splitting, fixtures, previews
- `shared-ui/AGENTS.md` — router ownership, host-split tests, deliberate edits in a large tree

Working in one of those packages, read both files. Working anywhere else, this
file is the whole contract.
