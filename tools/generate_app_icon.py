#!/usr/bin/env python3
"""Renders the TaskTracker launcher icon for iOS and Android from one set of tokens.

Run from the repository root:

    python3 tools/generate_app_icon.py

Writes:

  iosApp/TaskTracker/Assets.xcassets/AppIcon.appiconset/AppIcon.png       (1024, light)
  iosApp/TaskTracker/Assets.xcassets/AppIcon.appiconset/AppIcon-Dark.png  (1024, dark)
  androidApp/src/main/res/drawable/ic_launcher_background.xml
  androidApp/src/main/res/drawable/ic_launcher_foreground.xml
  androidApp/src/main/res/drawable/ic_launcher_monochrome.xml

## Why a generator rather than two hand-drawn assets

The PNG and the vector have to be the same picture. They are also the two places this
repo is most likely to drift apart, because only one of them can be eyeballed: nobody
compares a rendered `ic_launcher_foreground.xml` against a 1024px PNG by looking at
them. Emitting both from one geometry means a change to `GLYPH_BOX` moves both, and the
Android vector cannot drift from the iOS bitmap by anybody editing one of them.

## Why this is not a Pillow script

There is no Pillow and no numpy on the machine this was written on (and a build tool
that needs a `pip install` is a build tool that stops working the next time the
virtualenv is rebuilt). `zlib` is in the standard library, and a PNG is a zlib stream
wrapped in four chunks, so the whole thing is about 200 lines of arithmetic and one
`zlib.compress`. Anti-aliasing comes from analytic signed-distance coverage rather than
from supersampling, which is both faster at 1024px and exact at the round caps.

## The palette is derived from the accent token, not chosen

`TaskTrackerAccent.Light` is `#34537A`, and it is the ramp's *middle* stop verbatim,
which is also where the checkmark sits: the mark runs bottom-left to top-right and
the ramp runs top-left to bottom-right along the other diagonal, so the mark samples
the mid stop. The ends lift and deepen from it (`#4A72B4` / `#16223A`), giving the
field the range the Liquid Glass surfaces have.

White on the raw stops measures 4.82:1 at the lightest, 7.88:1 at the accent, and
15.86:1 at the deepest. The raw stops are the *floor*, not the gate: the mark is
drawn on top of the sheen and the lanes too, and the worst pixel is where the long
arm crosses the middle lane inside the sheen's reach. `_render` therefore returns
the lowest white-on-composited-background contrast found anywhere the mark is
solidly drawn, and `main` fails the run if that number is under 4.5:1 — the same
text bar, measured where the mark actually is.
"""

import math
import os
import struct
import sys
import zlib

# --- the design tokens ------------------------------------------------------------------------

CANVAS = 1024

# The ramp is three stops, and the middle one is the accent itself rather than its
# average: TaskTrackerAccent.Light (#34537A) sits at t=0.5, which is exactly where
# the checkmark lives — the mark runs bottom-left to top-right, the ramp runs
# top-left to bottom-right along the other diagonal, so every mark pixel samples the
# mid stop. The lifted and deepened ends give the field the depth the Liquid Glass
# surfaces have, which the old two-stop pair (#40659A/#22354F, both within a few
# points of the accent) could not: they averaged to the brand colour but read as one
# gloomy navy at launcher size.
GRADIENT_TOP = (0x4A, 0x72, 0xB4)  # white 4.82:1
GRADIENT_MID = (0x34, 0x53, 0x7A)  # TaskTrackerAccent.Light; white 7.88:1
GRADIENT_BOTTOM = (0x16, 0x22, 0x3A)  # white 15.86:1

# The dark-appearance variant. Apple's dark icons are a *darkened* rendering of the
# same mark, not a second mark: the glyph stays white and the field drops, which is
# also what Android 13's themed icons do with `ic_launcher_monochrome`.
DARK_GRADIENT_TOP = (0x1E, 0x2A, 0x3A)
DARK_GRADIENT_MID = (0x14, 0x1C, 0x28)
DARK_GRADIENT_BOTTOM = (0x0D, 0x13, 0x20)

