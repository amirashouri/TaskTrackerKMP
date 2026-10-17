# TaskTrackerKMP — Feature Plan

A roadmap for extending TaskTrackerKMP beyond its current personal/board tool into a
fuller personal and team task manager. Each entry states the gap it closes, where in the
architecture it lands (module + layer), and an implementation priority.

Priorities are P0 (foundational — blocks other items), P1 (high value, clear path), P2
(medium value or needs discovery), P3 (nice-to-have polish).

A box is ticked when the feature has landed on `main` with its tests; partial slices
note what remains in their line.

---

## 0. Foundation: real people, not free text

Today `WorkItem.assignee` and `Sprint.owner` are free-text `String?`, and while
`Project.memberIDs: List<UserID>` exists at the entity level, there is no user
directory backing it — no UI to add/remove members, no profile resolution, and the
"My Tasks" filter matches names by first-name substring, which is fragile and wrong
for teams. Every team feature below depends on this being real.

- [x] **0.1 User directory / member store** · P0 · New `feature-teams-domain` (port) + `feature-teams-data` (Room + sync) — A `TeamPort` exposing `User` entities (id, email, displayName, avatarId, position, status, lastSeen). Seeded from the auth identity and the server's member list on sign-in. This is the `UserID` → profile lookup every other feature needs. *Landed as a first slice: the port and session-cache seeding are real; the server member-list transport is not, so non-session members resolve to `null` until sync lands.*
- [x] **0.2 Link assignee and owner to UserID** · P0 · `domain` (Entities), `feature-projects-domain` (port + use cases) — Change `WorkItem.assignee` from `String?` to `assigneeID: UserID?`; change `Sprint.owner` from `String?` to `ownerID: UserID?`. Update `ProjectRepositoryPort`, `RecordMapping`, and `MoveWorkItem`/`SaveWorkItem` use cases. The `assigneeMatchesCurrentUser` logic in `ProjectBoardModel` becomes an exact `UserID` comparison instead of a fragile string match. *Landed as a clean break: `assigneeID`/`ownerID` replaced the free-text columns outright (a name cannot resolve to a directory id, so nothing was carried across); shortly after, the whole migration history was deleted — the store reset to schema version 1 with destructive fallback, since nothing has shipped. The picker offers Unassigned plus the signed-in user, and display names resolve through `TeamPort`.*
- [x] **0.3 Project member management UI** · P0 · `feature-projects-presentation` (project editor sheet) — Wire the project editor to `Project.memberIDs` via a member-picker that reads from the directory in 0.1. Add invite-by-email (queues an outbox row). This is the UI the data model already hints at. *Landed without a directory search: 0.1's `TeamPort` resolves a `UserID` it is given but cannot list or search people, so a name/email search box would have been a mock pretending to be a directory. The sheet now shows selected members (names resolved through `TeamPort`, raw id as honest fallback), offers the signed-in user as a one-tap add, and routes everyone else through invite-by-email — which queues a new `OutboxOperation.Invite` row carrying `{projectID, email}`. The mock directory (`MockProjectMembers`) was deleted with its search UI. *Polish follow-up:* members render as `FlowRow` glass pills with initial discs (the `WorkItemDependencyPills` shape) in a split-out `ProjectMembersSection`; the add-me action moved to the section's label row; previews and the first-launch seed carry members (`ViewPreviewData.previewMemberIDs` — two named plus one unnamed id to keep the honest-fallback path on the page; `ProjectSeed.members` flags the signed-in user, the only person a seed can honestly name).* *Fixed later:* the creator is now a member from the moment the project is created — `RoomProjectRepository.createProject` folds the signed-in user into `memberIDs` — because the server authorizes a project it does not already have against the payload's own member list, so a project created without its creator in that list was **permanently unsyncable** rather than merely unauthorized: the server answered `Rejected` and the queue dead-lettered the change. The picker stays for choosing anyone else.

---

## 1. Team collaboration

- [x] **1.1 Comments / threaded discussions** · P1 · New `Comment` entity in `domain`; `feature-projects-domain` (port + use cases `AddComment`, `LoadComments`); `feature-projects-data` (Room + outbox); `feature-projects-presentation` (comment thread in the work-item editor) — A `Comment` entity (id, workItemID, authorID, body, createdAt, parentID for replies). Read through the project port so data stays behind the use-case boundary. Threaded via optional `parentID`. Rendered in the WorkItemEditor sheet.
- [x] **1.2 @mentions** · P1 · `domain` (mention parse rule in `WorkItemRules` or a new `MentionRules`) + `feature-projects-domain` — Parse `@username` or `@<userID>` tokens in comment bodies. Each mention becomes a notification row (see 1.3). The domain layer owns the parsing rule so both the editor and the sync sink agree on what a mention is.
- [x] **1.3 Notification inbox** · P1 · New `feature-notifications` (domain/data/presentation) + new `AppTab` or pushed screen — A lightweight inbox: rows for "mentioned in a comment", "task assigned to you", "due today", "status changed on your task". Stored in Room with an `isRead` flag and cleared on sign-out. The "My Tasks" filter and this inbox are the two ways a person learns about their work.
- [x] **1.4 Local notifications / reminders** · P1 · New `expect/actual` platform seam (`NotificationCenter` on iOS, `AlarmManager` on Android) + `feature-notifications-domain` scheduling port — A `LocalNotifier` port that the data layer calls when a notification row is inserted: "this task is due in 10 minutes", "you were mentioned". Uses the existing `PushNotification`/platform-bridge pattern. This is also the mechanism reminders (2.2) use.
- [x] **1.5 Task dependency visualization** · P2 · `feature-projects-presentation` (board card + a new dependency subview in the editor) — `blockedByIDs` already exists but is invisible outside the editor. Render a mini "depends on" / "blocked by" list on the card and in the editor sheet. Does **not** add new persistence — it reuses the existing field.
- [x] **1.6 Project-level "assignable members" filter** · P2 · `feature-projects-presentation` (project picker / board header) — On a project's board, the "My Tasks" toggle and the member list should show only this project's members, read from the directory in 0.1.
- [ ] **1.7 Role-based project permissions** · P3 · `domain/Project` (new `role` field per member) + `ProjectRepositoryPort` + use-case guards — Add `ProjectRole` (Owner, Member, Viewer, Guest) to project membership. Gate `setArchived`, `deleteProject`, and member management behind Owner. The port is the enforcement point so the UI can't bypass it. (The administrative value is `Owner` rather than `Admin` so the word `Admin` stays reserved for the *account* role — `UserRole` in `feature-auth-domain`, read from the access token's `role` claim.)
- [ ] **1.8 Activity feed / project timeline** · P2 · `feature-projects-domain` (projection) + `feature-projects-presentation` — Aggregate `StatusChange` rows across a project into a single timeline view, newest first. Today the history exists per-task in the editor; this is a project-level roll-up.

---

## 2. Personal task management

- [x] **2.1 Recurring tasks** · P1 · `domain/WorkItem` (new `recurrence: Recurrence?` field) + `ProjectRepositoryPort` + use cases — A `Recurrence` value class: `Daily`, `Weekly(on day)`, `Monthly(on day)`, `Yearly(on date)`, with an optional `until: Instant?`. Expanding a recurring task in the editor creates the next instance and queues a future outbox row. This is one of the most-requested features and fits cleanly into the existing entity/write model.
- [ ] **2.2 Due-date reminders** · P1 · Shares the `LocalNotifier` seam from 1.4; `feature-projects-domain` scheduling use case — When a work item gets a `dueDate`, schedule a local notification (default: 10 min before, configurable). The reminder is cancelled/updated on any edit that changes the due date or status. Reuses the inbox (1.3) for the in-app signal.
- [ ] **2.3 Search across projects** · P1 · `feature-projects-domain` (`SearchWorkItems` use case reading through the port) + `feature-projects-presentation` (new screen or top-level search in the dashboard) — A fuzzy search over work-item keys and titles, scoped to the signed-in user's projects. The use case lives in domain so the matching rule is testable without I/O; the presentation reads through the model.
- [ ] **2.4 Labels / tags** · P2 · `domain/WorkItem` (new `labels: List<String>`) + `ProjectRepositoryPort` + `feature-projects-presentation` (filter row + editor chips) — A free-form label list on each work item, with a project-level tag set for autocomplete. Rendered as chips on cards and a filterable row on the board. Distinct from the fixed `Priority`/`Status` enums.
- [ ] **2.5 Sub-tasks / checklists** · P2 · `domain` (new `parentID: WorkItemID?` on `WorkItem` or a separate `ChecklistItem`) + use-case rules — Either a nullable parent link (makes a work item a "checklist" container) or a separate checklist entity. The editor sheet gains a checklist section. Reuses the existing `orderingKey` for ordering.
- [ ] **2.6 Calendar view** · P2 · `feature-projects-presentation` (new screen under the Board tab or a new tab) — A month/day view showing work items by `dueDate`. Reuses `BacklogProjection`/date logic. This is a layout-heavy addition, so it needs a rendered-pixel test per the design-system contract.
- [ ] **2.7 Task templates** · P2 · `domain` (new `Template` entity or a `isTemplate` flag) + `feature-projects-domain` use case — "Save as template" on the work-item editor, and "apply template" when creating a new item in the same project. Templates carry title, details, priority, and default labels.
- [ ] **2.8 Custom statuses per project** · P3 · `domain` (project-scoped status set) + `ProjectRepositoryPort` + `feature-projects-presentation` — Today the six statuses are fixed in the domain enum. Allowing per-project custom statuses is a schema change with migration and sync implications — worth doing once teams need their own workflow, not before.
- [ ] **2.9 Focus mode / Pomodoro** · P3 · New `feature-focus` (presentation only — domain timers) or `design-system` timer tokens — A simple timer surface with a start/pause/reset, optionally auto-pausing a work item's status. Needs design-system timer tokens (not reinvented primitives).

---

## 3. Cross-cutting quality-of-life

- [ ] **3.1 Global search shortcut** · P2 · `shared-ui` (toolbar action) → reuses 2.3 — A search icon in the tab bar or dashboard header that opens 2.3. One-liner navigation entry point; the feature itself lives in 2.3.
- [ ] **3.2 Export project archive** · P2 · `feature-projects-domain` (projection to a serializable form) + `feature-projects-presentation` (share sheet) — Export a project's resolved items as Markdown or CSV from the Archive screen. Read-only over the existing projection.
- [ ] **3.3 File attachments** · P3 · New `Attachment` entity + `feature-projects-data` blob store + editor UI — Attach an image or document to a work item. Needs a platform file-picker seam.
- [ ] **3.4 Dark mode auto-schedule** · P3 · `design-system` (theme bridge) + `ShellPreferences` — Honor the system's light/dark schedule instead of a manual toggle. The `ThemeScheme` enum exists; this just adds a `.System` value and a platform `expect/actual` for the current mode.
- [ ] **3.5 Keyboard shortcuts / quick capture** · P3 · `shared-ui` (global shortcut handler) — `Ctrl/Cmd + N` to create a work item, `Ctrl/Cmd + K` to open search. Platform seams only.

---

## Implementation order at a glance

1. **P0**: 0.1 → 0.2 → 0.3 — the identity foundation. Nothing team-shaped can ship without real `UserID` assignment.
2. **P1 wave A (team)**: 1.1 → 1.2 → 1.3 → 1.4 — comments, mentions, inbox, local notifications. These turn the board into a collaboration surface.
3. **P1 wave B (personal)**: 2.1 → 2.2 → 2.3 — recurring tasks, due-date reminders, search. The most universally requested personal features.
4. **P2**: 1.5, 1.6, 1.8, 2.4, 2.5, 2.6, 2.7, 3.1, 3.2 — polish and depth on both front.
5. **P3**: 1.7, 2.8, 2.9, 3.3, 3.4, 3.5 — schema-level or layout-heavy niches.

## Notes on fit

- **Clean architecture is preserved**: every feature has a domain/data/presentation triplet or extends an existing one. Ports (`AuthSessionPort`, `ProjectRepositoryPort`, `SettingsPort`) are the pattern; new features add ports to `:packages:domain` or a new feature-domain module, never reaching into data from presentation.
- **Sync outbox stays the write rule**: writes return the value produced; local writes enqueue an outbox row; a pull never enqueues. New write use cases (comments, recurrence expansion, member invites) follow the existing `inTransaction` + outbox pattern in `feature-projects-data` / the new `feature-teams-data`.
- **KMP constraint**: platform seams (`LocalNotifier`, theme auto-mode, file picker) go through `expect/actual` with `jvm`, `androidMain`, and `iosMain` actuals, per the convention plugin.
- **Testing**: model-level rules (recurrence parsing, mention parsing, search matching) are unit-tested in `commonTest`; UI tests follow the scenario-object + per-host-entry-point split documented in `Project.md`.

---

## 4. Desktop target (JVM app)

Today the JVM target (`jvm()`) is a test host only: it runs `commonTest` fast on the
developer's machine and that is the whole of its job. Compose Desktop — the same Compose
Multiplatform runtime, on a `Window` instead of an `Activity` or a `UIViewController` —
is not wired. Adding it turns the JVM target from "test feedback loop" into a fourth
shippable shell, alongside Android, iOS, and (eventually) the JVM tests.

