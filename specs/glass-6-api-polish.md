# glass-6 — Glass API polish: dedupe buttons, kill dead code, name the numbers

- **status:** pending
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

- **Done:** to fill at implementation.
- **Pending:** to fill at implementation.
- **Next step:** glass-7 stays pending-decision.
- **Gotchas:** `ttGlassAddButton`'s `"+"` text path is used by the splash and
  previews; keep the text variant alive behind the defaulted parameter rather
  than migrating call sites in this task.

## Risks / notes

- Grep the whole repo (`shared-ui`, `packages/*`) for `ttGlassAddButton` before
  changing its signature — defaulted-only additions are safe, but the internal
  dedupe must not change modifier *order* (alpha → surface → clickable), which
  affects hit-testing and the disabled alpha's effect on the rim.
