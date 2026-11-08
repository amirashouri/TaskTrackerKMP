# agent-management — Manage project agents and runs

- **status:** in-progress
- **priority:** P1
- **context:** Plan for project repository metadata and a client UX for pairing
  agents and applying them to tasks on a local workstation or VPS. The deferred
  wire contracts are now backed by the server; the remaining work is a green
  release gate before this spec can be retired.

## Scope

The KMP client provides project repository URL editing, persists/syncs the
optional metadata offline-first, and provides admin-facing agent setup and
status plus task-run initiation and progress/result views. The client is a
control panel; the agent performs repository work on its own machine, whether
that is the admin's desktop or a VPS.

## Files

The implementation file list was confirmed during implementation. No KMP source
file was edited in this session: the wire DTOs already declared `host`,
`runnerId`, `requestedRunnerId` and `diff` with defaults, and the UI already
rendered the host label, the dispatch target picker and the run diff sheet.

- `packages/domain/src/commonMain/kotlin/com/tasktracker/domain/Entities.kt` —
  optional project repository URL on the client project model.
- `packages/database/src/commonMain/kotlin/com/tasktracker/database/ProjectRecord.kt`
  — URL persisted in local project storage.
- `packages/feature-projects-data/.../HttpAdminProjectsRemote.kt`,
  `HttpAgentRunsRemote.kt`, `HttpAgentPairingRemote.kt` — project and agent DTOs.
- `packages/feature-settings-data/.../HttpAgentRunnersRemote.kt` — runner roster
  DTO, including nullable `host`.
- `packages/feature-projects-presentation/.../project/ProjectAgentSection.kt`,
  `workitem/WorkItemAgentSection.kt` — host label, pairing sheet, target picker,
  run diff sheet; structural coverage in `AgentManagementScenarios`.
- `docs/DECISIONS.md` — durable wire-name and `host` decisions promoted from this
  work. `TaskTrackerServer/specs/agent-wire-contracts.md` is the matching server
  handoff.

## Contract

- [x] An optional project repository URL is editable by an authorized admin
  and survives local persistence, restart, and synchronization. Its wire name
  is exactly `repoUrl`.
- [x] The KMP DTO declares the repository URL as optional with a compatible
  default, so old server responses and existing local records continue to
  decode.
- [x] Newly added server response fields are not assumed to be ignored by
  installed clients. Client support and server response rollout use the
  compatibility strategy agreed with the server spec: the client shipped the
  defaulted DTOs first, the server emitted second.
- [x] An admin can initiate agent pairing from the client and see clear success,
  expiry, failure, and retry states. Pairing secrets are shown only as required
  for pairing and are not persisted in ordinary project data or logs.
- [x] The client distinguishes agents assigned to the project, shows their
  machine/connection health, and makes the execution host clear: a nullable
  `host` becomes the desktop/remote label and "not stated" is not a third kind.
  The server now backs that label with `agent_runners.host` /
  `agent_pairing_codes.host`, and `tt agent connect --pairing-code` is the
  bootstrap the copied command in the pairing sheet invokes.
- [x] Applying an agent requests a server-managed run with a stable `runId`;
  ordinary task status edits or sync replay do not accidentally create runs.
  The target picker's `runnerId` is persisted by the server and returned as
  `requestedRunnerId`.
- [x] The client displays queued, running, succeeded, failed, and offline/
  unavailable states from server-owned run state. It reports connectivity
  failures honestly and does not present an unacknowledged run as started.
- [x] Offline behavior is explicit: a dispatch requires a live connection and
  fails honestly; task status is never overloaded as an execution command.
- [x] The run diff sheet is backed by a real `diff` field on the run DTO, not
  only a faked `AgentRun`; the server returns it from the runner's finish call.
- [x] Tests cover serialization, local mapping/persistence, request idempotency
  behavior where owned by the client, and observable UI state. Tests fail when
  the production behavior is removed.
- [x] Common code introduces no platform-specific APIs without an explicit
  `expect`/`actual` contract.

## Subtasks and dependencies

All six original subtasks are complete. The deferred host-label, `runnerId`
targeting and result-diff contracts were agreed by cross-repo ACK with the shipped
wire names (`host`, `runnerId` → `requestedRunnerId`, `diff`); the spec's earlier
`requestedRunnerID` / `resultDiff` spelling was never on the wire and a case-only
rename would break installed builds. No KMP DTO edit was needed.

