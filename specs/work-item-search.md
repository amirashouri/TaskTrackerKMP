# work-item-search — filter the Board and every Backlog segment

- **status:** done — validated on all three hosts (`scripts/check.sh full` green, baseline
  regenerated), ready to merge
- **priority:** P1
- **plan:** `none` — standalone work. Depends on
  [`glass-search-field.md`](glass-search-field.md) for the field primitive; that task lands
  first.
- **Client-only.** Filtering runs over the project projection the screens already hold, so
  there is no port, DTO, database, or server change: no cross-repo `ACK`, no wire-format
  risk.

## Scope

The user types into one glass search field on the Board and on the Backlog screen; the
visible cards narrow to matching work items in whichever backlog segment is selected
(Backlog, Kanban, Archive). Typing updates the field immediately; the list settles after a
short debounce.

- `:packages:domain` — the single matching rule, so the Board and all three backlog
  segments cannot disagree about what matches.
- `:packages:feature-projects-domain` — `matching(query)` on the two projections that have a
  screen. Membership stays in the projection types, next to the sections it filters.
- `:packages:feature-projects-presentation` — the debounce seam, the two screens, and the
  no-results state.
- `:packages:design-system` — three strings only (placeholder, no-results title/body). The
  field itself is the prerequisite spec.
- **Not `:shared-ui`.** The query is screen state, the same way the backlog's current
  segment is: both shells render the same screens, so the wide shell gets search with no
  host edit. It is deliberately not a `ShellPreferences` value, unlike the backlog sort
  order.

## Files

Proposed Phase 1 plan — confirm or re-`ACK` before implementation.

- `packages/domain/src/commonMain/kotlin/com/tasktracker/domain/SearchRules.kt` (new) —
  `WorkItemSearch.matches(item, query)` and `filter(items, query)`. Pure, no I/O.
- `packages/domain/src/commonTest/kotlin/com/tasktracker/domain/SearchRulesTest.kt` (new) —
  positive and negative matching cases.
- `packages/feature-projects-domain/src/commonMain/kotlin/com/tasktracker/featureprojectsdomain/Projections.kt`
  — `BoardProjection.matching(query)` and `BacklogSectionedProjection.matching(query)`;
  blank returns the same instance. Both delegate to `WorkItemSearch`.
- `packages/feature-projects-domain/src/commonTest/kotlin/com/tasktracker/featureprojectsdomain/ProjectionsTest.kt`
  — a case per section: a query that matches one backlog item leaves the other sections'
  membership unchanged, and the archive keeps newest-resolved-first order.
- `packages/feature-projects-presentation/src/commonMain/kotlin/com/tasktracker/featureprojectspresentation/shared/DebouncedQuery.kt`
  (new) — `@Composable internal fun rememberDebouncedQuery(query, debounceMillis)` and the
  shared `SEARCH_DEBOUNCE_MILLIS = 250L`.
- `packages/feature-projects-presentation/src/commonMain/kotlin/com/tasktracker/featureprojectspresentation/project/MemberPicker.kt`
  — point its private constant at the shared one; behaviour unchanged (two 250ms constants
  is how the two searches drift apart).
- `packages/feature-projects-presentation/src/commonMain/kotlin/com/tasktracker/featureprojectspresentation/board/ProjectBoardScreen.kt`
  — query state, debounced projection, field in the header, no-results state, new tags.
- `packages/feature-projects-presentation/src/commonMain/kotlin/com/tasktracker/featureprojectspresentation/backlog/ProjectBacklogScreen.kt`
  — same, with the field inside the measured header and a no-results state per segment.
- `packages/feature-projects-presentation/src/commonTest/kotlin/com/tasktracker/featureprojectspresentation/WorkItemSearchScenarios.kt`
  (new) — the screen-level scenarios listed under Contract.
- `packages/feature-projects-presentation/src/jvmTest/.../ProjectsUiJvmTest.kt`,
  `src/iosTest/.../ProjectsUiIosTest.kt`,
  `src/androidHostTest/.../ProjectsUiAndroidHostTest.kt` — one thin entry point per
  scenario, per host.
