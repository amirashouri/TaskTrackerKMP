# <Task ID or slug> — <imperative title>

- **status:** pending | in-progress | done (`done` means validated, reviewed, and ready to merge)
- **priority:** P0 | P1 | P2 | P3
- **plan:** [`docs/PLAN.md`](../docs/PLAN.md) §<section>, entry `<id>`, or
  `none` for standalone work — the active roadmap is the index; this file is
  the task's implementation contract.

## Scope

The modules and layers this task may touch. If a file outside this list has to change,
that is the Drift Constraint in `AGENTS.md` — abort, re-plan, get a second `ACK`.

## Files

Filled in at Phase 1 (the ACK'd 3-bullet plan). Until then: `to confirm at Phase 1`.

- `path/to/File.kt` — what changes and why.

## Contract

What must be true when the task is done. Each line is falsifiable — the `test-reviewer`
checks these, not good intentions.

- [ ] <an observable property a test pins>
- [ ] <the property still holds when the production change is reverted (Rule 1)>

## Validation

- Run: `scripts/check.sh quick`
- Counts: <how `test-baseline.json` should move — which host(s), and why>. Regenerate with
  `scripts/test-baseline.py`; `scripts/check-baseline.sh` must pass.
- Pixel-reading tests go through the `test-reviewer` subagent before the PR.
  Structural and behavioral tests do not need this review.

## Handoff

Filled at the end of each session. Carries state across agents, tools, and
sessions; a committed spec does not depend on chat context.

- **Done:** <what is finished and verified>
- **Pending:** <what remains>
- **Next step:** <the single next concrete action>
- **Gotchas:** <constraints or decisions the next agent must not re-litigate>

## Risks / notes

- <what is easy to get wrong here>
