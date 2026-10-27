# glass-5 — Lens velocity: measure it per frame, not per recomposition

- **status:** done
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

- **Done:** `ttLensPlacement` drives the lens with four `Animatable`s and reports
  `animX.velocity` as `velocityX`. Two things beyond the swap were needed to
  satisfy the contract: the animatables are created only once the selected
  segment has a rectangle, and they are created *at* that target, so the frame
  the lens first has bounds is its resting state (`remember(measured)`); and the
  velocity is no longer divided by the density — the animatable holds dp, so its
  velocity already is dp/s, and dividing scaled the trail's 500.dp threshold with
  the display. Test: `jvmTest/GlassVelocityTest.kt` (5 tests) steps a stopped
  `mainClock` one frame at a time — `runComposeUiTest`'s clock is the fake
  `MonotonicFrameClock`, so nothing here is wall-clocked: at rest on the frame the
  lens appears (and on the five after it), sign follows direction both ways,
  velocity scales with distance (three times the travel, three times the peak),
  settled inside `TAB_LENS_SETTLE_MILLIS` (3% of peak measured at the mark, at
  rest on target by the end), and reduced motion snaps without a velocity.
  Verified: `:packages:design-system:jvmTest --tests '*GlassVelocity*' --tests
  '*TabLens*'` green, the whole design-system `jvmTest` (179 tests) green with
  `TabLensContrastTest`/`TabLensCornerTest` unmodified, and `:shared-ui` plus the
  three feature modules that host the pickers (`feature-teams-presentation`,
  `feature-settings-presentation`, `feature-projects-presentation`) green.
- **Pending:** iOS and Android host runs of the new test are release-gate items,
  not run here (it renders nothing, so the only difference between hosts would be
  the frame grid). `currentNanoTime` — `TimeSource.kt` and its three actuals — is
  now unused: it existed only for the composition-timing estimate this task
  removed, and its KDoc still claims `ttLensPlacement` uses it. Deletion is code
  removal, so it needs an ACK; it is not done here.
- **Next step:** implement glass-6.
- **Gotchas:** the unit change is visible on real hardware — the trail now appears
  at the documented 500 dp/s on every display, where a 3× screen previously
  needed three times that speed. Intended, and the trail's threshold was always
  written as dp/s, but worth knowing before someone reports the trail as
  "suddenly appearing". Mutation-checked, per §4: restoring `Animatable(0f)` fails
  the appearance test (17.6 dp travelled on the second frame) and negating the
  velocity fails the direction test. `ttLensPlacement` still returns Dp positions
  the pickers place with `.offset()` — the lens was not moved into a
  `graphicsLayer` translation.

## Risks / notes

- `runTest` with a fake `MonotonicFrameClock` is the only way to make spring
  velocity deterministic on JVM — do not wall-clock this test.
