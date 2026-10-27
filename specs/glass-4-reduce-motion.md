# glass-4 — Reduce Motion: a composition local that stills the glass

- **status:** pending
- **priority:** P2
- **plan:** no `docs/PLAN.md` entry — standalone design-system hardening sprint
  (`specs/glass-1..7`). No wire-format change; Fast Path per workspace AGENTS.md.

## Scope

- `:packages:design-system` — a `LocalReducedMotion` composition local
  (default `false`, matching `LocalGlassIntensity`'s default-to-full pattern)
  read by the three motion sources in the glass material:
  1. the tilt parallax specular (roll forced to 0),
  2. the press scale (`ttGlassButton` scale animation),
  3. the selection lens spring (`ttLensPlacement` / `ttSelectionWash`).
- The default must leave every existing test and preview pixel-identical.
- App-shell adoption (reading the platform Reduce Motion switch into the local)
  is follow-up work outside the package; recorded in the handoff.

## Files

- `packages/design-system/src/commonMain/kotlin/com/tasktracker/designsystem/ReducedMotion.kt`
  (new) — `LocalReducedMotion` with the same doc discipline as
  `GlassIntensity.kt`: why the default is `false`, why it lives in composition,
  and that `true` means *less motion*, not *no animation* (transitions become
  `snap()`, nothing pops).
- `packages/design-system/src/commonMain/kotlin/com/tasktracker/designsystem/Glass.kt`
  — `ttGlassSurface` zeroes the roll when reduced; `ttGlassButton` snaps the
  press scale; `ttLensPlacement` snaps the lens spring; `ttSelectionWash` snaps
  the wash.
- `packages/design-system/src/jvmTest/kotlin/com/tasktracker/designsystem/ReducedMotionTest.kt`
  (new) — with `LocalReducedMotion` providing `true`: specular offset is 0 for
  any roll, press-scale target is 1f, lens placement lands without spring travel.

## Contract

- [ ] `LocalReducedMotion` defaults to `false`; every existing glass test passes
      unmodified.
- [ ] With `true`, the specular offset is 0 regardless of the motion state.
- [ ] With `true`, `ttGlassButton`'s scale animation never leaves 1f on press.
- [ ] With `true`, lens/wash movement still *reaches* its target (snap, not
      frozen).
- [ ] No change to `LocalGlassIntensity` semantics; the two locals compose
      independently (a user can have full transparency + reduced motion).

## Non-goal

- [ ] No platform setting is auto-detected inside the package (shell's job), and
      no non-glass animation in other packages is touched.

## Validation

- Run: `./gradlew --offline :packages:design-system:jvmTest --tests '*Glass*' --tests '*ReducedMotion*'`
- Counts: +1 test file (jvm); `test-baseline.json` moves accordingly at release.

## Handoff

- **Done:** to fill at implementation.
- **Pending:** to fill at implementation.
- **Next step:** implement glass-5.
- **Gotchas:** `pressSpring`/`selectionSpring` are shared tokens — do not swap
  them globally; choose `snap()` at the read sites gated on the local.

## Risks / notes

- `animateFloatAsState`/`animateDpAsState` spec is decided once per
  composition of the caller — gating the spec on a composition local works, but
  a mid-composition flip of the local must be covered by the tests (spec swap
  takes effect without restart).
