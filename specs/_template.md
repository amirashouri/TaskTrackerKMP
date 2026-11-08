# <Task ID or slug> — <imperative title>

- **status:** pending | in-progress | done (`done` means validated, reviewed, and ready to merge)
- **priority:** P0 | P1 | P2 | P3
- **context:** optional — where the work came from (a sync log, a device finding,
  a follow-up spec). `specs/` is the only index; this file is self-contained and
  holds the task's scope, contract, validation and handoff.

## Scope

The modules, layers, and targets this task may touch. If a file outside this list has to change,
that is the Drift Constraint in `AGENTS.md` — abort, re-plan, get a second `ACK`.

- **Targets affected:** commonMain | androidMain | iosMain | desktopMain
- **Modules affected:** e.g., `packages:core`, `packages:sharedui`

## Files

Filled in at Phase 1 (the ACK'd 3-bullet plan). Until then: `to confirm at Phase 1`.

- `path/to/File.kt` — what changes and why.

## Contract

What must be true when the task is done. Each line is falsifiable — the `test-reviewer`
checks these, not good intentions.

- [ ] <an observable property a test pins>
- [ ] <the property still holds when the production change is reverted (Rule 1)>
- [ **KMP Constraint:** No platform-specific APIs (`java.*`, `android.*`, etc.) introduced into `commonMain` without an explicit `expect/actual` contract.]

## Non-goal

What this task must not do. Refactoring specs should name the boundary so they do not
become catch-all cleanup.

- [ ] <one explicit thing out of scope>

## Validation

- **Iteration Phase (Use during coding):**
  Run: `scripts/check.sh quick <module> '<TestClass>.<testName>'`
  *(Fast, keeps test XML caches, skips baselines. Run this frequently to iterate under seconds).*
- **Handoff Phase (Run once before marking done):**
  Run: `scripts/check.sh` (or `scripts/check.sh fast`) to execute JVM unit tests + architecture graph checks (`verifyArchitectureGraph`, `verifyModuleImports`).
- **Counts / Baselines:**
  If structural test suites change, `test-baseline.json` may shift. Regenerate via a full run if needed (`scripts/check.sh full` — note: requires macOS for iOS target).
- **UI / Pixel-reading tests:**
  Go through the `test-reviewer` subagent before the PR. Structural and behavioral tests do not need this review.

## Handoff

Filled at the end of each session. Carries state across agents, tools, and
sessions; a committed spec does not depend on chat context.

- **Done:** <what is finished and verified>
- **Pending:** <what remains>
- **Next step:** <the single next concrete action>
- **Gotchas:** <constraints or decisions the next agent must not re-litigate>

## Risks / notes

- <what is easy to get wrong here, e.g., Compose state preservation across recompositions, KMP expect/actual platform gotchas>