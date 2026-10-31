# glass-search-field — add the shared liquid-glass search field

- **status:** done — validated on all three hosts (`scripts/check.sh full` green), reviewed
  inline (no `test-reviewer` agent exists in this repo), ready to merge
- **priority:** P1
- **plan:** `none` — standalone work. This is the prerequisite for
  [`work-item-search.md`](work-item-search.md): land this first so the feature consumes one
  field primitive instead of each screen spelling its own pill.

## Scope

- `:packages:design-system` only. A search field is a visual primitive, so its surface,
  glyphs, spacing, and clear affordance live here — not in a feature screen.
- Behaviour is out of scope: filtering, the debounce, and the no-results copy are
  `work-item-search.md`. This task delivers a controlled field that reports every edit and
  does not own the query.

## Files

Proposed Phase 1 plan — confirm or re-`ACK` before implementation.

- `packages/design-system/src/commonMain/kotlin/com/tasktracker/designsystem/SearchField.kt`
  (new) — `@Composable fun ttSearchField(...)` plus its private preview. One public
  composable, named after the file.
- `packages/design-system/src/commonMain/kotlin/com/tasktracker/designsystem/TabIcons.kt` —
  add `ttActionIcons.search` beside `add`/`remove`/`close`.
- `packages/design-system/src/commonMain/composeResources/values/strings.xml` — add
  `work_item_search_clear` ("Clear search"). The placeholder and no-results copy belong to
  the feature spec.
- `packages/design-system/src/commonTest/kotlin/com/tasktracker/designsystem/SearchFieldTest.kt`
  (new) — structural: value/hint/clear visibility, tag routing, disabled and focus states.
- `packages/design-system/src/commonTest/kotlin/com/tasktracker/designsystem/TabIconsRenderTest.kt`
  — one silhouette case for the new glyph (reuse the existing render harness).

## Contract

- [x] `ttSearchField` is the app's only search-field implementation. It applies
      `ttGlassSurface(shape = RoundedCornerShape(ttRadius.control))`, takes at least
      `ttSpacing.fieldMinHeight`, and spends `ttSpacing.xl` horizontally on the text — the
      surface-before-padding order `ttTextField` documents, so the visible pill is the
      control's real height.
- [x] No new visual token is introduced: the field reuses `ttRadius.control`,
      `ttSpacing.fieldMinHeight/xl/md/sm/xs`, `ttLabel()`, `ttLabelSecondary()`,
      `ttAccent()`, and `ttGlass.DISABLED_ALPHA`.
- [x] The leading magnifier is decorative (`contentDescription = null`, tinted
      `ttLabelSecondary`) and the hint is drawn only while `value.isEmpty()`.
- [x] The clear affordance is shown **only** when `value.isNotEmpty()`: a `Box` clipped to
      `ttRadius.control` with `clickable(role = Role.Button, onClickLabel = <clear string>)`,
      `ttSpacing.xs` padding, and `ttActionIcons.close` at `ttIconSize.close` — the
      `MemberPicker` pill-dismiss pattern. Activating it delivers `onValueChange("")`.
      (A 44dp `ttGlassIconButton` inside a 48dp field is all rim; do not nest a second
      glass circle.)
- [x] `fieldModifier` reaches the `BasicTextField` and `modifier` reaches the pill, matching
      `ttTextField`. Tests type and tag through `fieldModifier`; a tag moved to the pill
      silently retargets `performTextInput`.
- [x] The field is controlled: every keystroke calls `onValueChange` immediately, the hint
      follows `value`, and the composable holds no authoritative query. The IME action is
      `ImeAction.Search`; `keyboardActions` is a caller parameter so a screen can dismiss
      focus on submit.
- [x] Focus draws the same accent ring and 6% accent wash as `ttTextField` from the same
      tokens; `enabled = false` applies `ttGlass.DISABLED_ALPHA` and ignores input.
- [x] `ttActionIcons.search` is a 24-unit magnifier in the 4..20 span of the existing action
      glyphs: an outer and inner lens contour filled even-odd to make the ring, plus a
      diagonal handle. The silhouette test fails if the fill is dropped or the two contours
      cancel — `ImageVector.Builder.addPath` silently renders nothing without a `Brush` fill,
      and two identical contours cancel under even-odd just as silently. A *missing* inner
      contour (a solid disc) paints ink and is caught by
      `TabIconsGeometryTest.the search glyph's ring is a ring` instead, which counts contours
      and compares their widths; neither test sees both faults, so both exist.
