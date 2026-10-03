#!/usr/bin/env python3
"""
Generates the Cooking & Farming content's assets and data: textures (vanilla sprites recoloured, plus a few drawn
here pixel by pixel), item and block models, block states, names, recipes, loot tables and tags.

    VANILLA=/path/to/minecraft-assets-26.3 python3 tools/gen_food.py

The item list here must match dev.abps.content.Food (which holds the food values and effects).
"""
import colorsys
import json
import os

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
VANILLA = os.environ.get("VANILLA", "mc263")
VT = os.path.join(VANILLA, "assets", "minecraft", "textures")
ASSETS = os.path.join(ROOT, "src", "client", "resources", "assets", "abpsmod")
DATA = os.path.join(ROOT, "src", "main", "resources", "data")
NS = "abpsmod"
I = lambda n: f"{NS}:{n}"
M = lambda n: f"minecraft:{n}"


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


# ------------------------------------------------------------------ colour helpers

def hexrgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def ramp(base, n=7):
    """Seven shades of a colour, dark to light, with a little hue shift like hand-picked pixel art palettes."""
    r, g, b = [c / 255 for c in hexrgb(base)]
    h, s, v = colorsys.rgb_to_hsv(r, g, b)
    out = []
    for i in range(n):
        t = i / (n - 1)
        vv = min(1, v * (0.48 + 0.82 * t))
        ss = max(0, min(1, s * (1.15 - 0.45 * t)))
        hh = (h + (0.015 if t > 0.6 else -0.01) * (t - 0.5)) % 1
        out.append(tuple(int(round(c * 255)) for c in colorsys.hsv_to_rgb(hh, ss, vv)))
    return out


