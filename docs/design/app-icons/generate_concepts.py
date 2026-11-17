#!/usr/bin/env python3
"""Render TaskTracker app-icon CONCEPTS from one dependency-free geometry file.

This is a design-exploration tool. It writes to docs/design/app-icons/ and does
NOT touch the shipped launcher assets (those belong to tools/generate_app_icon.py).

Why no Pillow/numpy: the repo's shipped generator already establishes that the
build must not need a pip install. PNG is a zlib stream in four chunks, so the
whole renderer is stdlib. Anti-aliasing is analytic signed-distance coverage at
one sample per pixel, which stays exact at the round checkmark caps.

    python3 docs/design/app-icons/generate_concepts.py
"""

import math
import os
import struct
import zlib

N = 1024
HERE = os.path.dirname(os.path.abspath(__file__))

# --- the brand ramp (tools/generate_app_icon.py tokens) ---------------------------------------
TOP = (0x4A, 0x72, 0xB4)
MID = (0x34, 0x53, 0x7A)
BOT = (0x16, 0x22, 0x3A)
DARK_TOP = (0x1E, 0x2A, 0x3A)
DARK_MID = (0x14, 0x1C, 0x28)
DARK_BOT = (0x0D, 0x13, 0x20)

GLASS_TOP = (0x75, 0x99, 0xD2)
GLASS_MID = (0x45, 0x6B, 0xA6)
GLASS_BOT = (0x20, 0x34, 0x58)

WHITE = (255, 255, 255)

# --- sdf primitives (all in unit space, distance returned in unit space) ----------------------
def clamp(v, lo=0.0, hi=1.0):
    return lo if v < lo else (hi if v > hi else v)


def sd_segment(px, py, ax, ay, bx, by):
    pax, pay = px - ax, py - ay
    bax, bay = bx - ax, by - ay
    den = bax * bax + bay * bay
    h = clamp((pax * bax + pay * bay) / den) if den else 0.0
    dx, dy = pax - bax * h, pay - bay * h
    return math.sqrt(dx * dx + dy * dy)


def sd_capsule(px, py, ax, ay, bx, by, r):
    return sd_segment(px, py, ax, ay, bx, by) - r


def sd_round_box(px, py, cx, cy, hx, hy, r):
    qx = abs(px - cx) - (hx - r)
    qy = abs(py - cy) - (hy - r)
    mx, my = max(qx, 0.0), max(qy, 0.0)
    return math.sqrt(mx * mx + my * my) + min(max(qx, qy), 0.0) - r


def sd_polygon(px, py, pts):
    d = (px - pts[0][0]) ** 2 + (py - pts[0][1]) ** 2
    s = 1.0
    count = len(pts)
    for i in range(count):
        j = i - 1
        xi, yi = pts[i]
        xj, yj = pts[j]
        ex, ey = xj - xi, yj - yi
        wx, wy = px - xi, py - yi
        den = ex * ex + ey * ey
        h = clamp((wx * ex + wy * ey) / den) if den else 0.0
        bx, by = wx - ex * h, wy - ey * h
        dd = bx * bx + by * by
        if dd < d:
            d = dd
        c1 = py >= yi
        c2 = py < yj
        c3 = ex * wy > ey * wx
        if (c1 and c2 and c3) or ((not c1) and (not c2) and (not c3)):
            s = -s
    return s * math.sqrt(d)


def sd_ring(px, py, cx, cy, radius, half):
    return abs(math.hypot(px - cx, py - cy) - radius) - half


# --- shape helpers: each returns a coverage factor in [0, 1] ----------------------------------
def fill(d):
    return clamp(0.5 - d * N)


def stroke(d, half):
    return clamp((half - abs(d)) * N + 0.5)


def check(px, py, p0, p1, p2, r):
    d = min(
        sd_capsule(px, py, p0[0], p0[1], p1[0], p1[1], r),
        sd_capsule(px, py, p1[0], p1[1], p2[0], p2[1], r),
    )
    return fill(d)


