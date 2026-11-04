# TaskTrackerKMP — Active roadmap

This is the concise index of active and completed roadmap areas. A spec is the
implementation contract for work in progress. A completed task leaves no spec:
only its durable decisions survive, in
[`docs/DECISIONS.md`](DECISIONS.md#6-notes-kept-from-completed-task-specs).

## How to use this roadmap

- Choose an active item below and respect its dependencies and stated order.
- Read its spec in `specs/` if one exists. Create and review a spec
  before implementation.
- Keep one task on one branch and one PR. Do not bundle neighboring work.
- Follow `AGENTS.md` for ACK, host, test-review, validation, and merge rules.

## 10 — Mobile LAN parity

Mobile clients reach the plain-HTTP `lan` provider like desktop: the platform
transport gates (Android's cleartext allow-list, iOS ATS plus the local-network
prompt) are lifted above the unchanged URL validator policy, and the Android
local-network permission acceptance closes with it.

| Task | State | Intent | Depends on | Execution details |
| --- | --- | --- | --- | --- |
| 10.1 | In progress | Mobile plain-HTTP LAN parity | Android local-network permission acceptance | [`specs/mobile-lan-plain-http.md`](../specs/mobile-lan-plain-http.md) |

## 11 — Agent runs

A work item in progress can be handed to an LLM agent that runs on the machine
holding the code (`tt agent run`). The app dispatches the run and follows its
progress: status, latest step, event log, cancel. Agent state is server-owned and
polled, so nothing enters the sync envelope, no DTO gains a field, and v1 adds no
Room table. The server half is `TaskTrackerServer` Milestone 10.

| Task | State | Intent | Depends on | Execution details |
| --- | --- | --- | --- | --- |
| 11.1 | Complete | Agent-run IDs, domain models, `AgentRunsPort`, HTTP remote, DI binding | Server `T10.3` | — |
| 11.2 | Complete | Dispatch and live progress in the work item editor | 11.1 | — |

Both specs are gone; the decisions worth keeping are in
[`DECISIONS.md` §6](DECISIONS.md#6-notes-kept-from-completed-task-specs).

11.1 lands before 11.2, and both stay inside the existing `feature-projects-*`
modules: the only surface is the work item, and an `feature-agent-*` triple is
extracted only if a standalone screen arrives.
