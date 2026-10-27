# glass-7 — Backdrop blur: the real glass material (decision needed before any code)

- **status:** pending — **blocked on a product/tech decision, see below**
- **priority:** P3
- **plan:** no `docs/PLAN.md` entry — standalone design-system hardening sprint
  (`specs/glass-1..7`). No wire-format change; Fast Path per workspace AGENTS.md.

## The decision

The glass material today is painted gradients; nothing behind a surface is
sampled. Real liquid glass needs backdrop sampling, and the three options have
different costs:

1. **`haze` library** (Compose Multiplatform backdrop blur) — cross-platform
   from one API, but adds a third-party dependency to `design-system`, which is
   the package every other module compiles against. Dependency policy decision.
2. **Platform interop** — `UIVisualEffectView` on iOS (native blur + vibrancy,
   free Reduce-Transparency compliance), `RenderEffect` blur on Android 12+
   with a graceful no-op below. Most native result, most code, and the iOS path
   replaces the Compose-drawn rim with a UIKit composite unless carefully
   layered.
3. **Stay as-is** — the painted material passes its contrast and pixel tests and
   reads as glass; treat blur as a deliberate non-goal and close this spec.

No implementation starts until this is chosen. If option 3, delete this spec.

## Scope (only valid for options 1–2)

- `:packages:design-system` — one new modifier `ttGlassBackdrop` layered
  *under* the existing fill ramp, gated on `LocalGlassIntensity` (at intensity
  0 the backdrop is opaque and no sampling happens — Reduce Transparency must
  not pay blur cost).
- Performance budget: blur applies to a bounded layer (the glass surface's
  parent backdrop, not the window), and degrades to the painted material on
  targets where sampling is unavailable (jvm desktop without acceleration).

## Files

- to confirm at Phase 1 after the decision.

## Contract

- [ ] At `LocalGlassIntensity = 0f`, output is pixel-identical to today's
      material and no blur work is scheduled.
- [ ] At full intensity on iOS/Android, content behind a glass surface is
      visibly blurred through the fill (screenshot acceptance, `test-reviewer`
      pass required — this one is genuinely pixel-reading).
- [ ] All existing contrast tests still pass (the fill ramp and hairline sit on
      top of the backdrop unchanged).
- [ ] jvm/desktop fallback renders the painted material without error.

## Non-goal

- [ ] No displacement/refraction meshes, no dynamic lensing — blur only, this
      spec; refraction is a separate decision if blur lands.

## Validation

- jvm: focused glass suite unchanged; iOS/Android host runs are the release
  gate; screenshot acceptance recorded as pending until run on a host.

## Handoff

- **Done:** spec written; decision pending.
- **Pending:** the choice between haze / interop / none.
- **Next step:** ask the product owner; do not start implementation.
- **Gotchas:** `design-system` is a leaf dependency of everything — a
  third-party dependency here is a policy change for the whole repo, hence the
  gate.