# The glass gloss: white, radial, falling linearly to zero at SHEEN_RADIUS. This is
# the one Liquid Glass element the old flat field was missing. The centre is placed
# *inside* the 33dp safe circle (its distance from the viewport centre is 30.1dp,
# and the corner it points at is the corner a highlight belongs in) because a gloss
# whose peak sits outside the circular mask renders as a crescent, not a light.
#
# 0.09 with lanes at 0.15 puts the composited worst case — the mark crossing the
# middle lane inside the gloss's reach — at 4.75:1. Raising the gloss to 0.12 at the
# original 0.18 lanes measured 4.29:1 and the run failed its own gate, which is what
# the gate is for; these two numbers are a pair, and moving either re-measures both.
SHEEN_CENTRE = (0.33, 0.28)  # fractions of CANVAS, from the top-left
SHEEN_RADIUS = 0.55  # fraction of CANVAS
SHEEN_ALPHA = 0.09
SHEEN_ALPHA_DARK = 0.10

ON_ICON = (255, 255, 255)

# The board lanes behind the check. 0.15 rather than the original 0.18: the sheen
# shares these rows with the mark, and 0.18 + a 0.09 gloss measured 4.42:1 under the
# crossing — a fail. 0.15 keeps the gloss at full strength and puts the composited
# worst case at 4.75:1.
LANE_ALPHA = 0.15

# --- the composition --------------------------------------------------------------------------

# A pill, not a rounded rectangle: the lane's corner radius equals its half-height, so
# the shape is a round-capped line and the VectorDrawable can say so with
# `strokeLineCap="round"` instead of approximating the caps with arcs.
LANE_HALF_LENGTH = 0.32 * CANVAS
LANE_RADIUS = 0.026 * CANVAS
LANE_CENTRES = (0.315, 0.50, 0.685)  # fractions of CANVAS, measured from the top

GLYPH_BOX = 0.48 * CANVAS
GLYPH_ORIGIN = CANVAS / 2 - GLYPH_BOX / 2

# The checkmark, as fractions of GLYPH_BOX. These are `AppMarkDefaults`' proportions
# moved, and the moves are the point of this file rather than a detail of it:
#
#   AppMarkDefaults    short arm (0.10, 0.80) -> vertex (0.38, 0.80) -> tip (0.90, 0.18)
#   here               short arm (0.04, 0.78) -> vertex (0.34, 0.78) -> tip (0.93, 0.14)
#
# `AppMark` is tuned for a 68dp tile drawn at 1x inside a screen the user is already
# looking at. A launcher icon is seen at roughly 40px and is then masked down to about
# half that, so the two changes are both about surviving downscale: the short arm
# reaches further left and the vertex sits further left with it, which widens the
# corner that a small render is most likely to round off; and the long arm climbs
# further, which keeps the diagonal from flattening into a horizontal dash.
#
# `AppMark` is deliberately NOT changed to match. One of the two numbers would be wrong
# at one of the two sizes, and pretending otherwise is how a logo ends up with a stroke
# that looks heavy in the splash and flimsy on the home screen.
SHORT_ARM = (0.04, 0.78)
VERTEX = (0.34, 0.78)
TIP = (0.93, 0.14)

# Stroke as a fraction of the glyph box, so it is comparable to AppMarkDefaults'
# `strokeFraction` divided by its `glyphFraction`: 0.085 / (30/68) = 0.193. This is
# 0.24, i.e. 24% heavier, which is the same downscale argument as the geometry above.
STROKE_OF_GLYPH = 0.24

# --- Android ------------------------------------------------------------------------------

ANDROID_VIEWPORT = 108

# 33dp is the radius of the 66dp circle, which is the tightest of the standard adaptive
# masks; the square-ish masks show more. The content is fitted to the *circle* rather
# than to the 72dp square because the circle is the one every launcher can be relied on
# to show, and fitting to the square would put the lane ends outside it.
ANDROID_SAFE_RADIUS = 33.0

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# --- geometry, resolved once ----------------------------------------------------------------


def _glyph_point(fx, fy):
    return (GLYPH_ORIGIN + fx * GLYPH_BOX, GLYPH_ORIGIN + fy * GLYPH_BOX)


CHECK_POINTS = (
    _glyph_point(*SHORT_ARM),
    _glyph_point(*VERTEX),
    _glyph_point(*TIP),
)
CHECK_STROKE = GLYPH_BOX * STROKE_OF_GLYPH
CHECK_SEGMENTS = ((CHECK_POINTS[0], CHECK_POINTS[1]), (CHECK_POINTS[1], CHECK_POINTS[2]))

LANE_LINES = tuple(
    (
        (CANVAS / 2 - LANE_HALF_LENGTH, fy * CANVAS),
        (CANVAS / 2 + LANE_HALF_LENGTH, fy * CANVAS),
    )
    for fy in LANE_CENTRES
)


