# epics — cross-project work container, screen and derived progress

- **status:** in-progress
- **priority:** P1
- **branch:** `feat/epics` — cut before the first edit; work never starts on `main`
- **blocked by:** none — the server half landed on `TaskTrackerServer` `feat/epics`
  (unmerged); `../TaskTrackerServer/docs/API.md` §Sync is the contract. Client work goes
  model/network → data → UI.
- **context:** Requested 2026-11-24: an Epic groups work items across two or more projects,
  carries title/description/due date/archive state, and derives status and progress from its
  subtasks. The Projects tab gains a Project | Epic segment, and its "+" creates the kind
  the showing segment lists. This is the client half of a cross-repo change; read `../AGENTS.md` §1/§2.
  The app is pre-release, so the wire may change freely and the server's spec is the fixed
  point for key names.

## Scope

- **Targets affected:** commonMain, commonTest, jvmTest, iosTest, androidHostTest
- **Modules affected:** `:packages:core`, `:packages:domain`, `:packages:database`,
  `:packages:sync`, `:packages:feature-projects-domain`, `:packages:feature-projects-data`,
  `:packages:feature-projects-presentation`, `:packages:design-system`, `:packages:routing`,
  `:shared-ui`, `:composition-root`

The change must not introduce a new Gradle module, a new `expect`/`actual`, a new table in
another feature, or a key on `Project`/`Sprint`. No architecture-table edit: Epic lives in
`:packages:domain` and the existing `ProjectRepositoryPort`.

## Files

- `packages/core/src/commonMain/kotlin/com/tasktracker/core/Identifiers.kt` — `EpicID`.
- `packages/domain/src/commonMain/kotlin/com/tasktracker/domain/Entities.kt` — `Epic`,
  `EpicStatus`, the derived-status function, and `WorkItem.epicID`.
- `packages/domain/src/commonTest/.../EpicStatusTest.kt` — the derivation truth table.
- `packages/database/src/commonMain/.../EpicRecord.kt` — `EpicRecord`, `EpicDao`.
- `packages/database/src/commonMain/.../TaskTrackerDatabase.kt` — register `EpicRecord`
  and `EpicDao`, bump the schema version.
- `packages/database/src/commonMain/.../WorkItemRecord.kt` — `epicID` column and index.
- `packages/database/src/commonMain/.../Outbox.kt` — `OutboxEntity.Epic`.
- `packages/database/schemas/...` — regenerated schema JSON.
- `packages/sync/src/commonMain/.../SyncScope.kt` — `Epics("epics")`.
- `packages/sync/src/commonMain/.../InMemorySyncTransport.kt` — entity→scope mapping.
- `packages/feature-projects-domain/.../ProjectRepositoryPort.kt` — epic reads/writes and
  `workItemsForEpic`.
- `packages/feature-projects-domain/.../EpicUseCases.kt` — create, update, archive-only,
  archive-all, link/unlink, with the one-epic and same-project rules.
- `packages/feature-projects-data/.../RecordMapping.kt` — `Epic` ↔ `EpicRecord`, and
  `WorkItem.epicID`.
- `packages/feature-projects-data/.../RoomProjectRepository.kt` — atomic epic+outbox,
  archive-all batch, and `workItemsForEpic`.
- `packages/feature-projects-data/.../LocalDataSyncSink.kt` — apply pulled epics.
- `packages/feature-projects-data/.../ProjectChangeBus.kt` — `ProjectChange.Kind.Epics`.
- `packages/design-system/.../EntityIcons.kt` — task/project/epic glyphs on the 24-unit grid.
- `packages/design-system/.../strings.xml` — epic copy.
- `packages/routing/.../Routes.kt` — `AppRoute.EpicEditor(epicID)`.
- `packages/feature-projects-presentation/.../project/EpicListModel.kt`.
- `.../project/EpicRow.kt`, `EpicEditorModel.kt`, `EpicEditorScreen.kt`,
  `EpicProjectPickerSheet.kt`, `EpicItemPickerSheet.kt`, `EpicArchiveSheet.kt`,
  `EpicProgress.kt`.
- `.../project/ProjectsListModel.kt`, `ProjectsComponents.kt`, `ProjectsListScreen.kt` —
  the segment, the add action, and the epic list.
- `shared-ui/.../ProjectsHost.kt`, `NavigationHost.kt` — route and model wiring.
- `shared-ui/.../ShellPreferences.kt` — the segment choice.
- `shared-ui/src/commonTest/.../ShellScenarios.kt` + jvm/iOS/Robolectric entry points.

## Contract

- [x] `WorkItem.epicID` decodes when absent (default `null`) and survives a local link and
      a pulled change (`RoomProjectRepositoryEpicTest`, `LocalDataSyncSinkEpicTest`).
- [x] The derived status matches the truth table: all terminal → Done; any
      `InProgress`/`InReview` → In progress; any `Todo` → Todo; otherwise Backlog; no
      subtasks → Backlog. `Closed` counts as terminal, `InReview` as in progress, and only
      the coarse `category` is read (`EpicStatusTest`).
- [x] One epic per work item, enforced by the use case and not only by the picker
      (`EpicUseCasesTest`).
- [x] An item can only join an epic that lists the item's project (`EpicUseCasesTest`).
- [x] Archive-only flips the epic and leaves subtasks untouched; archive-all files every
      subtask away and unarchive restores the ones it filed (`EpicArchiveTest`).
