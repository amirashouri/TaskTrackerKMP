---
name: kmp-architecture-workflow
description: Use when adding or changing a feature, module dependency, platform-specific implementation, or shared Compose UI in TaskTrackerKMP. Covers this repository's Clean Architecture boundaries, checked Gradle graph, KMP source sets, and Liquid Glass design-system ownership.
---

# KMP feature and architecture workflow

TaskTrackerKMP is a Kotlin Multiplatform app with shared Compose UI for Android and iOS.
Gradle modules enforce the package boundaries. Follow the repository's actual dependency
table rather than applying a generic Clean Architecture template: some cross-cutting
dependencies are intentionally narrow and documented in the table.

## Place work in the existing layers

Use the closest existing feature and layer instead of creating a parallel module:

- `*-domain`: feature business rules and ports; keep UI and infrastructure out.
- `*-data`: adapters and implementations of that feature's ports.
- `*-presentation`: feature state and Compose UI; depend on the feature domain, not its data.
- `:composition-root`: bind ports to implementations; it is the only module allowed to see
  all library modules.
- `:shared-ui`: assemble shared screens and navigation from presentation modules.
- `:packages:design-system`: shared UI tokens and Compose resources.

For a new dependency or module, inspect only the relevant entries in
`build-logic/convention/src/main/kotlin/tasktracker.architecture.gradle.kts`:
`allowedLibraryTable`, `allowedDependenciesTable`, and `packageOwnersTable`. Update the
tables when the intended graph changes. Then use `verifyArchitectureGraph` and
`verifyModuleImports` as the authority; do not add a dependency just to make an import
compile or re-export a type through an unrelated module.

## Keep Android and iOS behavior aligned

Start in `commonMain` for domain behavior, state, and Compose UI. Put code in `androidMain`
or `iosMain` only when it uses a platform API or must honor a platform behavior. Express a
shared call site with `expect`/`actual` only when a real platform seam is needed, and supply
all actuals required by the configured hierarchy. The convention plugin adds a JVM target,
so a new `expect` commonly also needs a `jvmMain` actual. Check the existing module's source
sets and convention plugin before deciding.

Keep screens and their Liquid Glass language shared on both mobile platforms. Reuse the
existing `:packages:design-system` tokens and modifiers; do not recreate blur, gradients,
rims, or color mappings inside a feature. If a needed primitive is missing, define the shared
immutable token or modifier in the design system and document the visual measurement that
justifies any non-obvious value. Do not introduce Material3.

## Verify the change at its boundary

- For architecture-only changes, run the two architecture verification tasks after the
  source and graph are aligned.
- For changes with behavior or UI, follow the project's test matrix in
  `kmp-compose-tests`; JVM success alone does not establish iOS behavior.
- For a UI property that only exists in rendered output, use `rendered-ui-measurement` and
  measure both JVM and iOS. Prefer structural assertions when the property can be read from
  the modifier or semantics tree.
- When a task spans multiple files, follow the repository's plan-first ACK protocol in
  `AGENTS.md` before editing.
