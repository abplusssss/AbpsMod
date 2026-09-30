#!/usr/bin/env python3
"""
Generates the particle textures used by the client effects engine (no dependencies).

The glow textures store their light in the RGB channels and keep alpha at 255, so they can be drawn with additive
blending: black adds nothing, so the edges fade out smoothly. The smoke texture uses alpha instead and is drawn
normally. Run from the repository root:  python3 tools/gen_fx_textures.py
"""
import math, os, random, struct, zlib

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "client", "resources", "assets", "abpsmod", "textures", "particle")
JSON_OUT = os.path.join(os.path.dirname(__file__), "..", "src", "client", "resources", "assets", "abpsmod", "particles")
SIZE = 128


def smooth(edge0, edge1, x):
    t = max(0.0, min(1.0, (x - edge0) / (edge1 - edge0)))
    return t * t * (3 - 2 * t)


def write_png(path, pixels, size):
    raw = bytearray()
    for y in range(size):
        raw.append(0)
        for x in range(size):
            raw.extend(pixels[y][x])
    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xffffffff)
    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)


def light(v, warm=0.0):
    """Turns an intensity into a white pixel, slightly warm in the hot core."""
    v = max(0.0, min(1.0, v))
    r = v
    g = v ** (1.0 + 0.10 * warm)
    b = v ** (1.0 + 0.25 * warm)
    return (int(r * 255), int(g * 255), int(b * 255), 255)


def render(name, fn):
    px = []
    for j in range(SIZE):
        row = []
        for i in range(SIZE):
            x = (i + 0.5) / SIZE * 2 - 1
            y = (j + 0.5) / SIZE * 2 - 1
            row.append(fn(x, y))
        px.append(row)
    write_png(os.path.join(OUT, name + ".png"), px, SIZE)
    with open(os.path.join(JSON_OUT, name + ".json"), "w") as f:
        f.write('{\n  "textures": [\n    "abpsmod:%s"\n  ]\n}\n' % name)
    print("wrote", name)


def edge_fade(r):
    return 1.0 - smooth(0.80, 1.0, r)


def glow(x, y):
    r = math.hypot(x, y)
    core = 0.8 * math.exp(-(r * 6.2) ** 2)
    halo = math.exp(-(r * 2.6) ** 2) * 0.5
    body = max(0.0, 1 - r) ** 2.4 * 0.3
    return light((core + halo + body) * edge_fade(r), 0.6)


def spark(x, y):
    r = math.hypot(x, y)
    ax, ay = abs(x), abs(y)
    hor = math.exp(-ay * 34) * math.exp(-ax * 2.6)
    ver = math.exp(-ax * 34) * math.exp(-ay * 2.6)
    d1 = math.exp(-abs(x - y) * 26) * math.exp(-r * 4.5) * 0.45
    d2 = math.exp(-abs(x + y) * 26) * math.exp(-r * 4.5) * 0.45
    core = math.exp(-(r * 6.5) ** 2)
    halo = math.exp(-(r * 2.6) ** 2) * 0.35
    return light((max(hor, ver) + d1 + d2 + core + halo) * edge_fade(r), 0.5)


def flare(x, y):
    r = math.hypot(x, y)
    streak = math.exp(-abs(y) * 30) * math.exp(-abs(x) * 1.9)
    cross = math.exp(-abs(x) * 30) * math.exp(-abs(y) * 4.5) * 0.5
    core = math.exp(-(r * 5.0) ** 2)
    halo = math.exp(-(r * 1.9) ** 2) * 0.4
    return light((streak + cross + core + halo) * edge_fade(max(abs(x), abs(y))), 0.6)


def ring(x, y):
    r = math.hypot(x, y)
    line = math.exp(-((r - 0.86) / 0.040) ** 2)
    halo = 0.22 * math.exp(-((r - 0.86) / 0.16) ** 2)
    inner = 0.06 * smooth(0.0, 0.86, r) * (1 - smooth(0.86, 0.92, r))
    return light((line + halo + inner) * (1.0 - smooth(0.93, 1.0, r)), 0.3)