Desktop is the closest parallel to the iOS shell: same `commonMain` screen tree, same
`AppGraph`, same thin platform entry point that owns the graph and hands it to a hosted
Compose tree. The Android shell is the counter-example — it is a full AGP application
module, not a source set inside a library — and Desktop does not need to copy that shape.

- [x] **4.1 Adopt the source-set shape** · P0 · Decision recorded in PR #4 —
  Desktop goes in `shared-ui/src/jvmMain/`, the same shape iOS already uses for its entry
  point (`shared-ui/src/iosMain/MainViewController.kt`). No new Gradle module, no new
  row in `packageOwnersTable` or `allowedLibraryTable`, no new framework/binaries block.
  The decision point is not shape-choice but *commitment to this shape*: recorded here
  and in `Project.md` so a later change is a deliberate reversal, not a silent drift.
  Validated by 4.2 + 4.3 compiling under `:shared-ui:compileJvmMain` with
  `:shared-ui:jvmTest` still at its existing 53/7 baseline.
- [x] **4.2 Put `compose.desktop.currentOs` on the app classpath, not only the test classpath** · P0 ·
  `shared-ui/build.gradle.kts` — Done in PR #4. Compose Desktop is already on the
  `jvmTest` classpath; this adds it to the Desktop *app* classpath via
  `jvmMainImplementation(compose.desktop.currentOs)`, without dropping it from
  `jvmTest`. Confirmed the Compose Multiplatform 1.12.1 desktop coordinates resolve
  and that `:shared-ui:compileJvmMain` and `:shared-ui:jvmTest` both still compile.
- [x] **4.3 Add the Desktop entry point** · P0 ·
  `shared-ui/src/jvmMain/kotlin/com/tasktracker/sharedui/DesktopMain.kt` — Done in
  PR #4. `main() = application { Window(onCloseRequest = ::exitApplication) { ... } }`,
  graph owned once in `DesktopGraph`, content renders `TaskTrackerApp`. Modeled on
  `MainViewController.kt`: the graph is owned once in process scope, the entry point
  takes no arguments from the platform side, and the file contributes only the window
  and the graph. Nothing in `commonMain` changed for it.
- [x] **4.4 Confirm the architecture tables need no edit for the source-set shape** · P0 ·
  `build-logic/convention/src/main/kotlin/tasktracker.architecture.gradle.kts` +
  `settings.gradle.kts` — Confirmed in PR #4: no new module is added, so no new row in
  `packageOwnersTable` or `allowedLibraryTable` is required. Confirmed by running
  `verifyArchitectureGraph` + `verifyModuleImports` on the merged branch — both passed.
- [x] **4.5 Add a `run` task for Desktop** · P1 ·
  `shared-ui/build.gradle.kts` — Done. A `JavaExec` `runDesktop` that launches
  `com.tasktracker.sharedui.DesktopMainKt` against the `jvmMain` runtime classpath.
  Verified: the task resolves (`./gradlew :shared-ui:tasks`), launches, and the app
  reaches its startup sequence (sync coordinator starts; on a clean store the startup
  cycle finishes). `:shared-ui:jvmTest` stays at its existing 53/7 baseline.

  Caveat recorded: the first launch against this machine's existing on-disk store
  failed with "migration from 2 to 3 was required but not found" — a pre-existing
  database-module condition exposed by this being the first time the Desktop app opened
  the on-disk store here. Deleting the stale `~/Library/Application Support/TaskTracker/`
  store reproduced the documented dev-store behaviour (drop and recreate) and a clean
  launch followed. The run task is the deliverable; the stale-store failure is a
  pre-existing condition, not a run-task bug.
- [x] **4.6 Decide Desktop persistence and implement any new `jvmMain` actuals** · P1 ·
  `packages:keychain` + `packages:user-defaults`, `shared-ui/src/jvmMain/` or the
  owning module's `jvmMain/` — The JVM target's keychain and user-defaults actuals are
  in-memory today (test-host stubs). A Desktop app that signs in and restarts loses its
  session unless Desktop gets real persistence for at least one of them. Decide which seams
  Desktop gets real backends for (file-based preferences, a JVM keychain backend, both, or
  neither and Desktop inherits the stubs), and implement the corresponding `jvmMain`
  actuals in the module that owns each seam. This is also where any new Desktop-only seam
  lives if one turns out to be needed (window icon, tray, anything the other two platforms
  do not have).

  **Decision: both seams get real backends, and they are not the same kind of store.** The
  two seams are not symmetric and treating them as one is the mistake this task was written
  to avoid. `:packages:user-defaults` holds non-sensitive, device-scoped values — the
  appearance scheme and the last-visited project — and both are properties of the *machine*,
  so a restart must not lose them and an unencrypted file is the right store. Credentials are
  the opposite: a file is exactly what `PreferencesBackend`'s own doc says must never hold a
  token, so they get an OS-backed store instead.

  Amended mid-task, recorded because the first answer was wrong in an instructive way. The
  first pass shipped preferences only and left the keychain on `InMemoryKeychain`, on the
  reasoning that the JVM has no system keychain. That reasoning was true of the *JDK*, not of
  the platform: macOS has had `Security.framework` all along, and it is reachable from the
  JVM through JNA. With only the preferences half done, **a Desktop restart still did not
  restore a session** — the user signed in again on every launch. That is now fixed, so the
  session survives a Desktop restart on macOS.

  Implemented:
  - `packages/user-defaults/src/jvmMain/…/FilePropertiesBackend.kt` (new) — a
    `Properties`-backed backend over one file, with the app-support directory resolved in
    this module rather than borrowed from `:packages:database`, because `user-defaults`
    sits below `database` in `allowedLibraryTable` and cannot depend on it.
  - `packages/user-defaults/src/jvmMain/…/JvmPropertiesBackend.kt` — `platformPreferences()`
    now routes on the new process flag: file-backed on Desktop, in-memory on the JVM test
    host, so a test that writes a preference still leaves nothing behind.
  - `packages/core/src/jvmMain/…/DesktopAppMarker.kt` — the `isDesktopApp` flag. It was
    sitting untracked; this commits it.
  - `shared-ui/src/jvmMain/…/DesktopMain.kt` — sets the flag before `application {}`, and
    the two imports that named `com.tasktracker.database` (`isDesktopApp`, and a
    `preferencesFilePath` that never existed) are gone. `shared-ui` does not depend on
    `:packages:database` and the architecture table says it should not; the shell now reads
    the path through `:packages:user-defaults`, which it already had.
  - `packages/keychain/src/jvmMain/…/MacOsSecurity.kt` (new) — the JNA bindings to
    `CoreFoundation` and `Security`: the four `SecItem*` calls, the `CFDictionary` helpers and
    the pointer ownership rules. The `kSec*` and `kCFBoolean*` constants are read out of the
    framework's exported globals rather than recreated by name, so a wrong symbol fails loudly
    instead of producing a query that matches nothing.
  - `packages/keychain/src/jvmMain/…/MacOsKeychain.kt` (new) — `KeychainAccess` over one
    `kSecClassGenericPassword` item per key, add-then-update on a duplicate, and a namespace
    wipe scoped to our own service.
  - `packages/keychain/src/jvmMain/…/PlatformKeychain.jvm.kt` — routes: the JVM test host and
    any non-macOS Desktop process keep `InMemoryKeychain`, and the non-macOS case says so in
    the log rather than pretending to store something. macOS Desktop gets `MacOsKeychain`.
  - `gradle/libs.versions.toml` + `packages/keychain/build.gradle.kts` — JNA, a `jvmMain`-only
    library edge, so neither architecture table is touched.

  Verified: 7 new tests in `:packages:user-defaults`'s `jvmTest`, all mutation-checked —
  reverting the write path turns 4 red, pointing the router at the in-memory backend turns 1
  red, renaming the preferences file turns 1 red, and making an absent file throw turns 1
  red. `:packages:user-defaults:jvmTest` 26/26 green; `verifyArchitectureGraph` and
  `verifyModuleImports` green with no new edge; `:androidApp:compileDebugKotlin` green.
  `:shared-ui:jvmTest` unchanged at its 53/7 baseline, and all 12 JVM failures were
  reproduced at `HEAD` in a stashed clean tree, so none of them belongs to this work.

  Verified, keychain half: 5 new tests in `:packages:keychain`'s `jvmTest`. Two assert the
  routing and are mutation-checked — removing the test-host guard turns one red, and it names
  the actual type it got. Three exercise the real keychain against a unique
  `kSecAttrService` and are **opt-in**, because writing to a developer's login keychain is
  not something a default `jvmTest` should do:

      TASKTRACKER_KEYCHAIN_INTEGRATION=1 ./gradlew :packages:keychain:jvmTest

  That run found a real defect, which is the reason it exists: `SecItemDelete` treats an
  unspecified `kSecMatchLimit` as *one*, the same default `SecItemCopyMatching` has, so the
  first version of `removeAll` deleted a single item, returned `errSecSuccess`, and left the
  refresh token in the keychain. A sign-out that silently keeps a refresh token is the exact
  failure `KeychainStore.removeAll` is written to prevent. Measured with two items under one
  service: `removeAll` left one behind; with `kSecMatchLimit = kSecMatchLimitAll` it removes
  both. The fix is commented with that measurement, and the test fails without it.
- [x] **4.7 Confirm Compose resources and strings render on Desktop** · P1 ·
  Verification — `:packages:design-system` already owns `compose.resources` and already
  generates `Res`/`stringResource` for all CMP targets including Desktop. Launch the Desktop
  app (or run the Desktop test host) and confirm the shell renders strings from
  `composeResources` rather than falling back to raw strings, and that no new resource home
  is needed. If a Desktop-launch icon or window asset is wanted, decide its location here;
  if not, record that Desktop reuses the existing compose resources with no new asset work.

  **Confirmed, no new resource home.** The generator emits the `Res` class once, into
  `com/tasktracker/resources/Res.kt`, and assembles resources per target — `jvmMain` and
  `jvmTest` both appear under `build/generated/compose/resourceGenerator/assembledResources/`,
  so the Desktop target is a first-class consumer and not a target that has to borrow
  Android's copy. `:shared-ui`'s Desktop entry renders the same `TaskTrackerApp` as the other
  two shells, which is where the Desktop resources are actually exercised.

  The gap was verification, not wiring: **nothing in the suite named an expected string**, so
  a host that resolved every resource to the wrong value stayed green — the shell scenarios
  only fail on a missing bundle incidentally, when `stringResource` throws. Added
  `packages/design-system/src/commonTest/…/ResourceStringsTest.kt`, which asserts three
  resolved values through `getString`: `app_name` (a `translatable="false"` identity string),
  `settings_title` (an ordinary label) and `status_in_review` (a status word). One value would
  not be enough — a single accessor still passes if the generated properties are crossed.

  Asserted structurally rather than by rendering, per the `AGENTS.md` rule: the property is
  "the resource layer resolves on this target", which is readable without a frame, and a
  rendered assertion would additionally be at the mercy of the Android host's viewport for no
  gain. Mutation-checked by editing `strings.xml` itself (`settings_title` → `SettingsMUTATED`),
  which turns the test red and prints both values, then reverted.

  **Window icon: no new asset, and nothing to decide yet.** The Desktop target has a
  `runDesktop` `JavaExec` and no packaging task — there is no `nativeDistributions` block, so
  no `.app` bundle is produced and therefore no icon file is consumed. Choosing an icon is a
  packaging decision and belongs with packaging; recording "no new asset work" here is
  accurate rather than a deferral.
