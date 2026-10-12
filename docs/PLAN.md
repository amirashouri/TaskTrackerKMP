# Feature Plan

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
- [x] **0.3 Project member management UI** · P0 · `feature-projects-presentation` (project editor sheet) — Wire the project editor to `Project.memberIDs` via a member-picker that reads from the directory in 0.1. Add invite-by-email (queues an outbox row). This is the UI the data model already hints at. *Landed without a directory search: 0.1's `TeamPort` resolves a `UserID` it is given but cannot list or search people, so a name/email search box would have been a mock pretending to be a directory. The sheet now shows selected members (names resolved through `TeamPort`, raw id as honest fallback), offers the signed-in user as a one-tap add, and routes everyone else through invite-by-email — which queues a new `OutboxOperation.Invite` row carrying `{projectID, email}`. The mock directory (`MockProjectMembers`) was deleted with its search UI. *Polish follow-up:* members render as `FlowRow` glass pills with initial discs (the `WorkItemDependencyPills` shape) in a split-out `ProjectMembersSection`; the add-me action moved to the section's label row; previews and the first-launch seed carry members (`ViewPreviewData.previewMemberIDs` — two named plus one unnamed id to keep the honest-fallback path on the page; `ProjectSeed.members` flags the signed-in user, the only person a seed can honestly name).*

---

## 1. Team collaboration

- [x] **1.1 Comments / threaded discussions** · P1 · New `Comment` entity in `domain`; `feature-projects-domain` (port + use cases `AddComment`, `LoadComments`); `feature-projects-data` (Room + outbox); `feature-projects-presentation` (comment thread in the work-item editor) — A `Comment` entity (id, workItemID, authorID, body, createdAt, parentID for replies). Read through the project port so data stays behind the use-case boundary. Threaded via optional `parentID`. Rendered in the WorkItemEditor sheet.
- [x] **1.2 @mentions** · P1 · `domain` (mention parse rule in `WorkItemRules` or a new `MentionRules`) + `feature-projects-domain` — Parse `@username` or `@<userID>` tokens in comment bodies. Each mention becomes a notification row (see 1.3). The domain layer owns the parsing rule so both the editor and the sync sink agree on what a mention is.
- [x] **1.3 Notification inbox** · P1 · New `feature-notifications` (domain/data/presentation) + new `AppTab` or pushed screen — A lightweight inbox: rows for "mentioned in a comment", "task assigned to you", "due today", "status changed on your task". Stored in Room with an `isRead` flag and cleared on sign-out. The "My Tasks" filter and this inbox are the two ways a person learns about their work.
- [x] **1.4 Local notifications / reminders** · P1 · New `expect/actual` platform seam (`NotificationCenter` on iOS, `AlarmManager` on Android) + `feature-notifications-domain` scheduling port — A `LocalNotifier` port that the data layer calls when a notification row is inserted: "this task is due in 10 minutes", "you were mentioned". Uses the existing `PushNotification`/platform-bridge pattern. This is also the mechanism reminders (2.2) use.
- [x] **1.5 Task dependency visualization** · P2 · `feature-projects-presentation` (board card + a new dependency subview in the editor) — `blockedByIDs` already exists but is invisible outside the editor. Render a mini "depends on" / "blocked by" list on the card and in the editor sheet. Does **not** add new persistence — it reuses the existing field.
- [x] **1.6 Project-level "assignable members" filter** · P2 · `feature-projects-presentation` (project picker / board header) — On a project's board, the "My Tasks" toggle and the member list should show only this project's members, read from the directory in 0.1.
- [ ] **1.7 Role-based project permissions** · P3 · `domain/Project` (new `role` field per member) + `ProjectRepositoryPort` + use-case guards — Add `UserRole` (Admin, Member, Viewer, Guest) to project membership. Gate `setArchived`, `deleteProject`, and member management behind Admin. The port is the enforcement point so the UI can't bypass it.
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
