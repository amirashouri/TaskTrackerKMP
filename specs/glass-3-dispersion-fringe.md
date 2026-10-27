# glass-3 — Dispersion fringe: document it, name it, keep the pixels

- **status:** done
- **priority:** P2
- **plan:** no `docs/PLAN.md` entry — standalone design-system hardening sprint
  (`specs/glass-1..7`). No wire-format change; Fast Path per workspace AGENTS.md.

## Scope

- `:packages:design-system` — the dark-mode RGB fringe in `ttGlassSurface`
  (`Glass.kt`, the `isDark` block drawing pure `Color.Red`/`Green`/`Blue`
  hairlines at the top edge, green at 1.5× alpha).
- This is the only rendering trick in the package with no comment and no named
  tokens. It simulates chromatic dispersion. The fix is documentation and
  tokenization, **not** a rendering change — the pixels are measured.

## Files

- `packages/design-system/src/commonMain/kotlin/com/tasktracker/designsystem/Glass.kt`
  — move the three alpha values (base, green multiplier) into the `ttGlass`
  token table (`DISPERSION_ALPHA`, `DISPERSION_GREEN_MULTIPLIER`), with a block
  comment recording: what the fringe is, why pure RGB channels, why dark-only
  and `base == null`, why the green channel leads, and which pixel test holds
  the values.
- `packages/design-system/src/jvmTest/kotlin/com/tasktracker/designsystem/GlassConstantsTest.kt`
  (or the existing constants test that holds `ttGlass` numbers — confirm target)
  — assert the new token values so a silent retune is caught.

## Contract

- [ ] `ttGlass.DISPERSION_ALPHA` and `ttGlass.DISPERSION_GREEN_MULTIPLIER`
      exist, are the only source of the fringe's numbers, and equal the
      pre-existing values (`0.4f` relative alpha and `1.5f`).
- [ ] The fringe block carries a comment explaining the effect and its
      constraints in the style of the rest of the file.
- [ ] No pixel output changes: all existing glass pixel tests pass unmodified.

## Non-goal

- [ ] No reimplementation of dispersion (no per-channel draw of the fill), no
      light-mode fringe, and no renaming of existing public tokens.

## Validation

- Run: `./gradlew --offline :packages:design-system:jvmTest --tests '*Glass*'`
- Counts: +1 constants test; no pixel movement.

## Handoff

- **Done:** Moved dispersion values to `ttGlass.DISPERSION_ALPHA` (0.4f) and `ttGlass.DISPERSION_GREEN_MULTIPLIER` (1.5f) with explanatory comments. No rendering change; all glass tests pass.
- **Pending:** None.
- **Next step:** implement glass-4.
- **Gotchas:** The fringe still draws before the rim border in `drawBehind`; order unchanged.

## Risks / notes

- If the constants test target does not exist, add the assertions to the
  closest existing `ttGlass` constants test rather than creating a new file.