def shard(x, y):
    # a faceted diamond with a bright edge, for debris and crystals
    d = abs(x) / 0.42 + abs(y) / 0.95
    body = 1.0 - smooth(0.80, 1.0, d)
    edge = math.exp(-((d - 0.92) / 0.06) ** 2)
    facet = 0.25 + 0.35 * (1 if x * y > 0 else 0.6)
    return light((body * facet + edge * 0.9), 0.4)


def slash(x, y):
    # a crescent opening to the right
    r = math.hypot(x, y)
    inner = math.hypot(x - 0.34, y)
    outer_mask = 1.0 - smooth(0.90, 0.98, r)
    inner_mask = smooth(0.80, 0.86, inner)
    crescent = outer_mask * inner_mask
    angle = math.atan2(y, x)
    along = 0.55 + 0.45 * math.cos(angle * 0.9)
    edge = math.exp(-((r - 0.93) / 0.05) ** 2)
    return light(crescent * along * 0.85 + edge * 0.5 * inner_mask, 0.3)


def sigil(x, y):
    r = math.hypot(x, y)
    ang = math.atan2(y, x)
    outer = math.exp(-((r - 0.94) / 0.02) ** 2)
    outer2 = 0.6 * math.exp(-((r - 0.88) / 0.012) ** 2)
    mid = 0.9 * math.exp(-((r - 0.62) / 0.018) ** 2)
    inn = 0.7 * math.exp(-((r - 0.30) / 0.014) ** 2)
    v = outer + outer2 + mid + inn
    # rune ticks between the outer rings
    seg = (ang / (2 * math.pi) * 48) % 1.0
    if 0.86 <= r <= 0.94 and 0.35 < seg < 0.65:
        v += 0.75
    # glyph bars between the middle rings, longer every fourth one
    seg2 = (ang / (2 * math.pi) * 24) % 1.0
    reach = 0.86 if int(ang / (2 * math.pi) * 24) % 4 == 0 else 0.76
    if 0.64 <= r <= reach and 0.45 < seg2 < 0.55:
        v += 0.7
    # six-point star inside
    for k in range(6):
        a = k * math.pi / 3
        dx = x * math.cos(a) + y * math.sin(a)
        dy = -x * math.sin(a) + y * math.cos(a)
        if abs(dy) < 0.012 and abs(dx) < 0.62:
            v += 0.35
    v += 0.5 * math.exp(-(r * 9) ** 2)
    return light(v * (1.0 - smooth(0.96, 1.0, r)), 0.2)


_rng = random.Random(7)
_noise = [[_rng.random() for _ in range(9)] for _ in range(9)]


def vnoise(x, y):
    xi, yi = int(math.floor(x)), int(math.floor(y))
    xf, yf = x - xi, y - yi
    def n(a, b):
        return _noise[b % 9][a % 9]
    u, v = xf * xf * (3 - 2 * xf), yf * yf * (3 - 2 * yf)
    return (n(xi, yi) * (1 - u) + n(xi + 1, yi) * u) * (1 - v) + (n(xi, yi + 1) * (1 - u) + n(xi + 1, yi + 1) * u) * v


def smoke(x, y):
    r = math.hypot(x, y)
    n = 0.6 * vnoise((x + 1) * 2.4, (y + 1) * 2.4) + 0.4 * vnoise((x + 1) * 5.0 + 3, (y + 1) * 5.0 + 3)
    a = (1.0 - smooth(0.15, 0.95, r)) * (0.55 + 0.6 * n)
    a = max(0.0, min(1.0, a))
    shade = int(215 + 40 * n)
    return (shade, shade, shade, int(a * 255))


os.makedirs(OUT, exist_ok=True)
os.makedirs(JSON_OUT, exist_ok=True)
render("fx_glow", glow)
render("fx_spark", spark)
render("fx_flare", flare)
render("fx_ring", ring)
render("fx_shard", shard)
render("fx_slash", slash)
render("fx_sigil", sigil)
render("fx_smoke", smoke)