- [x] **4.8 Add Desktop to the test-host picture** · P1 ·
  `Project.md` test-host table + any new Desktop test entry points — Document Desktop as a
  test host in the table. The existing `commonTest` scenarios and pixel tests already run on
  a JVM target and Desktop is a JVM target, so the question is whether Desktop passes the
  same suites the JVM host does. Decide whether Desktop is its own column (with its own
  entry-point class, like `HostedGraphScenariosTest`) or folds into the existing JVM column;
  the source-set shape does not by itself force either choice. If Desktop exposes any new
  `expect`/`actual` that the JVM test host did not cover, add the matching test before this
  task is done.

  **Decision: Desktop folds into the JVM column, and inspecting the target is what settles
  it.** `shared-ui/src/jvmMain` (the app) and `shared-ui/src/jvmTest` (its tests) compile to the
  same `jvm` target and the same source sets, so `jvmTest` *is* the Desktop app's suite. A
  Desktop column would run the same tasks over the same code and report the same numbers
  twice, and `:shared-ui:runDesktop` is a `JavaExec` launch rather than an assertion. Recorded
  in `Project.md` → Tests as a row that says so plus a note under it, and in the `AGENTS.md`
  host table. No new `expect`/`actual`: Desktop adds none, so there was no host implementation
  to add and the atomicity rule had nothing to bind.

  **The gap it found.** Desktop exposes no new seam, but it does hold one Desktop-only
  behaviour and nothing asserted it. `DesktopMain`'s `main` set `isDesktopApp = true`, and that
  line is the entire difference between the app and the test host: both JVM storage seams pick
  their backend from the flag when they construct it. Deleting it left the suite
  byte-identical while the app silently went back to in-memory preferences and the in-memory
  keychain — a signature that no restart survives. Same shape as the `CachedProfile` gap, and
  exactly the class of claim 4.7 found unasserted for resources; it was also unreachable,
  because `main` opens a window and blocks until it closes.

  Fixed by extracting `startDesktopProcess(openShell)`, which marks the process and *only then*
  opens the shell — the order is the contract rather than a style choice, because the shell's
  `AppGraph.start()` constructs every store — plus
  `shared-ui/src/jvmTest/…/DesktopEntryPointJvmTest.kt` (3 tests) which enters it. The three
  assert: the flag is already set at the instant the shell opens; the preferences router
  therefore hands the shell `FilePropertiesBackend` rather than the test host's
  `JvmPropertiesBackend`; and the test host still gets the in-memory stub, which is the half a
  leaked flag would break (and the half that protects the shared scenarios from writing to the
  developer's real files). They read the type only — `FilePropertiesBackend` loads per call and
  its constructor touches nothing — so no real preferences file is opened or written.

  Mutation-checked three ways: deleting the assignment turns **two** red, and swapping the two
  statements turns the same two red. Deleting the flag's restoration in the test helper turns
  tests red as well — the helper's own check catches the first test that leaks and the stub
  test's entry assertion catches a leak from before it, so a leak is red in every method order.
  Each message names the flag and the value it actually saw. The keychain half
  of the same decision needs no test here — `:shared-ui` has no edge to `:packages:keychain`
  (the architecture table says it should not) and that routing is already held by
  `MacOsKeychainTest`'s flag flip in the module that owns it. The three tests are `jvmTest`-only
  because the code under test is `jvmMain`, so the `commonTest`-body-per-host rule does not
  apply: there is nothing to run on the other two hosts.
- [x] **4.9 Run the decision command with the Desktop target in the picture and reconcile** · P1 ·
  Verification — Run the decision command from `Project.md` (or the Desktop-equivalent
  subset) after 4.2–4.7 and confirm `shared-ui` and the modules it touches are green at the
  module level before this section is marked done. If any pre-existing failure behaves
  differently on Desktop than on the JVM host, note the difference here rather than silently
  folding it into the existing numbers.

  **Measured, one run, nothing restored from cache.**

  ```bash
  ./gradlew jvmTest iosSimulatorArm64Test verifyArchitectureGraph verifyModuleImports \
            :androidApp:compileDebugKotlin --continue --rerun-tasks
  ```

  The `test-results` trees were deleted first and the run forced, so the figures are that run's
  output: 524 actionable tasks, **524 executed, 0 from cache**, 0 compile errors. JVM **1534 tests,
  12 failed**; iOS simulator **1494 tests, 12 failed** — the same documented 12 on both hosts,
  and none of them new. `verifyArchitectureGraph` passed, all 27 `verifyModuleImports`
  tasks passed, `:androidApp:compileDebugKotlin` passed. The JVM figure is the pre-4.8 1531 plus
  the 3 entry-point tests; iOS is unchanged because those tests are `jvmTest`-only.

  Two things worth carrying:
  - **The run exits non-zero, and only on the pre-existing 12.** `--continue` is not optional:
    without it Gradle stops after the first failing test task and the verification tasks never
    run at all, so a red exit status here says nothing about the architecture checks either
    way. Run it with the flag and read the task lines.
  - **No pre-existing failure behaves differently on Desktop than on the JVM host, because they
    are the same host** — that is what 4.8 decided. The shell's code is compiled and exercised
    by `jvmTest` itself; what differs at runtime is the storage wiring, and that is held by
    4.8's test rather than by anything the decision command can observe.

  The Android-host row is **not** re-measured and stays at 1238 tests / 33 failures: nothing it
  compiles changed (`DesktopMain.kt` is `jvmMain`, and the new test is `jvmTest`-only), which
  `:androidApp:compileDebugKotlin` passing in this same run supports. That is reasoning rather
  than a measurement, and it is recorded here as such.

  With that, section 4 is complete: 4.1–4.9 all landed.

---

### 4.0 Implementation order (Desktop)

1. **P0**: 4.1 → 4.2 → 4.3 → 4.4 — shape committed (source set), target, entry point,
   tables. Nothing renders until the entry point exists and the tables allow it.
2. **P1**: 4.5 → 4.6 → 4.7 — run task, persistence decision, resources confirmation.
   After this the app launches and keeps its state across restarts (or deliberately does
   not).
3. **P1**: 4.8 → 4.9 — test-host documentation and the decision-command reconciliation.
   Last, because it measures the work above.

### 4.0 Notes on fit

- **Clean architecture is preserved**: Desktop is a shell, not a feature. It sits above
  `:composition-root` and `:shared-ui`, same as the other two shells, and never reaches
  into a data module directly.
- **No `commonMain` changes required**: the existing screen tree, models, and use cases
  render on Desktop through the same `TaskTrackerApp` the other shells render. Desktop work
  is confined to the entry point, the target declaration, and any JVM-only actuals.
- **Persistence is the one real product decision**: whether Desktop inherits the in-memory
  test-host stubs or gets real file-based storage is a product choice, not a build detail,
  and it determines whether a signed-in Desktop user survives a restart.
- **Testing**: Desktop reuses the existing Compose UI test infrastructure (it is a JVM
  target with `ui-test` + skiko already on the classpath); the new work is documenting it
  as a host and adding any entry points the host split requires.

---

## 5. A layout that fits a desktop window

Section 4 made Desktop *launch*. It did not make it *fit*: the shell is laid out for a
phone and renders exactly that way in a 1440×900 window. This section is the layout half.

The case for it is structural rather than a matter of taste. Every navigation decision in
the shell is one of three mobile shapes, and each has a canonical wide counterpart it
cannot become by being stretched:

- **The bottom tab bar** is a floating glass capsule anchored `Alignment.BottomCenter`,
  sized by `fillMaxWidth`, with the lens's segment width derived as `maxWidth / tabs.size`.
  At 390dp that is five segments of 78dp. At 1440dp it is five segments of 288dp — a
  capsule a metre wide with the tabs marooned inside it, and a lens that crosses a third
  of the window on every switch. `TTGlassTabBar`'s own KDoc already records this failure
  measured once: "the first render of this measured a 540px bar in a 1024px window".
- **Navigation is single-pane.** `NavigationHost` renders the top of the active tab's
  stack and nothing else, filling the window. The Sprints tab's `SprintList` →
  `SprintDetail` is a list-detail pair being shown one at a time, which on a wide window
  is half a window spent on nothing plus a navigation step that did not need to happen.
- **Every modal is a bottom sheet.** `WorkItemEditorDestination` is an `AnimatedVisibility`
  sliding in from the bottom edge at `fillMaxSize`, and `TTBottomSheetScaffold` anchors its
  surface at `Alignment.BottomCenter` with a `slideInVertically`. On a phone that is the
  thumb-reachable gesture. On a desktop it is a full-height panel arriving from off-screen
  — and the `LocalTabBarScrollUnderInset` it pads by exists only to clear a bar the
  desktop window does not have.

**This is a layout, not a port.** No screen, model, use case, port or entity changes, and
`commonMain` stays the single screen tree all three shells render. What changes is which
chrome the shell draws around that tree and how many panes it draws it in.

**The one invariant every task below inherits.** The layout is chosen from the **width it
is offered**, never from which platform is running. `isDesktopApp` already exists in
`:packages:core` and is the obvious thing to branch on, and it is the wrong question: it
answers "which process am I", not "how much room do I have". A desktop window dragged to
700dp wide would then be handed a two-pane layout that does not fit, and a tablet at
1024dp would be handed the phone one. A width-derived answer gets both right and needs no
platform check anywhere.

- [x] **5.1 A width-derived layout class, and no new `expect`** · P0 · new
  `shared-ui/src/commonMain/…/ShellLayoutClass.kt`, `…/ShellLayout.kt`, and
  `shared-ui/src/commonTest/…/ShellLayoutClassTest.kt` — A pure
  `ShellLayoutClass.Compact | Expanded` with a named threshold and a `forWidth(Dp)`
  factory; a `LocalShellLayoutClass` defaulting to `Compact`; and a `ShellLayout`
  composable that reads `BoxWithConstraints.maxWidth` once and provides the class to
  everything below it. `RootScreen` wraps the `RootDestination.Main` branch in it.

  **Decision: `BoxWithConstraints` in `commonMain`, not a `WindowSizeClass`
  `expect`/`actual`.** The rejected alternative is the library-shaped one: an `expect`
  returning a size class, with a `jvmMain` actual reading the Compose Desktop window, an
  `iosMain` actual reading the `UIViewController`, and an `androidMain` actual reading the
  `Activity`. It buys nothing here and costs three actuals. `BoxWithConstraints` reports
  the width the shell was *actually offered*, which on every host is the same number the
  real window produces, and it is already proven in this codebase — `TTGlassTabBar` uses
  it for the lens. Decisively, the JVM test host has no real window at all, so an
  `expect/actual` would need a test-only override to be drivable, whereas the width
  arrives through the existing `ShellScenarios.setUpShell(width: Dp?)` hook with no new
  seam. `DECISIONS.md` already carries the rule that every `expect` needs a `jvm` actual;
  this task adds no `expect` to satisfy it.

  **Decision: one threshold, at 840dp.** Material's three-way split puts a medium class at
  600dp, and the middle class is rejected deliberately rather than overlooked: between 600
  and 840 a side rail plus a single pane costs horizontal room and gains nothing, because
  there is no second pane to spend it on. One threshold also keeps "which chrome" and "how
  many panes" from becoming two answers that can disagree — the failure mode `RootScreen`'s
  KDoc already rejects for the session root.

  `ShellLayout` is placed **below the splash gate**, around the signed-in area rather than
  around the whole root. `BoxWithConstraints` is a `SubcomposeLayout`, and `RootScreen`
  documents at length why nothing may add a measure pass ahead of the splash handoff: a
  board's models must not be built behind a splash that has not finished drawing. The
  sign-in tree and the splash have no use for a layout class, so they do not get one.

  Tests are pure functions — no composition, no pixels, so no per-host entry points and
  they run as plain `@Test`s on all three hosts. The boundary is asserted on both sides
  (`839.dp` and `840.dp`, plus `839.5.dp` to pin `>=` against `>`), `Dp.Infinity` is
  asserted Expanded because an unbounded parent genuinely produces it, and the threshold
  itself is pinned to its exact value so a later change to it is deliberate.

  **Closed by this run, and the commit message was wrong about its own verification.** The
  5.1 commit states the build "still cannot run in this sandbox" and that verification was
  done by invoking the Kotlin compiler out of the Gradle cache by hand. That is not true of
  this machine: with `JAVA_HOME` pointed at the Android Studio JBR and `org.gradle.java.home`
  set in `gradle.properties`, the whole decision command runs in under a minute.
  `ShellLayoutClassTest` is 9/9 on both hosts through Gradle, and the mutation ritual stands —
  flipping `>=` to `>` turns `the threshold width itself is expanded` red, and moving the
  threshold to 800dp turns three red including the pin.

  **The gap this task admitted is now closed.** 5.1 noted that nothing asserted `ShellLayout`
  actually reads `maxWidth` and provides the class — the two-line body was compile-checked and
  not behaviour-checked. 5.3's scenarios close it from both sides: `aWideShellDrawsTheRailAndNoBar`
  can only pass if a 1200dp shell reached `Expanded`, and `aNarrowShellDrawsTheBarAndNoRail`
  only if a 390dp shell reached `Compact`. Neither is a fact about `forWidth`.

- [x] **5.2 `TTGlassNavigationRail`, the vertical sibling of the tab bar** · P0 ·
  `:packages:design-system` new `…/NavigationRail.kt` + its tests — One glass surface
  running down the leading edge, one travelling lens, icon over label per destination,
  `Role.Tab` and `selected` semantics, the same `ttSelectionLabelInk` for both caption
  states. Same split as the bar: the *component* lives in the design system, and the
  shell owns the threshold, the edge padding and the test tag.

  Every rule `TTGlassTabBar` documents is inherited rather than re-derived, and the three
  that matter are inherited because they were each a real defect: one glass surface for
  the whole rail and never one per destination (there is no `GlassEffectContainer` on
  these platforms to merge neighbours); the lens is a plain wash and a rim drawn with
  `drawBehind` and is **not** `ttGlassSurface`, because a second ramp inside the first
  stacks two borders; and the lens **resizes rather than scales**, because a
  `graphicsLayer` scale on the JVM renderer darkened the surface behind it by ~0.14 of
  relative luminance, which is the entire alpha of the lens's own wash.

  What is genuinely new and needs its own measurement is the vertical geometry: the
  segment height is `maxHeight / destinations.size`, and unlike the bar's width a rail's
  height is **not** offered — it is the sum of its content. The bar's KDoc records the
  exact bug this invites ("a 768pt-tall wrapper with the tabs parked dead centre at
  y=363"); the rail has the same trap in the other axis, and its lens travel is derived
  from a segment height that has to come from the row of cells rather than from the rail.

  Tests: the geometry and the ink derivation are structural and belong in `commonTest`
  beside `TabLensCornerTest` and `TabLensContrastTest`; the silhouette, the hairline and
  the glass edge are pixel properties, and `AGENTS.md` says those are measured on **both**
  rasterising hosts.

  **Landed. No new token, which is the one decision here worth reading.** The plan as written
  proposed a `ttSpacing.navigationRailWidth`, and it is not in the tree. Two reasons. A rail's
  width is not offered — like its height it is the sum of its content, the widest caption plus
  the padding — so there is no number to put in a token, and `design-system/AGENTS.md` requires
  a new token to *"document the visual measurement that justifies any non-obvious value"*.
  Picking one and writing the justification afterwards is the inversion rule 4 exists to
  prevent. The consequence is that `Spacing.kt` and `SpacingScaleTest.kt` are untouched; an
  off-ramp value would have had to be added to that test's explicit `offRamp` enumeration, which
  was never in the plan.

  **The segment height is read from a `matchParentSize` `BoxWithConstraints` on the cell column**,
  as the plan says it must be, and both lens dimensions come from there rather than from the
  rail's own box. `BoxWithConstraints` reports its *incoming* constraints, not its resolved size,
  so a rail wrapping its content reports an unbounded width and a segment derived from it would
  be `Infinity / 5`. The bar gets away with reading `maxWidth` for its segment because
  `fillMaxWidth` resolves the width before it is measured; a rail has no equivalent.

  **`lensStretch` became `internal` rather than being copied.** One word in `TabBar.kt`. The
  alternative was twenty duplicated lines in `NavigationRail.kt` and two private functions with
  the same name and signature in one package.

  **Both stretch tokens swap axis, and that is deliberate.** `TAB_LENS_WIDEN` and
  `TAB_LENS_SQUASH` describe the lens's behaviour along its *travel*, not along the screen. On
  the bar the travel is horizontal so the lens widens along x; here it grows along y and draws
  in from the sides. One pair of numbers for one material beats inventing a second pair that
  measures the same effect.

  **Measured, on both hosts, and they agree to the byte.** `NavigationRailPixelTest` diffs two
  rails with the lens on different destinations and everything else blank. Clipped, the lens's
  worst corner reads `#02` on light and `#00` on dark — below the `#03` noise floor, so a
  correct lens contributes nothing to its own footprint. With the clip removed the same corner
  reads **`#20` (0.125)** on dark, against the bar's `#21`, and the test turns red. The JVM and
  the iOS simulator report identical values in both states, which is the one thing this
  component's other measurements do not: the captions differ between rasterisers by most of a
  point of contrast, because a glyph stroke's coverage is a rasteriser's business, while this is
  a hard-edged rectangle overdrawing its own silhouette.

  **Two test failures of the test, both caught by the mutation ritual and both worth recording.**
  The first version of the centring scenario asserted only that the lens's centre fell *inside*
  its destination's slice, and it passed against a mutation that dropped the half-segment
  centring outright. The arithmetic says why: at 55dp per segment the lens is 49dp tall, so the
  centre may sit a quarter of a segment either side of the middle — roughly 27dp of slack on
  each side. A containment check over a shape that nearly fills the thing containing it is not a
  check. It now asserts exact equality, which is available because at rest the lens is shorter
  than its segment by construction and the clamp cannot engage. The second was a whole axis: the
  rewritten test added the lens inset to the *y* coordinate and failed every index by exactly
  3.0dp, because the travel axis is y and the inset belongs on x. The component was right and
  the test was wrong.

  **The lens window is the rail, not a destination cell.** The bar windows on the selected tab's
  cell; a rail's lens spans the rail's full width while a cell is only as wide as its own
  caption, so windowing on a cell would clip the shape under measurement.

- [x] **5.3 The shell picks its chrome from the width** · P0 · `shared-ui/…/NavigationHost.kt` —
  Under `Compact`, today's bottom `TabBar` and today's inset accounting, byte for byte.
  Under `Expanded`, `TTGlassNavigationRail` on the leading edge with the content beside
  it, and the bar is not composed at all.

  The load-bearing half is the inset, not the branch. `tabBarInset` is measured from the
  bar's height and published as `LocalTabBarScrollUnderInset`, which every scrolling list
  pads its last row by and which `TTBottomSheetScaffold` reads to keep its action row out
  from under the bar. Under `Expanded` there is no bar, so the inset must be `0.dp` — and
  that is already the local's default, which is why the rail branch simply does not set
  it rather than setting it to zero. Reusing the default is the point: a second spelling
  of "there is no bar" is a second thing to keep true.

  `TabBar`'s `WindowInsets.ime` keyboard guard and its `navigationBarsPadding()` are
  mobile concerns that become no-ops on a desktop window; they are left alone rather than
  made conditional, because they are already correct at zero and a branch that says so is
  a branch that has to be re-verified on the phone.

  New scenarios in `ShellScenarios`, driven by the existing `width` parameter: a narrow
  shell draws the bar and no rail; a wide shell draws the rail and no bar; a wide shell
  publishes a zero scroll-under inset; the rail marks exactly one destination selected.
  The three existing bar-geometry scenarios (`theTabBarFloatsAboveTheBottomEdge`,
  `theTabBarIsWiderThanItsTabs`, `fiveTabsFitAtPhoneWidth`) must keep passing unchanged —
  they are the evidence that the compact path was not disturbed.

  **Landed, and the branch is `when (LocalShellLayoutClass.current)` with the expanded arm
  not touching `tabBarInset`.** Reusing the `0.dp` default of `LocalTabBarScrollUnderInset`
  rather than writing a zero is the plan's own point and it survives: there is no second
  spelling of "there is no bar" in the tree.

  **The rail's width is measured and fed back as the content box's leading inset**, by the same
  `onSizeChanged` idiom the bar already uses for `tabBarInset`. It has to be measured rather than
  known for the same reason 5.2 dropped the width token: a rail is as wide as its widest caption.
  Under `Compact` the value is written by nobody, stays `0.dp`, and the padding is a no-op —
  which is why the compact path is unchanged rather than unchanged-in-effect.

  **The load-bearing change was in the test helper, and it is a finding rather than a chore.**
  `setUpShell`'s `width` defaulted to `null`, meaning "whatever viewport this host gives the
  root". That was invisible while the shell drew the same chrome at every width, and it stopped
  being true the moment the chrome started following the width: the JVM host renders into a
  window comfortably past 840dp, so twelve scenarios that never stated a width silently became
  expanded-shell scenarios and began failing on a `shell.tabs` tag that was no longer composed.
  The shell was right and the scenarios were underspecified. The default is now `PHONE_WIDTH`, so
  a scenario that does not say what width it is about gets the width these scenarios are about.
  A scenario that wants the wide layout passes `DESKTOP_WIDTH` — 1200dp rather than something
  just over the threshold, so a moved threshold cannot silently keep these passing.

  **Four scenarios, four mutations, all caught.** Removing the content padding turns
  `theRailIsALeadingColumnRatherThanAStretchedOne` red; making the chrome ignore the layout class
  turns three red; removing `semantics { selected }` from the rail cells turns
  `exactlyOneRailDestinationIsMarkedSelected` red; and the three existing bar-geometry scenarios
  pass unchanged throughout. The content-besides-the-rail assertion reads the projects list's
  left edge rather than the content box's, because the box carrying the padding is anonymous — a
  node inside it is the only thing that can show whether the padding reached the pixels. It reads
  the *list* and not the board's columns because the landing tab has no open project and draws no
  columns, and it reads a full-bleed screen because a centred card would clear the rail for
  reasons that have nothing to do with the inset.

  **The three wide-width scenarios cannot run on the Android host, and the host is the reason.**
  `setUpShell` expresses a width as a `Box(Modifier.width(…))` around the app, and a fixed width
  is a *request*: the box is measured against its parent's constraints, so a host with a narrower
  viewport silently clamps it. Robolectric's default viewport clamps 1200dp to something well under
  the 840dp threshold, so the shell correctly draws the phone chrome there, the rail is absent, and
  all three scenarios failed for a reason that has nothing to do with the rail. `shellHonouredRequestedWidth`
  measures the shell it actually got, prints when it was clamped, and skips. A desktop window is
  not a configuration the Android host has, which makes this an inability to express the case
  rather than a failure of it — the same bargain `thisHostRasterisesText` makes, for the same
  reason, and recorded in `AGENTS.md` rather than left to be found. The fourth scenario,
  `aNarrowShellDrawsTheBarAndNoRail`, passes on all three hosts and is the one that guards the
  compact path on the Android host.

  **The rail is full-height, and its glyphs share one axis; both were defects found by looking at
  it.** The first rail wrapped its destinations and sat at `Alignment.CenterStart`, which measured
  216dp of a 768dp window — a glass capsule a quarter of the window's height floating in the middle
  of the left edge, which reads as a floating capsule rather than as navigation. It now fills the
  height it is offered, anchored to the top, floating by `tabBarEdgeInset` at each end so it is
  not full-bleed either.

  The cells share that height with `weight(1f)`, which is load-bearing rather than cosmetic and is
  the rail's **only** height owner: the lens's segment is `maxHeight / destinations.size`, so cells
  that did not divide the height equally would put the travelling lens in the wrong place on a
  tall window. Two `fillMaxHeight` calls were carried while this was built and both were
  **provably redundant** — mutating either left every measurement in the suite identical, because
  the weighted cells had already taken the height — so both were deleted. A modifier that looks
  like the source of a value and is not costs exactly what it cost here.

  **The misaligned glyphs were a real defect, not a tuning problem.** Cells are as wide as their
  own captions and the captions differ — `"Board"` is far narrower than `"Projects"` — so with
  every cell placed at the leading edge, each glyph sat at *its own* caption's centre and the five
  icons were visibly out of line. `horizontalAlignment = CenterHorizontally` on the column fixes
  it, because centring each cell puts every cell's midpoint on one shared axis, and a cell centres
  its own glyph on its own midpoint. Giving every cell the same width would work too but needs a
  width to give them, and the widest caption is a typography measurement the design system cannot
  know — the same finding that kept a rail-width token out of the tree. Measured after the fix,
  the five destination centre lines run **48.5..49.0dp, a spread of 0.5dp**, which is the caption
  widths' own sub-pixel rounding; before it they were tens of dp apart.

  **Two further defects, both found by looking at the running app rather than by a test.** The lens
  overran the destination it was marking, and the first destination sat hard against the rail's top
  edge while the last had most of a tall cell empty beneath it.

  The lens overrun is a consequence of the full-height rail. A rail's segment is a fifth of the
  rail's height — **146dp measured**, against about 49dp on the bar — so the lens's 10% stretch
  takes it from 140dp to **154dp, eight points taller than its own cell**. Its *top* edge still
  landed correctly, which is the edge a bounds check looks at, and its bottom edge crossed into the
  next destination. `railLensHeight` now caps the growth at the segment: the settle is kept across
  the cross axis, where the lens draws in, and given up only along the travel, where a tall cell
  has nothing to spare. It is a pure function rather than an assertion on the animation because at
  rest the stretch is zero and the lens fits with room to spare — **a rendered measurement cannot
  see the clamp at all**, and the first attempt, which drove the test clock one frame into the
  settle, did not catch it either.

  The spacing asymmetry was `Arrangement.spacedBy(gap)` packing to the top: correct while a cell was
  exactly as tall as its content, silently wrong the moment cells were weighted. Each destination
  now centres in its cell, which makes the two ends symmetric — measured before the fix, **5dp
  above the first icon and 109dp below the last**. The icon-to-caption gap is unchanged;
  `spacedBy` with an alignment still spaces its children by that gap.

  **The lens's travel position is now asserted, which was the last unasserted claim in this
  section.** `selected` and the lens are two independent things: the first is set by `RailCell`'s
  `semantics { selected = … }`, the second by the animated `segmentHeight * selectedIndex` that
  positions it. Pinning that travel to `0f` parks the lens permanently on the first destination —
  an indicator marking the wrong thing on every other destination, with the selection semantics
  still perfectly correct — and the whole suite stayed green. `exactlyOneRailDestinationIsMarkedSelected`
  now measures the lens before and after a tap: it must *move*, and it must end up on the
  destination that is selected. Two assertions because they fail differently — a lens that never
  moves and a lens that falls off the end both satisfy "it moved", and only the containment catches
  the second. Measured after `waitForIdle`, so the spring has settled and the reading is the final
  position rather than a frame of the animation.

  **Still unasserted:** the vertical centring of each destination. Removing it leaves the build
  green. Closing it needs a tag on a cell's *content* rather than its clickable area, which widens
  the production surface — recorded rather than done, because a tag added for a test is a tag the
  next rename misses.

  **Three of the four entry points were missing `@Test` for four runs, and nothing said so.**
  They were inserted after `fiveTabsFitAtPhoneWidth`, which pushed the `@Test` that belonged to
  the *next* entry above the first new method and left the four with none. The suite stayed at
  47 tests and green, three mutation checks came back with no output at all, and the only reason
  it surfaced is that a mutation which should have turned a test red did not. A test that does
  not run is indistinguishable from a test that passes; this file's own coverage-gap notes are
  about assertions that are *present and wrong*, and this was the blunter version.

  **The planned "a wide shell publishes a zero scroll-under inset" scenario is not written, and
  the reason is a property of every consumer rather than of this change.** All six readers of
  `LocalTabBarScrollUnderInset` spend it as scroll *range* — `contentPadding` on a lazy list, or
  bottom padding on a scrolling form — and not one of them as a viewport inset. A local published
  as scroll range is invisible to bounds: removing it shortens a scroll, and the only way to read
  that back is to scroll to the end of a list, which is what `theLastRowOfEachListScrollsClearOfTheTabBar`
  does — one of the twelve pre-existing failures. Asserting the zero that way would duplicate a
  red scenario rather than add coverage. The structural precondition it rests on *is* asserted,
  from both sides: no bar is composed at a wide width, and nothing writes the inset.

- [x] **5.4 Two panes for the Sprints tab** · P1 · `shared-ui/…/NavigationHost.kt`,
  `shared-ui/…/SprintPanes.kt` — Under `Expanded`, the Sprints tab draws
  `SprintList` and `SprintDetail` side by side and the detail pane follows the selection
  rather than replacing the list.

  **Landed. `SprintTwoPanes(listPane, detailPane)` takes two content lambdas, and that signature
  is the design.** `NavigationHost` already renders exactly these two screens — the tab root for
  the list, the pushed route for the detail — from the router, which is the only navigation state
  in the module. A component taking the screens would need them handed to it, which means either
  duplicating the detail's wiring or introducing a second piece of state holding "the sprint the
  pane is showing". Both are the shape `NavigationHost` rejects for the session root and the
  editor overlay: two answers to one question, whose symptom is invisible because each pane renders
  perfectly well on its own. So the detail pane is the *same* `backgroundPushed` the single-pane
  path renders — a tap, a deep link and a restored stack differ only in **where** it is drawn.

  `Projects` and `Backlog` stay out, and that is a finding rather than a deferral. The Projects
  tab's root is the projects list and its "detail" — the board — is a *different tab*, so a
  two-pane Projects would be drawing two panes belonging to two tabs and would need a second notion
  of "the project under the cursor" to reconcile them. Backlog's three sections are one screen, not
  pushes. `SprintDetail` is the only pair whose second pane is already a route in the same stack.

  `BackHandler` is untouched: popping removes the detail pane and the list goes back to full
  width, which is the right answer in both arrangements.

  **Judgement recorded:** with *nothing* selected the list stays full width and there is no second
  pane. A "select a sprint" placeholder would need a new string in `design-system`'s `strings.xml`,
  and an empty glass pane reads as broken. Always-two-panes is the better end state and is not
  done here.

  **Three scenarios; the two mutations that could be aimed at them, both caught.** Presence is not
  the assertion — both panes existing is satisfied by the detail drawn *over* the list, which is the
  single-pane layout with the list left composed underneath, and it is the easy mistake here
  because the two are the same two composables. `list.right < detail.left` is what says they are
  beside each other; stacking them turns it red with *"the list runs to 1000.0dp and the detail
  starts at 97.0dp"*. Reverting the branch turns two scenarios red.

  **The choosing-another-sprint scenario's first version asserted the wrong thing.** It asserted
  the detail pane had *moved*, which is wrong by design: a two-pane layout's detail pane is a fixed
  slot and correctly stays put while its contents change. It failed against correct behaviour. The
  two assertions that matter are the stack's **length** — the one this task's own note names, and
  the one that fails if the pane were implemented as a push — and the pane's **rendered text**,
  read by walking the semantics tree because the tagged card is a merged node and `config[Text]`
  on it throws `Key not present`.

  Two seeding facts, each of which cost a run, recorded so they are not rediscovered:
  `shellTestSprint` has a **fixed default `id`**, so two seeded sprints collide in the list's keys
  with `Key "…" was already used`; and `SprintDetailGoalTag` is the goal *sentence*, absent when
  none is set, not the goal's story points.

  **The router is not duplicated.** The detail pane renders
  `navigation.pathFor(AppTab.Sprints).lastOrNull()` — the same expression the single-pane
  path renders — so a tap, a deep link and a restored stack all land on the same screen
  and differ only in *where* it is drawn. The rejected alternative is a second piece of
  state holding "the selected sprint", and it is rejected for the reason `RootScreen`
  already gives about the session root: two answers to one question is how a selection
  ends up applied to the list pane and not to the detail, or the other way round.

  **Projects and Backlog are deliberately out of scope, and this is a finding rather than
  a deferral.** The app contains exactly one list-detail pair. The Projects tab's root is
  the projects list and its "detail" — the board — is a *different tab*, so a two-pane
  Projects would be drawing two panes belonging to two tabs and would need a second
  notion of "the project under the cursor" to reconcile them. The Backlog tab's
  Backlog/Archived/Kanban are sections of one screen, not pushes. `SprintDetail`'s
  selection is the only pair where the second pane is already a route in the same stack,
  which is what makes it the one that can be widened without new state.

  Also asserted: selecting a different sprint in the list changes the detail pane without
  changing the tab's path length, which is the assertion that would fail if the pane were
  implemented as a push.

- [x] **5.5 Modals present as windows, not as sheets from the bottom edge** · P1 ·
  `:packages:design-system` `…/BottomSheetScaffold.kt` + `shared-ui/…/NavigationHost.kt` —
  Under `Expanded`, `TTBottomSheetScaffold` centres its surface with a scale-and-fade
  rather than anchoring it at `Alignment.BottomCenter` and sliding it in vertically, and
  the `WorkItemEditor` overlay does the same instead of `slideInVertically` at
  `fillMaxSize`.

  **Landed. The sheet learns the layout class through a design-system local, because there
  is no other channel.** `TTBottomSheetScaffold` sits below `:shared-ui` in the graph and
  cannot take a shell type in its signature — so `SheetPresentation` (`BottomSheet` /
  `Window`) and `LocalSheetPresentation` (defaulted `BottomSheet`) live in design-system,
  beside `LocalTabBarScrollUnderInset`, and the shell provides `Window` iff
  `ShellLayoutClass.Expanded` at the same `CompositionLocalProvider` site. The 5.1 KDoc
  said `LocalShellLayoutClass` was "the only mechanism that reaches" the scaffold; as
  landed, that local is declared in `:shared-ui` and unreachable from design-system, so
  the claim was aspirational — the local is still the only mechanism, but the
  *declaration* has to sit where both ends can see it, which is what the second local
  does. A derived boolean would have been two answers to "what layout is this"; a
  `BottomSheet | Window` vocabulary is the component's own answer to "how do I present",
  derived once, at one site, from the one class.

  Nothing below the shell changes: previews, the sign-in tree and any test standing the
  scaffold up alone read `BottomSheet` and lay out exactly as before, which is why
  `ProjectPickerSheetRenderTest` is untouched. The scrim, outside-tap, scrolling and
  inset modifiers are shared by both arms — the fade on the `AnimatedVisibility` wrapper
  doubly so, which is why the window's column scales only, and why its duration is the
  fade's own: one scale-and-fade finishing on the same frame, not two overlapping fades.
  The scale constant (0.94f) sits beside `SHEET_SPRING` and `SHEET_FADE_MILLIS` as a
  file-local transition constant, not a token — nothing is measured or tinted from it —
  and the editor's own 0.94f in `NavigationHost` is a separate declaration with a note,
  because the modules cannot share it and a vocabulary that drifts per component is two
  vocabularies.

  **The dismissal follows the transition, not the other way round.** `dismissEditor` is
  one local function for the chevron/Cancel path and system back, because the second
  copy was exactly how the two exits could disagree. Under `Compact` it is the old dance
  verbatim — hold in `dismissingEditors`, wait `EDITOR_EXIT_MS`, pop — since popping
  first would tear the slide down mid-flight. Under `Expanded` it drops the route in the
  click: a window has no slide to finish, the delay would be dead time, and
  `dismissingEditors` becomes what it is under that arm — state nothing writes. The
  window's exit is a plain fade that never plays (the route leaves the composition at
  once); it exists so the spec is coherent, not because anything reads it.

  **Judgement recorded:** the window keeps `fillMaxWidth` — a full-width band, centred
  vertically. A max-width cap for desktop dialogs is the obvious next thought and is not
  done here: nothing in the task asked for it, and the token to spend is one nobody has
  measured.

  **Four scenarios, four mutations, each red on exactly its own.** A wide shell opens a
  sheet centred (top gap equals bottom gap within 2dp, plus a >20dp float guard so a
  full-height surface cannot satisfy "centred"); a narrow shell keeps it bottom-anchored,
  asserted as the bottom sitting within 150dp of the root — proximity, not asymmetry with
  the top. The first version read `bottomGap < topGap` and was correct on the JVM and iOS
  hosts, then failed on the Android host's cramped viewport: 24dp above, 68dp below, the
  anchored sheet filling the screen. Asymmetry is not the anchor either; nearness to the
  bottom is, and on a viewport the form fills, the two disagree. 150 separates the
  anchored gap (frame chrome, never more than a hundred) from the windowed gap (~280
  where it runs) with a margin neither placement can cross, without pinning the bar's
  measured height. The editor pair reads the router one
  frame after the click with the clock held: the wide arm must already have popped, the
  narrow arm must still hold *and* still be composed, and must have popped *and* gone by
  the settled run — the second half exists because the hold alone would green-light a
  shell that never completes the dismissal. Driven through `router.push`, not the row
  taps: `tappingASprintTaskRowOpensTheEditor` is one of the twelve pre-existing failures
  and this task neither fixes nor resteps it. `test-reviewer` said can-fail on all four
  with no findings.

  Two pieces of bookkeeping are coupled to the vertical slide and must not be re-entered.
  `NavigationHost`'s `dismissingEditors` list plus `EDITOR_EXIT_MS` exists to keep a
  dismissed editor composed until its slide finishes; a window that fades has no slide to
  finish, so the delay is a frame of dead time and the list is state nobody reads. The
  other is the sheet's `LocalTabBarScrollUnderInset` padding, which 5.3 makes zero under
  `Expanded` and which is therefore already correct — but it is correct for a *different
  reason* on each side, and that is worth a comment rather than a coincidence.

  `filingAwayInTheEditorClosesTheSheet` and `tappingASprintTaskRowOpensTheEditor` are two
  of the twelve documented pre-existing failures. This task must not be credited with them
  and must not fix them; the new scenarios run at an expanded width and are separate.

- [x] **5.6 Give the Desktop window a size, a title, and a memory** · P2 ·
  `shared-ui/src/jvmMain/…/DesktopMain.kt` + `:packages:user-defaults` — `Window` currently
  takes no size and no title, so the app opens at the platform default and a desktop layout
  is not reachable by hand without dragging. Set a default window size that is above 5.1's
  threshold and a title from the resource bundle, and decide whether the size persists
  across launches through the file-backed preferences backend 4.6 already built.

  `WindowState` is `jvmMain`-only, so this stays inside the entry point and touches no
  `commonMain` — the same containment 4.6's storage work had. If the size does persist, the
  key belongs in `:packages:user-defaults` beside the appearance and the last-visited
  project, for the reason that file already records: the window size is a property of the
  machine, not of the session, so it must not be wiped by `UserDefaultsStore.removeAll` on
  sign-out.

  **Landed. The size persists — and the key is one `removeAll` cannot reach.** The title is
  `Res.string.app_name`, the string 4.7 already asserts, so the window names the app the way
  the shell does. The opening size is `1280 × 800` when nothing is remembered
  (`DefaultDesktopWindowSize`), for the reason `ShellExpandedMinimumWidth` (840) exists: a
  first launch reaches the expanded two-pane shell without the frame being dragged, and 800
  of height gives the panes a body rather than a letterbox. The remembered size is `WxH`
  (`"1280x800"`) under the verbatim key `com.tasktracker.window.geometry`, in
  `:packages:user-defaults`' `jvmMain` (`WindowGeometry.kt`) — **not** a `DefaultsKey`,
  because `removeAll` walks that enum on sign-out and would clear a member with the session,
  and this is the machine's property rather than the session's.
  `theSizeSurvivesSignOutBecauseItIsNotADefaultsKey` pins it by calling `removeAll` and
  reading the size back; moving the key into `DefaultsKey` under the same namespaced form
  turns it red, which is the mutation that was run.

  **The read is synchronous, and that is the whole reason these functions exist.** Every
  other preference is read in a `LaunchedEffect` into a `StateFlow`; the window is the one
  that cannot wait, because `rememberWindowState(size = …)` takes its value on the first
  frame and there is no coroutine scope to suspend in yet. `WindowGeometryDefaults` blocks
  on one small `Properties` read through `platformPreferences()` — the expect/actual 4.6
  built, with the backend injectable so a test never touches the developer's real file. The
  write goes the other way: keyed on `windowState.size` with a 500 ms settle, so a drag
  cancels its own pending write per frame instead of rewriting the file per frame (the
  `ShellPreferences` preview/commit split, applied to a window), and it is deliberately not
  a save in `onCloseRequest` — that runs only on a polite close, so a killed process would
  keep whatever the last polite close left.

  **Nine tests, all `jvmTest`, because the code under test is `jvmMain`.** `:shared-ui`'s
  `DesktopWindowJvmTest` pins the gate (`DefaultDesktopWindowSize.width >=
  ShellExpandedMinimumWidth`), that a remembered size wins, and that an absent one falls
  back — the choice is a pure `desktopWindowSize(stored)`, reachable by a test that `main`
  (blocking on a real window) is not. `:packages:user-defaults`'
  `WindowGeometryDefaultsTest` pins the round trip, the survival, the unparseable value and
  the untouched file; `WindowGeometryTest` is the `WxH` table, added after `test-reviewer`
  showed that the single value driven through the store left `parse`'s promised "negative or
  zero side" branch — and the `null`, empty, trailing-`x` and half-pair shapes — deletable
  with the suite still green. Nine assertions, nine mutations, each red on exactly its own;
  `test-reviewer` says can-fail with no remaining blocking or major finding.

  **Counts.** Re-measured from the JUnit XML: JVM **1588**, iOS **1539**, Android **1283**,
  the same 12 / 12 / 34 failures. Nine are this task's and are JVM-only; the other **+4 per
  host** over the figures 5.4 recorded are 5.5's modal scenarios, which landed without
  updating `Project.md` — 5.7 reconciles that with the host tables, which this task does not
  otherwise touch (Desktop is still the JVM host, not a fourth).

- [x] **5.7 Reconcile the counts and the host tables** · P1 · `Project.md`, `AGENTS.md` — Run
  the decision command and reconcile the totals, then record the test-host consequence in
  `Project.md` and `AGENTS.md`. The structural decision itself — "the shell's layout is
  chosen by width, never by platform" — is **already in `DECISIONS.md`**, recorded in 5.1's
  change rather than here: a decision written down two tasks after it is made is a decision
  that has already had two chances to drift.

  The consequence to state precisely: Desktop is **still** not a fourth test host — 4.8
  settled that and nothing here changes it — but it now has a *layout dimension* the JVM
  host can drive, through `setUpShell(width = …)`. That is a new capability of an existing
  host rather than a new host, and the tables should say so rather than growing a column.
  Every new scenario is per-host (a `commonTest` body with three entry points), so all
  three totals move, unlike the JVM-only tests 4.6 and 4.8 added.

  **Landed. All three figures are one run's, measured together, and both docs now carry the
  same three.** 2026-10-15, one `./gradlew check`: JVM **1588**, iOS **1539**, Android
  **1283**, with the same 12 / 12 / 34 failures, name for name. `AGENTS.md`'s table had been
  frozen at 1571 / 1531 / 1275 and simply stopped being updated — the failure mode the doc
  itself names. Over the 1575 / 1535 / 1279 that 5.4 recorded, the movement is 5.5's **+4 per
  host** (its modal scenarios, never recorded) and 5.6's **+9 on the JVM only** (its `jvmMain`
  tests); the rest of the gap is the stretch before 5.4 that the table also never saw. Two
  contradictions inside `Project.md` fell out of the same re-measure: the prose said "the 33
  on the Android host" where the table said 34, and split 14 / 8 / 3 / **8** where the XML has
  **9** in `:shared-ui`. It is 34, and 14 / 8 / 3 / 9.

  **Two figures were not merely stale but unreproducible, and are corrected with their
  method stated.** `Project.md` claimed `commonTest` holds **1729** `@Test` annotations; the
  source has **1389** (line-leading `@Test` across `**/src/commonTest/**`), and 1729 cannot
  be a `commonTest` count at all — every `commonTest` body runs on the JVM host, and the whole
  JVM host is 1588, so the figure is capped below its own claim. Its `:shared-ui` "42 shell
  scenarios" is now **58**, counted the way the sentence always said: the `@Test` entry points
  in `TaskTrackerAppJvmTest` / `TaskTrackerAppIosTest` / `TaskTrackerAppAndroidHostTest`.
  `AGENTS.md`'s `:shared-ui` line moved with it (79 JVM / 71 iOS), and its "eight" — which its
  own table contradicted with "nine" — is now nine.

  **The consequence, stated rather than columnised.** Desktop is still not a fourth test host
  — 4.8 settled that — but the shell's *width* (5.1) is a dimension the JVM host drives
  through `ShellScenarios.setUpShell(width = …)`, so the Desktop row in both tables now says
  so in a clause rather than growing a column. Every scene section 5 added is a `commonTest`
  body with three entry points, which is why all three totals move together, unlike the
  JVM-only tests 4.6 and 4.8 added.

  No source changed, so that run *is* the validation: `jvmTest`, `iosSimulatorArm64Test`,
  `verifyArchitectureGraph`, `verifyModuleImports` and `:androidApp:compileDebugKotlin` all
  behaved exactly as the tables now say.

