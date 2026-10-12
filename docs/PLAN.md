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

- [ ] **4.1 Choose the shape: module vs source set** · P0 · Decision — A new
  `:desktopApp` Gradle module (Android-parallel: a thin shell above `:composition-root`
  and `:shared-ui`, like `:androidApp`) **or** a `jvmMain` source set inside
  `:shared-ui` (iOS-parallel: a `main()` entry point next to `iosMain`/`androidMain`,
  like `MainViewController.kt`). The module shape keeps Desktop's run task, any
  manifest-ish concerns, and its entry point out of `shared-ui`; the source-set shape
  keeps one fewer module and mirrors how iOS already owns its entry point. The two shapes
  diverge on whether `verifyArchitectureGraph` needs a new `:desktopApp` row and whether
  `shared-ui/build.gradle.kts` gains a `jvm` framework/binaries block. Shot-cut: pick the
  shape before any code is written, because the other files read differently depending on
  it.
- [ ] **4.2 Declare the Desktop target and its source sets** · P0 ·
  `build-logic/convention/src/main/kotlin/tasktracker.kmp.library.gradle.kts` +
  `gradle/libs.versions.toml` — Add the Desktop target to the convention plugin (or to a
  new `:desktopApp` build file if 4.1 picks the module shape). Compose Desktop is already
  on the classpath for `jvmTest` (`compose.desktop.currentOs`); the Desktop target needs
  it on the app classpath instead, plus a `main` entry. Confirm the Compose Multiplatform
  1.12.1 desktop coordinates resolve for the chosen target and that `jvmToolchain(25)`
  still applies. This is the task that makes `./gradlew :desktopApp:compileJvmMain` (or
  the source-set equivalent) exist.
- [ ] **4.3 Add the Desktop entry point** · P0 ·
  `shared-ui/src/jvmMain/.../DesktopMain.kt` (or `:desktopApp`'s own `main`) — A
  `main()` that builds the graph (`AppGraph.start()`), opens a Compose `Window`, and
  renders `TaskTrackerApp` inside it. Model it on `MainViewController.kt`: the graph is
  owned once in process scope, the entry point takes no arguments from the platform side,
  and the file contributes only the window and the graph — no Desktop-specific navigation
  or state. This is the file that makes Desktop render the existing screen tree; nothing
  in `commonMain` changes for it.
- [ ] **4.4 Wire the architecture tables for the chosen shape** · P0 ·
  `build-logic/convention/src/main/kotlin/tasktracker.architecture.gradle.kts` — If 4.1
  picks the module shape, add `:desktopApp` to `packageOwnersTable` and
  `allowedLibraryTable` (same edges as `:androidApp`: `:composition-root`, `:shared-ui`,
  `:packages:di`). If 4.1 picks the source-set shape, confirm no table change is needed
  and record that confirmation here. Either way, run
  `verifyArchitectureGraph` + `verifyModuleImports` after the edit and before anything
  else, because the tables are the gate.
- [ ] **4.5 Add a `run` task for Desktop** · P1 ·
  The Desktop build file (module or source-set shape) — A Gradle task that launches the
  Desktop app: depends on the Desktop compile/link task and runs the `main` class with the
  right classpath. This is the Desktop equivalent of `:androidApp:installDebug` and of
  Xcode's build-and-run; without it Desktop exists in Gradle but not as something a
  developer can launch. Name it so it is discoverable (`runDesktop` or per-module
  `:desktopApp:run`) and so it does not collide with the existing `jvmTest` path.
- [ ] **4.6 Decide Desktop persistence and implement any new actuals** · P1 ·
  `packages/keychain` + `packages/user-defaults`, `jvmMain` actuals — The JVM target's
  keychain and user-defaults actuals are in-memory today (test-host stubs). A Desktop app
  that signs in and restarts loses its session unless Desktop gets real persistence for at
  least one of them. Decide which seams Desktop gets real backends for (file-based
  preferences, a JVM keychain backend, both, or neither and Desktop inherits the stubs),
  and implement the corresponding `jvmMain` actuals. This is also where any new Desktop-
  only seam lives if one turns out to be needed (window icon, tray, anything that does not
  exist on the other two platforms).
- [ ] **4.7 Confirm Compose resources and strings work on Desktop** · P1 ·
  Verification — `:packages:design-system` already owns `compose.resources` and already
  generates `Res`/`stringResource` for all CMP targets including Desktop. Run the Desktop
  target (or the Desktop test host) and confirm the shell renders strings from
  `composeResources` rather than falling back to raw strings, and that no new resource
  home is needed. If a Desktop-launch icon or window asset is wanted, decide its location
  here; if not, record that Desktop reuses the existing compose resources with no new asset
  work.
- [ ] **4.8 Add Desktop to the test-host picture** · P1 ·
  `Project.md` test-host table + any new Desktop test entry points — Document Desktop as a
  test host in the table. The existing `commonTest` scenarios and pixel tests already run
  on a JVM target and Desktop is a JVM target, so the regression is: does Desktop pass the
  same suites the JVM host does? Decide whether Desktop is its own column (with its own
  entry-point class, like `HostedGraphScenariosTest`) or folds into the existing JVM
  column. If Desktop exposes any new `expect`/`actual` that the JVM test host did not cover,
  add the matching test before this task is done.
- [ ] **4.9 Run the decision command on the Desktop target and reconcile** · P1 ·
  Verification — Run the decision command from `Project.md` (or the Desktop equivalent) and
  confirm Desktop is green at the module level before this section is marked done. If any
  of the pre-existing failures behave differently on Desktop than on the JVM host, note the
  difference here rather than silently folding it into the existing numbers.

---

### 4.0 Implementation order (Desktop)

1. **P0**: 4.1 → 4.2 → 4.3 → 4.4 — shape, target, entry point, tables. Nothing renders
   until the entry point exists and the tables allow it.
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
