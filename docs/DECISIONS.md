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
8. **Coalesced Work-Item Snapshots:** A queued work-item upsert deletes any `pending`/`failed` upsert of the same item in the same transaction. The outbox carries **state, not history**, and `claimable` breaks a same-`createdAt` tie on a random uuid — so two snapshots from one user action can reach the server in either order and the older one wins the pull. `inFlight` and `dead` rows are never withdrawn. Do not assert queue *growth* for a work-item write; assert one row per item and its payload.

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
- **Sprint membership rides its own sync entity.** `SprintMember` has its own Room
  table, outbox row and `sprintMembers` pull scope; `Sprint` gains no field,
  because installed clients strict-decode sprint payloads and an added key is a
  decode failure. One row per `(sprintID, userID)`, a duplicate is an update and
  `availableStoryPoints` is floored at zero. The edit guard is `!isLocked &&
  !isFinished` in the model as well as in the UI — a hidden button is not a rule.
- **Per-member progress is derived, never stored.** Delivered points count `Done`
  items only; `Closed` items appear but earn nothing; unestimated items contribute
  zero; unassigned items get a derived row. Work items stay the source of truth — a
  per-member total would be a second answer that drifts.
- **Directory policy is server-side.** A blank user search lists addable
  accounts while a typed search includes deleted authors. The client filters
  what it receives and must not "fix" that asymmetry; `RoomTeamRepository`
  stays the offline fallback that owns the signed-in identity.
- **A teammate picker reads the admin directory, not the session.** Choosing an
  existing collaborator reads `MemberAdminPort.members(Active)` first;
  `TeamPort.searchMembers` is session-only without a backend and can answer with
  just the signed-in admin, silently hiding the rest of the team. The domain keeps
  ids only; display names resolve at lookup.
- **Never swallow `CancellationException`.** Rethrow it, do not record a
  cancellation as a failure or raise a notice for a screen that is gone. A
  failed profile PATCH restores the last server-confirmed snapshot rather than
  re-reading `port.userDetails()`, which is a networked read-through.

- **Epic status and progress are derived, never stored.** `deriveEpicStatus` reads only
  the coarse `WorkItemStatus` category: all terminal → `Done`; any `InProgress`/`InReview`
  or any terminal subtask → `InProgress`; otherwise any `Todo` → `Todo`, otherwise `Backlog`;
  an empty list is `Backlog`, not `Done`. A single finished item means the epic has begun, so a
  Done/Closed subtask beside an unfinished one reads `InProgress`, not `Todo`. `Closed` counts
  as terminal. No Room column, no wire key, no cached percentage — a stored status would be a
  second answer that drifts the moment a subtask moves. Mutation-checked: treating `InReview`
  as `Done` turns `EpicStatusTest.inReviewIsInProgress` red; removing the terminal branch turns
  both partial-completion cases red.
- **Epic progress is what this device can see.** The total is `workItemsForEpic(id).size`,
  which is the permission boundary rather than a bug: a member of one linked project sees a
  smaller total than a member of both, and the copy stays neutral about it. Never resurrect
  the total from a server count. `WorkItem.isArchived` is a filing choice: archive-all sets
  it on the epic's subtasks and unarchive restores them; no listing may read it as
  "finished".
- **An epic's owner is one member, defaulted to an admin, never cleared.** `ownerID` decodes
  with a null default so a row written before the field existed still loads, and the editor
  shows the signed-in admin or the first active admin from the live directory in that case.
  The picker is single-select with no "unassigned" entry: the server gives a create its
  deterministic admin default and keeps the stored owner when an update omits it. Null is
  therefore a legacy or deleted-account value, not a state the UI may offer.
- **A backlog item links to an epic through a project-scoped picker, and the action hides
  once linked.** The picker offers only active epics that list the item's project — the same
  rule `SetWorkItemEpic` enforces — because anything else is a choice the write refuses.
  `WorkItem.epicID` is the single link field, so showing the action on an already-linked row
  would either do nothing or replace the link without saying so. Load the choices on demand;
  the backlog's projection is the list being read, and an unopened picker owes it no read.
  The linked epic's name is context, not an editor field: the work-item editor resolves it
  once on open into a read-only chip under the page title, and `epicID` stays edited only
  through the backlog picker. A missing epic drops the chip rather than inventing a
  placeholder.

### Selection and batches

- **A batch is presentation convenience over per-item commands.** `PutWorkItemsOnBoard` and
  `ArchiveWorkItems` loop the same `MoveWorkItem` / `ArchiveWorkItem` the single-row paths use,
  so every item keeps its own history row and its own outbox row. There is no server batch
  endpoint and no new wire key; do not add one to make a selection cheaper.
- **A batch reports item by item.** Applied and skipped items leave the selection, failed items
  stay ticked, and only the first failure's message reaches the banner — the failed rows still
  ticked are what carry the count. A terminal item met mid-batch is *skipped*, not failed; the
  refusal is a message, so the skip is decided before the archive call rather than by matching it.