### 5.0 Implementation order (desktop layout)

1. **P0**: 5.1 → 5.2 → 5.3 — the class, the component, the branch. Nothing changes on a
   phone until 5.3, and 5.3 is where the regression risk is.
2. **P1**: 5.4 → 5.5 — the two things a wide window actually buys: a second pane and a
   modal that does not arrive from off-screen.
3. **P2/P1**: 5.6 → 5.7 — the window a human can resize, then the measurement.

### 5.0 Notes on fit

- **No `commonMain` screen changes.** 5.1–5.5 change the shell and the design system, both
  of which are already shared by all three shells. No feature module, no entity, no port,
  no architecture-table row.
- **`Compact` is the unchanged path.** Every branch added here leaves today's behaviour as
  the fallback, and the fallback is what the three existing bar-geometry scenarios pin. A
  change that alters a phone must turn one of them red.
- **The threshold is a decision, not a constant.** It is pinned by an exact-value test for
  the reason `AGENTS.md` gives about numbers a test already holds: moving it should be a
  deliberate edit that a reader can see in a diff.

# PLAN — Admin Member & Project Management (KMP client side)

Repo: `/Users/amirreza/Desktop/SampleProjects/TaskTracker/TaskTrackerKMP`
Story: an admin-only section in **Settings** with **Manage members** and **Manage
projects**. Members: create (email/password/role/position), list with Active/Deleted
segments, delete (soft-delete, confirm popup), reactivate. Projects: Active/Archived
segments, selecting one opens the edit-project page. Detail opens as a **new screen on
mobile** and a **split screen on desktop**. This file is the repo-scoped breakdown;
the server half lives in `../TaskTrackerServer/docs/PLAN.md`, as its `T8.x` tasks —
the `SRV-x` series this plan once cited was dropped when the two halves were reconciled.