# --- colour helpers ---------------------------------------------------------------------------
def ramp(t, a, b, c):
    t = clamp(t)
    if t < 0.5:
        k = t / 0.5
        x, y = a, b
    else:
        k = (t - 0.5) / 0.5
        x, y = b, c
    return (
        x[0] + (y[0] - x[0]) * k,
        x[1] + (y[1] - x[1]) * k,
        x[2] + (y[2] - x[2]) * k,
    )


# --- the renderer -----------------------------------------------------------------------------
def render(path, concept, stops=(TOP, MID, BOT), sheen=0.09, rim=None, sweep=False):
    buf = bytearray(N * N * 4)
    tile_hx = tile_hy = 0.42
    tile_r = 0.20
    a, b, c = stops

    for y in range(N):
        py = (y + 0.5) / N
        row = y * N * 4
        for x in range(N):
            px = (x + 0.5) / N
            dt = sd_round_box(px, py, 0.5, 0.5, tile_hx, tile_hy, tile_r)
            cov = fill(dt)
            if cov <= 0.0:
                continue
            idx = row + x * 4

            # base gradient along the top-left -> bottom-right diagonal
            r, g, bl = ramp((px + py) / 2.0, a, b, c)

            # sheen: white radial light, centre inside the safe circle
            sd = math.hypot(px - 0.33, py - 0.28)
            if sd < 0.55:
                s = (1.0 - sd / 0.55) * sheen * 255.0
                r += s
                g += s
                bl += s

            # optional diagonal specular band (Liquid Glass concept)
            if sweep:
                band = 1.0 - abs((px + py) - 0.86) / 0.24
                if band > 0.0:
                    s = band * band * 0.10 * 255.0
                    r += s
                    g += s
                    bl += s

            layers = concept(px, py)
            for f in layers:
                if f > 0.0:
                    f = min(f, 1.0)
                    inv = 1.0 - f
                    r = r * inv + WHITE[0] * f
                    g = g * inv + WHITE[1] * f
                    bl = bl * inv + WHITE[2] * f

            if rim is not None:
                rf = stroke(dt + rim[1], rim[1]) * rim[0]
                if rf > 0.0:
                    rf = min(rf, 1.0)
                    inv = 1.0 - rf
                    r = r * inv + WHITE[0] * rf
                    g = g * inv + WHITE[1] * rf
                    bl = bl * inv + WHITE[2] * rf

            buf[idx] = int(clamp(r, 0, 255))
            buf[idx + 1] = int(clamp(g, 0, 255))
            buf[idx + 2] = int(clamp(bl, 0, 255))
            buf[idx + 3] = int(cov * 255.0 + 0.5)

    write_png(path, buf)


def write_png(path, buf):
    def chunk(tag, data):
        return (
            struct.pack(">I", len(data))
            + tag
            + data
            + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
        )

    raw = bytearray()
    for y in range(N):
        raw.append(0)
        raw.extend(buf[y * N * 4 : (y + 1) * N * 4])
    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", N, N, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)
    print("wrote", os.path.relpath(path, HERE), os.path.getsize(path), "bytes")


# --- concepts ---------------------------------------------------------------------------------
def signature(px, py):
    """Recommended: the mark, refined. Completes the lanes it checks off."""
    layers = []
    for ly in (0.315, 0.50, 0.685):
        d = sd_capsule(px, py, 0.275, ly, 0.72, ly, 0.020)
        layers.append(fill(d) * 0.15)
    chk = check(px, py, (0.33, 0.64), (0.46, 0.64), (0.71, 0.36), 0.047)
    layers.append(chk)
    return layers


