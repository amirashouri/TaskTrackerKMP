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
  shared-ui/src/jvmMain/resources/AppIcon.png                            (1024, masked)

The last one is the desktop source PNG: same light render, but clipped to the
rounded-square tile with an alpha channel, which is what `iconutil` packs into
AppIcon.icns for the macOS .app bundle. Regenerate the .icns after running this:

  mkdir -p /tmp/tasktracker.iconset
  # ... populate every required size from the masked PNG ...
  iconutil -c icns -o shared-ui/src/jvmMain/resources/AppIcon.icns /tmp/tasktracker.iconset

## Why a generator rather than two hand-drawn assets

The PNG and the vector have to be the same picture. They are also the two places this
repo is most likely to drift apart, because only one of them can be eyeballed: nobody
compares a rendered `ic_launcher_foreground.xml` against a 1024px PNG by looking at
them. Emitting both from one geometry means a change to `CHECK_POINTS` moves both, and the
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

# --- the adopted 02 Board composition -------------------------------------------------------

# Every number below is the 02 Board concept from docs/design/app-icons/ mapped out of
# that render's 0.08..0.92 tile into the full canvas: `_ship(u) = (u - 0.08) / 0.84`.
# iOS masks the full canvas itself; leaving the concept's inset in would shrink the mark.
# Widths divide by the span only — they have no positional offset.
TILE_INSET = 0.08
TILE_SPAN = 1.0 - 2 * TILE_INSET


def _ship(u):
    return (u - TILE_INSET) / TILE_SPAN


def _ship_len(v):
    return v / TILE_SPAN


# Columns: three tall pills. The concept drew near-pill rounded rectangles
# (half-width 0.078, corner radius 0.072); a capsule of radius 0.078 keeps the PNG
# and the VectorDrawable the same shape with no arc approximation, and is what lets
# the existing round-capped mask/emitter machinery do all the work.
LANE_ALPHA = 0.15
COLUMN_CENTRES = tuple(_ship(c) for c in (0.31, 0.50, 0.69))
LANE_RADIUS = _ship_len(0.078) * CANVAS
COLUMN_HALF_STRAIGHT = _ship_len(0.235 - 0.078) * CANVAS

# Card dashes on each column, as bright as the foreground gate allows (0.24, just
# under its 0.25 cap). They can stay this bright because of their position, not a
# dimmed alpha: the dashes sit at 0.42/0.63 of the canvas and are short enough that
# the enlarged check never crosses one. The mark therefore rests only on the columns
# (4.53:1), not on a column *and* a card — crossing both lifted the field under the
# mark to 3.1:1 and failed the gate at 40px. The concept drew them at 0.28; 0.24 is
# indistinguishable at launcher size and keeps the existing invariant intact.
CARD_ALPHA = 0.24
CARD_RADIUS = _ship_len(0.012) * CANVAS
CARD_HALF_STRAIGHT = 25.0
CARD_CENTRES = tuple(_ship(c) for c in (0.42, 0.63))

# The enlarged checkmark from the approved concept, mapped the same way. Brand.kt's
# AppMark uses the same Board geometry at tile-fraction scale so the splash and
# sign-in marks stay aligned with the launcher identity.
CHECK_POINTS = tuple(
    (_ship(x) * CANVAS, _ship(y) * CANVAS)
    for x, y in ((0.38, 0.54), (0.468, 0.54), (0.63, 0.39))
)
CHECK_STROKE = 2 * _ship_len(0.038) * CANVAS

# --- Android ------------------------------------------------------------------------------

ANDROID_VIEWPORT = 108

# 33dp is the radius of the 66dp circle, which is the tightest of the standard adaptive
# masks; the square-ish masks show more. The content is fitted to the *circle* rather
# than to the 72dp square because the circle is the one every launcher can be relied on
# to show, and fitting to the square would put the lane ends outside it.
ANDROID_SAFE_RADIUS = 33.0

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# --- geometry, resolved once ----------------------------------------------------------------


CHECK_SEGMENTS = ((CHECK_POINTS[0], CHECK_POINTS[1]), (CHECK_POINTS[1], CHECK_POINTS[2]))

# A column is a vertical capsule: the straight run plus its two round caps.
COLUMN_LINES = tuple(
    (
        (cx * CANVAS, CANVAS / 2 - COLUMN_HALF_STRAIGHT),
        (cx * CANVAS, CANVAS / 2 + COLUMN_HALF_STRAIGHT),
    )
    for cx in COLUMN_CENTRES
)