> **Not in the repo (historically).** This plan was authored in plan mode, which then
> restricted edits to `~/.opencode/plan`; it has since landed here. Each *remaining*
> task becomes one `specs/<plan-id>-<slug>.md` (per `specs/README.md`) + one branch +
> one PR, exactly as `AGENTS.md` requires. The first slice shipped without those specs.

## Status (as of 2026-10-17)

This section was reviewed against the code on `main`, not against its own task marks. As of
`CLI-13` the first slice is shipped and green, and **every** acceptance criterion in it is met —
including the two that were not when this section was written, and the test gap that let them go
unnoticed for as long as they did.

- **Landed:** `CLI-2`–`CLI-14`. Server `T8.9` (`GET /v1/admin/projects/{id}`) was mounted and
  verified first, per the dual-engine ordering rule, and `CLI-12` was written against it.
- **The two criteria that were not met** — `CLI-9`'s editor and `CLI-10`'s wide arrangements —
  are closed by `CLI-12` and `CLI-13`. The projects pane draws the editor; the members pane draws
  the add-member form, because there is no member detail to draw (**D6**) — and since `CLI-15` it
  draws it for as long as the section is open rather than behind the list's `+` (**D8**).
- **The test gap that hid them** — the shell scenarios bound no admin port, so they asserted
  `AdminPanes`' placeholder tags and the real destinations were never composed by a test — is
  closed by `CLI-14`. `AdminPortFakes` binds both ports, and three scenarios assert which pane
  the real screen landed in, by geometry rather than by presence.
