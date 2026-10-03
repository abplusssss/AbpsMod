#!/usr/bin/env python3
"""
Generates the Extras, Travel, Building and QoL content: backpacks, grappling hook, magnet charm, builder's wand,
ruby apple, sleeping bag, mob trophies, hang glider and Ender Wings; waystones, chairs, stools, tables and display
pedestals; and eight new paintings painted here pixel by pixel.

    VANILLA=/path/to/minecraft-assets-26.3 python3 tools/gen_extras.py
"""
import json
import math
import os
import random
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_food import (ASSETS, DATA, NS, I, M, ANY, RED, hexrgb, load, recolor, save, shaped, shapeless, sprite, tag, write, item_model,
                      box, self_drop)

PAL = {"o": "#1E1A1A", "G": "#9AA0A8", "g": "#5E646C", "W": "#E8E8EE", "R": "#C8303A", "r": "#86202A", "B": "#3A6AD8", "b": "#22408A",
       "Y": "#F2C83A", "y": "#B8901E", "K": "#7A4A26", "k": "#4E2E16", "C": "#D8C8A0", "c": "#A89870", "P": "#8A4AD8", "p": "#5A2E96"}

DRAWN = {
    "grappling_hook": ("Grappling Hook", [
        "..........G.G...", ".........GWGWG..", "..........GgG...", "...........g....", "..........g.....", ".........K......",
        "........K.......", ".......k........", "......K.........", ".....k..........", "....K...........", "...k............",
        "..KK............", ".KkK............", ".kK.............", "................"], PAL),
    "magnet_charm": ("Magnet Charm", [
        "................", "................", "...RRRR..BBBB...", "..RrrrR..BbbbB..", "..Rr..RR.B..bB..", "..Rr...RRB..bB..",
        "..Rr....R...bB..", "..Rr........bB..", "..WW........WW..", "..WW........WW..", "................", "......Y.Y.......",
        ".....Y.Y.Y......", "......Y.Y.......", "................", "................"], PAL),
    "sleeping_bag": ("Sleeping Bag", [
        "................", "................", "................", "................", "....WWWWWWWWW...", "...WCWWWWWWWWW..",
        "..RRRRRRRRRRRRR.", ".RrRRRRRRRRRRRR.", ".RRrRRRRRRRRRRr.", ".RRRrrrrrrrrrrr.", ".rRRRRRRRRRRRRr.", "..rrrrrrrrrrrr..",
        "................", "................", "................", "................"], PAL),
    "mob_trophy": ("Mob Trophy", [
        "................", "...YYYYYYYYYY...", "..Yy.YYYYYY.yY..", "..Y..YYYYYY..Y..", "..Y..YYYYYY..Y..", "...Y.YYYYYY.Y...",
        "....YYYYYYYY....", ".....yYYYYy.....", "......yYYy......", ".......YY.......", ".......YY.......", "......yYYy......",
        ".....KKKKKK.....", "....KkkkkkkK....", "....KKKKKKKK....", "................"], PAL),
    "hang_glider": ("Hang Glider", [
        "................", "................", "........R.......", ".......RRR......", "......RrRrR.....", ".....RrRRRrR....",
        "....RrRRRRRrR...", "...RrRRRRRRRrR..", "..RRRRRRRRRRRRR.", ".Wo....o....oW..", "......o.o.......", ".....o...o......",
        "....GGGGGGG.....", "................", "................", "................"], PAL),
}


# ------------------------------------------------------------------ paintings

def grad(img, top, bottom, y0=0, y1=None):
    w, h = img.size
    y1 = h if y1 is None else y1
    a, b = hexrgb(top), hexrgb(bottom)
    px = img.load()
    for y in range(y0, y1):
        t = (y - y0) / max(1, y1 - y0 - 1)
        c = tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))
        for x in range(w):
            px[x, y] = c + (255,)


def dither(img, amount=10, seed=1):
    rnd = random.Random(seed)
    px = img.load()
    for y in range(img.size[1]):
        for x in range(img.size[0]):
            r, g, b, a = px[x, y]
            d = rnd.randint(-amount, amount)
            px[x, y] = (max(0, min(255, r + d)), max(0, min(255, g + d)), max(0, min(255, b + d)), a)


def put(img, x, y, color):
    if 0 <= x < img.size[0] and 0 <= y < img.size[1]:
        img.load()[x, y] = hexrgb(color) + (255,)


