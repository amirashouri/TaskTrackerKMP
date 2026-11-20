# backlog-multi-select — multi-select backlog items to board or archive

- **status:** in-progress
- **priority:** P1
- **branch:** `feat/backlog-multi-select`
- **blocked by:** none
- **context:** Requested 2026-11-20 after the identity-section work: the Backlog page can only
  act on one row at a time, so triaging a long backlog means tapping "Put on board" or "Archive"
  per row. This spec adds selection there; the board's column selection is the companion
  `specs/board-column-multi-select.md` and lands second because it reuses the selection
  primitives created here. Client-only: every item is still moved with its own existing domain
  command, so there is **no wire change** and no ACK under `../AGENTS.md` §1.

## Scope

Selection state, batch commands, and the screen affordances for the **Backlog section** only.
The Kanban and Archived segments of the same screen keep their per-row actions.

- **Targets affected:** commonMain, commonTest
- **Modules affected:** `:packages:feature-projects-domain`,
  `:packages:feature-projects-presentation`, `:packages:design-system` (strings and, if one is
  needed, a selection icon — tokens stay there by rule), `:shared-ui` only if a host callback
  signature changes

## Files

- `packages/feature-projects-domain/.../WorkItemBatches.kt` (new) — `WorkItemBatchOutcome`,
  `WorkItemBatchFailure`, `PutWorkItemsOnBoard`, `ArchiveWorkItems`. The single-item
  `putOnBoard` delegates to the one-item batch so the lane cannot resolve two ways.
- `packages/feature-projects-domain/src/commonTest/.../WorkItemBatchesTest.kt` (new) — order,
  one-lane-per-set, per-item failures, terminal skip.
- `packages/feature-projects-presentation/.../shared/WorkItemSelection.kt` (new) — the ordered
  selection set and its invariants, shared with the board spec. Covered by
  `WorkItemSelectionTest`.
- `.../presentation/backlog/ProjectBacklogModel.kt` — `isSelecting`, `selection`,
  `isBatchRunning`, `startSelecting` / `toggleSelected` / `clearSelection`, and
  `putSelectionOnBoard` / `archiveSelection`.
- `.../presentation/backlog/ProjectBacklogScreen.kt` — select mode, the header's Select/Clear
  control, the action bar, the selection-aware row tap and long-press, and the batch lane
  resolver (`chooseLane`) shared with the single-row path.
- `.../presentation/backlog/WorkItemBacklogRow.kt` — `selectable` semantics (`Role.Checkbox` +
  `selected`), the drawn tick box, and per-row actions hidden while selecting.
- `.../presentation/backlog/PutOnBoardSheet.kt` — now takes a composed `body` sentence, so one
  sheet asks for one task or for `N items`.
- `packages/design-system/.../TabIcons.kt` — new `ttActionIcons.select`; `strings.xml` — Select,
  Clear, "N selected", "Working…", the plural prompt body.
- `shared-ui/.../NavigationHost.kt` — wires the model's selection state and both batch commands
  into the backlog destination. `ProjectsHost.kt` does not host this screen; unchanged.
- Scenario drivers register the eight new scenarios in `ProjectsUiJvmTest`,
  `ProjectsUiAndroidHostTest` and `ProjectsUiIosTest`.

## Contract

- [x] Long-press on a row (touch) or the backlog header's **Select** action enters selection
  mode; while it is on, a row tap toggles that row instead of opening the editor, and Clear,
  Back, or leaving the destination exits it and restores the editor tap.
- [x] Selection is confined to the Backlog section: Archived rows can never be selected, switching
  segment clears the set, and changing the search/sort never leaves hidden rows selected
  (`ProjectBacklogModelTest`, `leavingTheBacklogSectionEndsSelection`; entering the mode closes
  the field and editing the field drops the selection).
- [x] **Add to board** applies one chosen lane (`StatusDefinition.id`) to every selected item,
  through the same `MoveWorkItem` command and remembered-default rules the single-row path uses;
  each item queues its own outbox row and lands in that lane (`WorkItemBatchesTest`,
  `ProjectBacklogModelTest`, `theSelectionBarBoardsTheSetIntoOneLane`,
  `theSelectionWithSeveralColumnsAsksForOneLane`).
