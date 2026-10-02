#!/usr/bin/env python3
"""
Makes the 16x16 item textures, item models and item model definitions for the custom dungeon items.

Each item is drawn the way vanilla items are: along the 45 degree diagonal, from the handle in the bottom left to
the tip in the top right. Every material gets a five tone ramp (outline, shadow, base, light, glint) with a small
hue shift, so shadows are richer and highlights warmer, and the outline is the darkest tone of the material next to
it instead of plain black.

No image library needed: the PNGs are written by hand with zlib.

Run from the repo root:  python3 tools/gen_item_textures.py [preview.png]
"""
import colorsys
import json
import os
import struct
import sys
import zlib

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
ASSETS = os.path.join(ROOT, "src", "client", "resources", "assets", "abpsmod")


# ---------------------------------------------------------------- colour

def ramp(base):
    """Tones from a base colour, matched to how a hand-drawn vanilla style sword is shaded:
    0 darkest (lower right outline), "A" mid outline (upper left outline), 1 shadow, 2 base, 3 light, 4 glint."""
    r, g, b = ((base >> 16) & 255) / 255, ((base >> 8) & 255) / 255, (base & 255) / 255
    h, s, v = colorsys.rgb_to_hsv(r, g, b)
    grey = s < 0.08

    def tone(vm, sat, dh, lift=0.0):
        nv = v * vm + (1 - v * vm) * lift
        ns = s if grey else min(1, max(0, sat))
        nh = h if grey else (h + dh) % 1
        rr, gg, bb = colorsys.hsv_to_rgb(nh, ns, min(1, nv))
        return (int(rr * 255) << 16) | (int(gg * 255) << 8) | int(bb * 255)

    return {
        0: tone(0.48, s + 0.10, -0.015),
        "A": tone(0.77, s + 0.04, -0.006),
        1: tone(0.63, s + 0.08, -0.01),
        2: tone(1.0, s, 0),
        3: tone(1.0, s * 0.78, 0.008, 0.38),
        4: tone(1.0, s * 0.48, 0.015, 0.72),
    }


# ---------------------------------------------------------------- canvas

class Canvas:
    """A 16x16 grid of (material, tone). The outline pass turns empty neighbours into the material's tone 0."""

    def __init__(self):
        self.g = [[None] * 16 for _ in range(16)]

    def put(self, x, y, mat, tone, drawn=False):
        """drawn marks a pixel that is part of a hand-drawn outline, so the outline pass leaves it alone."""
        if 0 <= x < 16 and 0 <= y < 16:
            self.g[y][x] = (mat, tone, drawn)

    def get(self, x, y):
        return self.g[y][x] if 0 <= x < 16 and 0 <= y < 16 else None

    def diag(self, t, s):
        """Blade coordinates: t runs along the blade (handle -15 to tip +15), s across it (-1 upper left, +1 lower right).
        x + y = 15 + s, x - y = t. Returns None when the pair doesn't land on a pixel."""
        if (t + 15 + s) % 2:
            return None
        x = (t + 15 + s) // 2
        y = 15 + s - x
        return x, y

    def dput(self, t, s, mat, tone):
        p = self.diag(t, s)
        if p:
            self.put(p[0], p[1], mat, tone)

    def grid(self, mats, outline=True):
        out = [[None] * 16 for _ in range(16)]
        for y in range(16):
            for x in range(16):
                c = self.g[y][x]
                if c:
                    out[y][x] = mats[c[0]][c[1]]
        if outline:
            for y in range(16):
                for x in range(16):
                    if self.g[y][x]:
                        continue
                    # The upper left side of a shape gets the softer outline, the lower right the darkest one,
                    # which is what gives vanilla items their light-from-the-top-left look
                    for dx, dy, t in ((1, 0, "A"), (0, 1, "A"), (-1, 0, 0), (0, -1, 0)):
                        n = self.get(x + dx, y + dy)
                        if n and not (len(n) > 2 and n[2]):
                            out[y][x] = mats[n[0]][t]
                            break
        return out


def stamp(cv, x0, y0, rows, legend):
    """Draws a hand-made pixel block. legend maps a letter to (material, tone)."""
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in legend:
                cv.put(x0 + x, y0 + y, *legend[ch])


