# sprint-card-archive — archive a finished sprint from its card

- **status:** in-progress
- **priority:** P2
- **branch:** `feat/sprint-card-archive`
- **blocked by:** none
- **context:** Archiving a finished sprint currently requires opening its detail page, where the
  Archive action lives behind a confirmation dialog. The list's Finished section is where a user
  sees what needs filing away, so the action belongs on the card. The request was explicit that
  this should be an action button under the Finished status, not a menu item.

## Scope

- **Targets affected:** commonMain, commonTest, jvmTest, androidHostTest, iosTest (scenario
  registration only)
- **Modules affected:** `:packages:feature-projects-presentation`, `:shared-ui`

No domain port, repository, wire DTO, or design-system token changes: the archive use case and
the archive icon already exist. The detail screen's confirmed flow stays as it is.

## Files

- `packages/feature-projects-presentation/.../sprint/SprintModels.kt` — `SprintListModel.archive`.
- `packages/feature-projects-presentation/.../sprint/SprintListRowView.kt` — archive action on
  the sprint-progress row.
- `packages/feature-projects-presentation/.../sprint/SprintListScreen.kt` — thread `onArchive`
  into the Finished section only.
- `shared-ui/.../NavigationHost.kt` — wire the card action to the model.
- `packages/feature-projects-presentation/src/commonTest/.../SprintListModelTest.kt` — model tests.
- `packages/feature-projects-presentation/src/commonTest/.../SprintListScenarios.kt` — UI scenario.
- `packages/feature-projects-presentation/src/{jvmTest,androidHostTest,iosTest}/.../ProjectsUi*Test.kt`
  — register the scenario, as every other sprint-list scenario is registered.

## Contract

- [x] A finished, non-archived card shows an archive action button on the same row as the
      sprint-progress text (`SprintListScenarios.theFinishedCardArchivesWithoutOpeningTheSprint`).
- [x] An in-progress/scheduled card offers no archive action; archived rows are never given the
      callback (`SprintListScenarios.anOpenCardOffersNoArchiveAction`, and the Finished section is
      the only caller that passes one).
- [x] Tapping the action archives that sprint through `SprintListModel.archive`, without invoking
      the card's open callback, and the row moves to the Archived section on the repository change
      (`SprintListModelTest.archiving a finished sprint moves it out of the finished section`).
- [x] Archiving is not offered for an unfinished sprint at the model level either: the use case
      refuses it and the failure reaches the screen's existing error banner
      (`SprintListModelTest.archiving an unfinished sprint reports the refusal`).
- [x] Reverting the production wiring in `SprintListScreen` (removing the `onArchive` argument)
      fails `theFinishedSprintCardArchivesWithoutOpeningTheSprint`; restored, it passes.

**KMP Constraint:** commonMain only; no platform APIs, no expect/actual, no design-system edits.

## Non-goal

- No confirmation dialog on the card: archiving is reversible from the Archived tab, and the
  detail page keeps its confirm. Adding one is a separate decision.
- No unarchive action on archived cards, and no swipe-to-archive.

## Validation

- **Iteration:** `scripts/check.sh quick feature-projects-presentation 'SprintList'`.
- **Before handoff:** `scripts/check.sh full` — the sprint row's rendered tree changes, so the
  matrix must cover JVM, Android-host and iOS.
- **Counts / Baselines:** +4 tests per host (two model tests from `commonTest` and two scenario
  registrations); regenerated with `scripts/test-baseline.py` after the full run, as the gate
  requires.

## Handoff

- **Done:** `SprintListModel.archive` uses `ArchiveSprint` through `reporting`, so refusals land
  in the existing banner; the Finished section supplies the row's `onArchive`, which renders a
  `ttGlassIconButton` trailing the sprint-progress text on that row; `SprintListDestination`
  launches it on the screen's scope. Two model tests and two UI scenarios (registered on JVM,
  Android-host and iOS).
- **Verification:** `scripts/check.sh full` — jvm 2398 / iOS 2333 / android-host 1988 tests, no
  failures; `test-baseline.json` regenerated (the expected +4 per host). Mutation check: dropping
  the screen's `onArchive` argument fails the finished-card scenario; restoring it passes.
- **Pending:** merge and push (branch not merged).
- **Next step:** review the diff, merge the branch, and delete this spec in the final commit —
  nothing durable to promote first.
- **Gotchas:** the archived section reuses `SprintListRowView`, so only the Finished section may
  pass `onArchive`; passing it to every row would offer archiving on an already archived sprint.
  The badge and progress-text tags are on merged children, so scenario lookups need
  `useUnmergedTree = true`. The placement assertion compares the action's bounds with the
  progress text's, which fails if the button moves back to the header.

## Promote

- **To `docs/DECISIONS.md`:** nothing durable — this reuses the existing archive use case and
  lifecycle; it is UI placement, not a rule.
