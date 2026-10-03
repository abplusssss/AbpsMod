#!/usr/bin/env python3
"""
Builds the dungeon monsters' real entity models from tools/mob_specs.txt:

  * src/client/resources/assets/abpsmod/mobs/<id>.json      the model (parts, cubes, UVs, animation settings),
                                                           read by the client's DungeonMonsterModel
  * src/client/resources/assets/abpsmod/textures/entity/mob/<id>.png   the painted texture
  * src/main/java/dev/abps/dungeon/mob/MobSpecs.java        hitbox sizes and flags for the server
  * previews:  python3 tools/gen_mob_models.py --sheet out.png   a still of every monster
               python3 tools/gen_mob_models.py --video out.mp4   every monster walking, looking and attacking

Spec units are pixels with y up from the feet and +z the way the mob faces. Minecraft's model space has y down
(feet at y=24) and faces -z, so everything is turned 180 degrees round x on the way out. Cubes are rounded to
whole pixels like vanilla models, and every cube gets a painted, box-UV mapped texture.

The animation in render() mirrors DungeonMonsterModel.setupAnim exactly, so the videos show what the game does.
"""
import json
import math
import os
import random
import subprocess
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
SPEC = os.path.join(ROOT, "tools", "mob_specs.txt")
MODEL_DIR = os.path.join(ROOT, "src", "client", "resources", "assets", "abpsmod", "mobs")
TEX_DIR = os.path.join(ROOT, "src", "client", "resources", "assets", "abpsmod", "textures", "entity", "mob")
JAVA = os.path.join(ROOT, "src", "main", "java", "dev", "abps", "dungeon", "mob", "MobSpecs.java")

FLYERS = {"frost_wraith", "frost_sprite", "ember_imp", "shade"}
TITLES = {
    "grave_knight": "Grave Knight", "bone_caller": "Bone Caller", "ghoul": "Ghoul", "crypt_weaver": "Crypt Weaver",
    "frost_wraith": "Frost Wraith", "ice_brute": "Ice Brute", "frost_sprite": "Frost Sprite", "magma_brute": "Magma Brute",
    "ember_imp": "Ember Imp", "forge_warden": "Forge Warden", "sculk_lurker": "Sculk Lurker", "echo_witch": "Echo Witch",
    "shade": "Shade", "hollow_king": "The Hollow King", "glacial_warden": "The Glacial Warden", "infernal_colossus": "The Infernal Colossus",
}


# ================================================================ maths

