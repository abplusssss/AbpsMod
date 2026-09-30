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


def render_variants(name, fns):
    """Writes several textures and one particle json listing all of them; the game picks one at random per particle."""
    names = []
    for k, fn in enumerate(fns):
        vn = "%s_%d" % (name, k)
        px = []
        for j in range(SIZE):
            row = []
            for i in range(SIZE):
                row.append(fn((i + 0.5) / SIZE * 2 - 1, (j + 0.5) / SIZE * 2 - 1))
            px.append(row)
        write_png(os.path.join(OUT, vn + ".png"), px, SIZE)
        names.append(vn)
    with open(os.path.join(JSON_OUT, name + ".json"), "w") as f:
        f.write('{\n  "textures": [\n' + ",\n".join('    "abpsmod:%s"' % n for n in names) + '\n  ]\n}\n')
    print("wrote", name, len(fns))


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



# ---------------------------------------------------------------- helpers for the themed sprites

def aa(d, w=0.03):
    """Antialiased inside test from a signed distance (negative = inside)."""
    return 1.0 - smooth(-w, w, d)


def rgba(shade, a):
    s = int(max(0.0, min(1.0, shade)) * 255)
    return (s, s, s, int(max(0.0, min(1.0, a)) * 255))


def seg_dist(px, py, ax, ay, bx, by):
    dx, dy = bx - ax, by - ay
    l2 = dx * dx + dy * dy
    t = 0.0 if l2 == 0 else max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / l2))
    return math.hypot(px - (ax + t * dx), py - (ay + t * dy))


def rot(x, y, a):
    c, s_ = math.cos(a), math.sin(a)
    return x * c - y * s_, x * s_ + y * c


# ---------------------------------------------------------------- additive (light) sprites

def make_flame(seed):
    rng = random.Random(seed)
    phase = rng.random() * 6.28
    sway = 0.10 + rng.random() * 0.10
    lean = (rng.random() - 0.5) * 0.25
    def fn(x, y):
        t = (0.92 - y) / 1.84          # 0 at the bottom, 1 at the tip
        if t < 0 or t > 1:
            return light(0)
        x0 = sway * math.sin(t * 5.0 + phase) * t + lean * t
        w = 0.40 * math.sin(math.pi * min(1.0, t ** 0.62)) * (1 - t) ** 0.35 + 0.012
        nd = abs(x - x0) / w
        body = (1 - smooth(0.35, 1.0, nd)) * (0.35 + 0.65 * (1 - t) ** 0.8)
        core = math.exp(-(nd * 1.6) ** 2) * (1 - t) ** 1.4
        base = math.exp(-(((y - 0.7) / 0.22) ** 2) - (x / 0.30) ** 2) * 0.7
        return light((body * 0.75 + core * 0.8 + base) * (1 - smooth(0.9, 1.0, math.hypot(x, y * 0.95))), 0.9)
    return fn


def ember(x, y):
    t = (0.85 - y) / 1.5
    r = math.hypot(x, y - 0.25)
    head = math.exp(-(r * 3.4) ** 2)
    core = math.exp(-(r * 8.0) ** 2)
    tail = 0.0
    if 0 < t < 1:
        tail = math.exp(-(x / (0.16 * (1 - t) + 0.02)) ** 2) * (1 - t) ** 1.3 * 0.55
    return light((head * 0.7 + core + tail) * edge_fade(math.hypot(x, y)), 0.8)


def streak(x, y):
    b = smooth(-1.0, 0.85, x)
    th = 0.030 + 0.045 * (x + 1) / 2
    line = math.exp(-(y / th) ** 2) * b ** 1.5
    head = math.exp(-(((x - 0.82) / 0.13) ** 2 + (y / 0.10) ** 2))
    halo = math.exp(-(y / 0.22) ** 2) * b ** 3 * 0.25
    return light((line + head * 0.9 + halo) * (1 - smooth(0.9, 1.0, abs(x))) * (1 - smooth(0.85, 1.0, abs(y))), 0.3)


def wisp(x, y):
    t = (0.55 - y) / 1.5
    hr = math.hypot(x, y - 0.55)
    head = math.exp(-(hr / 0.17) ** 2) + 0.35 * math.exp(-(hr / 0.34) ** 2)
    tail = 0.0
    if 0 < t < 1:
        x0 = 0.28 * math.sin(t * 6.2) * t
        w = 0.15 * (1 - t) ** 1.1 + 0.015
        tail = math.exp(-((x - x0) / w) ** 2) * (1 - t) ** 1.2 * 0.85
    return light((head + tail) * edge_fade(math.hypot(x, y)), 0.1)


