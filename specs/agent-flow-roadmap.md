# Agent workflow — align task lifecycle and roadmap guidance

- **status:** done
- **priority:** P2
- **plan:** none — standalone documentation maintenance.

## Scope

Make the client agent workflow explicit and provider-neutral, clarify task-spec
status and handoff rules, and separate the active roadmap from detailed history.
Preserve the KMP-specific build, ACK, architecture, and test-host requirements.
Do not change app behavior or wire contracts.

## Files

- `AGENTS.md` — link the active roadmap/archive and define spec status and handoff
  steps within the existing KMP workflow.
- `docs/PLAN.md` — concise status index of active and completed areas, pending
  tasks, dependencies, and detail links.
- `docs/PLAN-ARCHIVE.md` — preserve the prior detailed roadmap.
- `specs/README.md` and `specs/_template.md` — clarify source-of-truth roles,
  reading budget, provider-neutral continuity, and when a spec becomes done.
- `docs/TEST-HARNESS-PLAN.md` — correct its roadmap relationship.
- Existing pending specs and the Desktop skill — update links or status claims
  affected by the roadmap split.
- This spec — record the work and handoff.

## Contract

- [x] The active roadmap lists all pending feature, server-connection, and test
  harness work with current states and links to details.
- [x] The previous detailed feature and admin plans remain preserved in the
  archive.
- [x] Agent guidance says to read only the selected spec or archive section,
  keep status in-progress through validation/review, and mark done before merge.
- [x] KMP-specific ACK, three-host validation, baseline, and test-review rules
  remain in force.

## Validation

- Run `scripts/check.sh docs` for documentation-only changes.
- Run `git diff --check`.
- Validate local Markdown links in the changed documents.
- Verify the archived task details match the original plan.

## Handoff

- **Done:** Created a concise roadmap and preserved the detailed roadmap;
  updated agent workflow, spec lifecycle, test-harness-plan reference, existing
  plan links, and the Desktop skill's stale status.
- **Pending:** Push and open a PR; no remote operation was requested.
- **Next step:** Push this branch when authorized, then follow the KMP PR
  workflow.
- **Gotchas:** KMP portable skills live in `.agents/skills/`; OpenCode-specific
  agents remain under `.opencode/`. Server-connection tasks 6.1–6.4 require the
  cross-repo ACK protocol even when a particular client-only slice does not
  change the shared link contract.

## Risks / notes

- Do not remove detailed pending task notes from the archive until each task's
  spec captures its acceptance criteria and implementation constraints.