- [x] **Archive** files every selected item through `ArchiveWorkItem` (terminal `Closed`), each
  with its own `resolvedAt` write; an item that became terminal on another device between the
  selection and the command is counted as skipped rather than failing the batch
  (`WorkItemBatchesTest`, `ProjectBacklogModelTest`).
- [x] A failure on one item does not abort the rest: the model returns moved/archived/skipped/
  failed counts plus the first failure, succeeded items leave the selection, and failed items stay
  selected so the caller can retry them (`WorkItemBatchesTest`, `ProjectBacklogModelTest`).
- [x] While a batch runs the action bar is disabled and names the work in progress; the selected
  count is announced to assistive technology, and each row exposes a `selected` state
  (`theBarRefusesActionsAndNamesTheWorkInProgress`, `theHeaderSelectEntersTheMode`,
  `tappingARowInSelectModeTicksIt`).
- [x] Reverting the batch use case to a single-item call fails the batch test naming the defect
  (Rule 1 — verified by mutating `PutWorkItemsOnBoard` to `take(1)`: three tests failed).

**KMP Constraint:** no platform-specific APIs (`java.*`, `android.*`, …) in `commonMain` without
an explicit `expect/actual` contract; long-press comes from `combinedClickable`/`pointerInput`
inside the existing Compose surface, not from a platform gesture API.

## Non-goal

- Shift-click ranges, select-all-visible, and select-all-project — a later spec if the list proves
  long enough to need them.
- Multi-drag, and any drag of a selection.
- Undo of a batch. The outbox is the durability story; a mistaken batch is corrected by acting on
  the items again.
- No server batch endpoint and no new wire key: batching is a presentation convenience over the
  per-item commands the sync engine already pushes.
- Selection in the Kanban or Archived sections of this screen; those keep their per-row Open,
  Reopen, and Copy actions.
- The board column's selection — `specs/board-column-multi-select.md`.

## Validation

- **Iteration:** `scripts/check.sh quick packages:feature-projects-domain
  'com.tasktracker.featureprojectsdomain.*'`,
  `scripts/check.sh quick packages:feature-projects-presentation
  'com.tasktracker.featureprojectspresentation.*'`, and the backlog scenarios' `shared-ui`
  entry points.
- **Before handoff:** `scripts/check.sh full` — new strings, a new visual state, and a test-count
  move; the full matrix is what proves the Compose affordance on all three hosts.
- **Counts / Baselines:** tests increase on all three hosts; `test-baseline.json` is regenerated
  from a green full run, never hand-edited.
- **Stays pending:** how the long-press feels on a real phone, and whether the desktop entry
  point should also be a modifier-click — both are device checks, not test assertions.

## Handoff

- **Done:** Domain batch use cases, the shared `WorkItemSelection`, model selection state and
  both batch commands, the screen's Select/Clear control and action bar, the row's checkbox
  semantics and tick box, the batch-aware column sheet, strings, icon, host wiring, and tests at
  every layer. Focused domain, presentation, UI and shared-ui checks are green; the full gate is
  the next step.
- **Pending:** `scripts/check.sh full` (three hosts + architecture + baseline) and the
  `test-baseline.json` regeneration it implies; then commit on this branch, run
  `specs/board-column-multi-select.md`, and promote the two lines under **Promote** to
  `docs/DECISIONS.md` when the branch lands.
- **Next step:** run the full gate, regenerate the baseline from a green run, and commit.
- **Gotchas:** One action bar serves both sections' widths — it replaces the sort row, so the
  search toggle is deliberately hidden while selecting; putting it back would let a query hide a
  ticked row. The batch lane resolver (`chooseLane`) is shared with the single-row path, and
  `putOnBoard` now delegates to the one-item batch: do not reintroduce a second resolution.
  `busyItemID` stayed a single item; the batch uses its own `isBatchRunning`. A partial failure
  reports only the first failure's sentence — the failed rows staying ticked is what carries the
  count, so do not add a second banner for the rest.

## Promote

- **To `docs/DECISIONS.md`:** "Batch actions are presentation convenience over per-item domain
  commands: each item keeps its own outbox row and the wire stays per-entity." and "Backlog
  selection is scoped to the Backlog section and never survives navigation."
