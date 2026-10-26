# Sprint window overlap — show the server/store refusal in the editor

- **status:** done
- **priority:** P1
- **plan:** no `docs/PLAN.md` entry — standalone cross-repo fix. The server half is
  [`../../TaskTrackerServer/specs/sprint-window-overlap.md`](../../TaskTrackerServer/specs/sprint-window-overlap.md).

## Scope

- `:packages:feature-projects-presentation` — `SprintEditorSheet` and scenarios.
- `:shared-ui` — `NavigationHost` wiring.
- No domain/data changes; the rule is already enforced in the store and now also
  by the server. This only makes a store refusal visible.

## Files

- `packages/feature-projects-presentation/src/commonMain/kotlin/com/tasktracker/featureprojectspresentation/sprint/SprintEditorSheet.kt`
  — add `errorMessage: String? = null`, `onDismissError: () -> Unit = {}`, render
  `ttBanner` above the action row with `SprintEditorErrorTag`; add the tag.
- `shared-ui/src/commonMain/kotlin/com/tasktracker/sharedui/NavigationHost.kt` —
  pass `editor.errorMessage` and `editor::dismissError` to both editor call sites.
- `packages/feature-projects-presentation/src/commonTest/kotlin/com/tasktracker/featureprojectspresentation/SprintEditorScenarios.kt`
  — structural scenario that a passed `errorMessage` renders as a banner above Save.
- `packages/feature-projects-presentation/src/jvmTest/kotlin/com/tasktracker/featureprojectspresentation/ProjectsUiJvmTest.kt`
- `packages/feature-projects-presentation/src/iosTest/kotlin/com/tasktracker/featureprojectspresentation/ProjectsUiIosTest.kt`
- `packages/feature-projects-presentation/src/androidHostTest/kotlin/com/tasktracker/featureprojectspresentation/ProjectsUiAndroidHostTest.kt`
  — thin entry points for the new scenario (host-split tests).

## Contract

- [x] The sprint editor shows `ttBanner` with the model's `errorMessage` above
      the Cancel/Save row when a store/refusal message is present.
- [x] Dismissing the banner calls `onDismissError` (no navigation change).
- [x] A server-`rejected` sprint push arrives as `ProjectModel.errorMessage` on
      `SprintEditorModel` (via the repository/use-case path) and leaves the sheet
      open; it is now rendered rather than silent.
- [x] The local draft checks (`blockedBySprintName`, `endIsBeforeStart`) remain
      unchanged; the banner is only for a refusal the form could not see.

## Validation

- `./gradlew --offline --no-parallel :packages:feature-projects-presentation:jvmTest --tests '*SprintEditor*'`
- `./gradlew --offline --no-parallel :shared-ui:compileTestKotlinJvm`
- iOS/Android host runs: structural scenario added to all three entry points;
      pixel/rendering checks not required for a banner above the button row.
- Mutation check: comment out the banner in `SprintEditorSheet` and the new
      scenario must fail.

## Handoff

- **Done:** added banner + wiring on both call sites, structural scenario `aPassedErrorMessageRendersAsABannerAboveSave` and test entry points on JVM, iOS, and Android hosts.
- **Pending:** none.
- **Next step:** complete.
- **Gotchas:**
  - `ttBanner` is in `:packages:design-system`; the sheet already uses it
    everywhere else — no new design primitive.
  - The sheet scrolls (`TTBottomSheetScaffold`), so the banner must sit above the
    buttons, not under the title.
  - `SprintEditorModelTest` already expects the refusal to land in `errorMessage`
    as a banner — this makes that assertion true on screen.

## Risks / notes

- A `rejected` outbox row is dead-lettered on the device; surfacing dead-letter
  reasons in the sprint UI is a separate sync-UI task. This only shows the reason
  when `errorMessage` is set by the model.
