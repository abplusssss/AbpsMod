#!/usr/bin/env python3
"""
Draws every dungeon monster body from the specs in RigModels.java, standing still, seen from the front left.
It uses the same maths as Rig.java, so what it shows is what the game builds (minus textures: each block is drawn
in one flat color).

Run from the repo root:  python3 tools/preview_rigs.py out.png
"""
import math
import os
import re
import struct
import sys
import zlib

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
SRC = os.path.join(ROOT, "src", "main", "java", "dev", "abps", "dungeon", "RigModels.java")

COLORS = {
    "polished_deepslate": 0x4a4a52, "iron_block": 0xd8d8d8, "polished_blackstone": 0x35303a, "chiseled_stone_bricks": 0x8a8a8a,
    "verdant_froglight": 0x9ef59a, "dark_oak_planks": 0x4a3220, "gold_block": 0xf5cd34, "bone_block": 0xe5dfc8,
    "sea_lantern": 0xc8f0f0, "redstone_block": 0xd01010, "blackstone": 0x2a2630, "packed_ice": 0x8db4f0, "blue_ice": 0x74a8f5,
    "ice": 0xa8c8ff, "snow_block": 0xf4fafa, "magma_block": 0xb04a10, "shroomlight": 0xffa040, "basalt": 0x505058,
    "nether_bricks": 0x3a1a1e, "blast_furnace": 0x6a6a6a, "chiseled_polished_blackstone": 0x3a3540, "netherite_block": 0x48404a,
    "gilded_blackstone": 0x6a5020, "sculk": 0x0c2a34, "amethyst_block": 0x9a68d0, "crying_obsidian": 0x7a2ad0, "calcite": 0xe0e0dc,
    "emerald_block": 0x30d070, "stone": 0x808080,
    "concrete:black": 0x101014, "concrete:cyan": 0x158a90, "concrete:gray": 0x404448, "concrete:green": 0x4a5a24,
    "concrete:purple": 0x6420a0, "concrete:white": 0xe0e4e6, "concrete:light_blue": 0x3aa0d8, "concrete:red": 0x902020,
    "glass:light_blue": 0x9ad0f0, "glass:white": 0xf0f0f0, "glass:cyan": 0x40d0d0, "glass:black": 0x202028, "glass:purple": 0x8040c0,
}


def quat_y(a):
    return (math.cos(a / 2), 0, math.sin(a / 2), 0)  # (w, x, y, z)


def quat_x(a):
    return (math.cos(a / 2), math.sin(a / 2), 0, 0)


def quat_z(a):
    return (math.cos(a / 2), 0, 0, math.sin(a / 2))


def qmul(a, b):
    aw, ax, ay, az = a
    bw, bx, by, bz = b
    return (aw * bw - ax * bx - ay * by - az * bz, aw * bx + ax * bw + ay * bz - az * by,
            aw * by - ax * bz + ay * bw + az * bx, aw * bz + ax * by - ay * bx + az * bw)


def qrot(q, v):
    w, x, y, z = q
    p = (0, v[0], v[1], v[2])
    r = qmul(qmul(q, p), (w, -x, -y, -z))
    return (r[1], r[2], r[3])


ID = (1, 0, 0, 0)


def parse_specs():
    src = open(SRC).read()
    humanoid = re.search(r"HUMANOID = \{(.*?)\};", src, re.S).group(1)
    humanoid = re.findall(r'"([^"]*)"', humanoid)
    models = {}
    for m in re.finditer(r'SPECS\.put\("(\w+)",\s*(humanoid\(|new String\[\]\{)(.*?)\)?\);', src, re.S):
        lines = re.findall(r'"([^"]*)"', m.group(3))
        if m.group(2).startswith("humanoid"):
            lines = humanoid + lines
        models[m.group(1)] = lines
    return models


def build(lines):
    pivots, parts = {}, []
    cfg = {"float": 0, "pitch": 0, "arms": 0, "size": 1}
    for line in lines:
        t = line.split()
        if t[0] == "pivot":
            pivots[t[1]] = tuple(float(v) for v in t[2:5])
        elif t[0] in cfg:
            cfg[t[0]] = float(t[1])
        elif t[0] in ("box", "glow"):
            rot, off = ID, (0, 0, 0)
            for extra in t[9:]:
                if extra.startswith("r="):
                    rx, ry, rz = (math.radians(float(v)) for v in extra[2:].split(","))
                    rot = qmul(qmul(quat_y(ry), quat_x(rx)), quat_z(rz))
                elif extra.startswith("o="):
                    off = tuple(float(v) for v in extra[2:].split(","))
            parts.append((t[2].split("[")[0], t[1], tuple(float(v) for v in t[3:6]), tuple(float(v) for v in t[6:9]), rot, off))
    return pivots, parts, cfg


def bone_rot(bone, cfg):
    if bone in ("ARM_L", "ARM_R"):
        return quat_x(math.radians(cfg["arms"]))
    if bone == "BODY":
        return quat_x(math.radians(cfg["pitch"]))
    if bone == "CAPE":
        return quat_x(0.08)
    return ID


