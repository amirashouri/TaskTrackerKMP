# more-tag-icons — Add four tag icons to the manage-project picker

- **status:** done
- **priority:** P2
- **context:** User request: add server, marketing, data analysis, and design icons to the manage-project tag picker. "marketting" is normalised to Marketing. The server stores `icon` as an opaque client string (`TaskTrackerServer/docs/API.md`), so this is client-only.

## Scope

- **Targets affected:** commonMain, commonTest
- **Modules affected:** `packages:domain`, `packages:design-system`, `packages:feature-projects-presentation`
- **Branch:** `feat/more-tag-icons` off updated `origin/main`

## Files

- `packages/domain/src/commonMain/kotlin/com/tasktracker/domain/Entities.kt` — add `Server`, `Marketing`, `DataAnalysis`, `Design` to `TagIcon` with wire keys `server`, `marketing`, `dataanalysis`, `design`.
- `packages/design-system/src/commonMain/kotlin/com/tasktracker/designsystem/TagIcons.kt` — draw four glyphs on the shared 24-unit grid and append them to `ttTagIcons.all`.
- `packages/feature-projects-presentation/src/commonMain/kotlin/com/tasktracker/featureprojectspresentation/workitem/WorkItemTags.kt` — extend exhaustive `TagIcon.vector()`.
- `packages/feature-projects-presentation/src/commonTest/kotlin/com/tasktracker/featureprojectspresentation/AdminProjectEditorModelTest.kt` — cover add/round-trip of the new icon values.
- `packages/design-system/src/commonTest/kotlin/com/tasktracker/designsystem/TabIconsGeometryTest.kt` — existing generic geometry loops include `ttTagIcons.all`; no change expected.

## Contract

- [x] All four new `TagIcon` values parse from and serialise to their agreed wire keys.
- [x] Each new value maps to a non-empty glyph inside the 24-unit viewport; the existing geometry tests cover this through `ttTagIcons.all`.
- [x] The manage-project picker offers all twelve icons because it iterates `TagIcon.entries`; no picker code changes.
- [x] A tag using each new icon survives `AdminProjectEditorModel.addTag` and is stored with the chosen icon.
- [x] [ **KMP Constraint:** No platform-specific APIs introduced into `commonMain`.]

## Non-goal

- [ ] No server code or wire-key rename: the server accepts any `icon` string verbatim.
- [ ] No icon dependency, font, or traced SF Symbol.

## Validation

- `scripts/check.sh quick :packages:design-system 'TabIconsGeometryTest'`
- `scripts/check.sh quick :packages:feature-projects-presentation 'AdminProjectEditorModelTest'`
- `scripts/check.sh full` — required for the rendering/pixel gate; regenerate `test-baseline.json` only through the script if counts move.

## Handoff

- **Done:** Four icons added across domain, design-system and presentation; wire keys covered; full matrix gate green on JVM, iOS simulator and Android host after regenerating `test-baseline.json`.
- **Pending:** Retire this spec; no code work remains.
- **Next step:** Merge `feat/more-tag-icons` after review.
- **Gotchas:** `wireValue` comes from the lowercased enum name, so `DataAnalysis` is `dataanalysis` on the wire. The server stores `icon` verbatim, so no server change was needed. All tag glyphs share one `EvenOdd` path: contours must abut or leave a gap.

## Risks / notes

- Hand-drawn glyphs can compile while drawing nothing; the geometry test's non-empty-node and viewport assertions are the guard, and the exhaustive `when` makes a missing mapping a compile error.
- No rendered-pixel test is added: the new drawings are covered by the existing hand-drawn geometry policy and the full rendering gate.
