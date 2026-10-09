# AGENTS.md — `:packages:feature-projects-presentation`

Scoping: everything in the root `AGENTS.md` still applies. This file holds only rules
that are true here and false elsewhere.

- **One public composable per file, named after it.** Helpers, tag constants, `@Preview`s
  and file-local `const val`s move with it. Split past ~400 lines or more than one
  top-level `@Composable`; below that a screen and its one helper read better together.
- **Two fixtures, two jobs.** `ViewPreviewData` lives in `commonMain` because previews
  cannot reach `commonTest` — read-mostly, deterministic, shared by previews and UI
  tests. `FakeProjectRepository` lives in `commonTest` — records calls, seeds shapes,
  and exists for model tests. Do not merge them.
- **Previews pin schemes, never `ThemeScheme.System`.** Fixtures come from
  `ViewPreviewData` so a preview renders the same shape every time it is opened.
- **Compose Multiplatform 1.12.1 bites:** `BasicText` takes no `textAlign` (it goes in
  the style), `TextStyle` has no `overflow`, and `ImageVector.Builder.addPath` takes a
  `Brush` fill — omitting it renders nothing, silently, even with a tint.