def android_scale():
    """dp per canvas pixel, chosen so nothing drawn falls outside the safe circle.

    The bound is *exact*, not a bounding box: for a circle of radius r centred at p the
    farthest point from the origin is at distance |p| + r, and every shape here is a
    union of circles and capsules. So the maximum is taken over the capsule endpoints.

    Using the bounding box of the drawn geometry instead would be simpler and would
    shrink the icon for no benefit: this composition is wide and flat, so its bounding
    box has empty top corners, and padding for empty corners costs real size on a
    launcher. The measured difference is about 3dp of icon width.
    """
    furthest = 0.0
    # Lanes: each end cap is a circle of radius LANE_RADIUS.
    for (ax, ay), (bx, by) in LANE_LINES:
        furthest = max(
            furthest,
            math.hypot(ax - CANVAS / 2, ay - CANVAS / 2) + LANE_RADIUS,
            math.hypot(bx - CANVAS / 2, by - CANVAS / 2) + LANE_RADIUS,
        )
    # Check: the stroke is a union of capsules about the polyline's three vertices.
    for px, py in CHECK_POINTS:
        furthest = max(furthest, math.hypot(px - CANVAS / 2, py - CANVAS / 2) + CHECK_STROKE / 2)
    return ANDROID_SAFE_RADIUS / furthest


# --- rasterising -------------------------------------------------------------------------------


def _dist_to_segment(px, py, ax, ay, bx, by):
    pax = px - ax
    pay = py - ay
    bax = bx - ax
    bay = by - ay
    t = (pax * bax + pay * bay) / (bax * bax + bay * bay)
    if t < 0.0:
        t = 0.0
    elif t > 1.0:
        t = 1.0
    dx = pax - t * bax
    dy = pay - t * bay
    return math.sqrt(dx * dx + dy * dy)


def _stroke_mask(segments, half):
    """Byte-per-pixel coverage of a round-capped polyline, in one canvas-sized buffer.

    Coverage is `half + 0.5 - distance`, clamped: a one-pixel-wide ramp centred on the
    outline. That is the analytic anti-aliasing of a straight edge and a circular cap,
    which is what makes the round terminals smooth without supersampling.
    """
    mask = bytearray(CANVAS * CANVAS)
    for (ax, ay), (bx, by) in segments:
        x0 = max(0, int(math.floor(min(ax, bx) - half - 1)))
        x1 = min(CANVAS - 1, int(math.ceil(max(ax, bx) + half + 1)))
        y0 = max(0, int(math.floor(min(ay, by) - half - 1)))
        y1 = min(CANVAS - 1, int(math.ceil(max(ay, by) + half + 1)))
        for y in range(y0, y1 + 1):
            row = y * CANVAS
            for x in range(x0, x1 + 1):
                d = _dist_to_segment(x + 0.5, y + 0.5, ax, ay, bx, by)
                a = half + 0.5 - d
                if a <= 0.0:
                    continue
                if a > 1.0:
                    a = 1.0
                v = int(a * 255.0 + 0.5)
                i = row + x
                if v > mask[i]:
                    mask[i] = v
    return mask


def _ramp(t, top, mid, bottom):
    """The three-stop field colour at anti-diagonal parameter t in [0, 1]."""
    if t < 0.5:
        f = t * 2.0
        a, b = top, mid
    else:
        f = (t - 0.5) * 2.0
        a, b = mid, bottom
    return (a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f)