- `packages/design-system/src/commonMain/composeResources/values/strings.xml` — add
  `work_item_search_placeholder` ("Search tasks"), `work_item_search_empty_title`
  ("No matching tasks"), `work_item_search_empty_body` ("Try a different word, or clear
  the search to see everything.").

## Contract

### The matching rule

- [x] Matching is `WorkItemSearch`, shared by the Board and the Backlog. A query is trimmed;
      blank means **all items**, and returns the projection unchanged (identity, not a copy
      with the same contents).
- [x] Otherwise an item matches when its **key** (`TT-142`) or **title** contains the query
      as a case-insensitive substring. This is the rule the dependency picker already uses
      (`WorkItemDependencyPickerSheet`), so the app has one definition of "matches a task".
- [x] Details, tag names, assignee names, and blocked-by keys are **not** searched. A query
      that appears only in `details` returns nothing — pin that negative case.
- [x] No fuzzy matching, no separator-insensitive keys: `TT142` does not match `TT-142`.
- [x] Replacing the matcher with `return all` (or `return true`) turns the negative cases
      red, including the details-only and no-match cases.

### Board

- [x] The field sits in the board header, immediately below the project/title/My Tasks row
      and above the error banner and lanes, and is rendered only when a project is loaded.
      It uses `ttSearchField`; the screen does not draw its own pill.
- [x] The debounced query narrows the board **after** the model's sprint and My Tasks
      filters. Search and My Tasks compose as an intersection and neither resets the other.
- [x] Lane membership, lane counts, and card order all come from the filtered projection, so
      a lane header's count is the number of visible cards. All six lanes stay present even
      when a search removes every card from one of them; the horizontal snap geometry is
      unchanged.
- [x] The assignee stack in the header keeps reading the **unfiltered** projection: typing
      narrows cards, not the people the board belongs to.
- [x] A non-blank query that matches nothing replaces the lane row with the shared
      no-results state (`ProjectBoardEmptySearchTag`), not six empty lanes. Clearing restores
      the lanes. With a blank query, the board's existing empty-lane behaviour is untouched.
- [x] Dragging a visible card still targets the correct lane and moves it; the card remains
      in the filtered set because a move does not change key or title.

### Backlog, all three segments

- [x] The field sits inside the floating header, below the project switcher/add row and
      above the error banner and segmented picker. It is inside the `onSizeChanged`-measured
      column, so the list's top inset grows with it and the first row stays below the header
      (the existing `theListScrollsUnderTheHeader` scenario must still pass).
- [x] One query filters the currently selected segment: Backlog filters untriaged items
      before `backlogIn(sortOrder)` sorts them, Kanban filters the board items, Archive
      filters terminal items before newest-resolved-first ordering.
- [x] Switching segments keeps the typed query; each segment shows its own matches. Tapping
      a sort chip while a query is active keeps the query and re-sorts the matches.
- [x] A non-blank query with no matches in the active segment shows the shared no-results
      state (`ProjectBacklogEmptySearchTag`) instead of that segment's "empty" copy
      ("The backlog is empty" / "Nothing on the board" / "Nothing filed away"). With a blank
      query those three empty states are unchanged.
- [x] The query is screen-scoped, like `currentSection`: it survives segment switches and
      sort changes, and is cleared when the destination leaves composition. It is not a
      preference and is not persisted.

### Debounce

- [x] The field's `value` updates on every keystroke (no visible lag in the text). The
      **applied** query — the one that filters the list — changes only after the trimmed
      value has been idle for `SEARCH_DEBOUNCE_MILLIS` (250ms, the member picker's value).
- [x] A second keystroke inside the window cancels the pending apply; only the last value is
      applied. Test: type a query, advance the test clock 100ms and assert the list is still
      unfiltered, advance 200ms more and assert it is filtered.
- [x] Clearing to blank applies immediately, with no debounce wait, so the clear control
      restores the full list on the next frame.
- [x] Pending work is cancelled when the screen leaves composition; no coroutine outlives
      the destination.
- [x] Reverting the debounce to an immediate apply turns the "still unfiltered at 100ms"
      assertion red.

## Non-goal

- [x] No repository, SQL, or network search; no new port, no sync or DTO change. The project
      projection is already in memory.
- [x] The month-grouped standalone archive screen (`ProjectArchiveScreen`) is not searched —
      only the Backlog screen's Archived segment.
- [x] Not the cross-project search in
      [`2.3-search-across-projects.md`](2.3-search-across-projects.md); this filters one
      screen's already-loaded project.
- [x] No query persistence, recent-search history, highlighted match fragments, or result
      count chip.
- [x] No new visual primitive or token: the field is `glass-search-field`, the empty state
      reuses `ProjectsEmptyState`.

## Validation

- Run, one line per touched module:
  - `./gradlew --offline --no-parallel :packages:domain:jvmTest --tests
    'com.tasktracker.domain.SearchRulesTest'`
  - `./gradlew --offline --no-parallel :packages:feature-projects-domain:jvmTest --tests
    'com.tasktracker.featureprojectsdomain.ProjectionsTest'`
  - `./gradlew --offline --no-parallel :packages:feature-projects-presentation:jvmTest
    --tests 'com.tasktracker.featureprojectspresentation.ProjectsUiJvmTest'`
- Counts: `SearchRulesTest` and the `ProjectionsTest` additions are plain `commonTest`, so
  each case moves the JVM, iOS, and Android-host totals. `WorkItemSearchScenarios` bodies
  live in `commonTest` with one entry point per host, so each scenario adds one test to each
  host's total — not one total overall. No rasterising case is added here; the field's
  silhouette test belongs to `glass-search-field`.
- The two screens render on every host, so the full matrix (`scripts/check.sh full`) is the
  release gate before merge. If the iOS or Android-host run is not executed in this session,
  record it as pending in the Handoff rather than claiming it.
- Structural/behavioural scenarios do not need `test-reviewer`; there is no new pixel
  claim in this task.

## Handoff

- **Done:** `WorkItemSearch` (`SearchRules.kt`) with `SearchRulesTest`; `matching(query)` on
  `BoardProjection` and `BacklogSectionedProjection` with five new `ProjectionsTest` cases;
  `DebouncedQuery.kt` (`SEARCH_DEBOUNCE_MILLIS` + `rememberDebouncedQuery`); `MemberPicker`
  now points at the shared constant; the field, the narrowed projection and the no-results
  state on both screens; `WorkItemSearchScenarios` with eight scenarios and one entry point
  per host. Focused JVM run green: `SearchRulesTest`, `ProjectionsTest`,
  `ProjectsUiJvmTest` (102 tests, whole file), plus the full `jvmTest` of the four touched
  modules and `:shared-ui:compileKotlinJvm`.
- **Verified on all three hosts:** the eight entry points are green on each. JVM and iOS both
  report `ProjectsUiJvmTest`/`ProjectsUiIosTest` at `tests=102 skipped=0 failures=0`, and the
  Android host reports `tests=102 skipped=0 failures=0` for `ProjectsUiAndroidHostTest` — the
  scenarios advance `mainClock` rather than sleeping, so the debounce case is a real virtual
  time assertion on every host and none of the eight is skipped. `SearchRulesTest` (9),
  `ProjectionsTest` (36 total, 5 new) and the four modules' full `jvmTest` are green.
- **Gate:** `scripts/check.sh full` is green on all three hosts — `jvmTest` 2132,
  `iosSimulatorArm64Test` 2070, `testAndroidHostTest` 1764, 0 failed / 0 errors — including
  `verifyArchitectureGraph` (no dependency edge was added, as the scope note predicted) and
  `verifyModuleImports`. `test-baseline.json` was regenerated from that run and
  `scripts/check-baseline.sh` passes; the committed baseline had been stale by 49 tests per
  host (30 from these two specs, 19 pre-existing drift).
- **Next step:** none outstanding. Commit is the user's call.
- **Gotchas:**
  - `assertTextEquals("2", "explanation")` is **not** a message — the second string is a
    second accepted value. Use the module's `assertText(tag, expected, useUnmerged)` helper,
    which reports the actual text, or a plain comment.
  - Lane counts and hint text live inside merged nodes (`BoardColumnView`'s clickable, the
    field's `decorationBox`), so they are only reachable with `useUnmerged = true`. Row tags
    are reached through `onNested`.
  - `remember(projection, appliedQuery)`, not `remember(appliedQuery)`: the model replaces the
    projection on every reload and on every My Tasks toggle.
  - The seeded project has exactly one untriaged item, so a "the list narrowed" scenario
    needs a second `Backlog` row to lose — `twoUntriagedBacklog` in the scenarios file is that
    fixture.
  - The debounce is verified by mutation: replacing `delay(debounceMillis)` with an immediate
    `applied = trimmed` turns `theBoardWaitsForTheDebounce` and
    `clearingTheSearchRestoresTheListAtOnce` red.

## Risks / notes

- **A local filter does not need a debounce for speed — the user asked for it for feel.**
  250ms is the existing member-picker value and is long enough to avoid a flash on every
  keystroke, short enough not to read as lag. Do not add a spinner: a 250ms local filter
  with a progress indicator looks slower than it is.
- **Compose test time.** Use `mainClock.autoAdvance = false` and
  `mainClock.advanceTimeBy(...)`; a wall-clock `Thread.sleep` would pass on a fast JVM and
  flake on CI. The same scenario has since been run on the iOS host (green, `skipped=0`), which
  is what this note was asking for.
- **IME composition.** A CJK keyboard emits `onValueChange` while composing; the debounce
  absorbs the intermediate strings. The contract is about the applied value, not every
  intermediate composition update.
- **The board's empty-lane invariant is deliberate.** Do not satisfy "no matches" by
  deleting lanes when a query is blank, or by letting the search state persist after the
  query is cleared. The carve-out is exactly: non-blank applied query + zero matches.
- **`remember(projection, appliedQuery)`** must key on the projection *instance* (the model
  replaces it on every reload) as well as the query, or a server/observer update will leave
  a stale filtered list on screen.
