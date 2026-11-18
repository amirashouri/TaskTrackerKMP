# delete-supersedes-unsent-upserts — A queued delete must outrank the state it deletes

- **status:** in-progress
- **priority:** P1
- **context:** Follow-up found while reviewing #81 (`outbox-work-item-coalescing`). Same
  ordering class, opposite operation: the create/update side now coalesces, the delete side
  neither coalesces nor orders itself after the row it contradicts.

## Scope

- **Targets affected:** commonMain, commonTest
- **Modules affected:** `packages:feature-projects-data`

## Files

- `packages/feature-projects-data/src/commonMain/kotlin/com/tasktracker/featureprojectsdata/RoomProjectRepository.kt`
  — `enqueueDelete` drops the entity's `pending`/`failed` upserts before inserting the
  delete (generic, so every delete path gets it); `deleteWorkItem` and `deleteProject` stamp
  their delete rows with `dateProvider.now()` instead of the entity's `updatedAt`.
- `packages/feature-projects-data/src/commonTest/kotlin/com/tasktracker/featureprojectsdata/RoomProjectRepositoryTest.kt`
  — three regressions: item created then deleted before any push; delete queued while the
  upsert is `inFlight`; project created offline then deleted with a sprint and an item.

## Diagnosis

Two defects, and either alone is enough to resurrect a deleted item.

1. **Nothing supersedes the unsent upsert.** `createWorkItem` queues a full-state upsert; if
   the user deletes the item before the queue drains, both rows exist. `OutboxDao.claimable`
   orders them by `createdAt` and then a random uuid, so the delete can be claimed first.
2. **The delete is stamped with the wrong instant.** `deleteWorkItem` and `deleteProject`
   pass `item.updatedAt` / `project.updatedAt` — the entity's *last edit* — as the outbox
   row's `createdAt`. So even with distinct instants the delete sorts at or before the
   upsert it should follow.

The server completes the failure: `resolveProject` treats a delete of an unknown id as a
no-op ("for a delete that is a no-op", `engine.go`), and `WorkItemRepository.Delete` is an
unconditional `DELETE … WHERE id`. So the delete is accepted and does nothing, the upsert
lands after it, and the item is back on the next pull — the user's deletion is lost with no
error anywhere.

## Contract

- [ ] Deleting a work item whose upsert has never been pushed leaves **exactly one** queued
      row for it, and that row is the delete.
- [ ] Deleting a project created offline leaves **no upserts** for the project or for its
      sprints and items — only delete rows.
- [ ] A delete queued while the entity's upsert is `inFlight` leaves both rows and is stamped
      strictly later than the upsert, so `claimable` orders upsert-then-delete.
- [ ] `dead` rows are still never withdrawn; a refusal the user is looking at survives.
- [ ] The property still holds when the production change is reverted (Rule 1).
- [ **KMP Constraint:** No platform-specific APIs introduced into `commonMain`; no signature,
      port, or wire change.]

## Non-goal

- [ ] No change to `createdAt` semantics for upserts.
- [ ] No change to child rows of a different entity: an unsent *comment* upsert for an item
      that is then deleted still queues and is still rejected by the server. That is the same
      class of bug one level down and deserves its own task; `deleteWorkItem` already
      documents that comments go silently.
- [ ] No schema change, no server change, no wire change.

## Validation

- **Iteration Phase (Use during coding):**
  Run: `scripts/check.sh quick packages:feature-projects-data 'RoomProjectRepositoryTest'`
- **Handoff Phase (Run once before marking done):**
  Run: `scripts/check.sh full` then `scripts/test-baseline.py` if counts move.
- **Counts / Baselines:** three added tests; regenerate, never hand-edit.

## Handoff

- **Done:** diagnosis and scope.
- **Pending:** implementation, tests, gate.
- **Next step:** write the three failing tests first, then the two production edits.
- **Gotchas:** coalescing belongs in `enqueueDelete`, not in each caller — the sprint and
  attachment paths queue deletes too and have the same defect. `dateProvider.now()` is the
  precedent already used by `deleteAttachment`.

## Risks / notes

- Deleting a project queues several deletes at one `now`, so their tie breaks on entity rank
  (project first). Safe: the server's deletes are unconditional and tolerate missing rows,
  and the log order for deletes carries no state.
- `TestDateProvider` starts at `Moments.start`, the same instant most tests create at — the
  in-flight test must `clock.advance(...)` before deleting or the ordering assertion is a tie.