- **Shipped differently than written:** `CLI-1` reads the access token's `role` claim
  rather than declaring a field on the auth token envelope; its section is rewritten
  below. `CLI-4` ships `feature-teams-domain/UserStatus`, not the `MemberStatus` the
  section named. `CLI-12` ships a *model* beside the existing stateless editor rather than the
  new screen the section named (**D7**), and `CLI-13` keeps the members section a list + form
  pair rather than the list-detail pair the section named.

## Frozen decisions (agreed with the user)

| # | Decision | Consequence |
| --- | --- | --- |
| D1 | Admin gate = the access token's `role` claim, read client-side. | `CLI-1` reads the token's `role` claim into `UserIdentity.role`, failing closed to `Member`. **Revised:** it does **not** add a field to the auth token envelope — the server's `T8.7` records why sending one would break installed clients. |
| D2 | Manage Projects lists **all** projects server-wide. | New admin projects network port; the local Room/outbox path is **not** used for the admin list. |
| D3 | Deleted member = status value **`deleted`**. | Client maps a fresh `feature-teams-domain/UserStatus` (`active`/`inactive`/`deleted`); the settings `UserStatus` (Active/Away/Busy/BeRightBack) is a different enum in a different package. |
| D4 | ≤1h access-token window after delete. | No client change beyond handling the eventual 401. |
| D5 | The admin project editor is **port-backed**. | Seeds from `AdminProjectsPort.project(id)`, saves through `updateProject(...)`, so server `GET /v1/admin/projects/{id}` (`T8.9`) must be mounted. The local `ProjectEditorScreen` **cannot** serve it: a project the admin is not a member of is not in their Room store, and the sync push refuses a non-member. |
| D6 | **No member detail screen.** | The members section is a list with inline delete/reactivate plus a create form. The `memberID` threaded through `AppRoute.AdminMembers`, `adminMembersStack` and `AdminMembersDestination` was vestigial and left in `CLI-13`; **gained in `CLI-13`:** with no detail to draw, the wide arrangement's second pane is the create form itself, so the section is a list + form pair rather than a list-detail pair. |
| D7 | The admin project editor is a **model beside the shared stateless screen**, not a new screen. | `AdminProjectEditorModel` extends the same `ProjectModel` base as `ProjectEditorModel` and renders the same `ProjectEditorScreen`; the two differ only in the seam — the admin port versus the local store (**D5**). It offers no archive control, matching the existing form, and it cannot borrow `ProjectEditorModel`: that one writes through the sync path, which refuses a project the writer is not a member of. |
| D8 | The wide members pane holds the form **whenever the section is open**, not behind a trigger. | Revised the `CLI-13` arrangement on the user's instruction (**`CLI-15`**): the pane *is* the form, so the list's header `+` is not drawn in that arrangement and the pane's form draws no Cancel — a pane that is never dismissed has nothing to cancel. The phone is untouched: the `+` opens the overlay and Cancel closes it. The pane is keyed on `MemberAdminModel.completedCreates`, because a form that is never torn down would otherwise keep the account it just made. |

## Architecture constraints this plan must respect (verified)

- `build-logic/convention/src/main/kotlin/tasktracker.architecture.gradle.kts`:
  - `:packages:feature-teams-data` has **no** `:packages:network` edge (line 182) — must be added for the member API.
  - `:packages:feature-teams-domain` depends only on `:packages:core`.
  - `:packages:feature-projects-data` **already** has `:packages:network`.
  - `:packages:feature-projects-presentation` **already** depends on `:packages:feature-teams-domain`.
  - `:packages:feature-settings-presentation` does **not** depend on `feature-teams-domain` — so the admin gate is threaded as a plain `isAdmin: Boolean`, avoiding a new edge.
- Presentation may **not** depend on a data module. New screens talk to **ports** in the domain module; `:composition-root` binds the data implementations; `:shared-ui` gains the presentation-module edge.
- `NavigationHost.kt` and `:shared-ui` are **read and edited deliberately, never scripted** — the tree is large and a scripted replace has bitten before. *Fact corrected 2026-10-17:* the older wording here said most of the module is **untracked**; it is not — `git ls-files` lists the module and `git status --untracked-files=all shared-ui` is clean, so git can recover an edit. **Both** files that still carried the untracked claim — `shared-ui/AGENTS.md` and the root `AGENTS.md` — were corrected in `CLI-13`.
- `NavigationHost.kt`’s route `when` is exhaustive: a new `AppRoute` is a compile error until handled.
- The only split-screen precedent is `shared-ui/.../SprintPanes.kt` (`Expanded` widths, Sprints-only); desktop-vs-mobile is width-driven via `LocalShellLayoutClass`, never platform branching.
- `JSONCoding.ignoreUnknownKeys` is **off** — added server keys must be declared in Kotlin with a default first.

