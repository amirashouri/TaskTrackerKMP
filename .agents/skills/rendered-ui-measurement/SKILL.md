---
name: rendered-ui-measurement
description: Use when asserting visual properties (contrast, alpha, fills, blur) from rendered frames or validating pixel-reading tests.
---

# Rendered UI & Pixel Testing

## 1. Mutation Testing Ritual (Strictly Required)
1. Run test -> Expect **Green**.
2. Temporarily break or invert the production value -> **Must fail naming the defect**.
3. Restore value -> Expect **Green**.
   *(If step 2 passes, discard the test immediately).*

## 2. Twin-Frame Differencing
To isolate an element's ink without background contamination:
* Compose two identical layouts in one frame: one with content, one blanked with equal-metric spaces.
* Keep a gap between components wider than the drop shadow.
* Use masks to ignore edges:
    * Restrict sampling to the middle window along X (clears corner arcs).
    * Set a per-row pixel count ceiling (rules ink full row; glyphs ink fractional rows).
    * Take the median luminance across surviving rows.

## 3. Dual-Host Calibration
* JVM and iOS rasterizers differ: iOS renders small glyph strokes at ~85% coverage.
* **Calibrate to the weaker host:** Ensure WCAG contrast floor (≥ 4.5:1 for normal text) is met on **both** JVM and iOS.

## 4. Inspection CLI
Inspect screenshots offline without third-party dependencies:
```bash
python3 tools/inspect_frame.py <path-to-png> [--ascii 40 20] [--edge ROW]
```