# Each column carries two horizontal card dashes.
CARD_LINES = tuple(
    (
        (cx * CANVAS - CARD_HALF_STRAIGHT, cy * CANVAS),
        (cx * CANVAS + CARD_HALF_STRAIGHT, cy * CANVAS),
    )
    for cx in COLUMN_CENTRES
    for cy in CARD_CENTRES
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
    # Columns: a vertical capsule, radius LANE_RADIUS. Their top/bottom caps are
    # now the outermost geometry, not the check.
    for (ax, ay), (bx, by) in COLUMN_LINES:
        furthest = max(
            furthest,
            math.hypot(ax - CANVAS / 2, ay - CANVAS / 2) + LANE_RADIUS,
            math.hypot(bx - CANVAS / 2, by - CANVAS / 2) + LANE_RADIUS,
        )
    # Card dashes: horizontal capsules, radius CARD_RADIUS.
    for (ax, ay), (bx, by) in CARD_LINES:
        furthest = max(
            furthest,
            math.hypot(ax - CANVAS / 2, ay - CANVAS / 2) + CARD_RADIUS,
            math.hypot(bx - CANVAS / 2, by - CANVAS / 2) + CARD_RADIUS,
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


def _render(top, mid, bottom, sheen_alpha, column_mask, card_mask, check_mask):
    """Composite the ramp, the sheen, the columns, the cards and the check.

    Each translucent layer is looked up only on rows that contain any of it. Columns
    cover most rows, cards only six short bands, so this keeps the card lookup — the
    one that is mostly zero — off roughly three quarters of the image.

    Returns `(rows, worst_contrast)`: the picture, and the lowest white-on-background
    contrast found at any pixel the mark covers solidly. That number is the real
    legibility gate — the mark is drawn on the sheen, columns and cards too, so the
    raw stops alone cannot answer "can the check be read".
    """
    rows = []

    def active_rows(mask):
        return [any(mask[y * CANVAS + x] for x in range(0, CANVAS, 4)) for y in range(CANVAS)]

    column_rows = active_rows(column_mask)
    card_rows = active_rows(card_mask)

    inv = 1.0 / (CANVAS - 1)
    sheen_cx = SHEEN_CENTRE[0] * CANVAS
    sheen_cy = SHEEN_CENTRE[1] * CANVAS
    sheen_r = SHEEN_RADIUS * CANVAS

    worst = float("inf")

    for y in range(CANVAS):
        row = bytearray(CANVAS * 3)
        off = y * CANVAS
        on_column_row = column_rows[y]
        on_card_row = card_rows[y]
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
            if on_column_row:
                c = column_mask[off + x]
                if c:
                    a = c / 255.0 * LANE_ALPHA
                    r += (ON_ICON[0] - r) * a
                    g += (ON_ICON[1] - g) * a
                    b += (ON_ICON[2] - b) * a
            if on_card_row:
                c = card_mask[off + x]
                if c:
                    a = c / 255.0 * CARD_ALPHA
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


# The macOS tile: inset 100/1024, radius 220/1024, measured off the shipped AppIcon.png.
# Unlike the iOS asset this one keeps an alpha channel, so Finder and the Dock see a
# rounded square against transparency rather than a square against the desktop.
DESKTOP_INSET = 100
DESKTOP_RADIUS = 220


def _write_masked_png(path, rows):
    """The light render clipped to the macOS rounded-square tile, as RGBA."""
    raw = bytearray()
    lo, hi = DESKTOP_INSET, CANVAS - DESKTOP_INSET
    for y in range(CANVAS):
        raw.append(0)
        src = rows[y]
        for x in range(CANVAS):
            # signed distance to the rounded box, in pixels; coverage is a 1px ramp
            qx = abs(x + 0.5 - CANVAS / 2) - ((hi - lo) / 2 - DESKTOP_RADIUS)
            qy = abs(y + 0.5 - CANVAS / 2) - ((hi - lo) / 2 - DESKTOP_RADIUS)
            d = math.sqrt(max(qx, 0.0) ** 2 + max(qy, 0.0) ** 2) + min(max(qx, qy), 0.0) - DESKTOP_RADIUS
            a = int(max(0.0, min(1.0, 0.5 - d)) * 255.0 + 0.5)
            i = x * 3
            raw += bytes((src[i], src[i + 1], src[i + 2], a))
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
        handle.write(chunk(b"IHDR", struct.pack(">IIBBBBB", CANVAS, CANVAS, 8, 6, 0, 0, 0)))
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
    """The columns and cards must not be able to read as a foreground element.

    Both are texture, not competing marks: at the size a launcher actually draws an
    icon, three columns and six dashes in front of a checkmark would be the first thing
    to turn to mush. Past about a quarter alpha the design has to be re-measured at 40px
    rather than trusted.
    """
    for name, alpha in (("column", LANE_ALPHA), ("card", CARD_ALPHA)):
        if alpha > 0.25:
            sys.exit("FAIL: %s alpha %.2f makes it a foreground element" % (name, alpha))


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


def _capsule_paths(lines, scale):
    return [
        'M%s,%s L%s,%s'
        % (_n(_dp(ax, scale)), _n(_dp(ay, scale)), _n(_dp(bx, scale)), _n(_dp(by, scale)))
        for (ax, ay), (bx, by) in lines
    ]


def _stroked_paths(paths, alpha, half_width, scale):
    return "".join(
        """    <path
        android:pathData="%s"
        android:strokeColor="%s"
        android:strokeAlpha="%s"
        android:strokeWidth="%s"
        android:strokeLineCap="round" />
"""
        % (path, _hex(ON_ICON), _n(alpha), _n(half_width * 2 * scale))
        for path in paths
    )


def _check_path(scale):
    pts = [(_dp(x, scale), _dp(y, scale)) for x, y in CHECK_POINTS]
    return "M%s,%s L%s,%s L%s,%s" % (
        _n(pts[0][0]), _n(pts[0][1]),
        _n(pts[1][0]), _n(pts[1][1]),
        _n(pts[2][0]), _n(pts[2][1]),
    )


def _foreground_xml(scale):
    columns = _stroked_paths(_capsule_paths(COLUMN_LINES, scale), LANE_ALPHA, LANE_RADIUS, scale)
    cards = _stroked_paths(_capsule_paths(CARD_LINES, scale), CARD_ALPHA, CARD_RADIUS, scale)
    return """<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by tools/generate_app_icon.py. Do not edit by hand. -->
<!--
  Fitted to the %sdp safe circle rather than to the 72dp safe square: the columns'
  top and bottom caps are the outermost geometry, and a square fit would put them
  outside the circular mask. See android_scale() for why the bound is the capsule
  endpoints and not the bounding box.
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
%s%s    <path
        android:pathData="%s"
        android:strokeColor="%s"
        android:strokeWidth="%s"
        android:strokeLineCap="round"
        android:strokeLineJoin="round" />
</vector>
""" % (
        _n(ANDROID_SAFE_RADIUS),
        columns,
        cards,
        _check_path(scale),
        _hex(ON_ICON),
        _n(CHECK_STROKE * scale),
    )


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
    column_mask = _stroke_mask(COLUMN_LINES, LANE_RADIUS)
    card_mask = _stroke_mask(CARD_LINES, CARD_RADIUS)

    icon_dir = os.path.join(
        REPO, "iosApp", "TaskTracker", "Assets.xcassets", "AppIcon.appiconset"
    )
    drawable_dir = os.path.join(REPO, "androidApp", "src", "main", "res", "drawable")
    desktop_dir = os.path.join(REPO, "shared-ui", "src", "jvmMain", "resources")

    worst = {}
    light_rows = None
    for name, top, mid, bottom, sheen in (
        ("AppIcon.png", GRADIENT_TOP, GRADIENT_MID, GRADIENT_BOTTOM, SHEEN_ALPHA),
        ("AppIcon-Dark.png", DARK_GRADIENT_TOP, DARK_GRADIENT_MID, DARK_GRADIENT_BOTTOM, SHEEN_ALPHA_DARK),
    ):
        path = os.path.join(icon_dir, name)
        rows, worst[name] = _render(top, mid, bottom, sheen, column_mask, card_mask, check_mask)
        _write_png(path, rows)
        print("  wrote %s (%d x %d, no alpha)" % (os.path.relpath(path, REPO), CANVAS, CANVAS))
        if name == "AppIcon.png":
            light_rows = rows
            _preview(rows)

    _assert_legible(worst["AppIcon.png"], worst["AppIcon-Dark.png"])

    desktop_path = os.path.join(desktop_dir, "AppIcon.png")
    _write_masked_png(desktop_path, light_rows)
    print("  wrote %s (%d x %d, rounded-square alpha)"
          % (os.path.relpath(desktop_path, REPO), CANVAS, CANVAS))

    scale = android_scale()
    print()
    print("android scale %.5f dp/px; column width %.1f dp of the 108dp viewport"
          % (scale, LANE_RADIUS * 2 * scale))
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