## Task order

| ID | Title | Depends on | ACK | State |
| --- | --- | --- | --- | --- |
| CLI-1 | Read `role` from the access token's claim into identity | — | yes (wire) | **done** — as the claim, not an envelope field |
| CLI-2 | Admin section + entry rows in Settings, gated on role | CLI-1 | yes (2+ modules) | **done** |
| CLI-3 | Admin `AppRoute`s + `NavigationHost` dispatch + desktop two-pane scaffolding | CLI-2 | yes (shared-ui/routing) | **done** |
| CLI-4 | `feature-teams-domain`: member-admin port + role/status vocabulary | — | yes (domain API) | **done** — ships `UserStatus`, not `MemberStatus` |
| CLI-5 | `feature-teams-data`: HTTP member-admin remote + network edge | CLI-4 | yes (graph) | **done** |
| CLI-6 | New `:packages:feature-teams-presentation`: members screens | CLI-3, CLI-5 | yes (new module) | **done** |
| CLI-7 | `feature-projects-domain`: admin-projects port | — | yes (domain API) | **done** |
| CLI-8 | `feature-projects-data`: HTTP admin-projects remote | CLI-7 | yes (data API) | **done** |
| CLI-9 | `feature-projects-presentation`: Manage-projects screen (segments) | CLI-3, CLI-8 | yes | **met** — the list and the editor both landed (`CLI-12`); the arrangement is `CLI-13` |
| CLI-10 | Desktop split-screen vs mobile page wiring for both admin areas | CLI-6, CLI-9 | yes | **done** — projects: list + editor panes; members: list + the add-member form (`CLI-13`) |
| CLI-11 | Tests, `test-baseline.json` regen, `scripts/check.sh` | all | — | **done** — the admin scenarios bind their ports now (`CLI-14`) |
| CLI-12 | Wire the admin project editor through `AdminProjectsPort` | CLI-9, server `T8.9` | yes (wire) | **done** — a model beside the shared stateless screen |
| CLI-13 | Fix both admin pane arrangements; drop the vestigial `memberID` | CLI-6, CLI-9 | yes (shared-ui/routing) | **done** — the form is the members section's second pane |
| CLI-14 | Bind the admin ports in host scenarios so the real destinations run | CLI-12, CLI-13 | yes (shared-ui) | **done** — folded into `CLI-13` |
| CLI-15 | The wide members pane holds the form from the moment the section opens | CLI-13, CLI-14 | yes (2 modules) | **done** — the pane is the form; the `+` and the pane's Cancel are gone |

---

## CLI-1 — Read `role` from the access token's claim into identity — DONE, rewritten

**As originally written this task was wrong.** It specified a new `@SerialName("role")`
field on the auth token *envelope*, which the server's `T8.7` records as the one thing
that must not ship: the client leaves `ignoreUnknownKeys` off, so a key the server adds
and the client has not declared is a decoding failure on every installed build, exactly
as `token_type`/`expires_in` were in `T7.1`. There is no client-first ordering to
preserve here, because nothing needs to be sent.

**As shipped**, the role was already on the access token: the server puts a `role` claim
on the access token *and* the refresh token, and the client derives identity from the
token rather than from the envelope's `user_*` fields. Reading that claim is a pure
client change.

**Landed**
- `packages/feature-auth-domain/src/commonMain/kotlin/com/tasktracker/featureauthdomain/AuthSession.kt` — `UserRole { Admin, Member }` with `wire` + `fromWire`, and `UserIdentity.role: UserRole = UserRole.Member`.
- `packages/feature-auth-data/src/commonMain/kotlin/com/tasktracker/featureauthdata/AuthRepository.kt` — sets `role = UserRole.fromWire(claims.role)`.

**Acceptance / tests**
- An absent, unknown or differently-cased `role` claim resolves to `Member` rather than
  throwing — the fail-closed rule, so a role a future server adds cannot unlock a screen
  this build has not shipped.
- A claim of `admin` yields `UserRole.Admin`.
- `AuthTokenResponse` gains **no** field, so the server's five-key envelope assertion in
  `cmd/server/contract_test.go` stays true.

## CLI-2 — Admin section + entry rows in Settings

**Files**
- `packages/feature-settings-presentation/.../SettingsScreen.kt` — add an **Admin** `SettingsSection` (reusing `SettingsActionRow`) with "Manage members" and "Manage projects"; render it only when `isAdmin`.
- `packages/feature-settings-presentation/.../SettingsEnvironment.kt` (or the screen signature) — thread `isAdmin: Boolean` through; the shell supplies it from `UserIdentity.role` (no `feature-teams-domain` edge added).
- `shared-ui/.../NavigationHost.kt` (`SettingsDestination`, ~L1753) — pass `isAdmin` computed from the signed-in identity.
- `packages/feature-settings-presentation/src/commonTest/.../SettingsScenarios.kt` — structural tests.
- Any new row that becomes a public `@Composable` gets its **own file** (splitting rule).

**Acceptance / tests**
- Member: the Admin section is absent from the tree. Admin: both rows are present.
- Tapping a row invokes the injected callback with the right destination (structural).
- Mutation-check: make the gate unconditional → the "hidden for member" test goes red.

## CLI-3 — Admin routes + navigation dispatch + desktop two-pane scaffolding

**Files**
- `packages/routing/src/commonMain/kotlin/com/tasktracker/routing/Routes.kt` — new `AppRoute` cases, e.g. `AdminMembers(memberId: UserID? = null)` and `AdminProjects(projectId: ProjectID? = null)` (parameterized so a selection can be carried on `Expanded` and pushed on `Compact`).
- `shared-ui/.../NavigationHost.kt` — handle both routes in the exhaustive `when`; `Compact` → push detail screens; `Expanded` → two-pane.
- **NEW** `shared-ui/.../AdminPanes.kt` — two-pane layout modelled on `SprintPanes.kt` (list pane + divider + detail pane), reusing its divider.
- `packages/routing/src/commonTest/...` — route/path tests; `shared-ui` host-split tests.

**Acceptance / tests**
- `Compact` + a selected id → a pushed detail route; back pops to the list.
- `Expanded` → list and detail coexisting; selecting changes the detail pane without a push.
- Mutation-check: force one layout branch → the opposite-width test goes red.

## CLI-4 — `feature-teams-domain`: member-admin port + vocabulary — DONE

**Landed.** Naming corrected: the status type is `UserStatus`, not the `MemberStatus`
this section named — and it is a *second* `UserStatus`, distinct from the settings enum
of that name in `feature-settings-domain`.
- `packages/feature-teams-domain/.../MemberAdminPort.kt` — `members(status: UserStatus)`, `createMember(email, password, role, position)`, `softDeleteMember(id)`, `reactivateMember(id): User`.
- `packages/feature-teams-domain/.../MemberRole.kt` — `Admin`/`Member` with `wireValue`.
- `packages/feature-teams-domain/.../UserStatus.kt` — `active`/`inactive`/`deleted` with `wireValue` and a total `fromWireValue` fallback.
- `packages/feature-teams-domain/src/commonTest/.../MemberVocabularyTest.kt` — wire-value round-trips.

**Acceptance / tests**
- Every wire value round-trips; an unknown status resolves to a safe default rather than throwing.
- Only `active` and `deleted` name a bucket the server serves. `inactive` is a member who
  can still sign in and be managed, so it is *not* withheld from the live list.
- Extends the read-only `TeamPort` seam its KDoc already names as "the backend member API".

## CLI-5 — `feature-teams-data`: HTTP member-admin remote

**Files**
- `build-logic/convention/src/main/kotlin/tasktracker.architecture.gradle.kts` — add `":packages:network"` to `:packages:feature-teams-data` (update the comment at line ~179 that currently says it does no network work).
- `packages/feature-teams-data/.../HttpMemberAdminRemote.kt` (**new**) — per-call DTOs with `@SerialName` matching the server keys (`email`, `password`, `display_name`, `position`, `role`, `status`, `id`, `created_at`), `Endpoint` classes, modelled on `HttpAuthRemote.kt`.
- `packages/feature-teams-data/.../MemberAdminRepository.kt` (**new**) — implements `MemberAdminPort`.
- `packages/feature-teams-data/src/commonTest/...` — remote tests (decode + request shape).

**Acceptance / tests**
- Create/list/delete/reactivate hit the right method+path with auth required; the response decodes into `User`/`MemberRole`/`MemberStatus`.
- Mutation-check: break the `@SerialName` case → the decode test goes red.

## CLI-6 — New `:packages:feature-teams-presentation`: members screens

**Files**
- New module `packages/feature-teams-presentation/` (build file) — edges: core, domain, design-system, routing, di, `feature-teams-domain`.
- `.../MembersAdminScreen.kt` — `TTGlassSegmentedPicker` (Active/Deleted), list, `+` in the top bar; state in a Compose-state model (the `SettingsModel`/`ProjectModel` pattern, not androidx `ViewModel`).
- `.../MemberAdminModel.kt`, `.../MemberRow.kt` (trailing trash `ttGlassButton`), `.../AddMemberDialog.kt` (email, password, role, position dropdown fed by `UserPosition.all`), delete uses `ConfirmPopup` with a destructive tint.
- `build-logic/.../tasktracker.architecture.gradle.kts` — `packageOwnersTable` + `allowedLibraryTable` + `shared-ui` edge; `:composition-root` binding.
- `.../src/commonTest/...` — model tests with a fake `MemberAdminPort`; host tests.

**Acceptance / tests**
- Default segment is Active; switching to Deleted lists deleted members and the row offers **reactivate** instead of delete.
- Creating a member appends a row (fake port records the call).
- Delete shows `ConfirmPopup`; cancel ⇒ no call; confirm ⇒ `softDeleteMember` called.
- Mutation-check: default the segment to Deleted → the "defaults to Active" test goes red.

## CLI-7 — `feature-projects-domain`: admin-projects port

**Files**
- `packages/feature-projects-domain/.../AdminProjectsPort.kt` (**new**) — `projects(status: ProjectAdminStatus)` (Active/Archived), `project(id)`, `updateProject(patch)`.
- `packages/feature-projects-domain/src/commonTest/...` — port contract tests.

## CLI-8 — `feature-projects-data`: HTTP admin-projects remote

**Files**
- `packages/feature-projects-data/.../HttpAdminProjectsRemote.kt` (**new**) — DTOs per the server decision (recommend: reuse the sync project shape: camelCase, ISO-8601 via the existing timestamp serializer, `isArchived`, `version`).
- `packages/feature-projects-data/.../AdminProjectsRepository.kt` (**new**) — implements `AdminProjectsPort`.
- `packages/feature-projects-data/src/commonTest/...` — decode + request tests.

**Acceptance / tests**
- Active vs Archived selection maps to `?status=`; the edit patch sends only changed fields + `version`.
- Mutation-check: swap the `status` query value → the segment test goes red.

## CLI-9 — `feature-projects-presentation`: Manage-projects screen — MET

**Landed:** the list and the editor. `packages/feature-projects-presentation/.../AdminProjectsScreen.kt`
renders a `TTGlassSegmentedPicker` (Active/Archived) over the port's `projects(status)`
and reports a selection upward through `onProjectSelected`.

