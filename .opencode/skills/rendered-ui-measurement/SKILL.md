---
name: rendered-ui-measurement
description: Use when proving or measuring a UI assertion - contrast, ink, colour, alpha, compositing, or anything read off a rendered frame - or when a pixel-reading test is green and you suspect it cannot fail. Covers the mutation-first ritual, twin-frame differencing, estimator robustness, and measuring on two rasterisers.
---

# Proving a UI assertion with rendered pixels

A correct token in the wrong compositing is a recurring and expensive class of defect: the
colour is right, the alpha is right, and the result on screen is still unreadable. You cannot
catch it in a unit test by computing an alpha blend, because the blend is not what the
compositor does. Render the component and read the pixels.

`GlassFillContrastTest` (the sign-in button) and `TabLensContrastTest` (the tab bar captions)
are the two worked examples in this repo.

## Rule 1: prove the test can fail *before* you trust that it passes

This is the whole skill. A pixel-reading test that has never failed may be measuring nothing
at all, and the failure mode is silent — it goes green, it looks like coverage, and it is
worthless.

Do this before interpreting any result, including a red one:

1. Run the test. Green.
2. **Revert the fix in the production file.** Not the test.
3. Run the test. It must fail, and the message must name the real defect.
4. Restore the fix. Run again. Green.

If step 3 is green, stop. The test is not measuring what you fixed, and no amount of
inspecting its internals will tell you what it *is* measuring — you have to find out by
making the input obviously wrong. Delete it and write a different one.

This costs one build cycle. The alternative — debugging a measurement you have never
established is sensitive — costs eight, and is what happens if you skip it.

PLAN.md records 22 mutations run and 22 caught, and two that were missed on the first pass,
both because the *test* was at fault. This repo's own history says the ritual is not optional.

### The cheapest sensitivity probe

To find out what a measurement is actually looking at, feed it an input that cannot be
mistaken. Set the ink under test to something deliberately unreadable — pure black on a black
bar, or the ground colour itself — and read what the test reports:

```
the selected caption in Light appearance is #c1c8d2 on #ced6e0, which is 1.15 to 1
```

The reported value tells you the estimator is reading the real thing. If it reports something
else, the estimator is picking up a different pixel and you have found your bug in one run.

## Rule 2: one complete diagnostic, then read it once

When a measurement is wrong, add **one** diagnostic that dumps everything you might need —
token values under the active theme, per-row changed counts, per-row extremes, bounding
boxes — then read the output once and decide. Adding one `println` per hypothesis and
rebuilding is the same as having no diagnostic, only slower: it produces a trail of
non-answers and sends you theorising instead of looking.

A diagnostic that finds the bug in one run looks like this — per-row counts *and* the extreme
pixel for each row, which is what revealed that one row of two changed pixels was the entire
problem:

```
y=28:n=2  y=29:n=10  y=30:n=8   y=31:n=33  y=32:n=39  y=33:n=27 ... y=38:n=2
```

Rows with tens of changed pixels are glyph strokes. A row with two is not.

## Rule 3: diff two renderings, and know what contaminates the diff

The reliable way to isolate one element is to render the frame twice in a single composition
— once with the element present, once with it replaced by something that draws no ink but
occupies identical layout — and diff them. The element is the intersection of the change.

```kotlin
Column {
    Bar(captions = CAPTIONS, suffix = "inked")
    Bar(captions = BLANKED, suffix = "blanked")   // same layout, a space per caption
}
```

Requirements, each of which was learned the hard way:

- **Blank with a glyph of the same metrics, not by hiding the composable.** Layout must match
  or the diff reports a band, not a glyph.
- **A gap between the bars wider than the bar's shadow.** Shadows fall downward; flush bars
  put the first bar's shadow across the top rows of the second and the diff reads it as ink.
- **Separate the two bars by a spacer that is itself not measured.**
- **A tolerance, not exact inequality.** Two identically laid out bars composited at different
  sub-pixel offsets differ by about one colour step across the whole rectangle. Compared
  exactly, that noise reads as "the caption covers the entire tab". Two or three colour steps
  against a stroke that differs from its ground by hundreds.

