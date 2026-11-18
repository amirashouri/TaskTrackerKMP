# edit-project-tag — Edit an existing project tag and its icon

- **status:** done
- **priority:** P2
- **context:** Follow-up on the same `feat/more-tag-icons` branch. The user wants an existing tag's icon to be editable. Work items already reference tags by `TagID`; changing the catalog entry must therefore update every task that uses the tag. No server or wire-key change is needed — the server stores the tag catalog verbatim and items keep the same ids.

## Scope

- **Targets affected:** commonMain, commonTest, jvmTest, androidHostTest, iosTest
- **Modules affected:** `packages:feature-projects-presentation`, `packages:design-system`, `shared-ui`
- **Branch:** continue on `feat/more-tag-icons`

## Files

- `packages/feature-projects-presentation/src/commonMain/kotlin/com/tasktracker/featureprojectspresentation/project/AdminProjectEditorModel.kt` — add validated `updateTag(id, name, color, icon)`, excluding the edited id from the duplicate check and treating a no-op as unchanged.
- `packages/feature-projects-presentation/src/commonMain/kotlin/com/tasktracker/featureprojectspresentation/project/ProjectTagsSection.kt` — add an Edit button per existing tag; selecting it loads the tag into the existing composer with Save replacing Add; extend the refusal check with the edited id.
- `shared-ui/src/commonMain/kotlin/com/tasktracker/sharedui/AdminProjectsDestination.kt` — pass `onUpdate = model::updateTag`.
- `packages/design-system/src/commonMain/composeResources/values/strings.xml` — add tag edit/save labels and update the tags body copy to say tags can be edited.
- `packages/feature-projects-presentation/src/commonTest/.../AdminProjectEditorModelTest.kt` — cover update validation, dirty state, save, and ID-based propagation to task tag references.
- `packages/feature-projects-presentation/src/commonTest/.../ProjectEditorScenarios.kt` — UI scenario for editing a tag's icon through the composer.
- `packages/feature-projects-presentation/src/{jvmTest,androidHostTest,iosTest}/.../ProjectsUi*Test.kt` — register the new UI scenario on all three hosts.

## Contract

- [x] An admin can select an existing tag, change its icon in the same composer used for adding tags, and save the change.
- [x] The tag keeps its `TagID`, so every work item that already references it renders the new icon without being edited or re-saved.
- [x] Editing a tag keeps the name valid and unique against the other tags; the maximum-catalog rule does not block an edit.
- [x] Choosing the values a tag already has leaves `hasChanges` false.
- [x] No changes to server files or wire keys.

## Non-goal

- [ ] No inline second icon picker; the existing composer is the single editor for name, colour and icon.
- [ ] No migration of stored work items: they keep their tag ids.

## Validation

- `scripts/check.sh quick :packages:feature-projects-presentation 'AdminProjectEditorModelTest'`
- `scripts/check.sh quick :packages:feature-projects-presentation 'ProjectsUiJvmTest.theTagCatalogSectionEditsAnExistingIcon'`
- `scripts/check.sh full` — rendering change; regenerate `test-baseline.json` only via `python3 scripts/test-baseline.py` if counts move.

## Handoff

- **Done:** Existing tags can be edited through the add composer, with Save replacing Add; the edit keeps the tag id, so all work items referencing it resolve to the new icon. Model, UI and propagation tests added; full matrix gate green after baseline regeneration.
- **Pending:** Retire this spec.
- **Next step:** Merge `feat/more-tag-icons` after review.
- **Gotchas:** `TagRules.normaliseName` trims and caps but does not change case; `"bug"` is a different name from `"Bug"`. A no-op test must use whitespace trimming. The duplicate check excludes the edited id and the catalog cap is skipped for edits.

## Risks / notes

- A duplicate check that forgets to exclude the edited id would make every no-op rename look invalid.
- A duplicate test tag between the composer and a second picker is avoided by keeping only one icon picker.
