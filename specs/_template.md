# <PLAN-id> — <imperative title>

- **status:** pending | in-progress | done
- **priority:** P0 | P1 | P2 | P3
- **plan:** [`docs/PLAN.md`](../docs/PLAN.md) §<section>, entry `<id>` — the *what* and the
  rationale live there; this file is the *how*.

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

- Run: `scripts/check.sh <scope>`
- Counts: <how `test-baseline.json` should move — which host(s), and why>. Regenerate with
  `scripts/test-baseline.py`; `scripts/check-baseline.sh` must pass.
- Tests added go through the `test-reviewer` subagent before the PR.

## Risks / notes

- <what is easy to get wrong here>