def make_rune(seed):
    rng = random.Random(seed)
    nodes = [(gx * 0.55, gy * 0.55) for gx in (-1, 0, 1) for gy in (-1, 0, 1)]
    segs = []
    for _ in range(rng.randint(4, 6)):
        a, b = rng.sample(nodes, 2)
        segs.append((a, b))
    def fn(x, y):
        d = min(seg_dist(x, y, a[0], a[1], b[0], b[1]) for a, b in segs)
        v = math.exp(-(d / 0.035) ** 2) + 0.3 * math.exp(-(d / 0.11) ** 2)
        return light(v * edge_fade(math.hypot(x, y) * 1.05), 0.2)
    return fn


def make_bolt(seed):
    rng = random.Random(seed)
    def build(x0, y0, x1, y1, depth, off):
        if depth == 0:
            return [(x0, y0, x1, y1)]
        mx, my = (x0 + x1) / 2 + (rng.random() - 0.5) * off, (y0 + y1) / 2
        return build(x0, y0, mx, my, depth - 1, off * 0.55) + build(mx, my, x1, y1, depth - 1, off * 0.55)
    main = build(0.0, -0.98, 0.0, 0.98, 5, 0.55)
    branch = []
    for k in (1, 2):
        s0 = main[len(main) * k // 3]
        branch += build(s0[0], s0[1], s0[0] + (rng.random() - 0.5) * 1.3, s0[1] + 0.5 + rng.random() * 0.3, 3, 0.3)
    def fn(x, y):
        d = min(seg_dist(x, y, a, b, c, e) for a, b, c, e in main)
        db = min(seg_dist(x, y, a, b, c, e) for a, b, c, e in branch)
        v = math.exp(-(d / 0.028) ** 2) + 0.35 * math.exp(-(d / 0.12) ** 2) + 0.55 * math.exp(-(db / 0.022) ** 2) + 0.15 * math.exp(-(db / 0.08) ** 2)
        return light(v * edge_fade(max(abs(x), abs(y) * 0.98)), 0.1)
    return fn


def swirl(x, y):
    r = math.hypot(x, y)
    th = math.atan2(y, x)
    arm = max(0.0, math.cos(3 * th - 7.5 * r)) ** 5
    arm2 = max(0.0, math.cos(5 * th + 11 * r + 1.0)) ** 8 * 0.4
    env = smooth(0.04, 0.32, r) * (1 - smooth(0.62, 1.0, r))
    return light((arm + arm2) * env * 0.9 + 0.3 * math.exp(-(r * 8) ** 2), 0.1)


def rays(x, y):
    r = math.hypot(x, y)
    th = math.atan2(y, x)
    a = max(0.0, math.cos(th * 10)) ** 10 * max(0.0, 1 - r) ** 1.3
    b = max(0.0, math.cos(th * 5 + 0.6)) ** 16 * max(0.0, 1 - r) ** 0.8 * 0.6
    core = math.exp(-(r * 5.0) ** 2)
    return light((a + b) * 0.9 + core, 0.5)


def claw(x, y):
    v = 0.0
    for off in (-0.42, 0.0, 0.42):
        s = (x - y) / 1.414
        u = (x + y) / 1.414 - off
        shift = 0.16 * (1 - min(1.0, (s / 1.0) ** 2))
        w = 0.085 * max(0.0, 1 - (s / 1.02) ** 2) ** 0.7 + 0.004
        d = abs(u - shift) / w
        v += (1 - smooth(0.4, 1.0, d)) * (0.5 + 0.5 * (1 - abs(s))) + 0.25 * math.exp(-((u - shift) / 0.1) ** 2) * (1 - abs(s))
    return light(v * (1 - smooth(0.92, 1.0, max(abs(x), abs(y)))), 0.4)


def arrow(x, y):
    shaft = math.exp(-(y / 0.028) ** 2) * (1 - smooth(0.5, 0.56, x)) * smooth(-0.95, -0.6, x)
    head_w = max(0.0, (0.98 - x) / 0.5) * 0.30
    head = (1 - smooth(0.6, 1.0, abs(y) / max(head_w, 0.001))) * (1 if 0.48 < x < 0.98 else 0)
    fl = 0.0
    for sgn in (-1, 1):
        fl = max(fl, math.exp(-(seg_dist(x, y, -0.92, 0, -0.68, sgn * 0.2) / 0.02) ** 2))
        fl = max(fl, math.exp(-(seg_dist(x, y, -0.8, 0, -0.56, sgn * 0.2) / 0.02) ** 2))
    tip = math.exp(-(((x - 0.95) / 0.1) ** 2 + (y / 0.1) ** 2))
    glow_ = 0.2 * math.exp(-(y / 0.2) ** 2) * smooth(-1, 0.6, x)
    return light((shaft + head * 0.9 + fl * 0.8 + tip + glow_) * (1 - smooth(0.93, 1.0, abs(x))), 0.4)


def heart(x, y):
    X, Y = x * 1.3, -y * 1.3 + 0.1
    f = (X * X + Y * Y - 1) ** 3 - X * X * Y ** 3
    g = math.copysign(abs(f) ** (1 / 3), f)
    inside = 1 - smooth(-0.04, 0.04, g)
    edge = math.exp(-(g / 0.07) ** 2)
    shade = 0.6 + 0.4 * math.exp(-(((x + 0.3) / 0.4) ** 2 + ((y + 0.25) / 0.35) ** 2))
    return light(inside * shade * 0.85 + edge * 0.8, 0.2)


def skull(x, y):
    cran = ((x / 0.62) ** 2 + ((y + 0.22) / 0.58) ** 2) - 1
    jaw = max(abs(x) / 0.36, abs(y - 0.5) / 0.30) - 1
    d = min(cran, jaw) * 0.5
    fill = aa(d, 0.05)
    eyes = max(aa(((x - 0.25) / 0.17) ** 2 + ((y + 0.15) / 0.2) ** 2 - 1, 0.15), aa(((x + 0.25) / 0.17) ** 2 + ((y + 0.15) / 0.2) ** 2 - 1, 0.15))
    nose = aa(abs(x) / 0.07 + abs(y - 0.18) / 0.13 - 1, 0.2)
    teeth = 0.0
    if 0.38 < y < 0.75 and abs(x) < 0.34 and int((x + 0.34) / 0.085) % 2 == 0 and y > 0.55:
        teeth = 0.45
    edge = math.exp(-(d / 0.045) ** 2)
    v = fill * (0.62 - 0.6 * max(eyes, nose)) - teeth * 0.4 + edge * 0.6
    halo = 0.22 * math.exp(-(max(d, 0) / 0.2) ** 2)
    return light((v + halo) * edge_fade(math.hypot(x, y) * 0.95), 0.1)


def shockwave(x, y):
    r = math.hypot(x, y)
    edge = math.exp(-((r - 0.86) / 0.055) ** 2)
    trail = 0.42 * smooth(0.25, 0.86, r) ** 2.4 * (1 - smooth(0.86, 0.9, r))
    return light((edge + trail) * (1 - smooth(0.92, 1.0, r)), 0.3)


# ---------------------------------------------------------------- alpha-blended sprites

def make_leaf(seed):
    rng = random.Random(seed)
    curve = (rng.random() - 0.5) * 0.35
    ang = (rng.random() - 0.5) * 0.4
    def fn(x, y):
        xr, yr = rot(x, y, ang)
        t = (yr + 0.92) / 1.84
        if t < 0 or t > 1:
            return rgba(0, 0)
        cx = curve * math.sin(t * math.pi)
        w = 0.44 * math.sin(math.pi * t ** 0.85) ** 0.9
        d = abs(xr - cx) - w
        vein = math.exp(-((xr - cx) / 0.018) ** 2) * (1 - t) * 0.5 + math.exp(-((abs(xr - cx) - (t * 0.5 + 0.05) * 0.4) / 0.015) ** 2) * 0.12 * (1 if 0.15 < t < 0.85 else 0)
        shade = 0.72 + 0.28 * (0.5 - abs(xr - cx) / max(w, 0.02) * 0.5) - vein * 0.35
        return rgba(shade, aa(d, 0.035))
    return fn


def make_petal(seed):
    rng = random.Random(seed)
    def fn(x, y):
        xr, yr = rot(x, y, (rng.random() - 0.5) * 0.0)
        t = (yr + 0.8) / 1.6
        if t < 0 or t > 1:
            return rgba(0, 0)
        w = 0.55 * math.sin(math.pi * t ** 0.7) * (1 - 0.35 * t)
        d = abs(xr) - w
        shade = 0.85 + 0.15 * (1 - t) - 0.15 * abs(xr) / max(w, 0.02)
        return rgba(shade, aa(d, 0.04))
    return fn


def droplet(x, y):
    t = (y + 0.9) / 1.2
    if y < 0.3:
        w = 0.5 * max(0.0, t) ** 1.5 if t > 0 else 0
        d = abs(x) - w if t > 0 else 1
    else:
        d = math.hypot(x, y - 0.3) - 0.5
    inside = aa(d, 0.045)
    shade = 0.62 + 0.3 * (1 - (y + 0.9) / 1.8) + 0.5 * math.exp(-(((x + 0.16) / 0.11) ** 2 + ((y - 0.15) / 0.2) ** 2))
    rim = math.exp(-(d / 0.06) ** 2) * 0.25
    return rgba(min(1.0, shade + rim), inside * 0.92)


def bubble(x, y):
    r = math.hypot(x, y)
    rimv = math.exp(-((r - 0.82) / 0.07) ** 2)
    fill = 0.14 * (1 - smooth(0.0, 0.82, r)) + 0.1 * smooth(0.5, 0.82, r) * (1 - smooth(0.82, 0.9, r))
    spec = math.exp(-(((x + 0.32) / 0.13) ** 2 + ((y + 0.34) / 0.07) ** 2))
    spec2 = 0.5 * math.exp(-(((x - 0.3) / 0.06) ** 2 + ((y - 0.38) / 0.06) ** 2))
    a = rimv * 0.8 + fill + spec + spec2
    return rgba(0.95, min(1.0, a) * (1 - smooth(0.92, 1.0, r)))


def make_foam(seed):
    rng = random.Random(seed)
    bubbles = []
    for _ in range(46):
        a = rng.random() * 6.283
        rr = math.sqrt(rng.random()) * 0.72
        bubbles.append((math.cos(a) * rr, math.sin(a) * rr, 0.05 + rng.random() ** 2 * 0.2))
    def fn(x, y):
        best = 0.0
        shade = 0.9
        for bx, by, br in bubbles:
            d = math.hypot(x - bx, y - by) - br
            a = aa(d, 0.02)
            if a > best:
                best = a
                shade = 0.82 + 0.18 * (1 - min(1.0, math.hypot(x - bx, y - by) / br)) - 0.1 * max(0.0, d / br + 0.2)
        fade = 1 - smooth(0.6, 1.0, math.hypot(x, y))
        return rgba(shade, best * 0.9 * fade)
    return fn


def watersheet(x, y):
    fx = 1 - smooth(0.62, 1.0, abs(x))
    fy = 1 - smooth(0.62, 1.0, abs(y))
    n = 0.6 * vnoise((x + 1) * 1.6, (y + 1) * 5.0) + 0.4 * vnoise((x + 1) * 3.4 + 5, (y + 1) * 9.0 + 2)
    caustic = math.exp(-((math.sin((x * 5.0 + n * 3.0) * 3.14159) * 0.5 + 0.5 - 0.92) / 0.05) ** 2) * 0.16
    shade = 0.72 + 0.3 * n + caustic
    return rgba(shade, fx * fy * 0.88)


def make_debris(seed):
    rng = random.Random(seed)
    n = rng.randint(5, 7)
    pts = []
    for k in range(n):
        a = 6.283 * k / n + (rng.random() - 0.5) * 0.5
        r = 0.55 + rng.random() * 0.35
        pts.append((math.cos(a) * r, math.sin(a) * r))
    def inside(x, y):
        sgn = 0
        for k in range(n):
            ax, ay = pts[k]
            bx, by = pts[(k + 1) % n]
            cr = (bx - ax) * (y - ay) - (by - ay) * (x - ax)
            if cr < 0:
                return False
        return True
    def dist_edge(x, y):
        return min(seg_dist(x, y, pts[k][0], pts[k][1], pts[(k + 1) % n][0], pts[(k + 1) % n][1]) for k in range(n))
    def fn(x, y):
        if not inside(x, y):
            return rgba(0, 0)
        e = dist_edge(x, y)
        facet = 0.55 + 0.35 * math.sin(math.atan2(y, x) * 2.0 + seed) + 0.1 * vnoise((x + 1) * 3, (y + 1) * 3)
        shade = facet * (0.6 + 0.4 * smooth(0.0, 0.12, e))
        return rgba(shade, 1.0 if e > 0.02 else e / 0.02)
    return fn


def _crack_field(x, y):
    rng = random.Random(11)
    v = 0.0
    r = math.hypot(x, y)
    for k in range(9):
        a = 6.283 * k / 9 + (rng.random() - 0.5) * 0.4
        px, py = 0.0, 0.0
        ang = a
        length = 0.55 + rng.random() * 0.4
        steps = 8
        for s in range(steps):
            ang += (rng.random() - 0.5) * 0.9
            nx, ny = px + math.cos(ang) * length / steps, py + math.sin(ang) * length / steps
            d = seg_dist(x, y, px, py, nx, ny)
            w = 0.03 * (1 - s / steps) + 0.006
            v = max(v, math.exp(-(d / w) ** 2))
            px, py = nx, ny
    return v * (1 - smooth(0.85, 1.0, r))


def crack(x, y):
    v = _crack_field(x, y)
    return (22, 16, 16, int(min(1.0, v * 1.1) * 235))


def make_splat(seed):
    rng = random.Random(seed)
    blobs = [(0.0, 0.0, 0.32)]
    for _ in range(14):
        a = rng.random() * 6.283
        rr = 0.3 + rng.random() * 0.5
        blobs.append((math.cos(a) * rr, math.sin(a) * rr, 0.03 + rng.random() * 0.1))
    def fn(x, y):
        best = 0.0
        for bx, by, br in blobs:
            best = max(best, aa(math.hypot(x - bx, y - by) - br, 0.03))
        for bx, by, br in blobs[1:]:
            best = max(best, math.exp(-(seg_dist(x, y, 0, 0, bx, by) / 0.03) ** 2) * 0.9 * (1 if math.hypot(bx, by) < 0.6 else 0))
        shade = 0.7 + 0.3 * vnoise((x + 1) * 3, (y + 1) * 3)
        return rgba(shade, best * 0.95)
    return fn


def thorn(x, y):
    # a spike lying along +x, pointing right, wide at the base
    t = (x + 0.95) / 1.9
    if t < 0 or t > 1:
        return rgba(0, 0)
    w = 0.30 * (1 - t) ** 1.25 + 0.006
    d = abs(y) - w
    shade = 0.45 + 0.4 * t + 0.25 * (1 - abs(y) / max(w, 0.01)) * (1 - t)
    return rgba(shade, aa(d, 0.03) * (1 - smooth(0.0, 0.05, -t + 0.04) * 0))


def bat(x, y):
    ax = abs(x)
    top = -0.55 + 0.35 * ax ** 1.4 - 0.12 * math.sin(ax * 5)
    scallop = 0.12 * abs(math.sin(ax * 9.0)) if ax > 0.25 else 0.0
    bottom = 0.05 + 0.45 * ax ** 1.1 - scallop
    wing = 1.0 if (0.12 < ax < 0.98 and top < y < bottom) else 0.0
    edge = min(abs(y - top), abs(y - bottom), 0.98 - ax if ax > 0.9 else 1.0)
    body = aa(((x / 0.13) ** 2 + ((y - 0.02) / 0.34) ** 2) - 1, 0.25)
    head = aa(math.hypot(x, y + 0.36) - 0.13, 0.04)
    ears = max(aa(seg_dist(x, y, -0.06, -0.42, -0.1, -0.62) - 0.02, 0.02), aa(seg_dist(x, y, 0.06, -0.42, 0.1, -0.62) - 0.02, 0.02))
    a = max(wing * min(1.0, edge / 0.02), body, head, ears)
    return rgba(0.15 + 0.15 * body, a)


# ---------------------------------------------------------------- write everything

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
render_variants("fx_flame", [make_flame(1), make_flame(2), make_flame(3)])
render("fx_ember", ember)
render("fx_streak", streak)
render("fx_wisp", wisp)
render_variants("fx_rune", [make_rune(s) for s in (3, 5, 8, 13)])
render_variants("fx_bolt", [make_bolt(s) for s in (2, 4, 9)])
render("fx_swirl", swirl)
render("fx_rays", rays)
render("fx_claw", claw)
render("fx_arrow", arrow)
render("fx_heart", heart)
render("fx_skull", skull)
render("fx_shockwave", shockwave)
render("fx_crackglow", lambda x, y: light(_crack_field(x, y), 0.8))
render_variants("fx_leaf", [make_leaf(s) for s in (1, 2, 3)])
render_variants("fx_petal", [make_petal(s) for s in (1, 2)])
render("fx_droplet", droplet)
render("fx_bubble", bubble)
render_variants("fx_foam", [make_foam(4), make_foam(9)])
render("fx_watersheet", watersheet)
render_variants("fx_debris", [make_debris(s) for s in (1, 2, 3, 4)])
render("fx_crack", crack)
render_variants("fx_splat", [make_splat(1), make_splat(2)])
render("fx_bat", bat)
render("fx_thorn", thorn)