def board(px, py):
    """Boards and sprints: three lanes, each with cards, one checked."""
    layers = []
    for cx in (0.31, 0.5, 0.69):
        d = sd_round_box(px, py, cx, 0.5, 0.078, 0.235, 0.072)
        layers.append(fill(d) * 0.15)
        for cy in (0.425, 0.575):
            dc = sd_capsule(px, py, cx - 0.039, cy, cx + 0.039, cy, 0.012)
            layers.append(fill(dc) * 0.28)
    chk = check(px, py, (0.38, 0.54), (0.468, 0.54), (0.63, 0.39), 0.038)
    layers.append(chk)
    return layers


SHIELD = [
    (0.5, 0.21), (0.715, 0.295), (0.715, 0.515), (0.655, 0.655),
    (0.5, 0.78), (0.345, 0.655), (0.285, 0.515), (0.285, 0.295),
]


def vault(px, py):
    """Your server. Your rules. A shield with the mark inside."""
    layers = []
    d = sd_polygon(px, py, SHIELD)
    layers.append(fill(d) * 0.10)
    layers.append(stroke(d, 0.009) * 0.85)
    chk = check(px, py, (0.40, 0.515), (0.475, 0.515), (0.61, 0.375), 0.037)
    layers.append(chk)
    return layers


def glass(px, py):
    """Liquid Glass: the mark on a lit, refractive lens."""
    layers = []
    lens = sd_round_box(px, py, 0.5, 0.5, 0.30, 0.30, 0.16)
    layers.append(fill(lens) * 0.06)
    layers.append(stroke(lens + 0.010, 0.010) * 0.22)
    # checkmark with a soft offset impression beneath it
    soft = check(px - 0.012, py - 0.014, (0.35, 0.63), (0.46, 0.63), (0.70, 0.38), 0.046)
    layers.append(soft * 0.16)
    chk = check(px, py, (0.35, 0.63), (0.46, 0.63), (0.70, 0.38), 0.044)
    layers.append(chk)
    return layers


def sync(px, py):
    """Offline-first: the mark inside a sync orbit."""
    layers = []
    d = sd_ring(px, py, 0.5, 0.5, 0.255, 0.014)
    ang = math.degrees(math.atan2(py - 0.5, px - 0.5))
    # leave a gap at the upper right so the orbit reads as a sync arrow
    if -48.0 <= ang <= 12.0:
        d = 1.0
    layers.append(fill(d) * 0.85)
    # arrow head at the gap: a solid triangle whose tip follows the orbit
    arrow = [(0.700, 0.344), (0.646, 0.322), (0.686, 0.282)]
    layers.append(fill(sd_polygon(px, py, arrow)) * 0.88)
    chk = check(px, py, (0.415, 0.525), (0.48, 0.525), (0.60, 0.405), 0.034)
    layers.append(chk)
    return layers


def essential(px, py):
    """Essential: one bold mark, legible down to 16px. No lanes, no noise."""
    return [check(px, py, (0.30, 0.625), (0.455, 0.625), (0.72, 0.35), 0.058)]


def main():
    render(os.path.join(HERE, "01-signature.png"), signature)
    render(
        os.path.join(HERE, "01-signature-dark.png"),
        signature,
        stops=(DARK_TOP, DARK_MID, DARK_BOT),
        sheen=0.10,
    )
    render(os.path.join(HERE, "02-board.png"), board)
    render(
        os.path.join(HERE, "02-board-dark.png"),
        board,
        stops=(DARK_TOP, DARK_MID, DARK_BOT),
        sheen=0.10,
    )
    render(os.path.join(HERE, "03-vault.png"), vault)
    render(
        os.path.join(HERE, "04-glass.png"),
        glass,
        stops=(GLASS_TOP, GLASS_MID, GLASS_BOT),
        sheen=0.13,
        rim=(0.28, 0.006),
        sweep=True,
    )
    render(os.path.join(HERE, "05-sync.png"), sync)
    render(os.path.join(HERE, "06-essential.png"), essential, sheen=0.05)


if __name__ == "__main__":
    main()
