# search-field-toggle — reveal the search field from a header button

- **status:** done — validated on all three hosts (`scripts/check.sh full` green, baseline
  regenerated), ready to merge
- **priority:** P2
- **plan:** `none` — a follow-up to [`glass-search-field.md`](glass-search-field.md) and
  [`work-item-search.md`](work-item-search.md), which shipped the field and the filtering. This
  changes only how the field is reached.

## Scope

- The field is **closed by default** on both the Board and the Backlog, and each screen gains a
  toggle button beside the control it already had: next to the `+` on the Backlog, next to My
  Tasks on the Board.
- The reveal is animated, and the platform's reduced-motion setting is honoured.
- Two modules, because the toggle's accessible labels belong to `:packages:design-system` with
  every other string (§2 "Tokens & Resources"). No dependency edge is added, so the architecture
  graph is untouched.

## Files

- `packages/design-system/src/commonMain/composeResources/values/strings.xml` — `work_item_search_open`
  and `work_item_search_close`.
- `packages/feature-projects-presentation/src/commonMain/kotlin/com/tasktracker/featureprojectspresentation/shared/SearchReveal.kt`
  (new) — `SEARCH_REVEAL_MILLIS` and `SearchReveal`.
- `…/board/ProjectBoardScreen.kt` — `searchOpen`, the toggle in the header row, the field wrapped
  in `SearchReveal`, `ProjectBoardSearchToggleTag`.
- `…/backlog/ProjectBacklogScreen.kt` — the same, with the toggle in the switcher row beside `+`,
  and `ProjectBacklogSearchToggleTag`.
- `…/commonTest/…/WorkItemSearchScenarios.kt` — helpers open the field; six new scenarios.
- `…/jvmTest|iosTest|androidHostTest/…/ProjectsUi*Test.kt` — one entry point each per scenario.

## Contract

- [x] The field is closed on first composition on both screens; the toggle is present.
- [x] The toggle sits **before** the existing trailing control, so `+` and My Tasks do not move.
- [x] The toggle's content description flips with state ("Search tasks" ↔ "Close search"): a
      toggle must announce what the tap will do, not what it currently is.
- [x] The toggle is accent-tinted while the field is open, matching My Tasks' active state.
- [x] No toggle on the Board when there is no project — it would reveal a field over a list that
      does not exist.
- [x] Closing clears the query, so the list is never filtered by a control that is off screen.
- [x] Closing also clears focus, so the keyboard does not outlive the field.
- [x] The reveal is a fade plus a vertical expand from the top; the field takes its space in the
      column rather than overlaying the list.
- [x] Reduced motion is a change of travel, not of outcome: the field still appears, without
      unfolding.
- [x] Both screens use the same seam, so the two cannot drift in duration or easing.
- [x] Open/closed is screen state, like the query: it resets when the destination is left.

## Handoff

- **Done:** the two strings, `SearchReveal.kt`, the toggle on both screens, six scenarios, three
  host entry points each, and this file. `:packages:feature-projects-presentation:jvmTest` is
  green with `ProjectsUiJvmTest` at `tests=108 failures=0`.
- **Mutations run (§4), each against the test that claims the behaviour:**
  - `searchOpen` defaulted to `true` → `theBoardHidesTheSearchUntilAsked` and
    `theBacklogHidesTheSearchUntilAsked` red (plus the typing helpers, which then toggle a field
    that is already open — expected collateral, not a second finding).
  - `query = ""` removed from the close branch → exactly `closingTheBoardSearchClearsTheQuery`
    ("should say 2, but says 1") and `closingTheBacklogSearchClearsTheQuery` red.
  - `SearchReveal` forced to `EnterTransition.None`/`ExitTransition.None` →
    `theFieldUnfoldsRatherThanSnapping` red, reporting the lanes already at the open position.
  - `LocalReducedMotion` ignored (`val reducedMotion = false`) →
    `reducedMotionRevealsTheFieldWithoutTravel` red. **This one caught a bad test:** the first
    version asserted only that the lanes had moved down, and it passed against the mutation,
    because one frame of a 200ms unfold is already lower than closed. The assertion now compares
    against the *settled* height (58dp expected, 10dp observed under the mutation).
- **Verified on all three hosts:** `ProjectsUiJvmTest`, `ProjectsUiIosTest` and
  `ProjectsUiAndroidHostTest` each report `tests=108 failed=0 skipped=0` — the six new scenarios
  run on every host, including the Android one, because the reveal is measured from layout rather
  than from pixels. The full gate is green at `jvmTest` 2138, `iosSimulatorArm64Test` 2076,
  `testAndroidHostTest` 1770, 0 failed / 0 errors, plus `verifyArchitectureGraph` and
  `verifyModuleImports`. The baseline grew by exactly the six scenarios per host and
  `scripts/check-baseline.sh` exits 0.
- **Pending:** nothing. Commit is the user's call.
- **Gotchas:**
  - **Do not measure the field to test the animation.** `expandVertically` clips its content, so
    the field's own bounds can report full height while almost none of it is visible. The lane row
    below is the honest measurement, because it is a sibling that moves with the space being made.
  - **`> closed` is not "did not animate".** Any partial frame satisfies it. Compare against the
    position reached when the reveal is over.
  - The clock-driven scenarios must open the field *before* `mainClock.autoAdvance = false`; the
    reveal is driven by that clock, so otherwise they measure a field mid-unfold.
  - `assertContentDescriptionEquals` reads the merged button node directly — no unmerged tree
    needed, unlike the field's hint.
  - A re-run that finishes in under a second is usually a build-cache hit on identical inputs.
    Check the result XML's counts and mtime rather than trusting `BUILD SUCCESSFUL`.

## Risks / notes

- **Two labels, not one.** A single "Search" label would be read out in both states, telling a
  screen reader the opposite of what the tap does. The cost is a second string; the alternative is
  a toggle that lies.
- **Clearing on close is a decision, not an accident.** Keeping the query would let a user close
  the field and leave a filtered list behind with no visible cause. If a future change wants the
  query to survive, the toggle has to show that it is filtering — a badge or a filled tint — at
  which point the current tint alone is not enough.
- **The animation is not a substitute for focus.** The field does not take focus when it opens, so
  on mobile the user taps the toggle and then the field. Auto-focus was left out deliberately: it
  races the unfold and can throw the keyboard up before the field has its final height, which is
  exactly the jump this task set out to remove.