def lum(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def hsv(c):
    return colorsys.rgb_to_hsv(c[0] / 255, c[1] / 255, c[2] / 255)


def band(lo, hi, smin=0.25, vmin=0.12):
    """Selects pixels whose hue falls between lo and hi (wrapping past 1)."""
    def sel(c):
        if c[3] < 10:
            return False
        h, s, v = hsv(c)
        inside = lo <= h <= hi if lo <= hi else (h >= lo or h <= hi)
        return inside and s >= smin and v >= vmin
    return sel


RED = band(0.93, 0.045)
ORANGE = band(0.03, 0.115)
YELLOW = band(0.10, 0.19)
GREEN = band(0.17, 0.45, 0.2)
WARM = band(0.0, 0.17, 0.2)  # browns, oranges, tans
ANY = lambda c: c[3] > 10


def recolor(im, select, color, gamma=0.8):
    px = im.load()
    pts = [(x, y) for y in range(im.size[1]) for x in range(im.size[0]) if select(px[x, y])]
    if not pts:
        return im
    ls = [lum(px[x, y]) for x, y in pts]
    lo, hi = min(ls), max(ls)
    R = ramp(color)
    for x, y in pts:
        t = ((lum(px[x, y]) - lo) / max(1, hi - lo)) ** gamma
        px[x, y] = R[min(6, int(round(t * 6)))] + (px[x, y][3],)
    return im


def load(rel):
    return Image.open(os.path.join(VT, rel + ".png")).convert("RGBA")


def save(im, rel):
    path = os.path.join(ASSETS, "textures", rel + ".png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path)


def tint(im, color):
    """Multiplies a grey texture (like leaves) by a colour."""
    px = im.load()
    r0, g0, b0 = hexrgb(color)
    for y in range(im.size[1]):
        for x in range(im.size[0]):
            r, g, b, a = px[x, y]
            px[x, y] = (r * r0 // 255, g * g0 // 255, b * b0 // 255, a)
    return im


def dots(im, color, spots):
    """Little fruits: a light pixel over a dark one at each spot."""
    R = ramp(color)
    px = im.load()
    for x, y in spots:
        if 0 <= x < 16 and 0 <= y < 15:
            px[x, y] = R[5] + (255,)
            px[x, y + 1] = R[3] + (255,)
            if x + 1 < 16:
                px[x + 1, y] = R[4] + (255,)
                px[x + 1, y + 1] = R[2] + (255,)
    return im


def sprite(rows, pal):
    """A sprite drawn from 16 strings of palette letters ('.' is clear)."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                px[x, y] = hexrgb(pal[ch]) + (255,)
    return im


# ------------------------------------------------------------------ the content

# Crops: id -> (name, produce name, seed name, stage source, produce colour, produce sprite base, select, seed base)
CROPS = {
    "tomato": ("Tomato Plant", "Tomato", "Tomato Seeds", "potatoes", "#F2482C", ("item/apple", RED), "item/melon_seeds", "#E8C77D"),
    "corn": ("Corn Stalk", "Corn", "Corn Kernels", "carrots", "#F2C94C", ("item/carrot", ORANGE), "item/pumpkin_seeds", "#F2C94C"),
    "onion": ("Onion Plant", "Onion", "Onion Sets", "beetroots", "#9B4A8C", ("item/beetroot", RED), "item/beetroot_seeds", "#9B6A5A"),
    "cabbage": ("Cabbage Plant", "Cabbage", "Cabbage Seeds", "carrots", "#9CCF62", ("item/beetroot", RED), "item/wheat_seeds", "#5E8C3A"),
    "chili_pepper": ("Chili Plant", "Chili Pepper", "Chili Seeds", "potatoes", "#D42A1E", ("item/carrot", ORANGE), "item/melon_seeds", "#E0632A"),
    "strawberry": ("Strawberry Plant", "Strawberry", "Strawberry Seeds", "beetroots", "#E3263B", ("item/sweet_berries", RED), "item/pumpkin_seeds", "#F59AAE"),
}

FRUITS = {  # fruit trees: id -> (fruit name, colour)
    "orange": ("Orange", "#FF8C1A"),
    "lemon": ("Lemon", "#F2D632"),
    "peach": ("Peach", "#FF9E7A"),
    "plum": ("Plum", "#7E2F91"),
}

# Plain items: id -> (name, base texture, [(select, colour)], gamma)
ITEMS = {
    # Ingredients
    "flour": ("Flour", "item/sugar", [(ANY, "#EFE4C6")]),
    "raw_patty": ("Raw Patty", "item/cookie", [(ANY, "#D86A6E")]),
    "cooked_patty": ("Cooked Patty", "item/cookie", [(ANY, "#7B4A2C")]),
    "fish_fillet": ("Fish Fillet", "item/cod", [(ANY, "#F2906E")]),
    "cooked_fillet": ("Cooked Fillet", "item/cooked_cod", [(ANY, "#E3A36E")]),
    "milk_bottle": ("Bottle of Milk", "item/honey_bottle", [(WARM, "#F7F4EA")]),
    "goat_milk": ("Bottle of Goat Milk", "item/honey_bottle", [(WARM, "#EFE3C6")]),
    "cheese": ("Cheese", "item/honeycomb", [(ANY, "#F5D56A")]),
    "butter": ("Butter", "item/gold_ingot", [(ANY, "#F7E7A6")]),
    "rich_compost": ("Rich Compost", "item/bone_meal", [(ANY, "#5C3D22")]),
    # Pot meals
    "tomato_soup": ("Tomato Soup", "item/beetroot_soup", [(RED, "#E2532C")]),
    "vegetable_stew": ("Vegetable Stew", "item/beetroot_soup", [(RED, "#86A848")]),
    "corn_chowder": ("Corn Chowder", "item/beetroot_soup", [(RED, "#F2D46E")]),
    "chili_con_carne": ("Chili con Carne", "item/beetroot_soup", [(RED, "#8E2E17")]),
    "fish_stew": ("Fish Stew", "item/beetroot_soup", [(RED, "#E99A5A")]),
    "stuffed_cabbage": ("Stuffed Cabbage", "item/beetroot_soup", [(RED, "#A3CF6E")]),
    # Oven
    "pizza": ("Pizza", "item/pumpkin_pie", [(ORANGE, "#D9432B")]),
    "strawberry_pie": ("Strawberry Pie", "item/pumpkin_pie", [(ORANGE, "#E8405F")]),
    "corn_bread": ("Corn Bread", "item/bread", [(ANY, "#E9BC4C")]),
    "grilled_cheese": ("Grilled Cheese", "item/bread", [(ANY, "#D8963A")]),
    # Street food made in a furnace
    "corn_on_the_cob": ("Corn on the Cob", "item/carrot", [(ORANGE, "#E3A83A")]),
    # Drinks
    "orange_juice": ("Orange Juice", "item/honey_bottle", [(WARM, "#FF9A1F")]),
    "lemonade": ("Lemonade", "item/honey_bottle", [(WARM, "#F7E35A")]),
    "peach_tea": ("Peach Tea", "item/honey_bottle", [(WARM, "#E8A06A")]),
    "strawberry_smoothie": ("Strawberry Smoothie", "item/honey_bottle", [(WARM, "#F2758F")]),
    "hot_cocoa": ("Hot Cocoa", "item/honey_bottle", [(WARM, "#6E3F25")]),
    "plum_juice": ("Plum Juice", "item/honey_bottle", [(WARM, "#7A2E6E")]),
    "apple_cider": ("Apple Cider", "item/honey_bottle", [(WARM, "#D98C2B")]),
    "golden_cider": ("Golden Cider", "item/honey_bottle", [(WARM, "#F7C928")]),
    "plum_cordial": ("Plum Cordial", "item/honey_bottle", [(WARM, "#55174E")]),
}

PAL = {"o": "#2B1A10", "B": "#C8843E", "b": "#9A5A26", "h": "#E7B067", "M": "#6A3A1E", "m": "#4A2614", "L": "#5FAF3A", "l": "#3E8A26",
       "T": "#D9402A", "C": "#F5C842", "c": "#D99A1E", "W": "#FFF6E0", "S": "#E8C77D", "R": "#B0301E", "G": "#8A8A8A", "Y": "#F7DD4A",
       "P": "#F2E6C8", "p": "#D9C7A0", "K": "#8C5A2B", "O": "#E8862A"}

DRAWN = {
    "burger": ("Burger", [
        "................", "................", ".....hhhhhh.....", "...hhBBBBBBhh...", "..hBBWBBBWBBBh..", "..BBBBBBBWBBBb..",
        ".oBBBBBBBBBBBbo.", ".LLlLLlLLlLLlLL.", ".YYCYYYCYYYCYYY.", ".TTRTTTRTTTTRTT.", ".MMmMMMmMMMMmMM.", "..mMMMMMMMMMMm..",
        ".oBBBBBBBBBBBBo.", "..bBBBBBBBBBBb..", "...bbbbbbbbbb...", "................"], PAL),
    "taco": ("Taco", [
        "................", "................", "................", "....LlLTLLlT....", "...LTMLMlTMLL...", "..lMMLmMTMLmTL..",
        "..CMMmMMMmMMMC..", ".CcMMMMmMMMMMcC.", ".CCcmMMMMMMMcCC.", "CCCCcMMMMMMcCCCo", "CcCCCcmMMMcCCcCo", ".CCcCCCcccCCcCo.",
        "..oCCcCCCCcCCo..", "....ooCCCCoo....", "................", "................"], PAL),
    "fries": ("Fries", [
        "................", ".....Y..Y.......", "....YY.YCY.Y....", "...YCY.YCYYCY...", "...YCYYYCYYCY...", "...YCYCYCYCCY...",
        "..RRRRRRRRRRRR..", "..RWWRRRRRRWWR..", "..RRWWRRRRWWRR..", "..RRRRWWWWRRRR..", "..RRRRRWWRRRRR..", "...RRRRRRRRRR...",
        "...RRRRRRRRRR...", "....RRRRRRRR....", "................", "................"], PAL),
    "kebab": ("Kebab", [
        "..............G.", ".............GG.", "...........MMG..", "..........MmMM..", ".........LlLM...", "........TTTL....",
        ".......MMMT.....", "......MmMM......", ".....OOOM.......", "....MMMO........", "...MmMM.........", "..KKMM..........",
        ".KK.............", "KK..............", "K...............", "................"], PAL),
    "hot_dog": ("Hot Dog", [
        "................", "................", "................", "................", "..........hhhh..", "........hhBBBBo.",
        "......hhBBBBBTo.", "....hhBBBBRTTYo.", "...hBBBRRTTYTBo.", "..hBBRRTTYTTBbo.", ".hBRTTYTTRRBbo..", ".BRRTTRRRBBbo...",
        ".BbBBBBBbbbo....", "..obbbbboo......", "................", "................"], PAL),
    "popcorn": ("Popcorn", [
        "................", "....P.PP.P......", "...PPpPPPPpP....", "..PpPPWPPpPPP...", "..PPWPPpPWPPp...", "..RRRRRRRRRRRR..",
        "..RWWRRWWRRWWR..", "..RWWRRWWRRWWR..", "..RWWRRWWRRWWR..", "...RWWRWWRRWW...", "...RWWRWWRRWW...", "...RWWRWWRRWW...",
        "....RWWRWWRW....", "....RRRRRRRR....", "................", "................"], PAL),
    "chopped_vegetables": ("Chopped Vegetables", [
        "................", "................", "................", "................", "...OO..LL.......", "..OOO.LlL..TT...",
        "..OO..Ll..TTR...", ".....LL..OO.....", "..TT....OOO.LL..", ".TTR.Yy..OO.LlL.", ".TR..YY.....LL..", "......LL.OO.....",
        ".OO..LlL.OOO....", "OOO...L...O.....", "................", "................"], {**PAL, "y": "#D9B22E"}),
}


def textures():
    # Crops: four growth stages from vanilla's crops, the last one bearing our produce, plus produce and seeds
    for cid, (_, _, _, src, color, (pbase, psel), sbase, scolor) in CROPS.items():
        for k in range(4):
            im = load(f"block/{src}_stage{k}")
            if k == 3:
                recolor(im, lambda c: c[3] > 10 and not GREEN(c), color)
            save(im, f"block/{cid}_stage{k}")
        save(recolor(load(pbase), psel, color), f"item/{cid}")
        save(recolor(load(sbase), ANY, scolor), f"item/{cid}_seeds")
    # Strawberries get their seeds
    im = Image.open(os.path.join(ASSETS, "textures", "item", "strawberry.png")).convert("RGBA")
    px = im.load()
    for y in range(16):
        for x in range(16):
            if (x * 3 + y * 5) % 7 == 0 and RED(px[x, y]) and lum(px[x, y]) > 70:
                px[x, y] = (255, 230, 110, 255)
    im.save(os.path.join(ASSETS, "textures", "item", "strawberry.png"))
    # Fruit trees: leaves (plain and fruiting), saplings and the fruit
    spots = [(2, 2), (9, 3), (5, 7), (12, 8), (1, 11), (8, 12), (13, 13)]
    for fid, (_, color) in FRUITS.items():
        leaves = tint(load("block/oak_leaves"), "#5AA832")
        save(leaves, f"block/{fid}_leaves")
        save(dots(leaves.copy(), color, spots), f"block/{fid}_leaves_ripe")
        sap = recolor(load("block/oak_sapling"), GREEN, "#62A83A")
        save(dots(sap, color, [(7, 3)]), f"block/{fid}_sapling")
        save(recolor(load("item/apple"), RED, color), f"item/{fid}")
    # Plain items
    for iid, (_, base, ops) in ITEMS.items():
        im = load(base)
        for sel, col in ops:
            recolor(im, sel, col)
        save(im, f"item/{iid}")
    for iid, (_, rows, pal) in DRAWN.items():
        save(sprite(rows, pal), f"item/{iid}")
    # Kitchen and farm blocks
    for part in ("top", "side", "bottom", "inner"):
        im = load(f"block/cauldron_{part}")
        save(recolor(im, ANY, "#B8693A" if part != "inner" else "#6E3A22"), f"block/cooking_pot_{part}")
    save(recolor(load("block/smoker_side"), ANY, "#9C5A40"), "block/stone_oven_side")
    save(recolor(load("block/smoker_top"), ANY, "#8A5440"), "block/stone_oven_top")
    oven = load("block/furnace_front_on")
    recolor(oven, lambda c: c[3] > 10 and hsv(c)[1] < 0.25, "#9C5A40")
    save(oven, "block/stone_oven_front")
    for part in ("side", "top", "bottom"):
        save(recolor(load(f"block/barrel_{part}"), WARM, "#4E3220"), f"block/aging_barrel_{part}")
    glass = load("block/glass")
    px = glass.load()
    for y in range(16):
        for x in range(16):
            r, g, b, a = px[x, y]
            px[x, y] = (150, 220, 140, 60) if a < 10 else (min(255, r * 180 // 255 + 40), min(255, g * 230 // 255 + 20), min(255, b * 160 // 255), a)
    save(glass, "block/greenhouse_glass")


# ------------------------------------------------------------------ models, states, names

BLOCKS = {  # block id -> name
    "cooking_pot": "Cooking Pot", "stone_oven": "Stone Oven", "cutting_board": "Cutting Board", "aging_barrel": "Aging Barrel",
    "sprinkler": "Sprinkler", "quality_sprinkler": "Quality Sprinkler", "greenhouse_glass": "Greenhouse Glass", "scarecrow": "Scarecrow",
}


def item_model(iid, texture=None):
    write(f"{ASSETS}/models/item/{iid}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": texture or f"{NS}:item/{iid}"}})
    write(f"{ASSETS}/items/{iid}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/{iid}"}})


def block_item(bid):
    write(f"{ASSETS}/items/{bid}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/{bid}"}})


def single_state(bid):
    write(f"{ASSETS}/blockstates/{bid}.json", {"variants": {"": {"model": f"{NS}:block/{bid}"}}})


def box(frm, to, tex, faces=("down", "up", "north", "south", "west", "east")):
    def uv(face):
        x0, y0, z0 = frm
        x1, y1, z1 = to
        if face in ("up", "down"):
            return [x0, z0, x1, z1]
        if face in ("north", "south"):
            return [x0, 16 - y1, x1, 16 - y0]
        return [z0, 16 - y1, z1, 16 - y0]
    return {"from": list(frm), "to": list(to), "faces": {f: {"uv": uv(f), "texture": tex if isinstance(tex, str) else tex[f]} for f in faces}}


def models():
    lang = {}
    for cid, (plant, produce, seed, *_rest) in CROPS.items():
        for k in range(4):
            write(f"{ASSETS}/models/block/{cid}_stage{k}.json", {"parent": "minecraft:block/crop", "textures": {"crop": f"{NS}:block/{cid}_stage{k}"}})
        stage = lambda age: 0 if age <= 1 else 1 if age <= 3 else 2 if age <= 6 else 3
        write(f"{ASSETS}/blockstates/{cid}_crop.json", {"variants": {f"age={a}": {"model": f"{NS}:block/{cid}_stage{stage(a)}"} for a in range(8)}})
        item_model(cid)
        item_model(f"{cid}_seeds")
        lang[f"block.{NS}.{cid}_crop"] = plant
        lang[f"item.{NS}.{cid}"] = produce
        lang[f"item.{NS}.{cid}_seeds"] = seed
    for fid, (fruit, _) in FRUITS.items():
        for ripe in (False, True):
            suffix = "_ripe" if ripe else ""
            write(f"{ASSETS}/models/block/{fid}_leaves{suffix}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/{fid}_leaves{suffix}"}})
        write(f"{ASSETS}/blockstates/{fid}_leaves.json", {"variants": {"ripe=false": {"model": f"{NS}:block/{fid}_leaves"},
                                                                       "ripe=true": {"model": f"{NS}:block/{fid}_leaves_ripe"}}})
        block_item(f"{fid}_leaves")
        write(f"{ASSETS}/models/block/{fid}_sapling.json", {"parent": "minecraft:block/cross", "textures": {"cross": f"{NS}:block/{fid}_sapling"}})
        single_state(f"{fid}_sapling")
        item_model(f"{fid}_sapling", f"{NS}:block/{fid}_sapling")
        item_model(fid)
        lang[f"block.{NS}.{fid}_leaves"] = f"{fruit} Leaves"
        lang[f"block.{NS}.{fid}_sapling"] = f"{fruit} Sapling"
        lang[f"item.{NS}.{fid}"] = fruit
    for iid, v in list(ITEMS.items()) + list(DRAWN.items()):
        item_model(iid)
        lang[f"item.{NS}.{iid}"] = v[0]

    # Cooking pot: vanilla's cauldron shape in copper
    write(f"{ASSETS}/models/block/cooking_pot.json", {"parent": "minecraft:block/cauldron", "textures": {
        "particle": f"{NS}:block/cooking_pot_side", "top": f"{NS}:block/cooking_pot_top", "bottom": f"{NS}:block/cooking_pot_bottom",
        "side": f"{NS}:block/cooking_pot_side", "inside": f"{NS}:block/cooking_pot_inner"}})
    write(f"{ASSETS}/models/block/stone_oven.json", {"parent": "minecraft:block/cube", "textures": {
        "particle": f"{NS}:block/stone_oven_side", "down": f"{NS}:block/stone_oven_top", "up": f"{NS}:block/stone_oven_top",
        "north": f"{NS}:block/stone_oven_front", "south": f"{NS}:block/stone_oven_front", "west": f"{NS}:block/stone_oven_side",
        "east": f"{NS}:block/stone_oven_side"}})
    write(f"{ASSETS}/models/block/aging_barrel.json", {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "top": f"{NS}:block/aging_barrel_top", "bottom": f"{NS}:block/aging_barrel_bottom", "side": f"{NS}:block/aging_barrel_side"}})
    write(f"{ASSETS}/models/block/greenhouse_glass.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/greenhouse_glass"}})
    # Cutting board: a thin plank with a knife resting on it
    board = "minecraft:block/stripped_oak_log"
    write(f"{ASSETS}/models/block/cutting_board.json", {"parent": "minecraft:block/block", "textures": {
        "particle": board, "board": board, "edge": "minecraft:block/spruce_planks", "steel": "minecraft:block/iron_block", "handle": "minecraft:block/dark_oak_planks"},
        "elements": [box((2, 0, 3), (14, 1, 13), "#board"), box((1, 0, 3), (2, 1.5, 13), "#edge"), box((14, 0, 3), (15, 1.5, 13), "#edge"),
                     box((4, 1, 6), (11, 1.5, 7.5), "#steel"), box((11, 1, 6.2), (14, 2, 7.3), "#handle")]})
    # Sprinklers: a copper base, a pipe and a spinning head
    for sid, head in (("sprinkler", "minecraft:block/copper_block"), ("quality_sprinkler", "minecraft:block/gold_block")):
        write(f"{ASSETS}/models/block/{sid}.json", {"parent": "minecraft:block/block", "textures": {
            "particle": "minecraft:block/copper_block", "base": "minecraft:block/cut_copper", "pipe": "minecraft:block/oxidized_copper", "head": head},
            "elements": [box((3, 0, 3), (13, 3, 13), "#base"), box((7, 3, 7), (9, 9, 9), "#pipe"), box((4, 9, 7), (12, 10, 9), "#head"),
                         box((7, 9, 4), (9, 10, 12), "#head"), box((6.5, 10, 6.5), (9.5, 11, 9.5), "#head")]})
    # Scarecrow: a post, a hay body with arms and a pumpkin head
    write(f"{ASSETS}/models/block/scarecrow.json", {"parent": "minecraft:block/block", "textures": {
        "particle": "minecraft:block/hay_block_side", "post": "minecraft:block/oak_log", "hay": "minecraft:block/hay_block_side",
        "hay_top": "minecraft:block/hay_block_top", "face": "minecraft:block/carved_pumpkin", "pumpkin": "minecraft:block/pumpkin_side",
        "pumpkin_top": "minecraft:block/pumpkin_top"},
        "elements": [box((7, 0, 7), (9, 6, 9), "#post"),
                     box((5, 6, 6), (11, 11, 10), {"down": "#hay_top", "up": "#hay_top", "north": "#hay", "south": "#hay", "west": "#hay", "east": "#hay"}),
                     box((1, 9, 7), (15, 10, 9), "#post"),
                     box((5, 11, 5), (11, 16, 11), {"down": "#pumpkin_top", "up": "#pumpkin_top", "north": "#face", "south": "#pumpkin", "west": "#pumpkin", "east": "#pumpkin"})]})
    for bid, name in BLOCKS.items():
        single_state(bid)
        block_item(bid)
        lang[f"block.{NS}.{bid}"] = name

    path = f"{ASSETS}/lang/en_us.json"
    full = json.load(open(path))
    full.update(lang)
    write(path, full)


# ------------------------------------------------------------------ data

def recipe(name, obj):
    write(f"{DATA}/{NS}/recipe/{name}.json", obj)


def shapeless(name, ingredients, result, count=1, category="misc"):
    r = {"type": "minecraft:crafting_shapeless", "category": category, "ingredients": ingredients, "result": {"id": result}}
    if count > 1:
        r["result"]["count"] = count
    recipe(name, r)


def shaped(name, pattern, key, result, count=1, category="misc"):
    r = {"type": "minecraft:crafting_shaped", "category": category, "key": key, "pattern": pattern, "result": {"id": result}}
    if count > 1:
        r["result"]["count"] = count
    recipe(name, r)


def cook(name, ingredient, result, xp=0.35, kinds=("smelting", "smoking", "campfire_cooking")):
    times = {"smelting": 200, "smoking": 100, "campfire_cooking": 600}
    for k in kinds:
        recipe(f"{name}_from_{k}", {"type": f"minecraft:{k}", "category": "food", "cookingtime": times[k], "experience": xp,
                                    "ingredient": ingredient, "result": {"id": result}})


def loot(bid, table):
    write(f"{DATA}/{NS}/loot_table/blocks/{bid}.json", table)


def self_drop(bid):
    loot(bid, {"type": "minecraft:block", "pools": [{"condition": {"type": "minecraft:survives_explosion"},
                                                    "entries": [{"type": "minecraft:item", "name": I(bid)}], "rolls": 1}],
               "random_sequence": f"{NS}:blocks/{bid}"})


def tag(kind, ns, name, values):
    """Adds to a tag file, keeping what other generators already put there."""
    path = f"{DATA}/{ns}/tags/{kind}/{name}.json"
    old = json.load(open(path))["values"] if os.path.exists(path) else []
    write(path, {"replace": False, "values": old + [v for v in values if v not in old]})


def data():
    # Crop loot: produce (and some seeds back) when grown, otherwise the seeds
    for cid in CROPS:
        grown = {"type": "minecraft:match_block", "blocks": I(f"{cid}_crop"), "state": {"age": "7"}}
        count = lambda lo, hi: {"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "max": hi, "min": lo}}
        loot(f"{cid}_crop", {"type": "minecraft:block", "modifier": {"type": "minecraft:explosion_decay"}, "pools": [
            {"rolls": 1, "entries": [{"type": "minecraft:alternatives", "children": [
                {"type": "minecraft:item", "condition": grown, "name": I(cid), "modifier": count(1, 3)},
                {"type": "minecraft:item", "name": I(f"{cid}_seeds")}]}]},
            {"rolls": 1, "condition": grown, "entries": [{"type": "minecraft:item", "name": I(f"{cid}_seeds"), "modifier": count(0, 2)}]}],
            "random_sequence": f"{NS}:blocks/{cid}_crop"})
    for fid in FRUITS:
        self_drop(f"{fid}_sapling")
        loot(f"{fid}_leaves", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "condition": {"type": "minecraft:any_of", "terms": ["minecraft:tool/can_shear", "minecraft:tool/can_silk_touch"]},
             "name": I(f"{fid}_leaves")},
            {"type": "minecraft:item", "condition": {"type": "minecraft:random_chance", "chance": 0.06}, "name": I(f"{fid}_sapling")},
            {"type": "minecraft:item", "condition": {"type": "minecraft:random_chance", "chance": 0.05}, "name": M("stick")}]}]}],
            "random_sequence": f"{NS}:blocks/{fid}_leaves"})
    for bid in BLOCKS:
        self_drop(bid)

    # Tags
    crops = [I(f"{c}_crop") for c in CROPS]
    tag("block", "minecraft", "crops", crops)
    tag("block", "minecraft", "maintains_farmland", crops)
    tag("block", "minecraft", "bee_growables", crops)
    tag("block", "minecraft", "saplings", [I(f"{f}_sapling") for f in FRUITS])
    tag("item", "minecraft", "saplings", [I(f"{f}_sapling") for f in FRUITS])
    tag("block", "minecraft", "mineable/hoe", [I(f"{f}_leaves") for f in FRUITS])
    tag("block", "minecraft", "mineable/axe", [I("cutting_board"), I("aging_barrel"), I("scarecrow")])
    tag("block", "minecraft", "mineable/pickaxe", [I("cooking_pot"), I("stone_oven"), I("sprinkler"), I("quality_sprinkler")])
    tag("item", "minecraft", "villager_plantable_seeds", [I(f"{c}_seeds") for c in CROPS])

    # Kitchen blocks
    shaped("cooking_pot", ["C C", "CBC", "CCC"], {"C": M("copper_ingot"), "B": M("bowl")}, I("cooking_pot"))
    shaped("stone_oven", ["BBB", "BFB", "BBB"], {"B": M("bricks"), "F": M("furnace")}, I("stone_oven"))
    shaped("cutting_board", ["  I", "PPP"], {"I": M("iron_ingot"), "P": "#minecraft:planks"}, I("cutting_board"))
    shapeless("aging_barrel", [M("barrel"), M("iron_ingot"), M("iron_ingot"), M("honeycomb")], I("aging_barrel"))
    shaped("sprinkler", [" C ", "CWC", " C "], {"C": M("copper_ingot"), "W": M("water_bucket")}, I("sprinkler"))
    shapeless("quality_sprinkler", [I("sprinkler"), I("ruby"), I("ruby"), M("gold_ingot"), M("gold_ingot")], I("quality_sprinkler"))
    shaped("greenhouse_glass", ["GGG", "GDG", "GGG"], {"G": M("glass"), "D": M("green_dye")}, I("greenhouse_glass"), 8)
    shaped("scarecrow", ["P", "H", "S"], {"P": M("carved_pumpkin"), "H": M("hay_block"), "S": M("stick")}, I("scarecrow"))
    shapeless("rich_compost", [M("bone_meal"), M("bone_meal"), M("rotten_flesh"), M("dirt")], I("rich_compost"), 2)
    shapeless("flour", [M("wheat"), M("wheat"), M("wheat")], I("flour"), 2)
    shapeless("milk_bottles", [M("milk_bucket"), M("glass_bottle"), M("glass_bottle"), M("glass_bottle")], I("milk_bottle"), 3)

    # Cooking
    cook("cooked_patty", I("raw_patty"), I("cooked_patty"))
    cook("cooked_fillet", I("fish_fillet"), I("cooked_fillet"))
    cook("corn_on_the_cob", I("corn"), I("corn_on_the_cob"), kinds=("smelting", "campfire_cooking"))
    cook("popcorn", I("corn"), I("popcorn"), kinds=("smoking",))

    # Street food
    shapeless("burger", [M("bread"), I("cooked_patty"), I("cabbage"), I("tomato")], I("burger"), category="misc")
    shapeless("taco", [I("flour"), I("cooked_patty"), I("cabbage"), I("chili_pepper")], I("taco"), 2)
    shapeless("hot_dog", [M("bread"), M("cooked_porkchop"), I("onion")], I("hot_dog"), 2)
    shapeless("fries", [M("baked_potato"), M("baked_potato"), M("paper")], I("fries"))
    shapeless("kebab", [M("stick"), I("cooked_patty"), I("onion"), I("chili_pepper")], I("kebab"), 2)

    # Drinks
    bottle = M("glass_bottle")
    shapeless("orange_juice", [I("orange"), I("orange"), M("sugar"), bottle], I("orange_juice"))
    shapeless("lemonade", [I("lemon"), I("lemon"), M("sugar"), bottle], I("lemonade"))
    shapeless("peach_tea", [I("peach"), I("peach"), M("sugar"), bottle], I("peach_tea"))
    shapeless("plum_juice", [I("plum"), I("plum"), M("sugar"), bottle], I("plum_juice"))
    shapeless("apple_cider", [M("apple"), M("apple"), M("sugar"), bottle], I("apple_cider"))
    shapeless("strawberry_smoothie", [I("strawberry"), I("strawberry"), M("sugar"), I("milk_bottle")], I("strawberry_smoothie"))
    shapeless("hot_cocoa", [M("cocoa_beans"), M("cocoa_beans"), M("sugar"), I("milk_bottle")], I("hot_cocoa"))
    print("food content generated")


if __name__ == "__main__":
    textures()
    models()
    data()
