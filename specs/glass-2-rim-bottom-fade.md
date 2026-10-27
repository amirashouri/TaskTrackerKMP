# glass-2 — Rim gradient: make the bottom fade real (or prove it renders)

- **status:** pending
- **priority:** P1
- **plan:** no `docs/PLAN.md` entry — standalone design-system hardening sprint
  (`specs/glass-1..7`). No wire-format change; Fast Path per workspace AGENTS.md.

## Scope

- `:packages:design-system` — the border brush in `ttGlassSurface`.
- Hypothesis to test first: with `endY = Float.POSITIVE_INFINITY`, the vertical
  gradient's interpolation fraction is ~0 at every y, so the rim paints at full
  alpha for its whole height and `ttGlass.RIM_BOTTOM_ALPHA` (0.6) is dead code.
  If a pixel test disproves the hypothesis (Compose clamps infinity somewhere),
  the fix is documenting that instead of changing rendering.

## Files

- `packages/design-system/src/commonMain/kotlin/com/tasktracker/designsystem/Glass.kt`
  — replace the infinite-`endY` border brush with one sized to the drawn bounds
  (a `drawBehind`-drawn border or `endY = size.height` in a custom border) so the
  bottom edge actually lands at `rim.alpha * RIM_BOTTOM_ALPHA`.
- `packages/design-system/src/jvmTest/kotlin/com/tasktracker/designsystem/GlassSurfacePixelTest.kt`
  — new assertions: rim pixel at the top edge is `HAIRLINE_ALPHA` (dark) and rim
  pixel at the bottom edge is `HAIRLINE_ALPHA * RIM_BOTTOM_ALPHA`.
- `packages/design-system/src/androidHostTest/` and `iosTest/` — thin entry
  points per the host-split test convention, if the host runs the pixel test.

## Contract

- [ ] A pixel test asserts the top-edge rim alpha and the bottom-edge rim alpha
      of a tall (≥ 80dp) glass surface in dark theme, and the two differ by the
      `RIM_BOTTOM_ALPHA` factor.
- [ ] The test fails against the pre-fix gradient (mutation check: revert the
      brush change and the bottom-edge assertion must fail).
- [ ] Existing `GlassSurfacePixelTest` measurements (hairline alphas, raised-edge
      read) are unchanged at the top edge.

## Non-goal

- [ ] No change to hairline width, alphas, or the light-theme shadow rim.

## Validation

- Run: `./gradlew --offline :packages:design-system:jvmTest --tests '*GlassSurfacePixel*'`
- Counts: +2 assertions in the existing pixel test file; `test-baseline.json`
  moves by the new test only.
- Pixel-reading review: the new assertions are programmatic pixel reads, not
  screenshot judgement — no `test-reviewer` pass required.

## Handoff

- **Done:** to fill at implementation.
- **Pending:** to fill at implementation.
- **Next step:** implement glass-3.
- **Gotchas:** if the fix changes the rendered rim on tall cards, the splash and
  previews repaint — run the full focused glass suite, not just the pixel test.

## Risks / notes

- `Modifier.border` re-measures its brush per draw; moving the gradient into the
  existing `drawBehind` avoids a second layer and keeps the border hairline
  crisp — prefer that path over a custom `Border` implementation.