def _render(top, mid, bottom, sheen_alpha, lane_mask, check_mask):
    """Composite the ramp, the sheen, the lanes and the check, one row at a time.

    The lanes are looked up only on the rows that have any, which is 3 bands of 53px
    out of 1024. Without that check the lane lookup runs a million times to find zero
    about ninety-five percent of the time, which is most of this function's cost.

    Returns `(rows, worst_contrast)`: the picture, and the lowest white-on-background
    contrast found at any pixel the mark covers solidly. That number is the real
    legibility gate — the mark is drawn on the sheen and the lanes too, so the raw
    stops alone cannot answer "can the check be read".
    """
    rows = []
    lane_rows = [
        any(lane_mask[y * CANVAS + x] for x in range(0, CANVAS, 4)) for y in range(CANVAS)
    ]

    inv = 1.0 / (CANVAS - 1)
    sheen_cx = SHEEN_CENTRE[0] * CANVAS
    sheen_cy = SHEEN_CENTRE[1] * CANVAS
    sheen_r = SHEEN_RADIUS * CANVAS

    worst = float("inf")

    for y in range(CANVAS):
        row = bytearray(CANVAS * 3)
        off = y * CANVAS
        on_lane_row = lane_rows[y]
        dy = y - sheen_cy
        dy2 = dy * dy
        for x in range(CANVAS):
            # The ramp is recomputed from t rather than carried in an accumulator:
            # with three stops the per-pixel step doubles at the mid stop, and an
            # accumulator that has to notice the crossing is more code than this.
            t = 0.5 * (x + y) * inv
            r, g, b = _ramp(t, top, mid, bottom)
            dx = x - sheen_cx
            d = math.sqrt(dx * dx + dy2)
            if d < sheen_r:
                # Linear falloff, no smoothstep: at 12% peak alpha the knee of a
                # smoothstep is a difference no launcher will ever show, and linear
                # is one fewer magic curve when matching the VectorDrawable below.
                a = sheen_alpha * (1.0 - d / sheen_r)
                r += (ON_ICON[0] - r) * a
                g += (ON_ICON[1] - g) * a
                b += (ON_ICON[2] - b) * a
            if on_lane_row:
                c = lane_mask[off + x]
                if c:
                    a = c / 255.0 * LANE_ALPHA
                    r += (ON_ICON[0] - r) * a
                    g += (ON_ICON[1] - g) * a
                    b += (ON_ICON[2] - b) * a
            c = check_mask[off + x]
            if c:
                # Measure before painting: once the mark is composited the pixel is
                # white and the question has answered itself.
                if c == 255:
                    contrast = _contrast_against_white((int(r + 0.5), int(g + 0.5), int(b + 0.5)))
                    if contrast < worst:
                        worst = contrast
                a = c / 255.0
                r += (ON_ICON[0] - r) * a
                g += (ON_ICON[1] - g) * a
                b += (ON_ICON[2] - b) * a
            i = x * 3
            row[i] = int(r + 0.5)
            row[i + 1] = int(g + 0.5)
            row[i + 2] = int(b + 0.5)
        rows.append(row)
    return rows, worst


def _write_png(path, rows):
    """A minimal 8-bit truecolour PNG.

    Colour type 2 rather than 6 on purpose: `AppIcon.appiconset` rejects an alpha
    channel, and a file with one is silently flattened by some tools and rejected by
    others. Emitting RGB here means the file is correct before it ever reaches Xcode.
    """
    raw = bytearray()
    for row in rows:
        raw.append(0)  # filter type 0 (None); these gradients compress fine without one
        raw += row
    compressed = zlib.compress(bytes(raw), 9)

    def chunk(tag, data):
        return (
            struct.pack(">I", len(data))
            + tag
            + data
            + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
        )

    with open(path, "wb") as handle:
        handle.write(b"\x89PNG\r\n\x1a\n")
        handle.write(chunk(b"IHDR", struct.pack(">IIBBBBB", CANVAS, CANVAS, 8, 2, 0, 0, 0)))
        handle.write(chunk(b"IDAT", compressed))
        handle.write(chunk(b"IEND", b""))


# --- the contrast gate ------------------------------------------------------------------------


def _relative_luminance(rgb):
    def channel(value):
        c = value / 255.0
        return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4

    r, g, b = (channel(v) for v in rgb)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def _contrast_against_white(rgb):
    return 1.05 / (_relative_luminance(rgb) + 0.05)


def _assert_legible(worst_light, worst_dark):
    """Fail the run, not the launch, if the mark cannot be read on its own background.

    The numbers that matter are the ones `_render` measured on the *composited*
    field — ramp plus sheen plus lanes, where the mark actually is — and both
    appearances are held to the same 4.5:1 bar. The raw stops are checked too, as a
    floor: a stop that fails on its own would need the sheen's direction to rescue
    it, and a palette that leans on the sheen is one tuning pass away from illegible.
    """
    for name, stop in (
        ("light ramp top", GRADIENT_TOP),
        ("light ramp mid", GRADIENT_MID),
        ("light ramp bottom", GRADIENT_BOTTOM),
        ("dark ramp top", DARK_GRADIENT_TOP),
        ("dark ramp mid", DARK_GRADIENT_MID),
        ("dark ramp bottom", DARK_GRADIENT_BOTTOM),
    ):
        ratio = _contrast_against_white(stop)
        if ratio < 4.5:
            sys.exit("FAIL: white mark is %.2f:1 on the %s, below 4.5:1" % (ratio, name))
        print("  contrast  white on %-16s %5.2f:1" % (name, ratio))
    for name, worst in (("light", worst_light), ("dark", worst_dark)):
        if worst < 4.5:
            sys.exit(
                "FAIL: composited field under the mark bottoms out at %.2f:1 on the "
                "%s icon, below 4.5:1" % (worst, name)
            )
        print("  contrast  white on %-16s %5.2f:1  (composited, under the mark)"
              % (name + " field", worst))