- [x] Mutation check: making the clear affordance unconditional turns the empty-value case
      red; deleting the inner lens contour turns the geometry case red; the debounce-free
      glyph that shipped in the working tree turned the silhouette case red (it drew zero
      ink).
- [x] RTL: the glyph, text, and clear control use logical start/end placement, so the field
      mirrors without a separate layout.

## Non-goal

- [x] No filtering, debounce, empty-state copy, or screen wiring — that is
      [`work-item-search.md`](work-item-search.md).
- [x] No change to `ttTextField`'s public shape or geometry. The two fields share tokens,
      not an implementation.
- [x] No `Material3`, no new icon resource file, no per-feature copy of the pill.

## Validation

- Run: `./gradlew --offline --no-parallel :packages:design-system:jvmTest --tests
  'com.tasktracker.designsystem.SearchFieldTest' --tests
  'com.tasktracker.designsystem.TabIconsRenderTest'` — focused, under a minute.
- Hosts: the glyph silhouette is a rendered-pixel claim, so it must be checked on both
  rasterising hosts before merge (JVM and the iOS simulator). The Android host records the
  render case as a skip through the existing `rasterising {}` seam — skipped +1, tests
  unchanged, matching the baseline's current skip column. Name the iOS run as pending in
  the Handoff if it is not run locally.
- Counts: `SearchFieldTest` in `commonTest` moves all three host totals by one per case.
  If it needs a Robolectric entry point it follows the `ResourceStringsScenarios` split —
  bodies in `commonTest`, one thin entry point per host.
- The silhouette test is pixel-reading: pass it through the `test-reviewer` subagent and
  perform the mutation ritual in `AGENTS.md` §4 before the PR.

## Handoff