### What still contaminates it

Even with all of that, the diff can contain pixels that are not your element:

- **Rounded-corner arcs.** Where a lens's corner crosses the measurement window it leaves a
  sliver of one or two pixels in a single row, and those pixels are the brightest thing in
  the frame.
- **Wide horizontal rules.** A rim inks every pixel across; letters ink a fraction.
- **The unselected element's own state**, if the two frames differ in anything else.

Three masks take these out, each chosen as a property of the shape rather than a tuned
constant, so a future size change does not silently defeat it:

1. A **middle window** in x — the centred glyph is well inside, the element's corner arcs are
   at its own extremes.
2. A **per-row width ceiling** — a row of strokes inks a fraction of the window, a rule inks
   all of it.
3. A **median across the surviving rows** rather than a maximum. This is the one that matters:
   a sliver in one row wins a maximum outright, because on a dark bar the brightest changed
   pixel wins the "furthest from the ground" contest against a caption that is only slightly
   lighter than its ground. A sliver in one row is not a glyph, and the rows of a caption
   agree with each other.

The symptom that points here is specific and worth recognising: **the measured ink is lighter
than the token you set, on a dark bar, and it varies between runs.** An ink can never be
brighter than the colour it was composited from, so a brighter reading means you measured
something else.

## Rule 4: measure on both rasterisers, and expect them to disagree

A JVM run is not evidence about iOS. The same token on the same backdrop measured:

| | JVM | iOS simulator |
| --- | --- | --- |
| `TaskTrackerAccent.Light` on the light bar | 5.37:1 | 3.98:1 |
| tab bar's unselected ink on the light bar | 6.07:1 | 4.36:1 |

Both pairs clear or fail together only by luck. iOS rasterizes a small glyph's stroke at
roughly **85% coverage**, so a caption reads lighter than the colour it was given, and a token
tuned on the JVM can sit under the 4.5:1 threshold on the device that matters.

**Set every ink value from the worse of the two hosts.** Then hold both to the real threshold
in the test, so the value cannot drift.

When a threshold is met on one host and missed on the other, the fix is normally a change to
the token's compositing, not to the test's threshold:

- alpha ink over a translucent surface → raise the alpha
- an opaque token that is too light on that backdrop → mix it toward the label, keeping its hue
- a token documented against white, used on a bar that is not white → the wrong compositing;
  see `GlassFillContrastTest`

## Contrast measurement

WCAG relative luminance, sRGB:

```
lin(c) = c/12.92            if c <= 0.04045
lin(c) = ((c+0.055)/1.055)^2.4   otherwise
L      = 0.2126 R + 0.7152 G + 0.0722 B
ratio  = (lighter + 0.05) / (darker + 0.05)
```

4.5:1 is the floor for normal text. **Measure the rendered glyph, not the token** — an
antialiased stroke core rarely reaches full coverage on either host, so the rendered value is
what the eye gets.

For the ground, take the mode of the blanked frame over the measurement rectangle. The caption
never covers enough of the rectangle to become the mode. Do not composite the expected value
in your head and assert on that; that is the modelling trap this skill exists to avoid.

## Inspecting a frame offline

```
python3 tools/inspect_frame.py <png> [--ascii W H] [--edge ROW]
```

Reads a PNG with no third-party dependencies and prints geometry, contrast, and a coarse
luminance map. Use it to check a screenshot's structure without opening it. The ASCII map is
coarse enough to read layout but will not tell you whether a bar is *pretty* — that still
needs a human to open the file.

## Failure message hygiene

The assertion message is part of the test. It should carry both colours and the number:

```
the selected caption in Dark appearance is #676f7a on #282a2c, which is 2.83 to 1 —
below the 4.5 to 1 needed to read it
```

This is not politeness. In this repo the message is regularly how the actual defect gets
found — a surprising value in a failure message is the signal that the measurement is
pointing at the wrong pixel, and a message that only says `assertTrue failed` throws that
away. Also state the *direction*: if strengthening an effect makes the number worse, say so,
or the next reader will try it.