- [x] The Projects tab switches Project | Epic with `TTGlassSegmentedPicker`, and the
      selection survives a tab switch through `ShellPreferences`
      (`projectEpicSegmentScenarios`).
- [x] The "+" creates for the active segment — the project form on Projects, the epic
      editor on Epics — with no menu in between (`projectCreateActionsScenarios`).
- [x] The epic card shows the epic glyph, project chips, derived status, `done/total`, and
      due date, and is visually distinct from `ProjectRowView` and `WorkItemCard`
      (`epicRowScenarios`).
- [x] The archive dialog offers both options only when undone subtasks exist, and names the
      count (`epicArchiveDialogScenarios`).
- [x] The progress bar and status are absent in create and present in edit/detail
      (`epicEditorScenarios`).
- [x] Reverting the derivation (e.g. treating `InReview` as Done) turns a named test red.

**KMP Constraint:** no platform APIs in `commonMain`; no new `expect`/`actual` is expected.
New resources land only in `:packages:design-system`.

## Non-goal

- Epic delete, epic search/filter, epic keys, story-point progress, agent brief changes.
- Per-project Epic sections (the segment is top-level; a project board gains no epic lane).
- Notifications for epic activity.
- Changing sprint behaviour or the sprint picker's existing copy.
- A new Gradle module or architecture-table edge.

## Validation

- **Iteration:** `scripts/check.sh quick :packages:domain '<EpicStatusTest>'`,
  `:packages:database`, `:packages:feature-projects-data`,
  `:packages:feature-projects-presentation` as each lands.
- **Before handoff:** `scripts/check.sh full` — this touches rendering (new icons, a card,
  a segmented control), so it needs JVM, iOS and the Robolectric entry points.
- **Counts / Baselines:** done — `test-baseline.json` regenerated from the green full run
  (JVM 2445, iOS 2380, Android host 2016) and `check-baseline.sh` passes.
- **Stays pending:** a device install and the real server round-trip (the mock transport
  cannot prove the wire).

## Handoff

- **Done:** the client half, end to end, on `feat/epics`. `EpicID`; `Epic`/`EpicStatus`/
  `deriveEpicStatus` and `WorkItem.epicID`; `EpicRules`; `EpicRecord`/`EpicDao` and schema 4
  (`epics`, `work_items.epicID`, `work_items.isArchived`); `OutboxEntity.Epic`; the `epics`
  pull scope and transport mapping; the port's epic reads/writes and `workItemsForEpic`;
  `CreateEpic`/`UpdateEpic`/`SetEpicArchived`/`SetWorkItemEpic`; `RoomProjectRepository`'s
  atomic epic+outbox, unlink-on-project-drop, archive-all batch and queue coalescing;
  `LocalDataSyncSink.applyEpic`; `ProjectChange.Kind.Epics`; design-system glyphs and copy;
  `AppRoute.EpicEditor`; `EpicListModel`, `EpicRowView`, `EpicProgress`, the editor, both
  pickers and the archive sheet; the Project | Epic segment in `ShellPreferences`; the
  segment-scoped add action; and the five shell scenarios. `scripts/check.sh full` is green on
  JVM, iOS and Robolectric (0 failures), `test-baseline.json` regenerated from that run
  (2446 / 2381 / 2017), and
  `scripts/check-baseline.sh` passes. The mutation ritual was run: treating `InReview` as
  `Done` turns `EpicStatusTest.inReviewIsInProgress` red, then restored green.
- **Pending:** a device install and the real server round-trip — the mock transport cannot
  prove the wire, and the server half is still unmerged on `TaskTrackerServer` `feat/epics`.
- **Next step:** merge the server branch, run a two-device epic create/link/archive against
  it, then delete this spec and keep the `DECISIONS.md` bullets.
- **Gotchas:**
  - The status is derived and never stored; do not add a Room column or a wire key for it.
  - Progress counts only the subtasks this device can see, so a member of one linked
    project sees a smaller total than a member of both. That is the permission boundary,
    not a bug; copy stays neutral.
  - `WorkItem.isArchived` is a filing choice, not "finished": archive-all sets it, and no
    listing may treat it as terminal.
  - A story's segment state lives in `ShellPreferences`; a `remember` in the destination
    dies on a tab switch.
  - `TTGlassSegmentedPicker` already exists; do not add a second segmented control.
  - The "+" creates for the showing segment, with no dropdown and no chooser sheet; switching
    the segment is the choice.
  - A sheet's content must not add its own `verticalScroll`: `TTBottomSheetScaffold` already
    scrolls the card, and a nested one throws on open. The epic project picker did that and
    crashed on the first project-button tap; `epicProjectPickerSurvivesAnEmptyCatalog` guards
    it, and an empty catalog now reads "No projects yet".
  - `EpicEditorModel` sends `Patch.Unmentioned` for an unchanged due date and a null project
    set for an unchanged one; sending them unconditionally costs the remove-project relink
    sweep on every save.
  - A pushed `EpicEditor` can be the Projects stack's *only* route (the tab root is an empty
    stack), so back pops even at size 1. The generic `size > 1` guard is for tabs with a real
    root route, like Sprints.

## Promote

- **To `docs/DECISIONS.md`:** under "Domain and sync", the derived-epic-status rule and the
  visible-subtask progress rule; under "UI", the top-level segment choice and why it is a
  preference.
