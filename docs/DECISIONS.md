# Architecture & Design Decisions

Permanent architectural invariants and design constraints. A rule lives here only if violating it has caused bugs. Operational test workflows live in `docs/TESTING.md`.

## 1. Structural Invariants
* **Navigation:** Hand-rolled `Router` in `:packages:routing` (pure state, no Compose dependency). Rendered exclusively by `:shared-ui`. No third-party nav libraries.
* **Strings & Resources:** Configured in `:packages:design-system` only (`compose.resources`). Never configure resources in multiple modules (causes DEX merge collisions).
* **Width-Driven Shell (Never Platform-Driven):**
  * Layout class derives strictly from available width via `BoxWithConstraints` in `commonMain`, **never** by branching on `isDesktopApp` or platform.
  * Threshold: `ShellExpandedMinimumWidth = 840.dp`. Below 840dp is `Compact` (bottom bar, sheets); at or above is `Expanded` (side rail/sidebar, windowed modals, multi-pane).
* **Test Tag Ownership:** Composables do not tag their own roots; callers provide the tag. (`testTag` overwrites rather than merges; self-tagging hides nodes from parent semantics trees).
* **Database Configuration:** Injected `DatabaseConfiguration`, never a defaulted host, ensuring Android host tests and unconfigured environments fail safely instead of silently reaching in-memory stores.

## 2. Sync & Storage Invariants
1. **Atomic Outbox Writes:** Record mutations and their corresponding outbox row must commit in **one database transaction** (`Transactions.useWriterConnection`). Never split into two steps.
2. **Pull Isolation:** Pull cycles write incoming data to disk and must **never enqueue outbox rows** (prevents infinite sync loops).
3. **Ordered Push:** Pushes process oldest-first. A retryable verdict halts the cycle; never claim past an unverified change.
4. **Local-Only History:** Task status history is purely local; never enqueue to outbox.
5. **Verdicts Match by Entry ID:** Match by ID, never queue index. Unmentioned IDs trigger retry; duplicate IDs invalidate the verdict.
6. **Session-End Safety:** Sign-out mid-push releases in-flight rows; never mark them dead-lettered.
7. **Terminal State Contract:** `resolvedAt != null` **strictly if and only if** `isTerminal`. Enforce on both read and write paths.

## 3. General Code Constraints
* **Composable File Boundary:** Exactly one public `@Composable` per file, matching the filename. No Material3 (TaskTracker uses custom Liquid Glass tokens).
* **Test Names:** Backtick sentences with **no commas or periods** (Kotlin/Native compiler rejects punctuation in symbol names).
* **Native Flow Safety:** Never write unbounded `for (x in flow)` loops (causes Native SIGSEGV on teardown).
* **Suspend Seams:** Every `@Throws` on a `suspend` function **must** declare `CancellationException` (Kotlin/Native fails compilation otherwise).
* **Architecture Graph:** `build-logic/.../tasktracker.architecture.gradle.kts` tables are exact-set. Any new module edge without a table edit fails `verifyArchitectureGraph`.

## 4. High-Risk Failure Vectors
* **Session Ordering:** Must `await` auth state transitions before screen redirection; asynchronous `launch` silently creates race conditions.
* **Data Converters:** Adding an entity column requires updating bidirectional conversions in both directions in the same commit.
* **Platform Seams:** Every `expect` requires `jvm`, `ios`, and `android` actuals in the same transaction.

## 5. Known Intentional Product Gaps (Do Not "Fix")
* **Mock Sync:** Default API host is `https://api.tasktracker.invalid`; sync operates via `MockSyncTransport`.
* **Single Local Store:** Outbox rows carry `userID`, but local Room entities are shared across accounts on the same device.
* **Dark Mode:** Tints accent colors only; glass surfaces remain semantic tokens.
* **Verification Matrix:** The full 3-host test suite is optimized (`scripts/check.sh full` completes in <10m) and serves as the release and multiplatform gate. Routine code edits use the sub-minute focused JVM loop.