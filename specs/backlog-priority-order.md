# backlog-priority-order — order backlog cards by priority, then creation time

- **status:** in-progress
- **priority:** P2
- **plan:** `none` — standalone polish on the backlog listing.

## Scope

Domain ordering and the projections that consume it. Presentation already renders
`projection.backlog` in the order the domain hands it, so no screen file changes.

## Files

- `packages/domain/src/commonMain/kotlin/com/tasktracker/domain/Entities.kt` — add
  `WorkItem.backlogOrderingKey`, the board's tie-break chain with manual `rank`
  left out.
- `packages/domain/src/commonMain/kotlin/com/tasktracker/domain/ProjectRules.kt` —
  `WorkItemGrouping.untriaged` sorts by that key instead of `orderingKey`.
- `packages/domain/src/commonTest/kotlin/com/tasktracker/domain/DomainTest.kt` —
  pin priority over insertion rank and oldest-first within a priority.
- `packages/feature-projects-domain/src/commonTest/kotlin/com/tasktracker/featureprojectsdomain/ProjectionsTest.kt`
  — replace the old "same order the board would rank it" expectation.

## Contract

- [x] `WorkItemGrouping.untriaged` returns `Backlog` items by priority descending,
      then `createdAt` ascending, then key; it ignores `rank`.
- [x] Items with equal priority keep the board's oldest-first fallback, so the two
      listings agree about everything a user can control.
- [x] The Kanban and Archived sections are unchanged: board order and newest-filed
      respectively.

## Non-goal

- [ ] Do not change board-column ordering or drag behaviour; `rank` remains the
      primary key there.
- [ ] Do not reorder the Archived tab, which is intentionally newest-first.

## Validation

- Run: `./gradlew --offline --no-parallel :packages:domain:jvmTest
  :packages:feature-projects-domain:jvmTest --tests 'com.tasktracker.domain.WorkItemGroupingTest'
  --tests 'com.tasktracker.featureprojectsdomain.ProjectionsTest'` — focused, green.
- Counts: two new/rewritten behavioural tests; host totals move by the same count on
  JVM. No `test-baseline.json` regeneration needed for this focused change.
- No pixel-reading tests are involved.

## Handoff

- **Done:** Domain ordering change, both focused suites green on JVM.
- **Pending:** Review and merge; no full-matrix run was needed for this domain-only
  change.
- **Next step:** Review the diff and merge `feat/backlog-priority-order`.
- **Gotchas:** Backlog `rank` is insertion order, not user intent — new cards are
  created at the top of the column. Do not "fix" the split by giving backlog its own
  copy of the sort; `backlogOrderingKey` is the one definition.

## Risks / notes

- A backlog card triaged onto the board lands at the bottom of its lane, so the order
  the user saw in the backlog is not preserved by the move. That is existing board
  behaviour and out of scope here.
