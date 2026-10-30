# backlog-priority-order — order backlog cards by the sort keys the user picks

- **status:** in-progress
- **priority:** P2
- **plan:** `none` — standalone polish on the backlog listing.

## Scope

The backlog's ordering, from the domain rule that produces it to the row that chooses it.
The board and the archive are not touched: the board sorts by manual `rank`, the archive is
newest-filed first.

Two sort keys exist — priority and creation time — and both can be active at once. The first
is the order the list reads in, the second only breaks the ties the first leaves.

## Files

- `packages/domain/src/commonMain/kotlin/com/tasktracker/domain/BacklogSort.kt` — the keys,
  the ordered selection, the tap state machine, and the comparator it builds.
- `packages/domain/src/commonMain/kotlin/com/tasktracker/domain/ProjectRules.kt` —
  `WorkItemGrouping.untriaged` takes a `BacklogSortOrder` and sorts by it.
- `packages/feature-projects-domain/src/commonMain/kotlin/com/tasktracker/featureprojectsdomain/Projections.kt`
  — `BacklogSectionedProjection.backlogIn(order)` for a selection made after the projection
  was built.
- `packages/feature-projects-presentation/src/commonMain/kotlin/com/tasktracker/featureprojectspresentation/backlog/ProjectBacklogScreen.kt`
  — holds the selection for the Backlog section and passes it down.
- `packages/feature-projects-presentation/src/commonMain/kotlin/com/tasktracker/featureprojectspresentation/backlog/BacklogSortControls.kt`
  — the two chips and the `Sort by …` label.
- `packages/design-system/src/commonMain/composeResources/values/strings.xml` — the label
  formats and the short key names the label is built from.
- `packages/domain/src/commonTest/kotlin/com/tasktracker/domain/DomainTest.kt`,
  `packages/feature-projects-domain/src/commonTest/kotlin/com/tasktracker/featureprojectsdomain/ProjectionsTest.kt`
  — ordering and tap-state behaviour.
- `shared-ui/src/commonTest/kotlin/com/tasktracker/sharedui/BacklogSectionScenarios.kt` — the
  chips, the label and the rows as a user walks the whole cycle.

## Contract

- [x] `WorkItemGrouping.untriaged(items, order)` returns `Backlog` items by `order`, and the
      default order is a single priority key (highest first). `rank` is never consulted.
- [x] A selection is one or two keys. Keys chain in list order and the final tie-break is the
      item key, so the order is deterministic.
- [x] Both keys can be active: priority leading, or creation time leading.
- [x] Tapping an inactive key switches it on **in front** at its default direction; the
      previous leader becomes the tie-breaker and keeps its direction.
- [x] Tapping the leading key reverses it; tapping it again switches it off and the
      tie-breaker takes the lead.
- [x] Tapping the tie-breaker promotes it to lead, keeping its direction.
- [x] The last remaining key never switches off — it cycles between its two directions,
      because a list has to be ordered by something.
- [x] The label names every active key in the order applied: "Sort by priority",
      "Sort by priority and newly added", "Sort by lowest priority and oldest added".
- [x] The chips report `selected` and their direction, and an off chip says "Not sorting".
- [x] The Kanban and Archive sections are unchanged.

## Non-goal

- [ ] Do not change board-column ordering or drag behaviour; `rank` remains the primary key
      there.
- [ ] ~~Do not persist the selection across app launches — it is screen state, like the tab.~~
      **Reversed** by [`backlog-sort-persists.md`](backlog-sort-persists.md): the selection is
      the user's choice, and losing it on every tab switch and relaunch was the bug. It is now
      a `ShellPreferences` value stored under `backlog.sortOrder`.
- [ ] Do not reorder the Archived tab, which is intentionally newest-first.

## Validation

- Run: `./gradlew --offline --no-parallel :packages:domain:jvmTest
  :packages:feature-projects-domain:jvmTest --tests 'com.tasktracker.domain.WorkItemGroupingTest'
  --tests 'com.tasktracker.featureprojectsdomain.ProjectionsTest'` — focused, green.
- Run: `./gradlew --offline --no-parallel :shared-ui:jvmTest --tests
  'com.tasktracker.sharedui.ShellScenarios*'` — the chip scenario is a shell scenario, so it
  runs on the JVM host here; the iOS and Android hosts read the same scenario list.
- The label is read as text (`assertTextEquals`) rather than as pixels, so no pixel-reading
  test is involved and the mutation ritual does not apply.
- `test-baseline.json` needs regenerating at the release gate: this change adds tests, and the
  baseline is only written by `scripts/test-baseline.py` after `scripts/check.sh full`.

## Handoff

- **Done:** Multi-key selection in the domain, the projection seam, the chips and the label.
  Domain and projection suites green on the JVM host.
- **Pending:** The shared-UI shell scenario has not been run yet on this branch — it is the
  gate for the chip walk and the label text.
- **Next step:** Run the shell scenario above, then `scripts/check.sh full` before the PR.
- **Gotchas:**
  - Backlog `rank` is insertion order, not user intent — new cards are created at the top of
    the column. Do not "fix" the split by giving the backlog its own copy of the sort;
    `BacklogSortOrder` is the one definition.
  - The default is a *single* key, so two items of equal priority fall to the key tie-break.
    That is deliberate: a hidden second key would contradict a label that names only one.
  - `toggled` is a state machine with three cases, not a boolean flip. Read its KDoc before
    changing the order of the branches — the leading-key cases are ordered by specificity.

## Risks / notes

- A backlog card triaged onto the board lands at the bottom of its lane, so the order the user
  saw in the backlog is not preserved by the move. That is existing board behaviour and out of
  scope here.
- The selection used to live in `remember`, so leaving the screen reset it to priority. It is
  persisted now — see [`backlog-sort-persists.md`](backlog-sort-persists.md), which also
  records why the "it is screen state" answer was wrong.
