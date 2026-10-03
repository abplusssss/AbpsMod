#!/usr/bin/env python3
"""
Generates the Fishing & Exploration content: 43 fish (recoloured from vanilla's four fish), rods, bait, a treasure
map, trophy mounts and sushi, with models, names, tags and recipes. It also writes src/main/resources/abpsmod/fish.json,
which the mod reads at startup to register the fish and decide where each one bites.

    VANILLA=/path/to/minecraft-assets-26.3 python3 tools/gen_fish.py
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_food import (ASSETS, DATA, NS, I, M, ANY, hexrgb, load, lum, ramp, recolor, save, shaped, shapeless, sprite, tag, write, item_model)

# id, name, rarity (1 common .. 5 legendary), where, when, base sprite, body colour, accent colour
FISH = [
    ("sardine", "Sardine", 1, "ocean", "any", "cod", "#B8C7D6", "#4C6A8A"),
    ("anchovy", "Anchovy", 1, "ocean", "any", "cod", "#9FB0B8", "#3E5560"),
    ("perch", "Perch", 1, "river", "any", "cod", "#C9C26A", "#4F5A24"),
    ("trout", "Trout", 1, "river", "any", "salmon", "#D9A8A0", "#6E5A50"),
    ("carp", "Carp", 1, "river", "any", "cod", "#C08A4A", "#6A4420"),
    ("bluegill", "Bluegill", 1, "river", "day", "tropical_fish", "#5E86C9", "#E8913A"),
    ("catfish", "Catfish", 1, "swamp", "any", "cod", "#7E7466", "#3A322A"),
    ("herring", "Herring", 1, "cold", "any", "cod", "#C9D4DE", "#56708A"),
    ("mackerel", "Mackerel", 1, "ocean", "day", "cod", "#6FA8A0", "#1F4E5A"),
    ("minnow", "Minnow", 1, "river", "any", "cod", "#C7B48C", "#6E5E3E"),
    ("bass", "Bass", 2, "river", "any", "salmon", "#7FA052", "#33461F"),
    ("pike", "Pike", 2, "river", "any", "salmon", "#A8B054", "#46502A"),
    ("snapper", "Red Snapper", 2, "warm", "any", "salmon", "#E0604A", "#7A2A1E"),
    ("tilapia", "Tilapia", 2, "swamp", "any", "cod", "#B49A9A", "#5A4848"),
    ("eel", "Eel", 2, "ocean", "night", "salmon", "#5E6A3A", "#262C16"),
    ("flounder", "Flounder", 2, "ocean", "any", "cod", "#C9B48C", "#6E5A3A"),
    ("grouper", "Grouper", 2, "warm", "any", "pufferfish", "#9A7A5A", "#4A3424"),
    ("rainbow_trout", "Rainbow Trout", 2, "river", "rain", "salmon", "#E8A0C0", "#4FA07A"),
    ("arctic_char", "Arctic Char", 2, "cold", "any", "salmon", "#E8744A", "#5A3A30"),
    ("piranha", "Piranha", 2, "jungle", "any", "pufferfish", "#D8423A", "#7A8A90"),
    ("tuna", "Tuna", 3, "ocean", "any", "salmon", "#3E5A8A", "#C9D2DC"),
    ("swordfish", "Swordfish", 3, "ocean", "day", "salmon", "#4A78B8", "#1E2E50"),
    ("lionfish", "Lionfish", 3, "warm", "any", "tropical_fish", "#E8563A", "#F7EBDC"),
    ("angelfish", "Angelfish", 3, "warm", "day", "tropical_fish", "#F2CE3A", "#3A64C8"),
    ("clownfish", "Clownfish", 3, "warm", "any", "tropical_fish", "#F28A2A", "#FFFFFF"),
    ("ghost_carp", "Ghost Carp", 3, "river", "night", "cod", "#E4ECF7", "#8FA4C8"),
    ("frost_minnow", "Frost Minnow", 3, "cold", "any", "cod", "#A8E4F7", "#3A8ACB"),
    ("mudfish", "Mudfish", 3, "swamp", "rain", "cod", "#7A5E3E", "#3A2A1A"),
    ("blindfish", "Cave Blindfish", 3, "cave", "any", "cod", "#F2C9C9", "#B48A8A"),
    ("glowfin", "Glowfin", 3, "cave", "any", "tropical_fish", "#5EF2E4", "#1E6A80"),
    ("sturgeon", "Sturgeon", 4, "river", "rain", "salmon", "#5A6066", "#24282C"),
    ("marlin", "Marlin", 4, "ocean", "day", "salmon", "#2A4AA8", "#9FC4F2"),
    ("golden_koi", "Golden Koi", 4, "river", "any", "tropical_fish", "#F7C83A", "#FFFFFF"),
    ("moonfish", "Moonfish", 4, "ocean", "night", "pufferfish", "#C9C4F2", "#5A4E9A"),
    ("storm_eel", "Storm Eel", 4, "ocean", "rain", "salmon", "#F2E44A", "#2A2E3E"),
    ("magma_cod", "Magma Cod", 2, "lava", "any", "cod", "#F27A2A", "#2A1610"),
    ("blaze_snapper", "Blaze Snapper", 3, "lava", "any", "salmon", "#F7C02A", "#B8401E"),
    ("obsidian_angler", "Obsidian Angler", 4, "lava", "any", "pufferfish", "#4A2E6A", "#160E22"),
    ("void_minnow", "Void Minnow", 3, "void", "any", "cod", "#5A3A8A", "#120A20"),
    ("chorus_guppy", "Chorus Guppy", 4, "void", "any", "tropical_fish", "#C85AC8", "#F2D2F7"),
    ("sea_dragon", "Sea Dragon", 5, "ocean", "night", "salmon", "#2AA89A", "#F2C83A"),
    ("phoenix_koi", "Phoenix Koi", 5, "lava", "any", "tropical_fish", "#F23A2A", "#F7D23A"),
    ("end_leviathan", "End Leviathan", 5, "void", "any", "salmon", "#2A1A3E", "#B88AF2"),
]

RARITY = {1: "Common", 2: "Uncommon", 3: "Rare", 4: "Epic", 5: "Legendary"}

PAL = {"o": "#2B1A10", "W": "#F7F4EA", "w": "#D9D2C2", "S": "#F28C6B", "s": "#C9603E", "K": "#24342A", "k": "#3A5A3A", "P": "#E88A9A",
       "p": "#B85A6A", "G": "#F2D23A", "g": "#B8901E", "R": "#C83A2A", "B": "#6A4A2A", "b": "#4A3018"}

DRAWN = {
    "worm_bait": ("Worm Bait", [
        "................", "................", "................", "......PPP.......", ".....PpppP......", "....Pp...pP.....",
        "....Pp....P.....", ".....P....pP....", "..........pP....", ".........Pp.....", "...PP...Pp......", "..Pp.pPPp.......",
        "..Pp..pp........", "...P............", "................", "................"], PAL),
    "sushi": ("Sushi", [
        "................", "................", "................", "................", "....SSSSSSSS....", "...SsSSsSSsSS...",
        "...WWWWWWWWWW...", "..KWWWWWWWWWWK..", "..KwWWWWWWWWwK..", "..KkwwwwwwwwkK..", "..KKkkkkkkkkKK..", "...KKKKKKKKKK...",
        "................", "................", "................", "................"], PAL),
}


def two_tone(base, body, accent):
    """Recolours a vanilla fish: the darker half of its pixels takes the accent colour, the lighter half the body colour."""
    im = load(f"item/{base}")
    px = im.load()
    pts = [(x, y) for y in range(16) for x in range(16) if px[x, y][3] > 10]
    ls = sorted(lum(px[x, y]) for x, y in pts)
    cut = ls[len(ls) * 2 // 5]
    dark = [(x, y) for x, y in pts if lum(px[x, y]) <= cut]
    light = [(x, y) for x, y in pts if lum(px[x, y]) > cut]
    for group, color in ((dark, accent), (light, body)):
        if not group:
            continue
        gl = [lum(px[x, y]) for x, y in group]
        lo, hi = min(gl), max(gl)
        R = ramp(color)
        for x, y in group:
            t = ((lum(px[x, y]) - lo) / max(1, hi - lo)) ** 0.8
            px[x, y] = R[min(6, 1 + int(round(t * 5)))] + (px[x, y][3],)
    # A dark outline pixel for the eye stays dark
    return im


def textures():
    for fid, _, _, _, _, base, body, accent in FISH:
        save(two_tone(base, body, accent), f"item/{fid}")
    for rid, col in (("ruby_rod", "#E8344A"), ("magma_rod", "#F27A2A"), ("void_rod", "#8A4AD8")):
        for suffix in ("", "_cast"):
            im = load(f"item/fishing_rod{suffix}")
            recolor(im, lambda c: c[3] > 10 and lum(c) < 150 and (c[0] > c[2] + 10), col)
            save(im, f"item/{rid}{suffix}")
    save(recolor(load("item/glow_berries"), ANY, "#5EF2C8"), "item/glow_bait")
    save(recolor(load("item/glow_berries"), ANY, "#F7C83A"), "item/golden_bait")
    save(load("item/buried_treasure_map"), "item/treasure_map")
    save(recolor(load("item/item_frame"), ANY, "#8A5A2E"), "item/trophy_mount")
    for iid, (_, rows, pal) in DRAWN.items():
        save(sprite(rows, pal), f"item/{iid}")


def models():
    lang = {}
    for fid, name, rarity, *_ in FISH:
        item_model(fid)
        lang[f"item.{NS}.{fid}"] = name
    for rid, name in (("ruby_rod", "Ruby Fishing Rod"), ("magma_rod", "Magma Rod"), ("void_rod", "Void Rod")):
        for suffix in ("", "_cast"):
            write(f"{ASSETS}/models/item/{rid}{suffix}.json", {"parent": "minecraft:item/handheld_rod", "textures": {"layer0": f"{NS}:item/{rid}{suffix}"}})
        write(f"{ASSETS}/items/{rid}.json", {"model": {"type": "minecraft:condition", "property": "minecraft:fishing_rod/cast",
                                                       "on_false": {"type": "minecraft:model", "model": f"{NS}:item/{rid}"},
                                                       "on_true": {"type": "minecraft:model", "model": f"{NS}:item/{rid}_cast"}}})
        lang[f"item.{NS}.{rid}"] = name
    for iid, name in (("glow_bait", "Glow Bait"), ("golden_bait", "Golden Bait"), ("treasure_map", "Treasure Map"), ("trophy_mount", "Trophy Mount")):
        item_model(iid)
        lang[f"item.{NS}.{iid}"] = name
    for iid, (name, _, _) in DRAWN.items():
        item_model(iid)
        lang[f"item.{NS}.{iid}"] = name
    path = f"{ASSETS}/lang/en_us.json"
    full = json.load(open(path))
    full.update(lang)
    write(path, full)


def data():
    fish = [I(f[0]) for f in FISH]
    tag("item", NS, "fish", fish)
    tag("item", "minecraft", "fishes", fish)
    for k in ("smelting", "smoking", "campfire_cooking"):
        times = {"smelting": 200, "smoking": 100, "campfire_cooking": 600}
        write(f"{DATA}/{NS}/recipe/cooked_fish_from_{k}.json", {"type": f"minecraft:{k}", "category": "food", "cookingtime": times[k],
                                                               "experience": 0.35, "ingredient": f"#{NS}:fish", "result": {"id": I("cooked_fillet")}})
    shaped("ruby_rod", ["  /", " /S", "/ R"], {"/": M("stick"), "S": M("string"), "R": I("ruby")}, I("ruby_rod"), category="equipment")
    shaped("magma_rod", ["  /", " /S", "/ M"], {"/": M("blaze_rod"), "S": M("string"), "M": M("magma_cream")}, I("magma_rod"), category="equipment")
    shaped("void_rod", ["  /", " /S", "/ E"], {"/": M("end_rod"), "S": M("string"), "E": I("endite_shard")}, I("void_rod"), category="equipment")
    shapeless("worm_bait", [M("dirt"), M("rotten_flesh")], I("worm_bait"), 4)
    shapeless("glow_bait", [I("worm_bait"), M("glow_berries")], I("glow_bait"), 1)
    shapeless("golden_bait", [I("worm_bait"), M("gold_nugget"), M("gold_nugget")], I("golden_bait"), 1)
    shapeless("sushi", [I("fish_fillet"), M("kelp")], I("sushi"), 2)
    shaped("trophy_mount", ["PPP", "PFP", "PGP"], {"P": "#minecraft:planks", "F": M("item_frame"), "G": M("gold_ingot")}, I("trophy_mount"))
    os.makedirs(os.path.join(os.path.dirname(DATA), "abpsmod"), exist_ok=True)
    with open(os.path.join(os.path.dirname(DATA), "abpsmod", "fish.json"), "w") as f:
        json.dump([{"id": x[0], "name": x[1], "rarity": x[2], "where": x[3], "when": x[4]} for x in FISH], f, indent=1)
    print(f"{len(FISH)} fish generated")


if __name__ == "__main__":
    textures()
    models()
    data()
