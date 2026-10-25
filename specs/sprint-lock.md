# Sprint lock — an owner-applied lock replaces the start-date edit freeze

- **status:** done
- **priority:** P2
- **plan:** no `docs/PLAN.md` entry — standalone cross-repo feature. The server
  half is [`../../TaskTrackerServer/specs/sprint-lock.md`](../../TaskTrackerServer/specs/sprint-lock.md).

## Scope

- `:packages:domain` — `Sprint.isLocked`, synced, default `false`.
- `:packages:database` — `SprintRecord.isLocked`, version 7, `7.json`.
- `:packages:feature-projects-data` — `SprintRecord` mapping,
  `setSprintLocked`, and the window/cap refusal in `updateSprint`.
- `:packages:feature-projects-domain` — `ProjectRepositoryPort.setSprintLocked`,
  `LockSprint`/`UnlockSprint`, the lock refusal in `UpdateSprint` and
  `DeleteSprint`, and the membership rule now keyed on the lock instead of
  `isActiveOn`.
- `:packages:feature-projects-presentation` — `SprintEditorModel.windowLocked`,
  `SprintDetail.canManageLock`, `SprintDetailModel.lock`/`unlock`, the detail
  card's lock/unlock control and its note.
- `:packages:design-system` — `sprint_lock`, `sprint_unlock`,
  `sprint_locked_note`, and the rewritten editor note.
- `:shared-ui` — the `SprintDetailDestination` wiring.

## Contract

- [x] The old rule is gone: a sprint whose start date has passed is editable.
      It is the explicit flag, not the clock, that freezes anything.
- [x] A locked sprint refuses window and point-cap edits
      (`UpdateSprint` and the store both refuse), and refuses task membership
      changes in both directions (`AddWorkItemsToSprint`,
      `RemoveWorkItemsFromSprint`).
- [x] A locked sprint still accepts its description: name, prose goal and owner
      stay editable, and the editor keeps their fields live.
- [x] The owner can unlock and edit everything again. Locking is not a
      one-way door, which is the whole point of replacing the start-date rule.
- [x] The lock is owner-gated on the detail card (`canManageLock`), the same
      rule as Finish; an unowned sprint can be locked by anyone.
- [x] A finished sprint is frozen whether or not it is locked — the old rule's
      answer for it is kept — and the detail card offers no lock control over
      a finished sprint.
- [x] `DeleteSprint` refuses a locked sprint, and the card's remove button is
      disabled for one, so a sprint on hold cannot disappear from under its
      task list.
- [x] The flag round-trips through Room and the outbox, so a lock reaches every
      device as an ordinary sprint upsert.
- [x] Room bumps to 7 and regenerates `7.json`; historical schemas are
      untouched.

## Validation

- `./gradlew --offline --no-parallel --max-workers=1 :packages:feature-projects-domain:jvmTest --tests '*SprintUseCasesTest*'`
- `./gradlew --offline --no-parallel --max-workers=1 :packages:feature-projects-data:jvmTest --tests '*RoomSprintRepositoryTest*' --tests '*RecordCompletenessTest*'`
- `./gradlew --offline --no-parallel --max-workers=1 :packages:feature-projects-presentation:jvmTest --tests '*SprintEditorModelTest*' --tests '*SprintDetailModelTest*'`
- `./gradlew --offline --no-parallel --max-workers=1 :shared-ui:compileTestKotlinJvm`
- `scripts/check.sh` was not run here (the user asked to skip it); the filtered
  JVM runs above cover every touched module's unit surface. This spec has no
  pixel-reading test: the new control is asserted structurally by its test tag
  and by the model's `canManageLock`/`isLocked`.

## Handoff

- **Done:** domain, database, data, use cases, models, both sheets, navigation,
  string resources and tests.
- **Pending:** nothing in this repo.
- **Next step:** merge alongside the server half; the client declares
  `isLocked` with a default, so it works against a server that does not send it
  yet.
- **Gotchas:**
  - `windowLocked` is `isLocked || isFinished`. Dropping the `isFinished` half
    would hand a closed sprint's window back to the editor, which the old
    start-date rule never allowed.
  - The lock lives in `setSprintLocked` rather than `updateSprint` so the write
    is one column and one outbox row; an `updateSprint(isLocked = …)` call
    would also have to satisfy the lock refusal it is trying to lift.
  - `SprintDetail.canEditItems` no longer reads `isActive`. Anything else that
    used "active" as a proxy for "committed" needs the same move to the flag.
