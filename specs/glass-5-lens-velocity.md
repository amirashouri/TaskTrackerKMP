# glass-5 — Lens velocity: measure it per frame, not per recomposition

- **status:** pending
- **priority:** P2
- **plan:** no `docs/PLAN.md` entry — standalone design-system hardening sprint
  (`specs/glass-1..7`). No wire-format change; Fast Path per workspace AGENTS.md.

## Scope

- `:packages:design-system` — `ttLensPlacement`'s velocity estimate
  (`Glass.kt`, the `VelocityTracker` class sampling `lensX` and
  `currentNanoTime()` during composition).
- Recomposition timing is not frame timing: the delta/dt pair is noisy and can
  spike when recomposition is batched, producing a wrong-direction trail or a
  surprise full-strength blur. The velocity should come from the animation
  itself — per-frame, deterministic.

## Files

- `packages/design-system/src/commonMain/kotlin/com/tasktracker/designsystem/Glass.kt`
  — derive velocity from the running animation: drive the lens position with
  `Animatable` + `LaunchedEffect` (or read `AnimationState` per frame) so the
  per-frame velocity is the spring's own `velocity`, converted to the
  `Dp`-per-second shape `LensPlacement.velocityX` already exposes.
- `packages/design-system/src/jvmTest/kotlin/com/tasktracker/designsystem/GlassVelocityTest.kt`
  (new) — with a fake frame clock: velocity is 0 at rest; during travel it has
  the correct sign and grows with travel distance; it decays to ~0 as the spring
  settles (the existing `TAB_LENS_SETTLE_MILLIS` window stays valid).

## Contract

- [ ] At rest, `velocityX` is 0 (no phantom trail on first composition).
- [ ] During travel, velocity sign matches travel direction and magnitude
      scales with distance covered per frame.
- [ ] Velocity settles to ~0 within the existing settle window; the lens
      visually behaves as before at rest and after settling.
- [ ] The public `LensPlacement` shape is unchanged (callers in `TabBar`-style
      hosts keep compiling).

## Non-goal

- [ ] No change to the trail's rendering math in `ttGlassLens` (threshold,
      max blur radius — those tokens move in glass-6, not here).

## Validation

- Run: `./gradlew --offline :packages:design-system:jvmTest --tests '*GlassVelocity*' --tests '*TabLens*'`
- Counts: +1 test file (jvm); existing tab-lens tests must pass unmodified
  (they pin geometry/contrast at rest).

## Handoff

- **Done:** to fill at implementation.
- **Pending:** to fill at implementation.
- **Next step:** implement glass-6.
- **Gotchas:** `ttLensPlacement` returns Dp-position values the pickers place
  with `.offset()` — switching to `Animatable` must not move the lens into a
  `graphicsLayer` translation in this task; that is a layout-behaviour change
  out of scope.

## Risks / notes

- `runTest` with a fake `MonotonicFrameClock` is the only way to make spring
  velocity deterministic on JVM — do not wall-clock this test.