**The selection used to lead nowhere.** The instruction this task shipped with ("routes to
the existing `ProjectEditorScreen`") was wrong as a plan, not merely unfinished: that
screen writes through the local Room/sync path, and a project the admin is not a member of
is absent from their store and refused by the sync push. Per **D5** the editor had to be
port-backed, and `CLI-12` landed it as `AdminProjectEditorModel` beside that same stateless
screen. What remains of this section is the arrangement, not the screen: `CLI-13` and
`CLI-14`.

**Acceptance / tests**
- Archived projects appear only under Archived.
- Mutation-check: hard-code the Active filter → the archived-selection test goes red.
- Selecting a project opens an editor seeded from `project(id)` and saving calls
  `updateProject(...)` — landed in `CLI-12`, and asserted with a bound port in `CLI-14`
  (`aSelectedAdminProjectOpensTheEditorBesideTheList`).

## CLI-10 — Desktop split screen vs mobile page — MET

**What landed:** the routes and the pane scaffolding (`AdminPanes.kt`, the `Expanded` `when`
arms), and, in `CLI-12`/`CLI-13`, the arrangements themselves. The two faults this section was
written for, and their fixes:

1. **The projects detail pane drew the list.** `NavigationHost.kt` hands `pushedPane` to the
   projects pane, and the pushed route is `AdminProjects(projectID)` — whose destination ignored
   that id and rendered the list. `CLI-12` made the destination read the id, so the pane now
   draws the editor; no change was needed in `NavigationHost.kt` itself.
2. **Binding the members port collapsed the two-pane**, while the placeholder path drew
   `AdminTwoPanes` around a list that had nothing to select. Both are gone with **D6**: the
   members section has no detail, so its wide arrangement is a list plus the create form in the
   second pane, and the placeholder path is one pane like the real one.

**Acceptance / tests**
- `Expanded`: the Settings entry shows list + detail side by side, with the detail pane showing a
  *detail*, never the list. `Compact`: the entry pushes a full screen. Both are asserted with the
  ports bound (`CLI-14`).
- Mutation-check: make the projects destination draw the list for a selected id → the editor
  scenario goes red; drop `isWide` from the members arm → the members pane scenario goes red.

## CLI-11 — Tests, baseline, verification — DONE

**Files**
- `test-baseline.json` — regenerate with `scripts/test-baseline.py` in the same change as the new tests (never hand-edit; `scripts/check-baseline.sh` enforces).
- Anything pixel-reading (contrast of the trash/`+` buttons over glass, the segmented picker) → follow the render-vs-structural table + the `test-reviewer` subagent + mutation ritual.

**Acceptance**
- `scripts/check.sh` green; the documented pre-existing host failures (`docs/TEST-HOSTS.md`) unchanged.

**What was missing, and is now there:** the suite was green because the admin scenarios asserted
`AdminPanes`' placeholders rather than the real screens. `CLI-14` binds both admin ports and
asserts the arrangements, which is what makes the three mutations under `CLI-13` red instead of
invisible. Nothing here was pixel-reading — the claims are pane geometry and node counts, which a
source-level assertion can see — so no `test-reviewer` pass was required.

---

## CLI-12 — Wire the admin project editor through `AdminProjectsPort` — DONE

**Depends on** server `T8.9` (`GET /v1/admin/projects/{id}`), which landed and was verified
there first, per the dual-engine ordering rule.

**Corrected during implementation.** This task named a new screen,
`AdminProjectEditor.kt`. That was wrong. `ProjectEditorScreen` is already stateless — it
takes the draft and the callbacks and holds no port — so a second screen would be a second
copy of the same fields, which is the drift that screen's own design note exists to
prevent. The missing unit was a *model*, and the screen is reused.

**Landed**
- `packages/feature-projects-presentation/.../AdminProjectEditorModel.kt` (**new**) —
  extends the same `ProjectModel` base as `ProjectEditorModel`, seeds from
  `AdminProjectsPort.project(id)`, and saves `project.copy(name, details)` against the
  version the seed carried. A `null` seed sets `isMissing` rather than an error, because
  the port's KDoc requires "the row is gone" and "the read failed" to stay different
  answers — a screen that conflated them would offer a retry for a deleted project, or
  worse, hide a 500 behind "deleted".
- `shared-ui/.../AdminProjectsDestination.kt` — a non-null `projectID` now renders
  `ProjectEditorScreen` driven by that model; the id was previously ignored, so selecting a
  row drew the list again. A null id renders the list, and loading and missing render their
  own notice.
- `packages/design-system/.../values/strings.xml` — one string, `admin_projects_missing`.
  A missing row needs wording and `AppError.NotFound` takes its text from the caller.
- `packages/feature-projects-presentation/src/commonTest/.../AdminProjectEditorModelTest.kt`
  — the seed read, the missing row, the untouched fields travelling through the save, the
  version the second save carries, and a refused save reporting rather than claiming
  success.

**Acceptance / tests**
- Selecting a project reads `project(id)` and renders that project's fields; saving calls
  `updateProject(project, version)` with the version the seed carried, so the optimistic
  lock is carried rather than invented.
- A `null` seed offers no save and says the project is not on the server.
- Mutation-check, both caught: send `""` instead of the seed's version → the two version
  tests go red; drop the `isMissing` assignment → the missing test goes red.
- The destination's own `projectID` branch is asserted by **CLI-14**, which is what binds
  an admin port into a shell scenario; until a port is bound the shell renders the
  `AdminPanes` placeholder and the destination is never composed.

## CLI-13 — Fix both admin pane arrangements; drop the vestigial `memberID` — DONE

**Corrected during implementation, twice.** The plan expected the *projects* arm to be the
repair and the *members* arm to stay a two-pane pair. Reading the code said otherwise, and the
user's answer settled the members half:

- The projects arm needed **no change at all** once `CLI-12` landed. It hands its detail pane
  `pushedPane`, whose `AppRoute.AdminProjects` branch renders `AdminProjectsDestination` — so the
  "the pane draws the list twice" defect *was* the destination ignoring its `projectID`, and
  reading the id is the whole fix.
- The members arm is not a list-detail pair. There is no member detail screen (**D6**), so its
  second pane has nothing of its own to hold. The user's answer: it holds the **add-member form**
  — a popup on a phone, the right pane on a desktop.

**Landed**
- `packages/feature-teams-presentation/.../AddMemberForm.kt` (**new**) — the panel: four fields,
  Cancel/Create, and the private helpers, tags and consts that are its contract.
  `AddMemberDialog.kt` keeps the scrim and centres that panel, so the phone's arrangement is
  unchanged; the scrim is the only thing that made it a popup.
- `shared-ui/.../AdminMembersDestination.kt` — one model, two arrangements, chosen by a new
  `isWide` parameter. Wide draws `AdminTwoPanes(list, form-or-placeholder)` and tells the list
  `isAddFormOpen = false`, because the list draws its own overlay from that flag and would
  otherwise compose a second form. Nothing else about the wiring is duplicated, which is why this
  is a parameter rather than a second composable holding a second copy of it. **Revised in
  `CLI-15`:** the pane no longer waits for the `+` — the wide arrangement's detail pane is the form
  itself (**D8**).
- `packages/routing/.../Routes.kt` + `Router.kt` — `AppRoute.AdminMembers` is a `data object` and
  `adminMembersStack()` takes no id. The exhaustive `when` turned every stale call site into a
  compile error, as the plan said it would.
- `shared-ui/.../AdminPanes.kt` — `AdminMembersPlaceholder()` loses its id, and
  `AdminMemberDetailTag` is gone: its only producer was the member detail **D6** removed.
- `packages/feature-projects-presentation/.../AdminProjectsScreen.kt` — one new tag,
  `AdminProjectsScreenTag`. Beyond the approved list, and required by `CLI-14`: the members screen
  was tagged and the projects screen was not, so a scenario with a bound port had no anchor for
  "the real section landed in this pane".

**Acceptance / tests**
- Both arrangements of the members form, asserted off geometry: on a wide window the form starts
  where the list ends, on a phone it overlaps the list that stays composed under it.
- The admin project editor in the pane beside the list — `CLI-12`'s own acceptance criterion,
  which could not be asserted until a port was bound. That is why `CLI-14` is folded into this PR.
- Three mutations, all caught: make the projects destination draw the list for a non-null id →
  `aSelectedAdminProjectOpensTheEditorBesideTheList` goes red; drop `isWide` from the members arm →
  `aWideShellDrawsTheMembersListAndTheAddFormSideBySide` goes red; let the list draw its own
  overlay in the wide arrangement → the same scenario fails with *"Expected exactly '1' node but
  found '2' nodes that satisfy: (TestTag = 'adminMembers.addForm')"*.
- Two routing tests that asserted a member *selection* replaced rather than pushed are gone with
  the selection. `RoutingTest` now holds that re-entering the section is a no-op, which is what a
  re-introduced member route would break first.

## CLI-14 — Bind the admin ports in host scenarios so the real destinations run — DONE, folded into CLI-13

**Why it was its own task.** Every admin shell scenario ran with `memberAdminPort == null` and
`adminProjectsPort == null`, so it asserted `AdminPanes`' placeholder tags. The real destinations
were never composed by the shell, which is exactly how `CLI-9` and `CLI-10` stayed green while
unmet. Once `CLI-13` had an arrangement worth asserting, keeping the arrangement and its proof in
two PRs would have meant merging one that nothing could verify.

**Landed**
- `shared-ui/src/commonTest/.../AdminPortFakes.kt` (**new**) — `FakeMemberAdminPort`,
  `FakeAdminProjectsPort`, and the fixtures the scenarios seed. Deliberately not recording doubles:
  what these scenarios assert is *where* a screen lands, and the traffic claims belong to the
  feature modules' own model tests, which already have one each.
- `TestDoubles.kt` + `ShellScenarios.kt` — `FakeResolver.forSession` and `setUpShell` take the two
  ports and bind them **only when a scenario asks**, so the placeholder path that three scenarios
  still assert stays reachable.
- Three scenarios, all three hosts: the wide members arrangement, the phone's overlay, and the
  admin editor beside the list.

**Acceptance / tests**
- The real screens are asserted with the ports bound, by tag and by pane geometry — not by
  placeholder tag.
- Mutation-check: the mutations listed under `CLI-13` are red *because* these scenarios exist.
  Before this, reverting either arrangement left the suite green.

## CLI-15 — The wide members pane holds the form from the moment the section opens — DONE

**Why.** `CLI-13` made the form the members section's second pane but left it behind the same
trigger the phone uses: the pane drew `AdminDetailPlaceholder` until the list's `+` was tapped, and
took back the placeholder on Cancel. The user's report: the form sat flush against the top of the
pane, the `+` was still there with the form already beside it, and the form should simply be in the
right pane when the members page is open.

**Landed**
- `shared-ui/.../AdminMembersDestination.kt` — the wide `detailPane` draws `AddMemberForm`
  unconditionally, on the list's own top gutter (`ttSpacing.screenGutterLarge`; not centred, so a
  failed create's banner does not move the panel under the cursor typing into it) and keyed on
  `model.completedCreates`. It passes `showsAddAction = !isWide` and `onDismiss = null`, and its
  `onCreate` now uses the `Boolean` `createMember` documents for the caller, so the phone's overlay
  closes on a create that landed instead of sitting open over the row it just added.
- `packages/feature-teams-presentation/.../MembersAdminScreen.kt` — a new
  `showsAddAction: Boolean = true` gates the header's `+`. The back control is not gated with it:
  it is the screen's only way out on the hosts with no system back.
- `packages/feature-teams-presentation/.../AddMemberForm.kt` — `onDismiss` is optional and Cancel is
  drawn only when it is given; Create then takes the width the pair had split.
- `packages/feature-teams-presentation/.../MemberAdminModel.kt` — `completedCreates`, moved only by
  a create that landed. It exists because the pane's form is never torn down: on the phone the
  fields are cleared by the form leaving the composition, and this count is that same clearing
  reached from the other side.

**Acceptance / tests**
- `aWideShellDrawsTheMembersListAndTheAddFormSideBySide` rewritten: the form is in the pane with no
  tap, right of the list, below the pane's top edge, exactly one form, no `MembersAddTag` anywhere,
  and a create that lands leaves the Create button down again.
- Three new scenarios in `MembersAdminScenarios`, on all three hosts: a list with the form beside it
  keeps its back control and offers no `+`; the overlay's form still offers Cancel and dispatches
  it; a form with no dismiss draws no Cancel. The middle one is the positive half of the pair — a
  change that stopped drawing Cancel anywhere would otherwise pass both.
- `onlyAFinishedCreateIsCounted` in `MemberAdminModelTest`: a create that landed counts, a failed one
  does not.
- Mutations, all caught: drop the top gutter → the flush assertion, naming both edges; drop the
  `key` → *"the account was created, so the pane's form should hold nothing and Create should be
  down again"*; `showsAddAction = true` on wide → *"the pane is already drawing the form, so the
  list's Add button opens what is already open"*; drop `completedCreates++` →
  `onlyAFinishedCreateIsCounted`.

---

## Open assumptions (correct me before ACK)

1. Role vocabulary is **deliberately duplicated**: `UserRole` in `feature-auth-domain` (identity) and `MemberRole` in `feature-teams-domain` (member admin), following the existing duplicated-`preferredGreeting` precedent — rather than adding a `feature-settings-presentation → feature-auth/teams-domain` edge.
2. Members UI lives in a **new `feature-teams-presentation`** module; projects UI stays in the existing `feature-projects-presentation`, so no new cross-presentation edge appears. **Revised twice:** the admin project editor is *not* a new port-backed screen — it is `AdminProjectEditorModel` beside the existing stateless `ProjectEditorScreen`, which `CLI-12` shipped and the plan's own `CLI-12` text had wrong (**D7**). The "no new edge" half survives either way.
3. Position choices for the add-member form come from the client's `UserPosition.all`; the server stores free text (matches server assumption 1).
4. The admin members/projects screens are **online-only** (direct network, no Room/outbox), unlike the offline-first board/backlog surfaces. This is also *why* D5 holds: there is no local copy of a project the admin is not a member of, so the editor cannot be the local one.

The server half of this plan is `../TaskTrackerServer/docs/PLAN.md`, Milestone 8
(`T8.1`–`T8.9`). All nine are done: `T8.9` (`GET /v1/admin/projects/{id}`) landed first, as the
dual-engine ordering rule requires, and `CLI-12` was written against it.

D6 and D7 gained their second halves in `CLI-13`: D6's "no member detail" is what made the
members section a list + form rather than a list-detail pair (so the form, not a member, is the
second pane), and D7's "a model beside the screen" is why `CLI-12` changed no screen at all.

---

## Test harness cleanup

The client test-harness cleanup has its own plan: [`docs/TEST-HARNESS-PLAN.md`](TEST-HARNESS-PLAN.md).
It covers the honest gate, the named known-failure list, the three-host matrix, and CI.