- **Backlog selection never survives navigation and never spans a filter.** The mode is scoped to
  the Backlog section, entering it closes the search, and switching segment, leaving the
  destination or editing the query drops the set. A ticked row the user cannot see is a count
  they cannot account for.
- **The board's Archive on a Done card closes it.** `Done` is a board lane and `Closed` is the
  archive, so archiving a done card is `Done → Closed` with the existing `resolvedAt` kept — the
  date the work ended, not the day the board was tidied. `Closed` stays refused and is the batch's
  only skip. `Closed` is still not a status the editor offers: closing is an action, not a choice.
- **Board selection is lane-scoped and starts at the lane header.** A set belongs to one lane, so
  tapping a card in another lane ticks nothing and the other headers drop their Select while a set
  is open. There is no long-press entry: long-press lifts a card for drag, and a stationary hold
  would start a drag ghost. Drag is off while selecting, and the wire is still one row per card.

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
- **Selection-bar motion is one helper, and the bar stays composed.** The board and
  backlog bars animate through `SelectionBarMotion.kt`, never behind an `if` — an
  uncomposed bar cannot animate away. A `Column`'s `spacedBy` counts children, so an
  `AnimatedVisibility` left as a sibling spends a gap even while empty: supply the gap
  inside the animated content, or swap the bar in place through a single child.
- **The Project | Epic segment is a preference, not screen state.** It is top-level and a
  `remember` in the destination dies on a tab switch, so it lives in `ShellPreferences`
  (`DefaultsKey.ProjectsSegment`) with the value plus a change callback threaded into
  `ProjectsHost`. The same rule as backlog sort: store choices, not screen state.
- **The Projects "+" creates for the showing segment.** No dropdown and no chooser sheet:
  the segment above it is the choice, so the plus opens the project form on Projects and the
  epic editor on Epics. It is hidden when that segment has no editor. The epic editor is a
  pushed route (`AppRoute.EpicEditor`) rather than a modal.
- **Silent rendering failures.** `ImageVector.Builder.addPath` draws nothing
  without a `Brush` fill, and equal contours cancel under even-odd just as
  silently. A gradient with `endY = Float.POSITIVE_INFINITY` interpolates at ~0
  everywhere, so its fade constants become dead code.
- **Baseline drift is not your regression.** `test-baseline.json` is regenerated
  only from `scripts/check.sh full` and can be stale on `main`. Compare the delta
  against the number of `@Test` lines you added before assuming you broke it.
  Regenerate from a *green* run only (zero failures and errors on every host),
  then re-run the gate: `check-baseline.sh` alone proves the counts, not the tests.
- **A sheet's content never scrolls itself.** `TTBottomSheetScaffold` already wraps its card
  in `verticalScroll`; a second one inside the content is measured with unbounded height and
  throws `IllegalStateException` when the sheet opens. The epic project picker did this, so
  the first tap on its project button crashed on a fresh install with no projects. Rows grow
  and the scaffold scrolls; an empty picker says so instead of drawing nothing.
- **A windowed sheet's padding is part of its measured geometry.**
  `TTBottomSheetScaffold` caps the window with `heightIn(max = 760.dp)`; 72dp of
  outer padding plus the form's own 16dp inset needs 776dp, so the content
  scrolled 16dp — the centre read low and the action row fell below the viewport.
  Window clearance belongs on the outer padding (56dp), and the inner vertical
  padding is `0.dp` in window mode. Reverting only the inner padding reproduces
  the centring failure; restoring 72dp reproduces both it and the missing created
  project. Mutate each padding separately before claiming which test it serves.
- **A back press must be dispatched on the dispatcher the handler joined.**
  `setUpShell` provides `LocalNavigationEventDispatcherOwner`, and every host's
  `BackHandler` prefers it — on Android that is `activity-compose` 1.12's
  `androidx.activity.compose.BackHandler`. `activity.onBackPressed()` completes
  on the *activity's* dispatcher, which that handler never joined, so it reaches
  nothing under Robolectric. Dispatching a synthetic input on the provided
  dispatcher is the real chain on all three hosts; the old Android-only skip was
  a stale measurement, not a host limitation. No capability gate for system back.
- **A comment claiming "test X cannot see Y" is unverified.** Two guards can
  overlap completely; mutate the behaviour, run both, and write down which one
  goes red. That is the only way to know a pair of tests is complementary.
- **External contacts go through Compose's `LocalUriHandler`.** The Settings
  creator card opens `mailto:` and `https://` with this common composition local,
  whose Compose 1.12.1 actuals cover Android, iOS and Desktop — no `expect`/`actual`
  seam or new dependency is warranted. A recording handler in tests proves the
  exact URI; static creator copy belongs in `:packages:design-system`, the only
  module that configures `compose.resources`.
- **Grid cards cap summary text at one clipped line.** `ProjectRowView` gives
  `project.details` `maxLines = 1` with `TextOverflow.Ellipsis`; a wrapped summary
  makes one card taller than its neighbours in the projects grid. Reverting the
  constant to two lines grew the long-summary card from 131px to 150px.