def faces(lines, yaw_deg):
    pivots, parts, cfg = build(lines)
    k = cfg["size"]
    yaw = quat_y(math.radians(-yaw_deg))
    out = []
    for block, bone, frm, size, rot, off in parts:
        rb = bone_rot(bone, cfg) if bone not in ("LEGS", "QLEGS") else ID
        o = qrot(rot, tuple(v * k for v in off))
        f = tuple(frm[i] * k + o[i] for i in range(3))
        piv = pivots.get(bone, (0, 0, 0))
        local = qrot(rb, f)
        local = tuple(local[i] + piv[i] * k for i in range(3))
        local = (local[0], local[1] + cfg["float"] * k, local[2])
        left = qmul(yaw, qmul(rb, rot))
        trans = qrot(yaw, local)
        corners = {}
        for cx in (0, 1):
            for cy in (0, 1):
                for cz in (0, 1):
                    v = qrot(left, (cx * size[0] * k, cy * size[1] * k, cz * size[2] * k))
                    corners[(cx, cy, cz)] = (v[0] + trans[0], v[1] + trans[1], v[2] + trans[2])
        quads = [((0, 0, 0), (1, 0, 0), (1, 1, 0), (0, 1, 0)), ((0, 0, 1), (1, 0, 1), (1, 1, 1), (0, 1, 1)),
                 ((0, 0, 0), (0, 1, 0), (0, 1, 1), (0, 0, 1)), ((1, 0, 0), (1, 1, 0), (1, 1, 1), (1, 0, 1)),
                 ((0, 0, 0), (1, 0, 0), (1, 0, 1), (0, 0, 1)), ((0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1))]
        shade = [0.8, 0.8, 0.65, 0.65, 0.5, 1.0]
        for qi, q in enumerate(quads):
            out.append(([corners[c] for c in q], COLORS.get(block, 0xff00ff), shade[qi], block))
    return out


def render(lines, size=160, yaw=35, pitch=18):
    fs = faces(lines, yaw)
    cp = math.cos(math.radians(pitch))
    sp = math.sin(math.radians(pitch))

    def cam(v):
        # Camera looks along -z toward the model, tilted down a little
        x, y, z = v
        return (x, y * cp - z * sp, y * sp + z * cp)

    projected = []
    allpts = []
    for pts, col, sh, block in fs:
        c = [cam(p) for p in pts]
        projected.append((sum(p[2] for p in c) / 4, c, col, sh, block))
        allpts += c
    lo_x = min(p[0] for p in allpts)
    hi_x = max(p[0] for p in allpts)
    lo_y = min(p[1] for p in allpts)
    hi_y = max(p[1] for p in allpts)
    scale = (size - 16) / max(hi_x - lo_x, hi_y - lo_y, 1)
    img = [[0x2b2b33] * size for _ in range(size)]
    for y in range(size):
        for x in range(size):
            if (x // 8 + y // 8) % 2:
                img[y][x] = 0x303038
    projected.sort(key=lambda f: f[0])  # far first: larger z is closer to the camera
    for depth, c, col, sh, block in projected:
        poly = [((p[0] - (lo_x + hi_x) / 2) * scale + size / 2, size - 8 - (p[1] - lo_y) * scale) for p in c]
        r, g, b = (col >> 16) & 255, (col >> 8) & 255, col & 255
        if block in ("sea_lantern", "shroomlight", "verdant_froglight", "redstone_block", "crying_obsidian", "emerald_block"):
            sh = 1.0
        colr = (int(r * sh) << 16) | (int(g * sh) << 8) | int(b * sh)
        fill(img, poly, colr, size)
    return img


def fill(img, poly, col, size):
    ys = [p[1] for p in poly]
    for y in range(max(0, int(min(ys))), min(size, int(max(ys)) + 1)):
        xs = []
        n = len(poly)
        for i in range(n):
            (x1, y1), (x2, y2) = poly[i], poly[(i + 1) % n]
            if (y1 <= y + 0.5 < y2) or (y2 <= y + 0.5 < y1):
                xs.append(x1 + (y + 0.5 - y1) * (x2 - x1) / (y2 - y1))
        xs.sort()
        for i in range(0, len(xs) - 1, 2):
            for x in range(max(0, int(xs[i] + 0.5)), min(size, int(xs[i + 1] + 0.5))):
                img[y][x] = col


def write_png(path, img):
    h, w = len(img), len(img[0])
    raw = bytearray()
    for row in img:
        raw.append(0)
        for c in row:
            raw += bytes(((c >> 16) & 255, (c >> 8) & 255, c & 255))

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    open(path, "wb").write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))


def main():
    models = parse_specs()
    names = list(models)
    size, cols = 200, 4
    rows = (len(names) + cols - 1) // cols
    sheet = [[0x18181c] * (size * cols) for _ in range(size * rows)]
    for i, n in enumerate(names):
        img = render(models[n], size)
        ox, oy = (i % cols) * size, (i // cols) * size
        for y in range(size):
            sheet[oy + y][ox:ox + size] = img[y]
    write_png(sys.argv[1] if len(sys.argv) > 1 else "rigs.png", sheet)
    print(", ".join(names))


if __name__ == "__main__":
    main()
