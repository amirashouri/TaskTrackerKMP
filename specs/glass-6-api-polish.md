# glass-6 — Glass API polish: dedupe buttons, kill dead code, name the numbers

- **status:** done
- **priority:** P3
- **plan:** no `docs/PLAN.md` entry — standalone design-system hardening sprint
  (`specs/glass-1..7`). No wire-format change; Fast Path per workspace AGENTS.md.

## Scope

- `:packages:design-system` — public-surface cleanup inside the glass file.
  Behaviour-preserving except where a contract line says otherwise.

## Files

- `packages/design-system/src/commonMain/kotlin/com/tasktracker/designsystem/Glass.kt`
  - `ttGlassAddButton` gains `enabled: Boolean = true` (it is the only glass
    control without it) and gains `image: ImageVector?` alongside the legacy
    `"+"` text so it can share the icon path of `ttGlassIconButton`.
  - Extract the shared Box skeleton (size, surface, disabled alpha, clickable,
    semantics) of `ttGlassAddButton`/`ttGlassIconButton` into one private
    composable; both public signatures survive.
  - Remove the no-op `.background(Color.Transparent)` and
    `.pointerInput(Unit) { }` chains from both buttons.
  - Remove `Segment`'s `compact` parameter (private, no caller passes true) — or
    wire it, if a caller surfaces during the work; prefer removal.
  - Move `velocityThreshold = 500.dp` and `maxBlurRadius = 8.dp` into the
    `ttGlass` token table (`LENS_VELOCITY_THRESHOLD_DP`, `LENS_MAX_BLUR_DP`).
- `packages/design-system/src/jvmTest/kotlin/com/tasktracker/designsystem/` —
  constants assertions for the two new tokens; structural tests for the add
  button's `enabled` behaviour (alpha `DISABLED_ALPHA`, click disabled).

## Contract

- [ ] `ttGlassAddButton(enabled = false)` renders at `ttGlass.DISABLED_ALPHA`
      and does not fire `onClick`.
- [ ] Both buttons render identical pixel output to before when enabled
      (existing render/geometry tests pass unmodified).
- [ ] No call sites outside the package break: public signatures only gain
      defaulted parameters.
- [ ] The two new lens tokens equal the previous inline literals, and a
      constants test pins them.

## Non-goal

- [ ] No new button variants, no change to `TTGlassButtonStyle`, and no
      deprecation of any public symbol (the deprecated `ttGlass.FILL_ALPHA`
      removal is spec 9.2's, not this).

## Validation

- Run: `./gradlew --offline :packages:design-system:jvmTest --tests '*Glass*' --tests '*Button*'`
- Counts: +2–3 tests (enabled add button, constants); pixel baselines must not
  move.

## Handoff

- **Done:** `ttGlassAddButton` gained `enabled: Boolean = true` and
  `image: ImageVector? = null` (the `+` stays the default, so the text variant is
  untouched); it and `ttGlassIconButton` now draw through one private
  `GlassRoundAction` — size, surface, disabled dim, click, description — with the
  no-op `.background(Color.Transparent)` and `.pointerInput(Unit) { }` chains
  dropped from both and the now-unused `pointerInput` import removed;
  `Segment`'s `compact` parameter is gone (its only caller never passed it);
  `ttGlassLens`'s inline `500.dp`/`8.dp` are now
  `ttGlass.LENS_VELOCITY_THRESHOLD_DP`/`LENS_MAX_BLUR_DP`. Test:
  `jvmTest/GlassApiPolishTest.kt` (3 tests) — both tokens pinned exactly; the
  enable/disable pair asserted in both directions (semantics *and* click count,
  because "the disabled one does not fire" passes against a button that never
  fires); and the disabled alpha as an identity rather than a colour:
  `disabled == 0.4 * enabled + 0.6 * backdrop` at the median face of each button,
  measured over a black backdrop (over the light canvas this button's white wash
  hides a wrong alpha completely — the backdrop probe asserts the black is where
  the sample thinks it is). Measured: one colour step off, `18/255` against the
  arithmetic's `19/255`, i.e. the frame's 8-bit rounding; tolerance is the
  module's standing five steps. Mutations: dropping the dim fails with "nothing
  was dimmed at all", and making the click ignore the flag fails the semantics
  assertion.
- **Pending:** iOS and Android host runs of the new test are release-gate items.
  "Both buttons render identical pixels when enabled" is proven by construction
  and by the suites, not by a rasterising test: the two removals draw nothing,
  and the surviving `.alpha(if (enabled) 1f else …)` is a no-op at 1f
  (`Modifier.alpha` returns its receiver untouched — read out of the 1.12.1 `ui`
  bytecode, since the alternative would have added an empty `graphicsLayer`,
  which this repo has measured darkening a backdrop by 0.14 of relative
  luminance). Nothing in the repo renders either button in a pixel test, so there
  is no baseline that could have moved. Verified green: `:packages:design-system`
  (182), `:shared-ui` (126), and the four feature modules that host the icon
  button — projects (374), settings (68), teams (36), auth (20) — 806 tests, none
  edited.
- **Next step:** glass-7 **decided and closed**: backdrop blur is a deliberate
  non-goal (option 3 of that spec). The painted material is what this product
  ships — it passes its contrast and pixel tests, and the two alternatives were a
  third-party dependency inside the one package every module compiles against, or a
  UIKit/RenderEffect interop that replaces the Compose-drawn rim on iOS and needs a
  host to verify. Per its own instruction the spec was deleted; the decision is
  recorded here so it is not re-litigated from a file that no longer exists. The
  sprint is closed.
- **Gotchas:** `ttGlassAddButton` has **no call sites anywhere in the repo** — not
  the splash, not `Previews.kt`, which is what this spec's note assumed — so the
  new `enabled` and `image` paths are exercised only by the new test. It stays
  public and undeprecated. Separately: glass-3's contract line "assert the new
  token values" was never honoured — nothing in `commonTest`/`jvmTest` reads
  `ttGlass.DISPERSION_ALPHA` or `DISPERSION_GREEN_MULTIPLIER` — and its spec is
  marked done; this file's constants test is the pattern to reuse if that is
  picked up.

## Risks / notes

- Grep the whole repo (`shared-ui`, `packages/*`) for `ttGlassAddButton` before
  changing its signature — defaulted-only additions are safe, but the internal
  dedupe must not change modifier *order* (alpha → surface → clickable), which
  affects hit-testing and the disabled alpha's effect on the rim.
