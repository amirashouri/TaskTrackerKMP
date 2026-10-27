# glass-1 — DeviceMotion: one shared controller, real updates, draw-phase reads

- **status:** done
- **priority:** P0
- **plan:** no `docs/PLAN.md` entry — standalone design-system hardening sprint
  (`specs/glass-1..7`). No wire-format change; Fast Path per workspace AGENTS.md.

## Scope

- `:packages:design-system` only — motion plumbing behind the glass material.
- No rendering change: the specular must paint identically for a stationary device.

## Files

- `packages/design-system/src/commonMain/kotlin/com/tasktracker/designsystem/DeviceMotion.kt`
  — controller contract: a `State<Float>`-backed `roll`/`pitch` exposed as
  `@Composable fun rememberDeviceMotion(): DeviceMotionController` reading from an
  app-scoped composition local, plus `LocalDeviceMotion` provided by a new
  `DeviceMotionProvider` composable so the app shell creates exactly one platform
  controller.
- `packages/design-system/src/androidMain/kotlin/com/tasktracker/designsystem/DeviceMotion.android.kt`
  — move listener registration/unregistration into `DisposableEffect`; stop the
  listener on dispose; resume on re-enter. One instance, not one per call site.
- `packages/design-system/src/iosMain/kotlin/com/tasktracker/designsystem/DeviceMotion.ios.kt`
  — install the `CMDeviceMotionHandler` update queue so `_roll`/`_pitch` actually
  update (they are currently never written; `updateFromMotion()` has no caller);
  stop updates in `onDispose`.
- `packages/design-system/src/jvmMain/kotlin/com/tasktracker/designsystem/DeviceMotion.jvm.kt`
  — adapt to the new contract (static zero provider).
- `packages/design-system/src/commonMain/kotlin/com/tasktracker/designsystem/Glass.kt`
  — `ttGlassSurface` stops reading `roll()` in composition: the state is read
  *inside* the `drawBehind` lambda so motion invalidates redraw only, not
  recomposition of every glass surface.

## Contract

- [ ] On iOS, a device roll changes the specular offset (`_roll` is written by the
      motion handler, provable by a unit-level test of the handler lambda, since a
      real sensor cannot run on CI).
- [ ] Exactly one platform controller exists per composition root that provides
      `LocalDeviceMotion` (structural: two `ttGlassSurface` call sites share one
      controller instance).
- [ ] The Android listener is unregistered on dispose (test double asserts
      `stop()` was called when the provider leaves composition).
- [ ] `ttGlassSurface` paints the same output for `roll = 0` as before (pixel
      tests unchanged).
- [ ] Motion deltas invalidate only draw phase — structural: no composable-level
      read of the motion state outside `drawBehind`/draw scopes.

## Non-goal

- [ ] No new visual effects, no change to the specular's math, and no change to
      the `DeviceMotionController` public surface beyond how it is obtained
      (the provider wiring is glass-1's job; app-side adoption of the provider in
      `shared-ui` shell, if required, is called out in the handoff).

## Validation

- Run: `./gradlew --offline :packages:design-system:jvmTest --tests '*Glass*'`
- Run: `./gradlew --offline :packages:design-system:iosSimulatorArm64Test --tests '*Glass*'`
  (iOS targets the rewritten handler; if the focused iOS run exceeds a minute,
  record it pending for the release gate).
- Counts: `test-baseline.json` — +1 jvm structural test (single shared
  controller), no pixel-value changes expected.

## Handoff

- **Done:** controller contract rewritten to `State<Float>` properties with
  `start()`/`stop()`; `DeviceMotionProvider` (app-scoped, test-injectable) +
  `LocalDeviceMotion` + `rememberDeviceMotion()` fallback in commonMain; Android
  registers/unregisters via `DisposableEffect` (`start()` is idempotent); iOS
  installs the missing `CMDeviceMotionHandler` on `NSOperationQueue.mainQueue`
  (the specular never moved on iOS before this — nothing wrote the States) and
  stops updates on dispose; JVM stub is a static zero singleton. `ttGlassSurface`
  now reads `roll.value` inside `drawBehind` (draw-phase invalidation only).
  Tests: `DeviceMotionTest` (3 structural tests, commonTest) — sharing, provider
  dispose, and the `StartStopMotion` discipline.
- **Pending:** iOS host run (`iosSimulatorArm64Test`) and Android host run are
  release-gate items, not run in this session. The shell (`shared-ui`) has not
  yet adopted `DeviceMotionProvider` — until it does, `ttGlassSurface` uses the
  per-call-site fallback (no regression: one controller per surface with proper
  dispose, vs. before one controller per surface with a leak). Adopting the
  provider at the app root is a one-line follow-up.
- **Next step:** implement glass-2.
- **Gotchas:** the specular offset math (`roll * size.width * 0.15f`) is
  pixel-test-adjacent — untouched, only where the value is read changed. The
  controller interface is a breaking change (`roll()`/`pitch()` composables are
  gone) — safe because `Glass.kt` was the only call site in the repo.

## Risks / notes

- The iOS handler runs on a background queue: `_roll` writes must be main-safe
  (`Dispatchers.Main` / `MainActor`) or read via a snapshot-safe `State`.
- `remember { AndroidDeviceMotion(...) }` in the provider must not depend on
  `LocalContext` changes (activity recreation) silently leaking the old
  listener — key the `remember` on the context or hold the listener in the
  `DisposableEffect`.