# Letters for fully hand-drawn items. Main material: C F A E D B, darkest to brightest (A is the soft outline).
# Accent (guards, pommels, bindings): x y z g G W. Handle: L I J K. Gem: q Q.
LEGEND = {
    "C": ("m", 0, True), "F": ("m", 1, True), "A": ("m", "A", True), "E": ("m", 2, True), "D": ("m", 3, True), "B": ("m", 4, True),
    "x": ("a", 0, True), "y": ("a", 1, True), "z": ("a", "A", True), "g": ("a", 2, True), "G": ("a", 3, True), "W": ("a", 4, True),
    "L": ("h", 0, True), "I": ("h", "A", True), "J": ("h", 2, True), "K": ("h", 3, True),
    "q": ("g", 2, True), "Q": ("g", 4, True),
}


def drawn(cv, rows):
    stamp(cv, 0, 0, rows, LEGEND)


# ---------------------------------------------------------------- parts

def handle(cv, t0, t1, wrap=False):
    """A two pixel wide stick from t0 (bottom) to t1. With wrap, every other step is a darker grip band."""
    for t in range(t0, t1 + 1):
        band = wrap and (t // 2) % 2 == 0
        cv.dput(t, 0, "h", 2 if band else 3)
        cv.dput(t, 1, "h", 1)


def pommel(cv, t, gem=False):
    """A round knob at the bottom of a handle."""
    for s in (-1, 0, 1):
        cv.dput(t, s, "a", 3 if s < 0 else 2 if s == 0 else 1)
    for s in (0,):
        cv.dput(t - 1, s, "a", 1)
    cv.dput(t + 1, -1, "a", 3)
    cv.dput(t + 1, 1, "a", 1)
    if gem:
        cv.dput(t, 0, "a", 4)


def guard(cv, t, half, gem=True):
    """A crossguard across the blade at t, two diagonals thick, `half` pixels to each side."""
    for s in range(-half, half + 1):
        cv.dput(t, s, "a", 3 if s < 0 else 2)
        cv.dput(t - 1, s, "a", 2 if s < 0 else 1)
    # Flared tips
    cv.dput(t + 1, -half, "a", 3)
    cv.dput(t - 2, half, "a", 1)
    if gem:
        cv.dput(t, 0, "g", 3)
        cv.dput(t - 1, 0, "g", 2)
        cv.dput(t, -1, "g", 4) if cv.diag(t, -1) else None


def blade(cv, t0, t1, width=1, taper=3):
    """A blade from t0 to the tip at t1. width 1 is three diagonals across (a slim blade), 2 is five (a broad one).
    The upper edge is the base tone, a bright ridge runs down the middle and the lower edge falls into shadow."""
    for t in range(t0, t1 + 1):
        left = t1 - t  # steps from the tip
        w = width if left >= taper else max(0, width - (taper - left + 1) // 2)
        for s in range(-w, w + 1):
            if s < 0:
                tone = 3 if s == -w and width > 1 else 2
            elif s == 0:
                tone = 4 if left % 6 in (2, 3) or left <= 1 else 3
            else:
                tone = 1 if s == w else 2
            cv.dput(t, s, "m", tone)
    cv.dput(t1, 0, "m", 4)


# ---------------------------------------------------------------- items

SWORD = [
    ".............AAA",
    "............ABBC",
    "...........ABDBC",
    "..........ABDBC.",
    ".........ABDEC..",
    "........ABDEC...",
    "..zz...ABDEC....",
    "..zyz.ADEEC.....",
    "...zGxEDEC......",
    "...zGGgEC.......",
    "....zgyx........",
    "...IJxyyx.......",
    "..IKL.xxyx......",
    "zzJL....xx......",
    "zgy.............",
    "yyy.............",
]

GREATSWORD = [
    "............AAAA",
    "...........ABBBC",
    "..........ABDDBC",
    ".........ABDDEC.",
    "........ABDDEC..",
    ".......ABDDEC...",
    "......ABDDEC....",
    ".zz..ABDDEC.....",
    ".zGzADDEEC......",
    "..zGxDEEC.......",
    "..zGGqEC........",
    "...zggyx........",
    "..IJxyyyx.......",
    ".IKL..xxyx......",
    "zzJL....xxx.....",
    "zgy.............",
]

DAGGER = [
    "................",
    "................",
    "................",
    "...........AAA..",
    "..........ABBC..",
    ".........ABDBC..",
    "........ABDEC...",
    "...zz..ABDEC....",
    "...zyzADEEC.....",
    "....zGxDEC......",
    "....zGGgC.......",
    ".....zgyx.......",
    "....IJxyyx......",
    "...IKL.xxx......",
    "zzzJL...........",
    "zgy.............",
]


def sword(cv, long=False):
    drawn(cv, GREATSWORD if long else SWORD)


def dagger(cv):
    drawn(cv, DAGGER)


def mirror(pts):
    """Reflects points across the handle's line (x + y = 15), for heads that are the same on both sides."""
    return [(15 - y, 15 - x, t) for x, y, t in pts]


def put_all(cv, mat, pts):
    for x, y, t in pts:
        cv.put(x, y, mat, t)


def spear(cv):
    handle(cv, -13, 4)
    # A leaf shaped head with a bright ridge
    for t in range(6, 15):
        w = 1 if 7 <= t <= 11 else 0
        for s in range(-w, w + 1):
            cv.dput(t, s, "m", 3 if s < 0 else (4 if 8 <= t <= 12 else 3) if s == 0 else 1)
    cv.dput(14, 0, "m", 4)
    # Barbs either side at the base of the head
    cv.dput(6, -2, "m", 3)
    cv.dput(6, 2, "m", 1)
    # A wrapped collar and a tassel
    for s in (-1, 0, 1):
        cv.dput(5, s, "a", 3 if s < 0 else 2 if s == 0 else 1)
        cv.dput(4, s, "a", 2 if s < 1 else 1)
    cv.dput(3, 2, "g", 3)
    cv.dput(2, 3, "g", 2)
    cv.dput(1, 2, "g", 2)
    cv.dput(0, 3, "g", 1)
    pommel(cv, -14)


def staff(cv):
    handle(cv, -14, 3)
    # Wrapped bands up the shaft
    for t in (-10, -6, -2):
        cv.dput(t, 0, "a", 3)
        cv.dput(t, 1, "a", 1)
    # An orb with a highlight in its top left
    cx, cy = 11.5, 3.5
    for y in range(0, 8):
        for x in range(8, 16):
            dx, dy = x - cx, y - cy
            if dx * dx + dy * dy <= 6.5:
                k = dx + dy
                tone = 4 if (dx < -0.5 and dy < -0.5 and k < -1.5) else 3 if k < 0 else 2 if k < 2 else 1
                cv.put(x, y, "g", tone)
    cv.put(10, 2, "g", 4)
    cv.put(11, 2, "g", 4)
    # Claws of the accent holding it
    put_all(cv, "a", [(8, 4, 3), (8, 3, 3), (9, 6, 2), (10, 7, 2), (12, 7, 1), (14, 5, 1), (9, 1, 3)])


def axe(cv):
    handle(cv, -14, 11)
    # A fan shaped blade on the upper left of the handle, sharpened bright along its curved edge
    rows = {0: (8, 11), 1: (6, 12), 2: (5, 12), 3: (4, 11), 4: (4, 10), 5: (4, 9), 6: (5, 8), 7: (6, 7)}
    for y, (x0, x1) in rows.items():
        for x in range(x0, x1 + 1):
            edge = x - x0
            back = (15 - y) - x  # distance to the handle
            tone = 4 if edge == 0 else 3 if edge == 1 else 1 if back <= 2 else 2
            if y == 0 and edge > 0:
                tone = 3
            if y >= 6 and edge > 0:
                tone = 1
            cv.put(x, y, "m", tone)
    # A rivet and the band holding the head on
    cv.put(9, 3, "a", 3)
    put_all(cv, "a", [(13, 1, 3), (12, 2, 3), (11, 3, 2), (10, 4, 2), (9, 5, 1)])
    put_all(cv, "a", [(14, 2, 1), (13, 3, 1), (12, 4, 1)])


def pickaxe(cv):
    handle(cv, -14, 5)
    left_outer = [(2, 4, 3), (2, 3, 4), (3, 2, 4), (4, 1, 4), (5, 1, 4), (6, 1, 3), (7, 1, 3), (8, 1, 3), (9, 2, 3), (10, 3, 2)]
    left_inner = [(3, 4, 2), (3, 3, 2), (4, 2, 3), (5, 2, 2), (6, 2, 2), (7, 2, 2), (8, 2, 2), (9, 3, 2), (10, 4, 2)]
    right = [(x, y, 1 if t >= 3 else 1) for x, y, t in mirror(left_outer)] + [(x, y, 2) for x, y, t in mirror(left_inner)]
    put_all(cv, "m", right)
    put_all(cv, "m", left_outer + left_inner)
    # Tips get a glint, the centre a bound collar
    cv.put(2, 3, "m", 4)
    put_all(cv, "a", [(11, 4, 3), (10, 5, 2), (11, 5, 1), (12, 4, 2)])


def shovel(cv):
    handle(cv, -14, 4)
    for s in (-1, 0, 1):
        cv.dput(5, s, "a", 3 if s < 0 else 2 if s == 0 else 1)
        cv.dput(6, s, "a", 2 if s < 1 else 1)
    # A rounded spade
    for t in range(7, 15):
        w = 2 if t < 13 else 1 if t < 14 else 0
        for s in range(-w, w + 1):
            tone = 4 if (s == -w and t < 13) else 3 if s < 0 else 2 if s == 0 else 1
            cv.dput(t, s, "m", tone)
    cv.dput(9, 0, "m", 3)
    cv.dput(11, 0, "m", 3)


def hammer(cv):
    handle(cv, -14, 3, wrap=True)
    # A wide, flat-faced head across the end of the handle
    for t in range(4, 8):
        for s in range(-5, 6):
            if abs(s) == 5 and t in (4, 7):
                continue  # bevelled corners
            tone = 4 if s <= -4 else 3 if s < 0 else 2 if s < 4 else 1
            if t == 7 and s < 4:
                tone = min(4, tone + 1)  # the top face catches the light
            if t == 4:
                tone = max(1, tone - 1)
            cv.dput(t, s, "m", tone)
    # Metal bands at both ends and a glowing rune in the middle
    for s in (-5, -4, 4, 5):
        for t in (5, 6):
            cv.dput(t, s, "a", 3 if s < 0 else 1)
    for t, s, k in ((6, 0, 4), (5, -1, 3), (5, 1, 3), (7, 1, 3), (6, 2, 2), (6, -2, 3)):
        cv.dput(t, s, "g", k)


def scythe(cv):
    handle(cv, -14, 12)
    outer = [(13, 0, 3), (12, 0, 4), (11, 0, 4), (10, 0, 3), (9, 0, 3), (8, 1, 3), (7, 1, 3), (6, 2, 3), (5, 2, 3), (4, 3, 3), (3, 4, 3), (2, 5, 3), (1, 7, 3), (1, 6, 3)]
    inner = [(13, 1, 2), (12, 1, 2), (11, 1, 2), (10, 1, 2), (9, 1, 2), (8, 2, 2), (7, 2, 2), (6, 3, 1), (5, 3, 1), (4, 4, 1), (3, 5, 1), (2, 6, 1)]
    put_all(cv, "m", outer + inner)
    put_all(cv, "a", [(14, 1, 3), (14, 2, 2), (13, 2, 1)])


def drill(cv):
    handle(cv, -14, -2, wrap=True)
    for t in range(-1, 3):
        for s in (-2, -1, 0, 1, 2):
            cv.dput(t, s, "a", 3 if s < 0 else 2 if s < 2 else 1)
    for t in range(3, 15):
        w = 2 if t < 6 else 1 if t < 11 else 0
        for s in range(-w, w + 1):
            stripe = (t + s) % 3 == 0
            cv.dput(t, s, "m", 4 if stripe and s <= 0 else 3 if s < 0 else 2 if s == 0 else 1)
    cv.dput(0, 0, "g", 4)
    cv.dput(1, 1, "g", 3)


def chakram(cv):
    import math
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            d = math.hypot(dx, dy)
            if 3.6 <= d <= 6.6:
                k = dx + dy
                tone = 4 if (d > 5.6 and k < -4) else 3 if k < -1 else 2 if k < 3 else 1
                if d < 4.4:
                    tone = max(1, tone - 1)  # the inner rim is in shadow
                cv.put(x, y, "m", tone)
    # Eight blades around the edge, swept the same way
    for i in range(8):
        a = i * math.pi / 4 + 0.2
        for r, tone in ((7.2, 3), (7.9, 4)):
            x, y = int(round(7.5 + math.cos(a) * r)), int(round(7.5 + math.sin(a) * r))
            cv.put(x, y, "m", tone if math.cos(a) + math.sin(a) < 0 else 2)
    # Glowing runes on the ring
    for i in range(4):
        a = i * math.pi / 2 + math.pi / 4
        cv.put(int(round(7.5 + math.cos(a) * 5)), int(round(7.5 + math.sin(a) * 5)), "g", 4)


def chestplate(cv):
    stamp(cv, 1, 1, [
        "..LLL....LLL..",
        ".LMMMLggLMMMD.",
        "LMMMMLGGMMMMDD",
        "LMMMMMLLMMMMMD",
        ".DMMMMMMMMMMD.",
        "..LMMMggMMMD..",
        "..LMMgWWgMMD..",
        "..LMMMggMMMD..",
        "..LMMMMMMMMD..",
        "..LMMMMMMMMD..",
        "..DMMMMMMMDD..",
        "..DDMMMMMDDD..",
        "...DDDDDDDD...",
    ], {"M": ("m", 2), "L": ("m", 3), "W": ("g", 4), "D": ("m", 1), "g": ("a", 3), "G": ("a", 2)})


def boots(cv):
    stamp(cv, 1, 4, [
        ".LLLL...LLLL..",
        ".LMMD...LMMD..",
        ".gggg...gggg..",
        ".LMMD...LMMD..",
        ".LMMD...LMMD..",
        ".LMMMD..LMMMD.",
        "LLMMMMD.LMMMMD",
        "DDDDDDD.DDDDDD",
    ], {"M": ("m", 2), "L": ("m", 3), "D": ("m", 1), "g": ("a", 3)})
    # Little wings
    for x, y in ((0, 5), (0, 6), (1, 3), (8, 3), (9, 5)):
        cv.put(x, y, "g", 4)


def feather(cv):
    # A quill along the diagonal with barbs on both sides, burning brighter at the tip
    for t in range(-13, 15):
        cv.dput(t, 0, "h", 3)
    for t in range(-6, 14):
        tip = t > 7
        w = 2 if -3 < t < 10 else 1
        for s in range(1, w + 1):
            cv.dput(t, -s, "m", 4 if tip else 3)
            cv.dput(t, s, "m", 2 if not tip else 3)
        if t % 3 == 0:
            cv.dput(t, w + 1, "m", 1)
    cv.dput(14, 0, "g", 4)
    cv.dput(13, -1, "g", 4)
    cv.dput(12, 0, "g", 3)


DRAW = {
    "sword": lambda c: sword(c),
    "greatsword": lambda c: sword(c, long=True),
    "dagger": dagger,
    "spear": spear,
    "staff": staff,
    "axe": axe,
    "pickaxe": pickaxe,
    "shovel": shovel,
    "hammer": hammer,
    "scythe": scythe,
    "drill": drill,
    "chakram": chakram,
    "chestplate": chestplate,
    "boots": boots,
    "feather": feather,
}

WOOD = 0x8B5A2B
DARK_WOOD = 0x5A3A22
GOLD = 0xF2B21C
IRON = 0xB8C2CC

# id: (shape, main metal or material, handle, guard/binding, gem/glow)
ITEMS = {
    # Older dungeon items
    "bloodfang": ("sword", 0xD3202A, DARK_WOOD, 0x8E1B1B, 0xFF5A5A),
    "frostbite": ("sword", 0x7FD6F7, 0x4A5A6A, 0xCFE9F5, 0xE8FBFF),
    "stormcaller": ("sword", 0x3FA9F5, 0x2A2F6B, GOLD, 0xFFF176),
    "sunblade": ("sword", 0xFFC93C, 0x7A4A2A, 0xE0761A, 0xFFF3B0),
    "voidrender": ("greatsword", 0x7A4FD0, 0x22182E, 0x3A2560, 0xE45BFF),
    "reaper": ("scythe", 0xC9D1D6, 0x3B2A22, 0x4A4A4A, 0x7CFF4F),
    "earthsplitter": ("axe", 0xC0793A, DARK_WOOD, 0x4A4A52, 0xFFB15A),
    "timberfall": ("axe", 0xA8B4BC, WOOD, 0x6E8F3A, 0xB8E06A),
    "quarry_pick": ("pickaxe", IRON, WOOD, 0x6A4A2E, GOLD),
    "molten_pick": ("pickaxe", 0xFF6A2A, 0x3B2722, 0x2A2A2A, 0xFFE35A),
    "veinripper": ("pickaxe", 0x2CC9D9, 0x2A3238, 0x1E6A72, 0xB8FF5A),
    "earthmover": ("shovel", 0xA88A72, WOOD, 0x6A5040, 0xD8B890),
    "bulwark": ("chestplate", 0x8A9AA6, 0x3A4650, GOLD, 0xFFE07A),
    "windrunners": ("boots", 0x9FE6EF, 0x5A7080, 0x5ACDE0, 0xFFFFFF),
    "phoenix_feather": ("feather", 0xFF6A2A, 0xFFD27A, 0xC2301A, 0xFFF3A0),
    # Weapons
    "thunder_hammer": ("hammer", 0x8E9CA8, 0x3B2A22, 0x4A3A30, 0x4FD2FF),
    "ember_staff": ("staff", 0xFF6A00, 0x5A3A26, 0xB0501A, 0xFFB040),
    "frost_staff": ("staff", 0x8FE4F0, 0x50606C, 0xB8D8E8, 0x8FEFFF),
    "soul_staff": ("staff", 0x50F0C8, 0x2A3036, 0x3A6A60, 0x2BF5C0),
    "shadow_daggers": ("dagger", 0x6A40C8, 0x222028, 0x3A2A5A, 0xC08CFF),
    "dragonbone_greatsword": ("greatsword", 0xEEE6D8, 0x5A3A26, 0x9A2020, 0xFF3A3A),
    "tide_spear": ("spear", 0x2FB8A8, 0x3A4A50, 0xC9A04A, 0x8FE8DC),
    "echo_chakram": ("chakram", 0xB8C4CC, 0x263238, 0x2A3A44, 0x2AE8FF),
    # Tools
    "prospector_pick": ("pickaxe", 0xFFCC33, WOOD, 0x6A4A2E, 0x4FE0F0),
    "titan_drill": ("drill", 0x8A98A4, 0x3A4650, 0xE08A1A, 0xFFB040),
    "harvest_scythe": ("scythe", 0xC8D23A, WOOD, 0x6A8A2A, 0xA8E06A),
    "gravedigger": ("shovel", 0x6A8494, 0x3B2A22, 0x2A3A44, 0x7CFF4F),
}

# Items that are held like tools (handheld model); the rest use the flat generated model
FLAT = {"bulwark", "windrunners", "phoenix_feather", "echo_chakram"}


def render(item_id):
    shape, main_c, handle_c, accent_c, gem_c = ITEMS[item_id]
    cv = Canvas()
    DRAW[shape](cv)
    mats = {"m": ramp(main_c), "h": ramp(handle_c), "a": ramp(accent_c), "g": ramp(gem_c)}
    return cv.grid(mats)


def write_png(path, grid):
    raw = bytearray()
    for y in range(16):
        raw.append(0)
        for x in range(16):
            c = grid[y][x]
            raw += bytes((0, 0, 0, 0)) if c is None else bytes(((c >> 16) & 255, (c >> 8) & 255, c & 255, 255))
    write_rgba(path, 16, 16, raw)


def write_rgba(path, w, h, raw):
    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)


def main():
    tex_dir = os.path.join(ASSETS, "textures", "item")
    model_dir = os.path.join(ASSETS, "models", "item")
    items_dir = os.path.join(ASSETS, "items")
    grids = []
    for item_id in ITEMS:
        grid = render(item_id)
        grids.append(grid)
        write_png(os.path.join(tex_dir, item_id + ".png"), grid)
        parent = "minecraft:item/generated" if item_id in FLAT else "minecraft:item/handheld"
        os.makedirs(model_dir, exist_ok=True)
        os.makedirs(items_dir, exist_ok=True)
        with open(os.path.join(model_dir, item_id + ".json"), "w") as f:
            json.dump({"parent": parent, "textures": {"layer0": "abpsmod:item/" + item_id}}, f, indent=2)
            f.write("\n")
        with open(os.path.join(items_dir, item_id + ".json"), "w") as f:
            json.dump({"model": {"type": "minecraft:model", "model": "abpsmod:item/" + item_id}}, f, indent=2)
            f.write("\n")
    if len(sys.argv) > 1:
        # A preview sheet: every item at 10x on a light background, 7 to a row
        scale, cols = 12, 7
        rows = (len(grids) + cols - 1) // cols
        w, h = cols * 18 * scale, rows * 18 * scale
        raw = bytearray()
        for y in range(h):
            raw.append(0)
            for x in range(w):
                gx, gy = x // (18 * scale), y // (18 * scale)
                px, py = (x // scale) % 18 - 1, (y // scale) % 18 - 1
                i = gy * cols + gx
                c = None
                if i < len(grids) and 0 <= px < 16 and 0 <= py < 16:
                    c = grids[i][py][px]
                if c is None:
                    c = 0xC6C6C6
                raw += bytes(((c >> 16) & 255, (c >> 8) & 255, c & 255, 255))
        write_rgba(sys.argv[1], w, h, raw)
    print("wrote", len(ITEMS), "items")


if __name__ == "__main__":
    main()