def rot_x(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def rot_y(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def rot_z(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def zyx(rx, ry, rz):
    """Minecraft model parts turn in z, y, x order."""
    return rot_z(rz) @ rot_y(ry) @ rot_x(rx)


# ================================================================ specs -> model

def parse_specs():
    mobs, cur = {}, None
    for raw in open(SPEC):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("=="):
            cur = line[2:].strip()
            mobs[cur] = []
        else:
            mobs[cur].append(line)
    return mobs


def build(name, lines):
    """Turns a spec into a part tree in Minecraft model space."""
    pivots = {}
    cfg = {"float": 0.0, "pitch": 0.0, "arms": 0.0, "swing": 0.7, "orbit": 1.0, "size": 1.0}
    boxes = []
    for line in lines:
        t = line.split()
        if t[0] == "pivot":
            pivots[t[1]] = [float(v) for v in t[2:5]]
        elif t[0] in cfg:
            cfg[t[0]] = float(t[1])
        elif t[0] in ("box", "glow"):
            b = {"bone": t[1], "block": t[2], "from": [float(v) for v in t[3:6]], "size": [float(v) for v in t[6:9]],
                 "r": (0.0, 0.0, 0.0), "o": (0.0, 0.0, 0.0), "p": 0.0, "glow": t[0] == "glow"}
            for e in t[9:]:
                if e.startswith("r="):
                    b["r"] = tuple(float(v) for v in e[2:].split(","))
                elif e.startswith("o="):
                    b["o"] = tuple(float(v) for v in e[2:].split(","))
                elif e.startswith("p="):
                    b["p"] = float(e[2:])
            boxes.append(b)
    k = cfg["size"]

    def conv_pivot(p):
        return [p[0] * k, 24 - p[1] * k, -p[2] * k]

    def int_box(frm, size):
        """Whole pixels, keeping the middle where it was. Returns spec-space corner and size."""
        out_f, out_s = [], []
        for i in range(3):
            s = max(1, int(round(size[i] * k)))
            c = (frm[i] + size[i] / 2) * k
            out_f.append(c - s / 2)
            out_s.append(s)
        return out_f, out_s

    def cube_from_spec(frm, size):
        """A cube in spec space (relative to its part) as a Minecraft cube relative to its part."""
        return [frm[0], -(frm[1] + size[1]), -(frm[2] + size[2])], size

    parts = [{"name": "root", "parent": None, "pivot": [0, 24, 0], "rot": [0, 0, 0], "cubes": []}]
    root_pivot_spec = [0, 0, 0]
    bone_parts = {}
    legs = []
    counter = [0]

    def add_part(name, parent, pivot, rot):
        parts.append({"name": name, "parent": parent, "pivot": pivot, "rot": rot, "cubes": []})
        return parts[-1]

    def bone_part(bone):
        if bone in bone_parts:
            return bone_parts[bone]
        piv = pivots.get(bone, [0, 0, 0])
        rot = [0, 0, 0]
        if bone == "BODY":
            rot = [math.radians(cfg["pitch"]), 0, 0]
        if bone in ("ARM_L", "ARM_R"):
            rot = [math.radians(cfg["arms"]), 0, 0]
        if bone == "CAPE":
            rot = [0.08, 0, 0]
        # Parts hang off the root, whose pivot is at the feet (y=24): offsets are relative to it
        p = conv_pivot(piv)
        part = add_part(bone.lower(), "root", [p[0], p[1] - 24, p[2]], rot)
        part["spec_pivot"] = piv
        bone_parts[bone] = part
        return part

    for b in boxes:
        bone = b["bone"]
        if bone in ("LEGS", "QLEGS"):
            # Every leg is its own part at its root, so it can swing round its own hip
            piv = pivots.get(bone, [0, 0, 0])
            spec_root = [piv[i] + b["from"][i] for i in range(3)]
            p = conv_pivot(spec_root)
            counter[0] += 1
            pname = ("leg" if bone == "LEGS" else "qleg") + str(counter[0])
            rx, ry, rz = (math.radians(v) for v in b["r"])
            outer = add_part(pname, "root", [p[0], p[1] - 24, p[2]], [0, -ry, 0])
            mid = add_part(pname + "x", pname, [0, 0, 0], [rx, 0, 0])
            inner = add_part(pname + "z", pname + "x", [0, 0, 0], [0, 0, -rz])
            frm, size = int_box([b["o"][0], b["o"][1], b["o"][2]], b["size"])
            c_from, c_size = cube_from_spec(frm, size)
            inner["cubes"].append({"from": c_from, "size": c_size, "block": b["block"], "glow": b["glow"]})
            legs.append({"part": pname, "phase": math.radians(b["p"]), "type": "spider" if bone == "LEGS" else "quad"})
            continue
        part = bone_part(bone)
        rel = b["from"]
        if b["r"] != (0.0, 0.0, 0.0) or b["o"] != (0.0, 0.0, 0.0):
            # A turned box: a little chain of parts at its corner, turning y, then x, then z like the spec says
            counter[0] += 1
            pname = part["name"] + "_t" + str(counter[0])
            rx, ry, rz = (math.radians(v) for v in b["r"])
            corner = [rel[0] * k, -rel[1] * k, -rel[2] * k]
            add_part(pname, part["name"], corner, [0, -ry, 0])
            add_part(pname + "x", pname, [0, 0, 0], [rx, 0, 0])
            inner = add_part(pname + "z", pname + "x", [0, 0, 0], [0, 0, -rz])
            frm, size = int_box([b["o"][0], b["o"][1], b["o"][2]], b["size"])
            c_from, c_size = cube_from_spec(frm, size)
            inner["cubes"].append({"from": c_from, "size": c_size, "block": b["block"], "glow": b["glow"]})
        else:
            frm, size = int_box(rel, b["size"])
            c_from, c_size = cube_from_spec(frm, size)
            part["cubes"].append({"from": c_from, "size": c_size, "block": b["block"], "glow": b["glow"]})

    anim = {"swing": cfg["swing"], "float": cfg["float"] * k, "orbit": cfg["orbit"]}
    return {"name": name, "parts": parts, "legs": legs, "anim": anim, "flies": name in FLYERS}


# ================================================================ painting

PALETTE = {
    "polished_deepslate": 0x4a4a52, "iron_block": 0xc9ced3, "polished_blackstone": 0x3a3540, "chiseled_stone_bricks": 0x8d8d8d,
    "verdant_froglight": 0x9ef59a, "dark_oak_planks": 0x553826, "gold_block": 0xf2c230, "bone_block": 0xe3dcc2,
    "sea_lantern": 0xbdf2f0, "redstone_block": 0xe01818, "blackstone": 0x2c2832, "packed_ice": 0x8db6f0, "blue_ice": 0x6aa4f2,
    "ice": 0xa9ccff, "snow_block": 0xf2f8f8, "magma_block": 0x8a3410, "shroomlight": 0xffa23a, "basalt": 0x56565e,
    "nether_bricks": 0x44191e, "blast_furnace": 0x6c6c70, "chiseled_polished_blackstone": 0x3c3742, "netherite_block": 0x4a424c,
    "gilded_blackstone": 0x2e2a30, "sculk": 0x0f3440, "amethyst_block": 0x9a66d6, "crying_obsidian": 0x8a30f0, "calcite": 0xdedcd6,
    "emerald_block": 0x2fd37a,
    "concrete:black": 0x18181e, "concrete:cyan": 0x16949a, "concrete:gray": 0x4b5054, "concrete:green": 0x55662a,
    "concrete:purple": 0x6a24aa, "concrete:white": 0xe4e8ea, "concrete:light_blue": 0x3aa6de, "concrete:red": 0x9a2424,
    "glass:light_blue": 0xa6d8f4, "glass:white": 0xf2f4f6, "glass:cyan": 0x48e0e0, "glass:black": 0x26262e, "glass:purple": 0x8a46cc,
}


def material(block):
    b = block.split("[")[0]
    if b.startswith("concrete:"):
        return "cloth"
    if b.startswith("glass:"):
        return "glass"
    return {
        "iron_block": "plate", "netherite_block": "darkplate", "gold_block": "gold", "polished_deepslate": "stone", "polished_blackstone": "stone",
        "blackstone": "rock", "basalt": "rock", "calcite": "stone", "chiseled_stone_bricks": "carved", "chiseled_polished_blackstone": "carved",
        "dark_oak_planks": "wood", "bone_block": "bone", "packed_ice": "ice", "blue_ice": "ice", "ice": "ice", "snow_block": "snow",
        "magma_block": "magma", "nether_bricks": "brick", "blast_furnace": "furnace", "gilded_blackstone": "gilded", "sculk": "sculk",
        "amethyst_block": "crystal",
    }.get(b, "glow" if b in ("verdant_froglight", "sea_lantern", "shroomlight", "redstone_block", "crying_obsidian", "emerald_block") else "stone")


def rgb(c):
    return np.array([(c >> 16) & 255, (c >> 8) & 255, c & 255], dtype=float)


def shade(col, k):
    """Lighter for k > 0, darker for k < 0, with a slight warm/cool hue shift like hand-shaded pixel art."""
    c = col.copy()
    if k > 0:
        c = c + (255 - c) * k
        c[0] += 6 * k
    else:
        c = c * (1 + k)
        c[2] += 10 * -k
    return np.clip(c, 0, 255)


def paint(mat, face, w, h, base, rnd):
    """Paints one face. face is top, bottom, side, front or back. Returns an h x w x 4 array."""
    img = np.zeros((h, w, 4))
    img[..., 3] = 255
    noise = np.array([[rnd.random() for _ in range(w)] for _ in range(h)])
    col = rgb(base)

    def fill(c):
        img[..., :3] = c

    def tone(k):
        return shade(col, k)

    if mat in ("plate", "darkplate", "gold"):
        # Polished plate: a soft vertical gradient, a bright bevel along the top and left, a dark one bottom/right,
        # a seam across tall faces and rivets in the corners of big ones
        for y in range(h):
            k = 0.18 - 0.36 * (y / max(1, h - 1))
            img[y, :, :3] = tone(k)
        img[..., :3] += (noise[..., None] - 0.5) * (14 if mat != "gold" else 18)
        if mat == "gold":
            for d in range(-h, w):
                for i in range(max(0, -d), min(h, w - d)):
                    if (i + d) % 7 in (0, 1):
                        img[i, i + d, :3] = tone(0.45)
        if h >= 3 and w >= 3:
            img[0, :, :3] = tone(0.4)
            img[:, 0, :3] = tone(0.28)
            img[-1, :, :3] = tone(-0.45)
            img[:, -1, :3] = tone(-0.3)
        if h >= 8 and face in ("front", "back", "side"):
            img[h // 2, 1:-1, :3] = tone(-0.35)
            img[h // 2 + 1, 1:-1, :3] = tone(0.25)
        if w >= 6 and h >= 6 and face != "bottom":
            for (y, x) in ((1, 1), (1, w - 2), (h - 2, 1), (h - 2, w - 2)):
                img[y, x, :3] = tone(0.55)
    elif mat in ("stone", "rock", "carved"):
        fill(tone(0))
        img[..., :3] += (noise[..., None] - 0.5) * (30 if mat == "rock" else 18)
        if mat == "rock":
            for _ in range(max(1, w * h // 18)):
                y, x = rnd.randrange(h), rnd.randrange(w)
                img[y, x, :3] = tone(-0.35)
        if mat == "carved" and w >= 4 and h >= 4:
            img[1, 1:-1, :3] = tone(-0.3)
            img[-2, 1:-1, :3] = tone(0.2)
            img[1:-1, 1, :3] = tone(-0.25)
            img[1:-1, -2, :3] = tone(0.15)
            if w >= 6 and h >= 6:
                cy, cx = h // 2, w // 2
                img[cy - 1:cy + 1, cx - 1:cx + 1, :3] = tone(-0.4)
        if h >= 3 and w >= 3:
            img[0, :, :3] = np.minimum(img[0, :, :3] + 25, 255)
            img[-1, :, :3] *= 0.7
    elif mat == "cloth":
        fill(tone(0))
        img[..., :3] += (noise[..., None] - 0.5) * 12
        if face in ("front", "back", "side") and w >= 3:
            # Folds: darker creases every few pixels that wander a little
            for x0 in range(1, w, 3):
                x = x0
                for y in range(h):
                    if 0 <= x < w:
                        img[y, x, :3] = tone(-0.22)
                    if rnd.random() < 0.15:
                        x += rnd.choice((-1, 1))
            img[-1, :, :3] = tone(-0.35)
            img[0, :, :3] = tone(0.12)
        if face == "top":
            img[..., :3] = tone(0.08) + (noise[..., None] - 0.5) * 10
    elif mat == "wood":
        fill(tone(0))
        for x in range(w):
            k = (0.12 if x % 3 == 0 else -0.12 if x % 3 == 2 else 0)
            img[:, x, :3] = tone(k) + (noise[:, x, None] - 0.5) * 12
        for _ in range(max(1, w * h // 30)):
            img[rnd.randrange(h), rnd.randrange(w), :3] = tone(-0.4)
    elif mat == "bone":
        fill(tone(0))
        img[..., :3] += (noise[..., None] - 0.5) * 16
        for y in range(1, h, 4):
            img[y, :, :3] = tone(-0.15)
        if h >= 3 and w >= 3:
            img[0, :, :3] = tone(0.2)
            img[-1, :, :3] = tone(-0.3)
            img[:, -1, :3] = tone(-0.2)
    elif mat in ("ice", "snow"):
        fill(tone(0))
        img[..., :3] += (noise[..., None] - 0.5) * (10 if mat == "snow" else 18)
        if mat == "ice":
            for d in range(-h, w, 5):
                for i in range(h):
                    x = i + d
                    if 0 <= x < w:
                        img[i, x, :3] = tone(0.45)
                        if x + 1 < w:
                            img[i, x + 1, :3] = tone(0.2)
            if h >= 3 and w >= 3:
                img[0, :, :3] = tone(0.5)
                img[:, 0, :3] = tone(0.3)
                img[-1, :, :3] = tone(-0.3)
        else:
            for _ in range(max(1, w * h // 12)):
                img[rnd.randrange(h), rnd.randrange(w), :3] = tone(-0.08)
    elif mat == "magma":
        img[..., :3] = shade(rgb(0x2a1410), 0) + (noise[..., None] - 0.5) * 16
        # Glowing cracks: short random walks of bright orange with a dimmer halo
        for _ in range(max(1, w * h // 22)):
            y, x = rnd.randrange(h), rnd.randrange(w)
            for _ in range(rnd.randint(3, 7)):
                if 0 <= y < h and 0 <= x < w:
                    img[y, x, :3] = rgb(0xffb040)
                    for dy, dx in ((0, 1), (1, 0), (0, -1), (-1, 0)):
                        yy, xx = y + dy, x + dx
                        if 0 <= yy < h and 0 <= xx < w and img[yy, xx, 0] < 150:
                            img[yy, xx, :3] = rgb(0xb4401a)
                y += rnd.choice((-1, 0, 1))
                x += rnd.choice((-1, 0, 1))
    elif mat == "brick":
        fill(tone(0))
        img[..., :3] += (noise[..., None] - 0.5) * 14
        for y in range(h):
            if y % 3 == 2:
                img[y, :, :3] = tone(-0.45)
            else:
                for x in range(w):
                    if (x + (y // 3) * 2) % 5 == 4:
                        img[y, x, :3] = tone(-0.45)
    elif mat == "furnace":
        fill(tone(0))
        img[..., :3] += (noise[..., None] - 0.5) * 18
        if face == "front" and w >= 5 and h >= 5:
            # A dark mouth with a fire glowing in it
            y0, y1, x0, x1 = h // 2, h - 2, 1, w - 1
            img[y0:y1, x0:x1, :3] = rgb(0x1a1414)
            for x in range(x0, x1):
                img[y1 - 1, x, :3] = rgb(0xff9a30)
                if rnd.random() < 0.6:
                    img[y1 - 2, x, :3] = rgb(0xffd060)
            img[2, 1:-1, :3] = tone(-0.4)
        if h >= 3:
            img[0, :, :3] = tone(0.25)
            img[-1, :, :3] = tone(-0.4)
    elif mat == "gilded":
        img[..., :3] = shade(col, 0) + (noise[..., None] - 0.5) * 18
        for _ in range(max(1, w * h // 6)):
            img[rnd.randrange(h), rnd.randrange(w), :3] = rgb(0xe8b030) if rnd.random() < 0.7 else rgb(0xffe080)
    elif mat == "sculk":
        img[..., :3] = shade(col, 0) + (noise[..., None] - 0.5) * 16
        for _ in range(max(1, w * h // 14)):
            y, x = rnd.randrange(h), rnd.randrange(w)
            img[y, x, :3] = rgb(0x30e0e8)
            if x + 1 < w:
                img[y, x + 1, :3] = rgb(0x1a8a98)
    elif mat == "crystal":
        fill(tone(0))
        for d in range(-h, w, 3):
            for i in range(h):
                x = i + d
                if 0 <= x < w:
                    img[i, x, :3] = tone(0.3 if (d // 3) % 2 else -0.2)
    elif mat == "glass":
        img[..., :3] = shade(col, 0.1) + (noise[..., None] - 0.5) * 10
        for d in range(-h, w, 6):
            for i in range(h):
                x = i + d
                if 0 <= x < w:
                    img[i, x, :3] = shade(col, 0.6)
        # A few clear pixels so it reads as a wisp, not a solid block
        for _ in range(w * h // 9):
            img[rnd.randrange(h), rnd.randrange(w), 3] = 0
        img[0, :, 3] = 255
    elif mat == "glow":
        img[..., :3] = shade(col, 0.15)
        if w >= 3 and h >= 3:
            img[1:-1, 1:-1, :3] = shade(col, 0.45)
        img[..., :3] += (noise[..., None] - 0.5) * 10
    if face == "top":
        img[..., :3] = img[..., :3] * 1.04
    if face == "bottom":
        img[..., :3] = img[..., :3] * 0.85
    img[..., :3] = np.clip(img[..., :3], 0, 255)
    return img


def layout(model):
    """Gives every cube a place on the texture (Minecraft's box layout) and paints it."""
    cubes = [c for p in model["parts"] for c in p["cubes"]]
    regions = []
    for c in cubes:
        w, h, d = (int(round(v)) for v in c["size"])
        regions.append((2 * d + 2 * w, d + h, c))
    # Shelf packing, tallest first
    tex_w = 64
    while True:
        order = sorted(range(len(regions)), key=lambda i: -regions[i][1])
        x = y = shelf = 0
        ok = True
        for i in order:
            rw, rh, c = regions[i]
            if rw > tex_w:
                ok = False
                break
            if x + rw > tex_w:
                x, y, shelf = 0, y + shelf, 0
            c["uv"] = [x, y]
            x += rw
            shelf = max(shelf, rh)
        tex_h = y + shelf
        if ok and tex_h <= tex_w * 2:
            break
        tex_w *= 2
    tex_h = 1 << max(5, (tex_h - 1).bit_length())
    if tex_h < tex_w // 2:
        tex_h = tex_w // 2
    tex = np.zeros((tex_h, tex_w, 4))
    rnd = random.Random(model["name"])
    for rw, rh, c in regions:
        u, v = c["uv"]
        w, h, d = (int(round(s)) for s in c["size"])
        base = PALETTE.get(c["block"].split("[")[0], 0x808080)
        mat = material(c["block"])
        faces = {
            "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
            "side1": (u, v + d, d, h), "front": (u + d, v + d, w, h), "side2": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h),
        }
        for fname, (fx, fy, fw, fh) in faces.items():
            if fw <= 0 or fh <= 0:
                continue
            kind = "side" if fname.startswith("side") else fname
            tex[fy:fy + fh, fx:fx + fw] = paint(mat, kind, fw, fh, base, rnd)
    return tex, tex_w, tex_h


# ================================================================ output for the game

def export(model, tex, tw, th):
    os.makedirs(MODEL_DIR, exist_ok=True)
    os.makedirs(TEX_DIR, exist_ok=True)
    Image.fromarray(tex.astype(np.uint8), "RGBA").save(os.path.join(TEX_DIR, model["name"] + ".png"))
    out = {"texture": [tw, th], "anim": model["anim"], "flies": model["flies"], "legs": model["legs"], "parts": []}
    for p in model["parts"]:
        out["parts"].append({"name": p["name"], "parent": p["parent"], "pivot": [round(v, 4) for v in p["pivot"]],
                             "rot": [round(v, 5) for v in p["rot"]],
                             "cubes": [{"uv": c["uv"], "from": [round(v, 4) for v in c["from"]], "size": [int(round(v)) for v in c["size"]]}
                                       for c in p["cubes"]]})
    with open(os.path.join(MODEL_DIR, model["name"] + ".json"), "w") as f:
        json.dump(out, f, separators=(",", ":"))


def bounds(model):
    """Hitbox size in blocks from the model at rest."""
    pts = []
    for verts, *_ in faces_of(model, pose(model, 0, 0, 0, 0, 0, 0)):
        pts += verts
    pts = np.array(pts)
    w = max(pts[:, 0].max() - pts[:, 0].min(), pts[:, 2].max() - pts[:, 2].min()) / 16
    h = (pts[:, 1].max() - min(0, pts[:, 1].min())) / 16
    return min(w * 0.8, 2.6), h


def write_java(models):
    rows = []
    for m in models:
        w, h = bounds(m)
        rows.append('        add("%s", %.2ff, %.2ff, %s);' % (m["name"], max(0.4, w), max(0.5, h * 0.95), "true" if m["flies"] else "false"))
    src = """package dev.abps.dungeon.mob;

import java.util.LinkedHashMap;
import java.util.Map;

/** Hitbox sizes and flags of the dungeon monsters. Generated by tools/gen_mob_models.py from tools/mob_specs.txt. */
public final class MobSpecs {

    public record Spec(String id, float width, float height, boolean flies) {
    }

    public static final Map<String, Spec> ALL = new LinkedHashMap<>();

    private static void add(String id, float width, float height, boolean flies) {
        ALL.put(id, new Spec(id, width, height, flies));
    }

    static {
%s
    }

    private MobSpecs() {
    }
}
""" % "\n".join(rows)
    os.makedirs(os.path.dirname(JAVA), exist_ok=True)
    open(JAVA, "w").write(src)


# ================================================================ animation (mirrors DungeonMonsterModel.setupAnim)

def pose(model, age, walk_pos, walk_speed, head_yaw, head_pitch, attack):
    """Extra rotation for every part this frame, and how far the whole body is lifted (pixels)."""
    a = model["anim"]
    swing = a["swing"] / 0.7
    extra = {}
    leg = math.cos(walk_pos * 0.6662) * 1.4 * walk_speed * swing * 0.7
    arm = math.cos(walk_pos * 0.6662) * walk_speed * swing * 0.8
    raise_ = -math.sin(attack * math.pi) * 1.4
    extra["leg_l"] = (leg, 0, 0)
    extra["leg_r"] = (-leg, 0, 0)
    extra["arm_l"] = (-arm + raise_, 0, 0)
    extra["arm_r"] = (arm + raise_, 0, 0)
    extra["head"] = (math.radians(head_pitch), math.radians(head_yaw), 0)
    extra["tail"] = (0, math.sin(age * 0.2) * 0.35, 0)
    extra["cape"] = (0.3 * walk_speed + math.sin(age * 0.13) * 0.04, 0, 0)
    extra["wing_l"] = (0, math.sin(age * 0.4) * 0.55, 0)
    extra["wing_r"] = (0, -math.sin(age * 0.4) * 0.55, 0)
    extra["orbit"] = (0, age * a["orbit"] * 0.1, 0)
    for lg in model["legs"]:
        if lg["type"] == "spider":
            extra[lg["part"]] = (0, math.sin(walk_pos * 0.6662 * 1.5 + lg["phase"]) * 0.35 * walk_speed, 0)
        else:
            extra[lg["part"]] = (math.sin(walk_pos * 0.6662 + lg["phase"]) * 0.6 * walk_speed, 0, 0)
    lift = a["float"] + (math.sin(age * 0.16) * 1.3 if a["float"] > 0 else 0)
    return extra, lift


def faces_of(model, posed):
    """Every cube face in spec space (y up, +z front): (corners, uv rect, which face)."""
    extra, lift = posed
    by_name = {p["name"]: p for p in model["parts"]}
    mats = {}

    def world(p):
        if p["name"] in mats:
            return mats[p["name"]]
        rx, ry, rz = p["rot"]
        ex = extra.get(p["name"], (0, 0, 0))
        r = zyx(rx + ex[0], ry + ex[1], rz + ex[2])
        t = np.array(p["pivot"], dtype=float)
        if p["name"] == "root":
            t = t - np.array([0, lift, 0])
        if p["parent"]:
            pr, pt = world(by_name[p["parent"]])
            m = (pr @ r, pt + pr @ t)
        else:
            m = (r, t)
        mats[p["name"]] = m
        return m

    out = []
    for p in model["parts"]:
        r, t = world(p)
        for c in p["cubes"]:
            x0, y0, z0 = c["from"]
            w, h, d = c["size"]
            x1, y1, z1 = x0 + w, y0 + h, z0 + d
            u, v = c["uv"]
            # Minecraft's box faces (model space, y down): corners listed so texture (0,0) is the first corner,
            # (1,0) the second and (0,1) the fourth
            fl = {
                "top": ([(x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1)], (u + d, v, w, d)),
                "bottom": ([(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)], (u + d + w, v, w, d)),
                "west": ([(x0, y0, z1), (x0, y0, z0), (x0, y1, z0), (x0, y1, z1)], (u, v + d, d, h)),
                "front": ([(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)], (u + d, v + d, w, h)),
                "east": ([(x1, y0, z0), (x1, y0, z1), (x1, y1, z1), (x1, y1, z0)], (u + d + w, v + d, d, h)),
                "back": ([(x1, y0, z1), (x0, y0, z1), (x0, y1, z1), (x1, y1, z1)], (u + 2 * d + w, v + d, w, h)),
            }
            for fname, (corners, rect) in fl.items():
                verts = []
                for q in corners:
                    wv = r @ np.array(q, dtype=float) + t
                    verts.append((wv[0], 24 - wv[1], -wv[2]))  # back to spec space
                out.append((verts, rect, fname, c))
    return out


# ================================================================ rendering previews

LIGHT = {"top": 1.0, "bottom": 0.5, "front": 0.8, "back": 0.8, "west": 0.6, "east": 0.6}


def render(model, tex, size, cam_yaw, cam_pitch, mob_yaw, posed, zoom, center_y, bg):
    """Software rasterizer: orthographic camera, z-buffer, nearest-texel sampling, Minecraft-like face shading."""
    img = bg.copy()
    zbuf = np.full((size, size), -1e9)
    cy, sy = math.cos(math.radians(cam_yaw + mob_yaw)), math.sin(math.radians(cam_yaw + mob_yaw))
    cp, sp = math.cos(math.radians(cam_pitch)), math.sin(math.radians(cam_pitch))
    th, tw = tex.shape[:2]
    for verts, (fu, fv, fw, fh), fname, cube in faces_of(model, posed):
        if fw <= 0 or fh <= 0:
            continue
        pts = []
        for x, y, z in verts:
            x2 = x * cy + z * sy
            z2 = -x * sy + z * cy
            y2 = y * cp - z2 * sp
            z3 = y * sp + z2 * cp
            pts.append((size / 2 + x2 * zoom, size / 2 - (y2 - center_y) * zoom, z3))
        p0, p1, p3 = np.array(pts[0]), np.array(pts[1]), np.array(pts[3])
        e1, e2 = p1 - p0, p3 - p0
        det = e1[0] * e2[1] - e1[1] * e2[0]
        if abs(det) < 1e-6:
            continue
        xs = [p[0] for p in pts]
        ys = [p[1] for p in pts]
        x0, x1 = max(0, int(min(xs))), min(size - 1, int(max(xs)) + 1)
        y0, y1 = max(0, int(min(ys))), min(size - 1, int(max(ys)) + 1)
        if x0 > x1 or y0 > y1:
            continue
        gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
        dx, dy = gx - p0[0], gy - p0[1]
        s = (dx * e2[1] - dy * e2[0]) / det
        t = (e1[0] * dy - e1[1] * dx) / det
        inside = (s >= 0) & (s < 1) & (t >= 0) & (t < 1)
        if not inside.any():
            continue
        depth = p0[2] + s * e1[2] + t * e2[2]
        tu = np.clip((fu + s * fw).astype(int), 0, tw - 1)
        tv = np.clip((fv + t * fh).astype(int), 0, th - 1)
        texel = tex[tv, tu]
        region = zbuf[y0:y1 + 1, x0:x1 + 1]
        mask = inside & (texel[..., 3] > 25) & (depth > region)
        if not mask.any():
            continue
        lit = 1.0 if material(cube["block"]) == "glow" else LIGHT[fname]
        target = img[y0:y1 + 1, x0:x1 + 1]
        target[mask] = texel[..., :3][mask] * lit
        region[mask] = depth[mask]
    return img


def backdrop(size, floor_y):
    """A dark dungeon floor and wall to stand the monsters on."""
    bg = np.zeros((size, size, 3))
    for y in range(size):
        k = y / size
        bg[y, :] = (np.array([22, 20, 30]) * (1 - k) + np.array([40, 36, 48]) * k)
    fy = int(floor_y)
    for y in range(max(0, fy), size):
        for x in range(0, size, 1):
            tile = ((x // 24) + ((y - fy) // 12)) % 2
            bg[y, x] = (52, 48, 58) if tile else (44, 40, 50)
    return bg


def mob_view(model):
    pts = np.array([v for verts, *_ in faces_of(model, pose(model, 0, 0, 0, 0, 0, 0)) for v in verts])
    h = pts[:, 1].max()
    span = max(h, pts[:, 0].max() - pts[:, 0].min(), pts[:, 2].max() - pts[:, 2].min())
    return h, span


def sheet(models, path):
    size, cols = 300, 4
    rows = (len(models) + cols - 1) // cols
    out = Image.new("RGB", (size * cols, size * rows), (16, 16, 20))
    font = font_of(18)
    for i, (m, tex) in enumerate(models):
        h, span = mob_view(m)
        zoom = (size * 0.78) / max(span, 1)
        center = h / 2
        bg = backdrop(size, size / 2 + center * zoom * math.cos(math.radians(14)))
        img = render(m, tex, size, 30, 14, 0, pose(m, 10, 0, 0, 0, 0, 0), zoom, center, bg)
        tile = Image.fromarray(img.astype(np.uint8))
        ImageDraw.Draw(tile).text((10, 8), TITLES.get(m["name"], m["name"]), fill=(255, 230, 140), font=font)
        out.paste(tile, ((i % cols) * size, (i // cols) * size))
    out.save(path)


def font_of(px):
    for f in ("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", "/usr/share/fonts/dejavu/DejaVuSans-Bold.ttf"):
        if os.path.exists(f):
            return ImageFont.truetype(f, px)
    return ImageFont.load_default()


def video(models, path, seconds_each=5, fps=24, size=480):
    """Each monster in turn: it walks toward the camera while the camera circles, looks around and attacks."""
    tmp = os.path.join(os.path.dirname(path) or ".", "_frames")
    os.makedirs(tmp, exist_ok=True)
    for f in os.listdir(tmp):
        os.remove(os.path.join(tmp, f))
    big, small = font_of(26), font_of(16)
    n = 0
    for m, tex in models:
        h, span = mob_view(m)
        zoom = (size * 0.62) / max(span, 1)
        center = h / 2
        bg = backdrop(size, size / 2 + center * zoom * math.cos(math.radians(12)) + 4)
        frames = seconds_each * fps
        for f in range(frames):
            age = f * 20 / fps
            phase = f / frames
            walking = phase < 0.62
            speed = 1.0 if walking else max(0.0, 1 - (phase - 0.62) * 8)
            walk_pos = age * 0.62
            head_yaw = math.sin(age * 0.09) * 35 if not walking else math.sin(age * 0.05) * 12
            head_pitch = 12 * math.sin(age * 0.07)
            attack = 0.0
            for at in (0.72, 0.86):
                if at <= phase < at + 0.08:
                    attack = (phase - at) / 0.08
            cam = -50 + 140 * phase
            img = render(m, tex, size, cam, 12, 0, pose(m, age, walk_pos, speed, head_yaw, head_pitch, attack), zoom, center, bg)
            frame = Image.fromarray(img.astype(np.uint8))
            d = ImageDraw.Draw(frame)
            d.text((16, 12), TITLES.get(m["name"], m["name"]), fill=(255, 226, 120), font=big)
            label = "walking" if walking else ("attacking" if attack > 0 else "looking around")
            d.text((16, 44), label, fill=(190, 190, 205), font=small)
            frame.save(os.path.join(tmp, "f%05d.png" % n))
            n += 1
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-framerate", str(fps), "-i", os.path.join(tmp, "f%05d.png"),
                    "-c:v", "libx264", "-pix_fmt", "yuv420p", "-crf", "20", path], check=True)
    for f in os.listdir(tmp):
        os.remove(os.path.join(tmp, f))
    os.rmdir(tmp)


def main():
    specs = parse_specs()
    only = None
    if "--only" in sys.argv:
        only = sys.argv[sys.argv.index("--only") + 1].split(",")
    models = []
    for name, lines in specs.items():
        if only and name not in only:
            continue
        m = build(name, lines)
        tex, tw, th = layout(m)
        models.append((m, tex))
        if not only:
            export(m, tex, tw, th)
    if not only:
        write_java([m for m, _ in models])
    if "--sheet" in sys.argv:
        sheet(models, sys.argv[sys.argv.index("--sheet") + 1])
    if "--video" in sys.argv:
        video(models, sys.argv[sys.argv.index("--video") + 1])
    print("built", len(models), "models")


if __name__ == "__main__":
    main()
