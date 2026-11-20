# board-column-multi-select — multi-select a board column to move or clear Done

- **status:** in-progress
- **priority:** P1
- **branch:** `feat/board-column-multi-select`
- **blocked by:** none — the selection primitives and the batch outcome shape shipped with the
  backlog selection (`specs/backlog-multi-select.md`, merged in PR #91 and removed from
  `specs/`). This spec must not grow a second copy of them.
- **context:** Requested 2026-11-20 with the backlog spec. The board can move one card by drag or
  per-card buttons, but a **Done** card has no action at all: `BoardCardActions` returns early for
  a terminal item and `ArchiveWorkItem` refuses one, so the Done lane can only grow. There is
  currently no path that closes a Done item — `WorkItemEditorModel.availableStatuses` hides
  `Closed` — which is why clearing Done needs both the selection UI and one domain rule change.
  Client-only: no wire change, no ACK under `../AGENTS.md` §1.

## Scope

Selection and batch actions inside the board's lanes, and the one domain rule that lets a Done
card leave the board. Nothing in the backlog screen changes here.

- **Targets affected:** commonMain, commonTest
- **Modules affected:** `:packages:feature-projects-domain`,
  `:packages:feature-projects-presentation`, `:packages:design-system` (strings),
  `:shared-ui` only if a host callback signature changes

## Files

`to confirm at Phase 1`; the anchors the plan is expected to touch.

- `packages/feature-projects-domain/.../ArchiveWorkItem.kt` — the rule change: a `Done` item is
  closed (`Done → Closed`, `resolvedAt` preserved) instead of refused; `Closed` stays refused.
  `ArchiveWorkItemTest` moves with it.
- `packages/feature-projects-presentation/.../shared/WorkItemSelection.kt` — consumed, not
  re-created (owner: the backlog spec).
- `.../presentation/board/ProjectBoardModel.kt` — selection state, batch move/archive entry
  points, and the batch outcome (counts + first failure).
- `.../presentation/board/ProjectBoardScreen.kt` — select mode, header/column affordances,
  action bar, and the drag gate that must also honour selection.
- `.../presentation/board/BoardColumn.kt` — column header Select-all and the selected-card
  treatment.
- `packages/design-system/src/commonMain/composeResources/values/strings.xml` — Select, Select all,
  Clear, "N selected", Archive/Close wording, batch outcome sentences.
- `shared-ui/src/commonMain/kotlin/com/tasktracker/sharedui/ProjectsHost.kt` and
  `NavigationHost.kt` — expected unchanged; confirm at Phase 1 (both host the board).

## Contract

- [x] The column header's **Select** enters selection mode **scoped to that lane**; a tap
  toggles a card, other lanes' cards are unaffected, and Clear/Back leaves the mode and restores
  drag and tap-to-open. There is no long-press entry: on the board a long-press already lifts the
  card for drag (`detectDragGesturesAfterLongPress`, 300ms), so a stationary hold would start a
  drag ghost instead of a selection. The header is the only entry point, and it is drawn on every
  lane that has cards.
- [x] The column header's **Select all** selects only the cards that lane is currently projecting
  — the sprint filter and the "My tasks" filter apply, and hidden cards are never selected.
- [x] Archiving a **Done** card closes it: status becomes `Closed`, `resolvedAt` is kept, the next
  projection no longer lists it in Done, and it appears in the Archived segment
  (`ArchiveWorkItemTest`, `ArchiveRoundTripScenarios`).
- [x] Archiving an already-`Closed` card still refuses with its existing message and is counted as
  skipped, not failed — a batch over a mixed lane completes.
- [x] **Move to …** sends every selected card to the chosen `StatusDefinition.id` through
  `MoveWorkItem`, one command per card in board order; ranks append at the destination lane.
- [x] Partial failure is reported the same way as the backlog batch: moved/closed/skipped/failed
  counts plus the first failure; succeeded cards leave the selection and failed cards stay
  selected.
- [x] While selection mode is on, dragging is disabled for every card, and the action bar is
  disabled while a batch runs; the selected count is announced to assistive technology and each
  card exposes a `selected` state (`BoardColumnScenarios`).
- [x] Reverting the `Done → Closed` rule fails the domain test naming the refused close, and
  reverting the projection refresh fails the board scenario (Rule 1).

**KMP Constraint:** no platform-specific APIs (`java.*`, `android.*`, …) in `commonMain` without
an explicit `expect/actual` contract; the header controls and tap-to-toggle stay inside the
existing Compose card surface.

## Non-goal

- Multi-drag, and dragging any card while a selection exists.
- Selection spanning lanes, or a board-wide select-all.
- Bulk edits of fields other than status (assignee, priority, tags, sprint) — their own spec.
- No server batch endpoint, no new wire key: every card is still one outbox row.
- No undo, and no change to which statuses the editor offers — `Closed` stays out of the editor's
  picker; closing a card is an action, not a status choice.
- The backlog page's selection — `specs/backlog-multi-select.md`.

## Validation

- **Iteration:** `scripts/check.sh quick packages:feature-projects-domain
  'com.tasktracker.featureprojectsdomain.*'`,
  `scripts/check.sh quick packages:feature-projects-presentation
  'com.tasktracker.featureprojectspresentation.*'`, plus the board scenarios' `shared-ui`
  entry points.
- **Before handoff:** `scripts/check.sh full` — new strings, selection visuals, and the drag gate.
- **Counts / Baselines:** +14 on each host (jvm 2370, iOS 2305, Android host 1980);
  `test-baseline.json` is regenerated from the green full run.
- **Stays pending:** the long-press feel on a phone, and confirming on a device that the Done lane
  actually empties after a batch (the projection test is the executable proof; the device check is
  the visual one).

## Handoff

- **Done:** `ArchiveWorkItem` closes a `Done` item (`resolvedAt` kept) and refuses only `Closed`;
  `ArchiveWorkItems` skips only `Closed`; the backlog model's "finished elsewhere" test moved to
  `Closed` so it still covers the skip path. `ProjectBoardModel` carries the lane-scoped selection
  (`startSelecting`, `toggleSelected`, `selectAll` intersected with the lane projection, `clearSelection`,
  `moveSelection`, `archiveSelection`) and prunes on reload except while a batch runs. The board draws
  header Select / Select all / Clear, ticks cards through the shared `SelectionTickBox`, hides search
  and drag while selecting, and the bar offers Move to … and Archive/Close. `BoardCardActions`
  offers Archive for `Done` and skips only `Closed`. Five `BoardSelectionScenarios` run on all three
  hosts; `scripts/check.sh full` is green at jvm 2370, iOS 2305, Android host 1980, and
  `test-baseline.json` is regenerated from that run.
- **Pending:** the device pass — that the Done lane visually empties after a batch and that the bar
  does not cover the lane on a phone.
- **Next step:** merge, delete the spec, and promote the two decisions below.
- **Gotchas:** no long-press entry (it lifts a drag ghost); selection starts at the lane header and
  the other lanes' headers drop their Select while a set is open. The tick's test tag is a single
  constant, so assert it per screen, not per card. On the Android host a pointer click on the Done
  lane can land outside the viewport, so the scenarios drive those controls by semantics action.

## Promote

- **To `docs/DECISIONS.md`:** "The board's Archive action on a Done card closes it: Done is a
  lane, Closed is the archive, and closing preserves `resolvedAt`." and "Selection is lane-scoped
  and disables drag; the wire remains per-item."
