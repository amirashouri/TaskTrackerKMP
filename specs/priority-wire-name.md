# Priority travels in lowercase on the wire

- **status:** in-progress
- **priority:** P0
- **plan:** no `docs/PLAN.md` entry — this was found from a device's sync logs rather than
  picked off the roadmap. It has no PLAN-id, so this file *is* the record. Add a PLAN entry
  if it is ever scheduled as roadmap work rather than a defect fix.

## Scope

One module: `:packages:domain`. No wire-format change to the server — the server has been
correct since `0001_initial`, and the client was the side that disagreed.

## Files

- `packages/domain/src/commonMain/kotlin/com/tasktracker/domain/Entities.kt` — `@SerialName`
  on every `Priority` entry, so the serialized name is its `wireValue` rather than the enum's
  entry name.
- `packages/domain/src/commonTest/kotlin/com/tasktracker/domain/DomainTest.kt` — a
  `PriorityWireTest` pinning the wire spelling of every entry.
- `packages/domain/src/commonTest/kotlin/com/tasktracker/domain/RecurrenceTest.kt` — the
  historical-payload fixture carried `"priority":"Medium"`; corrected to `"medium"`.
- `test-baseline.json` — regenerated (one test per host).

## Contract

- [x] `JSONCoding.json.encodeToString(Priority.serializer(), p) == "\"${p.wireValue}\""` for
      every entry — the enum's entry name must never reach the wire.
- [x] The property fails when a single `@SerialName` is removed (mutation-checked: the test
      reported `expected:<"medium"> but was:<"Medium">`).

## Validation

- Run: focused JVM tests for the touched modules (under a minute); the three-host run is the release gate: `scripts/check.sh full`
- Counts: **+1 test on every host** — `:packages:domain` runs on jvm, iOS and the Android
  host, so all three totals move by one. Regenerate with `scripts/test-baseline.py`;
  `scripts/check-baseline.sh` must pass.
- `PriorityWireTest` is a structural assertion, not a pixel test — the `test-reviewer`
  subagent is not required.

## Handoff

- **Done:** the fix, the guard test, the fixture correction, and the baseline, all green on
  the three hosts (`scripts/check.sh full` exits 0). Mutation-checked red without the
  `@SerialName`.
- **Pending:** nothing in this repo. The device's dead rows still need a retry.
- **Next step:** merge this, then in the desktop app use *retry stuck changes* — the repair
  path rebuilds each payload from the local row, so the dead rows go back out in lowercase
  with a fresh attempt budget. No database wipe, no migration.
- **Gotchas:** do **not** add a `@JsonNames("Medium")` alias to soften the change. The revive
  path (`RoomProjectRepository.rebuiltPayload`) re-encodes from the store rather than
  re-sending the stored bytes, so no stale `"Medium"` payload is ever decoded; an alias would
  only keep an invalid spelling alive. Do not "fix" this on the server either — see below.

## Risks / notes

- The client's own `wireValue` documents lowercase as "the stable machine key for storage and
  the wire", the server's `Priority` constants and `work_items_priority_check` have used
  lowercase since migration `0001_initial`, and `internal/sync/engine.go` normalizes nothing.
  `medium` is the deployed contract; the client's serialized name was the accident. The
  cross-project rule that the client is the fixed point (so the server changes) does not
  apply to a value the client never stores and never intended to send.
- Why it was so damaging: the server's refusal is a Postgres `CHECK` violation (`23514`),
  which the client maps to a **retryable** failure. The row therefore spends the whole retry
  budget before dead-lettering, and the push loop stops at the first retryable failure — so a
  single work item with a bad priority spelling halted everything queued behind it, silently.
  The same value is also what the server *emits*, so the pull path (`LocalDataSyncSink`) could
  not decode a work item from the server either.
- Easy to get wrong: adding a new `Priority` entry without a `@SerialName`. `PriorityWireTest`
  iterates `entries`, so it catches a missing annotation on a new entry too.
