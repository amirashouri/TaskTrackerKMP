# workitem-editor-title-and-create-attachments — Fix the dead title field and the no-op create-time attach

- **status:** in-progress
- **priority:** P0
- **plan:** proposed (no active `docs/PLAN.md` entry). Follow-up to the attachment work recorded in
  [`attachment-client-sync.md`](attachment-client-sync.md); the editor surface is the one
  entry 3.3 landed.

## Scope

Client-side, KMP-only. Two defects on the live `WorkItemEditorSheet` → `ProjectsEditorFullScreen`
path:

1. **The title field rendered a constant.** `WorkItemEditorSheet` computed the surface header
   ("New Task" / "Edit TT-1") into `editTitle` and passed it as `title` to
   `ProjectsEditorFullScreen`, whose single `title` parameter fed *both* the header text and
   `ProjectsTextField(value = title)`. The model updated correctly on every keystroke, but the
   field kept rendering the header string, so typing appeared to do nothing. Introduced by
   `ecf0c978` ("always full-screen" refactor).
2. **Attaching a file on a create silently did nothing.** `WorkItemEditorModel.attachFile`
   began with `val id = existing?.id ?: return null`; a create has no `existing`, so the call
   returned before any work. The Attachments section is rendered unconditionally, so the button
   was reachable and appeared to work.

Chosen fix for (2): **stage, then attach on save.** A pick on a create is held in the model,
screened on the same rules the attach will apply, shown as a pending row, and filed against the
id the save mints.

## Files

- `packages/feature-projects-presentation/.../workitem/WorkItemEditor.kt` — the sheet passes
  the draft `title` through; `ProjectsEditorFullScreen` derives its header from
  `isCreating`/`issuedKey` and threads `pendingAttachments`/`onRemovePending`.
- `packages/feature-projects-domain/.../ProjectUseCases.kt` — `AttachFile.refusal(fileName,
  bytes)`: the pure refusal check, extracted so staging and attaching share one source for the
  cap and the empty/blank rules.
- `packages/feature-projects-presentation/.../workitem/WorkItemEditorModel.kt` —
  `stagedAttachments`, `stageAttachment`, `removeStagedAttachment`, `flushStagedAttachments`,
  and `reportRefusal`; `attachFile` keys on `savedWorkItemID` instead of `existing?.id`.
- `packages/feature-projects-presentation/.../workitem/AttachmentList.kt` — `pendingAttachments`
  / `onRemovePending` and the pending row + its tags.
- `packages/design-system/.../composeResources/values/strings.xml` — `attachment_pending`.
- `shared-ui/.../NavigationHost.kt` — `onAttachClick` stages on a create; `onSave` flushes the
  held picks and uploads each in the background.
- Tests: `WorkItemEditorScenarios.kt` (title), `AttachmentRowScenarios.kt` (pending rows),
  `WorkItemEditorModelTest.kt` (staging/flush), three host entry points, `test-baseline.json`.

## Contract

- [x] Typing into the title field renders the typed draft, not the header string.
- [x] Reverting the sheet to pass the header string turns the title test red.
- [x] A pick on a create is held and writes nothing until save.
- [x] A pick over the cap is refused at pick time with the same wording the attach uses.
- [x] Saving a create files the held picks onto the new id and clears the held list.
- [x] A held pick renders as a pending row with its caption; the empty state does not.
- [x] Remove names the held pick by position.

## Validation

- Run: `scripts/check.sh full` — all three hosts, 0 failures.
- Counts: +8 tests per host (5 model tests in `commonTest`, 3 scenario entry points per host).
  Regenerated with `scripts/test-baseline.py`; `scripts/check-baseline.sh` passes.
- The scenarios are structural assertions (semantics tree + model state), not pixel reads, so
  `test-reviewer` is not required.

## Handoff

- **Done:** both defects fixed; title test mutation-checked red then green; staging + flush
  covered by model and UI tests; `scripts/check.sh full` green on JVM, iOS and Android host;
  `test-baseline.json` regenerated (1819 / 1768 / 1475, 0 failures).
- **Pending:** push the branch and open the PR to `main`.
- **Next step:** `gh pr create --base main`.
- **Gotchas:** `WorkItemEditorScreen`/`WorkItemEditorForm` are preview-only and were
  deliberately left untouched. `attachFile` still no-ops on a create — staging is a separate
  method, so the existing "attachment calls on a create model are no-ops" test still holds.
  Pending picks have no `AttachmentID`, so their rows are keyed by index.

## Risks / notes

- Staging reuses `AttachFile.refusal`, so the client cap and the server's `AttachmentMaxBytes`
  stay one number; a second copy here would drift.
- A storage failure during the flush is reported to the banner but the editor still dismisses
  (the validation refusals were already screened at pick time), matching the create path's
  existing "save succeeded, dismiss" shape.
