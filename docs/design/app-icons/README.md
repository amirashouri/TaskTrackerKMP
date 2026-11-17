# TaskTracker app-icon concepts

Seven 1024×1024 launcher-icon directions derived from the existing brand tokens. The
shipped icon is **02 Board**; the other six are design exploration and are not shipped.

Open `index.html` to compare them side by side.

## The motto

> Your tasks. Your server. Your rules.

| Concept | File | Motto line it expresses |
| :-- | :-- | :-- |
| 02 Board *(adopted)* | `02-board.png` | Your tasks. Kanban lanes with cards and a completed check. |
| 02 Board, dark *(adopted)* | `02-board-dark.png` | Same mark, lowered field for dark appearances. |
| 01 Signature | `01-signature.png` | Your tasks. The current mark refined — lowest-risk evolution. |
| 01 Signature, dark | `01-signature-dark.png` | Same mark, lowered field for dark appearances. |
| 03 Vault | `03-vault.png` | Your server. Your rules. Shield + mark = ownership and privacy. |
| 04 Glass | `04-glass.png` | The Liquid Glass design system, made literal. |
| 05 Sync | `05-sync.png` | Offline-first, cross-device — the mark inside a sync orbit. |
| 06 Essential | `06-essential.png` | Your tasks, full stop. One bold mark, legible at 16px. |

## Design constraints honoured

- **Ramp:** the three stops `#4A72B4 → #34537A → #16223A` from
  `tools/generate_app_icon.py`. `#34537A` is `TaskTrackerAccent.Light`, the middle stop the
  mark sits on. Dark and glass variants use that file's dark ramp and a lifted glass ramp.
- **Mark:** the same two-stroke, round-capped checkmark as `AppMarkDefaults` in
  `Brand.kt` / `ic_launcher_foreground.xml`, rescaled per concept.
- **Material:** the white radial sheen and (in 04) the specular band that define the
  app's Liquid Glass surfaces.
- **Safe area:** the tile is a rounded square with the same inset as the shipped icon;
  the meaningful content stays inside the circular launcher mask.

## Regenerating

```bash
python3 docs/design/app-icons/generate_concepts.py
```

Pure stdlib — no Pillow, numpy, or pip install, matching the constraint the shipped generator
documents. PNGs are zlib streams written directly; anti-aliasing is analytic signed-distance
coverage at one sample per pixel.

## Adoption status

**02 Board is adopted.** Its geometry has been ported into `tools/generate_app_icon.py`,
which is now the single source of truth for the shipped icon. Regenerate the shipped iOS,
Android, and desktop assets with:

```bash
python3 tools/generate_app_icon.py
```

The generator emits the iOS PNGs, the Android vectors, and the desktop PNG; the `.icns` is
packed from that PNG with `iconutil` as documented in `shared-ui/build.gradle.kts`. The other
concepts here remain a design record and are not shipped.

### One deliberate deviation from the concept

In `02-board.png` the card dashes sit where the enlarged check crosses them. That is fine at
1024px but fails the shipped generator's 4.5:1 launcher-size legibility gate: white on the
column *and* a card bottomed out at 3.11:1. In the shipped icon the dashes are shorter and
moved to 0.42/0.63 of the canvas so the check never crosses one — the field under the mark is
then the column alone at 4.53:1 (8.66:1 dark). Card alpha is 0.24 rather than the concept's
0.28 to stay under the existing foreground-element gate. The visual difference at launcher
size is negligible; the concept PNGs are left as the design record of what was approved.
