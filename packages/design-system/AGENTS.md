# AGENTS.md — `:packages:design-system`

Scoping: everything in the root `AGENTS.md` still applies. This file holds only rules
that are true here and false elsewhere.

- **This is the only module that configures `compose.resources`.** Shared strings,
  images, and fonts live here; feature modules consume `Res.*`, never declare their own.
- **No Material3.** All surfaces bind to this module's own tokens and modifiers
  (e.g. `Modifier.liquidGlassCard()`). Do not recreate blur, gradients, or rims in a
  feature — add the missing shared primitive here and document the measurement behind
  any non-obvious value.
- **Pixel vs structural tests:** if the property survives being read off the modifier or
  semantics tree, assert structurally. Render pixels only for ink contrast, compositing
  under translucent layers, silhouettes, blurs, and hairlines — and gate those with
  `thisHostRasterisesText()` (the `expect` in `commonTest`, actuals per host), or they
  fail on the Android host. See the `rendered-ui-measurement` skill.
