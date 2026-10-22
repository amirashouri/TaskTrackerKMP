# sync-debug-logging — Console sync-failure logs in debug mode

- **status:** done
- **priority:** P1
- **plan:** none — standalone work (client-side diagnostic logging only)

## Scope

`:packages:sync` only — the client sync engine. No network change, no wire-format change, no Room change, no server touch. Single Gradle module, single repo → fast path, no cross-repo ACK, no KMP-internal ACK.

## Files

- `packages/sync/src/commonMain/kotlin/com/tasktracker/sync/SyncEngine.kt` — debug-mode flag + error-level logging of sync failures and transport errors.
- `packages/sync/src/commonTest/kotlin/com/tasktracker/sync/SyncEngineTest.kt` — behavioral test that a failed cycle's report carries a `SyncFailure` with stage + message.

## Contract

- [x] When a sync cycle ends with failures, the console receives an `error`-level line containing the trigger, every failure's stage + message, and the cycle counts (pushed/accepted/conflicts/rejected/retrying/applied/queueDepth/deadLetterCount/requiresSignOut).
- [x] A transport failure in push or pull is logged at `error` level with context (scope/cursor where applicable) instead of being folded into the report silently.
- [x] Verbose debug traces are gated behind `isDebug` (defaults `true`); release builds flip it to `false`.
- [x] Existing `info`/`warning` cycle summaries are unchanged.
- [x] A failed cycle's `SyncReport.failures` still contains the `SyncFailure(stage, message)` (behavior preserved; tested).

## Validation

- Run: `:packages:sync:jvmTest` → `BUILD SUCCESSFUL`; 91 tests, 0 failures (includes the 2 new tests below). `scripts/check.sh fast packages:sync` runs the same module plus graph tasks; it gates on `test-baseline.json`, which was generated from a full all-hosts run, so a single-module subset run mismatches on totals — regenerate with `scripts/check.sh full` then `scripts/test-baseline.py` before merge (pre-existing repo constraint, not caused by this change).
- Counts: `test-baseline.json` jvmTest should move +2 once regenerated from a full run (the 2 new sync tests); no failures added.
- Console output reaches the existing default `TTLogger` sink, which is `println` to stdout on every host — exactly the device-readable log the user asked for.

## Handoff

- **Done:** `isDebug` flag + `logger.error` cycle-failure and transport-failure logging in `SyncEngine.kt`; two `SyncEngineTest` cases pinning the failure-report contract; `:packages:sync:jvmTest` green (`BUILD SUCCESSFUL`, 91 tests, 0 failures).
- **Pending:** regenerate `test-baseline.json` (`scripts/check.sh full` then `scripts/test-baseline.py`) before merge; optional but recommended: wire `isDebug` to the platform build type so release builds print `false`.
- **Next step:** baseline regeneration + review/merge.
- **Gotchas:** `packages/core` has no `androidMain`/`iosMain` source sets, so cross-platform build-type detection (`expect`/`actual`) is out of scope; `isDebug` is module-level and defaults `true`. Log sink is `println` (console) by default. Baseline check requires a full all-hosts run; a subset run always mismatches totals against the committed full baseline.

## Risks / notes

- Easy to get wrong: logging secrets. `describe(error)` prefers `AppError.message` (user-facing text); avoid appending raw tokens/credentials to log lines.