def _assert_lanes_stay_behind_the_mark():
    """The lanes must not be able to read as a foreground element.

    At 15% on a mid-tone ramp they are a texture, and that is deliberate: at the size a
    launcher actually draws an icon, three separate bars in front of a checkmark would
    be the first thing to turn to mush. If this alpha is ever raised past roughly a
    quarter, the design has to be re-measured at 40px rather than trusted.
    """
    if LANE_ALPHA > 0.25:
        sys.exit("FAIL: lane alpha %.2f makes the lanes a foreground element" % LANE_ALPHA)


# --- the Android vectors ----------------------------------------------------------------------


def _dp(value, scale):
    return (value - CANVAS / 2) * scale + ANDROID_VIEWPORT / 2


def _n(value):
    return ("%.2f" % value).rstrip("0").rstrip(".")


def _hex(rgb):
    return "#FF%02X%02X%02X" % rgb


def _background_xml(top, mid, bottom):
    sheen_alpha_byte = int(SHEEN_ALPHA * 255.0 + 0.5)
    return """<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by tools/generate_app_icon.py. Do not edit by hand. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="linear"
                android:startX="0"
                android:startY="0"
                android:endX="108"
                android:endY="108">
                <item android:offset="0" android:color="%s" />
                <item android:offset="0.5" android:color="%s" />
                <item android:offset="1" android:color="%s" />
            </gradient>
        </aapt:attr>
    </path>
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="radial"
                android:centerX="%s"
                android:centerY="%s"
                android:gradientRadius="%s">
                <item android:offset="0" android:color="#%02XFFFFFF" />
                <item android:offset="1" android:color="#00FFFFFF" />
            </gradient>
        </aapt:attr>
    </path>
</vector>
""" % (
        _hex(top),
        _hex(mid),
        _hex(bottom),
        _n(SHEEN_CENTRE[0] * ANDROID_VIEWPORT),
        _n(SHEEN_CENTRE[1] * ANDROID_VIEWPORT),
        _n(SHEEN_RADIUS * ANDROID_VIEWPORT),
        sheen_alpha_byte,
    )


def _lane_paths(scale):
    paths = []
    for (ax, ay), (bx, by) in LANE_LINES:
        paths.append(
            'M%s,%s L%s,%s'
            % (_n(_dp(ax, scale)), _n(_dp(ay, scale)), _n(_dp(bx, scale)), _n(_dp(by, scale)))
        )
    return paths


def _check_path(scale):
    pts = [(_dp(x, scale), _dp(y, scale)) for x, y in CHECK_POINTS]
    return "M%s,%s L%s,%s L%s,%s" % (
        _n(pts[0][0]), _n(pts[0][1]),
        _n(pts[1][0]), _n(pts[1][1]),
        _n(pts[2][0]), _n(pts[2][1]),
    )


def _foreground_xml(scale):
    lanes = "".join(
        """    <path
        android:pathData="%s"
        android:strokeColor="%s"
        android:strokeAlpha="%s"
        android:strokeWidth="%s"
        android:strokeLineCap="round" />
"""
        % (path, _hex(ON_ICON), _n(LANE_ALPHA), _n(LANE_RADIUS * 2 * scale))
        for path in _lane_paths(scale)
    )
    return """<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by tools/generate_app_icon.py. Do not edit by hand. -->
<!--
  Fitted to the %sdp safe circle rather than to the 72dp safe square: the lanes run
  past the check on both sides, and a square fit would put their round ends outside the
  circular mask. See android_scale() for why the bound is the capsule endpoints and not
  the bounding box.
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
%s    <path
        android:pathData="%s"
        android:strokeColor="%s"
        android:strokeWidth="%s"
        android:strokeLineCap="round"
        android:strokeLineJoin="round" />
</vector>
""" % (_n(ANDROID_SAFE_RADIUS), lanes, _check_path(scale), _hex(ON_ICON), _n(CHECK_STROKE * scale))


