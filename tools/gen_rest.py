#!/usr/bin/env python3
"""
Generates the last pieces: ruby rails, three new farm animal looks (ducks, highland cows, wild boars), placeable
feasts and the Crystal Hollows End biome.

    VANILLA=/path/to/minecraft-assets-26.3 python3 tools/gen_rest.py
"""
import colorsys
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_food import (ASSETS, DATA, NS, VANILLA, I, M, ANY, hsv, load, recolor, save, shaped, tag, write, box, self_drop)


def textures():
    # Ruby rails: vanilla rails with ruby instead of iron, wooden sleepers kept
    for part in ("rail", "rail_corner"):
        im = load(f"block/{part}")
        recolor(im, lambda c: c[3] > 10 and hsv(c)[1] < 0.18, "#D8304A")
        save(im, f"block/ruby_{part}")

    lowsat = lambda c: c[3] > 10 and hsv(c)[1] < 0.22
    # Duck: a chicken with a green head and a brown body (beak, feet and wattle keep their colours)
    im = load("entity/chicken/chicken_temperate")
    px = im.load()
    for y in range(im.size[1]):
        for x in range(im.size[0]):
            c = px[x, y]
            if not lowsat(c):
                continue
            head = x < 14 and y < 9
            base = (0x2E, 0x7A, 0x42) if head else (0x8A, 0x6A, 0x4A)
            v = hsv(c)[2]
            px[x, y] = tuple(int(min(255, b * (0.55 + 0.6 * v))) for b in base) + (c[3],)
    save(im, "entity/chicken/duck")
    save(recolor(load("entity/chicken/chicken_temperate_baby"), lowsat, "#E2C46A"), "entity/chicken/duck_baby")
    # Highland cow: the shaggy cold cow in ginger
    for suffix in ("", "_baby"):
        im = load(f"entity/cow/cow_cold{suffix}")
        recolor(im, lambda c: c[3] > 10 and 0.0 <= hsv(c)[0] <= 0.14 and hsv(c)[1] > 0.15, "#C8662A")
        save(im, f"entity/cow/highland{suffix}")
        im = load(f"entity/pig/pig_temperate{suffix}")
        recolor(im, lambda c: c[3] > 10 and (hsv(c)[0] > 0.85 or hsv(c)[0] < 0.08) and hsv(c)[1] > 0.12, "#5E3E2A")
        save(im, f"entity/pig/wild_boar{suffix}")


FEASTS = {"roast_feast": ("Roast Feast", "minecraft:block/stripped_mangrove_log", "minecraft:block/honeycomb_block", "minecraft:block/moss_block"),
          "seafood_feast": ("Seafood Feast", "minecraft:block/pink_terracotta", "minecraft:block/yellow_terracotta", "minecraft:block/moss_block")}