def frame(img, color="#5A3A20", light="#8A5A30"):
    w, h = img.size
    for x in range(w):
        put(img, x, 0, light)
        put(img, x, h - 1, color)
    for y in range(h):
        put(img, 0, y, light)
        put(img, w - 1, y, color)


def ridge(img, base, color, rough, seed, peak=None):
    """A mountain or hill silhouette filling everything below a random ridge line."""
    rnd = random.Random(seed)
    w, h = img.size
    y = base
    for x in range(w):
        y += rnd.choice([-1, 0, 0, 1]) * rough
        if peak:
            y = min(y, base - int(peak * math.exp(-((x - w / 2) / (w / 5)) ** 2)))
        y = max(2, min(h - 1, y))
        for yy in range(int(y), h):
            put(img, x, yy, color)


def p_sunset_sea():
    im = Image.new("RGBA", (32, 16))
    grad(im, "#3A2A6A", "#F28A4A", 0, 9)
    grad(im, "#2A5A8A", "#16304E", 9, 16)
    for dx in range(-3, 4):
        for dy in range(-3, 1):
            if dx * dx + dy * dy <= 9:
                put(im, 16 + dx, 9 + dy, "#FFD25A")
    for y in range(10, 15):
        for x in range(14 - (y - 9) // 2, 19 + (y - 9) // 2, 2):
            put(im, x, y, "#F2B05A")
    dither(im, 6, 2)
    frame(im)
    return im


def p_ruby_cavern():
    im = Image.new("RGBA", (16, 16))
    grad(im, "#141018", "#2A2230")
    rnd = random.Random(3)
    for _ in range(14):
        x, y = rnd.randint(1, 14), rnd.randint(2, 14)
        put(im, x, y, "#E8344A")
        put(im, x + 1, y, "#FF8A94")
        put(im, x, y + 1, "#86202A")
    dither(im, 5, 3)
    frame(im)
    return im


def p_endite_isles():
    im = Image.new("RGBA", (32, 32))
    grad(im, "#0E0818", "#2A1240")
    rnd = random.Random(4)
    for _ in range(30):
        put(im, rnd.randint(1, 30), rnd.randint(1, 16), "#F8E4FF")
    for cx, cy, r in ((9, 20, 5), (22, 14, 4), (24, 25, 3)):
        for dx in range(-r, r + 1):
            for dy in range(0, r):
                if dx * dx / (r * r) + dy * dy / (r * r * 0.6) <= 1:
                    put(im, cx + dx, cy + dy, "#D8D2A0" if dy == 0 else "#B8B080")
        put(im, cx, cy - 1, "#933CD0")
        put(im, cx, cy - 2, "#BE6EF2")
    dither(im, 5, 4)
    frame(im)
    return im


def p_harvest_moon():
    im = Image.new("RGBA", (16, 32))
    grad(im, "#0E1A3A", "#3A3A6A", 0, 22)
    for dx in range(-4, 5):
        for dy in range(-4, 5):
            if dx * dx + dy * dy <= 16:
                put(im, 8 + dx, 8 + dy, "#F2C86A" if dx * dx + dy * dy < 9 else "#D8A040")
    grad(im, "#4E3A16", "#2A1E0A", 22, 32)
    rnd = random.Random(5)
    for x in range(1, 15):
        h = rnd.randint(3, 6)
        for y in range(22 - h, 22):
            put(im, x, y, "#C8A040" if (x + y) % 2 else "#A07A28")
    dither(im, 5, 5)
    frame(im)
    return im


def p_fishing_pier():
    im = Image.new("RGBA", (32, 16))
    grad(im, "#9AD2F2", "#E8F4FA", 0, 8)
    grad(im, "#3A8AB8", "#1E5A80", 8, 16)
    for x in range(14, 31):
        put(im, x, 8, "#8A5A30")
        put(im, x, 9, "#5A3A20")
    for x in (16, 22, 28):
        for y in range(10, 15):
            put(im, x, y, "#4A2E18")
    for y in range(4, 8):
        put(im, 20, y, "#2A2A2A")
    put(im, 20, 3, "#C8A080")
    for k in range(6):
        put(im, 21 + k, 3 - k // 2, "#5A3A20")
    for y in range(1, 10):
        put(im, 27, 3 + y, "#E8E8E8")
    dither(im, 4, 6)
    frame(im)
    return im


def p_crystal_hollows():
    im = Image.new("RGBA", (32, 32))
    grad(im, "#120A1E", "#2A1640")
    rnd = random.Random(7)
    for _ in range(9):
        x, base = rnd.randint(3, 28), rnd.choice([31, 0])
        h = rnd.randint(6, 13)
        for k in range(h):
            y = base - k if base == 31 else base + k
            wdt = max(0, (h - k) // 4)
            for dx in range(-wdt, wdt + 1):
                put(im, x + dx, y, "#B87AF2" if dx <= 0 else "#7A3AC8")
            put(im, x, y, "#F2D2FF" if k > h - 3 else "#D29AFF")
    for _ in range(10):
        put(im, rnd.randint(2, 29), rnd.randint(6, 25), "#E8344A")
    dither(im, 5, 7)
    frame(im)
    return im


def p_siege():
    im = Image.new("RGBA", (64, 32))
    grad(im, "#F28A4A", "#5A2A3A", 0, 20)
    ridge(im, 20, "#3A2A2A", 1, 8)
    grad(im, "#4A3A2A", "#2A1E16", 24, 32)
    for x in range(12, 52):
        for y in range(14, 24):
            put(im, x, y, "#8A8A8A" if (x + y) % 3 else "#6E6E6E")
    for x in range(12, 52, 3):
        put(im, x, 13, "#8A8A8A")
        put(im, x + 1, 13, "#8A8A8A")
    for x in range(29, 35):
        for y in range(18, 24):
            put(im, x, y, "#2A1A10")
    for dy in range(-2, 3):
        put(im, 32, 10 + dy, "#40E8F2")
    for x in (6, 8, 55, 58, 61, 3):
        for y in range(26, 30):
            put(im, x, y, "#1E1A16")
    dither(im, 5, 8)
    frame(im)
    return im


def p_meteor_night():
    im = Image.new("RGBA", (32, 16))
    grad(im, "#060A1E", "#1E2A4E")
    rnd = random.Random(9)
    for _ in range(26):
        put(im, rnd.randint(1, 30), rnd.randint(1, 10), "#E8E8FF")
    for start in ((4, 1), (14, 2), (24, 1)):
        x, y = start
        for k in range(7):
            put(im, x + k, y + k // 2, "#FFD25A" if k == 6 else "#F2A04A" if k > 3 else "#8A5A6A")
    ridge(im, 13, "#0E1210", 1, 10)
    dither(im, 4, 9)
    frame(im)
    return im


PAINTINGS = {  # id: (title, painter, width, height, function)
    "sunset_sea": ("Sunset Sea", p_sunset_sea),
    "ruby_cavern": ("Ruby Cavern", p_ruby_cavern),
    "endite_isles": ("The Endite Isles", p_endite_isles),
    "harvest_moon": ("Harvest Moon", p_harvest_moon),
    "fishing_pier": ("The Old Pier", p_fishing_pier),
    "crystal_hollows": ("Crystal Hollows", p_crystal_hollows),
    "siege": ("The Siege", p_siege),
    "meteor_night": ("Meteor Night", p_meteor_night),
}


# ------------------------------------------------------------------ generate

BLOCKS = {"waystone": "Waystone", "oak_chair": "Oak Chair", "spruce_chair": "Spruce Chair", "oak_stool": "Oak Stool", "oak_table": "Oak Table",
          "spruce_table": "Spruce Table", "display_pedestal": "Display Pedestal"}
FACING_BLOCKS = {"oak_chair", "spruce_chair"}


def textures():
    for iid, (_, rows, pal) in DRAWN.items():
        save(sprite(rows, pal), f"item/{iid}")
    save(recolor(load("item/bundle"), ANY, "#8A5A30"), "item/backpack")
    save(recolor(load("item/bundle"), ANY, "#C8303A"), "item/ruby_backpack")
    wand = load("item/blaze_rod")
    recolor(wand, ANY, "#8A5A30")
    px = wand.load()
    for y in range(16):
        for x in range(16):
            if px[x, y][3] > 10 and x + (15 - y) > 22:
                px[x, y] = hexrgb("#40E8F2") + (255,)
    save(wand, "item/builders_wand")
    save(recolor(load("item/golden_apple"), lambda c: c[3] > 10 and not (c[1] > c[0] and c[1] > c[2]), "#E8344A"), "item/ruby_apple")
    save(recolor(load("item/elytra"), ANY, "#7A3AC8"), "item/ender_wings")
    wings = Image.open(os.path.join(os.environ.get("VANILLA", "mc263"), "assets/minecraft/textures/entity/equipment/wings/elytra.png")).convert("RGBA")
    save(recolor(wings, ANY, "#6A2EB8"), "entity/equipment/wings/ender_wings")
    for pid, (_, fn) in PAINTINGS.items():
        save(fn(), f"painting/{pid}")


def models():
    lang = {}
    for iid, (name, _, _) in DRAWN.items():
        item_model(iid)
        lang[f"item.{NS}.{iid}"] = name
    for iid, name in (("backpack", "Backpack"), ("ruby_backpack", "Ruby Backpack"), ("builders_wand", "Builder's Wand"), ("ruby_apple", "Ruby Apple"),
                      ("ender_wings", "Ender Wings")):
        item_model(iid)
        lang[f"item.{NS}.{iid}"] = name
    write(f"{ASSETS}/equipment/ender_wings.json", {"layers": {"wings": [{"texture": f"{NS}:ender_wings"}]}})

    # Waystone: a carved stone pillar with a lodestone cap
    write(f"{ASSETS}/models/block/waystone.json", {"parent": "minecraft:block/block", "textures": {
        "particle": "minecraft:block/chiseled_stone_bricks", "base": "minecraft:block/stone_bricks", "pillar": "minecraft:block/chiseled_stone_bricks",
        "cap_top": "minecraft:block/lodestone_top", "cap": "minecraft:block/lodestone_side", "gem": "minecraft:block/amethyst_block"},
        "elements": [box((1, 0, 1), (15, 3, 15), "#base"), box((4, 3, 4), (12, 13, 12), "#pillar"), box((3, 13, 3), (13, 16, 13),
                     {"down": "#cap", "up": "#cap_top", "north": "#cap", "south": "#cap", "west": "#cap", "east": "#cap"}),
                     box((7, 8, 3.5), (9, 10, 4), "#gem", faces=("north",)), box((7, 8, 12), (9, 10, 12.5), "#gem", faces=("south",))]})
    for wood in ("oak", "spruce"):
        planks, log = f"minecraft:block/{wood}_planks", f"minecraft:block/{wood}_log"
        legs = [box((2, 0, 2), (4, 8, 4), "#log"), box((12, 0, 2), (14, 8, 4), "#log"), box((2, 0, 12), (4, 8, 12 + 2), "#log"), box((12, 0, 12), (14, 8, 14), "#log")]
        write(f"{ASSETS}/models/block/{wood}_chair.json", {"parent": "minecraft:block/block", "textures": {"particle": planks, "planks": planks, "log": log},
                                                          "elements": legs + [box((1, 8, 1), (15, 10, 15), "#planks"), box((1, 10, 13), (15, 22, 15), "#planks"),
                                                                              box((2, 12, 12.5), (14, 20, 13), "#log", faces=("north",))]})
        write(f"{ASSETS}/models/block/{wood}_table.json", {"parent": "minecraft:block/block", "textures": {"particle": planks, "planks": planks, "log": log},
                                                          "elements": [box((0, 13, 0), (16, 16, 16), "#planks"), box((1, 0, 1), (3, 13, 3), "#log"),
                                                                       box((13, 0, 1), (15, 13, 3), "#log"), box((1, 0, 13), (3, 13, 15), "#log"), box((13, 0, 13), (15, 13, 15), "#log")]})
    write(f"{ASSETS}/models/block/oak_stool.json", {"parent": "minecraft:block/block", "textures": {
        "particle": "minecraft:block/oak_planks", "planks": "minecraft:block/oak_planks", "log": "minecraft:block/oak_log", "cushion": "minecraft:block/red_wool"},
        "elements": [box((4, 0, 4), (6, 7, 6), "#log"), box((10, 0, 4), (12, 7, 6), "#log"), box((4, 0, 10), (6, 7, 12), "#log"), box((10, 0, 10), (12, 7, 12), "#log"),
                     box((3, 7, 3), (13, 9, 13), "#planks"), box((3.5, 9, 3.5), (12.5, 10, 12.5), "#cushion")]})
    write(f"{ASSETS}/models/block/display_pedestal.json", {"parent": "minecraft:block/block", "textures": {
        "particle": "minecraft:block/polished_andesite", "stone": "minecraft:block/polished_andesite", "trim": "minecraft:block/gold_block",
        "top": "minecraft:block/smooth_stone"},
        "elements": [box((2, 0, 2), (14, 2, 14), "#stone"), box((4, 2, 4), (12, 11, 12), "#stone"), box((3, 11, 3), (13, 12, 13), "#trim"),
                     box((2, 12, 2), (14, 14, 14), {"down": "#stone", "up": "#top", "north": "#stone", "south": "#stone", "west": "#stone", "east": "#stone"})]})
    for bid, name in BLOCKS.items():
        if bid in FACING_BLOCKS:
            write(f"{ASSETS}/blockstates/{bid}.json", {"variants": {f"facing={f}": ({"model": f"{NS}:block/{bid}", "y": y} if y else {"model": f"{NS}:block/{bid}"})
                                                                    for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}})
        else:
            write(f"{ASSETS}/blockstates/{bid}.json", {"variants": {"": {"model": f"{NS}:block/{bid}"}}})
        write(f"{ASSETS}/items/{bid}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/{bid}"}})
        lang[f"block.{NS}.{bid}"] = name
    for pid, (title, _) in PAINTINGS.items():
        lang[f"painting.{NS}.{pid}.title"] = title
        lang[f"painting.{NS}.{pid}.author"] = "Abps Studio"
    path = f"{ASSETS}/lang/en_us.json"
    full = json.load(open(path))
    full.update(lang)
    write(path, full)


def data():
    for bid in BLOCKS:
        self_drop(bid)
    tag("block", "minecraft", "mineable/axe", [I(b) for b in BLOCKS if "chair" in b or "table" in b or "stool" in b])
    tag("block", "minecraft", "mineable/pickaxe", [I("waystone"), I("display_pedestal")])
    for pid, (title, fn) in PAINTINGS.items():
        im = fn()
        write(f"{DATA}/{NS}/painting_variant/{pid}.json", {"asset_id": I(pid), "author": {"color": "gray", "translate": f"painting.{NS}.{pid}.author"},
                                                           "height": im.size[1] // 16, "title": {"color": "yellow", "translate": f"painting.{NS}.{pid}.title"},
                                                           "width": im.size[0] // 16})
    tag("painting_variant", "minecraft", "placeable", [I(p) for p in PAINTINGS])

    shaped("backpack", ["LSL", "LCL", "LLL"], {"L": M("leather"), "S": M("string"), "C": M("chest")}, I("backpack"), category="equipment")
    shapeless("ruby_backpack", [I("backpack"), I("ruby"), I("ruby"), I("ruby"), I("ruby"), M("chest")], I("ruby_backpack"), category="equipment")
    shaped("grappling_hook", [" II", " SI", "S  "], {"I": M("iron_ingot"), "S": M("string")}, I("grappling_hook"), category="equipment")
    shaped("magnet_charm", ["R B", "I I", " G "], {"R": M("redstone"), "B": M("lapis_lazuli"), "I": M("iron_ingot"), "G": M("gold_ingot")}, I("magnet_charm"))
    shaped("builders_wand", ["  A", " S ", "S  "], {"A": M("amethyst_shard"), "S": M("stick")}, I("builders_wand"), category="equipment")
    shaped("ruby_apple", ["RRR", "RAR", "RRR"], {"R": I("ruby"), "A": M("apple")}, I("ruby_apple"))
    shaped("sleeping_bag", ["WWW", "LLL"], {"W": "#minecraft:wool", "L": M("leather")}, I("sleeping_bag"))
    shaped("hang_glider", ["PPP", "SLS", " S "], {"P": M("phantom_membrane"), "S": M("stick"), "L": M("leather")}, I("hang_glider"), category="equipment")
    shapeless("ender_wings", [M("elytra"), I("endite_ingot"), M("ender_eye"), M("ender_eye")], I("ender_wings"), category="equipment")
    shaped("waystone", [" A ", "SES", "SSS"], {"A": M("amethyst_shard"), "S": M("stone_bricks"), "E": M("ender_pearl")}, I("waystone"))
    for wood in ("oak", "spruce"):
        shaped(f"{wood}_chair", ["P  ", "PPP", "S S"], {"P": M(f"{wood}_planks"), "S": M("stick")}, I(f"{wood}_chair"), 2, category="building")
        shaped(f"{wood}_table", ["PPP", "S S", "S S"], {"P": M(f"{wood}_planks"), "S": M("stick")}, I(f"{wood}_table"), category="building")
    shaped("oak_stool", ["W", "P", "S"], {"W": "#minecraft:wool", "P": M("oak_planks"), "S": M("stick")}, I("oak_stool"), 2, category="building")
    shaped("display_pedestal", ["SGS", " A ", "AAA"], {"S": M("smooth_stone_slab"), "G": M("gold_ingot"), "A": M("polished_andesite")}, I("display_pedestal"),
           category="building")
    print("extras generated")


if __name__ == "__main__":
    textures()
    models()
    data()
