# agent-management — Manage project agents and runs

- **status:** in-progress
- **priority:** P1
- **context:** Plan for project repository metadata and a client UX for pairing
  agents and applying them to tasks on a local workstation or VPS.

## Scope

The KMP client provides project repository URL editing, persists/syncs the
optional metadata offline-first, and provides admin-facing agent setup and
status plus task-run initiation and progress/result views. The client is a
control panel; the agent performs repository work on its own machine, whether
that is the admin's desktop or a VPS.

## Files

Confirm the implementation file list in the implementation plan before editing.
Known project model, persistence, and remote API files:

- `packages/domain/src/commonMain/kotlin/com/tasktracker/domain/Entities.kt` —
  add optional project repository URL to the client project model.
- `packages/database/src/commonMain/kotlin/com/tasktracker/database/ProjectRecord.kt`
  — persist the URL in local project storage.
- `packages/feature-projects-data/src/commonMain/kotlin/com/tasktracker/featureprojectsdata/HttpAdminProjectsRemote.kt`
  — map the optional URL through supported project request/response DTOs.

The project editor, sync mappings, agent-management UI, agent/run domain and
data modules, navigation, and tests are to be confirmed in the implementation
plan. The implementation must follow KMP module boundaries and include all
required platform `actual`s if any are introduced. Do not expand the approved
implementation scope without a secondary ACK.

## Contract

- [x] An optional project repository URL is editable by an authorized admin
  and survives local persistence, restart, and synchronization. Its wire name
  is exactly `repoUrl`.
- [x] The KMP DTO declares the repository URL as optional with a compatible
  default, so old server responses and existing local records continue to
  decode.
- [x] Newly added server response fields are not assumed to be ignored by
  installed clients. Client support and server response rollout use the
  compatibility strategy agreed with the server spec.
- [x] An admin can initiate agent pairing from the client and see clear success,
  expiry, failure, and retry states. Pairing secrets are shown only as required
  for pairing and are not persisted in ordinary project data or logs.
- [ ] The client can distinguish agents assigned to the project, show their
  machine/connection health, and make the execution host clear (local desktop
  or remote/VPS agent). The project editor now lists the project's live machines
  with derived health, but the host label still needs a server field.
- [x] Applying an agent requests a server-managed run with a stable `runId`;
  ordinary task status edits or sync replay do not accidentally create runs.
- [x] The client displays queued, running, succeeded, failed, and offline/
  unavailable states from server-owned run state. It reports connectivity
  failures honestly and does not present an unacknowledged run as started.
- [x] Offline behavior is explicit: a dispatch requires a live connection and
  fails honestly; task status is never overloaded as an execution command.
- [x] Tests cover serialization, local mapping/persistence, request idempotency
  behavior where owned by the client, and observable UI state. Tests fail when
  the production behavior is removed.
- [x] Common code introduces no platform-specific APIs without an explicit
  `expect`/`actual` contract.

## Subtasks and dependencies

1. **Carry project repository metadata end to end.** Add an optional field to
   the domain entity and local record, update migrations/mappings, project
   create/edit UI, and remote request/response DTOs. This is the foundation for
   displaying project repository metadata to the agent.
2. **Add agent pairing and project assignment UX.** Provide an admin flow that
   requests a short-lived pairing code from the server and guides the admin to
   run/pair an agent on the intended machine. Show pairing expiry, errors,
   agent identity, assignment, and health. Support local desktop and remote/VPS
   setup without assuming the server can reach the agent.
3. **Add agent and run client contracts.** Define KMP domain/data interfaces
   and DTOs matching the server spec for listing agent state, creating a run,
   and observing run state. This depends on the server API contract being
   implemented and verified.
4. **Add task-level agent application UX.** Allow an authorized user to choose
   an available project agent and explicitly start a run. Show the execution
   host and repository metadata; distinguish an accepted/queued run from actual
   execution.
5. **Show run progress and results.** Present server-reported lifecycle state,
   failure, and completion details. Refresh/poll using existing client patterns
   and avoid creating duplicate runs during retries or offline sync.
6. **Test and verify.** Add focused domain/data/presentation tests for changed
   modules, serialization and persistence coverage, and any required UI
   structural assertions. Validate architecture constraints and record
   platform/render checks that remain pending.

## Non-goal

- [ ] The KMP app does not itself clone repositories or execute agent code.
- [ ] The app does not store long-lived agent credentials or private repository
  access tokens in project metadata.
- [ ] This task does not promise unattended run creation while offline unless
  the server/client contract explicitly supports a separate queued run command.

## Validation

- Use `scripts/check.sh quick <module> '<TestClass or pattern>'` for focused
  tests in each changed module.
- Run `scripts/check.sh` or `scripts/check.sh fast` before handoff for JVM unit
  tests and architecture graph/import checks, as required by the owning task
  plan.
- If `expect`/`actual`, build graph, or rendering behavior changes, run
  `scripts/check.sh full` when available; otherwise record the unverified
  targets as pending.
- Follow the KMP test-baseline rules if structural suites change. Do not alter
  baselines to hide failures.

## Handoff

- **Done:** `repoUrl` carried through `Project`, `ProjectRecord` (schema v2),
  record mapping, admin DTOs, create/edit models and both project forms; the
  server now emits `repoUrl,omitempty`. Project-scoped pairing is implemented as
  `AgentPairingPort` / `HttpAgentPairingRemote` / `ProjectAgentSetupModel` and a
  pairing sheet on the admin editor, with one-time code, expiry, failure and
  retry states and no persistence. The project editor now reads the project's
  live machines through the same port and shows running/offline/stopped derived
  from one heartbeat observation, refreshing after the pairing sheet closes.
  Both project forms carry the per-project `agentEnabled` switch, the work-item
  agent block shows the project repository, and the run section pages the event
  cursor behind a full-log sheet. Settings shows each machine's health beside
  its model summary instead of replacing it. Dispatch still carries a stable
  idempotency key.
- **Pending:** A local-desktop vs remote/VPS host label needs a server runner
  field; choosing a specific project agent for a dispatch needs a server
  `runnerId` on the run contract; a diff/PR view needs an agent result
  contract, not just a longer log. The pairing CLI flag (`tt agent connect
  --pairing-code`) is still not implemented. Structural Compose scenarios for
  the roster and toggle have not been added; model and serialization tests
  cover the behaviour.
- **Next step:** Agree the server runner host field and run `runnerId` with the
  server spec, then add the CLI flag and run `scripts/check.sh full` as the
  release gate.
- **Gotchas:** `WorkItemGrouping.resolved` filters `isArchived` while three
  existing domain tests build terminal items without archiving them; those tests
  fail on `main` too and are not caused by this slice. A dispatch retry reuses
  the key only while the model's `pendingDispatchKey` is held; closing the
  editor drops it by design. The machine roster is filtered client-side from
  the account-scoped runner route; a legacy runner with no `projectId` is not
  shown.

## Risks / notes

- The desktop app can guide local setup, but an agent must run as its own
  process on the chosen execution machine. A VPS agent can be paired from the
  client and should connect outward through NAT/firewalls.
- Make the difference between agent pairing, agent connectivity, run acceptance,
  and task completion visible to users.
- Desktop-local and remote/VPS pairing should share the same server contract
  and differ only in setup guidance; avoid platform-specific assumptions in
  common code.
- Keep project repository URL distinct from repository authorization. Private
  repository access is provisioned to the agent securely, not sent as ordinary
  project metadata.