- **Done:** `ttSearchField` (`SearchField.kt`), `ttActionIcons.search` (`TabIcons.kt`), the
  `work_item_search_clear` string, `SearchFieldTest`, a silhouette case in
  `TabIconsRenderTest`, and a `the search glyph's ring is a ring` case in
  `TabIconsGeometryTest`. Focused JVM run is green: `SearchFieldTest`,
  `TabIconsRenderTest`, `TabIconsGeometryTest` (plus the module's full `jvmTest`).
- **Done, but not as the file list says:** the focus treatment is shared with `ttTextField`
  rather than copied — `FocusWashAlpha` and `FocusRingWidth` moved from `private` to
  `internal` in `TextField.kt` and `SearchField.kt` uses them. Public shape and geometry of
  `ttTextField` are unchanged; two private literals that happen to agree is how the two
  fields end up in different focus states.
- **Verified on all three hosts:** `:packages:design-system:jvmTest` and
  `:packages:design-system:iosSimulatorArm64Test` are green with `SearchFieldTest` at
  `tests=7 skipped=0 failures=0` — the structural cases actually run on iOS, which is the
  point of the host guard, not a formality. `:packages:design-system:testAndroidHostTest` is
  green too, with `SearchFieldTest` `tests=7 skipped=7` and `TabIconsRenderTest` skipped: the
  Robolectric Android host cannot rasterise text or pixels, so
  `assumeHostRasterisesText()` is what keeps that run honest instead of falsely red.
  `TabIconsGeometryTest` runs all 13 cases on every host, including
  `the search glyph's ring is a ring` — geometry is host-independent, so the *solid-disc*
  failure mode is caught everywhere while the *invisible-glyph* one is caught only where
  pixels exist (JVM + iOS).
- **Review pass — done inline, because there is no `test-reviewer` here.** The spec asked for
  it, but this repo has no such subagent (`find -iname '*test-reviewer*'` is empty; `.agents/`
  holds only a `rendered-ui-measurement` skill). Rather than skip the step, it was performed
  against AGENTS.md §3–§4 by hand, and it found three things:
  1. **A false comment, now corrected.** Both the geometry test's header and an earlier draft
     of this handoff claimed `TabIconsGeometryTest` cannot see the cancelling mistake. It can:
     equal contours have equal widths, so `hole.width < outer.width` fires. Verified by
     setting `searchRingStroke` to zero — geometry fails with "the lens's inner contour is as
     wide as the ring's outer one", while the ink probe fails with "painted nothing". What the
     ink probe *uniquely* adds is the no-fill-brush cause, which path data cannot show.
  2. **A misleading assertion message, now corrected.** The ink probe said the cause was "no
     fill brush", which is wrong for a cancellation bug. It now names both causes and says the
     count is the fact while the cause is for the reader to find.
  3. **The §4 mutation was not previously demonstrated for `search`.** It was demonstrated for
     the tab glyphs, but `search` was only *listed* in the probe. Both directions are now
     measured: dropping the inner contour reddens geometry only (`expected:<3> but was:<2>`,
     ink stays green because a disc still paints); zeroing the stroke reddens both.
- **Gate:** `scripts/check.sh full` is green — `jvmTest` 2132, `iosSimulatorArm64Test` 2070,
  `testAndroidHostTest` 1764, **0 failed / 0 errors** on all three hosts, plus
  `verifyArchitectureGraph`, `verifyModuleImports` and the Android compile.
  `test-baseline.json` has been regenerated from that run (the committed file was stale by 49
  tests per host: the 30 added here and in the feature spec, plus 19 that had drifted before
  this work) and `scripts/check-baseline.sh` now passes with exit 0.
- **Next step:** none outstanding for this spec. Commit is the user's call — the working tree
  holds this primitive plus the feature that consumes it
  ([`work-item-search.md`](work-item-search.md)).
- **Gotchas:**
  - The first implementation of the glyph drew **nothing**: the outer and inner lens contours
    were identical rounded squares, so even-odd cancelled them, and the handle was a bare
    `moveTo`/`lineTo` (zero area, so a filled path paints nothing either). Both guards catch
    that, and the split between them is narrower than an earlier draft of this note claimed —
    it was measured, not assumed. Setting `searchRingStroke` to zero makes the two contours
    identical *and* the handle zero-width: `TabIconsRenderTest` fails with
    `ttTabIcons.'search' painted nothing: 0 ink pixels`, and `TabIconsGeometryTest` fails with
    "the lens's inner contour is as wide as the ring's outer one", because equal contours have
    equal widths. What the ink probe uniquely adds is the cause path data cannot show — a path
    added with **no fill brush**, which draws nothing while every coordinate stays correct, and
    which is what made every tab glyph invisible in the first place. What geometry uniquely
    adds is the *solid disc* (a dropped inner contour still paints ink, so the probe passes).
    Mutation-checked in both directions: deleting the inner contour reddens geometry only,
    zeroing the stroke reddens both.
  - `BasicTextField` inside the row must use `weight(1f)`, not `fillMaxWidth()`. A filled
    field takes the pill's whole width and lays the clear cross out past the right rim: it
    stays in the tree and is clipped and untappable. `ttTextField` has no siblings, so it does
    not have this problem and cannot be copied blindly.
  - The hint is drawn inside `decorationBox`, so the field's merged semantics swallow it —
    a test that looks for `TTSearchFieldHintTag` needs `useUnmergedTree = true`.
  - The clear glyph carries `contentDescription = null`; the clickable box's `onClickLabel`
    is the label, matching `MemberPicker`'s pill dismiss. Two descriptions would be announced
    twice.

## Risks / notes

- **Nested glass.** The field is already a translucent surface. A second glass circle for
  "clear" inside it reads as two rims and loses the 48dp target; the inline 12dp cross is
  the established dismiss pattern and keeps the tap target by padding, not by elevation.
- **The icon builder's silent blank.** `addPath` with no `Brush` fill, or an even-odd ring
  whose inner contour is in the same direction, renders nothing while compiling cleanly.
  The silhouette test is the only thing that catches it.
- **Contrast.** Text and hint are `ttLabel()`/`ttLabelSecondary()` on the same glass as
  `ttTextField`, so the existing glass-vs-label contrast coverage applies. If the clear
  glyph measures under 3:1 in light appearance, change the tint token, not a literal colour.
