# <Task ID or slug> — <imperative title>

- **status:** pending | in-progress — delete this file to finish the task; there is no `done` state
- **priority:** P0 | P1 | P2 | P3
- **branch:** `feat/<kebab-case-slug>` — blank until cut; work never starts on `main`
- **blocked by:** none | `<spec path or release it waits on>`
- **context:** 1–4 lines on where the work came from (a sync log, a device finding, a follow-up
  spec). For cross-repo work, name the companion spec in `../TaskTrackerServer/specs/` and state
  the landing order. `specs/` is the only index; this file is self-contained and holds the task's
  scope, contract, validation and handoff.

## Scope

The modules, layers, and targets this task may touch. If a file outside this list has to change,
that is the Drift Constraint in `AGENTS.md` — abort, re-plan, get a second `ACK`.

- **Targets affected:** commonMain | commonTest | androidMain | androidHostTest | iosMain |
  iosTest | jvmMain | jvmTest
- **Modules affected:** exact `settings.gradle.kts` names, e.g. `:packages:domain`, `:shared-ui`,
  `:composition-root`

## Files

Filled in at Phase 1 (the ACK'd 3-bullet plan). Until then: `to confirm at Phase 1`.

- `path/to/File.kt` — what changes and why.

## Diagnosis

Bug specs only; delete the heading for feature work. The root cause, not the symptom — what is
actually wrong, and why the obvious fix is the wrong one. This is what stops the next agent from
re-diagnosing.

## Contract

What must be true when the task is done. Each line is falsifiable — the `test-reviewer` checks
these, not good intentions. Name the test that proves a line when one exists.

- [ ] <an observable property a test pins> (`<TestClass>.<testName>`)
- [ ] <the property still holds when the production change is reverted (Rule 1)>

**KMP Constraint:** no platform-specific APIs (`java.*`, `android.*`, …) in `commonMain` without
an explicit `expect/actual` contract, and an `actual` lands in the same commit as its `expect`.

## Non-goal

Plain bullets — a non-goal is a boundary, not a checklist item. Refactoring specs should name the
boundary so they do not become catch-all cleanup.

- <one explicit thing out of scope, and where it is tracked instead>

## Validation

The commands for *this* task, and what a focused run cannot prove. Scopes and the release gate
live in `AGENTS.md` §3; do not restate them here.

- **Iteration:** `scripts/check.sh quick <module> '<ClassOrPattern>'` for each touched module.
- **Before handoff:** <the scope that covers this change — `fast`, or `full` when the task
  touches `expect`/`actual`, visual tokens, or the build graph>
- **Counts / Baselines:** how the counts should move; `test-baseline.json` is regenerated from a
  green full run, never hand-edited.
- **Stays pending:** <what a focused run cannot prove — a device install, a CI tag run>

## Handoff

Filled at the end of each session. Carries state across agents, tools, and sessions; a committed
spec does not depend on chat context.

- **Done:** <what is finished and verified>
- **Pending:** <what remains>
- **Next step:** <the single next concrete action>
- **Gotchas:** <what is easy to get wrong here, and the constraints or decisions the next agent
  must not re-litigate — written before coding, corrected after>

## Promote

What survives this spec when it is deleted. The spec is removed in the final task-branch commit,
so anything durable moves first.

- **To `docs/DECISIONS.md`:** <section and the rule in one sentence, or "nothing — this is
  plumbing, not a rule">
