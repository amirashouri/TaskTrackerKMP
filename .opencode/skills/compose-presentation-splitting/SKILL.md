---
name: compose-presentation-splitting
description: Use when adding or changing Compose UI layouts, screens, rows, previews, or components within any *-presentation module or the :shared-ui module. Enforces the repository's strict file splitting and architectural reuse limits.
related:
  - kmp-architecture-workflow
---

# Presentation Architecture and Compose Splitting Primitives

Presentation structures scale by strict decomposition rather than incremental code appends. Feature layout files are bound by layout boundaries, line budgets, and strict preview encapsulation.

## The Architectural Line Budgets
- **One Composable, One File:** The functional unit of a presentation package is **one public composable**. The file must be named identically to that composable (e.g., `WorkItemCard.kt` contains exactly `fun WorkItemCard`).
- **Encapsulation Rules:** Private helper components, preview definitions (`@Preview`), local tag constants, and local `const val` variables must live at the bottom of the exact file drawing that parent node. Never aggregate tags or previews in standalone files.
- **The 400-Line Ceiling:** If an entry point or screen file (such as `ProjectBoardScreen.kt`) crosses ~400 lines of source code or accumulates more than one top-level, structurally independent layout, you are required to split it. Extract secondary components into separate, single-purpose files immediately.

## Primitives and Styling Constraints
- **Compose Multiplatform 1.12.1 Specifics:** Do not write syntax illegal under this runtime:
  1. `BasicText` lacks a `textAlign` parameter. Pass alignment variables inside the `style` block.
  2. `TextStyle` lacks an `overflow` setting. It must be designated on the `ParagraphStyle` layer.
  3. `ImageVector.Builder.addPath` requires a `fill: Brush`. Omitting this or passing a direct color prints absolute transparency. Always use `fill = SolidColor(Color.Black)` combined with color tints.
- **Liquid Glass Modifier Bindings:** Never build arbitrary layouts using raw rendering primitives, blurs, or gradients. You must bind layouts exclusively to the established designTokens in `:packages:design-system` using dedicated extension maps (e.g., `Modifier.liquidGlassCard()`).
