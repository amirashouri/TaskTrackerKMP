# editor-discard-confirmation — Ask before Cancel throws away an editor draft

- **status:** in-progress
- **priority:** P1
- **plan:** none (standalone user-requested change; no active `docs/PLAN.md` entry)

## Scope

Client-side, KMP-only. Three editor surfaces close a draft on Cancel, and each now asks
first when the draft has unsaved work:

1. `ProjectEditorScreen` — the admin project create/edit pane and the member project
   editor when it is hosted full-screen.
2. `NewProjectSheet` — the member project create/edit sheet, including its scrim exit.
3. `WorkItemEditorSheet` → `ProjectsEditorFullScreen` — the live work item editor for
   both create and edit.

A clean form still closes immediately: a prompt there would ask the user to confirm losing
nothing, and on every create that would train them to dismiss it. Save and File Away are
not guarded; they are what the draft was for.

The flag is not `hasChanges` on a create. `WorkItemEditorModel.hasChanges` is deliberately
`true` for every create because a create always writes a record, so the discard question
uses a new `hasUnsavedChanges` that compares against the values the editor opened with
(including a board-lane status seeded by the route).

## Files

- `packages/feature-projects-presentation/.../project/ProjectEditorModel.kt` — a create's
  `hasChanges` now also counts a picked member.
- `packages/feature-projects-presentation/.../project/AdminProjectCreateModel.kt` — new
  `hasChanges` for the wide-window create pane; `clearDraft` returns it to clean.
- `packages/feature-projects-presentation/.../project/ProjectEditorScreen.kt` — the guard,
  `ConfirmPopup`, and `ProjectEditorDiscardTag`; the form body is wrapped in a `Box` so the
  popup can draw over it.
- `packages/feature-projects-presentation/.../project/NewProjectSheet.kt` — guards Cancel
  and the scrim through one `pendingDiscard` action, so each confirmation runs the callback
  that opened it.
- `packages/feature-projects-presentation/.../workitem/WorkItemEditorModel.kt` —
  `createBaseline`, `currentDraft()`, `hasUnsavedChanges`; staged attachments and unparsed
  story-points text count as work.
- `packages/feature-projects-presentation/.../workitem/WorkItemEditor.kt` — the full-screen
  guard and popup. Also fixes `Spacer(modifier.height(40.dp))` → `Spacer(Modifier…)` from
  the preceding layout commit: the lowercase parameter put the shell's test tag and fill on
  a 40 dp spacer, which made every `shell.workItemEditor` query ambiguous on all hosts.
- `packages/design-system/.../composeResources/values/strings.xml` — `common_discard`,
  `editor_discard_title`, `editor_discard_body`.
- `shared-ui/.../NavigationHost.kt`, `ProjectsHost.kt`, `AdminProjectsDestination.kt` —
  pass the model flags; no decision lives in the hosts.
- Tests: model tests for the three models, new `ProjectEditorScenarios.kt` (full-screen and
  sheet), `WorkItemEditorScenarios.kt`, three host entry-point files, `test-baseline.json`.

## Contract

- [x] Cancel on a clean project editor closes it and shows no prompt.
- [x] Cancel on a dirty project editor shows `ConfirmPopup`; dismissing it keeps the editor
  open, confirming runs `onCancel`.
- [x] The same holds for the project sheet, and its scrim takes the same path.
- [x] Cancel on a clean work item editor closes it; a dirty one prompts.
- [x] A pristine create is clean even when the route seeded a board lane; typing, clearing
  the title back to blank, staging an attachment, and unparsed points text answer correctly.
- [x] A member picked on a project create counts as a change; a successful admin create
  returns the form to clean.
- [x] Save and File Away still dismiss without a prompt.
- [x] Reverting the guard makes the scenarios lose their popup and the `cancels` assertion
  fail (structural mutation check).

## Validation

- Run: `scripts/check.sh full` — all three hosts, 0 failures.
- Counts: regenerated with `scripts/test-baseline.py`; `scripts/check-baseline.sh` passes.
  Totals live in `test-baseline.json`, not here.
- The scenarios read the semantics tree and model state, not pixels, so `test-reviewer` is
  not required.

## Handoff

- **Done:** models, guards, wiring, strings, and structural tests on JVM, iOS, and the
  Android host; `scripts/check.sh full` green after regenerating the baseline. The
  pre-existing `shared-ui` failures were the `Spacer(modifier…)` typo above, not this
  feature; fixing it was required for a green full run.
- **Pending:** branch, commit, push, and PR to `main`.
- **Next step:** `git switch -c feat/editor-discard-confirmation`, commit, then `gh pr create
  --base main`.
- **Gotchas:** `ConfirmPopup` is a `Box(fillMaxSize)` overlay, not a platform dialog, so its
  parent must be a `Box`; that is why `ProjectEditorScreen` gained a wrapper. The preview-only
  `WorkItemEditorScreen` / `WorkItemEditorForm` still have no guard — they do not reach a
  model. `NewProjectSheet`'s scrim test uses the semantics action because a centre-point
  click lands on the sheet in front of the scrim.

## Risks / notes

- `hasUnsavedChanges` reads Compose state (`storyPointsText`, `stagedAttachments`), so the
  Cancel button recomposes when a pick is staged.
- Trimming is part of the comparison on both models, matching the save path: whitespace-only
  edits are not work the save would preserve.
