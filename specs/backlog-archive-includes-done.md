# backlog-archive-includes-done — list finished work in the backlog's archive

- **status:** done
- **priority:** P1
- **plan:** none (standalone bug fix; reported from use)

## Scope

`packages/feature-projects-domain` (the sectioned projection) and
`packages/feature-projects-presentation` (the backlog row), plus the `:shared-ui` scenario
hosts that register the backlog's UI scenarios. No wire format, no port, no Gradle graph.

## Files

- `packages/feature-projects-domain/src/commonMain/kotlin/com/tasktracker/featureprojectsdomain/Projections.kt`
  — `BacklogSectionedProjection.archived` reads `isTerminal` rather than `Closed`; `totalItemCount`
  becomes `items.size`, because the archive now overlaps the board's done lane.
- `packages/feature-projects-presentation/src/commonMain/kotlin/com/tasktracker/featureprojectspresentation/backlog/WorkItemBacklogRow.kt`
  — "File away" only on non-terminal rows; "Reopen" on any terminal row.
- `packages/feature-projects-presentation/src/commonMain/kotlin/com/tasktracker/featureprojectspresentation/backlog/ProjectBacklogModel.kt`
  — comment only: the archive is terminal, and overlaps the board on purpose.
- Tests: `ProjectionsTest`, `ProjectBacklogModelTest`,
  `shared-ui/.../BacklogSectionScenarios.kt`, `ShellScenarios.kt`, `TaskTrackerAppScenarios.kt`.

## Contract

- [x] A `Done` item is in `BacklogSectionedProjection.archived`, newest-first, alongside `Closed`.
- [x] `totalItemCount` counts each item once, so a `Done` item in two sections is one item.
- [x] A terminal row renders no "File away"; a live row still does.
- [x] A terminal row in the archive renders "Reopen" (a `Done` task's way back is the same as a
      `Closed` one's).
- [x] Reverting the production change fails the new tests, each naming the defect (mutation run
      recorded below).

## Non-goal

- [x] Not changing `ArchiveWorkItem`'s refusal of terminal items. `Done` is already filed away;
      the refusal is correct, and the bug was that the archive did not show what it refused.
- [x] Not changing what `Closed` means, and not adding a `Done → Closed` transition.
- [x] Not touching the project-level Archive tab (`ArchiveProjection` already reads `isTerminal`).

## Validation

- `./gradlew --offline --no-parallel :packages:feature-projects-domain:jvmTest :packages:feature-projects-presentation:jvmTest`
  — full module suites, green.
- `./gradlew --offline --no-parallel :shared-ui:jvmTest --tests 'com.tasktracker.sharedui.TaskTrackerAppJvmTest.<scenario>'`
  — every backlog scenario in the group, green on JVM.
- Mutation: with `archived` back on `Closed` and the row back on `!= Closed`, `ProjectionsTest`
  fails 3 tests (`expected:<[TT-5, TT-6]> but was:<[TT-6]>`), `ProjectBacklogModelTest` fails
  `a done task is refused a second filing and is already in the archive`
  (`expected:<[AP-1]> but was:<[]>`), and both new scenarios fail (`assertDoesNotExist`). Fix
  restored, all green.
- Counts: +1 test in `ProjectionsTest`, +1 in `ProjectBacklogModelTest`, +1 scenario × 3 hosts
  (`TaskTrackerAppJvmTest` / `…AndroidHostTest` / `…IosTest`), and one scenario renamed at
  index 2 (no index shifts; the new one is appended at 14). `test-baseline.json` is stale until
  the next `scripts/check.sh full`.

## Handoff

- **Done:** the fix, the four tests above, and the mutation run.
- **Pending:** iOS simulator and Android host runs of `TaskTrackerAppScenarios` (the JVM host is
  the one that was run here); `scripts/check.sh full` and the baseline regeneration at release.
- **Next step:** `scripts/check.sh full`, then `python3 scripts/test-baseline.py`.
- **Gotchas:** the archive is terminal, not `Closed` — three surfaces had drifted
  (`ArchiveWorkItem`, `ArchiveProjection` and the editor already said `isTerminal`; the backlog's
  projection and row did not). Any new listing of "finished" work must read
  `WorkItemStatus.isTerminal`, or it will disagree with the other two.

## Risks / notes

- `totalItemCount` was a sum of the three sections. It is now `items.size`: with the overlap the
  sum double-counted every `Done` item, and a count that silently disagrees with the pile is
  worse than no count.
- The archived list's order is a stable sort on `archivedOn`, so `Done` and `Closed` items that
  share an instant keep the caller's order. That is what the existing "dual-authored closed
  items share a moment" test pins, and it now covers both statuses.
