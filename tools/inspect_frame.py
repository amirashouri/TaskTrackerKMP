#!/usr/bin/env python3
"""
Inspect a rendered frame numerically, and print an ASCII preview of it.

The model's place in this loop matters and is worth stating plainly: it cannot look at an
image. So the visual work is done by measuring pixels rather than viewing them — geometry
from edge detection, legibility from WCAG contrast ratios, and layout from an ASCII map
that shows luminance and hue buckets coarsely enough to read the structure.

That is not a consolation prize. It catches the class of defect that matters here — an
indicator that renders at 4% opacity, a lens that lands one segment to the left, an inset
that is 8dp instead of 16 — all of which are invisible in a description and obvious in a
number. It cannot judge whether a bar is *pretty*. For that, the PNGs are written for a
human to open.

    python3 tools/inspect_frame.py <png> [--ascii W H] [--edge ROW]
"""
import sys
import zlib
import struct


def load(path):
    data = open(path, 'rb').read()
    assert data[:8] == b'\x89PNG\r\n\x1a\n', 'not a PNG'
    pos, idat, meta = 8, [], None
    while pos < len(data):
        length, ctype = struct.unpack('>I4s', data[pos:pos + 8])
        body = data[pos + 8:pos + 8 + length]
        if ctype == b'IHDR':
            w, h, depth, color, *_ = struct.unpack('>IIBBBBB', body)
            assert depth == 8, f'unsupported bit depth {depth}'
            meta = (w, h, color)
        elif ctype == b'IDAT':
            idat.append(body)
        elif ctype == b'IEND':
            break
        pos += 12 + length
    w, h, color = meta
    channels = {0: 1, 2: 3, 4: 2, 6: 4}[color]
    raw = zlib.decompress(b''.join(idat))
    stride = w * channels
    out = bytearray(w * h * 4)
    prev = bytearray(stride)
    pos = 0
    for y in range(h):
        f = raw[pos]
        pos += 1
        line = bytearray(raw[pos:pos + stride])
        pos += stride
        for i in range(stride):
            a = line[i - channels] if i >= channels else 0
            b = prev[i]
            c = prev[i - channels] if i >= channels else 0
            if f == 1:
                line[i] = (line[i] + a) & 255
            elif f == 2:
                line[i] = (line[i] + b) & 255
            elif f == 3:
                line[i] = (line[i] + (a + b) // 2) & 255
            elif f == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 255
        for x in range(w):
            o = (y * w + x) * 4
            i = x * channels
            if color == 6:
                out[o:o + 4] = line[i:i + 4]
            elif color == 2:
                out[o:o + 3] = line[i:i + 3]
                out[o + 3] = 255
            else:
                v = line[i]
                out[o:o + 4] = bytes((v, v, v, 255 if channels == 1 else line[i + 1]))
        prev = line
    return w, h, out


def px(img, w, x, y):
    o = (y * w + x) * 4
    return img[o], img[o + 1], img[o + 2], img[o + 3]


def rel_lum(r, g, b):
    def ch(c):
        c /= 255
        return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4
    return 0.2126 * ch(r) + 0.7152 * ch(g) + 0.0722 * ch(b)


def contrast(a, b):
    la, lb = rel_lum(*a), rel_lum(*b)
    hi, lo = max(la, lb), min(la, lb)
    return (hi + 0.05) / (lo + 0.05)


def ascii_map(img, w, h, cols=100, rows=28):
    """Bucket by luminance. Good for structure; poor for colour."""
    ramp = ' .:-=+*#%@'
    print(f'  {w}x{h} -> {cols}x{rows}')
    for ry in range(rows):
        line = []
        for rx in range(cols):
            xs = int(rx * w / cols)
            ys = min(h - 1, int(ry * h / rows))
            r, g, b, _ = px(img, w, xs, ys)
            lum = rel_lum(r, g, b)
            line.append(ramp[min(int((1 - lum) ** 0.5 * (len(ramp) - 1) + 0.5), len(ramp) - 1)])
        print('  ' + ''.join(line))


def hex_grid(img, w, h, cols=24, rows=8):
    """
    A coarse grid of hex colours.

    Preferred over the ASCII map for this job, and the reason is worth recording: a bar is
    mostly a *tint*, and a luminance ramp renders every step of one hue as the same character.
    The gradient that makes the glass look like glass is invisible, and so is a selection lens
    drawn at 4% opacity - which is exactly the defect a lens is likely to have.
    """
    print(f'  {w}x{h} -> {cols}x{rows} hex')
    for ry in range(rows):
        cells = []
        for rx in range(cols):
            xs = int((rx + 0.5) * w / cols)
            ys = min(h - 1, int((ry + 0.5) * h / rows))
            r, g, b, _ = px(img, w, xs, ys)
            cells.append(f'{r:02x}{g:02x}{b:02x}')
        print('  ' + ' '.join(cells))



def row_profile(img, w, h, y):
    """Per-column luminance along one row: edges show up as steps."""
    vals = [rel_lum(*px(img, w, x, y)[:3]) for x in range(w)]
    print(f'  row {y}: min={min(vals):.3f} max={max(vals):.3f}')
    # Report the largest step-to-step jumps, which is where the real edges are.
    jumps = sorted(
        ((abs(vals[x + 1] - vals[x]), x) for x in range(w - 1)),
        reverse=True,
    )[:14]
    for d, x in sorted(jumps, key=lambda t: t[1]):
        c0, c1 = px(img, w, x, y)[:3], px(img, w, x + 1, y)[:3]
        print(f'    x={x:5d}->{x + 1:5d}  dL={d:.4f}  {c0} -> {c1}')


def main():
    path = sys.argv[1]
    w, h, img = load(path)
    args = sys.argv[2:]
    print(f'{path}  {w}x{h}')
    if '--ascii' in args:
        i = args.index('--ascii')
        ascii_map(img, w, h, int(args[i + 1]), int(args[i + 2]))
    if '--hex' in args:
        i = args.index('--hex')
        hex_grid(img, w, h, int(args[i + 1]), int(args[i + 2]))
    if '--edge' in args:
        row_profile(img, w, h, int(args[args.index('--edge') + 1]))
    if '--sample' in args:
        for spec in args[args.index('--sample') + 1].split(','):
            x, y = (int(v) for v in spec.split(':'))
            print(f'  ({x},{y}) = {px(img, w, x, y)}')


if __name__ == '__main__':
    main()