def _monochrome_xml(scale):
    return """<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by tools/generate_app_icon.py. Do not edit by hand. -->
<!--
  The themed-icon layer (Android 13+), and the checkmark alone rather than the whole
  composition. Android tints this with the user's wallpaper palette and then picks the
  contrasting shade itself, so anything low-alpha in here turns into a muddy second
  tone at best. A checkmark is also the most legible thing this icon can become when it
  is reduced to one colour.
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:pathData="%s"
        android:strokeColor="%s"
        android:strokeWidth="%s"
        android:strokeLineCap="round"
        android:strokeLineJoin="round" />
</vector>
""" % (_check_path(scale), _hex(ON_ICON), _n(CHECK_STROKE * scale))


# --- preview ----------------------------------------------------------------------------------


def _preview(rows, cols=62):
    """An ASCII dump, so the composition can be looked at without an image viewer.

    Each character is the *mean* of its cell rather than a single sampled pixel. A
    point sample is what this did first, and it is worthless here: at a 16px cell it
    lands on a lane about a third of the time and misses it the rest, so the preview
    showed the lanes as a dashed flicker and sent me looking for a rendering bug in the
    mask that was not there. Averaging is what makes the shape legible.
    """
    ramp = " .:-=+*#%@"
    cell = CANVAS / float(cols)
    lines = int(round(CANVAS / cell / 2.0))
    print()
    for cy in range(lines):
        y0 = int(cy * cell * 2)
        y1 = min(CANVAS, y0 + int(cell * 2))
        line = []
        for cx in range(cols):
            x0 = int(cx * cell)
            x1 = min(CANVAS, x0 + int(cell))
            total = 0.0
            n = 0
            for y in range(y0, y1, 2):
                # `rows` is a list of per-row bytearrays, so the row offset is implicit.
                # Indexing as though each row were the whole image walks off the end.
                row = rows[y]
                for x in range(x0, x1, 2):
                    i = x * 3
                    total += 0.2126 * row[i] + 0.7152 * row[i + 1] + 0.0722 * row[i + 2]
                    n += 1
            luma = total / n / 255.0
            line.append(ramp[min(9, int(luma * 10))])
        print("".join(line))


# --- main -------------------------------------------------------------------------------------


def main():
    _assert_lanes_stay_behind_the_mark()

    print("composing")
    check_mask = _stroke_mask(CHECK_SEGMENTS, CHECK_STROKE / 2)
    lane_mask = _stroke_mask(LANE_LINES, LANE_RADIUS)

    icon_dir = os.path.join(
        REPO, "iosApp", "TaskTracker", "Assets.xcassets", "AppIcon.appiconset"
    )
    drawable_dir = os.path.join(REPO, "androidApp", "src", "main", "res", "drawable")

    worst = {}
    for name, top, mid, bottom, sheen in (
        ("AppIcon.png", GRADIENT_TOP, GRADIENT_MID, GRADIENT_BOTTOM, SHEEN_ALPHA),
        ("AppIcon-Dark.png", DARK_GRADIENT_TOP, DARK_GRADIENT_MID, DARK_GRADIENT_BOTTOM, SHEEN_ALPHA_DARK),
    ):
        path = os.path.join(icon_dir, name)
        rows, worst[name] = _render(top, mid, bottom, sheen, lane_mask, check_mask)
        _write_png(path, rows)
        print("  wrote %s (%d x %d, no alpha)" % (os.path.relpath(path, REPO), CANVAS, CANVAS))
        if name == "AppIcon.png":
            _preview(rows)

    _assert_legible(worst["AppIcon.png"], worst["AppIcon-Dark.png"])

    scale = android_scale()
    print()
    print("android scale %.5f dp/px; lane span %.1f dp of the 108dp viewport"
          % (scale, LANE_HALF_LENGTH * 2 * scale))
    for name, body in (
        ("ic_launcher_background.xml", _background_xml(GRADIENT_TOP, GRADIENT_MID, GRADIENT_BOTTOM)),
        ("ic_launcher_foreground.xml", _foreground_xml(scale)),
        ("ic_launcher_monochrome.xml", _monochrome_xml(scale)),
    ):
        path = os.path.join(drawable_dir, name)
        with open(path, "w") as handle:
            handle.write(body)
        print("  wrote %s" % os.path.relpath(path, REPO))


if __name__ == "__main__":
    main()