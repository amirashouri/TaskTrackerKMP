# AGENTS.md — `:packages:design-system`

Scoping: everything in the root `AGENTS.md` still applies. This file holds only rules
that are true here and false elsewhere.

## Core Asset and Token Ownership
- **This is the only module that configures `compose.resources`.** Shared strings,
  images, and fonts live here; feature modules consume `Res.*`, never declare their own.
- **No Material3.** All surfaces bind to this module's own tokens and modifiers. Do not recreate blur, gradients, or rims in a feature — add the missing shared primitive here and document the measurement behind any non-obvious value.

## Liquid Glass Design Primitive Constraints (CRITICAL)
- **Token Immobilization:** Do not guess or invent visual values. Liquid Glass layers depend on heavily saturated fluid background gradients, deep backdrop blurs, and glossy reflective borders that must strictly derive from immutable tokens in this package.
- **Visual Primitives Inventory:** If an agent or user task requests a styling variation not covered by existing modifiers (like `Modifier.liquidGlassCard()`), you must define the shared token configuration *inside your Phase 1 plan* before generating any layout arrangement code.
- **Rasterizer Compensation:** Liquid Glass parameters must be tuned to the absolute worst-performing host target (typically the iOS simulator's 85% stroke coverage constraint). Never tune a token solely to clear a JVM host threshold.

## Testing Rules and Execution ceilings
- **Pixel vs structural tests:** If the property survives being read off the modifier or semantics tree, assert structurally. Render pixels only for ink contrast, compositing under translucent layers, silhouettes, blurs, and hairlines.
- **Matrix Gating:** Every pixel measurement test must be gated via `rasterising { runComposeUiTest { ... } }` (leveraging the `thisHostRasterisesText()` split seams) or it will crash the Android host.
- **Sensitivity Probes:** Before accepting a green state on a new layout or token definition, you must execute the sensitivity probe outlined in `rendered-ui-measurement` (reverting the production token to matching ground color) to prove the test framework is sensitive to contrast failures.

## Layout Splitting Thresholds
- Follow the presentation file line rules explicitly. Any new custom component or visualization primitive added to expand the Liquid Glass ecosystem must land in its own isolated file named after the public composable, along with its specific previews and tag constants.