## Non-goal

- [x] The KMP app does not itself clone repositories or execute agent code.
- [x] The app does not store long-lived agent credentials or private repository
  access tokens in project metadata.
- [x] This task does not promise unattended run creation while offline unless
  the server/client contract explicitly supports a separate queued run command.

## Validation

- `scripts/check.sh quick <module> '<TestClass or pattern>'` for focused tests.
- `scripts/check.sh full` as the release gate; follow the test-baseline rules and
  do not alter baselines to hide failures.

**Results (2026-11-08):** the four `AgentManagementScenarios` JVM entry points
(roster host/health, pairing host choice, dispatch target picker, terminal run with
a diff), `HttpAgentRunsRemoteTest`, `HttpAgentPairingRemoteTest` and
`HttpAgentRunnersRemoteTest` all pass. `scripts/check.sh full` is **red** — see
Handoff. `test-baseline.json` and `test-known-failures.json` were left untouched.

## Handoff

- **Done:** `repoUrl` carried through `Project`, `ProjectRecord` (schema v2), record
  mapping, admin DTOs, create/edit models and both project forms; the server emits
  `repoUrl,omitempty`. Project-scoped pairing, the machine roster with derived
  health, the `agentEnabled` switch, the dispatch target picker, paged event cursor
  behind a full-log sheet, and the run diff sheet are all implemented. Settings
  shows each machine's health and host beside its model summary. The three deferred
  wire contracts are now backed by the server: `host` is stored on the runner and
  pairing code, `runnerId` is persisted and returned as `requestedRunnerId`, and
  `diff` is returned from finish. `tt agent connect --pairing-code` redeems the
  one-use code the pairing sheet tells the admin to copy. The shipped wire names
  and `host` semantics are promoted into `docs/DECISIONS.md` and
  `TaskTrackerServer/docs/ARCHITECTURE.md`.
- **Pending:** `scripts/check.sh full` on pristine `main` reports six failures per
  host (18 total): `SprintSummaryTest#theDoneAndIncompleteSplitAgreesWithTheArchivePredicate`,
  three `WorkItemGroupingTest`s, and two `TaskTrackerWide*Test`s
  (`aWideShellCentresTheSheetLikeAWindow`,
  `creatingAProjectShowsItInTheSidebarWithoutARelaunch`). Only a markdown file was
  modified for that run, and `WorkItemGroupingTest` was reproduced red by a focused
  run. `test-known-failures.json` is empty and `test-baseline.json` still records
  zero failures with older totals. These must be fixed under their own work, not
  here; the baseline must only be regenerated from a green run.
- **Next step:** get the full gate green, then delete this spec and
  `TaskTrackerServer/specs/agent-wire-contracts.md` in their final commits. No
  further agent-management implementation is outstanding.
- **Gotchas:** `WorkItemGrouping.resolved` filters `isArchived` while the four
  failing domain tests build terminal items without archiving them; those tests
  fail on `main` too and are not caused by this slice. The two wide-shell UI
  failures are likewise pre-existing and unrelated to the wire contracts. A
  dispatch retry reuses the idempotency key only while the model's
  `pendingDispatchKey` is held; closing the editor drops it by design. The machine
  roster is filtered client-side from the account-scoped runner route; a legacy
  runner with no `projectId` is not shown, and an empty `host` is "not stated",
  not `desktop`.

## Risks / notes

- The desktop app can guide local setup, but an agent must run as its own
  process on the chosen execution machine. A VPS agent can be paired from the
  client and should connect outward through NAT/firewalls.
- Make the difference between agent pairing, agent connectivity, run acceptance,
  and task completion visible to users.
- Desktop-local and remote/VPS pairing share the same server contract and differ
  only in setup guidance; avoid platform-specific assumptions in common code.
- Keep project repository URL distinct from repository authorization. Private
  repository access is provisioned to the agent securely, not sent as ordinary
  project metadata.
- The client is the fixed point of the wire: `ignoreUnknownKeys` is off, so a
  gained, lost or case-renamed response key is a hard break for installed builds.