def models():
    lang = {}
    # Ruby rail: vanilla's rail block state with our textures
    write(f"{ASSETS}/models/block/ruby_rail.json", {"parent": "minecraft:block/rail_flat", "textures": {"rail": f"{NS}:block/ruby_rail"}})
    write(f"{ASSETS}/models/block/ruby_rail_corner.json", {"parent": "minecraft:block/rail_curved", "textures": {"rail": f"{NS}:block/ruby_rail_corner"}})
    write(f"{ASSETS}/models/block/ruby_rail_raised_ne.json", {"parent": "minecraft:block/template_rail_raised_ne", "textures": {"rail": f"{NS}:block/ruby_rail"}})
    write(f"{ASSETS}/models/block/ruby_rail_raised_sw.json", {"parent": "minecraft:block/template_rail_raised_sw", "textures": {"rail": f"{NS}:block/ruby_rail"}})
    with open(os.path.join(VANILLA, "assets/minecraft/blockstates/rail.json")) as f:
        state = json.load(f)
    for v in state["variants"].values():
        v["model"] = v["model"].replace("minecraft:block/rail", f"{NS}:block/ruby_rail")
    write(f"{ASSETS}/blockstates/ruby_rail.json", state)
    write(f"{ASSETS}/models/item/ruby_rail.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:block/ruby_rail"}})
    write(f"{ASSETS}/items/ruby_rail.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/ruby_rail"}})
    lang[f"block.{NS}.ruby_rail"] = "Ruby Rail"

    # Feasts: a big platter that shrinks as people take servings
    for fid, (name, meat, side, garnish) in FEASTS.items():
        tex = {"particle": "minecraft:block/quartz_block_top", "plate": "minecraft:block/quartz_block_top", "meat": meat, "side": side, "garnish": garnish}
        plate = [box((1, 0, 1), (15, 1, 15), "#plate")]
        full = plate + [box((4, 1, 4), (12, 6, 11), "#meat"), box((2, 1, 11), (5, 3, 14), "#side"), box((11, 1, 11), (14, 3, 14), "#side"),
                        box((2, 1, 2), (4, 2, 4), "#garnish"), box((12, 1, 2), (14, 2, 4), "#garnish"), box((6, 6, 6), (10, 7, 9), "#garnish")]
        half = plate + [box((4, 1, 4), (9, 5, 11), "#meat"), box((2, 1, 11), (5, 3, 14), "#side"), box((2, 1, 2), (4, 2, 4), "#garnish")]
        last = plate + [box((5, 1, 5), (8, 3, 9), "#meat")]
        for suffix, els in (("", full), ("_half", half), ("_last", last)):
            write(f"{ASSETS}/models/block/{fid}{suffix}.json", {"parent": "minecraft:block/block", "textures": tex, "elements": els})
        write(f"{ASSETS}/blockstates/{fid}.json", {"variants": {
            "servings=4": {"model": f"{NS}:block/{fid}"}, "servings=3": {"model": f"{NS}:block/{fid}"},
            "servings=2": {"model": f"{NS}:block/{fid}_half"}, "servings=1": {"model": f"{NS}:block/{fid}_last"}}})
        write(f"{ASSETS}/items/{fid}.json", {"model": {"type": "minecraft:model", "model": f"{NS}:block/{fid}"}})
        lang[f"block.{NS}.{fid}"] = name
    lang[f"biome.{NS}.crystal_hollows"] = "Crystal Hollows"
    path = f"{ASSETS}/lang/en_us.json"
    full_lang = json.load(open(path))
    full_lang.update(lang)
    write(path, full_lang)


def data():
    self_drop("ruby_rail")
    tag("block", "minecraft", "rails", [I("ruby_rail")])
    tag("item", "minecraft", "rails", [I("ruby_rail")])
    tag("block", "minecraft", "mineable/pickaxe", [I("ruby_rail")])
    shaped("ruby_rail", ["X X", "X#X", "XRX"], {"X": I("ruby"), "#": M("stick"), "R": M("redstone")}, I("ruby_rail"), 12, category="misc")
    for fid in FEASTS:
        self_drop(fid)

    # New looks for farm animals, each in its own kind of place
    def variant(kind, vid, biomes, model=None):
        v = {"asset_id": f"{NS}:entity/{kind}/{vid}", "baby_asset_id": f"{NS}:entity/{kind}/{vid}_baby",
             "spawn_conditions": [{"condition": {"type": "minecraft:biome", "biomes": biomes}, "priority": 2}]}
        if model:
            v["model"] = model
        write(f"{DATA}/{NS}/{kind}_variant/{vid}.json", v)
    variant("chicken", "duck", [M("river"), M("frozen_river"), M("swamp"), M("mangrove_swamp")])
    variant("cow", "highland", [M("meadow"), M("windswept_hills"), M("windswept_gravelly_hills"), M("windswept_forest")], "cold")
    variant("pig", "wild_boar", [M("forest"), M("dark_forest"), M("birch_forest"), M("old_growth_birch_forest")])

    # The Crystal Hollows: an End highlands biome of its own (decorated with crystals by the mod)
    with open(os.path.join(VANILLA, "data/minecraft/worldgen/biome/end_highlands.json")) as f:
        biome = json.load(f)
    biome["effects"]["water_color"] = "#b87af2"
    write(f"{DATA}/{NS}/worldgen/biome/crystal_hollows.json", biome)
    print("the rest generated")


if __name__ == "__main__":
    textures()
    models()
    data()
