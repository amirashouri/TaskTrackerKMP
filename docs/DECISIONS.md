# Architecture & Design Decisions

Permanent architectural invariants and design constraints. A rule lives here only if violating it has caused bugs. Operational test workflows live in [`Project.md`](../Project.md#2-verification-loops) §2 and §5.

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

## 6. Notes kept from completed task specs

The per-task specs that produced these decisions were removed before the first
release. Only constraints a future change could silently violate are kept here;
validation runs, counts, handoffs and per-task file lists were deliberately
dropped. Sections 1–5 above remain authoritative for the structural invariants.

### Wire format and configuration

- **One response convention.** Client–server response keys are `camelCase` and
  timestamps are ISO-8601 UTC whole seconds. Go `json:"…"` and Kotlin
  `@SerialName` must match exactly. Enum *values* (`in_progress`), SQL column
  names, the agent runner's `last_seen_at` and third-party LLM request keys are
  not response keys and keep their own spelling.
- **Strict decoding is the contract.** `JSONCoding` leaves `ignoreUnknownKeys`
  off: a response may not gain, lose or rename a key unless the client ships
  first with a default. Every optional DTO field needs a default; enabling
  `ignoreUnknownKeys` globally is not a substitute.
- **The agent wire names are the ones that shipped, not the ones the spec
  proposed.** `host`, dispatch request `runnerId` → run response
  `requestedRunnerId`, and `diff` are the contract. `requestedRunnerID` and
  `resultDiff` were never adopted: a case-only rename breaks installed builds, so
  a tidier name in a spec is not a reason to change the wire. Request and
  response keys need not match — `runnerId` is the target picker's choice on the
  way in, `requestedRunnerId` is the stored target, and `runnerId` on the run is
  the machine that actually claimed. All three response keys are defaulted on the
  DTO and `omitempty` on the server, so an untargeted run without a diff gains no
  new key at all.
- **`host` is a stated fact, not a third kind.** `desktop` and `remote` are the
  only kinds; null or empty means "not stated" (a legacy machine or an older
  client's pairing code). Redeeming a code copies its `host` and `projectId` onto
  the runner, so the machine cannot name its own project. A UI that reads an
  empty host as "desktop" would mislabel every legacy machine.
- **The connect link is one shared grammar.**
  `tasktracker://connect?url=<QueryEscape(the whole verified URL)>` is rendered
  by `tt install` and percent-decoded exactly once by the client. Escape the
  whole URL, including `&` and `%`, or an existing `%2F` becomes a different
  address. It only prefills a field the user confirms — a custom scheme can be
  claimed by another app and is never a trust decision.
- **Plain HTTP is local-only.** `https` is valid for any parseable host; plain
  `http` is accepted only for local addresses (loopback, RFC1918, link-local,
  CGNAT, `.local`, single-label). A public `http://` root is refused at save
  time. This policy is deliberately wider than Android's cleartext allow-list;
  do not narrow it to satisfy a platform gate.
- **The API root resolves once.** `platformApiBaseUrl` runs before the graph
  exists and uses `runBlocking` to choose which HTTP clients to bind; an
  unreadable store falls back to the build-time value. Clients are bound once,
  so a saved URL takes effect only after a restart. The URL is a raw preference
  key, not a `DefaultsKey`, because sign-out's `removeAll()` would erase it —
  it is a device property, not a credential.

### Domain and sync

- **Terminal means `Done` or `Closed`.** `WorkItemStatus.isTerminal` is the one
  definition of finished work. Any new listing of finished items must read it,
  not filter on `Closed`, or it disagrees with `ArchiveWorkItem` and the Archive
  tab. A `Done` item is in both the board's done lane and the archive; counts
  must be `items.size`, never the sum of the sections.
- **`WorkItem.isArchived` is not archive membership.** The flag is a separate,
  user-owned filing choice that the file-away path never sets. Switching
  `WorkItemGrouping.resolved` to filter on it emptied the archive and turned four
  domain tests red on every host, while `Projections.kt` still read `isTerminal`
  for the same question — the two listings disagreed about what work was finished.
  "Finished" is a workflow fact (`isTerminal`); "filed away" is a filing choice
  (`isArchived`). Never replace one predicate with the other.
- **A preference is shell state.** A feature `presentation` module may not see
  `:packages:user-defaults`, so a screen takes a value plus a change callback
  and `:shared-ui` holds it in `ShellPreferences`. `remember` inside a
  destination dies on a tab switch. Store *choices*, not screen state: the
  search query is screen-scoped, while backlog sort order is a preference.
- **Agent runs are server-owned.** No Room table, no offline queue, no field on
  the `workItem` envelope. State is polled with `afterSeq` and recovered with
  `GET /v1/agent/runs?workItemId=…`; a run that outlives the app is normal.
  "Stop" is the server's claim gate, not a process kill.
- **Tags ride the project entity.** The catalog needs no sync entity, outbox
  scope or Room table, and is written only through the admin project API.
  Deleting a tag never rewrites work items: readers drop ids the catalog no
  longer defines. `availableTags == null` means the read failed, not "no tags";
  collapsing it to `emptyList()` loses tags whenever a read is slow.
- **Sprint lock replaces the clock.** An explicit reversible `isLocked` flag,
  not the start date, freezes window, cap and membership. A locked sprint still
  accepts name/goal/owner edits; a finished sprint is frozen either way.
  `windowLocked` is `isLocked || isFinished`, and anything using "active" as a
  proxy for "committed" must read the flag.
- **Directory policy is server-side.** A blank user search lists addable
  accounts while a typed search includes deleted authors. The client filters
  what it receives and must not "fix" that asymmetry; `RoomTeamRepository`
  stays the offline fallback that owns the signed-in identity.
- **Never swallow `CancellationException`.** Rethrow it, do not record a
  cancellation as a failure or raise a notice for a screen that is gone. A
  failed profile PATCH restores the last server-confirmed snapshot rather than
  re-reading `port.userDetails()`, which is a networked read-through.

### Architecture and models

- **The architecture graph is an allow-list.** Edits to
  `build-logic/.../tasktracker.architecture.gradle.kts` require an ACK, and a
  forbidden edge compiles and passes focused tests — only `verifyArchitectureGraph`
  at the release gate catches it. Prefer a design that needs no table edit.
- **Every model lives in its feature's `presentation` module.** Screens are
  functions of their arguments and own no async. Observation is the caller's
  scope, and a "has loaded" flag must be set by an answer, not an attempt, so a
  failed or cancelled load is retried.
- **Platform seams are all-or-nothing.** A new `expect` needs `jvm`, `ios` and
  `android` actuals in the same change; introducing one is a cross-module change
  requiring an ACK.
- **Shell scenarios are indexed.** `ShellScenarios.<group>Scenarios` lists are
  reached as `projectBacklogScenarios[7]`. Inserting mid-list silently renames
  every test below it — append and add the next index.

### UI, rendering and tests

- **Measure the sibling that moves.** `AnimatedVisibility` + `expandVertically`
  clips its child, so the child's own bounds are a bad measurement. Measure a
  sibling below it, and compare against the *settled* position — one frame of a
  tween already satisfies "`> closed`" even when the animation is ignored.
- **Drive animations with the Compose clock.** Use `mainClock.autoAdvance = false`
  and `advanceTimeBy(...)`; `Thread.sleep` passes on a fast JVM and flakes on CI.
  `LocalReducedMotion` means "snap to the target", not "skip the change".
- **Silent rendering failures.** `ImageVector.Builder.addPath` draws nothing
  without a `Brush` fill, and equal contours cancel under even-odd just as
  silently. A gradient with `endY = Float.POSITIVE_INFINITY` interpolates at ~0
  everywhere, so its fade constants become dead code.
- **Baseline drift is not your regression.** `test-baseline.json` is regenerated
  only from `scripts/check.sh full` and can be stale on `main`. Compare the delta
  against the number of `@Test` lines you added before assuming you broke it.
  Regenerate from a *green* run only (zero failures and errors on every host),
  then re-run the gate: `check-baseline.sh` alone proves the counts, not the tests.
- **A windowed sheet's padding is part of its measured geometry.**
  `TTBottomSheetScaffold` caps the window with `heightIn(max = 760.dp)`; 72dp of
  outer padding plus the form's own 16dp inset needs 776dp, so the content
  scrolled 16dp — the centre read low and the action row fell below the viewport.
  Window clearance belongs on the outer padding (56dp), and the inner vertical
  padding is `0.dp` in window mode. Reverting only the inner padding reproduces
  the centring failure; restoring 72dp reproduces both it and the missing created
  project. Mutate each padding separately before claiming which test it serves.
- **A comment claiming "test X cannot see Y" is unverified.** Two guards can
  overlap completely; mutate the behaviour, run both, and write down which one
  goes red. That is the only way to know a pair of tests is complementary.

### Product limits

- **The project owns no infrastructure.** No wildcard domain, provisioning
  endpoint, hosted relay, static IP, port forwarding or DDNS. A customer's
  exposure is a provider *they* hold an account with, and `tt` never phones
  home. `lan` is the default provider and serves desktop, Android and iOS over
  plain HTTP; `tailscale` and `cloudflare` are additive behind one flag.
- **Android Local Network Protection fails silently.** On API 37 a blocked TCP
  connect to a local address times out rather than being refused. The app needs
  `ACCESS_LOCAL_NETWORK` declared and requested at runtime unless the root is
  loopback; deciding by hostname instead would put a nearby-devices prompt in
  front of public-server users. `100.64.0.0/10` covers Tailscale.
- **License terms.** MIT + Commons Clause + a no-rebranding term: source-available
  (not OSI), forbids selling and redistributing under another name. Both repos
  must keep `LICENSE` byte-identical or the terms drift.
- **Real backdrop blur is a non-goal.** The painted material ships because the
  alternatives were a third-party dependency in a package every module compiles
  against, or UIKit interop that replaces the Compose-drawn rim on iOS.
- **Never log or render untrusted content.** Prefer user-facing `AppError.message`
  in log lines, never append tokens, and render the agent event log as plain
  text — never as markup or a link the model chose.