- **A paused clock needs a frame-producing wait.** With reduced motion the
  selection bar's `EnterTransition.None` asks the test clock for no frame, so a
  bare `advanceTimeByFrame()` after the Select click can sample a tree in which
  the bar was never composed on the Android host. Advance *until the node exists*
  and keep the travel assertion at that first observation: reverting the reveal
  to an animated spec fails with 0dp of travel against 54dp settled.

- **Wide layouts cap reading width at the shell's breakpoint, per module.** The
  projects list, the backlog and the sprint list all switch at `840.dp` — the
  value of `ShellExpandedMinimumWidth` — but each presentation module owns its
  own constant (`ProjectsWideMinimumWidth`, `BacklogWideMinimumWidth`,
  `SprintWideMinimumWidth`) because presentation may not import `shared-ui`.
  Wide projects become a `LazyVerticalGrid(GridCells.Adaptive(340.dp))`; the
  backlog and the sprint list stay one column capped at `960.dp` and centred
  (`BacklogMaxContentWidth`, `SprintMaxContentWidth`), because their order is
  the information and columns would destroy the scan.
- **Cap the floating header and the list together.** The backlog header is
  measured for the list's top inset, so the header's width cap and the list's
  live in the same `BoxWithConstraints` and share one `Alignment.TopCenter`.
  The header's outer `md` inset plus its inner `md` padding equals the list's
  `xxl` gutter, which is what puts their controls on one leading edge; capping
  only the list leaves the header on the window edge.
- **A forced-width test must centre against the screen, not the host.**
  `requiredWidth(1400.dp)` can exceed a phone-sized test host, and the root
  centres the overflow, so `boundsInRoot.left` is not
  `(windowWidth - contentWidth) / 2`. Read the screen node's own bounds and
  compare centres.
- **Shell chrome reuses presentation controls through a state/callback seam.**
  The desktop sidebar's Theme panel is a public `ThemeControlsPanel` in
  `feature-settings-presentation`: it reuses the Settings theme picker, accent
  row and glass slider, with a compact accent variant that drops names but keeps
  the travelling lens and 44dp cell targets. It takes values and callbacks and
  never reads `user-defaults`, leaving `ShellPreferences` as the one writer. Its
  expanded content is capped at 40% of the sidebar's measured height, so a short
  window scrolls the panel instead of moving the measured destination lens.
- **Bottom-anchor the sidebar panel with `Arrangement.SpaceBetween`, not a
  weighted spacer.** The project list's `weight(1f, fill = false)` wraps to its
  projects; a second weighted child shares the leftover but does *not* inherit
  the list's unused share, so the panel floated 200dp above the floor. Two
  children under `SpaceBetween` (destination stack, panel) give the whole gap
  to the bottom without touching the stack's adjacency invariant. The scroll
  cap must come from the panel's own measured height: `BoxWithConstraints` next
  to a weighted child reports infinite `maxHeight`.
- **Measure the moving thing, not its container.** The accent lens is derived
  from the cell rect, so asserting lens-centre equals cell-centre passed even
  with the swatch pinned to the cell's top. Compare the lens with the *swatch*.
  And `Arrangement.Center` is inert in a column that wraps its content: the
  compact cell needs a fixed 44dp height for the centring to have space to act.
- **An accent thumb on an accent-filled track needs an on-accent rim.** The
  slider's knob was invisible at 100% because its fill matched the track; a
  2dp `ttOnAccent` rim makes the edge readable without changing the gesture
  geometry. `SliderThumbVisibilityTest` samples that rim off a raster and was
  mutation-checked with the rim removed. The panel's open/close uses
  `AnimatedVisibility` with reduced-motion snap; its scenario compares the
  content height one frame after the tap with the settled height.
- **Measured selection geometry is value state, not `LayoutCoordinates`.**
  Compose reuses a row's `LayoutCoordinates` object across layout passes, so
  assigning the same instance to a state map again does not invalidate when only
  its position moved. The sidebar lens kept the pre-population rectangle after a
  sidebar→rail→sidebar resize, a row above the selected one. Store an `IntRect`
  per row and refresh it in every `onGloballyPositioned` callback; the rail's
  index-based lens has no such problem.

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
- **Native backdrop blur is now available on iOS via `TTGlassSurface`.** It wraps
  content in a `UIVisualEffectView` (`UIBlurEffect`) so iOS surfaces read as real
  native glass. The painted `ttGlassSurface` remains the fallback on JVM/Android
  and for any surface not yet migrated; fully replacing the painted material
  across all 33 call sites is still in progress on the `feat/native-liquid-glass-ios`
  branch. macOS desktop keeps the painted material (Compose Desktop has no native
  vibrancy seam). The native path is opt-in through `LocalNativeGlassEnabled`
  (default `false`), turned on only by the iOS entry point in `MainViewController`:
  `UIKitView` needs an interop container that the Compose test scene does not
  provide, so a `runComposeUiTest` tree that reaches for one fails to compose with
  `LocalInteropContainer not provided` (61 iOS view tests, when the gate was absent).
- **Never log or render untrusted content.** Prefer user-facing `AppError.message`
  in log lines, never append tokens, and render the agent event log as plain
  text — never as markup or a link the model chose.